package net.chlod.minecraft.homerun.data.world

import org.bukkit.configuration.serialization.ConfigurationSerializable

abstract class ResetLoadInstructions(
    val type: ResetLoadInstructionType,
    val sourceWorld: String,
    val targetWorld: String
) : ConfigurationSerializable {

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "type" to type,
            "sourceWorld" to sourceWorld,
            "targetWorld" to targetWorld
        )
    }

    enum class ResetLoadInstructionType(val typeName: String) {
        RESET("reset"),
        COPY("copy"),
        RENAME("rename"),
    }

}