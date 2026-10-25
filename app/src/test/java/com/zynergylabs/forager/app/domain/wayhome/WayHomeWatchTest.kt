package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLines.hairpin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WayHomeWatch] (dispatch 2026-10-24-10 Amendment 1, PR B; RECORD -1613): one job at a time, cancelled by a second
 * Return, Stop and the service ending, never publishing once cancelled; the walker's place from the service's fixes;
 * and the stray rules: over 50 m re-routes once, re-armed within 45 m, one job at a time, a fallback ends it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WayHomeWatchTest {

    private val scope = TestScope(StandardTestDispatcher())
    private val logs = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logs += message }

    /** Each call to the way-home search waits on its own answer; [cancelled] counts the calls cancelled while waiting. */
    private val answers = mutableListOf<CompletableDeferred<WayHomeResult>>()
    private var cancelled = 0
    private val track = Track("t1", null, 0L, null, listOf(TrackPoint(45.0, -120.0, null, 5f, 0L), TrackPoint(45.001, -120.0, null, 5f, 1L)))
    private var returning = true

    private val watch = WayHomeWatch(
        scope = scope,
        readTrack = { Result.success(track) },
        findWayHome = { _: Track, _: Waypoint? ->
            val answer = CompletableDeferred<WayHomeResult>().also { answers += it }
            try {
                answer.await()
            } catch (e: CancellationException) {
                cancelled++
                throw e
            }
        },
        isReturning = { returning },
        errorLog = errorLog,
    )

    private val alongTrails = WayHomeResult.AlongTrails(hairpin)

    private fun gps(east: Double, north: Double, accuracy: Float = 5f) = point(at(east, north), accuracy)
    private fun point(p: LatLng, accuracy: Float) = TrackPoint(p.lat, p.lng, null, accuracy, 0L)

    private fun foundOnFirstReturn() {
        watch.find("t1", null)
        scope.runCurrent()
        answers.last().complete(alongTrails)
        scope.runCurrent()
    }

    @Test
    fun `Return shows the track at once, then the way home when found`() {
        watch.find("t1", null)
        assertEquals(WayHomeShown("t1", 1, null), watch.shown.value)
        scope.runCurrent()
        answers.single().complete(alongTrails)
        scope.runCurrent()
        val shown = watch.shown.value!!
        assertEquals(alongTrails, shown.result)
        assertEquals(1L, shown.generation)
        assertTrue(shown.line != null)
    }

    @Test
    fun `a second Return cancels the first, which never shows`() {
        watch.find("t1", null)
        scope.runCurrent()
        watch.find("t1", null)
        scope.runCurrent()
        assertEquals("the first search was cancelled", 1, cancelled)
        answers[0].complete(WayHomeResult.TrackOnly(WayHomeNote.NO_ROUTE_FOUND))
        answers[1].complete(alongTrails)
        scope.runCurrent()
        assertEquals(WayHomeShown("t1", 2, alongTrails, watch.shown.value!!.line), watch.shown.value)
    }

    @Test
    fun `a cancelled search that finishes anyway never shows`() {
        // A search that does not stop when cancelled (as a routing call waits for its engine, NonCancellable).
        val answer = CompletableDeferred<WayHomeResult>()
        val stubborn = WayHomeWatch(scope, { Result.success(track) }, { _, _ -> withContext(NonCancellable) { answer.await() } }, { true }, errorLog)
        stubborn.find("t1", null)
        scope.runCurrent()
        stubborn.stop("t1")
        answer.complete(alongTrails)
        scope.runCurrent()
        assertNull("the stopped search's answer is not shown", stubborn.shown.value)
    }

    @Test
    fun `a search a second Return replaced, finishing anyway, never shows`() {
        val first = CompletableDeferred<WayHomeResult>()
        val second = CompletableDeferred<WayHomeResult>()
        val calls = mutableListOf(first, second)
        val stubborn = WayHomeWatch(scope, { Result.success(track) }, { _, _ -> val mine = calls.removeAt(0); withContext(NonCancellable) { mine.await() } }, { true }, errorLog)
        stubborn.find("t1", null)
        scope.runCurrent()
        stubborn.find("t1", null)
        scope.runCurrent()
        first.complete(alongTrails)
        scope.runCurrent()
        assertEquals("the second Return's search is still the one shown, still working", WayHomeShown("t1", 2, null), stubborn.shown.value)
    }

    @Test
    fun `Stop, and the service ending, cancel the work and show nothing, and another track's end is ignored`() {
        watch.find("t1", null)
        scope.runCurrent()
        watch.stop("t1")
        scope.runCurrent()
        assertEquals(1, cancelled)
        assertNull(watch.shown.value)

        watch.find("t1", null)
        scope.runCurrent()
        watch.end("other")
        assertEquals(1, cancelled)
        watch.end(null)
        scope.runCurrent()
        assertEquals(2, cancelled)
        assertNull(watch.shown.value)
    }

    @Test
    fun `the walker's place comes from GPS fixes inside the gate, only while returning`() {
        foundOnFirstReturn()
        watch.onFix(gps(0.0, -50.0), FixProvider.NETWORK)
        assertNull("a network fix places nobody", watch.progressFor("t1"))
        watch.onFix(gps(0.0, -50.0, accuracy = 60f), FixProvider.GPS)
        assertNull("a fix outside the gate places nobody", watch.progressFor("t1"))
        watch.onFix(gps(0.0, -50.0), FixProvider.GPS)
        assertEquals(570.0, (watch.progressFor("t1") as WayHomeProgress.Placed).place.remainingMeters, 0.5)
        returning = false
        assertNull("not returning, nothing is read", watch.progressFor("t1"))
    }

    @Test
    fun `a stray over 50 m re-routes once, the line stays until the new one, and within 45 m re-arms it`() {
        foundOnFirstReturn()
        watch.onFix(gps(-60.0, -50.0), FixProvider.GPS)
        scope.runCurrent()
        assertEquals("one re-route", 2, answers.size)
        assertEquals("the line shown stays meanwhile", 1L, watch.shown.value!!.generation)

        watch.onFix(gps(-70.0, -50.0), FixProvider.GPS)
        scope.runCurrent()
        assertEquals("one job at a time", 2, answers.size)
        answers[1].complete(alongTrails)
        scope.runCurrent()
        assertEquals(2L, watch.shown.value!!.generation)

        watch.onFix(gps(-70.0, -50.0), FixProvider.GPS)
        watch.onFix(gps(-75.0, -50.0), FixProvider.GPS)
        scope.runCurrent()
        assertEquals("once per stray", 2, answers.size)
        assertEquals("the suppressed stray is logged once: $logs", 1, logs.count { "not re-routed" in it && "already re-routed once" in it })

        watch.onFix(gps(-40.0, -50.0), FixProvider.GPS)
        watch.onFix(gps(-60.0, -50.0), FixProvider.GPS)
        scope.runCurrent()
        assertEquals("back within 45 m re-armed it", 3, answers.size)
    }

    @Test
    fun `a re-route that falls back to the track ends re-routing for the return`() {
        foundOnFirstReturn()
        watch.onFix(gps(-60.0, -50.0), FixProvider.GPS)
        scope.runCurrent()
        answers[1].complete(WayHomeResult.TrackOnly(WayHomeNote.NO_ROUTE_FOUND))
        scope.runCurrent()
        assertEquals(WayHomeResult.TrackOnly(WayHomeNote.NO_ROUTE_FOUND), watch.shown.value!!.result)
        watch.onFix(gps(-40.0, -50.0), FixProvider.GPS)
        watch.onFix(gps(-200.0, -50.0), FixProvider.GPS)
        scope.runCurrent()
        assertEquals(2, answers.size)
    }

    @Test
    fun `an engine Error in a re-route does not escape, ends that search, and gives the track, logged`() {
        // Sorrel's M-395-2: an OutOfMemoryError from BRouter is an Error, not an Exception.
        var calls = 0
        val oom = WayHomeWatch(scope, { Result.success(track) }, { _, _ -> if (++calls == 1) alongTrails else throw OutOfMemoryError("engine") }, { true }, errorLog)
        oom.find("t1", null)
        scope.runCurrent()
        oom.onFix(gps(-60.0, -50.0), FixProvider.GPS)
        scope.runCurrent()
        assertEquals("the re-route's Error gives the track", WayHomeResult.TrackOnly(WayHomeNote.NO_ROUTE_FOUND), oom.shown.value!!.result)
        assertEquals("logged once: $logs", 1, logs.count { "failed" in it })
    }

    @Test
    fun `the far band's hysteresis holds across off the line - out above 50 m, back only below 45 m`() {
        foundOnFirstReturn()
        watch.onFix(gps(-48.0, -50.0), FixProvider.GPS)
        assertTrue("48 m: placed", watch.progressFor("t1") is WayHomeProgress.Placed)
        watch.onFix(gps(-51.0, -50.0), FixProvider.GPS)
        assertTrue("51 m: off the line", watch.progressFor("t1") is WayHomeProgress.OffLine)
        watch.onFix(gps(-48.0, -50.0), FixProvider.GPS)
        assertTrue("48 m again: still off the line, not back until under 45 m", watch.progressFor("t1") is WayHomeProgress.OffLine)
        watch.onFix(gps(-44.0, -50.0), FixProvider.GPS)
        assertTrue("44 m: placed again", watch.progressFor("t1") is WayHomeProgress.Placed)
    }

    @Test
    fun `a search that throws gives the track with Couldn't find a trail route, logged`() {
        val throwing = WayHomeWatch(scope, { Result.success(track) }, { _, _ -> error("boom") }, { true }, errorLog)
        throwing.find("t1", null)
        scope.runCurrent()
        assertEquals(WayHomeResult.TrackOnly(WayHomeNote.NO_ROUTE_FOUND), throwing.shown.value!!.result)
        assertEquals(1, logs.size)
    }

    @Test
    fun `after a re-route, a walker who walks on past the new line's start reads more with each metre walked out`() {
        // Hazel's S22 check, section 3 (dispatch 2026-10-25-06, RECORD -1630): the re-routed line starts at the newest
        // stored point, (-60, -50) here; the walker walks on west, away from it, and the figure held at the line's total.
        foundOnFirstReturn()
        watch.onFix(gps(-60.0, -50.0), FixProvider.GPS)
        scope.runCurrent()
        val rerouted = WayHomeResult.AlongTrails(
            WayHomeLines.wayHome(
                WayHomeLines.part(WayHomePartKind.TRACK_BACK, -60.0 to -50.0, 0.0 to -50.0, 0.0 to 0.0),
                hairpin.trail,
                hairpin.trackToStart,
            ),
        )
        answers[1].complete(rerouted)
        scope.runCurrent()
        val total = watch.shown.value!!.line!!.totalMeters
        assertEquals(630.0, total, 0.5)
        var previous = 0.0
        for (out in listOf(0.0, 10.0, 20.0, 30.0, 40.0)) {
            watch.onFix(gps(-60.0 - out, -50.0), FixProvider.GPS)
            val figure = (watch.progressFor("t1") as WayHomeProgress.Placed).place.remainingMeters
            assertTrue("$out m on: never short of the line drawn, $figure against ${total + out}", figure >= total + out - 0.5)
            if (out > 0.0) assertTrue("$out m on: more than at the last fix, $figure against $previous", figure > previous)
            previous = figure
        }
        assertEquals("no second re-route within the stray band", 2, answers.size)
    }
}
