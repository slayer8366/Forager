package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.SundownCountdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [sundownLineFor], [civilDuskAfter], [sundownLeaveByAt] and [SunCrossing.previousDescendingCrossing]
 * (dispatch 2026-09-28-592, Amendment 1, RECORD -593): which state the sundown line is in, from the
 * real countdown and the real crossing search, with nothing faked.
 */
class SundownLineTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val here = LatLng(45.0, -122.0)
    private val morning = 1_791_010_800_000L // 2026-10-03T07:00:00Z
    private val compute = ComputeSundownCountdownUseCase()
    private val known = compute(morning, here, morning, 0L) as SundownCountdown.Known
    private val sunset = known.sunsetAtEpochMillis
    private val dusk = known.civilDuskAtEpochMillis!!

    private fun countdownAt(now: Long, marginMillis: Long = hour) = compute(now, here, now, marginMillis)

    private fun assertSameSecond(expected: Long, actual: Long?) {
        assertNotNull(actual)
        assertTrue("$actual within a second of $expected", kotlin.math.abs(actual!! - expected) < 1_000L)
    }

    @Test
    fun `the backward search finds the sunset the forward search found, from after it`() {
        val found = SunCrossing.previousDescendingCrossing(sunset + 2 * hour, 24 * hour, here.lat, here.lng, SunCrossing.SUNSET_ALTITUDE_DEGREES)
        assertSameSecond(sunset, found)
    }

    @Test
    fun `the backward search from before sunset finds yesterday's, a day earlier`() {
        val found = SunCrossing.previousDescendingCrossing(sunset - hour, 24 * hour, here.lat, here.lng, SunCrossing.SUNSET_ALTITUDE_DEGREES)!!
        assertTrue("about a day before: ${(sunset - found) / minute} min", sunset - found in (23 * hour + 50 * minute)..(24 * hour + 10 * minute))
    }

    @Test
    fun `civil dusk after a sunset is the countdown's own dusk`() {
        assertSameSecond(dusk, civilDuskAfter(sunset, here))
    }

    @Test
    fun `the leave-by time is the turnaround less the walk back, or the turnaround when it is unknown`() {
        assertEquals(1_000L - 300L, sundownLeaveByAt(1_000L, 300L))
        assertEquals(1_000L, sundownLeaveByAt(1_000L, null))
    }

    @Test
    fun `no position is finding your position`() {
        assertEquals(SundownLine.FindingPosition, sundownLineFor(SundownCountdown.NoPositionYet, null, null))
    }

    @Test
    fun `before sunset, the countdown's sunset and dusk, and the start-back time passed through`() {
        val now = sunset - 3 * hour
        val line = sundownLineFor(countdownAt(now), here, startBackAtEpochMillis = sunset - 2 * hour)
        line as SundownLine.BeforeSunset
        assertEquals(now, line.nowEpochMillis)
        assertSameSecond(sunset, line.sunsetAtEpochMillis)
        assertSameSecond(dusk, line.civilDuskAtEpochMillis)
        assertEquals(sunset - 2 * hour, line.startBackAtEpochMillis)
    }

    @Test
    fun `before sunset with no start-back time, none`() {
        val line = sundownLineFor(countdownAt(sunset - 3 * hour), here, startBackAtEpochMillis = null) as SundownLine.BeforeSunset
        assertEquals(null, line.startBackAtEpochMillis)
    }

    /** The countdown has moved on to tomorrow's sunset; the line must say tonight's, not count down to tomorrow. */
    @Test
    fun `between sunset and dark with the countdown on tomorrow's sunset, today's sunset and its dusk`() {
        val now = sunset + 10 * minute
        val countdown = countdownAt(now) as SundownCountdown.Known
        assertTrue("precondition: the countdown is on tomorrow's", countdown.sunsetAtEpochMillis > now + 20 * hour)

        val line = sundownLineFor(countdown, here, startBackAtEpochMillis = 123L) as SundownLine.AfterSunset
        assertSameSecond(sunset, line.sunsetAtEpochMillis)
        assertSameSecond(dusk, line.civilDuskAtEpochMillis)
        assertEquals(now, line.nowEpochMillis)
    }

    @Test
    fun `after dark with the countdown on tomorrow's sunset, dark since today's dusk`() {
        val line = sundownLineFor(countdownAt(sunset + 3 * hour), here, startBackAtEpochMillis = null) as SundownLine.DarkSince
        assertSameSecond(dusk, line.civilDuskAtEpochMillis)
    }

    @Test
    fun `in the small hours, still dark since the evening's dusk, not a countdown to the coming sunset`() {
        val line = sundownLineFor(countdownAt(sunset + 9 * hour), here, startBackAtEpochMillis = null)
        line as SundownLine.DarkSince
        assertSameSecond(dusk, line.civilDuskAtEpochMillis)
    }

    /** A held countdown, as the watch holds one once its sunset has passed: that sunset and that dusk. */
    @Test
    fun `a held countdown past its sunset gives that sunset, and dark at its dusk`() {
        val held = (countdownAt(sunset - hour) as SundownCountdown.Known).copy(nowEpochMillis = sunset + 5 * minute)
        val line = sundownLineFor(held, here, startBackAtEpochMillis = null) as SundownLine.AfterSunset
        assertSameSecond(sunset, line.sunsetAtEpochMillis)
        assertSameSecond(dusk, line.civilDuskAtEpochMillis)

        val later = held.copy(nowEpochMillis = dusk + minute)
        assertEquals(SundownLine.DarkSince(held.civilDuskAtEpochMillis!!), sundownLineFor(later, here, null))
    }

    @Test
    fun `the polar cases pass through`() {
        assertEquals(SundownLine.SunDoesNotSet, sundownLineFor(SundownCountdown.SunDoesNotSet, here, null))
        assertEquals(SundownLine.SunStaysDown, sundownLineFor(SundownCountdown.SunStaysDown, here, null))
    }

    // ── Amendment 2 (RECORD -595): hidden until 2 h 30 min before sunset, or start-back under 1 h away ──

    private fun before(untilSunsetMinutes: Long, untilStartBackMinutes: Long? = null) =
        SundownLine.BeforeSunset(0L, untilSunsetMinutes * minute, null, untilStartBackMinutes?.let { it * minute })

    @Test
    fun `hidden at 2 h 31 min before sunset and shown at 2 h 29 min, and at exactly 2 h 30 min`() {
        assertEquals(false, before(151).isShown())
        assertEquals(true, before(149).isShown())
        assertEquals(true, before(150).isShown())
    }

    @Test
    fun `shown early when the start-back time is 59 min away, hidden at 61 min with sunset further than 2 h 30 min`() {
        assertEquals(true, before(240, untilStartBackMinutes = 59).isShown())
        assertEquals(false, before(240, untilStartBackMinutes = 61).isShown())
        assertEquals("at exactly an hour it is not yet less than one", false, before(240, untilStartBackMinutes = 60).isShown())
    }

    @Test
    fun `a start-back time already passed shows, and so does everything from sunset on`() {
        assertEquals(true, before(200, untilStartBackMinutes = -5).isShown())
        assertEquals(true, SundownLine.AfterSunset(0L, -minute, minute).isShown())
        assertEquals(true, SundownLine.DarkSince(-minute).isShown())
        assertEquals(true, SundownLine.SunStaysDown.isShown())
    }

    @Test
    fun `no position, and polar day, are hidden`() {
        assertEquals(false, SundownLine.FindingPosition.isShown())
        assertEquals(false, SundownLine.SunDoesNotSet.isShown())
    }

    @Test
    fun `the two thresholds are the owner's numbers`() {
        assertEquals(150 * minute, SUNDOWN_LINE_SUNSET_WINDOW_MILLIS)
        assertEquals(60 * minute, SUNDOWN_LINE_START_BACK_WINDOW_MILLIS)
    }
}
