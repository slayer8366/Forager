package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.mapdata.OfflineBasemap
import com.zynergylabs.forager.app.domain.mapdata.ReadNearbyMapData
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routing.TrailEngine
import com.zynergylabs.forager.app.domain.routing.TrailEngineSource
import com.zynergylabs.forager.app.domain.routing.TrailRouter
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
import com.zynergylabs.forager.app.domain.wayto.StreetDropOffEnds
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointNote
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The pick-up at the walker's end, end to end on the drop-off fixture (dispatch 2026-11-04-04, A2 and A4): the real use
 * case, the real vendored BRouter and both real readers, with the walker standing where the fixture's pins stand and the
 * waypoint in the wood (pin_d). The far end's traps work as traps at the near end: on a good fix the route starts where
 * the drop-off chooser would end it, never at the engine's own start. A rougher fix is today's gated lead-in.
 */
class FindWayToWaypointPickUpTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> synchronized(logged) { logged += message } }
    private val profile by lazy { RoutingTestData.profileDir(tmp) }
    private val router by lazy { BRouterTrailRouter(profile, errorLog) }
    private val data: File by lazy { DropOffTestData.segments(tmp) }
    /** Every engine run, from and to: A4's "at most two engine runs" is counted here, not assumed. */
    private val runs = mutableListOf<Pair<LatLng, LatLng>>()
    private val counted by lazy {
        object : TrailRouter {
            override suspend fun route(start: LatLng, end: LatLng, routeData: File) = router.route(start, end, routeData).also { synchronized(runs) { runs += start to end } }
        }
    }
    private val engines = object : TrailEngineSource {
        override suspend fun engine(): Result<TrailEngine> = Result.success(TrailEngine(counted, BRouterTrailSnapper(router, TrailSnapMethod.SNAP)))
    }
    private val tile = DropOffTestData.resource(DropOffTestData.TILE).readBytes()
    private val tileJson = File(checkNotNull(javaClass.classLoader?.getResource("mapdata/tilejson-with-0003.json")).toURI()).readText()
    private val basemap = object : OfflineBasemap {
        override suspend fun tileJson() = Result.success<String?>(tileJson)
        override suspend fun tile(id: TileId) = Result.success(if (id == TileId(15, 9875, 12393)) tile else null)
    }

    private fun find(walker: String, accuracyMeters: Float?, to: String = "pin_d") = runBlocking {
        val region = Ground.region(id = 7, centre = DropOffTestData.point("pin_a"), radiusKm = 2, routeData = RegionRouteData.Stored("run", "t", 10L, data))
        val ends = StreetDropOffEnds(ReadNearbyMapData(basemap), BRouterRouteWays(profile))
        FindWayToWaypointUseCase(FakeRegions(region), engines, errorLog, ends)(DropOffTestData.point(walker), DropOffTestData.point(to), accuracyMeters)
    }

    private fun way(result: WayToWaypointResult): WayHome {
        assertTrue("along trails, not $result (log: $logged)", result is WayToWaypointResult.AlongTrails)
        return (result as WayToWaypointResult.AlongTrails).way
    }

    private fun assertTrailStartsAt(way: WayHome, start: String) {
        val off = GeoDistance.metersBetween(way.trail.points.first(), DropOffTestData.point(start))
        assertEquals("the trail starts $off m from $start", 0.0, off, 1.0)
        assertEquals("the lead-in ends where the trail starts", way.trail.points.first(), way.trackBack.points.last())
    }

    @Test
    fun `a walker at a house on a good fix is picked up from the street in front, where the driveway comes out`() {
        val way = way(find("pin_a", accuracyMeters = 5f))
        assertTrailStartsAt(way, "dropoff_a")
        assertEquals(DropOffTestData.point("pin_a"), way.trackBack.points.first())
        assertEquals(77.6, way.trackBack.meters, 1.0)
        assertTrailEndsAtTheWood(way)
        assertEquals("the first run, then one from the pick-up: $runs", 2, runs.size)
        assertEquals(DropOffTestData.point("pin_d"), runs[1].second)
    }

    @Test
    fun `a walker at a house behind a fenced lane is picked up from the address street`() {
        assertTrailStartsAt(way(find("pin_b", accuracyMeters = 5f)), "dropoff_b")
    }

    @Test
    fun `a walker at a house behind a stream is picked up from the street across only a culvert`() {
        assertTrailStartsAt(way(find("pin_c", accuracyMeters = 5f)), "dropoff_c")
    }

    @Test
    fun `a fix of exactly the limit still chooses the pick-up`() {
        assertTrailStartsAt(way(find("pin_a", accuracyMeters = StreetDropOffEnds.PICK_UP_FIX_ACCURACY_METERS)), "dropoff_a")
    }

    @Test
    fun `on a rougher fix the walker's end is today's gated lead-in, which crosses the house behind`() {
        // The engine starts the route at the dead end behind house A; the lead-in to it crosses the house next door.
        assertEquals(WayToWaypointResult.StraightLine(WayToWaypointNote.LEAD_IN_BLOCKED), find("pin_a", accuracyMeters = 15.5f))
        assertTrue(logged.any { "LEAD_IN_BLOCKED" in it && "the lead-in crosses" in it && "pick-up" !in it })
        assertEquals("no second run on a rougher fix: $runs", 1, runs.size)
    }

    @Test
    fun `a fix with no accuracy is treated as rougher`() {
        assertEquals(WayToWaypointResult.StraightLine(WayToWaypointNote.LEAD_IN_BLOCKED), find("pin_a", accuracyMeters = null))
    }

    @Test
    fun `a walker at a house picked up and a waypoint at a house dropped off take one route between the two`() {
        // At most two engine runs: the first, and one between the pick-up and the drop-off.
        val way = way(find("pin_b", accuracyMeters = 5f, to = "pin_a"))
        assertTrailStartsAt(way, "dropoff_b")
        val off = GeoDistance.metersBetween(way.trail.points.last(), DropOffTestData.point("dropoff_a"))
        assertEquals("the trail ends $off m from dropoff_a", 0.0, off, 1.0)
        assertEquals("two engine runs, the second from the pick-up to the drop-off: $runs", 2, runs.size)
        assertEquals("the second run starts at the pick-up", 0.0, GeoDistance.metersBetween(runs[1].first, DropOffTestData.point("dropoff_b")), 1.0)
        assertEquals("the second run ends at the drop-off", 0.0, GeoDistance.metersBetween(runs[1].second, DropOffTestData.point("dropoff_a")), 1.0)
    }

    @Test
    fun `a walker in the wood on a good fix keeps the engine's start, its lead-in gated`() {
        val way = way(find("walker", accuracyMeters = 5f))
        val engineStart = way(runBlocking {
            val region = Ground.region(id = 7, centre = DropOffTestData.point("pin_a"), radiusKm = 2, routeData = RegionRouteData.Stored("run", "t", 10L, data))
            FindWayToWaypointUseCase(FakeRegions(region), engines, ErrorLog { _, _, _ -> }, StreetDropOffEnds(ReadNearbyMapData(basemap), BRouterRouteWays(profile)))(DropOffTestData.point("walker"), DropOffTestData.point("pin_d"))
        })
        assertEquals("the same way as on a rougher fix", engineStart, way)
    }

    private fun assertTrailEndsAtTheWood(way: WayHome) {
        val off = GeoDistance.metersBetween(way.trail.points.last(), DropOffTestData.point("engine_d"))
        assertEquals("the trail ends $off m from engine_d", 0.0, off, 1.0)
    }
}
