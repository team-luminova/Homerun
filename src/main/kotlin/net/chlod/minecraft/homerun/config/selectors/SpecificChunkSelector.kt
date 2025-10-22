package net.chlod.minecraft.homerun.config.selectors

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.World

class SpecificChunkSelector(val chunks: List<Pair<Int, Int>>, val verbose: Boolean?) : ChunkSelectorSetting {

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun deserialize(args: Map<String, Any>): SpecificChunkSelector {
            val expression = args["specific_chunks"]
            if (expression is List<*>) {
                val chunks: MutableList<Pair<Int, Int>> = ArrayList()
                var verbose = false
                for (item in expression) {
                    if (item is Map<*, *>) {
                        verbose = true
                        val x = item["x"]
                        val z = item["z"]
                        if (x is Int && z is Int) {
                            chunks.add(Pair(x, z))
                        } else {
                            throw IllegalArgumentException(
                                "Couldn't deserialize SpecificChunkSelector: invalid chunk coordinates"
                            )
                        }
                    }
                    if (item is List<*>) {
                        if (item.size == 2) {
                            val x = item[0]
                            val z = item[1]
                            if (x is Int && z is Int) {
                                chunks.add(Pair(x, z))
                            } else {
                                throw IllegalArgumentException(
                                    "Couldn't deserialize SpecificChunkSelector: invalid chunk coordinates"
                                )
                            }
                        } else {
                            throw IllegalArgumentException(
                                "Couldn't deserialize SpecificChunkSelector: invalid chunk coordinates"
                            )
                        }
                    }
                }

                if (!chunks.isEmpty()) {
                    return SpecificChunkSelector(chunks, verbose)
                }
            }
            throw IllegalArgumentException(
                "Couldn't deserialize SpecificChunkSelector: unknown value"
            )
        }
    }

    override fun getHumanReadableDescription(): String {
        return "the chunks: ${chunks.joinToString { "[${it.first}, ${it.second}]" }}"
    }

    override fun getRetainedChunks(
        plugin: Homerun,
        world: World
    ): Set<Pair<Int, Int>> {
        return chunks.toSet()
    }

    override fun serialize(): Map<String?, Any?> {
        val serializedChunks: MutableList<Any> = ArrayList()
        for (chunk in chunks) {
            if (verbose == true) {
                serializedChunks.add(
                    mapOf(
                        "x" to chunk.first,
                        "z" to chunk.second
                    )
                )
            } else {
                serializedChunks.add(
                    listOf(
                        chunk.first,
                        chunk.second
                    )
                )
            }
        }
        return mapOf(
            "specific_chunks" to serializedChunks
        )
    }


}