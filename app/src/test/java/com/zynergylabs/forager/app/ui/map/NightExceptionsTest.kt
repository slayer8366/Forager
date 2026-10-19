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
 * RECORD -1345: the own Street's roads, railways and boundaries, and the rings of the place dot and the toilets and
 * drinking-water icons, take their own night colours instead of the inversion (the owner: "Roads (Recommended),
 * Railways + boundaries (Recommended), Place dots + icon rings (Recommended)"). Footpaths and tracks were not chosen
 * and keep the inversion. The design's copies are in the served style's metadata; the contrast floors are the ones
 * `map-style/tools/check-street-night.mjs` holds (2.5:1 for the lines, 3:1 for the rings), against the night forest,
 * grass and ground derived with the app's own [nightColorOf].
 */
class NightExceptionsTest {

    private val style = Json.parseToJsonElement(File("../server/pmtiles-worker/src/street-style-v1.json").readText()).jsonObject
    private val metadata = style.getValue("metadata").jsonObject
    private val palette = metadata.getValue("forager:palette").jsonObject

    private fun rgb(hex: String): Int = hex.removePrefix("#").toInt(16)

    private fun luminance(colour: Int): Double {
        fun channel(c: Int): Double = (c / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * channel((colour shr 16) and 0xFF) + 0.7152 * channel((colour shr 8) and 0xFF) + 0.0722 * channel(colour and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private val nightGrounds: Map<String, Int> = mapOf(
        "forest" to nightColorOf((0xFF shl 24) or rgb(palette.getValue("forest").jsonPrimitive.content)),
        "grass" to nightColorOf((0xFF shl 24) or rgb(palette.getValue("grass").jsonPrimitive.content)),
        "ground" to nightColorOf((0xFF shl 24) or rgb("#e2dfda")),
    )

    @Test
    fun `the app's layer exceptions are the design's`() {
        val design = metadata.getValue("forager:night-layers").jsonObject.mapValues { it.value.jsonPrimitive.content }
        assertEquals(design, STREET_NIGHT_LAYER_COLOURS)
    }

    @Test
    fun `the app's icon rings are the design's`() {
        val design = metadata.getValue("forager:night-icon-rings").jsonObject.mapValues { (_, ring) ->
            val o = ring.jsonObject
            IconRingNight(rgb(o.getValue("ring").jsonPrimitive.content), rgb(o.getValue("fill").jsonPrimitive.content), rgb(o.getValue("night").jsonPrimitive.content))
        }
        assertEquals(design, STREET_NIGHT_ICON_RINGS)
    }

    @Test
    fun `every exception reads on the night forest, grass and ground`() {
        STREET_NIGHT_LAYER_COLOURS.forEach { (layer, hex) ->
            nightGrounds.forEach { (name, ground) ->
                val ratio = contrast(rgb(hex), ground)
                assertTrue("$layer $hex on night $name is $ratio:1", ratio >= 2.5)
            }
        }
        STREET_NIGHT_ICON_RINGS.forEach { (icon, ring) ->
            nightGrounds.forEach { (name, ground) ->
                val ratio = contrast(ring.nightRing, ground)
                assertTrue("$icon ring on night $name is $ratio:1", ratio >= 3.0)
            }
        }
    }

    @Test
    fun `each exception layer exists in the Street style, and its line takes the exception at night`() {
        val layers = style.getValue("layers").jsonArray.associateBy { it.jsonObject.getValue("id").jsonPrimitive.content }
        STREET_NIGHT_LAYER_COLOURS.forEach { (id, hex) ->
            val layer = layers[id]?.jsonObject ?: error("no layer $id in the Street style")
            val value = layer.getValue("paint").jsonObject.getValue("line-color")
            val colour = if (value is JsonPrimitive) StyleColourValue.Literal(value.content) else StyleColourValue.Expression(value.toString())
            val outcome = streetNightRecolourOf(LayerColourProperty(id, "line-color", colour), layer["source-layer"]?.jsonPrimitive?.content)
            assertEquals(id, NightRecolour.Recoloured(LayerColourProperty(id, "line-color", colour), StyleColourValue.Literal(hex)), outcome)
        }
    }

    @Test
    fun `footpaths, tracks, tunnels and road edges not chosen keep the inversion`() {
        for (id in listOf("roads_path_foot", "roads_path_track", "roads_tunnels_minor_casing", "roads_major_casing_late", "roads_highway_casing_late")) {
            val property = LayerColourProperty(id, "line-color", StyleColourValue.Literal("#a8452a"))
            assertEquals(id, offlineNightRecolourOf(property), streetNightRecolourOf(property, "roads"))
        }
    }

    @Test
    fun `an icon's ring goes to its night colour, its inside inverts, and its anti-aliased edge blends between them`() {
        val dot = STREET_NIGHT_ICON_RINGS.getValue("townspot")
        val ring = 0xFF868686.toInt()
        val inside = 0xFFF3F3F3.toInt()
        val edge = 0x80BCBCBC.toInt() // on the ring-to-inside line, about half way, half transparent
        val other = 0xFFFF0000.toInt() // a colour off that line
        val night = nightIconPixelsOf(intArrayOf(ring, inside, edge, other), dot)
        assertEquals(0xFFD6D6D6.toInt(), night[0])
        assertEquals(nightColorOf(inside), night[1])
        // #bcbcbc is (188 - 134) / (243 - 134) = 0.495 of the way from ring to inside: 0.495 of the way from #d6d6d6
        // (214) to the inside's inversion #0c0c0c (12): 214 - 0.495 * 202 = 114 (#727272), alpha 0x80 kept.
        assertEquals(0x80727272.toInt(), night[2])
        assertEquals(nightColorOf(other), night[3])
        // With no ring, the plain inversion.
        assertEquals(nightPixelsOf(intArrayOf(ring)).single(), nightIconPixelsOf(intArrayOf(ring), null).single())
    }
}
