package net.chlod.minecraft.homerun.config.borders.consumable.effect

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.borders.ConsumableBorderType
import net.chlod.minecraft.homerun.config.borders.consumable.ConsumableBorderStatus
import org.bukkit.Tag
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.Player

class ConsumableBorderFreezeEffect : ConsumableBorderEffect {

    companion object {
        const val TYPE = "freezing"

        /** This is a Minecraft thing. */
        const val MAX_FREEZE_TICKS = 140
    }

    val lastDamagedTick = mutableMapOf<Player, Long>()

    override fun onTick(
        plugin: Homerun,
        resetRule: ResetRule,
        border: ConsumableBorderType,
        player: Player,
        status: ConsumableBorderStatus
    ) {
        player.lockFreezeTicks(status.ticksSinceEmpty > 0)
        if (status.currentlyInsideBorder && status.ticksSinceEmpty <= 0) {
            if (player in lastDamagedTick) {
                lastDamagedTick.remove(player)
            }
        }
    }

    override fun onEmptyTick(
        plugin: Homerun,
        resetRule: ResetRule,
        border: ConsumableBorderType,
        player: Player,
        status: ConsumableBorderStatus
    ) {
        player.freezeTicks = (player.freezeTicks + border.tickPeriod.toInt() * plugin.tickPeriod.toInt())
            .coerceAtMost(MAX_FREEZE_TICKS)

        // Check if the player has any freeze-immune wearables. If they do, we have to manually deal them damage.
        if (
            status.ticksSinceEmpty > MAX_FREEZE_TICKS &&
            player.equipment.armorContents.any { it != null && Tag.ITEMS_FREEZE_IMMUNE_WEARABLES.isTagged(it.type) }
        ) {
            val lastTick = lastDamagedTick[player]
            if (lastTick == null) {
                // Skip the first damage hit.
                // Because we're benevolent.
                // (This prevents a rapid double-damage when switching from vanilla-based freeze damage and border ticks
                // -based freeze damage. Instead, we might get a long 4-second delay between the next hit, but this is
                // likely more acceptable than two rapid succession damage hits, which can kill the player.)
                lastDamagedTick[player] = status.ticksSinceEmpty
            } else if (lastTick + 40 < status.ticksSinceEmpty) {
                player.damage(1.0, DamageSource.builder(DamageType.FREEZE).build())
                lastDamagedTick[player] = status.ticksSinceEmpty
            }
        } else if (player in lastDamagedTick) {
            // Remove the LDT if they suddenly become non-immune to freeze damage or if the ticksSinceEmpty drops below
            // 140. This works with the above first damage hit to avoid double-damage.
            lastDamagedTick.remove(player)
        }
    }
}