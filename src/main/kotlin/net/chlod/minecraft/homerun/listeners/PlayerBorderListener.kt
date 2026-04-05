package net.chlod.minecraft.homerun.listeners

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import java.util.*

class PlayerBorderListener(val plugin: Homerun) : Listener {

    private val playerLastLocation = mutableMapOf<UUID, Location>()

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerMove(event: PlayerMoveEvent) {
        onBorderUpdate(event)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // Use a fake player move event.
        @Suppress("UnstableApiUsage")
        onBorderUpdate(
            PlayerMoveEvent(
                event.player,
                event.player.location.clone(),
                event.player.location.clone()
            )
        )
    }

    fun onBorderUpdate(event: PlayerMoveEvent) {
        val player = event.player
        val worldData = plugin.retainedChunkCache.getRetainedChunks(player.location.world.name) ?: return
        if (worldData.isEmpty()) {
            // No retained chunks in this world. Skip.
            return
        }

        val lastLocation = playerLastLocation[player.uniqueId]
        val currentLocation = player.location
        if (
            lastLocation?.blockX == currentLocation.blockX &&
            lastLocation.blockY == currentLocation.blockY &&
            lastLocation.blockZ == currentLocation.blockZ
        ) {
            // Player is still in the same block, so we don't need to update borders.
            return
        }
        playerLastLocation[player.uniqueId] = player.location

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