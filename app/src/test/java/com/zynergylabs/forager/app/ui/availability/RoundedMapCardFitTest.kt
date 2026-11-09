package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-10-12-08 (RECORD -945, -950): the navigation info's two parts give way in what is left of the owner's order
 * (RECORD -934, -936; -950: "the remaining fit steps keep their order"): the turn words shrink to the degrees and the needle, then
 * "within 16 ft" takes "≤ 16 ft". Nothing further ("Invent no new trims"): past that the fit reports an overflow. A compass status
 * may take the crossed-out compass at each step before the next. The evening stack's widest piece is room the right part keeps.
 *
 * Rewritten from dispatch 2026-10-12-06's three-column fit (`cardColumnsFit`), whose steps for the kind word, "Alt" and the grid
 * reference's own line are moot since -945 and -950 (the kind is its own line; height and the grid reference are the strip's).
 * Those tests asserted, for example, `assertFalse("the kind goes first", f.kindInColumn)` and `assertTrue(f.gridOwnLine)`.
 *
 * Pinned on round numbers so each step's threshold is exact: divider 10, gap 2, arrow 10. The arrow line, whole: the turn (60 +
 * 2 + 10), a gap, the north arrow and the heading (10 + 2 + 40): 126; with the turn short (20 + 2 + 10): 86. The left part is
 * the widest of the figure (40), the arrow line and the kind (30); the total adds the divider and the evening piece.
 */
class RoundedMapCardFitTest {

    private fun widths(
        available: Int,
        headingIsStatus: Boolean = false,
        headingPx: Int = 40,
        evening: Int? = null,
        figurePx: Int = 40,
        figureShortPx: Int? = null,
    ) = NavigationInfoWidths(
        availablePx = available,
        dividerPx = 10,
        gapPx = 2,
        arrowPx = 10,
        figurePx = figurePx,
        figureShortPx = figureShortPx,
        kindPx = 30,
        turnWholePx = 60,
        turnShortPx = 20,
        headingPx = headingPx,
        headingIsStatus = headingIsStatus,
        headingIconPx = 12,
        eveningPiecePx = evening,
    )

    private fun fit(w: NavigationInfoWidths) = navigationInfoFit(w, turnWhole = "sharp right 169°", turnShort = "169°")

    @Test
    fun `with room everything is drawn whole`() {
        val f = fit(widths(136))
        assertEquals("sharp right 169°", f.turnText)
        assertFalse(f.figureShort)
        assertFalse(f.headingAsIcon)
        assertFalse(f.overflows)
        assertEquals("the left part needs the whole arrow line", 126, f.leftPx)
        assertEquals("no evening piece, no right part", 0, f.rightPx)
    }

    @Test
    fun `first the turn words shrink to the degrees and the needle`() {
        val f = fit(widths(135))
        assertEquals("169°", f.turnText)
        assertFalse("the figure keeps its whole form", f.figureShort)
        assertFalse(f.overflows)
        assertEquals(86, f.leftPx)
    }

    @Test
    fun `then within 16 ft takes its short form, only where the figure is what is too wide`() {
        val roomForShortTurn = fit(widths(110, figurePx = 100, figureShortPx = 30))
        assertEquals("169°", roomForShortTurn.turnText)
        assertFalse("the short turn was enough", roomForShortTurn.figureShort)
        val f = fit(widths(109, figurePx = 100, figureShortPx = 30))
        assertTrue("then the figure's short form", f.figureShort)
        assertFalse(f.overflows)
        assertEquals(86, f.leftPx)
    }

    @Test
    fun `where even the last step is not enough the fit says so and trims nothing more`() {
        val f = fit(widths(50, figurePx = 100, figureShortPx = 30))
        assertTrue(f.overflows)
        assertEquals("the last step: the turn short", "169°", f.turnText)
        assertTrue("and the figure short", f.figureShort)
        assertEquals("what the left part needs is reported", 86, f.leftPx)
    }

    @Test
    fun `a compass status in words takes the crossed-out compass before the turn words shrink`() {
        val f = fit(widths(100, headingIsStatus = true))
        assertTrue("the icon", f.headingAsIcon)
        assertEquals("the turn stays whole: the icon was enough (72 + 2 + 12 = 86, + 10)", "sharp right 169°", f.turnText)
    }

    @Test
    fun `a reading never becomes the icon`() {
        val f = fit(widths(100, headingIsStatus = false))
        assertFalse(f.headingAsIcon)
        assertEquals("169°", f.turnText)
    }

    @Test
    fun `the widest evening piece is room the right part keeps`() {
        val f = fit(widths(146, evening = 50))
        assertEquals("86 + 10 + 50 fits with the turn short", "169°", f.turnText)
        assertEquals(50, f.rightPx)
        assertFalse(f.overflows)
        assertTrue("one less and it overflows", fit(widths(145, evening = 50)).overflows)
    }

    @Test
    fun `with no heading drawn the arrow line is the turn and the two arrows`() {
        val f = fit(widths(200, headingPx = 0))
        assertEquals("72 + 2 + 10", 84, f.leftPx)
    }
}
