package net.chlod.minecraft.homerun.data

import org.bukkit.World

abstract class WorldLockout {

    val world: World?

    constructor(world: World) {
        this.world = world
    }

    protected constructor() {
        this.world = null
    }

    private var locked = false

    open fun isLocked(): Boolean {
        return locked
    }

    fun lock() {
        locked = true
    }

    fun unlock() {
        locked = false
    }

}