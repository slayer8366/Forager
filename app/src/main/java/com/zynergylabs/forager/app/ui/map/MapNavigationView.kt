package com.zynergylabs.forager.app.ui.map

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * The navigation view's effect, moved whole from [SightingsMap] by UI split Phase 2.2 (dispatch 2026-10-19-29), called
 * from the place it stood there and keyed exactly as it was. [navigationView], [basemap], [trackLiveLocation] and
 * [context] are the values [SightingsMap] already had, captured when the effect launches, as before; the latest look is
 * read through [state], as `currentPositionLook` was, and the map's height through [SightingsMapState.navigationMapHeightPx].
 * The comment inside is the effect's own.
 */
@Composable
internal fun MapNavigationView(
    state: SightingsMapState,
    navigationView: NavigationViewRequest?,
    basemap: Basemap,
    trackLiveLocation: Boolean,
    context: Context,
) {
    // Dispatch 2026-09-28-430 (plan task T22): the navigation view. Applied while navigating and
    // following: on Return, on "Return to Route" or locate (restoreRequestId), when the facing
    // changes (a stuck compass), after a style load or a tab's camera restore (loadedStyle), and when
    // the map's height changes (the padding that puts the walker below the centre). Not while the
    // user has moved away from it: the camera stays where they put it. When navigation stops, the
    // map goes back to flat and north-up, still following if it was. Everything here is the SDK's
    // tracking API, not a camera move: an API camera move ends MapLibre's tracking. What MapLibre
    // then draws is device-only: a real MapView cannot run under Robolectric.
    LaunchedEffect(state.mapLibreMap, state.loadedStyle, navigationView != null, navigationView?.following, navigationView?.facing, navigationView?.restoreRequestId, navigationView?.zoomOnStart, state.navigationMapHeightPx) {
        val map = state.mapLibreMap ?: return@LaunchedEffect
        if (state.loadedStyle == null || !trackLiveLocation) return@LaunchedEffect
        val component = map.locationComponent
        if (!component.isLocationComponentActivated) return@LaunchedEffect
        val view = navigationView
        if (view == null) {
            if (state.navigationModeChange.gestureProtection) {
                // Dispatch 2026-10-19-37: back to the options the map holds outside navigation (the nudge's, on a map with the spring-back).
                component.applyStyle(ordinaryLocationOptions(context, state.positionLook, state.nudgeSpringBack))
                state.navigationModeChange.gestureProtection = false
            }
            if (state.navigationModeChange.active) {
                state.cameraMoveClassifier.markAppMove()
                Log.i(NAVIGATION_VIEW_LOG_TAG, "leaving navigation: nudge spring running=${state.nudgeElastic.springing}") // dispatch -470, Part A
                state.navigationModeChange.leaveNavigation(map, navigationMaxZoom(basemap, navigating = false))
            }
            return@LaunchedEffect
        }
        // Dispatch -457, Parts B and C: nudge and pinch protection, on while navigating. Every
        // activation (a style load, locate) applies the ordinary options and resets this.
        if (!state.navigationModeChange.gestureProtection) {
            component.applyStyle(liveLocationComponentOptions(context, navigating = true, look = state.positionLook))
            state.navigationModeChange.gestureProtection = true
        }
        // Dispatch -440, Amendment 1: the camera may reach the start zoom on every basemap while
        // navigating. Set on every run, since a style load (line above, setMaxZoomPreference) puts
        // the basemap's own cap back.
        map.setMaxZoomPreference(navigationMaxZoom(basemap, navigating = true))
        if (!view.following) return@LaunchedEffect
        state.cameraMoveClassifier.markAppMove()
        state.navigationModeChange.applyView(
            map,
            view.facing,
            navigationViewTopPaddingPx(state.navigationMapHeightPx),
            startZoom = if (view.zoomOnStart) NAVIGATION_VIEW_ZOOM else null,
            onStartZoomApplied = view.onStartZoomApplied,
        )
    }
}
