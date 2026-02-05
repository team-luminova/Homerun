package net.chlod.minecraft.homerun.data

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.NamespacedKey

class HomerunNamespacedKeys(val plugin: Homerun) {

    // World metadata

    /**
     * Key for the number of times a world has been reset.
     */
    val resetCount = NamespacedKey(plugin, "reset_count")

    /**
     * Key for the name of the source world from which this world was reset.
     */
    val resetSourceWorld = NamespacedKey(plugin, "reset_source_world")

    /**
     * Key for the seed of the source world from which this world was reset.
     */
    val resetSourceSeed = NamespacedKey(plugin, "reset_source_seed")

    /**
     * Key for the original seed of the world before any resets. This is retained throughout all resets.
     */
    val resetOriginalSeed = NamespacedKey(plugin, "reset_original_seed")

    // Player metadata

    /**
     * Key for the reset behavior for a player (respawned, killed, etc.).
     */
    val playerResetDisposition = NamespacedKey(plugin, "player_reset_disposition")

}