package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.map.OFFLINE_STYLE_URL
import com.zynergylabs.forager.app.map.STREET_STYLE_URLS
import java.io.File
import kotlin.math.pow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NightExceptionsTest]'s twin for Street v4, the fill-in (Forager-cartography `docs/design/2026-10-30-fill-in-rulings.md`;
 * the owner: "Keep it osm-like", "Yes, ship it", and for ramps "Invert them too (Recommended)"; then motorways "A: OSM
 * pink" and "Invert like OSM", tertiary "Dark grey #333333" and rail "Plain inversion"). v4 has its own night tables,
 * chosen by the style's URI ([streetNightTablesFor]): the major roads, motorways, ramps and rail take the plain inversion
 * (no layer exception) except the tertiary's day white, which is `#333333`, and the five new fills (residential,
 * commercial, railway land, farmland, parks) take their ruled night colours. v1 to v3, and Street under Topographical, keep [STREET_NIGHT_TABLES], which
 * [NightExceptionsTest] holds.
 *
 * Read from `map-style/forager-street-v4.json`: v4 is not on the Worker yet.
 */
class NightExceptionsV4Test {

    private val style = Json.parseToJsonElement(File("../map-style/forager-street-v4.json").readText()).jsonObject
    private val metadata = style.getValue("metadata").jsonObject
    private val palette = metadata.getValue("forager:palette").jsonObject
    private val layers = style.getValue("layers").jsonArray.associateBy { it.jsonObject.getValue("id").jsonPrimitive.content }

    /** v4 as the Worker would serve it, beside v3: `/style/street-v4.json`. */
    private val v4Url = "https://forager-pmtiles.brandonlee1-894.workers.dev/style/street-v4.json"
    private val tables = streetNightTablesFor(v4Url)

    private fun rgb(hex: String): Int = hex.removePrefix("#").toInt(16)

    private fun luminance(colour: Int): Double {
        fun channel(c: Int): Double = (c / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channel((colour shr 16) and 0xFF) + 0.7152 * channel((colour shr 8) and 0xFF) + 0.0722 * channel(colour and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private val nightGrounds: Map<String, Int> by lazy {
        mapOf(
            "forest" to tables.fillColours.getValue(rgb(palette.getValue("forest").jsonPrimitive.content)),
            "grass" to nightColorOf((0xFF shl 24) or rgb(palette.getValue("grass").jsonPrimitive.content)),
            "ground" to nightColorOf((0xFF shl 24) or rgb("#e2dfda")),
        )
    }

    /** The lines v4 inverts that v3 gives a night exception (the rulings' table, "Motorways", "Night tertiary and rail"). */
    private val INVERTED_LINES = setOf("roads_major", "roads_bridges_major", "roads_highway", "roads_bridges_highway", "roads_link", "roads_bridges_link", "roads_rail")

    private val INFORMAL_NIGHT_FOREST_BAND = 1.9..2.3
    private val INFORMAL_LAYERS = setOf("roads_path_informal_z13", "roads_path_informal")

    private fun nightOf(id: String, property: String): NightRecolour {
        val value = layers.getValue(id).jsonObject.getValue("paint").jsonObject.getValue(property)
        val colour = if (value is JsonPrimitive) StyleColourValue.Literal(value.content) else StyleColourValue.Expression(value.toString())
        return streetNightRecolourOf(LayerColourProperty(id, property, colour), layers.getValue(id).jsonObject["source-layer"]?.jsonPrimitive?.content, tables)
    }

    @Test
    fun `v4's layer exceptions are the design's`() {
        val design = metadata.getValue("forager:night-layers").jsonObject.mapValues { it.value.jsonPrimitive.content }
        assertEquals(design, tables.layerColours)
    }

    @Test
    fun `v4's fill night colours are the design's`() {
        val fills = metadata.getValue("forager:night-fills").jsonObject.entries.associate { (day, night) -> rgb(day) to rgb(night.jsonPrimitive.content) }
        assertEquals(fills, tables.fillColours)
    }

    @Test
    fun `v4's line colours by day colour are the design's`() {
        val design = metadata.getValue("forager:night-line-colours").jsonObject.mapValues { (_, colours) ->
            colours.jsonObject.entries.associate { (day, night) -> rgb(day) to rgb(night.jsonPrimitive.content) }
        }
        assertEquals(design, tables.lineColours)
    }

    @Test
    fun `v4's night is the ruled table`() {
        // The rulings' table, by hand, so a design and app that drifted together still fail here.
        assertEquals(
            mapOf(0xD5D1CB to 0x2D2A25, 0xE3CFCA to 0x362824, 0xD6D1D4 to 0x2B2829, 0xE8EACD to 0x2E2A10, 0xCDE5BF to 0x2C461E),
            tables.fillColours - STREET_NIGHT_FILL_COLOURS.keys,
        )
        assertEquals(STREET_NIGHT_FILL_COLOURS, tables.fillColours.filterKeys { it in STREET_NIGHT_FILL_COLOURS })
        // The major roads, the motorways and their ramps ("Invert like OSM"), and the rail ("Plain inversion") take the
        // inversion: no layer exception. The rest are v3's.
        assertEquals(STREET_NIGHT_LAYER_COLOURS - INVERTED_LINES, tables.layerColours)
        // The night tertiary ("Dark grey #333333 (Recommended)"): day white on the major-road layers.
        // The night tertiary ("Dark grey #333333") on the major roads, and the minor roads' white with it ("Low-zoom roads").
        // landuse_edges' darker rims are held in their own test.
        assertEquals(
            mapOf("roads_major" to mapOf(0xFFFFFF to 0x333333), "roads_bridges_major" to mapOf(0xFFFFFF to 0x333333), "roads_minor" to mapOf(0xFFFFFF to 0x333333)),
            tables.lineColours - "landuse_edges",
        )
    }

    @Test
    fun `the motorways, their ramps and casings, and the rail take the plain inversion`() {
        val casings = listOf("roads_highway_casing_early", "roads_highway_casing_late", "roads_bridges_highway_casing", "roads_link_casing", "roads_bridges_link_casing")
        for (id in (INVERTED_LINES - setOf("roads_major", "roads_bridges_major")) + casings) {
            val property = LayerColourProperty(id, "line-color", dayValueOf(id, "line-color"))
            assertEquals(id, offlineNightRecolourOf(property), nightOf(id, "line-color"))
        }
        // OSM Carto's motorway fill and casing (the rulings' "Motorways"), so from z12.5 the inversion is #7f2939 and #e83673;
        // below, Carto's low-zoom pink #e66e89 and casing #c24e6b ("Low-zoom roads"), inverted too.
        assertEquals(listOf(nightOfDay(0xE66E89), "rgba(127, 41, 57, 1)"), nightStopsOf("roads_highway"))
        assertEquals(listOf(nightOfDay(0xC24E6B), "rgba(232, 54, 115, 1)"), nightStopsOf("roads_highway_casing_late"))
        assertEquals(StyleColourValue.Literal("rgba(99, 108, 111, 1)"), (nightOf("roads_rail", "line-color") as NightRecolour.Recoloured).night)
    }

    @Test
    fun `the minor roads are a faint texture by day, and dark grey at night like the tertiary`() {
        // "Low-zoom roads", A: #c4beb4 until z11.5, white by z12; at night the white is #333333 and the low colour inverts.
        assertEquals(listOf(nightOfDay(0xC4BEB4), "rgba(51, 51, 51, 1)"), nightStopsOf("roads_minor"))
        assertEquals(tables.lineColours.getValue("roads_major"), tables.lineColours.getValue("roads_minor"))
    }

    @Test
    fun `the night tertiary sits under the trails on the night forest, grass and ground`() {
        // The app's own night tertiary, as STREET_V4_NIGHT_TABLES draws it (Sorrel's re-look: not a copy of the value).
        val tertiary = tables.lineColours.getValue("roads_major").getValue(0xFFFFFF)
        val footpath = nightColorOf((0xFF shl 24) or rgb(palette.getValue("footpath").jsonPrimitive.content)) and 0xFFFFFF
        nightGrounds.forEach { (name, ground) ->
            val ratio = contrast(tertiary, ground)
            val ceiling = contrast(footpath, ground)
            assertTrue("night tertiary on night $name is $ratio:1, the night footpath $ceiling:1", ratio < ceiling)
            if (name == "forest") assertTrue("night tertiary on night forest is $ratio:1, outside 1.7 to 2.3", ratio in 1.7..2.3)
        }
    }

    @Test
    fun `each v4 exception layer exists, and its line takes the exception at night`() {
        tables.layerColours.forEach { (id, hex) ->
            assertTrue("no layer $id in Street v4", id in layers)
            assertEquals(id, StyleColourValue.Literal(hex), (nightOf(id, "line-color") as NightRecolour.Recoloured).night)
        }
    }

    @Test
    fun `every v4 exception reads on the night forest, grass and ground`() {
        val nightFootpathOnForest = contrast(
            nightColorOf((0xFF shl 24) or rgb(palette.getValue("footpath").jsonPrimitive.content)),
            nightGrounds.getValue("forest"),
        )
        tables.layerColours.forEach { (layer, hex) ->
            nightGrounds.forEach { (name, ground) ->
                val ratio = contrast(rgb(hex), ground)
                if (layer in INFORMAL_LAYERS && name == "forest") {
                    assertTrue("$layer $hex on night forest is $ratio:1, outside the band", ratio in INFORMAL_NIGHT_FOREST_BAND)
                    assertTrue("$layer $hex on night forest is $ratio:1, not below the night footpath's $nightFootpathOnForest", ratio < nightFootpathOnForest)
                } else {
                    assertTrue("$layer $hex on night $name is $ratio:1", ratio >= 2.5)
                }
            }
        }
    }

    @Test
    fun `the settled and park fills take their ruled night colours`() {
        fun nightColours(id: String): List<String> {
            val night = (nightOf(id, "fill-color") as NightRecolour.Recoloured).night
            val text = when (night) {
                is StyleColourValue.Literal -> night.text
                is StyleColourValue.Expression -> night.json
            }
            return Regex("""rgba\((\d+), (\d+), (\d+), 1\)""").findAll(text)
                .map { m -> "#" + (1..3).joinToString("") { "%02x".format(m.groupValues[it].toInt()) } }.toList()
        }
        // In the design's order: residential, commercial, railway, farmland, and the earth default (inverted).
        assertEquals(listOf("#2d2a25", "#362824", "#2b2829", "#2e2a10", "#24211c"), nightColours("landuse_settled"))
        assertEquals(listOf("#2c461e"), nightColours("landuse_parks_fill"))
    }

    @Test
    fun `the major roads and their ramps invert, but the tertiary is dark grey, so each ramp matches its road`() {
        for (id in listOf("roads_major", "roads_bridges_major")) {
            // A zoom cross-fade (10.5 to 11.5) from Carto's low-zoom colours to the ruled ones, each a match on kind_detail.
            val night = ((nightOf(id, "line-color") as NightRecolour.Recoloured).night as StyleColourValue.Expression).json
            val fade = Json.parseToJsonElement(night).jsonArray
            assertEquals(id, "interpolate", fade[0].jsonPrimitive.content)
            val (low, high) = listOf(fade[4].jsonArray, fade[6].jsonArray)
            // From z11.5, the ruled night: trunk #845104, primary #674400, secondary #463100 (the inversion), tertiary #333333.
            val ruled = mapOf("trunk" to "rgba(132, 81, 4, 1)", "primary" to "rgba(103, 68, 0, 1)", "secondary" to "rgba(70, 49, 0, 1)", "tertiary" to "rgba(51, 51, 51, 1)")
            // Below z10.5, Carto's low-zoom colours (#eeab4f, #efc46c, #ecd58c, #bdb7ad) inverted: the white is not in them.
            val lowZoom = mapOf("trunk" to nightOfDay(0xEEAB4F), "primary" to nightOfDay(0xEFC46C), "secondary" to nightOfDay(0xECD58C), "tertiary" to nightOfDay(0xBDB7AD))
            for ((match, expected) in listOf(high to ruled, low to lowZoom)) {
                val byKind = kindsOf(match)
                expected.forEach { (road, colour) ->
                    assertEquals("$id $road", colour, byKind[road])
                    assertEquals("$id ${road}_link", colour, byKind["${road}_link"])
                }
            }
            // Any other kind_detail keeps v3's major colour from z11.5, inverted.
            assertEquals(id, nightOfDay(0xF6D68F), high.last().jsonPrimitive.content)
        }
    }

    private fun kindsOf(match: kotlinx.serialization.json.JsonArray): Map<String, String> = (2 until match.size - 1 step 2).flatMap { i ->
        val kinds = match[i].let { k -> if (k is JsonPrimitive) listOf(k.content) else k.jsonArray.map { it.jsonPrimitive.content } }
        kinds.map { it to match[i + 1].jsonPrimitive.content }
    }.toMap()

    /** A layer's day colour as the style holds it: a literal, or an expression as JSON. */
    private fun dayValueOf(id: String, property: String): StyleColourValue {
        val value = layers.getValue(id).jsonObject.getValue("paint").jsonObject.getValue(property)
        return if (value is JsonPrimitive) StyleColourValue.Literal(value.content) else StyleColourValue.Expression(value.toString())
    }

    /** The night colours at each stop of a layer's line-color zoom cross-fade, low zoom first. */
    private fun nightStopsOf(id: String): List<String> {
        val night = ((nightOf(id, "line-color") as NightRecolour.Recoloured).night as StyleColourValue.Expression).json
        val fade = Json.parseToJsonElement(night).jsonArray
        return (4 until fade.size step 2).map { fade[it].jsonPrimitive.content }
    }

    private fun nightOfDay(day: Int): String {
        val n = nightColorOf((0xFF shl 24) or day)
        return "rgba(${(n shr 16) and 0xFF}, ${(n shr 8) and 0xFF}, ${n and 0xFF}, 1)"
    }

    @Test
    fun `the area edges take their darker night rims, and the water edges take the night water`() {
        // "Darker rims (Recommended)": each landuse edge's day colour to Sage's REC night rim, by hand. The water night rule
        // paints the water edges the water's colour, so they stay off at night (accepted).
        val rims = mapOf(
            0x94B988 to 0x38552B, 0xC2CDAA to 0x1D240B, 0xB4CAA8 to 0x203216, 0x88B99D to 0x255239, 0xADC8BC to 0x203C36,
            0xB0ABA4 to 0x5D636C, 0xC3BFBA to 0x1D1B18, 0xD0BDB9 to 0x241A18, 0xC4BFC2 to 0x1A1919, 0xCCCEB4 to 0x131207,
            0xC9C3BD to 0x3B352E, 0xC9C0BF to 0x3C3231, 0xC6B3C3 to 0x382735,
        )
        assertEquals(rims, tables.lineColours.getValue("landuse_edges"))
        val night = ((nightOf("landuse_edges", "line-color") as NightRecolour.Recoloured).night as StyleColourValue.Expression).json
        for (rim in rims.values) {
            val text = "rgba(${(rim shr 16) and 0xFF}, ${(rim shr 8) and 0xFF}, ${rim and 0xFF}, 1)"
            assertTrue("landuse_edges at night lacks $text: $night", night.contains(text))
        }
        assertEquals(StyleColourValue.Literal(STREET_NIGHT_WATER), (nightOf("water_edges", "line-color") as NightRecolour.Recoloured).night)
        // Industrial in OSM Carto's lilac (style/landcover.mss:22) inverts to #21111e.
        assertEquals(StyleColourValue.Literal("rgba(33, 17, 30, 1)"), (nightOf("landuse_industrial", "fill-color") as NightRecolour.Recoloured).night)
    }

    @Test
    fun `v4 is chosen by its style's name, and every other style keeps v1 to v3's tables`() {
        assertNotEquals(STREET_NIGHT_TABLES, tables)
        assertEquals(tables, streetNightTablesFor("https://raw.githubusercontent.com/slayer8366/Forager/street-v4/map-style/forager-street-v4.json"))
        for (url in STREET_STYLE_URLS + listOf(null, OFFLINE_STYLE_URL)) {
            assertSame(url.toString(), STREET_NIGHT_TABLES, streetNightTablesFor(url))
        }
    }
}
