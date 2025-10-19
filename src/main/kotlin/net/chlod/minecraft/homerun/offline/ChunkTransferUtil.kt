package net.chlod.minecraft.homerun.offline

import net.chlod.minecraft.homerun.data.ResetData
import net.querz.mcaselector.changer.fields.ForceBlendField
import net.querz.mcaselector.config.ConfigProvider
import net.querz.mcaselector.config.WorldConfig
import net.querz.mcaselector.io.JobHandler
import net.querz.mcaselector.io.RegionDirectories
import net.querz.mcaselector.io.WorldDirectories
import net.querz.mcaselector.io.job.ChunkImporter
import net.querz.mcaselector.io.job.FieldChanger
import net.querz.mcaselector.io.job.SelectionDeleter
import net.querz.mcaselector.logging.Logging
import net.querz.mcaselector.selection.Selection
import net.querz.mcaselector.util.point.Point2i
import net.querz.mcaselector.util.point.Point3i
import net.querz.mcaselector.util.progress.Progress
import net.querz.mcaselector.util.property.DataProperty
import net.querz.mcaselector.util.range.Range
import java.io.File
import kotlin.math.log

class ChunkTransferUtil {

    private val resetData: ResetData

    private var sourceWorld: WorldDirectories
    private var targetWorld: WorldDirectories

    constructor(resetData: ResetData) {
        this.resetData = resetData
        val serverDirectory = resetData.plugin.server.pluginsFolder.parent
        val sourceWorldDirectory = File(serverDirectory, resetData.sourceWorld)
        val targetWorldDirectory = File(serverDirectory, resetData.targetWorld)

        val sourceRegion = File(sourceWorldDirectory, "region")
        val sourcePoi = File(sourceWorldDirectory, "poi")
        val sourceEntities = File(sourceWorldDirectory, "entities")

        verifyWorldDirectories(sourceRegion, sourcePoi, sourceEntities)
        sourceWorld = WorldDirectories(sourceRegion, sourcePoi, sourceEntities)

        val targetRegion = File(targetWorldDirectory, "region")
        val targetPoi = File(targetWorldDirectory, "poi")
        val targetEntities = File(targetWorldDirectory, "entities")

        verifyWorldDirectories(targetRegion, targetPoi, targetEntities)
        targetWorld = WorldDirectories(targetRegion, targetPoi, targetEntities)
    }

    private fun verifyWorldDirectories(region: File, poi: File, entities: File) {
        verifyWorldDirectory(region)
        verifyWorldDirectory(poi)
        verifyWorldDirectory(entities)
    }

    private fun verifyWorldDirectory(worldDirectory: File) {
        if (worldDirectory.exists() && !worldDirectory.isDirectory) {
            throw IllegalArgumentException("Provided world directory ${worldDirectory.path} is not a directory")
        }
        if (!worldDirectory.exists() && !worldDirectory.mkdirs()) {
            throw IllegalArgumentException("Provided world directory ${worldDirectory.path} could not be created")
        }
    }

    fun transferChunks() {
        // Instantiate a very bare-bones MCASelector environment
        MCASelectorVersionImplLoader.init()

        val logFolder = File(resetData.plugin.dataFolder, "logs")
        logFolder.mkdirs()

        ConfigProvider.GLOBAL.debug = true
        JobHandler.setTrimSaveData(false)

        ConfigProvider.WORLD = WorldConfig()
        ConfigProvider.WORLD.setWorldDirs(targetWorld)
        val source: WorldDirectories = sourceWorld
        val selection = Selection()

        for (chunk in resetData.chunks) {
            selection.addChunk(Point2i(chunk.first, chunk.second))
        }

        importChunks(source, selection)
        while (JobHandler.getActiveJobs() > 0) {
            Thread.onSpinWait()
        }
        forceDeleteOldChunks()
        while (JobHandler.getActiveJobs() > 0) {
            Thread.onSpinWait()
        }
        forceBlendNewChunks(selection)
        while (JobHandler.getActiveJobs() > 0) {
            Thread.onSpinWait()
        }
        resetData.plugin.componentLogger.info("Waiting for verification...")
        Thread.sleep(60000)
    }

    fun forceDeleteOldChunks() {
        val progress = PluginProgress(resetData, "deleting old chunks")
        val selection = Selection()

        for (chunk in resetData.chunks) {
            selection.addChunk(Point2i(chunk.first, chunk.second))
        }
        selection.isInverted = true

        SelectionDeleter.deleteSelection(
            selection,
            progress
        )
    }

    fun importChunks(source: WorldDirectories, selection: Selection) {
        val progress = PluginProgress(resetData, "importing chunks")
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
                    resetData.plugin.componentLogger.warn("failed to delete temp file {}", tempFile.region)
                }
                if (!tempFile.poi.delete()) {
                    resetData.plugin.componentLogger.warn("failed to delete temp file {}", tempFile.poi)
                }
                if (!tempFile.entities.delete()) {
                    resetData.plugin.componentLogger.warn("failed to delete temp file {}", tempFile.entities)
                }
            }
        }
    }

    fun forceBlendNewChunks(selection: Selection) {
        val progress = PluginProgress(resetData, "forcing blending on all chunks")
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

    class PluginProgress(val resetData: ResetData, val prefix: String) : Progress {
        private var max: Int = 1
        private var msg: String? = null
        private var progress: Int = 0

        private fun printProgress() {
            val percent = (progress.toDouble() / max.toDouble()) * 100.0
            resetData.plugin.componentLogger.info("Progress (%s): %.2f%% (%d/%d) - %s".format(
                prefix,
                percent,
                progress,
                max,
                msg ?: ""
            ))
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
            resetData.plugin.componentLogger.info("Done: %s".format(msg ?: ""))
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