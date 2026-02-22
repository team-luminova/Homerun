package net.chlod.minecraft.homerun.offline

import java.io.File

/**
 * Simple data class holding references to the region, POI, and entities
 * directories of a Minecraft world. Replaces the former MCASelector dependency.
 */
data class WorldDirectories(
    val region: File,
    val poi: File,
    val entities: File
)

