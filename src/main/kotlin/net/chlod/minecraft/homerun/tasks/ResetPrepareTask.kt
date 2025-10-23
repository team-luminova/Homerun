package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetParameters.DimensionResetBehavior
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.util.TargetWorldPatternSubstitutor
import net.chlod.minecraft.homerun.data.ResetLock
import net.chlod.minecraft.homerun.data.world.WorldResetLoadInstruction
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.World.Environment
import org.bukkit.WorldCreator
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable
import java.io.File

class ResetPrepareTask(val plugin: Homerun, val rule: ResetRule) : BukkitRunnable() {

    val componentLogger = plugin.componentLogger
    val server = plugin.server

    override fun run() {
        if (Bukkit.isTickingWorlds()) {
            return
        }

        // Load old world
        val sourceWorld = server.getWorld(rule.parameters.world ?: server.worlds[0].name)
        if (sourceWorld == null) {
            componentLogger.error("Could not reset world: world '${rule.parameters.world}' not found")
            cancel()
            return
        }

        val worldPatternSubstitutor = TargetWorldPatternSubstitutor(plugin, rule)
        val targetWorldName = worldPatternSubstitutor.substitute(
            sourceWorld, rule.parameters.targetWorldPattern ?: $$"$${sourceWorld.name}_${timestamp}"
        )

        val targetWorld = generateWorld(sourceWorld, targetWorldName) ?: return
        cancel()
        setMetadata(sourceWorld, targetWorld)

        val resetInstructionsList = mutableListOf<WorldResetLoadInstruction>()
        val resetInstructions = gatherWorldInformation(sourceWorld, targetWorldName)
        resetInstructionsList.add(resetInstructions)

        if (sourceWorld.environment != Environment.NORMAL && rule.parameters.netherBehavior != null) {
            componentLogger.warn("Tried to reset Nether for world of ${sourceWorld.environment.name} dimension. Skipping...")
        } else {
            when (val sourceWorldNether = server.getWorld("${sourceWorld.name}_nether")) {
                null if rule.parameters.netherBehavior != DimensionResetBehavior.WIPE -> {
                    componentLogger.warn("Source world Nether dimension not found, skipping Nether generation...")
                }

                null -> { /* do nothing. This condition exists for smart casting. */
                }

                else -> {
                    val dimResetInstructions = checkGenerateDimension(
                        sourceWorldNether,
                        targetWorldName,
                        rule.parameters.netherBehavior
                    )
                    if (dimResetInstructions != null) {
                        if (dimResetInstructions.first != null) {
                            setMetadata(sourceWorld, targetWorld)
                        }
                        resetInstructionsList.add(dimResetInstructions.second)
                    }
                }
            }
        }
        if (sourceWorld.environment != Environment.NORMAL && rule.parameters.endBehavior != null) {
            componentLogger.warn("Tried to reset End for world of ${sourceWorld.environment.name} dimension. Skipping...")
        } else {
            when (val sourceWorldEnd = server.getWorld("${sourceWorld.name}_the_end")) {
                null if rule.parameters.endBehavior != DimensionResetBehavior.WIPE -> {
                    componentLogger.warn("Source world End dimension not found, skipping End generation...")
                }

                null -> { /* do nothing. This condition exists for smart casting. */
                }

                else -> {
                    val dimResetInstructions = checkGenerateDimension(
                        sourceWorldEnd,
                        targetWorldName,
                        rule.parameters.endBehavior
                    )
                    if (dimResetInstructions != null) {
                        if (dimResetInstructions.first != null) {
                            setMetadata(sourceWorld, targetWorld)
                        }
                        resetInstructionsList.add(dimResetInstructions.second)
                    }
                }
            }
        }

        val resetLock = ResetLock.create(
            plugin,
            resetInstructionsList
        )
        resetLock.save()
        componentLogger.info("Reset instructions list saved.")

        if (rule.parameters.modifyServerProperties ?: true) {
            val serverProperties = File(server.pluginsFolder.parentFile, "server.properties")
            componentLogger.info("Modifying server.properties to set spawn world...")
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
            componentLogger.info("Modified server.properties spawn world...")
        }

        if (rule.parameters.restart ?: true) {
            componentLogger.info("Restarting server...")
            server.restart()
        }
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

    fun setMetadata(sourceWorld: World, targetWorld: World) {
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
    }

    fun checkGenerateDimension(
        sourceWorldDim: World,
        targetWorldName: String,
        behavior: DimensionResetBehavior?
    ): Pair<World?, WorldResetLoadInstruction>? {
        val dimensionSuffix = sourceWorldDim.environment.name.lowercase()
        when (behavior) {
            DimensionResetBehavior.NORMAL -> {
                val world = generateWorld(sourceWorldDim, "${targetWorldName}_${dimensionSuffix}")
                return Pair(
                    world,
                    gatherWorldInformation(sourceWorldDim, "${targetWorldName}_${dimensionSuffix}")
                )
            }

            DimensionResetBehavior.RENAME, DimensionResetBehavior.COPY -> {
                return Pair(
                    null,
                    WorldResetLoadInstruction(
                        sourceWorldDim.name + "_nether",
                        targetWorldName + "_nether"
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

        componentLogger.info("Getting source spawn location...")
        val spawnLocation = sourceWorld.spawnLocation.clone()
        spawnLocation.world = null

        return WorldResetLoadInstruction(
            sourceWorld.name,
            targetWorldName,
            retainedChunks,
            spawnLocation,
            rule.parameters.outsidePlayerBehavior
        )
    }

}