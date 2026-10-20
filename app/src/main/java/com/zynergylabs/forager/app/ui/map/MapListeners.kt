package com.zynergylabs.forager.app.ui.map

import android.content.res.Resources
import android.util.Log
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.fanout.CameraMoveCause
import com.zynergylabs.forager.app.ui.map.fanout.MapTapHandler
import com.zynergylabs.forager.app.ui.map.fanout.MapTapSinks
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.layerPaintFor
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapView

/**
 * Every MapLibre listener this map registers, and the map-ready callback that registers them, in one
 * `DisposableEffect` keyed on the MapView: moved whole from [SightingsMap] by UI split Phase 2.2, PR 9 (dispatch
 * 2026-10-19-29), called from the place it stood there, just after [MapLifecycle], and keyed exactly as it was (the same
 * MapView, now reached as [SightingsMapState.mapView]). The registration order inside `getMapAsync` is unchanged.
 *
 * Captured once, at registration, as before: [resources] (the tap probe's density) and [fanSpace] (the tap handler's
 * room; `rememberMapFanSpace` is keyed on the keep-outs, and the handler keeps the space it was registered with while
 * the layout reads the latest). Everything else is read when a listener fires, through [state]: the latest callbacks
 * and lists, the map handle, the loaded style, the cover phase, the camera bookkeeping. The comments inside are the
 * block's own.
 */
@Composable
internal fun MapListeners(
    state: SightingsMapState,
    resources: Resources,
    fanSpace: MapFanSpace,
) {
    // Registered once per composition of this MapView, not per recomposition: getMapAsync's
    // callback fires exactly once for the life of the MapView, so there's no re-registration to
    // guard against the way the style reload's guard (MapStyleReload.kt) is needed for setStyle.
    DisposableEffect(state.mapView) {
        // A style that cannot be loaded — the offline style with no region covering the camera
        // and no network, or a worker URL that moved — fails here rather than in setStyle's own
        // callback, which simply never fires. Logged, never silently swallowed (CLAUDE.md); the
        // message is MapLibre's own. addOnDidFailLoadingMapListener verified against the pinned
        // 13.5.0 artifact with javap, the same way every other SDK call in this file was.
        state.mapView.addOnDidFailLoadingMapListener { message ->
            Log.w(SIGHTINGS_MAP_TAG, "MapLibre failed to load the map style: $message")
        }
        // RECORD -752 (dispatch 2026-09-28-755, item 4): logging only, to find the S22's blank and magnified frame on a return
        // to Maps. The first few frames this MapView renders, with its size and camera then; the restore's own logs are in MapStyleReload.kt.
        // A MapView cannot run under Robolectric, so this is read from the phone's logcat (MAPS_COMEBACK_LOG_TAG).
        val firstFramesLogged = intArrayOf(0)
        val frameLogger = MapView.OnDidFinishRenderingFrameListener { fully, _, _ ->
            // RECORD -761: the first full frame after the style has loaded (the camera was restored before it) takes over from
            // the cover.
            if (fully && state.loadedStyle != null && state.coverPhase == CoverPhase.COVERING) {
                state.coverPhase = CoverPhase.HANDED_OVER
                Log.i(MAPS_COMEBACK_LOG_TAG, "cover: handed over at the first full frame, zoom=${state.mapLibreMap?.cameraPosition?.zoom}")
            }
            if (firstFramesLogged[0] < MAPS_COMEBACK_FRAMES_LOGGED) {
                firstFramesLogged[0]++
                val camera = state.mapLibreMap?.cameraPosition
                Log.i(MAPS_COMEBACK_LOG_TAG, "frame ${firstFramesLogged[0]} fully=$fully view=${state.mapView.width}x${state.mapView.height} render=${state.mapView.renderView.width}x${state.mapView.renderView.height} zoom=${camera?.zoom} target=${camera?.target} style=${state.loadedStyle != null}")
            }
        }
        state.mapView.addOnDidFinishRenderingFrameListener(frameLogger)
        state.mapView.getMapAsync { map ->
            // Marker fan-out (dispatch 2026-09-28-197): the tap is decided by MapTapHandler, which is the
            // resolution this listener used to do inline (resolveTap over every tappable layer, a point
            // query then a TAP_BOX_DP box, then mapTapOutcome), with the fan-out in front of and behind it.
            // The four outcomes come back through the sinks below, unchanged from what the listener did.
            val handler = MapTapHandler(
                fan = state.fanOut,
                probe = MapLibreProbe(map, resources.displayMetrics.density) { key ->
                    // Where a fanned record is now, from the lists the map draws (the projection of it is the probe's): the
                    // same lookup a shown bubble's re-anchoring uses. Null once the record is no longer drawn.
                    focusedFeaturePosition(FocusedMapFeature(key.layerId, key.featureId), state.plannedTrips, state.waypoints, state.findMarkers, state.photoMarkers)
                },
                space = fanSpace,
                // One layer at a time (amendment -255): a bubble showing on a sighting or a point glyph, which is
                // what a fanned marker's bubble is, means an empty-map tap closes it and leaves the fan.
                bubbleOpen = { state.focusedObservationId != null || state.focusedFeature != null },
                drawOrder = { orderedLayers(MAP_LAYER_REGISTRY, state.layersState) },
                // A record on a layer switched off is not drawn, so it is not a fan member.
                warn = { message -> Log.w(SIGHTINGS_MAP_TAG, message) },
                layerDrawn = { id -> MAP_LAYER_REGISTRY.firstOrNull { it.id == id }?.let { layerPaintFor(it, state.layersState).visible } ?: false },
                sinks = object : MapTapSinks {
                    override fun onPlainTap() = state.onTap()

                    override fun onSightingTap(observationId: Long?, xPx: Float, yPx: Float) {
                        // The sighting path as before L0a: the dot's observationId looked back up in
                        // the current list, the bubble anchored at the tap point, onTap when the id no
                        // longer resolves.
                        val tappedSighting = observationId?.let { id -> state.sightings.firstOrNull { it.observationId == id } }
                        if (tappedSighting != null) {
                            state.onSightingTap(tappedSighting, Offset(xPx, yPx), map.cameraPosition.bearing.toFloat())
                        } else {
                            state.onTap()
                        }
                    }

                    override fun onFeatureTap(layerId: String, featureId: String, xPx: Float, yPx: Float, at: LatLng) {
                        // M1 (owner's ruling 1, "Bubble only"): a feature tap opens its bubble and nothing
                        // else, as a sighting tap already did.
                        state.onFeatureTap(MapFeatureTap(layerId, featureId, Offset(xPx, yPx), map.cameraPosition.bearing.toFloat(), at))
                    }

                    override fun onCloseBubble() = state.onCloseBubble()

                    override fun onUnidentifiedFeature(layerId: String) {
                        // Logged, never silent: a feature with no id cannot name its record, so no
                        // bubble can show, and the tap is taken as a plain one.
                        Log.w(SIGHTINGS_MAP_TAG, "Tapped a feature on $layerId with no $FEATURE_ID_PROPERTY; taken as a plain tap.")
                        state.onTap()
                    }
                },
            )
            state.tapHandlerRef.handler = handler
            map.addOnMapClickListener { latLng ->
                // toScreenLocation confirmed via javap against the pinned org.maplibre.gl:android-sdk:13.5.0.
                val screenPoint = map.projection.toScreenLocation(latLng)
                handler.onMapTap(LatLng(latLng.latitude, latLng.longitude), screenPoint.x, screenPoint.y)
                // false: unconsumed, matching the deleted osmdroid MapEventsOverlay's
                // singleTapConfirmedHelper — a plain tap isn't meant to swallow the event.
                false
            }
            map.addOnMapLongClickListener { latLng ->
                state.onLongPress(LatLng(latLng.latitude, latLng.longitude))
                true
            }
            // Only a touch counts: REASON_API_GESTURE is dispatched from MapGestureDetector's
            // listeners alone, every programmatic move from Transform as REASON_API_ANIMATION —
            // see MapRenderMode.onUserCameraGesture's doc comment for the javap check.
            // Dispatch 2026-09-28-430, ruling C: while navigating the map turns with the compass and
            // rarely rests, so a shown bubble is kept on its glyph on every camera move, not only at
            // idle. Outside navigation it is re-anchored at idle as before.
            map.addOnCameraMoveListener {
                if (state.navigationView != null) state.reanchorFocusedBubble(map)
                state.navigationModeChange.noteCameraMove(map) // dispatch -470, Part A: logging only
            }
            // Dispatch -477, Part A: every fling MapLibre reports while navigating, with the camera mode then.
            // Logging only.
            map.addOnFlingListener {
                if (state.navigationView != null) {
                    Log.i(NAVIGATION_VIEW_LOG_TAG, "fling reported: mode=${map.locationComponent.takeIf { it.isLocationComponentActivated }?.cameraMode}")
                }
            }
            map.addOnCameraMoveStartedListener { reason ->
                state.navigationModeChange.noteCameraMoveStarted(reason) // dispatch -470, Part A: logging only
                // A fanned stack folds on a camera move the user made or asked for, a gesture or the app's own; it stays open when the map re-centres
                // itself while it follows the location (dispatch 2026-09-28-380: the user did not mean that move, and the fan travels with the map).
                val following = map.locationComponent.isLocationComponentActivated && map.locationComponent.cameraMode != CameraMode.NONE
                val cause = state.cameraMoveClassifier.classify(isUserCameraGesture(reason), following)
                if (cause == CameraMoveCause.UNKNOWN) {
                    Log.w(SIGHTINGS_MAP_TAG, "A camera move (reason $reason) is not a touch, not marked as the app's and not the location follower's; the open fan is folded as it always was.")
                }
                state.tapHandlerRef.handler?.onCameraMoveStarted(cause)
                if (isUserCameraGesture(reason)) state.onUserCameraGesture()
            }
            // OnCameraIdleListener.onCameraIdle() takes no argument (verified via javap against
            // the pinned org.maplibre.gl:android-sdk:13.5.0 artifact — MapLibreMap$OnCameraIdleListener
            // declares only `void onCameraIdle()`), so the position has to be read back explicitly
            // via getCameraPosition() inside the callback, not received as a parameter the way
            // addOnMapLongClickListener's latLng is.
            map.addOnCameraIdleListener {
                state.cameraMoveClassifier.onCameraIdle()
                state.cameraIdleCount++
                // CameraPosition.target is declared `LatLng?` in the pinned SDK itself (verified via
                // javap: the vendor's own constructor carries an org.jetbrains.annotations.Nullable
                // on this parameter) — null before the map has finished laying out a first camera
                // position, which an idle event can fire for. Nothing to report yet in that case, so
                // this skips the callback rather than fabricating a coordinate.
                map.cameraPosition.target?.let { target ->
                    state.onCameraIdle(LatLng(target.latitude, target.longitude))
                }
                // Part 1 layout fixes, item 4: the camera this map settled on, for the map that comes
                // back after a tab change. Not before this MapView's first style has loaded: until then
                // the camera is the SDK's default, and the restore has yet to read what was saved.
                if (state.loadedStyle != null) {
                    val camera = map.cameraPosition
                    camera.target?.let { target ->
                        state.cameraMemory?.saved = MapCameraSnapshot(
                            target = LatLng(target.latitude, target.longitude),
                            zoom = camera.zoom,
                            bearing = camera.bearing,
                            tilt = camera.tilt,
                            following = map.locationComponent.isLocationComponentActivated &&
                                map.locationComponent.cameraMode != CameraMode.NONE,
                            appliedTarget = state.lastAppliedCameraTarget,
                        )
                    }
                }
                // Item 8 (dispatch 2026-09-29-57, amendment -262, "Remember and reopen"): the fan a user left when they opened a
                // find on the Journal reopens here, at the first idle after this map's style has loaded (the camera memory's
                // restore has been applied by then, and the style load's own content fold, onContentChanged, has run), through
                // openFanFor. A reopen that comes to nothing is logged, never silent. Whether a later camera move or content
                // change folds it again is device-only: a real MapView cannot be built under Robolectric.
                state.fanReopen.onCameraIdle(state.loadedStyle != null)
                // Keeps a shown bubble glued to its glyph across a pan, zoom or rotate gesture: see
                // reanchorFocusedBubble, which a rotation of the device also calls (onViewportResized, in SightingsMapLayout.kt).
                state.reanchorFocusedBubble(map)
            }
            // MapLibre's own tap-to-reveal attribution control defaults to bottom-start — the same
            // corner this composable's own always-visible Basemap.attribution caption occupies (see
            // SightingsMapLayout.kt), so the two painted over each other: MapLibre's control and this app's own
            // basemap-specific credit, both real and both required, neither readable. Moved to
            // bottom-end so each has its own corner rather than trying to stack them, which would
            // need a margin computed from this caption's own (basemap-dependent) text height to stay
            // correct — confirmed via javap against the pinned org.maplibre.gl:android-sdk artifact
            // that UiSettings exposes setAttributionGravity/setAttributionMargins for exactly this.
            map.uiSettings.setAttributionGravity(Gravity.BOTTOM or Gravity.END)
            // Part 1 layout fixes, item 5: MapLibre's own margins, which MapAttributionInsets.kt adds the insets to.
            state.attributionDefaultMargins = map.uiSettings.let {
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
            state.mapLibreMap = map
        }
        onDispose { state.mapView.removeOnDidFinishRenderingFrameListener(frameLogger) }
    }
}
