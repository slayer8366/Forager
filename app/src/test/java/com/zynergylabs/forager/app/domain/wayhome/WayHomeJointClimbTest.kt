package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.ClimbWalks
import com.zynergylabs.forager.app.domain.ClimbWalks.Stop
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.WayBackClimb
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * The climb inside each joint between the walker's track and the trail route is counted from the track's own barometric
 * heights, and the step between those heights and the trail data's still is not (dispatch 2026-10-30-06 Amendment 2,
 * RECORD -1842; the owner: "Count the joint climb"). Through the way home's real entry point, [FindWayHomeUseCase], and
 * the climb the walker's place reads ([placeOnWayHome]).
 *
 * The walks here are slow on purpose: a point every 5 m and every 65 s, past the barometer's one-minute window, so each
 * point's barometric height is its own reading and a 10 m rise over a 20 m joint is not smoothed away. The trail runs
 * east along north = 0 from the start; the trail data puts it 20 m above the barometer's heights, a datum offset a
 * counted step would add as climb at one joint and descent at the other.
 */
class WayHomeJointClimbTest {

    private val routePoints = listOf(at(500.0, 0.0), at(375.0, 0.0), at(250.0, 0.0), at(125.0, 0.0), at(0.0, 0.0))

    private val logs = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logs += message }

    /** Straight legs through [stops], a point every 5 m and 65 s, pressure and GPS height both the stop's true height. */
    private fun slowWalk(stops: List<Stop>): List<TrackPoint> {
        val samples = ArrayList<Stop>()
        samples += stops.first()
        for ((a, b) in stops.zipWithNext()) {
            val steps = ceil(hypot(b.east - a.east, b.north - a.north) / 5.0).toInt().coerceAtLeast(1)
            for (s in 1..steps) {
                val t = s.toDouble() / steps
                samples += Stop(a.east + (b.east - a.east) * t, a.north + (b.north - a.north) * t, a.height + (b.height - a.height) * t)
            }
        }
        return samples.mapIndexed { i, stop ->
            val p = at(stop.east, stop.north)
            TrackPoint(
                lat = p.lat,
                lng = p.lng,
                altitude = 100.0 + stop.height,
                accuracyMeters = 4f,
                timestampEpochMillis = 1_000_000L + i * 65_000L,
                speedMetersPerSecond = 0.1f,
                speedAccuracyMetersPerSecond = 0.5f,
                pressureHpa = ClimbWalks.pressureAt(100.0 + stop.height).toFloat(),
            )
        }
    }

    /** The climb from the walk's far end, its last stop, on the way home along the trail route with [trailHeight]. */
    private fun climbHome(stops: List<Stop>, trailHeight: Double): WayBackClimb.Measured {
        val track = Ground.track().copy(points = slowWalk(stops))
        val found = runBlocking {
            FindWayHomeUseCase(
                FakeRegions(Ground.region()),
                FakeRouter(FakeRouter.found(500, routePoints, List(routePoints.size) { trailHeight })),
                FakeSnapper(listOf(at(0.0, 0.0) to at(1_000.0, 0.0))),
                errorLog,
            )(track, null)
        }
        assertTrue("precondition: along trails, not $found (log: $logs)", found is WayHomeResult.AlongTrails)
        val wayHome = (found as WayHomeResult.AlongTrails).wayHome
        assertTrue("precondition: the trail part carries the trail data's heights, not ${wayHome.trail.heights}", wayHome.trail.heights is PartHeights.TrailData)
        val far = stops.last()
        val placed = placeOnWayHome(WayHomeLine(wayHome), at(far.east, far.north), null)
        assertTrue("placed, not $placed", placed is WayHomeProgress.Placed)
        val climb = (placed as WayHomeProgress.Placed).place.climb
        assertTrue("measured, not $climb", climb is WayBackClimb.Measured)
        return climb as WayBackClimb.Measured
    }

    @Test
    fun `the leave joint's 10 m climb is counted, and the 20 m datum offset is not`() {
        // Out: along the trail at 10 m, then 20 m north off it, down to 0 m, then on into the forest, level. Home: level
        // through the forest, 10 m up over the leave joint, then the trail, level by the trail data at 130 m against the
        // barometer's 110 m. Counted as the step, the offset would add 20 m up here and 20 m down at the entry joint.
        val stops = listOf(Stop(0.0, 0.0, 10.0), Stop(500.0, 0.0, 10.0), Stop(500.0, 20.0, 0.0), Stop(500.0, 200.0, 0.0))
        val climb = climbHome(stops, trailHeight = 130.0)
        assertEquals("the leave joint's 10 m up, and no step: $climb", 10.0, climb.climbMeters, 0.5)
        assertEquals("nothing down, and no step at the entry joint: $climb", 0.0, climb.descentMeters, 0.5)
    }

    @Test
    fun `the entry joint's 10 m climb is counted, and the 20 m datum offset is not`() {
        // Out: from a start 20 m south of the trail at 10 m, down to the trail at 0 m, along it level, then 100 m north
        // into the forest, level. Home: the trail, level by the trail data at 120 m against the barometer's 100 m, then
        // 10 m up over the entry joint to the start.
        val stops = listOf(Stop(0.0, -20.0, 10.0), Stop(0.0, 0.0, 0.0), Stop(500.0, 0.0, 0.0), Stop(500.0, 100.0, 0.0))
        val climb = climbHome(stops, trailHeight = 120.0)
        assertEquals("the entry joint's 10 m up, and no step: $climb", 10.0, climb.climbMeters, 0.5)
        assertEquals("nothing down, and no step at the leave joint: $climb", 0.0, climb.descentMeters, 0.5)
    }

    @Test
    fun `with level joints a 20 m datum offset counts nothing`() {
        val stops = listOf(Stop(0.0, 0.0, 0.0), Stop(500.0, 0.0, 0.0), Stop(500.0, 20.0, 0.0), Stop(500.0, 200.0, 0.0))
        val climb = climbHome(stops, trailHeight = 120.0)
        assertEquals("no climb: $climb", 0.0, climb.climbMeters, 0.5)
        assertEquals("no descent: $climb", 0.0, climb.descentMeters, 0.5)
    }
}
