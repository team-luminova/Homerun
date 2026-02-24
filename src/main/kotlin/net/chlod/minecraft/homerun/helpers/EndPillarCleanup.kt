package net.chlod.minecraft.homerun.helpers

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.data.util.WorldUtils
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.boss.DragonBattle
import org.bukkit.entity.EnderCrystal
import org.bukkit.entity.Entity
import org.bukkit.persistence.PersistentDataType

open class EndPillarCleanup {

    protected val plugin: Homerun
    protected val worldUtils: WorldUtils

    constructor(plugin: Homerun) {
        this.plugin = plugin
        this.worldUtils = WorldUtils(plugin)
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
        val serializedLocations = world.persistentDataContainer.get(
            plugin.keys.endCrystals,
            PersistentDataType.LIST.byteArrays()
        ) ?: emptyList()

        val knownEndCrystalLocations = serializedLocations.map { bytes ->
            val buffer = java.nio.ByteBuffer.wrap(bytes)
            val x = buffer.double  // Read first double (x coordinate)
            val y = buffer.double  // Read first double (x coordinate)
            val z = buffer.double  // Read second double (z coordinate)
            Triple(x, y, z)
        }

        val foundCrystals = world.getEntitiesByClass(EnderCrystal::class.java)
        plugin.logger.info(
            "Found ${foundCrystals.size} end crystals in the world. " +
                    if (foundCrystals.isEmpty()) "No end crystals to clean up."
                    else "Checking for unrecognized crystals..."
        )
        for (crystal in foundCrystals) {
            if (!crystal.isShowingBottom) {
                // This end crystal is user-placed. Ignore it.
                continue
            }
            val crystalX = crystal.location.x
            val crystalY = crystal.location.y
            val crystalZ = crystal.location.z
            if (knownEndCrystalLocations.any { (x, y, z) ->
                    x in (crystalX - 1)..(crystalX + 1) &&
                            y in (crystalY - 1)..(crystalY + 1) &&
                            z in (crystalZ - 1)..(crystalZ + 1)
                }) {
                // This end crystal is recognized. Ignore it.
                continue
            }
            // This end crystal is not recognized. Destroy it.
            plugin.logger.info("Detected unrecognized end crystal at ${crystal.location}. Removing it.")
            crystal.remove()
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
        // Find any end crystal that may still be alive in this area that isn't our current crystal.
        val nearbyCrystals =
            world.getNearbyEntities(endCrystal.location, spikeRadius.toDouble(), 320.0 / 2, spikeRadius.toDouble())
                .filterIsInstance<EnderCrystal>()
                .filter { it != endCrystal && it.isShowingBottom }
        for (crystal in nearbyCrystals) {
            plugin.logger.info("Detected old end crystal at ${crystal.location} during new crystal cleanup. Removing it.")
            crystal.remove()
        }
    }

}