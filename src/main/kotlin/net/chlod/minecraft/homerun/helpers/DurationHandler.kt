package net.chlod.minecraft.homerun.helpers

import net.chlod.minecraft.homerun.Homerun
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.math.floor

class DurationHandler(val plugin: Homerun) {

    fun asCountdown(durationMillis: Long): String {
        val hours = (durationMillis / 3600000) % 24
        val minutes = (durationMillis / 60000) % 60
        val seconds = (durationMillis / 1000) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun asTextString(durationMillis: Long): String {
        val durations = getUnitComponents(durationMillis)
        if (durations.isEmpty()) {
            // Weird?
            return floor(durationMillis / 1000.0).toInt().toString() + " seconds"
        }

        return durations.joinToString(plugin.messages.getRaw("warning-chat-glue")) {
            PlainTextComponentSerializer.plainText().serialize(it)
        }
    }

    fun asTextComponent(durationMillis: Long): Component? {
        val durations = getUnitComponents(durationMillis)
        if (durations.isEmpty()) {
            // Weird?
            return null
        }

        return durations.reduce { acc, component ->
            acc
                .append(plugin.messages.get("warning-chat-glue"))
                .append(component)
        }
    }

    private fun getUnitComponents(durationMillis: Long): List<Component> {
        val hoursUntil = (durationMillis / 3600000) % 24
        val minutesUntil = (durationMillis / 60000) % 60
        val secondsUntil = (durationMillis / 1000) % 60

        val durations = mutableListOf<Component>()

        if (hoursUntil > 0) {
            durations.add(
                plugin.messages.get(
                    "warning-chat-hours",
                    Placeholder.unparsed("num", hoursUntil.toString()),
                    Formatter.choice("unit", hoursUntil)
                )
            )
        }
        if (minutesUntil > 0) {
            durations.add(
                plugin.messages.get(
                    "warning-chat-minutes",
                    Placeholder.unparsed("num", minutesUntil.toString()),
                    Formatter.choice("unit", minutesUntil)
                )
            )
        }
        if (secondsUntil > 0) {
            durations.add(
                plugin.messages.get(
                    "warning-chat-seconds",
                    Placeholder.unparsed("num", secondsUntil.toString()),
                    Formatter.choice("unit", secondsUntil)
                )
            )
        }

        return durations
    }

}