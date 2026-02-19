package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetParameters.DimensionResetBehavior
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.util.TargetWorldPatternSubstitutor
import net.chlod.minecraft.homerun.data.PlayerLockout
import net.chlod.minecraft.homerun.data.ResetLock
import net.chlod.minecraft.homerun.data.world.ResetLoadInstructions
import net.chlod.minecraft.homerun.data.world.WorldCopyLoadInstruction
import net.chlod.minecraft.homerun.data.world.WorldRenameLoadInstruction
import net.chlod.minecraft.homerun.data.world.WorldResetLoadInstruction
import net.chlod.minecraft.homerun.offline.WorldDataTransferUtil
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

    inner class DimensionResetSubtask(val sourceWorld: World, val sourceBehavior: DimensionResetBehavior?) {
        fun onLockout(): PlayerLockout {
            val dimLockout = PlayerLockout.of(sourceWorld)
            dimLockout.lock()
            dimLockout.kickAll()
            return dimLockout
        }

        fun onGenerate(targetWorldName: String): ResetLoadInstructions? {
            sourceWorld.save(true)
            val dimResetInstructions = checkGenerateDimension(
                sourceWorld,
                targetWorldName,
                sourceBehavior,
            )
            if (dimResetInstructions != null) {
                val targetWorldDim = dimResetInstructions.first
                if (targetWorldDim != null) {
                    setMetadata(sourceWorld, targetWorldDim)
                }
                return dimResetInstructions.second
            } else {
                return null
            }
        }
    }

    override fun run() {
        if (Bukkit.isTickingWorlds()) {
            return
        }

        // 1. Load old world
        val sourceWorld = server.getWorld(rule.parameters.world ?: server.worlds[0].name)
        if (sourceWorld == null) {
            componentLogger.error("Could not reset world: world '${rule.parameters.world}' not loaded")
            cancel()
            return
        }

        // 2. Kick out all players and prevent new joins or teleports until we're done
        val lockouts = mutableListOf<PlayerLockout>()
        val lockout = PlayerLockout.of(sourceWorld)
        lockouts.add(lockout)
        lockout.lock()
        lockout.kickAll()

        // 2a. Process dimensions and, if they also need to lockout, lockout.
        val netherResetSubtask = processNether(sourceWorld)
        val endResetSubtask = processEnd(sourceWorld)
        if (netherResetSubtask != null) {
            lockouts.add(netherResetSubtask.onLockout())
        }
        if (endResetSubtask != null) {
            lockouts.add(endResetSubtask.onLockout())
        }

        // 2b. We can consider this task as "past the point of no return"; let's cancel it to prevent it from running
        // on next tick.
        cancel()

        // 3. Generate new world with substituted name, and save old world to ensure all data is written to disk.
        val worldPatternSubstitutor = TargetWorldPatternSubstitutor(plugin, rule)
        val targetWorldName = worldPatternSubstitutor.substitute(
            sourceWorld, rule.parameters.targetWorldPattern ?: $$"$${sourceWorld.name}_${timestamp}"
        )
        val targetWorld = generateWorld(sourceWorld, targetWorldName) ?: return
        sourceWorld.save(true)
        setMetadata(sourceWorld, targetWorld)

        // 4. Gather information about the world to be used for loading, and generate chunks in the import area of the
        // new world to ensure an .mca file is created for the region.
        val resetInstructionsList = mutableListOf<ResetLoadInstructions>()
        val resetInstructions = gatherWorldInformation(sourceWorld, targetWorldName)
        resetInstructionsList.add(resetInstructions)
        generateWorldChunks(resetInstructions.chunks!!, targetWorld)

        // 4a. Do the same for dimensions, if necessary.
        if (netherResetSubtask != null) {
            val netherResetInstructions = netherResetSubtask.onGenerate(targetWorldName)
            if (netherResetInstructions != null) {
                resetInstructionsList.add(netherResetInstructions)
            }
        }
        if (endResetSubtask != null) {
            val endResetInstructions = endResetSubtask.onGenerate(targetWorldName)
            if (endResetInstructions != null) {
                resetInstructionsList.add(endResetInstructions)
            }
        }

        // 5. Create instruction list
        val resetLock = ResetLock.create(
            plugin,
            resetInstructionsList
        )
        resetLock.save()
        componentLogger.info("Reset instructions list saved.")

        // 6. Copy datapacks now to ensure they're in place when the world is loaded, otherwise they will be marked
        // as missing before our plugin load listeners even fire.
        for (instructions in resetInstructionsList) {
            if (instructions is WorldResetLoadInstruction) {
                WorldDataTransferUtil(plugin, instructions).copyDatapacks()
            }
        }

        // 7. Modify server properties to set new world as spawn world, and restart if necessary.
        if (rule.parameters.modifyServerProperties ?: true) {
            modifyServerPropertiesViaReflection(targetWorldName)
        }
        if (rule.parameters.restart ?: (rule.parameters.modifyServerProperties ?: false)) {
            componentLogger.info("Restarting server...")
            server.restart()
        } else if (
            rule.parameters.modifyServerProperties == true
            && rule.parameters.restart == false
        ) {
            componentLogger.warn("Restart not requested, but server.properties was modified. This is dangerous!")
            componentLogger.warn("You should remove the \"restart\" parameter for this reset rule, or set it to true.")
        }

        // If the server isn't restarting, we can unlock the world immediately since players will be kicked and
        // won't be able to rejoin until they see the new world name in the server list.
        lockouts.forEach { it.unlock() }
    }

    fun processNether(sourceWorld: World): DimensionResetSubtask? {
        if (sourceWorld.environment != Environment.NORMAL && rule.parameters.netherBehavior != DimensionResetBehavior.WIPE) {
            componentLogger.warn("Tried to reset Nether for world of ${sourceWorld.environment.name} dimension. Skipping...")
            return null
        } else {
            when (val sourceWorldNether = server.getWorld("${sourceWorld.name}_nether")) {
                null if rule.parameters.netherBehavior != DimensionResetBehavior.WIPE -> {
                    componentLogger.warn("Source world Nether dimension not found, skipping Nether generation...")
                    return null
                }

                null -> {
                    /* do nothing. This condition exists for smart casting. */
                    return null
                }

                else -> return DimensionResetSubtask(sourceWorldNether, rule.parameters.netherBehavior)
            }
        }
    }

    fun processEnd(sourceWorld: World): DimensionResetSubtask? {
        if (sourceWorld.environment != Environment.NORMAL && rule.parameters.endBehavior != DimensionResetBehavior.WIPE) {
            componentLogger.warn("Tried to reset End for world of ${sourceWorld.environment.name} dimension. Skipping...")
            return null
        } else {
            when (val sourceWorldEnd = server.getWorld("${sourceWorld.name}_the_end")) {
                null if rule.parameters.endBehavior != DimensionResetBehavior.WIPE -> {
                    componentLogger.warn("Source world End dimension not found, skipping End generation...")
                    return null
                }

                null -> {
                    /* do nothing. This condition exists for smart casting. */
                    return null
                }

                else -> return DimensionResetSubtask(sourceWorldEnd, rule.parameters.endBehavior)
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
                    gatherWorldInformation(sourceWorldDim, "${targetWorldName}_${dimensionSuffix}")
                )
                generateWorldChunks(generateInfo.second.chunks!!, world!!)
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

    fun gatherWorldInformation(sourceWorld: World, targetWorldName: String): WorldResetLoadInstruction {
        componentLogger.info("Getting list of retained chunks...")

        val retainedChunkList = mutableListOf<Pair<Int, Int>>()
        for (selector in rule.parameters.retainedChunks) {
            componentLogger.info("* ${selector.getHumanReadableDescription()}")
            retainedChunkList.addAll(selector.getRetainedChunks(plugin, sourceWorld))
        }
        val retainedChunks = retainedChunkList.distinct()

        return WorldResetLoadInstruction(
            sourceWorld.name,
            sourceWorld.environment.id,
            targetWorldName,
            retainedChunks,
            rule.parameters.outsidePlayerBehavior
        )
    }

}