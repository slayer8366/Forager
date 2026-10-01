package com.zynergylabs.forager.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import com.zynergylabs.forager.app.ui.theme.MapPalette
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zynergylabs.forager.app.domain.EntryMapFrame
import com.zynergylabs.forager.app.domain.GeoDistance
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
import com.zynergylabs.forager.app.ui.map.fanout.CameraMoveCause
import com.zynergylabs.forager.app.ui.map.fanout.CameraMoveClassifier
import com.zynergylabs.forager.app.ui.map.fanout.FanMember
import com.zynergylabs.forager.app.ui.map.fanout.FanOutHideGate
import com.zynergylabs.forager.app.ui.map.fanout.FanReopenCoordinator
import com.zynergylabs.forager.app.ui.map.fanout.MapTapHandler
import com.zynergylabs.forager.app.ui.map.fanout.MapTapSinks
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutBackHandler
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutHost
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutState
import com.zynergylabs.forager.app.ui.map.layers.LayerPaint
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayerSpec
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.MapSourceIds
import com.zynergylabs.forager.app.ui.map.layers.OpacityProperty
import com.zynergylabs.forager.app.ui.map.layers.OpacityValue
import com.zynergylabs.forager.app.ui.map.layers.TAP_BOX_DP
import com.zynergylabs.forager.app.ui.map.layers.TapHit
import com.zynergylabs.forager.app.ui.map.layers.activeLayerCredits
import com.zynergylabs.forager.app.ui.map.layers.COLOUR_FIELDS
import com.zynergylabs.forager.app.ui.map.layers.ForecastCellsShown
import com.zynergylabs.forager.app.ui.map.layers.withUnavailableColourFieldsHidden
import com.zynergylabs.forager.app.domain.ForecastCellsResult
import com.zynergylabs.forager.app.ui.map.layers.layerPaintFor
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
import com.zynergylabs.forager.app.ui.map.layers.MapTapOutcome
import com.zynergylabs.forager.app.ui.map.layers.mapTapOutcome
import com.zynergylabs.forager.app.ui.map.layers.resolveTap
import com.zynergylabs.forager.app.ui.map.layers.tappableLayerIds
import com.zynergylabs.forager.app.ui.map.layers.TRACK_WIDTH_ZOOM_STOPS
import com.zynergylabs.forager.app.ui.map.layers.ZoomWidthStop
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToInt
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

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
 * - *Content does not paint outside this composable's slot.* The `Modifier.clipToBounds()` below is
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
 * against [SIGHTING_LAYER_ID] in the click listener below) and calls out with the matching
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
     * `NIGHT_RASTER_PAINT`), except Satellite, which stays day; over the offline style, the same
     * transform applied to its own layers after it loads (`applyOfflineNightRecolour`). Every
     * overlay marker follows it on every basemap, Satellite included: the markers draw from
     * [MapPalette.forMode] of this flag (colour build C2), so over Satellite only the markers switch.
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
    /** See [com.zynergylabs.forager.app.ui.map.MapOverlayContent.focusedFeature]'s own doc comment. */
    focusedFeature: FocusedMapFeature? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.forecast]'s own doc comment. */
    forecast: MapForecastFeed? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.cameraRequest]'s own doc comment. */
    cameraRequest: MapCameraRequest? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapOverlayContent.journalHighlights]'s own doc comment. */
    journalHighlights: JournalEntryHighlights = JournalEntryHighlights.NONE,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.cameraMemory]'s own doc comment. */
    cameraMemory: MapCameraMemory? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.attributionEndInset]'s own doc comment. */
    attributionEndInset: Dp = 0.dp,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.attributionBottomInset]'s own doc comment. */
    attributionBottomInset: Dp? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.attributionKeepClear]'s own doc comment. */
    attributionKeepClear: androidx.compose.ui.geometry.Rect? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.returnMemory]'s own doc comment. */
    returnMemory: MapReturnMemory? = null,
    /** See [com.zynergylabs.forager.app.ui.map.MapRenderMode.backEnabled]'s own doc comment. */
    backEnabled: Boolean = true,
) {
    val context = LocalContext.current

    // The marker palette is MapPalette.forMode(nightMode) on every basemap, Satellite included
    // (colour build C2). It is chosen inside requestedMapStyle, below, rather than here, so that a
    // headless test reaches it (OfflineStyleSwapTest); the style effect reads requested.palette.
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        // MapLibre is initialised at application start; this call is idempotent and is here so a
        // map composed on a path that skipped it still fails loudly (logged) rather than crashing later.
        initializeMapLibre(context)
        MapView(context).apply { onCreate(null) }
    }

    // Latest callbacks, read from inside listeners registered exactly once in the DisposableEffect
    // below — without this indirection, a listener registered once would keep calling whichever
    // onTap/onLongPress lambda instance was current at registration time, not the caller's latest.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnSightingTap by rememberUpdatedState(onSightingTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)
    val currentOnUserCameraGesture by rememberUpdatedState(onUserCameraGesture)
    val currentOnFeatureTap by rememberUpdatedState(onFeatureTap)
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
    val currentLayersState by rememberUpdatedState(drawnLayersState)
    // The map chrome's colour (the navigation bar's), read here because it follows the app's theme.
    val chromeColour = navigationBarContainerColor().toArgb()
    val currentChromeColour by rememberUpdatedState(chromeColour)
    // The chrome colour the fan's circle image holds in the loaded style: set when the style loads (addFanOutLayers registers it), so the colour effect
    // below registers it again only for a real change of colour.
    val fanCircleColour = remember { arrayOfNulls<Int>(1) }
    // Which camera moves are the location follower's and which are the user's or the app's own (dispatch 2026-09-28-380): MapLibre reports every
    // programmatic move with one reason, so each camera move this file makes marks itself here first, and the camera-idle listener clears the mark.
    val cameraMoveClassifier = remember { CameraMoveClassifier { task -> Handler(Looper.getMainLooper()).post(task) } }
    val currentForecast by rememberUpdatedState(forecast)
    // Counts camera idles (map layers L0b, B5): the colour fields' cell feed below is keyed on it, so
    // the store is asked for the blocks in view each time the camera goes idle.
    var cameraIdleCount by remember { mutableIntStateOf(0) }
    // Read inside the click listener below (registered once, see that DisposableEffect's own
    // comment) so a tapped dot resolves against whichever sightings list is current, not whichever
    // one was in scope the moment the listener was registered.
    val currentSightings by rememberUpdatedState(sightings)
    // See MapOverlayContent.focusedObservationId's own doc comment for the dismiss-then-reappear
    // bug this closes: read fresh on every camera-idle event rather than latched into a local var
    // at tap time, so a caller-side dismissal (which only ever changes this parameter, never reaches
    // the click listener below) is visible here too.
    val currentFocusedObservationId by rememberUpdatedState(focusedObservationId)
    // M1 (F2): the point feature a caller's bubble is showing, and the lists it is looked up in, read
    // fresh on every camera idle for the reason currentFocusedObservationId is.
    val currentFocusedFeature by rememberUpdatedState(focusedFeature)
    // Part 1 layout fixes, item 4: read by the camera-idle listener (registered once) and by the first
    // style load, for the reason the lines above give.
    val currentCameraMemory by rememberUpdatedState(cameraMemory)
    // Item 8: read by the camera-idle listener (registered once), for the reason the lines above give.
    val currentReturnMemory by rememberUpdatedState(returnMemory)
    val currentPlannedTrips by rememberUpdatedState(plannedTrips)
    val currentWaypoints by rememberUpdatedState(waypoints)
    val currentFindMarkers by rememberUpdatedState(findMarkers)
    val currentPhotoMarkers by rememberUpdatedState(photoMarkers)
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    // The Style instance from the most recently completed setStyle callback. Distinct from
    // "which style is currently applied" (appliedStyle, below) because this is what the data
    // effect keys on: a new Style object means new (empty) sources that need their content pushed.
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    // The marker fan-out (dispatch 2026-09-28-197): its state, and the tap handler that opens and folds it,
    // which exists once the map is ready (getMapAsync below).
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
    // activateLiveLocationIfPermitted has to run again below. Accepted because a basemap, palette
    // or offline switch is a deliberate, roughly once-per-outing action, not something that fires
    // on every recomposition.
    var appliedStyle by remember { mutableStateOf<AppliedMapStyle?>(null) }

    // What the camera was last deliberately moved to by the data+camera refresh effect below —
    // *not* re-derived from mapLibreMap.cameraPosition, which changes continuously while GPS
    // tracking owns the camera and would make this comparison meaningless. See
    // shouldMoveCameraToTarget's own doc comment for the hardware-reported bug this closes.
    var lastAppliedCameraTarget by remember { mutableStateOf<Pair<Region, LatLng?>?>(null) }

    // The id of the last MapRenderMode.cameraRequest this MapView applied (the entry map's opening
    // frame): kept with the MapView, so a request arriving again on a later recomposition, after a
    // fullscreen switch or a find overlay's Back, does not move the camera the user has since moved.
    var lastAppliedCameraRequestId by remember { mutableStateOf<String?>(null) }

    // Part 1 layout fixes, item 5: MapLibre's own attribution margins, read once the map is ready, which
    // the caption's insets are added to below.
    var attributionDefaultMargins by remember { mutableStateOf<IntArray?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    mapLibreMap?.locationComponent?.onStart()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapView.onPause()
                    mapLibreMap?.locationComponent?.onStop()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            tearDownMap(object : MapTeardownTarget {
                override fun stopLocationUpdates() {
                    mapLibreMap?.locationComponent?.onStop()
                }

                override fun destroyLocationComponent() {
                    mapLibreMap?.locationComponent?.onDestroy()
                }

                override fun destroyMapView() = mapView.onDestroy()
            })
        }
    }

    // Re-projects the glyph a shown bubble belongs to and reports it, so the bubble follows the glyph. Called at
    // every camera idle, and (Part 2 follow-ups F1 item 1) when the map's own size changes: a device rotation
    // resizes the view without any camera idle, and the bubble used to stay at its portrait place until the
    // next pan. Reads the latest lists and callbacks through the current* states, as the idle listener always did.
    fun reanchorFocusedBubble(map: MapLibreMap) {
        // Keeps a shown observation bubble glued to its own marker's real screen position
        // across a pan/zoom/rotate — a hardware report asked for exactly this ("have it stay
        // there when we move the map, so we know which one it belongs to"), and re-projecting
        // whichever sighting currentFocusedObservationId currently names on every idle (the
        // same projection call the click listener above uses once, at tap time) is what
        // answers it without this composable needing to reimplement MapLibre's own
        // screen<->geo math. Resolved fresh against currentSightings/currentFocusedObservationId
        // rather than a sighting captured at tap time, so a dismissal that's since cleared
        // the caller's own focused id (see MapOverlayContent.focusedObservationId's doc
        // comment) is reflected here too instead of silently re-reviving a closed bubble. The
        // bearing carried alongside — also re-read fresh here, not just at tap time — is what
        // lets the caller keep the bubble's own placement direction correct (screen position
        // and orientation both live, not just position) after a rotate gesture; see
        // AnchoredAtScreenPoint's own doc comment in AvailabilityScreen.kt for what it does
        // with this value.
        currentFocusedObservationId
            ?.let { id -> currentSightings.firstOrNull { it.observationId == id } }
            ?.let { sighting ->
                val screenPoint = map.projection.toScreenLocation(MapLibreLatLng(sighting.lat, sighting.lng))
                currentOnSightingTap(sighting, Offset(screenPoint.x, screenPoint.y), map.cameraPosition.bearing.toFloat())
            }
        // M1 (F2): the same for a point feature's bubble. Its glyph's own position is re-found
        // in the lists this map draws (focusedFeaturePosition) and reported as a feature tap
        // at that point, so the caller's bubble follows the glyph. A record no longer drawn
        // is not re-fired, and a dismissed bubble has no focusedFeature, so nothing revives it.
        currentFocusedFeature?.let { focus ->
            focusedFeaturePosition(focus, currentPlannedTrips, currentWaypoints, currentFindMarkers, currentPhotoMarkers)?.let { at ->
                val glyph = map.projection.toScreenLocation(MapLibreLatLng(at.lat, at.lng))
                currentOnFeatureTap(
                    MapFeatureTap(focus.layerId, focus.featureId, Offset(glyph.x, glyph.y), map.cameraPosition.bearing.toFloat(), at),
                )
            }
        }
    }

    // Registered once per composition of this MapView, not per recomposition: getMapAsync's
    // callback fires exactly once for the life of the MapView, so there's no re-registration to
    // guard against the way applyBasemap's guard above is needed for setStyle.
    DisposableEffect(mapView) {
        // A style that cannot be loaded — the offline style with no region covering the camera
        // and no network, or a worker URL that moved — fails here rather than in setStyle's own
        // callback, which simply never fires. Logged, never silently swallowed (CLAUDE.md); the
        // message is MapLibre's own. addOnDidFailLoadingMapListener verified against the pinned
        // 13.5.0 artifact with javap, the same way every other SDK call in this file was.
        mapView.addOnDidFailLoadingMapListener { message ->
            Log.w(SIGHTINGS_MAP_TAG, "MapLibre failed to load the map style: $message")
        }
        mapView.getMapAsync { map ->
            // Marker fan-out (dispatch 2026-09-28-197): the tap is decided by MapTapHandler, which is the
            // resolution this listener used to do inline (resolveTap over every tappable layer, a point
            // query then a TAP_BOX_DP box, then mapTapOutcome), with the fan-out in front of and behind it.
            // The four outcomes come back through the sinks below, unchanged from what the listener did.
            val handler = MapTapHandler(
                fan = fanOut,
                probe = MapLibreProbe(map, context.resources.displayMetrics.density) { key ->
                    // Where a fanned record is now, from the lists the map draws (the projection of it is the probe's): the
                    // same lookup a shown bubble's re-anchoring uses. Null once the record is no longer drawn.
                    focusedFeaturePosition(FocusedMapFeature(key.layerId, key.featureId), currentPlannedTrips, currentWaypoints, currentFindMarkers, currentPhotoMarkers)
                },
                space = fanSpace,
                // One layer at a time (amendment -255): a bubble showing on a sighting or a point glyph means an empty-map tap closes it and
                // leaves the fan. A tap on a fanned icon no longer produces that pair (it folds the fan, dispatch 2026-09-28-381), nor does a tap on a stack
                // (it closes the bubble as the fan opens, continuation -383); the return from a find's page still does.
                bubbleOpen = { currentFocusedObservationId != null || currentFocusedFeature != null },
                // The fan an icon was just picked from (null on any other tap), for the way back from that find's page.
                onFannedFrom = { picked -> currentReturnMemory?.fannedFrom = picked },
                drawOrder = { orderedLayers(MAP_LAYER_REGISTRY, currentLayersState) },
                // A record on a layer switched off is not drawn, so it is not a fan member.
                warn = { message -> Log.w(SIGHTINGS_MAP_TAG, message) },
                layerDrawn = { id -> MAP_LAYER_REGISTRY.firstOrNull { it.id == id }?.let { layerPaintFor(it, currentLayersState).visible } ?: false },
                sinks = object : MapTapSinks {
                    override fun onPlainTap() = currentOnTap()

                    override fun onSightingTap(observationId: Long?, xPx: Float, yPx: Float) {
                        // The sighting path as before L0a: the dot's observationId looked back up in
                        // the current list, the bubble anchored at the tap point, onTap when the id no
                        // longer resolves.
                        val tappedSighting = observationId?.let { id -> currentSightings.firstOrNull { it.observationId == id } }
                        if (tappedSighting != null) {
                            currentOnSightingTap(tappedSighting, Offset(xPx, yPx), map.cameraPosition.bearing.toFloat())
                        } else {
                            currentOnTap()
                        }
                    }

                    override fun onFeatureTap(layerId: String, featureId: String, xPx: Float, yPx: Float, at: LatLng) {
                        // M1 (owner's ruling 1, "Bubble only"): a feature tap opens its bubble and nothing
                        // else, as a sighting tap already did.
                        currentOnFeatureTap(MapFeatureTap(layerId, featureId, Offset(xPx, yPx), map.cameraPosition.bearing.toFloat(), at))
                    }

                    override fun onUnidentifiedFeature(layerId: String) {
                        // Logged, never silent: a feature with no id cannot name its record, so no
                        // bubble can show, and the tap is taken as a plain one.
                        Log.w(SIGHTINGS_MAP_TAG, "Tapped a feature on $layerId with no $FEATURE_ID_PROPERTY; taken as a plain tap.")
                        currentOnTap()
                    }
                },
            )
            tapHandlerRef.handler = handler
            map.addOnMapClickListener { latLng ->
                // toScreenLocation confirmed via javap against the pinned org.maplibre.gl:android-sdk:13.5.0.
                val screenPoint = map.projection.toScreenLocation(latLng)
                handler.onMapTap(LatLng(latLng.latitude, latLng.longitude), screenPoint.x, screenPoint.y)
                // false: unconsumed, matching the deleted osmdroid MapEventsOverlay's
                // singleTapConfirmedHelper — a plain tap isn't meant to swallow the event.
                false
            }
            map.addOnMapLongClickListener { latLng ->
                currentOnLongPress(LatLng(latLng.latitude, latLng.longitude))
                true
            }
            // Only a touch counts: REASON_API_GESTURE is dispatched from MapGestureDetector's
            // listeners alone, every programmatic move from Transform as REASON_API_ANIMATION —
            // see MapRenderMode.onUserCameraGesture's doc comment for the javap check.
            map.addOnCameraMoveStartedListener { reason ->
                // A fanned stack folds on a camera move the user made or asked for, a gesture or the app's own; it stays open when the map re-centres
                // itself while it follows the location (dispatch 2026-09-28-380: the user did not mean that move, and the fan travels with the map).
                val following = map.locationComponent.isLocationComponentActivated && map.locationComponent.cameraMode != CameraMode.NONE
                val cause = cameraMoveClassifier.classify(isUserCameraGesture(reason), following)
                if (cause == CameraMoveCause.UNKNOWN) {
                    Log.w(SIGHTINGS_MAP_TAG, "A camera move (reason $reason) is not a touch, not marked as the app's and not the location follower's; the open fan is folded as it always was.")
                }
                tapHandlerRef.handler?.onCameraMoveStarted(cause)
                if (isUserCameraGesture(reason)) currentOnUserCameraGesture()
            }
            // OnCameraIdleListener.onCameraIdle() takes no argument (verified via javap against
            // the pinned org.maplibre.gl:android-sdk:13.5.0 artifact — MapLibreMap$OnCameraIdleListener
            // declares only `void onCameraIdle()`), so the position has to be read back explicitly
            // via getCameraPosition() inside the callback, not received as a parameter the way
            // addOnMapLongClickListener's latLng is.
            map.addOnCameraIdleListener {
                cameraMoveClassifier.onCameraIdle()
                cameraIdleCount++
                // CameraPosition.target is declared `LatLng?` in the pinned SDK itself (verified via
                // javap: the vendor's own constructor carries an org.jetbrains.annotations.Nullable
                // on this parameter) — null before the map has finished laying out a first camera
                // position, which an idle event can fire for. Nothing to report yet in that case, so
                // this skips the callback rather than fabricating a coordinate.
                map.cameraPosition.target?.let { target ->
                    currentOnCameraIdle(LatLng(target.latitude, target.longitude))
                }
                // Part 1 layout fixes, item 4: the camera this map settled on, for the map that comes
                // back after a tab change. Not before this MapView's first style has loaded: until then
                // the camera is the SDK's default, and the restore has yet to read what was saved.
                if (loadedStyle != null) {
                    val camera = map.cameraPosition
                    camera.target?.let { target ->
                        currentCameraMemory?.saved = MapCameraSnapshot(
                            target = LatLng(target.latitude, target.longitude),
                            zoom = camera.zoom,
                            bearing = camera.bearing,
                            tilt = camera.tilt,
                            following = map.locationComponent.isLocationComponentActivated &&
                                map.locationComponent.cameraMode != CameraMode.NONE,
                            appliedTarget = lastAppliedCameraTarget,
                        )
                    }
                }
                // Item 8 (dispatch 2026-09-29-57, amendment -262, "Remember and reopen"): the fan a user left when they opened a
                // find on the Journal reopens here, at the first idle after this map's style has loaded (the camera memory's
                // restore has been applied by then, and the style load's own content fold, onContentChanged, has run), through
                // openFanFor. A reopen that comes to nothing is logged, never silent. Whether a later camera move or content
                // change folds it again is device-only: a real MapView cannot be built under Robolectric.
                fanReopen.onCameraIdle(loadedStyle != null)
                // Keeps a shown bubble glued to its glyph across a pan, zoom or rotate gesture: see
                // reanchorFocusedBubble, which a rotation of the device also calls (onViewportResized below).
                reanchorFocusedBubble(map)
            }
            // MapLibre's own tap-to-reveal attribution control defaults to bottom-start — the same
            // corner this composable's own always-visible Basemap.attribution caption occupies (see
            // below), so the two painted over each other: MapLibre's control and this app's own
            // basemap-specific credit, both real and both required, neither readable. Moved to
            // bottom-end so each has its own corner rather than trying to stack them, which would
            // need a margin computed from this caption's own (basemap-dependent) text height to stay
            // correct — confirmed via javap against the pinned org.maplibre.gl:android-sdk artifact
            // that UiSettings exposes setAttributionGravity/setAttributionMargins for exactly this.
            map.uiSettings.setAttributionGravity(Gravity.BOTTOM or Gravity.END)
            // Part 1 layout fixes, item 5: MapLibre's own margins, which the insets below are added to.
            attributionDefaultMargins = map.uiSettings.let {
                intArrayOf(it.attributionMarginLeft, it.attributionMarginTop, it.attributionMarginRight, it.attributionMarginBottom)
            }
            // MapLibre's own compass view (a floating circular reset-to-north control it draws
            // itself, top-right by default) is replaced by the map icon bar's own orientation-
            // reset control — a real hardware report found the two overlapping, and the SDK's own
            // compass has no callback this project could otherwise hook into for the icon bar's
            // matching entry, only a tap target of its own with fixed positioning. Disabled here
            // rather than repositioned: keeping both would mean two controls that do the same
            // thing, in two different places, on the same screen.
            map.uiSettings.isCompassEnabled = false
            mapLibreMap = map
        }
        onDispose { }
    }

    // Style swap: basemap, palette, the offline style (Stage 2e-ii), or effective night (colour
    // build C1: nightMode is a key, and AppliedMapStyle.night is compared). Keyed on the inputs
    // rather than driven from an AndroidView update block: setStyle is asynchronous (its callback
    // is where the new style's sources/layers can actually be added), which the old synchronous
    // update-block shape has no equivalent of.
    //
    // The offline branch loads OFFLINE_STYLE_URL by URI (see MapStyleSource's own doc comment for
    // why by URI and never fromJson), the exact string every region was downloaded against, so
    // MapLibre's offline database can serve the style document, its TileJSON and its tiles from
    // the store. Nothing else about the swap differs from a basemap swap: initializeOverlayLayers
    // re-adds every overlay in the callback exactly as it does for a basemap change, and the
    // data+camera refresh effect below re-pushes their content keyed on loadedStyle. One thing
    // deliberately left as the user will see it (owner ruling, 2e-ii: report, do not fix): the max
    // zoom preference stays the *basemap's* (17 for OpenTopoMap) over a store that stops at zoom
    // 15 — vector tiles overzoom cleanly, per OfflineMapRepository.MAX_ZOOM's doc comment, so the
    // user can zoom past the data's own ceiling without a hard stop. 2e-ii also left night inert
    // on the offline style; since colour build C1 it is applied by recolouring the loaded style's
    // own layers in the setStyle callback below (mapStyleSourceFor's doc comment).
    //
    // A style that fails to load (offline with no region covering the camera, a worker URL that
    // moved, a cold store) never reaches this callback, so appliedStyle and loadedStyle keep their
    // previous values and the map shows MapLibre's own blank. That failure is logged by the
    // OnDidFailLoadingMapListener registered in the DisposableEffect above, never swallowed; what
    // the user should be *told* in that state is a decision the pre-build report lists and this
    // dispatch did not make.
    LaunchedEffect(mapLibreMap, basemap, useOfflineTiles, nightMode, nightModeLoaded) {
        val map = mapLibreMap ?: return@LaunchedEffect
        // null until the Night Maps preference has loaded (the cold-launch gate): the effect
        // relaunches when nightModeLoaded turns true, and the first style it requests is then the
        // right one. The map shows MapLibre's own blank until then.
        val requested = requestedMapStyle(
            basemap = basemap,
            useOfflineTiles = useOfflineTiles,
            nightMode = nightMode,
            nightModeLoaded = nightModeLoaded,
        ) ?: return@LaunchedEffect
        if (!needsStyleReload(appliedStyle, requested)) return@LaunchedEffect
        // Captured before setStyle below discards the LocationComponent entirely (see
        // activateLiveLocationIfPermitted's own doc comment on why re-activation is needed at
        // all) — null only the very first time this composable ever activates the puck;
        // CameraMode.NONE if the user had already broken tracking by panning/zooming;
        // CameraMode.TRACKING if they hadn't. Restoring exactly this, rather than always
        // re-forcing TRACKING, is the fix for a real hardware report: switching basemap (or
        // toggling night mode, which goes through this same style-swap path on every basemap:
        // since colour build C2 the marker palette follows the toggle even over Satellite, whose
        // basemap style stays day) was recentering the
        // map on the user's location even after they had deliberately panned away — the
        // GPS/locate-me icon is the control for that, not this one.
        val previousCameraMode = if (map.locationComponent.isLocationComponentActivated) {
            map.locationComponent.cameraMode
        } else {
            null
        }
        // Part 1 layout fixes, item 4 (planner message 2026-09-28-98): a map that has left composition
        // with its tab and come back is a new MapView, which would open where a fresh map does (the
        // region move below, at zoomForRadiusKm, and the first activation's zoom-in). Its first style
        // load restores the camera the user left instead: target, zoom, bearing and tilt, the region
        // target taken as already applied (so the region move does not run, while a new search still
        // does), and the tracking mode (so the zoom-in does not run). Only on this MapView's first
        // style; a later style swap keeps its own camera, as before.
        val cameraRestore = cameraRestoreFor(if (appliedStyle == null) currentCameraMemory?.saved else null, previousCameraMode)
        cameraRestore?.let { cameraMoveClassifier.markAppMove(); applyCameraRestore(map, it) }
        map.setMaxZoomPreference(basemap.maxZoom.toDouble())
        val builder = when (val source = mapStyleSourceFor(basemap, night = requested.night, useOfflineTiles = useOfflineTiles)) {
            is MapStyleSource.Json -> Style.Builder().fromJson(source.json)
            is MapStyleSource.Uri -> Style.Builder().fromUri(source.uri)
        }
        map.setStyle(builder) { style ->
            // The symbol fade off for the whole style, before anything is drawn on it (PlacementTransitions.kt).
            disableSymbolFade(style)
            // Offline night (colour build C1 (d)): the offline style has no raster layer for
            // NIGHT_RASTER_PAINT to act on, so its own layers are recoloured here, after it loads
            // from its one URL and before the overlays are added, so the overlays' own line and
            // fill layers are never touched. Day, or night off, restyles nothing: the style came
            // fresh from its URI.
            if (requested.useOfflineTiles && requested.night) applyOfflineNightRecolour(style)
            initializeOverlayLayers(
                style,
                density = context.resources.displayMetrics.density,
                palette = requested.palette,
                drawOrder = orderedLayers(MAP_LAYER_REGISTRY, currentLayersState),
                layersState = currentLayersState,
                chromeColour = currentChromeColour,
            )
            fanCircleColour[0] = currentChromeColour
            // The data+camera refresh effect below re-pushes every source right after this, keyed
            // on loadedStyle among other things — including the sighting source, with "selected"
            // baked in from whatever focusedObservationId is current at that point. Nothing here
            // needs to seed it separately.
            // Item 4: again once the style has loaded, in case a style's own default camera replaced it,
            // and the region target recorded before loadedStyle wakes the data+camera effect below.
            cameraRestore?.let {
                cameraMoveClassifier.markAppMove()
                applyCameraRestore(map, it)
                lastAppliedCameraTarget = it.appliedTarget
            }
            appliedStyle = requested
            loadedStyle = style
            // setStyle discards the previous style's LocationComponent state the same way it does
            // this composable's own layers (see initializeOverlayLayers' own doc comment on why
            // that function re-runs here) — so the live-location "puck" needs the same
            // re-activate-on-every-new-style treatment. Guarded by trackLiveLocation — see that
            // parameter's own doc comment for why a historical-place map instance must never seize
            // the camera for the device's current location at all.
            if (trackLiveLocation) {
                cameraMoveClassifier.markAppMove() // activating (or re-activating, after a style swap) sets the camera mode and may ease the zoom
                activateLiveLocationIfPermitted(map, style, context, restoreCameraMode = cameraRestore?.cameraMode ?: previousCameraMode)
            }
        }
    }

    // Data + camera refresh. Runs on every relevant prop change *and* whenever a new style just
    // finished loading (loadedStyle changing is what makes this re-populate a freshly blank style
    // after a basemap swap) — the same "rebuild content every update, regardless of why the update
    // fired" behaviour the deleted osmdroid version had in its single `update` block, split here
    // because MapLibre's own API separates "style ready" from "camera/property changed".
    LaunchedEffect(
        loadedStyle, region, sightings, plannedTrips, focusOverride, breadcrumbPoints, waypoints, focusedObservationId,
        keptTrackPolylines, findMarkers, photoMarkers, offlineRegionCircles, showSearchCentre, cameraRequest, journalHighlights,
    ) {
        val style = loadedStyle ?: return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        refreshOverlayData(
            style, region, sightings, plannedTrips, breadcrumbPoints, waypoints, focusedObservationId,
            keptTrackPolylines, findMarkers, photoMarkers, offlineRegionCircles, showSearchCentre, journalHighlights,
        )

        // Once the live-location "puck" is actively tracking (the default once permission is
        // granted — see activateLiveLocationIfPermitted), it owns the camera continuously, on its
        // own internal update loop, independent of recomposition. Jumping the camera to the search
        // region here too — on every sightings/breadcrumb update this effect already keys on,
        // which includes roughly every 15s while a track is recording — would fight it, snapping the
        // view back to the search center out from under a walker watching their live position. Once
        // a pan/zoom/the CameraMode.NONE break in activateLiveLocationIfPermitted's own doc comment
        // drops tracking, this resumes controlling the camera exactly as it did before that existed.
        val isGpsTracking = map.locationComponent.isLocationComponentActivated &&
            map.locationComponent.cameraMode != CameraMode.NONE
        val target = region to focusOverride
        // A one-shot frame (the entry map's opening frame) stands in for this target's move: the
        // target is recorded as applied too, so the region move below does not follow it and undo it.
        // A later target change (a locate-me pan) still moves the camera, as before.
        val frameWanted = cameraRequest != null && shouldApplyCameraRequest(isGpsTracking, cameraRequest, lastAppliedCameraRequestId)
        if (frameWanted) cameraMoveClassifier.markAppMove()
        val frameApplied = frameWanted && applyCameraFrame(map, cameraRequest!!.frame, context.resources.displayMetrics.density)
        if (frameApplied) {
            lastAppliedCameraRequestId = cameraRequest?.id
            lastAppliedCameraTarget = target
        } else if (shouldMoveCameraToTarget(isGpsTracking, target, lastAppliedCameraTarget)) {
            val center = MapLibreLatLng(region.lat, region.lng)
            // focusOverride pans the camera without moving the search-location marker or the
            // zoom-from-radius heuristic below, both of which stay anchored to region — see
            // MapSlot's doc comment on this parameter for why the two are kept independent.
            val cameraTarget = focusOverride?.let { MapLibreLatLng(it.lat, it.lng) } ?: center
            cameraMoveClassifier.markAppMove()
            map.cameraPosition = CameraPosition.Builder()
                .target(cameraTarget)
                .zoom(zoomForRadiusKm(region.radiusKm))
                .build()
            lastAppliedCameraTarget = target
        }
    }

    // Layer visibility and opacity (map layers L0a, A3): each native layer's `visibility` layout
    // property and its opacity paint properties set from layersState through layerPaintFor, on the
    // loaded style's own layers — no setStyle, so nothing is rebuilt. Keyed on loadedStyle as well,
    // so a freshly loaded style gets the current state; initializeOverlayLayers has already built
    // each layer with it, so for that case this re-sets the same values.
    // A fanned stack folds only when what the map draws changes a member (a record gone or moved, its layer switched off); a change
    // that leaves its members alone keeps it (intent 2026-09-28-274, "Fold only if members change"), and so does a replaced style
    // (dispatch 2026-09-28-279): the effects below draw the fan's layers, frame, circle colour and fade again on the new one.
    // Not when a bubble opens (focusedObservationId, focusedFeature): a bubble opening does not itself change what the map draws. A tap on a fanned icon folds the fan in the tap handler.
    LaunchedEffect(loadedStyle, sightings, plannedTrips, waypoints, findMarkers, photoMarkers, drawnLayersState, journalHighlights) {
        fanReopen.onContentEffect(loadedStyle != null, loadedStyle)
    }

    // Draws the fan: pushes the copies and their legs at every step of its progress (fanFrameCollections), and hides the
    // originals of the fanned markers while it is up, but not before the renderer reports the copies drawn: hiding first
    // left a frame or two with neither on screen, the blink at the open's start (dispatch 2026-09-28-369, amendment -371).
    // The order is FanOutHideGate's; the signal is FanOutRenderSignal's. Device-only: see FanOutLayers.kt.
    // The gate outlives a restart of this effect (it is keyed on focusedObservationId too, so tapping a fanned sighting restarts it with
    // the fan up) and is new only with a new style, whose layers start unfiltered: a restart then asks it again for the same members and
    // changes nothing, and the originals stay hidden (the planner's review of a5185a2f).
    val fanHideGate = remember(loadedStyle) { FanOutHideGate() }
    LaunchedEffect(loadedStyle, mapLibreMap, focusedObservationId) {
        val style = loadedStyle ?: return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        val density = context.resources.displayMetrics.density
        val effectScope = this
        val gate = fanHideGate
        var waiting: Job? = null
        var clearing: Job? = null
        var hiddenFor: List<FanMember>? = null
        // True to start with, so a restart that finds the fan folding drops a wait that was still pending (gate.onFold) on its first pass.
        var wasOpen = true
        snapshotFlow { Triple(fanOut.members, fanOut.progress, fanOut.wantOpen) }.collect { (members, progress, wantOpen) ->
            if (hiddenFor !== members) {
                hiddenFor = members
                waiting?.cancel()
                clearing?.cancel()
                val step = gate.onMembers(members, spread = progress > 0f)
                applyFanOutHiding(style, step.hide)
                if (step.reveal.isNotEmpty()) {
                    // A release: the originals are shown again, but the copies stay (they stand on them at progress 0) until the
                    // renderer reports the originals drawn, so the stack is never empty for a frame at the fold's end.
                    clearing = effectScope.launch {
                        clearCopiesWhenOriginalsDrawn(map, mapView, step.reveal) {
                            pushFanFrame(style, fanFrameCollections(emptyList(), { fanMemberLatLng(map, it, 0f, density) }, focusedObservationId, 0f, drawOrder = orderedLayers(MAP_LAYER_REGISTRY, currentLayersState)))
                        }
                    }
                }
                if (step.awaiting) {
                    waiting = effectScope.launch {
                        hideWhenCopiesDrawn(style, map, mapView, gate, members, step.generation) { fanOut.progress >= 1f }
                    }
                }
            }
            if (wasOpen && !wantOpen) {
                // Folding: a hide still waiting is dropped, so the originals stay shown until the release shows everything.
                gate.onFold()
                waiting?.cancel()
            }
            wasOpen = wantOpen
            if (members.isEmpty() && clearing?.isActive == true) return@collect // the copies stay until the originals are drawn
            pushFanFrame(
                style,
                fanFrameCollections(
                    members, { fanMemberLatLng(map, it, progress, density) }, focusedObservationId, progress,
                    drawOrder = orderedLayers(MAP_LAYER_REGISTRY, currentLayersState),
                ),
            )
        }
    }

    // Item 8: the open fan's member keys, written where "Open in Journal" reads them (a closed fan writes an empty list).
    // A map that is left (a tab switch) writes nothing on the way out, so the last list survives until the tap that read it.
    LaunchedEffect(returnMemory) {
        val memory = returnMemory ?: return@LaunchedEffect
        snapshotFlow { if (fanOut.isOpen) fanOut.members.map { it.key } else emptyList() }.collect { keys -> memory.openFanKeys = keys }
    }

    // Item 2 (dispatch 2026-09-28-265): while a fan is open the marker icons outside it draw at 80% of
    // what they drew. The one writer of these opacities, so the fade composes with the Layers sheet's
    // state (fanFadedPaint multiplies the resolved paint) and the restore on folding is the state's own
    // value. Device-only: see FanClarity.kt.
    LaunchedEffect(loadedStyle, drawnLayersState) {
        val style = loadedStyle ?: return@LaunchedEffect
        snapshotFlow { fanOut.isOpen }.collect { fanOpen ->
            MAP_LAYER_REGISTRY.forEach { spec -> applyLayerPaint(style, fanFadedPaint(spec, layerPaintFor(spec, drawnLayersState), fanOpen)) }
        }
    }

    // Item 3: the circles' colour is the map chrome's, which follows the app's theme, so a theme change
    // recolours them on the loaded style (the layer was built with the colour current at style load).
    LaunchedEffect(loadedStyle, chromeColour) {
        val style = loadedStyle ?: return@LaunchedEffect
        if (fanCircleNeedsRecolour(fanCircleColour[0], chromeColour)) {
            applyFanCircleStyle(style, chromeColour, context.resources.displayMetrics.density)
            fanCircleColour[0] = chromeColour
        }
    }

    // Colour-field cells (map layers L0b, B5): each time the camera goes idle, and after every style
    // load (a basemap change and a night-mode change both reload the style, which drops every source,
    // and initializeOverlayLayers re-adds the cell sources empty), the store is asked for the blocks
    // touching the visible area, per colour field the feed names, for the feed's week. That is the same
    // path a downloaded store will use. Below MIN_FORECAST_ZOOM, or past the MAX_FORECAST_BLOCKS
    // backstop, nothing is requested and each field draws empty (planner's ruling on Q9). A field the
    // store has no data for draws empty too. What the drawn cells say about their dates goes back to
    // the host for the legend (MapForecastFeed.onCellsShown). Keyed on the feed's store, week and
    // groups rather than the feed itself, whose callback is a new lambda on every recomposition.
    LaunchedEffect(loadedStyle, forecast?.store, forecast?.week, forecast?.groupsByLayer, cameraIdleCount) {
        val style = loadedStyle ?: return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        val feed = forecast
        val blocks = feed?.takeIf { it.groupsByLayer.isNotEmpty() }?.let {
            val zoom = map.cameraPosition.zoom
            val bounds = map.projection.visibleRegion.latLngBounds
            forecastBlocksToRequest(zoom, bounds.latitudeSouth, bounds.longitudeWest, bounds.latitudeNorth, bounds.longitudeEast)
                .also { requested ->
                    if (requested == null && zoom >= MIN_FORECAST_ZOOM) {
                        Log.w(SIGHTINGS_MAP_TAG, "The view touches more than $MAX_FORECAST_BLOCKS forecast blocks; no cells requested.")
                    }
                }
        }
        val shown = mutableMapOf<String, ForecastCellsShown>()
        COLOUR_FIELDS.forEach { field ->
            val group = feed?.groupsByLayer?.get(field.layerId)
            val cells = if (feed == null || group == null || blocks == null) {
                emptyList()
            } else {
                when (val result = feed.store.cells(group, feed.week, blocks)) {
                    ForecastCellsResult.NoForecastData -> emptyList()
                    is ForecastCellsResult.Cells -> {
                        if (result.rejectedCount > 0) {
                            Log.w(SIGHTINGS_MAP_TAG, "${result.rejectedCount} forecast feature(s) for $group were rejected and are not drawn.")
                        }
                        result.cells
                    }
                }
            }
            val source = style.getSourceAs<GeoJsonSource>(field.sourceId)
            if (source == null) {
                Log.w(SIGHTINGS_MAP_TAG, "The ${field.sourceId} source is not in the loaded style; its cells were not drawn.")
            } else {
                source.setGeoJson(forecastCellsFeatureCollection(cells))
            }
            forecastCellsShownOf(cells)?.let { shown[field.layerId] = it }
        }
        currentForecast?.onCellsShown?.invoke(shown)
    }

    // Re-engages GPS camera tracking on demand — the map redesign's GPS/locate-me icon, tapped
    // either for its first activation or to resume tracking after a manual pan/zoom broke it (see
    // activateLiveLocationIfPermitted's own doc comment on CameraMode.NONE). Also the natural retry
    // point if the very first tap only triggered the OS permission dialog: MapOverlayContent's own
    // doc comment on resumeTrackingRequestId covers why a second tap is what completes activation
    // in that case, not an automatic one.
    LaunchedEffect(resumeTrackingRequestId) {
        if (!trackLiveLocation) return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        val style = loadedStyle ?: return@LaunchedEffect
        // The user pressed locate: the move that follows is theirs, so it is marked before the mode changes and closes an open fan as before.
        cameraMoveClassifier.markAppMove()
        if (map.locationComponent.isLocationComponentActivated) {
            map.locationComponent.cameraMode = CameraMode.TRACKING
        } else {
            activateLiveLocationIfPermitted(map, style, context)
        }
    }

    // Part 1 layout fixes, item 5 (Part 1's device check, flag 3; planner message 2026-09-28-98): MapLibre's
    // attribution button ("i") keeps from the bottom what the always-visible caption keeps
    // (bottomInset: the nav's measured height, which includes the system navigation bar, and in
    // fullscreen the true edge, the caption's own treatment), and from its end edge the inset the host
    // hands in (the overlaid rail in a short landscape window at the rotation that puts it on that
    // edge). Without them it sat under the system navigation bar and the nav in portrait, and under
    // the rail and the system bar at 90. What MapLibre draws is device-only: this map cannot run
    // under Robolectric.
    val layoutDirection = LocalLayoutDirection.current
    val attributionBottomPx = with(LocalDensity.current) { (attributionBottomInset ?: bottomInset).roundToPx() }
    // Item 1 (dispatch 2026-09-29-57, amendment -262, "Move the 'i'"): moved inboard of the landscape L, which the host
    // hands over as its measured bounds, when the two would intersect. The map's own size is tracked here for it.
    var mapSizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val attributionEndPx = with(LocalDensity.current) {
        val defaults = attributionDefaultMargins
        val endInsetPx = attributionEndInset.roundToPx()
        if (defaults == null) {
            endInsetPx
        } else {
            attributionEndInsetClearOf(
                keepClear = attributionKeepClear,
                mapWidthPx = mapSizePx.width,
                mapHeightPx = mapSizePx.height,
                defaults = defaults,
                bottomInsetPx = attributionBottomPx,
                endInsetPx = endInsetPx,
                isRtl = layoutDirection == LayoutDirection.Rtl,
                buttonPx = ATTRIBUTION_BUTTON_DP.dp.roundToPx(),
                gapPx = ATTRIBUTION_CLEAR_GAP_DP.dp.roundToPx(),
            )
        }
    }
    LaunchedEffect(mapLibreMap, attributionDefaultMargins, attributionBottomPx, attributionEndPx, layoutDirection) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val defaults = attributionDefaultMargins ?: return@LaunchedEffect
        val (left, top, right, bottom) = attributionMarginsPx(defaults, attributionBottomPx, attributionEndPx, isRtl = layoutDirection == LayoutDirection.Rtl)
        map.uiSettings.setAttributionMargins(left, top, right, bottom)
    }

    // The map icon bar's orientation-reset control — MapLibre's own native compass view is
    // disabled above (see the DisposableEffect(mapView) block's own comment), so this is the only
    // way to straighten the map back to north once a rotate gesture has turned it. easeCamera, not
    // an instant jump, matching this map's other camera moves; bearing only, not target or zoom.
    LaunchedEffect(resetOrientationRequestId) {
        val map = mapLibreMap ?: return@LaunchedEffect
        cameraMoveClassifier.markAppMove()
        map.easeCamera(CameraUpdateFactory.bearingTo(0.0))
    }

    // The fan's clock, and Back closing it before anything else Back would close.
    MarkerFanOutHost(fanOut)
    MarkerFanOutBackHandler(fanOut, bubbleOpen = focusedObservationId != null || focusedFeature != null, backEnabled = backEnabled)

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                // Gesture ownership: a drag that starts on the map pans the map, even when an
                // ancestor is scrolling. Without this, the offline-region picker
                // (OfflineMapsPanel's Column has verticalScroll) could take the drag and scroll
                // the panel instead, which made the pin hard to place — the owner's report.
                //
                // Why this is needed even though MapLibre already asks: MapView's own
                // initialisation calls requestDisallowInterceptTouchEvent(true) exactly once
                // (read from the 13.5.0 artifact's bytecode, in the same block as
                // setClickable/setLongClickable/setFocusable). That call cannot do the job here
                // for two independent reasons — the MapView is constructed inside `remember`
                // above, before AndroidView attaches it, so it has no parent to propagate the
                // request to; and ViewGroup.dispatchTouchEvent clears FLAG_DISALLOW_INTERCEPT on
                // every ACTION_DOWN, so a one-shot request at construction is gone by the first
                // touch regardless. Re-asserting it per gesture is what actually holds.
                //
                // Compose honours it: AndroidViewHolder overrides
                // requestDisallowInterceptTouchEvent and forwards it to PointerInteropFilter
                // (both read from the compose-ui 1.12.0 artifact), which then dispatches to the
                // view on the Initial pass and consumes — so the scrolling ancestor never sees
                // the change. Compose clears the flag again when the gesture ends, so this does
                // not latch.
                //
                // Returns false: MapLibre's own onTouchEvent still runs exactly as before, so
                // pan, pinch-zoom, double-tap-zoom and rotate are untouched. Safe to attach —
                // MapView never sets an OnTouchListener on itself (also checked in the artifact),
                // so nothing is being clobbered.
                mapView.setOnTouchListener { view, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    false
                }
                mapView
            },
            // Load-bearing intent carried over from osmdroid, re-reasoned rather than re-verified
            // for MapLibre — see this composable's own doc comment, "Content does not paint outside
            // this composable's slot".
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                // After the view's own layout has taken the new size (the post), so the projection is the new
                // one; a no-op while no map is ready or nothing is focused.
                .trackMapFanSpace(fanSpace)
                .onSizeChanged { mapSizePx = it }
                .onViewportResized { mapView.post { mapLibreMap?.let(::reanchorFocusedBubble) } },
        )
        // The always-visible attribution line CopyrightOverlay used to draw directly onto the
        // osmdroid MapView. MapLibre has its own tap-to-reveal attribution control
        // (UiSettings.isAttributionEnabled, on by default) built from each style source's own
        // "attribution" field (see styleJsonFor), but that is deliberately not relied on alone here
        // — see Basemap's doc comment on [Basemap.attribution] for why an always-drawn guarantee
        // matters for this app's USGS/ODbL credit and shouldn't quietly become tap-only.
        // A list of credits since map layers L0a (A5): the basemap's, then each visible layer's own
        // (none of today's layers has one, so the text is exactly what it always was).
        Text(
            text = attributionCaption(mapCreditsFor(basemap, useOfflineTiles, activeLayerCredits(MAP_LAYER_REGISTRY, drawnLayersState), nightMode)),
            style = MaterialTheme.typography.labelSmall,
            color = ComposeColor.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = bottomInset)
                .background(ComposeColor.Black.copy(alpha = 0.55f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Adds every overlay source and layer this composable draws, all starting with empty
 * `FeatureCollection`s — called once per loaded [Style], from [SightingsMap]'s basemap-swap effect.
 *
 * Split from [refreshOverlayData] (which pushes the real data) because `setStyle` throws away the
 * previous style's sources and layers wholesale: a basemap swap needs both — the shape rebuilt here,
 * the content pushed there — while a plain data change (a new search, a new planned trip) only ever
 * needs the second.
 *
 * **Layer add order is the draw order (later added draws on top), and since map layers L0a that
 * order is the registry's**: this walks [drawOrder] (`orderedLayers` of `MAP_LAYER_REGISTRY`) and adds
 * one native layer per spec, each source the first time a layer needs it. Nothing here orders
 * anything by hand; to move a layer, move it in the registry, whose doc comment has the groups
 * (colour fields < areas < lines < markers) and the reasons for the order within each. The comment
 * this replaced ("search centre, sightings, planned trips last", from the osmdroid overlay list) was
 * stale before L0a: it predated the breadcrumb, the kept tracks and the offline circles, which were
 * all inserted between those. One visible change came with the registry and was accepted by the
 * owner: the search centre and the sighting dots now draw above the track lines.
 *
 * Each layer is built with today's paint and layout ([nativeLayerFor]), then given its visibility
 * and opacities from [layersState] ([layerPaintFor]) before it is added, so a layer's opacity is
 * written in one place, the registry's base opacities. At the default state that is exactly the
 * paint it always had.
 *
 * [palette] is the Night Maps palette ([MapPalette.forMode], colour build C2), so every colour here
 * follows the toggle. The point markers other than the sighting dot are bitmap [SymbolLayer]s, each
 * its own silhouette (`MarkerGlyphs.kt`, [MarkerIcon]) with its casing drawn into the bitmap and its
 * anchor at the bitmap's centre, hence `icon-anchor: center` for all of them. The sighting dot stays
 * a [CircleLayer] whose own ring is its casing; the tracks, the offline outline and its night border
 * are line layers ([trackLayerSpecs], [offlineRegionOutlineSpec], [offlineRegionBorderSpec]).
 *
 * The sighting layer's `circle-stroke-color`/`circle-stroke-width` are fixed expressions keyed on
 * each feature's own `"selected"` boolean property — see [sightingStrokeColorExpression]'s own doc
 * comment for why that property, not a paint-property expression comparing `observationId`
 * directly, is what actually selects the ring. Nothing here needs to know which sighting is
 * currently focused: [refreshOverlayData] bakes `"selected"` into the pushed data itself.
 *
 * Runs after the offline night recolour in the `setStyle` callback, never before: that recolour
 * walks every layer present, and the overlays must not be among them.
 */
private fun initializeOverlayLayers(
    style: Style,
    density: Float,
    palette: MapPalette,
    drawOrder: List<MapLayerSpec>,
    layersState: MapLayersState,
    chromeColour: Int,
) {
    // Every bitmap marker's image, in this palette's colours. Registered here and nowhere else.
    MarkerIcon.entries.forEach { style.addImage(it.imageId, markerIconImage(it, palette, density).bitmap) }

    val addedSources = mutableSetOf<String>()
    for (spec in drawOrder) {
        val layer = nativeLayerFor(spec, palette)
        if (layer == null) {
            // A registry entry this function cannot build is a programming error, never a silent
            // gap: logged by id, and the rest of the overlays still draw.
            Log.w(SIGHTINGS_MAP_TAG, "No native layer is defined for registry layer ${spec.id}; it is not drawn.")
            continue
        }
        if (addedSources.add(spec.sourceId)) style.addSource(GeoJsonSource(spec.sourceId, emptyFeatureCollection()))
        layer.setProperties(*paintProperties(layerPaintFor(spec, layersState)))
        style.addLayer(layer)
    }
    // The marker fan-out's own layers, above every registry layer (FanOutLayers.kt).
    addFanOutLayers(style, palette, chromeColour, density)
}

/**
 * The native layer for [spec], with today's paint and layout and no opacity (that comes from the
 * registry, [paintProperties]); `null` for a registry id this file has no builder for.
 */
private fun nativeLayerFor(spec: MapLayerSpec, palette: MapPalette): Layer? {
    markerIconForLayer(spec.id)?.let { return markerSymbolLayer(spec.id, spec.sourceId, it) }
    // A colour field (map layers L0b): a fill whose colour is its ramp on each cell's chance. Its
    // opacity is the registry's (a base of 0.6, owner's ruling on Q10), set with the rest of its paint.
    COLOUR_FIELDS.firstOrNull { it.layerId == spec.id }?.let { field ->
        return FillLayer(spec.id, spec.sourceId).withProperties(PropertyFactory.fillColor(colourFieldFillColour(field.ramp)))
    }
    lineSpecForLayer(spec.id)?.let { return lineLayerFor(it, palette) }
    return when (spec.id) {
        // Lowest of all (the areas group), so a coverage circle never covers a marker or a line.
        OFFLINE_REGION_CIRCLE_LAYER_ID -> FillLayer(spec.id, spec.sourceId).withProperties(
            PropertyFactory.fillColor(palette.offlineRegion),
        )
        // One shared layer for every observation, styled once — unlike osmdroid, which built one
        // Drawable and stamped it per Marker, MapLibre draws every feature in the source with the
        // same layer properties, so there is nothing per-sighting to construct here.
        SIGHTING_LAYER_ID -> CircleLayer(spec.id, spec.sourceId).withProperties(*sightingCircleProperties(palette))
        else -> null
    }
}

/** Holds the tap handler once the map is ready, for the listeners and effects that outlive one composition to reach it. */
private class TapHandlerRef {
    var handler: MapTapHandler? = null
}

/**
 * The sighting dot's own paint: colour, radius, and a ring keyed off each feature's `"selected"`
 * property (see [sightingStrokeColorExpression], [sightingStrokeWidthExpression]). Shared by the
 * sighting layer and the fan-out's copy of it, so a fanned dot is the dot it was.
 */
internal fun sightingCircleProperties(palette: MapPalette): Array<PropertyValue<*>> = arrayOf(
    PropertyFactory.circleColor(palette.sightingDot),
    PropertyFactory.circleRadius(SIGHTING_DOT_RADIUS_PX),
    // See sightingStrokeColorExpression's own doc comment — it keys off each feature's own
    // "selected" property, so nothing here needs seeding with the current
    // focusedObservationId the way an id-comparison expression would. The width keys off the
    // same property: the selected ring is 3dp, every other ring 1.5dp (colour build C2, an
    // owner-approved tweak; see sightingStrokeWidthExpression).
    PropertyFactory.circleStrokeColor(sightingStrokeColorExpression(palette)),
    PropertyFactory.circleStrokeWidth(sightingStrokeWidthExpression()),
)

/**
 * The bitmap marker a symbol layer draws, by layer id; `null` for a layer that is not a bitmap
 * marker. The search centre is a reticle since colour build C2 (d); planned trips are flags anchored
 * at the pole foot; waypoints pins anchored at the tip; finds a mushroom at its stem foot; photos a
 * rounded square with a camera, centred. `internal` so `MapLayerRegistryTest`'s palette check reads
 * what the map draws.
 */
internal fun markerIconForLayer(layerId: String): MarkerIcon? = when (layerId) {
    SEARCH_CENTER_LAYER_ID -> MarkerIcon.SEARCH_CENTRE
    PLANNED_TRIP_LAYER_ID -> MarkerIcon.PLANNED_TRIP
    WAYPOINT_LAYER_ID -> MarkerIcon.WAYPOINT
    FIND_LAYER_ID -> MarkerIcon.FIND
    PHOTO_LAYER_ID -> MarkerIcon.PHOTO
    // J8: the journal-entry halos under the three marker kinds.
    MapLayerIds.JOURNAL_ENTRY_WAYPOINTS -> MarkerIcon.WAYPOINT_JOURNAL_HALO
    MapLayerIds.JOURNAL_ENTRY_FINDS -> MarkerIcon.FIND_JOURNAL_HALO
    MapLayerIds.JOURNAL_ENTRY_PHOTOS -> MarkerIcon.PHOTO_JOURNAL_HALO
    else -> null
}

/**
 * The line a line layer draws, by layer id: the offline region's dashed outline (its casing, colour
 * build C2 (c)) and the night border beneath it ([offlineRegionBorderSpec]), and the breadcrumb and
 * kept tracks each with its casing ([trackLayerSpecs]: the breadcrumb dashed, see
 * [BREADCRUMB_DASH_PATTERN]; the kept tracks solid, see [keptTracksFeatureCollection]). `null` for a
 * layer that is not one of these.
 */
internal fun lineSpecForLayer(layerId: String): LineLayerSpec? =
    (listOf(offlineRegionBorderSpec(), offlineRegionOutlineSpec()) + trackLayerSpecs() + journalHaloLineSpecs()).singleOrNull { it.layerId == layerId }

/**
 * A bitmap marker's [SymbolLayer]. Every [MarkerIcon]'s image has its anchor at its exact centre
 * ([drawGlyph]), so `icon-anchor: center` puts the pin tip, stem foot, pole foot or centre on the
 * feature's coordinate, with no `icon-offset`.
 */
private fun markerSymbolLayer(layerId: String, sourceId: String, icon: MarkerIcon): SymbolLayer =
    SymbolLayer(layerId, sourceId).withProperties(
        PropertyFactory.iconImage(icon.imageId),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
    )

/**
 * [paint] as MapLibre property values: `visibility` (a layout property) and each opacity paint
 * property. Every [OpacityProperty] maps to its own `PropertyFactory` setter, each checked with
 * `javap` against the pinned `13.5.0` artifact (`visibility(String)`, `fillOpacity(Float)`,
 * `lineOpacity(Float)`, `circleOpacity(Float)`, `circleStrokeOpacity(Float)`, `iconOpacity(Float)`).
 */
private fun paintProperties(paint: LayerPaint): Array<PropertyValue<*>> = buildList<PropertyValue<*>> {
    add(PropertyFactory.visibility(if (paint.visible) Property.VISIBLE else Property.NONE))
    paint.opacities.forEach { add(opacityPropertyValue(it)) }
}.toTypedArray()

private fun opacityPropertyValue(value: OpacityValue): PropertyValue<Float> = when (value.property) {
    OpacityProperty.FILL -> PropertyFactory.fillOpacity(value.value)
    OpacityProperty.LINE -> PropertyFactory.lineOpacity(value.value)
    OpacityProperty.CIRCLE -> PropertyFactory.circleOpacity(value.value)
    OpacityProperty.CIRCLE_STROKE -> PropertyFactory.circleStrokeOpacity(value.value)
    OpacityProperty.ICON -> PropertyFactory.iconOpacity(value.value)
}

/**
 * Sets [paint] on the loaded style's layer of that id, in place. A layer the style does not have
 * is logged, not skipped silently: every registry layer is added on each style load, so a missing
 * one means that add failed.
 */
private fun applyLayerPaint(style: Style, paint: LayerPaint) {
    val layer = style.getLayer(paint.layerId)
    if (layer == null) {
        Log.w(SIGHTINGS_MAP_TAG, "Layer ${paint.layerId} is not in the loaded style; its visibility and opacity were not set.")
        return
    }
    layer.setProperties(*paintProperties(paint))
}

/**
 * The [TapHit] for [feature], queried on [layerId]: a sighting's id is its `observationId` number
 * property, written in decimal; every other layer's is its [FEATURE_ID_PROPERTY] string property.
 * `null` when the feature has neither. A plain function over the pure GeoJSON [Feature], so a
 * headless test can round-trip a builder's feature through it.
 */
internal fun tapHitOf(layerId: String, feature: Feature): TapHit = TapHit(
    layerId = layerId,
    featureId = if (layerId == SIGHTING_LAYER_ID) {
        feature.getNumberProperty("observationId")?.toLong()?.toString()
    } else {
        feature.getStringProperty(FEATURE_ID_PROPERTY)
    },
)

/**
 * Pushes the real content into every source [initializeOverlayLayers] created, replacing whatever
 * was there before. Cheap and safe to call on every relevant prop change — `GeoJsonSource.setGeoJson`
 * updates in place; it does not touch the layers referencing the source, unlike `setStyle`.
 */
private fun refreshOverlayData(
    style: Style,
    region: Region,
    sightings: List<Sighting>,
    plannedTrips: List<PlannedTrip>,
    breadcrumbPoints: List<LatLng>,
    waypoints: List<Waypoint>,
    focusedObservationId: Long?,
    keptTrackPolylines: List<RecordPolyline>,
    findMarkers: List<RecordPoint>,
    photoMarkers: List<RecordPoint>,
    offlineRegionCircles: List<RecordRegion>,
    showSearchCentre: Boolean,
    journalHighlights: JournalEntryHighlights,
) {
    style.getSourceAs<GeoJsonSource>(SEARCH_CENTER_SOURCE_ID)?.setGeoJson(searchCentreOverlay(region, showSearchCentre))
    style.getSourceAs<GeoJsonSource>(SIGHTING_SOURCE_ID)?.setGeoJson(sightingsFeatureCollection(sightings, focusedObservationId))
    style.getSourceAs<GeoJsonSource>(PLANNED_TRIP_SOURCE_ID)?.setGeoJson(plannedTripsFeatureCollection(plannedTrips))
    style.getSourceAs<GeoJsonSource>(BREADCRUMB_SOURCE_ID)?.setGeoJson(breadcrumbFeatureCollection(breadcrumbPoints))
    style.getSourceAs<GeoJsonSource>(WAYPOINT_SOURCE_ID)?.setGeoJson(waypointsFeatureCollection(waypoints))
    style.getSourceAs<GeoJsonSource>(KEPT_TRACKS_SOURCE_ID)?.setGeoJson(keptTracksFeatureCollection(keptTrackPolylines))
    style.getSourceAs<GeoJsonSource>(FIND_SOURCE_ID)?.setGeoJson(pointsFeatureCollection(findMarkers))
    style.getSourceAs<GeoJsonSource>(PHOTO_SOURCE_ID)?.setGeoJson(pointsFeatureCollection(photoMarkers))
    style.getSourceAs<GeoJsonSource>(OFFLINE_REGION_CIRCLE_SOURCE_ID)?.setGeoJson(offlineRegionCirclesFeatureCollection(offlineRegionCircles))
    // J8: the halos under the shown entries' kept records.
    journalHighlightFeatureCollections(journalHighlights).forEach { (sourceId, collection) ->
        style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(collection)
    }
}

/**
 * The data+camera refresh effect's own decision of whether to move the camera to [target] —
 * extracted as a plain function, the same reason [locationIndicatorTrackingAnimationMultiplier]
 * below is one, so the actual defect (not just the surrounding native-object plumbing) is
 * unit-testable. Real hardware report this fixes: that effect is keyed on `loadedStyle` (needed so
 * [refreshOverlayData] above re-runs after a basemap swap blanks the style), but with no guard, it
 * also re-ran the camera move below whenever GPS tracking wasn't active — including on a basemap
 * or night-mode swap (a night toggle reloads the style on every basemap since colour build C2, over
 * Satellite for the marker palette alone) that changed neither `region` nor `focusOverride` — which read as "changing
 * map style brought the map back to my location" even though the GPS/locate-me icon is the only
 * control meant to do that. Comparing [target] against [lastAppliedCameraTarget] — what was
 * actually last applied, not merely that the effect ran again — is what tells "the search moved"
 * apart from "the style reloaded." [isGpsTracking] itself is left as a native-backed computation
 * at the call site (see that effect's own doc comment on why moving the camera while it's true
 * would fight the puck), not folded into this function, so this stays a pure comparison.
 */
/**
 * Whether a MapLibre camera-move-started [reason] is the user's touch — see
 * [MapRenderMode.onUserCameraGesture]. A plain function so the one constant it keys on is pinned
 * by a headless test rather than only by a device.
 */
internal fun isUserCameraGesture(reason: Int): Boolean =
    reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE

internal fun shouldMoveCameraToTarget(
    isGpsTracking: Boolean,
    target: Pair<Region, LatLng?>,
    lastAppliedCameraTarget: Pair<Region, LatLng?>?,
): Boolean = !isGpsTracking && target != lastAppliedCameraTarget

/**
 * Whether [request] moves the camera now: a request is applied once per id, and never while GPS
 * tracking owns the camera (the rule [shouldMoveCameraToTarget] follows).
 */
internal fun shouldApplyCameraRequest(
    isGpsTracking: Boolean,
    request: MapCameraRequest?,
    lastAppliedRequestId: String?,
): Boolean = !isGpsTracking && request != null && request.id != lastAppliedRequestId

/**
 * What a map restores from a [MapCameraSnapshot] (Part 1 layout fixes, item 4): the camera, the region
 * target taken as already applied, and the location component's camera mode.
 */
internal data class MapCameraRestore(
    val target: LatLng,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double,
    val appliedTarget: Pair<Region, LatLng?>?,
    /** A [CameraMode] constant: [CameraMode.TRACKING] if the snapshot was following, [CameraMode.NONE] if not. */
    val cameraMode: Int,
)

/**
 * The restore a map makes as it first loads a style, or `null` for none (Part 1 layout fixes, item 4):
 * [saved], the camera the user left, when there is one and the location component has no mode of its
 * own yet ([previousCameraMode] `null`, a new `MapView`). A style swap on a live map keeps its own
 * camera and mode, as before. Following restores [CameraMode.TRACKING] and panned away
 * [CameraMode.NONE]; either is non-null, so the first activation's zoom-in does not run.
 */
internal fun cameraRestoreFor(saved: MapCameraSnapshot?, previousCameraMode: Int?): MapCameraRestore? {
    if (saved == null || previousCameraMode != null) return null
    return MapCameraRestore(
        target = saved.target,
        zoom = saved.zoom,
        bearing = saved.bearing,
        tilt = saved.tilt,
        appliedTarget = saved.appliedTarget,
        cameraMode = if (saved.following) CameraMode.TRACKING else CameraMode.NONE,
    )
}

/** Moves [map]'s camera to [restore]'s, at once, as the region move does. */
private fun applyCameraRestore(map: MapLibreMap, restore: MapCameraRestore) {
    map.cameraPosition = CameraPosition.Builder()
        .target(MapLibreLatLng(restore.target.lat, restore.target.lng))
        .zoom(restore.zoom)
        .bearing(restore.bearing)
        .tilt(restore.tilt)
        .build()
}

/**
 * MapLibre's attribution margins, as `[left, top, right, bottom]` px (Part 1 layout fixes, item 5):
 * [defaults], MapLibre's own, with [bottomInsetPx] added at the bottom and [endInsetPx] at the end edge,
 * which is the right in a left-to-right layout and the left in a right-to-left one (the button's
 * gravity is `BOTTOM or END`).
 */
internal fun attributionMarginsPx(defaults: IntArray, bottomInsetPx: Int, endInsetPx: Int, isRtl: Boolean): IntArray {
    val (left, top, right, bottom) = defaults
    return if (isRtl) {
        intArrayOf(left + endInsetPx, top, right, bottom + bottomInsetPx)
    } else {
        intArrayOf(left, top, right + endInsetPx, bottom + bottomInsetPx)
    }
}

/** The zoom a fitted frame opens at: [fittedZoom], capped at [maxZoom]. */
internal fun cappedFrameZoom(fittedZoom: Double, maxZoom: Double): Double = minOf(fittedZoom, maxZoom)

/**
 * Moves [map]'s camera to [frame], at once rather than eased, as the region move does. A
 * [EntryMapFrame.Fit] goes through MapLibre's own `getCameraForLatLngBounds`, with the frame's
 * padding in px on every side, and its zoom capped by [cappedFrameZoom]. Whether that fit is right
 * at the preview's size and at the phone's density is device-only: the native call cannot run
 * headless. `false`, logged, when MapLibre gives no camera for the bounds (it is `@Nullable`) or
 * rejects them, so the caller falls back to the region move instead of leaving the camera unset.
 */
private fun applyCameraFrame(map: MapLibreMap, frame: EntryMapFrame, density: Float): Boolean {
    val position = when (frame) {
        is EntryMapFrame.SinglePoint -> CameraPosition.Builder()
            .target(MapLibreLatLng(frame.at.lat, frame.at.lng))
            .zoom(frame.zoom)
            .build()
        is EntryMapFrame.Fit -> {
            val bounds = try {
                LatLngBounds.from(frame.bounds.north, frame.bounds.east, frame.bounds.south, frame.bounds.west)
            } catch (e: IllegalArgumentException) {
                Log.w(SIGHTINGS_MAP_TAG, "Camera frame bounds ${frame.bounds} rejected; falling back to the region move.", e)
                return false
            }
            val paddingPx = (frame.paddingDp * density).roundToInt()
            val fitted = map.getCameraForLatLngBounds(bounds, intArrayOf(paddingPx, paddingPx, paddingPx, paddingPx))
            if (fitted == null) {
                Log.w(SIGHTINGS_MAP_TAG, "No camera for bounds ${frame.bounds}; falling back to the region move.")
                return false
            }
            CameraPosition.Builder(fitted).zoom(cappedFrameZoom(fitted.zoom, frame.maxZoom)).build()
        }
    }
    map.cameraPosition = position
    return true
}

/**
 * MapLibre's own puck-movement animation runs on a fixed internal base duration
 * ([org.maplibre.android.location.LocationComponentOptions.trackingAnimationDurationMultiplier]
 * scales it, rather than taking an absolute millisecond value) — verified against the pinned
 * `13.5.0` artifact with `javap -v` on the package-private `LocationComponentConstants` class,
 * since it isn't part of the public API surface: `TRANSITION_ANIMATION_DURATION_MS = 750L`. Not
 * guaranteed stable across SDK versions; re-verify the same way after any MapLibre bump.
 */
internal const val LOCATION_COMPONENT_BASE_ANIMATION_DURATION_MS = 750f

/**
 * docs/motion-spec.md §2 "User location": animate only on meaningful GPS change, avoid jitter.
 * [LocationComponentOptions.trackingAnimationDurationMultiplier] is the one knob MapLibre
 * exposes for how long the puck takes to glide to a new fix -- a multiplier on
 * [LOCATION_COMPONENT_BASE_ANIMATION_DURATION_MS], not an absolute millisecond value -- so this
 * scales [MotionTokens.LOCATION_INDICATOR_MOVE_DURATION_MS] against that base rather than
 * leaving the SDK default in place. A plain function, not inlined into
 * [activateLiveLocationIfPermitted], so the computed value is unit-testable without the native
 * MapLibre objects that function needs.
 */
internal fun locationIndicatorTrackingAnimationMultiplier(): Float =
    MotionTokens.LOCATION_INDICATOR_MOVE_DURATION_MS / LOCATION_COMPONENT_BASE_ANIMATION_DURATION_MS

/**
 * The options [activateLiveLocationIfPermitted] hands MapLibre for the puck. Its own function so the
 * options are unit-testable: `LocationComponentOptions` is a plain value class, unlike the
 * native-backed `Style` the activation itself needs.
 */
internal fun liveLocationComponentOptions(context: Context): LocationComponentOptions =
    LocationComponentOptions.builder(context)
        // Duration ratio, not an absolute value: see locationIndicatorTrackingAnimationMultiplier()
        // above — its base (750ms) came from javap-inspecting the pinned MapLibre artifact, not
        // public API or documentation, so it can silently go stale on a MapLibre version bump.
        .trackingAnimationDurationMultiplier(locationIndicatorTrackingAnimationMultiplier())
        // Below the fan's lowest layer, so an open fan draws over the puck (dispatch 2026-09-28-290).
        // The owner, on seeing the puck over an open fan: "When the icon expands over user location,
        // the user location overlaps it. Can the fan icons move on top of the user location instead
        // of beneath?" With no position given MapLibre adds the puck on top of the style, over the
        // fan. Only the puck's topmost layer is placed by this; the rest of its layers (accuracy
        // circle, background, shadow, and the bearing or foreground) are each added directly below
        // the one before, so the whole puck sits just under the fan, above every registry layer
        // (13.5.0, read with javap on LocationComponentPositionManager and
        // SymbolLocationLayerRenderer). The layer must already be in the style: see
        // addFanOutLayers, which initializeOverlayLayers runs before either activation call.
        .layerBelow(FanOutIds.LEGS_CASING_LAYER)
        .build()

/**
 * The options to apply to an already-initialised LocationComponent just before [liveLocationComponentOptions],
 * or `null` when nothing is needed first (dispatch 2026-09-28-318, fail 3).
 *
 * After a basemap swap the SDK has already put the puck's layers back, before this app's layers exist (see
 * `LiveLocationPuckSwapTest` for the chain, read from 13.5.0 with javap), so the puck sits under the markers and the fan.
 * Re-activating hands the SDK the options it already holds, and it moves nothing for those. Applying the same options with
 * no position first makes the next application a change, so the SDK takes the puck's layers out and adds them again
 * below [FanOutIds.LEGS_CASING_LAYER], which exists by then. Both happen in one call on the main thread, so no frame is
 * drawn between them. Rejected: moving the puck's layers by their own ids (those names are the SDK's, not public API),
 * and adding this app's layers through `Style.Builder` so they exist before the SDK re-places the puck (it restructures
 * working code). A component not yet initialised builds its layers where [options] say, so it needs nothing first.
 */
internal fun puckReplacementOptions(alreadyInitialised: Boolean, options: LocationComponentOptions): LocationComponentOptions? =
    if (alreadyInitialised) options.toBuilder().layerAbove(null).layerBelow(null).build() else null

/**
 * Turns on MapLibre's own "blue dot" location puck and has the camera follow it — "like regular
 * GPS," the project owner's own framing, rather than the compass strip's pre-existing one-shot
 * locate-me fetch (which still exists unchanged, feeding that strip's own text readout, not the
 * map's camera). A no-op, not a crash or a silent guess, when [Manifest.permission.ACCESS_FINE_LOCATION]/
 * [Manifest.permission.ACCESS_COARSE_LOCATION] aren't granted — same "explicit unsupported state,
 * never fabricated" rule [com.zynergylabs.forager.app.location.AndroidLocationProvider.hasLocationPermission]
 * already follows for the one-shot path; the map simply won't show a puck until permission exists
 * and something re-triggers this (a fresh style load, or the locate-me icon — see
 * [resumeTrackingRequestId][MapOverlayContent.resumeTrackingRequestId]'s own doc comment).
 *
 * [restoreCameraMode] is what this composable's own basemap-swap effect passes to avoid a real
 * hardware-reported bug: `setStyle` (any basemap change, or a night-mode toggle, which shares this
 * same path on every basemap, Satellite included since colour build C2) discards the LocationComponent outright, so this function has to run again on every
 * such swap just to keep the puck visible — but always re-forcing [CameraMode.TRACKING] here, as
 * this used to do, snapped the camera back onto the user's location on every basemap switch even
 * after they had deliberately panned away, which the GPS/locate-me icon is the control for, not
 * this one. `null` (the default, and what the locate-me icon's own re-activation call passes)
 * means "genuinely first activation" and still defaults to [CameraMode.TRACKING] plus the
 * zoom-in below; a non-null value restores exactly that mode instead, whatever it was.
 *
 * The zoom-in only fires on that genuine first activation ([restoreCameraMode] `== null`) — the
 * project owner's own ask ("when starting the maps, on any map mode, have it zoom in
 * automatically and center on user location") — not on every basemap-swap re-activation, where it
 * would just be the same unwanted recenter one step removed (this time snapping zoom rather than
 * position). Zoom only, not target: [CameraMode.TRACKING] already owns panning to the live fix,
 * so this doesn't fight it by also specifying a target.
 *
 * Breaking out of [CameraMode.TRACKING] again is built into MapLibre's
 * [LocationComponent][org.maplibre.android.location.LocationComponent] itself, not code this app
 * wrote: the SDK's own gesture detection drops to [CameraMode.NONE] the moment the user pans,
 * drags, or zooms, which is also what the data+camera refresh effect above checks to decide
 * whether it's safe to move the camera itself without fighting an active puck.
 */
@SuppressLint("MissingPermission") // hasLocationPermission() below is the real (runtime) check.
private fun activateLiveLocationIfPermitted(
    map: MapLibreMap,
    style: Style,
    context: Context,
    // @CameraMode.Mode is an IntDef (see org.maplibre.android.location.modes.CameraMode's own
    // javap output -- a final class of `int` constants, not a real Kotlin type), so this and
    // LocationComponent's own cameraMode property are both plain Int, not CameraMode.
    restoreCameraMode: Int? = null,
) {
    if (!hasLocationPermission(context)) return
    val locationComponent = map.locationComponent
    val options = liveLocationComponentOptions(context)
    puckReplacementOptions(locationComponent.isLocationComponentActivated, options)?.let { locationComponent.applyStyle(it) }
    locationComponent.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style)
            .locationComponentOptions(options)
            .useDefaultLocationEngine(true)
            .build(),
    )
    locationComponent.isLocationComponentEnabled = true
    // COMPASS, not NORMAL: the puck itself points the device's own heading, the same live sensor
    // the compass strip's heading text already reads — matching, not duplicating, that readout.
    locationComponent.renderMode = RenderMode.COMPASS
    locationComponent.cameraMode = restoreCameraMode ?: CameraMode.TRACKING
    if (restoreCameraMode == null) {
        map.easeCamera(CameraUpdateFactory.zoomTo(FIRST_ACTIVATION_ZOOM))
    }
}

/**
 * The zoom level a first-ever GPS activation eases to, once — a close, orienting view rather than
 * whatever the region-search zoom heuristic ([zoomForRadiusKm], topping out at 13.0) happened to
 * leave the camera at. 16.0 is the conventional "street level" a locate-me control zooms to
 * elsewhere (Google Maps, among others) — not derived from anything specific to this app.
 */
private const val FIRST_ACTIVATION_ZOOM = 16.0

/** Same check, same two permissions, as [com.zynergylabs.forager.app.location.AndroidLocationProvider.hasLocationPermission] — not shared code across an app/domain-layer boundary that owns neither Context nor Manifest. */
private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}

private fun emptyFeatureCollection(): FeatureCollection = FeatureCollection.fromFeatures(emptyList())

/**
 * The search-centre marker. title/snippet kept as properties — see this file's class doc comment on
 * the deferred tap-to-see popup.
 *
 * `internal`, not `private`: [GeoJsonSource]/[Style] are native-backed (verified with `javap` against
 * the pinned artifact — every constructor calls a `native` `initialize`), so they cannot be built in
 * a JVM unit test at all, on-device or Robolectric, without `UnsatisfiedLinkError`. This function and
 * its siblings below are the actual production code that decides *what* goes into those sources —
 * plain [org.maplibre.geojson] data classes, which carry no native methods (also checked with
 * `javap`) — so pulling the boundary here and testing up to it (`SightingsMapOverlayDataTest`) is the
 * same split [MapLibreOfflineMapRepository]'s own doc comment already draws for `OfflineRegion`.
 */
internal fun searchCenterFeatureCollection(region: Region): FeatureCollection {
    val feature = Feature.fromGeometry(Point.fromLngLat(region.lng, region.lat))
    feature.addStringProperty(FEATURE_ID_PROPERTY, SEARCH_CENTRE_FEATURE_ID)
    feature.addStringProperty("title", "Search location")
    feature.addStringProperty("snippet", "Radius: ${region.radiusKm} km")
    return FeatureCollection.fromFeature(feature)
}

/**
 * What the search-centre source actually receives: [searchCenterFeatureCollection] when
 * [showSearchCentre] is on, an empty collection when it is off — the source and layer stay in the
 * style either way, so a caller flipping the flag never triggers a style rebuild. See
 * [com.zynergylabs.forager.app.ui.map.MapRenderMode.showSearchCentre] for which callers turn it off and why.
 * Split out from [refreshOverlayData] so the decision is testable without a [Style]
 * (`SightingsMapOverlayDataTest`), the same boundary [searchCenterFeatureCollection]'s own doc
 * comment describes.
 */
internal fun searchCentreOverlay(region: Region, showSearchCentre: Boolean): FeatureCollection =
    if (showSearchCentre) searchCenterFeatureCollection(region) else emptyFeatureCollection()

/**
 * [focusedObservationId] bakes a `"selected"` boolean into whichever feature it names, `false` on
 * every other — see [sightingStrokeColorExpression]'s own doc comment for why the paint layer
 * reads this property instead of comparing `observationId` itself inside a GL expression.
 */
internal fun sightingsFeatureCollection(sightings: List<Sighting>, focusedObservationId: Long? = null): FeatureCollection {
    val features = sightings.map { sighting ->
        Feature.fromGeometry(Point.fromLngLat(sighting.lng, sighting.lat)).apply {
            addStringProperty("title", sighting.commonName ?: sighting.scientificName)
            addStringProperty("snippet", sighting.observedOn?.toString() ?: sighting.scientificName)
            // Round-trips through queryRenderedFeatures in the map click listener below, to look the
            // tapped feature back up in the current `sightings` list — the only property here that's
            // actually read back, rather than kept "for a future click handler" the way title/snippet
            // were before this.
            addNumberProperty("observationId", sighting.observationId)
            // Computed in Kotlin, once, per push — not read back by anything on this side, only by
            // sightingStrokeColorExpression's own GL expression.
            addBooleanProperty("selected", sighting.observationId == focusedObservationId)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

/**
 * The sighting layer's `circle-stroke-color`: [MapPalette.sightingDotStroke] (white by day) for
 * every dot, except [MapPalette.sightingDotStrokeSelected] (blue) for whichever one
 * [sightingsFeatureCollection]'s own `"selected"` property currently marks `true` — the ring
 * [ObservationBubble]'s own arrow points at, so the highlighted dot and the bubble naming it agree
 * even in a dense cluster where the arrow's own tip alone could land ambiguously close to a
 * neighbour.
 *
 * A fixed expression, not parameterized on `focusedObservationId`: an id-comparison
 * (`["==", ["get","observationId"], id]`, even coerced through `to-string` on both sides first)
 * was tried and confirmed — twice, on real hardware, alongside a doubled stroke width that also
 * showed no change — to never actually select anything once `setProperties` reaches the native
 * renderer, for reasons this project's own off-device tests can't diagnose (`Expression.equals`
 * only checks the built tree, never how MapLibre's GL engine evaluates a numeric or string `eq`).
 * Baking `"selected"` into each feature's own data in Kotlin, and reading it here as a plain
 * boolean condition (`["case", ["get","selected"], ...]`, no comparison at all), sidesteps the
 * entire class of doubt: there is no representation for a boolean read back from GeoJSON to
 * disagree with.
 *
 * A widened selected-stroke width (first 3.5px, then 4.5px, alongside a deeper blue) was tried as
 * a second signal on top of colour, on the reasoning that a hairline is hard to read by hue alone
 * at a glance — and reverted on request, twice, to keep the highlight to colour alone: see
 * [SIGHTING_DOT_STROKE_WIDTH_PX]'s own doc comment for that history. `circle-stroke-width` is a
 * flat constant for every dot now, selected or not; only this colour expression is data-driven.
 *
 * A plain function, not inlined into [initializeOverlayLayers]: both [Expression] and
 * [org.maplibre.android.style.layers.PropertyValue] carry no native methods (`javap` against the
 * pinned `org.maplibre.gl:android-sdk:13.5.0` artifact confirms neither), so unlike the
 * `Style`/`CircleLayer`/`GeoJsonSource` boundary [sightingsFeatureCollection]'s own doc comment
 * describes, the expression this builds is itself constructible and comparable
 * (`Expression.equals`) off a real device — `SightingsMapOverlayDataTest` exercises it directly.
 */
internal fun sightingStrokeColorExpression(palette: MapPalette): Expression =
    Expression.switchCase(
        Expression.get("selected"),
        Expression.color(palette.sightingDotStrokeSelected),
        Expression.color(palette.sightingDotStroke),
    )

/**
 * The sighting layer's `circle-stroke-width`: [SIGHTING_DOT_SELECTED_STROKE_WIDTH_PX] (3dp) for the
 * dot [sightingsFeatureCollection] marks `"selected"`, [SIGHTING_DOT_STROKE_WIDTH_PX] (1.5dp) for every
 * other. Keyed on the same boolean property as [sightingStrokeColorExpression], for the same reason.
 *
 * Colour build C2, an owner-approved tweak: the highlight is colour **and** width again. Widths of
 * 3.5 and 4.5px were tried before and reverted on request to keep the highlight to colour alone
 * ([SIGHTING_DOT_STROKE_WIDTH_PX] has that history); the owner approved 3dp on the glyph board.
 */
internal fun sightingStrokeWidthExpression(): Expression =
    Expression.switchCase(
        Expression.get("selected"),
        Expression.literal(SIGHTING_DOT_SELECTED_STROKE_WIDTH_PX),
        Expression.literal(SIGHTING_DOT_STROKE_WIDTH_PX),
    )

/**
 * One overlay [LineLayer], described without constructing it: [LineLayer] calls a native
 * initialiser from its constructor, so a headless test cannot build one, but it can read this
 * (`SightingsMapOverlayDataTest`). [lineLayerFor] is the only thing that turns one into a layer, so
 * what the test reads is what the map draws. Widths are MapLibre style units, which are dp.
 */
internal data class LineLayerSpec(
    val layerId: String,
    val sourceId: String,
    /** The palette role this line is drawn in. */
    val colour: (MapPalette) -> Int,
    val widthDp: Float,
    /** `line-dasharray`, in multiples of [widthDp]; `null` for a solid line. */
    val dashPattern: List<Float>?,
    /** Round caps and joins (the tracks); `false` leaves MapLibre's butt caps and miter joins. */
    val roundCaps: Boolean,
    /**
     * How this line's width follows the zoom, as fractions of [widthDp] (track widths by zoom, owner,
     * 2026-09-28), or `null` for a constant [widthDp] at every zoom (the offline outline).
     * [lineWidthExpression] is what turns it into the layer's `line-width`.
     */
    val widthByZoom: List<ZoomWidthStop>? = null,
)

/**
 * [spec]'s width stops as (zoom, width in dp) pairs, in zoom order, or `null` when its width is a
 * constant: each stop's fraction of [LineLayerSpec.widthDp].
 */
internal fun lineWidthStops(spec: LineLayerSpec): List<Pair<Float, Float>>? =
    spec.widthByZoom?.sortedBy { it.zoom }?.map { it.zoom to spec.widthDp * it.fractionOfFullWidth }

/**
 * [spec]'s width in dp at [zoom], evaluated as MapLibre's linear `interpolate` does: linear between
 * stops, the end values outside them. What [lineWidthExpression] asks the map to draw, in a form a
 * headless test can read at any zoom.
 */
internal fun lineWidthAtZoom(spec: LineLayerSpec, zoom: Float): Float {
    val stops = lineWidthStops(spec)?.takeIf { it.isNotEmpty() } ?: return spec.widthDp
    if (zoom <= stops.first().first) return stops.first().second
    if (zoom >= stops.last().first) return stops.last().second
    val upper = stops.indexOfFirst { it.first >= zoom }
    val (z0, w0) = stops[upper - 1]
    val (z1, w1) = stops[upper]
    return w0 + (w1 - w0) * (zoom - z0) / (z1 - z0)
}

/**
 * The `line-width` [lineLayerFor] gives [spec]'s layer: `interpolate(linear, zoom, …)` over
 * [lineWidthStops] when it has stops (track widths by zoom, owner, 2026-09-28), the constant
 * [LineLayerSpec.widthDp] otherwise.
 */
internal fun lineWidthExpression(spec: LineLayerSpec): Expression {
    val stops = lineWidthStops(spec)?.takeIf { it.isNotEmpty() } ?: return Expression.literal(spec.widthDp)
    return Expression.interpolate(
        Expression.linear(),
        Expression.zoom(),
        *stops.map { (zoom, width) -> Expression.stop(zoom, width) }.toTypedArray(),
    )
}

/**
 * The track lines, in draw order: each track's casing immediately before it, so it draws directly
 * below it (colour build C2 (c)). A casing is the track's own line, [CASING_WIDTH_DP] wider on each
 * side, in [MapPalette.casing], and always solid: under the dashed breadcrumb it still outlines the
 * whole trail, so the dashes read as one path against a busy ground.
 *
 * Each track and its casing thin out together as the map zooms out ([TRACK_WIDTH_ZOOM_STOPS], owner "2 A",
 * 2026-09-29): the casing copies its track's stops, so the widths above are the full widths, at zoom
 * 18 and above, and the casing keeps its ratio to its line (9 to 6) at every zoom. At zoom 12 and
 * below that is a 2.25 dp casing over a 1.5 dp line, 0.375 dp a side, not [CASING_WIDTH_DP].
 */
internal fun trackLayerSpecs(): List<LineLayerSpec> {
    fun casingFor(track: LineLayerSpec, layerId: String) = track.copy(
        layerId = layerId,
        colour = MapPalette::casing,
        widthDp = track.widthDp + 2 * CASING_WIDTH_DP,
        dashPattern = null,
    )
    val breadcrumb = LineLayerSpec(
        layerId = BREADCRUMB_LAYER_ID,
        sourceId = BREADCRUMB_SOURCE_ID,
        colour = MapPalette::breadcrumb,
        widthDp = BREADCRUMB_STROKE_WIDTH_PX,
        dashPattern = BREADCRUMB_DASH_PATTERN.toList(),
        roundCaps = true,
        widthByZoom = TRACK_WIDTH_ZOOM_STOPS,
    )
    val keptTrack = LineLayerSpec(
        layerId = KEPT_TRACKS_LAYER_ID,
        sourceId = KEPT_TRACKS_SOURCE_ID,
        colour = MapPalette::keptTrack,
        widthDp = KEPT_TRACK_STROKE_WIDTH_PX,
        dashPattern = null,
        roundCaps = true,
        widthByZoom = TRACK_WIDTH_ZOOM_STOPS,
    )
    return listOf(
        casingFor(breadcrumb, BREADCRUMB_CASING_LAYER_ID),
        breadcrumb,
        casingFor(keptTrack, KEPT_TRACKS_CASING_LAYER_ID),
        keptTrack,
    )
}

/**
 * The offline region's outline (colour build C2 (c)): a dashed line in [MapPalette.casing],
 * [OFFLINE_REGION_CIRCLE_OUTLINE_WIDTH_PX] wide, dash 6dp and gap 4dp, with butt ends. The glyph
 * board's dash pattern; the region's fill is its own role, [MapPalette.offlineRegion], at
 * the registry's 0.2 fill opacity (`MAP_LAYER_REGISTRY`).
 */
internal fun offlineRegionOutlineSpec(): LineLayerSpec = LineLayerSpec(
    layerId = OFFLINE_REGION_CIRCLE_OUTLINE_LAYER_ID,
    sourceId = OFFLINE_REGION_CIRCLE_SOURCE_ID,
    colour = MapPalette::casing,
    widthDp = OFFLINE_REGION_CIRCLE_OUTLINE_WIDTH_PX,
    dashPattern = listOf(
        OFFLINE_REGION_OUTLINE_DASH_DP / OFFLINE_REGION_CIRCLE_OUTLINE_WIDTH_PX,
        OFFLINE_REGION_OUTLINE_GAP_DP / OFFLINE_REGION_CIRCLE_OUTLINE_WIDTH_PX,
    ),
    roundCaps = false,
)

/**
 * The border under the offline region's dashed outline (dispatch `2026-09-28-79`; owner: "The outline
 * should have a white border", "Yes the night outline only"), built as [trackLayerSpecs] builds a
 * track's casing: the outline's own line, [CASING_WIDTH_DP] wider on each side (4.5 dp in all, the
 * dashes' 1.5 dp in its middle), solid, on the same source, with the outline's butt caps and constant
 * width, in [MapPalette.offlineRegionBorder]. Solid so that the edge carries through the dashes' gaps
 * too. At night that role is white, drawn at the registry's line opacity, so over the darkest night
 * ground, where the black dashes alone could barely be made out (Part 1, check 5 (b)), the edge reads as
 * a light band with the dashes along it; by day the role is fully transparent and the layer draws
 * nothing. The registry places it directly below the outline (`MAP_LAYER_REGISTRY`).
 */
internal fun offlineRegionBorderSpec(): LineLayerSpec {
    val outline = offlineRegionOutlineSpec()
    return outline.copy(
        layerId = MapLayerIds.OFFLINE_REGION_BORDER,
        colour = MapPalette::offlineRegionBorder,
        widthDp = outline.widthDp + 2 * CASING_WIDTH_DP,
        dashPattern = null,
    )
}

/**
 * J8: the two line halos, each [JOURNAL_HALO_WIDTH_DP] wider on each side than what it lies under, solid,
 * in [MapPalette.journalEntry]: the offline region's under its 1.5 dp dashed outline (so the halo reads
 * through the dashes' gaps too), and the kept tracks' under their casing, thinning with the zoom in step
 * with the track and its casing ([TRACK_WIDTH_ZOOM_STOPS]), so at every zoom the halo keeps its ratio to
 * the line. Round joins, so the ring stays smooth round a track's bends and the region's circle.
 */
internal fun journalHaloLineSpecs(): List<LineLayerSpec> {
    val outline = offlineRegionOutlineSpec()
    val keptTrack = trackLayerSpecs().single { it.layerId == KEPT_TRACKS_LAYER_ID }
    return listOf(
        LineLayerSpec(
            layerId = MapLayerIds.JOURNAL_ENTRY_REGIONS,
            sourceId = MapSourceIds.JOURNAL_ENTRY_REGIONS,
            colour = MapPalette::journalEntry,
            widthDp = outline.widthDp + 2 * JOURNAL_HALO_WIDTH_DP,
            dashPattern = null,
            roundCaps = true,
        ),
        LineLayerSpec(
            layerId = MapLayerIds.JOURNAL_ENTRY_TRACKS,
            sourceId = MapSourceIds.JOURNAL_ENTRY_TRACKS,
            colour = MapPalette::journalEntry,
            widthDp = keptTrack.widthDp + 2 * CASING_WIDTH_DP + 2 * JOURNAL_HALO_WIDTH_DP,
            dashPattern = null,
            roundCaps = true,
            widthByZoom = keptTrack.widthByZoom,
        ),
    )
}

/**
 * J8: what each halo source receives, by source id, from [highlights]: the same pure builders the
 * records' own sources use, so a halo feature carries its record's id and geometry exactly as the
 * record's does. A plain function so a headless test reads what the sources get.
 */
internal fun journalHighlightFeatureCollections(highlights: JournalEntryHighlights): Map<String, FeatureCollection> = mapOf(
    MapSourceIds.JOURNAL_ENTRY_REGIONS to offlineRegionCirclesFeatureCollection(highlights.offlineRegionCircles),
    MapSourceIds.JOURNAL_ENTRY_TRACKS to keptTracksFeatureCollection(highlights.trackPolylines),
    MapSourceIds.JOURNAL_ENTRY_WAYPOINTS to pointsFeatureCollection(highlights.waypointMarkers),
    MapSourceIds.JOURNAL_ENTRY_FINDS to pointsFeatureCollection(highlights.findMarkers),
    MapSourceIds.JOURNAL_ENTRY_PHOTOS to pointsFeatureCollection(highlights.photoMarkers),
)

/** The [LineLayer] [spec] describes, in [palette]'s colours. Its source must already exist when it is added. */
private fun lineLayerFor(spec: LineLayerSpec, palette: MapPalette): LineLayer {
    val properties = buildList {
        add(PropertyFactory.lineColor(spec.colour(palette)))
        add(PropertyFactory.lineWidth(lineWidthExpression(spec)))
        if (spec.roundCaps) {
            add(PropertyFactory.lineCap(Property.LINE_CAP_ROUND))
            add(PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND))
        }
        spec.dashPattern?.let { add(PropertyFactory.lineDasharray(it.toTypedArray())) }
    }
    return LineLayer(spec.layerId, spec.sourceId).withProperties(*properties.toTypedArray())
}

/**
 * The active track's recorded points as a single [LineString] feature, oldest first — an empty
 * [FeatureCollection] when [points] has fewer than two points (nothing recorded yet, or only the
 * first fix so far): a `LineString` needs at least two points.
 */
internal fun breadcrumbFeatureCollection(points: List<LatLng>): FeatureCollection {
    if (points.size < 2) return emptyFeatureCollection()
    val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.lng, it.lat) })
    val feature = Feature.fromGeometry(line).apply { addStringProperty(FEATURE_ID_PROPERTY, BREADCRUMB_FEATURE_ID) }
    return FeatureCollection.fromFeature(feature)
}

internal fun plannedTripsFeatureCollection(plannedTrips: List<PlannedTrip>): FeatureCollection {
    val features = plannedTrips.map { trip ->
        Feature.fromGeometry(Point.fromLngLat(trip.location.lng, trip.location.lat)).apply {
            addStringProperty(FEATURE_ID_PROPERTY, trip.id)
            addStringProperty("title", "Planned trip")
            addStringProperty("snippet", trip.date.toString())
        }
    }
    return FeatureCollection.fromFeatures(features)
}

/** Every saved [Waypoint] as a point feature carrying its own id and name, for the pin's [SymbolLayer]. */
internal fun waypointsFeatureCollection(waypoints: List<Waypoint>): FeatureCollection {
    val features = waypoints.map { waypoint ->
        Feature.fromGeometry(Point.fromLngLat(waypoint.lng, waypoint.lat)).apply {
            addStringProperty(FEATURE_ID_PROPERTY, waypoint.id)
            addStringProperty("title", waypoint.name)
            addStringProperty("snippet", waypoint.note)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

/**
 * A Cartography entry's kept tracks as one [LineString] feature per inner list — a genuine
 * `MultiLineString`, not [breadcrumbFeatureCollection]'s single connected line. Journal Stage 2d. An
 * inner list with fewer than two points contributes no feature at all (same "a LineString needs at
 * least two points" reasoning [breadcrumbFeatureCollection] already has) rather than a degenerate
 * one-point line; this is also what makes a kept track that resolved to zero/one usable point (data
 * already thin before this reaches here) draw nothing instead of erroring.
 */
internal fun keptTracksFeatureCollection(polylines: List<RecordPolyline>): FeatureCollection {
    val features = polylines
        .filter { it.points.size >= 2 }
        .map { track ->
            Feature.fromGeometry(LineString.fromLngLats(track.points.map { Point.fromLngLat(it.lng, it.lat) })).apply {
                addStringProperty(FEATURE_ID_PROPERTY, track.recordId)
            }
        }
    return FeatureCollection.fromFeatures(features)
}

/** Journal Stage 2d's find and photo pins, one point feature per record, carrying the record's id. */
internal fun pointsFeatureCollection(points: List<RecordPoint>): FeatureCollection =
    FeatureCollection.fromFeatures(
        points.map { point ->
            Feature.fromGeometry(Point.fromLngLat(point.at.lng, point.at.lat)).apply {
                addStringProperty(FEATURE_ID_PROPERTY, point.recordId)
            }
        },
    )

/**
 * A Cartography entry's kept offline regions as filled polygon features — Journal Stage 2d. Each
 * [Region] needs no fetch to resolve (its own snapshot already carries lat/lng/radius); the polygon
 * ring itself comes from [GeoDistance.circlePolygonPoints], the true-circle approximation built for
 * exactly this drawn-shape use (as opposed to [GeoDistance.boundingBox]'s tile-download rectangle).
 */
internal fun offlineRegionCirclesFeatureCollection(regions: List<RecordRegion>): FeatureCollection {
    val features = regions.map { kept ->
        val region = kept.region
        val ring = GeoDistance.circlePolygonPoints(LatLng(region.lat, region.lng), region.radiusKm)
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring.map { Point.fromLngLat(it.lng, it.lat) }))).apply {
            // Read back through the outline layer, which shares this source: the fill is not tappable.
            addStringProperty(FEATURE_ID_PROPERTY, kept.recordId)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

// Source/layer ids. Fixed strings rather than generated, since every one of them is referenced by
// name from at least two places (the registry and refreshOverlayData, or a layer referencing its
// source) and a typo needs to be a compile error, not a silently-missing layer. Since map layers
// L0a the strings live in the layers package (MapLayerIds, MapSourceIds), beside the registry that
// orders them; these names are kept as aliases so the rest of this file reads as it did.
private const val SEARCH_CENTER_SOURCE_ID = MapSourceIds.SEARCH_CENTRE
private const val SEARCH_CENTER_LAYER_ID = MapLayerIds.SEARCH_CENTRE
private const val SIGHTING_SOURCE_ID = MapSourceIds.SIGHTINGS
private const val SIGHTING_LAYER_ID = MapLayerIds.SIGHTINGS
private const val PLANNED_TRIP_SOURCE_ID = MapSourceIds.PLANNED_TRIPS
private const val PLANNED_TRIP_LAYER_ID = MapLayerIds.PLANNED_TRIPS
private const val BREADCRUMB_SOURCE_ID = MapSourceIds.BREADCRUMB
private const val BREADCRUMB_LAYER_ID = MapLayerIds.BREADCRUMB
private const val BREADCRUMB_CASING_LAYER_ID = MapLayerIds.BREADCRUMB_CASING
private const val WAYPOINT_SOURCE_ID = MapSourceIds.WAYPOINTS
private const val WAYPOINT_LAYER_ID = MapLayerIds.WAYPOINTS

// Journal Stage 2d.
private const val KEPT_TRACKS_SOURCE_ID = MapSourceIds.KEPT_TRACKS
private const val KEPT_TRACKS_LAYER_ID = MapLayerIds.KEPT_TRACKS
private const val KEPT_TRACKS_CASING_LAYER_ID = MapLayerIds.KEPT_TRACKS_CASING
private const val FIND_SOURCE_ID = MapSourceIds.FINDS
private const val FIND_LAYER_ID = MapLayerIds.FINDS
private const val PHOTO_SOURCE_ID = MapSourceIds.PHOTOS
private const val PHOTO_LAYER_ID = MapLayerIds.PHOTOS
private const val OFFLINE_REGION_CIRCLE_SOURCE_ID = MapSourceIds.OFFLINE_REGIONS
private const val OFFLINE_REGION_CIRCLE_LAYER_ID = MapLayerIds.OFFLINE_REGION_FILL
private const val OFFLINE_REGION_CIRCLE_OUTLINE_LAYER_ID = MapLayerIds.OFFLINE_REGION_OUTLINE

/**
 * The property every overlay feature but a sighting carries its own id in (map layers L0a, A4): a
 * record id for a find, photo, kept track, waypoint, planned trip or offline region, and a fixed id
 * for the two singletons ([SEARCH_CENTRE_FEATURE_ID], [BREADCRUMB_FEATURE_ID]). A string property,
 * not the GeoJSON feature id, for the reason the sighting's `observationId` is a property: a
 * property is what this file has seen round-trip through `queryRenderedFeatures` on hardware. A
 * sighting keeps its `observationId` and gets no second id.
 */
internal const val FEATURE_ID_PROPERTY = "featureId"

/** The search-centre marker's fixed feature id: there is only ever one. */
internal const val SEARCH_CENTRE_FEATURE_ID = "search-centre"

/** The breadcrumb trail's fixed feature id: there is only ever one active track. */
internal const val BREADCRUMB_FEATURE_ID = "breadcrumb"

// The overlay's colours come from ui/theme/MapPalette.kt, hand-authored per role in a day and a
// night variant and chosen by the Night Maps toggle (MapPalette.forMode) -- not derived from the app
// theme; that type's doc comment has why. Only non-colour geometry constants remain here.

/**
 * The casing every marker except the sighting dot is outlined in, on each side of its fill
 * (colour build C2 (c), the planner's call): the tracks' casing lines are this much wider on each
 * side, and the marker bitmaps (`MarkerGlyphs.kt`) draw it into their own padding.
 */
internal const val CASING_WIDTH_DP = 1.5f

// The dot's fill opacity (0.7, about the deleted osmdroid version's 0xB3 alpha) and its ring's
// (0.85) are the registry's base opacities since map layers L0a (MAP_LAYER_REGISTRY).
private const val SIGHTING_DOT_RADIUS_PX = 9f

// The stroke that keeps individual dots distinguishable within a dense cluster — see this file's
// class doc comment, "The overlay colours", for the hardware finding this fixes. A light, near-
// opaque stroke (not translucent like the fill, its 0.85 opacity now in the registry) so the
// boundary itself stays crisp regardless of how many dots overlap or what opacity the fill
// composites to underneath.
//
// The selected dot's ring used to widen this on top of recolouring — first to 3.5px, then to
// 4.5px alongside MapPalette.sightingDotStrokeSelected moving to a deeper blue — and both were
// reverted on request, leaving the highlight to colour alone. Colour build C2 widens it again, to
// SIGHTING_DOT_SELECTED_STROKE_WIDTH_PX, as an owner-approved tweak (sightingStrokeWidthExpression).
internal const val SIGHTING_DOT_STROKE_WIDTH_PX = 1.5f
internal const val SIGHTING_DOT_SELECTED_STROKE_WIDTH_PX = 3f


/**
 * The offline style's night: every background, fill and line colour property of the loaded style set
 * to its V1 night value ([offlineNightRecolourOf]). The SDK calls were checked with `javap` against
 * the pinned `13.5.0` artifact before this was written: `Style.getLayers`, `Layer.setProperties`,
 * the `BackgroundLayer`/`FillLayer`/`LineLayer` colour getters (each a `PropertyValue<String>`), the
 * `PropertyFactory` colour setters for `String` and `Expression`, `PropertyValue.isExpression`/
 * `getExpression`/`isValue`/`getValue`, and `Expression.toString`/`Expression.raw`.
 *
 * A property whose value is unset (the style leaves it to the default) is not touched. One the pure
 * function cannot read is left in its day colour and logged by layer and property, never dropped
 * silently (CLAUDE.md); a summary line says how many were recoloured and how many left. Which form
 * MapLibre hands the colours back in (an `rgba()` string, `["rgba", ...]` arrays inside expressions)
 * is unverified until a device run; a form this does not read shows up in these log lines.
 */
private fun applyOfflineNightRecolour(style: Style) {
    var recoloured = 0
    var left = 0
    for (layer in style.layers) {
        for ((property, value) in dayColourPropertiesOf(layer)) {
            val colourValue = when {
                value.isNull -> continue
                value.isExpression -> StyleColourValue.Expression(value.expression.toString())
                value.value is String -> StyleColourValue.Literal(value.value as String)
                else -> null
            }
            val outcome = if (colourValue == null) {
                NightRecolour.Left(
                    LayerColourProperty(layer.id, property, StyleColourValue.Literal(value.toString())),
                    "value of type ${value.value?.javaClass?.name} is neither a colour string nor an expression",
                )
            } else {
                offlineNightRecolourOf(LayerColourProperty(layer.id, property, colourValue))
            }
            when (outcome) {
                is NightRecolour.Recoloured -> try {
                    layer.setProperties(nightPropertyValue(property, outcome.night))
                    recoloured++
                } catch (e: RuntimeException) {
                    left++
                    Log.w(SIGHTINGS_MAP_TAG, "Offline night: could not set ${layer.id}/$property; left in its day colour.", e)
                }
                is NightRecolour.Left -> {
                    left++
                    Log.w(SIGHTINGS_MAP_TAG, "Offline night: ${layer.id}/$property left in its day colour: ${outcome.reason}")
                }
            }
        }
    }
    Log.i(SIGHTINGS_MAP_TAG, "Offline night: $recoloured colour properties recoloured, $left left in day colours.")
}

/** [layer]'s colour properties the offline night walks ([NIGHT_RECOLOURED_PROPERTIES]), with their day values. */
private fun dayColourPropertiesOf(layer: Layer): List<Pair<String, PropertyValue<*>>> = when (layer) {
    is BackgroundLayer -> listOf("background-color" to layer.backgroundColor)
    is FillLayer -> listOf("fill-color" to layer.fillColor, "fill-outline-color" to layer.fillOutlineColor)
    is LineLayer -> listOf("line-color" to layer.lineColor)
    else -> emptyList()
}

private fun nightPropertyValue(property: String, night: StyleColourValue): PropertyValue<*> = when (night) {
    is StyleColourValue.Literal -> when (property) {
        "background-color" -> PropertyFactory.backgroundColor(night.text)
        "fill-color" -> PropertyFactory.fillColor(night.text)
        "fill-outline-color" -> PropertyFactory.fillOutlineColor(night.text)
        "line-color" -> PropertyFactory.lineColor(night.text)
        else -> error("offline night does not walk $property")
    }
    is StyleColourValue.Expression -> {
        val expression = Expression.raw(night.json)
        when (property) {
            "background-color" -> PropertyFactory.backgroundColor(expression)
            "fill-color" -> PropertyFactory.fillColor(expression)
            "fill-outline-color" -> PropertyFactory.fillOutlineColor(expression)
            "line-color" -> PropertyFactory.lineColor(expression)
            else -> error("offline night does not walk $property")
        }
    }
}

private const val SIGHTINGS_MAP_TAG = "SightingsMap"
private const val FAN_OUT_RESTORE_TAG = "MarkerFanOut"
private const val BREADCRUMB_STROKE_WIDTH_PX = 6f


// Journal Stage 2d.
private const val KEPT_TRACK_STROKE_WIDTH_PX = 6f
private const val OFFLINE_REGION_CIRCLE_OUTLINE_WIDTH_PX = 1.5f

// The offline outline's dash and gap, in dp (colour build C2 (c), from the glyph board §1).
private const val OFFLINE_REGION_OUTLINE_DASH_DP = 6f
private const val OFFLINE_REGION_OUTLINE_GAP_DP = 4f

/**
 * A short dash with round line caps ([Property.LINE_CAP_ROUND], already set on the breadcrumb
 * layer) renders as a trail of small dots — "breadcrumbs" should look like breadcrumbs, the
 * project owner's own reasoning. `4:10` (a short mark, a gap two and a half times longer) is
 * tuned for that dot read, in the same line-width-relative units [PropertyFactory.lineDasharray]
 * always took (see [BREADCRUMB_STROKE_WIDTH_PX]).
 *
 * `internal`: this is the actual array [PropertyFactory.lineDasharray] receives in production, not
 * a copy. `SightingsMapOverlayDataTest` asserts it is non-empty — the closest a headless JVM test
 * can get to "the breadcrumb trail is still dashed" (see [searchCenterFeatureCollection]'s doc
 * comment for why nothing here can construct the real, native-backed [LineLayer] that actually
 * renders it). Whether MapLibre draws it as visibly dot-like on a real basemap is a hardware-only
 * question, not yet re-confirmed.
 */
internal val BREADCRUMB_DASH_PATTERN = arrayOf(0.4f, 1.0f)

/**
 * A visual-only heuristic mapping search radius to a legible starting zoom level, not a domain
 * prediction. Unchanged from the deleted osmdroid version — see that history for the full pixel-math
 * reasoning; it does not depend on which renderer draws the result. `internal` so
 * `SightingsMapOverlayDataTest` can assert it directly instead of duplicating the thresholds.
 */
internal fun zoomForRadiusKm(radiusKm: Int): Double = when {
    radiusKm <= 5 -> 13.0
    radiusKm <= 15 -> 12.0
    radiusKm <= 30 -> 10.5
    else -> 9.0
}
