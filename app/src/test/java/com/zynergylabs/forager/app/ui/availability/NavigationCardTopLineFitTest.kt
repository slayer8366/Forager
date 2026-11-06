package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.DistanceKind
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.model.formatDistanceWithAccuracy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-11-05-10 (RECORD -2181): the top line's pure half ([kindPlace], [longestFigures], [TopLineMeeting],
 * [leftPartWidthPx], [topLinePlace], [longestCardFigurePx]). Pinned on round numbers so each threshold is exact.
 *
 * The owner: "Meet at the middle (Recommended)", then "Shift the middle a little (Recommended)" where the longest distance doesn't
 * fit left of the middle. The owner's steps where "2.2 mi by trail" can't share the line: "Option 1 but stack by on top of trail
 * first, then just shorten it to trail, and then drop it down"; "straight" goes straight to its own line (the planner's reading,
 * told to the owner). The planner's call of 2026-11-06 decided the step for the narrowest left part across every heading and turn
 * word; since Amendment 1, G3 and H1, the left part is one width for the walk, and the step is decided on it as drawn.
 */
class NavigationCardTopLineFitTest {

    // ── The widest figure each kind can show (RECORD -2181: "chosen for the longest distance the card could show") ──

    /** [text] with every digit an 8: in tabular figures, every figure of one shape is one width. */
    private fun shape(text: String) = text.map { if (it.isDigit()) '8' else it }.joinToString("")

    /** Whether [shown] is never wider than one of [longest]: the same unit and the same "≈ ", and no more digits. */
    private fun coveredBy(shown: String, longest: List<String>): Boolean = longest.any { widest ->
        widest.substringAfterLast(' ') == shown.substringAfterLast(' ') &&
            (widest.startsWith("≈ ") || !shown.startsWith("≈ ")) &&
            shown.count(Char::isDigit) <= widest.count(Char::isDigit) &&
            shown.length <= widest.length
    }

    /**
     * Distances in metres up to the last with two whole digits of tenths in [unit] ("99.9 mi", "99.9 km"): every 10 cm to 2 km,
     * where the feet and metres round up to four digits ("1320 ft", "1000 m"), then every 10 m.
     */
    private fun sweep(unit: DistanceUnit): Sequence<Double> {
        val last = 99.94 * if (unit == DistanceUnit.MILES) 1_609.344 else 1_000.0
        return (0..20_000).asSequence().map { it / 10.0 } + (200..(last / 10).toInt()).asSequence().map { it * 10.0 }
    }

    @Test
    fun `by trail, the route's figures are never wider than the longest the fit is decided for`() {
        for (unit in DistanceUnit.entries) {
            val longest = longestFigures(DistanceKind.BY_TRAIL, unit)
            val shapes = sweep(unit).map { shape(formatDistanceMeters(it, unit)) }.toSet()
            val wider = shapes.filterNot { coveredBy(it, longest) }
            assertTrue("$unit: shapes wider than $longest: $wider", wider.isEmpty())
            // Positive control: the longest are reached, so they are not wider than they need to be.
            assertTrue("$unit: the feet or metres' four digits are reached: $shapes", shapes.contains(longest[0]))
            assertTrue("$unit: two whole digits of tenths are reached: $shapes", shapes.contains(longest[1]))
        }
    }

    @Test
    fun `straight, the figures outside the fix's circle are never wider than the longest the fit is decided for`() {
        for (unit in DistanceUnit.entries) {
            val longest = longestFigures(DistanceKind.STRAIGHT, unit)
            val shapes = mutableSetOf<String>()
            // No accuracy reported, the S22's constant 3.79 m, a middling fix, and the live gate's ceiling (50 m).
            for (accuracy in listOf<Float?>(null, 3.79f, 12.5f, 50f)) {
                sweep(unit)
                    .filter { accuracy == null || it > accuracy }
                    .forEach { shapes += shape(formatDistanceWithAccuracy(it, accuracy, unit)) }
            }
            val wider = shapes.filterNot { coveredBy(it, longest) }
            assertTrue("$unit: shapes wider than $longest: $wider", wider.isEmpty())
            assertTrue("$unit: the four digits after ≈ are reached: $shapes", shapes.contains(longest[0]))
            assertTrue("$unit: two whole digits of tenths are reached: $shapes", shapes.contains(longest[1]))
        }
    }

    @Test
    fun `the longest figures, as pinned`() {
        assertEquals(listOf("8888 ft", "88.8 mi"), longestFigures(DistanceKind.BY_TRAIL, DistanceUnit.MILES))
        assertEquals(listOf("≈ 8888 ft", "88.8 mi"), longestFigures(DistanceKind.STRAIGHT, DistanceUnit.MILES))
        assertEquals(listOf("8888 m", "88.8 km"), longestFigures(DistanceKind.BY_TRAIL, DistanceUnit.KILOMETERS))
        assertEquals(listOf("≈ 8888 m", "88.8 km"), longestFigures(DistanceKind.STRAIGHT, DistanceUnit.KILOMETERS))
    }

    @Test
    fun `G3, the left part is sized for the widest figure either kind could show in the unit`() {
        // Was `the narrowest figure, as pinned`: "8 ft" and "8 m", the floor of the narrowest left part, gone with it.
        val widths = mapOf("≈ 8888 ft" to 101, "8888 ft" to 81, "88.8 mi" to 85, "≈ 8888 m" to 105, "8888 m" to 84, "88.8 km" to 92)
        assertEquals("≈ 8888 ft, the straight line's", 101, longestCardFigurePx(DistanceUnit.MILES) { widths.getValue(it) })
        assertEquals("≈ 8888 m", 105, longestCardFigurePx(DistanceUnit.KILOMETERS) { widths.getValue(it) })
        assertEquals("a turn's distance for the next-turn reserve", "88.8 mi", longestTurnDistance(DistanceUnit.MILES))
        assertEquals("88.8 km", longestTurnDistance(DistanceUnit.KILOMETERS))
    }

    @Test
    fun `the gap is centred on the middle where the longest figure fits left of it`() {
        val even = TopLineMeeting(widthPx = 100, gapPx = 4, longestFigurePx = 48)
        assertEquals(48, even.figureEndPx)
        assertEquals(52, even.kindStartPx)
        assertEquals(48, even.rightRoomPx)
        assertEquals(0, even.shiftPx)
        val odd = TopLineMeeting(widthPx = 101, gapPx = 5, longestFigurePx = 10)
        assertEquals("the middle at 50, 2 px left of it", 48, odd.figureEndPx)
        assertEquals("a gap's width on", 53, odd.kindStartPx)
        assertEquals(48, odd.rightRoomPx)
    }

    @Test
    fun `the meeting point moves right just enough for the longest figure`() {
        val one = TopLineMeeting(widthPx = 100, gapPx = 4, longestFigurePx = 49)
        assertEquals("one pixel past the middle's end, one pixel moved", 49, one.figureEndPx)
        assertEquals(1, one.shiftPx)
        assertEquals(53, one.kindStartPx)
        assertEquals(47, one.rightRoomPx)
        val far = TopLineMeeting(widthPx = 100, gapPx = 4, longestFigurePx = 70)
        assertEquals(70, far.figureEndPx)
        assertEquals(22, far.shiftPx)
        assertEquals(26, far.rightRoomPx)
    }

    @Test
    fun `the meeting point never moves past the left part's right edge`() {
        val over = TopLineMeeting(widthPx = 100, gapPx = 4, longestFigurePx = 130)
        assertEquals(100, over.figureEndPx)
        assertEquals("no room beside it", -4, over.rightRoomPx)
    }

    @Test
    fun `with no kind the word ends at the middle, unmoved`() {
        val alone = TopLineMeeting(widthPx = 100, gapPx = 4, longestFigurePx = null)
        assertEquals(48, alone.figureEndPx)
        assertEquals(0, alone.shiftPx)
    }

    @Test
    fun `the left part's width is what InfoParts drew before this dispatch, the right part always kept`() {
        val fit = NavigationInfoFit(turnText = "", leftPx = 150, rightPx = 80)
        assertEquals("the right part keeps its width", 300 - 10 - 80, leftPartWidthPx(300, 10, fit))
        val over = fit.copy(overflows = true, leftPx = 250)
        assertEquals("overflowing, the left part stays whole and the right part takes what is left", 300 - 10 - 40, leftPartWidthPx(300, 10, over))
        assertEquals("never less than nothing", 0, leftPartWidthPx(5, 10, fit))
        // Was also: `assertEquals("with no evening line, the left part takes it all", 290, leftPartWidthPx(300, 10, fit, reserveRight = false))`.
        // Since Amendment 1, H2 and H5, the right part keeps its width with no evening line (the status words live there).
    }

    // The narrowest left part (the planner's call D1, 2026-11-06) went with G3 and H1: its two tests asserted, for example,
    // `assertEquals("the kind, 70, the widest need", 70, narrowest(fit, figure = 30, kind = 70))`. The step is now decided on the left
    // part as drawn, which keeps one width for the walk (below).

    // ── The steps ──

    /** A 200 px left part with a 4 px gap: 98 px each side. The longest figure 90, so it fits its half. */
    private fun place(kind: Int?, stacked: Int? = null, short: Int? = null, figure: Int = 90) =
        kindPlace(TopLineWidths(leftPartPx = 200, gapPx = 4, figurePx = figure, kindPx = kind, stackedPx = stacked, shortPx = short))

    @Test
    fun `with no kind there is nothing to place`() {
        assertEquals(KindPlace.NONE, place(kind = null))
    }

    @Test
    fun `the whole kind beside the distance where it fits right of the meeting point`() {
        assertEquals(KindPlace.BESIDE, place(kind = 98, stacked = 50, short = 50))
    }

    @Test
    fun `then by stacked over trail`() {
        assertEquals(KindPlace.STACKED, place(kind = 99, stacked = 98, short = 60))
    }

    @Test
    fun `then trail alone, in the owner's order, though the stack is never wider than trail in the card`() {
        // Pinned on widths the card cannot produce (a stack wider than its own wider word), so the owner's order is held.
        assertEquals(KindPlace.SHORT, place(kind = 99, stacked = 99, short = 98))
        // As the card measures them the stack is as wide as "trail", so wherever "trail" fits the stack fits first.
        assertEquals(KindPlace.STACKED, place(kind = 99, stacked = 98, short = 98))
        assertEquals(KindPlace.OWN_LINE, place(kind = 99, stacked = 99, short = 99))
    }

    @Test
    fun `then its own line`() {
        assertEquals(KindPlace.OWN_LINE, place(kind = 99, stacked = 99, short = 99))
    }

    @Test
    fun `straight cannot stack or shorten, so it goes to its own line`() {
        assertEquals(KindPlace.OWN_LINE, place(kind = 99))
    }

    @Test
    fun `a longest figure past its half moves the meeting point and takes the kind's room, not the kind's line outright`() {
        // 110 past the half's 98: the kind starts at 114, 86 px of room. Before the owner's "Shift the middle a little" this was
        // its own line whatever the kind's width.
        assertEquals(KindPlace.BESIDE, place(kind = 86, figure = 110))
        assertEquals(KindPlace.STACKED, place(kind = 87, stacked = 86, short = 86, figure = 110))
        assertEquals(KindPlace.OWN_LINE, place(kind = 87, figure = 110))
    }

    @Test
    fun `the step is decided on the left part as drawn, one width for the walk`() {
        // Was `the step is decided on the narrowest left part, so turning does not change it`, which asserted that a fit drawn 250
        // wide and one drawn 120 wide gave the same step, decided for a 70 px narrowest left part. Since G3 and H1 the left part no
        // longer follows the turn row, so there is one width to decide on.
        val fit = NavigationInfoFit(turnText = "", leftPx = 150, rightPx = 80)
        fun at(fit: NavigationInfoFit) = topLinePlace(
            kindText = "by trail", shownFigure = "1280 ft", unit = DistanceUnit.MILES, fit = fit, totalPx = 300, dividerPx = 10, gapPx = 4,
            figurePx = { if (it.startsWith("88.8")) 100 else 90 },
            kindPx = { mapOf("by trail" to 70, "by" to 20, "trail" to 40)[it] ?: 0 },
        )
        assertEquals("decided for the left part drawn, 300 - 10 - 80", 210, at(fit).decidedForPx)
        // 210 wide, the meeting point at 103, the kind from 107: 103 px of room, so "by trail" (70) sits beside.
        assertEquals(KindPlace.BESIDE, at(fit).kind)
        assertEquals("the meeting point is set for the widest figure", 100, at(fit).longestFigurePx)
        val over = fit.copy(overflows = true, leftPx = 250)
        assertEquals("overflowing: the left part as drawn, 250", 250, at(over).decidedForPx)
    }

    @Test
    fun `with no kind the top line has nothing to place`() {
        val fit = NavigationInfoFit(turnText = "", leftPx = 150, rightPx = 80)
        assertEquals(TopLinePlace.NONE, topLinePlace(null, "Arrived", DistanceUnit.MILES, fit, 300, 10, 4, { 90 }, { 40 }))
    }

    @Test
    fun `the kind forms`() {
        assertEquals("by" to "trail", kindStackOf("by trail"))
        assertEquals("trail", kindShortOf("by trail"))
        assertEquals(null, kindStackOf("straight"))
        assertEquals(null, kindShortOf("straight"))
        assertEquals(DistanceKind.STRAIGHT, distanceKindOf("straight"))
        assertEquals(null, distanceKindOf("Arrived"))
    }
}
