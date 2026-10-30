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
 * the owner: "Keep it osm-like", "Yes, ship it", and for ramps "Invert them too (Recommended)"). v4 has its own night
 * tables, chosen by the style's URI ([streetNightTablesFor]): the major-road centres take the plain inversion (no layer
 * exception), rail is `#a3acae`, and the five new fills (residential, commercial, railway land, farmland, parks) take
 * their ruled night colours. v1 to v3, and Street under Topographical, keep [STREET_NIGHT_TABLES], which
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
    fun `v4's night is the ruled table`() {
        // The rulings' table, by hand, so a design and app that drifted together still fail here.
        assertEquals(
            mapOf(0xD5D1CB to 0x2D2A25, 0xE3CFCA to 0x362824, 0xD6D1D4 to 0x2B2829, 0xE8EACD to 0x2E2A10, 0xCDE5BF to 0x2C461E),
            tables.fillColours - STREET_NIGHT_FILL_COLOURS.keys,
        )
        assertEquals(STREET_NIGHT_FILL_COLOURS, tables.fillColours.filterKeys { it in STREET_NIGHT_FILL_COLOURS })
        assertEquals("#a3acae", tables.layerColours["roads_rail"])
        assertTrue("roads_major" !in tables.layerColours && "roads_bridges_major" !in tables.layerColours)
        // Motorways and their ramps keep v3's amber.
        for (id in listOf("roads_highway", "roads_bridges_highway", "roads_link", "roads_bridges_link")) {
            assertEquals(id, STREET_NIGHT_LAYER_COLOURS.getValue(id), tables.layerColours[id])
        }
        assertEquals(STREET_NIGHT_LAYER_COLOURS - setOf("roads_major", "roads_bridges_major", "roads_rail"), tables.layerColours - "roads_rail")
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
    fun `the major roads and their ramps take the plain inversion, so each ramp matches its road`() {
        for (id in listOf("roads_major", "roads_bridges_major")) {
            val value = layers.getValue(id).jsonObject.getValue("paint").jsonObject.getValue("line-color")
            val property = LayerColourProperty(id, "line-color", StyleColourValue.Expression(value.toString()))
            assertEquals(id, offlineNightRecolourOf(property), nightOf(id, "line-color"))
            // The ruled night: trunk #845104, primary #674400, secondary #463100, tertiary #000000, each with its ramp.
            val night = ((nightOf(id, "line-color") as NightRecolour.Recoloured).night as StyleColourValue.Expression).json
            val match = Json.parseToJsonElement(night).jsonArray
            val byKind = (2 until match.size - 1 step 2).flatMap { i ->
                val kinds = match[i].let { k -> if (k is JsonPrimitive) listOf(k.content) else k.jsonArray.map { it.jsonPrimitive.content } }
                kinds.map { it to match[i + 1].jsonPrimitive.content }
            }.toMap()
            val expected = mapOf("trunk" to "rgba(132, 81, 4, 1)", "primary" to "rgba(103, 68, 0, 1)", "secondary" to "rgba(70, 49, 0, 1)", "tertiary" to "rgba(0, 0, 0, 1)")
            expected.forEach { (road, colour) ->
                assertEquals("$id $road", colour, byKind[road])
                assertEquals("$id ${road}_link", colour, byKind["${road}_link"])
            }
        }
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
