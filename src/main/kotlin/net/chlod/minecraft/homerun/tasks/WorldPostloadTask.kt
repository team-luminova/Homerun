package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetParameters
import net.chlod.minecraft.homerun.data.ResetLock
import net.chlod.minecraft.homerun.data.WorldResetData
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import org.bukkit.scheduler.BukkitRunnable
import java.io.File
import kotlin.io.path.Path

class WorldPostloadTask(val plugin: Homerun, val resetLock: ResetLock): BukkitRunnable() {

    val componentLogger = plugin.componentLogger

    override fun run() {
        for (worldResetData in resetLock.worldResetData) {
            componentLogger.info("Running postload for world reset from ${worldResetData.sourceWorld} to ${worldResetData.targetWorld}...")
            processWorld(worldResetData)
        }
    }

    fun processWorld(worldResetData: WorldResetData) {
        val newWorld = plugin.server.getWorld(worldResetData.targetWorld)

        if (newWorld == null) {
            componentLogger.error("Target world ${worldResetData.targetWorld} not found!")
            componentLogger.error("Players may spawn in wrong locations or people may spawn in weird places.")
            return
        }

        componentLogger.info("Setting spawn point for world ${worldResetData.targetWorld}")
        val spawnX = worldResetData.spawnLocation!!.x.toInt()
        val spawnY = worldResetData.spawnLocation.y.toInt()
        val spawnZ = worldResetData.spawnLocation.z.toInt()
        val spawnYaw = worldResetData.spawnLocation.yaw
        newWorld.setSpawnLocation(spawnX, spawnY, spawnZ, spawnYaw)
        componentLogger.info("Finished setting spawn point for world ${worldResetData.targetWorld}")

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

            if (!worldResetData.chunks!!.contains(Pair(chunkX, chunkZ))) {
                componentLogger.info("Player data '$playerName' is outside retained chunks, handling...")
                handlePlayerOutsideRetainedChunk(worldResetData, rootTag)
            }

            NbtIo.writeCompressed(rootTag, Path(playerFile.path))
        }
    }

    fun handlePlayerOutsideRetainedChunk(worldResetData: WorldResetData, rootTag: CompoundTag) {
        val bukkitValues = if (rootTag.contains("BukkitValues"))
            rootTag.getCompound("BukkitValues") else CompoundTag()

        val outsideKey = when (worldResetData.outsidePlayerBehavior) {
            null, ResetParameters.OutsidePlayerBehavior.SPAWN -> plugin.keys.needsRespawn
            ResetParameters.OutsidePlayerBehavior.KILL -> plugin.keys.needsKill
            ResetParameters.OutsidePlayerBehavior.WORLD_SPAWN -> plugin.keys.needsTeleportToSpawn
            ResetParameters.OutsidePlayerBehavior.HIGHEST -> plugin.keys.needsTeleportToHighest
            ResetParameters.OutsidePlayerBehavior.CLOSEST -> plugin.keys.needsTeleportToClosest
            ResetParameters.OutsidePlayerBehavior.CLOSEST_RETAINED -> plugin.keys.needsTeleportToClosestRetained
            ResetParameters.OutsidePlayerBehavior.IGNORE -> null /* do nothing */
        }
        if (outsideKey != null) {
            bukkitValues.putBoolean(outsideKey.asString(), true)
        }
        rootTag.put("BukkitValues", bukkitValues)
    }

}