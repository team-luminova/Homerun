package net.chlod.minecraft.homerun.listeners

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.Chunk
import org.bukkit.event.player.PlayerMoveEvent

class PlayerNotifyListener(val plugin: Homerun) : PlayerChunkMovementListener() {

    private val playerLastState = mutableMapOf<String, Boolean>()

    override fun onPlayerChunkChange(event: PlayerMoveEvent, oldChunk: Chunk?, newChunk: Chunk) {
        val playerChunkX = newChunk.x
        val playerChunkZ = newChunk.z

        val worldData = plugin.retainedChunkCache.getRetainedChunks(event.player.location.world.name) ?: return

        for (resetRule in worldData.keys) {
            val retainedChunks = worldData[resetRule] ?: continue // No retained chunks?
            val lastState = playerLastState[event.player.name]
            val inRetainedChunk = Pair(playerChunkX, playerChunkZ) in retainedChunks

            if (inRetainedChunk != lastState) {
                // Player is now in/out a retained chunk.
                playerLastState[event.player.name] = inRetainedChunk

                // If this is the first state change, skip sending a message.
                if (lastState == null) {
                    return
                }
            }
        }
    }

}