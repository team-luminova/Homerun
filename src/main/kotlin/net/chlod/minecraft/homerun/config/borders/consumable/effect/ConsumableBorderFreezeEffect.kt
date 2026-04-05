package net.chlod.minecraft.homerun.config.borders.consumable.effect

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.borders.consumable.ConsumableBorderStatus
import org.bukkit.entity.Player

class ConsumableBorderFreezeEffect : ConsumableBorderEffect {
    companion object {
        const val TYPE = "freezing"
    }

    override fun onTick(
        plugin: Homerun,
        resetRule: ResetRule,
        player: Player,
        status: ConsumableBorderStatus
    ) {
        player.lockFreezeTicks(status.ticksSinceEmpty > 0)
    }

    override fun onEmptyTick(
        plugin: Homerun,
        resetRule: ResetRule,
        player: Player,
        status: ConsumableBorderStatus
    ) {
        player.freezeTicks = (status.ticksSinceEmpty * 4).coerceAtMost(140).toInt()
    }
}