package com.zynergylabs.forager.app.ui.availability

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Directions
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-09-28-497 (plan task T7) with Amendment 1: "Arrived" in the HUD's large slot while
 * returning, within max(2 x accuracy, 15 m) of the start on a fix that is not lost, by the one rule
 * the map also reads ([arrivedAtStart]); and the Return control's X in a circle while navigating.
 * Since dispatch 2026-09-28-502, "Arrived" reads for a waypoint navigation as well, by the same rule.
 */
class NavigationHudArrivedTest {

    private val t = 1_700_000_000_000L
    private val start = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)

    /** A fix [metersNorth] metres north of the start, with the S22's constant 3.79 m accuracy. */
    private fun fixAt(metersNorth: Double, at: Long = t) =
        LocationFix.Update(lat = start.lat + metersNorth / 111_195.08, lng = start.lng, altitude = null, accuracyMeters = 3.79f, timestampEpochMillis = at, provider = FixProvider.GPS)

    private val route = ReturnRoute.Ahead(LatLng(start.lat, start.lng), 12.0)

    private fun readout(fix: LocationFix.Update, route: ReturnRoute?, now: Long = t + 1_000L) =
        navigationReadout(TrueHeadingReading.Available(0f), fix, start, DistanceUnit.MILES, now, route = route)

    @Test
    fun `returning, within 15 m of the start, the large figure reads Arrived`() {
        assertEquals(ARRIVED_TEXT, readout(fixAt(12.0), route).distanceText)
        assertTrue(arrivedAtStart(fixAt(12.0), start, t + 1_000L))
    }

    @Test
    fun `returning, beyond 15 m, the large figure is still the route distance`() {
        assertNotEquals(ARRIVED_TEXT, readout(fixAt(18.0), route).distanceText)
        assertFalse(arrivedAtStart(fixAt(18.0), start, t + 1_000L))
    }

    @Test
    fun `navigating to a waypoint, with no route, within 15 m of it the large figure reads Arrived too`() {
        // Dispatch -502 changed this. Before: named "navigating to anything but the start never reads
        // Arrived", it asserted that with no route (navigating to a waypoint) a fix 5 m away did not read
        // "Arrived". After: the owner chose arrival "Same as the start" for a waypoint, and Amendment 1 drops
        // the HUD's dependence on a route for "Arrived", with no arrival flag, so it asserts that it does.
        assertEquals("no route: a waypoint navigation", ARRIVED_TEXT, readout(fixAt(5.0), route = null).distanceText)
    }

    @Test
    fun `a lost fix never reads Arrived`() {
        val old = fixAt(5.0, at = t)
        val tenMinutesLater = t + 10 * 60_000L
        assertNotEquals(ARRIVED_TEXT, readout(old, route, now = tenMinutesLater).distanceText)
        assertFalse(arrivedAtStart(old, start, tenMinutesLater))
    }

    @Test
    fun `the Return control is an X in a circle while navigating, its own icon otherwise`() {
        assertEquals(Icons.Filled.Cancel, returnControlIcon(isReturning = true))
        assertEquals(Icons.Filled.Directions, returnControlIcon(isReturning = false))
        assertEquals("Stop navigating", returnControlDescription(isReturning = true, isRecording = true, info = null, distanceUnit = DistanceUnit.MILES))
        assertNotEquals("Stop navigating", returnControlDescription(isReturning = false, isRecording = true, info = null, distanceUnit = DistanceUnit.MILES))
    }
}
