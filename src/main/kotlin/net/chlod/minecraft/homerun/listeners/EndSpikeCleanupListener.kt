package net.chlod.minecraft.homerun.listeners

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.helpers.EndPillarCleanup
import org.bukkit.World
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntitySpawnEvent

class EndSpikeCleanupListener(plugin: Homerun) : EndPillarCleanup(plugin), Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onEntitySpawn(event: EntitySpawnEvent) {
        if (event.location.world.environment != World.Environment.THE_END) {
            // Not the world we need to look at.
            return
        }
        if (!worldUtils.isResetWorld(event.location.world)) {
            // Not a world that has been reset.
            return
        }
        if (event.entity.type == EntityType.END_CRYSTAL) {
            for (stackFrame in Thread.currentThread().stackTrace) {
                if (
                    stackFrame.className === "net.minecraft.world.level.levelgen.feature.SpikeFeature" &&
                    stackFrame.methodName === "placeSpike"
                ) {
                    plugin.logger.info("Detected end crystal spawn at ${event.location} by SpikeFeature. Fixing...")
                    cleanupNewEndCrystal(event.location.world, event.entity)
                }
            }
        }
    }

}