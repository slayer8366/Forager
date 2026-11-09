package com.zynergylabs.forager.app.domain.route

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routedata.RouteDataMissingReason
import com.zynergylabs.forager.app.domain.routing.RouteEnd
import com.zynergylabs.forager.app.domain.routing.TrailRouteFailure
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayto.FakeEngines
import com.zynergylabs.forager.app.domain.wayto.FakeTrailRouter
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plotting a route between waypoints (dispatch 2026-10-30-02, step 2), through the use case's own two entry points, on
 * the fake engine the way to a waypoint is tested on: one trail running east-west through [Ground]'s origin, each stop
 * matched to its foot on it within 250 m.
 */
class PlotRouteUseCaseTest {

    private val logs = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logs += message }
    private val router = FakeTrailRouter(listOf(at(-3_000.0, 0.0) to at(3_000.0, 0.0)))
    private val engines = FakeEngines(router)

    /** [com.zynergylabs.forager.app.domain.wayhome.FakeRegions] with a count of the listings. */
    private class CountingRegions(private val regions: Result<List<OfflineRegionSummary>>) : OfflineMapRepository {
        constructor(vararg regions: OfflineRegionSummary) : this(Result.success(regions.toList()))
        var listed = 0
        override suspend fun download(name: String, region: Region, onProgress: (downloaded: Int, total: Int) -> Unit): Result<OfflineRegionSummary> = error("not used")
        override suspend fun deleteRegion(id: Long): Result<Unit> = error("not used")
        override suspend fun listRegions(): Result<List<OfflineRegionSummary>> { listed++; return regions }
    }

    private fun stop(name: String, at: LatLng) = RouteStop(name, at)

    private fun plot(stops: List<RouteStop>, regions: OfflineMapRepository = CountingRegions(Ground.region())): PlottedRoute =
        runBlocking { PlotRouteUseCase(regions, engines, errorLog)(stops) }

    private fun leg(from: LatLng, to: LatLng, regions: OfflineMapRepository = CountingRegions(Ground.region())): RouteLeg =
        runBlocking { PlotRouteUseCase(regions, engines, errorLog).leg(from, to) }

    private fun assertNear(expected: LatLng, actual: LatLng) =
        assertEquals("metres apart, $expected and $actual", 0.0, GeoDistance.metersBetween(expected, actual), 0.01)

    // ---- along trails ---------------------------------------------------------------------------

    @Test
    fun `a leg between two stops near a trail runs stop, trail, stop, its joins counted and kept apart from the trail`() {
        val a = at(0.0, 40.0)
        val b = at(500.0, -60.0)

        val route = plot(listOf(stop("A", a), stop("B", b)))
        val trail = route.legs.single() as RouteLeg.Trail

        assertNear(a, trail.line.first())
        assertNear(b, trail.line.last())
        assertNear(at(0.0, 0.0), trail.line[trail.trailFrom])
        assertNear(at(500.0, 0.0), trail.line[trail.trailTo])
        assertEquals("the joins are the first and last stretch only", listOf(1, trail.line.size - 2), listOf(trail.trailFrom, trail.trailTo))
        assertEquals("40 m join + the engine's 500 m + 60 m join", 600.0, trail.meters, 0.5)
        assertEquals(trail.meters, route.totalMeters, 0.0)
        assertEquals("one engine run, from stop to stop", listOf(Triple(a, b, Ground.FOLDER)), router.asked)
        assertEquals("no fallback, nothing logged", emptyList<String>(), logs)
    }

    @Test
    fun `three stops are routed leg by leg in order, the regions listed once, and the total is the sum`() {
        val regions = CountingRegions(Ground.region())
        val (a, b, c) = listOf(at(0.0, 40.0), at(500.0, -60.0), at(1_200.0, 30.0))

        val route = plot(listOf(stop("A", a), stop("B", b), stop("C", c)), regions)

        assertEquals(listOf(a to b, b to c), router.asked.map { it.first to it.second })
        assertEquals(1, regions.listed)
        assertEquals(route.legs.sumOf { it.meters }, route.totalMeters, 1e-9)
        assertEquals(600.0 + (60.0 + 700.0 + 30.0), route.totalMeters, 1.0)
    }

    @Test
    fun `A to B to A is allowed`() {
        val a = at(0.0, 40.0)
        val b = at(500.0, -60.0)

        val route = plot(listOf(stop("A", a), stop("B", b), stop("A", a)))

        assertEquals(2, route.legs.size)
        assertEquals(route.legs[0].meters, route.legs[1].meters, 0.5)
    }

    @Test
    fun `the single-leg method gives the leg the whole route gives`() {
        val a = at(0.0, 40.0)
        val b = at(500.0, -60.0)

        assertEquals(plot(listOf(stop("A", a), stop("B", b))).legs.single(), leg(a, b))
    }

    // ---- refused --------------------------------------------------------------------------------

    @Test
    fun `two consecutive stops at one position are refused before anything is routed, by either method`() {
        val a = at(0.0, 40.0)
        val b = at(500.0, -60.0)

        val e = assertThrows(IllegalArgumentException::class.java) { plot(listOf(stop("A", a), stop("B", b), stop("B again", b))) }
        assertEquals("stops 1 and 2 are at the same position", e.message)
        assertThrows(IllegalArgumentException::class.java) { leg(a, a) }
        assertEquals("nothing was routed", emptyList<Any>(), router.asked)
    }

    @Test
    fun `fewer than two stops are refused`() {
        assertThrows(IllegalArgumentException::class.java) { plot(listOf(stop("A", at(0.0, 40.0)))) }
    }

    // ---- straight legs, one per reason ----------------------------------------------------------

    private val a = at(0.0, 40.0)
    private val b = at(500.0, -60.0)

    /** Every reason [assertStraight] has asserted on this instance, read by `every reason is asserted by a test above`. */
    private val asserted = mutableSetOf<StraightReason>()

    /** The straight leg between [from] and [to] with [reason], one log line naming it, and its straight-line length. */
    private fun assertStraight(reason: StraightReason, leg: RouteLeg, from: LatLng = a, to: LatLng = b) {
        assertEquals(RouteLeg.Straight(listOf(from, to), GeoDistance.metersBetween(from, to), reason), leg)
        assertEquals("logged once: $logs", 1, logs.size)
        assertTrue("the log names the reason: ${logs.single()}", reason.name in logs.single())
        logs.clear()
        asserted += reason
    }

    @Test
    fun `neither stop in a downloaded area is a straight leg, NO_REGION_COVERS`() =
        assertStraight(StraightReason.NO_REGION_COVERS, leg(a, b, CountingRegions()))

    @Test
    fun `stops in different areas are a straight leg, IN_DIFFERENT_AREAS, and no engine is built`() {
        val far = at(30_000.0, 40.0)
        assertStraight(StraightReason.IN_DIFFERENT_AREAS, leg(a, far, CountingRegions(Ground.region(radiusKm = 1))), to = far)
        assertEquals(0, engines.asked)
    }

    @Test
    fun `routes not usable in the area holding both stops say why, and no engine is built`() {
        fun with(routeData: RegionRouteData) = leg(a, b, CountingRegions(Ground.region(routeData = routeData)))

        assertStraight(StraightReason.ROUTES_NOT_DOWNLOADED, with(RegionRouteData.NotFetched))
        assertStraight(StraightReason.ROUTES_NOT_DOWNLOADED, with(RegionRouteData.Missing(RouteDataMissingReason.NETWORK)))
        assertStraight(StraightReason.ROUTES_NEED_APP_UPDATE, with(RegionRouteData.Missing(RouteDataMissingReason.FORBIDDEN)))
        assertStraight(StraightReason.ROUTES_FETCHING, with(RegionRouteData.Fetching))
        assertStraight(StraightReason.ROUTES_UNREADABLE, with(RegionRouteData.Damaged("gone")))
        assertStraight(StraightReason.NO_TRAIL_ROUTES, with(RegionRouteData.NoneHere))
        // Ground's base lies in W125_N45.
        assertStraight(StraightReason.NO_TRAIL_ROUTES, with(RegionRouteData.Stored("run-1", "t", 10L, Ground.FOLDER, missingSquares = listOf("W125_N45"))))
        assertEquals(0, engines.asked)
    }

    @Test
    fun `regions that cannot be listed make every leg a straight one, ROUTES_UNREADABLE, each logged`() {
        val failing = CountingRegions(Result.failure(IOException("database closed")))
        val c = at(1_200.0, 30.0)

        val route = plot(listOf(stop("A", a), stop("B", b), stop("C", c)), failing)

        assertEquals(listOf(StraightReason.ROUTES_UNREADABLE, StraightReason.ROUTES_UNREADABLE), route.legs.map { (it as RouteLeg.Straight).reason })
        assertEquals("one log per leg: $logs", 2, logs.size)
    }

    @Test
    fun `an engine that could not be built is NO_ROUTE_FOUND, logged`() {
        engines.failWith = IOException("disk full")
        assertStraight(StraightReason.NO_ROUTE_FOUND, leg(a, b))
    }

    @Test
    fun `a stop over 250 m from any trail is START_ or END_FAR_FROM_TRAIL, by which stop`() {
        val farA = at(0.0, 300.0)
        val farB = at(500.0, 300.0)
        assertStraight(StraightReason.START_FAR_FROM_TRAIL, leg(farA, b), from = farA)
        assertStraight(StraightReason.END_FAR_FROM_TRAIL, leg(a, farB), to = farB)
    }

    @Test
    fun `the engine's other failures name their reasons`() {
        fun failing(failure: TrailRouteFailure): RouteLeg {
            router.failWith = failure
            return leg(a, b)
        }

        assertStraight(StraightReason.NO_ROUTE_FOUND, failing(TrailRouteFailure.NoPath("no track found")))
        assertStraight(StraightReason.NO_ROUTE_FOUND, failing(TrailRouteFailure.EngineFailed("timeout")))
        assertStraight(StraightReason.ROUTES_UNREADABLE, failing(TrailRouteFailure.DataMissing("gone")))
        assertStraight(StraightReason.ROUTES_UNREADABLE, failing(TrailRouteFailure.DataCorrupt("checksum")))
        assertStraight(StraightReason.NO_TRAIL_ROUTES, failing(TrailRouteFailure.EndOutsideData(RouteEnd.END, squareHasNoFile = true)))
    }

    @Test
    fun `a trail route that runs outside the area is LEAVES_AREA`() {
        router.via = listOf(at(250.0, 5_000.0))
        assertStraight(StraightReason.LEAVES_AREA, leg(a, b, CountingRegions(Ground.region(radiusKm = 1))))
    }

    /**
     * Reads which reasons the class's tests actually assert, rather than counting the enum (Sorrel N2,
     * docs/reviews/2026-10-30-sorrel-pr445-pr446.md): every other test in this class is run on a fresh instance, the way
     * JUnit runs it, and the reasons each passed through [assertStraight] are collected. A reason added later without a
     * case, or a case whose [assertStraight] line is deleted, fails this by name.
     */
    @Test
    fun `every reason is asserted by a test above`() {
        val self = "every reason is asserted by a test above"
        val reasons = PlotRouteUseCaseTest::class.java.declaredMethods
            .filter { it.isAnnotationPresent(Test::class.java) && it.name != self }
            .flatMap { method -> PlotRouteUseCaseTest().also { method.invoke(it) }.asserted }
            .toSet()

        assertEquals("not asserted: ${StraightReason.entries - reasons}", StraightReason.entries.toSet(), reasons)
    }

    @Test
    fun `the routes of a newer region holding only one stop are never used`() {
        val holdsBoth = Ground.region(id = 1, createdAt = 1_000L)
        val holdsOne = Ground.region(id = 2, centre = at(-1_000.0, 0.0), radiusKm = 1, createdAt = 5_000L, routeData = RegionRouteData.Stored("run-2", "t", 10L, File("/routes/2")))

        leg(at(-500.0, 40.0), at(1_500.0, -60.0), CountingRegions(holdsBoth, holdsOne))

        assertEquals(Ground.FOLDER, router.asked.single().third)
    }
}
