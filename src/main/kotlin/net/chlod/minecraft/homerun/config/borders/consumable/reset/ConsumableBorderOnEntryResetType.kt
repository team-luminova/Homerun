package net.chlod.minecraft.homerun.config.borders.consumable.reset

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.borders.ConsumableBorderType
import net.chlod.minecraft.homerun.config.borders.consumable.ConsumableBorderStatus
import org.bukkit.entity.Player

class ConsumableBorderOnEntryResetType : ConsumableBorderResetType {
    companion object {
        const val TYPE = "on_entry"
    }

    override fun willReset(
        player: Player,
        border: ConsumableBorderType,
        rule: ResetRule,
        plugin: Homerun
    ): Boolean {
        val borderStatus = ConsumableBorderStatus.of(plugin, border, player)
        // Last entry time is when the user last entered the border. The reset will always happen before
        // a player enters the border, so the next time they re-enter the border, the last entry time will
        // then be after the last reset time. The last reset time is subsequently reset to a value above the
        // last entry time.
        return borderStatus.lastEntryTime > borderStatus.lastResetTime
    }
}