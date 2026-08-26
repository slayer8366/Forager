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
 *  - `raster-saturation` pulls colour out of the basemap so the overlay's own hues are the only
 *    saturated things on screen — the marks should be what the eye catches, not the terrain tint.
 *  - `raster-contrast` recovers a little of the shape definition dimming used to flatten, back
 *    when this block dimmed the ground — kept for now even though there is no more dimming for it
 *    to recover shape from; revisit if a future pass finds it does something unwanted at full
 *    brightness.
 *
 * Both are MapLibre style-spec v8 raster paint properties, so they need no code path of their own
 * — they ride in the style JSON the basemap swap already rebuilds.
 *
 * ## Dimming removed, 2026-08-26
 *
 * `raster-brightness-max` dimmed the ground — `0.32` most recently (raised from an initial `0.22`,
 * hardware-reported as unusably dark; see `MapPalette.NIGHT`'s doc comment, "Third pass," for that
 * whole account). Requested directly: dimming created a *different* problem — the night map read
 * lighter than the app's own dark-theme chrome around it (measured: this app's darkest surface
 * colours sit at 0.005–0.038 relative luminance; the dimmed ground at 0.32 sat at 0.099, roughly
 * 3–20× brighter), a visible imbalance between the map and the UI framing it.
 *
 * **Removed outright, not retargeted to a lower cap or a no-op value.** A lower cap reproduces the
 * `0.22` legibility failure this file's own history already found and rejected once, and Android's
 * own display/night-mode handling already does real work here that a per-app dim on top of it was
 * fighting rather than complementing. `raster-brightness-max` is not a property this style JSON
 * sets any more, night or day — night's ground is now the same brightness day's is.
 *
 * **The eventual replacement is colour inversion, not dimming — deliberately out of scope for this
 * session.** Inverting the basemap's own colours (pale background → dark, dark linework → pale)
 * would give a genuinely dark-toned map without dimming's flattened dynamic range, but MapLibre's
 * raster paint properties (`opacity`/`hue-rotate`/`brightness-min`/`brightness-max`/`saturation`/
 * `contrast`/`resampling`/`fade-duration` — the complete set, checked with `javap` against the
 * pinned `13.5.0` artifact) have no per-pixel invert. Real inversion would mean intercepting and
 * transforming tile images before MapLibre renders them, new infrastructure this session did not
 * build. Tracked as a deferred research item in `docs/plans/map-redesign.md`, not just here, so it
 * survives past this session rather than living only in a code comment.
 *
 * **Markers stay legible on the now-bright ground via shape, not colour.** `MapPalette.NIGHT_WARM`/
 * `NIGHT_INK` are unchanged and no longer clear their contrast floors against the brighter ground
 * on their own — but every night icon (`SightingsMap.kt`'s `*Bitmap` functions) now draws a
 * darkened, semi-transparent halo behind its fill, which keeps it visible against a light or dark
 * background alike. `MapPaletteTest`'s tile-contrast assertion is still expected to fail; the halo
 * is what actually keeps markers visible now, not that contrast floor.
 */
private const val NIGHT_RASTER_PAINT = """,
          "paint": {
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
