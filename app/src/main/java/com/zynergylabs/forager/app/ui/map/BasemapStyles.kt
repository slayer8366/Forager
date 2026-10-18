package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.map.OFFLINE_STYLE_ATTRIBUTION
import com.zynergylabs.forager.app.map.OFFLINE_STYLE_URL
import com.zynergylabs.forager.app.ui.theme.MapPalette

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
 * **No style declares `glyphs`** (dispatch 2026-10-18-04, item 1; the owner: "remove the fonts requests, but keep
 * the text"). Every style used to point `glyphs` at MapLibre's demo host (`demotiles.maplibre.org`), which is not a
 * production service, for the numbered foraging-area labels; those went with the foraging-areas feature (`192db6c7`)
 * and nothing the app adds has drawn map text since. The place and trail names the user reads are printed into the
 * raster tile images themselves, so they need no fonts. Rejected: bundling the glyphs as `asset://` files or serving
 * them from the mapping Worker, which would ship a font, and its licence, for text nothing draws. A future layer
 * with a `text-field` renders nothing until a `glyphs` URL is added back (hit for real once, PR #22), and that URL
 * must be one this project serves, not the demo host (`BasemapStyleTest`).
 */
/**
 * Night mode's raster paint block: the **V1** transform, applied to the basemap layer.
 *
 *  - `raster-brightness-min 1` and `raster-brightness-max 0` swap the output range, so every
 *    channel comes out as `1 - c`: the pale ground goes dark and the dark linework goes pale.
 *  - `raster-hue-rotate 180` turns every hue half way round first, which undoes the half turn the
 *    inversion gives it. The net effect, per MapLibre Native's own raster shader at the pinned
 *    `13.5.0` (cited in `docs/audits/2026-09-27-marker-swatch-board.md` section 2), is
 *    `night = clamp(c + 1 - 2 * mean(r, g, b))` per gamma-encoded channel: lightness inverts, hue
 *    stays. [nightColorOf] is the same formula for colours this project sets itself.
 *
 * **Why V1 and not plain inversion (V2).** V2 is the same block without the hue rotate. It turns the
 * whole ground blue-violet, which the owner found an eyesore at night: "I like the V1 arrangement
 * better. The blue is pretty but that can be an eyesore at night". V1 keeps blue at about the day
 * map's share (the swatch board, section 1). On the S22 V1 held hue within 1 degree at P90 on
 * Topographical and Street (`docs/audits/2026-09-27-night-inversion-spike-and-tile-measurement.md`).
 *
 * **Why the earlier blocks were dropped.** This block used to dim the ground with
 * `raster-brightness-max` (removed 2026-08-26: it left the map brighter than the dark chrome around
 * it, and a lower cap had already failed on legibility), and then only desaturated it
 * (`raster-saturation -0.35`, `raster-contrast 0.1`), which left a day-bright ground. Both were
 * stopgaps for the inversion an earlier session believed needed tile interception, because the
 * raster paint properties have no per-pixel invert. The brightness-range swap is that invert.
 *
 * Every basemap takes it. Satellite was the one exception (owner: "Satellite stays as it is at
 * night"), held by a per-basemap `basemapTakesNightPaint`; both went with Satellite (dispatch
 * 2026-09-28-708, see [Basemap]'s class doc).
 *
 * All three are MapLibre style-spec v8 raster paint properties, so they need no code path of their
 * own; they ride in the style JSON the basemap swap already rebuilds.
 */
private const val NIGHT_RASTER_PAINT = """,
          "paint": {
            "raster-brightness-min": 1,
            "raster-brightness-max": 0,
            "raster-hue-rotate": 180
          }"""

/**
 * Where a night Topographical map's crossfade from Street to topo begins and ends. Below
 * [NIGHT_FADE_START_ZOOM] only Street is drawn; from it the topo layer fades in (`raster-opacity` 0 at the
 * start, 1 at [NIGHT_FADE_END_ZOOM]) over the Street layer, which is drawn, at opacity 1, until the end
 * (a layer's `maxzoom` is exclusive and its `minzoom` inclusive, style spec v8); from the end only topo.
 *
 * **Why 9.5 to 9.7 and not 9.4 to 9.6.** 9.5 is where OpenTopoMap's tiles change character: MapLibre Native
 * 13.5.0 fetches raster tile zoom `round(mapZoom + 1)` for a 256 px source (`util::coveringZoomLevel`, cited
 * on [rasterTileZoomFor]), so tile zoom 11 starts at map zoom 9.5, and V1's mean lightness is 0.42 to 0.47 on
 * OpenTopoMap tile zoom 7 to 10 and 0.23 to 0.32 on 11 to 15 (81-tile sample, completion report). A fade that
 * started at 9.4 would fade the tinted tile level 10 back in over 9.4 to 9.5. Measured pixelwise on the real
 * tiles of both sources at the same x/y, V1 on both: blended mean / median lightness 0.186 / 0.180 at 9.40
 * (topo opacity 0), 0.249 / 0.246 at 9.44, 0.296 / 0.290 at 9.47, **0.342 / 0.353 at 9.4999** (the edge of
 * "light", where the test's dark limit is 0.345), then **0.224 / 0.201 at 9.50**, the instant the tile level
 * becomes 11: a lightening and a snap back, the opposite of what the owner chose the switch for. Starting the
 * fade at 9.5 means only tile level 11 ever fades in, and the blend stays at mean 0.22 to 0.27 (test). The
 * topo layer's `minzoom` is pinned to tile level 11 or deeper by `TopoNightStreetSwitchTest`.
 */
internal const val NIGHT_FADE_START_ZOOM = 9.5
internal const val NIGHT_FADE_END_ZOOM = 9.7

/** The id of the Street source and layer a night Topographical style adds under [RASTER_LAYER_ID]'s. */
internal const val NIGHT_STREET_SOURCE_ID = "basemap-street"
internal const val NIGHT_STREET_LAYER_ID = "basemap-street"

/**
 * The basemap a night [basemap] shows below [NIGHT_FADE_START_ZOOM] (and under its own layer until [NIGHT_FADE_END_ZOOM]), or `null` when it shows itself at
 * every zoom. Only Topographical has one: [Basemap.OSM_STANDARD]. A per-basemap decision rather than an
 * inline `if`, so the style and the attribution caption read the same answer and a test can pin the set.
 *
 * **Why.** The owner, on the S22, with three screenshots: "When maps are in topo night mode, they are in
 * night mode when zoomed in, but after zooming out to a certain point, it goes back to day mode. I
 * checked the diff between night and day and they have light colors, just a different palette". Night was
 * never switching off: [NIGHT_RASTER_PAINT] has no zoom dependence. OpenTopoMap's own low-zoom tiles
 * (tile zoom 7 to 10) are tinted, green lowlands and tan hills, and V1, which flips lightness about each
 * pixel's channel mean, turns the saturated greens light (the zoomed-out night screenshot's dominant
 * (120,216,120) is V1 of the day screenshot's (72,168,72), which gives (119,215,119); both quantised to
 * 16 levels). At trail zoom its tiles are pale and V1 makes them dark.
 *
 * **Every alternative, and why it lost.** The owner's test for the choice: "it does look a lot better than
 * switching to day mode suddenly. We have two tradeoffs and only one can break confidence in the app." A
 * visible change of map style that stays night is a tradeoff; a map that turns light below a zoom reads as
 * night mode failing, which is the one that breaks confidence.
 *  1. *Half-amplitude V1 below 9.5* (`raster-brightness-min` 0.5; the planner's ruling, built, then run on
 *     the S22). Owner: "Coder took the dimming route anyway. The map needs to not be dimmed, but match what
 *     we see when zoomed in". It dimmed the map, as the August 2026 dimming attempts above did.
 *  2. *Dimming with no inversion*, rejected before it was built: "Option A. No dimming. That's tacky and not
 *     a true night mode option".
 *  3. *Anything per pixel* (contrast, saturation, scaled brightness): "Pure inverted colors is the way, not
 *     fine tuned pixel manipulation. We may need to lose some of the topo rendering to make that happen". The
 *     shader keeps each pixel's channel mean through the hue rotate, so the output's channel mean is an
 *     affine function of the input's whatever the paint: no raster paint can make the low-zoom tinted tiles
 *     look like the pale zoomed-in ones.
 *  4. *Deeper OpenTopoMap tiles* (a second source with a smaller `tileSize`): the tinted look belongs to
 *     tile levels 7 to 10 however they are fetched, so reaching level 11 at map zoom 7 needs `tileSize` 32,
 *     where labels are about 1 dp and each view is about 34 times the tiles, from a server that publishes no
 *     limit and asks "bigger projects" to get in touch.
 *  5. *A hillshade layer over Street (Terrarium DEM)*: adds only subtle relief, does not keep hues, does not
 *     help above 9.5, and needs a licence credit block (a composite of public-domain and CC BY sources) and
 *     its own tiles, offline included.
 *  6. *A minimum zoom of 9.5 for topo*: pure V1, but it stops topo zooming out past about 31 by 68 km on a
 *     phone, and the app's own opening zoom for a region over 30 km is 9.0.
 *  7. *A hard switch at 9.5* (built, then superseded by the crossfade the owner asked for: "Add the cross
 *     fade to the topo switch. I honestly considered that myself").
 *  8. *A fade over 9.4 to 9.6*, rejected by measurement (table on [NIGHT_FADE_START_ZOOM]).
 *
 * **What the owner chose**, shown the renders: "I notice street maps doesn't have this problem. Maybe
 * switch to street maps instead of topo maps when zoomed out? Only when night maps mode is on. With it
 * off no switch to street occurs." So at topo night, below 9.5, the layer is OSM Standard, which V1 turns
 * dark at every zoom (mean 0.15 to 0.23 on tile zoom 7 to 15, one to four tiles each), with the same
 * [NIGHT_RASTER_PAINT] on both layers, unchanged, and the topo layer fading in over 9.5 to 9.7. Topo day,
 * every other basemap and the offline style are untouched.
 */
internal fun nightLowZoomBasemap(basemap: Basemap): Basemap? =
    if (basemap == Basemap.OPEN_TOPO_MAP) Basemap.OSM_STANDARD else null

internal fun styleJsonFor(basemap: Basemap, night: Boolean = false): String {
    val below = nightLowZoomBasemap(basemap)
    return if (night && below != null) nightStyleJsonWithBasemapBelow(basemap, below) else singleLayerStyleJson(basemap, night)
}

/**
 * [NIGHT_RASTER_PAINT] with `raster-opacity` added: a linear interpolate on zoom, 0 at [NIGHT_FADE_START_ZOOM]
 * and 1 at [NIGHT_FADE_END_ZOOM]. Built from [NIGHT_RASTER_PAINT] itself so the three V1 properties cannot drift
 * from it. The shader applies opacity **after** the brightness mix (`raster.fragment.glsl` at `android-v13.5.0`:
 * `color.a *= u_opacity` at line 33, `fragColor = vec4(mix(u_high_vec, u_low_vec, rgb) * color.a, color.a)` at
 * line 53), so V1 is untouched and, unlike interpolating `raster-brightness-min`/`-max`, a fade cannot pass
 * through a flat grey.
 */
private val NIGHT_RASTER_PAINT_FADING_IN: String =
    NIGHT_RASTER_PAINT.removeSuffix("\n          }") +
        ",\n            \"raster-opacity\": [\"interpolate\", [\"linear\"], [\"zoom\"], $NIGHT_FADE_START_ZOOM, 0, $NIGHT_FADE_END_ZOOM, 1]\n          }"

/**
 * [basemap]'s night style with [below]'s raster layer under it: [below] is drawn under
 * [NIGHT_FADE_END_ZOOM] (`maxzoom`, at opacity 1), [basemap] from [NIGHT_FADE_START_ZOOM] (`minzoom`) fading in
 * over it. Both carry [NIGHT_RASTER_PAINT]'s V1 properties; the topo layer's paint adds `raster-opacity`.
 * [basemap]'s source and layer keep the ids and content the day style gives them. Layers draw in array
 * order, so [below] is first; at any one zoom exactly one of the two is drawn.
 */
private fun nightStyleJsonWithBasemapBelow(basemap: Basemap, below: Basemap): String = """
    {
      "version": 8,
      "sources": {
        "$NIGHT_STREET_SOURCE_ID": {
          "type": "raster",
          "tiles": ["${below.tileUrlTemplate}"],
          "tileSize": 256,
          "maxzoom": ${below.maxZoom},
          "attribution": ${below.fullAttribution.toJsonStringLiteral()}
        },
        "$RASTER_SOURCE_ID": {
          "type": "raster",
          "tiles": ["${basemap.tileUrlTemplate}"],
          "tileSize": 256,
          "maxzoom": ${basemap.maxZoom},
          "attribution": ${basemap.fullAttribution.toJsonStringLiteral()}
        }
      },
      "layers": [
        {"id": "$NIGHT_STREET_LAYER_ID", "type": "raster", "source": "$NIGHT_STREET_SOURCE_ID", "maxzoom": $NIGHT_FADE_END_ZOOM$NIGHT_RASTER_PAINT},
        {"id": "$RASTER_LAYER_ID", "type": "raster", "source": "$RASTER_SOURCE_ID", "minzoom": $NIGHT_FADE_START_ZOOM$NIGHT_RASTER_PAINT_FADING_IN}
      ]
    }
""".trimIndent()

private fun singleLayerStyleJson(basemap: Basemap, night: Boolean): String = """
    {
      "version": 8,
      "sources": {
        "$RASTER_SOURCE_ID": {
          "type": "raster",
          "tiles": ["${basemap.tileUrlTemplate}"],
          "tileSize": 256,
          "maxzoom": ${basemap.maxZoom},
          "attribution": ${basemap.fullAttribution.toJsonStringLiteral()}
        }
      },
      "layers": [
        {"id": "$RASTER_LAYER_ID", "type": "raster", "source": "$RASTER_SOURCE_ID"${if (night) NIGHT_RASTER_PAINT else ""}}
      ]
    }
""".trimIndent()

/**
 * Which style document [SightingsMap] hands to `Style.Builder` — Stage 2e-ii's one decision, as a
 * value rather than a `Style.Builder` so it can be asserted in a plain JVM test
 * (`OfflineStyleSwapTest`), the same boundary [styleJsonFor]/`BasemapStyleTest` already draw.
 *
 * [Json] is the in-app raster style for a [Basemap], loaded with `fromJson`. [Uri] is a style
 * loaded **by URL**, which is the only way MapLibre's offline database can serve it: the store
 * keys the style document by the URL it was downloaded against (`OfflineStyle.kt`), so a `fromJson`
 * copy of the offline style's content would never ask the store for that resource. The tiles would
 * still be found by their own URLs, but the app would be rendering a copied style free to drift
 * from the one the download stored.
 */
sealed interface MapStyleSource {
    data class Json(val json: String) : MapStyleSource
    data class Uri(val uri: String) : MapStyleSource
}

/**
 * The offline style when [useOfflineTiles] is on, regardless of [basemap] and [night]; the
 * basemap's own raster style otherwise, with the night paint when [night] is on.
 *
 * The offline source ignores [night] because the store holds one style document, keyed by its one
 * URL: `NIGHT_RASTER_PAINT` is a raster paint block, and the offline style has 57 vector layers and
 * no raster layer, so there is nothing for it to apply to. Stage 2e-ii left night inert there (owner
 * ruling: report, do not fix). Since colour build C1 the offline style's night is applied after it
 * loads, by recolouring its own layers with the same V1 transform ([offlineNightRecolourOf], called
 * from `SightingsMap`'s `setStyle` callback), rather than by a second style URL that would need its
 * own download.
 */
internal fun mapStyleSourceFor(basemap: Basemap, night: Boolean, useOfflineTiles: Boolean): MapStyleSource =
    if (useOfflineTiles) MapStyleSource.Uri(OFFLINE_STYLE_URL) else MapStyleSource.Json(styleJsonFor(basemap, night))

/**
 * The always-visible credit for what the map is currently drawing — the offline style's own when
 * it is showing (a licensing obligation, see [OFFLINE_STYLE_ATTRIBUTION]), the [Basemap]'s
 * otherwise. Before 2e-ii the caption read [Basemap.attribution] unconditionally, which over
 * offline tiles would have credited OpenTopoMap for Protomaps geometry.
 */
internal fun mapAttributionFor(basemap: Basemap, useOfflineTiles: Boolean): String =
    if (useOfflineTiles) OFFLINE_STYLE_ATTRIBUTION else basemap.attribution

/**
 * Every credit the map's caption shows, in order: what the map is drawn on ([mapAttributionFor]'s
 * one credit) first, then each of [layerCredits] (the active layers' own, `activeLayerCredits`),
 * without repeating one already present. Map layers L0a, A5.
 */
internal fun mapCreditsFor(
    basemap: Basemap,
    useOfflineTiles: Boolean,
    layerCredits: List<String> = emptyList(),
    nightMode: Boolean = false,
): List<String> {
    // At topo night the style also draws Street below 9.7 ([nightLowZoomBasemap]), so its credit is owed. The
    // caption has no zoom input, so both credits show at every zoom; hiding one above the crossfade would need a zoom
    // listener for no gain. Offline is the vector style, which carries neither.
    val below = if (!useOfflineTiles && nightMode) nightLowZoomBasemap(basemap) else null
    return (listOf(mapAttributionFor(basemap, useOfflineTiles)) + listOfNotNull(below?.attribution) + layerCredits).distinct()
}

/** [credits] as the one line the caption draws, joined by [ATTRIBUTION_SEPARATOR]. */
internal fun attributionCaption(credits: List<String>): String = credits.joinToString(ATTRIBUTION_SEPARATOR)

/**
 * Between two credits in the caption. A middle dot rather than a comma or a dash, because both of
 * those already occur inside credits ("© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)", and the
 * removed Satellite's "USGS The National Map, orthoimagery — public domain").
 */
internal const val ATTRIBUTION_SEPARATOR = " · "

/**
 * Everything that, when it changes, means [SightingsMap] must call `setStyle` again — the
 * basemap swap's guard, as a value. Before 2e-ii the guard compared basemap and palette only
 * (`appliedBasemap == basemap && appliedPalette == mapPalette`), so an offline flag flipping on its
 * own would never have reached `setStyle` — the one place the pre-build report said a "toggle does
 * nothing" bug would come from (§2.2). Extracted so that gap is pinned by a test rather than by
 * reading the effect's keys.
 */
internal data class AppliedMapStyle(
    val basemap: Basemap,
    /**
     * The marker palette: `MapPalette.forMode` of the Night Maps toggle (colour build C2). The overlay
     * layers bake these colours in when the style loads, so a palette change is a reload.
     */
    val palette: MapPalette,
    val useOfflineTiles: Boolean,
    /**
     * Whether the basemap takes its night style: the Night Maps toggle, on every basemap and over the
     * offline style alike. Night Maps did nothing before colour build C1 partly because this value had
     * no night component at all, so a toggle compared equal and never reached `setStyle`. It differed
     * from the toggle only over Satellite, whose style stayed day; that exception (`effectiveNight`)
     * went with Satellite (dispatch 2026-09-28-708).
     */
    val night: Boolean,
)

/**
 * What [SightingsMap]'s style effect should load for these inputs, or `null` while the Night Maps
 * preference has not loaded ([nightModeLoaded] `false`): the cold-launch gate. Without it, the effect
 * could apply a day style before an asynchronous read reported night, and a night user's map would
 * stay day. A pure function so the gate is asserted headless (`OfflineStyleSwapTest`); the effect's
 * own keys are not reachable without a native MapView. The marker palette is chosen here too, from the
 * raw toggle, for the same reason: a test can reach it here and not in the composable.
 */
internal fun requestedMapStyle(
    basemap: Basemap,
    useOfflineTiles: Boolean,
    nightMode: Boolean,
    nightModeLoaded: Boolean,
): AppliedMapStyle? =
    if (!nightModeLoaded) {
        null
    } else {
        AppliedMapStyle(
            basemap = basemap,
            palette = MapPalette.forMode(nightMode),
            useOfflineTiles = useOfflineTiles,
            night = nightMode,
        )
    }

/** `true` when nothing has been applied yet, or when any part of [requested] differs from what was. */
internal fun needsStyleReload(applied: AppliedMapStyle?, requested: AppliedMapStyle): Boolean = applied != requested

/** The id every basemap's raster source/layer is added under — fixed, since a style swap replaces the whole style object anyway. */
internal const val RASTER_SOURCE_ID = "basemap"
internal const val RASTER_LAYER_ID = "basemap"

/**
 * Escapes [this] for use as a JSON string literal inside the hand-built style JSON above.
 *
 * [Basemap.fullAttribution] is this project's own fixed constant text today (never user input), so a
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
