package net.chlod.minecraft.homerun.data

import net.chlod.minecraft.homerun.Homerun
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player

class PlayerLockout : WorldLockout {

    companion object {

        private val lockoutMap = mutableMapOf<World, PlayerLockout>()

        private val globalLockout = PlayerLockout()
        val global: PlayerLockout
            get() = globalLockout

        fun of(world: World): PlayerLockout {
            return lockoutMap.getOrPut(world) { PlayerLockout(world) }
        }

    }

    private constructor(world: World) : super(world)
    private constructor() : super()

    override fun isLocked(): Boolean {
        return super.isLocked() || (world != null && globalLockout.isLocked())
    }

    fun kickAll(plugin: Homerun) {
        if (world == null) {
            Bukkit.getServer().onlinePlayers.forEach { player ->
                handleLockout(plugin, player)
            }
        } else {
            world.players.forEach { player ->
                handleLockout(plugin, player)
            }
        }
    }

    fun handleLockout(plugin: Homerun, player: Player) {
        player.kick(plugin.messages.get("lockout-join"))
    }

    fun handleSoftLockout(plugin: Homerun, player: Player) {
        player.sendMessage(plugin.messages.get("lockout-teleport"))
    }

}