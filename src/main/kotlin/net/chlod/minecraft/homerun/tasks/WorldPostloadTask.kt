package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetParameters
import net.chlod.minecraft.homerun.data.ResetLock
import net.chlod.minecraft.homerun.data.world.WorldResetLoadInstruction
import net.chlod.minecraft.homerun.helpers.EndPillarCleanup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import org.bukkit.World
import org.bukkit.World.Environment
import org.bukkit.scheduler.BukkitRunnable
import java.io.File
import java.util.*
import kotlin.io.path.Path

class WorldPostloadTask(val plugin: Homerun, val resetLock: ResetLock) : BukkitRunnable() {

    val componentLogger = plugin.componentLogger
    val resetInstructionByWorld = resetLock.resetInstructions.associateBy { it.targetWorld }

    override fun run() {
        for (resetInstructions in resetLock.resetInstructions) {
            if (resetInstructions !is WorldResetLoadInstruction) {
                return
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
            val playerName = if (rootTag.contains("bukkit") && rootTag.getCompound("bukkit").contains("lastKnownName"))
                rootTag.getCompound("bukkit").getString("lastKnownName")
            else playerFile.nameWithoutExtension

            // Fix the world UUID, or else they'll be teleported to world spawn
            val playerDimension = if (rootTag.contains("Dimension"))
                rootTag.getString("Dimension")
            else "minecraft:overworld"
            val playerEnvironment = when (playerDimension) {
                "minecraft:overworld" -> Environment.NORMAL
                "minecraft:the_nether" -> Environment.NETHER
                "minecraft:the_end" -> Environment.THE_END
                else -> {
                    componentLogger.warn("Player data '$playerName' has unrecognized dimension '$playerDimension'. Forcing reset...")
                    Environment.NORMAL
                }
            }
            val newWorldDim = when (playerEnvironment) {
                Environment.NETHER -> newWorld.name + "_nether"
                Environment.THE_END -> newWorld.name + "_the_end"
                else -> newWorld
            }
            val dimResetInstructions = resetInstructionByWorld[newWorldDim]
            if (dimResetInstructions != null && dimResetInstructions is WorldResetLoadInstruction) {
                // The world they're in is being reset too. This means that the world UUID will be changing,
                // so we need to update it in the player data.
                val dimUUID = UUID.fromString(dimResetInstructions.targetWorldUUID)
                rootTag.putLong("WorldUUIDLeast", dimUUID.leastSignificantBits)
                rootTag.putLong("WorldUUIDMost", dimUUID.mostSignificantBits)
            }

            // Check if the player is currently outside a retained chunk
            val posTag = rootTag.getList("Pos", Tag.TAG_DOUBLE.toInt())
            val posX = posTag.getDouble(0)
            val posZ = posTag.getDouble(2)
            val chunkX = posX.toInt() shr 4
            val chunkZ = posZ.toInt() shr 4

            if (dimResetInstructions != null) {
                if (dimResetInstructions is WorldResetLoadInstruction) {
                    if (!dimResetInstructions.chunks!!.contains(Pair(chunkX, chunkZ))) {
                        componentLogger.info("Player data '$playerName' is outside retained chunks, handling...")
                        handlePlayerOutsideRetainedChunk(resetInstructions, rootTag)
                    }
                } else {
                    // Rename or copy. This is automatically a kept chunk.
                }
            } else {
                // This user is in a world that's not being reset. We're skipping them, just in case they're in a world
                // that's not managed by us.
            }

            NbtIo.writeCompressed(rootTag, Path(playerFile.path))
        }
    }

    fun handlePlayerOutsideRetainedChunk(resetInstructions: WorldResetLoadInstruction, rootTag: CompoundTag) {
        val bukkitValues = if (rootTag.contains("BukkitValues"))
            rootTag.getCompound("BukkitValues") else CompoundTag()

        bukkitValues.putString(
            plugin.keys.playerResetDisposition.asString(),
            (resetInstructions.outsidePlayerBehavior
                ?: ResetParameters.OutsidePlayerBehavior.SPAWN).name.lowercase()
        )

        rootTag.put("BukkitValues", bukkitValues)
    }

}