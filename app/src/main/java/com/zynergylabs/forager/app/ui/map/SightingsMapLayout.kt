package com.zynergylabs.forager.app.ui.map

import android.util.Log
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.activeLayerCredits
import org.maplibre.android.location.modes.CameraMode

/**
 * What [SightingsMap] lays out, in its paint and touch order: the MapView in an [AndroidView] (its factory attaching
 * the one touch listener, which never consumes), the cover picture over it while a returning map reloads, and the
 * always-visible credit caption. Moved whole from [SightingsMap] by UI split Phase 2.2, PR 10 (dispatch
 * 2026-10-19-29), called last there, where it stood, so it is still composed after every effect and released first.
 * [modifier] is [SightingsMap]'s own; [fanSpace] is the latest one, as the layout always read. Holder states and
 * objects are read and written through [state]. The comments inside are the block's own.
 */
@Composable
internal fun SightingsMapLayout(
    state: SightingsMapState,
    modifier: Modifier,
    basemap: Basemap,
    useOfflineTiles: Boolean,
    drawnLayersState: MapLayersState,
    nightMode: Boolean,
    bottomInset: Dp,
    fanSpace: MapFanSpace,
) {
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
                state.mapView.setOnTouchListener { view, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    // Dispatch -463: the elastic nudge reads the finger here, before MapLibre does, and
                    // never consumes it.
                    state.nudgeElastic.onTouch(event)
                    // Dispatch -477: read before MapLibre handles this event, so a release while following
                    // reaches MapLibre with its fling switched off.
                    state.nudgeFlingGuard.onTouch(
                        event,
                        stillFollowing = state.navigationView != null &&
                            state.mapLibreMap?.locationComponent?.let { it.isLocationComponentActivated && it.cameraMode != CameraMode.NONE } == true,
                    )
                    // Dispatch 2026-10-19-37: the plain map's nudge, after navigation's; it never consumes either.
                    state.plainNudge.onTouch(event, view.resources.displayMetrics.density)
                    // Dispatch -477, Part A: each release while navigating, whether the map still follows and
                    // how far the finger travelled. Logging only.
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> { state.touchDownAt[0] = event.x; state.touchDownAt[1] = event.y }
                        MotionEvent.ACTION_UP -> if (state.navigationView != null) {
                            val travelDp = kotlin.math.hypot(event.x - state.touchDownAt[0], event.y - state.touchDownAt[1]) / view.resources.displayMetrics.density
                            val mode = state.mapLibreMap?.locationComponent?.takeIf { it.isLocationComponentActivated }?.cameraMode
                            Log.i(NAVIGATION_VIEW_LOG_TAG, "touch released: mode=$mode, travel=${"%.0f".format(travelDp)} dp")
                        }
                    }
                    false
                }
                state.mapView
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
                .onSizeChanged {
                    state.mapSizePx = it
                    state.navigationMapHeightPx = it.height
                }
                .onViewportResized { state.mapView.post { state.mapLibreMap?.let(state::reanchorFocusedBubble) } },
        )
        // RECORD -761: the cover, over the map and under everything else; no pointer input, so touches reach the map.
        if (state.coverPicture != null && state.coverPhase != CoverPhase.GONE) {
            MapReturnCoverLayer(
                picture = state.coverPicture,
                handedOver = state.coverPhase == CoverPhase.HANDED_OVER,
                onGone = {
                    state.coverPhase = CoverPhase.GONE
                    Log.i(MAPS_COMEBACK_LOG_TAG, "cover: gone, picture released")
                },
                modifier = Modifier.onSizeChanged {
                    if (state.coverPhase == CoverPhase.COVERING && (it.width != state.coverPicture.width || it.height != state.coverPicture.height)) {
                        Log.w(MAPS_COMEBACK_LOG_TAG, "cover: window ${it.width}x${it.height} is not the picture's ${state.coverPicture.width}x${state.coverPicture.height}; dropped")
                        state.coverPhase = CoverPhase.GONE
                    }
                },
            )
        }
        // The always-visible attribution line CopyrightOverlay used to draw directly onto the
        // osmdroid MapView. MapLibre has its own tap-to-reveal attribution control
        // (UiSettings.isAttributionEnabled, on by default) built from each style source's own
        // "attribution" field (see styleJsonFor), but that is deliberately not relied on alone here
        // — see Basemap's doc comment on [Basemap.attribution] for why an always-drawn guarantee
        // matters for this app's USGS/ODbL credit and shouldn't quietly become tap-only.
        // A list of credits since map layers L0a (A5): the basemap's, then each visible layer's own
        // (none of today's layers has one, so the text is exactly what it always was).
        MapCreditCaption(
            text = attributionCaption(mapCreditsFor(basemap, useOfflineTiles, activeLayerCredits(MAP_LAYER_REGISTRY, drawnLayersState), nightMode)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = bottomInset)
                // RECORD -954: its laid-out width, for the "i" beside it in landscape.
                .onSizeChanged { state.creditWidthPx = it.width },
        )
    }
}
