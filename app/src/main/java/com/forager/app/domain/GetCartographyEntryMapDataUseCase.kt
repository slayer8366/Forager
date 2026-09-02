package com.forager.app.domain

import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region

/**
 * Resolves a [CartographyEntry]'s kept references into what [com.forager.app.ui.map.MapOverlayContent]'s
 * own Stage 2d fields need to draw them — the live-fetch half [CartographyEntryReportScreen]'s
 * Stage 2c doc comment explicitly deferred ("no repository lookups, no live fetches" applied to
 * that screen's *own* render; this is the ViewModel-triggered side effect that resolves the data it
 * renders once resolved, per that screen's Stage 2d doc comment).
 *
 * Per kept-reference type — see the Stage 2d dispatch's own table for why these differ:
 * - **Waypoints** / **offline regions**: already carry lat/lng (and, for a region, radius) in their
 *   own snapshot, no fetch needed — mapped directly.
 * - **Tracks**: [TrackRepository.getById] per kept [com.forager.app.domain.model.TrackDecision.trackId].
 * - **Finds**: [MushroomLogRepository.getForDay] per distinct kept
 *   [com.forager.app.domain.model.FindDecision.foundOn] (there is no `getById`), then filtered by
 *   id — one call per distinct day among the kept finds, not one per find.
 * - **Photos**: resolved against [galleryPhotos], already loaded by the caller (the same list
 *   [CartographyEntryReportScreen] already threads through for its own photo grid) — genuinely no
 *   fetch of any kind for this one, unlike the other three.
 *
 * **A dangling or unresolvable reference contributes nothing, never a failure** — a kept track
 * deleted from Records ([TrackRepository.getById] returning `null`), a kept find likewise gone, or
 * a photo with a null coordinate (the ordinary case — see [com.forager.app.domain.model.LogPhoto]'s
 * own doc comment) or a missing gallery row, all fall out of their respective `mapNotNull` silently.
 * This deliberately does **not** short-circuit the whole result on one repository failure the way
 * [GetDerivedTripUseCase] does for its own, different reason (a live day's report misrepresenting
 * "what happened" if one source failed) — here, a single missing/errored item not resolving is the
 * expected steady state this whole method exists to tolerate, not an exceptional one; the entry's
 * snapshotted text already reads correctly regardless (see [CartographyEntry]'s own doc comment),
 * and the map must be structurally unable to break because one reference is gone.
 */
class GetCartographyEntryMapDataUseCase(
    private val trackRepository: TrackRepository,
    private val mushroomLogRepository: MushroomLogRepository,
) {
    suspend operator fun invoke(entry: CartographyEntry, galleryPhotos: List<GalleryPhoto>): CartographyEntryMapData {
        val trackPolylines = entry.trackDecisions.filter { it.kept }.mapNotNull { decision ->
            trackRepository.getById(decision.trackId).getOrNull()?.points?.map { LatLng(it.lat, it.lng) }
        }

        val keptFinds = entry.findDecisions.filter { it.kept }
        val findsByDayKey = keptFinds.map { it.foundOn.toString() }.distinct().associateWith { dayKey ->
            mushroomLogRepository.getForDay(dayKey).getOrNull().orEmpty()
        }
        val findMarkers = keptFinds.mapNotNull { decision ->
            findsByDayKey[decision.foundOn.toString()]
                ?.firstOrNull { it.id == decision.findId }
                ?.foundAt
                ?.let { LatLng(it.lat, it.lng) }
        }

        val waypointMarkers = entry.waypointDecisions.filter { it.kept }.map { LatLng(it.lat, it.lng) }

        val photosById = galleryPhotos.associateBy { it.photo.id }
        val photoMarkers = entry.photos.mapNotNull { attachment ->
            val photo = photosById[attachment.photoId]?.photo ?: return@mapNotNull null
            val lat = photo.latitude ?: return@mapNotNull null
            val lng = photo.longitude ?: return@mapNotNull null
            LatLng(lat, lng)
        }

        val offlineRegionCircles = entry.offlineRegionDecisions.filter { it.kept }.map {
            Region(lat = it.lat, lng = it.lng, radiusKm = it.radiusKm)
        }

        return CartographyEntryMapData(
            trackPolylines = trackPolylines,
            findMarkers = findMarkers,
            waypointMarkers = waypointMarkers,
            photoMarkers = photoMarkers,
            offlineRegionCircles = offlineRegionCircles,
        )
    }
}

/** Everything [CartographyEntryReportScreen]'s map needs, already resolved — see [GetCartographyEntryMapDataUseCase]'s own doc comment. */
data class CartographyEntryMapData(
    val trackPolylines: List<List<LatLng>>,
    val findMarkers: List<LatLng>,
    val waypointMarkers: List<LatLng>,
    val photoMarkers: List<LatLng>,
    val offlineRegionCircles: List<Region>,
) {
    /** `true` when nothing here resolved to a single drawable point or line — a real, reachable state (an entry made entirely of photos with null coordinates), not an error. */
    val isEmpty: Boolean
        get() = trackPolylines.isEmpty() && findMarkers.isEmpty() && waypointMarkers.isEmpty() && photoMarkers.isEmpty() && offlineRegionCircles.isEmpty()

    /**
     * Every resolved point, flattened — what [GeoDistance.boundingRegion] fits the camera to. An
     * offline region contributes only its centre here, not its full circle extent — a
     * simplification: a large kept region could in principle extend past this frame's own edge.
     * Accepted for now since the region-circle overlay itself is an open visual question the
     * dispatch that added it explicitly deferred to the owner's own judgement after seeing it.
     */
    val allPoints: List<LatLng>
        get() = trackPolylines.flatten() + findMarkers + waypointMarkers + photoMarkers + offlineRegionCircles.map { LatLng(it.lat, it.lng) }
}
