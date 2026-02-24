package net.chlod.minecraft.homerun.listeners

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.config.ResetParameters
import net.minecraft.world.entity.Entity
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.persistence.PersistentDataType
import kotlin.math.abs

class PlayerUpgradeListener(val plugin: Homerun) : Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    fun onJoinLate(event: PlayerJoinEvent) {
        checkPlayerForResetFlags(event.player)
    }

    fun checkPlayerForResetFlags(player: Player) {
        val playerResetDisposition = player.persistentDataContainer.get(
            plugin.keys.playerResetDisposition, PersistentDataType.STRING
        )
        if (playerResetDisposition != null) {
            val behavior = ResetParameters.OutsidePlayerBehavior.valueOf(playerResetDisposition.uppercase())
            when (behavior) {
                ResetParameters.OutsidePlayerBehavior.SPAWN -> handleRespawn(player)
                ResetParameters.OutsidePlayerBehavior.KILL -> handleKill(player)
                ResetParameters.OutsidePlayerBehavior.WORLD_SPAWN -> handleTeleportToWorldSpawn(player)
                ResetParameters.OutsidePlayerBehavior.HIGHEST -> handleTeleportToHighest(player)
                ResetParameters.OutsidePlayerBehavior.CLOSEST -> handleTeleportToClosest(player)
                ResetParameters.OutsidePlayerBehavior.IGNORE -> {}
            }
            player.persistentDataContainer.remove(plugin.keys.playerResetDisposition)
        }
    }

    private fun handleRespawn(player: Player) {
        plugin.componentLogger.info("${player.name} was not in a retained chunk during reset. Respawning them.")
        if (plugin.server is CraftServer) {
            val playerList = (plugin.server as CraftServer).server.playerList
            val serverPlayer = playerList.getPlayer(player.uniqueId)
            playerList.respawn(
                serverPlayer!!,
                true,
                Entity.RemovalReason.CHANGED_DIMENSION,
                PlayerRespawnEvent.RespawnReason.PLUGIN
            )
        } else {
            // Not a CraftServer? This should be impossible.
            // Just teleport them to their respawn location.
            player.teleport(player.world.spawnLocation)
        }
        player.sendMessage(plugin.messages.get("reset-spawn"))
    }

    private fun handleKill(player: Player) {
        plugin.componentLogger.info("${player.name} was not in a retained chunk during reset. Killing them.")
        player.health = 0.0
        player.sendMessage(plugin.messages.get("reset-kill"))
    }

    private fun handleTeleportToWorldSpawn(player: Player) {
        plugin.componentLogger.info("${player.name} was not in a retained chunk during reset. Teleporting to world spawn.")
        player.teleport(player.world.spawnLocation)
        player.sendMessage(plugin.messages.get("reset-worldspawn"))
    }

    private fun handleTeleportToHighest(player: Player) {
        plugin.componentLogger.info("${player.name} was not in a retained chunk during reset. Teleporting to highest block.")
        val highestLocation = player.world.getHighestBlockAt(player.location).location.add(0.0, 1.0, 0.0)
        player.teleport(highestLocation)
        player.sendMessage(plugin.messages.get("reset-highest"))
    }

    private fun handleTeleportToClosest(player: Player) {
        plugin.componentLogger.info("${player.name} was not in a retained chunk during reset. Teleporting to closest valid position.")
        // Find the closest valid position (solid block with 2 blocks of air above)
        val location = player.location.clone()
        val world = player.world

        var range = 1
        while (range < world.maxHeight / 2) {
            // Check the cube around a player (surfaces only, not the full volume)
            for (x in -range..range) {
                for (y in -range..range) {
                    for (z in -range..range) {
                        if (abs(x) != range && abs(y) != range && abs(z) != range) {
                            continue
                        }
                        val checkX = location.blockX + x
                        val checkY = location.blockY + y
                        val checkZ = location.blockZ + z
                        if (checkY < 0 || checkY >= world.maxHeight) {
                            continue
                        }
                        val block = world.getBlockAt(checkX, checkY, checkZ)
                        if (block.type.isSolid &&
                            world.getBlockAt(checkX, checkY + 1, checkZ).type.isAir &&
                            world.getBlockAt(checkX, checkY + 2, checkZ).type.isAir
                        ) {
                            val validLocation = block.location.add(0.0, 1.0, 0.0)
                            player.teleport(validLocation)
                            player.sendMessage(plugin.messages.get("reset-closest"))
                            return
                        }
                    }
                }
            }
            range++
        }

        // Teleport to highest if we can't find a valid point.
        handleTeleportToHighest(player)
    }


}