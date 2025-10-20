package net.chlod.minecraft.homerun.config

import net.chlod.minecraft.homerun.config.conditions.AlwaysResetCondition
import net.chlod.minecraft.homerun.config.conditions.CronResetCondition
import net.chlod.minecraft.homerun.config.conditions.ResetCondition
import org.bukkit.configuration.serialization.ConfigurationSerializable

class ResetRule(
    val conditions: List<ResetCondition>,
    val parameters: ResetParameters,
    val name: String?
) : ConfigurationSerializable {

    companion object {

        @JvmStatic
        val CONDITIONS = listOf(
            AlwaysResetCondition,
            CronResetCondition
        )

        @JvmStatic
        fun deserialize(args: Map<String, Object>): ResetRule {
            val conditions = args["conditions"]

            if (conditions !is List<*>) {
                throw IllegalArgumentException("Condition list is not a list")
            }

            val deserializedConditions: MutableList<ResetCondition> = ArrayList()

            for (condition in conditions) {
                for (conditionType in CONDITIONS) {
                    try {
                        val deserialized = conditionType.javaClass
                            .getMethod("deserialize", Map::class.java)
                            .invoke(conditionType, condition)
                        deserializedConditions.add(deserialized as ResetCondition)
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException("couldn't deserialize condition: $condition", e)
                    } catch (e: Exception) {
                        throw e
                    }
                }
            }

            val parameters = args["parameters"]
            if (parameters !is Map<*, *>) {
                throw IllegalArgumentException("Parameters is not a map")
            }
            if (parameters.keys.any { it !is String }) {
                throw IllegalArgumentException("Parameters map contains non-string keys")
            }
            @Suppress("UNCHECKED_CAST")
            val resetParameters = ResetParameters.deserialize(parameters as Map<String, Object>)

            val name = args["name"] as String?

            return ResetRule(
                deserializedConditions,
                resetParameters,
                name
            )
        }

    }

    override fun serialize(): Map<String?, Any?> {
        val serializedConditions: MutableList<Map<String?, Any?>> = ArrayList()
        for (condition in conditions) {
            serializedConditions.add(condition.serialize())
        }

        return HashMap<String?, Any?>().apply {
            "conditions" to serializedConditions
            "parameters" to parameters.serialize()
            if (name != null) {
                "name" to name
            }
        }
    }

}