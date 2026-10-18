package com.zynergylabs.forager.app.ui.map

import android.graphics.BitmapFactory
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Topo night stays dark at every zoom by showing the Street map below map zoom 9.5 and crossfading to topo
 * over 9.5 to 9.7 (dispatch 2026-09-28-310, the owner's choice: "I notice street maps doesn't have this
 * problem. Maybe switch to street maps instead of topo maps when zoomed out? Only when night maps mode is
 * on.", then "Add the cross fade to the topo switch"). The night-topo style JSON must carry an OSM Standard
 * layer (`maxzoom` 9.7, opacity 1) under the topo layer (`minzoom` 9.5, `raster-opacity` 0 at 9.5 rising
 * to 1 at 9.7), both with today's V1 paint; every other style document must be byte-for-byte what it was.
 *
 * The sampled tiles (`docs/audits/data/2026-09-30-topo-night/`: 81 OpenTopoMap tiles, 15 OSM tiles, tile
 * zoom 7 to 15 over Oregon City) are run through the paint of whichever layer the **parsed JSON** shows at
 * each map zoom, using [rasterShade], the headless model of the raster shader. A pass is a pass of that
 * model on those tiles, not a device check; the OSM sample is one to four tiles per zoom.
 *
 * Tile level N is shown for map zoom N - 1.5 up to N - 0.5 ([rasterTileZoomFor]), so each level is sampled
 * at both ends and the middle of its own map-zoom range.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TopoNightStreetSwitchTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val v1 = RasterPaint(brightnessMin = 1.0, brightnessMax = 0.0, hueRotate = 180.0)

    /** Unique colours of every sampled tile at one tile zoom, with pixel counts. */
    private class TileSet(val tileZoom: Int, val tileCount: Int, val colours: Map<Int, Int>)

    private fun loadTiles(dirs: List<String>, expectedFiles: Int, expectedPerZoom: Map<Int, Int>, expectedDistinct: Int, what: String): Map<Int, TileSet> {
        val files = dirs.flatMap { File(it).listFiles { f -> f.name.endsWith(".png") }.orEmpty().toList() }
        assertEquals("$what: a short directory would make every check below vacuous", expectedFiles, files.size)
        return files.groupBy { it.name.substringBefore('_').toInt() }.mapValues { (z, group) ->
            val counts = HashMap<Int, Int>()
            for (file in group) {
                val bitmap = BitmapFactory.decodeFile(file.path) ?: error("${file.name} did not decode")
                assertEquals("${file.name} is 256 px square", 256 to 256, bitmap.width to bitmap.height)
                val pixels = IntArray(256 * 256)
                bitmap.getPixels(pixels, 0, 256, 0, 0, 256, 256)
                for (pixel in pixels) counts.merge(pixel or (0xFF shl 24), 1, Int::plus)
            }
            TileSet(z, group.size, counts)
        }.also { byZoom ->
            assertEquals((7..15).toList(), byZoom.keys.sorted())
            assertEquals("$what: tiles per zoom", expectedPerZoom, byZoom.mapValues { it.value.tileCount })
            // Pillow (measure.py / the README) counts these distinct colours, summed per zoom; a decoder that
            // quantised, colour-managed or dropped pixels would not reproduce it.
            assertEquals("$what: distinct colours summed per tile zoom, against Pillow's count", expectedDistinct, byZoom.values.sumOf { it.colours.size })
        }
    }

    private val data = "../docs/audits/data/2026-09-30-topo-night"

    private val topoTiles: Map<Int, TileSet> by lazy {
        loadTiles(listOf("$data/tiles"), 81, (7..15).associateWith { 9 }, 17832, "OpenTopoMap sample")
    }

    private val streetTiles: Map<Int, TileSet> by lazy {
        loadTiles(listOf("$data/osm-tiles", "$data/renders/osm-extra"), 15, mapOf(7 to 1, 8 to 4, 9 to 1, 10 to 4, 11 to 1, 12 to 1, 13 to 1, 14 to 1, 15 to 1), 3652, "OSM Standard sample")
    }

    private fun topoNightStyle(): JsonObject = json.parseToJsonElement(styleJsonFor(Basemap.OPEN_TOPO_MAP, night = true)).jsonObject

    private fun layers(style: JsonObject) = style.getValue("layers").jsonArray.map { it.jsonObject }

    /** Whether [layer] is drawn at [mapZoom]: minzoom is inclusive, maxzoom exclusive (style spec, v8). */
    private fun drawnAt(layer: JsonObject, mapZoom: Double): Boolean =
        mapZoom >= (layer["minzoom"]?.jsonPrimitive?.doubleOrNull ?: 0.0) && mapZoom < (layer["maxzoom"]?.jsonPrimitive?.doubleOrNull ?: 24.0)

    private fun lightness(argb: Int): Double =
        (((argb shr 16) and 0xFF) + ((argb shr 8) and 0xFF) + (argb and 0xFF)) / (3.0 * 255.0)

    private fun meanAndMedian(set: TileSet, transform: (Int) -> Int): Pair<Double, Double> {
        val pixels = set.colours.map { (colour, count) -> lightness(transform(colour)) to count }.sortedBy { it.first }
        val total = pixels.sumOf { it.second }.toDouble()
        val mean = pixels.sumOf { it.first * it.second } / total
        var seen = 0
        val median = pixels.first { seen += it.second; seen >= total / 2 }.first
        return mean to median
    }

    /** Day pixels that are a label: near-neutral and dark (black text and its dark halo). */
    private fun isLabelPixel(day: Int): Boolean {
        val r = (day shr 16) and 0xFF
        val g = (day shr 8) and 0xFF
        val b = day and 0xFF
        return maxOf(r, g, b) - minOf(r, g, b) < 20 && (r + g + b) / 3 < 64
    }

    private fun mapZoomsShowing(z: Int): List<Double> =
        listOf(z - 1.5, z - 1.0, z - 0.5 - 1e-6).also { zooms ->
            zooms.forEach { assertEquals("map zoom $it should show tile level $z", z, rasterTileZoomFor(it)) }
        }

    /**
     * The top of the zoomed-in band: today's zoomed-in topo night is tile zoom 11 to 15 through V1
     * ([nightColorOf], independent of the paint model); its per-zoom means run 0.231 to 0.316, widened by
     * 0.03 (nine tiles at one place is not a population) to 0.345. "Dark" here means a sampled zoom's mean
     * and median both at or under that. The **lower** side is deliberately not asserted: OSM Standard is a
     * little darker than the zoomed-in topo (mean 0.15 to 0.23 against 0.23 to 0.32), and darker is not the
     * failure this guards against (the owner's report was a light map).
     */
    private fun darkLimit(): Double = (11..15).maxOf { meanAndMedian(topoTiles.getValue(it), ::nightColorOf).first } + 0.03

    // ---- the document --------------------------------------------------------------------------------------

    @Test
    fun `night topo is the Street layer under the topo layer, overlapping 9_5 to 9_7, V1 on both`() {
        val style = topoNightStyle()
        val layers = layers(style)
        assertEquals("night topo must carry two raster layers, Street first (drawn first, so under)", 2, layers.size)
        val (street, topo) = layers

        assertEquals("raster", street.getValue("type").jsonPrimitive.content)
        assertEquals("raster", topo.getValue("type").jsonPrimitive.content)
        assertEquals("the topo layer keeps the id and source the day style uses", RASTER_LAYER_ID, topo.getValue("id").jsonPrimitive.content)
        assertEquals(RASTER_SOURCE_ID, topo.getValue("source").jsonPrimitive.content)
        assertEquals("street layer maxzoom: it stays drawn until the fade has finished", 9.7, street.getValue("maxzoom").jsonPrimitive.double(), 0.0)
        assertNull("street layer has no minzoom", street["minzoom"])
        assertEquals("topo layer minzoom: where the fade starts", 9.5, topo.getValue("minzoom").jsonPrimitive.double(), 0.0)
        assertNull("topo layer has no maxzoom", topo["maxzoom"])
        assertTrue("two distinct sources", street.getValue("source").jsonPrimitive.content != topo.getValue("source").jsonPrimitive.content)
        assertTrue("the zoom ranges overlap: Street maxzoom above topo minzoom", street.getValue("maxzoom").jsonPrimitive.double() > topo.getValue("minzoom").jsonPrimitive.double())

        val v1Keys = setOf("raster-brightness-min", "raster-brightness-max", "raster-hue-rotate")
        val streetPaint = street.getValue("paint").jsonObject
        val topoPaint = topo.getValue("paint").jsonObject
        assertEquals("street: exactly the three V1 properties, so opacity is the default 1", v1Keys, streetPaint.keys)
        assertEquals("topo: the three V1 properties and raster-opacity", v1Keys + "raster-opacity", topoPaint.keys)
        for ((name, paint) in listOf("street" to streetPaint, "topo" to topoPaint)) {
            assertEquals("$name: V1", v1, rasterPaintOf(paint))
            assertEquals("$name: brightness-min is a plain number, not an expression", 1.0, paint.getValue("raster-brightness-min").jsonPrimitive.double(), 0.0)
        }
        assertEquals(
            "topo opacity: a linear zoom interpolate, 0 at 9.5 and 1 at 9.7",
            json.parseToJsonElement("""["interpolate", ["linear"], ["zoom"], 9.5, 0, 9.7, 1]"""),
            topoPaint.getValue("raster-opacity"),
        )
        for ((zoom, expected) in listOf(9.4 to 0.0, 9.5 to 0.0, 9.6 to 0.5, 9.7 to 1.0, 12.0 to 1.0)) {
            assertEquals("topo opacity at $zoom", expected, rasterOpacityAt(topoPaint, zoom), 1e-9)
            assertEquals("street opacity at $zoom", 1.0, rasterOpacityAt(streetPaint, zoom), 0.0)
        }

        val sources = style.getValue("sources").jsonObject
        assertEquals(setOf(RASTER_SOURCE_ID, street.getValue("source").jsonPrimitive.content), sources.keys)
        val streetSource = sources.getValue(street.getValue("source").jsonPrimitive.content).jsonObject
        assertEquals(listOf(Basemap.OSM_STANDARD.tileUrlTemplate), streetSource.getValue("tiles").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(Basemap.OSM_STANDARD.attribution, streetSource.getValue("attribution").jsonPrimitive.content)
        assertEquals(256, streetSource.getValue("tileSize").jsonPrimitive.int())
        assertEquals("the topo source is the day document's, unchanged", json.parseToJsonElement(styleJsonFor(Basemap.OPEN_TOPO_MAP, night = false)).jsonObject.getValue("sources").jsonObject.getValue(RASTER_SOURCE_ID), sources.getValue(RASTER_SOURCE_ID))
    }

    @Test
    fun `at every sampled map zoom the parsed night-topo style shows one layer, pure V1, and a dark map`() {
        val style = topoNightStyle()
        val limit = darkLimit()
        val failures = mutableListOf<String>()
        for (z in 7..15) for (mapZoom in mapZoomsShowing(z)) {
            val drawn = layers(style).filter { drawnAt(it, mapZoom) }
            if (drawn.size == 2 && mapZoom >= 9.5 && mapZoom < 9.7) continue // the crossfade: its own test below blends both layers
            if (drawn.size != 1) { failures += "map zoom $mapZoom: ${drawn.size} layers drawn, expected 1"; continue }
            val layer = drawn.single()
            val tilesUrl = style.getValue("sources").jsonObject.getValue(layer.getValue("source").jsonPrimitive.content).jsonObject
                .getValue("tiles").jsonArray.single().jsonPrimitive.content
            val set = when {
                "openstreetmap" in tilesUrl -> streetTiles.getValue(z)
                "opentopomap" in tilesUrl -> topoTiles.getValue(z)
                else -> error("unexpected tile source $tilesUrl")
            }
            val raster = try { rasterPaintOf(layer.getValue("paint").jsonObject) } catch (e: IllegalStateException) { failures += "map zoom $mapZoom: ${e.message}"; continue }
            if (raster != v1) failures += "tile z$z at map zoom $mapZoom: the drawn layer's paint is $raster, not pure V1"
            val (mean, median) = meanAndMedian(set) { rasterShade(it, raster) }
            if (mean > limit) failures += "tile z$z at map zoom $mapZoom (${if ("openstreetmap" in tilesUrl) "Street" else "topo"}): mean lightness %.3f, limit %.3f".format(mean, limit)
            if (median > limit) failures += "tile z$z at map zoom $mapZoom: ground (median) lightness %.3f, limit %.3f".format(median, limit)
            val labels = set.colours.filterKeys { isLabelPixel(it) }
            if (labels.isNotEmpty()) {
                val labelMean = labels.entries.sumOf { (c, n) -> lightness(rasterShade(c, raster)) * n } / labels.values.sum()
                if (labelMean < median + 0.15) failures += "tile z$z at map zoom $mapZoom: labels %.3f are not 0.15 above the ground %.3f".format(labelMean, median)
            }
            for (day in set.colours.keys) {
                val today = nightColorOf(day); val now = rasterShade(day, raster)
                for (shift in listOf(16, 8, 0)) if (Math.abs(((today shr shift) and 0xFF) - ((now shr shift) and 0xFF)) > 1) {
                    failures += "tile z$z at map zoom $mapZoom, day #%06X: today's V1 %08X, this paint %08X".format(day and 0xFFFFFF, today, now); break
                }
            }
        }
        if (failures.isNotEmpty()) fail("night topo is not dark, pure V1 at every sampled zoom:\n" + failures.distinct().take(40).joinToString("\n"))
    }

    /**
     * The pin that the fade never shows OpenTopoMap's tinted tile level (7 to 10): at every zoom the topo layer is
     * drawn at all, including the first, the tile level is 11 or deeper. A `minzoom` of 9.4 (tile level 10) fails.
     */
    @Test
    fun `the fading topo layer is never drawn on tile level 10 or shallower`() {
        val (street, topo) = layers(topoNightStyle())
        val topoMinZoom = topo.getValue("minzoom").jsonPrimitive.double()
        assertTrue(
            "topo minzoom $topoMinZoom is on tile level ${rasterTileZoomFor(topoMinZoom)}; a fading topo layer must not show level 10 (the tinted regime)",
            rasterTileZoomFor(topoMinZoom) >= 11,
        )
        var zoom = topoMinZoom
        while (zoom < street.getValue("maxzoom").jsonPrimitive.double()) {
            assertTrue("map zoom $zoom: the fade is on tile level ${rasterTileZoomFor(zoom)}", rasterTileZoomFor(zoom) >= 11)
            zoom += 0.01
        }
    }

    @Test
    fun `which layers the style draws on each side of the crossfade`() {
        val style = topoNightStyle()
        val drawnIds = { mapZoom: Double -> layers(style).filter { drawnAt(it, mapZoom) }.map { it.getValue("id").jsonPrimitive.content } }
        assertEquals("just below 9.5: Street only", listOf(NIGHT_STREET_ID_FOR_TEST), drawnIds(9.499))
        assertEquals("far out: Street only", listOf(NIGHT_STREET_ID_FOR_TEST), drawnIds(2.0))
        assertEquals("at 9.5 both, Street under", listOf(NIGHT_STREET_ID_FOR_TEST, RASTER_LAYER_ID), drawnIds(9.5))
        assertEquals("at 9.699 both", listOf(NIGHT_STREET_ID_FOR_TEST, RASTER_LAYER_ID), drawnIds(9.699))
        assertEquals("at 9.7 topo only (maxzoom is exclusive)", listOf(RASTER_LAYER_ID), drawnIds(9.7))
        assertEquals("far in: topo only", listOf(RASTER_LAYER_ID), drawnIds(18.0))
    }

    /**
     * The crossfade, blended as the shader composites it: each layer's V1 output (opaque, from its own tile) times
     * the layer's `raster-opacity` at that zoom, over what is beneath (premultiplied alpha, `out = c x a + beneath x
     * (1 - a)`; the blend mode is inferred from MapLibre's default, not read). Both sources' real tile 11/326/733 at
     * the same pixels, so this is a pixelwise blend, not a blend of two histograms. The dark limit applies with no
     * allowance: the fade only ever mixes tile level 11 (see the `minzoom` pin above).
     */
    @Test
    fun `across the crossfade the blended map stays dark, pixel for pixel`() {
        val style = topoNightStyle()
        val ls = layers(style)
        val limit = darkLimit()
        val topoPx = decodeTile("$data/tiles/11_326_733.png")
        val streetPx = decodeTile("$data/osm-tiles/11_326_733.png")
        val failures = mutableListOf<String>()
        for (mapZoom in listOf(9.5, 9.55, 9.6, 9.65, 9.6999, 9.7, 10.0)) {
            assertEquals("map zoom $mapZoom should be on tile level 11", 11, rasterTileZoomFor(mapZoom))
            val drawn = ls.filter { drawnAt(it, mapZoom) }
            val blended = IntArray(topoPx.size) { i ->
                var out = doubleArrayOf(0.0, 0.0, 0.0)
                for (layer in drawn) {
                    val src = if (layer.getValue("id").jsonPrimitive.content == RASTER_LAYER_ID) topoPx[i] else streetPx[i]
                    val paint = layer.getValue("paint").jsonObject
                    val shaded = rasterShade(src, rasterPaintOf(paint))
                    val a = rasterOpacityAt(paint, mapZoom)
                    out = doubleArrayOf(
                        ((shaded shr 16) and 0xFF) * a + out[0] * (1 - a),
                        ((shaded shr 8) and 0xFF) * a + out[1] * (1 - a),
                        (shaded and 0xFF) * a + out[2] * (1 - a),
                    )
                }
                (0xFF shl 24) or (Math.round(out[0]).toInt() shl 16) or (Math.round(out[1]).toInt() shl 8) or Math.round(out[2]).toInt()
            }
            val sorted = blended.map { lightness(it) }.sorted()
            val mean = sorted.average(); val median = sorted[sorted.size / 2]
            if (mean > limit) failures += "map zoom $mapZoom (${drawn.size} layers): blended mean lightness %.3f, limit %.3f".format(mean, limit)
            if (median > limit) failures += "map zoom $mapZoom: blended ground (median) lightness %.3f, limit %.3f".format(median, limit)
        }
        if (failures.isNotEmpty()) fail("the crossfade leaves the dark band:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `raster-opacity evaluates as the style spec defines it, and other expressions are refused`() {
        fun opacity(paint: String, zoom: Double) = rasterOpacityAt(json.parseToJsonElement(paint).jsonObject, zoom)
        assertEquals(1.0, opacity("{}", 9.0), 0.0)
        assertEquals(0.25, opacity("""{"raster-opacity": 0.25}""", 9.0), 0.0)
        val fade = """{"raster-opacity": ["interpolate", ["linear"], ["zoom"], 9.5, 0, 9.7, 1]}"""
        assertEquals(0.0, opacity(fade, 3.0), 0.0)
        assertEquals(0.0, opacity(fade, 9.5), 0.0)
        assertEquals(0.5, opacity(fade, 9.6), 1e-12)
        assertEquals(1.0, opacity(fade, 9.7), 0.0)
        assertEquals(1.0, opacity(fade, 20.0), 0.0)
        for (unsupported in listOf("""{"raster-opacity": ["step", ["zoom"], 0, 9.5, 1]}""", """{"raster-opacity": ["interpolate", ["exponential", 2], ["zoom"], 9.5, 0, 9.7, 1]}""", """{"raster-opacity": ["get", "x"]}""")) {
            try { opacity(unsupported, 9.6); fail("$unsupported should be refused, not guessed at") }
            catch (expected: IllegalStateException) {} catch (expected: IllegalArgumentException) {}
        }
    }

    private fun decodeTile(path: String): IntArray {
        val bitmap = BitmapFactory.decodeFile(path) ?: error("$path did not decode")
        val pixels = IntArray(256 * 256)
        bitmap.getPixels(pixels, 0, 256, 0, 0, 256, 256)
        return IntArray(pixels.size) { pixels[it] or (0xFF shl 24) }
    }

    private val NIGHT_STREET_ID_FOR_TEST = "basemap-street"

    // ---- attribution -----------------------------------------------------------------------------------------

    @Test
    fun `the caption at topo night credits both sources`() {
        val credits = mapCreditsFor(Basemap.OPEN_TOPO_MAP, useOfflineTiles = false, nightMode = true)
        assertEquals(listOf(Basemap.OPEN_TOPO_MAP.attribution, Basemap.OSM_STANDARD.attribution), credits)
        assertEquals("© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA) · © OpenStreetMap contributors", attributionCaption(credits))
    }

    @Test
    fun `the caption is unchanged everywhere else`() {
        for (basemap in Basemap.entries) for (night in listOf(false, true)) {
            if (basemap == Basemap.OPEN_TOPO_MAP && night) continue
            assertEquals("$basemap night=$night", listOf(mapAttributionFor(basemap, false)), mapCreditsFor(basemap, useOfflineTiles = false, nightMode = night))
        }
        for (basemap in Basemap.entries) for (night in listOf(false, true)) {
            assertEquals("offline $basemap night=$night", listOf(mapAttributionFor(basemap, true)), mapCreditsFor(basemap, useOfflineTiles = true, nightMode = night))
        }
    }

    // ---- the model's own contract ------------------------------------------------------------------------------

    @Test
    fun `the model's V1 is nightColorOf over every sampled tile colour`() {
        for (set in topoTiles.values + streetTiles.values) for (day in set.colours.keys) {
            val expected = nightColorOf(day); val got = rasterShade(day, v1)
            for (shift in listOf(16, 8, 0)) {
                assertTrue("day #%06X: nightColorOf %08X, model %08X".format(day and 0xFFFFFF, expected, got),
                    Math.abs(((expected shr shift) and 0xFF) - ((got shr shift) and 0xFF)) <= 1)
            }
        }
    }

    @Test
    fun `raster tile zoom is one ahead of the map zoom for 256 px tiles and rounds half up`() {
        assertEquals(10, rasterTileZoomFor(9.499))
        assertEquals(11, rasterTileZoomFor(9.5))
        assertEquals(8, rasterTileZoomFor(6.5))
        assertEquals(7, rasterTileZoomFor(6.49))
        assertEquals(9, rasterTileZoomFor(9.0, tileSize = 512))
    }

    private fun kotlinx.serialization.json.JsonPrimitive.double(): Double = doubleOrNull ?: error("not a number: $this")
    private fun kotlinx.serialization.json.JsonPrimitive.int(): Int = content.toInt()

    // --- The documents this change must not move, as literal bytes (`styleJsonFor`'s output before it). ---

    @Test
    fun `day style JSON is byte-identical to before for every basemap`() {
        assertEquals(TOPO_DAY, styleJsonFor(Basemap.OPEN_TOPO_MAP, night = false))
        assertEquals(STREET_DAY, styleJsonFor(Basemap.OSM_STANDARD, night = false))
    }

    @Test
    fun `Street's night style JSON is byte-identical to before`() {
        assertEquals(STREET_NIGHT, styleJsonFor(Basemap.OSM_STANDARD, night = true))
    }

    private val TOPO_DAY = listOf(
        "{",
        "  \"version\": 8,",
        "  \"sources\": {",
        "    \"basemap\": {",
        "      \"type\": \"raster\",",
        "      \"tiles\": [\"https://a.tile.opentopomap.org/{z}/{x}/{y}.png\"],",
        "      \"tileSize\": 256,",
        "      \"maxzoom\": 17,",
        "      \"attribution\": \"© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)\"",
        "    }",
        "  },",
        "  \"layers\": [",
        "    {\"id\": \"basemap\", \"type\": \"raster\", \"source\": \"basemap\"}",
        "  ]",
        "}",
    ).joinToString("\n")

    private val STREET_DAY = listOf(
        "{",
        "  \"version\": 8,",
        "  \"sources\": {",
        "    \"basemap\": {",
        "      \"type\": \"raster\",",
        "      \"tiles\": [\"https://tile.openstreetmap.org/{z}/{x}/{y}.png\"],",
        "      \"tileSize\": 256,",
        "      \"maxzoom\": 19,",
        "      \"attribution\": \"© OpenStreetMap contributors\"",
        "    }",
        "  },",
        "  \"layers\": [",
        "    {\"id\": \"basemap\", \"type\": \"raster\", \"source\": \"basemap\"}",
        "  ]",
        "}",
    ).joinToString("\n")

    private val STREET_NIGHT = listOf(
        "{",
        "  \"version\": 8,",
        "  \"sources\": {",
        "    \"basemap\": {",
        "      \"type\": \"raster\",",
        "      \"tiles\": [\"https://tile.openstreetmap.org/{z}/{x}/{y}.png\"],",
        "      \"tileSize\": 256,",
        "      \"maxzoom\": 19,",
        "      \"attribution\": \"© OpenStreetMap contributors\"",
        "    }",
        "  },",
        "  \"layers\": [",
        "    {\"id\": \"basemap\", \"type\": \"raster\", \"source\": \"basemap\",",
        "      \"paint\": {",
        "        \"raster-brightness-min\": 1,",
        "        \"raster-brightness-max\": 0,",
        "        \"raster-hue-rotate\": 180",
        "      }}",
        "  ]",
        "}",
    ).joinToString("\n")
}
