package net.chlod.minecraft.homerun.data.world

class WorldRenameLoadInstruction(sourceWorld: String, targetWorld: String) :
    ResetLoadInstructions(ResetLoadInstructionType.RENAME, sourceWorld, targetWorld) {

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Object>): WorldRenameLoadInstruction {
            val sourceWorld = args["sourceWorld"] as String
            val targetWorld = args["targetWorld"] as String

            return WorldRenameLoadInstruction(
                sourceWorld,
                targetWorld
            )
        }
    }

}