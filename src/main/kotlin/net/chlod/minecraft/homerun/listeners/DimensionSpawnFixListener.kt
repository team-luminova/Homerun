package net.chlod.minecraft.homerun.listeners

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.persistence.PersistentDataType

class DimensionSpawnFixListener(val plugin: Homerun) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onTeleport(event: PlayerTeleportEvent) {
        val world = event.to.world ?: return
        if (world.persistentDataContainer.has(plugin.keys.spawnModified)) {
            // We've already processed this world.
            return
        }

        val baseWorldName = getManagedWorld(world) ?: return

        when (world.environment) {
            World.Environment.NETHER -> {
                val netherTarget = event.to
                if (world.getBlockAt(netherTarget).type != Material.NETHER_PORTAL) {
                    // Not a nether portal block. They might have gotten here through other means.
                    return
                }
                plugin.logger.info("Portal teleport of Nether dimension for world '${baseWorldName}' detected.")

                // We'll set the current spawn location to the player's new location.
                // This should put the spawn location at the Nether portal (assuming commands were not used to teleport).
                world.spawnLocation.x = netherTarget.x
                world.spawnLocation.y = netherTarget.y
                world.spawnLocation.z = netherTarget.z

                world.persistentDataContainer.set(
                    plugin.keys.spawnModified,
                    PersistentDataType.BOOLEAN,
                    true
                )

                // Clear the chunk cache for this world.
                plugin.retainedChunkCache.flushCaches(true)

                plugin.logger.info("Set spawn location to player's current location (${netherTarget}).")
            }

            else -> return
        }
    }

    private fun getManagedWorld(world: World): String? {
        for (resetRule in plugin.resetRules) {
            for (parameters in resetRule.parametersList) {
                val worldName = parameters.world ?: plugin.server.worlds[0].name
                if (
                    worldName == world.name ||
                    (world.environment == World.Environment.NETHER && world.name == worldName + "_nether") ||
                    (world.environment == World.Environment.THE_END && world.name == worldName + "_the_end")
                ) {
                    return worldName
                }
            }
        }
        return null
    }

}