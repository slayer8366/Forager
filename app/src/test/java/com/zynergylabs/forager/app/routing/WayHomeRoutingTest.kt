package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.WayHomeNote
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * [WayHomeRouting] (dispatch 2026-10-24-10 Amendment 1, PR B): the engine's profile is installed on the first way home,
 * once per process; a failed install is logged and gives the track with "Couldn't find a trail route", and the next
 * Return tries again.
 */
class WayHomeRoutingTest {

    private val logs = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logs += message }
    private val walk = Ground.track(Ground.at(0.0, 0.0), Ground.at(0.0, 100.0))

    @Test
    fun `the profile is installed once, on the first way home`() {
        var installs = 0
        val routing = WayHomeRouting({ installs++; File("unused") }, FakeRegions(), errorLog, Dispatchers.Unconfined)
        runBlocking {
            // No downloaded region: the use case answers without the engine.
            assertEquals(WayHomeResult.TrackOnly(WayHomeNote.NO_TRAIL_ROUTES), routing.find(walk, null))
            assertEquals(WayHomeResult.TrackOnly(WayHomeNote.NO_TRAIL_ROUTES), routing.find(walk, null))
        }
        assertEquals(1, installs)
    }

    @Test
    fun `a failed install gives the track, logged, and the next Return tries again`() {
        var installs = 0
        val routing = WayHomeRouting({ installs++; throw IOException("disk full") }, FakeRegions(), errorLog, Dispatchers.Unconfined)
        runBlocking {
            assertEquals(WayHomeResult.TrackOnly(WayHomeNote.NO_ROUTE_FOUND), routing.find(walk, null))
            assertEquals(WayHomeResult.TrackOnly(WayHomeNote.NO_ROUTE_FOUND), routing.find(walk, null))
        }
        assertEquals("tried again", 2, installs)
        assertEquals("each failure logged once: $logs", 2, logs.count { "profile could not be installed" in it })
    }
}
