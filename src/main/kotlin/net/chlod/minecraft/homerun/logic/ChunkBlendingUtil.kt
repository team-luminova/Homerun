package net.chlod.minecraft.homerun.logic

import com.mojang.serialization.Dynamic
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.blending.BlendingData
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.craftbukkit.CraftChunk
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Field

class ChunkBlendingUtil(private val plugin: JavaPlugin) {

    /**
     * Sets blending data for a chunk to force chunk blending.
     * This removes heightmaps and light data to trigger regeneration at chunk borders.
     */
    fun setChunkBlendingData(chunk: Chunk) {
        val craftChunk = chunk as CraftChunk
        val craftWorld = craftChunk.craftWorld
        val serverLevel = craftWorld.handle

        val levelChunk = craftChunk.getHandle(ChunkStatus.FULL) as LevelChunk

        // Calculate min and max sections based on the chunk's sections
        val sections = levelChunk.sections
        var minSection = 0
        var maxSection = 0

        for (i in sections.indices) {
            val section = sections[i]
            if (section != null) {
                val sectionY = serverLevel.getSectionYFromSectionIndex(i)
                minSection = minOf(sectionY, minSection)
                maxSection = maxOf(sectionY, maxSection)
            }
        }

        // Ensure minimum bounds (matching your reference)
        minSection = minOf(minSection, -4)
        maxSection = maxOf(maxSection, 20)

        // Create BlendingData using NBT serialization
        val blendingData = createBlendingDataFromNBT(minSection, maxSection)

        // Set the blending data on the chunk using reflection
        setBlendingDataViaReflection(levelChunk, blendingData)

        // Mark chunk as unsaved
        levelChunk.markUnsaved()

        plugin.logger.info(
            "Set blending data for chunk (${chunk.x}, ${chunk.z}): " +
                    "minSection=$minSection, maxSection=$maxSection"
        )
    }

    /**
     * Creates BlendingData by deserializing from NBT using the CODEC
     */
    private fun createBlendingDataFromNBT(minSection: Int, maxSection: Int): BlendingData {
        return try {
            // Create NBT tag with blending data structure
            val blendingTag = CompoundTag()
            blendingTag.putInt("min_section", minSection)
            blendingTag.putInt("max_section", maxSection)

            // Use the Packed.CODEC to deserialize
            val dynamic = Dynamic(NbtOps.INSTANCE, blendingTag)
            val result = BlendingData.Packed.CODEC.parse(dynamic)

            // Get the result or throw if parsing failed
            var packed = result.getOrThrow { error ->
                RuntimeException("Failed to parse BlendingData: $error")
            }

            BlendingData.unpack(packed)!!
        } catch (e: Exception) {
            plugin.logger.severe("Could not create BlendingData: ${e.message}")
            e.printStackTrace()
            throw RuntimeException("Failed to create BlendingData", e)
        }
    }

    /**
     * Sets the blending data field on a LevelChunk using reflection
     */
    private fun setBlendingDataViaReflection(chunk: LevelChunk, blendingData: BlendingData) {
        try {
            // Find the blendingData field in ChunkAccess (superclass of LevelChunk)
            val superClass = chunk.javaClass.superclass // ChunkAccess
            val field = findBlendingDataField(superClass)
            field.isAccessible = true
            field.set(chunk, blendingData)
        } catch (e: NoSuchFieldException) {
            plugin.logger.severe("Could not find blendingData field: ${e.message}")
            throw e
        } catch (e: IllegalAccessException) {
            plugin.logger.severe("Could not access blendingData field: ${e.message}")
            throw e
        }
    }

    /**
     * Recursively searches for the blendingData field in the class hierarchy
     */
    private fun findBlendingDataField(clazz: Class<*>): Field {
        return try {
            clazz.getDeclaredField("blendingData")
        } catch (e: NoSuchFieldException) {
            // Try superclass if not found
            val superClass = clazz.superclass
            if (superClass != null) {
                findBlendingDataField(superClass)
            } else {
                throw NoSuchFieldException("blendingData field not found in class hierarchy")
            }
        }
    }

    /**
     * Batch process to set blending data for multiple chunks
     */
    fun setBlendingDataForRegion(
        world: World,
        minChunkX: Int,
        minChunkZ: Int,
        maxChunkX: Int,
        maxChunkZ: Int
    ) {
        var processed = 0
        val total = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1)

        for (x in minChunkX..maxChunkX) {
            for (z in minChunkZ..maxChunkZ) {
                try {
                    val chunk = world.getChunkAt(x, z)
                    setChunkBlendingData(chunk)
                    processed++

                    if (processed % 50 == 0) {
                        plugin.logger.info("Blending data progress: $processed / $total chunks")
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to set blending data for chunk ($x, $z): ${e.message}")
                }
            }
        }

        plugin.logger.info("Completed setting blending data for $processed / $total chunks")
    }
}