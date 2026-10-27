package com.zynergylabs.forager.app.ui.map

import android.graphics.PointF
import android.os.SystemClock
import android.util.Log
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.tile.TileOperation

/**
 * Dispatch 2026-10-27-08 (RECORD -1738): LOGGING ONLY, on the scratch branch glyph-logging, never merged. Finds why the
 * fill patterns (tree glyphs, tufts) go missing at the zoom the camera holds when the style reloads. Every line is
 * tagged [GLYPH_LOG_TAG] and carries the uptime in ms so the order of style, sprite, image and tile events can be read.
 * Nothing here changes behaviour: the unused-image listener returns true, as MapLibre does with no listener.
 */
internal const val GLYPH_LOG_TAG = "ForagerGlyphLog"

private val PATTERN_LAYERS = listOf("landuse_forest_glyphs", "landuse_wetland_tufts")
private val PATTERN_IMAGES = listOf("tree_dark_irregular", "night/tree_dark_irregular", "tufts", "night/tufts")

internal fun glyphLog(message: String) {
    Log.i(GLYPH_LOG_TAG, "t=${SystemClock.uptimeMillis()} $message")
}

/** The zoom, the two pattern layers' fill-pattern as read back, the four images, and what is rendered at the centre. */
internal fun glyphSnapshot(event: String, map: MapLibreMap?, style: Style?, mapView: MapView?) {
    val zoom = map?.cameraPosition?.zoom
    val zoomText = zoom?.let { String.format(java.util.Locale.ROOT, "%.2f", it) } ?: "null"
    if (style == null) {
        glyphLog("$event zoom=$zoomText style=null")
        return
    }
    val parts = mutableListOf<String>()
    parts += "fullyLoaded=${runCatching { style.isFullyLoaded }.getOrElse { "err:${it.javaClass.simpleName}" }}"
    for (id in PATTERN_LAYERS) {
        val value = runCatching {
            when (val layer = style.getLayer(id)) {
                null -> "absent"
                is FillLayer -> layer.fillPattern.let { if (it.isExpression) "expr:${it.expression}" else "${it.value}" } +
                    " vis=${layer.visibility.value} min=${layer.minZoom} max=${layer.maxZoom}"
                else -> "not-fill:${layer.javaClass.simpleName}"
            }
        }.getOrElse { "err:${it.javaClass.simpleName}" }
        parts += "$id=[$value]"
    }
    for (name in PATTERN_IMAGES) {
        val image = runCatching { style.getImage(name) }.getOrNull()
        parts += "img[$name]=" + (image?.let { "${it.width}x${it.height}@${it.density}" } ?: "null")
    }
    if (map != null && mapView != null && mapView.width > 0) {
        val centre = PointF(mapView.width / 2f, mapView.height / 2f)
        for (id in PATTERN_LAYERS + "landuse_park") {
            val count = runCatching { map.queryRenderedFeatures(centre, id).size }.getOrElse { -1 }
            parts += "rendered[$id]@centre=$count"
        }
    }
    glyphLog("$event zoom=$zoomText ${parts.joinToString(" ")}")
}

/**
 * MapLibre's own listeners, log only: style and sprite loading, missing and unused images, tile actions (all but the
 * two request kinds, with z, x, y and the overscaled zoom), render-map and idle. Returns the remover for onDispose.
 */
internal fun registerGlyphDiagnostics(mapView: MapView, mapProvider: () -> MapLibreMap?, styleProvider: () -> Style?): () -> Unit {
    val styleLoaded = MapView.OnDidFinishLoadingStyleListener {
        glyphSnapshot("didFinishLoadingStyle", mapProvider(), mapProvider()?.style, mapView)
    }
    val willLoad = MapView.OnWillStartLoadingMapListener { glyphLog("willStartLoadingMap zoom=${mapProvider()?.cameraPosition?.zoom}") }
    val spriteRequested = MapView.OnSpriteRequestedListener { id, url -> glyphLog("spriteRequested id=$id url=$url zoom=${mapProvider()?.cameraPosition?.zoom}") }
    val spriteLoaded = MapView.OnSpriteLoadedListener { id, url ->
        glyphLog("spriteLoaded id=$id url=$url")
        glyphSnapshot("afterSpriteLoaded", mapProvider(), mapProvider()?.style, mapView)
    }
    val spriteError = MapView.OnSpriteErrorListener { id, url -> glyphLog("spriteError id=$id url=$url") }
    val missing = MapView.OnStyleImageMissingListener { name -> glyphLog("styleImageMissing name=$name zoom=${mapProvider()?.cameraPosition?.zoom}") }
    val canRemove = MapView.OnCanRemoveUnusedStyleImageListener { name ->
        glyphLog("canRemoveUnusedStyleImage name=$name zoom=${mapProvider()?.cameraPosition?.zoom} -> true (unchanged behaviour)")
        true
    }
    val tiles = MapView.OnTileActionListener { op, z, x, y, wrap, overscaledZ, sourceId ->
        if (op != TileOperation.RequestedFromCache && op != TileOperation.RequestedFromNetwork) {
            glyphLog("tile $op src=$sourceId z=$z x=$x y=$y wrap=$wrap oz=$overscaledZ")
        }
    }
    val renderedMap = MapView.OnDidFinishRenderingMapListener { fully -> glyphLog("didFinishRenderingMap fully=$fully") }
    val idle = MapView.OnDidBecomeIdleListener { glyphSnapshot("didBecomeIdle", mapProvider(), styleProvider(), mapView) }
    val renderError = MapView.OnRenderErrorListener { glyphLog("renderError") }
    mapView.addOnDidFinishLoadingStyleListener(styleLoaded)
    mapView.addOnWillStartLoadingMapListener(willLoad)
    mapView.addOnSpriteRequestedListener(spriteRequested)
    mapView.addOnSpriteLoadedListener(spriteLoaded)
    mapView.addOnSpriteErrorListener(spriteError)
    mapView.addOnStyleImageMissingListener(missing)
    mapView.addOnCanRemoveUnusedStyleImageListener(canRemove)
    mapView.addOnTileActionListener(tiles)
    mapView.addOnDidFinishRenderingMapListener(renderedMap)
    mapView.addOnDidBecomeIdleListener(idle)
    mapView.addOnRenderErrorListener(renderError)
    glyphLog("diagnostics registered")
    return {
        mapView.removeOnDidFinishLoadingStyleListener(styleLoaded)
        mapView.removeOnWillStartLoadingMapListener(willLoad)
        mapView.removeOnSpriteRequestedListener(spriteRequested)
        mapView.removeOnSpriteLoadedListener(spriteLoaded)
        mapView.removeOnSpriteErrorListener(spriteError)
        mapView.removeOnStyleImageMissingListener(missing)
        mapView.removeOnCanRemoveUnusedStyleImageListener(canRemove)
        mapView.removeOnTileActionListener(tiles)
        mapView.removeOnDidFinishRenderingMapListener(renderedMap)
        mapView.removeOnDidBecomeIdleListener(idle)
        mapView.removeOnRenderErrorListener(renderError)
    }
}
