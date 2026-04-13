package net.chlod.minecraft.homerun.config.borders.consumable.extra

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.borders.ConsumableBorderType
import net.chlod.minecraft.masks.ItemMask
import org.bukkit.event.player.PlayerItemConsumeEvent

class ConsumableBorderConsumeEventTrigger(
    val item: ItemMask
) : ConsumableBorderEventTrigger<PlayerItemConsumeEvent> {

    companion object {
        const val TYPE = "consume"

        @JvmStatic
        fun deserialize(args: Map<String, Any>): ConsumableBorderConsumeEventTrigger {
            val mask = ItemMask.deserialize(args)

            return ConsumableBorderConsumeEventTrigger(mask)
        }
    }

    override fun test(
        plugin: Homerun,
        resetRule: ResetRule,
        border: ConsumableBorderType,
        event: PlayerItemConsumeEvent
    ): Boolean {
        return item.test(event.item)
    }

    override fun serialize(): Map<String?, Any?> {
        return mapOf(
            "type" to TYPE,
            "item" to item
        )
    }


}