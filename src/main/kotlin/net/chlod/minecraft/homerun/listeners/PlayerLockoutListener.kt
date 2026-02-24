package net.chlod.minecraft.homerun.listeners

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.data.PlayerLockout
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerTeleportEvent

class PlayerLockoutListener(val plugin: Homerun) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoinEarly(event: PlayerJoinEvent) {
        val lockout = PlayerLockout.of(event.player.world)
        if (lockout.isLocked()) {
            lockout.handleLockout(plugin, event.player)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onTeleport(event: PlayerTeleportEvent) {
        val lockout = PlayerLockout.of(event.to.world)
        if (lockout.isLocked()) {
            event.isCancelled = true
            lockout.handleSoftLockout(plugin, event.player)
        }
    }

}