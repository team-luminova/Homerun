package net.chlod.minecraft.homerun.listeners

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetParameters
import net.chlod.minecraft.homerun.config.ResetRule
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.world.WorldLoadEvent

class PlayerNotifyListener : Listener {

    private val plugin: Homerun
    private val resetRules: List<ResetRule>

    private val worldRetainedChunks = mutableMapOf<String, Triple<Boolean, Boolean, Set<Pair<Int, Int>>>>()
    private val playerLastState = mutableMapOf<String, Boolean>()

    constructor(plugin: Homerun, resetRules: List<ResetRule>) {
        this.plugin = plugin
        this.resetRules = resetRules

        this.cacheRetainedChunks()
    }

    private fun cacheRetainedChunks() {
        for (resetRule in resetRules) {
            if (
                !(resetRule.enabled ?: false) ||
                (!(resetRule.notifyExit ?: false) && !(resetRule.notifyEnter ?: false))
            )
                continue

            val world = plugin.server.getWorld(
                resetRule.parameters.world ?: plugin.server.worlds[0].name
            )
            if (world == null) continue
            cacheRetainedChunksForWorld(resetRule, world)

            if (resetRule.parameters.netherBehavior == ResetParameters.DimensionResetBehavior.NORMAL) {
                val netherWorld = plugin.server.getWorld("${world.name}_nether") ?: run {
                    plugin.componentLogger.warn("Nether world for '${world.name}' not found, skipping retained chunk processing for it.")
                    null
                } ?: continue
                cacheRetainedChunksForWorld(resetRule, netherWorld)
            }
            if (resetRule.parameters.endBehavior == ResetParameters.DimensionResetBehavior.NORMAL) {
                val endWorld = plugin.server.getWorld("${world.name}_the_end") ?: run {
                    plugin.componentLogger.warn("End world for '${world.name}' not found, skipping retained chunk processing for it.")
                    null
                } ?: continue
                cacheRetainedChunksForWorld(resetRule, endWorld)
            }
        }
        plugin.componentLogger.info("Cached retained chunks in ${worldRetainedChunks.size} worlds for player notifications.")
    }

    private fun cacheRetainedChunksForWorld(resetRule: ResetRule, world: World) {
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

    @EventHandler(priority = EventPriority.HIGH)
    fun onWorldLoad(event: WorldLoadEvent) {
        this.cacheRetainedChunks()
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val worldData = worldRetainedChunks[event.player.location.world.name] ?: return

        val playerChunkX = event.player.location.chunk.x
        val playerChunkZ = event.player.location.chunk.z

        val notifyEnter = worldData.first
        val notifyExit = worldData.second
        val retainedChunks = worldData.third
        val lastState = playerLastState[event.player.name]
        val inRetainedChunk = Pair(playerChunkX, playerChunkZ) in retainedChunks

        if (inRetainedChunk != lastState) {
            // Player is now in/out a retained chunk.
            playerLastState[event.player.name] = inRetainedChunk

            // If this is the first state change, skip sending a message.
            if (lastState == null) {
                return
            }

            if (notifyExit && !inRetainedChunk) {
                // Send them a message
                event.player.sendActionBar {
                    Component.text()
                        .append(
                            Component.text(
                                "You have entered the reset area.",
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
                event.player.sendActionBar {
                    Component.text("You have left the reset area.")
                        .color(NamedTextColor.GREEN)
                }
            }
        }
    }

}