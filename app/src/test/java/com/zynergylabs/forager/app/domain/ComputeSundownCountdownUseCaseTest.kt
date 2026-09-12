package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.SundownCountdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reference sunsets are the same Open-Meteo values `SunCrossingTest` uses, fetched 2026-09-11.
 * This class does not re-test the solar search; it tests the states built on top of it, and in
 * particular the ones that must never render as a blank or a zero.
 */
class ComputeSundownCountdownUseCaseTest {

    private val computeSundownCountdown = ComputeSundownCountdownUseCase()

    private val london = LatLng(51.5074, -0.1278)
    private val londonSunset = 1789237318000L // 2026-09-12, Open-Meteo
    private val tromso = LatLng(69.6492, 18.9553)

    private val oneHour = 60 * 60 * 1000L

    @Test
    fun `reports sunset, a turnaround an hour earlier, and a dusk after sunset`() {
        val now = londonSunset - 3 * oneHour
        val state = computeSundownCountdown(now, london, fixAtEpochMillis = now, darknessMarginMillis = oneHour)

        val known = state as SundownCountdown.Known
        assertEquals("sunset", londonSunset.toDouble(), known.sunsetAtEpochMillis.toDouble(), 60_000.0)
        assertEquals(
            "turnaround is sunset minus the margin",
            known.sunsetAtEpochMillis - oneHour, known.turnaroundAtEpochMillis,
        )
        // Relative to the sunset this class computed, not to the Open-Meteo fixture: the two
        // agree to within seconds, and asserting against the fixture would be asserting that the
        // bisection reproduces another implementation exactly, which is not the claim.
        assertEquals(known.turnaroundAtEpochMillis - now, known.millisUntilTurnaround)
        assertEquals(known.sunsetAtEpochMillis - now, known.millisUntilSunset)
        assertEquals("about two hours to turnaround", 2 * oneHour.toDouble(), known.millisUntilTurnaround.toDouble(), 60_000.0)
        assertEquals("about three hours to sunset", 3 * oneHour.toDouble(), known.millisUntilSunset.toDouble(), 60_000.0)
        assertNotNull("London in September reaches civil dusk", known.civilDuskAtEpochMillis)
        assertTrue("civil dusk follows sunset", known.civilDuskAtEpochMillis!! > known.sunsetAtEpochMillis)
    }

    @Test
    fun `past the turnaround the countdown goes negative rather than clamping to zero`() {
        // A clamp would render as "0 minutes left" forever, which reads as a deadline being met
        // rather than missed. The sign is the information.
        val now = londonSunset - 30 * 60 * 1000L
        val known = computeSundownCountdown(now, london, now, oneHour) as SundownCountdown.Known

        assertTrue("turnaround has passed", known.isPastTurnaround)
        assertTrue("sunset has not", !known.isPastSunset)
        assertEquals("about half an hour past turnaround", -30 * 60 * 1000.0, known.millisUntilTurnaround.toDouble(), 60_000.0)
        assertEquals("about half an hour before sunset", 30 * 60 * 1000.0, known.millisUntilSunset.toDouble(), 60_000.0)
    }

    @Test
    fun `after sunset it reports the next one rather than a negative day`() {
        val now = londonSunset + 5 * 60 * 1000L
        val known = computeSundownCountdown(now, london, now, oneHour) as SundownCountdown.Known
        assertTrue(
            "the reported sunset must be ahead of now, not the one just missed",
            known.millisUntilSunset > 0,
        )
    }

    @Test
    fun `no position yet is its own state, not a zeroed countdown`() {
        val state = computeSundownCountdown(londonSunset, position = null, fixAtEpochMillis = null, darknessMarginMillis = oneHour)
        assertEquals(SundownCountdown.NoPositionYet, state)
    }

    @Test
    fun `polar day and polar night are told apart, not merged`() {
        val midsummerNoon = utcMillis(2026, 6, 21, 12)
        assertEquals(
            "Tromso at midsummer: the sun does not set",
            SundownCountdown.SunDoesNotSet,
            computeSundownCountdown(midsummerNoon, tromso, midsummerNoon, oneHour),
        )
        val midwinterNoon = utcMillis(2026, 12, 21, 12)
        assertEquals(
            "Tromso in polar night: the sun stays down",
            SundownCountdown.SunStaysDown,
            computeSundownCountdown(midwinterNoon, tromso, midwinterNoon, oneHour),
        )
    }

    @Test
    fun `the fix age is carried, and a missing fix time does not fabricate one`() {
        val now = londonSunset - 2 * oneHour
        val staleBy = 7 * 60 * 1000L
        val known = computeSundownCountdown(now, london, now - staleBy, oneHour) as SundownCountdown.Known
        assertEquals("age of the fix used", staleBy, known.fixAgeMillis)

        val withoutFixTime = computeSundownCountdown(now, london, null, oneHour) as SundownCountdown.Known
        assertEquals("no fix time means no claimed age", 0L, withoutFixTime.fixAgeMillis)
    }

    @Test
    fun `a fix timestamped in the future reports zero age rather than a negative one`() {
        val now = londonSunset - 2 * oneHour
        val known = computeSundownCountdown(now, london, now + 60_000L, oneHour) as SundownCountdown.Known
        assertEquals(0L, known.fixAgeMillis)
    }

    @Test
    fun `a margin longer than the day leaves the turnaround in the past, honestly`() {
        val now = londonSunset - 2 * oneHour
        val known = computeSundownCountdown(now, london, now, 48 * oneHour) as SundownCountdown.Known
        assertTrue("an absurd margin reads as already past, not as an error", known.isPastTurnaround)
        assertTrue("sunset itself is unaffected", known.millisUntilSunset > 0)
    }

    @Test
    fun `zero margin puts the turnaround exactly at sunset`() {
        val now = londonSunset - 2 * oneHour
        val known = computeSundownCountdown(now, london, now, 0L) as SundownCountdown.Known
        assertEquals(known.sunsetAtEpochMillis, known.turnaroundAtEpochMillis)
    }

    @Test
    fun `civil dusk may be absent without the rest being unavailable`() {
        // 62N on 21 June: the sun sets (minimum altitude about -4.6) but never reaches -6, so
        // there is a sunset and no civil dusk. The boundary is between 60.5N and 61N on this
        // date; 60N was tried first and does reach -6, which is why the latitude is checked
        // rather than assumed. The assertion is that this degrades to a missing second marker,
        // not to a missing countdown.
        val farNorth = LatLng(62.0, -1.0)
        val midJune = utcMillis(2026, 6, 21, 12)
        val state = computeSundownCountdown(midJune, farNorth, midJune, oneHour)
        val known = state as SundownCountdown.Known
        assertNull("no civil dusk at 62N in midsummer", known.civilDuskAtEpochMillis)
        assertTrue("but sunset is still known", known.sunsetAtEpochMillis > midJune)
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int): Long =
        java.time.LocalDateTime.of(year, month, day, hour, 0)
            .toInstant(java.time.ZoneOffset.UTC)
            .toEpochMilli()
}
