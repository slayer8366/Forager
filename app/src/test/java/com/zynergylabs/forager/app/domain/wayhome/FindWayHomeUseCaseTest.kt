package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routedata.RouteDataMissingReason
import com.zynergylabs.forager.app.domain.routing.RouteEnd
import com.zynergylabs.forager.app.domain.routing.TrailRouteFailure
import com.zynergylabs.forager.app.domain.routing.TrailRouteResult
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The way home along trails through its use case (dispatch 2026-10-23-09 Amendment 1, part 1; the rulings are RECORD
 * -1589's), on synthetic ground with a fake snapper and router. The real engine's part is `FindWayHomeOnBRouterTest`.
 *
 * The walk most cases use: out along a trail running east from the start, 500 m, then 200 m north into the forest.
 */
class FindWayHomeUseCaseTest {

    private val logs = mutableListOf<Pair<String, Throwable>>()
    private val errorLog = ErrorLog { _, message, error -> logs += message to error }

    /** The trail, east from the start for a kilometre. */
    private val trail = listOf(at(0.0, 0.0) to at(1_000.0, 0.0))

    private val outAlongTheTrail = (0..50).map { at(it * 10.0, 0.0) }
    private val intoTheForest = (1..5).map { at(500.0, it * 40.0) }
    private val walk = Ground.track(*(outAlongTheTrail + intoTheForest).toTypedArray())

    /** The engine's route from the leave point back to the start, along the trail. */
    private val routeBack = FakeRouter.found(500, at(500.0, 0.0), at(250.0, 0.0), at(0.0, 0.0))

    private var snapper = FakeSnapper(trail)
    private var router = FakeRouter(routeBack)

    private fun find(
        track: Track = walk,
        origin: Waypoint? = null,
        regions: OfflineMapRepository = FakeRegions(Ground.region()),
    ) = runBlocking { FindWayHomeUseCase(regions, router, snapper, errorLog)(track, origin) }

    private fun alongTrails(result: WayHomeResult): WayHome {
        assertTrue("along trails, not $result", result is WayHomeResult.AlongTrails)
        return (result as WayHomeResult.AlongTrails).wayHome
    }

    private fun assertTrackOnly(expected: WayHomeNote?, result: WayHomeResult) {
        assertEquals(WayHomeResult.TrackOnly(expected), result)
        assertEquals("logged once: $logs", 1, logs.size)
        assertTrue("the log names the note: ${logs.single().first}", logs.single().first.contains(expected?.name ?: "no note"))
    }

    private fun near(expected: LatLng, actual: LatLng) =
        assertTrue("$actual is ${GeoDistance.metersBetween(expected, actual)} m from $expected", GeoDistance.metersBetween(expected, actual) < 0.01)

    // ---- The way home along trails ----

    @Test
    fun `the way home is the track back to where the walker left the trail, the trail route, then the track to the start`() {
        val wayHome = alongTrails(find())

        assertEquals(WayHomePartKind.TRACK_BACK, wayHome.trackBack.kind)
        assertEquals("the track back, most recent point first", (intoTheForest.reversed() + at(500.0, 0.0)).size, wayHome.trackBack.points.size)
        near(intoTheForest.last(), wayHome.trackBack.points.first())
        near(at(500.0, 0.0), wayHome.trackBack.points.last())
        assertEquals(200.0, wayHome.trackBack.meters, 0.5)

        assertEquals(WayHomePartKind.TRAIL, wayHome.trail.kind)
        assertEquals(listOf(at(500.0, 0.0), at(500.0, 0.0), at(250.0, 0.0), at(0.0, 0.0), at(0.0, 0.0)), wayHome.trail.points)
        assertEquals(500.0, wayHome.trail.meters, 0.01)
        assertEquals(0.0, wayHome.trailJointMeters, 0.01)

        assertEquals(WayHomePartKind.TRACK_TO_START, wayHome.trackToStart.kind)
        assertEquals(listOf(at(0.0, 0.0)), wayHome.trackToStart.points)
        assertEquals(0.0, wayHome.trackToStart.meters, 0.0)

        assertEquals(700.0, wayHome.totalMeters, 0.5)
        assertEquals(1L, wayHome.regionId)
        assertEquals("the router is asked once, from the leave point to the entry point", listOf(at(500.0, 0.0) to at(0.0, 0.0)), router.asked)
        assertEquals("nothing is logged when nothing falls back", emptyList<Pair<String, Throwable>>(), logs)
    }

    @Test
    fun `the line is the three parts end to end, each shared point once`() {
        val wayHome = alongTrails(find())

        assertEquals(wayHome.trackBack.points + wayHome.trail.points.drop(1) + wayHome.trackToStart.points.drop(1), wayHome.line)
        assertEquals(wayHome.trackBack.points.last(), wayHome.trail.points.first())
        assertEquals(wayHome.trail.points.last(), wayHome.trackToStart.points.first())
    }

    @Test
    fun `the joints between the track points and the engine's route are part of the trail's metres`() {
        // The walker left the trail 10 m north of it, and joined it 20 m north of it: the engine's route runs on the trail.
        val track = Ground.track(at(0.0, 20.0), *(1..50).map { at(it * 10.0, 0.0) }.dropLast(1).toTypedArray(), at(500.0, 10.0), *intoTheForest.toTypedArray())
        val wayHome = alongTrails(find(track))

        assertEquals(30.0, wayHome.trailJointMeters, 0.05)
        assertEquals(530.0, wayHome.trail.meters, 0.05)
        near(at(500.0, 10.0), wayHome.trail.points.first())
        near(at(0.0, 20.0), wayHome.trail.points.last())
    }

    @Test
    fun `a walker on the trail now has no track back`() {
        val wayHome = alongTrails(find(Ground.track(*outAlongTheTrail.toTypedArray())))

        assertEquals(listOf(at(500.0, 0.0)), wayHome.trackBack.points)
        assertEquals(0.0, wayHome.trackBack.meters, 0.0)
        assertEquals(500.0, wayHome.totalMeters, 0.5)
    }

    @Test
    fun `the far end returns along the walker's own track, to the origin waypoint`() {
        // Out from the start 60 m north of the trail, onto it, then the same walk.
        val track = Ground.track(at(0.0, 60.0), at(0.0, 30.0), *outAlongTheTrail.toTypedArray(), *intoTheForest.toTypedArray())
        val origin = Waypoint("o", at(-30.0, 60.0).lat, at(-30.0, 60.0).lng, null, "Start", "", 0L)
        val wayHome = alongTrails(find(track, origin))

        assertEquals(listOf(at(0.0, 0.0), at(0.0, 30.0), at(0.0, 60.0), at(-30.0, 60.0)), wayHome.trackToStart.points)
        assertEquals(90.0, wayHome.trackToStart.meters, 0.05)
    }

    @Test
    fun `where the walker left the trail is the newest point near it, after they went back onto it`() {
        // Off the trail and back onto it, then off again: the newest leave wins.
        val track = Ground.track(
            *(0..20).map { at(it * 10.0, 0.0) }.toTypedArray(),
            at(200.0, 100.0), at(300.0, 100.0),
            *(30..50).map { at(it * 10.0, 0.0) }.toTypedArray(),
            *intoTheForest.toTypedArray(),
        )
        alongTrails(find(track))

        assertEquals(listOf(at(500.0, 0.0) to at(0.0, 0.0)), router.asked)
    }

    @Test
    fun `a point within 25 m of a trail is on it, and one beyond is not`() {
        router = FakeRouter(FakeRouter.found(500, at(500.0, 0.0), at(0.0, 0.0)))
        alongTrails(find(Ground.track(at(0.0, 24.9), at(0.0, 200.0), at(500.0, 0.0), at(500.0, 200.0))))
        near(at(0.0, 24.9), router.asked.single().second)

        logs.clear()
        assertTrackOnly(WayHomeNote.NEVER_NEAR_TRAIL, find(Ground.track(at(0.0, 25.5), at(0.0, 200.0), at(500.0, 25.5), at(500.0, 200.0))))
    }

    // ---- Every fallback, and its note ----

    @Test
    fun `a track that never came near a trail follows the track, saying so`() {
        assertTrackOnly(WayHomeNote.NEVER_NEAR_TRAIL, find(Ground.track(*intoTheForest.toTypedArray())))
        assertTrue("no route is asked for", router.asked.isEmpty())
    }

    @Test
    fun `a track that touches a trail only where it first joined it is its own way home, with no note`() {
        assertTrackOnly(null, find(Ground.track(at(0.0, 0.0), *(1..5).map { at(0.0, it * 40.0) }.toTypedArray())))
        assertTrue(router.asked.isEmpty())
    }

    @Test
    fun `no downloaded area at the start means no trail routes here`() {
        assertTrackOnly(WayHomeNote.NO_TRAIL_ROUTES, find(regions = FakeRegions()))
    }

    @Test
    fun `routes the server has none of for the area mean no trail routes here`() {
        assertTrackOnly(WayHomeNote.NO_TRAIL_ROUTES, find(regions = FakeRegions(Ground.region(routeData = RegionRouteData.NoneHere))))
    }

    @Test
    fun `routes never fetched mean trail routes not downloaded, never no trail routes here`() {
        val result = find(regions = FakeRegions(Ground.region(routeData = RegionRouteData.NotFetched)))
        assertTrackOnly(WayHomeNote.ROUTES_NOT_DOWNLOADED, result)
        assertEquals("Trail routes not downloaded · following your track", (result as WayHomeResult.TrackOnly).note!!.text)
    }

    @Test
    fun `routes whose fetch failed mean trail routes not downloaded`() {
        for (reason in listOf(RouteDataMissingReason.NETWORK, RouteDataMissingReason.RATE_LIMITED, RouteDataMissingReason.NO_APP_KEY)) {
            logs.clear()
            assertTrackOnly(WayHomeNote.ROUTES_NOT_DOWNLOADED, find(regions = FakeRegions(Ground.region(routeData = RegionRouteData.Missing(reason)))))
        }
    }

    @Test
    fun `routes being fetched now mean getting trail routes`() {
        val result = find(regions = FakeRegions(Ground.region(routeData = RegionRouteData.Fetching)))
        assertTrackOnly(WayHomeNote.ROUTES_FETCHING, result)
        assertEquals("Getting trail routes… · following your track", (result as WayHomeResult.TrackOnly).note!!.text)
    }

    @Test
    fun `routes whose build lacks the start's square mean no trail routes here`() {
        val partial = RegionRouteData.Stored("run-1", "t", 10L, Ground.FOLDER, missingSquares = listOf("W125_N45"))
        assertTrackOnly(WayHomeNote.NO_TRAIL_ROUTES, find(regions = FakeRegions(Ground.region(routeData = partial))))
    }

    @Test
    fun `damaged routes, or regions that can't be listed, mean the routes couldn't be read`() {
        assertTrackOnly(WayHomeNote.ROUTES_UNREADABLE, find(regions = FakeRegions(Ground.region(routeData = RegionRouteData.Damaged("gone")))))

        logs.clear()
        val failure = IllegalStateException("no database")
        assertTrackOnly(WayHomeNote.ROUTES_UNREADABLE, find(regions = FakeRegions(Result.failure(failure))))
        assertEquals("the log keeps the cause", failure, logs.single().second.cause)
    }

    @Test
    fun `a start outside every area with routes, with the walker inside one, is a route leaving the area`() {
        val walkerOnly = Ground.region(centre = at(500.0, 200.0), radiusKm = 1)
        val track = Ground.track(at(-5_000.0, 0.0), *outAlongTheTrail.toTypedArray(), *intoTheForest.toTypedArray())
        assertTrackOnly(WayHomeNote.LEAVES_AREA, find(track, regions = FakeRegions(walkerOnly)))
    }

    @Test
    fun `each engine failure on the route is its note`() {
        val cases = mapOf(
            TrailRouteFailure.NoPath("no track found") to WayHomeNote.NO_ROUTE_FOUND,
            TrailRouteFailure.EngineFailed("timeout") to WayHomeNote.NO_ROUTE_FOUND,
            TrailRouteFailure.EndOutsideData(RouteEnd.END) to WayHomeNote.NO_ROUTE_FOUND,
            TrailRouteFailure.DataMissing("gone") to WayHomeNote.ROUTES_UNREADABLE,
            TrailRouteFailure.DataCorrupt("checksum") to WayHomeNote.ROUTES_UNREADABLE,
        )
        for ((failure, note) in cases) {
            logs.clear()
            router = FakeRouter(TrailRouteResult.Failed(failure))
            assertTrackOnly(note, find())
        }
    }

    @Test
    fun `unreadable data while finding the trail stops the scan, and says the routes couldn't be read`() {
        snapper = FakeSnapper(trail, failWith = TrailRouteFailure.DataCorrupt("checksum"))
        assertTrackOnly(WayHomeNote.ROUTES_UNREADABLE, find())
        assertEquals("one test, then no more", 1, snapper.asked.size)
    }

    @Test
    fun `a full route's own end off the network stops the scan as no route found`() {
        snapper = FakeSnapper(trail, failWith = TrailRouteFailure.EndOutsideData(RouteEnd.END))
        assertTrackOnly(WayHomeNote.NO_ROUTE_FOUND, find())
    }

    // ---- Leaving the area (the owner: "Edge + length check") ----

    @Test
    fun `a trail route that runs outside the area's rectangle leaves the area`() {
        router = FakeRouter(FakeRouter.found(500, at(500.0, 0.0), at(250.0, 20_000.0), at(0.0, 0.0)))
        assertTrackOnly(WayHomeNote.LEAVES_AREA, find())
    }

    @Test
    fun `a trail route more than 1·5 times the track between the same points leaves the area, and one at 1·5 times does not`() {
        // The track from the leave point back to the entry point is 500 m.
        router = FakeRouter(FakeRouter.found(751, at(500.0, 0.0), at(0.0, 0.0)))
        assertTrackOnly(WayHomeNote.LEAVES_AREA, find())

        logs.clear()
        router = FakeRouter(FakeRouter.found(749, at(500.0, 0.0), at(0.0, 0.0)))
        assertEquals(749.0, alongTrails(find()).trail.meters, 0.01)
    }

    @Test
    fun `track points outside the area's rectangle are never tested`() {
        val small = Ground.region(radiusKm = 1)
        val track = Ground.track(*outAlongTheTrail.toTypedArray(), at(500.0, 3_000.0))
        alongTrails(find(track, regions = FakeRegions(small)))

        assertFalse("the point 3 km north, outside the 1 km area, was not tested", snapper.asked.contains(at(500.0, 3_000.0)))
    }

    @Test
    fun `a track of one point has no way home to find, and says why in the log`() {
        assertTrackOnly(null, find(Ground.track(at(0.0, 0.0))))
    }
}
