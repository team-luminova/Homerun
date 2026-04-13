package net.chlod.minecraft.homerun.config.borders.consumable.extra

import org.bukkit.configuration.serialization.ConfigurationSerializable

class ConsumableBorderEvent(
    val operation: Operation,
    val amount: Double,
    val triggers: List<ConsumableBorderEventTrigger<*>>,
) : ConfigurationSerializable {

    enum class Operation {
        ADD,
        MULTIPLY
    }

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Any>): ConsumableBorderEvent {
            val operation = Operation.valueOf((args["operation"] as String).uppercase())
            val amount = (args["amount"] as Number).toDouble()

            if (args["triggers"] !is List<*>) {
                throw IllegalArgumentException("'triggers' must be a list")
            }
            if ((args["triggers"] as List<*>).any { it !is Map<*, *> }) {
                throw IllegalArgumentException("Each trigger must be a map")
            }
            val triggers = when (val triggersRaw = args["triggers"] ?: listOf<ConsumableBorderEventTrigger<*>>()) {
                is List<*> -> {
                    if (!triggersRaw.all { it is Map<*, *> }) {
                        throw IllegalArgumentException("Elements of 'triggers' must be maps")
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        (triggersRaw as List<Map<*, *>>).map {
                            when (it["type"]) {
                                ConsumableBorderConsumeEventTrigger.TYPE ->
                                    ConsumableBorderConsumeEventTrigger.deserialize(it as Map<String, Any>)

                                else ->
                                    throw IllegalArgumentException("'type' ${it["type"]} is not a valid event trigger type")
                            }
                        }
                    }
                }

                else -> throw IllegalArgumentException("'triggers' must be a list")
            }

            return ConsumableBorderEvent(
                operation,
                amount,
                triggers
            )
        }
    }

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "operation" to operation.name.lowercase(),
            "amount" to amount,
            "triggers" to triggers.map { it.serialize() }
        )
    }

}