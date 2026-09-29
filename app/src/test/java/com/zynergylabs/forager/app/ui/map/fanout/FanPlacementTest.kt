package com.zynergylabs.forager.app.ui.map.fanout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Where a fan's centre goes so the ring or spiral stays on screen and off the controls (dispatch
 * 2026-09-28-197, continuation `-208`; the owner's "2 A"). Pure arithmetic, in dp: every check is on
 * the touch squares the fan would occupy, not on hand-copied shift values. The true positions are
 * never moved; only the fan's centre is.
 */
class FanPlacementTest {

    private val half = FAN_TOUCH_DP / 2

    private fun squares(cx: Float, cy: Float, ring: List<FanOffset>, shift: FanOffset): List<FanRect> =
        ring.map { FanRect(cx + it.xDp + shift.xDp - half, cy + it.yDp + shift.yDp - half, cx + it.xDp + shift.xDp + half, cy + it.yDp + shift.yDp + half) }

    private fun inside(r: FanRect, b: FanRect) = r.left >= b.left - EPS && r.top >= b.top - EPS && r.right <= b.right + EPS && r.bottom <= b.bottom + EPS

    private fun overlaps(a: FanRect, b: FanRect) = a.left < b.right - EPS && b.left < a.right - EPS && a.top < b.bottom - EPS && b.top < a.bottom - EPS

    private fun place(cx: Float, cy: Float, n: Int, bounds: FanRect, keepOuts: List<FanRect> = emptyList()): Triple<FanShift, List<FanRect>, List<FanOffset>> {
        val ring = fanOffsets(n)
        val result = fanShift(cx, cy, ring, bounds, keepOuts)
        return Triple(result, squares(cx, cy, ring, result.shift), ring)
    }

    private val phone = FanRect(0f, 0f, 411f, 891f)

    @Test
    fun `a fan with room needs no shift`() {
        val (result, squares, _) = place(200f, 400f, 5, phone)
        assertEquals(FanOffset(0f, 0f), result.shift)
        assertTrue(result.allOnScreen)
        assertTrue(squares.all { inside(it, phone) })
    }

    @Test
    fun `at every edge and corner the whole fan lands inside the bounds, moved no further than it must`() {
        val spots = listOf(
            "left" to (10f to 400f), "right" to (401f to 400f), "top" to (200f to 10f), "bottom" to (200f to 881f),
            "top-left" to (5f to 5f), "top-right" to (406f to 5f), "bottom-left" to (5f to 886f), "bottom-right" to (406f to 886f),
        )
        for (n in listOf(2, 3, 8, 12)) {
            for ((name, at) in spots) {
                val (result, squares, ring) = place(at.first, at.second, n, phone)
                assertTrue("$name n=$n: every touch square is inside", squares.all { inside(it, phone) })
                assertTrue("$name n=$n: reported on screen", result.allOnScreen)
                // Minimal: shifting one dp back towards the start would put a square outside on that axis.
                val unshifted = squares(at.first, at.second, ring, FanOffset(0f, 0f))
                val needX = maxOf(0f, phone.left - unshifted.minOf { it.left }, unshifted.maxOf { it.right } - phone.right)
                val needY = maxOf(0f, phone.top - unshifted.minOf { it.top }, unshifted.maxOf { it.bottom } - phone.bottom)
                assertEquals("$name n=$n: x shift is the overshoot", needX, kotlin.math.abs(result.shift.xDp), 0.01f)
                assertEquals("$name n=$n: y shift is the overshoot", needY, kotlin.math.abs(result.shift.yDp), 0.01f)
            }
        }
    }

    @Test
    fun `next to a control, on each of its sides, no touch square lands on it`() {
        val control = FanRect(150f, 350f, 250f, 450f)
        val spots = listOf("left" to (140f to 400f), "right" to (260f to 400f), "above" to (200f to 340f), "below" to (200f to 460f))
        for (n in listOf(2, 4, 8)) {
            for ((name, at) in spots) {
                val (result, squares, _) = place(at.first, at.second, n, phone, listOf(control))
                assertTrue("$name n=$n: clear of the control", squares.none { overlaps(it, control) })
                assertTrue("$name n=$n: still on screen", squares.all { inside(it, phone) })
                assertEquals("$name n=$n: the one control cleared", 1, result.controlsCleared)
            }
        }
    }

    @Test
    fun `on a phone in portrait the cluster, the legend and the chip row are all cleared at once`() {
        val cluster = FanRect(340f, 200f, 411f, 520f)
        val legend = FanRect(250f, 800f, 411f, 870f)
        val chips = FanRect(60f, 80f, 350f, 130f)
        val controls = listOf(cluster, legend, chips)
        val spots = listOf("by the cluster" to (330f to 300f), "by the legend" to (240f to 830f), "under the chips" to (200f to 140f), "top-right corner" to (400f to 20f), "bottom-right corner" to (400f to 880f))
        for ((name, at) in spots) {
            for (n in listOf(3, 8, 10)) {
                val (result, squares, _) = place(at.first, at.second, n, phone, controls)
                assertTrue("$name n=$n: on screen", squares.all { inside(it, phone) })
                assertTrue("$name n=$n: clear of all three (${result.controlsCleared} of ${result.controlsTotal})", squares.none { s -> controls.any { overlaps(s, it) } })
                assertEquals(3, result.controlsCleared)
            }
        }
    }

    @Test
    fun `the shifted fan keeps its shape - only the centre moves`() {
        val (result, squares, ring) = place(5f, 5f, 8, phone)
        val moved = squares.map { (it.left + it.right) / 2 to (it.top + it.bottom) / 2 }
        ring.forEachIndexed { i, off ->
            assertEquals(5f + off.xDp + result.shift.xDp, moved[i].first, 0.01f)
            assertEquals(5f + off.yDp + result.shift.yDp, moved[i].second, 0.01f)
        }
    }

    @Test
    fun `a fan bigger than the window is centred on it and reported as not on screen`() {
        val tiny = FanRect(0f, 0f, 200f, 200f)
        val (result, squares, _) = place(20f, 20f, 20, tiny)
        assertFalse("20 markers cannot fit in 200 dp", result.allOnScreen)
        val cx = (squares.minOf { it.left } + squares.maxOf { it.right }) / 2
        val cy = (squares.minOf { it.top } + squares.maxOf { it.bottom }) / 2
        assertEquals(100f, cx, 0.5f)
        assertEquals(100f, cy, 0.5f)
    }

    @Test
    fun `a control that cannot be cleared is left, on screen first`() {
        val square = FanRect(0f, 0f, 300f, 300f)
        val band = FanRect(0f, 100f, 300f, 200f) // full width: a ring of eight (about 173 dp) fits above or below neither
        val (result, squares, _) = place(150f, 150f, 8, square, listOf(band))
        assertTrue("on screen", squares.all { inside(it, square) })
        assertTrue(result.allOnScreen)
        assertEquals(0, result.controlsCleared)
        assertEquals(1, result.controlsTotal)
    }

    @Test
    fun `when only some controls can be cleared, as many as possible are`() {
        val wide = FanRect(0f, 0f, 600f, 300f)
        val band = FanRect(0f, 100f, 600f, 200f) // cannot be cleared by an eight-marker ring
        val corner = FanRect(500f, 0f, 600f, 50f) // can
        val (result, squares, _) = place(540f, 60f, 8, wide, listOf(band, corner))
        assertTrue(squares.all { inside(it, wide) })
        assertEquals("the corner control is cleared though the band cannot be", 1, result.controlsCleared)
        assertTrue("clear of the corner control", squares.none { overlaps(it, corner) })
    }

    @Test
    fun `no bounds means no shift for the edges, but controls are still cleared`() {
        val ring = fanOffsets(4)
        val control = FanRect(190f, 390f, 260f, 460f)
        val result = fanShift(200f, 400f, ring, null, listOf(control))
        val squares = squares(200f, 400f, ring, result.shift)
        assertTrue(squares.none { overlaps(it, control) })
        assertTrue(hypot(result.shift.xDp, result.shift.yDp) > 0f)
    }

    private companion object {
        const val EPS = 0.01f
    }
}
