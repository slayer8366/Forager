package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-11-06-01, Amendment 1, J3 step 3 and J4 (RECORD -2183, the owner: "Own line, card moves up", "Follow the largest rule"):
 * where the landscape navigation card sits ([navigationCardLift]). Figures in px at 1 px per dp, from the 823 x 384 probe (run
 * 42402017914): the card's bottom on the edge at 384, the dot's centre at 252 (its top 240, its bottom 264), the strip's bottom 37.3,
 * the card with its own line and evening line 153.3 tall.
 */
class NavigationCardLiftTest {

    private fun lift(
        liftCase: Boolean = true,
        height: Float = 153.3f,
        topLimit: Float? = 264f,
        dotTop: Float? = 240f,
        stripBottom: Float? = 37.3f,
        lTop: Float? = null,
        lBottom: Float? = null,
    ) = navigationCardLift(liftCase, height, bottomEdgePx = 384f, topLimitPx = topLimit, dotTopPx = dotTop, stripBottomPx = stripBottom, lTopPx = lTop, lBottomPx = lBottom)

    @Test
    fun `J3 step 3 - a card whose evening line would take its top over the dot moves up until its bottom is at the dot's top`() {
        assertEquals(NavigationCardLift.Up(144f), lift())
    }

    @Test
    fun `just enough - a card that already clears the dot from the bottom edge stays there`() {
        // 823 x 384 at font 1.5 with its evening line: 118.7 tall, its top 13.3 below the dot's bottom.
        assertEquals(NavigationCardLift.OnBottomEdge, lift(height = 118.7f))
    }

    @Test
    fun `outside the case - no evening line, not landscape at 1,5 or more, or stepping aside for the route card - it stays on the bottom edge`() {
        assertEquals(NavigationCardLift.OnBottomEdge, lift(liftCase = false))
        assertEquals("no evening line: no height with one", NavigationCardLift.OnBottomEdge, lift(height = Float.NaN))
        assertEquals("before the map is measured", NavigationCardLift.OnBottomEdge, lift(dotTop = null, topLimit = null))
    }

    @Test
    fun `no room under the strip - it stays on the bottom edge and says why`() {
        val r = lift(height = 220f)
        assertTrue("$r", r is NavigationCardLift.NoRoom && r.reason.contains("strip"))
    }

    @Test
    fun `the L in its columns where the moved card would be - it stays on the bottom edge and says why`() {
        val r = lift(lTop = 150f, lBottom = 330f)
        assertTrue("$r", r is NavigationCardLift.NoRoom && r.reason.contains("L"))
        assertEquals("an L wholly below the dot's top is not covered", NavigationCardLift.Up(144f), lift(lTop = 241f, lBottom = 384f))
    }

    // J5, the defect (RECORD -2183): the card moved up was drawn from -49 to 213 dp at 640 x 360, font 2.0, after sunset, with the
    // S22's insets (probe run 42421420016): the decision took 420 px for the card with its evening line, and it was drawn 786 px
    // tall, because its two evening pieces wrapped in a 72 dp right part and the decision counted each as one line.
    @Test
    fun `J5 - a wrapped evening piece counts every line it takes`() {
        val heights = mapOf("Sun set 12:50 AM" to 288, "dark in 24 min" to 192)
        assertEquals(480, eveningStackHeightPx(heights.keys.toList(), lineHeightPx = 96) { heights.getValue(it) })
        assertEquals("unwrapped, one line each", 192, eveningStackHeightPx(listOf("a", "b"), lineHeightPx = 96) { 96 })
        assertEquals("none", 0, eveningStackHeightPx(emptyList(), lineHeightPx = 96) { 96 })
    }
}
