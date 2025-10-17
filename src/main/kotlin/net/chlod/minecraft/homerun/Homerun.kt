package net.chlod.minecraft.homerun

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.chlod.minecraft.homerun.logic.ChunkCopyUtil
import org.bukkit.Bukkit
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.ceil
import kotlin.math.floor

class Homerun : JavaPlugin() {

    // range is in diameter
    var range = 256 / 16

    override fun onLoad() {
        // Load logic
    }

    override fun onEnable() {
        var breakpoint = object : BasicCommand {
            override fun execute(commandSourceStack: CommandSourceStack, args: Array<String>) {
                object : BukkitRunnable() {
                    override fun run() {
                        if (Bukkit.isTickingWorlds()) {
                            return;
                        }

                        cancel()

                        val worldName = "world_${System.currentTimeMillis()}"
                        componentLogger.info("Generating new world...")
                        val currentWorld = commandSourceStack.location.world
                        val newWorld = server.createWorld(WorldCreator(worldName))

                        if (newWorld == null) {
                            componentLogger.error("Failed to create new world!")
                            commandSourceStack.sender.sendMessage("Failed to create new world!")
                            return
                        }

                        val spawnChunk = currentWorld.spawnLocation.chunk
                        val minX = floor(spawnChunk.x - (range / 2.0)).toInt()
                        val maxX = ceil(spawnChunk.x + (range / 2.0)).toInt() - 1
                        val minZ = floor(spawnChunk.z - (range / 2.0)).toInt()
                        val maxZ = ceil(spawnChunk.z + (range / 2.0)).toInt() - 1

                        ChunkCopyUtil(this@Homerun).copyRegion(
                            currentWorld,
                            newWorld,
                            minX,
                            minZ,
                            maxX,
                            maxZ
                        )
                        newWorld.setSpawnLocation(
                            currentWorld.spawnLocation.x.toInt(),
                            currentWorld.spawnLocation.y.toInt(),
                            currentWorld.spawnLocation.z.toInt()
                        )

                        componentLogger.info("World generation complete: $worldName")
                        commandSourceStack.sender.sendMessage("World generation complete: $worldName")
                        commandSourceStack.sender.sendMessage("Teleporting...")
                        if (commandSourceStack.sender is Player) {
                            (commandSourceStack.sender as Player).teleport(newWorld.spawnLocation)
                        }
                    }
                }.runTaskTimer(this@Homerun, 0L, 20L)
            }
        }

        registerCommand("breakpoint", breakpoint)

        // Plugin startup logic
        var world = server.worlds.firstOrNull();
        if (world != null) {
            componentLogger.info("World found: ${world.name}")
            componentLogger.info("keys:");
            for (key in world.persistentDataContainer.keys) {
                componentLogger.info(" - ${key.asString()}")
            }
        } else {
            componentLogger.info("No world found!")
        }
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
