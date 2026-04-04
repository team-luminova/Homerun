package net.chlod.minecraft.homerun.config.borders

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.math.Adjacency
import org.bukkit.HeightMap
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.BlockState
import org.bukkit.event.player.PlayerMoveEvent
import java.util.*

class HighestBlockBorderType(
    val distanceChunks: Int,
    val block: String? = null,
    val heightmap: String? = null
) : ResetBorder(BorderType.HIGHEST_BLOCK) {

    val trackedBlockStateChanges: MutableMap<Triple<ResetRule, String, UUID>, List<BlockState>> = mutableMapOf()

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun deserialize(args: Map<String, Any>): HighestBlockBorderType {
            val distanceChunks = args["distance_chunks"] as Int?
                ?: throw IllegalArgumentException("distance_chunks is required for highest_block border type")
            if (distanceChunks <= 0) {
                throw IllegalArgumentException("distance_chunks must be a positive integer")
            }

            val block = args["block"] as? String
            if (Material.matchMaterial(block ?: "REDSTONE_BLOCK") == null) {
                throw IllegalArgumentException("Invalid block type: $block")
            }

            val heightmap = args["heightmap"] as? String
            if (heightmap != null) {
                try {
                    HeightMap.valueOf(heightmap.uppercase())
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("Invalid heightmap type: $heightmap; see https://minecraft.wiki/w/Heightmap for possible options")
                }
            }

            return HighestBlockBorderType(
                distanceChunks,
                block,
                heightmap
            )
        }
    }

    override fun serialize(): Map<String?, Any?> {
        return super.serialize() + mapOf(
            "distance_chunks" to distanceChunks,
            "block" to block,
            "heightmap" to heightmap
        )
    }


    override fun doBorderUpdate(
        plugin: Homerun,
        resetRule: ResetRule,
        event: PlayerMoveEvent,
        from: Location,
        to: Location
    ) {
        val borderDistanceRadius = distanceChunks

        val playerChunkX = event.player.location.chunk.x
        val playerChunkZ = event.player.location.chunk.z

        val trackingKey = Triple(resetRule, event.player.world.name, event.player.uniqueId)
        val oldStateChanges = trackedBlockStateChanges[trackingKey] ?: emptySet()
        if (oldStateChanges.isNotEmpty()) {
            // Clear old borders if they exist.
            // Use a refreshed block state to reset the block back to its original state, in case the block was changed
            // by another player or by the player themselves.
            val blockStateChanges = oldStateChanges.map {
                event.from.world.getBlockAt(it.x, it.y, it.z).state
            }
            event.player.sendBlockChanges(blockStateChanges)
        }

        val nearbyChunks = mutableSetOf<Pair<Int, Int>>()

        val minX = (playerChunkX - borderDistanceRadius)
        val maxX = (playerChunkX + borderDistanceRadius)
        val minZ = (playerChunkZ - borderDistanceRadius)
        val maxZ = (playerChunkZ + borderDistanceRadius)

        // Ideally, the chunk radius should be small enough where this brute-force check is not a performance issue.
        // A radius of 3 means checking 7x7 = 49 chunks, which is not the worst.
        for (chunkX in minX..maxX) {
            for (chunkZ in minZ..maxZ) {
                nearbyChunks.add(Pair(chunkX, chunkZ))
            }
        }

        // Go through all non-retained chunks and find the ones that are adjacent to retained chunks.
        // For each adjacent chunk, we need to know whether we need to show a border on the
        // north/south/east/west side, or on one of the four corners.
        val directions =
            plugin.retainedChunkCache.getChunkAdjacency(event.player.world, nearbyChunks) ?: return
        val blockStateChanges = mutableListOf<BlockState>()
        val newBlockType = Material.matchMaterial(block ?: "REDSTONE_BLOCK") ?: Material.REDSTONE_BLOCK
        for (chunkPos in directions.keys) {
            val chunkAdjacency = directions[chunkPos] ?: continue
            if (chunkAdjacency.retained) {
                // This chunk is retained, so we don't need to show borders for it.
                continue
            }
            for (direction in chunkAdjacency.directions) {
                blockStateChanges.addAll(
                    getBlockUpdates(event.player.world, chunkPos, direction, newBlockType)
                )
            }
        }

        trackedBlockStateChanges[trackingKey] = blockStateChanges
        if (blockStateChanges.isNotEmpty()) {
            event.player.sendBlockChanges(blockStateChanges)
        }
    }

    fun getBlockUpdates(
        world: World,
        chunkPos: Pair<Int, Int>,
        direction: Adjacency.Direction,
        newBlockType: Material
    ): List<BlockState> {
        val heightMap = if (heightmap != null) {
            HeightMap.valueOf(heightmap.uppercase())
        } else {
            HeightMap.MOTION_BLOCKING_NO_LEAVES
        }
        val blockStates = getBorderCoordinates(world, chunkPos, direction, heightMap)
            .map { l -> world.getBlockAt(l) }
            .map { l -> l.state.copy() }
        blockStates.forEach { it.type = newBlockType }
        return blockStates
    }

}