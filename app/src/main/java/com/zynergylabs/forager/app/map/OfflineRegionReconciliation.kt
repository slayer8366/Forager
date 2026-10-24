package com.zynergylabs.forager.app.map

import com.zynergylabs.forager.app.data.local.OfflineRegionDao
import com.zynergylabs.forager.app.data.local.OfflineRegionEntity
import com.zynergylabs.forager.app.data.routedata.RegionRouteFolders
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
    /** The style URL the region was downloaded against, from MapLibre's own region definition; `null` if it names none. */
    val styleUrl: String? = null,
)

/**
 * Reconciles `OfflineManager`'s read with the Room index and returns the regions to show.
 * [deleteRegion] deletes a region's tiles from `OfflineManager`; [inFlightIds] are downloads this
 * process is still running. [routes] holds each region's trail route data (dispatch 2026-10-23-03): each shown
 * region carries its state and counts its bytes into its size, a region deleted here loses its routes too, and
 * route folders no region owns are cleared last.
 */
internal suspend fun reconcileOfflineRegions(
    live: List<LiveRegion>,
    dao: OfflineRegionDao,
    inFlightIds: Set<Long>,
    deleteRegion: suspend (Long) -> Unit,
    warn: (String) -> Unit,
    routes: RegionRouteFolders,
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

    val deletedIds = mutableSetOf<Long>()
    val shown = live.mapNotNull { region ->
        if (!region.isComplete) {
            when (val decision = incompleteRegionDecision(region, hasRoomRow = dao.getById(region.id) != null, inFlightIds)) {
                IncompleteRegionDecision.InFlight -> Unit
                IncompleteRegionDecision.Delete -> {
                    warn("Deleting region ${region.id}: it never finished (incomplete, not downloading now, no Room row, metadata never recorded a completion).")
                    deleteRegion(region.id)
                    dao.deleteById(region.id)
                    deletedIds += region.id
                    deleteRoutesOrLeaveForCleanup(routes, region.id, warn)
                }
                is IncompleteRegionDecision.Keep ->
                    warn("Region ${region.id} is incomplete; kept, not shown: ${decision.reason}.")
            }
            return@mapNotNull null
        }

        // A complete region with no Room row is rebuilt from its metadata. One whose metadata is
        // missing or unreadable cannot be, and is left out of the list with its tiles kept; that is
        // logged with the reason (dispatch 2026-09-28-658, M1), as every other branch here is.
        val row = dao.getById(region.id)
            ?: when (val read = region.metadata?.readRegionMetadata()) {
                null -> {
                    warn("Region ${region.id} is complete but has no Room row and no metadata to rebuild one from; kept, not shown.")
                    return@mapNotNull null
                }
                is RegionMetadataRead.Unreadable -> {
                    warn("Region ${region.id} is complete but has no Room row, and its metadata can't be read (${read.reason}); kept, not shown.")
                    return@mapNotNull null
                }
                is RegionMetadataRead.Parsed -> read.metadata.let { metadata ->
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
            }

        val routeData = routes.status(row.id)
        OfflineRegionSummary(
            id = row.id,
            name = row.name,
            region = Region(row.lat, row.lng, row.radiusKm),
            minZoom = row.minZoom,
            maxZoom = row.maxZoom,
            tileCount = region.tileCount.toInt(),
            // One unit to the user: the region's size is its tiles and its routes (dispatch 2026-10-23-03, item 2).
            sizeBytes = region.sizeBytes + routeData.sizeBytes,
            createdAtEpochMillis = row.createdAtEpochMillis,
            styleUrl = region.styleUrl,
            routeData = routeData,
        )
    }

    // Kept: every Room row (a row with no MapLibre region in this read is kept, above, and so are its routes), every
    // region in the read but those just deleted as never finished, and every download still running.
    routes.cleanOrphans(dao.getAll().map { it.id }.toSet() + (liveIds - deletedIds) + inFlightIds)
    return shown
}

/**
 * Deletes [regionId]'s routes after its tiles and row are gone. A failure is logged, not thrown: the region is already
 * gone, and its folder, now owned by no region, is removed by the next [RegionRouteFolders.cleanOrphans].
 */
internal suspend fun deleteRoutesOrLeaveForCleanup(routes: RegionRouteFolders, regionId: Long, warn: (String) -> Unit) {
    try {
        routes.delete(regionId)
    } catch (e: java.io.IOException) {
        warn("Couldn't delete region $regionId's routes (${e.message}); the next listing clears them.")
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
    val metadata = when (val read = region.metadata?.readRegionMetadata()) {
        null -> return IncompleteRegionDecision.Keep("its metadata is missing")
        is RegionMetadataRead.Unreadable -> return IncompleteRegionDecision.Keep("its metadata is unreadable (${read.reason})")
        is RegionMetadataRead.Parsed -> read.metadata
    }
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
