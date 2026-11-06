package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.FOLDER
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.at
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.lineOf
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.toward
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.way
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.wayHome
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.wayTo
import com.zynergylabs.forager.app.domain.routing.RouteWay
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The junctions along a route, read through the project's own RouteWays and judged (dispatch 2026-11-06-01, Phase 2a;
 * Amendment 1, F1, B1, B2, B4), on made-up ways round the point P. Each route is built as the use cases build theirs, so
 * the trail route's points are the data's own points, and its ends are the engine's matched points on a way.
 */
class RouteTurnsTest {

    private fun turnsOf(way: WayHome, ways: List<RouteWay>): RouteTurns.Read {
        val turns = runBlocking { routeTurnsFor(way, lineOf(way), FakeRouteWays(ways), FOLDER) }
        assertTrue("read, not $turns", turns is RouteTurns.Read)
        return turns as RouteTurns.Read
    }

    private fun assertAt(way: WayHome, junction: RouteJunction, east: Double, north: Double) {
        val node = lineOf(way).nodes[junction.node]
        assertEquals("the junction's node is at ($east, $north)", 0.0, GeoDistance.metersBetween(node, at(east, north)), 0.01)
    }

    // A crossroads at P: ways south, north, west and east of it. The south and west ways have a shape point each.
    private val south = way(0.0 to -200.0, 0.0 to -100.0, 0.0 to 0.0)
    private val north = way(0.0 to 0.0, 0.0 to 200.0)
    private val west = way(0.0 to 0.0, -100.0 to 0.0, -200.0 to 0.0)
    private val east = way(0.0 to 0.0, 200.0 to 0.0)

    // B1

    @Test
    fun `B1 - turning left at a crossroads is a junction of four ways and a plain left turn`() {
        val way = wayTo(
            leadIn = listOf(0.0 to -250.0, 0.0 to -200.0),
            trail = listOf(0.0 to -200.0, 0.0 to -100.0, 0.0 to 0.0, -100.0 to 0.0, -200.0 to 0.0),
            finalLeg = listOf(-200.0 to 0.0, -250.0 to 0.0),
        )
        val j = turnsOf(way, listOf(south, north, west, east)).junctions.single()

        assertEquals(JunctionKind.JUNCTION, j.kind)
        assertEquals(4, j.ways)
        assertEquals(JunctionClass.TURN, j.judgement.junctionClass)
        assertEquals(TurnSide.LEFT, j.judgement.side)
        assertEquals(TurnBand.PLAIN, j.judgement.band)
        assertEquals(-90.0, j.judgement.routeTurnDegrees!!, 0.5)
        assertEquals(-90.0, j.judgement.marginDegrees!!, 0.5)
        assertAt(way, j, 0.0, 0.0)
        assertEquals("50 m of lead-in and 200 m of trail", 250.0, j.alongMeters, 0.5)
    }

    @Test
    fun `B1 - a bend within one way, however sharp, is no junction at all`() {
        val bend = way(0.0 to -200.0, 0.0 to 0.0, -200.0 to 0.0)
        val way = wayTo(listOf(0.0 to -250.0, 0.0 to -200.0), listOf(0.0 to -200.0, 0.0 to 0.0, -200.0 to 0.0), listOf(-200.0 to 0.0, -250.0 to 0.0))

        assertEquals(emptyList<RouteJunction>(), turnsOf(way, listOf(bend)).junctions)
    }

    @Test
    fun `B1 - carrying straight on through a T is a junction that is no turn, with its path to the left`() {
        val stub = way(0.0 to 0.0, -200.0 to 0.0)
        val way = wayTo(listOf(0.0 to -250.0, 0.0 to -200.0), listOf(0.0 to -200.0, 0.0 to -100.0, 0.0 to 0.0, 0.0 to 200.0), listOf(0.0 to 200.0, 0.0 to 250.0))
        val j = turnsOf(way, listOf(south, north, stub)).junctions.single()

        assertEquals(3, j.ways)
        assertEquals(JunctionClass.STRAIGHT_ON, j.judgement.junctionClass)
        assertEquals(1, j.judgement.leftPaths)
        assertEquals(0, j.judgement.rightPaths)
    }

    @Test
    fun `B1 - every way in the data counts, whatever its tags`() {
        val privateWay = RouteWay(listOf(at(0.0, 0.0), at(0.0, 200.0)), mapOf("highway" to "service", "access" to "private"))
        val way = wayTo(listOf(0.0 to -250.0, 0.0 to -200.0), listOf(0.0 to -200.0, 0.0 to -100.0, 0.0 to 0.0, 200.0 to 0.0), listOf(200.0 to 0.0, 250.0 to 0.0))
        val j = turnsOf(way, listOf(south, privateWay, east)).junctions.single()

        assertEquals("the private way straight on makes the route's right a turn", JunctionClass.TURN, j.judgement.junctionClass)
        assertEquals(TurnSide.RIGHT, j.judgement.side)
    }

    @Test
    fun `B5 - a driveway leaving a junction the route goes straight through is no path passed, and is counted apart`() {
        val driveway = RouteWay(listOf(at(0.0, 0.0), at(-60.0, 0.0)), mapOf("highway" to "service", "service" to "driveway"))
        val footway = RouteWay(listOf(at(0.0, 0.0), at(60.0, 0.0)), mapOf("highway" to "footway"))
        val way = wayTo(listOf(0.0 to -250.0, 0.0 to -200.0), listOf(0.0 to -200.0, 0.0 to -100.0, 0.0 to 0.0, 0.0 to 200.0), listOf(0.0 to 200.0, 0.0 to 250.0))
        val j = turnsOf(way, listOf(south, north, driveway, footway)).junctions.single().judgement

        assertEquals(JunctionClass.STRAIGHT_ON, j.junctionClass)
        assertEquals(0, j.leftPaths)
        assertEquals(1, j.leftNotPaths)
        assertEquals(1, j.rightPaths)
        assertEquals(0, j.rightNotPaths)
    }

    // B2: the joins, "with the trail's two directions as the ways there".

    private val trail = way(0.0 to -300.0, 0.0 to 300.0)

    @Test
    fun `B2 - where the lead-in reaches the trail between its points, picking left is a turn`() {
        val way = wayTo(listOf(-50.0 to 0.0, 0.0 to 0.0), listOf(0.0 to 0.0, 0.0 to 300.0), listOf(0.0 to 300.0, 0.0 to 350.0))
        val j = turnsOf(way, listOf(trail)).junctions.single()

        assertEquals(JunctionKind.JOIN_START, j.kind)
        assertEquals("the lead-in and the trail's two directions", 3, j.ways)
        assertEquals(JunctionClass.TURN, j.judgement.junctionClass)
        assertEquals(TurnSide.LEFT, j.judgement.side)
        assertEquals(0.0, j.judgement.marginDegrees!!, 0.5)
        assertAt(way, j, 0.0, 0.0)
    }

    @Test
    fun `B2 - where the final leg leaves a trail that carries on, the way along the trail is straighter, so it is a turn`() {
        val way = wayTo(listOf(0.0 to -350.0, 0.0 to -300.0), listOf(0.0 to -300.0, 0.0 to 100.0), listOf(0.0 to 100.0, 60.0 to 100.0))
        val j = turnsOf(way, listOf(trail)).junctions.single()

        assertEquals(JunctionKind.JOIN_END, j.kind)
        assertEquals(JunctionClass.TURN, j.judgement.junctionClass)
        assertEquals(TurnSide.RIGHT, j.judgement.side)
        assertAt(way, j, 0.0, 100.0)
    }

    @Test
    fun `B2 - a final leg from the trail's own end is no junction`() {
        val endsHere = way(0.0 to -300.0, 0.0 to 100.0)
        val way = wayTo(listOf(0.0 to -350.0, 0.0 to -300.0), listOf(0.0 to -300.0, 0.0 to 100.0), listOf(0.0 to 100.0, 60.0 to 100.0))

        assertEquals(emptyList<RouteJunction>(), turnsOf(way, listOf(endsHere)).junctions)
    }

    @Test
    fun `B2 - a lead-in meeting the trail at a shallow angle carries straight on, with the trail behind to the right`() {
        val from = toward(0.0, 0.0, 195.0, 50.0)
        val way = wayTo(listOf(from, 0.0 to 0.0), listOf(0.0 to 0.0, 0.0 to 300.0), listOf(0.0 to 300.0, 0.0 to 350.0))
        val j = turnsOf(way, listOf(trail)).junctions.single()

        assertEquals(JunctionClass.STRAIGHT_ON, j.judgement.junctionClass)
        assertEquals(-15.0, j.judgement.routeTurnDegrees!!, 0.5)
        assertEquals(1, j.judgement.rightPaths)
    }

    @Test
    fun `B2 - a lead-in of no length, the walker on the trail, gives no direction to judge`() {
        val way = wayTo(listOf(0.0 to 0.0, 0.0 to 0.0), listOf(0.0 to 0.0, 0.0 to 300.0), listOf(0.0 to 300.0, 0.0 to 350.0))
        val j = turnsOf(way, listOf(trail)).junctions.single()

        assertEquals(JunctionKind.JOIN_START, j.kind)
        assertEquals(JunctionClass.NO_LEG, j.judgement.junctionClass)
        assertEquals(null, j.judgement.side)
    }

    @Test
    fun `B2 - the way home's two joints are joins, judged where the joint meets the trail route`() {
        val longTrail = way(0.0 to -300.0, 0.0 to -200.0, 0.0 to 0.0, 0.0 to 200.0, 0.0 to 300.0)
        val way = wayHome(
            trackBack = listOf(-100.0 to -100.0, -10.0 to -100.0),
            trail = listOf(-10.0 to -100.0, 0.0 to -100.0, 0.0 to 0.0, 0.0 to 100.0, -10.0 to 100.0),
            trackToStart = listOf(-10.0 to 100.0, -100.0 to 100.0),
            jointMeters = 20.0,
        )
        val turns = turnsOf(way, listOf(longTrail))
        val (first, last) = turns.junctions

        assertEquals(listOf(JunctionKind.JOIN_START, JunctionKind.JOIN_END), turns.junctions.map { it.kind })
        assertAt(way, first, 0.0, -100.0)
        assertEquals(JunctionClass.TURN, first.judgement.junctionClass)
        assertEquals(TurnSide.LEFT, first.judgement.side)
        assertAt(way, last, 0.0, 100.0)
        assertEquals(JunctionClass.TURN, last.judgement.junctionClass)
        assertEquals(TurnSide.LEFT, last.judgement.side)
        assertEquals("the walker's own track follows the last joint", last.node, turns.trackAheadAfterNode)
    }

    @Test
    fun `B2 - a way to a waypoint has no track ahead of unknown turns`() {
        val way = wayTo(listOf(-50.0 to 0.0, 0.0 to 0.0), listOf(0.0 to 0.0, 0.0 to 300.0), listOf(0.0 to 300.0, 0.0 to 350.0))

        assertEquals(null, turnsOf(way, listOf(trail)).trackAheadAfterNode)
    }

    // B4: "each turn in its turn, never merged, dog-legs included."

    @Test
    fun `B4 - a dog-leg's two turns 12 m apart stay two turns`() {
        val ways = listOf(
            way(0.0 to -200.0, 0.0 to 0.0),
            way(0.0 to 0.0, 0.0 to 200.0),
            way(0.0 to 0.0, 12.0 to 0.0),
            way(12.0 to 0.0, 12.0 to 200.0),
            way(12.0 to 0.0, 200.0 to 0.0),
        )
        val way = wayTo(listOf(0.0 to -250.0, 0.0 to -200.0), listOf(0.0 to -200.0, 0.0 to 0.0, 12.0 to 0.0, 12.0 to 200.0), listOf(12.0 to 200.0, 12.0 to 250.0))
        val turns = turnsOf(way, ways).junctions

        assertEquals(listOf(JunctionClass.TURN, JunctionClass.TURN), turns.map { it.judgement.junctionClass })
        assertEquals(listOf(TurnSide.RIGHT, TurnSide.LEFT), turns.map { it.judgement.side })
        assertEquals(12.0, turns[1].alongMeters - turns[0].alongMeters, 0.5)
    }

    // The data not matching the route.

    @Test
    fun `a junction whose way out is not among the data's ways there is unmatched, not a guess`() {
        val elsewhere = way(10.0 to 30.0, 10.0 to 200.0)
        val way = wayTo(listOf(0.0 to -250.0, 0.0 to -200.0), listOf(0.0 to -200.0, 0.0 to -100.0, 0.0 to 0.0, 10.0 to 30.0, 10.0 to 200.0), listOf(10.0 to 200.0, 10.0 to 250.0))
        val j = turnsOf(way, listOf(south, north, east, elsewhere)).junctions.single()

        assertEquals(JunctionClass.UNMATCHED, j.judgement.junctionClass)
        assertEquals(3, j.ways)
        assertEquals(null, j.judgement.routeTurnDegrees)
    }

    @Test
    fun `ways that cannot be read are unread, saying which failure and never its message`() {
        val way = wayTo(listOf(-50.0 to 0.0, 0.0 to 0.0), listOf(0.0 to 0.0, 0.0 to 300.0), listOf(0.0 to 300.0, 0.0 to 350.0))
        val turns = runBlocking { routeTurnsFor(way, lineOf(way), FakeRouteWays.failing(), FOLDER) }

        assertTrue("unread, not $turns", turns is RouteTurns.Unread)
        assertEquals("IOException", (turns as RouteTurns.Unread).why)
        assertEquals(1, turns.reading.reads)
    }

    // F1 and M2: the reading.

    @Test
    fun `F1 - a 3 km route is read in reads round stretches of it, keeps only the ways it touches, and is timed`() {
        val long = way(0.0 to -200.0, 0.0 to 1_000.0, 0.0 to 2_000.0, 0.0 to 3_000.0)
        val side = way(0.0 to 3_000.0, -200.0 to 3_000.0)
        val onward = way(0.0 to 3_000.0, 0.0 to 3_200.0)
        val away = way(500.0 to 500.0, 700.0 to 500.0)
        val way = wayTo(listOf(0.0 to -250.0, 0.0 to -200.0), listOf(0.0 to -200.0, 0.0 to 1_000.0, 0.0 to 2_000.0, 0.0 to 3_000.0, -200.0 to 3_000.0), listOf(-200.0 to 3_000.0, -250.0 to 3_000.0))
        val clock = sequenceOf(5_000_000L, 17_000_000L).iterator()
        val ways = FakeRouteWays(listOf(long, side, onward, away))
        val turns = runBlocking { routeTurnsFor(way, lineOf(way), ways, FOLDER, nanoTime = { clock.next() }) } as RouteTurns.Read

        assertEquals("the junction 3 km on is found", JunctionClass.TURN, turns.junctions.single().judgement.junctionClass)
        assertTrue("more than one read: ${ways.reads}", ways.reads >= 2)
        assertEquals(WaysReading(millis = 12L, reads = ways.reads, ways = 3), turns.reading)
    }
}
