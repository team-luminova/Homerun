package net.chlod.minecraft.homerun.data.border

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType

/**
 * Helper class for accessing specific player border statuses. Right now, the structure allows
 * for multiple statuses, but we only ever write to one ID. This class is more or less a stub
 * upon which we can probably add proper serialization/deserialization helpers, if we ever
 * decided to support more than one consumable border.
 */
class PlayerBorderStatusHelper(val plugin: Homerun) {

    fun getStatus(player: Player, id: String): PersistentDataContainer? {
        val statuses = player.persistentDataContainer.get(
            plugin.keys.playerBorderStatuses,
            PersistentDataType.TAG_CONTAINER
        )
        val statusKey = NamespacedKey(plugin, id)
        return statuses?.get(statusKey, PersistentDataType.TAG_CONTAINER)
    }

    fun setStatus(player: Player, id: String, serialized: PersistentDataContainer) {
        val statusKey = NamespacedKey(plugin, id)
        val statuses = player.persistentDataContainer.get(
            plugin.keys.playerBorderStatuses,
            PersistentDataType.TAG_CONTAINER
        ) ?: player.persistentDataContainer.adapterContext.newPersistentDataContainer()
        statuses.set(statusKey, PersistentDataType.TAG_CONTAINER, serialized)
    }

    fun deleteStatus(player: Player, id: String): Boolean {
        val statuses = player.persistentDataContainer.get(
            plugin.keys.playerBorderStatuses,
            PersistentDataType.TAG_CONTAINER
        )
        if (statuses == null) {
            return false
        }
        statuses.remove(NamespacedKey(plugin, id))
        return true
    }


}