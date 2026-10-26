package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLines.hairpin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WayHomeWatch] publishes the quiet off-trail state (dispatch 2026-10-26-13, plan step 7 PR 3; RECORD -1631, -1676):
 * on [WayHomeShown.offTrail], only when it changes, carried onto a re-routed line and ended by a fallback to the track.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WayHomeWatchOffTrailTest {

    private val scope = TestScope(StandardTestDispatcher())
    private val answers = mutableListOf<CompletableDeferred<WayHomeResult>>()
    private val track = Track("t1", null, 0L, null, listOf(TrackPoint(45.0, -120.0, null, 5f, 0L), TrackPoint(45.001, -120.0, null, 5f, 1L)))

    private val watch = WayHomeWatch(
        scope = scope,
        readTrack = { Result.success(track) },
        findWayHome = { _: Track, _: Waypoint? -> CompletableDeferred<WayHomeResult>().also { answers += it }.await() },
        isReturning = { true },
        errorLog = ErrorLog { _, _, _ -> },
    )

    private fun fix(east: Double, north: Double, second: Int, provider: FixProvider = FixProvider.GPS) {
        val p = at(east, north)
        watch.onFix(TrackPoint(p.lat, p.lng, null, 5f, T + second * 1_000L), provider)
    }

    private fun found() {
        watch.find("t1", null)
        scope.runCurrent()
        answers.last().complete(WayHomeResult.AlongTrails(hairpin))
        scope.runCurrent()
    }

    /** Fixes at (east, north) for each second in [seconds]; what [WayHomeWatch.shown] held after each. */
    private fun walk(east: Double, north: Double, seconds: IntRange, provider: FixProvider = FixProvider.GPS): List<WayHomeShown?> =
        seconds.map { fix(east, north, it, provider); watch.shown.value }

    @Test
    fun `off the trail for 15 s the state is published, once`() {
        found()
        val before = watch.shown.value!!
        assertEquals(OffTrailState.ON, before.offTrail)
        // On the dotted track back, 50 m from the trail's start.
        val shown = walk(0.0, -50.0, 0..20)
        assertEquals(OffTrailState.ON, shown[14]!!.offTrail)
        assertEquals(OffTrailState.OFF, shown[15]!!.offTrail)
        assertEquals("the same line and generation", before.copy(offTrail = OffTrailState.OFF), shown[20])
        assertEquals("published on the change only, not once a fix", 2, (listOf(before) + shown).map { System.identityHashCode(it) }.distinct().size)
    }

    @Test
    fun `back on the trail it reads Back on trail, then on`() {
        found()
        walk(0.0, -50.0, 0..15)
        val back = walk(100.0, 5.0, 20..35)
        assertEquals(OffTrailState.BACK_ON, back[10]!!.offTrail)
        assertEquals(OffTrailState.ON, back[15]!!.offTrail)
    }

    @Test
    fun `network fixes never change it`() {
        found()
        val shown = walk(0.0, -50.0, 0..30, FixProvider.NETWORK)
        assertTrue(shown.all { it!!.offTrail == OffTrailState.ON })
    }

    @Test
    fun `a re-route carries the state onto the new line, and a fallback to the track ends it`() {
        found()
        walk(0.0, -50.0, 0..15)
        // Over 50 m from the line: one re-route.
        walk(-80.0, -50.0, 16..17)
        assertEquals("a re-route is being worked out", 2, answers.size)
        answers.last().complete(WayHomeResult.AlongTrails(hairpin))
        scope.runCurrent()
        val rerouted = watch.shown.value!!
        assertEquals(2L, rerouted.generation)
        assertEquals("still quiet on the new line", OffTrailState.OFF, rerouted.offTrail)

        // Back within the re-arm figure, then a second stray whose re-route falls back to the track.
        walk(0.0, -50.0, 18..18)
        walk(-80.0, -50.0, 19..19)
        assertEquals(3, answers.size)
        answers.last().complete(WayHomeResult.TrackOnly(WayHomeNote.NO_ROUTE_FOUND))
        scope.runCurrent()
        assertEquals("no trail shown, so no off-trail state", OffTrailState.ON, watch.shown.value!!.offTrail)
    }

    @Test
    fun `a second Return starts on`() {
        found()
        walk(0.0, -50.0, 0..15)
        found()
        assertEquals(OffTrailState.ON, watch.shown.value!!.offTrail)
        val shown = walk(0.0, -50.0, 30..44)
        assertEquals("the hold starts again", OffTrailState.ON, shown.last()!!.offTrail)
    }

    private companion object {
        const val T = 1_700_000_000_000L
    }
}
