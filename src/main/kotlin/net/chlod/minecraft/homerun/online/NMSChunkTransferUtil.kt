package net.chlod.minecraft.homerun.online

import net.chlod.minecraft.homerun.Homerun
import net.chlod.minecraft.homerun.data.world.WorldResetLoadInstruction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.storage.RegionFileStorage
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.file.Path
import java.util.logging.Logger

/**
 * The meat and bones of the plugin is wiping chunks while keeping some. This is where that happens.
 * To summarize, there's three tasks that need to be done when the server is reset with kept chunks:
 *
 *   1. The chunks from the old world are copied into the new world. There's three places where this
 *      happens: the region (blocks, block data, block entities, etc.), entities (mobs, animals,
 *      players, etc.), and points of interest (villager job blocks, bee homes, etc.). All of these
 *      need to be copied, and they're all stored in each world's `.mca` files.
 *   2. The non-imported chunks from the new world are deleted. When the world is first generated
 *      during the prepare task ([net.chlod.minecraft.homerun.tasks.ResetPrepareTask]), a bunch of
 *      extra chunks *might* be generated because the spawn point differs from the old world. In that
 *      case, we need to wipe those chunks completely. Theoretically, we don't actually have to do
 *      that, and we can get away with just wiping whatever's within 3 chunks of an imported chunk.
 *      But because we're here anyway, and this world is newly-generated, we can just delete them
 *      entirely. Poor server, because it had to generate those chunks, but whatever.
 *   3. The newly-imported chunks are marked for force blending. This forces the chunks to blend into
 *      the surrounding chunks. Normally, this behavior only ever runs when upgrading worlds, but it's
 *      possible to force it by editing NBT data. Aside from this, we delete heightmaps and lighting
 *      caches to make sure that the blending looks natural across the board.
 *
 * This complicated process happens in a few seconds, but having it work right is part of what makes
 * this plugin tick. If something goes wrong along the way, the world can get corrupted, or all hell
 * breaks loose. We don't want that, of course.
 */
class NMSChunkTransferUtil(
    private val plugin: Homerun,
    private val resetInstructions: WorldResetLoadInstruction,
    private val preLoad: Boolean,
) {

    companion object {
        private var CTU_LOGGER: Logger? = null
        private fun getLogger(plugin: JavaPlugin): Logger {
            if (CTU_LOGGER == null) {
                CTU_LOGGER = Logger.getLogger(plugin.javaClass.simpleName)
                CTU_LOGGER!!.parent = plugin.logger
            }
            return CTU_LOGGER!!
        }
    }

    private val surgery = NMSChunkSurgeryUtil()

    private val sourceWorldDir: Path
    private val targetWorldDir: Path
    private val sourceLevelDat: Path
    private val targetLevelDat: Path

    private val logger: Logger

    private class WorldStorageHandleOptional(
        val chunk: RegionFileStorage,
        val entity: RegionFileStorage?,
        val poi: RegionFileStorage?
    ) : AutoCloseable {
        override fun close() {
            chunk.close()
            entity?.close()
            poi?.close()
        }

        fun flush() {
            chunk.flush()
            entity?.flush()
            poi?.flush()
        }
    }

    init {
        val worldContainer = plugin.server.worldContainer
        var srcDir = File(worldContainer, resetInstructions.sourceWorld)
        var dstDir = File(worldContainer, resetInstructions.targetWorld)

        var srcLevelDat = File(srcDir, "level.dat")
        require(srcLevelDat.isFile) {
            "Source $srcDir does not have a level.dat file."
        }
        sourceLevelDat = srcLevelDat.toPath()
        var dstLevelDat = File(dstDir, "level.dat")
        require(dstLevelDat.isFile) {
            "Target $dstDir does not have a level.dat file."
        }
        targetLevelDat = dstLevelDat.toPath()

        if (resetInstructions.sourceWorldEnvironmentId != 0) {
            srcDir = File(srcDir, "DIM${resetInstructions.sourceWorldEnvironmentId}")
            dstDir = File(dstDir, "DIM${resetInstructions.sourceWorldEnvironmentId}")
        }

        if (preLoad) {
            // Newer 1.21.9+ may not have entities/poi dirs yet if no chunks ever generated.
            // Region is always required for chunk imports, though.
            File(srcDir, "region").also {
                require(it.isDirectory) { "Source region directory missing: ${it.path}" }
            }
            File(dstDir, "region").mkdirs()

            // entities/poi are optional; ensure target dirs exist so RegionFileStorage can write.
            File(dstDir, "entities").mkdirs()
            File(dstDir, "poi").mkdirs()
        }

        sourceWorldDir = srcDir.toPath()
        targetWorldDir = dstDir.toPath()

        logger = Logger.getLogger("rsi<${resetInstructions.sourceWorld}><${resetInstructions.targetWorld}>")
        logger.parent = getLogger(plugin)
    }

    fun transferChunks() {
        val chunks = resetInstructions.chunks
            ?: throw IllegalStateException("WorldResetLoadInstruction has no chunks to transfer")

        val chunkSet = chunks.toSet()

        logger.info("Opening source and target world storages...")
        val dimension = Level.OVERWORLD // dimension is already handled by DIM subfolder resolution

        val srcChunk =
            surgery.open(sourceWorldDir.resolve("region"), "source", NMSChunkSurgeryUtil.StorageType.CHUNK, dimension)
        val dstChunk =
            surgery.open(targetWorldDir.resolve("region"), "target", NMSChunkSurgeryUtil.StorageType.CHUNK, dimension)

        val srcEntitiesDir = sourceWorldDir.resolve("entities")
        val dstEntitiesDir = targetWorldDir.resolve("entities")
        val srcPoiDir = sourceWorldDir.resolve("poi")
        val dstPoiDir = targetWorldDir.resolve("poi")

        val srcEntity = if (srcEntitiesDir.toFile().isDirectory && dstEntitiesDir.toFile().isDirectory) {
            surgery.open(srcEntitiesDir, "source", NMSChunkSurgeryUtil.StorageType.ENTITY, dimension)
        } else null
        val dstEntity = if (dstEntitiesDir.toFile().isDirectory) {
            surgery.open(dstEntitiesDir, "target", NMSChunkSurgeryUtil.StorageType.ENTITY, dimension)
        } else null

        val srcPoi = if (srcPoiDir.toFile().isDirectory && dstPoiDir.toFile().isDirectory) {
            surgery.open(srcPoiDir, "source", NMSChunkSurgeryUtil.StorageType.POI, dimension)
        } else null
        val dstPoi = if (dstPoiDir.toFile().isDirectory) {
            surgery.open(dstPoiDir, "target", NMSChunkSurgeryUtil.StorageType.POI, dimension)
        } else null

        WorldStorageHandleOptional(srcChunk, srcEntity, srcPoi).use { srcHandle ->
            WorldStorageHandleOptional(dstChunk, dstEntity, dstPoi).use { dstHandle ->
                importChunks(srcHandle, dstHandle, chunkSet)
                deleteNonImportedChunks(dstHandle, chunkSet)
                forceBlendChunks(dstHandle, chunkSet)

                logger.info("Flushing storages...")
                dstHandle.flush()
            }
        }

        logger.info("Chunk transfer complete.")
    }

    private fun importChunks(
        src: WorldStorageHandleOptional,
        dst: WorldStorageHandleOptional,
        chunks: Set<Pair<Int, Int>>
    ) {
        logger.info("Importing ${chunks.size} chunks (region + entities + POI)...")
        var imported = 0
        for ((x, z) in chunks) {
            surgery.copy(src.chunk, dst.chunk, x, z)
            if (src.entity != null && dst.entity != null) {
                surgery.copy(src.entity, dst.entity, x, z)
            }
            if (src.poi != null && dst.poi != null) {
                surgery.copy(src.poi, dst.poi, x, z)
            }

            imported++
            if (imported % 500 == 0) {
                logger.info(":: Imported $imported / ${chunks.size} chunks...")
            }
        }
        logger.info("Imported $imported chunks.")
    }

    private fun deleteNonImportedChunks(
        dst: WorldStorageHandleOptional,
        importedChunks: Set<Pair<Int, Int>>
    ) {
        logger.info("Deleting all non-imported chunks from target world...")

        var deleted = 0

        // CHUNK is always present
        deleted += deleteNonImportedFromDir(dst.chunk, targetWorldDir.resolve("region"), importedChunks, "CHUNK")

        // ENTITY/POI are optional
        dst.entity?.let {
            deleted += deleteNonImportedFromDir(it, targetWorldDir.resolve("entities"), importedChunks, "ENTITY")
        }
        dst.poi?.let {
            deleted += deleteNonImportedFromDir(it, targetWorldDir.resolve("poi"), importedChunks, "POI")
        }

        logger.info("Deleted $deleted non-imported chunk entries across all storage types.")
    }

    private fun deleteNonImportedFromDir(
        storage: RegionFileStorage,
        dir: Path,
        importedChunks: Set<Pair<Int, Int>>,
        label: String
    ): Int {
        val regionFiles = dir.toFile().listFiles { _, name -> name.endsWith(".mca") } ?: return 0
        var deleted = 0
        val emptyRegionFiles = mutableListOf<File>()

        for (regionFile in regionFiles) {
            // Parse region coordinates from filename: r.<rx>.<rz>.mca
            val parts = regionFile.name.split(".")
            if (parts.size != 4) continue
            val rx = parts[1].toIntOrNull() ?: continue
            val rz = parts[2].toIntOrNull() ?: continue

            // Each region file contains 32×32 chunks
            val baseX = rx shl 5
            val baseZ = rz shl 5

            var hasRetainedChunks = false

            for (localX in 0..31) {
                for (localZ in 0..31) {
                    val chunkX = baseX + localX
                    val chunkZ = baseZ + localZ
                    val key = Pair(chunkX, chunkZ)

                    if (key in importedChunks) {
                        hasRetainedChunks = true
                    } else {
                        // Only issue a delete if the chunk actually exists. Avoid
                        // creating empty region files for non-existent chunks.
                        val pos = ChunkPos(chunkX, chunkZ)
                        if (storage.read(pos) != null) {
                            storage.write(pos, null)
                            deleted++
                        }
                    }
                }
            }

            if (!hasRetainedChunks) {
                emptyRegionFiles.add(regionFile)
            }
        }

        // Flush the storage to release file handles before deleting empty region files.
        if (emptyRegionFiles.isNotEmpty()) {
            storage.flush()
            var filesDeleted = 0
            for (file in emptyRegionFiles) {
                if (file.delete()) {
                    filesDeleted++
                } else {
                    logger.warning(":: [$label] Failed to delete empty region file: ${file.name}")
                }
            }
            if (filesDeleted > 0) {
                logger.info(":: [$label] Removed $filesDeleted empty .mca file(s).")
            }
        }

        if (deleted > 0) {
            logger.info(":: [$label] Deleted $deleted non-imported chunk(s).")
        }
        return deleted
    }

    private fun forceBlendChunks(
        dst: WorldStorageHandleOptional,
        chunks: Set<Pair<Int, Int>>
    ) {
        logger.info("Force-blending ${chunks.size} imported chunks...")
        var blended = 0

        for ((x, z) in chunks) {
            surgery.edit(dst.chunk, x, z) { tag ->
                applyForceBlend(tag)
            }
            blended++
            if (blended % 500 == 0) {
                logger.info(":: Force-blended $blended / ${chunks.size} chunks...")
            }
        }

        logger.info("Force-blended $blended chunks.")
    }

    private fun applyForceBlend(tag: CompoundTag) {
        // 1.18 build height is -64 to 320. Divided by 16, that's -4 to 20. A "section" or
        // sometimes called a "subchunk" is a 16 block-tall portion of an existing chunk.
        // This basically just says "blend everything from Y=-64 to Y=320".
        val blendingData = CompoundTag()
        blendingData.putInt("min_section", -4)
        blendingData.putInt("max_section", 20)
        tag.put("blending_data", blendingData)

        // Wipe light data, which needs to be recalculated. Otherwise, lighting looks weird.
        tag.remove("isLightOn")
        tag.remove("starlight.light_version")
        if (tag.contains("sections", Tag.TAG_LIST.toInt())) {
            val sections = tag.getList("sections", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until sections.size) {
                val section = sections.getCompound(i)
                // Wipe a bunch of data that gets invalidated anyway.
                section.remove("BlockLight")
                section.remove("SkyLight")
                section.remove("starlight.blocklight_state")
                section.remove("starlight.skylight_state")
            }
        }

        // Wipe heightmaps, which need recalculation.
        tag.remove("Heightmaps")
    }

}

