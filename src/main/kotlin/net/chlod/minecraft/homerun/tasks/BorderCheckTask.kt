package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.borders.ResetBorder
import org.bukkit.scheduler.BukkitRunnable

class BorderCheckTask(val plugin: Homerun) : BukkitRunnable() {

    val lastRunCache = mutableMapOf<ResetBorder, Long>()

    override fun run() {
        plugin.resetRules.forEach { resetRule ->
            for (border in resetRule.borders ?: emptyList()) {
                val period = border.tickPeriod
                if (period > 1L) {
                    val lastRun = lastRunCache[border] ?: 1
                    if (lastRun >= period) {
                        lastRunCache[border] = 1
                    } else {
                        lastRunCache[border] = lastRun + 1
                        // Skip this for next tick.
                        continue
                    }
                }
                border.onTick(plugin, resetRule)
            }
        }
    }
}