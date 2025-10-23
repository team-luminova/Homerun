package net.chlod.minecraft.homerun.data

import net.chlod.minecraft.homerun.data.world.ResetLoadInstructions
import net.chlod.minecraft.homerun.data.world.ResetLoadInstructions.ResetLoadInstructionType
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
                    val resetInstructionsList = config.getMapList("worlds")

                    @Suppress("UNCHECKED_CAST")
                    val resetLock = ResetLock(
                        plugin,
                        id,
                        time,
                        resetInstructionsList.map {
                            if (it !is Map<*, *> || it.keys.any { key -> key !is String }) {
                                throw IllegalArgumentException("Invalid world reset data #${resetInstructionsList.indexOf(it) + 1}")
                            }
                            when (ResetLoadInstructionType.valueOf(it["type"] as String)) {
                                ResetLoadInstructionType.RESET ->
                                    WorldResetLoadInstruction.deserialize(it as Map<String, Object>)
                                ResetLoadInstructionType.COPY ->
                                    WorldResetLoadInstruction.deserialize(it as Map<String, Object>)
                                ResetLoadInstructionType.RENAME ->
                                    WorldResetLoadInstruction.deserialize(it as Map<String, Object>)
                            }
                        }
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