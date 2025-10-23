package net.chlod.minecraft.homerun.listeners

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.data.PlayerLockout
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent

class PlayerLockoutListener(val plugin: Homerun) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoinEarly(event: PlayerJoinEvent) {
        val lockout = PlayerLockout.of(event.player.world)
        if (lockout.isLocked()) {
            lockout.handleLockout(event.player)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val lockout = PlayerLockout.of(event.player.world)
        if (lockout.isLocked()) {
            lockout.handleLockout(event.player)
        }
    }

}