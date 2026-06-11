package net.chlod.minecraft.homerun.config.borders

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.math.Adjacency
import org.bukkit.HeightMap
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.configuration.serialization.ConfigurationSerializable
import org.bukkit.event.player.PlayerMoveEvent

abstract class ResetBorder(
    val type: BorderType,
    /**
     * User-set tick frequency. If you want a non-nullable value based on the default tick
     * frequency, use [tickPeriod] instead.
     */
    val period: Long? = null
) : ConfigurationSerializable {

    companion object {
        const val DEFAULT_TICK_PERIOD = 1L

        @JvmStatic
        fun deserializeType(args: Map<String, Any>): BorderType {
            val typeString = args["type"] as String
            return BorderType.valueOf(typeString.uppercase())
        }

        @JvmStatic
        fun getBorderCoordinates(
            world: World,
            chunkPos: Pair<Int, Int>,
            direction: Adjacency.Direction,
            heightmap: HeightMap = HeightMap.OCEAN_FLOOR
        ): List<Location> {
            val locations = mutableListOf<Location>()
            when (direction) {
                Adjacency.Direction.NORTH -> {
                    // Show border on north side of chunk
                    for (x in 0..15) {
                        val blockY = world.getHighestBlockYAt(
                            chunkPos.first * 16 + x,
                            chunkPos.second * 16,
                            heightmap
                        )
                        locations.add(
                            Location(
                                world,
                                chunkPos.first * 16 + x + 0.0,
                                blockY + 0.0,
                                chunkPos.second * 16 + 0.0
                            )
                        )
                    }
                }

                Adjacency.Direction.SOUTH -> {
                    // Show border on south side of chunk
                    for (x in 0..15) {
                        val blockY = world.getHighestBlockYAt(
                            chunkPos.first * 16 + x,
                            chunkPos.second * 16 + 15,
                            heightmap
                        )
                        locations.add(
                            Location(
                                world,
                                chunkPos.first * 16 + x + 0.0,
                                blockY + 0.0,
                                chunkPos.second * 16 + 15.0
                            )
                        )
                    }
                }

                Adjacency.Direction.EAST -> {
                    // Show border on east side of chunk
                    for (z in 0..15) {
                        val blockY = world.getHighestBlockYAt(
                            chunkPos.first * 16 + 15,
                            chunkPos.second * 16 + z,
                            heightmap
                        )
                        locations.add(
                            Location(
                                world,
                                chunkPos.first * 16 + 15.0,
                                blockY + 0.0,
                                chunkPos.second * 16 + z + 0.0
                            )
                        )
                    }
                }

                Adjacency.Direction.WEST -> {
                    // Show border on west side of chunk
                    for (z in 0..15) {
                        val blockY = world.getHighestBlockYAt(
                            chunkPos.first * 16,
                            chunkPos.second * 16 + z,
                            heightmap
                        )
                        locations.add(
                            Location(
                                world,
                                chunkPos.first * 16 - 0.0,
                                blockY + 0.0,
                                chunkPos.second * 16 + z + 0.0
                            )
                        )
                    }
                }

                Adjacency.Direction.NORTHEAST -> {
                    // Show border on northeast corner of chunk
                    val blockY = world.getHighestBlockYAt(
                        chunkPos.first * 16 + 15,
                        chunkPos.second * 16,
                        heightmap
                    )
                    locations.add(
                        Location(
                            world,
                            chunkPos.first * 16 + 15.0,
                            blockY + 0.0,
                            chunkPos.second * 16 - 0.0
                        )
                    )
                }

                Adjacency.Direction.NORTHWEST -> {
                    // Show border on northwest corner of chunk
                    val blockY = world.getHighestBlockYAt(
                        chunkPos.first * 16,
                        chunkPos.second * 16,
                        heightmap
                    )
                    locations.add(
                        Location(
                            world,
                            chunkPos.first * 16 - 0.0,
                            blockY + 0.0,
                            chunkPos.second * 16 - 0.0
                        )
                    )
                }

                Adjacency.Direction.SOUTHEAST -> {
                    // Show border on southeast corner of chunk
                    val blockY = world.getHighestBlockYAt(
                        chunkPos.first * 16 + 15,
                        chunkPos.second * 16 + 15,
                        heightmap
                    )
                    locations.add(
                        Location(
                            world,
                            chunkPos.first * 16 + 15.0,
                            blockY + 0.0,
                            chunkPos.second * 16 + 15.0
                        )
                    )
                }

                Adjacency.Direction.SOUTHWEST -> {
                    // Show border on southwest corner of chunk
                    val blockY = world.getHighestBlockYAt(
                        chunkPos.first * 16,
                        chunkPos.second * 16 + 15,
                        heightmap
                    )
                    locations.add(
                        Location(
                            world,
                            chunkPos.first * 16 - 0.0,
                            blockY + 0.0,
                            chunkPos.second * 16 + 15.0
                        )
                    )
                }

            }
            return locations
        }
    }

    val tickPeriod
        get() = period ?: DEFAULT_TICK_PERIOD

    enum class BorderType {
        HIGHEST_BLOCK,
        PARTICLES,
        CONSUMABLE,
        CHAT_ANNOUNCE,
        ACTION_BAR_ANNOUNCE,
    }

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "type" to type.name.lowercase(),
            "period" to period,
        ).filter { it.value != null }
    }

    /**
     * This check is run when the player moves blocks. Return quickly to optimize performance.
     */
    abstract fun doBorderUpdate(
        plugin: Homerun,
        resetRule: ResetRule,
        event: PlayerMoveEvent,
        from: Location,
        to: Location
    )

    /**
     * This check is run on every [tickPeriod] ticks. If you don't need to do anything, leave this
     * empty to help with performance.
     */
    open fun onTick(plugin: Homerun, resetRule: ResetRule) {
        // Only needed for particle dust border type, so do nothing by default
    }

}