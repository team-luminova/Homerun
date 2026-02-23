package net.chlod.minecraft.homerun.helpers

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetParameters
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.math.Adjacency
import org.bukkit.World
import org.bukkit.scheduler.BukkitRunnable

class RetainedChunkCache(val plugin: Homerun, val resetRules: List<ResetRule>) {

    class ChunkAdjacency(
        val x: Int,
        val z: Int,
        /**
         * Whether this is a retained chunk.
         */
        val retained: Boolean,
        /**
         * The directions in which this chunk is adjacent to retained chunks. If this is a retained chunk
         * itself, the directions in which it is adjacent to non-retained chunks.
         */
        val directions: List<Adjacency.Direction>
    ) {
        /**
         * Get local coordinates of border blocks for this chunk based on the directions. For example, if the chunk
         * has a border on the north side, all blocks with local z coordinate 0 are border blocks, and their coordinates
         * (<0, 0> to <15, 0>) are returned.
         */
        fun getBorderBlocks(): List<Pair<Int, Int>> {
            val borderBlocks = mutableListOf<Pair<Int, Int>>()
            for (direction in directions) {
                when (direction) {
                    Adjacency.Direction.NORTH -> {
                        for (x in 0..15) {
                            borderBlocks.add(Pair(x, 0))
                        }
                    }

                    Adjacency.Direction.SOUTH -> {
                        for (x in 0..15) {
                            borderBlocks.add(Pair(x, 15))
                        }
                    }

                    Adjacency.Direction.EAST -> {
                        for (z in 0..15) {
                            borderBlocks.add(Pair(15, z))
                        }
                    }

                    Adjacency.Direction.WEST -> {
                        for (z in 0..15) {
                            borderBlocks.add(Pair(0, z))
                        }
                    }

                    Adjacency.Direction.NORTHEAST -> {
                        borderBlocks.add(Pair(15, 0))
                    }

                    Adjacency.Direction.SOUTHEAST -> {
                        borderBlocks.add(Pair(15, 15))
                    }

                    Adjacency.Direction.SOUTHWEST -> {
                        borderBlocks.add(Pair(0, 15))
                    }

                    Adjacency.Direction.NORTHWEST -> {
                        borderBlocks.add(Pair(0, 0))
                    }
                }
            }
            return borderBlocks
        }
    }

    internal class RetainedChunkCacheRefreshTask(val retainedChunkCache: RetainedChunkCache) : BukkitRunnable() {
        override fun run() {
            retainedChunkCache.flushCaches(false)
        }
    }

    private val worldChunks = mutableMapOf<String, MutableMap<ResetRule, Set<Pair<Int, Int>>>>()
    private val chunkAdjacencyCache = mutableMapOf<String, MutableMap<Pair<Int, Int>, ChunkAdjacency>>()

    fun getRetainedChunks(worldName: String): Map<ResetRule, Set<Pair<Int, Int>>>? {
        return worldChunks[worldName]?.toMap()
    }

    fun isChunkRetained(worldName: String, chunk: Pair<Int, Int>): Boolean? {
        val worldData = worldChunks[worldName] ?: return null
        for ((_, retainedChunks) in worldData) {
            if (chunk in retainedChunks) {
                return true
            }
        }
        return false
    }

    /**
     * Finds all chunks adjacencies for the given chunks. For chunks that are retained, this returns directions
     * of non-retained chunks. For chunks that are not retained, this returns directions of adjacent retained chunks.
     *
     * If the world is not cached, returns null.
     */
    fun getChunkAdjacency(world: World, allChunks: Set<Pair<Int, Int>>): Map<Pair<Int, Int>, ChunkAdjacency>? {
        val result = mutableMapOf<Pair<Int, Int>, ChunkAdjacency>()
        val worldCache = chunkAdjacencyCache[world.name] ?: return null
        for (chunk in allChunks) {
            var chunkAdjacency = worldCache[chunk]
            if (chunkAdjacency == null) {
                chunkAdjacency = ChunkAdjacency(chunk.first, chunk.second, false, emptyList())
                worldCache[chunk] = chunkAdjacency
            }
            result[chunk] = chunkAdjacency
        }
        return result
    }

    fun getChunkAdjacency(world: World, chunk: Pair<Int, Int>): ChunkAdjacency? {
        val worldCache = chunkAdjacencyCache[world.name] ?: return null
        var chunkAdjacency = worldCache[chunk]
        if (chunkAdjacency == null) {
            chunkAdjacency = ChunkAdjacency(chunk.first, chunk.second, false, emptyList())
            worldCache[chunk] = chunkAdjacency
        }
        return chunkAdjacency
    }

    fun flushCaches(verbose: Boolean = false) {
        cacheRetainedChunks(verbose)
    }

    private fun cacheRetainedChunks(verbose: Boolean = false) {
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
                    if (verbose) {
                        plugin.componentLogger.debug("Nether world for '${world.name}' not found, skipping retained chunk processing for it.")
                    }
                    null
                } ?: continue
                cacheRetainedChunksForWorld(resetRule, netherWorld)
            }
            if (resetRule.parameters.endBehavior == ResetParameters.DimensionResetBehavior.NORMAL) {
                val endWorld = plugin.server.getWorld("${world.name}_the_end") ?: run {
                    if (verbose) {
                        plugin.componentLogger.debug("End world for '${world.name}' not found, skipping retained chunk processing for it.")
                    }
                    null
                } ?: continue
                cacheRetainedChunksForWorld(resetRule, endWorld)
            }
        }
        val totalChunks = worldChunks.values.sumOf { it -> it.values.sumOf { it.size } }
        plugin.componentLogger.debug("Cached $totalChunks retained chunks in ${worldChunks.size} worlds for player notifications.")
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
        val retainedUnflaggedDirections = Adjacency.findAdjacentUnflaggedPositions(allChunks, retainedChunks)
        if (world.name !in chunkAdjacencyCache) {
            chunkAdjacencyCache[world.name] = mutableMapOf()
        } else {
            chunkAdjacencyCache[world.name]!!.clear()
        }
        for ((chunk, directions) in unretainedDirections) {
            chunkAdjacencyCache[world.name]!![chunk] = ChunkAdjacency(chunk.first, chunk.second, false, directions)
        }
        for ((chunk, directions) in retainedUnflaggedDirections) {
            chunkAdjacencyCache[world.name]!![chunk] = ChunkAdjacency(chunk.first, chunk.second, true, directions)
        }
    }

}