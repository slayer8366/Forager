package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Which note the way to a waypoint gives when the two ends are not both in one area with usable routes (dispatch
 * 2026-10-26-10 Amendment 1, item 2; Sorrel's review of #412, S-412-1; RECORD -1666, the owner: "Fix: check both ends
 * first"). When no downloaded area's route rectangle holds both ends, the waypoint is in another area, whatever the state
 * of either end's routes; a route-state note ("not downloaded" and the rest) shows only when an area holding both ends
 * exists and its routes are in that state. Through the use case's own entry point, on [FakeTrailRouter].
 */
class FindWayToWaypointAreasTest {

    private val logs = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logs += message }
    private val router = FakeTrailRouter(listOf(at(-3_000.0, 0.0) to at(40_000.0, 0.0)))
    private val engines = FakeEngines(router)

    private val walker = at(0.0, 40.0)
    private val waypoint = at(30_000.0, 40.0)

    private fun find(vararg regions: OfflineRegionSummary): WayToWaypointResult =
        runBlocking { FindWayToWaypointUseCase(FakeRegions(*regions), engines, errorLog)(walker, waypoint) }

    private fun aroundWalker(routeData: RegionRouteData) = Ground.region(id = 1, radiusKm = 1, routeData = routeData)

    private fun aroundWaypoint(routeData: RegionRouteData) =
        Ground.region(id = 2, centre = at(30_000.0, 0.0), radiusKm = 1, routeData = routeData)

    private val stored1 = RegionRouteData.Stored("run-1", "t", 10L, Ground.FOLDER)
    private val stored2 = RegionRouteData.Stored("run-2", "t", 10L, File("/routes/2"))

    private val inAnotherArea = WayToWaypointResult.StraightLine(WayToWaypointNote.WAYPOINT_IN_ANOTHER_AREA)

    @Test
    fun `a waypoint in a second area whose routes were never fetched is in another area, since no area holds both ends`() {
        // S-412-1's case: fetching the second area's routes could never give a route, so "not downloaded" would mislead.
        assertEquals(inAnotherArea, find(aroundWalker(stored1), aroundWaypoint(RegionRouteData.NotFetched)))
        assertEquals(0, engines.asked)
        assertEquals("logged once: $logs", 1, logs.count { "WAYPOINT_IN_ANOTHER_AREA" in it })
    }

    @Test
    fun `a walker in an area whose routes were never fetched, with the waypoint in another, is in another area`() {
        assertEquals(inAnotherArea, find(aroundWalker(RegionRouteData.NotFetched), aroundWaypoint(stored2)))
    }

    @Test
    fun `a walker outside every area, with the waypoint's area damaged, is in another area whatever that area's state`() {
        assertEquals(inAnotherArea, find(aroundWaypoint(RegionRouteData.Damaged("gone"))))
        assertEquals(inAnotherArea, find(aroundWaypoint(RegionRouteData.Fetching)))
    }

    @Test
    fun `an area holding both ends with routes not fetched says not downloaded, though each end has routes in its own area`() {
        // The actionable case S-412-1 keeps: fetching the big area's routes gives a route.
        val holdsBoth = Ground.region(id = 3, centre = at(15_000.0, 0.0), radiusKm = 20, createdAt = 500L, routeData = RegionRouteData.NotFetched)

        assertEquals(
            WayToWaypointResult.StraightLine(WayToWaypointNote.ROUTES_NOT_DOWNLOADED),
            find(aroundWalker(stored1), aroundWaypoint(stored2), holdsBoth),
        )
        assertEquals(0, engines.asked)
    }

    @Test
    fun `an area holding both ends with stored routes is routed on, whatever the smaller areas hold`() {
        val holdsBoth = Ground.region(id = 3, centre = at(15_000.0, 0.0), radiusKm = 20, createdAt = 500L, routeData = RegionRouteData.Stored("run-3", "t", 10L, File("/routes/3")))

        val result = find(aroundWalker(RegionRouteData.NotFetched), aroundWaypoint(RegionRouteData.Damaged("gone")), holdsBoth)

        assertEquals(3L, (result as WayToWaypointResult.AlongTrails).way.regionId)
        assertEquals(listOf(Triple<LatLng, LatLng, File>(walker, waypoint, File("/routes/3"))), router.asked)
    }
}
