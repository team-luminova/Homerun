package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetParameters
import net.chlod.minecraft.homerun.data.ResetLock
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
            val bukkitTag = rootTag.getCompound("bukkit")
            val lastKnownNameTag = if (bukkitTag.isPresent)
                rootTag.getCompound("bukkit").get().getString("lastKnownName")
            else null
            val playerName = if (lastKnownNameTag?.isPresent == true)
                lastKnownNameTag.get()
            else playerFile.nameWithoutExtension

            // Fix the world UUID, or else they'll be teleported to world spawn
            rootTag.putLong("WorldUUIDLeast", newWorld.uid.leastSignificantBits)
            rootTag.putLong("WorldUUIDMost", newWorld.uid.mostSignificantBits)

            // Check if the player is currently outside a retained chunk
            val posTag = rootTag.getList("Pos")
            var willReset = false
            if (posTag.isEmpty) {
                val pos = posTag.get()
                val posXTag = pos.getDouble(0)
                val posZTag = pos.getDouble(2)
                if (posXTag.isEmpty || posZTag.isEmpty) {
                    // Uhh... they're... somewhere????????
                    // What?
                    willReset = true
                } else {
                    val chunkX = posXTag.get().toInt() shr 4
                    val chunkZ = posZTag.get().toInt() shr 4
                    willReset = !resetInstructions.chunks!!.contains(Pair(chunkX, chunkZ))
                }
            } else {
                // Uhh... they're... nowhere?
                // Reset them. Just in case...
                willReset = true
            }

            if (willReset) {
                componentLogger.info("Player data '$playerName' is outside retained chunks, handling...")
                handlePlayerOutsideRetainedChunk(resetInstructions, rootTag)
            }

            NbtIo.writeCompressed(rootTag, Path(playerFile.path))
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