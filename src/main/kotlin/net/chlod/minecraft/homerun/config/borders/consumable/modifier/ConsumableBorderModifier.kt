package net.chlod.minecraft.homerun.config.borders.consumable.modifier

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.borders.ConsumableBorderType
import org.bukkit.configuration.serialization.ConfigurationSerializable
import org.bukkit.entity.Player

class ConsumableBorderModifier(
    val amount: Double,
    val conditions: List<ConsumableBorderModifierCondition>
) : ConfigurationSerializable {

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Any>): ConsumableBorderModifier {
            if (args["amount"] !is Double) {
                throw IllegalArgumentException("'amount' must be a double")
            }
            val amount = args["amount"] as Double

            val conditions = mutableListOf<ConsumableBorderModifierCondition>()
            val conditionsRaw = args["conditions"]
            if (conditionsRaw !is List<*>) {
                throw IllegalArgumentException("'conditions' must be a list")
            }
            if (conditionsRaw.any { it !is Map<*, *> }) {
                throw IllegalArgumentException("Each consumable border multiplier 'condition' must be a map")
            }
            if (conditionsRaw.any { it -> (it as Map<*, *>).keys.any { it !is String } }) {
                // Sanity type checking.
                throw IllegalArgumentException("'condition' keys must be a string (this shouldn't happen?!?!)")
            }
            @Suppress("UNCHECKED_CAST")
            val conditionsTyped = conditionsRaw as List<Map<String, Any>>

            for (conditionRaw in conditionsTyped) {
                conditions.add(
                    when (conditionRaw["type"]) {
                        ConsumableBorderWornModifierCondition.TYPE ->
                            ConsumableBorderWornModifierCondition.deserialize(conditionRaw)

                        else -> throw IllegalArgumentException("Condition type '${conditionRaw["type"]}' does not exist")
                    }
                )
            }

            return ConsumableBorderModifier(amount, conditions)
        }
    }

    fun test(
        plugin: Homerun,
        resetRule: ResetRule,
        border: ConsumableBorderType,
        player: Player
    ): Boolean {
        return this.conditions.all { it.onTick(plugin, resetRule, border, player) }
    }

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "amount" to amount,
            "conditions" to conditions.map { it.serialize() }
        )
    }

}