package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetParameters
import net.chlod.minecraft.homerun.data.ResetLock
import net.chlod.minecraft.homerun.data.world.WorldResetLoadInstruction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import org.bukkit.scheduler.BukkitRunnable
import java.io.File
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

        val spawnX = resetInstructions.spawnLocation!!.x.toInt()
        val spawnY = resetInstructions.spawnLocation.y.toInt()
        val spawnZ = resetInstructions.spawnLocation.z.toInt()
        componentLogger.info("Setting spawn point for world ${resetInstructions.targetWorld} (${spawnX}, ${spawnY}, ${spawnZ})...")
        val spawnYaw = resetInstructions.spawnLocation.yaw
        newWorld.setSpawnLocation(spawnX, spawnY, spawnZ, spawnYaw)
        componentLogger.info("Finished setting spawn point for world ${resetInstructions.targetWorld}")

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
            rootTag.putLong("WorldUUIDLeast", newWorld.uid.leastSignificantBits)
            rootTag.putLong("WorldUUIDMost", newWorld.uid.mostSignificantBits)

            // Check if the player is currently outside a retained chunk
            val posTag = rootTag.getList("Pos", Tag.TAG_DOUBLE.toInt())
            val posX = posTag.getDouble(0)
            val posZ = posTag.getDouble(2)
            val chunkX = posX.toInt() shr 4
            val chunkZ = posZ.toInt() shr 4

            if (!resetInstructions.chunks!!.contains(Pair(chunkX, chunkZ))) {
                componentLogger.info("Player data '$playerName' is outside retained chunks, handling...")
                handlePlayerOutsideRetainedChunk(resetInstructions, rootTag)
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