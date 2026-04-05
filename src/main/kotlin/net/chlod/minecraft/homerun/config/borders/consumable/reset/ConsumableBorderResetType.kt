package net.chlod.minecraft.homerun.config.borders.consumable.reset

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.borders.ConsumableBorderType
import org.bukkit.entity.Player

interface ConsumableBorderResetType {
    fun willReset(player: Player, border: ConsumableBorderType, rule: ResetRule, plugin: Homerun): Boolean
}