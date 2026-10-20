package com.zynergylabs.forager.app.ui.map

import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.delay
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.zynergylabs.forager.app.ui.theme.MapPalette
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.RecordPolyline
import com.zynergylabs.forager.app.domain.model.RecordRegion
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.map.initializeMapLibre
import com.zynergylabs.forager.app.ui.map.fanout.CameraMoveClassifier
import com.zynergylabs.forager.app.ui.map.fanout.FanReopenCoordinator
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutBackHandler
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutHost
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutState
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.TAP_BOX_DP
import com.zynergylabs.forager.app.ui.map.layers.withUnavailableColourFieldsHidden
import com.zynergylabs.forager.app.ui.map.layers.MapTapOutcome
import com.zynergylabs.forager.app.ui.map.layers.mapTapOutcome
import com.zynergylabs.forager.app.ui.map.layers.resolveTap
import com.zynergylabs.forager.app.ui.map.layers.tappableLayerIds
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Shows the searched region as a map with a marker per real observation ([sightings]).
 *
 * [plannedTrips] draws a second, distinct marker per planned trip — a flag since colour build C2
 * (a diamond before it; `MarkerGlyphs.kt` has every marker's silhouette), to read as
 * different from the translucent sighting dots (density of what's been observed): a planned trip
 * is a place the user chose for themselves, not derived from observation history.
 *
 * [onLongPress] fires with the geographic point under a long-press, when a caller actually listens
 * for it — the intended consumer would turn it into a planned trip (via a date picker it owns; this
 * composable knows nothing about dates or persistence, only where the gesture happened). Wired
 * through `MapLibreMap.addOnMapLongClickListener` rather than a gesture detector on the
 * [AndroidView] itself, for the same reason the previous osmdroid implementation used
 * `MapEventsOverlay`: MapLibre's own touch handling already owns pan/zoom on this `MapView`, and a
 * second, independent gesture detector on top would race it for the same touch stream.
 *
 * **No production call site consumes this as of 2026-08-28.** Both `mapSlot(...)` call sites in
 * `AvailabilityScreen.kt` (`MapTab`, `CompactMapTab`) pass `{}` for [onLongPress] — the
 * trip-planning/log-a-find interaction this parameter used to drive now goes through panning the
 * camera, tapping the add (+) button, and confirming via
 * [com.zynergylabs.forager.app.ui.map.CentrePinLocationPickerOverlay] instead. Kept wired here deliberately,
 * not left by accident: the listener costs nothing while dormant, and whether to remove it or give
 * it a new consumer is a product decision this comment fix doesn't make.
 *
 * ## Migration note (osmdroid -> MapLibre, `docs/plans/maplibre-migration.md` §2b)
 *
 * This composable is a full rewrite, not a port. Per the plan: "the dot markers ... all become
 * style layers rather than osmdroid `Overlay`s." Every one of them is now a `GeoJsonSource` + a
 * `CircleLayer`/`LineLayer`/`SymbolLayer` in
 * [initializeOverlayLayers], with actual data pushed by [refreshOverlayData] — see that function's
 * doc comment for why the two are split. [Basemap]/[styleJsonFor] are the only pieces reused as-is;
 * everything else, including [zoomForRadiusKm]'s numbers and (until colour build C2 replaced them
 * with `MapPalette`'s per-role palette) the colour constants, was carried over
 * from the deleted osmdroid version deliberately (same reasoning, same visual intent), not reused
 * as code (the two rendering APIs share nothing at the type level). The one exception is the dash
 * pattern's ratio, later moved off the connector entirely and redesigned for its new home on the
 * breadcrumb trail — see [BREADCRUMB_DASH_PATTERN]'s own doc comment.
 *
 * **What is explicitly re-confirmed, and how, not just carried forward:**
 * - *The dashed line still reads as dashed.* `line-dasharray` is a real MapLibre style
 *   property (`PropertyFactory.lineDasharray`, verified against the pinned
 *   `org.maplibre.gl:android-sdk:13.5.0` artifact with `javap` — see this file's own history for
 *   what was checked), not a Canvas `PathEffect` workaround, so this is a supported case, not an
 *   emulation of one. `SightingsMapOverlayDataTest` asserts the built `LineLayer` actually carries a
 *   non-empty `line-dasharray` after every one of `basemap`'s possible values, the direct MapLibre
 *   analogue of what `SightingsMapBasemapSwapTest` asserted for osmdroid's `PathEffect`.
 * - *Content does not paint outside this composable's slot.* The `Modifier.clipToBounds()` in [SightingsMapLayout] is
 *   kept, but the specific mechanism the old doc comment described — osmdroid's `TilesOverlay` and
 *   `PolyOverlayWithIW` drawing raw Canvas bitmaps/paths past the view's rectangle because
 *   `AndroidViewsHandler` doesn't clip a hosted `View` — does not apply to MapLibre's renderer.
 *   MapLibre's `MapView` (a `FrameLayout`, confirmed via `javap`) hosts a GL-backed render surface
 *   (`MapView.getRenderView()`) sized to the view's own layout bounds; a GL surface's framebuffer is
 *   bounded by construction — there is no analogue of a Canvas draw call painting past a rectangle
 *   nobody clipped, because the GPU only rasterizes into the pixels the surface actually owns. This
 *   is a reasoned architectural claim from the API shape, **not a hardware observation** — this
 *   session has no device to confirm it visually, so the clip stays in place regardless (removing a
 *   defensive modifier on an argument alone, with nothing to observe the result on, is not a trade
 *   this migration takes). Flagged in the handoff as still needing an eyes-on check.
 * - *The overlay colours.* Originally left unretouched on the theory that no new palette existed yet
 *   to re-check them against, since this migration keeps all four basemaps as the *same* raster tile
 *   services osmdroid used (see [Basemap]'s doc comment). **That theory held for the connector but
 *   not the sighting dots.** The first real hardware pass of this renderer (Portland-metro, USGS
 *   Topo) confirmed the dashed connector still reads as dashed — the colour question there really
 *   was moot. But the same screenshot found the sighting dots (bark brown, at the sighting layer's 0.7 fill opacity, now in `MAP_LAYER_REGISTRY`)
 *   an unresolvable smudge in a dense cluster near Lake Oswego, overlapping each other and the
 *   cluster badge — a real legibility failure this migration's "same raster tiles, same palette"
 *   reasoning didn't predict, because the failure is about density and boundary loss between
 *   overlapping translucent dots, not about the colour reading against a *different* basemap
 *   palette. Fixed with [SIGHTING_DOT_STROKE_WIDTH_PX] and MapPalette's `sightingDotStroke` below — a stroke,
 *   not full opacity: overlap density is itself information (a muddle of dots *is* the signal that
 *   several sightings cluster there), so the fix is boundary definition, not maximum contrast. Not
 *   yet re-confirmed on hardware, and not yet checked on the imagery basemap specifically.
 *
 * **Partially rebuilt:** the tap-to-see-title/snippet popup osmdroid's `Marker.title`/`.snippet`
 * gave for free had no style-layer equivalent — until [onSightingTap], added for a real observation
 * marker's info card, which does query the tapped point back (`MapLibreMap.queryRenderedFeatures`
 * against [SIGHTING_LAYER_ID] in the click listener, MapListeners.kt) and calls out with the matching
 * [Sighting]. Since map layers L0a every other tappable layer is queried too and the winner is
 * chosen by `resolveTap` (markers, then lines, then colour fields; the topmost layer within a
 * group); a winner that is not a sighting goes to [onFeatureTap] with its layer id, the
 * `featureId` property the pure builders write ([FEATURE_ID_PROPERTY]) and where it was tapped, and
 * since M1 nothing else fires for it (`mapTapOutcome`): the caller shows its bubble.
 */
@Composable
fun SightingsMap(
    region: Region,
    sightings: List<Sighting>,
    modifier: Modifier = Modifier,
    plannedTrips: List<PlannedTrip> = emptyList(),
    basemap: Basemap = Basemap.DEFAULT,
    /** See [com.zynergylabs.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    focusOverride: LatLng? = null,
    onLongPress: (LatLng) -> Unit = {},
    /** See [com.zynergylabs.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    onTap: () -> Unit = {},
    /** See [com.zynergylabs.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    onSightingTap: (Sighting, Offset, Float) -> Unit = { _, _, _ -> },
    /** See [com.zynergylabs.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    onCameraIdle: (LatLng) -> Unit = {},
    /**
     * Night mode: the basemap's colours inverted with hue kept (the V1 transform, `BasemapStyles.kt`'s
     * `NIGHT_RASTER_PAINT`); over the offline style, the same transform applied to its own layers
     * after it loads (`applyOfflineNightRecolour`). Every overlay marker follows it: the markers draw
     * from [MapPalette.forMode] of this flag (colour build C2).
     * It is the Night Maps setting and nothing else; no twilight trigger or long-press override
     * drives it (both were replaced by the setting, `MapPreferencesRepository`).
     *
     * Not the device's dark theme, and not derived from it: see [MapPalette]'s doc comment for
     * why that was tried, measured and abandoned.
     */
    nightMode: Boolean = false,
    /** See [MapRenderMode.nightModeLoaded]'s own doc comment: no style loads while this is `false`. */
    nightModeLoaded: Boolean = true,
    /** See [com.zynergylabs.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    breadcrumbPoints: List<LatLng> = emptyList(),
    /** See [com.zynergylabs.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    waypoints: List<Waypoint> = emptyList(),
    /** See [com.zynergylabs.forager.app.ui.map.MapOverlayContent.resumeTrackingRequestId]'s own doc comment. */
    resumeTrackingRequestId: Int = 0,
    /** See [com.zynergylabs.forager.app.ui.map.MapOverlayContent.searchFrameRequestId]'s own doc comment. */
    searchFrameRequestId: Int = 0,
    /** See [com.zynergylabs.forager.app.ui.map.MapOverlayContent.resetOrientationRequestId]'s own doc comment. */
    resetOrientationRequestId: Int = 0,
    /** See [com.zynergylabs.forager.app.ui.map.MapOverlayContent.focusedObservationId]'s own doc comment. */
    focusedObservationId: Long? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.trackLiveLocation]'s own doc comment. */
    trackLiveLocation: Boolean = true,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.showSearchCentre]'s own doc comment. */
    showSearchCentre: Boolean = true,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.useOfflineTiles]'s own doc comment. */
    useOfflineTiles: Boolean = false,
    /** See [com.zynergylabs.forager.app.ui.map.MapOverlayContent.keptTrackPolylines]'s own doc comment. */
    keptTrackPolylines: List<RecordPolyline> = emptyList(),
    /** See [com.zynergylabs.forager.app.ui.map.MapOverlayContent.findMarkers]'s own doc comment. */
    findMarkers: List<RecordPoint> = emptyList(),
    /** See [com.zynergylabs.forager.app.ui.map.MapOverlayContent.photoMarkers]'s own doc comment. */
    photoMarkers: List<RecordPoint> = emptyList(),
    /** See [com.zynergylabs.forager.app.ui.map.MapOverlayContent.offlineRegionCircles]'s own doc comment. */
    offlineRegionCircles: List<RecordRegion> = emptyList(),
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.bottomInset]'s own doc comment. */
    bottomInset: Dp = 0.dp,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.onUserCameraGesture]'s own doc comment. */
    onUserCameraGesture: () -> Unit = {},
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.layers]'s own doc comment. */
    layersState: MapLayersState = MapLayersState.DEFAULT,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.onFeatureTap]'s own doc comment. */
    onFeatureTap: (MapFeatureTap) -> Unit = {},
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.onCloseBubble]'s own doc comment. */
    onCloseBubble: () -> Unit = {},
    /** See [com.zynergylabs.forager.app.ui.map.MapOverlayContent.focusedFeature]'s own doc comment. */
    focusedFeature: FocusedMapFeature? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.forecast]'s own doc comment. */
    forecast: MapForecastFeed? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.cameraRequest]'s own doc comment. */
    cameraRequest: MapCameraRequest? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapOverlayContent.journalHighlights]'s own doc comment. */
    journalHighlights: JournalEntryHighlights = JournalEntryHighlights.NONE,
    /** See [com.zynergylabs.forager.app.ui.map.MapOverlayContent.route]'s own doc comment. */
    route: RouteOnMap? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.cameraMemory]'s own doc comment. */
    cameraMemory: MapCameraMemory? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.leavingTab]'s own doc comment. */
    leavingTab: Boolean = false,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.attributionEndInset]'s own doc comment. */
    attributionEndInset: Dp = 0.dp,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.attributionBottomInset]'s own doc comment. */
    attributionBottomInset: Dp? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.attributionKeepClear]'s own doc comment. */
    attributionKeepClear: androidx.compose.ui.geometry.Rect? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.attributionBesideCredit]'s own doc comment. */
    attributionBesideCredit: Boolean = false,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.returnMemory]'s own doc comment. */
    returnMemory: MapReturnMemory? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.backEnabled]'s own doc comment. */
    backEnabled: Boolean = true,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.navigationView]'s own doc comment. */
    navigationView: NavigationViewRequest? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.trueHeading]'s own doc comment. */
    trueHeading: State<TrueHeadingReading>? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.nudgeSpringBack]'s own doc comment. */
    nudgeSpringBack: Boolean = false,
) {
    val context = LocalContext.current
    // Resources through LocalResources, which Compose re-reads on a configuration change, rather than
    // LocalContext.current.resources, which it does not (lint LocalContextResourcesRead; dispatch 2026-10-17-03).
    val resources = LocalResources.current

    // The marker palette is MapPalette.forMode(nightMode) on every basemap (colour build C2). It is chosen inside requestedMapStyle, below, rather than here, so that a
    // headless test reaches it (OfflineStyleSwapTest); the style effect reads requested.palette.
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        // MapLibre is initialised at application start; this call is idempotent and is here so a
        // map composed on a path that skipped it still fails loudly (logged) rather than crashing later.
        initializeMapLibre(context)
        MapView(context).apply { onCreate(null) }
    }

    // Latest callbacks, read from inside listeners registered exactly once in the DisposableEffect
    // in MapListeners.kt — without this indirection, a listener registered once would keep calling whichever
    // onTap/onLongPress lambda instance was current at registration time, not the caller's latest.
    val currentOnTapState = rememberUpdatedState(onTap)
    val currentOnSightingTapState = rememberUpdatedState(onSightingTap)
    val currentOnLongPressState = rememberUpdatedState(onLongPress)
    val currentOnCameraIdleState = rememberUpdatedState(onCameraIdle)
    val currentOnUserCameraGestureState = rememberUpdatedState(onUserCameraGesture)
    val currentNavigationViewState = rememberUpdatedState(navigationView)
    val currentNavigationView by currentNavigationViewState
    // Ruling A (continuation 2026-09-28-432): the puck, and a facing-up map, take the app's own true
    // heading, not MapLibre's magnetic one. The heading handed in, else one made here from the
    // screen's compass (LocalMapCompass), else none, and MapLibre's own engine stays. Which applies
    // is fixed for a map's life: a map is given a heading or a compass from the start, or neither.
    val mapCompass = LocalMapCompass.current
    val headingSource: State<TrueHeadingReading>? = trueHeading
        ?: mapCompass?.let { rememberTrueHeading(it.compassProvider, it.computeTrueHeading, it.liveFix) }
    val appCompassEngine = remember(headingSource != null) { if (headingSource != null) TrueHeadingCompassEngine() else null }
    LaunchedEffect(appCompassEngine, headingSource) {
        val engine = appCompassEngine ?: return@LaunchedEffect
        val source = headingSource ?: return@LaunchedEffect
        snapshotFlow { source.value }.collect { engine.update(it) }
    }
    // Dispatch 2026-09-28-510 (the owner: "App feeds the dot"): the dot is drawn where the app has judged
    // the walker to be, the screen's position (LocalMapPosition), else MapLibre's own engine stays. Fixed
    // for a map's life, like the compass. The look is read here, and changes rarely: it is derived, so a new
    // fix of the same kind does not recompose this map.
    val mapPosition = LocalMapPosition.current
    val appLocationEngine = remember(mapPosition != null) { if (mapPosition != null) AppLocationEngine() else null }
    val positionLook = mapPosition?.let { position -> remember(position) { derivedStateOf { positionLookOf(position.shown.value, position.liveFix.value) } }.value } ?: PositionLook.PRECISE
    val currentPositionLookState = rememberUpdatedState(positionLook)
    // The look last handed to MapLibre, so an unchanged look is not re-applied on every style load.
    val appliedPositionLook = remember { arrayOf(PositionLook.PRECISE) }
    // The navigation view's own camera mode changes, marked so the tracking listener (MapStyleReload.kt, MapLocationDot.kt) does not
    // read them as the user moving away from the view; and the mode the view last asked for.
    val navigationModeChange = remember { NavigationModeChange() }
    // The map's height, for the padding that puts the walker below the centre; recomputed on resize (fullscreen, rotation).
    val navigationMapHeightPxState = remember { mutableIntStateOf(0) }
    var navigationMapHeightPx by navigationMapHeightPxState
    val currentOnFeatureTapState = rememberUpdatedState(onFeatureTap)
    val currentOnCloseBubbleState = rememberUpdatedState(onCloseBubble)
    // Read by the click listener (the draw order the tap precedence ranks against) and by each
    // style load (the order the layers are added in): both were registered or launched before a
    // later state change could otherwise reach them.
    //
    // Map layers L0b (B5, and the planner's ruling on F2): the state this map draws with is the one
    // it is given with every colour field its forecast feed does not name hidden. So a map with no
    // feed (the Cartography entry map, the centre-pin pickers, any map in a release build, whose store
    // never has data) never draws, credits or hit-tests a colour field, whatever the stored choice
    // says; the stored choice is not changed.
    val drawnLayersState = remember(layersState, forecast?.groupsByLayer) {
        withUnavailableColourFieldsHidden(layersState, MAP_LAYER_REGISTRY, forecast?.groupsByLayer?.keys.orEmpty())
    }
    val currentLayersStateState = rememberUpdatedState(drawnLayersState)
    // The map chrome's colour (the navigation bar's), read here because it follows the app's theme.
    val chromeColour = navigationBarContainerColor().toArgb()
    val currentChromeColourState = rememberUpdatedState(chromeColour)
    // The chrome colour the fan's circle image holds in the loaded style: set when the style loads (addFanOutLayers registers it), so the colour effect
    // in MapFan.kt registers it again only for a real change of colour.
    val fanCircleColour = remember { arrayOfNulls<Int>(1) }
    // Which camera moves are the location follower's and which are the user's or the app's own (dispatch 2026-09-28-380): MapLibre reports every
    // programmatic move with one reason, so each camera move this file makes marks itself here first, and the camera-idle listener clears the mark.
    val cameraMoveClassifier = remember { CameraMoveClassifier { task -> Handler(Looper.getMainLooper()).post(task) } }
    val currentForecastState = rememberUpdatedState(forecast)
    // Counts camera idles (map layers L0b, B5): the colour fields' cell feed (MapForecastCells.kt) is keyed on it, so
    // the store is asked for the blocks in view each time the camera goes idle.
    val cameraIdleCountState = remember { mutableIntStateOf(0) }
    // Read inside the click listener in MapListeners.kt (registered once, see that DisposableEffect's own
    // comment) so a tapped dot resolves against whichever sightings list is current, not whichever
    // one was in scope the moment the listener was registered.
    val currentSightingsState = rememberUpdatedState(sightings)
    // See MapOverlayContent.focusedObservationId's own doc comment for the dismiss-then-reappear
    // bug this closes: read fresh on every camera-idle event rather than latched into a local var
    // at tap time, so a caller-side dismissal (which only ever changes this parameter, never reaches
    // the click listener in MapListeners.kt) is visible here too.
    val currentFocusedObservationIdState = rememberUpdatedState(focusedObservationId)
    // M1 (F2): the point feature a caller's bubble is showing, and the lists it is looked up in, read
    // fresh on every camera idle (MapListeners.kt) for the reason the focused observation id is, through its holder getter.
    val currentFocusedFeatureState = rememberUpdatedState(focusedFeature)
    val currentFocusedFeature by currentFocusedFeatureState
    // Part 1 layout fixes, item 4: read by the camera-idle listener (registered once) and by the first
    // style load, for the reason the lines above give.
    val currentCameraMemoryState = rememberUpdatedState(cameraMemory)
    val currentCameraMemory by currentCameraMemoryState
    // Item 8: read by the camera-idle listener (registered once), for the reason the lines above give.
    val currentReturnMemoryState = rememberUpdatedState(returnMemory)
    val currentReturnMemory by currentReturnMemoryState
    val currentPlannedTripsState = rememberUpdatedState(plannedTrips)
    val currentWaypointsState = rememberUpdatedState(waypoints)
    val currentFindMarkersState = rememberUpdatedState(findMarkers)
    val currentPhotoMarkersState = rememberUpdatedState(photoMarkers)
    val mapLibreMapState = remember { mutableStateOf<MapLibreMap?>(null) }
    var mapLibreMap by mapLibreMapState
    // Dispatch 2026-09-28-463: a nudge under the drag threshold gives, then springs back, while
    // navigating and following (see NudgeElastic.kt). Fed by the map view's touch listener (SightingsMapLayout.kt).
    val nudgeThresholdPx = NAVIGATION_NUDGE_THRESHOLD_DP * LocalDensity.current.density
    // Dispatch -477, Part A: where the current touch came down, for the release's log line.
    val touchDownAt = remember { FloatArray(2) }
    // Dispatch -477, Part B: a quick flick under the drag threshold springs back instead of ending
    // following through MapLibre's fling (see NudgeFlingGuard). Fed by the map view's touch listener (SightingsMapLayout.kt).
    val nudgeFlingGuard = remember {
        NudgeFlingGuard(
            object : FlingSwitch {
                override var enabled: Boolean
                    get() = mapLibreMap?.uiSettings?.isFlingVelocityAnimationEnabled ?: true
                    set(value) {
                        mapLibreMap?.uiSettings?.isFlingVelocityAnimationEnabled = value
                    }
            },
        )
    }
    // Built once: the AndroidView factory in SightingsMapLayout.kt hands this instance to the map view's listener, which is attached once.
    val nudgeElastic = remember {
        NudgeElasticDriver(
            camera = object : NudgeCamera {
                override fun canGive(): Boolean {
                    val component = mapLibreMap?.locationComponent ?: return false
                    return component.isLocationComponentActivated &&
                        currentNavigationView?.following == true &&
                        navigationModeChange.expected != null &&
                        !navigationModeChange.transitioning &&
                        component.cameraMode != CameraMode.NONE
                }

                override fun showPadding(padding: DoubleArray) {
                    mapLibreMap?.locationComponent?.paddingWhileTracking(padding, 0L)
                }
            },
            thresholdPx = nudgeThresholdPx,
            viewTopPaddingPx = { navigationViewTopPaddingPx(navigationMapHeightPx) },
        )
    }
    DisposableEffect(nudgeElastic) { onDispose { nudgeElastic.cancel() } }
    // Dispatch 2026-10-19-37: the plain map's own nudge, beside navigation's (PlainMapNudge.kt), on the maps that ask for it.
    // Fed by the map view's touch listener after navigation's (SightingsMapLayout.kt).
    val currentNudgeSpringBackState = rememberUpdatedState(nudgeSpringBack)
    val currentNudgeSpringBack by currentNudgeSpringBackState
    val plainNudge = remember {
        PlainMapNudge(
            map = object : PlainNudgeMap {
                override fun cameraMode(): Int? =
                    mapLibreMap?.locationComponent?.takeIf { it.isLocationComponentActivated }?.cameraMode

                override fun showPadding(padding: DoubleArray) {
                    mapLibreMap?.locationComponent?.paddingWhileTracking(padding, 0L)
                }

                override fun clearPaddingKeepingPicture() {
                    mapLibreMap?.let { clearNudgePaddingKeepingPicture(it, mapView, cameraMoveClassifier) }
                }

                override var flingEnabled: Boolean
                    get() = mapLibreMap?.uiSettings?.isFlingVelocityAnimationEnabled ?: true
                    set(value) {
                        mapLibreMap?.uiSettings?.isFlingVelocityAnimationEnabled = value
                    }

                override fun paddingShown(): Boolean = mapLibreMap?.cameraPosition?.padding?.any { it != 0.0 } == true
            },
            springBack = { currentNudgeSpringBack },
            navigating = { currentNavigationView != null },
            thresholdPx = nudgeThresholdPx,
        )
    }
    DisposableEffect(plainNudge) { onDispose { plainNudge.cancel() } }
    // The Style instance from the most recently completed setStyle callback. Distinct from
    // "which style is currently applied" (appliedStyle, below; MapStyleReload.kt sets both) because this is what the data
    // effect (MapDataRefresh.kt) keys on: a new Style object means new (empty) sources that need their content pushed.
    val loadedStyleState = remember { mutableStateOf<Style?>(null) }
    var loadedStyle by loadedStyleState

    // RECORD -761 (the owner: "Picture cover, this build (Recommended)"): a map coming back to the Maps tab draws the picture the
    // leaving map took of itself over its own first frames, until it has drawn a full frame of its own (MapReturnCover.kt, whose
    // header has the why, the when-not and the cost). Decided once, as this MapView is made; every outcome is logged.
    val coverPicture = remember {
        val memory = cameraMemory
        val cover = memory?.takeCover()
        val decision = coverDecision(
            pictureCamera = cover?.camera,
            saved = memory?.saved,
            target = region to focusOverride,
            searchFrameRequestId = searchFrameRequestId,
            appliedSearchFrameId = memory?.appliedSearchFrameId ?: 0,
            cameraRequestPending = cameraRequest != null,
        )
        if (memory != null) {
            Log.i(MAPS_COMEBACK_LOG_TAG, "cover: $decision" + (if (decision == CoverDecision.SHOW) "" else "; the map draws itself as before"))
        }
        if (decision == CoverDecision.SHOW) {
            cover!!.bitmap.asImageBitmap()
        } else {
            cover?.bitmap?.recycle()
            null
        }
    }
    val coverPhaseState = remember { mutableStateOf(if (coverPicture != null) CoverPhase.COVERING else CoverPhase.GONE) }
    var coverPhase by coverPhaseState
    LaunchedEffect(Unit) {
        if (coverPicture == null) return@LaunchedEffect
        delay(MAP_COVER_READY_CAP_MS)
        if (coverPhase == CoverPhase.COVERING) {
            Log.w(MAPS_COMEBACK_LOG_TAG, "cover: no full frame within $MAP_COVER_READY_CAP_MS ms; handed over anyway")
            coverPhase = CoverPhase.HANDED_OVER
        }
    }
    // The marker fan-out (dispatch 2026-09-28-197): its state, and the tap handler that opens and folds it,
    // which exists once the map is ready (getMapAsync, in MapListeners.kt).
    val fanOut = remember { MarkerFanOutState() }
    val tapHandlerRef = remember { TapHandlerRef() }
    // Item 8 (dispatch -267): reopens the fan a user left; see FanReopenCoordinator.
    val fanReopen = remember {
        FanReopenCoordinator(
            handler = { tapHandlerRef.handler },
            takeKeys = { currentReturnMemory?.takeFanKeys() },
            onUnavailable = { count -> Log.w(FAN_OUT_RESTORE_TAG, "The fan of $count markers could not be reopened: fewer than two of them are still drawn.") },
        )
    }
    // The room a fan has: this view's bounds, and the controls over it that the screen has measured (MapKeepOut.kt).
    val fanSpace = rememberMapFanSpace()
    // Guards against re-running setStyle on every recomposition, mirroring the deleted osmdroid
    // applyBasemap's own name()-comparison guard and for the same reason: setStyle discards every
    // source and layer the previous style had, so calling it when nothing about the style actually
    // changed would flash the map to blank and rebuild everything for nothing. One value holding
    // basemap, palette, the offline flag (Stage 2e-ii) and effective night (colour build C1) rather
    // than the two separate appliedBasemap/appliedPalette vars it replaced — see needsStyleReload's
    // own doc comment for the "toggle does nothing" gap two separate comparisons left open, and
    // AppliedMapStyle.night's for the same gap night itself had until C1.
    //
    // The palette is in here for the reason the old appliedPalette existed: the overlay layers are
    // built once per style load with their colours baked into the layer properties, so a palette
    // change is only visible after those layers are rebuilt.
    //
    // Restyling is not free -- setStyle discards the LocationComponent state, which is why
    // activateLiveLocationIfPermitted has to run again in MapStyleReload.kt. Accepted because a basemap, palette
    // or offline switch is a deliberate, roughly once-per-outing action, not something that fires
    // on every recomposition.
    val appliedStyleState = remember { mutableStateOf<AppliedMapStyle?>(null) }

    // What the camera was last deliberately moved to by the data+camera refresh effect (MapDataRefresh.kt) —
    // *not* re-derived from mapLibreMap.cameraPosition, which changes continuously while GPS
    // tracking owns the camera and would make this comparison meaningless. See
    // shouldMoveCameraToTarget's own doc comment for the hardware-reported bug this closes.
    val lastAppliedCameraTargetState = remember { mutableStateOf<Pair<Region, LatLng?>?>(null) }
    var lastAppliedCameraTarget by lastAppliedCameraTargetState

    // The id of the last MapRenderMode.cameraRequest this MapView applied (the entry map's opening
    // frame): kept with the MapView, so a request arriving again on a later recomposition, after a
    // fullscreen switch or a find overlay's Back, does not move the camera the user has since moved.
    val lastAppliedCameraRequestIdState = remember { mutableStateOf<String?>(null) }

    // RECORD -750, item 5: the last search frame this map applied, for a map with no camera memory; one with memory keeps it
    // there (MapCameraMemory.appliedSearchFrameId), so it outlives the MapView.
    val localAppliedSearchFrameId = remember { intArrayOf(0) }

    // Part 1 layout fixes, item 5: MapLibre's own attribution margins, read once the map is ready, which
    // the caption's insets are added to in MapAttributionInsets.kt.
    val attributionDefaultMarginsState = remember { mutableStateOf<IntArray?>(null) }

    // Item 1 (dispatch 2026-09-29-57, amendment -262): the map's own size, written by the layout (SightingsMapLayout.kt) and
    // read by the attribution placement (MapAttributionInsets.kt). Declared here, with the holder's other states, since UI split Phase 2.2 (dispatch 2026-10-19-29);
    // it was declared in the attribution block until then. A plain remember with a constant start, not an observer.
    val mapSizePxState = remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    // RECORD -954: the credit strip's laid-out width, written by the layout (SightingsMapLayout.kt) and read by the attribution
    // placement (MapAttributionInsets.kt).
    // Moved up here for the same reason as mapSizePx.
    val creditWidthPxState = remember { mutableIntStateOf(0) }

    // UI split Phase 2.2 (dispatch 2026-10-19-29): the map's state holder, over the very State objects remembered above, so
    // the effect groups can move to their own files and take it. Nothing is copied into it: each read is a getter and each
    // write a setter on the same State this function reads (SightingsMapState's own doc comment). Made once, for the life of
    // this MapView, like every unkeyed remember above. The effect groups moved to their own files take it.
    val sightingsMapState = remember {
        SightingsMapState(
            mapView = mapView,
            onTapState = currentOnTapState,
            onSightingTapState = currentOnSightingTapState,
            onLongPressState = currentOnLongPressState,
            onCameraIdleState = currentOnCameraIdleState,
            onUserCameraGestureState = currentOnUserCameraGestureState,
            navigationViewState = currentNavigationViewState,
            positionLookState = currentPositionLookState,
            onFeatureTapState = currentOnFeatureTapState,
            onCloseBubbleState = currentOnCloseBubbleState,
            layersStateState = currentLayersStateState,
            chromeColourState = currentChromeColourState,
            forecastState = currentForecastState,
            sightingsState = currentSightingsState,
            focusedObservationIdState = currentFocusedObservationIdState,
            focusedFeatureState = currentFocusedFeatureState,
            cameraMemoryState = currentCameraMemoryState,
            returnMemoryState = currentReturnMemoryState,
            plannedTripsState = currentPlannedTripsState,
            waypointsState = currentWaypointsState,
            findMarkersState = currentFindMarkersState,
            photoMarkersState = currentPhotoMarkersState,
            mapLibreMapState = mapLibreMapState,
            loadedStyleState = loadedStyleState,
            appliedStyleState = appliedStyleState,
            coverPhaseState = coverPhaseState,
            lastAppliedCameraTargetState = lastAppliedCameraTargetState,
            lastAppliedCameraRequestIdState = lastAppliedCameraRequestIdState,
            attributionDefaultMarginsState = attributionDefaultMarginsState,
            cameraIdleCountState = cameraIdleCountState,
            navigationMapHeightPxState = navigationMapHeightPxState,
            mapSizePxState = mapSizePxState,
            creditWidthPxState = creditWidthPxState,
            appliedPositionLook = appliedPositionLook,
            navigationModeChange = navigationModeChange,
            fanCircleColour = fanCircleColour,
            cameraMoveClassifier = cameraMoveClassifier,
            touchDownAt = touchDownAt,
            nudgeFlingGuard = nudgeFlingGuard,
            nudgeElastic = nudgeElastic,
            nudgeSpringBackState = currentNudgeSpringBackState,
            plainNudge = plainNudge,
            coverPicture = coverPicture,
            fanOut = fanOut,
            tapHandlerRef = tapHandlerRef,
            fanReopen = fanReopen,
            localAppliedSearchFrameId = localAppliedSearchFrameId,
        )
    }

    // Lifecycle and teardown: MapLifecycle.kt. Called here, before the listeners and after the nudges' DisposableEffects,
    // so the four DisposableEffects keep their creation order and so their dispose order (Compose forgets in reverse).
    MapLifecycle(state = sightingsMapState, lifecycleOwner = lifecycleOwner)

    // Every MapLibre listener, registered once in the map-ready callback: MapListeners.kt.
    MapListeners(state = sightingsMapState, resources = resources, fanSpace = fanSpace)

    // Style swap (Stage 2e-ii, colour build C1): MapStyleReload.kt.
    MapStyleReload(
        state = sightingsMapState,
        basemap = basemap,
        useOfflineTiles = useOfflineTiles,
        nightMode = nightMode,
        nightModeLoaded = nightModeLoaded,
        trackLiveLocation = trackLiveLocation,
        context = context,
        resources = resources,
        appCompassEngine = appCompassEngine,
        appLocationEngine = appLocationEngine,
    )

    // The way home (dispatch -497) and the data + camera refresh: MapDataRefresh.kt.
    MapDataRefresh(
        state = sightingsMapState,
        route = route,
        region = region,
        sightings = sightings,
        plannedTrips = plannedTrips,
        focusOverride = focusOverride,
        breadcrumbPoints = breadcrumbPoints,
        waypoints = waypoints,
        focusedObservationId = focusedObservationId,
        keptTrackPolylines = keptTrackPolylines,
        findMarkers = findMarkers,
        photoMarkers = photoMarkers,
        offlineRegionCircles = offlineRegionCircles,
        showSearchCentre = showSearchCentre,
        cameraRequest = cameraRequest,
        journalHighlights = journalHighlights,
        searchFrameRequestId = searchFrameRequestId,
        resources = resources,
    )

    // The marker fan's effects on the loaded style (dispatch 2026-09-28-197 and its follow-ups): MapFan.kt.
    MapFan(
        state = sightingsMapState,
        sightings = sightings,
        plannedTrips = plannedTrips,
        waypoints = waypoints,
        findMarkers = findMarkers,
        photoMarkers = photoMarkers,
        drawnLayersState = drawnLayersState,
        journalHighlights = journalHighlights,
        focusedObservationId = focusedObservationId,
        returnMemory = returnMemory,
        chromeColour = chromeColour,
        resources = resources,
    )

    // Colour-field cells (map layers L0b, B5): MapForecastCells.kt.
    MapForecastCells(state = sightingsMapState, forecast = forecast)

    // The location dot (dispatch 2026-09-28-510) and the locate button: MapLocationDot.kt.
    MapLocationDot(
        state = sightingsMapState,
        appLocationEngine = appLocationEngine,
        mapPosition = mapPosition,
        positionLook = positionLook,
        trackLiveLocation = trackLiveLocation,
        resumeTrackingRequestId = resumeTrackingRequestId,
        context = context,
        appCompassEngine = appCompassEngine,
    )

    // Dispatch 2026-09-28-430 (plan task T22): the navigation view: MapNavigationView.kt.
    MapNavigationView(
        state = sightingsMapState,
        navigationView = navigationView,
        basemap = basemap,
        trackLiveLocation = trackLiveLocation,
        context = context,
    )

    // Part 1 layout fixes, item 5, and RECORD -954: MapLibre's attribution button and logo: MapAttributionInsets.kt.
    MapAttributionInsets(
        state = sightingsMapState,
        attributionBottomInset = attributionBottomInset,
        bottomInset = bottomInset,
        attributionEndInset = attributionEndInset,
        attributionKeepClear = attributionKeepClear,
        attributionBesideCredit = attributionBesideCredit,
    )

    // The map icon bar's orientation-reset control — MapLibre's own native compass view is
    // disabled in MapListeners.kt (see that DisposableEffect(mapView) block's own comment), so this is the only
    // way to straighten the map back to north once a rotate gesture has turned it. easeCamera, not
    // an instant jump, matching this map's other camera moves; bearing only, not target or zoom.
    // Ruling D (continuation 2026-09-28-432): while navigating and following, a tap here is a move
    // away from the view: north-up, still following, and "Return to Route" shown. The first run
    // (this map's own creation) is not a tap, and keeps its old behaviour.
    val lastResetOrientationRequestId = remember { intArrayOf(resetOrientationRequestId) }
    LaunchedEffect(resetOrientationRequestId) {
        val map = mapLibreMap ?: return@LaunchedEffect
        cameraMoveClassifier.markAppMove()
        val tapped = resetOrientationRequestId != lastResetOrientationRequestId[0]
        lastResetOrientationRequestId[0] = resetOrientationRequestId
        val view = currentNavigationView
        if (tapped && view != null && view.following && map.locationComponent.isLocationComponentActivated) {
            navigationModeChange.northUpKeepingFollow(map)
            view.onLeftView()
            return@LaunchedEffect
        }
        map.easeCamera(CameraUpdateFactory.bearingTo(0.0))
    }

    // The fan's clock, and Back closing it before anything else Back would close.
    MarkerFanOutHost(fanOut)
    MarkerFanOutBackHandler(fanOut, bubbleOpen = focusedObservationId != null || focusedFeature != null, backEnabled = backEnabled)

    // RECORD -761: as the tab starts to leave, the picture the map coming back will draw over itself. MapLibre's own snapshot
    // (its GL surface; the map is a SurfaceView, which a Compose capture cannot read), with the camera it shows.
    LaunchedEffect(leavingTab) {
        if (!leavingTab) return@LaunchedEffect
        val memory = currentCameraMemory ?: return@LaunchedEffect
        val map = mapLibreMap
        if (map == null || loadedStyle == null) {
            Log.i(MAPS_COMEBACK_LOG_TAG, "cover: no picture taken, the map had not loaded")
            return@LaunchedEffect
        }
        map.snapshot { bitmap ->
            val camera = map.cameraPosition
            val target = camera.target
            if (target == null) {
                Log.w(MAPS_COMEBACK_LOG_TAG, "cover: picture dropped, the camera had no target")
                bitmap.recycle()
            } else {
                memory.keepCover(
                    MapCover(
                        bitmap,
                        MapCameraSnapshot(
                            target = LatLng(target.latitude, target.longitude),
                            zoom = camera.zoom,
                            bearing = camera.bearing,
                            tilt = camera.tilt,
                            following = map.locationComponent.isLocationComponentActivated && map.locationComponent.cameraMode != CameraMode.NONE,
                            appliedTarget = lastAppliedCameraTarget,
                        ),
                    ),
                )
                Log.i(MAPS_COMEBACK_LOG_TAG, "cover: picture kept, ${bitmap.width}x${bitmap.height} at zoom=${camera.zoom}")
            }
        }
    }

    // The layout: the MapView, the cover over it, the credit caption. SightingsMapLayout.kt.
    SightingsMapLayout(
        state = sightingsMapState,
        modifier = modifier,
        basemap = basemap,
        useOfflineTiles = useOfflineTiles,
        drawnLayersState = drawnLayersState,
        nightMode = nightMode,
        bottomInset = bottomInset,
        fanSpace = fanSpace,
    )
}

/** RECORD -752: logcat tag for a map's first frames and its camera restore on a return to Maps (logging only). */
internal const val MAPS_COMEBACK_LOG_TAG = "ForagerMapsComeback"

/** RECORD -752: how many of a new MapView's first rendered frames are logged. `internal` since UI split Phase 2.2, PR 9: read by MapListeners.kt. */
internal const val MAPS_COMEBACK_FRAMES_LOGGED = 8

internal const val SIGHTINGS_MAP_TAG = "SightingsMap"
private const val FAN_OUT_RESTORE_TAG = "MarkerFanOut"
