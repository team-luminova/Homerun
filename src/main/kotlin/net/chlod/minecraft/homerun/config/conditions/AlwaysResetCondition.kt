package net.chlod.minecraft.homerun.config.conditions

import net.chlod.minecraft.homerun.Homerun

class AlwaysResetCondition: ResetCondition {

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun deserialize(args: Map<String, Any>): AlwaysResetCondition {
            if (args.containsKey("always")) {
                return AlwaysResetCondition()
            } else {
                throw IllegalArgumentException("Invalid arguments for AlwaysResetCondition: $args")
            }
        }
    }

    override fun getHumanReadableDescription(): String {
        return "on server startup"
    }

    override fun isSatisfied(plugin: Homerun): Boolean {
        return true
    }

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "always" to true
        )
    }

}