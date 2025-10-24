package net.chlod.minecraft.homerun.data.world

import org.bukkit.configuration.serialization.ConfigurationSerializable

abstract class ResetLoadInstructions(
    val type: ResetLoadInstructionType,
    val sourceWorld: String,
    val sourceWorldEnvironmentId: Int,
    val targetWorld: String
) : ConfigurationSerializable {

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "type" to type.name.lowercase(),
            "sourceWorld" to sourceWorld,
            "sourceWorldEnvironmentId" to sourceWorldEnvironmentId,
            "targetWorld" to targetWorld
        )
    }

    enum class ResetLoadInstructionType() {
        RESET(),
        COPY(),
        RENAME(),
    }

}