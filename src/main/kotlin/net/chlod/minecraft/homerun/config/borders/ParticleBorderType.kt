package net.chlod.minecraft.homerun.config.borders

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ColorParser
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.helpers.RetainedChunkCache
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.data.BlockData
import org.bukkit.craftbukkit.inventory.SerializableMeta
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor

class ParticleBorderType(
    tickPeriod: Long?,
    val particle: Particle,
    val particleData: Any? = null,
    val rawParticleData: Any? = null,
    val distanceBlocks: Int? = null,
    val height: Int? = null,
    val pattern: String? = null,
    val count: Int? = null,
) : ResetBorder(BorderType.PARTICLES, tickPeriod) {

    enum class Pattern {
        DOT,
        VERTICAL,
        RANDOM
    }

    val playerChunkAdjacencies: MutableMap<UUID, Set<RetainedChunkCache.ChunkAdjacency>> = mutableMapOf()
    val playerActiveBorderBlocks: MutableMap<UUID, List<Pair<Int, Int>>> = mutableMapOf()

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun deserialize(args: Map<String, Any>): ParticleBorderType {
            val tickPeriod = (args["period"] as Int?)?.toLong()

            val particle = args["particle"] as? String
                ?: throw IllegalArgumentException("'particle' is required for 'particles' border type")
            val particleEnum = try {
                Particle.valueOf(particle.uppercase())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "Invalid particle type: '$particle'; see https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Particle.html for possible options"
                )
            }

            val rawParticleData = args["data"]
            // Test particle data deserialization to make sure the provided data is valid for the particle type.
            @Suppress("UNCHECKED_CAST")
            val particleData = deserializeParticleData(particleEnum, rawParticleData)

            val distanceBlocks = args["distance_blocks"] as? Int
            val height = args["height"] as? Int
            if (height != null && height <= 0) {
                throw IllegalArgumentException("height must be a positive integer")
            }
            val count = args["count"] as? Int

            val pattern = args["pattern"] as? String
            if (pattern != null) {
                try {
                    Pattern.valueOf(pattern.uppercase())
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException(
                        "Invalid pattern type: $pattern; valid options are ${
                            Pattern.entries.joinToString(", ")
                        }"
                    )
                }
            }

            return ParticleBorderType(
                tickPeriod,
                particleEnum,
                particleData,
                rawParticleData,
                distanceBlocks,
                height,
                pattern,
                count
            )
        }

        private fun deserializeParticleData(particle: Particle, args: Any?): Any? {
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            return when (particle.dataType) {
                Void::class.java -> null

                java.lang.Float::class.java -> {
                    if (args !is Number) {
                        throw IllegalArgumentException("Particle data for particle type ${particle.name} must be a number")
                    } else {
                        args.toFloat()
                    }
                }

                Integer::class.java -> {
                    args as? Int
                        ?: throw IllegalArgumentException("Particle data for particle type ${particle.name} must be an integer")
                }

                Color::class.java -> {
                    ColorParser.parseColor(args)
                }

                BlockData::class.java -> {
                    if (args is String) {
                        val material = Material.matchMaterial(args)
                            ?: throw IllegalArgumentException("Invalid block type: '$args'")
                        if (!material.isBlock) {
                            throw IllegalArgumentException("Material '$args' is not a block type")
                        }
                        return material.createBlockData()
                    }

                    if (args !is Map<*, *>) {
                        throw IllegalArgumentException("Block data is not a string or map")
                    }
                    if (args.keys.any { it !is String }) {
                        throw IllegalArgumentException("BlockData map contains non-string keys")
                    }

                    val block = args["block"] as? String
                        ?: throw IllegalArgumentException("'block' is required for particle type ${particle.name}")
                    val data = args["data"] as? String
                    val material = Material.matchMaterial(block)
                        ?: throw IllegalArgumentException("Invalid block type: '$block'")
                    if (!material.isBlock) {
                        throw IllegalArgumentException("Material '$block' is not a block type")
                    }
                    val blockData = if (data != null) {
                        try {
                            material.createBlockData(data)
                        } catch (e: Exception) {
                            throw IllegalArgumentException("Invalid block data for block type '$block': ${e.message}")
                        }
                    } else {
                        material.createBlockData()
                    }
                    blockData
                }

                ItemStack::class.java -> {
                    if (args is String) {
                        val material = Material.matchMaterial(args)
                            ?: throw IllegalArgumentException("Invalid item type: '$args'")
                        if (!material.isItem) {
                            throw IllegalArgumentException("Material '$args' is not an item type")
                        }
                        return ItemStack(material)
                    }

                    if (args !is Map<*, *>) {
                        throw IllegalArgumentException("ItemStack data is not a string or map")
                    }
                    if (args.keys.any { it !is String }) {
                        throw IllegalArgumentException("ItemStack data map contains non-string keys")
                    }
                    val materialStr = args["item"] as? String
                        ?: throw IllegalArgumentException("'item' is required for particle type ${particle.name}")
                    val material = Material.matchMaterial(materialStr)
                        ?: throw IllegalArgumentException("Invalid item type: '$materialStr'")
                    if (!material.isItem) {
                        throw IllegalArgumentException("Material '$materialStr' is not an item type")
                    }

                    val itemStack = ItemStack(material)

                    val rawItemData = args["data"]

                    if (rawItemData != null) {
                        if (rawItemData !is Map<*, *>) {
                            throw IllegalArgumentException("Item data is not a map")
                        }
                        if (rawItemData.keys.any { it !is String }) {
                            throw IllegalArgumentException("Item data contains non-string keys")
                        }

                        val finalItemData = rawItemData.toMutableMap()
                        // If there's no meta-type, fill it in.
                        if (!finalItemData.containsKey("meta-type")) {
                            finalItemData["meta-type"] = "UNSPECIFIC"
                        }

                        @Suppress("UNCHECKED_CAST")
                        itemStack.setItemMeta(
                            SerializableMeta.deserialize(
                                finalItemData as? Map<String, Any>
                            )
                        )
                    }

                    itemStack
                }

                Particle.DustOptions::class.java -> {
                    if (args == null) {
                        Particle.DustOptions(Color.RED, 1.0f)
                    }

                    if (args !is Map<*, *>) {
                        throw IllegalArgumentException("Dust options is not a map")
                    }
                    if (args.keys.any { it !is String }) {
                        throw IllegalArgumentException("Dust options map contains non-string keys")
                    }
                    val color = ColorParser.parseColor(args["color"])
                        ?: throw IllegalArgumentException("'color' is required for particle type ${particle.name}")
                    val size = args["size"] as? Number ?: 1.0

                    Particle.DustOptions(color, size.toFloat())
                }

                Particle.DustTransition::class.java -> {
                    if (args !is Map<*, *>) {
                        throw IllegalArgumentException("Dust transition options is not a map")
                    }
                    if (args.keys.any { it !is String }) {
                        throw IllegalArgumentException("Dust transition options map contains non-string keys")
                    }
                    val fromColor = ColorParser.parseColor(args["from_color"])
                        ?: throw IllegalArgumentException("'from_color' is required for particle type ${particle.name}")
                    val toColor = ColorParser.parseColor(args["to_color"])
                        ?: throw IllegalArgumentException("'to_color' is required for particle type ${particle.name}")
                    val size = args["size"] as? Number ?: 1.0

                    Particle.DustTransition(fromColor, toColor, size.toFloat())
                }


                // Vibration::class.java
                else ->
                    throw IllegalArgumentException("Particle currently unsupported: ${particle.name}")
            }
        }
    }

    override fun serialize(): Map<String?, Any?> {
        return super.serialize() + mapOf(
            "particle" to particle.name.lowercase(),
            "data" to rawParticleData,
            "distance_blocks" to distanceBlocks,
            "height" to height,
            "pattern" to pattern
        )
    }

    fun spawnParticles(player: Player, location: Location) {
        if (particle.dataType == Void::class.java) {
            player.spawnParticle(
                particle,
                location,
                count ?: 1
            )
        } else {
            player.spawnParticle(
                particle,
                location,
                count ?: 1,
                particleData
            )
        }
    }

    override fun doBorderUpdate(
        plugin: Homerun,
        resetRule: ResetRule,
        event: PlayerMoveEvent,
        from: Location,
        to: Location
    ) {
        updatePlayerChunkAdjacency(plugin, event, from, to)
        updatePlayerActiveBorderBlocks(event)
    }

    fun updatePlayerChunkAdjacency(plugin: Homerun, event: PlayerMoveEvent, from: Location, to: Location) {
        val playerChunkX = to.chunk.x
        val playerChunkZ = to.chunk.z
        if (
            from.chunk.x == playerChunkX &&
            from.chunk.z == playerChunkZ &&
            playerChunkAdjacencies.containsKey(event.player.uniqueId)
        ) {
            // Player hasn't changed chunks, so we don't need to update borders.
            return
        }

        val toCheck = mutableSetOf<Pair<Int, Int>>()
        for (dx in -2..2) {
            for (dz in -2..2) {
                toCheck.add(Pair(playerChunkX + dx, playerChunkZ + dz))
            }
        }

        val currentChunkAdjacencies = plugin.retainedChunkCache.getChunkAdjacency(
            event.player.world,
            toCheck
        )?.values ?: return
        if (currentChunkAdjacencies.all { it.directions.isEmpty() }) {
            playerChunkAdjacencies.remove(event.player.uniqueId)
        } else {
            playerChunkAdjacencies[event.player.uniqueId] = currentChunkAdjacencies.toSet()
        }
    }

    fun updatePlayerActiveBorderBlocks(event: PlayerMoveEvent) {
        val adjacencies = playerChunkAdjacencies[event.player.uniqueId] ?: return
        val nearbyBorderBlocks = mutableSetOf<Pair<Int, Int>>()

        for (adjacency in adjacencies) {
            if (adjacency.retained) continue
            nearbyBorderBlocks.addAll(
                adjacency.getBorderBlocks()
                    .map {
                        Pair(
                            adjacency.x * 16 + it.first,
                            adjacency.z * 16 + it.second
                        )
                    }
                    .filter {
                        event.player.location.distance(
                            Location(
                                event.player.world,
                                it.first.toDouble() + 0.5,
                                event.player.location.y,
                                it.second.toDouble() + 0.5
                            )
                        ) <= (distanceBlocks ?: 3)
                    }
            )
        }
        playerActiveBorderBlocks[event.player.uniqueId] = nearbyBorderBlocks.toList()
    }

    override fun onTick(plugin: Homerun, resetRule: ResetRule) {
        val height = this.height ?: 1

        val heightCheckCeiling = 2
        val heightMin = if (height < 3) 0 else -floor(height / 2.0).toInt()
        val heightMax = if (height < 3) height else ceil(height / 2.0).toInt()

        // Show the particle borders for each player with this border type
        for ((playerId, borderBlocks) in playerActiveBorderBlocks) {
            val player = plugin.server.getPlayer(playerId) ?: continue
            val world = player.world
            for (borderBlock in borderBlocks) {
                val x = borderBlock.first.toDouble()
                val y = player.location.y
                val z = borderBlock.second.toDouble()

                var heightOffset = 0
                if (height <= heightCheckCeiling) {
                    // Find the highest block near the player and spawn the particle above it, so that the particle
                    // is visible even if the player is underground. We only cover height < 2 because this is the most
                    // common use case that may obscure most, if not all, of the particle effects.
                    for (i in 0..<heightCheckCeiling) {
                        if (
                            !world.getBlockAt(
                                borderBlock.first,
                                player.location.blockY + heightOffset,
                                borderBlock.second
                            ).isPassable
                        ) {
                            heightOffset += 1
                        }
                    }
                }
                for (i in heightMin..<heightMax) {
                    val patternOffsets = getParticlePatternOffsets()
                    for (offset in patternOffsets) {
                        val particleLocation = Location(
                            player.world,
                            x + offset.first,
                            y + heightOffset + i + offset.second,
                            z + offset.third
                        )
                        spawnParticles(player, particleLocation)
                    }
                }
            }
        }
    }

    fun getParticlePatternOffsets(): List<Triple<Double, Double, Double>> {
        val patternEnum = Pattern.valueOf((pattern ?: "random").uppercase())
        return when (patternEnum) {
            Pattern.DOT -> listOf(Triple(0.5, 0.5, 0.5))

            Pattern.VERTICAL -> {
                val n = 3
                val offsets = mutableListOf<Triple<Double, Double, Double>>()
                for (i in 1..n) {
                    offsets.add(Triple(0.5, (1.0 / n) * i, 0.5))
                }
                offsets
            }

            Pattern.RANDOM -> {
                listOf(
                    Triple(
                        Math.random(),
                        Math.random(),
                        Math.random()
                    )
                )
            }
        }
    }
}