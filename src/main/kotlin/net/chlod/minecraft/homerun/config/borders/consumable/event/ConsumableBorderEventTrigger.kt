package net.chlod.minecraft.homerun.config.borders.consumable.event

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.borders.ConsumableBorderType
import org.bukkit.configuration.serialization.ConfigurationSerializable
import org.bukkit.event.Event

interface ConsumableBorderEventTrigger<T : Event> : ConfigurationSerializable {

    fun test(
        plugin: Homerun,
        resetRule: ResetRule,
        border: ConsumableBorderType,
        event: T
    ): Boolean

}