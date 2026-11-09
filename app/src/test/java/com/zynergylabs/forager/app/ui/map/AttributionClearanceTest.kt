package com.zynergylabs.forager.app.ui.map

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item 1 of dispatch 2026-09-29-57 (amendment -262, "Move the 'i'"): the margin computation as a pure function. Numbers are
 * the S22 landscape window in px at 450 dpi as the run record gives them (2316 x 1080, the rail 225 px, the L 96 dp wide), with
 * MapLibre's own margins as measured (right 146, bottom 11) and a 59 px button.
 */
class AttributionClearanceTest {

    private val width = 2316
    private val height = 1080
    private val defaults = intArrayOf(11, 11, 146, 11)
    private val button = 59
    private val gap = 22

    /** The button's rectangle at the given insets, as MapLibre lays it out (bottom-end gravity). */
    private fun buttonAt(endInset: Int, bottomInset: Int, rtl: Boolean = false): Rect {
        val bottom = (height - defaults[3] - bottomInset).toFloat()
        return if (rtl) {
            val left = (defaults[0] + endInset).toFloat()
            Rect(left, bottom - button, left + button, bottom)
        } else {
            val right = (width - defaults[2] - endInset).toFloat()
            Rect(right - button, bottom - button, right, bottom)
        }
    }

    private fun clearInset(keepClear: Rect?, endInset: Int, bottomInset: Int = 0, rtl: Boolean = false) =
        attributionEndInsetClearOf(keepClear, width, height, defaults, bottomInset, endInset, rtl, button, gap)

    /** The L snapped right at 90, its record button at the bottom: outer edge 225 + 22 in from the right, 270 px wide, to the bottom of the map. */
    private val lOnTheRight = Rect(width - 247f - 270f, 800f, width - 247f, height.toFloat())

    @Test
    fun `at 90 with the L on the right at its lower limit the button, as the app leaves it, sits under the L's record button`() {
        // The premise of the fix, so a green result below is against a real overlap: at the rail's own inset the button is inside the L.
        assertTrue("the button ${buttonAt(225, 0)} intersects the L $lOnTheRight at the rail's inset", buttonAt(225, 0).overlaps(lOnTheRight))
    }

    @Test
    fun `the inset that comes back puts the button clear of the L, a gap inboard of its inner edge`() {
        val inset = clearInset(lOnTheRight, endInset = 225)
        val moved = buttonAt(inset, 0)
        assertFalse("the button $moved does not intersect the L $lOnTheRight at inset $inset", moved.overlaps(lOnTheRight))
        assertEquals("the button's right edge is the gap inboard of the L's left edge", lOnTheRight.left - gap, moved.right, 1f)
    }

    @Test
    fun `an L on the far side leaves the inset as it was`() {
        val lOnTheLeft = Rect(24f, 800f, 24f + 270f, height.toFloat())
        assertEquals(225, clearInset(lOnTheLeft, endInset = 225))
    }

    @Test
    fun `an L above the button leaves the inset as it was`() {
        val lHigh = Rect(width - 247f - 270f, 100f, width - 247f, 400f)
        assertEquals(225, clearInset(lHigh, endInset = 225))
    }

    @Test
    fun `no L, or a map with no size yet, leaves the inset as it was`() {
        assertEquals(225, clearInset(null, endInset = 225))
        assertEquals(225, attributionEndInsetClearOf(lOnTheRight, 0, 0, defaults, 0, 225, false, button, gap))
    }

    @Test
    fun `a button already inboard of the L keeps a larger inset it was handed`() {
        val handed = 900
        assertEquals(handed, clearInset(lOnTheRight, endInset = handed))
    }

    @Test
    fun `in a right-to-left layout the end edge is the left and the button clears an L snapped there`() {
        val lOnTheLeft = Rect(247f, 800f, 247f + 270f, height.toFloat())
        val inset = clearInset(lOnTheLeft, endInset = 225, rtl = true)
        val moved = buttonAt(inset, 0, rtl = true)
        assertFalse("the button $moved does not intersect the L $lOnTheLeft at inset $inset", moved.overlaps(lOnTheLeft))
        assertEquals(lOnTheLeft.right + gap, moved.left, 1f)
    }
}
