package net.chlod.minecraft.homerun.data

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player

class PlayerLockout {

    companion object {

        private val lockoutMap = mutableMapOf<World, PlayerLockout>()

        private val globalLockout = PlayerLockout()
        val global: PlayerLockout
            get() = globalLockout

        fun of(world: World): PlayerLockout {
            return lockoutMap.getOrPut(world) { PlayerLockout(world) }
        }

    }

    val world: World?

    constructor(world: World) {
        this.world = world
    }

    private constructor() {
        this.world = null
    }

    private var lockedOut = false

    fun isLocked(): Boolean {
        return lockedOut || (world != null && globalLockout.isLocked())
    }

    fun lock() {
        lockedOut = true
    }

    fun unlock() {
        lockedOut = false
    }

    fun kickAll() {
        if (world == null) {
            Bukkit.getServer().onlinePlayers.forEach { player ->
                handleLockout(player)
            }
        } else {
            world.players.forEach { player ->
                handleLockout(player)
            }
        }
    }

    fun handleLockout(player: Player) {
        player.kick(Component.text("Server is resetting the world. Please reconnect shortly."))
    }

}