package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetParameters
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.conditions.ResetCondition
import org.bukkit.scheduler.BukkitRunnable

class ConditionCheckTask(val plugin: Homerun) : BukkitRunnable() {

    override fun run() {
        plugin.resetRules.forEachIndexed { index, resetRule ->
            if (resetRule.enabled != true)
                return@forEachIndexed

            var soonestResetTime: Long? = null
            for (condition in resetRule.conditions) {
                if (condition.isSatisfied(plugin)) {
                    plugin.componentLogger.warn(
                        "Reset condition satisfied for rule ${resetRule.name ?: index}, executing reset."
                    )
                    ResetPrepareTask(plugin, resetRule)
                        .runTaskTimer(plugin, 0L, 20L)
                    break
                } else {
                    val timeUntilResetMillis = condition.getTimeUntilNextReset(plugin) ?: return
                    if (soonestResetTime == null || timeUntilResetMillis < soonestResetTime) {
                        soonestResetTime = timeUntilResetMillis
                    }

                    for (parameters in resetRule.parametersList) {
                        onResetUnsatisfied(resetRule, condition, parameters, timeUntilResetMillis)
                    }
                }
            }
            plugin.timeUntilNextResetCache[resetRule] = soonestResetTime ?: return
        }
    }

    private fun onResetUnsatisfied(
        resetRule: ResetRule,
        condition: ResetCondition,
        parameters: ResetParameters,
        timeUntilResetMillis: Long
    ) {
        val world = if (parameters.world == null)
            plugin.server.worlds.firstOrNull() ?: return
        else
            (plugin.server.getWorld(parameters.world) ?: return)

        resetRule.warnings?.forEach {
            it.displayWarningMessage(
                plugin,
                world,
                resetRule,
                condition,
                timeUntilResetMillis
            )
        }

        if (parameters.netherBehavior == ResetParameters.DimensionResetBehavior.NORMAL) {
            val netherWorld = plugin.server.getWorld("${world.name}_nether")
            if (netherWorld != null) {
                resetRule.warnings?.forEach {
                    it.displayWarningMessage(
                        plugin,
                        netherWorld,
                        resetRule,
                        condition,
                        timeUntilResetMillis
                    )
                }
            }
        }
        if (parameters.endBehavior == ResetParameters.DimensionResetBehavior.NORMAL) {
            val endWorld = plugin.server.getWorld("${world.name}_the_end")
            if (endWorld != null) {
                resetRule.warnings?.forEach {
                    it.displayWarningMessage(
                        plugin,
                        endWorld,
                        resetRule,
                        condition,
                        timeUntilResetMillis
                    )
                }
            }
        }
    }

}