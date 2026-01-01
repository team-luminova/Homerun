package net.chlod.minecraft.homerun.listeners

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent

class PlayerNotifyListener : Listener {

    private val worldRetainedChunks = mutableMapOf<String, Triple<Boolean, Boolean, Set<Pair<Int, Int>>>>()
    private val playerLastState = mutableMapOf<String, Boolean>()

    constructor(plugin: Homerun, resetRules: List<ResetRule>) {
        for (resetRule in resetRules) {
            if ((resetRule.enabled ?: false) && !(resetRule.notifyExit ?: false) && !(resetRule.notifyEnter ?: false))
                continue

            val world = plugin.server.getWorld(
                resetRule.parameters.world ?: plugin.server.worlds[0].name
            )
            if (world == null) continue
            val retainedChunkSets = resetRule.parameters.retainedChunks.map {
                it.getRetainedChunks(plugin, world)
            }
            for (retainedChunks in retainedChunkSets) {
                if (world.name in this.worldRetainedChunks) {
                    this.worldRetainedChunks[world.name] = Triple(
                        resetRule.notifyExit ?: false,
                        resetRule.notifyExit ?: false,
                        this.worldRetainedChunks[world.name]!!.third.union(retainedChunks)
                    )
                } else {
                    this.worldRetainedChunks[world.name] = Triple(
                        resetRule.notifyExit ?: false,
                        resetRule.notifyExit ?: false,
                        retainedChunks
                    )
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val worldData = worldRetainedChunks[event.player.location.world.name] ?: return

        val playerChunkX = event.player.location.chunk.x
        val playerChunkZ = event.player.location.chunk.z

        val notifyEnter = worldData.first
        val notifyExit = worldData.second
        val retainedChunks = worldData.third
        val inRetainedChunk = Pair(playerChunkX, playerChunkZ) in retainedChunks

        if (inRetainedChunk != playerLastState[event.player.name]) {
            // Player is now in/out a retained chunk.
            playerLastState[event.player.name] = inRetainedChunk

            if (notifyExit && !inRetainedChunk) {
                // Send them a message
                event.player.sendMessage {
                    Component.text()
                        .append(
                            Component.text(
                                "You have left the safe area.",
                                NamedTextColor.RED,
                                TextDecoration.BOLD
                            )
                        )
                        .append(
                            Component.text(" Blocks or items left here will be reset.", NamedTextColor.RED)
                        )
                        .build()
                }
            } else if (notifyEnter && inRetainedChunk) {
                event.player.sendMessage {
                    Component.text("You have entered the safe area.")
                        .color(NamedTextColor.GREEN)
                }
            }
        }
    }

}