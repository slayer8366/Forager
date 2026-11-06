package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.SundownLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Dispatch 2026-11-05-10, Amendment 1, H5 (RECORD -2181), the pure half: the card's evening line before sunset, where it breaks, and
 * what the right part is sized for. The owner: "Shorten to Sunset: 4:52 PM ▪︎ Dark: 5:23 PM status words are on the right panel", then
 * "Split it at the ▪ (Recommended)". Pinned on a UTC clock and round widths, so a slip is a literal failure.
 */
class NavigationCardEveningTextTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private fun clockOf(full: String, short: String) = object : SundownClock {
        override fun full(epochMillis: Long): String = SimpleDateFormat(full, Locale.US).apply { timeZone = utc }.format(Date(epochMillis))
        override fun short(epochMillis: Long): String = SimpleDateFormat(short, Locale.US).apply { timeZone = utc }.format(Date(epochMillis))
    }
    private val twelve = clockOf("h:mm a", "h:mm")
    private val twentyFour = clockOf("H:mm", "H:mm")

    /** 2026-10-03T16:52:00Z: the owner's "4:52 PM" on the UTC clock. */
    private val sunset = 1_791_046_320_000L
    private val dusk = sunset + 31 * 60_000L
    private val now = sunset - 148 * 60_000L

    @Test
    fun `before sunset the card's line is the owner's, with no countdown`() {
        assertEquals("Sunset: 4:52 PM ▪︎ Dark: 5:23 PM", cardSundownLineText(SundownLine.BeforeSunset(now, sunset, dusk, null), twelve))
        // Positive control: the strip's own line is unchanged, countdown and all.
        assertEquals("Sunset 4:52 PM · in 2 h 28 min · dark 5:23", sundownLineText(SundownLine.BeforeSunset(now, sunset, dusk, null), twelve))
    }

    @Test
    fun `the planner's readings - no dusk, a 24-hour clock, and the other forms unchanged`() {
        assertEquals("no dusk: sunset alone", "Sunset: 4:52 PM", cardSundownLineText(SundownLine.BeforeSunset(now, sunset, null, null), twelve))
        assertEquals("Sunset: 16:52 ▪︎ Dark: 17:23", cardSundownLineText(SundownLine.BeforeSunset(now, sunset, dusk, null), twentyFour))
        val startBack = SundownLine.BeforeSunset(now, sunset, dusk, now + 60 * 60_000L)
        assertEquals("the start-back time keeps its words", sundownLineText(startBack, twelve), cardSundownLineText(startBack, twelve))
        val after = SundownLine.AfterSunset(sunset + 600_000L, sunset, dusk)
        assertEquals("after sunset keeps its words", sundownLineText(after, twelve), cardSundownLineText(after, twelve))
        assertEquals("Dark since 5:23 PM", cardSundownLineText(SundownLine.DarkSince(dusk), twelve))
        assertNull("hidden before its window opens, as the strip's", cardSundownLineText(SundownLine.FindingPosition, twelve))
    }

    @Test
    fun `the pieces break at the square only where asked, and at the joins as before`() {
        val line = "Sunset: 4:52 PM ▪︎ Dark: 5:23 PM"
        assertEquals(listOf("Sunset: 4:52 PM", "Dark: 5:23 PM"), eveningPieces(line, null, breakAtSquare = true))
        assertEquals(listOf(line), eveningPieces(line, null, breakAtSquare = false))
        assertEquals(listOf("Sunset: 4:52 PM", "Dark: 5:23 PM", "Back by 6:00 PM"), eveningPieces(line, "Back by 6:00 PM"))
        assertEquals(listOf("Sun set 4:52 PM", "dark in 21 min"), eveningPieces("Sun set 4:52 PM · dark in 21 min", null, breakAtSquare = false))
    }

    @Test
    fun `the widest time on the clock decides the widths, whatever today's times`() {
        // Every digit 10 px, every other character 5: "10:58 PM" and "1:58 PM" differ by a digit.
        val widthOf = { s: String -> s.sumOf { if (it.isDigit()) 10 else 5 }.toInt() }
        assertEquals(widthOf("10:58 PM"), widthOf(widestClockTime(twelve, widthOf)))
        assertEquals(widthOf("10:58"), widthOf(widestClockTime(twentyFour, widthOf)))
        val w = cardEveningWidths(twelve, widthOf)
        assertEquals(widthOf("Sunset: 10:58 PM ▪︎ Dark: 10:58 PM"), w.oneLinePx)
        assertEquals("the broken form's wider line", widthOf("Sunset: 10:58 PM"), w.brokenPx)
    }

    @Test
    fun `the line breaks unless it fits whole beside the left part with the next turn reserved`() {
        val w = CardEveningWidths(oneLinePx = 200, brokenPx = 100)
        assertFalse("fits: 129 + 13 + 200 = 342", eveningBreaks(partsPx = 342, dividerPx = 13, leftNeedPx = 129, evening = w))
        assertTrue("one short", eveningBreaks(partsPx = 341, dividerPx = 13, leftNeedPx = 129, evening = w))
        // The next turn's reserve: a gap, its arrow, a gap and the widest turn distance (Phase 1 of dispatch 2026-11-06-01: about 75 dp).
        assertEquals(4 + 22 + 4 + 45, nextTurnReservePx(gapPx = 4, arrowPx = 22, turnDistancePx = 45))
    }
}
