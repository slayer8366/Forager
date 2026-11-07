package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-11-06-01, Amendment 1, J2 (RECORD -2183), the pure half: where the next turn goes, on pinned widths (px). "Decided once
 * per screen and text size, never flipping while walking": on the left part at its narrowest, every evening piece the card can show
 * counted, so an evening line that would narrow the left part puts the next turn on its own line in daytime too.
 */
class NavigationCardNextTurnPlaceTest {

    // The parts 300, the divider 13, the left part's widest need 100, the right part's daytime width 107, the turn line 86, a gap 4,
    // the next turn's space 71: beside the needle needs 161 of the left part.
    private fun place(partsPx: Int = 300, widestEveningPiecePx: Int = 0, rightFloorPx: Int = 107, leftNeedPx: Int = 100) =
        nextTurnPlace(partsPx, dividerPx = 13, leftNeedPx = leftNeedPx, rightFloorPx = rightFloorPx, widestEveningPiecePx = widestEveningPiecePx, turnLinePx = 86, gapPx = 4, nextTurnSpacePx = 71)

    @Test
    fun `beside the needle where the turn line, a gap and the next turn fit the left part, to the pixel`() {
        assertEquals("180 left, 161 needed", NextTurnPlace.BESIDE_NEEDLE, place())
        assertEquals("161 left, 161 needed", NextTurnPlace.BESIDE_NEEDLE, place(partsPx = 281))
        assertEquals("160 left, 161 needed", NextTurnPlace.OWN_LINE, place(partsPx = 280))
    }

    @Test
    fun `an evening piece wider than the daytime right part narrows the left part, so the next turn takes its own line in every state`() {
        assertEquals("daytime 180 left", NextTurnPlace.BESIDE_NEEDLE, place(widestEveningPiecePx = 107))
        assertEquals("a 127 px piece leaves 160", NextTurnPlace.OWN_LINE, place(widestEveningPiecePx = 127))
    }

    @Test
    fun `where the right part keeps no daytime width (H8) the widest evening piece alone decides`() {
        assertEquals(NextTurnPlace.BESIDE_NEEDLE, place(partsPx = 250, rightFloorPx = 0, widestEveningPiecePx = 76))
        assertEquals(NextTurnPlace.OWN_LINE, place(partsPx = 250, rightFloorPx = 0, widestEveningPiecePx = 77))
    }

    @Test
    fun `the left part never counts narrower than its own need, where the card's overflow rule keeps it whole`() {
        assertEquals(NextTurnPlace.BESIDE_NEEDLE, place(partsPx = 100, leftNeedPx = 161))
        assertEquals(NextTurnPlace.OWN_LINE, place(partsPx = 100, leftNeedPx = 160))
    }

    @Test
    fun `every evening form the card's formatters give is among the pieces counted, broken at the square or not`() {
        val clock = object : SundownClock {
            override fun full(epochMillis: Long) = "%d:%02d PM".format((epochMillis / 3_600_000L % 12).let { if (it == 0L) 12 else it }, epochMillis / 60_000L % 60)
            override fun short(epochMillis: Long) = "%d:%02d".format((epochMillis / 3_600_000L % 12).let { if (it == 0L) 12 else it }, epochMillis / 60_000L % 60)
        }
        val broken = cardEveningPiecesAtWidest(clock, breaks = true)
        for (piece in listOf("Sunset: 10:58 PM", "Dark: 10:58 PM", "Sunset 10:58 PM", "start back by 10:58", "start back was 10:58", "Sun set 10:58 PM", "dark in 59 min", "Dark since 10:58 PM", "Sun stays down today", "Back by 10:58 PM")) {
            assertTrue("<$piece> in $broken", piece in broken)
        }
        assertTrue("one line where it does not break", "Sunset: 10:58 PM ▪︎ Dark: 10:58 PM" in cardEveningPiecesAtWidest(clock, breaks = false))
    }
    // J3 (RECORD -2183), the owner: "Arrow in the right part", "Own line, card moves up"; the step path, "Yes, that's it": steps 1 to 3.

    @Test
    fun `J3 step 1 - portrait at every font and landscape at 1,0 and 1,3 keep J2's decision, evening line or none`() {
        for (decided in listOf(NextTurnPlace.BESIDE_NEEDLE, NextTurnPlace.OWN_LINE)) {
            for (evening in listOf(false, true)) {
                assertEquals("$decided, evening $evening", decided, nextTurnPlaceNow(decided, landscapeLargeText = false, eveningLine = evening))
            }
        }
    }

    @Test
    fun `J3 step 2 - landscape at font 2,0 with no evening line puts the next turn in the right part`() {
        for (decided in listOf(NextTurnPlace.BESIDE_NEEDLE, NextTurnPlace.OWN_LINE)) {
            assertEquals(decided.name, NextTurnPlace.RIGHT_PART, nextTurnPlaceNow(decided, landscapeLargeText = true, eveningLine = false))
        }
    }

    @Test
    fun `J3 step 3 - landscape at font 2,0 with an evening line gives the next turn its own line`() {
        for (decided in listOf(NextTurnPlace.BESIDE_NEEDLE, NextTurnPlace.OWN_LINE)) {
            assertEquals(decided.name, NextTurnPlace.OWN_LINE, nextTurnPlaceNow(decided, landscapeLargeText = true, eveningLine = true))
        }
    }

    @Test
    fun `J3 - landscape at font 2,0 is the case steps 2 and 3 name, and nothing else is`() {
        assertTrue(isLandscapeLargeText(landscape = true, fontScale = 2.0f))
        assertTrue("the largest Android offers is 2.0; nothing larger falls back to J2", isLandscapeLargeText(landscape = true, fontScale = 2.2f))
        assertEquals("landscape 1.3 is step 1's", false, isLandscapeLargeText(landscape = true, fontScale = 1.3f))
        assertEquals("landscape 1.0 is step 1's", false, isLandscapeLargeText(landscape = true, fontScale = 1.0f))
        assertEquals("portrait 2.0 is step 1's", false, isLandscapeLargeText(landscape = false, fontScale = 2.0f))
    }
}
