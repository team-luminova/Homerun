package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.data.ResetData
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import org.bukkit.scheduler.BukkitRunnable
import java.io.File
import kotlin.io.path.Path

class WorldPostloadTask(val plugin: Homerun, val resetData: ResetData): BukkitRunnable() {

    val componentLogger = plugin.componentLogger

    override fun run() {
        val newWorld = plugin.server.getWorld(resetData.targetWorld)

        if (newWorld == null) {
            componentLogger.error("Target world ${resetData.targetWorld} not found!")
            componentLogger.error("Players may spawn in wrong locations or people may spawn in weird places.")
            return
        }

        componentLogger.info("Setting spawn point for world ${resetData.targetWorld}")
        val spawnX = resetData.spawnLocation[0].toInt()
        val spawnY = resetData.spawnLocation[1].toInt()
        val spawnZ = resetData.spawnLocation[2].toInt()
        val spawnYaw = resetData.spawnLocation[3].toFloat()
        newWorld.setSpawnLocation(spawnX, spawnY, spawnZ, spawnYaw)
        componentLogger.info("Finished setting spawn point for world ${resetData.targetWorld}")

        componentLogger.info("Checking player data...")
        val playersFolder = File(newWorld.worldFolder, "playerdata")
        val playerFiles = playersFolder.listFiles { file -> !file.extension.endsWith("_old") }

        for (playerFile in playerFiles!!) {
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

            if (!resetData.chunks.contains(Pair(chunkX, chunkZ))) {
                componentLogger.info("Player data '$playerName' is outside retained chunks, marking as needs respawn...")
                markNeedsRespawn(rootTag)
            }

            NbtIo.writeCompressed(rootTag, Path(playerFile.path))
        }
    }

    fun markNeedsRespawn(rootTag: CompoundTag) {
        var bukkitValues = if (rootTag.contains("BukkitValues"))
            rootTag.getCompound("BukkitValues") else CompoundTag()

        bukkitValues.putBoolean(plugin.KEY_NEEDS_RESPAWN.asString(), true)
        rootTag.put("BukkitValues", bukkitValues)
    }

}