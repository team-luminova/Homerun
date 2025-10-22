package net.chlod.minecraft.homerun.data

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.NamespacedKey

class HomerunNamespacedKeys(val plugin: Homerun) {

    // World metadata
    val resetCount = NamespacedKey(plugin, "reset_count")
    val resetSourceWorld = NamespacedKey(plugin, "reset_source_world")

    // Player metadata
    val needsRespawn = NamespacedKey(plugin, "player_needs_respawn")
    val needsKill = NamespacedKey(plugin, "player_needs_kill")
    val needsTeleportToSpawn = NamespacedKey(plugin, "player_needs_teleport_to_spawn")
    val needsTeleportToHighest = NamespacedKey(plugin, "player_needs_teleport_to_highest")
    val needsTeleportToClosest = NamespacedKey(plugin, "player_needs_teleport_to_closest")
    val needsTeleportToClosestRetained = NamespacedKey(plugin, "player_needs_teleport_to_closest_retained")

}