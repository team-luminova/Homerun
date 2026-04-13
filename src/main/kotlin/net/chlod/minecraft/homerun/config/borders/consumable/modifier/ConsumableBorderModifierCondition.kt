package net.chlod.minecraft.homerun.config.borders.consumable.modifier

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.borders.ConsumableBorderType
import org.bukkit.configuration.serialization.ConfigurationSerializable
import org.bukkit.entity.Player

interface ConsumableBorderModifierCondition : ConfigurationSerializable {

    fun onTick(plugin: Homerun, resetRule: ResetRule, border: ConsumableBorderType, player: Player): Boolean

}