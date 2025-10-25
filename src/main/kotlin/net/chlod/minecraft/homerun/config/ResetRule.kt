package net.chlod.minecraft.homerun.config

import net.chlod.minecraft.homerun.config.conditions.AlwaysResetCondition
import net.chlod.minecraft.homerun.config.conditions.CronResetCondition
import net.chlod.minecraft.homerun.config.conditions.ResetCondition
import org.bukkit.configuration.serialization.ConfigurationSerializable

class ResetRule(
    val conditions: List<ResetCondition>,
    val parameters: ResetParameters,
    val name: String?,
    val enabled: Boolean? = false
) : ConfigurationSerializable {

    companion object {

        @JvmStatic
        val CONDITIONS = listOf(
            AlwaysResetCondition,
            CronResetCondition
        )

        @JvmStatic
        fun deserialize(args: Map<String, Any>): ResetRule {
            val conditions = args["conditions"]

            if (conditions !is List<*>) {
                throw IllegalArgumentException("Condition list is not a list")
            }

            val deserializedConditions: MutableList<ResetCondition> = ArrayList()

            for (condition in conditions) {
                var deserialized = false
                for (conditionType in CONDITIONS) {
                    try {
                        val result = conditionType.javaClass
                            .getMethod("deserialize", Map::class.java)
                            .invoke(conditionType, condition)
                        deserializedConditions.add(result as ResetCondition)
                        deserialized = true
                        break
                    } catch (_: IllegalArgumentException) {
                        // Try the next condition type
                    } catch (e: java.lang.reflect.InvocationTargetException) {
                        // Unwrap and check if it's an IllegalArgumentException
                        if (e.cause is IllegalArgumentException) {
                            // Try the next condition type
                            continue
                        }
                        throw IllegalArgumentException("couldn't deserialize condition: $condition", e)
                    } catch (e: Exception) {
                        throw IllegalArgumentException("couldn't deserialize condition: $condition", e)
                    }
                }
                if (!deserialized) {
                    throw IllegalArgumentException("couldn't deserialize condition: $condition (no matching condition type)")
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
            val resetParameters = ResetParameters.deserialize(parameters as Map<String, Any>)

            val name = args["name"] as String?
            val enabled = args["enabled"] as Boolean?

            return ResetRule(
                deserializedConditions,
                resetParameters,
                name,
                enabled
            )
        }

    }

    override fun serialize(): Map<String?, Any?> {
        val serializedConditions: MutableList<Map<String?, Any?>> = ArrayList()
        for (condition in conditions) {
            serializedConditions.add(condition.serialize())
        }

        return HashMap<String?, Any?>().apply {
            if (name != null) {
                put("name", name)
            }
            if (enabled != null) {
                put("enabled", enabled)
            }
            put("conditions", serializedConditions)
            put("parameters", parameters.serialize())
        }
    }

}