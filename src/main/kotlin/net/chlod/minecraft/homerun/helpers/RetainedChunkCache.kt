package net.chlod.minecraft.homerun.helpers

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetParameters
import net.chlod.minecraft.homerun.config.ResetRule
import org.bukkit.World

class RetainedChunkCache(val plugin: Homerun, val resetRules: List<ResetRule>) {

    private val worldChunks = mutableMapOf<String, MutableMap<ResetRule, Set<Pair<Int, Int>>>>()

    fun getRetainedChunks(worldName: String): Map<ResetRule, Set<Pair<Int, Int>>>? {
        return worldChunks[worldName]?.toMap()
    }

    fun cacheRetainedChunks() {
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
        plugin.componentLogger.info("Cached retained chunks in ${worldChunks.size} worlds for player notifications.")
    }

    private fun cacheRetainedChunksForWorld(resetRule: ResetRule, world: World) {
        val retainedChunkSets = resetRule.parameters.retainedChunks.map {
            it.getRetainedChunks(plugin, world)
        }
        for (retainedChunks in retainedChunkSets) {
            if (world.name !in worldChunks) {
                worldChunks[world.name] = mutableMapOf()
            }

            worldChunks[world.name]!![resetRule] = retainedChunks
        }
    }

}