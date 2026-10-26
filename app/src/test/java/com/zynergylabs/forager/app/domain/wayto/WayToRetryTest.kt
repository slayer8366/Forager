package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-10-26-11 Amendment 1, item C (RECORD -1669, the owner: "Try again on entering an area (Recommended)"):
 * when a straight-line result tries the trail route again. The area is 1 km about the waypoint ([there]), so a walker
 * 1.5 km west of it is outside and one 500 m west is inside.
 */
class WayToRetryTest {

    private val there = at(500.0, -60.0)
    private val area = Ground.region(centre = there, radiusKm = 1)
    private val outside = at(-1_000.0, 40.0)
    private val inside = at(0.0, 40.0)

    private fun retry(note: WayToWaypointNote, from: LatLng = outside, regions: List<OfflineRegionSummary> = listOf(area)) =
        WayToRetry(there, note, from, regions)

    @Test
    fun `walking into an area holding both ends tries again, once, and not on the fixes outside it`() {
        val r = retry(WayToWaypointNote.WAYPOINT_IN_ANOTHER_AREA)
        assertFalse("still outside", r.judge(at(-900.0, 40.0), listOf(area)))
        assertTrue("the first fix inside", r.judge(inside, listOf(area)))
        assertFalse("the next fix inside: no second search", r.judge(at(10.0, 40.0), listOf(area)))
    }

    @Test
    fun `leaving the area and walking back in tries again`() {
        val r = retry(WayToWaypointNote.NO_TRAIL_ROUTES)
        assertTrue(r.judge(inside, listOf(area)))
        assertFalse(r.judge(outside, listOf(area)))
        assertTrue(r.judge(inside, listOf(area)))
    }

    @Test
    fun `a search made inside the area does not try again while the walker stays inside`() {
        // The area held both ends when this search ran and it still came back straight: walking on inside changes nothing.
        val r = retry(WayToWaypointNote.NO_ROUTE_FOUND, from = inside)
        assertFalse(r.judge(at(100.0, 40.0), listOf(area)))
        assertFalse(r.judge(at(200.0, 40.0), listOf(area)))
    }

    @Test
    fun `routes arriving for an area that holds both ends try again at the next fix`() {
        // A download, or its routes, finishing while navigating: the list read at the next fix has them stored.
        val fetching = Ground.region(centre = there, radiusKm = 1, routeData = RegionRouteData.Fetching)
        val r = retry(WayToWaypointNote.ROUTES_FETCHING, from = inside, regions = listOf(fetching))
        assertFalse("routes not stored yet", r.judge(inside, listOf(fetching)))
        assertTrue("stored now", r.judge(inside, listOf(area)))
    }

    @Test
    fun `far from any trail tries again after walking on more than 50 m from where it was asked (49 m no, 51 m yes), the stray figure`() {
        val from = at(0.0, 400.0)
        assertFalse(retry(WayToWaypointNote.WALKER_FAR_FROM_TRAIL, from = from).judge(at(0.0, 351.0), listOf(area)))
        assertTrue(retry(WayToWaypointNote.WALKER_FAR_FROM_TRAIL, from = from).judge(at(0.0, 349.0), listOf(area)))
    }

    @Test
    fun `walking on does not try again for a note walking cannot change`() {
        val r = retry(WayToWaypointNote.WAYPOINT_FAR_FROM_TRAIL, from = inside)
        assertFalse(r.judge(at(300.0, 40.0), listOf(area)))
        assertFalse(r.judge(at(-300.0, 40.0), listOf(area)))
    }
}
