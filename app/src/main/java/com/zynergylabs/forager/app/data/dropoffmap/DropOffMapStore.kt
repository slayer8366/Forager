package com.zynergylabs.forager.app.data.dropoffmap

import com.zynergylabs.forager.app.domain.mapdata.DropOffTileCopy
import com.zynergylabs.forager.app.domain.mapdata.DropOffTileCover
import com.zynergylabs.forager.app.domain.mapdata.RegionDropOffMap
import com.zynergylabs.forager.app.domain.mapdata.TileId
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The drop-off map copy on the phone (dispatch 2026-11-04-09): one folder per area under [root], named for the area's id,
 * holding its zoom-15 tiles as `15-<x>-<y>.mvt` (the tile, gzipped), `15-<x>-<y>.empty` for a tile the server answered
 * as empty, and [WHOLE] once nothing is wanted. Blocking file work, but for the two reads the way-to makes, which run on
 * [io].
 *
 * ## Why files, not Room
 *
 * CLAUDE.md's test is what the data will be queried for. The copy is asked two things, "this tile, from any area" and
 * "what does area N hold", and a folder answers both; nothing joins on it, filters by it or references it, and it is bulk
 * binary data, not a setting, so DataStore does not fit either. The route store made the same choice for the same
 * reasons (`data/routedata/RegionRouteStore`): no second record that could disagree with the disk, a journal backup that
 * copies `offline_regions` rows without these files (`data/backup/JournalTables.kt`), and no Room version to claim.
 *
 * ## The folder's life
 *
 * A tile is written to `<name>.partial` and renamed into place, so a reader sees it whole or absent. [beginPass] keeps
 * one pass per area in this process and makes its folder; a write after the area's copy was deleted fails, rather than
 * making the folder again. [delete] renames the folder aside first. [cleanOrphans] removes what no area owns. A tile
 * whose file cannot be unwrapped when read is discarded with the area's whole record, so the next pass fetches it again:
 * that is what a write cut short by a power loss leaves.
 */
internal class DropOffMapStore(
    private val root: File,
    private val log: (String, Throwable?) -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : DropOffTileCopy {
    /** Areas whose pass is running now, in this process: [cleanOrphans] leaves their folders alone. */
    private val passing: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    private fun folderFor(regionId: Long): File = File(root, regionId.toString())

    /**
     * Marks a pass for [regionId] as running and makes its folder; `false`, touching nothing, when one already runs. The
     * mark is set and tested in one step, so two passes can never both own the folder. Throws an [IOException] when the
     * folder cannot be made; the mark is then cleared. Only a caller that got `true` calls [endPass].
     */
    fun beginPass(regionId: Long): Boolean {
        if (!passing.add(regionId)) return false
        val folder = folderFor(regionId)
        if (!folder.isDirectory && !folder.mkdirs()) {
            passing -= regionId
            throw IOException("couldn't create ${folder.name}")
        }
        // A write a killed process left half done: never a tile.
        folder.listFiles { file -> file.name.endsWith(PARTIAL_SUFFIX) }?.forEach { it.delete() }
        return true
    }

    fun endPass(regionId: Long) {
        passing -= regionId
    }

    fun isWhole(regionId: Long): Boolean = File(folderFor(regionId), WHOLE).isFile

    /** Whether [regionId]'s copy has an answer for [tile]: a marked tile, or the server's empty one. Either way it is not wanted. */
    fun holds(regionId: Long, tile: TileId): Boolean {
        val folder = folderFor(regionId)
        return File(folder, tileName(tile)).isFile || File(folder, emptyName(tile)).isFile
    }

    /** Stores [mvt], the tile's decompressed bytes, gzipped, whole or not at all. Throws an [IOException] when it can't. */
    fun write(regionId: Long, tile: TileId, mvt: ByteArray) {
        val folder = existingFolder(regionId)
        val target = File(folder, tileName(tile))
        val partial = File(folder, target.name + PARTIAL_SUFFIX)
        GZIPOutputStream(FileOutputStream(partial)).use { it.write(mvt) }
        moveIntoPlace(partial, target)
    }

    /** Records that the server answered [tile] as empty (204), so it is not asked for again. Throws an [IOException] when it can't. */
    fun markEmpty(regionId: Long, tile: TileId) {
        val marker = File(existingFolder(regionId), emptyName(tile))
        if (!marker.isFile && !marker.createNewFile()) throw IOException("couldn't create ${marker.name}")
    }

    /** Records that nothing is wanted for [regionId]: its passes from now on ask for nothing. Throws an [IOException] when it can't. */
    fun markWhole(regionId: Long) {
        val folder = existingFolder(regionId)
        val partial = File(folder, WHOLE + PARTIAL_SUFFIX)
        partial.writeText("whole: every zoom-15 tile of the area is held here or marked in MapLibre's store\n")
        moveIntoPlace(partial, File(folder, WHOLE))
    }

    /** What [regionId]'s folder holds, read now. Its size is every file in it, the whole record's included. */
    fun status(regionId: Long): RegionDropOffMap {
        val files = folderFor(regionId).listFiles()?.filterNot { it.name.endsWith(PARTIAL_SUFFIX) } ?: return RegionDropOffMap.NONE
        return RegionDropOffMap(
            tiles = files.count { it.name.endsWith(TILE_SUFFIX) },
            emptyTiles = files.count { it.name.endsWith(EMPTY_SUFFIX) },
            sizeBytes = files.sumOf { it.length() },
            whole = files.any { it.name == WHOLE },
        )
    }

    override suspend fun tile(id: TileId): Result<ByteArray?> = withContext(io) {
        if (id.z != DropOffTileCover.ZOOM) return@withContext Result.success(null)
        for (folder in areaFolders()) {
            val file = File(folder, tileName(id))
            if (!file.isFile) continue
            try {
                return@withContext Result.success(GZIPInputStream(file.inputStream()).use { it.readBytes() })
            } catch (e: IOException) {
                discardNow(id, "it cannot be unwrapped (${e.message})")
            }
        }
        Result.success(null)
    }

    override suspend fun discard(id: TileId, reason: String) = withContext(io) { discardNow(id, reason) }

    private fun discardNow(id: TileId, reason: String) {
        for (folder in areaFolders()) {
            val file = File(folder, tileName(id))
            if (!file.exists()) continue
            val removed = file.delete()
            File(folder, WHOLE).delete()
            log(
                if (removed) {
                    "Removed tile ${id.z}/${id.x}/${id.y} from region ${folder.name}'s drop-off map copy: $reason. Its next pass fetches it again."
                } else {
                    "Couldn't remove tile ${id.z}/${id.x}/${id.y} from region ${folder.name}'s drop-off map copy ($reason); it is read past until it goes."
                },
                null,
            )
        }
    }

    /**
     * Deletes [regionId]'s copy. The folder is renamed out of the way first, which fails as a whole or not at all; if the
     * rename fails this throws, and the copy is still whole. Removing the renamed folder is retried by [cleanOrphans].
     */
    fun delete(regionId: Long) {
        val folder = folderFor(regionId)
        if (!folder.exists()) return
        val trash = File(root, "$regionId$DELETING_MARK${System.nanoTime()}")
        if (!folder.renameTo(trash)) throw IOException("couldn't move ${folder.name} aside to delete it")
        if (!trash.deleteRecursively()) log("Couldn't finish removing ${trash.name}; left for the next clean-up.", null)
    }

    /**
     * Removes every folder under [root] that no area in [keepIds] owns and no running pass is writing: an area's copy whose
     * area is gone, or a delete that did not finish. Each removal is logged; a name this store never writes is logged and
     * left alone. Never throws.
     */
    fun cleanOrphans(keepIds: Set<Long>) {
        val children = root.listFiles() ?: return
        for (child in children) {
            val reason = when {
                DELETING_MARK in child.name -> "an unfinished delete"
                child.name.toLongOrNull() == null -> {
                    log("The drop-off map copy holds '${child.name}', which it never writes; left alone.", null)
                    continue
                }
                child.name.toLong().let { it in keepIds || it in passing } -> continue
                else -> "no region ${child.name}"
            }
            val removed = child.deleteRecursively()
            log(
                if (removed) "Removed drop-off map copy ${child.name}: $reason." else "Couldn't remove drop-off map copy ${child.name} ($reason); left for the next clean-up.",
                null,
            )
        }
    }

    private fun existingFolder(regionId: Long): File =
        folderFor(regionId).takeIf { it.isDirectory } ?: throw IOException("region $regionId's drop-off map copy is gone")

    private fun moveIntoPlace(partial: File, target: File) {
        if (!partial.renameTo(target)) {
            partial.delete()
            throw IOException("couldn't move ${partial.name} into place")
        }
    }

    private fun areaFolders(): List<File> =
        root.listFiles { file -> file.isDirectory && file.name.toLongOrNull() != null }?.sortedBy { it.name }.orEmpty()

    private fun tileName(tile: TileId) = "${tile.z}-${tile.x}-${tile.y}$TILE_SUFFIX"

    private fun emptyName(tile: TileId) = "${tile.z}-${tile.x}-${tile.y}$EMPTY_SUFFIX"

    private companion object {
        const val TILE_SUFFIX = ".mvt"
        const val EMPTY_SUFFIX = ".empty"
        const val WHOLE = "whole"
        const val PARTIAL_SUFFIX = ".partial"
        const val DELETING_MARK = ".deleting-"
    }
}
