package com.zynergylabs.forager.app.ui.map

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue

/** The relayout's log tag, so its lines can be read on their own from logcat. */
internal const val PATTERN_RELAYOUT_LOG_TAG = "ForagerPatternRelayout"

/**
 * How long the restore waits for a fully rendered frame before it restores anyway, so a map that stops rendering (sent
 * to the background just after a reload) never keeps its patterns cleared. Logged when it fires.
 */
internal const val PATTERN_RESTORE_TIMEOUT_MS = 2_000L

/** The live style's fill layers as [PatternLayerTarget]: a pattern held as MapLibre holds it, a name or an expression. */
private class StylePatternLayers(private val style: Style) : PatternLayerTarget<PropertyValue<String>> {
    override fun transitionMs(): Long = style.transition.let { it.duration + it.delay }

    override fun patternLayers(): List<PatternLayer<PropertyValue<String>>> = style.layers.filterIsInstance<FillLayer>().mapNotNull { layer ->
        val pattern = layer.fillPattern
        when {
            pattern.isNull -> null
            pattern.isExpression -> PatternLayer(layer.id, pattern, pattern.expression.toString())
            pattern.value.isNullOrEmpty() -> null
            else -> PatternLayer(layer.id, pattern, pattern.value!!)
        }
    }

    override fun clearPattern(layerId: String): Boolean {
        val layer = style.getLayer(layerId) as? FillLayer ?: return false
        layer.setProperties(PropertyFactory.fillPattern(""))
        return true
    }

    override fun restorePattern(layerId: String, value: PropertyValue<String>): Boolean {
        val layer = style.getLayer(layerId) as? FillLayer ?: return false
        layer.setProperties(if (value.isExpression) PropertyFactory.fillPattern(value.expression) else PropertyFactory.fillPattern(value.value))
        return true
    }
}

/** The wait before the clear, on the MapView's own main-thread queue (cancelled with the MapView's callbacks). */
private class MapViewDeferral(private val mapView: MapView) : Deferral {
    override fun after(ms: Long, run: () -> Unit): () -> Unit {
        val r = Runnable { run() }
        mapView.postDelayed(r, ms)
        return { mapView.removeCallbacks(r) }
    }
}

/**
 * The restore at the first fully rendered frame at least `minGapMs` after the clear: the cleared tiles have been laid out
 * and drawn with no pattern by then (a restore 2-7 ms after the clear could fail on the S22; see PatternRelayout.kt).
 * [PATTERN_RESTORE_TIMEOUT_MS] is the fallback if no such frame comes; the log line says which it was and when.
 */
private class FirstFullFrameTrigger(private val mapView: MapView) : RestoreTrigger {
    override fun afterRendered(minGapMs: Long, restore: (how: String) -> Unit): () -> Unit {
        val start = SystemClock.uptimeMillis()
        val gate = RestoreFrameGate(start, minGapMs)
        var done = false
        lateinit var timeout: Runnable
        val frames = object : MapView.OnDidFinishRenderingFrameListener {
            override fun onDidFinishRenderingFrame(fully: Boolean, frameEncodingTime: Double, frameRenderingTime: Double) {
                val now = SystemClock.uptimeMillis()
                val elapsed = now - start
                if (done || !gate.isRestoreFrame(fully, now)) return
                done = true
                mapView.removeOnDidFinishRenderingFrameListener(this)
                mapView.removeCallbacks(timeout)
                restore("at the first full frame at least $minGapMs ms after the clear, $elapsed ms")
            }
        }
        timeout = Runnable {
            if (done) return@Runnable
            done = true
            mapView.removeOnDidFinishRenderingFrameListener(frames)
            restore("no full frame within $PATTERN_RESTORE_TIMEOUT_MS ms; restored on the timeout")
        }
        mapView.addOnDidFinishRenderingFrameListener(frames)
        mapView.postDelayed(timeout, PATTERN_RESTORE_TIMEOUT_MS)
        return {
            done = true
            mapView.removeOnDidFinishRenderingFrameListener(frames)
            mapView.removeCallbacks(timeout)
        }
    }
}

/**
 * The relayout for [mapView], with MapLibre's sprite listeners registered for as long as the MapView is shown.
 * [MapStyleReload] tells it of each style request and each `setStyle` callback.
 */
@Composable
internal fun rememberPatternRelayout(mapView: MapView): PatternRelayout<Style, PropertyValue<String>> {
    val relayout = remember(mapView) {
        PatternRelayout<Style, PropertyValue<String>>(
            targetFor = ::StylePatternLayers,
            deferral = MapViewDeferral(mapView),
            trigger = FirstFullFrameTrigger(mapView),
            log = { Log.i(PATTERN_RELAYOUT_LOG_TAG, it) },
        )
    }
    DisposableEffect(mapView, relayout) {
        // Listener types checked with javap against the pinned 13.5.0 artifact: onSpriteLoaded(String id, String url),
        // onSpriteError(String id, String url).
        val loaded = MapView.OnSpriteLoadedListener { _, _ -> relayout.onSpriteLoaded() }
        val failed = MapView.OnSpriteErrorListener { _, url -> relayout.onSpriteFailed(url) }
        mapView.addOnSpriteLoadedListener(loaded)
        mapView.addOnSpriteErrorListener(failed)
        onDispose {
            mapView.removeOnSpriteLoadedListener(loaded)
            mapView.removeOnSpriteErrorListener(failed)
        }
    }
    return relayout
}
