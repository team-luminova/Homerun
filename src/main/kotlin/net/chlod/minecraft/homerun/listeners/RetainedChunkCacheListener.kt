package net.chlod.minecraft.homerun.listeners

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldLoadEvent

class RetainedChunkCacheListener(val plugin: Homerun) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onWorldLoad(event: WorldLoadEvent) {
        plugin.retainedChunkCache.flushCaches()
    }

}