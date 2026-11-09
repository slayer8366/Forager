package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.FOLDER
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.at
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.lineOf
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.way
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.wayHome
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.wayTo
import com.zynergylabs.forager.app.domain.routing.RouteWay
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import com.zynergylabs.forager.app.domain.wayhome.WayHomePlace
import com.zynergylabs.forager.app.domain.wayhome.WayHomeProgress
import com.zynergylabs.forager.app.domain.wayhome.placeOnWayHome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The next turn from the walker's place (dispatch 2026-11-06-01, Phase 2a; Amendment 1, D1, E1, E2; the owner's TalkBack
 * ruling's count). The walker is placed by the app's own [placeOnWayHome] from each fix, in walking order, as
 * `WayHomeWatch` and `AvailabilityWaypointRoute` place them. Made-up ways round the point P; every line's points are on
 * its 10 m node step, so a fix on a node is exact.
 */
class NextTurnAheadTest {

    /** A walk along [way]: each fix placed after the last, and the next turn read at each. */
    private class Walk(way: WayHome, ways: List<RouteWay>) {
        val line: WayHomeLine = lineOf(way)
        val turns: RouteTurns = runBlocking { routeTurnsFor(way, line, FakeRouteWays(ways), FOLDER) }
        private var last: WayHomePlace? = null
        var progress: WayHomeProgress? = null
            private set

        fun at(east: Double, north: Double): NextTurn {
            val fix = TurnGround.at(east, north)
            val next = placeOnWayHome(line, fix, last)
            if (next is WayHomeProgress.Placed) last = next.place
            progress = next
            return nextTurn(NextTurnWay.AlongTrails(line, turns), next, fix)
        }
    }

    private fun turn(next: NextTurn): NextTurn.Turn {
        assertTrue("a turn, not $next", next is NextTurn.Turn)
        return next as NextTurn.Turn
    }

    // North up the trail to a crossroads at 200 m north, left there, then a final leg: the turn is 260 m along the line.
    private val leftAt200 = wayTo(
        leadIn = listOf(0.0 to -60.0, 0.0 to -50.0),
        trail = listOf(0.0 to -50.0, 0.0 to 200.0, -300.0 to 200.0),
        finalLeg = listOf(-300.0 to 200.0, -300.0 to 250.0),
    )
    private val crossroads200 = listOf(
        way(0.0 to -50.0, 0.0 to 200.0),
        way(0.0 to 200.0, -300.0 to 200.0),
        way(0.0 to 200.0, 0.0 to 400.0),
        way(0.0 to 200.0, 300.0 to 200.0),
    )

    // D1: "the figure counts from the needle's place, never below 0. A turn is passed only when the walker's place
    // passes its node, so it never passes early, and the place's only-forward rule is the hysteresis."

    /** The line's metres from the node nearest [east], [north] to [walk]'s turn or fork at [node]: what the needle's place reads. */
    private fun fromNearestNode(walk: Walk, node: Int, east: Double, north: Double): Double {
        val fix = at(east, north)
        val nearest = walk.line.nodes.indices.minBy { GeoDistance.metersBetween(walk.line.nodes[it], fix) }
        return walk.line.alongMeters[node] - walk.line.alongMeters[nearest]
    }

    private fun nodeOf(walk: Walk, junctionClass: JunctionClass, nth: Int = 0) =
        (walk.turns as RouteTurns.Read).junctions.filter { it.judgement.junctionClass == junctionClass }[nth].node

    @Test
    fun `D1 - the distance counts from the needle's place, not the walker's place behind it`() {
        val walk = Walk(leftAt200, crossroads200)
        val next = turn(walk.at(0.0, 170.0))
        val turnNode = nodeOf(walk, JunctionClass.TURN)
        val place = (walk.progress as WayHomeProgress.Placed).place.index

        assertEquals(TurnBand.PLAIN, next.band)
        assertEquals(TurnSide.LEFT, next.side)
        // The needle's place is the node nearest the walker, 30 m before the turn; the walker's place, the earliest node
        // within 25 m, is about 20 m further back.
        assertEquals(fromNearestNode(walk, turnNode, 0.0, 170.0), next.metersAway, 1e-9)
        assertEquals(30.0, next.metersAway, 5.0)
        val fromPlace = walk.line.alongMeters[turnNode] - walk.line.alongMeters[place]
        assertTrue("from the place it would read $fromPlace", fromPlace > next.metersAway + 15.0)
    }

    @Test
    fun `D1 - just past the turn's node, the place not yet past it, the same turn reads 0, never below`() {
        val walk = Walk(leftAt200, crossroads200)
        walk.at(0.0, 170.0)
        val next = turn(walk.at(-10.0, 200.0))

        assertEquals(TurnSide.LEFT, next.side)
        assertEquals(0.0, next.metersAway, 0.0)
    }

    @Test
    fun `D1 - a turn is passed only when the walker's place passes its node`() {
        val walk = Walk(leftAt200, crossroads200)
        walk.at(0.0, 170.0)
        walk.at(-10.0, 200.0)

        assertEquals("20 m past, the place is still before the node", TurnSide.LEFT, turn(walk.at(-20.0, 200.0)).side)
        assertEquals("30 m past, the place has passed it", NextTurn.NoTurnLeft, walk.at(-30.0, 200.0))
    }

    @Test
    fun `D1 - the next of two turns shows once the first is passed`() {
        val ways = listOf(
            way(0.0 to -50.0, 0.0 to 100.0),
            way(0.0 to 100.0, 0.0 to 300.0),
            way(0.0 to 100.0, 200.0 to 100.0),
            way(200.0 to 100.0, 200.0 to 400.0),
            way(200.0 to 100.0, 400.0 to 100.0),
        )
        val way = wayTo(listOf(0.0 to -60.0, 0.0 to -50.0), listOf(0.0 to -50.0, 0.0 to 100.0, 200.0 to 100.0, 200.0 to 400.0), listOf(200.0 to 400.0, 200.0 to 450.0))
        val walk = Walk(way, ways)

        assertEquals(TurnSide.RIGHT, turn(walk.at(0.0, -60.0)).side)
        walk.at(0.0, 50.0)
        walk.at(30.0, 100.0)
        val second = turn(walk.at(60.0, 100.0))
        assertEquals(TurnSide.LEFT, second.side)
        assertEquals(fromNearestNode(walk, nodeOf(walk, JunctionClass.TURN, nth = 1), 60.0, 100.0), second.metersAway, 1e-9)
        assertEquals(140.0, second.metersAway, 5.0)
    }

    // The owner's TalkBack ruling: "If other paths leave on the same side before your turn, it counts them".

    private val threeJunctions = listOf(
        way(0.0 to -50.0, 0.0 to 100.0),
        way(0.0 to 100.0, -200.0 to 100.0), // a path left at 100 m
        way(0.0 to 100.0, 0.0 to 200.0),
        way(0.0 to 200.0, -200.0 to 200.0), // a crossroads at 200 m: a path left
        way(0.0 to 200.0, 200.0 to 200.0), // and one right
        way(0.0 to 200.0, 0.0 to 300.0),
        way(0.0 to 300.0, -300.0 to 300.0), // the turn at 300 m, left or right
        way(0.0 to 300.0, 300.0 to 300.0),
        way(0.0 to 300.0, 0.0 to 500.0),
    )

    private fun turnAt300(eastAfter: Double) = wayTo(
        listOf(0.0 to -60.0, 0.0 to -50.0),
        listOf(0.0 to -50.0, 0.0 to 100.0, 0.0 to 200.0, 0.0 to 300.0, eastAfter to 300.0),
        listOf(eastAfter to 300.0, eastAfter to 350.0),
    )

    @Test
    fun `the count - the paths leaving on the turn's side at the junctions passed straight through before it`() {
        val next = turn(Walk(turnAt300(-300.0), threeJunctions).at(0.0, -60.0))

        assertEquals(TurnSide.LEFT, next.side)
        assertEquals("the paths left at 100 m and 200 m: \"take the third left\"", 2, next.sameSidePathsBefore)
    }

    @Test
    fun `the count - paths on the other side are not counted`() {
        val next = turn(Walk(turnAt300(300.0), threeJunctions).at(0.0, -60.0))

        assertEquals(TurnSide.RIGHT, next.side)
        assertEquals("the one path right, at 200 m", 1, next.sameSidePathsBefore)
    }

    @Test
    fun `the count - a path already passed is not counted`() {
        val walk = Walk(turnAt300(-300.0), threeJunctions)
        walk.at(0.0, 100.0)
        val next = turn(walk.at(0.0, 140.0))

        assertEquals(1, next.sameSidePathsBefore)
    }

    // B5, the owner: "Walkable ways only (Recommended)": "Driveways, parking-lot lanes and motorway slips don't, so a
    // driveway never makes it \"the second left\"."

    private fun tagged(highway: String, vararg eastNorth: Pair<Double, Double>) = RouteWay(eastNorth.map { at(it) }, mapOf("highway" to highway))

    private val withDriveways = listOf(
        way(0.0 to -50.0, 0.0 to 100.0),
        tagged("service", 0.0 to 100.0, -200.0 to 100.0), // a driveway left at 100 m
        way(0.0 to 100.0, 0.0 to 200.0),
        tagged("footway", 0.0 to 200.0, -200.0 to 200.0), // a footpath left at 200 m
        tagged("motorway_link", 0.0 to 200.0, 200.0 to 200.0), // a motorway slip right
        way(0.0 to 200.0, 0.0 to 300.0),
        way(0.0 to 300.0, -300.0 to 300.0),
        way(0.0 to 300.0, 300.0 to 300.0),
        way(0.0 to 300.0, 0.0 to 500.0),
    )

    @Test
    fun `B5 - the count passes over driveways and motorway slips, counting only walkable ways`() {
        assertEquals("the footpath alone: \"take the second left\"", 1, turn(Walk(turnAt300(-300.0), withDriveways).at(0.0, -60.0)).sameSidePathsBefore)
        assertEquals("the slip is not a path: \"turn right\"", 0, turn(Walk(turnAt300(300.0), withDriveways).at(0.0, -60.0)).sameSidePathsBefore)
    }

    // E1: the owner, "Blank (Recommended)": "The space keeps its place, empty, so nothing moves."

    @Test
    fun `E1 - every card state but a figure blanks the space, whatever lies ahead`() {
        val ahead = turn(Walk(leftAt200, crossroads200).at(0.0, 170.0))
        val blanks = mapOf(
            NextTurnCardState.ARRIVED to NextTurnBlank.ARRIVED,
            NextTurnCardState.NO_DISTANCE to NextTurnBlank.NO_DISTANCE,
            NextTurnCardState.NO_FIX to NextTurnBlank.NO_FIX,
            NextTurnCardState.WITHIN_FIX_CIRCLE to NextTurnBlank.WITHIN_FIX_CIRCLE,
        )
        for ((card, reason) in blanks) assertEquals("$card", NextTurn.Blank(reason), nextTurnSpace(card, ahead))
        assertEquals(ahead, nextTurnSpace(NextTurnCardState.SHOWS_DISTANCE, ahead))
        assertEquals(NextTurn.NoTurnLeft, nextTurnSpace(NextTurnCardState.SHOWS_DISTANCE, NextTurn.NoTurnLeft))
    }

    @Test
    fun `E1 - off the line by more than the far band, the route is held back and the space is blank`() {
        assertEquals(NextTurn.Blank(NextTurnBlank.ROUTE_WITHHELD), Walk(leftAt200, crossroads200).at(300.0, 0.0))
    }

    @Test
    fun `while the way, its junctions or the walker's place are not yet known, the space is blank`() {
        val line = lineOf(leftAt200)
        val turns = runBlocking { routeTurnsFor(leftAt200, line, FakeRouteWays(crossroads200), FOLDER) }
        val fix = at(0.0, 170.0)
        val placed = placeOnWayHome(line, fix, null)
        val notYet = NextTurn.Blank(NextTurnBlank.NOT_YET_KNOWN)

        assertEquals(notYet, nextTurn(NextTurnWay.Pending, null, fix))
        assertEquals(notYet, nextTurn(NextTurnWay.AlongTrails(line, null), placed, fix))
        assertEquals(notYet, nextTurn(NextTurnWay.AlongTrails(line, turns), null, null))
    }

    // E2: "For instances without trails, when there is no routing data, then keep it blank."

    @Test
    fun `E2 - a way with no trail route is blank`() {
        assertEquals(NextTurn.Blank(NextTurnBlank.NO_TRAIL_ROUTE), nextTurn(NextTurnWay.NoTrailRoute, null, at(0.0, 0.0)))
    }

    // The way home: the walker's track back, a joint onto a trail that ends where the last joint leaves it, then the
    // walker's track to the start. The first joint is a left turn.
    private val homeTrail = listOf(way(0.0 to -300.0, 0.0 to -200.0, 0.0 to 0.0, 0.0 to 100.0))

    private fun home(trackToStart: List<Pair<Double, Double>>) = wayHome(
        trackBack = listOf(-100.0 to -100.0, -10.0 to -100.0),
        trail = listOf(-10.0 to -100.0, 0.0 to -100.0, 0.0 to 0.0, 0.0 to 100.0, -10.0 to 100.0),
        trackToStart = trackToStart,
        jointMeters = 20.0,
    )

    @Test
    fun `E2 - on the way home's track back, the next turn is the coming joint`() {
        val walk = Walk(home(listOf(-10.0 to 100.0, -100.0 to 100.0)), homeTrail)
        val next = turn(walk.at(-100.0, -100.0))

        assertEquals(TurnSide.LEFT, next.side)
        assertEquals(fromNearestNode(walk, nodeOf(walk, JunctionClass.TURN), -100.0, -100.0), next.metersAway, 1e-9)
        assertEquals("from the walker's newest point, 90 m of track and 10 m of joint", 100.0, next.metersAway, 0.5)
    }

    @Test
    fun `E2 - with no known turn left before the walker's own track to the start, and past the last joint, the space is blank`() {
        val walk = Walk(home(listOf(-10.0 to 100.0, -100.0 to 100.0)), homeTrail)
        val unknown = NextTurn.Blank(NextTurnBlank.TRACK_TURNS_UNKNOWN)
        walk.at(-100.0, -100.0)
        walk.at(-10.0, -100.0)
        walk.at(0.0, -70.0)

        assertEquals("on the trail, past the joint's turn", unknown, walk.at(0.0, -40.0))
        walk.at(0.0, 0.0)
        walk.at(0.0, 50.0)
        walk.at(0.0, 100.0)
        walk.at(-20.0, 100.0)
        walk.at(-50.0, 100.0)
        assertEquals("on the track to the start", unknown, walk.at(-80.0, 100.0))
    }

    @Test
    fun `E2 - a way home whose trail ends at the start has nothing unknown ahead, so no turn left is said`() {
        val walk = Walk(home(listOf(-10.0 to 100.0)), homeTrail)
        walk.at(-100.0, -100.0)
        walk.at(-10.0, -100.0)
        walk.at(0.0, -70.0)

        assertEquals(NextTurn.NoTurnLeft, walk.at(0.0, -40.0))
    }

    // "No turn left must never stand in for unknown."

    @Test
    fun `an unmatched junction ahead blanks the space until it is passed, then what lies beyond shows`() {
        val ways = listOf(
            way(0.0 to -50.0, 0.0 to 0.0),
            way(0.0 to 0.0, 0.0 to 300.0),
            way(0.0 to 0.0, 200.0 to 0.0),
            way(10.0 to 30.0, 10.0 to 300.0),
        )
        val way = wayTo(listOf(0.0 to -60.0, 0.0 to -50.0), listOf(0.0 to -50.0, 0.0 to 0.0, 10.0 to 30.0, 10.0 to 300.0), listOf(10.0 to 300.0, 10.0 to 350.0))
        val walk = Walk(way, ways)

        assertEquals(NextTurn.Blank(NextTurnBlank.JUNCTION_UNREAD), walk.at(0.0, -60.0))
        walk.at(0.0, -20.0)
        walk.at(10.0, 30.0)
        assertEquals(NextTurn.NoTurnLeft, walk.at(10.0, 70.0))
    }

    @Test
    fun `ways that could not be read blank the space, never no turn left`() {
        val line = lineOf(leftAt200)
        val unread = runBlocking { routeTurnsFor(leftAt200, line, FakeRouteWays.failing(), FOLDER) }
        val fix = at(0.0, 170.0)

        assertEquals(NextTurn.Blank(NextTurnBlank.WAYS_UNREAD), nextTurn(NextTurnWay.AlongTrails(line, unread), placeOnWayHome(line, fix, null), fix))
    }

    @Test
    fun `with every junction ahead known and none a turn, no turn is left`() {
        val walk = Walk(turnAt300(-300.0), threeJunctions)
        walk.at(0.0, 100.0)
        walk.at(0.0, 200.0)
        walk.at(0.0, 300.0)
        walk.at(-30.0, 300.0)

        assertEquals(NextTurn.NoTurnLeft, walk.at(-60.0, 300.0))
    }

    @Test
    fun `a fork ahead is the next turn, with its distance`() {
        val ways = listOf(
            way(0.0 to -50.0, 0.0 to 100.0),
            way(0.0 to 100.0, 0.0 to 300.0),
            way(0.0 to 100.0, 100.0 to 273.2), // 30 degrees right of the route's way
        )
        val way = wayTo(listOf(0.0 to -60.0, 0.0 to -50.0), listOf(0.0 to -50.0, 0.0 to 100.0, 0.0 to 300.0), listOf(0.0 to 300.0, 0.0 to 350.0))
        val walk = Walk(way, ways)
        val next = walk.at(0.0, -60.0)

        assertTrue("a fork, not $next", next is NextTurn.Fork)
        assertEquals(fromNearestNode(walk, nodeOf(walk, JunctionClass.FORK), 0.0, -60.0), (next as NextTurn.Fork).metersAway, 1e-9)
        assertEquals(160.0, next.metersAway, 0.5)
    }
}
