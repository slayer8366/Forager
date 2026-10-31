package com.zynergylabs.forager.app.ui.map

import java.io.File
import kotlin.math.pow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BuildingsNightTest]'s twin for Street v4 (Forager-cartography `docs/design/2026-10-30-fill-in-rulings.md`, "Borders,
 * industrial and buildings"): the buildings in OSM Carto's tan, "Tan, like OSM", in place of RECORD -1733's greys, which
 * v1 to v3 keep. Carto's steps are kept ("Keep OSM's steps (Recommended)"): tan `#d9d0c9` at half opacity below z14, one
 * step darker than Carto's z14 shade at z14 (`#ccc0b7`, "One step darker #ccc0b7 (Recommended)": Carto's 4% darker
 * `#d1c6bd` inverts to 1.001:1 on the night water), `#d9d0c9` from z15, and Carto's outline `#b9a99c` from z15 ("OSM's
 * #b9a99c (Recommended)"; openstreetmap-carto style/buildings.mss:1-2).
 *
 * Night takes the plain inversion ("Accept the inversion (Recommended)"): `#372e27`, `#493d34`, `#372e27`, and `#645447`
 * for the outline, each at least 1.05:1 on the night water, the floor `NightWaterTest` holds for every night land fill.
 */
class BuildingsNightV4Test {

    private val layers: Map<String, JsonObject> =
        Json.parseToJsonElement(File("../map-style/forager-street-v4.json").readText()).jsonObject
            .getValue("layers").jsonArray.map { it.jsonObject }.associateBy { it.getValue("id").jsonPrimitive.content }

    private val tables = streetNightTablesFor("https://forager-pmtiles.brandonlee1-894.workers.dev/style/street-v4.json")

    private fun nightOf(id: String, property: String): NightRecolour {
        val layer = layers[id] ?: error("no layer $id in street-v4")
        val value = layer.getValue("paint").jsonObject[property] ?: error("$id has no $property")
        val colour = if (value is JsonPrimitive) StyleColourValue.Literal(value.content) else StyleColourValue.Expression(value.toString())
        return streetNightRecolourOf(LayerColourProperty(id, property, colour), layer["source-layer"]?.jsonPrimitive?.content, tables)
    }

    private fun luminance(colour: Int): Double {
        fun channel(c: Int): Double = (c / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channel((colour shr 16) and 0xFF) + 0.7152 * channel((colour shr 8) and 0xFF) + 0.0722 * channel(colour and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun `the buildings are tan by day, with Carto's steps`() {
        val paint = layers.getValue("buildings").getValue("paint").jsonObject
        assertEquals(Json.parseToJsonElement("""["step",["zoom"],"#d9d0c9",14,"#ccc0b7",15,"#d9d0c9"]"""), paint.getValue("fill-color"))
        assertEquals(Json.parseToJsonElement("""["step",["zoom"],0.5,14,1]"""), paint.getValue("fill-opacity"))
        assertEquals("#b9a99c", layers.getValue("buildings_outline").getValue("paint").jsonObject.getValue("line-color").jsonPrimitive.content)
    }

    @Test
    fun `the buildings fill inverts to 372e27 below z14, 493d34 at z14 and 372e27 from z15, its zoom steps kept`() {
        val night = nightOf("buildings", "fill-color") as NightRecolour.Recoloured
        assertEquals(
            Json.parseToJsonElement("""["step",["zoom"],"rgba(55, 46, 39, 1)",14,"rgba(73, 61, 52, 1)",15,"rgba(55, 46, 39, 1)"]"""),
            Json.parseToJsonElement((night.night as StyleColourValue.Expression).json),
        )
    }

    @Test
    fun `the buildings outline inverts to 645447`() {
        assertEquals(StyleColourValue.Literal("rgba(100, 84, 71, 1)"), (nightOf("buildings_outline", "line-color") as NightRecolour.Recoloured).night)
    }

    @Test
    fun `each night building colour clears the night water floor`() {
        // Read from what the app draws at night for v4, so a changed day colour is measured here, not a copy of it.
        val water = STREET_NIGHT_WATER.removePrefix("#").toInt(16)
        val drawn = listOf(nightOf("buildings", "fill-color"), nightOf("buildings_outline", "line-color")).joinToString(" ") { outcome ->
            when (val night = (outcome as NightRecolour.Recoloured).night) {
                is StyleColourValue.Literal -> night.text
                is StyleColourValue.Expression -> night.json
            }
        }
        val colours = Regex("""rgba\((\d+), (\d+), (\d+), 1\)""").findAll(drawn)
            .map { m -> (m.groupValues[1].toInt() shl 16) or (m.groupValues[2].toInt() shl 8) or m.groupValues[3].toInt() }.toList()
        assertEquals(4, colours.size)
        for (night in colours) {
            val ratio = contrast(night, water)
            assertTrue("night building #%06x on the night water is $ratio:1".format(night), ratio >= 1.05)
        }
    }
}
