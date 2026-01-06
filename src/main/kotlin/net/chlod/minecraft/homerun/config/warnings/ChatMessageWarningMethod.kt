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
import kotlin.math.round

class ChatMessageWarningMethod() : ResetWarningMethod(ResetWarningMethodType.CHAT_MESSAGE) {

    companion object {
        @JvmStatic
        fun deserialize(args: Map<String, Any>): ChatMessageWarningMethod {
            val intervalRaw = (args["intervals"] as? List<*>)
            if (intervalRaw.isNullOrEmpty()) {
                throw IllegalArgumentException("ChatMessageWarningMethod requires a non-empty interval list")
            } else if (intervalRaw.any { it !is Int }) {
                throw IllegalArgumentException("ChatMessageWarningMethod interval list must contain only integers")
            }
            @Suppress("UNCHECKED_CAST")
            val interval = (intervalRaw as List<Int>).toSortedSet().toList()

            return ChatMessageWarningMethod(interval)
        }
    }

    /**
     * A sorted set of intervals in seconds at which to send warning messages.
     * Order is ascending (lowest to highest).
     */
    lateinit var intervals: List<Int>

    /**
     * Map of the last update time for each condition to avoid spamming messages.
     * Pair is of (lastUpdateInMilliseconds, nextUpdateInMilliseconds).
     * Inherently, the second value in the pair is always less than the first.
     */
    val lastUpdateConditionMap = mutableMapOf<Pair<World, ResetCondition>, Pair<Long, Long>>()

    private constructor(intervals: List<Int>) : this() {
        this.intervals = intervals.toSortedSet().toList()
    }

    private fun getWarningTextColor(timeUntilResetMillis: Long): NamedTextColor? {
        val timeUntilResetSeconds = timeUntilResetMillis / 1000
        val refTimeIndex = intervals.indexOfFirst { timeUntilResetSeconds < it }
        val percentage = (refTimeIndex / intervals.size)
        return when {
            refTimeIndex == 0 -> NamedTextColor.DARK_RED
            percentage > 0.5 -> NamedTextColor.GREEN
            percentage > 0.25 -> NamedTextColor.YELLOW
            else -> NamedTextColor.RED
        }
    }

    /**
     * Finds the closest interval above the given time until reset in milliseconds.
     */
    private fun findClosestIntervalAbove(timeUntilResetMillis: Long): Long? {
        val timeUntilResetSeconds = round(timeUntilResetMillis / 1000.0).toInt()
        return intervals.firstOrNull { it > timeUntilResetSeconds }?.toLong()
    }

    /**
     * Finds the closest interval lower than the given time until reset in milliseconds.
     */
    private fun findClosestIntervalBelow(timeUntilResetMillis: Long): Long? {
        val timeUntilResetSeconds = round(timeUntilResetMillis / 1000.0).toInt()
        return intervals.lastOrNull { it < timeUntilResetSeconds }?.toLong()
    }

    /**
     * Checks if a message should be sent for the given condition based on the time until reset.
     * Returns true if a message should be sent, false otherwise.
     *
     * This is a stateful method that updates the last update time for the condition.
     */
    fun willSendNextMessage(world: World, condition: ResetCondition, timeUntilResetMillis: Long): Boolean {
        // Check if we should send a message at this interval
        val conditionPair = Pair(world, condition)
        val lastUpdatePair = lastUpdateConditionMap[conditionPair]
        if (lastUpdatePair != null) {
            val (lastUpdateMillis, nextUpdateMillis) = lastUpdatePair
            if (timeUntilResetMillis <= nextUpdateMillis) {
                // Time for the next message
                val nextIntervalSeconds = findClosestIntervalBelow(timeUntilResetMillis)
                val nextIntervalMillis =
                    if (nextIntervalSeconds != null) nextIntervalSeconds * 1000L else Long.MIN_VALUE
                // nextUpdateMillis here is now the current (interval time) millis
                lastUpdateConditionMap[conditionPair] = Pair(nextUpdateMillis, nextIntervalMillis)

                return true
            } else if (timeUntilResetMillis > lastUpdateMillis) {
                // The time until reset increased and is now greater than the last update time.
                // The condition must have changed, so we reset the last update to allow immediate messaging.
                val newLastUpdateMillis = findClosestIntervalAbove(timeUntilResetMillis)
                val nextUpdateMillis = findClosestIntervalBelow(timeUntilResetMillis)

                // These two values should never be null here, but just in case, we set them to extreme values
                lastUpdateConditionMap[conditionPair] = Pair(
                    if (newLastUpdateMillis != null) newLastUpdateMillis * 1000L else Long.MAX_VALUE,
                    if (nextUpdateMillis != null) nextUpdateMillis * 1000L else Long.MIN_VALUE
                )

                // The message should print immediately after this block
                return true
            } else {
                // Not yet time to send another message
                return false
            }
        } else {
            // First time seeing this condition.
            val newLastUpdateMillis = findClosestIntervalAbove(timeUntilResetMillis)
            val nextUpdateMillis = findClosestIntervalBelow(timeUntilResetMillis)

            if (newLastUpdateMillis == null) {
                // No intervals above the current time until reset, so no messages will be sent.
                return false
            }

            // These two values should never be null here, but just in case, we set them to extreme values
            lastUpdateConditionMap[conditionPair] = Pair(
                newLastUpdateMillis * 1000L,
                if (nextUpdateMillis != null) nextUpdateMillis * 1000L else Long.MIN_VALUE
            )

            return true
        }
    }

    override fun displayWarningMessage(
        plugin: Homerun,
        world: World,
        resetRule: ResetRule,
        condition: ResetCondition,
        timeUntilResetMillis: Long
    ) {
        if (!willSendNextMessage(world, condition, timeUntilResetMillis)) return
        val conditionPair = Pair(world, condition)
        val referenceIntervalMillis = lastUpdateConditionMap[conditionPair]?.first ?: timeUntilResetMillis

        val nextReset = condition.getNextReset(plugin)!!
        var text = Component.text("This world will reset in ")

        val hoursUntil = (referenceIntervalMillis / 3600000) % 24
        val minutesUntil = (referenceIntervalMillis / 60000) % 60
        val secondsUntil = (referenceIntervalMillis / 1000) % 60

        if (hoursUntil > 0) {
            text = text.append(
                Component
                    .text(
                        String.format(
                            "%d hour" + if (hoursUntil != 1L) "s" else "",
                            hoursUntil
                        )
                    )
                    .color(getWarningTextColor(referenceIntervalMillis))
                    .decorate(TextDecoration.BOLD)
            )
        }
        if (minutesUntil > 0) {
            if (hoursUntil > 0) {
                text = text.append(Component.text(", "))
            }
            text = text.append(
                Component
                    .text(
                        String.format(
                            "%d minute" + if (minutesUntil != 1L) "s" else "",
                            minutesUntil
                        )
                    )
                    .color(getWarningTextColor(referenceIntervalMillis))
                    .decorate(TextDecoration.BOLD)
            )
        }
        if (secondsUntil > 0) {
            if (hoursUntil > 0 || minutesUntil > 0) {
                text = text.append(Component.text(", "))
            }
            text = text.append(
                Component
                    .text(
                        String.format(
                            "%d second" + if (secondsUntil != 1L) "s" else "",
                            secondsUntil
                        )
                    )
                    .color(getWarningTextColor(referenceIntervalMillis))
                    .decorate(TextDecoration.BOLD)
            )
        }

        text = text
            .append(
                Component
                    .text("! (at ")
            )
            .append(
                Component
                    .text(
                        nextReset.withZoneSameInstant(ZoneId.of("UTC")).format(
                            DateTimeFormatterBuilder()
                                .appendPattern("yyyy-MM-dd HH:mm 'UTC'")
                                .toFormatter()
                        )
                    )
                    .decorate(TextDecoration.BOLD)
            )
            .append(Component.text(")"))

        world.forEachAudience { audience -> audience.sendMessage(text) }
    }

    override fun serialize(): Map<String?, Any?> {
        return super.serialize() + mapOf(
            "intervals" to intervals,
        )
    }
}