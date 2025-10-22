package net.chlod.minecraft.homerun.data

import net.chlod.minecraft.homerun.config.ResetParameters
import org.bukkit.Location
import org.bukkit.configuration.serialization.ConfigurationSerializable

/**
 * TODO: Split out chunks, spawnLocation, outsidePlayerBehavior and different copy modes into different interfaces.
 */
class WorldResetData(
    val sourceWorld: String,
    val targetWorld: String,
    val behavior: WorldResetBehavior,
    val chunks: List<Pair<Int, Int>>? = null,
    val spawnLocation: Location? = null,
    val outsidePlayerBehavior: ResetParameters.OutsidePlayerBehavior? = null
) : ConfigurationSerializable {

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Object>): WorldResetData {
            val sourceWorld = args["sourceWorld"] as String
            val targetWorld = args["targetWorld"] as String
            val chunksRaw = args["chunks"] as List<*>
            val chunks = chunksRaw.map {
                val pair = it as List<*>
                Pair((pair[0] as Int), (pair[1] as Int))
            }
            val spawnLocationRaw = args["spawnLocation"] as List<*>
            val spawnLocation = spawnLocationRaw.map { it as Double }

            return when (args["behavior"]) {
                "NORMAL" -> WorldResetData(
                    sourceWorld,
                    targetWorld,
                    WorldResetBehavior.NORMAL,
                    chunks,
                    Location(
                        null,
                        spawnLocation[0],
                        spawnLocation[1],
                        spawnLocation[2],
                        spawnLocation[3].toFloat(),
                        spawnLocation[4].toFloat()
                    ),
                    when (val behavior = args["outsidePlayerBehavior"]) {
                        is String -> ResetParameters.OutsidePlayerBehavior.valueOf(behavior)
                        else -> null
                    }
                )
                else -> WorldResetData(
                    sourceWorld,
                    targetWorld,
                    WorldResetBehavior.valueOf(args["behavior"] as String),
                    null,
                    null,
                    null
                )
            }
        }
    }

    override fun serialize(): Map<String?, Any?> {
        return when (behavior) {
            WorldResetBehavior.NORMAL -> mapOf(
                "sourceWorld" to sourceWorld,
                "targetWorld" to targetWorld,
                "behavior" to behavior.name,
                "chunks" to chunks!!.map { listOf(it.first, it.second) },
                "spawnLocation" to listOf(
                    spawnLocation!!.x,
                    spawnLocation.y,
                    spawnLocation.z,
                    spawnLocation.yaw.toDouble(),
                    spawnLocation.pitch.toDouble()
                ),
                "outsidePlayerBehavior" to outsidePlayerBehavior?.name
            )
            else -> mapOf(
                "sourceWorld" to sourceWorld,
                "targetWorld" to targetWorld,
                "behavior" to behavior.name
            )
        }
    }

    enum class WorldResetBehavior {
        /**
         * Performs a normal reset of the source world to the target world, copying chunks as needed.
         */
        NORMAL,

        /**
         * Copies the source world to the target world without deleting the source world.
         * @see ResetParameters.DimensionResetBehavior.COPY
         * */
        COPY,

        /**
         * Renames the source world to the target world.
         * @see ResetParameters.DimensionResetBehavior.RENAME
         */
        RENAME
    }

}