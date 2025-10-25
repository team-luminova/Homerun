package net.chlod.minecraft.homerun.data.world

class WorldRenameLoadInstruction(sourceWorld: String, sourceWorldEnvironmentId: Int, targetWorld: String) :
    ResetLoadInstructions(ResetLoadInstructionType.RENAME, sourceWorld, sourceWorldEnvironmentId, targetWorld) {

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Any>): WorldRenameLoadInstruction {
            val sourceWorld = args["sourceWorld"] as String
            val sourceWorldEnvironmentId = args["sourceWorldEnvironmentId"] as Int
            val targetWorld = args["targetWorld"] as String

            return WorldRenameLoadInstruction(
                sourceWorld,
                sourceWorldEnvironmentId,
                targetWorld
            )
        }
    }

}