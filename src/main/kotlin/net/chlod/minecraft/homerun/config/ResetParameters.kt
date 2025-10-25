package net.chlod.minecraft.homerun.config

import net.chlod.minecraft.homerun.config.selectors.ChunkSelectorSetting
import net.chlod.minecraft.homerun.config.selectors.FromWorldSpawnSelector
import net.chlod.minecraft.homerun.config.selectors.SpecificChunkSelector
import org.bukkit.configuration.serialization.ConfigurationSerializable

class ResetParameters(
    /**
     * The list of chunks to retain. Each entry defines a method for selecting chunks to retain.
     */
    val retainedChunks: List<ChunkSelectorSetting>,
    /**
     * The name of the world to reset from. By default, this is the default world
     * (the `level-name` value in the server.properties file). Use this if you want
     * to reset a world back to a specific set of chunks every time, but don't need
     * to keep changes to those chunks after resets.
     */
    val world: String? = null,
    /**
     * The pattern to follow for newly-created worlds. Pick a pattern which avoids
     * collisions, otherwise worlds may overwrite each other.
     *
     * Some level of templating is allowed. See documentation for details.
     */
    val targetWorldPattern: String? = null,
    /**
     * Whether the default world set in server.properties should be the newly-created
     * world after a reset.
     */
    val modifyServerProperties: Boolean? = null,
    /**
     * Whether to automatically restart the server after a reset. This will require a
     * valid restart script under the server's spigot.yml configuration.
     */
    val restart: Boolean? = null,
    /**
     * Behavior for players located outside of retained chunks when a reset occurs.
     * "kill" will kill them, "spawn" will teleport them to the world spawn,
     * "ignore" will leave them where they are, and "highest" will teleport them
     * to the highest non-air block at their current X/Z coordinates.
     */
    val outsidePlayerBehavior: OutsidePlayerBehavior? = null,
    /**
     * Whether to reset the Nether dimension along with the Overworld.
     */
    val netherBehavior: DimensionResetBehavior? = null,
    /**
     * Whether to reset the End dimension along with the Overworld.
     */
    val endBehavior: DimensionResetBehavior? = null
) : ConfigurationSerializable {

    companion object {

        @JvmStatic
        val SELECTORS = listOf(
            FromWorldSpawnSelector,
            SpecificChunkSelector
        )

        @Suppress("unused")
        @JvmStatic
        fun deserialize(args: Map<String, Any>): ResetParameters {
            val retainedChunksRaw = args["retained_chunks"]
            val retainedChunks: MutableList<ChunkSelectorSetting> = ArrayList()
            if (retainedChunksRaw is List<*>) {
                for (selector in retainedChunksRaw) {
                    var deserialized = false
                    for (selectorType in SELECTORS) {
                        try {
                            val result = selectorType.javaClass
                                .getMethod("deserialize", Map::class.java)
                                .invoke(selectorType, selector)
                            retainedChunks.add(result as ChunkSelectorSetting)
                            deserialized = true
                            break
                        } catch (_: IllegalArgumentException) {
                            // Try the next selector type
                        } catch (e: java.lang.reflect.InvocationTargetException) {
                            // Unwrap and check if it's an IllegalArgumentException
                            if (e.cause is IllegalArgumentException) {
                                // Try the next selector type
                                continue
                            }
                            throw IllegalArgumentException("can't deserialize selector '$selector'", e)
                        } catch (e: Exception) {
                            throw IllegalArgumentException("can't deserialize selector '$selector'", e)
                        }
                    }
                    if (!deserialized) {
                        throw IllegalArgumentException("can't deserialize selector '$selector' (no matching selector type)")
                    }
                }
            }

            if (retainedChunks.isEmpty()) {
                throw IllegalArgumentException("At least one chunk retention selector must be specified")
            }

            val outsidePlayerBehavior = when (val behavior = args["outside_player_behavior"]) {
                is String -> OutsidePlayerBehavior.valueOf(behavior.uppercase())
                else -> null
            }
            val netherBehavior = when (val behavior = args["nether_behavior"]) {
                is String -> DimensionResetBehavior.valueOf(behavior.uppercase())
                else -> null
            }
            val endBehavior = when (val behavior = args["end_behavior"]) {
                is String -> DimensionResetBehavior.valueOf(behavior.uppercase())
                else -> null
            }

            val world = args["world"] as String?
            val targetWorldPattern = args["target_world_pattern"] as String?
            val modifyServerProperties = args["modify_server_properties"] as Boolean?
            val restart = args["restart"] as Boolean?

            return ResetParameters(
                retainedChunks,
                world,
                targetWorldPattern,
                modifyServerProperties,
                restart,
                outsidePlayerBehavior,
                netherBehavior,
                endBehavior
            )
        }

    }

    override fun serialize(): Map<String?, Any?> {
        val serializedSelectors: MutableList<Map<String?, Any?>> = ArrayList()
        for (selector in retainedChunks) {
            serializedSelectors.add(selector.serialize())
        }

        return mapOf(
            "retained_chunks" to serializedSelectors,
            "world" to world,
            "target_world_pattern" to targetWorldPattern,
            "modify_server_properties" to modifyServerProperties,
            "restart" to restart,
            "outside_player_behavior" to outsidePlayerBehavior?.name?.lowercase(),
            "nether_behavior" to netherBehavior?.name?.lowercase(),
            "end_behavior" to endBehavior?.name?.lowercase()
        ).filter { it.value != null }
    }

    enum class OutsidePlayerBehavior {
        /**
         * Teleport players outside of the reset area back to their spawn point. If their
         * spawn point is also outside of the reset area, they will be teleported to the world spawn.
         * This is the default.
         */
        SPAWN,

        /**
         * Kill all players outside of the reset area. This will result in lost items.
         */
        KILL,

        /**
         * Teleport players outside of the reset area to the world spawn point.
         */
        WORLD_SPAWN,

        /**
         * Do nothing for players outside of the reset area. This may result in players being stuck
         * in chunks, leading to suffocation.
         */
        IGNORE,

        /**
         * Teleport players to the highest non-air block at their current X/Z coordinates.
         */
        HIGHEST,

        /**
         * Teleport players to the closest valid space at their current X/Z coordinates.
         */
        CLOSEST
    }

    /**
     * Behavior for resetting a dimension. This only applies when resetting an overworld dimension; it will be
     * ignored if the source world is a Nether or End dimension.
     */
    enum class DimensionResetBehavior {
        /**
         * Reset the dimension in accordance with the chunk retention rules.
         */
        NORMAL,

        /**
         * Completely wipe the dimension and regenerate it based on the new world's seed.
         */
        WIPE,

        /**
         * Copy the entire dimension. This will effectively duplicate the Nether of the source world, meaning
         * a significant use in storage and a much longer reset time if the Nether is large.
         */
        COPY,

        /**
         * Renames the source world's Nether folder to match the target world's Nether folder. This avoids
         * the storage and time cost of copying, but will require manual intervention to undo a reset.
         */
        RENAME
    }

}