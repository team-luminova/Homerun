package net.chlod.minecraft.homerun.config

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.borders.HighestBlockBorderType
import net.chlod.minecraft.homerun.config.borders.ParticleBorderType
import net.chlod.minecraft.homerun.config.borders.ResetBorder
import net.chlod.minecraft.homerun.config.conditions.AlwaysResetCondition
import net.chlod.minecraft.homerun.config.conditions.CronResetCondition
import net.chlod.minecraft.homerun.config.conditions.ResetCondition
import net.chlod.minecraft.homerun.config.warnings.BossBarWarningMethod
import net.chlod.minecraft.homerun.config.warnings.ChatMessageWarningMethod
import net.chlod.minecraft.homerun.config.warnings.PlayerListWarningMethod
import net.chlod.minecraft.homerun.config.warnings.ResetWarningMethod
import org.bukkit.configuration.serialization.ConfigurationSerializable

class ResetRule(
    val conditions: List<ResetCondition>,
    val parametersList: List<ResetParameters>,
    val name: String?,
    val enabled: Boolean? = false,
    val notifyEnter: Boolean? = false,
    val notifyExit: Boolean? = false,
    val borders: List<ResetBorder>? = null,
    /**
     * Methods to use for warning players about an upcoming reset.
     */
    val warnings: List<ResetWarningMethod>? = null
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
            val resetParametersList: List<ResetParameters> = when (parameters) {
                is List<*> -> {
                    // New format: list of parameter maps
                    parameters.map { entry ->
                        if (entry !is Map<*, *>) {
                            throw IllegalArgumentException("Parameters list entry is not a map")
                        }
                        if (entry.keys.any { it !is String }) {
                            throw IllegalArgumentException("Parameters map contains non-string keys")
                        }
                        @Suppress("UNCHECKED_CAST")
                        ResetParameters.deserialize(entry as Map<String, Any>)
                    }
                }

                is Map<*, *> -> {
                    // Legacy format: single parameter map
                    if (parameters.keys.any { it !is String }) {
                        throw IllegalArgumentException("Parameters map contains non-string keys")
                    }
                    @Suppress("UNCHECKED_CAST")
                    listOf(ResetParameters.deserialize(parameters as Map<String, Any>))
                }

                else -> throw IllegalArgumentException("Parameters is not a map or list")
            }

            if (resetParametersList.isEmpty()) {
                throw IllegalArgumentException("At least one parameter set must be specified")
            }

            val notifyEnter = (args["notify_enter"] ?: args["notify"]) as? Boolean
            val notifyExit = (args["notify_exit"] ?: args["notify"]) as? Boolean

            val bordersRaw = args["borders"] as? List<*>
            val borders: MutableList<ResetBorder> = ArrayList()
            bordersRaw?.forEachIndexed { i, border ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val result = ResetBorder.deserializeType(border as Map<String, Any>)
                    borders.add(
                        when (result) {
                            ResetBorder.BorderType.HIGHEST_BLOCK ->
                                HighestBlockBorderType.deserialize(border)

                            ResetBorder.BorderType.PARTICLES ->
                                ParticleBorderType.deserialize(border)
                        }
                    )
                } catch (e: Exception) {
                    throw IllegalArgumentException("can't deserialize border type #$i", e)
                }
            }

            val warningsRaw = args["warnings"] as? List<*>
            val warnings: MutableList<ResetWarningMethod> = ArrayList()
            warningsRaw?.forEachIndexed { i, warningMethod ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val result = ResetWarningMethod.deserializeType(warningMethod as Map<String, Any>)
                    warnings.add(
                        when (result) {
                            ResetWarningMethod.ResetWarningMethodType.BOSS_BAR ->
                                BossBarWarningMethod.deserialize(warningMethod)

                            ResetWarningMethod.ResetWarningMethodType.PLAYER_LIST ->
                                PlayerListWarningMethod.deserialize(warningMethod)

                            ResetWarningMethod.ResetWarningMethodType.CHAT_MESSAGE ->
                                ChatMessageWarningMethod.deserialize(warningMethod)
                        }
                    )
                } catch (e: Exception) {
                    throw IllegalArgumentException("can't deserialize warning method #$i", e)
                }
            }

            val name = args["name"] as String?
            val enabled = args["enabled"] as Boolean?

            return ResetRule(
                deserializedConditions,
                resetParametersList,
                name,
                enabled,
                notifyEnter,
                notifyExit,
                borders,
                warnings
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
            if (notifyEnter != null) {
                put("notify_enter", notifyEnter)
            }
            if (notifyExit != null) {
                put("notify_exit", notifyExit)
            }
            if (borders != null) {
                put("borders", borders.map { it.serialize() })
            }
            put("conditions", serializedConditions)
            if (warnings != null) {
                put("warnings", warnings.map { it.serialize() })
            }
            put("parameters", parametersList.map { it.serialize() })
        }
    }

    fun affectsWorld(plugin: Homerun, worldName: String): Boolean {
        for (parameters in parametersList) {
            val targetWorldName = parameters.world ?: plugin.server.worlds.firstOrNull()?.name ?: return false
            if (worldName == targetWorldName) {
                return true
            }
            if (
                worldName.endsWith("_nether") &&
                parameters.netherBehavior.isDestructive() &&
                worldName == targetWorldName + "_nether"
            ) {
                return true
            }
            if (
                worldName.endsWith("_the_end") &&
                parameters.endBehavior.isDestructive() &&
                worldName == targetWorldName + "_the_end"
            ) {
                return true
            }
        }
        return false
    }

}