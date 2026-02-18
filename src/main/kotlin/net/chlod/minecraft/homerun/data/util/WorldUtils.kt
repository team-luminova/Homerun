package net.chlod.minecraft.homerun.data.util

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.World

class WorldUtils(val plugin: Homerun) {

    /**
     * Check if this world is a world that has been reset by Homerun before.
     */
    fun isResetWorld(world: World): Boolean {
        return world.persistentDataContainer.has(
            plugin.keys.reset
        )
    }

}