package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.EntryMapFrame
import com.zynergylabs.forager.app.domain.ForecastCellStore
import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.RecordPolyline
import com.zynergylabs.forager.app.domain.model.RecordRegion
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.map.layers.ForecastCellsShown
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import java.time.LocalDate

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
     * Night mode: the basemap's colours inverted with hue kept (the V1 transform, `BasemapStyles.kt`'s
     * `NIGHT_RASTER_PAINT`), except Satellite, which stays day; the offline style is recoloured with
     * the same transform after it loads. Every overlay marker follows it too, on every basemap,
     * Satellite included: markers draw from `MapPalette.forMode` of this flag (colour build C2), so
     * over Satellite only the markers switch. Not the device's dark theme, and deliberately not derived from it —
     * see `MapPalette` for why that was tried, measured and abandoned.
     */
    val night: Boolean = false,
    /**
     * Whether [night] is the stored preference yet — colour build C1's cold-launch gate.
     * [SightingsMap] loads no style while this is `false`, so a night user's first style is the
     * night one rather than a day style the asynchronous preference read might never correct.
     *
     * `true` by default, and only the main map passes the real flag (`AvailabilityScreen`'s
     * `mapRenderMode`, from `AvailabilityUiState.nightModeMapsLoaded`), by the planner's ruling. The
     * centre-pin pickers and the Cartography entry map keep the default: each sits behind user
     * navigation, so the preference is assumed to have loaded by the time one is reached. That is an
     * inference, not an observation; it is a device item in
     * `docs/audits/2026-09-27-night-mode-c1-completion-report.md`.
     */
    val nightModeLoaded: Boolean = true,
    /**
     * Whether this map instance may seize the camera for live GPS tracking — Journal Stage 2d.
     * `true` (every existing caller's unchanged behavior) lets [SightingsMap] activate MapLibre's
     * own "blue dot" location puck and follow it the moment location permission is granted, exactly
     * as it always has. `false` is for a map instance about a **historical** place — Stage 2d's
     * Cartography entry map — where [region]/[com.zynergylabs.forager.app.ui.map.MapSlot]'s own `focusOverride`
     * framing an entry's kept data must not be immediately overridden by a jump to the device's
     * *current* location, which [SightingsMap]'s [org.maplibre.android.location.modes.CameraMode.TRACKING]
     * would otherwise force on first activation (a real behavior confirmed by reading
     * `activateLiveLocationIfPermitted`, not assumed). Bundled here, alongside [basemap]/[night],
     * rather than added as a new top-level [com.zynergylabs.forager.app.ui.map.MapSlot] parameter: that
     * function-type typealias is one parameter short of a real, previously-hit Compose compiler
     * crash at 10 declared parameters (see [MapOverlayContent]'s own doc comment) — [MapRenderMode]
     * exists exactly to absorb an addition like this one without touching that count.
     */
    val trackLiveLocation: Boolean = true,
    /**
     * Whether this map instance loads the downloaded regions' own style instead of [basemap] —
     * the manual toggle on [com.zynergylabs.forager.app.ui.log.CartographyEntryReportScreen]'s own map (Stage
     * 2e-i surfaced the choice; **Stage 2e-ii acts on it**). Read by [SightingsMap], which loads
     * `OFFLINE_STYLE_URL` by URI when this is `true` — the exact string every region was downloaded
     * against, so MapLibre's offline database can serve it — and the basemap's raster style
     * otherwise; see `mapStyleSourceFor`'s own doc comment for why by URI, and for how night mode
     * reaches the offline style (a post-load recolour, colour build C1). The attribution caption follows it (`mapAttributionFor`).
     * Manual only, by owner ruling: an automatic swap on losing connectivity would need
     * connectivity code this app does not have, and would reload the style mid-pan.
     *
     * Whether a live style load is actually served from the on-device store, offline, is the
     * question 2e-ii exists to answer and can only be answered on a device — see
     * `docs/audits/2026-09-07-offline-style-swap-prebuild-report.md` §5.2 for the pass that
     * settles it by structure (raster ambient tiles cannot serve a vector style). Bundled here for
     * the same reason [trackLiveLocation] is — [com.zynergylabs.forager.app.ui.map.MapSlot]'s own function-type
     * typealias is one parameter short of a real Compose compiler crash at 10 declared parameters
     * (see [MapOverlayContent]'s own doc comment), so a new capability like this one goes on
     * [MapRenderMode], never on [MapSlot] itself.
     */
    val useOfflineTiles: Boolean = false,
    /**
     * Whether this map draws the search-centre marker — the `searchCentre`-coloured reticle (a dot
     * before colour build C2) at
     * [com.zynergylabs.forager.app.ui.map.MapSlot]'s `region` centre that the live Maps tab uses to show where
     * the current search is anchored. `true` for every existing caller, unchanged. `false` for a
     * map about a **historical** place ([com.zynergylabs.forager.app.ui.log.CartographyEntryReportScreen]):
     * there `region` is only [com.zynergylabs.forager.app.domain.GeoDistance.boundingRegion]'s box midpoint, a
     * point where nothing happened, and a marker there is exactly the class of thing this project
     * has been removing (plate pulse, owner ruling on item 5). An explicit flag, deliberately not
     * inferred from [trackLiveLocation] `== false`: that field answers a different question, and
     * keying one behaviour on an unrelated property is the coupling that breaks silently when a
     * third caller arrives. Bundled here for the same reason [trackLiveLocation]/[useOfflineTiles]
     * are — [com.zynergylabs.forager.app.ui.map.MapSlot]'s own function-type typealias is one parameter short
     * of a real Compose compiler crash at 10 declared parameters (see [MapOverlayContent]'s own doc
     * comment), and [MapRenderMode] exists to absorb additions like this one.
     */
    val showSearchCentre: Boolean = true,
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
     * added as a new top-level [com.zynergylabs.forager.app.ui.map.MapSlot] parameter: that function-type
     * typealias is one parameter short of a real, previously-hit Compose compiler crash at 10
     * declared parameters (see [MapOverlayContent]'s own doc comment) — [MapRenderMode] exists
     * exactly to absorb an addition like this one without touching that count. [SightingsMap]'s own
     * `bottomInset` parameter is what this actually drives; [SightingsMapSlot] forwards it through
     * unchanged.
     */
    val bottomInset: Dp = 0.dp,
    /**
     * Fires when the user starts moving this map's camera by touch: a pan, pinch, rotate, tilt,
     * fling or double-tap zoom. Never for a move this app makes itself (a [MapSlot] `region`
     * change, `focusOverride`, the first activation's ease to zoom 16, live-location tracking,
     * the orientation reset). Picker-fixes dispatch, F1: [CentrePinLocationPicker]'s rule
     * "follow until you touch it" needs to tell a user's pan from a programmatic camera move, and
     * both end in the same [MapSlot] `onCameraIdle`, so the idle event alone cannot carry it.
     *
     * [SightingsMap] implements it with MapLibre's `addOnCameraMoveStartedListener`, keeping only
     * `REASON_API_GESTURE` (1). Checked with `javap -c` on the pinned 13.5.0 `classes.jar`: every
     * `onCameraMoveStarted(1)` dispatch is in a `MapGestureDetector` gesture listener (tap, move,
     * scale, rotate, shove, fling), while `Transform`, which carries every API camera move including
     * the location component's tracking, dispatches `REASON_API_ANIMATION` (3).
     *
     * A callback on [MapRenderMode], not an 11th [MapSlot] parameter, for the reason every field
     * above gives (the Compose compiler crash at 10 declared parameters; [MapSlot] is at 9), and
     * because a new top-level parameter or a wider `onCameraIdle` type would change every [MapSlot]
     * implementation, about 40 files, where a defaulted field here changes none. `{}` by default, a
     * non-capturing lambda, so every existing caller's [MapRenderMode] still compares equal.
     */
    val onUserCameraGesture: () -> Unit = {},
    /**
     * Every overlay layer's visibility and opacity, and the user's order for the reorderable group:
     * map layers L0a, A3. [SightingsMap] applies it to the native layers through their `visibility`
     * and opacity properties without reloading the style (`layerPaintFor` is the pure conversion).
     * [MapLayersState.DEFAULT] is every layer visible at today's opacities, so every existing caller
     * draws exactly as before. Nothing sets anything else yet: the Layers sheet and its DataStore
     * persistence are L0b.
     *
     * Here, not an 11th [MapSlot] parameter, for the reason every field above gives (the Compose
     * compiler crash at 10 declared parameters; [MapSlot] is at 9).
     */
    val layers: MapLayersState = MapLayersState.DEFAULT,
    /**
     * Fires when a tap's winning feature is on any layer but the sighting dots (map layers L0a, A4),
     * as one [MapFeatureTap]: the layer's id (`MapLayerIds`), the feature's own id (the record id
     * for a find, photo, kept track, waypoint, planned trip or offline region; a cell's centre for a
     * colour field), and where the tap was (M1, F1). A sighting still goes to [MapSlot]'s
     * `onSightingTap`, exactly as before. Which feature wins is `resolveTap`'s decision: markers,
     * then lines, then a colour-field cell only under the finger with nothing else near. The search
     * centre and the recording trail take no taps (M1, owner's ruling 4). It also fires on every
     * camera idle for [MapOverlayContent.focusedFeature], at that glyph's own position.
     *
     * **Since M1, [MapSlot]'s `onTap` no longer fires after this one** (owner's ruling 1, "Bubble
     * only"): a feature tap opens its bubble and nothing else, as a sighting tap always did, so a
     * feature tap no longer restores the fullscreen chrome. `{}` by default, a non-capturing lambda,
     * so every existing caller's [MapRenderMode] still compares equal. Here rather than on [MapSlot]
     * for the parameter-count reason [layers] gives.
     */
    val onFeatureTap: (MapFeatureTap) -> Unit = {},
    /**
     * Where this map's colour fields read their cells (map layers L0b, B5), or `null` for a map that
     * draws none: the Cartography entry map and the centre-pin pickers pass none (planner's ruling on
     * Q5: colour fields on the Maps tab only). Here rather than on [MapSlot] for the parameter-count
     * reason [layers] gives.
     */
    val forecast: MapForecastFeed? = null,
    /**
     * A one-shot camera frame (the entry map's opening frame, owner, 2026-09-28), or `null` for none.
     * [SightingsMap] applies a request once per [MapCameraRequest.id], after which the user's pan and
     * zoom stand: the same request arriving again on a later recomposition (returning from a find
     * overlay or a bubble target) does not move the camera. Here rather than on [MapSlot] for the
     * parameter-count reason [layers] gives.
     */
    val cameraRequest: MapCameraRequest? = null,
    /**
     * Where this map keeps the camera the user left, across the map leaving and re-entering
     * composition (Part 1 layout fixes, item 4, planner message `2026-09-28-98`), or `null` for a map
     * that keeps none. See [MapCameraMemory]. Here rather than on [MapSlot] for the parameter-count
     * reason [layers] gives.
     */
    val cameraMemory: MapCameraMemory? = null,
    /**
     * How far MapLibre's attribution button ("i") keeps from the map's end edge (Part 1 layout fixes,
     * item 5), beside [bottomInset] for the bottom. Zero for every caller but the compact Maps tab in a
     * short landscape window, whose overlaid rail sits on that edge at one rotation.
     */
    val attributionEndInset: Dp = 0.dp,
    /**
     * How far MapLibre's attribution button ("i") keeps from the map's bottom edge, when that differs from
     * [bottomInset], which the app's own always-visible caption follows. `null` (the default, every caller
     * but the compact Maps tab) means the button follows [bottomInset], as it always has. Part 2 follow-ups
     * F1 item 2 (Part 2 item 37): in portrait fullscreen [bottomInset] is 0 (the caption goes to the true
     * edge, the owner's ruling recorded in `AvailabilityCompactScaffold`), which put the "i" under the
     * system navigation band, where real taps opened nothing.
     */
    val attributionBottomInset: Dp? = null,
)

/**
 * A camera frame to apply once: [id] names the request, so a map that has applied it does not
 * apply it again, and [frame] is where the camera goes.
 */
data class MapCameraRequest(val id: String, val frame: EntryMapFrame)

/**
 * What a map needs to draw its colour fields from stored cells (map layers L0b, B5): the store, the
 * week to ask it for, which colour-field layers the store has data for (by layer id, with each one's
 * group), and a callback with the dates of the cells now drawn, which the host's legend shows.
 *
 * [SightingsMap] asks [store] for the blocks touching the visible area whenever the camera goes idle,
 * the same path a downloaded store will use, and again after every style load.
 */
data class MapForecastFeed(
    val store: ForecastCellStore,
    val week: LocalDate,
    val groupsByLayer: Map<String, String>,
    val onCellsShown: (Map<String, ForecastCellsShown>) -> Unit = {},
)

/**
 * One feature tap, as [MapRenderMode.onFeatureTap] reports it (M1, F1 accepted by the planner): the
 * layer and the feature's own id, as before, plus where the tap was, so a bubble can be anchored on
 * it and a colour field's cell looked up by block. [screenPoint] is in the map slot's own
 * coordinates, as `onSightingTap`'s is; [bearingDeg] is the camera's bearing; [at] is the tapped
 * map position. For a point feature re-anchored at a camera idle ([MapOverlayContent.focusedFeature]),
 * [screenPoint] and [at] are the glyph's own.
 */
data class MapFeatureTap(
    val layerId: String,
    val featureId: String,
    val screenPoint: Offset,
    val bearingDeg: Float,
    val at: LatLng,
)

/** A point feature a caller is showing a bubble for, by layer and feature id (M1, F2): see [MapOverlayContent.focusedFeature]. */
data class FocusedMapFeature(val layerId: String, val featureId: String)

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
     * fire on every other tap. See [com.zynergylabs.forager.app.ui.map.SightingsMap]'s own
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
     * M1 (F2): the point feature, if any, the caller is showing a bubble for, generalising
     * [focusedObservationId] to finds, photos, waypoints and planned trips. On every camera idle the
     * map re-projects that glyph's own position and reports it through [MapRenderMode.onFeatureTap],
     * so the bubble stays on its glyph across a pan, zoom or rotate. `null` once the caller has
     * dismissed the bubble, for the reason [focusedObservationId] gives: a dismissal must not be
     * undone by the next idle. Lines, regions and cells are never focused here; their bubbles keep
     * the tap point (planner's M1 ruling).
     */
    val focusedFeature: FocusedMapFeature? = null,
    /**
     * Journal Stage 2d: a Cartography entry's kept tracks, one [RecordPolyline] per track, each oldest
     * point first — a genuine `MultiLineString`, not [breadcrumbPoints] concatenated. A single
     * `List<LatLng>` (what [breadcrumbPoints] already is) can only ever draw as one connected
     * `LineString` (see [breadcrumbFeatureCollection]); two kept tracks drawn that way would show a
     * spurious straight-line jump between the end of one and the start of the other. This is a
     * genuinely separate field, not a reshaping of [breadcrumbPoints], so every existing caller
     * (both `AvailabilityScreen.kt` call sites) is unaffected by construction — neither sets it, and
     * its default is empty. A kept track that no longer resolves (deleted from Records) is simply
     * absent from this list by the time it reaches here — see [com.zynergylabs.forager.app.domain.GetCartographyEntryMapDataUseCase]'s
     * own doc comment for where that resolution happens; this composable never knows a track was
     * ever kept, only what actually resolved.
     *
     * Each item carries its record's id beside its geometry since map layers L0a ([RecordPoint],
     * [RecordPolyline], [RecordRegion]), written into its map feature so a tap can name the record.
     */
    val keptTrackPolylines: List<RecordPolyline> = emptyList(),
    /**
     * Journal Stage 2d: a Cartography entry's kept finds with a resolved coordinate — drawn as
     * discrete markers, each a [SymbolLayer][org.maplibre.android.style.layers.SymbolLayer] like the
     * [Waypoint] markers; since colour build C2 a find is a mushroom in its own colour, no longer the
     * waypoint's pin in the offline region's colour (`MarkerGlyphs.kt`). A find with no coordinate (the ordinary case — see
     * [com.zynergylabs.forager.app.domain.model.MushroomLogEntry.foundAt]'s own doc comment) is simply absent
     * from this list, never a placeholder point.
     *
     * Each item carries its record's id beside its geometry since map layers L0a ([RecordPoint],
     * [RecordPolyline], [RecordRegion]), written into its map feature so a tap can name the record.
     */
    val findMarkers: List<RecordPoint> = emptyList(),
    /**
     * Journal Stage 2d: a Cartography entry's kept photos with a resolved coordinate (both
     * [com.zynergylabs.forager.app.domain.model.LogPhoto.latitude]/`.longitude` non-null, and the gallery row
     * still present) — most existing photos will have neither, which is normal, not an error; see
     * [com.zynergylabs.forager.app.domain.model.LogPhoto]'s own doc comment on the two ways a photo gains a
     * coordinate. Also a discrete [SymbolLayer][org.maplibre.android.style.layers.SymbolLayer] marker: since
     * colour build C2 a rounded square with a camera, in its own colour, no longer the planned trip's
     * diamond.
     *
     * Each item carries its record's id beside its geometry since map layers L0a ([RecordPoint],
     * [RecordPolyline], [RecordRegion]), written into its map feature so a tap can name the record.
     */
    val photoMarkers: List<RecordPoint> = emptyList(),
    /**
     * Journal Stage 2d: a Cartography entry's kept offline regions, drawn as a translucent coverage
     * circle each — their snapshot already carries lat/lng/radius (see [Region]'s own shape), so
     * unlike tracks/finds/photos this needs no live fetch to resolve at all. Whether this is more
     * useful than cluttered is an open visual question the dispatch that added this explicitly left
     * to be reported on after building it, not decided in advance.
     *
     * Each item carries its record's id beside its geometry since map layers L0a ([RecordPoint],
     * [RecordPolyline], [RecordRegion]), written into its map feature so a tap can name the record.
     */
    val offlineRegionCircles: List<RecordRegion> = emptyList(),
    /**
     * J8: the records kept by the entries shown on the map, highlighted in place by the five
     * journal-entry halo layers under their own records (`GetJournalEntryHighlightsUseCase`, live
     * geometry only). Only the Maps tab sets it; every other map draws no highlight.
     */
    val journalHighlights: JournalEntryHighlights = JournalEntryHighlights.NONE,
)

/**
 * The map, as a slot the screen fills rather than a call the screen makes.
 *
 * This is the same seam [com.zynergylabs.forager.app.domain.MushroomRepository] puts in front of iNaturalist,
 * applied to the UI layer: an external integration — a `View` that starts render threads, writes a
 * filesystem cache under `cacheDir` and fetches tiles over the network the moment it is composed —
 * sits behind an interface this project owns, and the caller depends on the interface.
 * [com.zynergylabs.forager.app.ui.availability.AvailabilityScreen] previously named [SightingsMap] directly,
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
        nightModeLoaded = renderMode.nightModeLoaded,
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
        focusedFeature = content.focusedFeature,
        trackLiveLocation = renderMode.trackLiveLocation,
        showSearchCentre = renderMode.showSearchCentre,
        useOfflineTiles = renderMode.useOfflineTiles,
        keptTrackPolylines = content.keptTrackPolylines,
        findMarkers = content.findMarkers,
        photoMarkers = content.photoMarkers,
        offlineRegionCircles = content.offlineRegionCircles,
        bottomInset = renderMode.bottomInset,
        onUserCameraGesture = renderMode.onUserCameraGesture,
        layersState = renderMode.layers,
        onFeatureTap = renderMode.onFeatureTap,
        forecast = renderMode.forecast,
        cameraRequest = renderMode.cameraRequest,
        journalHighlights = content.journalHighlights,
        cameraMemory = renderMode.cameraMemory,
        attributionEndInset = renderMode.attributionEndInset,
        attributionBottomInset = renderMode.attributionBottomInset,
        modifier = modifier,
    )
}
