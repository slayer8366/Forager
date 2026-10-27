package com.zynergylabs.forager.app.ui.map

import java.io.File
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
 * The pattern fade turned off after a style load (dispatch 2026-10-27-13, RECORD -1748), against a fake of the served
 * Trail Map v3 style. It shows only which layers get `fill-pattern-transition` 0/0: that the glyphs then survive a reload
 * is the S22 colour count (a real MapView does not run under Robolectric here, and the fault is in MapLibre's tiles).
 */
class PatternNoFadeTest {

    /** The served style's layers: every layer's type and fill-pattern, and each transition set, in order. */
    private class FakeStyle(layers: List<JsonObject>) : PatternFadeTarget {
        constructor(version: Int = 3) : this(
            Json.parseToJsonElement(File("../server/pmtiles-worker/src/street-style-v$version.json").readText()).jsonObject
                .getValue("layers").jsonArray.map { it.jsonObject },
        )

        val types: Map<String, String> = layers.associate { it.getValue("id").jsonPrimitive.content to it.getValue("type").jsonPrimitive.content }
        val patterns: Map<String, String?> = layers.associate { o ->
            o.getValue("id").jsonPrimitive.content to (o["paint"]?.jsonObject?.get("fill-pattern")?.let { (it as? JsonPrimitive)?.content ?: it.toString() })
        }
        val transitionsSet = mutableListOf<String>()

        override fun fillLayers(): List<FillLayerPattern> =
            types.filterValues { it == "fill" }.keys.map { FillLayerPattern(it, patterns[it]) }

        override fun setPatternTransitionOff(layerId: String): Boolean {
            if (types[layerId] != "fill") return false
            transitionsSet += layerId
            return true
        }
    }

    private val log = mutableListOf<String>()

    @Test
    fun `every fill layer with a pattern in the served style gets the zero transition, and no other layer does`() {
        val style = FakeStyle()
        val expected = style.patterns.filterValues { !it.isNullOrEmpty() }.keys.toList()
        // The served v3 style's own two pattern layers, read from the file, so a third one added later is covered too.
        assertTrue("the fixture should hold the forest glyphs and the tufts, held $expected",
            expected.containsAll(listOf("landuse_forest_glyphs", "landuse_wetland_tufts")))

        turnOffPatternFade(style) { log += it }

        assertEquals("the transition is set on exactly the pattern layers, in layer order", expected, style.transitionsSet)
        assertEquals("one log line per style load", 1, log.size)
        assertTrue(log.single(), log.single().contains("on ${expected.size} of ${expected.size} pattern layers"))
    }

    @Test
    fun `a fill layer with an empty or unset pattern, and a pattern given as an expression, are told apart`() {
        fun layer(id: String, type: String, pattern: String?): JsonObject = Json.parseToJsonElement(
            """{"id":"$id","type":"$type","paint":{${if (pattern != null) "\"fill-pattern\":$pattern" else ""}}}""",
        ).jsonObject
        val style = FakeStyle(
            listOf(
                layer("plain", "fill", null),
                layer("emptied", "fill", "\"\""),
                layer("named", "fill", "\"tufts\""),
                layer("expr", "fill", """["step",["zoom"],"a",14,"b"]"""),
                layer("line", "line", null),
            ),
        )

        turnOffPatternFade(style) { log += it }

        assertEquals(listOf("named", "expr"), style.transitionsSet)
    }

    @Test
    fun `a style with no pattern layer changes nothing and says so`() {
        val style = FakeStyle(listOf(Json.parseToJsonElement("""{"id":"water","type":"fill","paint":{}}""").jsonObject))

        turnOffPatternFade(style) { log += it }

        assertEquals(emptyList<String>(), style.transitionsSet)
        assertEquals(listOf("Pattern fade: the style has no pattern layers; nothing to change."), log)
    }

    @Test
    fun `a layer that cannot be set is logged, and the others are still set`() {
        val style = object : PatternFadeTarget {
            val set = mutableListOf<String>()
            override fun fillLayers() = listOf(FillLayerPattern("gone", "tufts"), FillLayerPattern("kept", "tree_dark_irregular"))
            override fun setPatternTransitionOff(layerId: String): Boolean {
                if (layerId == "gone") return false
                set += layerId
                return true
            }
        }

        turnOffPatternFade(style) { log += it }

        assertEquals(listOf("kept"), style.set)
        assertTrue(log.single(), log.single().contains("on 1 of 2 pattern layers [kept]; could not set gone (no such fill layer)"))
    }
}
