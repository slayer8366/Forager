package com.forager.app.ui.map

/**
 * The one place a [Basemap] becomes a MapLibre style — this project's own analog of the deleted
 * `BasemapTileSources.kt`, which did the same job for osmdroid's `ITileSource`.
 *
 * Every basemap here stays a **raster** source: the minimal MapLibre style JSON a raster tile
 * template needs (`"version": 8`, one `"raster"` source, one `"raster"` layer), built directly from
 * [Basemap]'s own fields rather than four separately hosted style documents — see [Basemap]'s class
 * doc for why. This is the same mechanism `MapLibreBasemapPreviewActivity`'s `rasterStyleJson`
 * proved (that scaffolding is deleted in this same change; its style-JSON shape survives here,
 * carried forward rather than reinvented).
 *
 * [glyphs] is set on every style, not only the ones that need text, because [SightingsMap] adds a
 * `SymbolLayer` with a `text-field` for the numbered foraging-area markers on top of *whichever*
 * basemap is active — a style with no `glyphs` URL renders `text-field` as nothing at all, silently
 * (this was hit for real: see `MapLibreBasemapPreviewActivity`'s history, "Fix missing area-marker
 * labels: style JSON had no glyphs URL", hardware-confirmed on PR #22). [AREA_MARKER_FONT_STACK] is
 * the same font that fix settled on, for the same reason: `"Open Sans Semibold"` is what
 * `demotiles.maplibre.org`'s own reference style actually ships in its glyph set, not a guess.
 */
/**
 * Night mode's raster paint block, applied to the basemap layer.
 *
 * The basemap is the glare source, not the markers. A white topo sheet at full brightness is what
 * makes a phone unusable at night and wrecks dark adaptation, so night mode dims *the ground* and
 * lifts the marks off it (see `MapPalette.NIGHT`). Dimming the tiles is also what makes a night
 * palette meaningful at all: the marks and the ground move together, which is exactly what could
 * not happen when the palette was keyed on the device theme.
 *
 *  - `raster-brightness-max` caps the tile's brightest output. 0.32 takes a pale topo sheet down
 *    to roughly the tone `MapPalette.NIGHT_TILE_REFERENCE` models, which is the ground the night
 *    palette's contrast is checked against.
 *  - `raster-saturation` pulls colour out of the basemap so the overlay's own hues are the only
 *    saturated things on screen — the marks should be what the eye catches, not the terrain tint.
 *  - `raster-contrast` recovers a little of the shape definition that dimming flattens, so
 *    contour lines and water edges stay readable rather than becoming an even grey wash.
 *
 * All three are MapLibre style-spec v8 raster paint properties, so they need no code path of their
 * own — they ride in the style JSON the basemap swap already rebuilds.
 *
 * **`raster-brightness-max` was 0.22, hardware-reported as unusably dark on a topo basemap at
 * night, 2026-08-26.** Raised to 0.32 — see `MapPalette.NIGHT`'s own doc comment, "Third pass,"
 * for the full account of why 0.32 (not further, and not by lightening the ground alone) and how
 * every marker colour was re-solved to hold both its contrast floor and its separation from every
 * other marker at the new tone. Past roughly 0.44, no colour — including white — can reach 4.0:1
 * against the resulting ground at all, which is a hard ceiling on this value, not a taste choice.
 * Whether 0.32 itself is comfortable at 3am, and whether the imagery basemap (already dark) is
 * over- or under-dimmed by the same settings, remain device-gate questions — the imagery case in
 * particular is untested and may well want its own values.
 */
private const val NIGHT_RASTER_PAINT = """,
          "paint": {
            "raster-brightness-max": 0.32,
            "raster-saturation": -0.35,
            "raster-contrast": 0.1
          }"""

internal fun styleJsonFor(basemap: Basemap, night: Boolean = false): String = """
    {
      "version": 8,
      "glyphs": "$GLYPHS_URL_TEMPLATE",
      "sources": {
        "$RASTER_SOURCE_ID": {
          "type": "raster",
          "tiles": ["${basemap.tileUrlTemplate}"],
          "tileSize": 256,
          "maxzoom": ${basemap.maxZoom},
          "attribution": ${basemap.attribution.toJsonStringLiteral()}
        }
      },
      "layers": [
        {"id": "$RASTER_LAYER_ID", "type": "raster", "source": "$RASTER_SOURCE_ID"${if (night) NIGHT_RASTER_PAINT else ""}}
      ]
    }
""".trimIndent()

/** The id every basemap's raster source/layer is added under — fixed, since a style swap replaces the whole style object anyway. */
internal const val RASTER_SOURCE_ID = "basemap"
internal const val RASTER_LAYER_ID = "basemap"

/**
 * MapLibre's own public glyph PBF endpoint (demotiles.maplibre.org, no key) — see this file's class
 * doc comment for why every style needs one.
 */
internal const val GLYPHS_URL_TEMPLATE = "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf"
internal val AREA_MARKER_FONT_STACK = arrayOf("Open Sans Semibold")

/**
 * Escapes [this] for use as a JSON string literal inside the hand-built style JSON above.
 *
 * [Basemap.attribution] is this project's own fixed constant text today (never user input), so a
 * full JSON-string escaper is more than the actual risk warrants — but "more than needed" is cheap
 * insurance against a future attribution string that happens to contain a `"` or a backslash
 * silently producing invalid JSON that `Style.Builder.fromJson` would then reject at runtime with a
 * message pointing nowhere near the real cause.
 */
private fun String.toJsonStringLiteral(): String {
    val escaped = buildString {
        append('"')
        this@toJsonStringLiteral.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }
    return escaped
}
