package net.chlod.minecraft.homerun.listeners

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.borders.ConsumableBorderType
import net.chlod.minecraft.homerun.config.borders.consumable.event.ConsumableBorderConsumeEventTrigger
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemConsumeEvent

class ConsumableBorderConsumeListener(val plugin: Homerun) : Listener {

    @EventHandler
    fun onItemConsumed(event: PlayerItemConsumeEvent) {
        for (resetRule in plugin.resetRules) {
            if (!(resetRule.enabled ?: true)) {
                continue
            }

            for (border in resetRule.borders ?: emptyList()) {
                if (border !is ConsumableBorderType) {
                    continue
                }

                borderScan@ for (borderEvent in border.events) {
                    for (trigger in borderEvent.triggers) {
                        if (trigger is ConsumableBorderConsumeEventTrigger) {
                            if (trigger.test(plugin, resetRule, border, event)) {
                                border.applyEvent(plugin, event.player, borderEvent)
                                continue@borderScan
                            }
                        }
                    }
                }
            }
        }
    }

}