package net.chlod.minecraft.homerun.config.borders.consumable.reset

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.borders.ConsumableBorderType
import net.chlod.minecraft.homerun.config.borders.consumable.ConsumableBorderStatus
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType

class ConsumableBorderOnResetResetType : ConsumableBorderResetType {
    companion object {
        const val TYPE = "on_reset"
    }

    override fun willReset(
        player: Player,
        border: ConsumableBorderType,
        rule: ResetRule,
        plugin: Homerun
    ): Boolean {
        val borderStatus = ConsumableBorderStatus.Companion.of(plugin, border, player)
        val lastResetWhen = player.location.world.persistentDataContainer.get(
            plugin.keys.resetTimestamp,
            PersistentDataType.LONG
        ) ?: 0L
        // If true, border was last reset before the most recent world reset, so we should reset it now.
        return borderStatus.lastResetTime <= lastResetWhen
    }
}