package net.chlod.minecraft.homerun.config.borders.consumable

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.borders.ConsumableBorderType
import net.chlod.minecraft.homerun.data.border.PlayerBorderStatusHelper
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

class ConsumableBorderStatus(
    val plugin: Homerun,
    val border: ConsumableBorderType,
    val player: Player
) {

    companion object {
        private val statusCache = mutableMapOf<Triple<Homerun, ConsumableBorderType, Player>, ConsumableBorderStatus>()

        fun of(plugin: Homerun, border: ConsumableBorderType, player: Player): ConsumableBorderStatus {
            val cacheKey = Triple(plugin, border, player)
            if (!statusCache.containsKey(cacheKey)) {
                statusCache[cacheKey] = ConsumableBorderStatus(plugin, border, player)
            }
            return statusCache[cacheKey]!!
        }
    }

    private val keyRemainingTime = NamespacedKey(plugin, "remaining_time")
    private val keyCurrentlyInsideBorder = NamespacedKey(plugin, "currently_inside_border")
    private val keyLastExitTime = NamespacedKey(plugin, "last_exit_time")
    private val keyLastEntryTime = NamespacedKey(plugin, "last_entry_time")
    private val keyLastResetTime = NamespacedKey(plugin, "last_reset_time")

    var regeneratingTime: Long = border.duration.toLong()
    var extraTime: Long = 0L
    var ticksSinceEmpty: Long = 0L
    var currentlyInsideBorder: Boolean = false
    var lastExitTime: Long = 0L
    var lastEntryTime: Long = 0L
    var lastResetTime: Long = 0L

    val remainingTime
        get() = regeneratingTime + extraTime

    init {
        val helper = PlayerBorderStatusHelper(plugin).getStatus(player, "consumable")
        if (helper != null) {
            regeneratingTime = helper.get(keyRemainingTime, PersistentDataType.LONG) ?: border.duration.toLong()
            currentlyInsideBorder = helper.get(keyCurrentlyInsideBorder, PersistentDataType.BYTE)?.toInt() == 1
            lastExitTime = helper.get(keyLastExitTime, PersistentDataType.LONG) ?: 0L
            lastEntryTime = helper.get(keyLastEntryTime, PersistentDataType.LONG) ?: 0L
            lastResetTime = helper.get(keyLastResetTime, PersistentDataType.LONG) ?: 0L
        } else {
            this.save()
        }
    }

    fun subtract(n: Long) {
        if (n > extraTime) {
            val remaining = n - extraTime
            regeneratingTime = (regeneratingTime - remaining).coerceAtLeast(0)
        } else {
            extraTime -= n
        }
    }

    fun reset(resetExtra: Boolean = false) {
        regeneratingTime = border.duration.toLong()
        lastResetTime = System.currentTimeMillis()
        if (resetExtra) {
            extraTime = 0L
        }
    }

    fun save() {
        val newPdc = toPersistentDataContainer(player.persistentDataContainer.adapterContext)
        PlayerBorderStatusHelper(plugin).setStatus(player, "consumable", newPdc)
    }

    fun toPersistentDataContainer(adapter: PersistentDataAdapterContext): PersistentDataContainer {
        val pdc = adapter.newPersistentDataContainer()
        pdc.set(
            keyRemainingTime,
            PersistentDataType.LONG,
            regeneratingTime
        )
        pdc.set(
            keyCurrentlyInsideBorder,
            PersistentDataType.BYTE,
            if (currentlyInsideBorder) 1 else 0
        )
        pdc.set(
            keyLastExitTime,
            PersistentDataType.LONG,
            lastExitTime
        )
        pdc.set(
            keyLastEntryTime,
            PersistentDataType.LONG,
            lastEntryTime
        )
        pdc.set(
            keyLastResetTime,
            PersistentDataType.LONG,
            lastResetTime
        )
        return pdc
    }


}