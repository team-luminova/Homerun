package net.chlod.minecraft.homerun.logic

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.craftbukkit.CraftChunk
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.plugin.java.JavaPlugin

class ChunkCopyUtil(private val plugin: JavaPlugin) {

    /**
     * Copies a chunk from source world to target world at specified coordinates.
     * This performs a deep copy of blocks, biomes, block entities, and lighting data.
     *
     * @param sourceChunk The source chunk to copy from
     * @param targetWorld The target world to copy to
     * @param targetX Target chunk X coordinate
     * @param targetZ Target chunk Z coordinate
     * @param copyEntities Whether to copy entities (not recommended due to world-specific references)
     */
    fun copyChunk(
        sourceChunk: Chunk,
        targetWorld: World,
        targetX: Int,
        targetZ: Int,
        copyEntities: Boolean = false
    ) {
        // Ensure we're on the main thread
        if (!plugin.server.isPrimaryThread) {
            plugin.server.scheduler.runTask(plugin, Runnable {
                copyChunk(sourceChunk, targetWorld, targetX, targetZ, copyEntities)
            })
            return
        }

        val craftSourceChunk = sourceChunk as CraftChunk
        val craftSourceWorld = craftSourceChunk.craftWorld
        val craftTargetWorld = targetWorld as CraftWorld

        val sourceLevel = craftSourceWorld.handle
        val targetLevel = craftTargetWorld.handle

        // Get the source chunk at FULL status
        val sourceLevelChunk = craftSourceChunk.getHandle(ChunkStatus.FULL) as LevelChunk

        // Load or generate the target chunk
        val targetLevelChunk = targetLevel.getChunk(targetX, targetZ, ChunkStatus.FULL, true) as LevelChunk

        // Copy chunk sections (blocks and biomes)
        copySections(sourceLevelChunk, targetLevelChunk, targetLevel)

        // Copy block entities (tile entities)
        copyBlockEntities(sourceLevelChunk, targetLevelChunk, targetX, targetZ, targetLevel)

        // Copy heightmaps
        copyHeightmaps(sourceLevelChunk, targetLevelChunk)

        // Reset lighting data
        resetLighting(targetLevel, targetX, targetZ)

        // Copy persistent data container
        targetLevelChunk.persistentDataContainer.putAll(
            sourceLevelChunk.persistentDataContainer.toTagCompound()
        )

        // Copy inhabited time
        targetLevelChunk.inhabitedTime = sourceLevelChunk.inhabitedTime

        // Mark chunk as unsaved
        targetLevelChunk.markUnsaved()

        // Recalculate lighting
        recalculateLighting(targetLevel, targetX, targetZ)

        plugin.logger.info(
            "Copied chunk (${sourceChunk.x}, ${sourceChunk.z}) from ${sourceChunk.world.name} " +
                    "to ($targetX, $targetZ) in ${targetWorld.name}"
        )
    }

    /**
     * Copies all chunk sections including blocks and biomes
     */
    private fun copySections(
        source: LevelChunk,
        target: LevelChunk,
        targetLevel: ServerLevel
    ) {
        val sourceSections = source.sections
        val targetSections = target.sections

        source.blendingData

        for (i in sourceSections.indices) {
            val sourceSection = sourceSections[i]

            if (sourceSection == null || sourceSection.hasOnlyAir()) {
                continue
            }

            val targetSection = targetSections[i]

            // Copy block states using the copy() method which creates a deep copy
            val copiedStates = sourceSection.states.copy()

            // Copy biomes using the copy() method
            val copiedBiomes = sourceSection.biomes.copy()

            // Create new section with copied data
            // We need to manually copy the data since we can't directly replace sections
            targetSection.states.acquire()
            try {
                // Copy each block state
                for (x in 0..15) {
                    for (y in 0..15) {
                        for (z in 0..15) {
                            val blockState = sourceSection.getBlockState(x, y, z)
                            targetSection.setBlockState(x, y, z, blockState, false)
                        }
                    }
                }
            } finally {
                targetSection.states.release()
            }

            // Copy biomes
            targetSection.acquire()
            try {
                for (x in 0..3) {
                    for (y in 0..3) {
                        for (z in 0..3) {
                            val biome = sourceSection.getNoiseBiome(x, y, z)
                            targetSection.setBiome(x, y, z, biome)
                        }
                    }
                }
            } finally {
                targetSection.release()
            }
        }
    }

    /**
     * Copies block entities (chests, furnaces, signs, etc.)
     */
    private fun copyBlockEntities(
        source: LevelChunk,
        target: LevelChunk,
        targetX: Int,
        targetZ: Int,
        targetLevel: ServerLevel
    ) {
        // Clear existing block entities in target chunk
        target.blockEntities.clear()

        // Copy each block entity
        for ((sourcePos, sourceBlockEntity) in source.blockEntities) {
            val relativeX = sourcePos.x and 15
            val relativeZ = sourcePos.z and 15
            val y = sourcePos.y

            // Calculate new position in target chunk
            val newX = (targetX shl 4) + relativeX
            val newZ = (targetZ shl 4) + relativeZ
            val newPos = BlockPos(newX, y, newZ)

            // Get the block state at this position
            val blockState = target.getBlockState(newPos)

            // Create a new block entity if the block supports it
            if (blockState.hasBlockEntity()) {
                val newBlockEntity = (blockState.block as net.minecraft.world.level.block.EntityBlock)
                    .newBlockEntity(newPos, blockState)

                if (newBlockEntity != null) {
                    // Save the source block entity's NBT data
                    val nbt = sourceBlockEntity.saveWithFullMetadata(
                        targetLevel.registryAccess()
                    )

                    // Update position in NBT
                    nbt.putInt("x", newX)
                    nbt.putInt("y", y)
                    nbt.putInt("z", newZ)

                    // Load the NBT data into the new block entity
                    newBlockEntity.loadWithComponents(
                        nbt,
                        targetLevel.registryAccess()
                    )

                    // Add to target chunk
                    target.setBlockEntity(newBlockEntity)
                }
            }
        }
    }

    /**
     * Copies heightmaps from source to target
     */
    private fun copyHeightmaps(source: LevelChunk, target: LevelChunk) {
        for (type in net.minecraft.world.level.levelgen.Heightmap.Types.entries) {
            val sourceHeightmap = source.heightmaps[type]
            if (sourceHeightmap != null) {
                val targetHeightmap = target.getOrCreateHeightmapUnprimed(type)
                targetHeightmap.setRawData(
                    target,
                    type,
                    sourceHeightmap.rawData
                )
            }
        }
    }

    private fun resetLighting(level: ServerLevel, chunkX: Int, chunkZ: Int) {
        val chunkPos = ChunkPos(chunkX, chunkZ)
        val lightEngine = level.lightEngine

        // Disable light updates for this chunk
        lightEngine.retainData(chunkPos, false)

        // Clear existing light data
        lightEngine.setLightEnabled(chunkPos, false)

        // Schedule lighting recalculation on next tick
        level.chunkSource.lightEngine.tryScheduleUpdate()
    }

    /**
     * Recalculates lighting for the target chunk
     */
    private fun recalculateLighting(level: ServerLevel, chunkX: Int, chunkZ: Int) {
        val chunkPos = ChunkPos(chunkX, chunkZ)
        val lightEngine = level.lightEngine

        // Enable light updates for this chunk
        lightEngine.retainData(chunkPos, true)

        // Queue lighting updates
        lightEngine.setLightEnabled(chunkPos, true)

        // Schedule lighting recalculation on next tick
        level.chunkSource.lightEngine.tryScheduleUpdate()
    }

    /**
     * Batch copy multiple chunks from one world to another.
     * Useful for copying regions or entire worlds with a different seed.
     *
     * @param sourceWorld Source world
     * @param targetWorld Target world
     * @param chunks List of chunk coordinates (x, z) to copy
     * @param offsetX X offset to apply when copying
     * @param offsetZ Z offset to apply when copying
     * @param onProgress Callback for progress updates
     */
    fun batchCopyChunks(
        sourceWorld: World,
        targetWorld: World,
        chunks: List<Pair<Int, Int>>,
        offsetX: Int = 0,
        offsetZ: Int = 0,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ) {
        var current = 0
        val total = chunks.size

        for ((chunkX, chunkZ) in chunks) {
            // Load source chunk
            val sourceChunk = sourceWorld.getChunkAt(chunkX, chunkZ, true)

            // Copy to target with offset
            copyChunk(
                sourceChunk,
                targetWorld,
                chunkX + offsetX,
                chunkZ + offsetZ,
                copyEntities = false
            )

            current++
            onProgress?.invoke(current, total)

            // Yield to prevent server lag
            if (current % 10 == 0) {
                Thread.sleep(50)
            }
        }

        plugin.logger.info("Batch copy completed: $total chunks copied")
    }

    /**
     * Copy a region of chunks from source to target world
     */
    fun copyRegion(
        sourceWorld: World,
        targetWorld: World,
        minChunkX: Int,
        minChunkZ: Int,
        maxChunkX: Int,
        maxChunkZ: Int,
        offsetX: Int = 0,
        offsetZ: Int = 0
    ) {
        val chunks = mutableListOf<Pair<Int, Int>>()

        for (x in minChunkX..maxChunkX) {
            for (z in minChunkZ..maxChunkZ) {
                chunks.add(Pair(x, z))
            }
        }

        plugin.logger.info(
            "Copying region: ($minChunkX, $minChunkZ) to ($maxChunkX, $maxChunkZ) - ${chunks.size} chunks"
        )

        batchCopyChunks(sourceWorld, targetWorld, chunks, offsetX, offsetZ) { current, total ->
            if (current % 50 == 0) {
                plugin.logger.info("Progress: $current / $total chunks copied")
            }
        }
    }
}