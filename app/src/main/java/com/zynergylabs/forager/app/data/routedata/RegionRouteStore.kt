package com.zynergylabs.forager.app.data.routedata

import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Each offline region's trail route files, on the phone (dispatch 2026-10-23-03): one folder per region under [root],
 * named for the region's id, holding the `.rd5` files under BRouter's own names and the folder's own record
 * ([RECORD_FILE_NAME], [RegionRouteRecord]). Blocking file work; callers run it off the main thread.
 *
 * ## Why the state lives in the folder, not in Room
 *
 * The dispatch left the choice to the coder ("if a column or table is needed"); none is. CLAUDE.md's test is what the
 * data will be queried for: nothing joins on or filters by a region's route state, and the one lookup that reads it
 * ("routes for the area containing X") already lists every region. A Room column would also be a second record of
 * what is on disk, and could disagree with it: a journal backup copies `offline_regions` rows onto another phone
 * without these files (`data/backup/JournalTables.kt`), and a row would then claim routes the phone does not have.
 * Reading the folder on every listing is the rule the tile sizes already follow (`OfflineMapRepository.listRegions`).
 * And no Room change means no migration number to claim beside two other branches in flight, and no change to the
 * backup's tables, which are outside this dispatch's files.
 *
 * ## The folder's life
 *
 * A fetch writes into `<id>.partial`, writes the record last, and renames the folder to `<id>`; so a folder under an
 * id is whole or absent. Deleting renames `<id>` to `<id>.deleting-<n>` first, so a half-deleted folder is never read
 * as a region's routes. [cleanOrphans] removes what neither a region nor a running fetch owns.
 */
internal class RegionRouteStore(
    private val root: File,
    private val log: (String, Throwable?) -> Unit,
) {
    /** Region ids whose fetch is writing its `.partial` folder now, in this process: [cleanOrphans] leaves those alone. */
    private val fetching: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    fun folderFor(regionId: Long): File = File(root, regionId.toString())

    /**
     * An empty `.partial` folder for [regionId]'s fetch, marked as running until [endFetch]; or `null`, touching
     * nothing, when a fetch for [regionId] is already running (Sorrel's review of #383, M1). The mark is set and tested
     * in one step, so two callers can never both own the folder. Only a caller that got a folder calls [endFetch].
     */
    fun beginFetch(regionId: Long): File? {
        if (!fetching.add(regionId)) return null
        val partial = partialFor(regionId)
        if (partial.exists() && !partial.deleteRecursively()) throw IOException("couldn't clear ${partial.name}")
        if (!partial.mkdirs()) throw IOException("couldn't create ${partial.name}")
        return partial
    }

    /**
     * Writes [record] into [partial] and moves it into place as [regionId]'s folder, replacing any older one. For any
     * record but stored routes, files a failed fetch left are removed first: routes are kept whole or not at all.
     */
    fun commit(regionId: Long, partial: File, record: RegionRouteRecord) {
        // Kept: the files the record names, and nothing else. A failed fetch keeps none; a stored one keeps only its
        // own, so a stray from an earlier attempt (a superseded run) is never left beside them (#383 review, L1).
        val keep = (record as? RegionRouteRecord.Stored)?.files?.map { it.name }?.toSet().orEmpty()
        partial.listFiles()?.filter { it.name !in keep }?.forEach { stray ->
            if (!stray.deleteRecursively()) throw IOException("couldn't remove ${stray.name} from ${partial.name}")
        }
        // Synced before the rename, so a power loss after it cannot leave a renamed folder with a short record (L2).
        writeSynced(File(partial, RECORD_FILE_NAME), record.toBytes())
        val target = folderFor(regionId)
        if (target.exists()) discard(regionId, target)
        if (!partial.renameTo(target)) throw IOException("couldn't move ${partial.name} to ${target.name}")
    }

    /** Clears whatever [beginFetch] left that [commit] did not take, and stops protecting it. */
    fun endFetch(regionId: Long) {
        val partial = partialFor(regionId)
        if (partial.exists() && !partial.deleteRecursively()) log("Couldn't remove ${partial.name}; left for the next clean-up.", null)
        fetching -= regionId
    }

    /** What [regionId]'s folder holds, read now; [RegionRouteData.Fetching] while a fetch for it runs. */
    fun status(regionId: Long): RegionRouteData {
        val folder = folderFor(regionId)
        if (!folder.isDirectory) return RegionRouteData.NotFetched
        val recordFile = File(folder, RECORD_FILE_NAME)
        val bytes = try {
            recordFile.readBytes()
        } catch (e: IOException) {
            return damaged(regionId, "its record can't be read (${e.message})")
        }
        val record = when (val read = bytes.readRegionRouteRecord()) {
            is RegionRouteRecordRead.Unreadable -> return damaged(regionId, "its record is unreadable (${read.reason})")
            is RegionRouteRecordRead.Parsed -> read.record
        }
        return when (record) {
            RegionRouteRecord.NoneHere -> RegionRouteData.NoneHere
            is RegionRouteRecord.Missing -> RegionRouteData.Missing(record.reason)
            is RegionRouteRecord.Stored -> {
                // Sizes are checked on every read; the sha256 was checked when the file was stored, and BRouter
                // checks its own cell checksums as it reads.
                record.files.firstOrNull { File(folder, it.name).length() != it.size }?.let { file ->
                    val on = File(folder, file.name)
                    return damaged(regionId, if (on.exists()) "${file.name} is ${on.length()} bytes, recorded ${file.size}" else "${file.name} is gone")
                }
                RegionRouteData.Stored(record.runId, record.osmTimestamp, record.files.sumOf { it.size }, folder, record.missingSquares)
            }
        }
    }

    /**
     * Deletes [regionId]'s routes. The folder is renamed out of the way first, which fails as a whole or not at all; if
     * the rename fails this throws, and the routes are still whole. Removing the renamed folder's files is retried by
     * [cleanOrphans] when it does not finish.
     */
    fun delete(regionId: Long) {
        val folder = folderFor(regionId)
        if (folder.exists()) discard(regionId, folder)
    }

    /**
     * Removes every folder under [root] that no region in [keepIds] owns and no running fetch is writing: a region's
     * folder whose region is gone, a `.partial` a killed process left, a `.deleting` folder a delete did not finish.
     * Each removal is logged; a name this store never writes is logged and left alone. Never throws.
     */
    fun cleanOrphans(keepIds: Set<Long>) {
        val children = root.listFiles() ?: return
        for (child in children) {
            when (val verdict = verdictFor(child.name, keepIds)) {
                Verdict.Keep -> Unit
                Verdict.Unknown -> log("Route data folder holds '${child.name}', which it never writes; left alone.", null)
                is Verdict.Remove -> {
                    val removed = child.deleteRecursively()
                    log(
                        if (removed) "Removed route folder ${child.name}: ${verdict.reason}." else "Couldn't remove route folder ${child.name} (${verdict.reason}); left for the next clean-up.",
                        null,
                    )
                }
            }
        }
    }

    private sealed interface Verdict {
        data object Keep : Verdict

        data object Unknown : Verdict

        data class Remove(val reason: String) : Verdict
    }

    private fun verdictFor(name: String, keepIds: Set<Long>): Verdict {
        if (DELETING_MARK in name) return Verdict.Remove("an unfinished delete")
        if (name.endsWith(PARTIAL_SUFFIX)) {
            val id = name.removeSuffix(PARTIAL_SUFFIX).toLongOrNull() ?: return Verdict.Unknown
            return if (id in fetching) Verdict.Keep else Verdict.Remove("a fetch that never finished")
        }
        val id = name.toLongOrNull() ?: return Verdict.Unknown
        return if (id in keepIds || id in fetching) Verdict.Keep else Verdict.Remove("no region $id")
    }

    private fun writeSynced(file: File, bytes: ByteArray) {
        java.io.FileOutputStream(file).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
    }

    private fun discard(regionId: Long, folder: File) {
        val trash = File(root, "$regionId$DELETING_MARK${System.nanoTime()}")
        if (!folder.renameTo(trash)) throw IOException("couldn't move ${folder.name} aside to delete it")
        if (!trash.deleteRecursively()) log("Couldn't finish removing ${trash.name}; left for the next clean-up.", null)
    }

    private fun damaged(regionId: Long, detail: String): RegionRouteData {
        log("Route data for region $regionId is damaged: $detail.", null)
        return RegionRouteData.Damaged(detail)
    }

    private fun partialFor(regionId: Long): File = File(root, "$regionId$PARTIAL_SUFFIX")

    private companion object {
        const val PARTIAL_SUFFIX = ".partial"
        const val DELETING_MARK = ".deleting-"
    }
}
