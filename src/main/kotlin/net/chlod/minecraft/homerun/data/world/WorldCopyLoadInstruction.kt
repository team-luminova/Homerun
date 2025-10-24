package net.chlod.minecraft.homerun.data.world

class WorldCopyLoadInstruction(sourceWorld: String, sourceWorldEnvironmentId: Int, targetWorld: String) :
    ResetLoadInstructions(ResetLoadInstructionType.COPY, sourceWorld, sourceWorldEnvironmentId, targetWorld) {

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Object>): WorldCopyLoadInstruction {
            val sourceWorld = args["sourceWorld"] as String
            val sourceWorldEnvironmentId = args["sourceWorldEnvironmentId"] as Int
            val targetWorld = args["targetWorld"] as String

            return WorldCopyLoadInstruction(
                sourceWorld,
                sourceWorldEnvironmentId,
                targetWorld
            )
        }
    }

}