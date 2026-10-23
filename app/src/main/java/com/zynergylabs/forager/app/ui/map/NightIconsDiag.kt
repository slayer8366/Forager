package com.zynergylabs.forager.app.ui.map

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

// DIAGNOSTIC ONLY (dispatch 2026-10-22-05): branch night-icons-diag, never merged. Read-only: it reads images and
// logs, it never adds, removes or changes an image.

private const val DIAG_TAG = "NightDiag"
private val DIAG_NAMES = listOf("trailhead_b", "parking", "peak", "tree_dark_irregular", "tufts")
private var diagLoad = 0

private fun probe(style: Style, map: MapLibreMap, load: Int, t0: Long, at: String) {
    val ms = SystemClock.elapsedRealtime() - t0
    if (map.style !== style) {
        Log.i(DIAG_TAG, "load#$load +${ms}ms [$at]: style replaced since this load; probe skipped")
        return
    }
    val each = DIAG_NAMES.joinToString(", ") { name ->
        val b = try { style.getImage(name) } catch (e: RuntimeException) { return@joinToString "$name=THREW(${e.javaClass.simpleName}: ${e.message})" }
        if (b == null) "$name=null" else "$name=${b.width}x${b.height}/d${b.density}/px0=${Integer.toHexString(b.getPixel(b.width / 2, b.height / 2))}"
    }
    val all = STREET_SPRITE_NIGHT_NAMES.count { n -> try { style.getImage(n) != null } catch (e: RuntimeException) { false } }
    val loaded = try { style.isFullyLoaded } catch (e: RuntimeException) { "THREW" }
    Log.i(DIAG_TAG, "load#$load +${ms}ms [$at] fullyLoaded=$loaded nonNull=$all/${STREET_SPRITE_NIGHT_NAMES.size}: $each")
}

/** Called first thing in the setStyle callback; returns the load number. */
internal fun nightIconsDiag(map: MapLibreMap, mapView: MapView, style: Style, night: Boolean, willRepaint: Boolean): Int {
    val load = ++diagLoad
    val t0 = SystemClock.elapsedRealtime()
    Log.i(DIAG_TAG, "load#$load setStyle callback at uptime=${t0}ms uri=${style.uri} night=$night repaintWillRun=$willRepaint")
    probe(style, map, load, t0, "callback")
    var finishedLoading = 0
    var rendered = 0
    var missing = 0
    val loadingListener = MapView.OnDidFinishLoadingStyleListener {
        if (finishedLoading++ < 3) probe(style, map, load, t0, "OnDidFinishLoadingStyle#$finishedLoading")
    }
    val renderedListener = MapView.OnDidFinishRenderingMapListener { fully ->
        if (rendered++ < 3) probe(style, map, load, t0, "OnDidFinishRenderingMap#$rendered fully=$fully")
    }
    val missingListener = MapView.OnStyleImageMissingListener { id ->
        if (missing++ < 10) Log.i(DIAG_TAG, "load#$load +${SystemClock.elapsedRealtime() - t0}ms OnStyleImageMissing id=$id")
    }
    mapView.addOnDidFinishLoadingStyleListener(loadingListener)
    mapView.addOnDidFinishRenderingMapListener(renderedListener)
    mapView.addOnStyleImageMissingListener(missingListener)
    val handler = Handler(Looper.getMainLooper())
    for (delay in listOf(0L, 50L, 250L, 1000L, 3000L, 10000L)) {
        handler.postDelayed({ probe(style, map, load, t0, "delay ${delay}ms") }, delay)
    }
    handler.postDelayed({
        mapView.removeOnDidFinishLoadingStyleListener(loadingListener)
        mapView.removeOnDidFinishRenderingMapListener(renderedListener)
        mapView.removeOnStyleImageMissingListener(missingListener)
        Log.i(DIAG_TAG, "load#$load listeners removed; finishedLoading=$finishedLoading rendered=$rendered missing=$missing")
    }, 12000L)
    return load
}
