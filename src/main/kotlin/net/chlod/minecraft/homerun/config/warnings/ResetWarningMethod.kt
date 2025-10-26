package net.chlod.minecraft.homerun.config.warnings

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.conditions.ResetCondition
import org.bukkit.World
import org.bukkit.configuration.serialization.ConfigurationSerializable

abstract class ResetWarningMethod(val type: ResetWarningMethodType) : ConfigurationSerializable {

    companion object {
        @JvmStatic
        fun deserializeType(args: Map<String, Any>): ResetWarningMethodType {
            val typeString = args["type"] as String
            return ResetWarningMethodType.valueOf(typeString.uppercase())
        }
    }

    abstract fun displayWarningMessage(
        plugin: Homerun,
        world: World,
        resetRule: ResetRule,
        condition: ResetCondition,
        timeUntilResetMillis: Long
    )

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "type" to type.name.lowercase()
        )
    }

    enum class ResetWarningMethodType {
        BOSS_BAR,
        PLAYER_LIST,
        CHAT_MESSAGE
    }

}