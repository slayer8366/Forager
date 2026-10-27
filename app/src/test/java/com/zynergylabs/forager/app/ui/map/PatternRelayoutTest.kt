package com.zynergylabs.forager.app.ui.map

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pattern relayout after a style reload (dispatch 2026-10-27-11, RECORD -1742), driven as MapStyleReload drives it:
 * a style requested, its `setStyle` callback, and MapLibre's sprite-loaded hook, against a fake of the served v3 style.
 * It shows only the wiring: that the glyphs come back on a phone is the S22 colour count (a real MapView does not run
 * under Robolectric here, and the fault is in MapLibre's tiles).
 */
class PatternRelayoutTest {

    /**
     * The served Trail Map v3 style as the live style holds it: every layer's fill-pattern (null for none), as an ordered
     * map, and every set the relayout makes, in order.
     */
    private class FakeStyle(version: Int = 3) : PatternLayerTarget<String> {
        val patterns: LinkedHashMap<String, String?> = LinkedHashMap<String, String?>().apply {
            val style = Json.parseToJsonElement(File("../server/pmtiles-worker/src/street-style-v$version.json").readText()).jsonObject
            for (layer in style.getValue("layers").jsonArray) {
                val o = layer.jsonObject
                val pattern = if (o["type"]?.jsonPrimitive?.content == "fill") (o["paint"]?.jsonObject?.get("fill-pattern") as? JsonPrimitive)?.content else null
                put(o.getValue("id").jsonPrimitive.content, pattern)
            }
        }
        val dayPatterns: Map<String, String?> = LinkedHashMap(patterns)
        val sets = mutableListOf<String>()

        override fun patternLayers(): List<PatternLayer<String>> =
            patterns.mapNotNull { (id, p) -> p?.takeIf { it.isNotEmpty() }?.let { PatternLayer(id, it, it) } }

        override fun clearPattern(layerId: String): Boolean = set(layerId, "")

        override fun restorePattern(layerId: String, value: String): Boolean = set(layerId, value)

        private fun set(layerId: String, value: String): Boolean {
            if (layerId !in patterns) return false
            patterns[layerId] = value
            sets += "$layerId=\"$value\""
            return true
        }
    }

    /** The map's next render, held until the test lets it happen. */
    private class FakeRender : RestoreTrigger {
        val pending = mutableListOf<(String) -> Unit>()
        var cancelled = 0

        override fun afterRendered(restore: (how: String) -> Unit): () -> Unit {
            pending += restore
            return { cancelled++ }
        }

        /** Renders: every waiting restore runs, as a stale one would if the cancel were missed. */
        fun render() {
            val due = pending.toList()
            pending.clear()
            due.forEach { it("test render") }
        }
    }

    private val log = mutableListOf<String>()

    private fun relayoutOver(styles: Map<String, FakeStyle>, render: FakeRender) =
        PatternRelayout<String, String>(targetFor = { styles.getValue(it) }, trigger = render, log = { log += it })

    @Test
    fun `after a reload each pattern layer is set to empty and then back to its own value, once the sprite has loaded`() {
        val style = FakeStyle()
        val render = FakeRender()
        val relayout = relayoutOver(mapOf("night" to style), render)

        relayout.onStyleRequested()
        relayout.onStyleLoaded("night")
        assertEquals("nothing before the sprite has loaded", emptyList<String>(), style.sets)
        relayout.onSpriteLoaded()
        assertEquals(listOf("landuse_forest_glyphs=\"\"", "landuse_wetland_tufts=\"\""), style.sets)
        render.render()

        assertEquals(
            listOf("landuse_forest_glyphs=\"\"", "landuse_wetland_tufts=\"\"", "landuse_forest_glyphs=\"tree_dark_irregular\"", "landuse_wetland_tufts=\"tufts\""),
            style.sets,
        )
        assertEquals("the style ends as it started", style.dayPatterns, style.patterns)
        assertEquals(2, log.size)
        assertTrue(log[1], log[1].startsWith("Pattern relayout: restored 2 of 2 pattern layers"))
    }

    @Test
    fun `layers without a pattern are never touched`() {
        val style = FakeStyle()
        val relayout = relayoutOver(mapOf("s" to style), FakeRender())
        relayout.onStyleRequested()
        relayout.onSpriteLoaded()
        relayout.onStyleLoaded("s")
        val touched = style.sets.map { it.substringBefore('=') }.toSet()
        assertEquals(setOf("landuse_forest_glyphs", "landuse_wetland_tufts"), touched)
        assertTrue("v3 has other fill layers, which must stay untouched", style.dayPatterns.size > touched.size + 10)
    }

    @Test
    fun `the sprite loading before the style callback still relays out, once`() {
        val style = FakeStyle()
        val render = FakeRender()
        val relayout = relayoutOver(mapOf("s" to style), render)
        relayout.onStyleRequested()
        relayout.onSpriteLoaded()
        relayout.onStyleLoaded("s")
        render.render()
        relayout.onSpriteLoaded()
        render.render()
        assertEquals(4, style.sets.size)
        assertEquals(style.dayPatterns, style.patterns)
    }

    @Test
    fun `nothing is restored when the style changed before the restore`() {
        val first = FakeStyle()
        val second = FakeStyle()
        val render = FakeRender()
        val relayout = relayoutOver(mapOf("first" to first, "second" to second), render)

        relayout.onStyleRequested()
        relayout.onStyleLoaded("first")
        relayout.onSpriteLoaded()
        relayout.onStyleRequested()
        render.render()

        assertEquals("the first style was cleared and never restored", listOf("landuse_forest_glyphs=\"\"", "landuse_wetland_tufts=\"\""), first.sets)
        assertEquals(1, render.cancelled)
        assertEquals("the second style is untouched until its own sprite loads", emptyList<String>(), second.sets)
        assertTrue(log.toString(), log.any { it.contains("nothing restored") })
    }

    @Test
    fun `a sprite that loaded for the previous style does not start the next style's relayout`() {
        val first = FakeStyle()
        val second = FakeStyle()
        val render = FakeRender()
        val relayout = relayoutOver(mapOf("first" to first, "second" to second), render)
        relayout.onStyleRequested()
        relayout.onSpriteLoaded()
        relayout.onStyleRequested()
        relayout.onStyleLoaded("second")
        assertEquals(emptyList<String>(), second.sets)
        relayout.onSpriteLoaded()
        render.render()
        assertEquals(4, second.sets.size)
        assertEquals(emptyList<String>(), first.sets)
    }

    @Test
    fun `a style with no pattern layers sets nothing and says so`() {
        val style = FakeStyle().apply { patterns.replaceAll { _, _ -> null } }
        val render = FakeRender()
        val relayout = relayoutOver(mapOf("s" to style), render)
        relayout.onStyleRequested()
        relayout.onStyleLoaded("s")
        relayout.onSpriteLoaded()
        assertEquals(emptyList<String>(), style.sets)
        assertEquals(emptyList<(String) -> Unit>(), render.pending)
        assertEquals(listOf("Pattern relayout: the style has no pattern layers; nothing to relay out."), log)
    }
}
