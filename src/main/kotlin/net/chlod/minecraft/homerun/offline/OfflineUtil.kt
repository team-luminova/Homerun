package net.chlod.minecraft.homerun.offline

import net.chlod.minecraft.homerun.data.ResetData
import net.querz.mcaselector.io.WorldDirectories
import java.io.File

abstract class OfflineUtil {

    protected val resetData: ResetData

    protected var sourceWorld: WorldDirectories
    protected var targetWorld: WorldDirectories

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

}