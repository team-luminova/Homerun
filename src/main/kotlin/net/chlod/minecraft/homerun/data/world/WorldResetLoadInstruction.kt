package net.chlod.minecraft.homerun.data.world

import net.chlod.minecraft.homerun.config.ResetParameters
import org.bukkit.World.Environment
import org.bukkit.configuration.serialization.ConfigurationSerializable

class WorldResetLoadInstruction(
    sourceWorld: String,
    sourceWorldEnvironmentId: Int,
    targetWorld: String,
    val targetWorldUUID: String? = null,
    val chunks: List<Pair<Int, Int>>? = null,
    val outsidePlayerBehavior: ResetParameters.OutsidePlayerBehavior? = null,
    /**
     * Metadata about sub-dimensions (Nether, End) that were reset alongside this world.
     * Only populated for overworld instructions when `netherBehavior`/`endBehavior` caused
     * sub-dimension resets. Allows downstream tasks (e.g. `WorldPostloadTask`) to resolve
     * dimension world names and UUIDs without fragile string concatenation.
     */
    val subDimensions: List<SubDimensionInfo>? = null
) : ResetLoadInstructions(ResetLoadInstructionType.RESET, sourceWorld, sourceWorldEnvironmentId, targetWorld) {

    /**
     * Metadata about a sub-dimension that was reset alongside the parent overworld.
     */
    data class SubDimensionInfo(
        /** The Bukkit world name of the sub-dimension (e.g. "world_1234_nether"). */
        val worldName: String,
        /** The UUID of the newly-created sub-dimension world, or null for rename/copy operations. */
        val worldUUID: String?,
        /** The environment type of this sub-dimension. */
        val environment: Environment,
        /** The type of reset operation performed on this sub-dimension. */
        val resetType: ResetLoadInstructionType
    ) : ConfigurationSerializable {

        override fun serialize(): Map<String?, Any?> {
            return mapOf(
                "worldName" to worldName,
                "worldUUID" to worldUUID,
                "environment" to environment.id,
                "resetType" to resetType.name.lowercase()
            )
        }

        companion object {
            @JvmStatic
            fun deserialize(args: Map<String, Any>): SubDimensionInfo {
                val worldName = args["worldName"] as String
                val worldUUID = args["worldUUID"] as? String
                val environmentId = args["environment"] as Int
                val environment = Environment.entries.first { it.id == environmentId }
                val resetType = ResetLoadInstructionType.valueOf((args["resetType"] as String).uppercase())
                return SubDimensionInfo(worldName, worldUUID, environment, resetType)
            }
        }
    }

    /**
     * Finds the [SubDimensionInfo] for the given [Environment], if one exists.
     */
    fun getSubDimension(environment: Environment): SubDimensionInfo? {
        return subDimensions?.firstOrNull { it.environment == environment }
    }

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Any>): WorldResetLoadInstruction {
            val sourceWorld = args["sourceWorld"] as String
            val sourceWorldEnvironmentId = args["sourceWorldEnvironmentId"] as Int
            val targetWorld = args["targetWorld"] as String
            val targetWorldUUID = args["targetWorldUUID"] as? String
            val chunksRaw = args["chunks"] as List<*>
            val chunks = chunksRaw.map {
                val pair = it as List<*>
                Pair((pair[0] as Int), (pair[1] as Int))
            }

            @Suppress("UNCHECKED_CAST")
            val subDimensionsRaw = args["subDimensions"] as? List<*>
            val subDimensions = subDimensionsRaw?.map {
                when (it) {
                    is SubDimensionInfo -> it
                    is Map<*, *> -> {
                        if (!it.keys.all { key -> key is String }) {
                            throw IllegalArgumentException("Sub-dimension data contains non-string keys: $it")
                        }
                        @Suppress("UNCHECKED_CAST")
                        SubDimensionInfo.deserialize(it as Map<String, Any>)
                    }

                    else -> throw IllegalArgumentException("Invalid sub-dimension data: $it")
                }
            }

            return WorldResetLoadInstruction(
                sourceWorld,
                sourceWorldEnvironmentId,
                targetWorld,
                targetWorldUUID,
                chunks,
                when (val behavior = args["outsidePlayerBehavior"]) {
                    is String -> ResetParameters.OutsidePlayerBehavior.valueOf(behavior)
                    else -> null
                },
                subDimensions
            )
        }
    }

    override fun serialize(): Map<String?, Any?> {
        return super.serialize() + mapOf(
            "targetWorldUUID" to targetWorldUUID,
            "chunks" to chunks!!.map { listOf(it.first, it.second) },
            "outsidePlayerBehavior" to outsidePlayerBehavior?.name,
            "subDimensions" to subDimensions?.map { it.serialize() }
        )
    }

}