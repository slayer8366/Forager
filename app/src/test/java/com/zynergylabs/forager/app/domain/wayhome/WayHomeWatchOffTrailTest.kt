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

    private fun found(wayHome: WayHome = hairpin) {
        watch.find("t1", null)
        scope.runCurrent()
        answers.last().complete(WayHomeResult.AlongTrails(wayHome))
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
    fun `walking the track from the trailhead to the start never reads off trail`() {
        found()
        // 80 m north of the trail's end, on the walker's own track to the car, for 30 s.
        val shown = walk(0.0, 100.0, 0..30)
        assertTrue(shown.all { it!!.offTrail == OffTrailState.ON })
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
        scope.runCurrent()
        assertEquals("a re-route is being worked out", 2, answers.size)
        answers.last().complete(WayHomeResult.AlongTrails(hairpin))
        scope.runCurrent()
        val rerouted = watch.shown.value!!
        assertEquals(2L, rerouted.generation)
        assertEquals("still quiet on the new line", OffTrailState.OFF, rerouted.offTrail)

        // Back within the re-arm figure, then a second stray whose re-route falls back to the track.
        walk(0.0, -50.0, 18..18)
        walk(-80.0, -50.0, 19..19)
        scope.runCurrent()
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

    /**
     * A way home shaped as [FindWayHomeUseCase] makes one (its `trailPoints`): the trail part runs from the track point
     * the walker left the trail at, [leftAtSouth] m south of it along their leaving track at east 50, by a straight joint
     * to the engine's route on the trail (north 0), west along it to (0, 0), and by a 10 m joint to the track point they
     * joined at, (0, 10); then their own track north to the start. [backFromSouth] is where the dotted track back begins.
     */
    private fun leftDownTheTrack(leftAtSouth: Double, backFromSouth: Double, east: Double = 50.0) = WayHome(
        trackBack = WayHomeLines.part(WayHomePartKind.TRACK_BACK, east to -backFromSouth, east to -leftAtSouth),
        trail = WayHomeLines.part(WayHomePartKind.TRAIL, east to -leftAtSouth, east to 0.0, 0.0 to 0.0, 0.0 to 10.0),
        trackToStart = WayHomeLines.part(WayHomePartKind.TRACK_TO_START, 0.0 to 10.0, 0.0 to 110.0),
        trailJointMeters = leftAtSouth + 10.0,
        regionId = 1,
    )

    @Test
    fun `a stop 60 to 69 m out along the track the walker left by goes off after the hold`() {
        // Hazel's S22 check of #415, step 1's case: the trail part's joint reaches 25 m out along the leaving track, so
        // "on" measured against it reached 69 m out there, not the 45 m of the trail itself (RECORD -1683).
        for ((i, south) in listOf(60.0, 64.0, 69.0).withIndex()) {
            found(leftDownTheTrack(leftAtSouth = 25.0, backFromSouth = 80.0))
            val from = 100 * i
            val shown = walk(50.0, -south, from..from + 20)
            assertEquals("$south m out, at 14 s", OffTrailState.ON, shown[14]!!.offTrail)
            assertEquals("$south m out, off the trail itself: the quiet state at 15 s", OffTrailState.OFF, shown[15]!!.offTrail)
            assertEquals("$south m out, still off at 20 s", OffTrailState.OFF, shown[20]!!.offTrail)
        }
    }

    @Test
    fun `a stray past a re-route, still walking away, stays off`() {
        // Hazel's S22 check of #415, step 3: the re-route's line leaves the trail on this stray's own track, 22 m out, and
        // the walker 58 to 66 m out read "Back on trail" against its joint (RECORD -1683).
        found(leftDownTheTrack(leftAtSouth = 20.0, backFromSouth = 100.0, east = 150.0))
        assertTrue("on the trail", walk(50.0, 0.0, 0..5).all { it!!.offTrail == OffTrailState.ON })
        val out = walk(50.0, -47.0, 6..30)
        assertEquals("off 15 s, quiet", OffTrailState.OFF, out.last()!!.offTrail)
        assertEquals("no re-route within 50 m of the line", 1, answers.size)

        walk(50.0, -55.0, 31..32)
        scope.runCurrent()
        assertEquals("over 50 m from the line: a re-route", 2, answers.size)
        answers.last().complete(WayHomeResult.AlongTrails(leftDownTheTrack(leftAtSouth = 22.0, backFromSouth = 55.0)))
        scope.runCurrent()
        assertEquals("still quiet on the new line", OffTrailState.OFF, watch.shown.value!!.offTrail)

        // Walking on away from the trail, 58 m out to 66 m, for 18 s.
        val away = (33..50).map { s -> fix(50.0, -(58.0 + (s - 33) * 8.0 / 17), s); watch.shown.value }
        assertEquals("never back on while walking away", List(away.size) { OffTrailState.OFF }, away.map { it!!.offTrail })
        assertEquals("no second re-route", 2, answers.size)
    }

    private companion object {
        const val T = 1_700_000_000_000L
    }
}
