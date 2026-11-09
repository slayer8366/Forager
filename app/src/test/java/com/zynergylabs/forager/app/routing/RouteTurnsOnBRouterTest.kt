package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.nextturn.JunctionClass
import com.zynergylabs.forager.app.domain.nextturn.JunctionKind
import com.zynergylabs.forager.app.domain.nextturn.RouteKind
import com.zynergylabs.forager.app.domain.nextturn.RouteTurns
import com.zynergylabs.forager.app.domain.nextturn.RouteTurnsLog
import com.zynergylabs.forager.app.domain.nextturn.routeTurnsFor
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routing.TrailEngine
import com.zynergylabs.forager.app.domain.routing.TrailEngineSource
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import com.zynergylabs.forager.app.domain.wayto.AcceptEnds
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointResult
import com.zynergylabs.forager.app.routing.RoutingTestData.ACROSS_FROM
import com.zynergylabs.forager.app.routing.RoutingTestData.ACROSS_TO
import com.zynergylabs.forager.app.routing.RoutingTestData.NO_TRAIL_NEAR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.roundToLong

/**
 * The next turn's junctions and its M1 line on the real vendored BRouter and the public routing fixture with heights
 * (`src/test/resources/routing/segments-heighted/W75_N40.rd5`, Harriman; dispatch 2026-11-06-01, Phase 2a). The routes are
 * the use cases' own, found through their real entry points, and the junctions are read with the app's own
 * [BRouterRouteWays] (F1). Nothing is stubbed but the list of downloaded regions. No position is printed or asserted on.
 */
class RouteTurnsOnBRouterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val warned = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> synchronized(warned) { warned += message } }

    private val profile: File by lazy { RoutingTestData.profileDir(tmp) }
    private val router by lazy { BRouterTrailRouter(profile, errorLog) }
    private val data: File by lazy { RoutingTestData.heightedSegments(tmp) }
    private val routeWays by lazy { BRouterRouteWays(profile) }
    private val engines = object : TrailEngineSource {
        override suspend fun engine(): Result<TrailEngine> = Result.success(TrailEngine(router, BRouterTrailSnapper(router, TrailSnapMethod.SNAP)))
    }

    private val infos = mutableListOf<String>()
    private val job = SupervisorJob()
    private val turnsLog by lazy { RouteTurnsLog(CoroutineScope(job + Dispatchers.Default), routeWays, { _, message -> synchronized(infos) { infos += message } }, errorLog) }
    private val found = mutableListOf<Pair<WayHome, File>>()

    private fun settle() = runBlocking { job.children.forEach { it.join() } }

    private fun wayTo(from: LatLng, waypoint: LatLng): WayToWaypointResult = runBlocking {
        val region = Ground.region(id = 7, centre = ACROSS_FROM, radiusKm = 20, routeData = RegionRouteData.Stored("run", "t", 10L, data))
        FindWayToWaypointUseCase(FakeRegions(region), engines, errorLog, AcceptEnds, trailRouteFound = { way, folder ->
            found += way to folder
            turnsLog.trailRouteFound(RouteKind.WAY_TO_WAYPOINT, way, folder)
        })(from, waypoint)
    }

    private fun alongTrails(result: WayToWaypointResult): WayHome {
        assertTrue("along trails, not $result (log: $warned)", result is WayToWaypointResult.AlongTrails)
        return (result as WayToWaypointResult.AlongTrails).way
    }

    // M1, through the way to a waypoint's own entry point.

    @Test
    fun `M1 - the way to a waypoint tells of its trail route once, with its route data, and the line follows with no position`() {
        val way = alongTrails(wayTo(ACROSS_FROM, ACROSS_TO))
        settle()

        assertEquals(listOf(way to data), found)
        val message = infos.single()
        assertTrue(message, message.startsWith("M1 next turns, way to a waypoint: ways read in "))
        assertTrue("some junction judged: $message", message.contains(" junction of "))
        assertFalse("no decimal figure, so no coordinate: $message", Regex("\\d[.,]\\d").containsMatchIn(message))
    }

    @Test
    fun `M1 - every search that finds a trail route logs again, as a re-route does`() {
        alongTrails(wayTo(ACROSS_FROM, ACROSS_TO))
        alongTrails(wayTo(ACROSS_TO, ACROSS_FROM))
        settle()

        assertEquals(2, found.size)
        assertEquals(2, infos.size)
    }

    @Test
    fun `M1 - a straight line tells of no trail route and logs nothing`() {
        assertTrue(wayTo(ACROSS_FROM, NO_TRAIL_NEAR) is WayToWaypointResult.StraightLine)
        settle()

        assertEquals(emptyList<Pair<WayHome, File>>(), found)
        assertEquals(emptyList<String>(), infos)
    }

    // M1, through the way home's own entry point (WayHomeRouting, what WayHomeWatch calls).

    @Test
    fun `M1 - the way home tells of its trail route once, and the line follows`() {
        val outOnTrails = RoutingTestData.commandLineRoute("inside-across-the-degree-line", "brouter-cli/heighted").take(57)
        val turn = outOnTrails.last()
        val perDegree = GeoDistance.metersPerDegree(turn.lat)
        val west = (2..8).map { LatLng(turn.lat, turn.lng - it * 25.0 / perDegree.lng) }
        val track = Track("t", null, 0L, null, (outOnTrails + west).mapIndexed { i, p -> TrackPoint(p.lat, p.lng, null, 5f, i * 1_000L) })
        val region = Ground.region(id = 7, centre = turn, radiusKm = 5, routeData = RegionRouteData.Stored("run", "t", 10L, data))
        val routing = WayHomeRouting(engines, FakeRegions(region), errorLog, trailRouteFound = { wayHome, folder ->
            found += wayHome to folder
            turnsLog.trailRouteFound(RouteKind.WAY_HOME, wayHome, folder)
        })

        val result = runBlocking { routing.find(track, null) }
        settle()

        assertTrue("along trails, not $result (log: $warned)", result is WayHomeResult.AlongTrails)
        assertEquals(listOf((result as WayHomeResult.AlongTrails).wayHome to data), found)
        assertTrue(infos.single(), infos.single().startsWith("M1 next turns, way home: ways read in "))
    }

    // F1: the reads along the route find every junction a read of the whole area finds.

    private data class Key(val lat: Long, val lng: Long)

    private fun key(p: LatLng) = Key((p.lat * 1e6).roundToLong(), (p.lng * 1e6).roundToLong())

    @Test
    fun `F1 - every junction the trail route passes, as a read of the whole area counts them, is judged, in walking order`() {
        val way = alongTrails(wayTo(ACROSS_FROM, ACROSS_TO))
        val turns = runBlocking { routeTurnsFor(way, WayHomeLine(way, trailAllowanceMeters = 0.0), routeWays, data) }
        assertTrue("read, not $turns", turns is RouteTurns.Read)
        turns as RouteTurns.Read

        // The whole area round the route, in one read, and each graph node's ways counted by their ends (Phase 1's way).
        val route = way.trail.points
        val centre = LatLng((route.minOf { it.lat } + route.maxOf { it.lat }) / 2, (route.minOf { it.lng } + route.maxOf { it.lng }) / 2)
        val radius = route.maxOf { GeoDistance.metersBetween(centre, it) } + 1_000.0
        val all = runBlocking { routeWays.near(centre, radius, data) }.getOrThrow()
        val ends = HashMap<Key, Int>()
        for (w in all) {
            ends.merge(key(w.points.first()), 1) { a, b -> a + b }
            ends.merge(key(w.points.last()), 1) { a, b -> a + b }
        }
        val interior = route.subList(1, route.lastIndex).map(::key).fold(mutableListOf<Key>()) { runs, k -> if (runs.lastOrNull() != k) runs += k; runs }
        val expected = interior.count { (ends[it] ?: 0) >= 3 }

        val judged = turns.junctions.filter { it.kind == JunctionKind.JUNCTION }
        assertTrue("the route passes junctions: $expected", expected > 0)
        assertEquals("junctions judged against junctions in the data", expected, judged.size)
        assertEquals("in walking order", turns.junctions.map { it.node }.sorted(), turns.junctions.map { it.node })
        assertTrue("some turn: ${judged.map { it.judgement.junctionClass }}", judged.any { it.judgement.junctionClass == JunctionClass.TURN })
        assertTrue("read in ${turns.reading.reads} reads", turns.reading.reads >= 1)
    }
}
