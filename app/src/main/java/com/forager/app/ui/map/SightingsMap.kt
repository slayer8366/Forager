package com.forager.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import com.forager.app.ui.theme.MapPalette
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forager.app.domain.GeoDistance
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.Waypoint
import com.forager.app.ui.motion.MotionTokens
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
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
 * [plannedTrips] draws a second, distinct marker per planned trip — a diamond, to read as
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
 * [com.forager.app.ui.map.CentrePinLocationPickerOverlay] instead. Kept wired here deliberately,
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
 * everything else, including [zoomForRadiusKm]'s numbers and the colour constants, is carried over
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
 *   was moot. But the same screenshot found the sighting dots (bark brown, [SIGHTING_DOT_OPACITY])
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
 * [Sighting]. Every other marker type — search centre, planned trips, waypoints — still has no
 * click handler; their [Feature]s built by [searchCenterFeatureCollection]/
 * [plannedTripsFeatureCollection] still carry title/snippet as GeoJSON properties only, ready for
 * the same treatment when one of those needs it too.
 */
@Composable
fun SightingsMap(
    region: Region,
    sightings: List<Sighting>,
    modifier: Modifier = Modifier,
    plannedTrips: List<PlannedTrip> = emptyList(),
    basemap: Basemap = Basemap.DEFAULT,
    /** See [com.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    focusOverride: LatLng? = null,
    onLongPress: (LatLng) -> Unit = {},
    /** See [com.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    onTap: () -> Unit = {},
    /** See [com.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    onSightingTap: (Sighting, Offset, Float) -> Unit = { _, _, _ -> },
    /** See [com.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    onCameraIdle: (LatLng) -> Unit = {},
    /**
     * Night mode: a slightly desaturated, higher-contrast basemap (`BasemapStyles.kt`'s
     * `NIGHT_RASTER_PAINT`). Sightings, area markers and every other overlay marker draw
     * identically to day mode regardless of this flag — see [MapPalette]'s own doc comment,
     * "Markers stay day-only, always" for why that's deliberate, not an oversight. Still drives
     * the map's own twilight trigger and long-press override; only what those feed into markers
     * has changed.
     *
     * Not the device's dark theme, and not derived from it: see [MapPalette]'s doc comment for
     * why that was tried, measured and abandoned.
     */
    nightMode: Boolean = false,
    /** See [com.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    breadcrumbPoints: List<LatLng> = emptyList(),
    /** See [com.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    waypoints: List<Waypoint> = emptyList(),
    /** See [com.forager.app.ui.map.MapOverlayContent.resumeTrackingRequestId]'s own doc comment. */
    resumeTrackingRequestId: Int = 0,
    /** See [com.forager.app.ui.map.MapOverlayContent.resetOrientationRequestId]'s own doc comment. */
    resetOrientationRequestId: Int = 0,
    /** See [com.forager.app.ui.map.MapOverlayContent.focusedObservationId]'s own doc comment. */
    focusedObservationId: Long? = null,
    /** See [com.forager.app.ui.map.MapRenderMode.trackLiveLocation]'s own doc comment. */
    trackLiveLocation: Boolean = true,
    /** See [com.forager.app.ui.map.MapOverlayContent.keptTrackPolylines]'s own doc comment. */
    keptTrackPolylines: List<List<LatLng>> = emptyList(),
    /** See [com.forager.app.ui.map.MapOverlayContent.findMarkers]'s own doc comment. */
    findMarkers: List<LatLng> = emptyList(),
    /** See [com.forager.app.ui.map.MapOverlayContent.photoMarkers]'s own doc comment. */
    photoMarkers: List<LatLng> = emptyList(),
    /** See [com.forager.app.ui.map.MapOverlayContent.offlineRegionCircles]'s own doc comment. */
    offlineRegionCircles: List<Region> = emptyList(),
) {
    val context = LocalContext.current

    // Always MapPalette.DAY, deliberately independent of nightMode — see MapPalette's own doc
    // comment, "Markers stay day-only, always." MapPalette.NIGHT/forMode still exist and are
    // still tested (MapPaletteTest), just not read here any more.
    val mapPalette = MapPalette.DAY
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        // Required once, before any MapLibre API touches the native library — the same
        // initialization com.forager.app.map.MapLibreOfflineMapRepository centralizes for its own
        // entry points, needed here too since this is a second, independent consumer of the SDK.
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    // Latest callbacks, read from inside listeners registered exactly once in the DisposableEffect
    // below — without this indirection, a listener registered once would keep calling whichever
    // onTap/onLongPress lambda instance was current at registration time, not the caller's latest.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnSightingTap by rememberUpdatedState(onSightingTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)
    // Read inside the click listener below (registered once, see that DisposableEffect's own
    // comment) so a tapped dot resolves against whichever sightings list is current, not whichever
    // one was in scope the moment the listener was registered.
    val currentSightings by rememberUpdatedState(sightings)
    // See MapOverlayContent.focusedObservationId's own doc comment for the dismiss-then-reappear
    // bug this closes: read fresh on every camera-idle event rather than latched into a local var
    // at tap time, so a caller-side dismissal (which only ever changes this parameter, never reaches
    // the click listener below) is visible here too.
    val currentFocusedObservationId by rememberUpdatedState(focusedObservationId)
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    // The Style instance from the most recently completed setStyle callback. Distinct from
    // "which Basemap is currently applied" (appliedBasemap, below) because this is what the data
    // effect keys on: a new Style object means new (empty) sources that need their content pushed.
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    // Guards against re-running setStyle on every recomposition, mirroring the deleted osmdroid
    // applyBasemap's own name()-comparison guard and for the same reason: setStyle discards every
    // source and layer the previous style had, so calling it when the basemap didn't actually
    // change would flash the map to blank and rebuild everything for nothing.
    var appliedBasemap by remember { mutableStateOf<Basemap?>(null) }

    // Tracked alongside appliedBasemap for the same reason it exists: the overlay layers are built
    // once per style load with their colours baked into the layer properties, so a palette change
    // is only visible after those layers are rebuilt. Without this, toggling night mode would
    // leave an already-loaded map drawing the previous palette until something else happened to
    // reload the style.
    //
    // Restyling is not free -- setStyle discards the LocationComponent state, which is why
    // activateLiveLocationIfPermitted has to run again below. Accepted because a night-mode switch
    // is a deliberate, roughly once-per-outing action, not something that fires on every
    // recomposition. That is a property of the mode being chosen for the map rather than inherited
    // from the device theme, which would have changed underneath the user.
    var appliedPalette by remember { mutableStateOf<MapPalette?>(null) }

    // What the camera was last deliberately moved to by the data+camera refresh effect below —
    // *not* re-derived from mapLibreMap.cameraPosition, which changes continuously while GPS
    // tracking owns the camera and would make this comparison meaningless. See
    // shouldMoveCameraToTarget's own doc comment for the hardware-reported bug this closes.
    var lastAppliedCameraTarget by remember { mutableStateOf<Pair<Region, LatLng?>?>(null) }

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
            mapLibreMap?.locationComponent?.onDestroy()
            mapView.onDestroy()
        }
    }

    // Registered once per composition of this MapView, not per recomposition: getMapAsync's
    // callback fires exactly once for the life of the MapView, so there's no re-registration to
    // guard against the way applyBasemap's guard above is needed for setStyle.
    DisposableEffect(mapView) {
        mapView.getMapAsync { map ->
            map.addOnMapClickListener { latLng ->
                // queryRenderedFeatures/toScreenLocation signatures confirmed via javap against the
                // pinned org.maplibre.gl:android-sdk:13.5.0 (Projection) and
                // org.maplibre.gl:android-sdk-geojson:6.0.1 (Feature) artifacts. Sighting dots share
                // one CircleLayer (SIGHTING_LAYER_ID) — restricting the query to it is what makes
                // this "did the tap land on a dot" rather than "did it land on the map at all."
                val screenPoint = map.projection.toScreenLocation(latLng)
                val tappedSighting = map.queryRenderedFeatures(screenPoint, SIGHTING_LAYER_ID)
                    .firstOrNull()
                    ?.getNumberProperty("observationId")
                    ?.toLong()
                    ?.let { id -> currentSightings.firstOrNull { it.observationId == id } }
                if (tappedSighting != null) {
                    currentOnSightingTap(tappedSighting, Offset(screenPoint.x, screenPoint.y), map.cameraPosition.bearing.toFloat())
                } else {
                    currentOnTap()
                }
                // false: unconsumed, matching the deleted osmdroid MapEventsOverlay's
                // singleTapConfirmedHelper — a plain tap isn't meant to swallow the event.
                false
            }
            map.addOnMapLongClickListener { latLng ->
                currentOnLongPress(LatLng(latLng.latitude, latLng.longitude))
                true
            }
            // OnCameraIdleListener.onCameraIdle() takes no argument (verified via javap against
            // the pinned org.maplibre.gl:android-sdk:13.5.0 artifact — MapLibreMap$OnCameraIdleListener
            // declares only `void onCameraIdle()`), so the position has to be read back explicitly
            // via getCameraPosition() inside the callback, not received as a parameter the way
            // addOnMapLongClickListener's latLng is.
            map.addOnCameraIdleListener {
                // CameraPosition.target is declared `LatLng?` in the pinned SDK itself (verified via
                // javap: the vendor's own constructor carries an org.jetbrains.annotations.Nullable
                // on this parameter) — null before the map has finished laying out a first camera
                // position, which an idle event can fire for. Nothing to report yet in that case, so
                // this skips the callback rather than fabricating a coordinate.
                map.cameraPosition.target?.let { target ->
                    currentOnCameraIdle(LatLng(target.latitude, target.longitude))
                }
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

    // Basemap swap. Keyed on (mapLibreMap, basemap) rather than driven from an AndroidView update
    // block: setStyle is asynchronous (its callback is where the new style's sources/layers can
    // actually be added), which the old synchronous update-block shape has no equivalent of.
    LaunchedEffect(mapLibreMap, basemap, mapPalette) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (appliedBasemap == basemap && appliedPalette == mapPalette) return@LaunchedEffect
        // Captured before setStyle below discards the LocationComponent entirely (see
        // activateLiveLocationIfPermitted's own doc comment on why re-activation is needed at
        // all) — null only the very first time this composable ever activates the puck;
        // CameraMode.NONE if the user had already broken tracking by panning/zooming;
        // CameraMode.TRACKING if they hadn't. Restoring exactly this, rather than always
        // re-forcing TRACKING, is the fix for a real hardware report: switching basemap (or
        // toggling night mode, which goes through this same style-swap path) was recentering the
        // map on the user's location even after they had deliberately panned away — the
        // GPS/locate-me icon is the control for that, not this one.
        val previousCameraMode = if (map.locationComponent.isLocationComponentActivated) {
            map.locationComponent.cameraMode
        } else {
            null
        }
        map.setMaxZoomPreference(basemap.maxZoom.toDouble())
        map.setStyle(Style.Builder().fromJson(styleJsonFor(basemap, night = nightMode))) { style ->
            initializeOverlayLayers(style, density = context.resources.displayMetrics.density, palette = mapPalette)
            // The data+camera refresh effect below re-pushes every source right after this, keyed
            // on loadedStyle among other things — including the sighting source, with "selected"
            // baked in from whatever focusedObservationId is current at that point. Nothing here
            // needs to seed it separately.
            appliedBasemap = basemap
            appliedPalette = mapPalette
            loadedStyle = style
            // setStyle discards the previous style's LocationComponent state the same way it does
            // this composable's own layers (see initializeOverlayLayers' own doc comment on why
            // that function re-runs here) — so the live-location "puck" needs the same
            // re-activate-on-every-new-style treatment. Guarded by trackLiveLocation — see that
            // parameter's own doc comment for why a historical-place map instance must never seize
            // the camera for the device's current location at all.
            if (trackLiveLocation) activateLiveLocationIfPermitted(map, style, context, restoreCameraMode = previousCameraMode)
        }
    }

    // Data + camera refresh. Runs on every relevant prop change *and* whenever a new style just
    // finished loading (loadedStyle changing is what makes this re-populate a freshly blank style
    // after a basemap swap) — the same "rebuild content every update, regardless of why the update
    // fired" behaviour the deleted osmdroid version had in its single `update` block, split here
    // because MapLibre's own API separates "style ready" from "camera/property changed".
    LaunchedEffect(
        loadedStyle, region, sightings, plannedTrips, focusOverride, breadcrumbPoints, waypoints, focusedObservationId,
        keptTrackPolylines, findMarkers, photoMarkers, offlineRegionCircles,
    ) {
        val style = loadedStyle ?: return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        refreshOverlayData(
            style, region, sightings, plannedTrips, breadcrumbPoints, waypoints, focusedObservationId,
            keptTrackPolylines, findMarkers, photoMarkers, offlineRegionCircles,
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
        if (shouldMoveCameraToTarget(isGpsTracking, target, lastAppliedCameraTarget)) {
            val center = MapLibreLatLng(region.lat, region.lng)
            // focusOverride pans the camera without moving the search-location marker or the
            // zoom-from-radius heuristic below, both of which stay anchored to region — see
            // MapSlot's doc comment on this parameter for why the two are kept independent.
            val cameraTarget = focusOverride?.let { MapLibreLatLng(it.lat, it.lng) } ?: center
            map.cameraPosition = CameraPosition.Builder()
                .target(cameraTarget)
                .zoom(zoomForRadiusKm(region.radiusKm))
                .build()
            lastAppliedCameraTarget = target
        }
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
        if (map.locationComponent.isLocationComponentActivated) {
            map.locationComponent.cameraMode = CameraMode.TRACKING
        } else {
            activateLiveLocationIfPermitted(map, style, context)
        }
    }

    // The map icon bar's orientation-reset control — MapLibre's own native compass view is
    // disabled above (see the DisposableEffect(mapView) block's own comment), so this is the only
    // way to straighten the map back to north once a rotate gesture has turned it. easeCamera, not
    // an instant jump, matching this map's other camera moves; bearing only, not target or zoom.
    LaunchedEffect(resetOrientationRequestId) {
        val map = mapLibreMap ?: return@LaunchedEffect
        map.easeCamera(CameraUpdateFactory.bearingTo(0.0))
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            // Load-bearing intent carried over from osmdroid, re-reasoned rather than re-verified
            // for MapLibre — see this composable's own doc comment, "Content does not paint outside
            // this composable's slot".
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        )
        // The always-visible attribution line CopyrightOverlay used to draw directly onto the
        // osmdroid MapView. MapLibre has its own tap-to-reveal attribution control
        // (UiSettings.isAttributionEnabled, on by default) built from each style source's own
        // "attribution" field (see styleJsonFor), but that is deliberately not relied on alone here
        // — see Basemap's doc comment on [Basemap.attribution] for why an always-drawn guarantee
        // matters for this app's USGS/ODbL credit and shouldn't quietly become tap-only.
        Text(
            text = basemap.attribution,
            style = MaterialTheme.typography.labelSmall,
            color = ComposeColor.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
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
 * needs the second. Layer add order is the draw order (later added draws on top), kept the same as
 * the deleted osmdroid version's overlay list order: search centre, sightings, planned trips last.
 *
 * Every marker is a plain [CircleLayer] now, day or night — see [MapPalette]'s own doc comment,
 * "Markers stay day-only, always." `docs/plans/contrast_assertions.md` archives the night-specific
 * `SymbolLayer`/icon-bitmap version this replaced, for whoever revives night-mode marker
 * differentiation later.
 *
 * The sighting layer's `circle-stroke-color`/`circle-stroke-width` are fixed expressions keyed on
 * each feature's own `"selected"` boolean property — see [sightingStrokeColorExpression]'s own doc
 * comment for why that property, not a paint-property expression comparing `observationId`
 * directly, is what actually selects the ring. Nothing here needs to know which sighting is
 * currently focused: [refreshOverlayData] bakes `"selected"` into the pushed data itself.
 */
private fun initializeOverlayLayers(style: Style, density: Float, palette: MapPalette) {
    style.addImage(PLANNED_TRIP_ICON_ID, plannedTripDiamondBitmap(density, palette.plannedTrip))

    // Added first, so its fill sits under every point marker and line — a coverage circle covering
    // a marker would make the marker unreadable, never the other way around. Journal Stage 2d.
    style.addSource(GeoJsonSource(OFFLINE_REGION_CIRCLE_SOURCE_ID, emptyFeatureCollection()))
    style.addLayer(
        FillLayer(OFFLINE_REGION_CIRCLE_LAYER_ID, OFFLINE_REGION_CIRCLE_SOURCE_ID).withProperties(
            PropertyFactory.fillColor(palette.areaMarkerBackground),
            PropertyFactory.fillOpacity(OFFLINE_REGION_CIRCLE_OPACITY),
        ),
    )
    style.addLayer(
        LineLayer(OFFLINE_REGION_CIRCLE_OUTLINE_LAYER_ID, OFFLINE_REGION_CIRCLE_SOURCE_ID).withProperties(
            PropertyFactory.lineColor(palette.areaMarkerBackground),
            PropertyFactory.lineWidth(OFFLINE_REGION_CIRCLE_OUTLINE_WIDTH_PX),
        ),
    )

    style.addSource(GeoJsonSource(SEARCH_CENTER_SOURCE_ID, emptyFeatureCollection()))
    style.addLayer(
        CircleLayer(SEARCH_CENTER_LAYER_ID, SEARCH_CENTER_SOURCE_ID).withProperties(
            PropertyFactory.circleRadius(SEARCH_CENTER_RADIUS_PX),
            PropertyFactory.circleColor(palette.searchCentre),
            PropertyFactory.circleStrokeColor(Color.WHITE),
            PropertyFactory.circleStrokeWidth(SEARCH_CENTER_STROKE_WIDTH_PX),
        ),
    )

    style.addSource(GeoJsonSource(SIGHTING_SOURCE_ID, emptyFeatureCollection()))
    style.addLayer(
        // One shared layer for every observation, styled once — unlike osmdroid, which built
        // one Drawable and stamped it per Marker, MapLibre draws every feature in the source
        // with the same layer properties, so there is nothing per-sighting to construct here.
        CircleLayer(SIGHTING_LAYER_ID, SIGHTING_SOURCE_ID).withProperties(
            PropertyFactory.circleColor(palette.sightingDot),
            PropertyFactory.circleOpacity(SIGHTING_DOT_OPACITY),
            PropertyFactory.circleRadius(SIGHTING_DOT_RADIUS_PX),
            // See sightingStrokeColorExpression's own doc comment — it keys off each feature's own
            // "selected" property, so nothing here needs seeding with the current
            // focusedObservationId the way an id-comparison expression would. Stroke width stays a
            // flat SIGHTING_DOT_STROKE_WIDTH_PX for every dot, selected or not — a prior widened
            // selected-width was tried and reverted on request, to keep the highlight to colour
            // alone (see SIGHTING_DOT_STROKE_WIDTH_PX's own doc comment for that history).
            PropertyFactory.circleStrokeColor(sightingStrokeColorExpression(palette)),
            PropertyFactory.circleStrokeWidth(SIGHTING_DOT_STROKE_WIDTH_PX),
            PropertyFactory.circleStrokeOpacity(SIGHTING_DOT_STROKE_OPACITY),
        ),
    )

    // Breadcrumb is dashed (a short dash with round caps reads as a trail of dots —
    // "breadcrumbs" should look like breadcrumbs). See BREADCRUMB_DASH_PATTERN's own doc comment.
    style.addSource(GeoJsonSource(BREADCRUMB_SOURCE_ID, emptyFeatureCollection()))
    style.addLayer(
        LineLayer(BREADCRUMB_LAYER_ID, BREADCRUMB_SOURCE_ID).withProperties(
            PropertyFactory.lineColor(palette.breadcrumb),
            PropertyFactory.lineWidth(BREADCRUMB_STROKE_WIDTH_PX),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineDasharray(BREADCRUMB_DASH_PATTERN),
        ),
    )

    // Kept tracks (Journal Stage 2d): a solid line, distinct from the live breadcrumb's dash — see
    // keptTracksFeatureCollection's own doc comment for why this is a genuine MultiLineString, not
    // breadcrumbPoints reshaped. connector was designed for, and is otherwise unused since, the
    // deleted foraging-areas feature — revived here rather than adding a new, contrast-unverified
    // colour to MapPalette.
    style.addSource(GeoJsonSource(KEPT_TRACKS_SOURCE_ID, emptyFeatureCollection()))
    style.addLayer(
        LineLayer(KEPT_TRACKS_LAYER_ID, KEPT_TRACKS_SOURCE_ID).withProperties(
            PropertyFactory.lineColor(palette.connector),
            PropertyFactory.lineWidth(KEPT_TRACK_STROKE_WIDTH_PX),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )

    style.addSource(GeoJsonSource(PLANNED_TRIP_SOURCE_ID, emptyFeatureCollection()))
    style.addLayer(
        SymbolLayer(PLANNED_TRIP_LAYER_ID, PLANNED_TRIP_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(PLANNED_TRIP_ICON_ID),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconAnchor("center"),
        ),
    )

    // Last, so a waypoint marker never sits under a planned-trip diamond if the two ever land on
    // the same point.
    style.addImage(WAYPOINT_ICON_ID, waypointPinBitmap(density, palette.waypoint))
    style.addSource(GeoJsonSource(WAYPOINT_SOURCE_ID, emptyFeatureCollection()))
    style.addLayer(
        SymbolLayer(WAYPOINT_LAYER_ID, WAYPOINT_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(WAYPOINT_ICON_ID),
            PropertyFactory.iconAllowOverlap(true),
            // Bottom, not center like the planned-trip diamond: a pin's drawn tip is what should
            // sit on the actual coordinate, not the shape's bounding-box center.
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        ),
    )

    // Find and photo markers (Journal Stage 2d) — the waypoint pin's own template, reused with a
    // different colour/shape per the dispatch's own guidance ("this is the reusable template for
    // find and photo markers — discrete markers, not density dots"). Added last, after waypoints,
    // for the same "never sit under a sibling point marker" reasoning the waypoint layer's own
    // comment already gives.
    style.addImage(FIND_ICON_ID, waypointPinBitmap(density, palette.areaMarkerBackground))
    style.addSource(GeoJsonSource(FIND_SOURCE_ID, emptyFeatureCollection()))
    style.addLayer(
        SymbolLayer(FIND_LAYER_ID, FIND_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(FIND_ICON_ID),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        ),
    )

    style.addImage(PHOTO_ICON_ID, plannedTripDiamondBitmap(density, palette.plannedTrip))
    style.addSource(GeoJsonSource(PHOTO_SOURCE_ID, emptyFeatureCollection()))
    style.addLayer(
        SymbolLayer(PHOTO_LAYER_ID, PHOTO_SOURCE_ID).withProperties(
            PropertyFactory.iconImage(PHOTO_ICON_ID),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconAnchor("center"),
        ),
    )
}

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
    keptTrackPolylines: List<List<LatLng>>,
    findMarkers: List<LatLng>,
    photoMarkers: List<LatLng>,
    offlineRegionCircles: List<Region>,
) {
    style.getSourceAs<GeoJsonSource>(SEARCH_CENTER_SOURCE_ID)?.setGeoJson(searchCenterFeatureCollection(region))
    style.getSourceAs<GeoJsonSource>(SIGHTING_SOURCE_ID)?.setGeoJson(sightingsFeatureCollection(sightings, focusedObservationId))
    style.getSourceAs<GeoJsonSource>(PLANNED_TRIP_SOURCE_ID)?.setGeoJson(plannedTripsFeatureCollection(plannedTrips))
    style.getSourceAs<GeoJsonSource>(BREADCRUMB_SOURCE_ID)?.setGeoJson(breadcrumbFeatureCollection(breadcrumbPoints))
    style.getSourceAs<GeoJsonSource>(WAYPOINT_SOURCE_ID)?.setGeoJson(waypointsFeatureCollection(waypoints))
    style.getSourceAs<GeoJsonSource>(KEPT_TRACKS_SOURCE_ID)?.setGeoJson(keptTracksFeatureCollection(keptTrackPolylines))
    style.getSourceAs<GeoJsonSource>(FIND_SOURCE_ID)?.setGeoJson(pointsFeatureCollection(findMarkers))
    style.getSourceAs<GeoJsonSource>(PHOTO_SOURCE_ID)?.setGeoJson(pointsFeatureCollection(photoMarkers))
    style.getSourceAs<GeoJsonSource>(OFFLINE_REGION_CIRCLE_SOURCE_ID)?.setGeoJson(offlineRegionCirclesFeatureCollection(offlineRegionCircles))
}

/**
 * The data+camera refresh effect's own decision of whether to move the camera to [target] —
 * extracted as a plain function, the same reason [locationIndicatorTrackingAnimationMultiplier]
 * below is one, so the actual defect (not just the surrounding native-object plumbing) is
 * unit-testable. Real hardware report this fixes: that effect is keyed on `loadedStyle` (needed so
 * [refreshOverlayData] above re-runs after a basemap swap blanks the style), but with no guard, it
 * also re-ran the camera move below whenever GPS tracking wasn't active — including on a basemap
 * or night-mode swap that changed neither `region` nor `focusOverride` — which read as "changing
 * map style brought the map back to my location" even though the GPS/locate-me icon is the only
 * control meant to do that. Comparing [target] against [lastAppliedCameraTarget] — what was
 * actually last applied, not merely that the effect ran again — is what tells "the search moved"
 * apart from "the style reloaded." [isGpsTracking] itself is left as a native-backed computation
 * at the call site (see that effect's own doc comment on why moving the camera while it's true
 * would fight the puck), not folded into this function, so this stays a pure comparison.
 */
internal fun shouldMoveCameraToTarget(
    isGpsTracking: Boolean,
    target: Pair<Region, LatLng?>,
    lastAppliedCameraTarget: Pair<Region, LatLng?>?,
): Boolean = !isGpsTracking && target != lastAppliedCameraTarget

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
 * Turns on MapLibre's own "blue dot" location puck and has the camera follow it — "like regular
 * GPS," the project owner's own framing, rather than the compass strip's pre-existing one-shot
 * locate-me fetch (which still exists unchanged, feeding that strip's own text readout, not the
 * map's camera). A no-op, not a crash or a silent guess, when [Manifest.permission.ACCESS_FINE_LOCATION]/
 * [Manifest.permission.ACCESS_COARSE_LOCATION] aren't granted — same "explicit unsupported state,
 * never fabricated" rule [com.forager.app.location.AndroidLocationProvider.hasLocationPermission]
 * already follows for the one-shot path; the map simply won't show a puck until permission exists
 * and something re-triggers this (a fresh style load, or the locate-me icon — see
 * [resumeTrackingRequestId][MapOverlayContent.resumeTrackingRequestId]'s own doc comment).
 *
 * [restoreCameraMode] is what this composable's own basemap-swap effect passes to avoid a real
 * hardware-reported bug: `setStyle` (any basemap change, or a night-mode toggle, which shares this
 * same path) discards the LocationComponent outright, so this function has to run again on every
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
    val locationComponentOptions = LocationComponentOptions.builder(context)
        // Duration ratio, not an absolute value: see locationIndicatorTrackingAnimationMultiplier()
        // above — its base (750ms) came from javap-inspecting the pinned MapLibre artifact, not
        // public API or documentation, so it can silently go stale on a MapLibre version bump.
        .trackingAnimationDurationMultiplier(locationIndicatorTrackingAnimationMultiplier())
        .build()
    locationComponent.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style)
            .locationComponentOptions(locationComponentOptions)
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

/** Same check, same two permissions, as [com.forager.app.location.AndroidLocationProvider.hasLocationPermission] — not shared code across an app/domain-layer boundary that owns neither Context nor Manifest. */
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
    feature.addStringProperty("title", "Search location")
    feature.addStringProperty("snippet", "Radius: ${region.radiusKm} km")
    return FeatureCollection.fromFeature(feature)
}

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
 * The active track's recorded points as a single [LineString] feature, oldest first — an empty
 * [FeatureCollection] when [points] has fewer than two points (nothing recorded yet, or only the
 * first fix so far): a `LineString` needs at least two points.
 */
internal fun breadcrumbFeatureCollection(points: List<LatLng>): FeatureCollection {
    if (points.size < 2) return emptyFeatureCollection()
    val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.lng, it.lat) })
    return FeatureCollection.fromFeature(Feature.fromGeometry(line))
}

internal fun plannedTripsFeatureCollection(plannedTrips: List<PlannedTrip>): FeatureCollection {
    val features = plannedTrips.map { trip ->
        Feature.fromGeometry(Point.fromLngLat(trip.location.lng, trip.location.lat)).apply {
            addStringProperty("title", "Planned trip")
            addStringProperty("snippet", trip.date.toString())
        }
    }
    return FeatureCollection.fromFeatures(features)
}

/** Every saved [Waypoint] as a point feature carrying its own name, for the pin's [SymbolLayer]. */
internal fun waypointsFeatureCollection(waypoints: List<Waypoint>): FeatureCollection {
    val features = waypoints.map { waypoint ->
        Feature.fromGeometry(Point.fromLngLat(waypoint.lng, waypoint.lat)).apply {
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
internal fun keptTracksFeatureCollection(polylines: List<List<LatLng>>): FeatureCollection {
    val features = polylines
        .filter { it.size >= 2 }
        .map { points -> Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.lng, it.lat) })) }
    return FeatureCollection.fromFeatures(features)
}

/** Plain point markers with no per-feature properties — Journal Stage 2d's find/photo pins, neither of which has a tap handler (or any other reason to carry one) yet. */
internal fun pointsFeatureCollection(points: List<LatLng>): FeatureCollection =
    FeatureCollection.fromFeatures(points.map { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)) })

/**
 * A Cartography entry's kept offline regions as filled polygon features — Journal Stage 2d. Each
 * [Region] needs no fetch to resolve (its own snapshot already carries lat/lng/radius); the polygon
 * ring itself comes from [GeoDistance.circlePolygonPoints], the true-circle approximation built for
 * exactly this drawn-shape use (as opposed to [GeoDistance.boundingBox]'s tile-download rectangle).
 */
internal fun offlineRegionCirclesFeatureCollection(regions: List<Region>): FeatureCollection {
    val features = regions.map { region ->
        val ring = GeoDistance.circlePolygonPoints(LatLng(region.lat, region.lng), region.radiusKm)
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring.map { Point.fromLngLat(it.lng, it.lat) })))
    }
    return FeatureCollection.fromFeatures(features)
}

/**
 * A solid diamond bitmap for the planned-trip [SymbolLayer]'s `icon-image`, distinct in shape and
 * colour from the translucent sighting-dot circles, so "planned" reads as its own kind of pin
 * rather than a variant of one — same intent as the deleted osmdroid `plannedTripIcon`, redrawn
 * because MapLibre's `SymbolLayer` needs a named image registered on the [Style]
 * (`Style.addImage`) rather than a per-`Marker` `Drawable`.
 */
private fun plannedTripDiamondBitmap(density: Float, markerColor: Int): Bitmap {
    val sizePx = (PLANNED_TRIP_MARKER_SIZE_DP * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val diamond = Path().apply {
        moveTo(sizePx / 2f, 0f)
        lineTo(sizePx.toFloat(), sizePx / 2f)
        lineTo(sizePx / 2f, sizePx.toFloat())
        lineTo(0f, sizePx / 2f)
        close()
    }
    canvas.drawPath(diamond, Paint().apply { color = markerColor; style = Paint.Style.FILL; isAntiAlias = true })
    return bitmap
}

/**
 * A teardrop pin bitmap for the waypoint [SymbolLayer]'s `icon-image` — round head, pointed tail
 * touching the actual coordinate (see [initializeOverlayLayers]'s `iconAnchor(BOTTOM)` for why the
 * tail, not the shape's center, has to be what's anchored). A distinct amber — the "you dropped a
 * pin here" colour convention this app's other roles don't otherwise use.
 */
private fun waypointPinBitmap(density: Float, markerColor: Int): Bitmap {
    val widthPx = (WAYPOINT_MARKER_WIDTH_DP * density).toInt().coerceAtLeast(1)
    val heightPx = (WAYPOINT_MARKER_HEIGHT_DP * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val headRadius = widthPx / 2f
    val headCenterX = widthPx / 2f
    val headCenterY = headRadius
    val pin = Path().apply {
        addCircle(headCenterX, headCenterY, headRadius, Path.Direction.CW)
    }
    // The tail: a triangle from the circle's sides down to the bottom-center tip, unioned with
    // the circle above so the whole pin fills as one shape.
    pin.addPath(
        Path().apply {
            moveTo(headCenterX - headRadius, headCenterY)
            lineTo(headCenterX, heightPx.toFloat())
            lineTo(headCenterX + headRadius, headCenterY)
            close()
        },
    )
    canvas.drawPath(pin, Paint().apply { color = markerColor; style = Paint.Style.FILL; isAntiAlias = true })
    return bitmap
}

// Source/layer ids. Fixed strings rather than generated, since every one of them is referenced by
// name from at least two places (initializeOverlayLayers and refreshOverlayData, or a layer
// referencing its source) and a typo needs to be a compile error, not a silently-missing layer.
private const val SEARCH_CENTER_SOURCE_ID = "search-center"
private const val SEARCH_CENTER_LAYER_ID = "search-center-layer"
private const val SIGHTING_SOURCE_ID = "sightings"
private const val SIGHTING_LAYER_ID = "sightings-layer"
private const val PLANNED_TRIP_SOURCE_ID = "planned-trips"
private const val PLANNED_TRIP_LAYER_ID = "planned-trips-layer"
private const val PLANNED_TRIP_ICON_ID = "planned-trip-diamond"
private const val BREADCRUMB_SOURCE_ID = "breadcrumb-trail"
private const val BREADCRUMB_LAYER_ID = "breadcrumb-trail-layer"
private const val WAYPOINT_SOURCE_ID = "waypoints"
private const val WAYPOINT_LAYER_ID = "waypoints-layer"
private const val WAYPOINT_ICON_ID = "waypoint-pin"

// Journal Stage 2d.
private const val KEPT_TRACKS_SOURCE_ID = "kept-tracks"
private const val KEPT_TRACKS_LAYER_ID = "kept-tracks-layer"
private const val FIND_SOURCE_ID = "find-markers"
private const val FIND_LAYER_ID = "find-markers-layer"
private const val FIND_ICON_ID = "find-pin"
private const val PHOTO_SOURCE_ID = "photo-markers"
private const val PHOTO_LAYER_ID = "photo-markers-layer"
private const val PHOTO_ICON_ID = "photo-diamond"
private const val OFFLINE_REGION_CIRCLE_SOURCE_ID = "offline-region-circles"
private const val OFFLINE_REGION_CIRCLE_LAYER_ID = "offline-region-circles-layer"
private const val OFFLINE_REGION_CIRCLE_OUTLINE_LAYER_ID = "offline-region-circles-outline-layer"

// The overlay's colours now come from ui/theme/MapPalette.kt, derived from the active
// ColorScheme and passed in -- see that type's doc comment for the derivation rule, and for
// the recorded objection about deriving map colours from a theme the basemap does not follow.
// Only non-colour geometry constants remain here.
private const val SIGHTING_DOT_OPACITY = 0.7f // ~= the deleted osmdroid version's 0xB3 alpha.
private const val SIGHTING_DOT_RADIUS_PX = 9f

// The stroke that keeps individual dots distinguishable within a dense cluster — see this file's
// class doc comment, "The overlay colours", for the hardware finding this fixes. A light, near-
// opaque stroke (not translucent like the fill) so the boundary itself stays crisp regardless of
// how many dots overlap or what opacity the fill composites to underneath.
//
// The selected dot's ring used to widen this on top of recolouring — first to 3.5px, then to
// 4.5px alongside MapPalette.sightingDotStrokeSelected moving to a deeper blue — and both were
// reverted on request: the highlight is colour alone now, [sightingStrokeColorExpression] against
// this one flat width for every dot, selected or not.
internal const val SIGHTING_DOT_STROKE_WIDTH_PX = 1.5f
private const val SIGHTING_DOT_STROKE_OPACITY = 0.85f

private const val PLANNED_TRIP_MARKER_SIZE_DP = 22f

private const val SEARCH_CENTER_RADIUS_PX = 8f
private const val SEARCH_CENTER_STROKE_WIDTH_PX = 2f

private const val BREADCRUMB_STROKE_WIDTH_PX = 6f

private const val WAYPOINT_MARKER_WIDTH_DP = 22f
private const val WAYPOINT_MARKER_HEIGHT_DP = 28f

// Journal Stage 2d.
private const val KEPT_TRACK_STROKE_WIDTH_PX = 6f
private const val OFFLINE_REGION_CIRCLE_OPACITY = 0.2f
private const val OFFLINE_REGION_CIRCLE_OUTLINE_WIDTH_PX = 1.5f

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
