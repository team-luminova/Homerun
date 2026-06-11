package net.chlod.minecraft.homerun.config.borders

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.borders.consumable.ConsumableBorderStatus
import net.chlod.minecraft.homerun.config.borders.consumable.effect.ConsumableBorderEffect
import net.chlod.minecraft.homerun.config.borders.consumable.effect.ConsumableBorderFreezeEffect
import net.chlod.minecraft.homerun.config.borders.consumable.event.ConsumableBorderEvent
import net.chlod.minecraft.homerun.config.borders.consumable.modifier.ConsumableBorderModifier
import net.chlod.minecraft.homerun.config.borders.consumable.reset.ConsumableBorderOnEntryResetType
import net.chlod.minecraft.homerun.config.borders.consumable.reset.ConsumableBorderOnResetResetType
import net.chlod.minecraft.homerun.config.borders.consumable.reset.ConsumableBorderResetType
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import kotlin.math.ceil

class ConsumableBorderType(
    tickPeriod: Long?,
    val warningType: ConsumableBorderWarningType,
    val showAfter: Int,
    val duration: Int,
    val regeneration: Int,
    val resetWhen: List<ConsumableBorderResetType>,
    val resetExtra: Boolean,
    val maxExtra: Int,
    val effects: List<ConsumableBorderEffect>,
    val events: List<ConsumableBorderEvent>,
    val multipliers: List<ConsumableBorderModifier>,
    val subtractions: List<ConsumableBorderModifier>
) : ResetBorder(BorderType.CONSUMABLE, tickPeriod) {

    enum class ConsumableBorderWarningType {
        BOSS_BAR,
        ACTION_BAR
    }

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun deserialize(args: Map<String, Any>): ConsumableBorderType {
            val tickPeriod = (args["period"] as Int?)?.toLong()

            // TODO: Make warnings configurable
            val warningType = when (val warningTypeRaw = args["warning_type"]) {
                is String -> ConsumableBorderWarningType.valueOf(warningTypeRaw.uppercase())
                else -> null
            }
            val showAfter = args["show_after"] as? Int
            val duration = args["duration"] as? Int

            val regeneration = args["regeneration"] as? Int
            // TODO: Make reset triggers configurable
            val resetWhen = when (val resetWhenRaw = args["reset_when"] ?: listOf<ConsumableBorderResetType>()) {
                is List<*> -> {
                    if (!resetWhenRaw.all { it is String }) {
                        throw IllegalArgumentException("Elements of 'reset_when' must be strings")
                    } else {
                        resetWhenRaw.map {
                            when (it) {
                                ConsumableBorderOnResetResetType.TYPE ->
                                    ConsumableBorderOnResetResetType()

                                ConsumableBorderOnEntryResetType.TYPE ->
                                    ConsumableBorderOnEntryResetType()

                                else ->
                                    throw IllegalArgumentException("Invalid value for 'reset_when': $it")
                            }
                        }
                    }
                }

                else -> throw IllegalArgumentException("'reset_when' must be a list/array")
            }
            val resetExtra = args["reset_extra"] as? Boolean ?: false
            val maxExtra = args["max_extra"] as? Int ?: 0

            // TODO: Make effects configurable
            val effects = when (val effectsRaw = args["effects"] ?: listOf<ConsumableBorderEffect>()) {
                is List<*> -> {
                    if (!effectsRaw.all { it is String }) {
                        throw IllegalArgumentException("Elements of 'effects' must be strings")
                    } else {
                        effectsRaw.map {
                            when (it) {
                                ConsumableBorderFreezeEffect.TYPE ->
                                    ConsumableBorderFreezeEffect()

                                else ->
                                    throw IllegalArgumentException("Invalid value for 'effects': $it")
                            }
                        }
                    }
                }

                else -> throw IllegalArgumentException("'effects' must be a list/array")
            }

            val events = when (val eventsRaw = args["events"] ?: listOf<ConsumableBorderEvent>()) {
                is List<*> -> {
                    if (!eventsRaw.all { it is Map<*, *> }) {
                        throw IllegalArgumentException("Elements of 'events' must be maps")
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        eventsRaw.map { ConsumableBorderEvent.deserialize(it as Map<String, Any>) }
                    }
                }

                else -> throw IllegalArgumentException("'events' must be a list")
            }

            val multipliers = when (val multipliersRaw = args["multipliers"] ?: listOf<ConsumableBorderModifier>()) {
                is List<*> -> {
                    if (!multipliersRaw.all { it is Map<*, *> }) {
                        throw IllegalArgumentException("Elements of 'multipliers' must be maps")
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        multipliersRaw.map { ConsumableBorderModifier.deserialize(it as Map<String, Any>) }
                    }
                }

                else -> throw IllegalArgumentException("'multipliers' must be a list")
            }
            val subtractions = when (val subtractionsRaw = args["subtractions"] ?: listOf<ConsumableBorderModifier>()) {
                is List<*> -> {
                    if (!subtractionsRaw.all { it is Map<*, *> }) {
                        throw IllegalArgumentException("Elements of 'subtractions' must be maps")
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        subtractionsRaw.map { ConsumableBorderModifier.deserialize(it as Map<String, Any>) }
                    }
                }

                else -> throw IllegalArgumentException("'subtractions' must be a list")
            }

            return ConsumableBorderType(
                tickPeriod,
                warningType ?: ConsumableBorderWarningType.ACTION_BAR,
                showAfter ?: 0,
                duration ?: -1,
                regeneration ?: 0,
                resetWhen,
                resetExtra,
                maxExtra,
                effects,
                events,
                multipliers,
                subtractions
            )
        }
    }

    val trackedPlayers = mutableMapOf<Player, ConsumableBorderStatus>()
    val bossBarCache = mutableMapOf<Player, BossBar>()

    override fun serialize(): Map<String?, Any?> {
        return super.serialize() + mapOf(
            "warning_type" to warningType.name.lowercase(),
            "show_after" to showAfter,
            "duration" to duration,
            "regeneration" to regeneration,
            "reset_when" to resetWhen.map {
                when (it) {
                    is ConsumableBorderOnResetResetType -> ConsumableBorderOnResetResetType.TYPE
                    is ConsumableBorderOnEntryResetType -> ConsumableBorderOnEntryResetType.TYPE
                    else -> throw IllegalArgumentException("Unknown reset type: ${it::class}")
                }
            },
            "reset_extra" to resetExtra,
            "max_extra" to maxExtra,
            "effects" to effects.map {
                when (it) {
                    is ConsumableBorderFreezeEffect -> ConsumableBorderFreezeEffect.TYPE
                    else -> throw IllegalArgumentException("Unknown effect type: ${it::class}")
                }
            },
            "events" to events.map { it.serialize() },
            "multipliers" to multipliers.map { it.serialize() },
            "subtractions" to subtractions.map { it.serialize() }
        )
    }

    override fun doBorderUpdate(
        plugin: Homerun,
        resetRule: ResetRule,
        event: PlayerMoveEvent,
        from: Location,
        to: Location
    ) {
        val borderStatus = ConsumableBorderStatus.of(plugin, this, event.player)
        for (resetType in resetWhen) {
            // TODO: This is really inefficient when the player's been standing inside the border for a while.
            // Should probably consider doing willReset checks only if the player is being tracked. Of course,
            // that will probably break the on_entry reset type.
            if (resetType.willReset(event.player, this, resetRule, plugin)) {
                borderStatus.reset(resetExtra)
                borderStatus.save()
            }
        }
        val inRetainedChunk = plugin.retainedChunkCache.getRetainedChunks(event.player.world.name)
            ?.get(resetRule)
            ?.contains(
                Pair(
                    event.player.chunk.x,
                    event.player.chunk.z
                )
            ) ?: false
        if (!inRetainedChunk && borderStatus.currentlyInsideBorder) {
            borderStatus.currentlyInsideBorder = false
            borderStatus.lastExitTime = System.currentTimeMillis()
        } else if (inRetainedChunk && !borderStatus.currentlyInsideBorder) {
            borderStatus.currentlyInsideBorder = true
            borderStatus.lastEntryTime = System.currentTimeMillis()
        }
        trackedPlayers[event.player] = borderStatus
    }

    fun applyEvent(
        plugin: Homerun,
        player: Player,
        event: ConsumableBorderEvent
    ) {
        val borderStatus = trackedPlayers[player]

        if (borderStatus == null) {
            plugin.logger.warning("Tried to add extra time to untracked player ${player.name}!")
            return
        }

        when (event.target) {
            ConsumableBorderEvent.Target.REGENERATING -> {
                when (event.operation) {
                    ConsumableBorderEvent.Operation.ADD ->
                        borderStatus.regeneratingTime += event.amount

                    ConsumableBorderEvent.Operation.MULTIPLY ->
                        borderStatus.regeneratingTime *= event.amount
                }
            }

            ConsumableBorderEvent.Target.EXTRA -> {
                when (event.operation) {
                    ConsumableBorderEvent.Operation.ADD ->
                        borderStatus.extraTime += event.amount

                    ConsumableBorderEvent.Operation.MULTIPLY ->
                        borderStatus.extraTime *= event.amount
                }
                if (borderStatus.extraTime >= maxExtra) {
                    borderStatus.extraTime = maxExtra.toDouble()
                }
            }
        }
        borderStatus.save()
    }

    fun calculateSubtractions(
        plugin: Homerun,
        resetRule: ResetRule,
        border: ConsumableBorderType,
        player: Player
    ): Double {
        // Multiplication before subtraction. This allows a much larger amount to be subtracted from.
        return 1.0 * (
                this.multipliers
                    .map { if (it.test(plugin, resetRule, border, player)) it.amount else 1.0 }
                    .reduceOrNull { a, b -> a * b } ?: 1.0
                ) - (
                this.subtractions
                    .map { if (it.test(plugin, resetRule, border, player)) it.amount else 0.0 }
                    .reduceOrNull { a, b -> a + b } ?: 0.0
                )
    }

    override fun onTick(plugin: Homerun, resetRule: ResetRule) {
        // n.b. a negative remaining time should imply an infinite amount.
        for (entry in trackedPlayers.entries) {
            val player = entry.key
            val borderStatus = entry.value

            if (!player.isValid) {
                // This user has left.
                borderStatus.save()
                trackedPlayers.remove(player)
                return
            }
            // Deduct time first
            var deducted = false
            if (!borderStatus.currentlyInsideBorder) {
                val delta = calculateSubtractions(
                    plugin, resetRule, this, player
                )
                if (delta > 0) {
                    deducted = true
                }
                if (borderStatus.remainingTime > 0L) {
                    borderStatus.subtract(delta)
                }
            } else {
                borderStatus.regeneratingTime = (borderStatus.regeneratingTime + regeneration)
                    .coerceAtMost(duration.toDouble())
            }
            val isSOL = !borderStatus.currentlyInsideBorder && borderStatus.remainingTime == 0.0
            val areEffectsActive = deducted && isSOL
            if (areEffectsActive) {
                borderStatus.ticksSinceEmpty += 1
            } else if (!isSOL) {
                borderStatus.ticksSinceEmpty = 0
            }
            borderStatus.save()

            effects.forEach {
                it.onTick(plugin, resetRule, this, player, borderStatus)
                if (areEffectsActive) {
                    it.onEmptyTick(plugin, resetRule, this, player, borderStatus)
                }
            }

            val isRegenerating =
                borderStatus.currentlyInsideBorder && regeneration > 0 && borderStatus.remainingTime < duration
            val isTimeToShowWarning =
                System.currentTimeMillis() - borderStatus.lastExitTime > (showAfter * 1000)
            val isRunningOut =
                !borderStatus.currentlyInsideBorder && ((deducted && isTimeToShowWarning) || borderStatus.remainingTime == 0.0)
            if (isRegenerating || isRunningOut) {
                when (warningType) {
                    ConsumableBorderWarningType.BOSS_BAR ->
                        showBossBar(plugin, player, borderStatus)

                    ConsumableBorderWarningType.ACTION_BAR ->
                        showActionBar(player, borderStatus)
                }
            } else {
                when (warningType) {
                    ConsumableBorderWarningType.BOSS_BAR ->
                        hideBossBar(player)

                    else -> {}
                }
            }
        }
    }

    fun showBossBar(plugin: Homerun, player: Player, status: ConsumableBorderStatus) {
        val doubleDuration = duration.toDouble()
        val percentage = (status.remainingTime.coerceAtMost(doubleDuration) / doubleDuration).toFloat()
        val bossBar = bossBarCache[player] ?: BossBar.bossBar(
            plugin.messages.get("border-consumable-warning-bar"),
            percentage,
            BossBar.Color.BLUE,
            BossBar.Overlay.NOTCHED_20
        )
        bossBarCache[player] = bossBar
            .name(
                if (!status.currentlyInsideBorder)
                    plugin.messages.get("border-consumable-warning-bar")
                else
                    plugin.messages.get("border-consumable-warning-bar-regen")
            )
            .progress(percentage)
        player.showBossBar(bossBarCache[player]!!)
    }

    fun hideBossBar(player: Player) {
        if (bossBarCache[player] != null) {
            player.hideBossBar(bossBarCache[player]!!)
            bossBarCache.remove(player)
        }
    }

    fun showActionBar(player: Player, status: ConsumableBorderStatus) {
        if (status.currentlyInsideBorder && (regeneration <= 0 || status.regeneratingTime == duration.toDouble())) {
            return
        }
        val doubleDuration = duration.toDouble()
        val percentage = status.remainingTime.coerceAtMost(doubleDuration) / doubleDuration
        val filled = ceil(percentage * 20).toInt()
        val unfilled = 20 - filled
        player.sendActionBar(
            Component.text(
                "\u2B1B".repeat(filled)
            )
                .color(NamedTextColor.WHITE)
                .append(
                    Component.text(
                        "\u2B1B".repeat(unfilled)
                    )
                        .color(NamedTextColor.BLACK)
                ),
        )
    }

    fun flushAllStatuses() {
        for (status in trackedPlayers.values) {
            status.save()
        }
    }

    fun disposeAllBossBars() {
        val bossBars = HashMap(bossBarCache)
        bossBarCache.clear()
        bossBars.forEach { (player, bar) -> player.hideBossBar(bar) }
    }

}