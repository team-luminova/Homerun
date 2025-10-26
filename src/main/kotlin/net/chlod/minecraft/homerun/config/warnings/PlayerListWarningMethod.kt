package net.chlod.minecraft.homerun.config.warnings

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.conditions.ResetCondition
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.World
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder

class PlayerListWarningMethod(
    val position: PlayerListWarningPosition?,
    val minSecondsBefore: Int?
) : ResetWarningMethod(ResetWarningMethodType.PLAYER_LIST) {

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Any>): PlayerListWarningMethod {
            val position = PlayerListWarningPosition.valueOf(args["position"] as String)
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
        val nextReset = condition.getNextReset(plugin)!!
        val text = Component.text("World reset at ")
            .color(NamedTextColor.GRAY)
            .append(
                Component
                    .text(
                        nextReset.withZoneSameInstant(ZoneId.of("UTC")).format(
                            DateTimeFormatterBuilder()
                                .appendPattern("yyyy-MM-dd HH:mm 'UTC'")
                                .toFormatter()
                        )
                    )
                    .color(NamedTextColor.WHITE)
            )
            .append(
                Component.text(", ")
                    .color(NamedTextColor.GRAY)
            )
            .append(
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
                    .decorate(TextDecoration.BOLD)
            )
            .append(
                Component.text(" remaining")
                    .color(NamedTextColor.GRAY)
            )

        if (position == PlayerListWarningPosition.HEADER) {
            world.players.forEach { player ->
                player.sendPlayerListHeader(text)
            }
        } else {
            world.players.forEach { player ->
                player.sendPlayerListFooter(text)
            }
        }
    }

    override fun serialize(): Map<String?, Any?> {
        return super.serialize() + mapOf(
            "position" to position?.name?.lowercase(),
            "min_seconds_before" to minSecondsBefore,
        ).filter { it.value != null }
    }

    enum class PlayerListWarningPosition {
        HEADER,
        FOOTER
    }
}