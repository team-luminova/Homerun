package net.chlod.minecraft.homerun.data

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.NamespacedKey

class HomerunNamespacedKeys(val plugin: Homerun) {

    // World metadata
    val resetCount = NamespacedKey(plugin, "reset_count")
    val resetSourceWorld = NamespacedKey(plugin, "reset_source_world")

    // Player metadata
    val playerResetDisposition = NamespacedKey(plugin, "player_reset_disposition")

}