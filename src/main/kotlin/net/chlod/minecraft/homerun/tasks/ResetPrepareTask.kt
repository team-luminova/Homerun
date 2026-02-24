package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetParameters
import net.chlod.minecraft.homerun.config.ResetParameters.DimensionResetBehavior
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.util.TargetWorldPatternSubstitutor
import net.chlod.minecraft.homerun.data.PlayerLockout
import net.chlod.minecraft.homerun.data.ResetLock
import net.chlod.minecraft.homerun.data.world.ResetLoadInstructions
import net.chlod.minecraft.homerun.data.world.WorldCopyLoadInstruction
import net.chlod.minecraft.homerun.data.world.WorldRenameLoadInstruction
import net.chlod.minecraft.homerun.data.world.WorldResetLoadInstruction
import net.chlod.minecraft.homerun.online.NMSChunkTransferUtil
import net.minecraft.server.dedicated.DedicatedServer
import net.minecraft.server.dedicated.DedicatedServerProperties
import net.minecraft.server.dedicated.DedicatedServerSettings
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.World.Environment
import org.bukkit.WorldCreator
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.entity.EnderCrystal
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable
import java.io.File
import java.nio.file.Path

class ResetPrepareTask(val plugin: Homerun, val rule: ResetRule) : BukkitRunnable() {

    val componentLogger = plugin.componentLogger
    val server = plugin.server

    inner class DimensionResetSubtask(
        val parameters: ResetParameters,
        val sourceWorld: World,
        val sourceBehavior: DimensionResetBehavior?
    ) {
        fun onLockout(): PlayerLockout {
            val dimLockout = PlayerLockout.of(sourceWorld)
            dimLockout.lock()
            dimLockout.kickAll(plugin)
            return dimLockout
        }

        fun onGenerate(targetWorldName: String): Pair<World?, ResetLoadInstructions>? {
            sourceWorld.save(true)
            val dimResetInstructions = checkGenerateDimension(
                parameters,
                sourceWorld,
                targetWorldName,
                sourceBehavior,
            )
            if (dimResetInstructions != null) {
                val targetWorldDim = dimResetInstructions.first
                if (targetWorldDim != null) {
                    setMetadata(sourceWorld, targetWorldDim)
                }
                return dimResetInstructions
            } else {
                return null
            }
        }
    }

    override fun run() {
        if (Bukkit.isTickingWorlds()) {
            return
        }

        val lockouts = mutableListOf<PlayerLockout>()
        val resetInstructionsList = mutableListOf<ResetLoadInstructions>()

        // Track the last target world name for server.properties modification
        var lastTargetWorldName: String? = null

        // Process each parameter set in series. Each one targets a (potentially different) world.
        for (parameters in rule.parametersList) {
            // 1. Load old world
            var sourceWorld = server.getWorld(parameters.world ?: server.worlds[0].name)
            if (sourceWorld == null) {
                componentLogger.error("World '${parameters.world}' not loaded. It will be generated. This might not be what you want! Skipping this reset rule...")
                sourceWorld = server.createWorld(WorldCreator(parameters.world ?: server.worlds[0].name))!!
            }

            // 2. Kick out all players and prevent new joins or teleports until we're done
            val lockout = PlayerLockout.of(sourceWorld)
            lockouts.add(lockout)
            lockout.lock()
            lockout.kickAll(plugin)

            // 2a. Process dimensions and, if they also need to lockout, lockout.
            val netherResetSubtask = processNether(sourceWorld, parameters)
            val endResetSubtask = processEnd(sourceWorld, parameters)
            if (netherResetSubtask != null) {
                lockouts.add(netherResetSubtask.onLockout())
            }
            if (endResetSubtask != null) {
                lockouts.add(endResetSubtask.onLockout())
            }

            // 3. Generate new world with substituted name, and save old world to ensure all data is written to disk.
            val worldPatternSubstitutor = TargetWorldPatternSubstitutor(plugin, rule)
            val targetWorldName = worldPatternSubstitutor.substitute(
                sourceWorld, parameters.targetWorldPattern ?: $$"$${sourceWorld.name}_${timestamp}"
            )
            val targetWorld = generateWorld(sourceWorld, targetWorldName) ?: continue
            sourceWorld.save(true)
            setMetadata(sourceWorld, targetWorld)
            if (parameters.modifyServerProperties == true) {
                lastTargetWorldName = targetWorldName
            }

            // 4. Gather information about the world to be used for loading, and generate chunks in the import area of the
            // new world to ensure an .mca file is created for the region.
            val subDimensions = mutableListOf<WorldResetLoadInstruction.SubDimensionInfo>()

            // 4a. Process dimensions first so we can embed SubDimensionInfo into the overworld instruction.
            if (netherResetSubtask != null) {
                val netherResult = netherResetSubtask.onGenerate(targetWorldName)
                if (netherResult != null) {
                    val (_, netherInstructions) = netherResult
                    resetInstructionsList.add(netherInstructions)
                    subDimensions.add(
                        WorldResetLoadInstruction.SubDimensionInfo(
                            worldName = netherInstructions.targetWorld,
                            worldUUID = if (netherInstructions is WorldResetLoadInstruction)
                                netherInstructions.targetWorldUUID else null,
                            environment = Environment.NETHER,
                            resetType = netherInstructions.type
                        )
                    )
                }
            }
            if (endResetSubtask != null) {
                val endResult = endResetSubtask.onGenerate(targetWorldName)
                if (endResult != null) {
                    val (_, endInstructions) = endResult
                    resetInstructionsList.add(endInstructions)
                    subDimensions.add(
                        WorldResetLoadInstruction.SubDimensionInfo(
                            worldName = endInstructions.targetWorld,
                            worldUUID = if (endInstructions is WorldResetLoadInstruction)
                                endInstructions.targetWorldUUID else null,
                            environment = Environment.THE_END,
                            resetType = endInstructions.type
                        )
                    )
                }
            }

            val resetInstructions = gatherWorldInformation(
                parameters, sourceWorld, targetWorld,
                subDimensions = subDimensions.ifEmpty { null }
            )
            resetInstructionsList.add(resetInstructions)
            generateWorldChunks(resetInstructions.chunks!!, targetWorld)
        }

        if (resetInstructionsList.isEmpty()) {
            componentLogger.error("No worlds were successfully processed for reset.")
            cancel()
            return
        }

        // 2b. We can consider this task as "past the point of no return"; let's cancel it to prevent it from running
        // on next tick.
        cancel()

        // 5. Ensure that the chunks also exist in the source world (1.21.9+ will no longer generate spawn chunks).
        for (instructions in resetInstructionsList) {
            if (instructions !is WorldResetLoadInstruction) {
                continue
            }

            val world = plugin.server.getWorld(instructions.sourceWorld)!!
            generateWorldChunks(instructions.chunks!!, world)
        }

        // 6. Create instruction list
        val resetLock = ResetLock.create(
            plugin,
            resetInstructionsList
        )
        resetLock.save()
        componentLogger.info("Reset instructions list saved.")

        // 7. Copy datapacks now to ensure they're in place when the world is loaded, otherwise they will be marked
        // as missing before our plugin load listeners even fire.
        for (instructions in resetInstructionsList) {
            if (instructions !is WorldResetLoadInstruction) {
                continue
            }

            NMSChunkTransferUtil(plugin, instructions, false).copyDatapacks()
        }

        // 8. Modify server properties to set new world as spawn world, and restart if necessary.
        val needsRestart = rule.parametersList.any { it.restart == true || it.modifyServerProperties == true }
        val willRestart = rule.parametersList.any { it.restart ?: it.modifyServerProperties ?: false }

        val newMainWorlds = rule.parametersList.filter { it.modifyServerProperties ?: false }
        if (newMainWorlds.size > 1) {
            componentLogger.warn("Multiple reset rules are set to modify server properties.")
            componentLogger.warn("Only the last one will take effect. Consider disabling `modify_server_properties` for all but one rule to avoid confusion.")
        }

        if (lastTargetWorldName != null) {
            modifyServerPropertiesViaReflection(lastTargetWorldName)
        }
        if (willRestart) {
            componentLogger.info("Restarting server...")
            server.restart()
        } else if (needsRestart) {
            componentLogger.warn("Restart not requested, but server.properties was modified. This is dangerous!")
            componentLogger.warn("You should remove the \"restart\" parameter for this reset rule, or set it to true.")
        }

        // If the server isn't restarting, we can unlock the world immediately since players will be kicked and
        // won't be able to rejoin until they see the new world name in the server list.
        lockouts.forEach { it.unlock() }
    }

    fun processNether(sourceWorld: World, parameters: ResetParameters): DimensionResetSubtask? {
        if (sourceWorld.environment != Environment.NORMAL && parameters.netherBehavior != DimensionResetBehavior.WIPE) {
            componentLogger.warn("Tried to reset Nether for world of ${sourceWorld.environment.name} dimension. Skipping...")
            return null
        } else {
            when (val sourceWorldNether = server.getWorld("${sourceWorld.name}_nether")) {
                null if parameters.netherBehavior != DimensionResetBehavior.WIPE -> {
                    componentLogger.warn("Source world Nether dimension not found, skipping Nether generation...")
                    return null
                }

                null -> {
                    /* do nothing. This condition exists for smart casting. */
                    return null
                }

                else -> return DimensionResetSubtask(parameters, sourceWorldNether, parameters.netherBehavior)
            }
        }
    }

    fun processEnd(sourceWorld: World, parameters: ResetParameters): DimensionResetSubtask? {
        if (sourceWorld.environment != Environment.NORMAL && parameters.endBehavior != DimensionResetBehavior.WIPE) {
            componentLogger.warn("Tried to reset End for world of ${sourceWorld.environment.name} dimension. Skipping...")
            return null
        } else {
            when (val sourceWorldEnd = server.getWorld("${sourceWorld.name}_the_end")) {
                null if parameters.endBehavior != DimensionResetBehavior.WIPE -> {
                    componentLogger.warn("Source world End dimension not found, skipping End generation...")
                    return null
                }

                null -> {
                    /* do nothing. This condition exists for smart casting. */
                    return null
                }

                else -> return DimensionResetSubtask(parameters, sourceWorldEnd, parameters.endBehavior)
            }
        }
    }

    fun modifyServerPropertiesViaReflection(targetWorldName: String) {
        try {
            val dedicatedServerField = CraftServer::class.java.getDeclaredField("console")
            dedicatedServerField.isAccessible = true
            val dedicatedServer = dedicatedServerField.get(server) as DedicatedServer
            val dedicatedServerSettings = dedicatedServer.settings
            val serverPropertiesPathField = DedicatedServerSettings::class.java.getDeclaredField("source")
            serverPropertiesPathField.isAccessible = true

            val serverPropertiesPath = serverPropertiesPathField.get(dedicatedServerSettings) as Path
            componentLogger.info("Detected server.properties path: $serverPropertiesPath")

            val serverPropertiesField = DedicatedServerSettings::class.java.getDeclaredField("properties")
            serverPropertiesField.isAccessible = true
            val serverProperties = serverPropertiesField.get(dedicatedServerSettings) as DedicatedServerProperties
            serverProperties.properties["level-name"] = targetWorldName
            dedicatedServerSettings.forceSave()

            componentLogger.info("Modified server.properties spawn world safely: $targetWorldName")
        } catch (e: Exception) {
            componentLogger.error("Could not modify server.properties via reflection: ${e.message}")
            modifyServerPropertiesDirectly(targetWorldName)
        }
    }

    fun modifyServerPropertiesDirectly(targetWorldName: String) {
        val serverProperties = File(server.pluginsFolder.parentFile, "server.properties")

        if (!serverProperties.exists()) {
            componentLogger.error("Could not find server.properties at expected location: ${serverProperties.path}")
            componentLogger.error("You will need to set the spawn world manually to '$targetWorldName'")
            componentLogger.error("Otherwise, players will not be able to join the correct world!")
            return
        }

        componentLogger.info("Modifying server.properties directly to set spawn world...")
        val properties = serverProperties.readLines().toMutableList()
        var spawnWorldSet = false
        for (i in properties.indices) {
            if (properties[i].startsWith("level-name=")) {
                properties[i] = "level-name=$targetWorldName"
                spawnWorldSet = true
                break
            }
        }
        if (!spawnWorldSet) {
            properties.add("level-name=$targetWorldName")
        }
        serverProperties.writeText(properties.joinToString("\n"))
        componentLogger.info("Modified server.properties spawn world directly: $targetWorldName")
    }

    fun generateWorld(sourceWorld: World, targetWorldName: String): World? {
        componentLogger.info("Generating new world ($targetWorldName)...")
        val newWorld = server.createWorld(
            WorldCreator(targetWorldName)
                .environment(sourceWorld.environment)
        )

        if (newWorld == null) {
            componentLogger.error("Failed to create new world!")
            return null
        }

        componentLogger.info("World '${newWorld.name}' loaded/created")
        return newWorld
    }

    fun generateWorldChunks(chunkList: List<Pair<Int, Int>>, world: World) {
        componentLogger.info("Generating ${chunkList.size} chunks in world '${world.name}'...")
        for (chunkCoords in chunkList) {
            world.loadChunk(chunkCoords.first, chunkCoords.second, true)
        }
        world.save(true)
        componentLogger.info("Finished generating chunks in world '${world.name}'.")
    }

    fun setMetadata(sourceWorld: World, targetWorld: World) {
        targetWorld.persistentDataContainer.set(
            plugin.keys.reset,
            PersistentDataType.BOOLEAN,
            true
        )
        targetWorld.persistentDataContainer.set(
            plugin.keys.resetCount,
            PersistentDataType.INTEGER,
            (sourceWorld.persistentDataContainer.get(
                plugin.keys.resetCount,
                PersistentDataType.INTEGER
            ) ?: 0) + 1
        )
        targetWorld.persistentDataContainer.set(
            plugin.keys.resetSourceWorld,
            PersistentDataType.STRING,
            sourceWorld.name
        )
        targetWorld.persistentDataContainer.set(
            plugin.keys.resetSourceSeed,
            PersistentDataType.LONG,
            sourceWorld.seed
        )
        if (!targetWorld.persistentDataContainer.has(
                plugin.keys.resetOriginalSeed,
                PersistentDataType.LONG
            )
        ) {
            val originalSeed = sourceWorld.persistentDataContainer.get(
                plugin.keys.resetOriginalSeed,
                PersistentDataType.LONG
            ) ?: sourceWorld.seed
            targetWorld.persistentDataContainer.set(
                plugin.keys.resetOriginalSeed,
                PersistentDataType.LONG,
                originalSeed
            )
        }
        if (!targetWorld.persistentDataContainer.has(
                plugin.keys.spawnModified,
                PersistentDataType.BOOLEAN
            )
        ) {
            // If this world was already spawn-modified before, the spawn point will be carried over and we don't
            // need to run an overwrite again.
            targetWorld.persistentDataContainer.set(
                plugin.keys.spawnModified,
                PersistentDataType.BOOLEAN,
                true
            )
        }

        val knownEndCrystalLocations = mutableListOf<Triple<Double, Double, Double>>()
        for (crystal in sourceWorld.getEntitiesByClass(EnderCrystal::class.java)) {
            if (crystal.isShowingBottom) {
                knownEndCrystalLocations.add(Triple(crystal.x, crystal.y, crystal.z))
            }
        }
        val serializedLocations = knownEndCrystalLocations.map { (x, y, z) ->
            // Create a byte array with both coordinates
            val bytes = ByteArray(8 * 3) // 8 bytes per double
            java.nio.ByteBuffer.wrap(bytes)
                .putDouble(x)
                .putDouble(y)
                .putDouble(z)
            bytes
        }

        targetWorld.persistentDataContainer.set(
            plugin.keys.endCrystals,
            PersistentDataType.LIST.byteArrays(),
            serializedLocations
        )
    }

    fun checkGenerateDimension(
        parameters: ResetParameters,
        sourceWorldDim: World,
        targetWorldName: String,
        behavior: DimensionResetBehavior?
    ): Pair<World?, ResetLoadInstructions>? {
        val dimensionSuffix = sourceWorldDim.environment.name.lowercase()
        when (behavior) {
            DimensionResetBehavior.NORMAL -> {
                val world = generateWorld(sourceWorldDim, "${targetWorldName}_${dimensionSuffix}")
                val generateInfo = Pair(
                    world,
                    gatherWorldInformation(parameters, sourceWorldDim, world!!)
                )
                generateWorldChunks(generateInfo.second.chunks!!, world)
                return generateInfo
            }

            DimensionResetBehavior.RENAME -> {
                return Pair(
                    null,
                    WorldRenameLoadInstruction(
                        sourceWorldDim.name + dimensionSuffix,
                        sourceWorldDim.environment.id,
                        targetWorldName + dimensionSuffix
                    )
                )
            }

            DimensionResetBehavior.COPY -> {
                return Pair(
                    null,
                    WorldCopyLoadInstruction(
                        sourceWorldDim.name + dimensionSuffix,
                        sourceWorldDim.environment.id,
                        targetWorldName + dimensionSuffix
                    )
                )
            }

            null, DimensionResetBehavior.WIPE -> return null /* do nothing */
        }
    }

    fun gatherWorldInformation(
        parameters: ResetParameters,
        sourceWorld: World,
        targetWorld: World,
        subDimensions: List<WorldResetLoadInstruction.SubDimensionInfo>? = null
    ): WorldResetLoadInstruction {
        componentLogger.info("Getting list of retained chunks...")

        val retainedChunkList = mutableListOf<Pair<Int, Int>>()
        for (selector in parameters.retainedChunks) {
            componentLogger.info("* ${selector.getHumanReadableDescription()}")
            retainedChunkList.addAll(selector.getRetainedChunks(plugin, sourceWorld))
        }
        val retainedChunks = retainedChunkList.distinct()

        return WorldResetLoadInstruction(
            sourceWorld.name,
            sourceWorld.environment.id,
            targetWorld.name,
            targetWorld.uid.toString(),
            retainedChunks,
            parameters.outsidePlayerBehavior,
            subDimensions
        )
    }

}