package com.zynergylabs.forager.app.ui.map

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PointF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * DIAGNOSTIC ONLY, branch diag/tree-glyph-gaps (dispatch 2026-10-22-07). Never merged.
 *
 * adb shell am broadcast -a forager.diag.CAMERA --ef lat 45.60 --ef lon -122.01 --ef zoom 12.5
 * adb shell am broadcast -a forager.diag.DEBUG --ez on true
 * adb shell am broadcast -a forager.diag.PROBE
 * adb shell am broadcast -a forager.diag.MINZOOM --ef z 11
 */
internal object GlyphGapProbe {
    const val TAG = "GlyphGap"
    private var installed: BroadcastReceiver? = null

    fun install(context: Context, map: MapLibreMap, mapView: MapView) {
        installed?.let { runCatching { context.applicationContext.unregisterReceiver(it) } }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                Handler(Looper.getMainLooper()).post { handle(intent, map, mapView) }
            }
        }
        val filter = IntentFilter().apply {
            addAction("forager.diag.CAMERA"); addAction("forager.diag.DEBUG")
            addAction("forager.diag.PROBE"); addAction("forager.diag.MINZOOM")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.applicationContext.registerReceiver(receiver, filter)
        }
        installed = receiver
        map.addOnCameraIdleListener {
            val cam = map.cameraPosition
            Log.i(TAG, "idle zoom=${cam.zoom} tilt=${cam.tilt} bearing=${cam.bearing} target=${cam.target}")
        }
        Log.i(TAG, "installed")
    }

    private fun handle(intent: Intent, map: MapLibreMap, mapView: MapView) {
        when (intent.action) {
            "forager.diag.CAMERA" -> {
                val pos = CameraPosition.Builder()
                    .target(LatLng(intent.getFloatExtra("lat", 45.60f).toDouble(), intent.getFloatExtra("lon", -122.01f).toDouble()))
                    .zoom(intent.getFloatExtra("zoom", 12.5f).toDouble()).tilt(intent.getFloatExtra("tilt", 0f).toDouble()).bearing(intent.getFloatExtra("bearing", 0f).toDouble()).build()
                map.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
                Log.i(TAG, "camera set $pos")
            }
            "forager.diag.DEBUG" -> {
                map.isDebugActive = intent.getBooleanExtra("on", true)
                Log.i(TAG, "debugActive=${map.isDebugActive}")
            }
            "forager.diag.MINZOOM" -> {
                val layer = map.style?.getLayer("landuse_forest_glyphs")
                if (layer == null) { Log.w(TAG, "no landuse_forest_glyphs layer"); return }
                val before = layer.minZoom
                layer.minZoom = intent.getFloatExtra("z", 11f)
                Log.i(TAG, "landuse_forest_glyphs minzoom $before -> ${layer.minZoom}")
            }
            "forager.diag.PROBE" -> probe(map, mapView)
        }
    }

    private fun probe(map: MapLibreMap, mapView: MapView) {
        val cam = map.cameraPosition
        val w = mapView.width; val h = mapView.height
        val cols = 36; val rows = 72
        Log.i(TAG, "probe zoom=${cam.zoom} tilt=${cam.tilt} view=${w}x$h grid=${cols}x$rows style=${map.style?.uri}")
        Log.i(TAG, "glyph layer minzoom=${map.style?.getLayer("landuse_forest_glyphs")?.minZoom}")
        var forest = 0; var glyph = 0; var gap = 0
        val lines = ArrayList<String>()
        for (r in 0 until rows) {
            val sb = StringBuilder()
            for (cIdx in 0 until cols) {
                val p = PointF((cIdx + 0.5f) * w / cols, (r + 0.5f) * h / rows)
                val fill = map.queryRenderedFeatures(p, "landuse_park").any { f -> f.getStringProperty("kind").let { k -> k == "forest" || k == "wood" } }
                val gl = map.queryRenderedFeatures(p, "landuse_forest_glyphs").isNotEmpty()
                if (fill) forest++
                if (gl) glyph++
                if (fill && !gl) gap++
                sb.append(when { fill && gl -> '#'; fill -> '.'; gl -> 'g'; else -> ' ' })
            }
            lines += sb.toString()
        }
        lines.forEachIndexed { i, l -> Log.i(TAG, "row %02d |%s|".format(i, l)) }
        Log.i(TAG, "cells forest=$forest glyph=$glyph forestWithoutGlyph=$gap")
        // Tile boundaries crossing the view, in grid cell units, for z11/z12/z13.
        val tl = map.projection.fromScreenLocation(PointF(0f, 0f))
        val br = map.projection.fromScreenLocation(PointF(w.toFloat(), h.toFloat()))
        for (z in 11..13) {
            val n = 1 shl z
            val x0 = floor((tl.longitude + 180) / 360 * n).toInt(); val x1 = floor((br.longitude + 180) / 360 * n).toInt()
            val y0 = tileY(tl.latitude, n); val y1 = tileY(br.latitude, n)
            val xs = (x0 + 1..x1).map { x ->
                val lon = x.toDouble() / n * 360 - 180
                val px = map.projection.toScreenLocation(LatLng(cam.target!!.latitude, lon)).x
                "x=$x@col%.1f".format(px * cols / w)
            }
            val ys = (y0 + 1..y1).map { y ->
                val lat = atan(sinh(PI * (1 - 2.0 * y / n))) * 180 / PI
                val py = map.projection.toScreenLocation(LatLng(lat, cam.target!!.longitude)).y
                "y=$y@row%.1f".format(py * rows / h)
            }
            Log.i(TAG, "z$z tiles x $x0..$x1 y $y0..$y1 vertical edges $xs horizontal edges $ys")
        }
    }

    private fun tileY(lat: Double, n: Int): Int {
        val r = lat * PI / 180
        return floor((1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2 * n).toInt()
    }
}
