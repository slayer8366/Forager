package com.zynergylabs.forager.app.ui.map

import android.content.Context
import android.content.res.Resources
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import com.zynergylabs.forager.app.map.OFFLINE_STYLE_URL
import com.zynergylabs.forager.app.map.currentOfflineRenderStyleUrl
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
import org.maplibre.android.maps.Style

/**
 * The style swap: one effect, moved whole from [SightingsMap] by UI split Phase 2.2 (dispatch 2026-10-19-29), called
 * from the place it stood there and keyed exactly as it was. Every parameter but [state] is a value [SightingsMap]
 * already had, captured when the effect launches, as before (so `setStyle`'s callback still sees the
 * [trackLiveLocation], [context] and engines of that launch). The loaded and applied style, the camera target and
 * the latest look, layers, chrome colour, camera memory and navigation view are read and written through [state],
 * on the same States as before. The comments inside are the effect's own.
 */
@Composable
internal fun MapStyleReload(
    state: SightingsMapState,
    basemap: Basemap,
    useOfflineTiles: Boolean,
    nightMode: Boolean,
    nightModeLoaded: Boolean,
    trackLiveLocation: Boolean,
    context: Context,
    resources: Resources,
    appCompassEngine: TrueHeadingCompassEngine?,
    appLocationEngine: AppLocationEngine?,
    /**
     * Step 8b: set on each style load to whether the style draws the official-trails band
     * ([applyReferenceTrailsOnStyleLoad]); [SightingsMap] draws, credits and reports with it.
     */
    referenceTrailsLoaded: MutableState<Boolean>,
    /** Step 8b: the layer choices as the map was given them, before the band is hidden for a style without it. */
    chosenLayers: State<MapLayersState>,
) {
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
    // data+camera refresh effect (MapDataRefresh.kt, called right after this) re-pushes their content keyed on loadedStyle. One thing
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
    // RECORD -1513: the switch as the map uses it, off for the rest of the process once the own Street has failed to load
    // (OwnStreetFallback.kt). Read here, in composition, so the fallback relaunches this effect and reloads the style.
    val ownStreet = OWN_STREET_FALLBACK.ownStreet()
    // Dispatches 2026-10-27-11 and -13 (RECORD -1742, -1748): patterns lost on the tiles a reload keeps, a MapLibre 13.5.0
    // fault worked around in PatternRelayout.kt. Told of each request below and each callback; it listens for the sprite itself.
    val patternRelayout = rememberPatternRelayout(state.mapView)
    LaunchedEffect(state.mapLibreMap, basemap, useOfflineTiles, nightMode, nightModeLoaded, ownStreet) {
        val map = state.mapLibreMap ?: return@LaunchedEffect
        // null until the Night Maps preference has loaded (the cold-launch gate): the effect
        // relaunches when nightModeLoaded turns true, and the first style it requests is then the
        // right one. The map shows MapLibre's own blank until then.
        // useOfflineTiles is also on for OSM or Topo with no signal (NoSignalTrailMap.kt): the regions' own style either way.
        val offlineStyleUrl = if (useOfflineTiles) currentOfflineRenderStyleUrl(context) else OFFLINE_STYLE_URL
        val requested = requestedMapStyle(
            basemap = basemap,
            useOfflineTiles = useOfflineTiles,
            nightMode = nightMode,
            nightModeLoaded = nightModeLoaded,
            offlineStyleUrl = offlineStyleUrl,
            ownStreet = ownStreet,
        ) ?: return@LaunchedEffect
        if (!needsStyleReload(state.appliedStyle, requested)) return@LaunchedEffect
        // Captured before setStyle below discards the LocationComponent entirely (see
        // activateLiveLocationIfPermitted's own doc comment on why re-activation is needed at
        // all) — null only the very first time this composable ever activates the puck;
        // CameraMode.NONE if the user had already broken tracking by panning/zooming;
        // CameraMode.TRACKING if they hadn't. Restoring exactly this, rather than always
        // re-forcing TRACKING, is the fix for a real hardware report: switching basemap (or
        // toggling night mode, which goes through this same style-swap path on every basemap)
        // was recentering the
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
        val cameraRestore = cameraRestoreFor(if (state.appliedStyle == null) state.cameraMemory?.saved else null, previousCameraMode)
        cameraRestore?.let {
            Log.i(MAPS_COMEBACK_LOG_TAG, "restore before the style: view=${state.mapView.width}x${state.mapView.height} to zoom=${it.zoom} target=${it.target} (camera was zoom=${map.cameraPosition.zoom})")
            state.cameraMoveClassifier.markAppMove()
            applyCameraRestore(map, it)
        }
        map.setMaxZoomPreference(operatingMaxZoom(basemap, ownStreet).toDouble())
        val source = mapStyleSourceFor(basemap, night = requested.night, useOfflineTiles = useOfflineTiles, offlineStyleUrl = requested.offlineStyleUrl, ownStreet = ownStreet)
        // Whose load a failure is: the failure listener (MapListeners.kt) gets only MapLibre's message (RECORD -1513).
        // Assumption, not verified: a later setStyle cancels this request, so any failure belongs to the latest lastStyleRequest.
        state.lastStyleRequest = MapStyleRequest(source, useOfflineTiles)
        val builder = when (source) {
            is MapStyleSource.Json -> Style.Builder().fromJson(source.json)
            is MapStyleSource.Uri -> Style.Builder().fromUri(source.uri)
            is MapStyleSource.StreetWithTopoOver -> streetWithTopoOverBuilder(source)
        }
        // Before setStyle, while the current style can still be written (a pending restore runs here). The MapView's first
        // style is told apart so it is not relaid out: it keeps no tiles from an earlier style (PatternRelayout.kt).
        patternRelayout.onStyleRequested(firstStyleOnThisMapView = state.appliedStyle == null)
        map.setStyle(builder) { style ->
            // The symbol fade off for the whole style, before anything is drawn on it (PlacementTransitions.kt).
            disableSymbolFade(style)
            // Offline night (colour build C1 (d)): the offline style has no raster layer for
            // NIGHT_RASTER_PAINT to act on, so its own layers are recoloured here, after it loads
            // from its one URL and before the overlays are added, so the overlays' own line and
            // fill layers are never touched. Day, or night off, restyles nothing: the style came
            // fresh from its URI.
            // Since dispatch 2026-10-18-05 Part 2 the same recolour serves the own Street online (loaded by URI too),
            // and walks text, halo and icon colours; with the own Street off, a URI style is only ever the offline one.
            // The own Street takes its night water exception (RECORD -1343); the offline style keeps pure inversion.
            // Street v4 (the fill-in) has its own night tables, chosen by the style's URI (StreetNightTables.kt); every other
            // Trail Map version, and Street under Topographical, keeps v1 to v3's.
            if (requested.night && source !is MapStyleSource.Json) {
                applyOfflineNightRecolour(style, streetWater = usesStreetSprite(source), streetTables = streetNightTablesFor((source as? MapStyleSource.Uri)?.uri))
            }
            // Trail Map's night icons and patterns: the bundled night copies, added under their own names and switched to
            // (NightImageSwitch.kt; dispatch 2026-10-22-06 Amendment 1). The sprite is not loaded yet at this point.
            if (requested.night && usesStreetSprite(source)) applyStreetNightImages(style, context)
            if (source is MapStyleSource.StreetWithTopoOver) hideStreetLabelsUnderTopo(style, source.streetLabelsMaxZoom)
            // Step 8b (dispatch 2026-10-26-15): the official-trails band's visibility from the user's choice, after the night
            // recolour (which walks every layer, hidden or not, so the band is in its night colour whenever it shows) and
            // before the overlays are added above it. Under Topographical the band is not offered (Trail Map only, -1633).
            referenceTrailsLoaded.value = applyReferenceTrailsOnStyleLoad(MapLibreStyleLayerSwitches(style), drawnOnThisMap = source !is MapStyleSource.StreetWithTopoOver, chosen = chosenLayers.value)
            initializeOverlayLayers(
                style,
                density = resources.displayMetrics.density,
                palette = requested.palette,
                drawOrder = orderedLayers(MAP_LAYER_REGISTRY, state.layersState),
                layersState = state.layersState,
                chromeColour = state.chromeColour,
            )
            state.fanCircleColour[0] = state.chromeColour
            // Dispatch 2026-09-28-510: the approximate and last known dots, by name, before the dot is activated
            // below; setStyle discards images with everything else, so on every style load.
            if (appLocationEngine != null) addPositionLookImages(style, context)
            // The data+camera refresh effect (MapDataRefresh.kt) re-pushes every source right after this, keyed
            // on loadedStyle among other things — including the sighting source, with "selected"
            // baked in from whatever focusedObservationId is current at that point. Nothing here
            // needs to seed it separately.
            // Item 4: again once the style has loaded, in case a style's own default camera replaced it,
            // and the region target recorded before loadedStyle wakes the data+camera effect (MapDataRefresh.kt).
            cameraRestore?.let {
                Log.i(MAPS_COMEBACK_LOG_TAG, "restore on the style load: view=${state.mapView.width}x${state.mapView.height}, camera was zoom=${map.cameraPosition.zoom} target=${map.cameraPosition.target}")
                state.cameraMoveClassifier.markAppMove()
                applyCameraRestore(map, it)
                state.lastAppliedCameraTarget = it.appliedTarget
            }
            state.appliedStyle = requested
            state.loadedStyle = style
            // setStyle discards the previous style's LocationComponent state the same way it does
            // this composable's own layers (see initializeOverlayLayers' own doc comment on why
            // that function re-runs here) — so the live-location "puck" needs the same
            // re-activate-on-every-new-style treatment. Guarded by trackLiveLocation — see that
            // parameter's own doc comment for why a historical-place map instance must never seize
            // the camera for the device's current location at all.
            if (trackLiveLocation) {
                state.cameraMoveClassifier.markAppMove() // activating (or re-activating, after a style swap) sets the camera mode and may ease the zoom
                state.navigationModeChange.byTheApp {
                    activateLiveLocationIfPermitted(map, style, context, restoreCameraMode = cameraRestore?.cameraMode ?: previousCameraMode, compassEngine = appCompassEngine, locationEngine = appLocationEngine, look = state.positionLook, nudgeSpringBack = state.nudgeSpringBack)
                    state.appliedPositionLook[0] = state.positionLook
                    Log.i(POSITION_DOT_LOG_TAG, "activated on a style load with look ${state.positionLook}")
                }
                state.navigationModeChange.listenTo(map) { state.navigationView }
                state.navigationModeChange.gestureProtection = false
            }
            // Last, once the style holds every layer and image reference it will (the night step's included): on a reload, once
            // the sprite has also loaded, the pattern layers are cleared after the style's transition (at least 300 ms) plus
            // 100 ms and restored at least 100 ms later (PatternRelayout.kt).
            patternRelayout.onStyleLoaded(style)
        }
    }
}
