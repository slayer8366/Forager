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
import androidx.compose.ui.graphics.Color as ComposeColor
import com.forager.app.ui.theme.MapPalette
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forager.app.domain.model.ForagingArea
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
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Shows the searched region as a map with a marker per real observation ([sightings]).
 *
 * When [areas] is non-empty, numbered foraging-area markers and a visiting-order connector are
 * drawn over the individual pins (the pins stay: they're the evidence the areas were derived
 * from). Pass an empty list to show pins alone. The connector line is solid; a dashed line is now
 * the live [breadcrumbPoints] trail instead — see [BREADCRUMB_DASH_PATTERN]'s doc comment for why
 * the two swapped, and what still keeps the connector from reading as a real walkable route.
 *
 * [plannedTrips] draws a third, distinct marker per planned trip — a diamond, to read as
 * different from both the translucent sighting dots (density of what's been observed) and the
 * numbered foraging-area labels (where to go based on that history): a planned trip is neither of
 * those, it's a place the user chose for themselves.
 *
 * [onLongPress] fires with the geographic point under a long-press, for the caller to turn into a
 * planned trip (via a date picker it owns — this composable knows nothing about dates or
 * persistence, only where the gesture happened). Wired through `MapLibreMap.addOnMapLongClickListener`
 * rather than a gesture detector on the [AndroidView] itself, for the same reason the previous
 * osmdroid implementation used `MapEventsOverlay`: MapLibre's own touch handling already owns
 * pan/zoom on this `MapView`, and a second, independent gesture detector on top would race it for
 * the same touch stream.
 *
 * ## Migration note (osmdroid -> MapLibre, `docs/plans/maplibre-migration.md` §2b)
 *
 * This composable is a full rewrite, not a port. Per the plan: "the dot markers, numbered area
 * markers, and dashed connectors ... all become style layers rather than osmdroid `Overlay`s." Every
 * one of them is now a `GeoJsonSource` + a `CircleLayer`/`LineLayer`/`SymbolLayer` in
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
 * - *The numbered area-marker text renders at all.* This was hit as a real, silent failure once
 *   already in this migration (`MapLibreBasemapPreviewActivity`'s history, "Fix missing area-marker
 *   labels: style JSON had no glyphs URL" — hardware-confirmed) before that scaffolding was deleted
 *   in this same change. [styleJsonFor] carries the fix (a `glyphs` URL on every style) forward, and
 *   `SightingsMapOverlayTest` asserts the built style actually has one.
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
 * **Not re-confirmed, and known to have changed:** the tap-to-see-title/snippet popup osmdroid's
 * `Marker.title`/`.snippet` gave for free. Style-layer geometry has no built-in equivalent —
 * `MapLibreMap.queryRenderedFeatures` plus a hand-built Compose info card would be needed to rebuild
 * it, and that is real UI work this pass doesn't include. The title/snippet strings themselves are
 * not lost: every [Feature] built by [searchCenterFeatureCollection]/[sightingsFeatureCollection]/
 * [areaMarkersFeatureCollection]/[connectorFeatureCollection]/[plannedTripsFeatureCollection] still
 * carries them as GeoJSON properties, ready for that future click handler. The one piece of that
 * text carrying an actual safety property — [VISITING_ORDER_DISCLAIMER] — does not depend on the
 * popup at all: `AvailabilityScreen` already renders it as a standing caption under the map
 * (verified: `grep -n VISITING_ORDER_DISCLAIMER` finds that call site independent of this file), so
 * the disclaimer stays user-visible with or without a tap.
 */
@Composable
fun SightingsMap(
    region: Region,
    sightings: List<Sighting>,
    modifier: Modifier = Modifier,
    areas: List<ForagingArea> = emptyList(),
    plannedTrips: List<PlannedTrip> = emptyList(),
    basemap: Basemap = Basemap.DEFAULT,
    /** See [com.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    focusOverride: LatLng? = null,
    onLongPress: (LatLng) -> Unit = {},
    /** See [com.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    onTap: () -> Unit = {},
    /** See [com.forager.app.ui.map.MapSlot]'s doc comment on this same parameter. */
    onCameraIdle: (LatLng) -> Unit = {},
    /**
     * Night mode: a dimmed basemap and the light-on-dark overlay palette that goes with it.
     * Defaults to `false`, so nothing changes until a caller opts in — the twilight trigger and
     * the long-press override that will drive this are separate work, and this parameter is
     * deliberately landed ahead of them so the palettes can be reviewed on their own.
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
) {
    val context = LocalContext.current

    // Resolved here rather than inside the LaunchedEffect below, which is not a composable.
    // MapPalette.forMode is a lookup, so there is nothing worth remembering.
    val mapPalette = MapPalette.forMode(night = nightMode)
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
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnCameraIdle by rememberUpdatedState(onCameraIdle)

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
            map.addOnMapClickListener {
                currentOnTap()
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
            initializeOverlayLayers(style, density = context.resources.displayMetrics.density, palette = mapPalette, night = nightMode)
            appliedBasemap = basemap
            appliedPalette = mapPalette
            loadedStyle = style
            // setStyle discards the previous style's LocationComponent state the same way it does
            // this composable's own layers (see initializeOverlayLayers' own doc comment on why
            // that function re-runs here) — so the live-location "puck" needs the same
            // re-activate-on-every-new-style treatment.
            activateLiveLocationIfPermitted(map, style, context, restoreCameraMode = previousCameraMode)
        }
    }

    // Data + camera refresh. Runs on every relevant prop change *and* whenever a new style just
    // finished loading (loadedStyle changing is what makes this re-populate a freshly blank style
    // after a basemap swap) — the same "rebuild content every update, regardless of why the update
    // fired" behaviour the deleted osmdroid version had in its single `update` block, split here
    // because MapLibre's own API separates "style ready" from "camera/property changed".
    LaunchedEffect(loadedStyle, region, sightings, areas, plannedTrips, focusOverride, breadcrumbPoints, waypoints) {
        val style = loadedStyle ?: return@LaunchedEffect
        val map = mapLibreMap ?: return@LaunchedEffect
        refreshOverlayData(style, region, sightings, areas, plannedTrips, breadcrumbPoints, waypoints)

        // Once the live-location "puck" is actively tracking (the default once permission is
        // granted — see activateLiveLocationIfPermitted), it owns the camera continuously, on its
        // own internal update loop, independent of recomposition. Jumping the camera to the search
        // region here too — on every sightings/area/breadcrumb update this effect already keys on,
        // which includes roughly every 15s while a track is recording — would fight it, snapping the
        // view back to the search center out from under a walker watching their live position. Once
        // a pan/zoom/the CameraMode.NONE break in activateLiveLocationIfPermitted's own doc comment
        // drops tracking, this resumes controlling the camera exactly as it did before that existed.
        val isGpsTracking = map.locationComponent.isLocationComponentActivated &&
            map.locationComponent.cameraMode != CameraMode.NONE
        if (!isGpsTracking) {
            val center = MapLibreLatLng(region.lat, region.lng)
            // focusOverride pans the camera without moving the search-location marker or the
            // zoom-from-radius heuristic below, both of which stay anchored to region — see
            // MapSlot's doc comment on this parameter for why the two are kept independent.
            val cameraTarget = focusOverride?.let { MapLibreLatLng(it.lat, it.lng) } ?: center
            map.cameraPosition = CameraPosition.Builder()
                .target(cameraTarget)
                .zoom(zoomForRadiusKm(region.radiusKm))
                .build()
        }
    }

    // Re-engages GPS camera tracking on demand — the map redesign's GPS/locate-me icon, tapped
    // either for its first activation or to resume tracking after a manual pan/zoom broke it (see
    // activateLiveLocationIfPermitted's own doc comment on CameraMode.NONE). Also the natural retry
    // point if the very first tap only triggered the OS permission dialog: MapOverlayContent's own
    // doc comment on resumeTrackingRequestId covers why a second tap is what completes activation
    // in that case, not an automatic one.
    LaunchedEffect(resumeTrackingRequestId) {
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
 * the deleted osmdroid version's overlay list order: search centre, sightings, connector, area
 * markers (circle then its label), planned trips last so a diamond never sits under a numbered area
 * marker.
 */
private fun initializeOverlayLayers(style: Style, density: Float, palette: MapPalette, night: Boolean) {
    // Every night icon gets a darkened, semi-transparent halo behind its fill so it stays visible
    // against a light *or* dark background — see NIGHT_ICON_HALO_ALPHA's own doc comment for why
    // this replaced tile-contrast-against-the-ground as the way markers stay legible at night.
    // null on day, unchanged from before this existed.
    val nightHaloColor = if (night) withAlpha(palette.sightingDotStroke, NIGHT_ICON_HALO_ALPHA) else null

    style.addImage(PLANNED_TRIP_ICON_ID, plannedTripDiamondBitmap(density, palette.plannedTrip, nightHaloColor))

    style.addSource(GeoJsonSource(SEARCH_CENTER_SOURCE_ID, emptyFeatureCollection()))
    if (night) {
        // Night differentiates markers by icon shape, not hue — see MapPalette.NIGHT's own doc
        // comment, "Fifth pass", for why. A target ring around a dot, distinct from every other
        // night icon's silhouette.
        style.addImage(
            SEARCH_CENTER_ICON_ID,
            searchCentreTargetBitmap(density, palette.searchCentre, palette.sightingDotStroke, nightHaloColor!!),
        )
        style.addLayer(
            SymbolLayer(SEARCH_CENTER_LAYER_ID, SEARCH_CENTER_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(SEARCH_CENTER_ICON_ID),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconAnchor("center"),
            ),
        )
    } else {
        style.addLayer(
            CircleLayer(SEARCH_CENTER_LAYER_ID, SEARCH_CENTER_SOURCE_ID).withProperties(
                PropertyFactory.circleRadius(SEARCH_CENTER_RADIUS_PX),
                PropertyFactory.circleColor(palette.searchCentre),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(SEARCH_CENTER_STROKE_WIDTH_PX),
            ),
        )
    }

    style.addSource(GeoJsonSource(SIGHTING_SOURCE_ID, emptyFeatureCollection()))
    if (night) {
        style.addImage(
            SIGHTING_DOT_ICON_ID,
            sightingDotBitmap(density, palette.sightingDot, palette.sightingDotStroke, nightHaloColor!!),
        )
        style.addLayer(
            SymbolLayer(SIGHTING_LAYER_ID, SIGHTING_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(SIGHTING_DOT_ICON_ID),
                PropertyFactory.iconOpacity(SIGHTING_DOT_OPACITY),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconAnchor("center"),
            ),
        )
    } else {
        style.addLayer(
            // One shared layer for every observation, styled once — unlike osmdroid, which built
            // one Drawable and stamped it per Marker, MapLibre draws every feature in the source
            // with the same layer properties, so there is nothing per-sighting to construct here.
            CircleLayer(SIGHTING_LAYER_ID, SIGHTING_SOURCE_ID).withProperties(
                PropertyFactory.circleColor(palette.sightingDot),
                PropertyFactory.circleOpacity(SIGHTING_DOT_OPACITY),
                PropertyFactory.circleRadius(SIGHTING_DOT_RADIUS_PX),
                PropertyFactory.circleStrokeColor(palette.sightingDotStroke),
                PropertyFactory.circleStrokeWidth(SIGHTING_DOT_STROKE_WIDTH_PX),
                PropertyFactory.circleStrokeOpacity(SIGHTING_DOT_STROKE_OPACITY),
            ),
        )
    }

    // Line style (dashed vs. solid), not colour, now carries the connector/breadcrumb distinction
    // — true day and night alike, since night mode shares one colour across every marker and line.
    // Breadcrumb is dashed (a short dash with round caps reads as a trail of dots — "breadcrumbs"
    // should look like breadcrumbs); connector is solid. See BREADCRUMB_DASH_PATTERN's own doc
    // comment for why the connector reading solid no longer implies a real walkable route, the
    // property the previous dashed choice protected.
    style.addSource(GeoJsonSource(CONNECTOR_SOURCE_ID, emptyFeatureCollection()))
    style.addLayer(
        LineLayer(CONNECTOR_LAYER_ID, CONNECTOR_SOURCE_ID).withProperties(
            PropertyFactory.lineColor(palette.connector),
            PropertyFactory.lineWidth(CONNECTOR_STROKE_WIDTH_PX),
        ),
    )

    // Added after the connector so an active recording's trail draws on top of it if the two ever
    // geographically overlap.
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

    style.addSource(GeoJsonSource(AREA_MARKER_SOURCE_ID, emptyFeatureCollection()))
    if (night) {
        style.addImage(
            AREA_MARKER_ICON_ID,
            areaMarkerBadgeBitmap(density, palette.areaMarkerBackground, nightHaloColor!!),
        )
        style.addLayer(
            SymbolLayer(AREA_MARKER_CIRCLE_LAYER_ID, AREA_MARKER_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(AREA_MARKER_ICON_ID),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconAnchor("center"),
            ),
        )
    } else {
        style.addLayer(
            CircleLayer(AREA_MARKER_CIRCLE_LAYER_ID, AREA_MARKER_SOURCE_ID).withProperties(
                PropertyFactory.circleColor(palette.areaMarkerBackground),
                PropertyFactory.circleRadius(AREA_MARKER_RADIUS_PX),
            ),
        )
    }
    style.addLayer(
        SymbolLayer(AREA_MARKER_LABEL_LAYER_ID, AREA_MARKER_SOURCE_ID).withProperties(
            // "{label}" is MapLibre's own token-substitution syntax inside a literal text-field
            // string, pulling the "label" GeoJSON property each area-marker Feature carries (see
            // areaMarkersFeatureCollection) — hardware-confirmed working in
            // MapLibreBasemapPreviewActivity's history before that scaffolding was deleted here.
            PropertyFactory.textField("{label}"),
            // Must name a font actually present in styleJsonFor's glyphs set, or text-field renders
            // nothing — see BasemapStyles.kt's doc comment for why this exact font.
            PropertyFactory.textFont(AREA_MARKER_FONT_STACK),
            PropertyFactory.textColor(palette.areaMarkerForeground),
            PropertyFactory.textSize(AREA_MARKER_FONT_SIZE_PX),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
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

    // Last, so a waypoint marker never sits under a planned-trip diamond or an area label if two
    // ever land on the same point.
    style.addImage(WAYPOINT_ICON_ID, waypointPinBitmap(density, palette.waypoint, nightHaloColor))
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
    areas: List<ForagingArea>,
    plannedTrips: List<PlannedTrip>,
    breadcrumbPoints: List<LatLng>,
    waypoints: List<Waypoint>,
) {
    style.getSourceAs<GeoJsonSource>(SEARCH_CENTER_SOURCE_ID)?.setGeoJson(searchCenterFeatureCollection(region))
    style.getSourceAs<GeoJsonSource>(SIGHTING_SOURCE_ID)?.setGeoJson(sightingsFeatureCollection(sightings))
    style.getSourceAs<GeoJsonSource>(CONNECTOR_SOURCE_ID)?.setGeoJson(connectorFeatureCollection(region, areas))
    style.getSourceAs<GeoJsonSource>(AREA_MARKER_SOURCE_ID)?.setGeoJson(areaMarkersFeatureCollection(areas))
    style.getSourceAs<GeoJsonSource>(PLANNED_TRIP_SOURCE_ID)?.setGeoJson(plannedTripsFeatureCollection(plannedTrips))
    style.getSourceAs<GeoJsonSource>(BREADCRUMB_SOURCE_ID)?.setGeoJson(breadcrumbFeatureCollection(breadcrumbPoints))
    style.getSourceAs<GeoJsonSource>(WAYPOINT_SOURCE_ID)?.setGeoJson(waypointsFeatureCollection(waypoints))
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

internal fun sightingsFeatureCollection(sightings: List<Sighting>): FeatureCollection {
    val features = sightings.map { sighting ->
        Feature.fromGeometry(Point.fromLngLat(sighting.lng, sighting.lat)).apply {
            addStringProperty("title", sighting.commonName ?: sighting.scientificName)
            addStringProperty("snippet", sighting.observedOn?.toString() ?: sighting.scientificName)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

/** [ForagingArea.visitOrder] as the "label" property the area-marker SymbolLayer's `text-field` reads via `"{label}"`. */
internal fun areaMarkersFeatureCollection(areas: List<ForagingArea>): FeatureCollection {
    val features = areas.map { area ->
        Feature.fromGeometry(Point.fromLngLat(area.center.lng, area.center.lat)).apply {
            addStringProperty("label", area.visitOrder.toString())
            addStringProperty("title", "Area ${area.visitOrder}")
            addStringProperty("snippet", foragingAreaSummary(area))
        }
    }
    return FeatureCollection.fromFeatures(features)
}

/**
 * The visiting-order connector, as a single [LineString] feature through the search centre and
 * every area centre in visiting order — an empty [FeatureCollection] when [areas] is empty, since
 * a `LineString` needs at least two points and "no areas" is a real, common state (a fresh search
 * with no clusters yet). Rendered solid, not dashed — see [BREADCRUMB_DASH_PATTERN]'s doc comment
 * for why, and what still keeps this line from reading as a real walkable route now that dashing
 * doesn't.
 */
internal fun connectorFeatureCollection(region: Region, areas: List<ForagingArea>): FeatureCollection {
    if (areas.isEmpty()) return emptyFeatureCollection()
    val orderedPoints = listOf(Point.fromLngLat(region.lng, region.lat)) +
        areas.map { Point.fromLngLat(it.center.lng, it.center.lat) }
    val feature = Feature.fromGeometry(LineString.fromLngLats(orderedPoints))
    feature.addStringProperty("title", "Visiting order")
    feature.addStringProperty("snippet", VISITING_ORDER_DISCLAIMER)
    return FeatureCollection.fromFeature(feature)
}

/**
 * The active track's recorded points as a single [LineString] feature, oldest first — an empty
 * [FeatureCollection] when [points] has fewer than two points (nothing recorded yet, or only the
 * first fix so far), same "a line needs two ends" reasoning as [connectorFeatureCollection].
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
 * Multiplies [color]'s existing alpha by [fraction] (0f–1f), keeping its RGB untouched. Used to
 * turn [MapPalette.NIGHT]'s opaque ink colour into the semi-transparent halo every night icon
 * draws behind its fill — see [NIGHT_ICON_HALO_ALPHA]'s own doc comment.
 */
private fun withAlpha(color: Int, fraction: Float): Int {
    val alpha = ((color ushr 24) * fraction).toInt().coerceIn(0, 255)
    return (alpha shl 24) or (color and 0x00FFFFFF)
}

/**
 * A solid diamond bitmap for the planned-trip [SymbolLayer]'s `icon-image`, distinct in shape and
 * colour from both the translucent sighting-dot circles and the numbered area-marker circles, so
 * "planned" reads as its own kind of pin rather than a variant of either — same intent as the
 * deleted osmdroid `plannedTripIcon`, redrawn because MapLibre's `SymbolLayer` needs a named image
 * registered on the [Style] (`Style.addImage`) rather than a per-`Marker` `Drawable`.
 *
 * [haloColor] is `null` on day (this icon's shape and behaviour are otherwise unchanged there) and
 * a semi-transparent ink on night — see [NIGHT_ICON_HALO_ALPHA]'s doc comment for why. When
 * present, the halo is drawn at this function's original, un-inset diamond bounds, and the coloured
 * fill shrinks to [NIGHT_ICON_INNER_SCALE] of that, centred, so the halo shows as a border.
 */
private fun plannedTripDiamondBitmap(density: Float, markerColor: Int, haloColor: Int? = null): Bitmap {
    val sizePx = (PLANNED_TRIP_MARKER_SIZE_DP * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    fun diamondPath(scale: Float): Path {
        val inset = sizePx * (1f - scale) / 2f
        return Path().apply {
            moveTo(sizePx / 2f, inset)
            lineTo(sizePx - inset, sizePx / 2f)
            lineTo(sizePx / 2f, sizePx - inset)
            lineTo(inset, sizePx / 2f)
            close()
        }
    }

    if (haloColor != null) {
        canvas.drawPath(diamondPath(1f), Paint().apply { color = haloColor; style = Paint.Style.FILL; isAntiAlias = true })
    }
    canvas.drawPath(
        diamondPath(if (haloColor != null) NIGHT_ICON_INNER_SCALE else 1f),
        Paint().apply { color = markerColor; style = Paint.Style.FILL; isAntiAlias = true },
    )
    return bitmap
}

/**
 * A teardrop pin bitmap for the waypoint [SymbolLayer]'s `icon-image` — round head, pointed tail
 * touching the actual coordinate (see [initializeOverlayLayers]'s `iconAnchor(BOTTOM)` for why the
 * tail, not the shape's center, has to be what's anchored). By day, a distinct amber — the "you
 * dropped a pin here" colour convention day mode's other roles don't otherwise use. By night,
 * [MapPalette.NIGHT_WARM] like every other marker; the pin *shape* itself, not colour, is what
 * keeps a waypoint distinct at night — see [MapPalette.NIGHT]'s doc comment, "Fifth pass."
 *
 * [haloColor] follows [plannedTripDiamondBitmap]'s own convention: `null` on day, a semi-
 * transparent ink on night. The halo pin is drawn at this function's original, un-inset bounds —
 * tip included, so [initializeOverlayLayers]'s `iconAnchor(BOTTOM)` still anchors precisely to the
 * real coordinate — and the coloured fill insets uniformly inside it.
 */
private fun waypointPinBitmap(density: Float, markerColor: Int, haloColor: Int? = null): Bitmap {
    val widthPx = (WAYPOINT_MARKER_WIDTH_DP * density).toInt().coerceAtLeast(1)
    val heightPx = (WAYPOINT_MARKER_HEIGHT_DP * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    fun pinPath(scale: Float): Path {
        val inset = widthPx * (1f - scale) / 2f
        val headRadius = widthPx / 2f - inset
        val headCenterX = widthPx / 2f
        val headCenterY = headRadius + inset
        val path = Path().apply {
            addCircle(headCenterX, headCenterY, headRadius, Path.Direction.CW)
        }
        // The tail: a triangle from the circle's sides down to the bottom-center tip, unioned with
        // the circle above so the whole pin fills as one shape.
        path.addPath(
            Path().apply {
                moveTo(headCenterX - headRadius, headCenterY)
                lineTo(headCenterX, heightPx.toFloat() - inset)
                lineTo(headCenterX + headRadius, headCenterY)
                close()
            },
        )
        return path
    }

    if (haloColor != null) {
        canvas.drawPath(pinPath(1f), Paint().apply { color = haloColor; style = Paint.Style.FILL; isAntiAlias = true })
    }
    canvas.drawPath(
        pinPath(if (haloColor != null) NIGHT_ICON_INNER_SCALE else 1f),
        Paint().apply { color = markerColor; style = Paint.Style.FILL; isAntiAlias = true },
    )
    return bitmap
}

/**
 * Night mode's sighting-dot icon: a filled circle with an ink ring, the same visual language day
 * mode's [CircleLayer] draws natively (`circleColor`/`circleStrokeColor`/`circleStrokeWidth`),
 * baked into a bitmap instead because night now differentiates every marker by icon shape — see
 * [MapPalette.NIGHT]'s doc comment, "Fifth pass." The simplest silhouette of the three new night
 * icons, matching this marker's own name: a plain dot is what "sighting dot" already says it is.
 *
 * [haloColor]: see [NIGHT_ICON_HALO_ALPHA]'s doc comment. Drawn first, at the full icon bounds, so
 * it shows beyond the existing ink stroke rather than being covered by it.
 */
private fun sightingDotBitmap(density: Float, fillColor: Int, strokeColor: Int, haloColor: Int): Bitmap {
    val sizePx = (SIGHTING_DOT_ICON_SIZE_DP * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f
    canvas.drawCircle(center, center, center, Paint().apply { color = haloColor; style = Paint.Style.FILL; isAntiAlias = true })
    val strokeWidthPx = SIGHTING_DOT_ICON_STROKE_WIDTH_DP * density
    val fillRadius = center - strokeWidthPx / 2f - (NIGHT_ICON_HALO_MARGIN_DP * density)
    canvas.drawCircle(
        center,
        center,
        fillRadius,
        Paint().apply {
            color = fillColor
            style = Paint.Style.FILL
            isAntiAlias = true
        },
    )
    canvas.drawCircle(
        center,
        center,
        fillRadius,
        Paint().apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            isAntiAlias = true
        },
    )
    return bitmap
}

/**
 * Night mode's search-centre icon: an ink ring around a smaller filled dot — a target/bullseye,
 * reading as "centred here" rather than "a location," distinct from [sightingDotBitmap]'s plain
 * dot and every other night icon's silhouette. Replaces day mode's plain circle-with-white-stroke
 * [CircleLayer] for the same reason as [sightingDotBitmap] — see [MapPalette.NIGHT]'s doc comment.
 *
 * [haloColor]: see [NIGHT_ICON_HALO_ALPHA]'s doc comment. Drawn first, at the full icon bounds, so
 * it shows beyond the target ring rather than being covered by it.
 */
private fun searchCentreTargetBitmap(density: Float, ringColor: Int, dotColor: Int, haloColor: Int): Bitmap {
    val sizePx = (SEARCH_CENTER_ICON_SIZE_DP * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f
    canvas.drawCircle(center, center, center, Paint().apply { color = haloColor; style = Paint.Style.FILL; isAntiAlias = true })
    val ringStrokeWidthPx = SEARCH_CENTER_ICON_RING_WIDTH_DP * density
    val ringRadius = center - ringStrokeWidthPx / 2f - (NIGHT_ICON_HALO_MARGIN_DP * density)
    canvas.drawCircle(
        center,
        center,
        ringRadius,
        Paint().apply {
            color = ringColor
            style = Paint.Style.STROKE
            strokeWidth = ringStrokeWidthPx
            isAntiAlias = true
        },
    )
    canvas.drawCircle(
        center,
        center,
        ringRadius * SEARCH_CENTER_ICON_DOT_RATIO,
        Paint().apply {
            color = dotColor
            style = Paint.Style.FILL
            isAntiAlias = true
        },
    )
    return bitmap
}

/**
 * Night mode's area-marker badge: a rounded-square background for the numbered-area label (still
 * drawn by `AREA_MARKER_LABEL_LAYER_ID` on top of this, unconditionally, day and night — only the
 * background shape this label sits on changes). Distinct from [sightingDotBitmap]'s circle and
 * [searchCentreTargetBitmap]'s ring, so a numbered area reads as its own kind of marker at a
 * glance even before the number itself is legible. Replaces day mode's plain circle [CircleLayer]
 * for the same reason as the other two — see [MapPalette.NIGHT]'s doc comment.
 *
 * [haloColor]: see [NIGHT_ICON_HALO_ALPHA]'s doc comment. Drawn first, at the full icon bounds; the
 * coloured badge insets inside it by [NIGHT_ICON_HALO_MARGIN_DP] on every side.
 */
private fun areaMarkerBadgeBitmap(density: Float, fillColor: Int, haloColor: Int): Bitmap {
    val sizePx = (AREA_MARKER_ICON_SIZE_DP * density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cornerRadiusPx = AREA_MARKER_ICON_CORNER_RADIUS_DP * density
    canvas.drawRoundRect(
        RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()),
        cornerRadiusPx,
        cornerRadiusPx,
        Paint().apply { color = haloColor; style = Paint.Style.FILL; isAntiAlias = true },
    )
    val margin = NIGHT_ICON_HALO_MARGIN_DP * density
    canvas.drawRoundRect(
        RectF(margin, margin, sizePx - margin, sizePx - margin),
        cornerRadiusPx,
        cornerRadiusPx,
        Paint().apply {
            color = fillColor
            style = Paint.Style.FILL
            isAntiAlias = true
        },
    )
    return bitmap
}

// Source/layer ids. Fixed strings rather than generated, since every one of them is referenced by
// name from at least two places (initializeOverlayLayers and refreshOverlayData, or a layer
// referencing its source) and a typo needs to be a compile error, not a silently-missing layer.
private const val SEARCH_CENTER_SOURCE_ID = "search-center"
private const val SEARCH_CENTER_LAYER_ID = "search-center-layer"
private const val SIGHTING_SOURCE_ID = "sightings"
private const val SIGHTING_LAYER_ID = "sightings-layer"
private const val CONNECTOR_SOURCE_ID = "visiting-order-connector"
private const val CONNECTOR_LAYER_ID = "visiting-order-connector-layer"
private const val AREA_MARKER_SOURCE_ID = "area-markers"
private const val AREA_MARKER_CIRCLE_LAYER_ID = "area-markers-circle-layer"
private const val AREA_MARKER_LABEL_LAYER_ID = "area-markers-label-layer"
private const val PLANNED_TRIP_SOURCE_ID = "planned-trips"
private const val PLANNED_TRIP_LAYER_ID = "planned-trips-layer"
private const val PLANNED_TRIP_ICON_ID = "planned-trip-diamond"
private const val BREADCRUMB_SOURCE_ID = "breadcrumb-trail"
private const val BREADCRUMB_LAYER_ID = "breadcrumb-trail-layer"
private const val WAYPOINT_SOURCE_ID = "waypoints"
private const val WAYPOINT_LAYER_ID = "waypoints-layer"
private const val WAYPOINT_ICON_ID = "waypoint-pin"
private const val SIGHTING_DOT_ICON_ID = "sighting-dot"
private const val SEARCH_CENTER_ICON_ID = "search-centre-target"
private const val AREA_MARKER_ICON_ID = "area-marker-badge"

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
private const val SIGHTING_DOT_STROKE_WIDTH_PX = 1.5f
private const val SIGHTING_DOT_STROKE_OPACITY = 0.85f
private const val AREA_MARKER_FONT_SIZE_PX = 14f
private const val AREA_MARKER_RADIUS_PX = 16f

// Connector now thinner than breadcrumb (was the other way around) -- a lighter line weight
// reinforces "suggested order, not a real path" now that dash pattern alone no longer carries
// that signal. See BREADCRUMB_DASH_PATTERN's own doc comment.
private const val CONNECTOR_STROKE_WIDTH_PX = 4f

private const val PLANNED_TRIP_MARKER_SIZE_DP = 22f

private const val SEARCH_CENTER_RADIUS_PX = 8f
private const val SEARCH_CENTER_STROKE_WIDTH_PX = 2f

// Night-only icon sizes -- day mode keeps drawing SEARCH_CENTER_RADIUS_PX/SIGHTING_DOT_RADIUS_PX/
// AREA_MARKER_RADIUS_PX above via CircleLayer directly, unaffected by these.
private const val SIGHTING_DOT_ICON_SIZE_DP = 16f
private const val SIGHTING_DOT_ICON_STROKE_WIDTH_DP = 1.5f
private const val SEARCH_CENTER_ICON_SIZE_DP = 18f
private const val SEARCH_CENTER_ICON_RING_WIDTH_DP = 2.5f
private const val SEARCH_CENTER_ICON_DOT_RATIO = 0.4f
private const val AREA_MARKER_ICON_SIZE_DP = 26f
private const val AREA_MARKER_ICON_CORNER_RADIUS_DP = 6f

// The night-icon halo -- see initializeOverlayLayers' nightHaloColor and the *Bitmap functions'
// own doc comments ("darkened transparent borders around the icons") for the problem this solves:
// once BasemapStyles.kt stopped dimming the night ground, MapPalette.NIGHT_WARM/NIGHT_INK no
// longer clear a contrast floor against it on their own, so every night icon now draws this halo
// behind its fill instead, keeping it visible against a light or dark ground alike.
//
// NIGHT_ICON_HALO_ALPHA: how opaque the halo ring is, as a fraction of NIGHT_INK's own alpha
// (which is fully opaque, so this is the halo's effective opacity outright). High enough to read
// as a solid border against either a pale or a dark tile, low enough that it doesn't itself read
// as a second, competing marker.
private const val NIGHT_ICON_HALO_ALPHA = 0.55f

// NIGHT_ICON_INNER_SCALE: the coloured fill shrinks to this fraction of the full icon bounds,
// centred, so the halo shows as a visible border rather than being fully covered by the fill.
private const val NIGHT_ICON_INNER_SCALE = 0.82f

// NIGHT_ICON_HALO_MARGIN_DP: for the icons whose fill is inset by a fixed margin rather than a
// uniform scale (sightingDot/searchCentre/areaMarker, all of which already had a stroke or ring
// width to reason about), the same border-width intent as NIGHT_ICON_INNER_SCALE expressed as an
// absolute inset instead, so the halo border reads as roughly the same visual width across icons.
private const val NIGHT_ICON_HALO_MARGIN_DP = 2f

// Breadcrumb thicker than connector now (was the other way around) -- see CONNECTOR_STROKE_WIDTH_PX.
private const val BREADCRUMB_STROKE_WIDTH_PX = 6f

private const val WAYPOINT_MARKER_WIDTH_DP = 22f
private const val WAYPOINT_MARKER_HEIGHT_DP = 28f

/**
 * A short dash with round line caps ([Property.LINE_CAP_ROUND], already set on the breadcrumb
 * layer) renders as a trail of small dots — "breadcrumbs" should look like breadcrumbs, the
 * project owner's own reasoning for moving the dash here from the connector. Not the connector's
 * former 18:14 ratio, carried over unchanged: that ratio reads as a conventional dashed *line*
 * (long dash, short gap), the right look for "this is a diagram annotation, not a path," but the
 * wrong look for a trail of dots. `4:10` (a short mark, a gap two and a half times longer) is
 * tuned for the dot read instead, in the same line-width-relative units
 * [PropertyFactory.lineDasharray] always took (see [BREADCRUMB_STROKE_WIDTH_PX]).
 *
 * **The connector was dashed for a safety reason, not a style one — "solid" would misleadingly
 * read as a real walkable path** (`README.md`'s "not a walking route" section; this project has no
 * trail-graph data, so a solid line could route someone across a river, a cliff, or private land).
 * Moving the dash to breadcrumb and making the connector solid was a deliberate choice made and
 * confirmed knowing this, not an oversight — two things now carry that signal in its place instead
 * of dashing: [CONNECTOR_STROKE_WIDTH_PX] is deliberately thinner than [BREADCRUMB_STROKE_WIDTH_PX]
 * (a lighter line reads as an annotation, not a route), and [VISITING_ORDER_DISCLAIMER] (surfaced
 * both as a standing on-screen caption and this feature's info-window snippet) states the "not a
 * route" property in words, not just line style. Revisit if a future hardware pass finds the
 * thinner-solid line alone is not read as clearly non-routable as the dash was.
 *
 * `internal`: this is the actual array [PropertyFactory.lineDasharray] receives in production, not
 * a copy. `SightingsMapOverlayDataTest` asserts it is non-empty — the closest a headless JVM test
 * can get to "the breadcrumb trail is still dashed" (see [searchCenterFeatureCollection]'s doc
 * comment for why nothing here can construct the real, native-backed [LineLayer] that actually
 * renders it). Whether MapLibre draws it as visibly dot-like on a real basemap, and whether the
 * connector's now-solid, now-thinner line still reads clearly as "not a route," are both
 * hardware-only questions, not yet re-confirmed.
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
