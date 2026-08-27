package com.forager.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Checks [CivilTwilight] against solar geometry that can be worked out independently, rather than
 * against numbers this implementation produced.
 *
 * The solstice cases are the useful ones: at solar noon the sun's altitude is
 * `90° − |latitude − declination|`, and declination is ±23.44° at the solstices. That is arithmetic
 * anyone can redo on paper, so the expected values below are derived rather than recorded, and a
 * regression cannot be "fixed" by pasting in whatever the code now returns.
 */
class CivilTwilightTest {

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val london = 51.5074 to -0.1278
    private val tromso = 69.6492 to 18.9553
    private val tolerance = 0.6

    @Test
    fun `London solar noon at the solstices matches 90 minus latitude plus or minus the tilt`() {
        val (lat, lon) = london
        // Solar noon in London is close to 12:00 UTC (longitude is within a tenth of a degree of
        // the prime meridian); the equation of time shifts it by minutes, which moves altitude by
        // well under the tolerance here.
        val midsummer = CivilTwilight.sunAltitudeDegrees(utc(2026, 6, 21, 12), lat, lon)
        assertEquals(90.0 - lat + 23.44, midsummer, tolerance)

        val midwinter = CivilTwilight.sunAltitudeDegrees(utc(2026, 12, 21, 12), lat, lon)
        assertEquals(90.0 - lat - 23.44, midwinter, tolerance)
    }

    @Test
    fun `the sun is below the horizon at London midnight in midsummer, but not by much`() {
        val (lat, lon) = london
        val altitude = CivilTwilight.sunAltitudeDegrees(utc(2026, 6, 21, 0), lat, lon)
        // Antipodal to solar noon: -(90 - lat + 23.44) reflected, i.e. lat + 23.44 - 90.
        assertEquals(lat + 23.44 - 90.0, altitude, tolerance)
        assertTrue("London midsummer midnight should be night", CivilTwilight.isNight(utc(2026, 6, 21, 0), lat, lon))
    }

    @Test
    fun `noon and midnight are on opposite sides of the threshold in London in winter`() {
        val (lat, lon) = london
        assertFalse("winter noon is daylight", CivilTwilight.isNight(utc(2026, 12, 21, 12), lat, lon))
        assertTrue("winter midnight is night", CivilTwilight.isNight(utc(2026, 12, 21, 0), lat, lon))
    }

    /**
     * The case that motivated computing altitude rather than sunrise and sunset times. During
     * polar night the sun never crosses the horizon, so there are no sunrise or sunset times to
     * fall between — but there is still real light around midday, and the map must not black out.
     */
    @Test
    fun `Tromso in polar night is not night mode at midday, though the sun never rises`() {
        val (lat, lon) = tromso
        val middayAltitude = CivilTwilight.sunAltitudeDegrees(utc(2026, 12, 21, 11), lat, lon)
        assertTrue("the sun should be below the horizon: was $middayAltitude", middayAltitude < 0.0)
        assertTrue(
            "but above the civil-twilight threshold, so still usable light: was $middayAltitude",
            middayAltitude > CivilTwilight.NIGHT_ALTITUDE_DEGREES,
        )
        assertFalse(CivilTwilight.isNight(utc(2026, 12, 21, 11), lat, lon))
    }

    @Test
    fun `Tromso in polar day is never night, including at local midnight`() {
        val (lat, lon) = tromso
        for (hour in 0..23) {
            assertFalse(
                "hour $hour of the midnight sun should not be night mode",
                CivilTwilight.isNight(utc(2026, 6, 21, hour), lat, lon),
            )
        }
    }

    @Test
    fun `longitude shifts the daily cycle by four minutes per degree`() {
        // Same latitude and instant, 90 degrees apart: six hours of rotation, so one place near
        // solar noon puts the other near sunrise or sunset.
        val atNoon = CivilTwilight.sunAltitudeDegrees(utc(2026, 3, 20, 12), 0.0, 0.0)
        val quarterTurnEast = CivilTwilight.sunAltitudeDegrees(utc(2026, 3, 20, 12), 0.0, 90.0)
        assertTrue("equator equinox noon should be near overhead: was $atNoon", atNoon > 85.0)
        assertTrue("90 degrees east should be near the horizon: was $quarterTurnEast", kotlin.math.abs(quarterTurnEast) < 5.0)
    }

    @Test
    fun `instants before the epoch do not put the sun on the wrong side of the sky`() {
        // Guards the mod-versus-remainder trap: `%` would return a negative hour angle here.
        val (lat, lon) = london
        val altitude = CivilTwilight.sunAltitudeDegrees(utc(1969, 6, 21, 12), lat, lon)
        assertEquals(90.0 - lat + 23.44, altitude, tolerance)
    }

    @Test
    fun `the threshold is civil twilight, not sunset`() {
        assertEquals(-6.0, CivilTwilight.NIGHT_ALTITUDE_DEGREES, 0.0)
        // A sun a degree below the horizon is dusk, not night: the map should still be in day mode.
        val (lat, lon) = london
        var foundDuskStillDay = false
        for (minute in 0 until 24 * 60 step 5) {
            val at = utc(2026, 9, 22, 0) + minute * 60_000L
            val altitude = CivilTwilight.sunAltitudeDegrees(at, lat, lon)
            if (altitude < 0.0 && altitude > -5.0) {
                assertFalse(
                    "sun at $altitude degrees is civil twilight, which is not night mode",
                    CivilTwilight.isNight(at, lat, lon),
                )
                foundDuskStillDay = true
            }
        }
        assertTrue("the sweep should have passed through civil twilight at an equinox", foundDuskStillDay)
    }
}
