package net.chlod.minecraft.homerun.offline

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.data.world.WorldResetLoadInstruction
import net.querz.mcaselector.changer.fields.ForceBlendField
import net.querz.mcaselector.config.ConfigProvider
import net.querz.mcaselector.config.WorldConfig
import net.querz.mcaselector.io.JobHandler
import net.querz.mcaselector.io.RegionDirectories
import net.querz.mcaselector.io.WorldDirectories
import net.querz.mcaselector.io.job.ChunkImporter
import net.querz.mcaselector.io.job.FieldChanger
import net.querz.mcaselector.io.job.SelectionDeleter
import net.querz.mcaselector.selection.Selection
import net.querz.mcaselector.util.point.Point2i
import net.querz.mcaselector.util.point.Point3i
import net.querz.mcaselector.util.progress.Progress
import net.querz.mcaselector.util.property.DataProperty
import org.apache.commons.io.FileUtils
import org.bukkit.plugin.Plugin
import java.io.File

class ChunkTransferUtil(plugin: Homerun, resetInstructions: WorldResetLoadInstruction) :
    OfflineUtil(plugin, resetInstructions) {

    fun transferChunks() {
        // Instantiate a very bare-bones MCASelector environment
        MCASelectorVersionImplLoader.init()

        val logFolder = File(plugin.dataFolder, "logs")
        logFolder.mkdirs()

        ConfigProvider.GLOBAL.debug = true
        JobHandler.setTrimSaveData(false)

        ConfigProvider.WORLD = WorldConfig()
        ConfigProvider.WORLD.setWorldDirs(targetWorld)
        val source: WorldDirectories = sourceWorld
        val selection = Selection()

        for (chunk in this.resetInstructions.chunks!!) {
            selection.addChunk(Point2i(chunk.first, chunk.second))
        }

        importChunks(source, selection)
        while (JobHandler.getActiveJobs() > 0) {
            Thread.onSpinWait()
        }
        // backupWorld("_1")
        forceDeleteOldChunks()
        while (JobHandler.getActiveJobs() > 0) {
            Thread.onSpinWait()
        }
        // backupWorld("_2")
        forceBlendNewChunks(selection)
        while (JobHandler.getActiveJobs() > 0) {
            Thread.onSpinWait()
        }
        // backupWorld("_3")
    }

    /**
     * World backup function for debugging purposes.
     */
    fun backupWorld(suffix: String) {
        File(plugin.server.worldContainer, this.resetInstructions.targetWorld + suffix).mkdirs()
        FileUtils.copyDirectory(
            targetWorld.region.parentFile,
            File(plugin.server.worldContainer, this.resetInstructions.targetWorld + suffix)
        ) { pathname -> !pathname.name.endsWith("session.lock") }
    }

    fun forceDeleteOldChunks() {
        val progress = PluginProgress(plugin, "deleting old chunks")
        val selection = Selection()

        for (chunk in this.resetInstructions.chunks!!) {
            selection.addChunk(Point2i(chunk.first, chunk.second))
        }
        selection.isInverted = true

        SelectionDeleter.deleteSelection(
            selection,
            progress
        )
    }

    fun importChunks(source: WorldDirectories, selection: Selection) {
        val progress = PluginProgress(plugin, "importing chunks")
        val tempFiles = DataProperty<MutableMap<Point2i?, RegionDirectories>?>()
        ChunkImporter.importChunks(
            source,
            progress,
            true,
            true,
            null,
            selection,
            null,
            Point3i(0, 0, 0),
            tempFiles
        )
        if (tempFiles.get() != null) {
            for (tempFile in tempFiles.get()!!.values) {
                if (!tempFile.region.delete()) {
                    plugin.componentLogger.warn("failed to delete temp file {}", tempFile.region)
                }
                if (!tempFile.poi.delete()) {
                    plugin.componentLogger.warn("failed to delete temp file {}", tempFile.poi)
                }
                if (!tempFile.entities.delete()) {
                    plugin.componentLogger.warn("failed to delete temp file {}", tempFile.entities)
                }
            }
        }
    }

    fun forceBlendNewChunks(selection: Selection) {
        val progress = PluginProgress(plugin, "forcing blending on all chunks")
        val forceBlendField = ForceBlendField()
        forceBlendField.newValue = true
        FieldChanger.changeNBTFields(
            listOf(forceBlendField),
            true,
            selection,
            progress,
            true
        )
    }

    class PluginProgress(val plugin: Plugin, val prefix: String) : Progress {
        private var max: Int = 1
        private var msg: String? = null
        private var progress: Int = 0

        private fun printProgress() {
            val percent = (progress.toDouble() / max.toDouble()) * 100.0
            plugin.componentLogger.info(
                "Progress (%s): %.2f%% (%d/%d) - %s".format(
                    prefix,
                    percent,
                    progress,
                    max,
                    msg ?: ""
                )
            )
        }

        override fun setMax(max: Int) {
            this.max = max
        }

        override fun updateProgress(msg: String?, progress: Int) {
            this.progress = progress
            this.msg = msg
            printProgress()
        }

        override fun done(msg: String?) {
            plugin.componentLogger.info("Done: %s".format(msg ?: ""))
        }

        override fun taskCancelled(): Boolean {
            return false
        }

        override fun cancelTask() {
            // No-op
        }

        override fun incrementProgress(msg: String?) {
            progress++
            this.msg = msg
            printProgress()
        }

        override fun incrementProgress(msg: String?, progress: Int) {
            this.progress += progress
            this.msg = msg
            printProgress()
        }

        override fun setMessage(msg: String?) {
            this.msg = msg
            printProgress()
        }
    }

}