package net.chlod.minecraft.homerun.offline

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.data.world.WorldResetLoadInstruction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import org.apache.commons.io.FileUtils
import java.io.File

class WorldDataTransferUtil(plugin: Homerun, resetInstructions: WorldResetLoadInstruction) :
    OfflineUtil(plugin, resetInstructions) {

    val sourceWorldDirectory: String = sourceWorld.region.parent!!
    val targetWorldDirectory: String = targetWorld.region.parent!!

    fun transferData() {
        copyDataFolders()
        copyGameRules()
    }

    fun copyDatapacks() {
        // Transferring datapacks
        val sourceDatapacks = File(sourceWorldDirectory, "datapacks")
        if (sourceDatapacks.exists()) {
            FileUtils.copyDirectory(
                sourceDatapacks,
                File(targetWorldDirectory, "datapacks")
            )
        } else {
            plugin.componentLogger.warn("Source datapacks folder does not exist, skipping datapacks transfer.")
        }
    }

    fun copyDataFolders() {
        // Transferring playerdata
        val sourcePlayerData = File(sourceWorldDirectory, "playerdata")
        if (sourcePlayerData.exists()) {
            FileUtils.copyDirectory(
                sourcePlayerData,
                File(targetWorldDirectory, "playerdata")
            )
        } else {
            plugin.componentLogger.warn("Source playerdata folder does not exist, skipping playerdata transfer.")
        }

        // Transferring stats
        val sourceStats = File(sourceWorldDirectory, "stats")
        if (sourceStats.exists()) {
            FileUtils.copyDirectory(
                sourceStats,
                File(targetWorldDirectory, "stats")
            )
        } else {
            plugin.componentLogger.warn("Source stats folder does not exist, skipping stats transfer.")
        }

        // Transferring advancements
        val sourceAdvancements = File(sourceWorldDirectory, "advancements")
        if (sourceAdvancements.exists()) {
            FileUtils.copyDirectory(
                sourceAdvancements,
                File(targetWorldDirectory, "advancements")
            )
        } else {
            plugin.componentLogger.warn("Source advancements folder does not exist, skipping advancements transfer.")
        }

        // Transferring data
        val sourceData = File(sourceWorldDirectory, "data")
        if (sourceData.exists()) {
            FileUtils.copyDirectory(
                sourceData,
                File(targetWorldDirectory, "data")
            )
        } else {
            plugin.componentLogger.warn("Source data folder does not exist, skipping data transfer.")
        }
    }

    private fun findLevelDat(worldDirectory: String): java.nio.file.Path {
        val currentLevel = File(worldDirectory, "level.dat")
        if (currentLevel.exists()) {
            return currentLevel.toPath()
        }

        val parentLevel = File(worldDirectory).parentFile?.let { File(it, "level.dat") }
        if (parentLevel?.exists() == true) {
            return parentLevel.toPath()
        }

        throw IllegalStateException("level.dat not found in $worldDirectory or its parent directory")
    }

    private fun copyNbtTag(
        sourceCompoundTag: CompoundTag,
        targetCompoundTag: CompoundTag,
        tagName: String,
        required: Boolean = false
    ) {
        if (sourceCompoundTag.contains(tagName)) {
            val tag = sourceCompoundTag.get(tagName)!!
            targetCompoundTag.put(tagName, tag)
        } else {
            if (required) {
                plugin.componentLogger.error("Source level.dat $tagName tag could not be found. Some data won't be transferred!")
            } else {
                plugin.componentLogger.warn("Source level.dat $tagName tag could not be found. Skipping $tagName transfer.")
            }
        }
    }

    fun copyGameRules() {
        val sourceLevelDat = findLevelDat(sourceWorldDirectory)
        val targetLevelDat = findLevelDat(targetWorldDirectory)

        val sourceRootTag = NbtIo.readCompressed(sourceLevelDat, NbtAccounter.unlimitedHeap())
        val sourceDataTag = sourceRootTag.getCompound("Data")

        if (sourceDataTag.isEmpty) {
            plugin.componentLogger.error("Source level.dat data tag could not be found. Some data won't be transferred!")
            return
        }
        val sourceDataTagValue = sourceDataTag.get()

        val targetRootTag = NbtIo.readCompressed(targetLevelDat, NbtAccounter.unlimitedHeap())
        val targetDataTag = targetRootTag.getCompound("Data")
        if (targetDataTag.isEmpty) {
            plugin.componentLogger.error("Target level.dat data tag could not be found in target world. Some data won't be transferred!")
            return
        }
        val targetDataTagValue = targetDataTag.get()

        val requiredTags = listOf(
            "GameRules",
            "Difficulty",
            "hardcore",
            "GameType",
            "SpawnX",
            "SpawnY",
            "SpawnZ",
            "SpawnAngle"
        )

        val extraTags = listOf(
            "Time",
            "DayTime",
            "BorderSizeLerpTime",
            "BorderCenterX",
            "BorderCenterZ",
            "BorderWarningBlocks",
            "BorderDamagePerBlock",
            "raining",
            "rainTime",
            "thunderTime",
            "thundering",
            "clearWeatherTime",
            "BorderSafeZone",
            "DragonFight"
        )

        for (tag in requiredTags) {
            copyNbtTag(
                sourceDataTagValue,
                targetDataTagValue,
                tag,
                true
            )
        }
        for (tag in extraTags) {
            copyNbtTag(
                sourceDataTagValue,
                targetDataTagValue,
                tag
            )
        }

        targetRootTag.put("Data", targetDataTagValue)

        NbtIo.writeCompressed(targetRootTag, targetLevelDat)
    }

}