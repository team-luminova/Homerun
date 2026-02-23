package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.scheduler.BukkitRunnable

class BorderCheckTask(val plugin: Homerun) : BukkitRunnable() {

    override fun run() {
        plugin.resetRules.forEach { resetRule ->
            resetRule.borders?.forEach { it.onTick(plugin, resetRule) }
        }
    }
}