package com.zynergylabs.forager.app.ui.map

import java.io.File
import kotlin.math.pow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RECORD -1343: the own Street's water is drawn [STREET_NIGHT_WATER] at night, a lighter blue than its inversion, on
 * every water fill and line; labels and everything else stay pure inversion (-1288), and the offline style is untouched.
 * The contrast floor is the one `map-style/tools/check-street-night.mjs` holds lines to, 3:1, against the night forest
 * and the night grass, which this test derives from the design's day colours with the app's own [nightColorOf].
 */
class NightWaterTest {

    private val style = Json.parseToJsonElement(File("../server/pmtiles-worker/src/street-style-v1.json").readText()).jsonObject
    private val palette = style.getValue("metadata").jsonObject.getValue("forager:palette").jsonObject

    private fun colour(name: String) = palette.getValue(name).jsonPrimitive.content

    private fun argb(hex: String): Int = (0xFF shl 24) or hex.removePrefix("#").toInt(16)

    private fun luminance(argb: Int): Double {
        fun channel(c: Int): Double = (c / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun `the app's night water is the design's`() {
        assertEquals(colour("nightWater"), STREET_NIGHT_WATER)
    }

    @Test
    fun `night water reads at 3 to 1 or better on the night forest and the night grass`() {
        val water = argb(STREET_NIGHT_WATER)
        val forest = nightColorOf(argb(colour("forest")))
        val grass = nightColorOf(argb(colour("grass")))
        val onForest = contrast(water, forest)
        val onGrass = contrast(water, grass)
        assertTrue("night water on the night forest is $onForest:1", onForest >= 3.0)
        assertTrue("night water on the night grass is $onGrass:1", onGrass >= 3.0)
        // What the pure inversion of the day water gave, for the record: the reason for the exception.
        val inverted = nightColorOf(argb("#80deea"))
        assertTrue("the inverted water was ${contrast(inverted, forest)}:1 on the night forest", contrast(inverted, forest) < 3.0)
    }

    @Test
    fun `every water fill and line in the Street style takes the night water, and its labels stay inverted`() {
        val outcomes = style.getValue("layers").jsonArray.flatMap { element ->
            val layer = element.jsonObject
            val paint = layer["paint"]?.jsonObject ?: return@flatMap emptyList()
            val type = layer.getValue("type").jsonPrimitive.content
            val sourceLayer = layer["source-layer"]?.jsonPrimitive?.content
            NIGHT_RECOLOURED_PROPERTIES.getOrDefault(type, emptyList()).mapNotNull { property ->
                paint[property]?.let { value ->
                    val colourValue = if (value is JsonPrimitive && value.isString) {
                        StyleColourValue.Literal(value.content)
                    } else {
                        StyleColourValue.Expression(value.toString())
                    }
                    Triple(layer.getValue("id").jsonPrimitive.content, sourceLayer, streetNightRecolourOf(LayerColourProperty(layer.getValue("id").jsonPrimitive.content, property, colourValue), sourceLayer))
                }
            }
        }
        val water = outcomes.filter { it.second == WATER_SOURCE_LAYER }
        val night = StyleColourValue.Literal(STREET_NIGHT_WATER)
        val drawn = water.filter { it.first in setOf("water", "water_stream", "water_river") }
        assertEquals(listOf("water", "water_stream", "water_river"), drawn.map { it.first })
        drawn.forEach { assertEquals(it.first, night, (it.third as NightRecolour.Recoloured).night) }
        val labels = water.filter { it.first.startsWith("water_label") || it.first == "water_waterway_label" }
        assertTrue("the water labels are walked", labels.isNotEmpty())
        labels.forEach { assertTrue("${it.first} keeps the inversion", (it.third as NightRecolour.Recoloured).night != night) }
        // Everything not drawn from water is the plain inversion, unchanged, apart from the -1345 layer exceptions,
        // which NightExceptionsTest holds.
        outcomes.filter { it.second != WATER_SOURCE_LAYER && it.first !in STREET_NIGHT_LAYER_COLOURS }.forEach { (id, _, outcome) ->
            assertEquals(id, offlineNightRecolourOf(outcome.property), outcome)
        }
    }

    @Test
    fun `the offline style's water keeps its inversion`() {
        // The offline path ([offlineNightRecolourOf]) has no water exception: today's offline night is unchanged.
        val water = LayerColourProperty("water", "fill-color", StyleColourValue.Literal("#80deea"))
        assertEquals(NightRecolour.Recoloured(water, StyleColourValue.Literal("rgba(0, 88, 100, 1)")), offlineNightRecolourOf(water))
    }
}
