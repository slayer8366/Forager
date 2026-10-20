package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.EntryMapFrame
import com.zynergylabs.forager.app.domain.ForecastCellStore
import com.zynergylabs.forager.app.domain.model.LatLng
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
     * `NIGHT_RASTER_PAINT`); the offline style is recoloured with the same transform after it loads.
     * Every overlay marker follows it too: markers draw from `MapPalette.forMode` of this flag (colour
     * build C2). Not the device's dark theme, and deliberately not derived from it —
     * see `MapPalette` for why that was tried, measured and abandoned.
     */
    val night: Boolean = false,
    /**
     * Whether [night] is the stored preference yet — colour build C1's cold-launch gate.
     * [SightingsMap] loads no style while this is `false`, so a night user's first style is the
     * night one rather than a day style the asynchronous preference read might never correct.
     *
     * `true` by default, and only the main map passes the real flag (`AvailabilityScreen`'s
     * `mapRenderMode`, from `AvailabilityUiState.nightModeMapsLoaded`), by the planner's ruling. Since
     * dispatch 2026-09-28-708 the main map's flag also waits for the stored basemap
     * (`AvailabilityUiState.mapMode`), so a restored Street is not preceded by a frame of the default
     * Topographical. The name still says night, for the first half of what it waits for; renaming it
     * across `SightingsMap`, `requestedMapStyle` and their tests was left out as churn. The
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
     * Closes the bubble that is showing, and nothing else: the tap handler calls it when a tap on a stack opens a fan (dispatch 2026-09-28-387, Part A). Not [MapSlot]'s `onTap`,
     * which also restores the fullscreen chrome. `{}` by default, a non-capturing lambda, so every existing caller's [MapRenderMode] still compares equal.
     */
    val onCloseBubble: () -> Unit = {},
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
     * RECORD -761: true while the tab holding this map is leaving (the tab crossfade's outgoing side), so the map takes the
     * picture a returning map draws over its first frames ([MapReturnCover.kt]). False for every caller but the Maps tab.
     */
    val leavingTab: Boolean = false,
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
    /**
     * The landscape L's measured bounds, in this map's own pixels, for MapLibre's attribution button ("i") to
     * keep clear of (dispatch 2026-09-29-57, amendment -262, item 1): [SightingsMap] moves the button's end
     * margin inboard of it when the two would intersect (`attributionEndInsetClearOf`). `null` for every caller
     * but the compact Maps tab in a short landscape window, which is where the L is.
     */
    val attributionKeepClear: androidx.compose.ui.geometry.Rect? = null,
    /**
     * RECORD -954 (the owner: "Have the map i next to the open street maps logo"; "Just landscape"): MapLibre's attribution
     * button sits beside the map credit strip at the bottom start, rather than in the bottom end corner. Only the compact Maps tab
     * in a short landscape window sets it; portrait and every other map keep the bottom-end corner.
     */
    val attributionBesideCredit: Boolean = false,
    /**
     * Where a fan's member keys are written while it is open, and where the fan to reopen after Back from a
     * find opened on the map is waiting (dispatch 2026-09-29-57, item 8). `null` for every caller but the compact
     * Maps tab. Here rather than on [MapSlot] for the parameter-count reason [layers] gives.
     */
    val returnMemory: MapReturnMemory? = null,
    /**
     * `false` while something sits over this map that owns Back, so an open fan's Back handler is not composed
     * and that thing's is the one asked (dispatch 2026-09-28-293: the Tools drawer). Default `true`: the pickers
     * and any caller with nothing over the map. Here rather than on [MapSlot] for the parameter-count reason
     * [layers] gives.
     */
    val backEnabled: Boolean = true,
    /**
     * The map's navigation view while navigating, `null` otherwise (dispatch 2026-09-28-430, plan
     * task T22): tilted, turned to the way the walker faces, following them below the centre. See
     * [NavigationViewRequest]. Off, the map is north-up and flat exactly as before.
     */
    val navigationView: NavigationViewRequest? = null,
    /**
     * The true heading the puck points and a facing-up map turns to, the one the compass strip and
     * the HUD read (continuation 2026-09-28-432, ruling A). `null` here, the map makes its own from
     * [LocalMapCompass] if one is provided, and otherwise keeps MapLibre's own (magnetic) compass.
     */
    val trueHeading: State<TrueHeadingReading>? = null,
    /**
     * Whether this map springs back from a small nudge while it follows the user outside navigation
     * (dispatch 2026-10-19-37; PlainMapNudge.kt has the step path). `false` by default; only the Maps
     * tab's map (`AvailabilityScreen`'s `mapRenderMode`) turns it on, by the owner's ruling "Maps tab
     * only": the centre-pin pickers keep MapLibre's own drag, so a small move still places the pin.
     */
    val nudgeSpringBack: Boolean = false,
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
    /**
     * Data part C (dispatch -668): told at each camera idle whether the camera is below the colour
     * fields' minimum zoom ([isBelowForecastZoom]), so the legend can say "Zoom in to see the forecast"
     * instead of showing an empty field with nothing said.
     */
    val onZoomedOutChanged: (Boolean) -> Unit = {},
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
