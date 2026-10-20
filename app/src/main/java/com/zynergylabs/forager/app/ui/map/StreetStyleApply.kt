package com.zynergylabs.forager.app.ui.map

import android.graphics.Bitmap
import android.util.Log
import com.zynergylabs.forager.app.map.STREET_SPRITE_ICON_NAMES
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
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
 * The own Street's icons at night (-1288, labels and all inverted; dispatch 2026-10-18-05 Part 2). The sprite's
 * icons are fixed-colour images, not SDF, so no paint property can recolour them (Part 1 report, Q1.3): each one is
 * read back by name ([STREET_SPRITE_ICON_NAMES]: MapLibre cannot list a style's images), turned through the same V1
 * transform pixel by pixel ([nightPixelsOf]), and put back under its own name. A missing image, or one with no
 * density for MapLibre to take its pixel ratio from (`Style.toImage` reads `Bitmap.getDensity`, checked with
 * `javap` at `13.5.0`), is left in its day colours and counted in the log line, never dropped silently.
 */
internal fun applyStreetSpriteNight(style: Style) {
    var inverted = 0
    val left = mutableListOf<String>()
    for (name in STREET_SPRITE_ICON_NAMES) {
        val day: Bitmap? = try {
            style.getImage(name)
        } catch (e: RuntimeException) {
            Log.w(SIGHTINGS_MAP_TAG, "Street night: could not read icon $name; left in its day colours.", e)
            null
        }
        if (day == null) {
            left += name
            continue
        }
        if (day.density == Bitmap.DENSITY_NONE) {
            Log.w(SIGHTINGS_MAP_TAG, "Street night: icon $name has no density, so its pixel ratio is unknown; left in its day colours.")
            left += name
            continue
        }
        val night = day.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(night.width * night.height)
        night.getPixels(pixels, 0, night.width, 0, 0, night.width, night.height)
        // The place dot and the toilets and drinking-water icons keep a light ring (RECORD -1345); the rest invert.
        night.setPixels(nightIconPixelsOf(pixels, STREET_NIGHT_ICON_RINGS[name]), 0, night.width, 0, 0, night.width, night.height)
        try {
            style.addImage(name, night)
            inverted++
        } catch (e: RuntimeException) {
            Log.w(SIGHTINGS_MAP_TAG, "Street night: could not set icon $name; left in its day colours.", e)
            left += name
        }
    }
    Log.i(SIGHTINGS_MAP_TAG, "Street night: $inverted icons inverted, ${left.size} left in day colours${if (left.isEmpty()) "" else ": $left"}.")
}
