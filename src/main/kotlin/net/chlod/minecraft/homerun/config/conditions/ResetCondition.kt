package net.chlod.minecraft.homerun.config.conditions

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.configuration.serialization.ConfigurationSerializable
import java.time.ZonedDateTime

interface ResetCondition : ConfigurationSerializable {

    fun getHumanReadableDescription(): String

    /**
     * This check is run every tick. This MUST complete quickly!
     * @return The time in milliseconds until the next reset, or null if not applicable
     */
    fun getTimeUntilNextReset(plugin: Homerun): Long? {
        return null
    }

    fun getNextReset(plugin: Homerun): ZonedDateTime? {
        return null
    }

    /**
     * This check is run every tick. This MUST complete quickly!
     */
    fun isSatisfied(plugin: Homerun): Boolean

}