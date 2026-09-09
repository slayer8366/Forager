package com.forager.app.ui.availability

import com.forager.app.domain.LocationFix
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.Waypoint
import com.forager.app.domain.model.WaypointDesignation
import com.forager.app.ui.map.TrueHeadingReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        showDecimalDegrees: Boolean = false,
        pathHomeMeters: Double? = null,
    ) = navigationReadout(heading, liveFix, target, unit, now, showDecimalDegrees, pathHomeMeters)

    // ── Path-home join dispatch: the one more short string, and what it yields to ──────────

    @Test
    fun `path home fills the status line's empty state as one number in the display unit`() {
        assertEquals("Path home 0.9 mi", readout(pathHomeMeters = 1_500.0).statusText)
        assertEquals("Path home 1.5 km", readout(pathHomeMeters = 1_500.0, unit = DistanceUnit.KILOMETERS).statusText)
        // Below a quarter mile the unit is feet: 350 m is 1148.3 ft.
        assertEquals("Path home 1148 ft", readout(pathHomeMeters = 350.0).statusText)
        assertEquals("Path home 350 m", readout(pathHomeMeters = 350.0, unit = DistanceUnit.KILOMETERS).statusText)
        // The distance slot is untouched: still the straight line.
        assertEquals("0.7 mi", readout(pathHomeMeters = 1_500.0).distanceText)
    }

    @Test
    fun `path home yields to approaching, to a stale fix and to a lost fix`() {
        val close = north.copy(lat = 45.52009)
        assertEquals("Approaching", readout(target = close, pathHomeMeters = 12.0).statusText)
        assertEquals("Last fix 45 s ago", readout(now = t + 45_000L, pathHomeMeters = 1_500.0).statusText)
        assertEquals("No fix for 6 min", readout(now = t + 6L * 60L * 1_000L, pathHomeMeters = 1_500.0).statusText)
        assertEquals("", readout(pathHomeMeters = null).statusText)
    }

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
    fun `approaching inside twice the reported accuracy - never arrived, and the needle is not drawn`() {
        // 0.00009° of latitude is 10.0 m; accuracy 12.5 m → threshold 25 m.
        val close = north.copy(lat = 45.52009)

        val r = readout(target = close)

        assertEquals("Approaching", r.statusText)
        // Inside the error circle the distance is the accuracy — 12.5 m is 41.01 ft — never "33 ft"
        // as if the fix knew where you stood to the foot (location-accuracy dispatch, item 2).
        assertEquals("within 41 ft", r.distanceText)
        assertNull(r.targetArrowDegrees)
        // The target column shows nothing — no "Turn N°" (the same unstable bearing as the
        // needle), no dash, no placeholder, and not the distance either: a first cut put the
        // distance here and the owner read "9 ft · 9 ft" on device (navigation-chrome amendment).
        assertEquals("", r.targetText)
    }

    /**
     * The needle's boundary, pinned on both sides with literals computed *independently* of the
     * constant under test: with accuracy 8 m the threshold is 16 m. One degree of latitude on
     * `GeoDistance`'s own mean radius (6 371 008.8 m) is 2π·R/360 = 111 195.08 m, so 0.000140° is
     * 15.57 m and 0.000148° is 16.46 m — neither derived from `APPROACHING_ACCURACY_MULTIPLIER`.
     * Fails with the needle gate removed (a needle at 15.57 m) and with a second threshold that
     * drifts from the label's (a label at 15.57 m with the needle still drawn, or the reverse).
     */
    @Test
    fun `just inside the threshold the needle is absent and Approaching shows - just outside, the needle is present and it does not`() {
        val eightMetres = fix.copy(accuracyMeters = 8f)
        val inside = north.copy(lat = 45.52 + 0.000140)
        val outside = north.copy(lat = 45.52 + 0.000148)

        val r1 = readout(liveFix = eightMetres, target = inside)
        // 15.57 m is 51.08 ft; 8 m accuracy is 26.25 ft → step 50 ft → "≈ 50 ft". The needle claim
        // below is the point of this test; the distance string is item 2's rounding, pinned too.
        assertEquals("≈ 50 ft", r1.distanceText)
        assertNull("no needle at 15.57 m with 8 m accuracy", r1.targetArrowDegrees)
        assertEquals("Approaching", r1.statusText)
        assertEquals("", r1.targetText)

        val r2 = readout(liveFix = eightMetres, target = outside)
        // 16.46 m is 54.0 ft → same 50 ft step → "≈ 50 ft" as well: the rounding does not tell the
        // two sides apart, which is exactly its job; the needle does.
        assertEquals("≈ 50 ft", r2.distanceText)
        assertNotNull("a needle at 16.46 m with 8 m accuracy", r2.targetArrowDegrees)
        assertEquals(315f, r2.targetArrowDegrees!!, 1e-3f)
        assertEquals("", r2.statusText)
        assertEquals("Turn 315°", r2.targetText)
    }

    @Test
    fun `no reported accuracy - no basis for approaching, so the needle stays drawn even close in`() {
        val noAccuracy = fix.copy(accuracyMeters = null)
        val close = north.copy(lat = 45.52009)

        val r = readout(liveFix = noAccuracy, target = close)

        assertNotNull(r.targetArrowDegrees)
        assertEquals("", r.statusText)
        assertEquals("Turn 315°", r.targetText)
        // And the distance formats exactly as before item 2: no accuracy, no rounding, no marker.
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
    fun `stale and approaching - both facts on the status line, needle withheld`() {
        val close = north.copy(lat = 45.52009)

        val r = readout(target = close, now = t + 45_000L)

        assertEquals("Approaching · last fix 45 s ago", r.statusText)
        assertNull(r.targetArrowDegrees)
        assertEquals("", r.targetText)
        assertTrue(r.distanceDeEmphasised)
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
    fun `no sensor - heading says so, target shows neither needle nor bearing text`() {
        val r = readout(heading = TrueHeadingReading.NoSensor)

        assertEquals("Compass unavailable", r.headingText)
        assertNull(r.northArrowDegrees)
        // Was "Bearing 0° N" until the two-data-corrections dispatch (Part C, owner-authorised
        // change to this assertion): an absolute bearing the user cannot orient to is withheld,
        // matching the approach and unreliable cases.
        assertEquals("", r.targetText)
        assertNull(r.targetArrowDegrees)
        assertEquals("0.7 mi", r.distanceText)
    }

    @Test
    fun `no sensor and approaching - the absolute bearing text is withheld too`() {
        // An absolute bearing you cannot orient to is a number without a use, and this close in it
        // is the same unstable number the needle would have drawn (owner's call).
        val close = north.copy(lat = 45.52009)

        val r = readout(heading = TrueHeadingReading.NoSensor, target = close)

        assertEquals("", r.targetText)
        assertEquals("Approaching", r.statusText)
    }

    // ── Compass-reliability dispatch: the fourth state, and its precedence ──────────────────

    @Test
    fun `unreliable compass - the label says so, both arrows withheld, target text empty, distance untouched`() {
        val r = readout(heading = TrueHeadingReading.Unreliable)

        assertEquals("Compass unreliable", r.headingText)
        assertNull(r.northArrowDegrees)
        assertNull(r.targetArrowDegrees)
        assertEquals("", r.targetText)
        assertEquals("0.7 mi", r.distanceText)
        assertEquals("", r.statusText)
    }

    /** Lost fix wins over an unreliable compass: the position failure is the more fundamental. */
    @Test
    fun `precedence - lost fix with unreliable compass reads as lost`() {
        val r = readout(heading = TrueHeadingReading.Unreliable, now = t + 6L * 60L * 1_000L)

        assertEquals("Target", r.targetText)
        assertEquals("No fix for 6 min", r.statusText)
        assertEquals("—", r.distanceText)
        assertNull(r.targetArrowDegrees)
        assertEquals("Compass unreliable", r.headingText)
    }

    /** Unreliable compass with the approach threshold: both withhold the needle; the status still says Approaching (a fact about distance, not heading). */
    @Test
    fun `precedence - unreliable compass while approaching withholds the needle and keeps Approaching`() {
        val close = north.copy(lat = 45.52009)

        val r = readout(heading = TrueHeadingReading.Unreliable, target = close)

        assertNull(r.targetArrowDegrees)
        assertEquals("", r.targetText)
        assertEquals("Approaching", r.statusText)
        assertEquals("within 41 ft", r.distanceText)
    }

    @Test
    fun `unreliable compass with no fix - the no-fix message still carries the status line`() {
        val r = readout(heading = TrueHeadingReading.Unreliable, liveFix = null)

        assertEquals("Compass unreliable", r.headingText)
        assertEquals("Location services unavailable", r.statusText)
    }

    @Test
    fun `no fix yet for the compass - a dash, not a message of its own`() {
        // The status line carries the one no-fix message (below); the heading label does not repeat
        // it. "Compass needs a fix" is gone.
        assertEquals("—", readout(heading = TrueHeadingReading.NeedsFix, liveFix = null).headingText)
    }

    @Test
    fun `no origin waypoint - said plainly, nothing substituted`() {
        val r = readout(target = null)

        assertEquals("No origin waypoint for this track", r.statusText)
        assertEquals("—", r.distanceText)
        assertNull(r.targetArrowDegrees)
        // A fix exists, so the second row still has something true to say.
        assertEquals("50 m", r.elevationText)
    }

    @Test
    fun `no fix at all - one message on the status line, no elevation or coordinates row`() {
        val r = readout(liveFix = null)

        assertEquals("Location services unavailable", r.statusText)
        assertEquals("Target", r.targetText)
        assertNull(r.elevationText)
        assertNull(r.coordinatesText)
    }

    @Test
    fun `with a fix the second row carries the elevation and MGRS, decimal degrees on request`() {
        val r = readout()
        assertEquals("50 m", r.elevationText)
        // Pinned against MgrsConverterTest's own Portland point rather than this fix — same
        // converter, a value that test already fixes independently.
        val portland = fix.copy(lat = 45.5152, lng = -122.6784, altitude = null)
        val p = readout(liveFix = portland)
        assertEquals("Elevation unavailable", p.elevationText)
        assertEquals("10T ER 25118 40235", p.coordinatesText)
        assertEquals("Lat. 45.5152 Long. -122.6784", readout(liveFix = portland, showDecimalDegrees = true).coordinatesText)
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
