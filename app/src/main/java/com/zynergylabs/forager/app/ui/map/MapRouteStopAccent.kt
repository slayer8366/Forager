package com.zynergylabs.forager.app.ui.map

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource

/*
 * Drives the selected stop's highlight from the frame clock (dispatch 2026-11-06-05, Amendment 7; the motion is
 * RouteStopAccentMotion.kt) and pushes each frame to the map. One source's data per frame, the accent's one or two points with their
 * opacity and size: no layer is added, removed or restyled while it moves. The fan reads the same frame (MapFan.kt), so its copy of the
 * selected stop moves with the accent at its place.
 */

/**
 * The plotted route on the map: [route] pushed whenever it or the style changes (a new style starts empty; dispatch 2026-10-30-08, moved
 * here from SightingsMap.kt at the same place in its effects), and its selected stop's highlight pushed at each frame of its motion.
 * Returns that frame, for the fan.
 */
@Composable
internal fun mapPlottedRoute(loadedStyle: Style?, route: PlottedRouteOnMap?): RouteStopAccentFrame {
    LaunchedEffect(loadedStyle, route) { plottedRouteOnCurrentStyle(loadedStyle, route) }
    val accent = rememberRouteStopAccentFrame(route?.selected)
    LaunchedEffect(loadedStyle, accent) { routeStopAccentOnCurrentStyle(loadedStyle, accent) }
    return accent
}

/**
 * The highlight to draw now for the selected stop's [selected] marker (`null` for none): it changes on every frame while a highlight
 * fades or pulses, and not at all once settled. Reads the system's Reduce Motion ([LocalReduceMotion]) at each selection.
 */
@Composable
internal fun rememberRouteStopAccentFrame(selected: SelectedStopMarker?): RouteStopAccentFrame {
    val reduceMotion by rememberUpdatedState(LocalReduceMotion.current)
    val timeline = remember { RouteStopAccentTimeline() }
    var frame by remember { mutableStateOf(RouteStopAccentFrame.NONE) }
    LaunchedEffect(selected) {
        var now = withFrameMillis { it }
        timeline.select(selected, now, reduceMotion)
        frame = timeline.frameAt(now)
        while (!timeline.isSettled(now)) {
            now = withFrameMillis { it }
            frame = timeline.frameAt(now)
        }
    }
    return frame
}

/** Pushes [frame] to the accent's source on the current style; nothing in a style change (logged by [currentStyle]), which runs it again on load. */
internal fun routeStopAccentOnCurrentStyle(loaded: Style?, frame: RouteStopAccentFrame) {
    val style = currentStyle(loaded, "The selected stop's highlight") ?: return
    val source = style.getSourceAs<GeoJsonSource>(PlottedRouteIds.SELECTED_SOURCE)
    if (source == null) {
        Log.w(SIGHTINGS_MAP_TAG, "The selected stop's source ${PlottedRouteIds.SELECTED_SOURCE} is missing; its highlight is not drawn.")
    } else {
        source.setGeoJson(routeStopAccentFeatures(frame))
    }
}
