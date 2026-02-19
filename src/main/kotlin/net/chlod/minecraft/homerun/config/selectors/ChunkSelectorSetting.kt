package net.chlod.minecraft.homerun.config.selectors

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.World
import org.bukkit.configuration.serialization.ConfigurationSerializable

abstract class ChunkSelectorSetting : ConfigurationSerializable {

    companion object {
        @JvmStatic
        fun deserializeDimensions(args: Map<String, Any>): List<World.Environment>? {
            return when (val dims = args["dimensions"]) {
                is List<*> -> dims.mapNotNull {
                    if (it is String) {
                        try {
                            World.Environment.valueOf(it.uppercase())
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    } else {
                        null
                    }
                }

                is String -> {
                    try {
                        listOf(World.Environment.valueOf(dims.uppercase()))
                    } catch (e: IllegalArgumentException) {
                        listOf(World.Environment.NORMAL)
                    }
                }

                else -> null
            }
        }

        fun getHumanReadableDimensionSuffix(dimensions: List<World.Environment>?): String {
            return when {
                dimensions == null -> ""
                else -> " in the " + dimensions.joinToString(", ") {
                    it.name.lowercase()
                        .split("_").joinToString(" ") { word ->
                            word.replaceFirstChar { c -> c.uppercase() }
                        }
                }
            }
        }
    }

    abstract fun getHumanReadableDescription(): String
    abstract fun getRetainedChunks(plugin: Homerun, world: World): Set<Pair<Int, Int>>

}