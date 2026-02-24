package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetParameters
import net.chlod.minecraft.homerun.data.ResetLock
import net.chlod.minecraft.homerun.data.world.ResetLoadInstructions
import net.chlod.minecraft.homerun.data.world.ResetLoadInstructions.ResetLoadInstructionType
import net.chlod.minecraft.homerun.data.world.WorldResetLoadInstruction
import net.chlod.minecraft.homerun.helpers.EndPillarCleanup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import org.bukkit.World
import org.bukkit.World.Environment
import org.bukkit.scheduler.BukkitRunnable
import java.io.File
import java.util.*
import kotlin.io.path.Path

class WorldPostloadTask(val plugin: Homerun, val resetLock: ResetLock) : BukkitRunnable() {

    val componentLogger = plugin.componentLogger

    /**
     * Index of all reset instructions by their **source** world name. Used to resolve
     * a player's `Dimension` tag (which refers to the world they were in before the reset)
     * to the corresponding reset instruction.
     */
    private val resetInstructionBySourceWorld: Map<String, ResetLoadInstructions> =
        resetLock.resetInstructions.associateBy { it.sourceWorld }

    override fun run() {
        for (resetInstructions in resetLock.resetInstructions) {
            if (resetInstructions !is WorldResetLoadInstruction) {
                continue
            }

            componentLogger.info("Running postload for world reset from ${resetInstructions.sourceWorld} to ${resetInstructions.targetWorld}...")
            processWorld(resetInstructions)
        }
    }

    fun processWorld(resetInstructions: WorldResetLoadInstruction) {
        val newWorld = plugin.server.getWorld(resetInstructions.targetWorld)

        if (newWorld == null) {
            componentLogger.error("Target world ${resetInstructions.targetWorld} not found!")
            componentLogger.error("Players may spawn in wrong locations or people may spawn in weird places.")
            return
        }

        processPlayerData(resetInstructions, newWorld)

        if (newWorld.environment == Environment.THE_END) {
            componentLogger.info("Cleaning up end world '${newWorld.name}'...")
            EndPillarCleanup(plugin).cleanupEndWorld(newWorld)
        }
    }

    fun processPlayerData(resetInstructions: WorldResetLoadInstruction, newWorld: World) {
        componentLogger.info("Checking player data...")
        val playersFolder = File(newWorld.worldFolder, "playerdata")
        val playerFiles = playersFolder.listFiles { file -> !file.extension.endsWith("_old") }

        if (playerFiles == null) {
            componentLogger.info("No player data found for world ${resetInstructions.targetWorld}, skipping player data processing.")
            return
        }

        for (playerFile in playerFiles) {
            val rootTag = NbtIo.readCompressed(Path(playerFile.path), NbtAccounter.unlimitedHeap())
            val bukkitTag = rootTag.getCompound("bukkit")
            val lastKnownNameTag = if (bukkitTag.isPresent)
                rootTag.getCompound("bukkit").get().getString("lastKnownName")
            else null
            val playerName = if (lastKnownNameTag?.isPresent == true)
                lastKnownNameTag.get()
            else playerFile.nameWithoutExtension

            val playerDimensionTag = rootTag.getString("Dimension")
            val playerDimension = if (playerDimensionTag.isPresent)
                playerDimensionTag.get()
            else "minecraft:overworld"

            // Resolve the instruction that governs the world the player was in.
            // This handles both main-world standard dimensions (minecraft:overworld, minecraft:the_nether,
            // minecraft:the_end) and non-main worlds (minecraft:<world_name>) as used in Paper 1.21.9+.
            val dimResetInstructions = resolvePlayerWorldInstructions(
                resetInstructions, playerDimension
            )

            if (dimResetInstructions == null) {
                // Player is in a world not targeted by any reset rule. Ignore them.
                componentLogger.debug("Player '$playerName' is in unmanaged dimension '$playerDimension', skipping.")
                NbtIo.writeCompressed(rootTag, Path(playerFile.path))
                continue
            }

            if (dimResetInstructions.type == ResetLoadInstructionType.COPY
                || dimResetInstructions.type == ResetLoadInstructionType.RENAME
            ) {
                // World was copied or renamed — all chunks are preserved, player is fine.
                // Still need to update the Dimension tag to point to the new world name.
                updateDimensionTag(rootTag, playerDimension, dimResetInstructions.targetWorld)
                componentLogger.debug("Player '$playerName' is in copied/renamed world, skipping.")
                NbtIo.writeCompressed(rootTag, Path(playerFile.path))
                continue
            }

            // At this point, the player is in a world that was reset (NORMAL type).
            val dimResetInstruction = dimResetInstructions as WorldResetLoadInstruction

            // Fix the world UUID, or else they'll be teleported to world spawn
            if (dimResetInstruction.targetWorldUUID != null) {
                val dimUUID = UUID.fromString(dimResetInstruction.targetWorldUUID)
                rootTag.putLong("WorldUUIDLeast", dimUUID.leastSignificantBits)
                rootTag.putLong("WorldUUIDMost", dimUUID.mostSignificantBits)
            }

            // Update the Dimension tag to point to the new target world.
            updateDimensionTag(rootTag, playerDimension, dimResetInstruction.targetWorld)

            // Check if the player is currently outside a retained chunk within this specific dimension
            val playerChunk = getOfflineChunk(rootTag)
            var willReset = false
            if (playerChunk == null) {
                componentLogger.info("Could not find position of player '$playerName'. Handling...")
                willReset = true
            } else if (dimResetInstruction.chunks != null) {
                willReset = !dimResetInstruction.chunks.contains(playerChunk)
            }

            if (willReset) {
                componentLogger.info("Player data '$playerName' is outside retained chunks in dimension '$playerDimension', handling...")
                handlePlayerOutsideRetainedChunk(resetInstructions, rootTag)
            }

            NbtIo.writeCompressed(rootTag, Path(playerFile.path))
        }
    }

    /**
     * Resolves the [ResetLoadInstructions] governing the world a player was in, based on
     * their `Dimension` NBT tag.
     *
     * In Paper 1.21.9+, the `Dimension` tag has the following semantics:
     * - `minecraft:overworld` — the main server world (defined by `level-name` in server.properties)
     * - `minecraft:the_nether` — the main server world's Nether dimension
     * - `minecraft:the_end` — the main server world's End dimension
     * - `minecraft:<world_name>` — any other Bukkit world, identified by its folder name.
     *   The name alone does NOT indicate its dimension type (e.g. `minecraft:foo_the_end` may
     *   or may not be an End world).
     *
     * This method maps the dimension string to a **source world name**, then looks it up in the
     * [resetInstructionBySourceWorld] map. For the three standard dimensions, the source world name
     * is derived from the current overworld instruction's [WorldResetLoadInstruction.sourceWorld]
     * and its [WorldResetLoadInstruction.subDimensions] metadata.
     *
     * @param overworldInstruction The overworld [WorldResetLoadInstruction] whose player data is being processed.
     * @param playerDimension The raw `Dimension` NBT tag value (e.g. `minecraft:overworld`).
     * @return The [ResetLoadInstructions] for the player's world, or `null` if the world is not
     *         targeted by any reset rule.
     */
    private fun resolvePlayerWorldInstructions(
        overworldInstruction: WorldResetLoadInstruction,
        playerDimension: String
    ): ResetLoadInstructions? {
        return when (playerDimension) {
            // Standard main-world dimensions: map to the overworld instruction or its sub-dimensions.
            "minecraft:overworld" -> overworldInstruction

            "minecraft:the_nether" -> {
                resolveSubDimension(overworldInstruction, Environment.NETHER)
            }

            "minecraft:the_end" -> {
                resolveSubDimension(overworldInstruction, Environment.THE_END)
            }

            // Non-main world: the dimension string is "minecraft:<world_name>" where <world_name>
            // is the Bukkit world folder name. Look up by source world name directly.
            else -> {
                val worldName = playerDimension.removePrefix("minecraft:")
                resetInstructionBySourceWorld[worldName]
            }
        }
    }

    /**
     * Resolves the [ResetLoadInstructions] for a sub-dimension (Nether/End) of the main world.
     *
     * First checks the overworld instruction's structured [WorldResetLoadInstruction.subDimensions]
     * metadata, then falls back to looking up by conventional source world name for legacy lock files.
     */
    private fun resolveSubDimension(
        overworldInstruction: WorldResetLoadInstruction,
        environment: Environment
    ): ResetLoadInstructions? {
        // Try the structured sub-dimension metadata first
        val subDim = overworldInstruction.getSubDimension(environment)
        if (subDim != null) {
            // subDim.worldName is the *target* world name; look up by source world in the instruction list.
            // The instruction with that target world will have the source world we need.
            // Since we index by source world, find the instruction whose target matches subDim.worldName.
            return resetLock.resetInstructions.firstOrNull { it.targetWorld == subDim.worldName }
        }

        // Fallback: legacy lock files without subDimensions metadata.
        // Conventionally, the Nether source world is "<overworld>_nether" and End is "<overworld>_the_end".
        val dimSuffix = when (environment) {
            Environment.NETHER -> "_nether"
            Environment.THE_END -> "_the_end"
            else -> return null
        }
        return resetInstructionBySourceWorld[overworldInstruction.sourceWorld + dimSuffix]
    }

    private fun updateDimensionTag(rootTag: CompoundTag, playerDimension: String, targetWorldName: String) {
        when (playerDimension) {
            // Main-world dimensions are stable identifiers — they don't change when the
            // main world is replaced, so we leave them as-is.
            "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end" -> return

            // Non-main world: update to the new target world name.
            else -> rootTag.putString("Dimension", "minecraft:$targetWorldName")
        }
    }

    fun getOfflineChunk(rootTag: CompoundTag): Pair<Int, Int>? {
        val posTag = rootTag.getList("Pos")
        if (posTag.isPresent) {
            val pos = posTag.get()
            val posXTag = pos.getDouble(0)
            val posZTag = pos.getDouble(2)
            if (posXTag.isEmpty) {
                return null
            } else if (posZTag.isEmpty) {
                return null
            } else {
                val chunkX = posXTag.get().toInt() shr 4
                val chunkZ = posZTag.get().toInt() shr 4
                return Pair(chunkX, chunkZ)
            }
        } else {
            // No position data — reset them to be safe.
            return null
        }
    }

    fun handlePlayerOutsideRetainedChunk(resetInstructions: WorldResetLoadInstruction, rootTag: CompoundTag) {
        var bukkitValuesTag = rootTag.getCompound("BukkitValues")
        if (bukkitValuesTag.isEmpty) {
            bukkitValuesTag = Optional.of(CompoundTag())
        }
        val bukkitValues = bukkitValuesTag.get()

        bukkitValues.putString(
            plugin.keys.playerResetDisposition.asString(),
            (resetInstructions.outsidePlayerBehavior
                ?: ResetParameters.OutsidePlayerBehavior.SPAWN).name.lowercase()
        )

        rootTag.put("BukkitValues", bukkitValues)
    }

}