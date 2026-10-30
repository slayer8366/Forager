package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.ClimbWalks
import com.zynergylabs.forager.app.domain.ClimbWalks.Heights
import com.zynergylabs.forager.app.domain.ClimbWalks.Stop
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.WayBackClimb
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trail part's heights from the trail data (dispatch 2026-10-30-06 Amendment 1, RECORD -1836, -1838), through the
 * way home's real entry point, [FindWayHomeUseCase], and the climb the walker's place reads ([placeOnWayHome]). A fake
 * router gives the heights until a heighted route fixture lands.
 *
 * The walk is `WayHomeClimbTest`'s: out along a trail running east from the start, climbing 40 m over 500 m by the
 * barometer, then 200 m north into the forest, climbing 10 m more. The trail data says otherwise about the trail, on
 * purpose: it runs over a 60 m hump, and it sits near 1,000 m, about 860 m above the barometer's heights, as a terrain
 * model and a GPS-anchored barometer can disagree (far more than they would, so a counted step cannot hide).
 */
class WayHomeTrailHeightsTest {

    private val stops = listOf(Stop(0.0, 0.0, 0.0), Stop(500.0, 0.0, 40.0), Stop(500.0, 200.0, 50.0))
    private val routePoints = listOf(at(500.0, 0.0), at(375.0, 0.0), at(250.0, 0.0), at(125.0, 0.0), at(0.0, 0.0))
    private val hump = listOf(1_000.0, 1_030.0, 1_060.0, 1_030.0, 1_000.0)
    private val farPoint = at(500.0, 200.0)

    private val logs = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logs += message }

    private fun wayHome(heights: Heights, routeHeights: List<Double?>): WayHome {
        val track = Ground.track().copy(points = ClimbWalks.walk(stops, heights, endAt = 10_000_000L))
        val found = runBlocking {
            FindWayHomeUseCase(
                FakeRegions(Ground.region()),
                FakeRouter(FakeRouter.found(500, routePoints, routeHeights)),
                FakeSnapper(listOf(at(0.0, 0.0) to at(1_000.0, 0.0))),
                errorLog,
            )(track, null)
        }
        assertTrue("precondition: along trails, not $found (log: $logs)", found is WayHomeResult.AlongTrails)
        return (found as WayHomeResult.AlongTrails).wayHome
    }

    private fun climbAtTheFarPoint(wayHome: WayHome): WayBackClimb? {
        val placed = placeOnWayHome(WayHomeLine(wayHome), farPoint, null)
        assertTrue("placed, not $placed", placed is WayHomeProgress.Placed)
        return (placed as WayHomeProgress.Placed).place.climb
    }

    private fun measured(climb: WayBackClimb?): WayBackClimb.Measured {
        assertTrue("measured, not $climb", climb is WayBackClimb.Measured)
        return climb as WayBackClimb.Measured
    }

    @Test
    fun `with a height at every route point the trail part carries the trail data's, and the joints none`() {
        val wayHome = wayHome(Heights.BAROMETER, hump)
        val trail = wayHome.trail.heights
        assertTrue("the trail part's heights are the trail data's, not $trail", trail is PartHeights.TrailData)
        trail as PartHeights.TrailData
        assertEquals("one sample per trail part point: the leave point, the route, the entry point", wayHome.trail.points.size, trail.samples.size)
        assertEquals("the route's heights, in walking order", hump, trail.samples.drop(1).dropLast(1).map { it.height })
        assertNull("the leave joint has no height", trail.samples.first().height)
        assertNull("nor the entry joint", trail.samples.last().height)
        assertEquals("the entry point at the trail part's own metres", wayHome.trail.meters, trail.samples.last().alongMeters, 0.01)
        val leaveJoint = GeoDistance.metersBetween(wayHome.trail.points[0], wayHome.trail.points[1])
        assertEquals("the route's first point at the end of the leave joint", leaveJoint, trail.samples[1].alongMeters, 0.01)
        assertEquals("the route's middle point halfway along the engine's 500 m", leaveJoint + 250.0, trail.samples[3].alongMeters, 0.01)
        assertEquals("nothing fell back, nothing logged", emptyList<String>(), logs)
    }

    @Test
    fun `the climb home counts the trail data's hump, not the walker's track along the trail`() {
        val climb = measured(climbAtTheFarPoint(wayHome(Heights.BAROMETER, hump)))
        // The forest: 10 m down by the barometer. The trail: 60 m up and 60 m down by the trail data. The stand-in would
        // have read the trail as 40 m down and nothing up.
        assertEquals("up the hump", 60.0, climb.climbMeters, 4.0)
        assertEquals("out of the forest and down the hump", 70.0, climb.descentMeters, 8.0)
    }

    @Test
    fun `the step between the barometer's heights and the trail data's is never counted`() {
        // A level trail in the trail data, some 860 m above the barometer's heights: counted, the step would be 860 m up
        // at the leave joint and as much down at the entry joint.
        val climb = measured(climbAtTheFarPoint(wayHome(Heights.BAROMETER, List(5) { 1_000.0 })))
        assertEquals("no climb from the step at either joint", 0.0, climb.climbMeters, 4.0)
        assertEquals("only the forest's 10 m down, no step", 10.0, climb.descentMeters, 8.0)
    }

    @Test
    fun `a route with only some points heighted uses the walker's track, logged once with the counts`() {
        val wayHome = wayHome(Heights.BAROMETER, listOf(1_000.0, 1_030.0, null, 1_030.0, 1_000.0))
        val trail = wayHome.trail.heights
        assertTrue("the stand-in, not $trail", trail is PartHeights.Barometric && trail.standIn)
        assertEquals("logged once: $logs", 1, logs.size)
        assertTrue("the log gives the counts: ${logs.single()}", "4 of 5 trail route points have heights" in logs.single())
    }

    @Test
    fun `a route with no heights uses the walker's track, logged once`() {
        val wayHome = wayHome(Heights.BAROMETER, List(5) { null })
        val trail = wayHome.trail.heights
        assertTrue("the stand-in, not $trail", trail is PartHeights.Barometric && trail.standIn)
        assertEquals("logged once: $logs", 1, logs.size)
        assertTrue("the log gives the counts: ${logs.single()}", "0 of 5 trail route points have heights" in logs.single())
        assertEquals("and the stand-in's climb: 50 m down, as before", 50.0, measured(climbAtTheFarPoint(wayHome)).descentMeters, 8.0)
    }

    @Test
    fun `with no barometer there is still no allowance, whatever heights the trail data has`() {
        val wayHome = wayHome(Heights.GPS_ONLY, hump)
        assertEquals("the trail part too", PartHeights.NoBarometer, wayHome.trail.heights)
        assertEquals(WayBackClimb.NoBarometer, climbAtTheFarPoint(wayHome))
        assertEquals("nothing to fall back from, nothing logged", emptyList<String>(), logs)
    }
}
