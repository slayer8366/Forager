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
 *
 * Dispatch 2026-10-19-41: informal paths take their own night colour too, held on the night forest to the owner's band
 * ([INFORMAL_NIGHT_FOREST_BAND]) in place of the 2.5:1 floor, for those two layers on that one ground only.
 *
 * Dispatch 2026-10-22-03 (Trail Map's character, style v2): the forest, wetland and bare-rock fills take their own
 * night colours ([STREET_NIGHT_FILL_COLOURS]), and the tree glyph pattern is painted `#5d804f`
 * ([STREET_NIGHT_PATTERN_COLOURS]), held to 1.35:1 on the night forest. The night forest is now `#446835`, so every
 * floor on "the night forest" here is measured on what the app draws there.
 */
class NightExceptionsTest {

    private val style = Json.parseToJsonElement(File("../server/pmtiles-worker/src/street-style-v2.json").readText()).jsonObject
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

    private val nightForest: Int = STREET_NIGHT_FILL_COLOURS.getValue(rgb(palette.getValue("forest").jsonPrimitive.content))

    private val nightGrounds: Map<String, Int> = mapOf(
        "forest" to nightForest,
        "grass" to nightColorOf((0xFF shl 24) or rgb(palette.getValue("grass").jsonPrimitive.content)),
        "ground" to nightColorOf((0xFF shl 24) or rgb("#e2dfda")),
    )

    /**
     * Informal paths on the night forest: the owner, "Accept the range (Recommended)" (docs/audits/2026-10-20-cloudflare-
     * upload-prep.md on pi-build, "The owner's rulings on revision 1"), about 1.9 to 2.3:1 and below the night footpath,
     * as a per-layer band and not a widened floor. The planner set the edges at exactly 1.9 and 2.3.
     */
    private val INFORMAL_NIGHT_FOREST_BAND = 1.9..2.3
    private val INFORMAL_LAYERS = setOf("roads_path_informal_z13", "roads_path_informal")

    @Test
    fun `the design's night bands are the ones this test holds, informal paths on the night forest only`() {
        val bands = metadata.getValue("forager:night-bands").jsonObject
        assertEquals(INFORMAL_LAYERS, bands.keys)
        bands.forEach { (layer, grounds) ->
            assertEquals(layer, setOf("forest"), grounds.jsonObject.keys)
            val band = grounds.jsonObject.getValue("forest").jsonObject
            assertEquals(layer, INFORMAL_NIGHT_FOREST_BAND.start, band.getValue("min").jsonPrimitive.content.toDouble(), 0.0)
            assertEquals(layer, INFORMAL_NIGHT_FOREST_BAND.endInclusive, band.getValue("max").jsonPrimitive.content.toDouble(), 0.0)
            assertEquals(layer, "footpath", band.getValue("belowNightOf").jsonPrimitive.content)
        }
    }

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
        val nightFootpathOnForest = contrast(
            nightColorOf((0xFF shl 24) or rgb(palette.getValue("footpath").jsonPrimitive.content)),
            nightGrounds.getValue("forest"),
        )
        STREET_NIGHT_LAYER_COLOURS.forEach { (layer, hex) ->
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
        // Dispatch 2026-10-19-41: Street v1's one footpath layer, roads_path_foot, became one layer per trail group; hard
        // trails keep the inversion (#7a1e10 to #ffad9f). Every path layer in the style but the two informal ones, read
        // from the style itself so a layer added later is covered without editing this list (Sorrel's F1: a hand list
        // had covered 9 of the 15). The road edges are named; each is checked to exist, so a renamed layer fails here.
        val styleIds = style.getValue("layers").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        val pathIds = styleIds.filter { it.startsWith("roads_path_") && !it.startsWith("roads_path_informal") }
        // The 15 non-informal footpath layers and the track; fewer would mean the prefix stopped matching the paths.
        assertTrue("only ${pathIds.size} path layers read from the style: $pathIds", pathIds.size >= 16)
        val edges = listOf("roads_tunnels_minor_casing", "roads_major_casing_late", "roads_highway_casing_late")
        assertEquals(emptyList<String>(), edges.filter { it !in styleIds })
        val ids = pathIds + edges
        for (id in ids) {
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

    @Test
    fun `the app's fill and pattern night colours are the design's`() {
        val fills = metadata.getValue("forager:night-fills").jsonObject.entries.associate { (day, night) -> rgb(day) to rgb(night.jsonPrimitive.content) }
        assertEquals(fills, STREET_NIGHT_FILL_COLOURS)
        val patterns = metadata.getValue("forager:night-patterns").jsonObject.mapValues { rgb(it.value.jsonPrimitive.content) }
        assertEquals(patterns, STREET_NIGHT_PATTERN_COLOURS)
    }

    @Test
    fun `forest, wetland and bare rock take their night colours in the park fill, and its other kinds invert`() {
        val layers = style.getValue("layers").jsonArray.associateBy { it.jsonObject.getValue("id").jsonPrimitive.content }
        fun nightOf(id: String): String {
            val value = layers.getValue(id).jsonObject.getValue("paint").jsonObject.getValue("fill-color")
            val outcome = streetNightRecolourOf(LayerColourProperty(id, "fill-color", StyleColourValue.Expression(value.toString())), "landuse")
            return ((outcome as NightRecolour.Recoloured).night as StyleColourValue.Expression).json
        }
        val park = nightOf("landuse_park")
        // The ruled night colours, each in its own branch, in the order the design writes them: forest, grass (inverted),
        // glacier, sand, military (inverted), wetland, bare rock, and the ground default (inverted).
        val colours = Regex("""rgba\((\d+), (\d+), (\d+), 1\)""").findAll(park).map { m -> (1..3).map { m.groupValues[it].toInt() } }.toList()
        val expected = listOf("#446835", "#a9d39b", "#e7e7e7", "#e2e0d7", "#c6dcdc", "#2a4f47", "#6e7580", "#e2dfda").mapIndexed { i, hex ->
            val c = rgb(hex)
            // Index 0, 5 and 6 are the ruled colours as they are; the rest are their day colour's inversion.
            val night = if (i in setOf(0, 5, 6)) c else nightColorOf((0xFF shl 24) or c) and 0xFFFFFF
            listOf((night shr 16) and 0xFF, (night shr 8) and 0xFF, night and 0xFF)
        }.toMutableList()
        expected[1] = (nightColorOf((0xFF shl 24) or rgb(palette.getValue("grass").jsonPrimitive.content)) and 0xFFFFFF).let { listOf((it shr 16) and 0xFF, (it shr 8) and 0xFF, it and 0xFF) }
        assertEquals(expected, colours)
        // The landcover's forest default takes the forest's night colour too.
        assertTrue(nightOf("landcover"), nightOf("landcover").contains("rgba(68, 104, 53, 1)"))
        // A fill colour only matches at full opacity: a half-transparent forest inverts.
        val faint = LayerColourProperty("landuse_park", "fill-color", StyleColourValue.Literal("rgba(169, 211, 155, 0.5)"))
        assertEquals(NightRecolour.Recoloured(faint, StyleColourValue.Literal("rgba(67, 109, 53, 0.5)")), streetNightRecolourOf(faint, "landuse"))
    }

    @Test
    fun `the tree glyph reads at 1_35 to 1 on the night forest, and would not on the inversion`() {
        val glyph = STREET_NIGHT_PATTERN_COLOURS.getValue("tree_dark_irregular")
        val onForest = contrast(glyph, nightForest)
        assertTrue("tree glyph on the night forest is $onForest:1", onForest >= 1.35)
        // The inversion #436d35 is why the forest exception ships with the glyph: 1.34:1 there.
        val inverted = nightColorOf((0xFF shl 24) or rgb(palette.getValue("forest").jsonPrimitive.content)) and 0xFFFFFF
        assertEquals(0x436D35, inverted)
        assertTrue(contrast(glyph, inverted) < 1.35)
    }

    @Test
    fun `a pattern is painted its night colour with each pixel's alpha kept`() {
        val day = intArrayOf(0x00000000, 0xFF3E7A3A.toInt(), 0x803E7A3A.toInt(), 0x05336633, 0x00FFFFFF)
        val night = nightPatternPixelsOf(day, 0x5D804F)
        assertEquals(listOf(0x00000000, 0xFF5D804F.toInt(), 0x805D804F.toInt(), 0x055D804F, 0x00FFFFFF), night.toList())
    }

    @Test
    fun `every fill pattern the style draws is painted at night, the tree and the tufts each their ruled colour`() {
        val patterns = style.getValue("layers").jsonArray
            .mapNotNull { it.jsonObject["paint"]?.jsonObject?.get("fill-pattern")?.jsonPrimitive?.content }
        assertEquals(listOf("tree_dark_irregular", "tufts"), patterns)
        assertEquals(patterns.toSet(), STREET_NIGHT_PATTERN_PAINTS.keys)
        // The tree glyph's day pixel to its ruled night; the tufts painted #2485ad (RECORD -1566), which is also the
        // inversion of its day #3fa0c8, the owner's earlier "Keep #2485ad".
        assertEquals(0xFF5D804F.toInt(), nightSpritePixelsOf("tree_dark_irregular", intArrayOf(0xFF3E7A3A.toInt())).single())
        assertEquals(0xFF2485AD.toInt(), nightSpritePixelsOf("tufts", intArrayOf(0xFF3FA0C8.toInt())).single())
        assertEquals(nightColorOf(0xFF3FA0C8.toInt()), nightSpritePixelsOf("tufts", intArrayOf(0xFF3FA0C8.toInt())).single())
        // An off-colour tufts edge pixel is painted, not inverted: the 1x sheet has such pixels.
        assertEquals(0x802485AD.toInt(), nightSpritePixelsOf("tufts", intArrayOf(0x8000FFFF.toInt())).single())
        // A new icon inverts like every icon: the ring #4fa074 and fill #d6f2e3 of the approved night icons.
        assertEquals(listOf(0xFF61B286.toInt(), 0xFF0E2A1B.toInt()), nightSpritePixelsOf("trailhead_b", intArrayOf(0xFF4FA074.toInt(), 0xFFD6F2E3.toInt())).toList())
    }
}
