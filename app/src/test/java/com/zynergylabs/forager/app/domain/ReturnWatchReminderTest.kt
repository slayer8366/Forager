package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings' "Off-track reminder" as [ReturnWatch] reads it (dispatch 2026-09-28-626; Amendment 1,
 * RECORD -627: "unticked, the off-track alert doesn't fire"). The same walk away as
 * [ReturnWatchTest]'s, with the reminder on and off: off, the judge still decides and the Return
 * button still turns, but nothing is delivered.
 */
class ReturnWatchReminderTest {

    private val delivered = mutableListOf<Alert>()
    private var reminderOn = true

    private fun begunWatch() = ReturnWatch(
        computeReturnToStart = ComputeReturnToStartUseCase(),
        alertDelivery = { delivered += it },
        isReminderOn = { reminderOn },
    ).apply {
        begin("track-1", TrackRecordingMode.BALANCED)
        setStartPoint("track-1", point(lat = 45.0, t = 1_000L))
        startReturn("track-1")
    }

    private fun point(lat: Double, t: Long) =
        TrackPoint(lat = lat, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = t)

    /** Four readings 111 m apart, 5 s apart: gone off at the fourth (see ReturnWatchTest.walkAway). */
    private fun ReturnWatch.walkAway(fromMillis: Long) {
        repeat(4) { i -> onFix(point(lat = 45.001 + i * 0.001, t = fromMillis + i * 5_000L), FixProvider.GPS) }
    }

    @Test
    fun `reminder on, going off track delivers the alert`() {
        val watch = begunWatch()
        watch.walkAway(fromMillis = 2_000L)
        assertEquals(listOf(Alert(kind = AlertKind.OFF_TRACK, overridesSilence = false)), delivered)
    }

    @Test
    fun `reminder off, going off track delivers nothing and still shows off track`() {
        reminderOn = false
        val watch = begunWatch()
        watch.walkAway(fromMillis = 2_000L)

        assertEquals(listOf<Alert>(), delivered)
        assertTrue("the Return button still shows off track", watch.state.value.isOffTrack)
    }

    @Test
    fun `turned back on mid-walk, the next stray alerts`() {
        reminderOn = false
        val watch = begunWatch()
        watch.walkAway(fromMillis = 2_000L)
        assertEquals(0, delivered.size)

        // Back on the path for over 10 s re-arms the judge; then a second stray. 25 m north of the start
        // (0.000225 deg): on the path (within 40 m of it) and outside the arrival circle (15 m). Was the start
        // itself until RECORD -844 made reaching the start end the return, which would end this test's return.
        repeat(3) { i -> watch.onFix(point(lat = 45.000225, t = 40_000L + i * 6_000L), FixProvider.GPS) }
        reminderOn = true
        watch.walkAway(fromMillis = 70_000L)
        assertEquals(1, delivered.size)
    }
}
