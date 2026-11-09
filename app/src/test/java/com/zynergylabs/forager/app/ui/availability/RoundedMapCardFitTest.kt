package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-10-12-08 (RECORD -945, -950): the navigation info's two parts give way in what is left of the owner's order
 * (RECORD -934, -936; -950: "the remaining fit steps keep their order"). The evening stack's widest piece is room the right part
 * keeps.
 *
 * Dispatch 2026-11-05-10, Amendment 1 (RECORD -2181) took two of the steps away, and these tests' claims changed with them:
 * - G2 ("Drop the words, keep the degrees"): the turn line is the degrees alone, so "the turn words shrink to the degrees and the
 *   needle" has nothing to shrink. Was, for example: `assertEquals("169°", f.turnText)` at 135 px, where 136 drew "sharp right 169°".
 * - H1 (the north arrow and the heading are the strip's): the crossed-out compass in their place is gone. Was: `assertTrue("the
 *   icon", f.headingAsIcon)` for a compass status in words.
 * - G3 ("The turn line"): the turn line is the degrees' fixed space, a gap and the needle, the same in every state.
 * Left: "within 16 ft" takes "≤ 16 ft" where the figure is what is too wide; past that the fit reports an overflow ("Invent no new
 * trims"). And since G3 the left part is never narrower than the widest figure the walk could show, and since H2 and H5 the right
 * part never narrower than its floor, the evening line's widest on the phone's clock.
 *
 * Pinned on round numbers so each threshold is exact: divider 10, gap 2, needle 10, the degrees' space 28: the turn line is 40. The
 * left part is the widest of the figure (40), the longest figure, the turn line and the kind (30); the total adds the divider and
 * the right part.
 */
class RoundedMapCardFitTest {

    private fun widths(
        available: Int,
        evening: Int? = null,
        figurePx: Int = 40,
        figureShortPx: Int? = null,
        longest: Int = 0,
        floor: Int = 0,
    ) = NavigationInfoWidths(
        availablePx = available,
        dividerPx = 10,
        gapPx = 2,
        arrowPx = 10,
        figurePx = figurePx,
        figureShortPx = figureShortPx,
        longestFigurePx = longest,
        kindPx = 30,
        turnSpacePx = 28,
        eveningPiecePx = evening,
        rightFloorPx = floor,
    )

    private fun fit(w: NavigationInfoWidths) = navigationInfoFit(w, turnText = "169°")

    @Test
    fun `G2 and G3, the turn line is the degrees' space, a gap and the needle, and the degrees are drawn as they are`() {
        val f = fit(widths(60))
        assertEquals("169°", f.turnText)
        assertEquals("28 + 2 + 10", 40, turnLinePx(widths(60)))
        assertEquals("the figure, 40, and the turn line, 40", 40, f.leftPx)
        assertEquals(28, f.turnSpacePx)
        assertFalse(f.overflows)
        assertEquals("no evening piece and no floor, no right part", 0, f.rightPx)
        assertEquals("the degrees' space is the widest of 0° to 180°", 33, widestTurnDegreesPx { if (it == "108°") 33 else it.length * 5 })
    }

    @Test
    fun `then within 16 ft takes its short form, only where the figure is what is too wide`() {
        val roomForFigure = fit(widths(110, figurePx = 100, figureShortPx = 30))
        assertFalse("the whole figure fits", roomForFigure.figureShort)
        val f = fit(widths(109, figurePx = 100, figureShortPx = 30))
        assertTrue("then the figure's short form", f.figureShort)
        assertFalse(f.overflows)
        assertEquals("the turn line, 40, the widest", 40, f.leftPx)
    }

    @Test
    fun `where even the last step is not enough the fit says so and trims nothing more`() {
        val f = fit(widths(45, figurePx = 100, figureShortPx = 30))
        assertTrue(f.overflows)
        assertTrue("the figure short", f.figureShort)
        assertEquals("what the left part needs is reported", 40, f.leftPx)
    }

    @Test
    fun `G3, the left part is never narrower than the longest figure, so it keeps one width as the number changes`() {
        val f = fit(widths(200, figurePx = 40, longest = 90))
        assertEquals(90, f.leftPx)
        assertTrue("overflowing for the longest, not the figure shown", fit(widths(99, figurePx = 40, longest = 90)).overflows)
    }

    @Test
    fun `the widest evening piece is room the right part keeps`() {
        val f = fit(widths(100, evening = 50))
        assertEquals(50, f.rightPx)
        assertFalse(f.overflows)
        assertTrue("one less and it overflows", fit(widths(99, evening = 50)).overflows)
    }

    @Test
    fun `H2 and H5, the right part keeps its floor with no evening piece, and a wider piece widens it`() {
        assertEquals("daytime: the floor", 60, fit(widths(200, floor = 60)).rightPx)
        assertEquals("a narrower piece: the floor", 60, fit(widths(200, evening = 50, floor = 60)).rightPx)
        assertEquals("a wider piece: its width", 70, fit(widths(200, evening = 70, floor = 60)).rightPx)
        assertTrue("the floor counts towards the total", fit(widths(109, floor = 60)).overflows)
    }

    @Test
    fun `the steps do not place the kind, which is decided on the width they leave (dispatch 2026-11-05-10)`() {
        assertEquals(TopLinePlace.NONE, fit(widths(136)).topLine)
        assertEquals(TopLinePlace.NONE, fit(widths(45, figurePx = 100, figureShortPx = 30)).topLine)
    }
}
