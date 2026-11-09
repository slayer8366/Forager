package com.zynergylabs.forager.app.ui.map

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.zynergylabs.forager.app.domain.fixOrNull
import org.maplibre.android.location.modes.CameraMode

/**
 * The location dot's three effects (the app's position into the dot, the dot's look, and locate), moved whole and in
 * their order from [SightingsMap] by UI split Phase 2.2 (dispatch 2026-10-19-29), called from the place they stood
 * there and keyed exactly as they were. Every parameter but [state] is a value [SightingsMap] already had, captured
 * when an effect launches, as before; the latest look and navigation view are read through [state], as
 * `currentPositionLook` and `currentNavigationView` were. The comments inside are the effects' own.
 */
@Composable
internal fun MapLocationDot(
    state: SightingsMapState,
    appLocationEngine: AppLocationEngine?,
    mapPosition: MapPosition?,
    positionLook: PositionLook,
    trackLiveLocation: Boolean,
    resumeTrackingRequestId: Int,
    context: Context,
    appCompassEngine: TrueHeadingCompassEngine?,
) {
    // Dispatch 2026-09-28-510: the app's position into the dot. A new fix of any kind is handed to
    // MapLibre (AppLocationEngine drops repeats). Keyed on the map as before: the label it once also
    // reported to went with dispatch 2026-09-28-535, and the key is kept so nothing else changes.
    LaunchedEffect(appLocationEngine, mapPosition, state.mapLibreMap) {
        val engine = appLocationEngine ?: return@LaunchedEffect
        val position = mapPosition ?: return@LaunchedEffect
        snapshotFlow { position.shown.value.fixOrNull }.collect { fix ->
            engine.update(fix)
        }
    }

    // Dispatch 2026-09-28-510: the dot's look when the kind of position changes (soft for approximate,
    // grey for last known, MapLibre's own for GPS). Activation already applies the look current then;
    // this catches a change after it. Navigation's own option swaps carry the look too (below), and this
    // keeps navigation's gesture options, so the two never undo each other.
    LaunchedEffect(state.mapLibreMap, state.loadedStyle, positionLook) {
        val map = state.mapLibreMap
        // Every run of this effect and what it decided, so `adb logcat -s ForagerDot` shows whether a look
        // reached MapLibre. It is how the S22 desk step found the GPS look applied and still not drawn.
        Log.i(POSITION_DOT_LOG_TAG, "look $positionLook: map=${map != null}, style=${state.loadedStyle != null}, track=$trackLiveLocation, engine=${appLocationEngine != null}, activated=${map?.locationComponent?.isLocationComponentActivated}, applied=${state.appliedPositionLook[0]}")
        if (map == null) return@LaunchedEffect
        if (state.loadedStyle == null || !trackLiveLocation || appLocationEngine == null) return@LaunchedEffect
        val component = map.locationComponent
        if (!component.isLocationComponentActivated || state.appliedPositionLook[0] == positionLook) return@LaunchedEffect
        // Dispatch 2026-10-19-37: outside navigation, a map with the spring-back keeps its nudge options through a look change.
        component.applyStyle(
            if (state.navigationModeChange.gestureProtection) {
                liveLocationComponentOptions(context, navigating = true, look = positionLook)
            } else {
                ordinaryLocationOptions(context, positionLook, state.nudgeSpringBack)
            },
        )
        state.appliedPositionLook[0] = positionLook
        Log.i(POSITION_DOT_LOG_TAG, "applied look $positionLook")
    }

    // Re-engages GPS camera tracking on demand — the map redesign's GPS/locate-me icon, tapped
    // either for its first activation or to resume tracking after a manual pan/zoom broke it (see
    // activateLiveLocationIfPermitted's own doc comment on CameraMode.NONE). Also the natural retry
    // point if the very first tap only triggered the OS permission dialog: MapOverlayContent's own
    // doc comment on resumeTrackingRequestId covers why a second tap is what completes activation
    // in that case, not an automatic one.
    LaunchedEffect(resumeTrackingRequestId) {
        if (!trackLiveLocation) return@LaunchedEffect
        val map = state.mapLibreMap ?: return@LaunchedEffect
        val style = state.loadedStyle ?: return@LaunchedEffect
        // The user pressed locate: the move that follows is theirs, so it is marked before the mode changes and closes an open fan as before.
        state.cameraMoveClassifier.markAppMove()
        if (map.locationComponent.isLocationComponentActivated) {
            // Dispatch 2026-10-19-37: a give left on screen by a spring cut short is cleared before following resumes (plain map only).
            state.plainNudge.beforeLocate()
            map.locationComponent.cameraMode = CameraMode.TRACKING
        } else {
            state.navigationModeChange.byTheApp { activateLiveLocationIfPermitted(map, style, context, compassEngine = appCompassEngine, locationEngine = appLocationEngine, look = state.positionLook, nudgeSpringBack = state.nudgeSpringBack) }
            state.appliedPositionLook[0] = state.positionLook
            Log.i(POSITION_DOT_LOG_TAG, "activated by locate with look ${state.positionLook}")
            state.navigationModeChange.listenTo(map) { state.navigationView }
            state.navigationModeChange.gestureProtection = false
        }
    }
}
