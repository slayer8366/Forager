package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.nextturn.TurnGround.FOLDER
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.at
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.lineOf
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.toward
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.way
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.wayHome
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.wayTo
import com.zynergylabs.forager.app.domain.routing.RouteWay
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.placeOnWayHome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H3 (dispatch 2026-11-06-01, Amendment 1; the planner's call from S22 session 21, open to the owner): "a join whose lead-in
 * (or final leg) is shorter than the 20 m measuring distance is not judged, so it is never a turn." On made-up ways round
 * the point P. The lengths either side of 20 m pin the measure; the lead-ins of 50 m in [RouteTurnsTest] stay judged.
 */
class ShortLegJoinTest {

    private fun turnsOf(way: WayHome, ways: List<RouteWay>): RouteTurns.Read {
        val turns = runBlocking { routeTurnsFor(way, lineOf(way), FakeRouteWays(ways), FOLDER) }
        assertTrue("read, not $turns", turns is RouteTurns.Read)
        return turns as RouteTurns.Read
    }

    /** A trail running north through P, between its points, so a lead-in reaching P meets three ways. */
    private val trail = way(0.0 to -300.0, 0.0 to 300.0)

    /** A lead-in of [meters] from the west to P, then north along the trail to its own end (no junction there). */
    private fun leadInFromWest(meters: Double) =
        wayTo(listOf(-meters to 0.0, 0.0 to 0.0), listOf(0.0 to 0.0, 0.0 to 300.0), listOf(0.0 to 300.0, 0.0 to 350.0))

    private fun startJoin(way: WayHome) = turnsOf(way, listOf(trail)).junctions.single { it.kind == JunctionKind.JOIN_START }

    @Test
    fun `H3 - a lead-in of 5 m onto the trail is not judged, so picking left there is no turn`() {
        val j = startJoin(leadInFromWest(5.0))

        assertEquals(JunctionClass.SHORT_LEG, j.judgement.junctionClass)
        assertEquals("not judged: no side", null, j.judgement.side)
        assertEquals("not judged: no angle", null, j.judgement.routeTurnDegrees)
    }

    @Test
    fun `H3 - a lead-in of 19 m is under the 20 m measure and not judged`() {
        assertEquals(JunctionClass.SHORT_LEG, startJoin(leadInFromWest(19.0)).judgement.junctionClass)
    }

    @Test
    fun `H3 - a lead-in of 21 m is judged, and picking left is a turn, as B2 has it`() {
        val j = startJoin(leadInFromWest(21.0))

        assertEquals(JunctionClass.TURN, j.judgement.junctionClass)
        assertEquals(TurnSide.LEFT, j.judgement.side)
    }

    @Test
    fun `H3 - a final leg of 10 m leaving a trail that carries on is not judged`() {
        val way = wayTo(listOf(0.0 to -350.0, 0.0 to -300.0), listOf(0.0 to -300.0, 0.0 to 100.0), listOf(0.0 to 100.0, 10.0 to 100.0))
        val j = turnsOf(way, listOf(trail)).junctions.single()

        assertEquals(JunctionKind.JOIN_END, j.kind)
        assertEquals(JunctionClass.SHORT_LEG, j.judgement.junctionClass)
    }

    @Test
    fun `H3 - a final leg of 25 m leaving the same trail is judged, and is a turn`() {
        val way = wayTo(listOf(0.0 to -350.0, 0.0 to -300.0), listOf(0.0 to -300.0, 0.0 to 100.0), listOf(0.0 to 100.0, 25.0 to 100.0))
        val j = turnsOf(way, listOf(trail)).junctions.single()

        assertEquals(JunctionClass.TURN, j.judgement.junctionClass)
        assertEquals(TurnSide.RIGHT, j.judgement.side)
    }

    @Test
    fun `H3 - the way home's first joint, after only 15 m of track back, is not judged, and its last joint still is`() {
        val longTrail = way(0.0 to -300.0, 0.0 to -200.0, 0.0 to 0.0, 0.0 to 200.0, 0.0 to 300.0)
        val way = wayHome(
            trackBack = listOf(-15.0 to -100.0, -10.0 to -100.0),
            trail = listOf(-10.0 to -100.0, 0.0 to -100.0, 0.0 to 0.0, 0.0 to 100.0, -10.0 to 100.0),
            trackToStart = listOf(-10.0 to 100.0, -100.0 to 100.0),
            jointMeters = 20.0,
        )
        val (first, last) = turnsOf(way, listOf(longTrail)).junctions

        assertEquals(JunctionKind.JOIN_START, first.kind)
        assertEquals("5 m of track back and the 10 m joint", JunctionClass.SHORT_LEG, first.judgement.junctionClass)
        assertEquals(JunctionKind.JOIN_END, last.kind)
        assertEquals("100 m to the start after it", JunctionClass.TURN, last.judgement.junctionClass)
    }

    @Test
    fun `H3 - a junction 15 m from the line's start is a junction, not a join, and is still judged`() {
        // H3 is about the joins only. A lead-in of 5 m reaches the trail straight on; the crossroads 10 m on is judged.
        val ways = listOf(way(0.0 to -300.0, 0.0 to 10.0), way(0.0 to 10.0, 0.0 to 300.0), way(0.0 to 10.0, -200.0 to 10.0), way(0.0 to 10.0, 200.0 to 10.0))
        val way = wayTo(listOf(0.0 to -5.0, 0.0 to 0.0), listOf(0.0 to 0.0, 0.0 to 10.0, -200.0 to 10.0), listOf(-200.0 to 10.0, -250.0 to 10.0))
        val crossroads = turnsOf(way, ways).junctions.single { it.kind == JunctionKind.JUNCTION }

        assertEquals(JunctionClass.TURN, crossroads.judgement.junctionClass)
        assertEquals(TurnSide.LEFT, crossroads.judgement.side)
    }

    // What the walker gets: the planner's case on the S22, a walker standing almost on the trail.

    @Test
    fun `H3 - a walker 5 m from the trail gets no turn at the join, and with no junction after it, the straight-ahead arrow`() {
        val way = leadInFromWest(5.0)
        val line = lineOf(way)
        val turns = turnsOf(way, listOf(trail))
        val here = at(-5.0, 0.0)

        assertEquals(NextTurn.NoTurnLeft, nextTurn(NextTurnWay.AlongTrails(line, turns), placeOnWayHome(line, here, null), here))
    }

    @Test
    fun `H3 - a short lead-in's trail behind is no path passed before the next turn`() {
        // A lead-in of 5 m meets the trail at a shallow angle, the trail behind it to the right, then the route turns right
        // at a T 200 m on. Judged, the join would carry straight on and count the trail behind as a path on the right.
        val ways = listOf(way(0.0 to -300.0, 0.0 to 200.0), way(0.0 to 200.0, 0.0 to 300.0), way(0.0 to 200.0, 200.0 to 200.0))
        val from = toward(0.0, 0.0, 195.0, 5.0)
        val way = wayTo(listOf(from, 0.0 to 0.0), listOf(0.0 to 0.0, 0.0 to 200.0, 200.0 to 200.0), listOf(200.0 to 200.0, 250.0 to 200.0))
        val line = lineOf(way)
        val turns = turnsOf(way, ways)
        val here = at(from)

        val next = nextTurn(NextTurnWay.AlongTrails(line, turns), placeOnWayHome(line, here, null), here)
        assertTrue("a turn, not $next", next is NextTurn.Turn)
        next as NextTurn.Turn
        assertEquals(TurnSide.RIGHT, next.side)
        assertEquals("\"turn right\": the short lead-in's trail behind is not counted", 0, next.sameSidePathsBefore)
    }

    // M1: the planner reads it on the S22, so a short leg says so.

    @Test
    fun `H3 - the M1 line counts short legs apart and names each one, with no position`() {
        val turns = turnsOf(leadInFromWest(5.0), listOf(trail))
        val line = routeTurnsLogLines(RouteKind.WAY_TO_WAYPOINT, turns).single()

        assertTrue(line, line.contains("1 judged: 0 turns, 0 forks, 0 straight on, 0 no leg, 1 short leg, 0 unmatched."))
        assertTrue(line, line.contains("join (start) of 3 ways: SHORT_LEG"))
    }
}
