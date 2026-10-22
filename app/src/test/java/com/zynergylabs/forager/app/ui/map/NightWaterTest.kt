package com.zynergylabs.forager.app.ui.map

import java.io.File
import kotlin.math.pow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The own Street's water at night: [STREET_NIGHT_WATER], `#163f4b`, on every water fill and line (dispatch 2026-10-22-03,
 * the owner's "Darker water fill", then "Same as water" for streams), in place of -1343's lighter `#8ec5f0`; labels and
 * everything else stay pure inversion (-1288) or their own named exceptions, and the offline style is untouched.
 *
 * The rule is the owner's "Rules from your colours", with the planner's numbers (Forager-cartography,
 * `docs/design/2026-10-22-night-colours-rulings.md`, "The planner's call: the checker numbers"): relative luminance at
 * most 0.050 and blue at most 96, and at least 1.05:1 against every night land fill the app draws, read from the served
 * v2 style and put through the app's own [streetNightRecolourOf]. `map-style/tools/check-street-night.mjs` holds the
 * same numbers on the design side.
 */
class NightWaterTest {

    private val style = Json.parseToJsonElement(File("../server/pmtiles-worker/src/street-style-v2.json").readText()).jsonObject
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

    /** The ruled night ceiling for water: luminance at most 0.050, blue at most 96. */
    private fun underWaterCeiling(argb: Int): Boolean = luminance(argb) <= 0.050 && (argb and 0xFF) <= 96

    private fun layers() = style.getValue("layers").jsonArray.map { it.jsonObject }

    private fun valueOf(element: JsonElement): StyleColourValue =
        if (element is JsonPrimitive && element.isString) StyleColourValue.Literal(element.content) else StyleColourValue.Expression(element.toString())

    /** Every colour in a recoloured value as ARGB at full alpha: `rgba()`/`#rrggbb` strings and `["rgba", ...]` arrays. */
    private fun coloursIn(value: StyleColourValue): List<Int> {
        val rgba = Regex("""^rgba?\(\s*(\d+),\s*(\d+),\s*(\d+)""")
        fun walk(e: JsonElement): List<Int> = when (e) {
            is JsonPrimitive -> if (!e.isString) {
                emptyList()
            } else {
                rgba.find(e.content)?.let { m -> listOf(argb("#" + (1..3).joinToString("") { m.groupValues[it].toInt().toString(16).padStart(2, '0') })) }
                    ?: if (Regex("^#[0-9a-fA-F]{6}$").matches(e.content)) listOf(argb(e.content)) else emptyList()
            }
            is JsonArray -> {
                val op = (e.firstOrNull() as? JsonPrimitive)?.content
                if ((op == "rgba" || op == "rgb") && e.drop(1).take(3).all { (it as? JsonPrimitive)?.doubleOrNull != null }) {
                    val (r, g, b) = e.drop(1).take(3).map { it.jsonPrimitive.doubleOrNull!!.toInt() }
                    listOf((0xFF shl 24) or (r shl 16) or (g shl 8) or b)
                } else {
                    e.flatMap(::walk)
                }
            }
            is JsonObject -> e.values.flatMap(::walk)
        }
        return when (value) {
            is StyleColourValue.Literal -> walk(JsonPrimitive(value.text))
            is StyleColourValue.Expression -> walk(Json.parseToJsonElement(value.json))
        }
    }

    @Test
    fun `the app's night water is the design's`() {
        assertEquals(colour("nightWater"), STREET_NIGHT_WATER)
    }

    @Test
    fun `night water is under the ruled ceiling, and the lighter blue it replaces is not`() {
        val water = argb(STREET_NIGHT_WATER)
        assertTrue("night water $STREET_NIGHT_WATER has luminance ${luminance(water)} and blue ${water and 0xFF}", underWaterCeiling(water))
        // -1343's #8ec5f0: luminance 0.52, blue 240. The rule exists to turn it away.
        assertFalse("the lighter blue #8ec5f0 passed the ceiling", underWaterCeiling(argb("#8ec5f0")))
    }

    @Test
    fun `night water reads at 1_05 to 1 or better on every night land fill the app draws`() {
        val water = argb(STREET_NIGHT_WATER)
        // Every fill and background outside the water source layer, recoloured by the app's own Street night.
        val nightLand = layers()
            .filter { it.getValue("type").jsonPrimitive.content in setOf("fill", "background") && it["source-layer"]?.jsonPrimitive?.content != WATER_SOURCE_LAYER }
            .flatMap { layer ->
                val id = layer.getValue("id").jsonPrimitive.content
                val property = if (layer.getValue("type").jsonPrimitive.content == "background") "background-color" else "fill-color"
                val value = layer["paint"]?.jsonObject?.get(property) ?: return@flatMap emptyList()
                val outcome = streetNightRecolourOf(LayerColourProperty(id, property, valueOf(value)), layer["source-layer"]?.jsonPrimitive?.content)
                coloursIn((outcome as NightRecolour.Recoloured).night).map { id to it }
            }
        // The sample has to hold the fills that could fail: the forest, the wetland and the bare rock at their night
        // colours, and the night grass (#2d3911), the closest to the water (1.08:1).
        val drawn = nightLand.map { it.second }.toSet()
        listOf("#446835", "#2a4f47", "#6e7580", "#2d3911").forEach { assertTrue("no night land fill $it read from the style", argb(it) in drawn) }
        assertTrue("only ${drawn.size} night land colours read", drawn.size >= 20)
        nightLand.forEach { (id, land) ->
            val ratio = contrast(water, land)
            assertTrue("night water on $id's ${Integer.toHexString(land)} is $ratio:1", ratio >= 1.05)
        }
    }

    @Test
    fun `every water fill and line in the Street style takes the night water, and its labels stay inverted`() {
        val outcomes = layers().flatMap { layer ->
            val paint = layer["paint"]?.jsonObject ?: return@flatMap emptyList()
            val type = layer.getValue("type").jsonPrimitive.content
            val sourceLayer = layer["source-layer"]?.jsonPrimitive?.content
            val id = layer.getValue("id").jsonPrimitive.content
            NIGHT_RECOLOURED_PROPERTIES.getOrDefault(type, emptyList()).mapNotNull { property ->
                paint[property]?.let { value -> Triple(id, sourceLayer, streetNightRecolourOf(LayerColourProperty(id, property, valueOf(value)), sourceLayer)) }
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
        // Everything not drawn from water is the plain inversion, apart from the -1345 layer exceptions and the fills that
        // draw a STREET_NIGHT_FILL_COLOURS colour, which NightExceptionsTest holds. Those fills are exactly the forest's.
        val differing = outcomes.filter { it.second != WATER_SOURCE_LAYER && it.first !in STREET_NIGHT_LAYER_COLOURS }
            .filter { (_, _, outcome) -> offlineNightRecolourOf(outcome.property) != outcome }
        assertEquals(listOf("landcover/fill-color", "landuse_park/fill-color"), differing.map { "${it.first}/${it.third.property.property}" })
    }

    @Test
    fun `the offline style's water keeps its inversion`() {
        // The offline path ([offlineNightRecolourOf]) has no water exception: today's offline night is unchanged.
        val water = LayerColourProperty("water", "fill-color", StyleColourValue.Literal("#80deea"))
        assertEquals(NightRecolour.Recoloured(water, StyleColourValue.Literal("rgba(0, 88, 100, 1)")), offlineNightRecolourOf(water))
    }
}
