package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.FOLDER
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.way
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.wayTo
import com.zynergylabs.forager.app.domain.routing.RouteWay
import com.zynergylabs.forager.app.domain.routing.RouteWays
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * The M1 info line (dispatch 2026-11-06-01, Amendment 1, B1 and H2; the owner's TalkBack ruling's count): what it says,
 * that it carries no position, and that [RouteTurnsLog] writes one per route found, off the caller's thread.
 */
class RouteTurnsLogTest {

    private fun judged(node: Int, along: Double, kind: JunctionKind, ways: Int, judgement: JunctionJudgement) =
        RouteJunction(node, along, kind, ways, judgement)

    @Test
    fun `M1 - each judged point's index, distance along, turn, margin, class and band, with the count and the ways it leaves out, after the read's time`() {
        val turns = RouteTurns.Read(
            junctions = listOf(
                judged(3, 31.4, JunctionKind.JOIN_START, 3, JunctionJudgement(JunctionClass.NO_LEG)),
                judged(12, 118.6, JunctionKind.JUNCTION, 4, JunctionJudgement(JunctionClass.STRAIGHT_ON, 4.4, 85.6, leftPaths = 1, leftNotPaths = 1)),
                judged(20, 201.0, JunctionKind.JUNCTION, 4, JunctionJudgement(JunctionClass.STRAIGHT_ON, -2.0, 88.0, leftPaths = 1, rightPaths = 1)),
                judged(26, 260.2, JunctionKind.JUNCTION, 4, JunctionJudgement(JunctionClass.TURN, -89.6, -89.6, TurnBand.PLAIN, TurnSide.LEFT)),
                judged(31, 310.0, JunctionKind.JUNCTION, 3, JunctionJudgement(JunctionClass.FORK, 6.0, 30.0, keep = TurnSide.RIGHT)),
                judged(40, 399.5, JunctionKind.JUNCTION, 3, JunctionJudgement(JunctionClass.UNMATCHED)),
                judged(44, 440.0, JunctionKind.JOIN_END, 3, JunctionJudgement(JunctionClass.TURN, 151.0, -151.0, TurnBand.SHARP, TurnSide.RIGHT)),
            ),
            trackAheadAfterNode = null,
            reading = WaysReading(millis = 12L, reads = 2, ways = 9),
        )

        assertEquals(
            listOf(
                "M1 next turns, way to a waypoint: ways read in 12 ms (2 reads, 9 ways); 7 judged: 2 turns, 1 fork, 2 straight on, " +
                    "1 no leg, 0 short legs, 1 unmatched. " +
                    "#1 node 3, 31 m, join (start) of 3 ways: NO_LEG | " +
                    "#2 node 12, 119 m, junction of 4 ways: turn 4°, margin 86°, STRAIGHT_ON, paths 1 left 0 right, not paths 1 left 0 right | " +
                    "#3 node 20, 201 m, junction of 4 ways: turn -2°, margin 88°, STRAIGHT_ON, paths 1 left 1 right, not paths 0 left 0 right | " +
                    "#4 node 26, 260 m, junction of 4 ways: turn -90°, margin -90°, TURN, plain left, 2 same-side paths before, 1 same-side way not counted | " +
                    "#5 node 31, 310 m, junction of 3 ways: turn 6°, margin 30°, FORK, keep right | " +
                    "#6 node 40, 400 m, junction of 3 ways: UNMATCHED | " +
                    "#7 node 44, 440 m, join (end) of 3 ways: turn 151°, margin -151°, TURN, sharp right, 0 same-side paths before, 0 same-side ways not counted",
            ),
            routeTurnsLogLines(RouteKind.WAY_TO_WAYPOINT, turns),
        )
    }

    @Test
    fun `M1 - ways that could not be read say so, with the failure's kind and no message`() {
        val turns = RouteTurns.Unread("IOException", WaysReading(millis = 5L, reads = 1, ways = 0))

        assertEquals(listOf("M1 next turns, way home: ways unread (IOException) after 5 ms (1 read)."), routeTurnsLogLines(RouteKind.WAY_HOME, turns))
    }

    @Test
    fun `M1 - a long route's line is split into numbered parts, each under the log's own limit, none lost`() {
        val many = (1..200).map { judged(it * 10, it * 100.0, JunctionKind.JUNCTION, 3, JunctionJudgement(JunctionClass.STRAIGHT_ON, 1.0, 80.0, leftPaths = 1)) }
        val lines = routeTurnsLogLines(RouteKind.WAY_HOME, RouteTurns.Read(many, null, WaysReading(40L, 9, 900)))

        assertTrue("${lines.size} parts", lines.size > 1)
        lines.forEachIndexed { i, line ->
            assertTrue("part ${i + 1} is ${line.length} characters", line.length <= ROUTE_TURNS_LOG_LINE_MAX_CHARS)
            assertTrue(line, line.startsWith("M1 next turns, way home (${i + 1} of ${lines.size}): "))
        }
        assertEquals("every point once", 200, lines.sumOf { Regex("#\\d+ node").findAll(it).count() })
    }

    // RouteTurnsLog: one line per route found, written once its ways are read.

    private val crossroads = listOf(
        way(0.0 to -50.0, 0.0 to 200.0),
        way(0.0 to 200.0, -300.0 to 200.0),
        way(0.0 to 200.0, 0.0 to 400.0),
    )
    private val leftAt200 = wayTo(listOf(0.0 to -60.0, 0.0 to -50.0), listOf(0.0 to -50.0, 0.0 to 200.0, -300.0 to 200.0), listOf(-300.0 to 200.0, -300.0 to 250.0))

    private val infos = mutableListOf<Pair<String, String>>()
    private val warnings = mutableListOf<Throwable>()
    private val job = SupervisorJob()

    private fun log(ways: RouteWays) = RouteTurnsLog(
        scope = CoroutineScope(job + Dispatchers.Default),
        routeWays = ways,
        info = { tag, message -> synchronized(infos) { infos += tag to message } },
        errorLog = ErrorLog { _, _, error -> synchronized(warnings) { warnings += error } },
    )

    private fun settle() = runBlocking { job.children.forEach { it.join() } }

    @Test
    fun `M1 - a route found is logged once at info level, and a re-route again`() {
        val log = log(FakeRouteWays(crossroads))
        log.trailRouteFound(RouteKind.WAY_TO_WAYPOINT, leftAt200, FOLDER)
        settle()
        log.trailRouteFound(RouteKind.WAY_TO_WAYPOINT, leftAt200, FOLDER)
        settle()

        assertEquals(2, infos.size)
        infos.forEach { (tag, message) ->
            assertEquals(RouteTurnsLog.TAG, tag)
            assertTrue(message, message.startsWith("M1 next turns, way to a waypoint: ways read in "))
            assertTrue(message, message.contains("TURN, plain left"))
        }
        assertEquals(emptyList<Throwable>(), warnings)
    }

    @Test
    fun `M1 - ways that cannot be read are logged as unread, and the failure is warned of`() {
        log(FakeRouteWays.failing()).trailRouteFound(RouteKind.WAY_HOME, leftAt200, FOLDER)
        settle()

        assertTrue(infos.single().second, infos.single().second.startsWith("M1 next turns, way home: ways unread (IOException) after "))
        assertTrue("${warnings.single()}", warnings.single().cause is IOException)
    }

    @Test
    fun `M1 - a reader that throws is warned of and ends nothing else`() {
        val throwing = object : RouteWays {
            override suspend fun near(center: LatLng, radiusMeters: Double, routeData: File): Result<List<RouteWay>> = throw IllegalStateException("made-up")
        }
        log(throwing).trailRouteFound(RouteKind.WAY_HOME, leftAt200, FOLDER)
        settle()

        assertTrue("${warnings.single()}", warnings.single().cause is IllegalStateException)
        assertFalse("the scope is still open", job.isCancelled)
        assertEquals(emptyList<Pair<String, String>>(), infos)
    }

    @Test
    fun `M1 - the line carries no position, only whole numbers`() {
        log(FakeRouteWays(crossroads)).trailRouteFound(RouteKind.WAY_TO_WAYPOINT, leftAt200, FOLDER)
        settle()
        val message = infos.single().second

        assertFalse("no decimal figure: $message", Regex("\\d[.,]\\d").containsMatchIn(message))
    }
}
