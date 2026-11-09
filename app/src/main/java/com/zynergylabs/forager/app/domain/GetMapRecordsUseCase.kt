package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.RecordPolyline
import com.zynergylabs.forager.app.domain.model.RecordRegion

/** A kind of saved record the Maps tab draws; names a read that failed in [MapRecords.failures]. */
enum class MapRecordKind { FINDS, PHOTOS, TRACKS, OFFLINE_REGIONS }

/** One kind's read failed: that kind is drawn as absent, and the caller logs [error]. */
data class MapRecordReadFailure(val kind: MapRecordKind, val error: Throwable)

/**
 * Every saved record the Maps tab draws, as record geometry with each record's id (map layers L0b,
 * B2). A kind whose read failed is empty here and named in [failures]; the other kinds are still
 * filled.
 */
data class MapRecords(
    val findMarkers: List<RecordPoint>,
    val photoMarkers: List<RecordPoint>,
    val trackPolylines: List<RecordPolyline>,
    val offlineRegionCircles: List<RecordRegion>,
    val failures: List<MapRecordReadFailure>,
) {
    /**
     * These records without the find, the album photo and the offline region whose deletes are in
     * their Undo window (planner's ruling on Q7, message 3 on dispatch 2026-09-28-03): the map leaves
     * them out at once, as Records does, and they come back on Undo. The pending ids live in the
     * ViewModels' pending-delete slots (J4: one per kind), so the screen passes them here; a `null` id
     * means nothing of that kind is pending. Tracks have no pending-delete slot, so they are untouched.
     */
    fun withoutPending(findId: String?, photoId: String?, offlineRegionId: String?): MapRecords {
        if (findId == null && photoId == null && offlineRegionId == null) return this
        return copy(
            findMarkers = if (findId == null) findMarkers else findMarkers.filterNot { it.recordId == findId },
            photoMarkers = if (photoId == null) photoMarkers else photoMarkers.filterNot { it.recordId == photoId },
            offlineRegionCircles = if (offlineRegionId == null) {
                offlineRegionCircles
            } else {
                offlineRegionCircles.filterNot { it.recordId == offlineRegionId }
            },
        )
    }

    companion object {
        val NONE = MapRecords(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/**
 * Every saved record the Maps tab draws (map layers L0b, B2; the owner: "Every saved record"): every
 * saved (non-draft) find with a location, every track in Records, every album photo with a location,
 * and every offline region, whether or not a Journal entry keeps it (ruling 3 clarified). Each through
 * the read path the Journal and Records already use, so the map and those tabs cannot disagree on what
 * "saved" means: [GetMushroomLogEntriesUseCase] (drafts excluded), [GetTracksUseCase],
 * [GetGalleryPhotosUseCase] and [OfflineMapRepository.listRegions].
 *
 * The rules the entry map already applies ([GetCartographyEntryMapDataUseCase]) apply here too: a track
 * that resolves to zero points gives no polyline, and a photo with no coordinate gives nothing. A find
 * with no location gives nothing, as it does there.
 *
 * **The track being recorded is left out** (owner's ruling on Q6, "Leave it out (Recommended)"): a
 * track with no end time is being recorded, and the "Recording trail" layer already draws it; it joins
 * Tracks once it has ended. Drawing it here too would lay a solid copy, as of the last reload, over the
 * dashed trail, since kept tracks draw above the breadcrumb in the registry.
 *
 * **A record with a pending delete** is left out by the screen through [MapRecords.withoutPending]
 * (planner's ruling on Q7): the pending state lives in the ViewModels (J4), and this reads the
 * repositories.
 *
 * **A failed read is not silent.** That kind is empty and named in [MapRecords.failures], and the
 * caller logs it (CLAUDE.md, Errors); the other kinds still draw. This is not the entry map's policy of
 * dropping an unresolvable reference silently: there a missing record is expected, here a failed read
 * of a whole kind is a failure.
 *
 * Headless: no map or Android type appears here. The Maps tab's hosts put these lists into
 * `MapOverlayContent`'s record fields.
 */
class GetMapRecordsUseCase(
    private val getFinds: GetMushroomLogEntriesUseCase,
    private val getTracks: GetTracksUseCase,
    private val getPhotos: GetGalleryPhotosUseCase,
    private val offlineMapRepository: OfflineMapRepository,
) {
    suspend operator fun invoke(): MapRecords {
        val failures = mutableListOf<MapRecordReadFailure>()
        fun <T> Result<List<T>>.orFailure(kind: MapRecordKind): List<T> =
            getOrElse { error ->
                failures += MapRecordReadFailure(kind, error)
                emptyList()
            }

        val findMarkers = getFinds().orFailure(MapRecordKind.FINDS).mapNotNull { entry ->
            entry.foundAt?.let { RecordPoint(entry.id, it) }
        }
        val photoMarkers = getPhotos().orFailure(MapRecordKind.PHOTOS).mapNotNull { gallery ->
            val lat = gallery.photo.latitude ?: return@mapNotNull null
            val lng = gallery.photo.longitude ?: return@mapNotNull null
            RecordPoint(gallery.photo.id, LatLng(lat, lng))
        }
        val trackPolylines = getTracks().orFailure(MapRecordKind.TRACKS).mapNotNull { track ->
            if (track.endedAtEpochMillis == null) return@mapNotNull null
            track.points.takeIf { it.isNotEmpty() }?.let { points -> RecordPolyline(track.id, points.map { LatLng(it.lat, it.lng) }) }
        }
        val offlineRegionCircles = offlineMapRepository.listRegions().orFailure(MapRecordKind.OFFLINE_REGIONS).map {
            RecordRegion(it.id.toString(), it.region)
        }
        return MapRecords(findMarkers, photoMarkers, trackPolylines, offlineRegionCircles, failures)
    }
}
