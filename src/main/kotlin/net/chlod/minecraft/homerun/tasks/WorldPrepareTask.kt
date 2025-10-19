package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.data.ResetData
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor

class WorldPrepareTask(val plugin: Plugin, val sourceWorld: World): BukkitRunnable() {

    val range = ceil(plugin.config.getInt("range") / 16.0).toInt()
    val newWorldName = "world_${System.currentTimeMillis()}"

    val componentLogger = plugin.componentLogger
    val server = plugin.server

    override fun run() {
        if (Bukkit.isTickingWorlds()) {
            return
        }

        componentLogger.info("Generating new world...")
        val currentWorld = sourceWorld
        val newWorld = server.createWorld(WorldCreator(newWorldName))

        if (newWorld == null) {
            componentLogger.error("Failed to create new world!")
            return
        }

        componentLogger.info("World loaded at ${newWorld.name}")

        cancel()

        componentLogger.info("Getting source spawn location chunk and range...")
        val spawnChunk = currentWorld.spawnLocation.chunk
        val minX = floor(spawnChunk.x - (range / 2.0)).toInt()
        val maxX = ceil(spawnChunk.x + (range / 2.0)).toInt() - 1
        val minZ = floor(spawnChunk.z - (range / 2.0)).toInt()
        val maxZ = ceil(spawnChunk.z + (range / 2.0)).toInt() - 1

        componentLogger.info("Prepared for reset. Saving reset data...")
        val chunkPairs = mutableListOf<Pair<Int, Int>>()
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                chunkPairs.add(Pair(x, z))
            }
        }
        val spawnLocation = listOf(
            currentWorld.spawnLocation.x,
            currentWorld.spawnLocation.y,
            currentWorld.spawnLocation.z,
            currentWorld.spawnLocation.yaw.toDouble(),
            currentWorld.spawnLocation.pitch.toDouble()
        )
        val resetData = ResetData.create(
            plugin,
            currentWorld.name,
            newWorldName,
            chunkPairs,
            spawnLocation
        )
        resetData.save()
        componentLogger.info("Reset data saved.")

        val serverProperties = File(server.pluginsFolder.parentFile, "server.properties")
        componentLogger.info("Modifying server.properties to set spawn world...")
        val properties = serverProperties.readLines().toMutableList()
        var spawnWorldSet = false
        for (i in properties.indices) {
            if (properties[i].startsWith("level-name=")) {
                properties[i] = "level-name=$newWorldName"
                spawnWorldSet = true
                break
            }
        }
        if (!spawnWorldSet) {
            properties.add("level-name=$newWorldName")
        }
        serverProperties.writeText(properties.joinToString("\n"))
        componentLogger.info("Modified server.properties spawn world...")

        componentLogger.info("Restarting server...")
        server.restart()
    }

}