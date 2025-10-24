package net.chlod.minecraft.homerun.data.world

import net.chlod.minecraft.homerun.config.ResetParameters
import org.bukkit.Location

class WorldResetLoadInstruction(
    sourceWorld: String,
    sourceWorldEnvironmentId: Int,
    targetWorld: String,
    val chunks: List<Pair<Int, Int>>? = null,
    val spawnLocation: Location? = null,
    val outsidePlayerBehavior: ResetParameters.OutsidePlayerBehavior? = null
) : ResetLoadInstructions(ResetLoadInstructionType.RESET, sourceWorld, sourceWorldEnvironmentId, targetWorld) {

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Object>): WorldResetLoadInstruction {
            val sourceWorld = args["sourceWorld"] as String
            val sourceWorldEnvironmentId = args["sourceWorldEnvironmentId"] as Int
            val targetWorld = args["targetWorld"] as String
            val chunksRaw = args["chunks"] as List<*>
            val chunks = chunksRaw.map {
                val pair = it as List<*>
                Pair((pair[0] as Int), (pair[1] as Int))
            }
            val spawnLocationRaw = args["spawnLocation"] as List<*>
            val spawnLocation = spawnLocationRaw.map { it as Double }

            return WorldResetLoadInstruction(
                sourceWorld,
                sourceWorldEnvironmentId,
                targetWorld,
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
        }
    }

    override fun serialize(): Map<String?, Any?> {
        return super.serialize() + mapOf(
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
    }

}