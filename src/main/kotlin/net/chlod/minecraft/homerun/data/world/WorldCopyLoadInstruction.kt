package net.chlod.minecraft.homerun.data.world

class WorldCopyLoadInstruction(sourceWorld: String, targetWorld: String) :
    ResetLoadInstructions(ResetLoadInstructionType.COPY, sourceWorld, targetWorld) {

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Object>): WorldCopyLoadInstruction {
            val sourceWorld = args["sourceWorld"] as String
            val targetWorld = args["targetWorld"] as String

            return WorldCopyLoadInstruction(
                sourceWorld,
                targetWorld
            )
        }
    }

}