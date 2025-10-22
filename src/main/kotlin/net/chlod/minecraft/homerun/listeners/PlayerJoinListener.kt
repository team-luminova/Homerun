package net.chlod.minecraft.homerun.listeners

import net.chlod.minecraft.homerun.Homerun
import net.kyori.adventure.text.Component
import net.minecraft.world.entity.Entity
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.persistence.PersistentDataType

class PlayerJoinListener(val plugin: Homerun) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoinEarly(event: PlayerJoinEvent) {
        if (plugin.lockedDown) {
            event.player.kick(Component.text("Server is resetting the world. Please reconnect shortly."))
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onJoinLate(event: PlayerJoinEvent) {
        val needsRespawn = event.player.persistentDataContainer.get(
            plugin.keys.needsRespawn, PersistentDataType.BOOLEAN
        )
        if (needsRespawn != null && needsRespawn) {
            plugin.componentLogger.info("${event.player.name} was not in a retained chunk during reset. Respawning them.")
            // This user was outside the retained chunk range, and must be respawned.
            // TODO: Make configurable whether they should keep their inventory or not
            if (plugin.server is CraftServer) {
                val playerList = (plugin.server as CraftServer).server.playerList
                val serverPlayer = playerList.getPlayer(event.player.uniqueId)
                if (serverPlayer == null) {
                    // This should be impossible
                    plugin.componentLogger.error("Could not respawn player ${event.player.name}: server player not found!")
                    return
                }
                // TODO: Should be KILLED by config
                playerList.respawn(
                    serverPlayer,
                    true,
                    Entity.RemovalReason.CHANGED_DIMENSION,
                    PlayerRespawnEvent.RespawnReason.PLUGIN
                )
            } else {
                // Not a CraftServer? This should be impossible.
                // Just teleport them to their respawn location.
                event.player.teleport(event.player.world.spawnLocation)
            }
            event.player.persistentDataContainer.remove(plugin.keys.needsRespawn)
            event.player.sendMessage("You were in a reset chunk and have been respawned for safety.")
        }
    }

}