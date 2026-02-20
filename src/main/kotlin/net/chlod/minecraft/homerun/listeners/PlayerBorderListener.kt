package net.chlod.minecraft.homerun.listeners

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import java.util.*

class PlayerBorderListener(val plugin: Homerun) : Listener {

    private val playerLastLocation = mutableMapOf<UUID, Location>()

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val worldData = plugin.retainedChunkCache.getRetainedChunks(event.player.location.world.name) ?: return
        if (worldData.isEmpty()) {
            // No retained chunks in this world. Skip.
            return
        }

        val lastLocation = playerLastLocation[event.player.uniqueId]
        val currentLocation = event.player.location
        if (
            lastLocation?.blockX == currentLocation.blockX &&
            lastLocation.blockY == currentLocation.blockY &&
            lastLocation.blockZ == currentLocation.blockZ
        ) {
            // Player is still in the same block, so we don't need to update borders.
            return
        }
        playerLastLocation[event.player.uniqueId] = event.player.location

        for (resetRule in plugin.resetRules) {
            resetRule.borders?.forEach { border ->
                border.doBorderUpdate(
                    plugin,
                    resetRule,
                    event,
                    lastLocation ?: currentLocation,
                    currentLocation
                )
            }
        }
    }

}