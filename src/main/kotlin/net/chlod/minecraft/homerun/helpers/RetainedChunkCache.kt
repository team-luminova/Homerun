package net.chlod.minecraft.homerun.helpers

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetParameters
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.math.Adjacency
import org.bukkit.World

class RetainedChunkCache(val plugin: Homerun, val resetRules: List<ResetRule>) {

    class ChunkAdjacency(
        /**
         * Whether this is a retained chunk.
         */
        val retained: Boolean,
        /**
         * The directions in which this chunk is adjacent to retained chunks. This is empty if the
         * chunk itself is retained.
         */
        val directions: List<Adjacency.Direction>
    )

    private val worldChunks = mutableMapOf<String, MutableMap<ResetRule, Set<Pair<Int, Int>>>>()
    private val chunkAdjacencyCache = mutableMapOf<String, MutableMap<Pair<Int, Int>, ChunkAdjacency>>()

    fun getRetainedChunks(worldName: String): Map<ResetRule, Set<Pair<Int, Int>>>? {
        return worldChunks[worldName]?.toMap()
    }

    /**
     * Finds all chunks that are adjacent to retained chunks for the given world. Only chunks which are in allChunks
     * are checked; if a chunk is adjacent to a retained chunk but not in allChunks, it will be ignored for performance.
     * If allChunks contains all surrounding adjacent chunks of an inner chunk, the result is cached.
     *
     * If the world is not cached, returns null.
     */
    fun getAdjacentRetainedChunks(world: World, allChunks: Set<Pair<Int, Int>>): Map<Pair<Int, Int>, ChunkAdjacency>? {
        val result = mutableMapOf<Pair<Int, Int>, ChunkAdjacency>()
        val worldCache = chunkAdjacencyCache[world.name] ?: return null
        for (chunk in allChunks) {
            var chunkAdjacency = worldCache[chunk]
            if (chunkAdjacency == null) {
                chunkAdjacency = ChunkAdjacency(false, emptyList())
                worldCache[chunk] = chunkAdjacency
            }
            result[chunk] = chunkAdjacency
        }
        return result
    }

    fun flushCaches() {
        cacheRetainedChunks()
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
        val totalChunks = worldChunks.values.sumOf { it -> it.values.sumOf { it.size } }
        plugin.componentLogger.info("Cached $totalChunks retained chunks in ${worldChunks.size} worlds for player notifications.")
    }

    private fun cacheRetainedChunksForWorld(resetRule: ResetRule, world: World) {
        val retainedChunkSets = resetRule.parameters.retainedChunks.map {
            it.getRetainedChunks(plugin, world)
        }
        if (world.name !in worldChunks) {
            worldChunks[world.name] = mutableMapOf()
        }
        val retainedChunks = retainedChunkSets.flatten().toSet()
        worldChunks[world.name]!![resetRule] = retainedChunks
        cacheAdjacentChunksForWorld(world, retainedChunks)
    }

    private fun cacheAdjacentChunksForWorld(world: World, retainedChunks: Set<Pair<Int, Int>>) {
        val allChunks = retainedChunks.toMutableSet()
        for (chunk in retainedChunks) {
            // Get all surrounding adjacent chunks of this chunk and add them to allChunks
            for (x in -1..1) {
                for (z in -1..1) {
                    if (x == 0 && z == 0) continue
                    allChunks.add(Pair(chunk.first + x, chunk.second + z))
                }
            }
        }
        // Now find the adjacent chunks for the retained chunks and cache them
        val unretainedDirections = Adjacency.findAdjacentFlaggedPositions(allChunks, retainedChunks)
        if (world.name !in chunkAdjacencyCache) {
            chunkAdjacencyCache[world.name] = mutableMapOf()
        } else {
            chunkAdjacencyCache.clear()
        }
        for ((chunk, directions) in unretainedDirections) {
            chunkAdjacencyCache[world.name]!![chunk] = ChunkAdjacency(false, directions)
        }
        // Cache the retained chunks themselves with empty directions
        for (chunk in retainedChunks) {
            chunkAdjacencyCache[world.name]!![chunk] = ChunkAdjacency(true, emptyList())
        }
    }

}