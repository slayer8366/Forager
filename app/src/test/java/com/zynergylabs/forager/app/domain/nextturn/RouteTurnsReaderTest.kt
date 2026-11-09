package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.FOLDER
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.lineOf
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.way
import com.zynergylabs.forager.app.domain.nextturn.TurnGround.wayTo
import com.zynergylabs.forager.app.domain.routing.RouteWay
import com.zynergylabs.forager.app.domain.routing.RouteWays
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The reader the holders hold (dispatch 2026-11-06-01, Amendment 1, H4, 2b-1): the same junctions [routeTurnsFor] gives, and
 * every failure an explicit [RouteTurns.Unread], warned of, never a throw and never a guess. On made-up ways round P.
 */
class RouteTurnsReaderTest {

    private val warned = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> warned += message }

    private val trail = way(0.0 to -300.0, 0.0 to 300.0)
    private val way = wayTo(listOf(-50.0 to 0.0, 0.0 to 0.0), listOf(0.0 to 0.0, 0.0 to 300.0), listOf(0.0 to 300.0, 0.0 to 350.0))
    private val clock = { 0L }

    @Test
    fun `it reads the junctions routeTurnsFor reads, from the folder it is given`() {
        val folders = mutableListOf<File>()
        val ways = object : RouteWays {
            override suspend fun near(center: LatLng, radiusMeters: Double, routeData: File): Result<List<RouteWay>> {
                folders += routeData
                return FakeRouteWays(listOf(trail)).near(center, radiusMeters, routeData)
            }
        }

        val read = runBlocking { RouteWaysTurnsReader(ways, errorLog, clock).turnsFor(way, lineOf(way), FOLDER) }

        assertEquals(runBlocking { routeTurnsFor(way, lineOf(way), FakeRouteWays(listOf(trail)), FOLDER, clock) }, read)
        assertTrue("a turn at the join: $read", (read as RouteTurns.Read).junctions.single().judgement.junctionClass == JunctionClass.TURN)
        assertEquals("from the route's own folder", listOf(FOLDER), folders.distinct())
        assertEquals(emptyList<String>(), warned)
    }

    @Test
    fun `with no route data folder it reads nothing and says so`() {
        val ways = FakeRouteWays(listOf(trail))

        val read = runBlocking { RouteWaysTurnsReader(ways, errorLog, clock).turnsFor(way, lineOf(way), null) }

        assertEquals(RouteTurns.Unread("no route data folder", WaysReading(0L, 0, 0)), read)
        assertEquals("nothing read", 0, ways.reads)
        assertEquals(1, warned.count { "no route data folder" in it })
    }

    @Test
    fun `a reader that throws gives unread, naming the failure's class and never its message, warned of`() {
        val throwing = object : RouteWays {
            override suspend fun near(center: LatLng, radiusMeters: Double, routeData: File): Result<List<RouteWay>> =
                throw IllegalStateException("made-up square name")
        }

        val read = runBlocking { RouteWaysTurnsReader(throwing, errorLog, clock).turnsFor(way, lineOf(way), FOLDER) }

        assertTrue("unread, not $read", read is RouteTurns.Unread)
        assertEquals("IllegalStateException", (read as RouteTurns.Unread).why)
        assertTrue("warned: $warned", warned.single().contains("IllegalStateException"))
        assertTrue("never the message: $warned", warned.none { "made-up square name" in it })
    }

    @Test
    fun `a read that fails is unread as routeTurnsFor says, and warned of`() {
        val read = runBlocking { RouteWaysTurnsReader(FakeRouteWays.failing(), errorLog, clock).turnsFor(way, lineOf(way), FOLDER) }

        assertEquals("IOException", (read as RouteTurns.Unread).why)
        assertEquals(1, warned.count { "IOException" in it })
    }
}
