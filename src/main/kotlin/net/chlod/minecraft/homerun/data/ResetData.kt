package net.chlod.minecraft.homerun.data

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File

class ResetData(
    val plugin: Plugin,
    val time: Long,
    val sourceWorld: String,
    val targetWorld: String,
    val chunks: List<Pair<Int, Int>>,
    val spawnLocation: Pair<Double, Double>
) {

    companion object {
        @JvmStatic
        val LOCK_FILENAME = "reset.lock"

        fun find(plugin: Plugin): ResetData? {
            val resetData = File(plugin.dataFolder, LOCK_FILENAME)
            if (!resetData.exists()) {
                return null
            }
            val config = YamlConfiguration.loadConfiguration(resetData)
            return try {
                ResetData(
                    plugin,
                    config.getLong("time"),
                    config.getString("sourceWorld")!!,
                    config.getString("targetWorld")!!,
                    config.getList("chunks")!!.map {
                        val pair = it as List<*>
                        Pair((pair[0] as Int), (pair[1] as Int))
                    },
                    Pair(
                        config.getDouble("spawn.x"),
                        config.getDouble("spawn.z")
                    )
                )
            } catch (_: Exception) {
                null
            }
        }

        fun create(
            plugin: Plugin,
            sourceWorld: String,
            targetWorld: String,
            chunks: List<Pair<Int, Int>>,
            spawnLocation: Pair<Double, Double>,
        ): ResetData {
            val time = System.currentTimeMillis()
            return ResetData(plugin, time, sourceWorld, targetWorld, chunks, spawnLocation)
        }
    }

    fun save() {
        val resetData = File(plugin.dataFolder, LOCK_FILENAME)
        val config = YamlConfiguration()
        config.set("time", time)
        config.set("sourceWorld", sourceWorld)
        config.set("targetWorld", targetWorld)
        config.set("chunks", chunks.map { listOf(it.first, it.second) })
        config.set("spawn.x", spawnLocation.first)
        config.set("spawn.z", spawnLocation.second)
        config.save(resetData)
    }

    fun delete() {
        val resetData = File(plugin.dataFolder, LOCK_FILENAME)
        if (resetData.exists()) {
            resetData.delete()
        }
    }

}