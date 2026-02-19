package net.chlod.minecraft.homerun.listeners

import org.bukkit.Chunk
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent

abstract class PlayerChunkMovementListener : Listener {

    private val playerLastChunk = mutableMapOf<String, Chunk>()

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (playerLastChunk[event.player.name] == event.player.location.chunk) {
            // Player is still in the same chunk, no need to check.
            return
        }

        onPlayerChunkChange(event, playerLastChunk[event.player.name], event.player.location.chunk)

        playerLastChunk[event.player.name] = event.player.location.chunk
    }

    abstract fun onPlayerChunkChange(event: PlayerMoveEvent, oldChunk: Chunk?, newChunk: Chunk)

}