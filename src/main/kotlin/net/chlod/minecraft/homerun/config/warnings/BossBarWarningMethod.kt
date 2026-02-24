package net.chlod.minecraft.homerun.config.warnings

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.conditions.ResetCondition
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter
import org.bukkit.World

class BossBarWarningMethod(
    val secondsBefore: Int
) : ResetWarningMethod(ResetWarningMethodType.BOSS_BAR) {

    val bossBars = mutableMapOf<Pair<World, ResetCondition>, BossBar>()

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Any>): BossBarWarningMethod {
            val secondsBefore = args["seconds_before"] as? Int ?: 60
            return BossBarWarningMethod(secondsBefore)
        }
    }

    private fun getWarningTextColor(timeUntilResetMillis: Long): NamedTextColor? {
        val percentage = timeUntilResetMillis.toDouble() / (secondsBefore * 1000)
        return when {
            percentage > 0.5 -> NamedTextColor.GREEN
            percentage > 0.25 -> NamedTextColor.YELLOW
            timeUntilResetMillis < 10000 -> NamedTextColor.DARK_RED
            else -> NamedTextColor.RED
        }
    }

    private fun getWarningBossBarColor(timeUntilResetMillis: Long): BossBar.Color {
        val percentage = timeUntilResetMillis.toDouble() / (secondsBefore * 1000)
        return when {
            percentage > 0.5 -> BossBar.Color.GREEN
            percentage > 0.25 -> BossBar.Color.YELLOW
            else -> BossBar.Color.RED
        }
    }

    override fun displayWarningMessage(
        plugin: Homerun,
        world: World,
        resetRule: ResetRule,
        condition: ResetCondition,
        timeUntilResetMillis: Long
    ) {
        if (timeUntilResetMillis >= secondsBefore * 1000) {
            return
        }

        val message = plugin.messages.get(
            "warning-bossbar",
            Formatter.number("seconds", timeUntilResetMillis / 1000.0)
        )
            .color(getWarningTextColor(timeUntilResetMillis))

        val conditionKey = Pair(world, condition)
        var bossBar = bossBars[conditionKey]
        if (bossBar != null) {
            bossBar
                .name(message)
                .progress(1 - (timeUntilResetMillis / (secondsBefore * 1000f)))
                .color(getWarningBossBarColor(timeUntilResetMillis))
        } else {
            bossBar = bossBars.getOrPut(conditionKey) {
                // Create a new BossBar for this condition
                BossBar.bossBar(
                    message,
                    1f,
                    getWarningBossBarColor(timeUntilResetMillis),
                    BossBar.Overlay.PROGRESS
                )
            }
            bossBars[conditionKey] = bossBar

            world.forEachAudience { audience -> audience.showBossBar(bossBar) }
        }
    }

    override fun serialize(): Map<String?, Any?> {
        return super.serialize() + mapOf(
            "seconds_before" to secondsBefore
        )
    }
}