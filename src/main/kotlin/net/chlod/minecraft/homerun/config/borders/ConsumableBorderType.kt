package net.chlod.minecraft.homerun.config.borders

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetRule
import net.chlod.minecraft.homerun.config.borders.consumable.ConsumableBorderStatus
import net.chlod.minecraft.homerun.config.borders.consumable.effect.ConsumableBorderEffect
import net.chlod.minecraft.homerun.config.borders.consumable.effect.ConsumableBorderFreezeEffect
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
    val effects: List<ConsumableBorderEffect>
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

            val warningType = when (val warningTypeRaw = args["warning_type"]) {
                is String -> ConsumableBorderWarningType.valueOf(warningTypeRaw.uppercase())
                else -> null
            }
            val showAfter = args["show_after"] as? Int
            val duration = args["duration"] as? Int
            val regeneration = args["regeneration"] as? Int
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

            return ConsumableBorderType(
                tickPeriod,
                warningType ?: ConsumableBorderWarningType.ACTION_BAR,
                showAfter ?: 0,
                duration ?: -1,
                regeneration ?: 0,
                resetWhen,
                effects
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
            "effects" to effects.map {
                when (it) {
                    is ConsumableBorderFreezeEffect -> ConsumableBorderFreezeEffect.TYPE
                    else -> throw IllegalArgumentException("Unknown effect type: ${it::class}")
                }
            }
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
                borderStatus.reset(resetExtra = false)
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

    override fun onTick(plugin: Homerun, resetRule: ResetRule) {
        // n.b. a negative remaining time should imply an infinite amount.
        for (entry in trackedPlayers.entries) {
            val player = entry.key
            val borderStatus = entry.value

            if (!player.isValid) {
                // This user has left.
                trackedPlayers.remove(player)
                return
            }
            // Deduct time first
            if (!borderStatus.currentlyInsideBorder) {
                if (borderStatus.remainingTime > 0L) {
                    borderStatus.subtract(1)
                }
            } else {
                borderStatus.regeneratingTime = (borderStatus.regeneratingTime + regeneration)
                    .coerceAtMost(duration.toLong())
            }
            val areEffectsActive = !borderStatus.currentlyInsideBorder && borderStatus.remainingTime == 0L
            if (areEffectsActive) {
                borderStatus.ticksSinceEmpty += 1
            } else {
                borderStatus.ticksSinceEmpty = 0
            }
            borderStatus.save()

            effects.forEach {
                it.onTick(plugin, resetRule, this, player, borderStatus)
                if (areEffectsActive) {
                    it.onEmptyTick(plugin, resetRule, this, player, borderStatus)
                }
            }

            if (
                borderStatus.currentlyInsideBorder ||
                System.currentTimeMillis() - borderStatus.lastExitTime > (showAfter * 1000)
            ) {
                when (warningType) {
                    ConsumableBorderWarningType.BOSS_BAR ->
                        showBossBar(plugin, player, borderStatus)

                    ConsumableBorderWarningType.ACTION_BAR ->
                        showActionbar(player, borderStatus)
                }
            }
        }
    }

    fun showBossBar(plugin: Homerun, player: Player, status: ConsumableBorderStatus) {
        if (status.currentlyInsideBorder && (regeneration <= 0 || status.regeneratingTime == duration.toLong())) {
            if (bossBarCache[player] != null) {
                player.hideBossBar(bossBarCache[player]!!)
                bossBarCache.remove(player)
            }
            return
        }
        val percentage = status.remainingTime.coerceAtMost(duration.toLong()).toFloat() / duration.toFloat()
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

    fun showActionbar(player: Player, status: ConsumableBorderStatus) {
        if (status.currentlyInsideBorder && (regeneration <= 0 || status.regeneratingTime == duration.toLong())) {
            return
        }
        val percentage = status.remainingTime.coerceAtMost(duration.toLong()).toDouble() / duration.toDouble()
        val filled = ceil(percentage * 20).toInt()
        val unfilled = 20 - filled;
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

}