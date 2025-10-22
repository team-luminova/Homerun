package net.chlod.minecraft.homerun.data

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File
import java.util.*

class ResetLock(
    val plugin: Plugin,
    val id: String,
    val time: Long,
    val worldResetData: List<WorldResetData>
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
                    val worldResetDataList = config.getMapList("worlds")
                    @Suppress("UNCHECKED_CAST")
                    val resetLock = ResetLock(
                        plugin,
                        file.name.removeSuffix(".lock"),
                        config.getLong("time"),
                        worldResetDataList.map {
                            if (it !is Map<*, *> || it.keys.any { key -> key !is String }) {
                                throw IllegalArgumentException("Invalid world reset data #${worldResetDataList.indexOf(it) + 1}")
                            }
                            WorldResetData.deserialize(it as Map<String, Object>)
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
            worldResetData: List<WorldResetData>
        ): ResetLock {
            val time = System.currentTimeMillis()
            return ResetLock(plugin, UUID.randomUUID().toString(), time, worldResetData)
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
        config.set("worldResetData", worldResetData)
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