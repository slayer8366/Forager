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
 * The pattern relayout after a style reload (dispatches 2026-10-27-11 and -13, RECORD -1742 and -1748), driven as
 * MapStyleReload drives it: a style requested, its `setStyle` callback, and MapLibre's sprite-loaded hook, against a fake
 * of the served v3 style, a held clock and held frames. The restore's frame choice is the same [RestoreFrameGate] the
 * phone uses. It shows only the wiring and the timing: that the glyphs come back on a phone is the S22 colour count (a
 * real MapView does not run under Robolectric here, and the fault is in MapLibre's tiles).
 */
class PatternRelayoutTest {

    /**
     * The served Trail Map v3 style as the live style holds it: every layer's fill-pattern (null for none), as an ordered
     * map, its transition as Android reads it (300/0 by default: MapLibre's parser gives a style with no `transition` 300 ms
     * as a set value, `parser.hpp:40`, and PlacementTransitions.kt keeps it; the S22 logs 300), and every set the relayout
     * makes, in order.
     */
    private class FakeStyle(version: Int = 3, val transition: Long = 300L) : PatternLayerTarget<String> {
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

        override fun transitionMs(): Long = transition

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

    /** A held clock: the waits asked for, run when the test moves time on. */
    private class FakeClock : Deferral {
        var now = 0L
        val asked = mutableListOf<Long>()
        private val pending = mutableListOf<Pair<Long, () -> Unit>>()
        var cancelled = 0

        override fun after(ms: Long, run: () -> Unit): () -> Unit {
            asked += ms
            val entry = (now + ms) to run
            pending += entry
            return {
                cancelled++
                pending.remove(entry)
            }
        }

        /** Moves time to [t]; every wait due by then runs. */
        fun advanceTo(t: Long) {
            now = t
            val due = pending.filter { it.first <= t }
            pending.removeAll(due)
            due.forEach { it.second() }
        }

        /** Runs every wait asked for, cancelled or not, as a missed cancel would. */
        fun runEvenCancelled(runs: List<() -> Unit>) = runs.forEach { it() }

        fun pendingRuns() = pending.map { it.second }
    }

    /** The map's frames on [clock]: each frame is offered to the waiting restore through the phone's own [RestoreFrameGate]. */
    private class FakeFrames(private val clock: FakeClock) : RestoreTrigger {
        private val waiting = mutableListOf<Pair<RestoreFrameGate, (String) -> Unit>>()
        val gapsAsked = mutableListOf<Long>()
        var cancelled = 0

        override fun afterRendered(minGapMs: Long, restore: (how: String) -> Unit): () -> Unit {
            gapsAsked += minGapMs
            val entry = RestoreFrameGate(clock.now, minGapMs) to restore
            waiting += entry
            return {
                cancelled++
                waiting.remove(entry)
            }
        }

        /** A frame finishes at [t] (fully rendered or not). */
        fun frameAt(t: Long, fully: Boolean = true) {
            clock.now = t
            val due = waiting.filter { it.first.isRestoreFrame(fully, t) }
            waiting.removeAll(due)
            due.forEach { it.second("test frame at $t") }
        }

        fun isWaiting() = waiting.isNotEmpty()
    }

    private val log = mutableListOf<String>()
    private val clock = FakeClock()
    private val frames = FakeFrames(clock)

    private fun relayoutOver(styles: Map<String, FakeStyle>) =
        PatternRelayout<String, String>(targetFor = { styles.getValue(it) }, deferral = clock, trigger = frames, log = { log += it })

    private val cleared = listOf("landuse_forest_glyphs=\"\"", "landuse_wetland_tufts=\"\"")
    private val restored = listOf("landuse_forest_glyphs=\"tree_dark_irregular\"", "landuse_wetland_tufts=\"tufts\"")

    @Test
    fun `no clear before the deferral, nothing is set until the style's transition plus 100 ms after the callback and sprite`() {
        val style = FakeStyle()
        val relayout = relayoutOver(mapOf("night" to style))

        relayout.onStyleRequested(firstStyleOnThisMapView = false)
        relayout.onStyleLoaded("night")
        relayout.onSpriteLoaded()
        assertEquals("one wait, of the style's 300 ms transition plus 100", listOf(400L), clock.asked)
        clock.advanceTo(399)
        assertEquals("nothing cleared before the deferral ends", emptyList<String>(), style.sets)
        clock.advanceTo(400)
        assertEquals("cleared when it ends", cleared, style.sets)
    }

    @Test
    fun `a transition the style sets is waited out instead of the default`() {
        val style = FakeStyle(transition = 650)
        val relayout = relayoutOver(mapOf("s" to style))
        relayout.onStyleRequested(firstStyleOnThisMapView = false)
        relayout.onSpriteLoaded()
        relayout.onStyleLoaded("s")
        assertEquals(listOf(750L), clock.asked)
    }

    @Test
    fun `a style that sets a shorter transition than 300 ms still waits 300 plus 100`() {
        val style = FakeStyle(transition = 150)
        val relayout = relayoutOver(mapOf("s" to style))
        relayout.onStyleRequested(firstStyleOnThisMapView = false)
        relayout.onStyleLoaded("s")
        relayout.onSpriteLoaded()
        assertEquals("the floor: nothing was measured below the S22's 300 ms", listOf(400L), clock.asked)
        assertEquals("an explicit 0 is not read as unset, and still waits the floor", 400L, clearDelayMs(0))
        assertEquals(400L, clearDelayMs(300))
    }

    @Test
    fun `no restore before the gap, only the first full frame at least 100 ms after the clear restores`() {
        val style = FakeStyle()
        val relayout = relayoutOver(mapOf("s" to style))
        relayout.onStyleRequested(firstStyleOnThisMapView = false)
        relayout.onStyleLoaded("s")
        relayout.onSpriteLoaded()
        clock.advanceTo(400)
        assertEquals(listOf(100L), frames.gapsAsked)

        frames.frameAt(407) // 7 ms after the clear: the S22 gap that could fail
        frames.frameAt(460)
        frames.frameAt(505, fully = false)
        assertEquals("not restored before 100 ms, nor at a partial frame", cleared, style.sets)
        frames.frameAt(512)
        assertEquals(cleared + restored, style.sets)
        assertEquals("the style ends as it started", style.dayPatterns, style.patterns)
        assertEquals("one log line for the load", 1, log.size)
        assertTrue(log.single(), log.single().contains("clear deferred 400 ms (style transition 300 ms); cleared 2 of 2"))
        assertTrue(log.single(), log.single().contains("restored 2 [landuse_forest_glyphs=tree_dark_irregular, landuse_wetland_tufts=tufts] (test frame at 512)"))
        frames.frameAt(600)
        assertEquals("restored once", 4, style.sets.size)
    }

    @Test
    fun `a style change during the deferral drops the clear`() {
        val first = FakeStyle()
        val second = FakeStyle()
        val relayout = relayoutOver(mapOf("first" to first, "second" to second))

        relayout.onStyleRequested(firstStyleOnThisMapView = false)
        relayout.onStyleLoaded("first")
        relayout.onSpriteLoaded()
        val firstWait = clock.pendingRuns()
        clock.advanceTo(200)
        relayout.onStyleRequested(firstStyleOnThisMapView = false)
        assertEquals("the pending clear was cancelled", 1, clock.cancelled)
        clock.advanceTo(1_000)
        clock.runEvenCancelled(firstWait) // as a missed cancel would

        assertEquals("the first style was never cleared", emptyList<String>(), first.sets)
        assertEquals("the second style is untouched until its own sprite loads", emptyList<String>(), second.sets)
        assertTrue(log.toString(), log.single().contains("during the 400 ms wait before the clear (nothing cleared); dropped"))
    }

    @Test
    fun `a style requested between the clear and the restore restores the old style first`() {
        val first = FakeStyle()
        val second = FakeStyle()
        val relayout = relayoutOver(mapOf("first" to first, "second" to second))

        relayout.onStyleRequested(firstStyleOnThisMapView = false)
        relayout.onStyleLoaded("first")
        relayout.onSpriteLoaded()
        clock.advanceTo(400)
        assertEquals(cleared, first.sets)
        frames.frameAt(450) // 50 ms after the clear: not yet the restore frame
        relayout.onStyleRequested(firstStyleOnThisMapView = false)
        assertEquals(
            "restored in onStyleRequested, before setStyle, while the old style can still be written",
            cleared + restored,
            first.sets,
        )
        assertEquals("the old style ends as it started, should the new style fail to load", first.dayPatterns, first.patterns)
        assertEquals("the frame wait was cancelled", 1, frames.cancelled)
        frames.frameAt(600)
        clock.advanceTo(3_000)
        assertEquals("restored once", 4, first.sets.size)
        assertEquals(emptyList<String>(), second.sets)
        assertEquals(1, log.size)
        assertTrue(log.single(), log.single().contains("cleared 2 of 2 [landuse_forest_glyphs, landuse_wetland_tufts]; a new style was requested before the restore; restored 2 before the new style [landuse_forest_glyphs=tree_dark_irregular, landuse_wetland_tufts=tufts]."))
    }

    @Test
    fun `the MapView's first style is not relaid out and says so, while the next style is`() {
        val first = FakeStyle()
        val second = FakeStyle()
        val relayout = relayoutOver(mapOf("first" to first, "second" to second))

        relayout.onStyleRequested(firstStyleOnThisMapView = true)
        relayout.onStyleLoaded("first")
        relayout.onSpriteLoaded()
        clock.advanceTo(2_000)
        frames.frameAt(2_100)
        assertEquals("no wait asked for the first style", emptyList<Long>(), clock.asked)
        assertEquals("a first style's patterns are never cleared", emptyList<String>(), first.sets)
        assertEquals(
            listOf("Pattern relayout: the MapView's first style; no tiles kept from an earlier style, so no relayout."),
            log,
        )

        relayout.onStyleRequested(firstStyleOnThisMapView = false)
        relayout.onStyleLoaded("second")
        relayout.onSpriteLoaded()
        clock.advanceTo(2_500) // the wait runs from the last frame, at 2 100
        frames.frameAt(2_600)
        assertEquals("a reload is relaid out", cleared + restored, second.sets)
        assertEquals(2, log.size)
    }

    @Test
    fun `layers without a pattern are never touched`() {
        val style = FakeStyle()
        val relayout = relayoutOver(mapOf("s" to style))
        relayout.onStyleRequested(firstStyleOnThisMapView = false)
        relayout.onSpriteLoaded()
        relayout.onStyleLoaded("s")
        clock.advanceTo(400)
        frames.frameAt(500)
        val touched = style.sets.map { it.substringBefore('=') }.toSet()
        assertEquals(setOf("landuse_forest_glyphs", "landuse_wetland_tufts"), touched)
        assertTrue("v3 has other fill layers, which must stay untouched", style.dayPatterns.size > touched.size + 10)
    }

    @Test
    fun `the sprite loading before the style callback still relays out, once`() {
        val style = FakeStyle()
        val relayout = relayoutOver(mapOf("s" to style))
        relayout.onStyleRequested(firstStyleOnThisMapView = false)
        relayout.onSpriteLoaded()
        relayout.onStyleLoaded("s")
        clock.advanceTo(400)
        frames.frameAt(500)
        relayout.onSpriteLoaded()
        clock.advanceTo(2_000)
        frames.frameAt(2_200)
        assertEquals(listOf(400L), clock.asked)
        assertEquals(4, style.sets.size)
        assertEquals(style.dayPatterns, style.patterns)
    }

    @Test
    fun `a sprite that loaded for the previous style does not start the next style's relayout`() {
        val first = FakeStyle()
        val second = FakeStyle()
        val relayout = relayoutOver(mapOf("first" to first, "second" to second))
        relayout.onStyleRequested(firstStyleOnThisMapView = false)
        relayout.onSpriteLoaded()
        relayout.onStyleRequested(firstStyleOnThisMapView = false)
        relayout.onStyleLoaded("second")
        clock.advanceTo(1_000)
        assertEquals(emptyList<Long>(), clock.asked)
        relayout.onSpriteLoaded()
        clock.advanceTo(1_400)
        frames.frameAt(1_500)
        assertEquals(4, second.sets.size)
        assertEquals(emptyList<String>(), first.sets)
    }

    @Test
    fun `a style with no pattern layers sets nothing and says so`() {
        val style = FakeStyle().apply { patterns.replaceAll { _, _ -> null } }
        val relayout = relayoutOver(mapOf("s" to style))
        relayout.onStyleRequested(firstStyleOnThisMapView = false)
        relayout.onStyleLoaded("s")
        relayout.onSpriteLoaded()
        clock.advanceTo(400)
        assertEquals(emptyList<String>(), style.sets)
        assertEquals(false, frames.isWaiting())
        assertEquals(
            listOf("Pattern relayout: clear deferred 400 ms (style transition 300 ms); the style has no pattern layers; nothing to relay out."),
            log,
        )
    }
}
