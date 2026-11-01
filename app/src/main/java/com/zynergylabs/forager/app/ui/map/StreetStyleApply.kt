package com.zynergylabs.forager.app.ui.map

import android.util.Log
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.HillshadeLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

// The own Street's style-load steps (dispatch 2026-10-18-05 Part 2), moved verbatim out of SightingsMap.kt when
// pre-main's UI split (Phase 2.2, dispatch 2026-10-19-29) was merged into street-cloudflare: the style swap they serve
// now lives in MapStyleReload.kt, and only `private` became `internal` so it can call them.

/**
 * The own Street style with Topographical over it at night ([MapStyleSource.StreetWithTopoOver]; dispatch 2026-10-18-05
 * Part 2): the Street style by URL, then the topo raster added as the top layer, drawn from the source's minimum zoom
 * and fading in, with the V1 night paint the raster styles use ([NIGHT_RASTER_PAINT]'s three properties). The SDK
 * calls were checked with `javap` against the pinned `13.5.0` artifact: `Style.Builder.fromUri`/`withSource`/
 * `withLayer`, `RasterSource(String, TileSet, int)`, `TileSet(String, String...)` with `setMaxZoom` and
 * `setAttribution`, `RasterLayer(String, String)` and the raster `PropertyFactory` setters.
 */
internal fun streetWithTopoOverBuilder(source: MapStyleSource.StreetWithTopoOver): Style.Builder {
    val tiles = TileSet("2.2.0", source.topo.tileUrlTemplate).apply {
        setMaxZoom(source.topo.maxZoom.toFloat())
        attribution = source.topo.fullAttribution
    }
    val layer = RasterLayer(RASTER_LAYER_ID, RASTER_SOURCE_ID).withProperties(
        PropertyFactory.rasterBrightnessMin(1f),
        PropertyFactory.rasterBrightnessMax(0f),
        PropertyFactory.rasterHueRotate(180f),
        PropertyFactory.rasterOpacity(
            Expression.interpolate(
                Expression.linear(),
                Expression.zoom(),
                Expression.stop(source.topoMinZoom, 0f),
                Expression.stop(source.topoFadeEndZoom, 1f),
            ),
        ),
    ).apply { minZoom = source.topoMinZoom.toFloat() }
    return Style.Builder().fromUri(source.streetUri)
        .withSource(RasterSource(RASTER_SOURCE_ID, tiles, 256))
        .withLayer(layer)
}

/**
 * At Topographical night over the own Street, the Street's labels stop where the topo begins to cover them
 * (-1291: "The Street labels must not show through that composition"). Run before the overlays are added, so only
 * the Street style's own symbol layers are touched; the app's markers keep every zoom.
 */
internal fun hideStreetLabelsUnderTopo(style: Style, maxZoom: Double) {
    var hidden = 0
    for (layer in style.layers) {
        if (layer is SymbolLayer) {
            layer.maxZoom = maxZoom.toFloat()
            hidden++
        }
    }
    Log.i(SIGHTINGS_MAP_TAG, "Topo night over Street: $hidden Street label layers end at zoom $maxZoom.")
}

/**
 * Street v4's hill shading at night (dispatch 2026-11-01-coder-11): every hillshade layer takes [night]'s colours. MapLibre
 * 13.5.0 takes shadow and highlight as colour arrays (one per light, for multidirectional shading), accent as one colour.
 */
internal fun applyStreetHillshadeNight(style: Style, night: HillshadeNight) {
    var set = 0
    for (layer in style.layers) {
        if (layer is HillshadeLayer) {
            layer.setProperties(
                PropertyFactory.hillshadeShadowColor(arrayOf(night.shadow)),
                PropertyFactory.hillshadeHighlightColor(arrayOf(night.highlight)),
                PropertyFactory.hillshadeAccentColor(night.accent),
            )
            set++
        }
    }
    if (set > 0) Log.i(SIGHTINGS_MAP_TAG, "Street night: $set hillshade layer(s) take their night colours.")
}

/**
 * Under Topographical, OpenTopoMap brings its own relief, so Street's hill shading is hidden (dispatch 2026-11-01-coder-11):
 * two shadings would double.
 */
internal fun hideHillshadeUnderTopo(style: Style) {
    for (layer in style.layers) {
        if (layer is HillshadeLayer) layer.setProperties(PropertyFactory.visibility(Property.NONE))
    }
}
