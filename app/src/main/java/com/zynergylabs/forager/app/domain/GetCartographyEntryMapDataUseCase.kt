package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region

/**
 * Resolves a [CartographyEntry]'s kept references into what [com.zynergylabs.forager.app.ui.map.MapOverlayContent]'s
 * own Stage 2d fields need to draw them — the live-fetch half [CartographyEntryReportScreen]'s
 * Stage 2c doc comment explicitly deferred ("no repository lookups, no live fetches" applied to
 * that screen's *own* render; this is the ViewModel-triggered side effect that resolves the data it
 * renders once resolved, per that screen's Stage 2d doc comment).
 *
 * Per kept-reference type — see the Stage 2d dispatch's own table for why these differ:
 * - **Waypoints** / **offline regions**: already carry lat/lng (and, for a region, radius) in their
 *   own snapshot, no fetch needed — mapped directly.
 * - **Tracks**: [TrackRepository.getById] per kept [com.zynergylabs.forager.app.domain.model.TrackDecision.trackId].
 * - **Finds**: [MushroomLogRepository.getForDay] per distinct kept
 *   [com.zynergylabs.forager.app.domain.model.FindDecision.foundOn] (there is no `getById`), then filtered by
 *   id — one call per distinct day among the kept finds, not one per find.
 * - **Photos**: resolved against [galleryPhotos], already loaded by the caller (the same list
 *   [CartographyEntryReportScreen] already threads through for its own photo grid) — genuinely no
 *   fetch of any kind for this one, unlike the other three.
 *
 * **A dangling or unresolvable reference contributes nothing, never a failure** — a kept track
 * deleted from Records ([TrackRepository.getById] returning `null`), a kept find likewise gone, or
 * a photo with a null coordinate (the ordinary case — see [com.zynergylabs.forager.app.domain.model.LogPhoto]'s
 * own doc comment) or a missing gallery row, all fall out of their respective `mapNotNull` silently.
 * This deliberately does **not** short-circuit the whole result on one repository failure the way
 * [GetDerivedTripUseCase] does for its own, different reason (a live day's report misrepresenting
 * "what happened" if one source failed) — here, a single missing/errored item not resolving is the
 * expected steady state this whole method exists to tolerate, not an exceptional one; the entry's
 * snapshotted text already reads correctly regardless (see [CartographyEntry]'s own doc comment),
 * and the map must be structurally unable to break because one reference is gone.
 *
 * **A kept track that resolves to zero points contributes no polyline, not an empty one** (plate
 * pulse, owner ruling on item 6). Since the timestamp-filter dispatch, [TrackRepository.getById]
 * can return a track whose every stored point was excluded at the read seam
 * ([com.zynergylabs.forager.app.domain.isNetworkProviderFix]) — `points = []`, `excludedPointCount > 0`. An
 * empty inner list is not something to draw, and emitting it made [CartographyEntryMapData.isEmpty]
 * report content for an entry with nothing drawable; `docs/audits/2026-09-07-cartography-plate-renderer-pulse.md`
 * §7 records the report screen surviving that only through its second guard. The `takeIf` below
 * and [CartographyEntryMapData.isEmpty]'s own points-based definition are deliberately *both*
 * fixed, not either: the property must be right on its own, and this use case must not emit the
 * shape that made it wrong.
 *
 * **Rule: an entry's map geometry comes from this use case and nowhere else** (plate pulse, owner
 * ruling on item 7). [CartographyEntry.waypointDecisions] and
 * [CartographyEntry.offlineRegionDecisions] carry coordinates in their snapshots, so a renderer
 * *could* read them straight off the entry with no fetch — and would then have to repeat the
 * `kept` filter this method applies, which is a bug waiting to happen the first time it is
 * forgotten. Withheld items must be unreachable by construction, not by convention: anything that
 * draws an entry on a map takes a [CartographyEntryMapData], and only this method builds one.
 */
class GetCartographyEntryMapDataUseCase(
    private val trackRepository: TrackRepository,
    private val mushroomLogRepository: MushroomLogRepository,
) {
    suspend operator fun invoke(entry: CartographyEntry, galleryPhotos: List<GalleryPhoto>): CartographyEntryMapData {
        val trackPolylines = entry.trackDecisions.filter { it.kept }.mapNotNull { decision ->
            trackRepository.getById(decision.trackId).getOrNull()?.points?.takeIf { it.isNotEmpty() }?.map { LatLng(it.lat, it.lng) }
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

/**
 * Everything [CartographyEntryReportScreen]'s map needs, already resolved — see
 * [GetCartographyEntryMapDataUseCase]'s own doc comment, including the rule that this type is the
 * only way an entry's geometry reaches a map.
 */
data class CartographyEntryMapData(
    val trackPolylines: List<List<LatLng>>,
    val findMarkers: List<LatLng>,
    val waypointMarkers: List<LatLng>,
    val photoMarkers: List<LatLng>,
    val offlineRegionCircles: List<Region>,
) {
    /**
     * Every point that is actually *drawn as a datum of the day* — track points, finds, waypoints,
     * photos. **Not** offline-region centres: a region is where tiles were downloaded, not where
     * anything happened, so it is neither content (see [isEmpty]) nor a point the offline-coverage
     * check may be asked about (a region always contains its own centre, which made every kept
     * region trivially "cover" its entry before this existed). Defined over points, not over the
     * outer lists, so a polyline with nothing in it counts for nothing.
     */
    val drawablePoints: List<LatLng>
        get() = trackPolylines.flatten() + findMarkers + waypointMarkers + photoMarkers

    /**
     * `true` when nothing here resolved to a single drawable point — a real, reachable state (an
     * entry made entirely of photos with null coordinates, or of tracks whose every point the read
     * seam excluded), not an error. Plate pulse, owner rulings on items 4 and 6: a kept offline
     * region alone is **not** content — "a green circle with nothing in it is not a day" — and an
     * empty polyline is not content either. This is [drawablePoints] being empty, and nothing else;
     * the previous list-based definition (`trackPolylines.isEmpty() && … && offlineRegionCircles.isEmpty()`)
     * was wrong on both counts.
     */
    val isEmpty: Boolean
        get() = drawablePoints.isEmpty()

    /**
     * What [GeoDistance.boundingRegion] fits the camera to: [drawablePoints] plus each kept offline
     * region's centre, so a region's circle is at least partly in frame whenever it is drawn — and
     * it is drawn only when something else resolved, since [isEmpty] gates the map. An offline
     * region contributes only its centre here, not its full circle extent — a simplification: a
     * large kept region could in principle extend past this frame's own edge. Accepted for now
     * since the region-circle overlay itself is an open visual question the dispatch that added it
     * explicitly deferred to the owner's own judgement after seeing it.
     */
    val allPoints: List<LatLng>
        get() = drawablePoints + offlineRegionCircles.map { LatLng(it.lat, it.lng) }
}
