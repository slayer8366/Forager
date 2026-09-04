package com.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * right now* — even though how each is chosen has since diverged: [basemap] is still the icon
 * bar's own quick-fire control, [night] is now Settings' persistent "Night Maps" checkbox rather
 * than a control on the map itself — see [night]'s own doc comment.
 */
data class MapRenderMode(
    val basemap: Basemap,
    /**
     * Night mode: a slightly desaturated, higher-contrast basemap (`BasemapStyles.kt`'s
     * `NIGHT_RASTER_PAINT`). Overlay markers (sightings, area markers, planned trips, waypoints)
     * render identically regardless of this flag — see `MapPalette`'s own doc comment, "Markers
     * stay day-only, always." Not the device's dark theme, and deliberately not derived from it —
     * see `MapPalette` for why that was tried, measured and abandoned.
     */
    val night: Boolean = false,
    /**
     * Whether this map instance may seize the camera for live GPS tracking — Journal Stage 2d.
     * `true` (every existing caller's unchanged behavior) lets [SightingsMap] activate MapLibre's
     * own "blue dot" location puck and follow it the moment location permission is granted, exactly
     * as it always has. `false` is for a map instance about a **historical** place — Stage 2d's
     * Cartography entry map — where [region]/[com.forager.app.ui.map.MapSlot]'s own `focusOverride`
     * framing an entry's kept data must not be immediately overridden by a jump to the device's
     * *current* location, which [SightingsMap]'s [org.maplibre.android.location.modes.CameraMode.TRACKING]
     * would otherwise force on first activation (a real behavior confirmed by reading
     * `activateLiveLocationIfPermitted`, not assumed). Bundled here, alongside [basemap]/[night],
     * rather than added as a new top-level [com.forager.app.ui.map.MapSlot] parameter: that
     * function-type typealias is one parameter short of a real, previously-hit Compose compiler
     * crash at 10 declared parameters (see [MapOverlayContent]'s own doc comment) — [MapRenderMode]
     * exists exactly to absorb an addition like this one without touching that count.
     */
    val trackLiveLocation: Boolean = true,
    /**
     * Whether the user has asked this map instance to serve tiles from a downloaded offline region
     * instead of the online basemap — Journal Stage 2e-i, the manual toggle on
     * [com.forager.app.ui.log.CartographyEntryReportScreen]'s own map. **Deliberately inert as of
     * Stage 2e-i: nothing reads this field yet.** [SightingsMapSlot]/[SightingsMap] still always
     * request tiles from [basemap] regardless of this value — see Stage 2e-i's own dispatch for why
     * the toggle exists (surface the user's choice, let them see and set it) without yet acting on
     * it (actually swapping to the offline vector style, `OFFLINE_STYLE_URL`, is Stage 2e-ii, and
     * carries a risk — whether a live style load is actually served from the on-device offline
     * cache rather than attempting a network fetch — that cannot be settled without a device).
     *
     * **Do not read this as dead code or dormant scaffolding to prune.** This project has already
     * had an unread field mislead a planner into treating live scaffolding as abandoned once; this
     * one is a deliberate seam, awaiting Stage 2e-ii's own consumer. Bundled here for the same
     * reason [trackLiveLocation] is — [com.forager.app.ui.map.MapSlot]'s own function-type
     * typealias is one parameter short of a real Compose compiler crash at 10 declared parameters
     * (see [MapOverlayContent]'s own doc comment), so a new capability like this one goes on
     * [MapRenderMode], never on [MapSlot] itself.
     */
    val useOfflineTiles: Boolean = false,
    /**
     * Extra clearance [SightingsMap]'s always-visible attribution line keeps above this map's own
     * bottom edge — fullscreen-maps dispatch, Part 2b. Zero for every existing caller: the
     * attribution sits flush above the map's bottom edge, as it always has, unless something floats
     * over that edge that attribution must not be covered by. `CompactMapTab`'s own fullscreen mode
     * is the one caller that sets this non-zero, once the app-wide bottom nav floats over the map
     * instead of resizing it (see that composable's own doc comment) — the nav's own measured
     * height, not a guessed constant, for the same "measure, don't hardcode" reasoning
     * `mapIconBarBottomPx` already established for a different floating-chrome measurement in this
     * codebase.
     *
     * Bundled here, alongside [basemap]/[night]/[trackLiveLocation]/[useOfflineTiles], rather than
     * added as a new top-level [com.forager.app.ui.map.MapSlot] parameter: that function-type
     * typealias is one parameter short of a real, previously-hit Compose compiler crash at 10
     * declared parameters (see [MapOverlayContent]'s own doc comment) — [MapRenderMode] exists
     * exactly to absorb an addition like this one without touching that count. [SightingsMap]'s own
     * `bottomInset` parameter is what this actually drives; [SightingsMapSlot] forwards it through
     * unchanged.
     */
    val bottomInset: Dp = 0.dp,
)

data class MapOverlayContent(
    val sightings: List<Sighting> = emptyList(),
    val plannedTrips: List<PlannedTrip> = emptyList(),
    /**
     * The active track's recorded points, oldest first, drawn as a growing trail — empty whenever
     * nothing is being recorded: a breadcrumb trail is where the device actually walked.
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
    /**
     * Which [Sighting] (by [Sighting.observationId]), if any, the caller is currently showing an
     * observation bubble for — null once the caller has dismissed it, whether by its own close icon
     * or by tapping elsewhere on the map. [SightingsMap] re-derives its own "which sighting to keep
     * re-projecting on camera idle" state from this on every recomposition rather than latching it
     * internally at tap time and never clearing it: a real hardware report found the bubble
     * reappearing after a dismiss, on the very next pan, with no sighting tapped — the previous
     * internal `focusedSighting` var was set once when a dot was tapped and never told about a
     * later dismissal (the caller's own close/tap-elsewhere handling is pure Compose state on the
     * [AvailabilityScreen] side, and never reaches this far down), so every subsequent camera-idle
     * event kept re-firing `onSightingTap` for the same sighting and silently undid the dismissal.
     * Threading the caller's own dismissed-or-not state back in here as plain data closes that gap:
     * once the caller's tracked sighting goes null, this goes null too on the next recomposition,
     * and the camera-idle listener has nothing left to re-fire for.
     */
    val focusedObservationId: Long? = null,
    /**
     * Journal Stage 2d: a Cartography entry's kept tracks, one inner list per track, each oldest
     * point first — a genuine `MultiLineString`, not [breadcrumbPoints] concatenated. A single
     * `List<LatLng>` (what [breadcrumbPoints] already is) can only ever draw as one connected
     * `LineString` (see [breadcrumbFeatureCollection]); two kept tracks drawn that way would show a
     * spurious straight-line jump between the end of one and the start of the other. This is a
     * genuinely separate field, not a reshaping of [breadcrumbPoints], so every existing caller
     * (both `AvailabilityScreen.kt` call sites) is unaffected by construction — neither sets it, and
     * its default is empty. A kept track that no longer resolves (deleted from Records) is simply
     * absent from this list by the time it reaches here — see [com.forager.app.domain.GetCartographyEntryMapDataUseCase]'s
     * own doc comment for where that resolution happens; this composable never knows a track was
     * ever kept, only what actually resolved.
     */
    val keptTrackPolylines: List<List<LatLng>> = emptyList(),
    /**
     * Journal Stage 2d: a Cartography entry's kept finds with a resolved coordinate — drawn as
     * discrete pins, the same [SymbolLayer][org.maplibre.android.style.layers.SymbolLayer] template
     * [Waypoint] markers already use. A find with no coordinate (the ordinary case — see
     * [com.forager.app.domain.model.MushroomLogEntry.foundAt]'s own doc comment) is simply absent
     * from this list, never a placeholder point.
     */
    val findMarkers: List<LatLng> = emptyList(),
    /**
     * Journal Stage 2d: a Cartography entry's kept photos with a resolved coordinate (both
     * [com.forager.app.domain.model.LogPhoto.latitude]/`.longitude` non-null, and the gallery row
     * still present) — most existing photos will have neither, which is normal, not an error; see
     * [com.forager.app.domain.model.LogPhoto]'s own doc comment on the two ways a photo gains a
     * coordinate. Also a discrete-pin [SymbolLayer][org.maplibre.android.style.layers.SymbolLayer].
     */
    val photoMarkers: List<LatLng> = emptyList(),
    /**
     * Journal Stage 2d: a Cartography entry's kept offline regions, drawn as a translucent coverage
     * circle each — their snapshot already carries lat/lng/radius (see [Region]'s own shape), so
     * unlike tracks/finds/photos this needs no live fetch to resolve at all. Whether this is more
     * useful than cluttered is an open visual question the dispatch that added this explicitly left
     * to be reported on after building it, not decided in advance.
     */
    val offlineRegionCircles: List<Region> = emptyList(),
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
 * rather than one parameter apiece. [onLongPress] is how the map *would* report a trip-planning
 * gesture back up, when wired — the caller turns the reported point into a plan/log action without
 * this composable knowing anything about dates or persistence. **No production call site wires it
 * as of 2026-08-28** — both `AvailabilityScreen.kt` call sites (`MapTab`, `CompactMapTab`) pass
 * `{}`. Kept deliberately, not left by accident (see [SightingsMap]'s own doc comment on the same
 * parameter for the fuller reasoning); the interaction it used to drive now goes through panning
 * the camera, tapping add (+), and confirming via `CentrePinLocationPickerOverlay` instead.
 * [Basemap] crosses this seam as this
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
     * Fires instead of [onTap] when the tap actually lands on a real observation dot — see
     * [SightingsMap]'s own doc comment ("Partially rebuilt") for how that's resolved
     * (`queryRenderedFeatures` against the sighting layer, matched back to the tapped [Sighting] by
     * `observationId`). Every production call site wires this to show a species-name/date detail
     * with a "View on iNaturalist" action; `{}` is still the right default for anything that has no
     * such detail view (tests, previews).
     *
     * The [Offset] is that [Sighting]'s own current on-screen position (px, in this slot's own
     * coordinate space); the [Float] is the camera's current bearing (degrees clockwise from
     * north). Both re-fire on every camera move for as long as the same sighting stays tapped, not
     * just once at tap time, so a caller's detail bubble can stay glued to its marker — in both
     * position and orientation — across a pan/zoom/rotate instead of reading as detached from
     * whichever dot it was originally about, or pointing the wrong way once the map has turned.
     */
    onSightingTap: (Sighting, Offset, Float) -> Unit,
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
 *
 * [onSightingTap] brought this typealias's declared parameter count to 9 — still short of the 10
 * that previously crashed the Compose compiler lowering this exact lambda (see
 * [MapOverlayContent]'s own doc comment for that `ComposableFunctionBodyTransformer` failure and
 * why [MapOverlayContent] exists at all); confirmed by this file's own
 * `./gradlew :app:compileDebugKotlin` passing with this parameter added, not assumed safe from the
 * count alone.
 */
val SightingsMapSlot: MapSlot = { region, content, renderMode, focusOverride, onLongPress, onTap, onSightingTap, onCameraIdle, modifier ->
    SightingsMap(
        region = region,
        sightings = content.sightings,
        plannedTrips = content.plannedTrips,
        basemap = renderMode.basemap,
        nightMode = renderMode.night,
        focusOverride = focusOverride,
        onLongPress = onLongPress,
        onTap = onTap,
        onSightingTap = onSightingTap,
        onCameraIdle = onCameraIdle,
        breadcrumbPoints = content.breadcrumbPoints,
        waypoints = content.waypoints,
        resumeTrackingRequestId = content.resumeTrackingRequestId,
        resetOrientationRequestId = content.resetOrientationRequestId,
        focusedObservationId = content.focusedObservationId,
        trackLiveLocation = renderMode.trackLiveLocation,
        keptTrackPolylines = content.keptTrackPolylines,
        findMarkers = content.findMarkers,
        photoMarkers = content.photoMarkers,
        offlineRegionCircles = content.offlineRegionCircles,
        bottomInset = renderMode.bottomInset,
        modifier = modifier,
    )
}
