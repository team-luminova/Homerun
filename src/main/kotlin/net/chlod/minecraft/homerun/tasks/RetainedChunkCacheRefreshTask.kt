package net.chlod.minecraft.homerun.tasks

import net.chlod.minecraft.homerun.helpers.RetainedChunkCache
import org.bukkit.scheduler.BukkitRunnable

class RetainedChunkCacheRefreshTask(val retainedChunkCache: RetainedChunkCache) : BukkitRunnable() {

    override fun run() {
        retainedChunkCache.flushCaches(false)
    }

}