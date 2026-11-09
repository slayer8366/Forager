package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.wayto.AcceptEnds
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.WayHomeNote
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointNote
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * [TrailRoutingEngine] (dispatch 2026-10-26-10, step 6 PR 2): one engine for the way home and the way to a waypoint,
 * built on first use, its profile installed once; a failed build keeps nothing, so the next ask tries again, and each
 * feature logs it once in its own words.
 */
class TrailRoutingEngineTest {

    private val logs = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logs += message }
    private val walk = Ground.track(Ground.at(0.0, 0.0), Ground.at(0.0, 100.0))
    private val walker = Ground.at(0.0, 40.0)
    private val waypoint = Ground.at(500.0, -60.0)

    @Test
    fun `one engine is built for both the way home and the way to a waypoint`() {
        var installs = 0
        var routersBuilt = 0
        val engine = TrailRoutingEngine(
            installProfile = { installs++; File("unused") },
            errorLog = errorLog,
            ioDispatcher = Dispatchers.Unconfined,
            newRouter = { routersBuilt++; BRouterTrailRouter(it, errorLog) },
        )
        val wayHome = WayHomeRouting(engine, FakeRegions(), errorLog)
        // A region whose routes are recorded as stored at a folder that is not there: the way to the waypoint gets as
        // far as the engine, which finds no data and says so.
        val wayTo = FindWayToWaypointUseCase(FakeRegions(Ground.region()), engine, errorLog, AcceptEnds)

        runBlocking {
            assertEquals(WayToWaypointResult.StraightLine(WayToWaypointNote.ROUTES_UNREADABLE), wayTo(walker, waypoint))
            assertEquals(WayHomeResult.TrackOnly(WayHomeNote.NO_TRAIL_ROUTES), wayHome.find(walk, null))
            assertEquals(WayToWaypointResult.StraightLine(WayToWaypointNote.ROUTES_UNREADABLE), wayTo(walker, waypoint))
            assertEquals(WayHomeResult.TrackOnly(WayHomeNote.NO_TRAIL_ROUTES), wayHome.find(walk, null))
            assertSame(engine.engine().getOrThrow(), engine.engine().getOrThrow())
        }
        assertEquals("the profile installed once", 1, installs)
        assertEquals("one router for both features", 1, routersBuilt)
    }

    @Test
    fun `a failed build is handed back, keeps nothing, and each feature logs it once in its own words`() {
        var installs = 0
        val engine = TrailRoutingEngine({ installs++; throw IOException("disk full") }, errorLog, Dispatchers.Unconfined)
        val wayHome = WayHomeRouting(engine, FakeRegions(), errorLog)
        val wayTo = FindWayToWaypointUseCase(FakeRegions(Ground.region()), engine, errorLog, AcceptEnds)

        runBlocking {
            assertEquals(WayToWaypointResult.StraightLine(WayToWaypointNote.NO_ROUTE_FOUND), wayTo(walker, waypoint))
            assertEquals(WayHomeResult.TrackOnly(WayHomeNote.NO_ROUTE_FOUND), wayHome.find(walk, null))
        }
        assertEquals("tried again", 2, installs)
        assertEquals("$logs", 1, logs.count { "the way home follows the track" in it })
        assertEquals("$logs", 1, logs.count { "The way to the waypoint is the straight line" in it && "profile could not be installed" in it })
        assertEquals("$logs", 2, logs.size)
    }
}
