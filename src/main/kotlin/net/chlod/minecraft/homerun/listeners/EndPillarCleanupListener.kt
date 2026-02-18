package net.chlod.minecraft.homerun.listeners

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.data.util.WorldUtils
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.boss.DragonBattle
import org.bukkit.entity.EnderCrystal
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.world.WorldLoadEvent
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

class EndPillarCleanupListener : Listener {

    private val plugin: Homerun
    private val worldUtils: WorldUtils

    companion object {
        val endCrystalSpawnLocations: List<Pair<Int, Int>> by lazy {
            calculateEndCrystalSpawnLocations()
        }

        private fun calculateEndCrystalSpawnLocations(): List<Pair<Int, Int>> {
            val locations = mutableListOf<Pair<Int, Int>>()
            for (i in 0..9) {
                val centerX = floor(42.0 * cos(2.0 * (-Math.PI + (Math.PI / 10) * i)))
                val centerZ = floor(42.0 * sin(2.0 * (-Math.PI + (Math.PI / 10) * i)))
                locations.add(Pair(centerX.toInt(), centerZ.toInt()))
            }
            return locations
        }
    }

    constructor(plugin: Homerun) {
        this.plugin = plugin
        this.worldUtils = WorldUtils(plugin)

        // Clean up all worlds that are currently loaded in.
        for (world in plugin.server.worlds) {
            if (world.environment != World.Environment.THE_END) {
                // Not the world we need to look at.
                return
            }
            if (!worldUtils.isResetWorld(world)) {
                // Not a world that has been reset.
                return
            }
            cleanupEndWorld(world)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onWorldLoad(event: WorldLoadEvent) {
        if (event.world.environment != World.Environment.THE_END) {
            // Not the world we need to look at.
            return
        }
        if (!worldUtils.isResetWorld(event.world)) {
            // Not a world that has been reset.
            return
        }
        cleanupEndWorld(event.world)
    }

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
                    plugin.logger.info("Detected end crystal spawn at ${event.location} by SpikeFeature.")
                    plugin.logger.info("Fixing spike...")
                    cleanupNewEndCrystal(event.location.world, event.entity)
                }
            }
        }
    }

    fun cleanupEndWorld(world: World) {
        if (world.enderDragonBattle == null) {
            // This world doesn't have an ongoing ender dragon fight. New world?
            plugin.logger.warning("World ${world.name} is in the end, but doesn't have an ender dragon fight!")
            plugin.logger.warning("This may cause issues with end pillar regeneration and ender dragon respawning.")
            return
        }

        if (
            world.enderDragonBattle?.hasBeenPreviouslyKilled() ?: false &&
            world.enderDragonBattle?.respawnPhase == DragonBattle.RespawnPhase.NONE
        ) {
            // Ender dragon was killed before, and we're not in a respawn phase.
            // Check for existing end crystals, and destroy them if they exist.
            cleanupExistingEndCrystals(world)
        }
    }

    fun cleanupExistingEndCrystals(world: World) {
        for (loc in endCrystalSpawnLocations) {
            val spawnLocation = Location(world, loc.first.toDouble(), 128.0, loc.second.toDouble())
            val detectedEntities = world.getNearbyEntitiesByType(
                EnderCrystal::class.java,
                spawnLocation,
                2.0,
                128.0
            )
            if (detectedEntities.isNotEmpty()) {
                // We found an end crystal within the spawn range of an end spike.
                for (endCrystal in detectedEntities) {
                    cleanupExistingEndCrystal(world, endCrystal)
                }
            }
        }
    }

    fun cleanupExistingEndCrystal(world: World, endCrystal: Entity) {
        // Check if this is a valid end spike crystal.
        // A crystal is valid if it is within the spawn range of an end spike. The end spike spawn locations are
        // determinable by a known formula, regardless of seed. More info: https://minecraft.wiki/w/End_Spike#Construction
        var isPositionValid = false
        for (spawnLocation in endCrystalSpawnLocations) {
            val spawnX = spawnLocation.first
            val spawnZ = spawnLocation.second
            if (
                endCrystal.location.blockX !in (spawnX - 1)..(spawnX + 1)
                || endCrystal.location.blockZ !in (spawnZ - 1)..(spawnZ + 1)
            ) {
                // This end crystal is outside the spawn range of an end spike.
                isPositionValid = true
                break
            }
        }
        if (!isPositionValid) {
            // This end crystal is not within the spawn range of any end spike. Ignore it.
            return
        }

        // If we're here, then this is a valid end spike crystal. We need to check if it's positioned correctly.
        // Not on bedrock? Destroy it. This handles floating end crystals.
        val crystalBaseBlock = world.getBlockAt(
            endCrystal.location.blockX,
            endCrystal.location.blockY - 1,
            endCrystal.location.blockZ
        )
        if (crystalBaseBlock.type !== Material.BEDROCK) {
            plugin.logger.info("Detected end crystal at ${endCrystal.location} that is not positioned on bedrock. Removing it.")
            endCrystal.remove()
            return
        }
        val crystalBlock = world.getBlockAt(
            endCrystal.location.blockX,
            endCrystal.location.blockY,
            endCrystal.location.blockZ
        )
        // Positioned inside of obsidian? Destroy it and the bedrock block below it. This handles end crystals that are
        // stuck inside a spike.
        if (crystalBlock.type === Material.OBSIDIAN) {
            plugin.logger.info("Detected end crystal at ${endCrystal.location} that is stuck in obsidian. Removing it.")
            endCrystal.remove()
            crystalBaseBlock.type = Material.OBSIDIAN
        }
    }

    fun cleanupNewEndCrystal(world: World, endCrystal: Entity) {
        // Destroy all obsidian, bedrock, and iron bar blocks in a 6x6 radius 4 blocks above the end crystal.
        // This gets rid of any blocks from a previous seed that is above the end crystal, which would otherwise
        // create a floating end spike.
        val spikeRadius = 6
        val centerX = endCrystal.location.blockX
        val centerY = endCrystal.location.blockY + 4
        val centerZ = endCrystal.location.blockZ
        for (x in (centerX - spikeRadius)..(centerX + spikeRadius)) {
            for (y in centerY..256) {
                for (z in (centerZ - spikeRadius)..(centerZ + spikeRadius)) {
                    val block = world.getBlockAt(x, y, z)
                    if (
                        block.type === Material.OBSIDIAN ||
                        block.type === Material.BEDROCK ||
                        block.type === Material.IRON_BARS
                    ) {
                        block.type = Material.AIR
                    }
                }
            }
        }
    }

}