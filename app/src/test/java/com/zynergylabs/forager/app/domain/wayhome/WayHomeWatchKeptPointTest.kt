package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.model.LatLng
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
import org.junit.Test

/**
 * Dispatch 2026-10-25-10 (RECORD -1636): the way home is found on the stored track plus the points the recording kept
 * after the last one written, so it starts from the walker's newest point, at Return and at each re-route. At Return
 * through the real service: `TrackRecordingServiceWayHomeNewestPointTest`. Here, the re-route, and what is not added.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WayHomeWatchKeptPointTest {

    private val scope = TestScope(StandardTestDispatcher())
    private val answers = mutableListOf<CompletableDeferred<WayHomeResult>>()
    private val handed = mutableListOf<Track>()

    /** Written: the first two kept points. */
    private val stored = Track("t1", null, 0L, null, listOf(kept(-50.0, -60.0, 1L), kept(-50.0, -55.0, 2L)))

    private val watch = WayHomeWatch(
        scope = scope,
        readTrack = { Result.success(stored) },
        findWayHome = { track: Track, _: Waypoint? ->
            handed += track
            CompletableDeferred<WayHomeResult>().also { answers += it }.await()
        },
        isReturning = { true },
        errorLog = ErrorLog { _, _, _ -> },
    )

    private fun kept(east: Double, north: Double, time: Long): TrackPoint = at(east, north).let { p: LatLng -> TrackPoint(p.lat, p.lng, null, 5f, time) }

    @Test
    fun `a re-route starts from the newest kept point, written or not`() {
        stored.points.forEach { watch.onKeptPoint("t1", it) }
        watch.find("t1", null)
        scope.runCurrent()
        answers.single().complete(WayHomeResult.AlongTrails(hairpin))
        scope.runCurrent()

        // The walker strays west, past 50 m from the line; the service keeps two points and has not written them.
        val strayed = listOf(kept(-60.0, -50.0, 3L), kept(-70.0, -50.0, 4L))
        strayed.forEach { watch.onKeptPoint("t1", it) }
        watch.onFix(strayed.last(), FixProvider.GPS)
        scope.runCurrent()

        assertEquals("one re-route", 2, handed.size)
        assertEquals(
            "the re-route is found on the written points and the two kept since, once each, in order",
            listOf(1L, 2L, 3L, 4L),
            handed[1].points.map { it.timestampEpochMillis },
        )
        assertEquals("its line starts from the newest kept point", strayed.last(), handed[1].points.last())
    }

    @Test
    fun `points kept on another recording are not added`() {
        watch.onKeptPoint("other", kept(-50.0, -50.0, 9L))
        watch.find("t1", null)
        scope.runCurrent()
        assertEquals(listOf(1L, 2L), handed.single().points.map { it.timestampEpochMillis })
    }

    @Test
    fun `a recording's end forgets its kept points`() {
        watch.onKeptPoint("t1", kept(-50.0, -50.0, 3L))
        watch.end("t1")
        watch.find("t1", null)
        scope.runCurrent()
        assertEquals(listOf(1L, 2L), handed.single().points.map { it.timestampEpochMillis })
    }
}
