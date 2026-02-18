package net.chlod.minecraft.homerun.helpers

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.data.util.WorldUtils
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.boss.DragonBattle
import org.bukkit.entity.EnderCrystal
import org.bukkit.entity.Entity
import org.bukkit.persistence.PersistentDataType
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

open class EndPillarCleanup {

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
            val z = buffer.double  // Read second double (z coordinate)
            Pair(x, z)
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
            val crystalZ = crystal.location.z
            if (knownEndCrystalLocations.any { (x, z) ->
                    x in (crystalX - 1)..(crystalX + 1) && z in (crystalZ - 1)..(crystalZ + 1)
                }) {
                // This end crystal is in a known spawn location. Ignore it.
                continue
            }
            // This end crystal is not in a known spawn location. Destroy it.
            plugin.logger.info("Detected unrecognized end crystal at ${crystal.location}. Removing it.")
            crystal.remove()
        }
    }

    fun cleanupExistingEndCrystal(world: World, endCrystal: Entity) {
        // TODO: Keep track of crystals that exist at reset time and delete all crystals (within spike radii) that
        // aren't in that list for any reason (upon world postload).

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