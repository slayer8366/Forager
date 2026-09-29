package com.zynergylabs.forager.app.map

import com.zynergylabs.forager.app.data.local.OfflineRegionDao
import com.zynergylabs.forager.app.data.local.OfflineRegionEntity
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.Region

/**
 * What `OfflineManager` reported for one region, as plain data: `OfflineManager` and
 * `OfflineRegion` cannot be built off a device, so [MapLibreOfflineMapRepository.listRegions] maps
 * what it reads into this and hands the decisions to [reconcileOfflineRegions], which a JVM test can
 * drive.
 */
internal class LiveRegion(
    val id: Long,
    val isComplete: Boolean,
    val metadata: ByteArray?,
    val tileCount: Long,
    val sizeBytes: Long,
)

/**
 * Reconciles `OfflineManager`'s read with the Room index and returns the regions to show.
 * [deleteRegion] deletes a region's tiles from `OfflineManager`; [inFlightIds] are downloads this
 * process is still running.
 */
internal suspend fun reconcileOfflineRegions(
    live: List<LiveRegion>,
    dao: OfflineRegionDao,
    inFlightIds: Set<Long>,
    deleteRegion: suspend (Long) -> Unit,
    warn: (String) -> Unit,
): List<OfflineRegionSummary> {
    // A Room row with no MapLibre region is kept, not shown, and logged every time it is seen.
    // MapLibre's read is not authoritative: an empty or partial read (a store that was not the one
    // the rows were written against, a read that raced initialisation) says nothing about whether
    // the row's tiles exist, and a row deleted on that say-so is gone for good. Only the user's own
    // delete (deleteRegion) removes a row.
    val liveIds = live.map { it.id }.toSet()
    roomRowIdsMissingFromRead(dao.getAll().map { it.id }.toSet(), liveIds).forEach { id ->
        warn("Room region row $id has no MapLibre region in this read; kept, not shown.")
    }

    return live.mapNotNull { region ->
        if (!region.isComplete) {
            when (val decision = incompleteRegionDecision(region, hasRoomRow = dao.getById(region.id) != null, inFlightIds)) {
                IncompleteRegionDecision.InFlight -> Unit
                IncompleteRegionDecision.Delete -> {
                    warn("Deleting region ${region.id}: it never finished (incomplete, not downloading now, no Room row, metadata never recorded a completion).")
                    deleteRegion(region.id)
                    dao.deleteById(region.id)
                }
                is IncompleteRegionDecision.Keep ->
                    warn("Region ${region.id} is incomplete; kept, not shown: ${decision.reason}.")
            }
            return@mapNotNull null
        }

        val row = dao.getById(region.id)
            ?: region.metadata?.toRegionMetadata()?.let { metadata ->
                OfflineRegionEntity(
                    id = region.id,
                    name = metadata.name,
                    lat = metadata.region.lat,
                    lng = metadata.region.lng,
                    radiusKm = metadata.region.radiusKm,
                    minZoom = metadata.minZoom,
                    maxZoom = metadata.maxZoom,
                    createdAtEpochMillis = metadata.downloadedAtEpochMillis,
                ).also { dao.upsert(it) }
            }
            ?: return@mapNotNull null

        OfflineRegionSummary(
            id = row.id,
            name = row.name,
            region = Region(row.lat, row.lng, row.radiusKm),
            minZoom = row.minZoom,
            maxZoom = row.maxZoom,
            tileCount = region.tileCount.toInt(),
            sizeBytes = region.sizeBytes,
            createdAtEpochMillis = row.createdAtEpochMillis,
        )
    }
}

/** Ids of Room rows that [read] (the ids `OfflineManager` listed) does not contain: to be logged, never deleted. */
internal fun roomRowIdsMissingFromRead(roomIds: Set<Long>, read: Set<Long>): Set<Long> = roomIds - read

internal sealed interface IncompleteRegionDecision {
    /** A download this process is still running. Not listed, not touched, not logged. */
    data object InFlight : IncompleteRegionDecision

    /** Provably never finished: deleted, tiles and row. */
    data object Delete : IncompleteRegionDecision

    /** Cannot be shown to have never finished: kept, not shown, logged with [reason]. */
    data class Keep(val reason: String) : IncompleteRegionDecision
}

/**
 * What to do with a region `OfflineManager` reports as incomplete. It is deleted only when it
 * provably never finished. `download()` creates the region with placeholder metadata
 * (`downloadedAtEpochMillis == 0`) and writes both the real timestamp and the Room row only after
 * the download completes, so a region with no Room row whose metadata still holds the placeholder
 * never finished. Anything that cannot be shown that way is kept: whether a completed region can
 * later report itself incomplete is native behaviour this code cannot rule out.
 */
internal fun incompleteRegionDecision(
    region: LiveRegion,
    hasRoomRow: Boolean,
    inFlightIds: Set<Long>,
): IncompleteRegionDecision {
    if (region.id in inFlightIds) return IncompleteRegionDecision.InFlight
    if (hasRoomRow) return IncompleteRegionDecision.Keep("it has a Room row, which is written only on completion")
    val metadata = region.metadata?.toRegionMetadata()
        ?: return IncompleteRegionDecision.Keep("its metadata is missing or unreadable")
    if (metadata.downloadedAtEpochMillis != 0L) {
        return IncompleteRegionDecision.Keep("its metadata records a completion time")
    }
    return IncompleteRegionDecision.Delete
}

/** What [MapLibreOfflineMapRepository.listRegions] makes of `OfflineManager`'s `onList` payload: a null list is a failed read, never an empty one. */
internal fun <T> regionListOrFailure(read: List<T>?): List<T> =
    read ?: throw java.io.IOException("listOfflineRegions returned no list.")

/**
 * The Room rows with no MapLibre region in [liveIds], as summaries that say so ([OfflineRegionSummary.isDownloaded]
 * `false`, no tiles, no size): what a backup restored onto a phone that never downloaded them leaves (owner, "1 B").
 * A read of what to list; **it deletes nothing**, and -106's guarantee (only the user's own delete removes a row)
 * stands. The caller must pass ids from a read it trusts: [MapLibreOfflineMapRepository.listNotDownloadedRegions]
 * fails, rather than passing an empty set, when `OfflineManager`'s read fails.
 */
internal fun notDownloadedRegions(rows: List<OfflineRegionEntity>, liveIds: Set<Long>): List<OfflineRegionSummary> =
    rows.filter { it.id !in liveIds }.map { row ->
        OfflineRegionSummary(
            id = row.id,
            name = row.name,
            region = Region(row.lat, row.lng, row.radiusKm),
            minZoom = row.minZoom,
            maxZoom = row.maxZoom,
            tileCount = 0,
            sizeBytes = 0L,
            createdAtEpochMillis = row.createdAtEpochMillis,
            isDownloaded = false,
        )
    }
