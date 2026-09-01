package com.forager.app.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The day-boundary logic itself, isolated from Room — Journal Stage 2a dispatch: "test the
 * day-boundary logic hard... it is the part most likely to be subtly wrong and the part no UI test
 * would catch." A fixed [ZoneId] is passed explicitly to every case here rather than relying on
 * [ZoneId.systemDefault], so these assertions hold regardless of what zone this suite happens to run
 * in.
 */
class LocalDayRangeTest {

    private val portland = ZoneId.of("America/Los_Angeles")

    @Test
    fun `foundOnKey matches MushroomLogEntryEntity's own ISO-8601 yyyy-MM-dd format exactly`() {
        val range = LocalDayRange.of(LocalDate.of(2026, 8, 5), portland)

        assertEquals("2026-08-05", range.foundOnKey)
    }

    @Test
    fun `a find at 11-59pm local belongs to that evening's day, not the next`() {
        val range = LocalDayRange.of(LocalDate.of(2026, 8, 5), portland)

        // 11:59pm on Aug 5th, Pacific time — must fall inside [start, end) for Aug 5th, not Aug 6th.
        val elevenFiftyNinePm = LocalDate.of(2026, 8, 5).atTime(23, 59).atZone(portland).toInstant().toEpochMilli()

        assertTrue(
            "expected 11:59pm Aug 5th ($elevenFiftyNinePm) to fall inside Aug 5th's range " +
                "(${range.epochMillisStartInclusive} until ${range.epochMillisEndExclusive})",
            elevenFiftyNinePm in range.epochMillisStartInclusive until range.epochMillisEndExclusive,
        )

        // One minute later, midnight, is the first instant of the *next* day — must fall outside.
        val midnight = LocalDate.of(2026, 8, 6).atStartOfDay(portland).toInstant().toEpochMilli()
        assertTrue(midnight !in range.epochMillisStartInclusive until range.epochMillisEndExclusive)
        assertEquals(midnight, range.epochMillisEndExclusive)
    }

    @Test
    fun `the range is exactly half-open, start inclusive and end exclusive`() {
        val range = LocalDayRange.of(LocalDate.of(2026, 8, 5), portland)

        assertEquals(range.epochMillisStartInclusive, Instant.ofEpochMilli(range.epochMillisStartInclusive).toEpochMilli())
        // The instant exactly at epochMillisEndExclusive belongs to the *next* day's own start, not this one.
        val nextDayRange = LocalDayRange.of(LocalDate.of(2026, 8, 6), portland)
        assertEquals(range.epochMillisEndExclusive, nextDayRange.epochMillisStartInclusive)
    }

    /**
     * 2026-03-08 is a US spring-forward day in `America/Los_Angeles` (clocks jump 2am to 3am) — 23
     * hours of wall-clock time, not 24. A naive `+ 86_400_000L` offset would place this day's own end
     * boundary one hour into the *next* calendar day's true midnight instead of at it.
     */
    @Test
    fun `a spring-forward DST transition day spans 23 hours, not a naive 24`() {
        val range = LocalDayRange.of(LocalDate.of(2026, 3, 8), portland)

        val actualDuration = Duration.ofMillis(range.epochMillisEndExclusive - range.epochMillisStartInclusive)
        assertEquals(Duration.ofHours(23), actualDuration)

        // The end boundary is exactly March 9th's own real local midnight, not March 8th's midnight + 24h.
        val march9Midnight = LocalDate.of(2026, 3, 9).atStartOfDay(portland).toInstant().toEpochMilli()
        assertEquals(march9Midnight, range.epochMillisEndExclusive)
    }

    /** The mirror case: 2026-11-01 is a US fall-back day in the same zone — 25 hours, not 24. */
    @Test
    fun `a fall-back DST transition day spans 25 hours, not a naive 24`() {
        val range = LocalDayRange.of(LocalDate.of(2026, 11, 1), portland)

        val actualDuration = Duration.ofMillis(range.epochMillisEndExclusive - range.epochMillisStartInclusive)
        assertEquals(Duration.ofHours(25), actualDuration)
    }

    @Test
    fun `defaults to the system zone when none is passed`() {
        val range = LocalDayRange.of(LocalDate.of(2026, 8, 5))
        val expected = LocalDayRange.of(LocalDate.of(2026, 8, 5), ZoneId.systemDefault())

        assertEquals(expected, range)
    }
}
