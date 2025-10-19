package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.data.ResetData
import net.chlod.minecraft.homerun.offline.ChunkTransferUtil
import net.chlod.minecraft.homerun.offline.WorldDataTransferUtil
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable

/**
 * I hope you get the joke.
 */
class WorldPostpareTask(val plugin: Plugin, val resetData: ResetData): BukkitRunnable() {

    val componentLogger = plugin.componentLogger

    override fun run() {
        componentLogger.info("Running chunk transplant...")
        ChunkTransferUtil(resetData).transferChunks()
        componentLogger.info("Finished chunk transplant")

        componentLogger.info("Copying player data, stats, and advancements...")
        WorldDataTransferUtil(resetData).transferData()
        componentLogger.info("Finished copying player data, stats, and advancements")
    }

}