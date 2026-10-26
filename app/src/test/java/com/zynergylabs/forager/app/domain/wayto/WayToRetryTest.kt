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

    /**
     * Until Amendment 2 this case used NO_ROUTE_FOUND, which item 3 (RECORD -1670, "Every 50 m walked (Recommended)") now
     * retries every 50 m walked; the claim it pins, that walking into the area fires once and walking on inside does not,
     * is kept for a note walking cannot change.
     */
    @Test
    fun `a search made inside the area does not try again while the walker stays inside, for a note walking cannot change`() {
        // The area held both ends when this search ran and it still came back straight: walking on inside changes nothing.
        val r = retry(WayToWaypointNote.ROUTES_UNREADABLE, from = inside)
        assertFalse(r.judge(at(100.0, 40.0), listOf(area)))
        assertFalse(r.judge(at(200.0, 40.0), listOf(area)))
    }

    /**
     * Dispatch 2026-10-26-11 Amendment 2, item 3 (RECORD -1670, the owner: "Every 50 m walked (Recommended)"): while a
     * straight result shows, the notes walking can change try again after each 50 m walked from where the search was
     * asked, the stray figure, as WALKER_FAR_FROM_TRAIL already did.
     */
    @Test
    fun `each note walking can change tries again after walking on more than 50 m from where it was asked (49 m no, 51 m yes)`() {
        val walkingCanChange = listOf(
            WayToWaypointNote.LEAVES_AREA,
            WayToWaypointNote.NO_ROUTE_FOUND,
            WayToWaypointNote.NO_TRAIL_ROUTES,
            WayToWaypointNote.WALKER_FAR_FROM_TRAIL,
            WayToWaypointNote.WAYPOINT_IN_ANOTHER_AREA,
        )
        for (note in walkingCanChange) {
            assertFalse("$note: 49 m on", retry(note, from = inside).judge(at(49.0, 40.0), listOf(area)))
            assertTrue("$note: 51 m on", retry(note, from = inside).judge(at(51.0, 40.0), listOf(area)))
        }
    }

    /** Item 3's other half: walking cannot change these, so they wait for the area trigger alone, however far the walker goes. */
    @Test
    fun `the notes walking cannot change do not try again on walking on`() {
        for (note in listOf(WayToWaypointNote.WAYPOINT_FAR_FROM_TRAIL, WayToWaypointNote.ROUTES_UNREADABLE, WayToWaypointNote.ROUTES_NEED_APP_UPDATE)) {
            val r = retry(note, from = inside)
            assertFalse("$note: 300 m east", r.judge(at(300.0, 40.0), listOf(area)))
            assertFalse("$note: 300 m west", r.judge(at(-300.0, 40.0), listOf(area)))
        }
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

    /**
     * Amendment 3, item 3 (RECORD -1671: the owner's question said "Inside an area"): the 50 m retry runs only while the
     * walker's fix is inside a downloaded area. Outside every one, no note tries again on walking on, however far; walking
     * into an area holding both ends still tries at once (the first case above).
     */
    @Test
    fun `outside every downloaded area no note tries again on walking on`() {
        for (note in WayToWaypointNote.entries) {
            val r = retry(note, from = outside)
            assertFalse("$note: 100 m on, outside", r.judge(at(-1_100.0, 40.0), listOf(area)))
            assertFalse("$note: 300 m on, outside", r.judge(at(-1_300.0, 40.0), listOf(area)))
            assertFalse("$note: no area listed", retry(note, from = outside, regions = emptyList()).judge(at(-900.0, 40.0), emptyList()))
        }
    }

    /**
     * "Inside" is the walker's fix inside a downloaded area, which need not hold the waypoint: a note such as
     * WAYPOINT_IN_ANOTHER_AREA is one a walk inside the walker's own area can change.
     */
    @Test
    fun `inside an area that holds the walker and not the waypoint, walking on tries again (49 m no, 51 m yes)`() {
        val mine = Ground.region(id = 2, centre = at(-3_000.0, 40.0), radiusKm = 1)
        val from = at(-3_000.0, 40.0)
        val note = WayToWaypointNote.WAYPOINT_IN_ANOTHER_AREA
        assertFalse(retry(note, from = from, regions = listOf(mine, area)).judge(at(-2_951.0, 40.0), listOf(mine, area)))
        assertTrue(retry(note, from = from, regions = listOf(mine, area)).judge(at(-2_949.0, 40.0), listOf(mine, area)))
    }

    /** A region listed but not downloaded on this phone (restored from a backup) is not an area the walker is inside. */
    @Test
    fun `a region not downloaded is not inside`() {
        val restored = area.copy(isDownloaded = false)
        val r = retry(WayToWaypointNote.NO_ROUTE_FOUND, from = inside, regions = listOf(restored))
        assertFalse(r.judge(at(100.0, 40.0), listOf(restored)))
    }
}
