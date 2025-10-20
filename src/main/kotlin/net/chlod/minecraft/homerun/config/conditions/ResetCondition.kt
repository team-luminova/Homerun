package net.chlod.minecraft.homerun.config.conditions

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.configuration.serialization.ConfigurationSerializable

interface ResetCondition: ConfigurationSerializable {

    fun getHumanReadableDescription(): String
    fun getTimeUntilNextReset(plugin: Homerun): Long? {
        return null
    }
    fun isSatisfied(plugin: Homerun): Boolean

}