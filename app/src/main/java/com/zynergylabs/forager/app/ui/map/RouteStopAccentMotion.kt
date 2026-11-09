package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.ui.map.fanout.FanKey

/*
 * The selected stop's highlight fades in and pulses once (dispatch 2026-11-06-05, Amendment 7, RECORD -2187). The owner: "when we tap the
 * waypoint and it highlights, have the waypoint highlight fade in and out and pulse once, the instant change makes it look clunky. That
 * lets you know something was selected", and to the step paths, "Yes, that's it": the highlight fades in instead of snapping; as it arrives
 * the pin swells about 15% and settles ("Pin swells (Recommended)", about half a second); another stop picked, the old one fades out while
 * the new one fades in and pulses; night and trip flags the same.
 *
 * Plain Kotlin on a millisecond clock, no map and no Compose, so a test reads every frame: MapRouteStopAccent.kt drives it from the
 * frame clock and hands each [RouteStopAccentFrame] to the map, where the accent's opacity and size are read per feature by the accent
 * layer (PlottedRouteLayers.kt) and the fan's icon layer (FanOutLayers.kt). Nothing is added or removed from the style per frame.
 *
 * Under the system's Reduce Motion: no pulse, and the highlight is there at once (the dispatch's "an instant (or very short) fade";
 * instant, as the app's other reduced-motion paths are). A highlight that goes because its layer or the route goes is gone at once too:
 * the pin under it goes at once, so a fading accent would be left over nothing (Amendment 3, "Hide with the layer").
 */

/** The fade in, and out, of the highlight. */
internal const val ROUTE_STOP_ACCENT_FADE_MS = 200L

/** When the swell starts, as the fade is arriving; it peaks at [ROUTE_STOP_ACCENT_PULSE_PEAK_MS] and has settled at [ROUTE_STOP_ACCENT_PULSE_END_MS]. */
internal const val ROUTE_STOP_ACCENT_PULSE_START_MS = 150L
internal const val ROUTE_STOP_ACCENT_PULSE_PEAK_MS = 300L
internal const val ROUTE_STOP_ACCENT_PULSE_END_MS = 500L

/** The swell's peak: about 15% larger (the owner's "Pin swells (Recommended)"). */
internal const val ROUTE_STOP_ACCENT_PULSE_SCALE = 1.15f

/** One highlight on the map in one frame: the stop's [marker], drawn at [opacity] over its plain pin, at [scale] of its size. */
internal data class RouteStopAccentShown(val marker: SelectedStopMarker, val opacity: Float, val scale: Float) {
    val key: FanKey get() = marker.fanKey
}

/** What the map draws of the highlight in one frame: any fading out first, the selected one last (on top), or nothing; [selectedKey] is the selected stop's marker. */
internal data class RouteStopAccentFrame(val shown: List<RouteStopAccentShown>, val selectedKey: FanKey? = null) {
    companion object {
        val NONE = RouteStopAccentFrame(emptyList())

        /** [marker]'s highlight at rest, as the map drew it before Amendment 7: full and its own size. */
        fun settled(marker: SelectedStopMarker?): RouteStopAccentFrame =
            if (marker == null) NONE else RouteStopAccentFrame(listOf(RouteStopAccentShown(marker, 1f, 1f)), marker.fanKey)
    }
}

/** A smooth step from 0 to 1 over [x] in 0..1: eases in and out, as the app's own motion does. */
private fun ease(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** The pulse's size [elapsedMs] after the highlight started arriving: 1, up to [ROUTE_STOP_ACCENT_PULSE_SCALE], back to 1. */
internal fun routeStopAccentScaleAt(elapsedMs: Long): Float {
    val rise = ROUTE_STOP_ACCENT_PULSE_SCALE - 1f
    return when {
        elapsedMs <= ROUTE_STOP_ACCENT_PULSE_START_MS -> 1f
        elapsedMs <= ROUTE_STOP_ACCENT_PULSE_PEAK_MS ->
            1f + rise * ease((elapsedMs - ROUTE_STOP_ACCENT_PULSE_START_MS).toFloat() / (ROUTE_STOP_ACCENT_PULSE_PEAK_MS - ROUTE_STOP_ACCENT_PULSE_START_MS))
        elapsedMs < ROUTE_STOP_ACCENT_PULSE_END_MS ->
            ROUTE_STOP_ACCENT_PULSE_SCALE - rise * ease((elapsedMs - ROUTE_STOP_ACCENT_PULSE_PEAK_MS).toFloat() / (ROUTE_STOP_ACCENT_PULSE_END_MS - ROUTE_STOP_ACCENT_PULSE_PEAK_MS))
        else -> 1f
    }
}

/**
 * The highlight's motion through selections, on a millisecond clock the caller supplies. [select] is told each selection (or `null` for
 * none) when it happens; [frameAt] says what to draw at a moment; [isSettled] says when nothing more moves, so the caller stops asking.
 */
internal class RouteStopAccentTimeline {
    private class Arriving(val marker: SelectedStopMarker, val atMs: Long, val fromOpacity: Float, val reduceMotion: Boolean)
    private class Leaving(val marker: SelectedStopMarker, val atMs: Long, val fromOpacity: Float, val pulseStartMs: Long)

    private var arriving: Arriving? = null
    private val leaving = mutableListOf<Leaving>()

    /**
     * [marker] is now the selected stop's, at [nowMs]. The same stop again (its waypoint moved, say) changes where it is drawn and nothing
     * else; another stop starts arriving while the one before fades out; `null` takes every highlight away at once.
     */
    fun select(marker: SelectedStopMarker?, nowMs: Long, reduceMotion: Boolean) {
        val current = arriving
        if (marker == null) {
            arriving = null
            leaving.clear()
            return
        }
        if (current != null && current.marker.fanKey == marker.fanKey) {
            arriving = Arriving(marker, current.atMs, current.fromOpacity, current.reduceMotion)
            return
        }
        // A stop picked again while it was still fading out arrives from where its fade had got to, not from nothing.
        val wasLeaving = leaving.firstOrNull { it.marker.fanKey == marker.fanKey }
        val from = wasLeaving?.let { leavingOpacity(it, nowMs) } ?: 0f
        leaving.removeAll { it.marker.fanKey == marker.fanKey }
        if (reduceMotion) {
            leaving.clear()
        } else if (current != null) {
            leaving += Leaving(current.marker, nowMs, arrivingOpacity(current, nowMs), current.atMs)
        }
        arriving = Arriving(marker, nowMs, from, reduceMotion)
    }

    fun frameAt(nowMs: Long): RouteStopAccentFrame {
        leaving.removeAll { nowMs - it.atMs >= ROUTE_STOP_ACCENT_FADE_MS }
        val out = leaving.map { RouteStopAccentShown(it.marker, leavingOpacity(it, nowMs), routeStopAccentScaleAt(nowMs - it.pulseStartMs)) }
        val inn = arriving?.let { RouteStopAccentShown(it.marker, arrivingOpacity(it, nowMs), if (it.reduceMotion) 1f else routeStopAccentScaleAt(nowMs - it.atMs)) }
        return RouteStopAccentFrame(out + listOfNotNull(inn), inn?.key)
    }

    fun isSettled(nowMs: Long): Boolean {
        val a = arriving
        val arrived = a == null || a.reduceMotion || nowMs - a.atMs >= ROUTE_STOP_ACCENT_PULSE_END_MS
        return arrived && leaving.all { nowMs - it.atMs >= ROUTE_STOP_ACCENT_FADE_MS }
    }

    private fun arrivingOpacity(a: Arriving, nowMs: Long): Float =
        if (a.reduceMotion) 1f else a.fromOpacity + (1f - a.fromOpacity) * ease((nowMs - a.atMs).toFloat() / ROUTE_STOP_ACCENT_FADE_MS)

    private fun leavingOpacity(l: Leaving, nowMs: Long): Float =
        l.fromOpacity * (1f - ease((nowMs - l.atMs).toFloat() / ROUTE_STOP_ACCENT_FADE_MS))
}
