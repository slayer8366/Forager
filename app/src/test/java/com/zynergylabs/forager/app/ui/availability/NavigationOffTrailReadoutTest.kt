package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HopBand
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.PathHome
import com.zynergylabs.forager.app.domain.RouteHome
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.domain.wayhome.OffTrailState
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quiet off-trail state in the navigation display's status line (dispatch 2026-10-26-13, plan step 7 PR 3; RECORD
 * -1631, the owner: "Off trail · Return to Route along your track (Recommended)", short form "Return to Route"; "Back on
 * trail" while it shows). How long "Back on trail" shows is the domain's (`WayHomeOffTrailTest`): the line shows it for
 * exactly as long as the state is [OffTrailState.BACK_ON].
 */
class NavigationOffTrailReadoutTest {

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private val east = LatLng(45.52, -122.679)

    private fun readout(route: ReturnRoute, now: Long = t + 1_000L) =
        navigationReadout(TrueHeadingReading.Available(0f), fix, start, DistanceUnit.MILES, now, route = route)

    @Test
    fun `off the trail the line reads the notice, with Return to Route as its short form and no empty one`() {
        val r = readout(ReturnRoute.Ahead(east, 1_500.0, offTrail = OffTrailState.OFF))
        assertEquals("Off trail · Return to Route along your track", r.statusText)
        assertEquals(listOf("Off trail · Return to Route along your track", "Return to Route"), r.statusForms())
        assertEquals("Return to Route", r.statusShortText)
    }

    @Test
    fun `back on the trail the line reads Back on trail, and on it is empty as before`() {
        val back = readout(ReturnRoute.Ahead(east, 1_500.0, offTrail = OffTrailState.BACK_ON))
        assertEquals("Back on trail", back.statusText)
        assertEquals(listOf("Back on trail"), back.statusForms())
        assertEquals("", readout(ReturnRoute.Ahead(east, 1_500.0, offTrail = OffTrailState.ON)).statusText)
    }

    @Test
    fun `a stale fix keeps the line over the notice`() {
        val stale = readout(ReturnRoute.Ahead(east, 1_500.0, offTrail = OffTrailState.OFF), now = t + 60_000L)
        assertTrue("a stale fix's line, not the notice: ${stale.statusText}", stale.statusText.startsWith("Last fix"))
    }

    @Test
    fun `returnRouteOf carries the state to the HUD's route`() {
        val path = PathHome(trackMeters = 1_500.0, hopMeters = 0.0, hopBand = HopBand.NONE, originLegMeters = null, pointCount = 2)
        val ahead = RouteHome.Ahead(east, 1_500.0, HopBand.NONE, 0.0, aimsAtRouteEnd = true, pathHome = path)
        assertEquals(OffTrailState.OFF, (returnRouteOf(ahead, null, OffTrailState.OFF) as ReturnRoute.Ahead).offTrail)
    }
}
