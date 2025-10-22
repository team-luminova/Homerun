package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.data.ResetLock
import net.chlod.minecraft.homerun.data.WorldResetData
import net.chlod.minecraft.homerun.offline.ChunkTransferUtil
import net.chlod.minecraft.homerun.offline.WorldDataTransferUtil
import org.bukkit.scheduler.BukkitRunnable
import java.io.File

class ResetPreloadTask(val plugin: Homerun, val resetLock: ResetLock): BukkitRunnable() {

    val componentLogger = plugin.componentLogger

    override fun run() {
        for (worldResetData in resetLock.worldResetData) {
            componentLogger.info("Running postload for world reset from ${worldResetData.sourceWorld} to ${worldResetData.targetWorld}...")
            when (worldResetData.behavior) {
                WorldResetData.WorldResetBehavior.NORMAL -> {
                    componentLogger.info("Running chunk transplant...")
                    ChunkTransferUtil(plugin, worldResetData).transferChunks()
                    componentLogger.info("Finished chunk transplant")

                    componentLogger.info("Copying player data, stats, and advancements...")
                    WorldDataTransferUtil(plugin, worldResetData).transferData()
                    componentLogger.info("Finished copying player data, stats, and advancements")
                }
                WorldResetData.WorldResetBehavior.COPY,
                WorldResetData.WorldResetBehavior.RENAME -> {
                    val serverFolder = plugin.server.worldContainer
                    val sourceWorldFolder = File(serverFolder, worldResetData.sourceWorld)
                    val targetWorldFolder = File(serverFolder, worldResetData.targetWorld)

                    if (!sourceWorldFolder.exists()) {
                        componentLogger.error("Source world folder ${sourceWorldFolder.path} does not exist, cannot copy world!")
                        continue
                    }
                    if (!targetWorldFolder.exists()) {
                        targetWorldFolder.mkdirs()
                    }

                    componentLogger.info("Copying world data from ${sourceWorldFolder.path} to ${targetWorldFolder.path}...")
                    if (worldResetData.behavior == WorldResetData.WorldResetBehavior.COPY) {
                        sourceWorldFolder.copyRecursively(targetWorldFolder, overwrite = true)
                    } else {
                        sourceWorldFolder.renameTo(targetWorldFolder)
                    }
                }
            }
        }
    }

}