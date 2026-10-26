package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.RouteLinePartKind
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routedata.RouteDataMissingReason
import com.zynergylabs.forager.app.domain.routing.RouteEnd
import com.zynergylabs.forager.app.domain.routing.TrailRouteFailure
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import com.zynergylabs.forager.app.domain.wayhome.WayHomeNote
import com.zynergylabs.forager.app.domain.wayhome.WayHomePartKind
import com.zynergylabs.forager.app.domain.wayhome.partsFrom
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * The way to a waypoint along trails (dispatch 2026-10-26-10, step 6 PR 2; RECORD -1652, -1653), through the use case's
 * own entry point, on a fake engine with one trail running east-west through [Ground]'s origin.
 */
class FindWayToWaypointUseCaseTest {

    private val logs = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logs += message }
    private val router = FakeTrailRouter(listOf(at(-3_000.0, 0.0) to at(3_000.0, 0.0)))
    private val engines = FakeEngines(router)

    private fun find(from: LatLng, waypoint: LatLng, vararg regions: OfflineRegionSummary): WayToWaypointResult =
        runBlocking { FindWayToWaypointUseCase(FakeRegions(*regions), engines, errorLog)(from, waypoint) }

    private fun way(result: WayToWaypointResult): WayHome {
        assertTrue("a route along trails, not $result", result is WayToWaypointResult.AlongTrails)
        return (result as WayToWaypointResult.AlongTrails).way
    }

    private fun assertNear(expected: LatLng, actual: LatLng) =
        assertEquals("metres apart, $expected and $actual", 0.0, GeoDistance.metersBetween(expected, actual), 0.01)

    private fun straight(note: WayToWaypointNote) = WayToWaypointResult.StraightLine(note)

    @Test
    fun `walker and waypoint each near a trail give three parts, the trail between the two trail points`() {
        val walker = at(0.0, 40.0)
        val waypoint = at(500.0, -60.0)

        val way = way(find(walker, waypoint, Ground.region()))

        assertEquals(WayHomePartKind.STRAIGHT, way.trackBack.kind)
        assertEquals(2, way.trackBack.points.size)
        assertNear(walker, way.trackBack.points[0])
        assertNear(at(0.0, 0.0), way.trackBack.points[1])
        assertEquals(40.0, way.trackBack.meters, 0.5)

        assertEquals(WayHomePartKind.TRAIL, way.trail.kind)
        assertNear(at(0.0, 0.0), way.trail.points.first())
        assertNear(at(500.0, 0.0), way.trail.points.last())
        assertEquals("the engine's own length", 500.0, way.trail.meters, 0.0)

        assertEquals(WayHomePartKind.STRAIGHT, way.trackToStart.kind)
        assertEquals(2, way.trackToStart.points.size)
        assertNear(at(500.0, 0.0), way.trackToStart.points[0])
        assertNear(waypoint, way.trackToStart.points[1])
        assertEquals(60.0, way.trackToStart.meters, 0.5)

        assertEquals(1L, way.regionId)
        assertEquals("one engine run, from the walker to the waypoint", listOf(Triple(walker, waypoint, Ground.FOLDER)), router.asked)
    }

    @Test
    fun `the line is placed and drawn as the way home's is, its two legs straight and the trail between`() {
        val way = way(find(at(0.0, 40.0), at(500.0, -60.0), Ground.region()))
        val line = WayHomeLine(way)

        assertEquals(listOf(RouteLinePartKind.STRAIGHT, RouteLinePartKind.TRAIL, RouteLinePartKind.STRAIGHT), partsFrom(line, 0).map { it.kind })
        assertEquals(600.0, line.totalMeters, 1.0)
        assertEquals(way.totalMeters, line.totalMeters, 1e-6)
    }

    @Test
    fun `a waypoint over 250 m from any trail gets the straight line, with its note`() {
        assertEquals(straight(WayToWaypointNote.WAYPOINT_FAR_FROM_TRAIL), find(at(0.0, 40.0), at(500.0, 300.0), Ground.region()))
        assertEquals("logged once: $logs", 1, logs.count { "WAYPOINT_FAR_FROM_TRAIL" in it })
    }

    @Test
    fun `a walker over 250 m from any trail gets the straight line, with its note`() {
        assertEquals(straight(WayToWaypointNote.WALKER_FAR_FROM_TRAIL), find(at(0.0, 300.0), at(500.0, -60.0), Ground.region()))
    }

    @Test
    fun `ends in different regions get the straight line, and no engine is built or run`() {
        val here = Ground.region(id = 1, radiusKm = 1)
        val there = Ground.region(id = 2, centre = at(30_000.0, 0.0), radiusKm = 1, routeData = RegionRouteData.Stored("run-2", "t", 10L, File("/routes/2")))

        assertEquals(straight(WayToWaypointNote.WAYPOINT_IN_ANOTHER_AREA), find(at(0.0, 40.0), at(30_000.0, 40.0), here, there))
        assertEquals(0, engines.asked)
        assertEquals(emptyList<Any>(), router.asked)
    }

    @Test
    fun `a waypoint outside every downloaded area, with the walker inside one, is in another area`() {
        assertEquals(straight(WayToWaypointNote.WAYPOINT_IN_ANOTHER_AREA), find(at(0.0, 40.0), at(30_000.0, 40.0), Ground.region(radiusKm = 1)))
    }

    @Test
    fun `a trail route with a point outside the area gets the straight line, with the leaves-area note`() {
        router.via = listOf(at(250.0, 5_000.0))

        val result = find(at(0.0, 40.0), at(500.0, -60.0), Ground.region(radiusKm = 1))

        assertEquals(straight(WayToWaypointNote.LEAVES_AREA), result)
        // RECORD -1665, the owner: "Add '· straight line'" (Amendment 1, item 1). The way home's sentence is unchanged.
        assertEquals("This route leaves your downloaded area · straight line", (result as WayToWaypointResult.StraightLine).note.text)
        assertEquals("This route leaves your downloaded area. Download a larger area to route there.", WayHomeNote.LEAVES_AREA.text)
    }

    @Test
    fun `a waypoint 100 m away near a trail still gets the trail route, since a trail route is always worth it`() {
        // RECORD -1653, the owner: "It's always worth it". No short-walk cut-off: the report's 150 m rule is not built.
        val way = way(find(at(0.0, 20.0), at(100.0, 20.0), Ground.region()))

        assertEquals(100.0, way.trail.meters, 0.0)
        assertEquals(140.0, way.totalMeters, 0.5)
    }

    @Test
    fun `the newest region holding both ends is routed on, never a newer one holding only one`() {
        val holdsBoth = Ground.region(id = 1, createdAt = 1_000L)
        val holdsWalkerOnly = Ground.region(id = 2, centre = at(-1_000.0, 0.0), radiusKm = 1, createdAt = 5_000L, routeData = RegionRouteData.Stored("run-2", "t", 10L, File("/routes/2")))
        val walker = at(-500.0, 40.0)
        val waypoint = at(1_500.0, -60.0)

        assertEquals(1L, way(find(walker, waypoint, holdsBoth, holdsWalkerOnly)).regionId)
        assertEquals(Ground.FOLDER, router.asked.single().third)

        val newerHoldsBoth = Ground.region(id = 3, createdAt = 9_000L, routeData = RegionRouteData.Stored("run-3", "t", 10L, File("/routes/3")))
        assertEquals(3L, way(find(walker, waypoint, holdsBoth, holdsWalkerOnly, newerHoldsBoth)).regionId)
        assertEquals(File("/routes/3"), router.asked.last().third)
    }

    @Test
    fun `routes not usable at an end say why, as the way home's notes do, and no engine is built`() {
        val walker = at(0.0, 40.0)
        val waypoint = at(500.0, -60.0)
        fun with(routeData: RegionRouteData) = find(walker, waypoint, Ground.region(routeData = routeData))

        assertEquals(straight(WayToWaypointNote.ROUTES_NOT_DOWNLOADED), with(RegionRouteData.NotFetched))
        assertEquals(straight(WayToWaypointNote.ROUTES_NOT_DOWNLOADED), with(RegionRouteData.Missing(RouteDataMissingReason.NETWORK)))
        assertEquals(straight(WayToWaypointNote.ROUTES_NEED_APP_UPDATE), with(RegionRouteData.Missing(RouteDataMissingReason.FORBIDDEN)))
        assertEquals(straight(WayToWaypointNote.ROUTES_FETCHING), with(RegionRouteData.Fetching))
        assertEquals(straight(WayToWaypointNote.ROUTES_UNREADABLE), with(RegionRouteData.Damaged("gone")))
        assertEquals(straight(WayToWaypointNote.NO_TRAIL_ROUTES), with(RegionRouteData.NoneHere))
        // Ground's base lies in W125_N45.
        assertEquals(straight(WayToWaypointNote.NO_TRAIL_ROUTES), with(RegionRouteData.Stored("run-1", "t", 10L, Ground.FOLDER, missingSquares = listOf("W125_N45"))))
        assertEquals(straight(WayToWaypointNote.NO_TRAIL_ROUTES), find(walker, waypoint))
        assertEquals(0, engines.asked)
        assertEquals("each fallback logged once: $logs", 8, logs.size)
    }

    @Test
    fun `regions that cannot be listed are unreadable routes`() {
        val failing = FakeRegions(Result.failure(IOException("database closed")))

        assertEquals(straight(WayToWaypointNote.ROUTES_UNREADABLE), runBlocking { FindWayToWaypointUseCase(failing, engines, errorLog)(at(0.0, 40.0), at(500.0, -60.0)) })
        assertEquals(1, logs.size)
    }

    @Test
    fun `an engine that could not be built is "Couldn't find a trail route", logged, and asked for again next time`() {
        engines.failWith = IOException("disk full")

        assertEquals(straight(WayToWaypointNote.NO_ROUTE_FOUND), find(at(0.0, 40.0), at(500.0, -60.0), Ground.region()))
        assertEquals(straight(WayToWaypointNote.NO_ROUTE_FOUND), find(at(0.0, 40.0), at(500.0, -60.0), Ground.region()))
        assertEquals(2, engines.asked)
        assertEquals("each logged once: $logs", 2, logs.count { "routing profile could not be installed" in it })
    }

    @Test
    fun `the engine's other failures name their notes`() {
        val walker = at(0.0, 40.0)
        val waypoint = at(500.0, -60.0)
        fun failing(failure: TrailRouteFailure): WayToWaypointResult {
            router.failWith = failure
            return find(walker, waypoint, Ground.region())
        }

        assertEquals(straight(WayToWaypointNote.NO_ROUTE_FOUND), failing(TrailRouteFailure.NoPath("no track found")))
        assertEquals(straight(WayToWaypointNote.NO_ROUTE_FOUND), failing(TrailRouteFailure.EngineFailed("timeout")))
        assertEquals(straight(WayToWaypointNote.ROUTES_UNREADABLE), failing(TrailRouteFailure.DataMissing("gone")))
        assertEquals(straight(WayToWaypointNote.ROUTES_UNREADABLE), failing(TrailRouteFailure.DataCorrupt("checksum")))
        assertEquals(4, logs.size)
    }

    @Test
    fun `an end in a square with no route file is "No trail routes here", not far from a trail`() {
        // Dispatch 2026-10-26-10 Amendment 2, RECORD -1667, the owner: "'No trail routes here' (Recommended)".
        fun failing(failure: TrailRouteFailure): WayToWaypointResult {
            router.failWith = failure
            return find(at(0.0, 40.0), at(500.0, -60.0), Ground.region())
        }

        assertEquals(straight(WayToWaypointNote.NO_TRAIL_ROUTES), failing(TrailRouteFailure.EndOutsideData(RouteEnd.END, squareHasNoFile = true)))
        assertEquals(straight(WayToWaypointNote.NO_TRAIL_ROUTES), failing(TrailRouteFailure.EndOutsideData(RouteEnd.START, squareHasNoFile = true)))
        // The engine's own "position not mapped" keeps its notes.
        assertEquals(straight(WayToWaypointNote.WAYPOINT_FAR_FROM_TRAIL), failing(TrailRouteFailure.EndOutsideData(RouteEnd.END)))
        assertEquals(straight(WayToWaypointNote.WALKER_FAR_FROM_TRAIL), failing(TrailRouteFailure.EndOutsideData(RouteEnd.START)))
    }

    @Test
    fun `the notes are the owner's words`() {
        // RECORD -1652 Q4, "Approve as written"; -1653. LEAVES_AREA: RECORD -1665, the owner: "Add '· straight line'".
        assertEquals(
            listOf(
                "No trail routes here · straight line",
                "Trail routes not downloaded · straight line",
                "Trail routes need an app update · straight line",
                "Getting trail routes… · straight line",
                "Trail routes couldn't be read · straight line",
                "Couldn't find a trail route · straight line",
                "This route leaves your downloaded area · straight line",
                "The waypoint is far from any trail · straight line",
                "You're far from any trail · straight line",
                "The waypoint is in another area · straight line",
            ),
            WayToWaypointNote.entries.map { it.text },
        )
    }
}
