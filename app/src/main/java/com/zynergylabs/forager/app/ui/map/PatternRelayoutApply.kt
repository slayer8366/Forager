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
internal const val PATTERN_RESTORE_TIMEOUT_MS = 5_000L

/** The live style's fill layers as [PatternLayerTarget]: a pattern held as MapLibre holds it, a name or an expression. */
private class StylePatternLayers(private val style: Style) : PatternLayerTarget<PropertyValue<String>> {
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

/**
 * The restore at the first fully rendered frame after the clear: the cleared tiles have been laid out and drawn with no
 * pattern by then, which is the state the S22 probe restored from (dispatch 2026-10-27-11 asks for the shortest gap that
 * works; the log line gives the time it took). [PATTERN_RESTORE_TIMEOUT_MS] is the fallback if no such frame comes.
 */
private class FirstFullFrameTrigger(private val mapView: MapView) : RestoreTrigger {
    override fun afterRendered(restore: (how: String) -> Unit): () -> Unit {
        val start = SystemClock.uptimeMillis()
        // SCRATCH (never merged): the gap read from files/pattern-gap, "delay:<ms>", "fullframes:<n>" or "fullafter:<ms>".
        val mode = try { java.io.File(mapView.context.filesDir, "pattern-gap").readText().trim() } catch (e: java.io.IOException) { "fullframes:1" }
        val kind = mode.substringBefore(':')
        val n = mode.substringAfter(':').toLongOrNull() ?: 1L
        Log.i(PATTERN_RELAYOUT_LOG_TAG, "SCRATCH gap mode $mode")
        if (kind == "delay") {
            val r = Runnable { restore("SCRATCH delay $n ms, ${SystemClock.uptimeMillis() - start} ms after the clear") }
            mapView.postDelayed(r, n)
            return { mapView.removeCallbacks(r) }
        }
        var fullSeen = 0L
        var done = false
        lateinit var timeout: Runnable
        val frames = object : MapView.OnDidFinishRenderingFrameListener {
            override fun onDidFinishRenderingFrame(fully: Boolean, frameEncodingTime: Double, frameRenderingTime: Double) {
                if (!fully || done) return
                val elapsed = SystemClock.uptimeMillis() - start
                fullSeen++
                Log.i(PATTERN_RELAYOUT_LOG_TAG, "SCRATCH full frame $fullSeen at $elapsed ms")
                if (kind == "fullframes" && fullSeen < n) return
                if (kind == "fullafter" && elapsed < n) return
                done = true
                mapView.removeOnDidFinishRenderingFrameListener(this)
                mapView.removeCallbacks(timeout)
                restore("SCRATCH $mode: full frame $fullSeen, ${SystemClock.uptimeMillis() - start} ms after the clear")
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
