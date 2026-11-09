package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HopBand
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.RouteHome
import com.zynergylabs.forager.app.domain.RouteWithheldReason
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.routeHome
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.NavigationFacing
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
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
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 12.5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val north = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)

    private fun readout(
        heading: TrueHeadingReading = TrueHeadingReading.Available(45f),
        liveFix: LocationFix.Update? = fix,
        target: Waypoint? = north,
        unit: DistanceUnit = DistanceUnit.MILES,
        now: Long = t + 1_000L,
        showDecimalDegrees: Boolean = false,
        route: ReturnRoute? = null,
    ) = navigationReadout(heading, liveFix, target, unit, now, showDecimalDegrees, route)

    // ── Dispatch 2026-09-28-430, ruling E: the stuck-compass notice is the heading label ──────

    @Test
    fun `while a stuck compass is retried the heading label says so, and once north-up it says that`() {
        val calibrating = navigationReadout(TrueHeadingReading.Unreliable, fix, north, DistanceUnit.MILES, t + 1_000L, facing = NavigationFacing.CALIBRATING)
        assertEquals("Compass calibrating…", calibrating.headingText)
        assertNull("the needle is still withheld", calibrating.targetArrowDegrees)
        val northUp = navigationReadout(TrueHeadingReading.Unreliable, fix, north, DistanceUnit.MILES, t + 1_000L, facing = NavigationFacing.NORTH_UP)
        assertEquals("Compass unavailable · north up", northUp.headingText)
        val noSensor = navigationReadout(TrueHeadingReading.NoSensor, fix, north, DistanceUnit.MILES, t + 1_000L, facing = NavigationFacing.NORTH_UP)
        assertEquals("Compass unavailable · north up", noSensor.headingText)
        // Facing-up, the label is what it always was.
        assertEquals("45° NE", navigationReadout(TrueHeadingReading.Available(45f), fix, north, DistanceUnit.MILES, t + 1_000L, facing = NavigationFacing.FACING_UP).headingText)
        assertEquals("Compass unreliable", readout(heading = TrueHeadingReading.Unreliable).headingText)
    }

    // ── Dispatch 2026-09-28-423: the route home. These two replace the path-home join
    // dispatch's two tests of "Path home NNN" in the status line, which that dispatch retires: the
    // route figure moves to the large slot (decision D3, option B) and the straight line, labelled,
    // takes the status line. Same figures, same units, same yielding. ──────────────────────

    /** A lookahead due east of the fix: from a device facing 45°, a 45° turn. */
    private val east = LatLng(45.52, -122.679)

    @Test
    fun `the route figure fills the large slot as one number in the display unit, and the status line carries the straight line, labelled`() {
        assertEquals("0.9 mi", readout(route = ReturnRoute.Ahead(east, 1_500.0)).distanceText)
        assertEquals("1.5 km", readout(route = ReturnRoute.Ahead(east, 1_500.0), unit = DistanceUnit.KILOMETERS).distanceText)
        // Below a quarter mile the unit is feet: 350 m is 1148.3 ft.
        assertEquals("1148 ft", readout(route = ReturnRoute.Ahead(east, 350.0)).distanceText)
        assertEquals("350 m", readout(route = ReturnRoute.Ahead(east, 350.0), unit = DistanceUnit.KILOMETERS).distanceText)
        // RECORD -942 (the owner: "Remove the 0.7m straight since the ≈800ft ahead is saying the same thing"): on the route the
        // status line has no straight-line note. Was: "0.7 mi straight" and "1.1 km straight".
        assertEquals("", readout(route = ReturnRoute.Ahead(east, 1_500.0)).statusText)
        assertEquals("", readout(route = ReturnRoute.Ahead(east, 1_500.0), unit = DistanceUnit.KILOMETERS).statusText)
        // With no route figure yet, the note stays: nothing else says how far the start is.
        assertEquals("1.1 km straight", readout(route = ReturnRoute.Pending, unit = DistanceUnit.KILOMETERS).statusText)
    }

    @Test
    fun `the straight line yields to approaching, to a stale fix and to a lost fix, and is absent with no route given`() {
        // Dispatch 2026-09-28-578 (Amendment 1, -579) changed this. Before: `close` (10 m) asserted
        // "Approaching". 10 m has arrived (radius max(2 x 12.5, 15) = 25 m), and arrived the status line
        // is empty, "Arrived" alone; the yielding claim moves to a target 50 m off, inside the 100 m
        // approaching zone and not arrived.
        val close = north.copy(lat = 45.52009)
        val fiftyMetres = north.copy(lat = 45.52045)
        assertEquals("Approaching", readout(target = fiftyMetres, route = ReturnRoute.Ahead(east, 60.0)).statusText)
        assertEquals("", readout(target = close, route = ReturnRoute.Ahead(east, 12.0)).statusText)
        assertEquals("Last fix 45 s ago", readout(now = t + 45_000L, route = ReturnRoute.Ahead(east, 1_500.0)).statusText)
        assertEquals("No fix for 6 min", readout(now = t + 6L * 60L * 1_000L, route = ReturnRoute.Ahead(east, 1_500.0)).statusText)
        assertEquals("", readout(route = null).statusText)
    }

    /**
     * The straight-line mode, held by a test named for it (the planner's ruling on -423): decision
     * D2 keeps the straight line for navigating to a waypoint, where there is no walked route to
     * follow. With no route given, the large slot is the straight line, the needle aims at the
     * waypoint itself, and the status line says nothing more. Nothing passes the HUD this way
     * today; the return HUD always passes a route.
     */
    @Test
    fun `navigating to a waypoint with no route keeps the straight line in the large slot and aims at the waypoint`() {
        val r = readout(route = null)
        assertEquals("0.7 mi", r.distanceText)
        assertEquals("left 45°", r.targetText)
        assertEquals("", r.statusText)
        assertFalse(r.routeRetryOffered)
    }

    @Test
    fun `the needle aims at the route's lookahead, not at the start`() {
        // The start is due north (a 315° turn from 45°); the lookahead due east (a 45° turn).
        val r = readout(route = ReturnRoute.Ahead(east, 1_500.0))
        assertEquals("right 45°", r.targetText)
        assertEquals(45f, r.targetArrowDegrees!!, 0.5f)
        assertEquals("left 45°", readout(route = null).targetText)
    }

    @Test
    fun `before the first route result the large slot is a dash, there is no needle, and the straight line is in the status line`() {
        val r = readout(route = ReturnRoute.Pending)
        assertEquals("—", r.distanceText)
        assertNull(r.targetArrowDegrees)
        assertEquals("", r.targetText)
        assertEquals("0.7 mi straight", r.statusText)
        assertFalse(r.routeRetryOffered)
    }

    @Test
    fun `a route withheld off the route says so in the large slot, with no needle, and offers Try again`() {
        val r = readout(route = ReturnRoute.Unavailable(canRetry = true))
        assertEquals("Unable to calculate route", r.distanceText)
        assertNull(r.targetArrowDegrees)
        assertEquals("", r.targetText)
        assertEquals("0.7 mi straight", r.statusText)
        assertTrue(r.routeRetryOffered)
    }

    @Test
    fun `a route withheld for no usable points says so and offers no Try again`() {
        val r = readout(route = ReturnRoute.Unavailable(canRetry = false))
        assertEquals("Unable to calculate route", r.distanceText)
        assertNull(r.targetArrowDegrees)
        assertFalse(r.routeRetryOffered)
    }

    @Test
    fun `with the fix lost the large slot is a dash and nothing is offered, whatever the route`() {
        val lost = t + 6L * 60L * 1_000L
        listOf(ReturnRoute.Pending, ReturnRoute.Ahead(east, 1_500.0), ReturnRoute.Unavailable(canRetry = true)).forEach { route ->
            val r = readout(now = lost, route = route)
            assertEquals("$route", "—", r.distanceText)
            assertNull("$route", r.targetArrowDegrees)
            assertFalse("$route", r.routeRetryOffered)
        }
    }

    @Test
    fun `a stale fix de-emphasises the route figure as it did the straight line`() {
        val r = readout(now = t + 45_000L, route = ReturnRoute.Ahead(east, 1_500.0))
        assertEquals("0.9 mi", r.distanceText)
        assertTrue(r.distanceDeEmphasised)
    }

    @Test
    fun `routeHome results map to what the HUD shows, from real searches`() {
        // A straight track north, 0.001° (111 m) a leg; the walker on its last point.
        val points = listOf(45.520, 45.521, 45.522).mapIndexed { i, lat ->
            TrackPoint(lat = lat, lng = -122.68, altitude = null, accuracyMeters = 5f, timestampEpochMillis = t + i * 15_000L)
        }
        val track = Track(id = "t1", name = "", startedAtEpochMillis = t, endedAtEpochMillis = null, points = points)
        val ahead = routeHome(track, LatLng(45.522, -122.68), origin = null) as RouteHome.Ahead
        assertEquals(ReturnRoute.Ahead(ahead.lookahead, ahead.routeMeters), returnRouteOf(ahead))
        // 0.001° of longitude at 45.52° N is about 78 m: the far band, off the route.
        val off = routeHome(track, LatLng(45.522, -122.679), origin = null)
        assertEquals(RouteWithheldReason.OFF_ROUTE, (off as RouteHome.Withheld).reason)
        assertEquals(ReturnRoute.Unavailable(canRetry = true), returnRouteOf(off))
        val empty = routeHome(track.copy(points = emptyList()), LatLng(45.522, -122.68), origin = null)
        assertEquals(RouteWithheldReason.NO_USABLE_POINTS, (empty as RouteHome.Withheld).reason)
        assertEquals(ReturnRoute.Unavailable(canRetry = false), returnRouteOf(empty))
        assertEquals(ReturnRoute.Pending, returnRouteOf(null))
        assertEquals(HopBand.FAR, off.hopBand)
    }

    @Test
    fun `facing north-east with the target due north - turn 315, distance 0 point 7 miles, nothing else to say`() {
        val r = readout()

        assertEquals("45° NE", r.headingText)
        assertEquals(-45f, r.northArrowDegrees)
        assertEquals("left 45°", r.targetText)
        assertEquals(315f, r.targetArrowDegrees!!, 1e-3f)
        assertEquals("0.7 mi", r.distanceText)
        assertFalse(r.distanceDeEmphasised)
        assertEquals("", r.statusText)
    }

    @Test
    fun `a target due west of a user facing north-east is a 225 degree turn`() {
        val west = north.copy(lat = 45.52, lng = -122.70)

        val r = readout(target = west)

        assertEquals("sharp left 135°", r.targetText)
        assertEquals(225f, r.targetArrowDegrees!!, 0.05f)
    }

    @Test
    fun `kilometres when that is the display unit`() {
        assertEquals("1.1 km", readout(unit = DistanceUnit.KILOMETERS).distanceText)
    }

    @Test
    fun `inside twice the reported accuracy - arrived, the status line is empty and the needle is not drawn`() {
        // 0.00009° of latitude is 10.0 m; accuracy 12.5 m → threshold 25 m.
        val close = north.copy(lat = 45.52009)

        val r = readout(target = close)

        // Dispatch 2026-09-28-578 (Amendment 1, -579) changed this. Before: the name began "approaching
        // inside twice the reported accuracy" and the status line was asserted to read "Approaching".
        // After: arrived, "Arrived" stands alone (the owner: "Drop "Approaching" when arrived").
        assertEquals("", r.statusText)
        // Dispatch -502 changed this (Amendment 2). Before: the name ended "- never arrived, and the
        // needle is not drawn", and the large slot was asserted to read "within 41 ft" (inside the error
        // circle the distance is the accuracy, 12.5 m being 41.01 ft; FormatDistanceMetersTest still pins
        // that string). After: arrival applies to any target, and arrival's radius, max(2 x 12.5, 15) =
        // 25 m, holds every approaching point, so the large slot reads "Arrived".
        assertEquals(ARRIVED_TEXT, r.distanceText)
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
     * Fails with the needle gate removed (a needle at 15.57 m).
     *
     * Dispatch 2026-09-28-578 (Amendment 1, -579) changed this test. Before: the name ended "and
     * Approaching shows - just outside, the needle is present and it does not", and the doc said the
     * label and the needle shared this one threshold. After: "Approaching" has its own zone, 100 m
     * and not arrived (the owner: "Give Approaching its own zone", "100 m / 330 ft"), so 15.57 m, which
     * has arrived (16 m), has an empty status line, and 16.46 m, which has not, reads "Approaching"
     * with the needle still drawn. The needle keeps the twice-accuracy threshold; the label no longer
     * reads it.
     */
    @Test
    fun `just inside the threshold the needle is absent - just outside, the needle is present and Approaching shows with it`() {
        val eightMetres = fix.copy(accuracyMeters = 8f)
        val inside = north.copy(lat = 45.52 + 0.000140)
        val outside = north.copy(lat = 45.52 + 0.000148)

        val r1 = readout(liveFix = eightMetres, target = inside)
        // Dispatch -502 changed this (Amendment 2). Before: the large slot was asserted to read "≈ 50 ft"
        // (15.57 m is 51.08 ft; 8 m accuracy is 26.25 ft → step 50 ft). After: arrival applies to any
        // target, and 15.57 m is inside arrival's max(2 x 8, 15) = 16 m, so it reads "Arrived". The needle
        // claim below is the point of this test and is unchanged.
        assertEquals(ARRIVED_TEXT, r1.distanceText)
        assertNull("no needle at 15.57 m with 8 m accuracy", r1.targetArrowDegrees)
        assertEquals("", r1.statusText)
        assertEquals("", r1.targetText)

        val r2 = readout(liveFix = eightMetres, target = outside)
        // 16.46 m is 54.0 ft → same 50 ft step → "≈ 50 ft" as well: the rounding does not tell the
        // two sides apart, which is exactly its job; the needle does.
        assertEquals("≈ 50 ft", r2.distanceText)
        assertNotNull("a needle at 16.46 m with 8 m accuracy", r2.targetArrowDegrees)
        assertEquals(315f, r2.targetArrowDegrees!!, 1e-3f)
        assertEquals("Approaching", r2.statusText)
        assertEquals("left 45°", r2.targetText)
    }

    // ── Dispatch 2026-09-28-578, Amendment 1 (-579): "Approaching" has its own zone ─────────────
    // The owner: "Give Approaching its own zone", then "100 m / 330 ft (Recommended)". Within 100 m and
    // not arrived: the distance counting down, the needle still pointing, "Approaching" under it.
    // Arrived: "Arrived" alone. Distances use GeoDistance's 111 195.08 m per degree of latitude.

    @Test
    fun `inside the approaching zone and not arrived - the distance counts down, the needle points, Approaching under it`() {
        val fiftyMetres = north.copy(lat = 45.52045) // 50.04 m; arrival is max(2 x 12.5, 15) = 25 m

        val r = readout(target = fiftyMetres, unit = DistanceUnit.KILOMETERS)

        assertEquals("Approaching", r.statusText)
        assertEquals("≈ 50 m", r.distanceText)
        assertEquals(315f, r.targetArrowDegrees!!, 1e-3f)
        assertEquals("left 45°", r.targetText)
        assertFalse(r.distanceDeEmphasised)
    }

    @Test
    fun `the approaching zone ends at 100 m - 99 m shows Approaching, 101 m does not`() {
        val inside = north.copy(lat = 45.52 + 0.000891) // 99.07 m
        val outside = north.copy(lat = 45.52 + 0.000909) // 101.08 m

        assertEquals("Approaching", readout(target = inside).statusText)
        assertEquals("", readout(target = outside).statusText)
        // Under a route the zone is the same; past it the line is empty (RECORD -942: no straight-line note on the route). Was:
        // the straight line past the zone.
        assertEquals("Approaching", readout(target = inside, route = ReturnRoute.Ahead(east, 120.0)).statusText)
        assertEquals("", readout(target = outside, route = ReturnRoute.Ahead(east, 120.0)).statusText)
    }

    /**
     * Arrived with a route, outside twice the accuracy (12 m with 3.79 m accuracy, the S22's constant:
     * arrived within 15 m, outside 7.58 m): before this dispatch the status line read "Straight line …"
     * under "Arrived". After, "Arrived" alone, the owner's words.
     */
    @Test
    fun `arrived with a route outside twice the accuracy - Arrived alone, no straight line under it`() {
        val s22 = fix.copy(accuracyMeters = 3.79f)
        val twelveMetres = north.copy(lat = 45.52 + 0.000108) // 12.01 m: arrived (15 m), outside 7.58 m

        val r = readout(liveFix = s22, target = twelveMetres, route = ReturnRoute.Ahead(east, 14.0))

        assertEquals(ARRIVED_TEXT, r.distanceText)
        assertEquals("", r.statusText)
        // The needle keeps today's twice-accuracy threshold: outside 7.58 m it is drawn.
        assertNotNull(r.targetArrowDegrees)
    }

    @Test
    fun `no reported accuracy - no basis for approaching, so the needle stays drawn even close in`() {
        val noAccuracy = fix.copy(accuracyMeters = null)
        val close = north.copy(lat = 45.52009)

        val r = readout(liveFix = noAccuracy, target = close)

        assertNotNull(r.targetArrowDegrees)
        assertEquals("", r.statusText)
        assertEquals("left 45°", r.targetText)
        // Dispatch -502 changed this (Amendment 2). Before: the large slot was asserted to read "33 ft" (no
        // accuracy, no rounding, no marker). After: arrival applies to any target, and with no accuracy its
        // radius is 15 m, so 10 m reads "Arrived". The needle, drawn with no basis for approaching, is unchanged.
        assertEquals(ARRIVED_TEXT, r.distanceText)
    }

    /**
     * Dispatch 2026-09-28-578 (Amendment 1, -579): the test above asserts an empty status line at 10 m
     * with no accuracy, and that is true for two reasons (no accuracy, no basis for the old
     * "Approaching"; and arrived, "Arrived" alone), so it cannot tell them apart. This case can: 50 m
     * with no accuracy has not arrived (radius 15 m) and is inside the 100 m zone, which is a fixed
     * distance and needs no accuracy, so "Approaching" shows; the needle, which still needs an
     * accuracy to withhold on, stays drawn.
     */
    @Test
    fun `no reported accuracy, 50 m off - not arrived, inside the zone, so Approaching shows and the needle stays drawn`() {
        val noAccuracy = fix.copy(accuracyMeters = null)
        val fiftyMetres = north.copy(lat = 45.52045)

        val r = readout(liveFix = noAccuracy, target = fiftyMetres)

        assertEquals("Approaching", r.statusText)
        assertEquals("164 ft", r.distanceText)
        assertEquals(315f, r.targetArrowDegrees!!, 1e-3f)
        assertEquals("left 45°", r.targetText)
    }

    @Test
    fun `a fix 45 seconds old is stale - distance kept but de-emphasised, age shown`() {
        val r = readout(now = t + 45_000L)

        assertEquals("0.7 mi", r.distanceText)
        assertTrue(r.distanceDeEmphasised)
        assertEquals("Last fix 45 s ago", r.statusText)
        assertEquals(315f, r.targetArrowDegrees!!, 1e-3f)
    }

    /**
     * Dispatch 2026-09-28-578 (Amendment 1, -579) changed this test. Before: named "stale and
     * approaching - both facts on the status line, needle withheld", asserting "Approaching · last fix
     * 45 s ago". 10 m with 12.5 m accuracy has arrived (25 m), and arrived, "Approaching" is dropped:
     * the S22 combined check (-571) saw "Arrived" over "Approaching · last fix 2 min ago". Stale and in
     * the zone without arriving is the next test.
     */
    @Test
    fun `stale and arrived - only the fix's age on the status line, needle withheld`() {
        val close = north.copy(lat = 45.52009)

        val r = readout(target = close, now = t + 45_000L)

        assertEquals(ARRIVED_TEXT, r.distanceText)
        assertEquals("Last fix 45 s ago", r.statusText)
        assertNull(r.targetArrowDegrees)
        assertEquals("", r.targetText)
        assertTrue(r.distanceDeEmphasised)
    }

    /** Dispatch 2026-09-28-578 (Amendment 1, -579): stale, inside the 100 m zone and not arrived, both facts show, and the needle still points. */
    @Test
    fun `stale and approaching, not arrived - both facts on the status line, needle drawn`() {
        val fiftyMetres = north.copy(lat = 45.52045)

        val r = readout(target = fiftyMetres, now = t + 45_000L, unit = DistanceUnit.KILOMETERS)

        assertEquals("Approaching · last fix 45 s ago", r.statusText)
        // Dispatch 2026-09-28-685, Amendment 1 (RECORD -694): its short form, and which one a width gets.
        assertEquals("Last fix 45 s ago", r.statusShortText)
        assertEquals("Approaching · last fix 45 s ago", statusTextThatFits(r, maxWidthPx = 100) { it.length })
        assertEquals("Last fix 45 s ago", statusTextThatFits(r, maxWidthPx = 30) { it.length })
        assertEquals("≈ 50 m", r.distanceText)
        assertTrue(r.distanceDeEmphasised)
        assertEquals(315f, r.targetArrowDegrees!!, 1e-3f)
        assertEquals("left 45°", r.targetText)
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
        // Dispatch 2026-09-28-578 (Amendment 1, -579) changed this. Before: "Approaching". 10 m has
        // arrived (25 m), and arrived the status line is empty, "Arrived" alone.
        assertEquals("", r.statusText)
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

    /**
     * Unreliable compass with the approach threshold: both withhold the needle; the status line is a
     * fact about distance, not heading, so the compass does not change it. Dispatch 2026-09-28-578
     * (Amendment 1, -579) changed this test. Before: named "… withholds the needle and keeps
     * Approaching", asserting "Approaching" at 10 m. 10 m has arrived (25 m), so the status line is
     * empty; the 50 m case, inside the zone and not arrived, keeps "Approaching" under an unreliable
     * compass.
     */
    @Test
    fun `precedence - unreliable compass while arrived withholds the needle, and inside the zone Approaching still shows`() {
        val close = north.copy(lat = 45.52009)

        val r = readout(heading = TrueHeadingReading.Unreliable, target = close)

        assertNull(r.targetArrowDegrees)
        assertEquals("", r.targetText)
        assertEquals("", r.statusText)

        val zone = readout(heading = TrueHeadingReading.Unreliable, target = north.copy(lat = 45.52045))
        assertNull(zone.targetArrowDegrees)
        assertEquals("Approaching", zone.statusText)
        // Dispatch -502 changed this (Amendment 2). Before: "within 41 ft". After: arrival applies to any
        // target, and 10 m is inside arrival's max(2 x 12.5, 15) = 25 m, so the large slot reads "Arrived".
        assertEquals(ARRIVED_TEXT, r.distanceText)
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
        // A fix exists, so the second row still has something true to say. In feet: this file's
        // unit is miles, and the elevation follows Units (dispatch 2026-09-28-549; 50 / 0.3048 = 164.04).
        assertEquals("164 ft", r.elevationText)
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
        // Dispatch 2026-09-28-549: miles here, so feet (50 / 0.3048 = 164.04); metres under metric, below.
        assertEquals("164 ft", r.elevationText)
        // Pinned against MgrsConverterTest's own Portland point rather than this fix — same
        // converter, a value that test already fixes independently.
        val portland = fix.copy(lat = 45.5152, lng = -122.6784, altitude = null)
        val p = readout(liveFix = portland)
        assertEquals("Elevation unavailable", p.elevationText)
        assertEquals("10T ER 25118 40235", p.coordinatesText)
        // Dispatch 2026-09-28-549: under metric the elevation reads exactly as before.
        assertEquals("50 m", readout(unit = DistanceUnit.KILOMETERS).elevationText)
        assertEquals("Elevation unavailable", readout(liveFix = portland, unit = DistanceUnit.KILOMETERS).elevationText)
        assertEquals("45.5152, -122.6784", readout(liveFix = portland, showDecimalDegrees = true).coordinatesText)
    }

    // ── Dispatch 2026-09-28-677 (data part B): words, kinds and labels ──────────────────────────

    @Test
    fun `the large figure says what it measures - straight with no route, by trail on the route, nothing for words or a dash`() {
        assertEquals("straight", readout(route = null).distanceKindText)
        assertEquals("by trail", readout(route = ReturnRoute.Ahead(east, 1_500.0)).distanceKindText)
        assertNull("a dash before the first route", readout(route = ReturnRoute.Pending).distanceKindText)
        assertNull("words, not a figure", readout(route = ReturnRoute.Unavailable(canRetry = true)).distanceKindText)
        assertNull("a lost fix shows a dash", readout(now = t + 6L * 60L * 1_000L, route = null).distanceKindText)
        // 10 m off, arrived (radius max(2 x 12.5, 15) = 25 m): "Arrived" alone.
        val close = north.copy(lat = 45.52009)
        assertEquals("Arrived", readout(target = close).distanceText)
        assertNull(readout(target = close).distanceKindText)
        // A stale fix still shows its figure, dimmed, so it still says what it is.
        assertEquals("straight", readout(now = t + 45_000L, route = null).distanceKindText)
    }

    @Test
    fun `a heading reading wears the label, a status does not`() {
        assertTrue(readout().headingIsReading)
        assertTrue("the dash while waiting is a reading too", readout(heading = TrueHeadingReading.NeedsFix).headingIsReading)
        assertFalse(readout(heading = TrueHeadingReading.NoSensor).headingIsReading)
        assertFalse(readout(heading = TrueHeadingReading.Unreliable).headingIsReading)
        val calibrating = navigationReadout(TrueHeadingReading.Available(45f), fix, north, DistanceUnit.MILES, t + 1_000L, facing = NavigationFacing.CALIBRATING)
        assertFalse("a facing notice names itself", calibrating.headingIsReading)
    }

    @Test
    fun `the second row shows with a fix, and with none only when the heading has more than a dash to say`() {
        assertTrue(readout().secondRowShown)
        assertFalse("no fix and the dash: the status line says it once", readout(heading = TrueHeadingReading.NeedsFix, liveFix = null).secondRowShown)
        assertTrue("no fix, no sensor: the row says Compass unavailable", readout(heading = TrueHeadingReading.NoSensor, liveFix = null).secondRowShown)
        assertNull(readout(heading = TrueHeadingReading.NoSensor, liveFix = null).coordinatesText)
    }

    @Test
    fun `the turn is in words from the needle's own angle`() {
        // Facing 45°, the start due north: 315° clockwise, 45° to the left; the arrow's rotation is unchanged.
        val r = readout(route = null)
        assertEquals("left 45°", r.targetText)
        assertEquals(315f, r.targetArrowDegrees!!, 0.5f)
        // Facing 350°, the start due north: 10° clockwise, a slight right.
        assertEquals("slight right 10°", readout(heading = TrueHeadingReading.Available(350f), route = null).targetText)
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
