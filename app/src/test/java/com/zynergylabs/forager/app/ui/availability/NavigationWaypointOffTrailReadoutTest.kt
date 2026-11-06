package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.wayhome.OffTrailState
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The quiet off-trail notice on the way to a waypoint (step 6 PR 4, dispatch 2026-10-27-01; RECORD -1725, the owner:
 * "Off trail · Head back to the line" (Recommended), short form "Off trail"), beside the way home's, which is unchanged.
 * "Back on trail" is the same words for both.
 */
class NavigationWaypointOffTrailReadoutTest {

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val pin = Waypoint("wp-1", 45.53, -122.68, null, "Creek pin", "", t)
    private val east = LatLng(45.52, -122.679)

    private fun readout(route: ReturnRoute) = navigationReadout(TrueHeadingReading.Available(0f), fix, pin, DistanceUnit.MILES, t + 1_000L, route = route)

    @Test
    fun `off the trail to a waypoint the line reads Head back to the line, with Off trail as its short form and no empty one`() {
        val r = readout(ReturnRoute.Ahead(east, 1_500.0, offTrail = OffTrailState.OFF, toWaypoint = true))
        assertEquals("Off trail · Head back to the line", r.statusText)
        assertEquals(listOf("Off trail · Head back to the line", "Off trail"), r.statusForms())
        assertEquals("Off trail", r.statusShortText)
    }

    @Test
    fun `the way home keeps its own words`() {
        val r = readout(ReturnRoute.Ahead(east, 1_500.0, offTrail = OffTrailState.OFF))
        assertEquals("Off trail · Return to Route along your track", r.statusText)
        assertEquals("Return to Route", r.statusShortText)
    }

    @Test
    fun `back on the trail to a waypoint reads Back on trail, and on it is empty`() {
        assertEquals("Back on trail", readout(ReturnRoute.Ahead(east, 1_500.0, offTrail = OffTrailState.BACK_ON, toWaypoint = true)).statusText)
        assertEquals("", readout(ReturnRoute.Ahead(east, 1_500.0, offTrail = OffTrailState.ON, toWaypoint = true)).statusText)
    }
}
