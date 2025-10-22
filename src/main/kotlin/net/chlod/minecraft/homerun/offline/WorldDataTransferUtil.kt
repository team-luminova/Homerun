package net.chlod.minecraft.homerun.offline

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.data.WorldResetData
import org.apache.commons.io.FileUtils
import java.io.File

class WorldDataTransferUtil(plugin: Homerun, worldResetData: WorldResetData) : OfflineUtil(plugin, worldResetData)  {

    val sourceWorldDirectory = sourceWorld.region.parent
    val targetWorldDirectory = targetWorld.region.parent

    fun transferData() {
        // Transferring playerdata
        FileUtils.copyDirectory(
            File(sourceWorldDirectory, "playerdata"),
            File(targetWorldDirectory, "playerdata")
        )

        // Transferring stats
        FileUtils.copyDirectory(
            File(sourceWorldDirectory, "stats"),
            File(targetWorldDirectory, "stats")
        )

        // Transferring advancements
        FileUtils.copyDirectory(
            File(sourceWorldDirectory, "advancements"),
            File(targetWorldDirectory, "advancements")
        )
    }

}