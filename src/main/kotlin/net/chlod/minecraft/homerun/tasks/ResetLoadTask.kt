package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.data.ResetLock
import net.chlod.minecraft.homerun.data.world.ResetLoadInstructions
import net.chlod.minecraft.homerun.data.world.WorldResetLoadInstruction
import net.chlod.minecraft.homerun.online.NMSChunkTransferUtil
import org.bukkit.scheduler.BukkitRunnable
import java.io.File

class ResetLoadTask(val plugin: Homerun, val resetLock: ResetLock) : BukkitRunnable() {

    val componentLogger = plugin.componentLogger

    override fun run() {
        for (resetInstructions in resetLock.resetInstructions) {
            componentLogger.info("Running preload for world reset from ${resetInstructions.sourceWorld} to ${resetInstructions.targetWorld}...")
            when (resetInstructions.type) {
                ResetLoadInstructions.ResetLoadInstructionType.RESET -> {
                    val transferUtil =
                        NMSChunkTransferUtil(plugin, resetInstructions as WorldResetLoadInstruction, true)

                    componentLogger.info("Running chunk transplant...")
                    transferUtil.transferChunks()
                    componentLogger.info("Finished chunk transplant")

                    componentLogger.info("Copying level data, player data, stats, and advancements...")
                    transferUtil.transferData()
                    componentLogger.info("Finished level data, copying player data, stats, and advancements")
                }

                ResetLoadInstructions.ResetLoadInstructionType.COPY,
                ResetLoadInstructions.ResetLoadInstructionType.RENAME -> {
                    val serverFolder = plugin.server.worldContainer
                    val sourceWorldFolder = File(serverFolder, resetInstructions.sourceWorld)
                    val targetWorldFolder = File(serverFolder, resetInstructions.targetWorld)

                    if (!sourceWorldFolder.exists()) {
                        componentLogger.error("Source world folder ${sourceWorldFolder.path} does not exist, cannot copy world!")
                        continue
                    }
                    if (!targetWorldFolder.exists()) {
                        targetWorldFolder.mkdirs()
                    }

                    componentLogger.info("Copying world data from ${sourceWorldFolder.path} to ${targetWorldFolder.path}...")
                    if (resetInstructions.type == ResetLoadInstructions.ResetLoadInstructionType.COPY) {
                        sourceWorldFolder.copyRecursively(targetWorldFolder, overwrite = true)
                    } else {
                        sourceWorldFolder.renameTo(targetWorldFolder)
                    }
                }
            }
        }
    }

}