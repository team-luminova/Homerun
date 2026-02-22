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
    private val resetInstructions: WorldResetLoadInstruction
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

    private val logger: Logger

    init {
        val worldContainer = plugin.server.worldContainer
        var srcDir = File(worldContainer, resetInstructions.sourceWorld)
        var dstDir = File(worldContainer, resetInstructions.targetWorld)

        if (resetInstructions.sourceWorldEnvironmentId != 0) {
            srcDir = File(srcDir, "DIM${resetInstructions.sourceWorldEnvironmentId}")
            dstDir = File(dstDir, "DIM${resetInstructions.sourceWorldEnvironmentId}")
        }

        // Ensure target directories exist
        for (subdir in listOf("region", "entities", "poi")) {
            File(srcDir, subdir).also {
                require(it.isDirectory) {
                    "Source $subdir directory missing: ${it.path}"
                }
            }
            File(dstDir, subdir).mkdirs()
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
        val src = surgery.openAll(sourceWorldDir, "source", dimension)
        val dst = surgery.openAll(targetWorldDir, "target", dimension)

        src.use { srcHandle ->
            dst.use { dstHandle ->
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
        src: NMSChunkSurgeryUtil.WorldStorageHandle,
        dst: NMSChunkSurgeryUtil.WorldStorageHandle,
        chunks: Set<Pair<Int, Int>>
    ) {
        logger.info("Importing ${chunks.size} chunks (region + entities + POI)...")
        var imported = 0
        for ((x, z) in chunks) {
            surgery.copyAll(src, dst, x, z)
            imported++
            if (imported % 500 == 0) {
                logger.info(":: Imported $imported / ${chunks.size} chunks...")
            }
        }
        logger.info("Imported $imported chunks.")
    }

    private fun deleteNonImportedChunks(
        dst: NMSChunkSurgeryUtil.WorldStorageHandle,
        importedChunks: Set<Pair<Int, Int>>
    ) {
        logger.info("Deleting all non-imported chunks from target world...")

        var deleted = 0
        for (type in NMSChunkSurgeryUtil.StorageType.entries) {
            val storage = when (type) {
                NMSChunkSurgeryUtil.StorageType.CHUNK -> dst.chunk
                NMSChunkSurgeryUtil.StorageType.ENTITY -> dst.entity
                NMSChunkSurgeryUtil.StorageType.POI -> dst.poi
            }
            val dir = targetWorldDir.resolve(type.dirName)
            deleted += deleteNonImportedFromDir(storage, dir, importedChunks, type.name)
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
        dst: NMSChunkSurgeryUtil.WorldStorageHandle,
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

