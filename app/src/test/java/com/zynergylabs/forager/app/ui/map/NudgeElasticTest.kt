package com.zynergylabs.forager.app.ui.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-09-28-463: the pure halves of the elastic nudge. How far the map gives for a finger's
 * travel, when it springs back, the padding that shows a give, and the spring's curve. What MapLibre
 * draws is device-only.
 */
class NudgeElasticTest {

    private val threshold = 96f // 48 dp at density 2

    @Test
    fun `a one-finger nudge gives half the finger's travel, on both axes`() {
        val give = NudgeGive(threshold)
        give.down(100f, 200f, NudgeOffset.ZERO)
        assertEquals(NudgeOffset(10f, -15f), give.move(120f, 170f, pointerCount = 1, canGive = true))
        assertEquals(NudgeOffset(-20f, 5f), give.move(60f, 210f, pointerCount = 1, canGive = true))
    }

    @Test
    fun `the give stops at half the threshold, however far the finger goes`() {
        val give = NudgeGive(threshold)
        give.down(0f, 0f, NudgeOffset.ZERO)
        assertEquals(NudgeOffset(48f, 0f), give.move(200f, 0f, pointerCount = 1, canGive = true))
        val diagonal = give.move(300f, 400f, pointerCount = 1, canGive = true)!!
        assertEquals("along the finger's direction", 28.8f, diagonal.dxPx, 0.01f)
        assertEquals(38.4f, diagonal.dyPx, 0.01f)
    }

    @Test
    fun `lifted while following, it springs back from the give`() {
        val give = NudgeGive(threshold)
        give.down(0f, 0f, NudgeOffset.ZERO)
        give.move(30f, 0f, pointerCount = 1, canGive = true)
        assertEquals(NudgeOffset(15f, 0f), give.release(stillFollowing = true))
        assertNull("the gesture is over", give.move(40f, 0f, pointerCount = 1, canGive = true))
    }

    @Test
    fun `a drag that left following does not spring, and its padding stays until the view is applied again`() {
        val give = NudgeGive(threshold)
        give.down(0f, 0f, NudgeOffset.ZERO)
        give.move(90f, 0f, pointerCount = 1, canGive = true)
        assertNull("following ended past the threshold", give.move(150f, 0f, pointerCount = 1, canGive = false))
        assertNull(give.release(stillFollowing = false))
    }

    @Test
    fun `a second finger gives nothing, and springs back what one finger gave`() {
        val give = NudgeGive(threshold)
        give.down(0f, 0f, NudgeOffset.ZERO)
        give.move(20f, 0f, pointerCount = 1, canGive = true)
        assertNull("a pinch starting", give.move(25f, 0f, pointerCount = 2, canGive = true))
        assertEquals(NudgeOffset(10f, 0f), give.release(stillFollowing = true))
    }

    @Test
    fun `nothing is given with no finger down, or while the view cannot take padding`() {
        val give = NudgeGive(threshold)
        assertNull(give.move(20f, 0f, pointerCount = 1, canGive = true))
        give.down(0f, 0f, NudgeOffset.ZERO)
        assertNull("a mode transition running", give.move(20f, 0f, pointerCount = 1, canGive = false))
        assertNull("no give, nothing to spring", give.release(stillFollowing = true))
    }

    @Test
    fun `a spring cut short by a new touch, so the new give starts from where the map is`() {
        val give = NudgeGive(threshold)
        give.down(0f, 0f, NudgeOffset(6f, -2f))
        assertEquals(NudgeOffset(16f, -2f), give.move(20f, 0f, pointerCount = 1, canGive = true))
    }

    @Test
    fun `a give right and down widens the left and top padding by twice the give, so the walker moves by the give`() {
        assertArrayEquals(doubleArrayOf(20.0, 230.0, 0.0, 0.0), nudgePadding(200.0, NudgeOffset(10f, 15f)), 1e-9)
        assertArrayEquals(doubleArrayOf(0.0, 200.0, 20.0, 30.0), nudgePadding(200.0, NudgeOffset(-10f, -15f)), 1e-9)
        assertArrayEquals("no give is the view's own padding", doubleArrayOf(0.0, 200.0, 0.0, 0.0), nudgePadding(200.0, NudgeOffset.ZERO), 1e-9)
    }

    @Test
    fun `the spring starts at the give, passes rest by about 13 percent, and ends exactly at rest`() {
        val from = NudgeOffset(20f, -10f)
        assertEquals(from, nudgeSpringOffset(from, 0f))
        val samples = (0..100).map { nudgeSpringOffset(from, it / 100f) }
        val furthestPast = samples.minOf { it.dxPx }
        assertEquals("past rest, the other way", -0.132f * 20f, furthestPast, 0.05f)
        assertTrue("on the same line", samples.all { kotlin.math.abs(it.dyPx * 2 + it.dxPx) < 1e-3f })
        assertEquals(NudgeOffset.ZERO, nudgeSpringOffset(from, 1f))
    }

    @Test
    fun `a mode transition of ours is known while it runs, and not after it ends, is cancelled, or navigation stops`() {
        val gate = StartZoomGate()
        assertFalse(gate.transitioning)
        val first = gate.request(sameModeAsInFlight = false, startZoom = 18.0)!!
        assertTrue(gate.transitioning)
        gate.finished(first)
        assertFalse(gate.transitioning)
        val second = gate.request(sameModeAsInFlight = false, startZoom = null)!!
        gate.cancelled(second)
        assertFalse(gate.transitioning)
        gate.request(sameModeAsInFlight = false, startZoom = null)
        gate.reset()
        assertFalse(gate.transitioning)
    }
}
