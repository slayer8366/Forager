package com.forager.app.ui.availability

import com.forager.app.domain.LocationFix
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.Waypoint
import com.forager.app.domain.model.WaypointDesignation
import com.forager.app.ui.map.TrueHeadingReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The HUD's pure half — every string and rotation, pinned. The fix sits at 45.52 N, 122.68 W;
 * the "north" target is 0.01° of latitude due north of it, 1112 m (0.691 mi) away.
 */
class NavigationHudReadoutTest {

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 12.5f, timestampEpochMillis = t)
    private val north = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)

    private fun readout(
        heading: TrueHeadingReading = TrueHeadingReading.Available(45f),
        liveFix: LocationFix.Update? = fix,
        target: Waypoint? = north,
        unit: DistanceUnit = DistanceUnit.MILES,
        now: Long = t + 1_000L,
    ) = navigationReadout(heading, liveFix, target, unit, now)

    @Test
    fun `facing north-east with the target due north - turn 315, distance 0 point 7 miles, nothing else to say`() {
        val r = readout()

        assertEquals("45° NE", r.headingText)
        assertEquals(-45f, r.northArrowDegrees)
        assertEquals("Turn 315°", r.targetText)
        assertEquals(315f, r.targetArrowDegrees!!, 1e-3f)
        assertEquals("0.7 mi", r.distanceText)
        assertFalse(r.distanceDeEmphasised)
        assertEquals("", r.statusText)
    }

    @Test
    fun `a target due west of a user facing north-east is a 225 degree turn`() {
        val west = north.copy(lat = 45.52, lng = -122.70)

        val r = readout(target = west)

        assertEquals("Turn 225°", r.targetText)
        assertEquals(225f, r.targetArrowDegrees!!, 0.05f)
    }

    @Test
    fun `kilometres when that is the display unit`() {
        assertEquals("1.1 km", readout(unit = DistanceUnit.KILOMETERS).distanceText)
    }

    @Test
    fun `approaching inside twice the reported accuracy - never arrived`() {
        // 0.00009° of latitude is 10.0 m; accuracy 12.5 m → threshold 25 m.
        val close = north.copy(lat = 45.52009)

        val r = readout(target = close)

        assertEquals("Approaching", r.statusText)
        assertEquals("33 ft", r.distanceText)
    }

    @Test
    fun `a fix 45 seconds old is stale - distance kept but de-emphasised, age shown`() {
        val r = readout(now = t + 45_000L)

        assertEquals("0.7 mi", r.distanceText)
        assertTrue(r.distanceDeEmphasised)
        assertEquals("Last fix 45 s ago", r.statusText)
        assertEquals(315f, r.targetArrowDegrees!!, 1e-3f)
    }

    @Test
    fun `a fix 6 minutes old is lost - distance and needle withheld, only the age remains`() {
        val r = readout(now = t + 6L * 60L * 1_000L)

        assertEquals("—", r.distanceText)
        assertNull(r.targetArrowDegrees)
        assertEquals("Target", r.targetText)
        assertEquals("No fix for 6 min", r.statusText)
    }

    @Test
    fun `no sensor - heading says so, target falls back to the absolute true bearing as text, no needle`() {
        val r = readout(heading = TrueHeadingReading.NoSensor)

        assertEquals("Compass unavailable", r.headingText)
        assertNull(r.northArrowDegrees)
        assertEquals("Bearing 0° N", r.targetText)
        assertNull(r.targetArrowDegrees)
        assertEquals("0.7 mi", r.distanceText)
    }

    @Test
    fun `no fix yet for the compass - needs a fix, not magnetic`() {
        assertEquals("Compass needs a fix", readout(heading = TrueHeadingReading.NeedsFix).headingText)
    }

    @Test
    fun `no origin waypoint - said plainly, nothing substituted`() {
        val r = readout(target = null)

        assertEquals("No origin waypoint for this track", r.statusText)
        assertEquals("—", r.distanceText)
        assertNull(r.targetArrowDegrees)
    }

    @Test
    fun `no fix at all - waiting`() {
        assertEquals("Waiting for a fix", readout(liveFix = null).statusText)
    }

    @Test
    fun `fix age reads seconds under a minute and whole minutes from a minute on`() {
        assertEquals("48 s", formatFixAge(48_000L))
        assertEquals("59 s", formatFixAge(59_999L))
        assertEquals("1 min", formatFixAge(60_000L))
        assertEquals("6 min", formatFixAge(372_000L))
        assertEquals("0 s", formatFixAge(-5_000L))
    }
}
