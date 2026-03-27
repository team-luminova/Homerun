package net.chlod.minecraft.homerun.world

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntArrayTag
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.storage.RegionFileStorage
import net.minecraft.world.level.chunk.storage.RegionStorageInfo
import java.nio.file.Path

/**
 * Utility function for hijacking NMS internals to do our bidding.
 */
class NMSChunkSurgeryUtil {

    enum class StorageType(val dirName: String, val infoType: String) {
        CHUNK("region", "chunk"),
        ENTITY("entities", "entity"),
        POI("poi", "poi")
    }

    class WorldStorageHandle(
        val chunk: RegionFileStorage,
        val entity: RegionFileStorage,
        val poi: RegionFileStorage
    ) : AutoCloseable {
        override fun close() {
            chunk.close()
            entity.close()
            poi.close()
        }

        fun flush() {
            chunk.flush()
            entity.flush()
            poi.flush()
        }
    }

    companion object {
        private val STORAGE_CTOR by lazy {
            RegionFileStorage::class.java.getDeclaredConstructor(
                RegionStorageInfo::class.java, Path::class.java, Boolean::class.javaPrimitiveType
            ).also { it.isAccessible = true }
        }
    }

    fun open(
        dir: Path,
        worldName: String,
        type: StorageType = StorageType.CHUNK,
        dimension: ResourceKey<Level> = Level.OVERWORLD
    ): RegionFileStorage {
        val info = RegionStorageInfo(worldName, dimension, type.infoType)
        return STORAGE_CTOR.newInstance(info, dir, false) as RegionFileStorage
    }

    fun openAll(
        worldDir: Path,
        worldName: String,
        dimension: ResourceKey<Level> = Level.OVERWORLD
    ): WorldStorageHandle {
        return WorldStorageHandle(
            chunk = open(worldDir.resolve(StorageType.CHUNK.dirName), worldName, StorageType.CHUNK, dimension),
            entity = open(worldDir.resolve(StorageType.ENTITY.dirName), worldName, StorageType.ENTITY, dimension),
            poi = open(worldDir.resolve(StorageType.POI.dirName), worldName, StorageType.POI, dimension)
        )
    }

    fun close(storage: RegionFileStorage) =
        storage.close()

    fun read(storage: RegionFileStorage, x: Int, z: Int): CompoundTag? =
        storage.read(ChunkPos(x, z))

    fun write(storage: RegionFileStorage, x: Int, z: Int, tag: CompoundTag?) =
        storage.write(ChunkPos(x, z), tag)

    fun copy(src: RegionFileStorage, dst: RegionFileStorage, x: Int, z: Int) {
        val tag = src.read(ChunkPos(x, z)) ?: return
        dst.write(ChunkPos(x, z), tag)
    }

    fun copyAll(src: WorldStorageHandle, dst: WorldStorageHandle, x: Int, z: Int) {
        copy(src.chunk, dst.chunk, x, z)
        copy(src.entity, dst.entity, x, z)
        copy(src.poi, dst.poi, x, z)
    }

    /**
     * Copy a single storage type's chunk NBT, relocating to new coordinates.
     * Automatically updates the correct position tags for the storage type.
     */
    fun copyRelocated(
        src: RegionFileStorage, dst: RegionFileStorage,
        type: StorageType,
        srcX: Int, srcZ: Int, dstX: Int, dstZ: Int
    ) {
        val tag = src.read(ChunkPos(srcX, srcZ)) ?: return
        updateChunkPosition(tag, type, dstX, dstZ)
        dst.write(ChunkPos(dstX, dstZ), tag)
    }

    /**
     * Copy chunk data across **all three** storage types, relocating to new
     * coordinates. Position tags are updated appropriately for each type.
     *
     * Yet unused, but we might want to use this in the future when we add options to force the
     * import to be relative to the new world's spawn point.
     */
    fun copyAllRelocated(
        src: WorldStorageHandle, dst: WorldStorageHandle,
        srcX: Int, srcZ: Int, dstX: Int, dstZ: Int
    ) {
        copyRelocated(src.chunk, dst.chunk, StorageType.CHUNK, srcX, srcZ, dstX, dstZ)
        copyRelocated(src.entity, dst.entity, StorageType.ENTITY, srcX, srcZ, dstX, dstZ)
        copyRelocated(src.poi, dst.poi, StorageType.POI, srcX, srcZ, dstX, dstZ)
    }

    /**
     * Edit chunk NBT in-place with a transformation callback.
     */
    fun edit(storage: RegionFileStorage, x: Int, z: Int, transform: (CompoundTag) -> Unit) {
        val pos = ChunkPos(x, z)
        val tag = storage.read(pos) ?: return
        transform(tag)
        storage.write(pos, tag)
    }

    fun delete(storage: RegionFileStorage, x: Int, z: Int) =
        storage.write(ChunkPos(x, z), null)

    fun deleteAll(handle: WorldStorageHandle, x: Int, z: Int) {
        delete(handle.chunk, x, z)
        delete(handle.entity, x, z)
        delete(handle.poi, x, z)
    }

    /**
     * Updates the position tags in the given [CompoundTag] for the given [StorageType].
     *
     * - **CHUNK**: sets `xPos` and `zPos` (int tags)
     * - **ENTITY**: sets `Position` (int array `[x, z]`)
     * - **POI**: no position tag to update (keyed solely by ChunkPos in the region file)
     */
    fun updateChunkPosition(tag: CompoundTag, type: StorageType, x: Int, z: Int) {
        when (type) {
            StorageType.CHUNK -> {
                tag.putInt("xPos", x)
                tag.putInt("zPos", z)
            }

            StorageType.ENTITY -> {
                tag.put("Position", IntArrayTag(intArrayOf(x, z)))
            }

            StorageType.POI -> {
                // POI data has no chunk position tag embedded in the NBT.
                // Position is determined entirely by region file location.
            }
        }
    }
}