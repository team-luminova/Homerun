package net.chlod.minecraft.homerun.data

import net.chlod.minecraft.homerun.data.world.ResetLoadInstructions
import net.chlod.minecraft.homerun.data.world.ResetLoadInstructions.ResetLoadInstructionType
import net.chlod.minecraft.homerun.data.world.WorldCopyLoadInstruction
import net.chlod.minecraft.homerun.data.world.WorldRenameLoadInstruction
import net.chlod.minecraft.homerun.data.world.WorldResetLoadInstruction
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.util.*

class ResetLock(
    val plugin: Plugin,
    val id: String,
    val time: Long,
    val resetInstructions: List<ResetLoadInstructions>
) {

    companion object {
        fun findAll(plugin: Plugin): List<ResetLock> {
            val locks = mutableListOf<ResetLock>()

            val lockFolder = File(plugin.dataFolder, "lock")
            if (!lockFolder.exists()) {
                return locks
            }

            for (file in lockFolder.listFiles()!!) {
                if (!file.name.endsWith(".lock")) {
                    continue
                }

                val config = YamlConfiguration.loadConfiguration(file)
                try {
                    val id = file.name.removeSuffix(".lock")
                    val time = config.getLong("time")
                    val resetInstructionsListRaw = config.getList("worlds")
                        ?: throw IllegalArgumentException("Missing worlds data for reset lock ${file.name.removeSuffix(".lock")}")

                    @Suppress("UNCHECKED_CAST")
                    val resetInstructionsList =
                        if (resetInstructionsListRaw.all { it is ResetLoadInstructions })
                            resetInstructionsListRaw as List<ResetLoadInstructions>
                        else {
                            resetInstructionsListRaw.map {
                                if (it is Map<*, *> && it.keys.all { key -> key is String }) {
                                    val instructionType = (it["type"] as String).uppercase()
                                    when (ResetLoadInstructionType.valueOf(instructionType)) {
                                        ResetLoadInstructionType.RESET ->
                                            WorldResetLoadInstruction.deserialize(it as Map<String, Object>)

                                        ResetLoadInstructionType.COPY ->
                                            WorldCopyLoadInstruction.deserialize(it as Map<String, Object>)

                                        ResetLoadInstructionType.RENAME ->
                                            WorldRenameLoadInstruction.deserialize(it as Map<String, Object>)
                                    }
                                } else {
                                    throw IllegalArgumentException(
                                        "Invalid world reset data #${
                                            resetInstructionsListRaw.indexOf(
                                                it
                                            ) + 1
                                        }"
                                    )
                                }
                            }
                        }

                    @Suppress("UNCHECKED_CAST")
                    val resetLock = ResetLock(
                        plugin,
                        id,
                        time,
                        resetInstructionsList
                    )
                    locks.add(resetLock)
                } catch (_: Exception) {
                    plugin.componentLogger.warn("Failed to load reset lock from $file")
                }
            }
            return locks
        }

        fun create(
            plugin: Plugin,
            resetInstructions: List<ResetLoadInstructions>
        ): ResetLock {
            val time = System.currentTimeMillis()
            return ResetLock(plugin, UUID.randomUUID().toString(), time, resetInstructions)
        }
    }

    fun save() {
        val lockFolder = File(plugin.dataFolder, "lock")
        if (!lockFolder.exists()) {
            lockFolder.mkdirs()
        }

        val resetLock = File(lockFolder, "$id.lock")
        val config = YamlConfiguration()
        config.set("time", time)
        config.set("worlds", resetInstructions)
        config.save(resetLock)
    }

    fun delete() {
        val lockFolder = File(plugin.dataFolder, "lock")
        if (!lockFolder.exists()) {
            return
        }

        val resetLock = File(lockFolder, "$id.lock")
        if (resetLock.exists()) {
            resetLock.delete()
        }
    }

}