package com.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.forager.app.domain.model.ForagingArea
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.Waypoint

/**
 * Everything [MapSlot] draws on top of the basemap, bundled into one value rather than one
 * [MapSlot] parameter per list.
 *
 * This exists for a real compiler limit, not just tidiness: a `@Composable` *function type*
 * (unlike a directly-declared `@Composable fun`) hits an internal Compose compiler crash —
 * `IllegalArgumentException: Function with 11 params had 1 changed params but expected 20`,
 * thrown from `ComposableFunctionBodyTransformer` while lowering [SightingsMapSlot]'s lambda —
 * once the type's declared parameter count reaches 10; [MapSlot] was at 9 before
 * [breadcrumbPoints] below needed to become its 10th, which is what surfaced this. Bundling the
 * content lists here instead keeps [MapSlot] itself at 7 declared parameters with room for
 * whatever Phase 1c's waypoint markers add next, without hitting the same wall again.
 */
/**
 * How the map renders, as one value: which basemap, and whether night mode is on.
 *
 * Bundled for the same compiler reason [MapOverlayContent] is — see its doc comment for the
 * `ComposableFunctionBodyTransformer` crash a `@Composable` function type hits at ten declared
 * parameters. [MapSlot] was at eight; night mode would have been the ninth, leaving one slot of
 * headroom before the same wall. Folding it in beside [basemap] keeps the count at eight.
 *
 * The grouping is not only expedient. Both fields answer one question — *how should this map draw
 * right now* — and they are chosen at the same moment by the same control: the icon stack's third
 * slot toggles [basemap] on tap and [night] on long-press. A type that holds exactly what one
 * control governs is easier to reason about than two parameters that happen to travel together.
 */
data class MapRenderMode(
    val basemap: Basemap,
    /**
     * Night mode: a dimmed basemap and the light-on-dark overlay palette. Not the device's dark
     * theme, and deliberately not derived from it — see `MapPalette` for why that was tried,
     * measured and abandoned.
     */
    val night: Boolean = false,
)

data class MapOverlayContent(
    val sightings: List<Sighting> = emptyList(),
    val areas: List<ForagingArea> = emptyList(),
    val plannedTrips: List<PlannedTrip> = emptyList(),
    /**
     * The active track's recorded points, oldest first, drawn as a growing trail — empty whenever
     * nothing is being recorded. See [SightingsMap]'s own doc comment for how this differs from
     * the dashed visiting-order connector: a breadcrumb trail is where the device actually walked,
     * not a suggested order between areas.
     */
    val breadcrumbPoints: List<LatLng> = emptyList(),
    /** Saved waypoints, drawn as markers — independent of any track, per [Waypoint]'s own doc comment. */
    val waypoints: List<Waypoint> = emptyList(),
    /**
     * A changing token, not a boolean flag — incrementing it (regardless of the new value) tells
     * the map to (re-)engage GPS camera tracking, the same "GPS icon re-centers and resumes
     * following" behavior the project owner asked for. A `Boolean` can't carry a repeat action:
     * tapping locate-me a second time while already tracking (e.g. after wandering off tracking via
     * a pan) needs to fire again even though the *value* "tracking wanted" never changed from
     * `true`; a changing `Int` always represents a fresh request, a toggled `Boolean` would only
     * fire on every other tap. See [com.forager.app.ui.map.SightingsMap]'s own
     * `activateLiveLocationIfPermitted` for what actually engaging tracking means, and why this is
     * a request rather than a live camera-mode readout (this app's map layer doesn't expose the
     * live/lost-tracking state back up to `AvailabilityScreen` — the compact map icon stack's
     * locate-me icon looks the same whether or not GPS tracking is currently engaged).
     */
    val resumeTrackingRequestId: Int = 0,
    /**
     * Same changing-token shape as [resumeTrackingRequestId], for the same reason — a `Boolean`
     * can't represent "do it again" when the value wouldn't otherwise change. This one resets the
     * camera's bearing to north, the map redesign's own custom replacement for MapLibre's native
     * compass view (disabled in [SightingsMap] — see that composable's own doc comment on why:
     * the map icon bar's orientation-reset control needed a real callback to trigger, which the
     * SDK's own compass widget doesn't expose one for).
     */
    val resetOrientationRequestId: Int = 0,
)

/**
 * The map, as a slot the screen fills rather than a call the screen makes.
 *
 * This is the same seam [com.forager.app.domain.MushroomRepository] puts in front of iNaturalist,
 * applied to the UI layer: an external integration — a `View` that starts render threads, writes a
 * filesystem cache under `cacheDir` and fetches tiles over the network the moment it is composed —
 * sits behind an interface this project owns, and the caller depends on the interface.
 * [com.forager.app.ui.availability.AvailabilityScreen] previously named [SightingsMap] directly,
 * so there was no way to compose the screen without also standing up the whole tile stack.
 *
 * The vendor behind this seam changed once already, from osmdroid to MapLibre Native
 * (`docs/plans/maplibre-migration.md`), and neither [AvailabilityScreen] nor this typealias's own
 * shape had to change for it — that is the seam doing its job, not a coincidence. [SightingsMap] is
 * the only implementation either renderer's types ever touched.
 *
 * The parameters are exactly what the screen knows and the map needs. [content] is every list the
 * map draws over the basemap — see [MapOverlayContent]'s own doc comment for why those are bundled
 * rather than one parameter apiece. [onLongPress] is how the map reports a trip-planning gesture
 * back up without knowing anything about dates or persistence — the screen owns the date picker and
 * the save call, the map only reports where the finger was. [Basemap] crosses this seam as this
 * project's own type, not a vendor tile-source type, for the same reason the rest of the seam
 * exists: the screen names the basemap it wants and stays ignorant of which vendor supplies the
 * tiles. [modifier] is last because it is the slot's *size contract* — the screen decides how much
 * room the map gets, which is the one thing about this arrangement the screen is actually
 * responsible for.
 */
typealias MapSlot = @Composable (
    region: Region,
    content: MapOverlayContent,
    renderMode: MapRenderMode,
    /**
     * When non-null, pans the camera here instead of [region]'s own centre — the map redesign's
     * GPS/locate-me icon (distinct from [region], which stays the search centre; see that button's
     * call site in `AvailabilityScreen.kt`'s `CompactMapTab` for why this doesn't touch the search
     * itself).
     */
    focusOverride: LatLng?,
    onLongPress: (LatLng) -> Unit,
    /**
     * Fires on a plain tap anywhere on the map — the map redesign's "tap the map to restore
     * chrome" while fullscreen (see `CompactMapTab`'s call site). `{}` by every caller that has no
     * fullscreen chrome to restore, same default-ignoring shape as [onLongPress] gets from callers
     * with nothing to plan.
     */
    onTap: () -> Unit,
    /**
     * Fires with the geographic point under the screen's centre every time the camera finishes
     * moving (a pan, a fling settling, a programmatic jump) — the read side of [region]/
     * [focusOverride]'s write-only camera control, added for [CentrePinLocationPicker]: a picker
     * that keeps a marker fixed at screen centre while the map pans underneath it needs to know
     * *where* centre currently points to answer "what did the user pick," which nothing before
     * this parameter existed could report. `{}` by every caller that isn't tracking the camera —
     * the same default-ignoring shape [onTap] already established.
     */
    onCameraIdle: (LatLng) -> Unit,
    modifier: Modifier,
) -> Unit

/**
 * The real map. This is the default every production call path gets, so introducing the seam
 * changed no caller: `MainActivity` passes nothing new.
 */
val SightingsMapSlot: MapSlot = { region, content, renderMode, focusOverride, onLongPress, onTap, onCameraIdle, modifier ->
    SightingsMap(
        region = region,
        sightings = content.sightings,
        areas = content.areas,
        plannedTrips = content.plannedTrips,
        basemap = renderMode.basemap,
        nightMode = renderMode.night,
        focusOverride = focusOverride,
        onLongPress = onLongPress,
        onTap = onTap,
        onCameraIdle = onCameraIdle,
        breadcrumbPoints = content.breadcrumbPoints,
        waypoints = content.waypoints,
        resumeTrackingRequestId = content.resumeTrackingRequestId,
        resetOrientationRequestId = content.resetOrientationRequestId,
        modifier = modifier,
    )
}
