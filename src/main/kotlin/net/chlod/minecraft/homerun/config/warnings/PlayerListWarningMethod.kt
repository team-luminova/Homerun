package net.chlod.minecraft.homerun.config.warnings

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.conditions.ResetCondition
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.World
import java.time.ZonedDateTime

class PlayerListWarningMethod(
    val position: PlayerListWarningPosition?,
    val minSecondsBefore: Int?
) : ResetWarningMethod(ResetWarningMethodType.PLAYER_LIST) {

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Any>): PlayerListWarningMethod {
            val position = PlayerListWarningPosition.valueOf(
                (args["position"] as? String ?: "both").uppercase()
            )
            val minSecondsBefore = args["min_seconds_before"] as? Int
            return PlayerListWarningMethod(position, minSecondsBefore)
        }
    }

    private fun getWarningTextColor(timeUntilResetMillis: Long): NamedTextColor? {
        val percentage = timeUntilResetMillis.toDouble() / ((minSecondsBefore ?: 86400) * 1000)
        return when {
            percentage > 0.5 -> NamedTextColor.GREEN
            percentage > 0.25 -> NamedTextColor.YELLOW
            percentage < 1 -> NamedTextColor.RED
            timeUntilResetMillis < 5 * 60000 -> NamedTextColor.DARK_RED
            else -> NamedTextColor.WHITE
        }
    }

    override fun displayWarningMessage(
        plugin: Homerun,
        world: World,
        resetRule: ResetRule,
        condition: ResetCondition,
        timeUntilResetMillis: Long
    ) {
        if (minSecondsBefore != null && timeUntilResetMillis > minSecondsBefore * 1000) {
            return
        }

        val nextReset = condition.getNextReset(plugin)!!

        if (position == PlayerListWarningPosition.BOTH) {
            world.players.forEach { player ->
                player.sendPlayerListHeader(getDateMessage(plugin, nextReset))
                player.sendPlayerListFooter(getCountdownMessage(plugin, timeUntilResetMillis))
            }
        } else {
            val combinedText = getCombinedMessage(plugin, nextReset, timeUntilResetMillis)
            if (position == PlayerListWarningPosition.HEADER) {
                world.players.forEach { player ->
                    player.sendPlayerListHeader(combinedText)
                }
            } else {
                world.players.forEach { player ->
                    player.sendPlayerListFooter(combinedText)
                }
            }
        }
    }

    fun getCombinedMessage(
        plugin: Homerun,
        nextReset: ZonedDateTime,
        timeUntilResetMillis: Long,
    ): Component {
        return plugin.messages.get(
            "warning-playerlist",
            Formatter.date(
                "date",
                plugin.messages.withTimezone(nextReset)
            ),
            Placeholder.component(
                "duration",
                Component
                    .text(
                        String.format(
                            "%02d:%02d:%02d",
                            (timeUntilResetMillis / 3600000) % 24,
                            (timeUntilResetMillis / 60000) % 60,
                            (timeUntilResetMillis / 1000) % 60
                        )
                    )
                    .color(getWarningTextColor(timeUntilResetMillis))
            )
        )
    }

    fun getDateMessage(
        plugin: Homerun,
        nextReset: ZonedDateTime
    ): Component {
        return plugin.messages.get(
            "warning-playerlist-date",
            Formatter.date(
                "date",
                plugin.messages.withTimezone(nextReset)
            )
        )
    }

    fun getCountdownMessage(
        plugin: Homerun,
        timeUntilResetMillis: Long
    ): Component {
        return plugin.messages.get(
            "warning-playerlist-countdown",
            Placeholder.component(
                "duration",
                Component
                    .text(
                        String.format(
                            "%02d:%02d:%02d",
                            (timeUntilResetMillis / 3600000) % 24,
                            (timeUntilResetMillis / 60000) % 60,
                            (timeUntilResetMillis / 1000) % 60
                        )
                    )
                    .color(getWarningTextColor(timeUntilResetMillis))
            )
        )
    }

    override fun serialize(): Map<String?, Any?> {
        return super.serialize() + mapOf(
            "position" to position?.name?.lowercase(),
            "min_seconds_before" to minSecondsBefore,
        ).filter { it.value != null }
    }

    enum class PlayerListWarningPosition {
        HEADER,
        FOOTER,
        BOTH
    }
}