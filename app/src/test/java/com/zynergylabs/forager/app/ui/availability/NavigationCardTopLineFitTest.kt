package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.DistanceKind
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.model.formatDistanceWithAccuracy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-11-05-10 (RECORD -2181): the top line's pure half ([kindPlace], [longestFigures], [TopLineHalves],
 * [leftPartWidthPx]). Pinned on round numbers so each threshold is exact.
 *
 * The owner's steps where "2.2 mi by trail" can't share the line: "Option 1 but stack by on top of trail first, then just shorten it
 * to trail, and then drop it down"; "straight" goes straight to its own line (the planner's reading, told to the owner).
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

    // ── The halves and the left part's width ──

    @Test
    fun `the gap is centred on the middle`() {
        val even = TopLineHalves(widthPx = 100, gapPx = 4)
        assertEquals(48, even.figureEndPx)
        assertEquals(52, even.kindStartPx)
        assertEquals(48, even.leftRoomPx)
        assertEquals(48, even.rightRoomPx)
        val odd = TopLineHalves(widthPx = 101, gapPx = 5)
        assertEquals("the middle at 50, 2 px left of it", 48, odd.figureEndPx)
        assertEquals("3 px right of it", 53, odd.kindStartPx)
        assertEquals(48, odd.rightRoomPx)
    }

    @Test
    fun `the left part's width is what InfoParts drew before this dispatch`() {
        val fit = NavigationInfoFit(turnText = "", headingAsIcon = false, leftPx = 150, rightPx = 80)
        assertEquals("the right part keeps its piece's width", 300 - 10 - 80, leftPartWidthPx(300, 10, fit, reserveRight = true))
        assertEquals("with no evening line, the left part takes it all", 290, leftPartWidthPx(300, 10, fit, reserveRight = false))
        val over = fit.copy(overflows = true, leftPx = 250)
        assertEquals("overflowing, the left part stays whole and the right part takes what is left", 300 - 10 - 40, leftPartWidthPx(300, 10, over, reserveRight = true))
        assertEquals("never less than nothing", 0, leftPartWidthPx(5, 10, fit, reserveRight = true))
    }

    // ── The steps, where the longest figure fits its half (every reading agrees) ──

    /** A 200 px left part with a 4 px gap: 98 px each side. The longest figure 90, so it fits its half. */
    private fun place(kind: Int?, stacked: Int? = null, short: Int? = null, figure: Int = 90) =
        kindPlace(TopLineWidths(leftPartPx = 200, gapPx = 4, figurePx = figure, kindPx = kind, stackedPx = stacked, shortPx = short))

    @Test
    fun `with no kind there is nothing to place`() {
        assertEquals(KindPlace.NONE, place(kind = null))
    }

    @Test
    fun `the whole kind beside the distance where it fits the right half`() {
        assertEquals(KindPlace.BESIDE, place(kind = 98, stacked = 50, short = 50))
    }

    @Test
    fun `then by stacked over trail`() {
        assertEquals(KindPlace.STACKED, place(kind = 99, stacked = 98, short = 60))
    }

    @Test
    fun `then trail alone`() {
        assertEquals(KindPlace.SHORT, place(kind = 99, stacked = 99, short = 98))
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
    fun `the kind forms`() {
        assertEquals("by" to "trail", kindStackOf("by trail"))
        assertEquals("trail", kindShortOf("by trail"))
        assertEquals(null, kindStackOf("straight"))
        assertEquals(null, kindShortOf("straight"))
        assertEquals(DistanceKind.STRAIGHT, distanceKindOf("straight"))
        assertEquals(null, distanceKindOf("Arrived"))
    }
}
