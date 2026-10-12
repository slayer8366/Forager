package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.ReturnRecordEvent.ReArmed
import com.zynergylabs.forager.app.domain.ReturnRecordEvent.ReturnEnded
import com.zynergylabs.forager.app.domain.ReturnRecordEvent.ReturnRefused
import com.zynergylabs.forager.app.domain.ReturnRecordEvent.ReturnStarted
import com.zynergylabs.forager.app.domain.ReturnRecordEvent.WentOffTrack
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dispatch 2026-09-28-451: what [ReturnWatch] writes to the lasting record, through its real entry
 * points (the service's begin, fixes and end; the screen's Return and its exit). A recording fake
 * stands in for the file; the file itself is `FileReturnRecordTest`'s.
 */
class ReturnWatchRecordTest {

    private val written = mutableListOf<ReturnRecordEvent>()
    private val watch = ReturnWatch(ComputeReturnToStartUseCase(), { }, ReturnRecord { written += it })

    private fun point(lat: Double, lng: Double = -122.0, t: Long) = TrackPoint(lat, lng, null, 4f, t)

    private fun begun() = watch.apply {
        begin("track-1", TrackRecordingMode.HIGH_ACCURACY)
        setStartPoint("track-1", point(45.0, t = 1_000L))
    }

    @Test
    fun `a Return writes an entry, and the walker ending it writes another`() {
        begun().startReturn("track-1")
        watch.stopReturn("track-1")
        assertEquals(listOf(ReturnStarted("track-1"), ReturnEnded("track-1", ReturnEndReason.BY_WALKER)), written)
    }

    @Test
    fun `stopping the recording, or the service being destroyed, ends a return in the record`() {
        begun().startReturn("track-1")
        watch.end("track-1")
        assertEquals(ReturnEnded("track-1", ReturnEndReason.RECORDING_STOPPED), written.last())

        begun().startReturn("track-1")
        watch.end(null)
        assertEquals(ReturnEnded("track-1", ReturnEndReason.SERVICE_DESTROYED), written.last())
    }

    @Test
    fun `ending with no return under way writes nothing about a return`() {
        begun()
        watch.stopReturn("track-1")
        watch.end("track-1")
        assertEquals(emptyList<ReturnRecordEvent>(), written)
    }

    @Test
    fun `a Return refused for another recording is written as refused`() {
        begun()
        watch.startReturn("track-2")
        assertEquals(listOf(ReturnRefused("track-2", watchedTrackId = "track-1")), written)
    }

    @Test
    fun `an alert's delivery outcome is written after the decision`() {
        val outcome = AlertDeliveryOutcome(notificationPosted = false, notificationProblem = "POST_NOTIFICATIONS denied", vibrated = true, vibrationProblem = null)
        val reporting = object : AlertDelivery {
            override fun deliver(alert: Alert) = Unit
            override fun deliverReporting(alert: Alert) = outcome
        }
        val watch = ReturnWatch(ComputeReturnToStartUseCase(), reporting, ReturnRecord { written += it })
        watch.begin("track-1", TrackRecordingMode.HIGH_ACCURACY)
        watch.setStartPoint("track-1", point(45.0, t = 1_000L))
        watch.startReturn("track-1")
        repeat(4) { i -> watch.onFix(point(45.001 + i * 0.001, t = 2_000L + i * 5_000L), FixProvider.GPS) }
        assertEquals(ReturnRecordEvent.AlertDelivered("track-1", outcome), written.last())
    }

    @Test
    fun `going off the path writes the decision with its reading's time, and coming back on writes re-armed`() {
        begun().startReturn("track-1")
        // 111 m north a reading every 5 s: off from the first, gone off at the fourth (15 s).
        repeat(4) { i -> watch.onFix(point(45.001 + i * 0.001, t = 2_000L + i * 5_000L), FixProvider.GPS) }
        // Back on the path for 10 s, 25 m north of the start (0.000225 deg): on the path (within 40 m plus
        // 4 m accuracy) and outside the arrival circle (15 m). Was the start itself until RECORD -844 made
        // reaching the start end the return; arrival's own entry is ReturnWatchArrivalTest's.
        repeat(3) { i -> watch.onFix(point(45.000225, t = 20_000L + i * 5_000L), FixProvider.GPS) }
        assertEquals(
            listOf(
                ReturnStarted("track-1"),
                WentOffTrack("track-1", readingAtMillis = 17_000L),
                // A delivery that cannot say what it did (this test's), recorded as such.
                ReturnRecordEvent.AlertDelivered("track-1", outcome = null),
                ReArmed("track-1", readingAtMillis = 30_000L),
            ),
            written,
        )
    }
}
