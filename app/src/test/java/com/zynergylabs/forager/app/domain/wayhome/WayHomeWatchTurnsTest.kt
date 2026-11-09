package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.nextturn.RouteTurns
import com.zynergylabs.forager.app.domain.nextturn.RouteTurnsReader
import com.zynergylabs.forager.app.domain.nextturn.WaysReading
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLines.hairpin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

/**
 * The way home's junctions held beside its line (dispatch 2026-11-06-01, Amendment 1, H4, 2b-1: "`routeTurnsFor` held where
 * each line is held (WayHomeWatch, AvailabilityWaypointRoute) with its route data folder, recomputed on each re-route"),
 * through [WayHomeWatch]'s own entry points: Return ([WayHomeWatch.find]), the service's fixes ([WayHomeWatch.onFix]) and
 * Stop. The reader is made up, so each read waits on its own answer; nothing a user sees changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WayHomeWatchTurnsTest {

    private val scope = TestScope(StandardTestDispatcher())
    private val errorLog = ErrorLog { _, _, _ -> }
    private val track = Track("t1", null, 0L, null, listOf(TrackPoint(45.0, -120.0, null, 5f, 0L), TrackPoint(45.001, -120.0, null, 5f, 1L)))

    private val answers = mutableListOf<CompletableDeferred<WayHomeResult>>()

    /** One read asked of the reader: what for, from which folder, and its answer to give. */
    private class Read(val wayHome: WayHome, val line: WayHomeLine, val routeData: File?) {
        val answer = CompletableDeferred<RouteTurns>()
        var cancelled = false
    }

    private val reads = mutableListOf<Read>()
    private val reader = RouteTurnsReader { wayHome, line, routeData ->
        val read = Read(wayHome, line, routeData).also { reads += it }
        try {
            read.answer.await()
        } catch (e: CancellationException) {
            read.cancelled = true
            throw e
        }
    }

    private val watch = WayHomeWatch(
        scope = scope,
        readTrack = { Result.success(track) },
        findWayHome = { _: Track, _: Waypoint? -> CompletableDeferred<WayHomeResult>().also { answers += it }.await() },
        isReturning = { true },
        errorLog = errorLog,
        readTurns = reader,
    )

    private val folder = File("/made-up/routes/region-1")
    private val alongTrails = WayHomeResult.AlongTrails(hairpin, routeData = folder)
    private fun turns(ways: Int) = RouteTurns.Read(emptyList(), trackAheadAfterNode = null, reading = WaysReading(0L, 1, ways))

    private fun gps(east: Double, north: Double) = at(east, north).let { TrackPoint(it.lat, it.lng, null, 5f, 0L) }

    private fun found(result: WayHomeResult) {
        scope.runCurrent()
        answers.last().complete(result)
        scope.runCurrent()
    }

    @Test
    fun `H4 - the way home's junctions are read from the folder it was found on, for the line shown, and held beside it`() {
        watch.find("t1", null)
        found(alongTrails)
        val shown = watch.shown.value!!
        assertNull("while they are read, none", shown.turns)

        val read = reads.single()
        assertSame("the way home found", hairpin, read.wayHome)
        assertSame("the line shown", shown.line, read.line)
        assertEquals("the route data it was found on", folder, read.routeData)

        read.answer.complete(turns(9))
        scope.runCurrent()
        assertEquals(turns(9), watch.shown.value!!.turns)
        assertEquals("the line is unchanged", shown.copy(turns = turns(9)), watch.shown.value)
    }

    @Test
    fun `H4 - a re-route's line has its junctions read afresh, and the old line's late answer never lands on it`() {
        watch.find("t1", null)
        found(alongTrails)
        reads.single().answer.complete(turns(1))
        scope.runCurrent()

        watch.onFix(gps(-60.0, -50.0), FixProvider.GPS) // more than 50 m off the hairpin: one re-route
        scope.runCurrent()
        assertEquals("the line shown keeps its turns meanwhile", turns(1), watch.shown.value!!.turns)
        found(alongTrails)
        assertEquals("the new line's turns are being read", 2, reads.size)
        assertNull(watch.shown.value!!.turns)
        assertSame(watch.shown.value!!.line, reads[1].line)

        reads[1].answer.complete(turns(2))
        scope.runCurrent()
        assertEquals(turns(2), watch.shown.value!!.turns)
    }

    @Test
    fun `H4 - a read still running when the next line comes is cancelled and never shown`() {
        watch.find("t1", null)
        found(alongTrails)
        val first = reads.single()

        watch.onFix(gps(-60.0, -50.0), FixProvider.GPS)
        found(alongTrails)
        first.answer.complete(turns(1))
        scope.runCurrent()

        assertEquals("the first line's read was cancelled", true, first.cancelled)
        assertNull("its answer is not on the new line", watch.shown.value!!.turns)
    }

    @Test
    fun `H4 - a re-route that falls back to the track has no turns, and its old read is cancelled`() {
        watch.find("t1", null)
        found(alongTrails)
        val first = reads.single()

        watch.onFix(gps(-60.0, -50.0), FixProvider.GPS)
        found(WayHomeResult.TrackOnly(WayHomeNote.NO_ROUTE_FOUND))
        first.answer.complete(turns(1))
        scope.runCurrent()

        assertEquals(true, first.cancelled)
        assertNull(watch.shown.value!!.line)
        assertNull(watch.shown.value!!.turns)
        assertEquals("no read for the track", 1, reads.size)
    }

    @Test
    fun `H4 - Stop cancels the read and shows nothing`() {
        watch.find("t1", null)
        found(alongTrails)
        val first = reads.single()

        watch.stop("t1")
        first.answer.complete(turns(1))
        scope.runCurrent()

        assertEquals(true, first.cancelled)
        assertNull(watch.shown.value)
    }

    @Test
    fun `H4 - a result found without a folder is read as such, and the reader says what that gives`() {
        watch.find("t1", null)
        found(WayHomeResult.AlongTrails(hairpin))

        assertEquals("no folder handed on: the reader says unread, never a guess", null, reads.single().routeData)
    }
}
