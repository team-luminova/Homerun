package net.chlod.minecraft.homerun.offline

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.data.world.WorldResetLoadInstruction
import org.apache.commons.io.FileUtils
import java.io.File

class WorldDataTransferUtil(plugin: Homerun, resetInstructions: WorldResetLoadInstruction) :
    OfflineUtil(plugin, resetInstructions) {

    val sourceWorldDirectory: String? = sourceWorld.region.parent
    val targetWorldDirectory: String? = targetWorld.region.parent

    fun transferData() {
        // Transferring playerdata
        val sourcePlayerData = File(sourceWorldDirectory, "playerdata")
        if (sourcePlayerData.exists()) {
            FileUtils.copyDirectory(
                sourcePlayerData,
                File(targetWorldDirectory, "playerdata")
            )
        }

        // Transferring stats
        val sourceStats = File(sourceWorldDirectory, "stats")
        if (sourceStats.exists()) {
            FileUtils.copyDirectory(
                sourceStats,
                File(targetWorldDirectory, "stats")
            )
        }

        // Transferring advancements
        val sourceAdvancements = File(sourceWorldDirectory, "advancements")
        if (sourceAdvancements.exists()) {
            FileUtils.copyDirectory(
                sourceAdvancements,
                File(targetWorldDirectory, "advancements")
            )
        }
    }

}