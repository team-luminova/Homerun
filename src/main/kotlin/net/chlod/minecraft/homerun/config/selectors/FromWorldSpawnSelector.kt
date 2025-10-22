package net.chlod.minecraft.homerun.config.selectors

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.World
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Selects chunks to retain based on their distance from the world spawn point.
 */
class FromWorldSpawnSelector : ChunkSelectorSetting {

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun deserialize(args: Map<String, Any>): FromWorldSpawnSelector {
            val expression = args["from_world_spawn"]
            if (expression is Int) {
                return FromWorldSpawnSelector(expression)
            }
            if (expression is Map<*, *>) {
                val x = expression["x"]
                val z = expression["z"]

                if (x is Int && z is Int) {
                    return FromWorldSpawnSelector(z, x)
                }

                val north = expression["north"]
                val south = expression["south"]
                val west = expression["west"]
                val east = expression["east"]

                if (north is Int && south is Int && west is Int && east is Int) {
                    return FromWorldSpawnSelector(north, south, west, east)
                }
            }
            throw IllegalArgumentException(
                "Couldn't deserialize FromWorldSpawnSelector: invalid setting (must be range, x/z range, or north/south/west/east range)"
            )
        }
    }

    private enum class Mode {
        SYMMETRICAL,
        ASYMMETRICAL,
        FULLY_CUSTOM
    }

    /**
     * North range (negative Z direction)
     */
    val negativeZRange: Int
    /**
     * South range (positive Z direction)
     */
    val positiveZRange: Int
    /**
     * West range (negative X direction)
     */
    val negativeXRange: Int
    /**
     * East range (positive X direction)
     */
    val positiveXRange: Int

    private val mode: Mode

    /**
     * Constructor for symmetrical range in all directions.
     * @param range The range in chunks to retain from the world spawn in all directions.
     *  This is a diameter. If there are an odd number of chunks, the uneven chunk is added
     *  to the north and west.
     */
    constructor(range: Int) {
        mode = Mode.SYMMETRICAL
        negativeZRange = ceil(range / 2.0).toInt()
        positiveZRange = floor(range / 2.0).toInt()
        negativeXRange = ceil(range / 2.0).toInt()
        positiveXRange = floor(range / 2.0).toInt()
    }

    /**
     * Constructor for separate Z and X ranges.
     * @param zRange The range in chunks to retain from the world spawn in the Z direction.
     *  This is a diameter. If there are an odd number of chunks, the uneven chunk is added
     *  to the north.
     * @param xRange The range in chunks to retain from the world spawn in the X direction.
     *  This is a diameter. If there are an odd number of chunks, the uneven chunk is added
     *  to the west.
     */
    constructor(
        zRange: Int,
        xRange: Int
    ) {
        mode = Mode.ASYMMETRICAL
        negativeZRange = ceil(zRange / 2.0).toInt()
        positiveZRange = floor(zRange / 2.0).toInt()
        negativeXRange = ceil(xRange / 2.0).toInt()
        positiveXRange = floor(xRange / 2.0).toInt()
    }

    /**
     * Constructor for fully custom ranges in all four directions.
     * @param north The range in chunks to retain to the north (negative Z direction) from the world spawn.
     * @param south The range in chunks to retain to the south (positive Z direction) from the world spawn.
     * @param west The range in chunks to retain to the west (negative X direction) from the world spawn.
     * @param east The range in chunks to retain to the east (positive X direction) from the world spawn.
     */
    constructor(north: Int, south: Int, west: Int, east: Int) {
        mode = Mode.FULLY_CUSTOM
        negativeZRange = north
        positiveZRange = south
        negativeXRange = west
        positiveXRange = east
    }

    override fun getHumanReadableDescription(): String {
        return when (mode) {
            Mode.SYMMETRICAL -> "a ${negativeZRange + positiveZRange}x${negativeXRange + positiveXRange} chunk area centered on spawn"
            Mode.ASYMMETRICAL -> "a ${negativeXRange + positiveXRange} (X) by ${negativeZRange + positiveZRange} (Z) chunk area centered on spawn"
            Mode.FULLY_CUSTOM -> "an area extending $negativeZRange chunks north, $positiveZRange chunks south, " +
                    "$negativeXRange chunks west, and $positiveXRange chunks east from spawn"
        }
    }

    override fun getRetainedChunks(
        plugin: Homerun,
        world: World
    ): Set<Pair<Int, Int>> {
        val retainedChunks = mutableSetOf<Pair<Int, Int>>()
        val spawnChunk = world.spawnLocation.chunk
        val minX = spawnChunk.x - negativeXRange
        val maxX = spawnChunk.x + positiveXRange - 1
        val minZ = spawnChunk.z - negativeZRange
        val maxZ = spawnChunk.z + positiveZRange - 1

        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                retainedChunks.add(Pair(x, z))
            }
        }

        return retainedChunks
    }

    override fun serialize(): Map<String?, Any?> {
        val result = mutableMapOf<String?, Any?>()
        when (mode) {
            Mode.SYMMETRICAL -> {
                result["from_world_spawn"] = negativeZRange + positiveZRange
            }
            Mode.ASYMMETRICAL -> {
                result["from_world_spawn"] = mapOf(
                    "x" to (negativeXRange + positiveXRange),
                    "z" to (negativeZRange + positiveZRange)
                )
            }
            Mode.FULLY_CUSTOM -> {
                result["from_world_spawn"] = mapOf(
                    "north" to negativeZRange,
                    "south" to positiveZRange,
                    "west" to negativeXRange,
                    "east" to positiveXRange
                )
            }
        }
        return result
    }


}