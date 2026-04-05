package net.chlod.minecraft.homerun.config.borders.consumable.effect

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.borders.consumable.ConsumableBorderStatus
import org.bukkit.entity.Player

interface ConsumableBorderEffect {
    fun onTick(plugin: Homerun, resetRule: ResetRule, player: Player, status: ConsumableBorderStatus)
    fun onEmptyTick(plugin: Homerun, resetRule: ResetRule, player: Player, status: ConsumableBorderStatus)
}