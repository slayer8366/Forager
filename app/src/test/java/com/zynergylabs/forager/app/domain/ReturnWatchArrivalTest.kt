package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RECORD -844 and -845: a return ends by itself when the walker reaches the start, and the recording
 * carries on. Through [ReturnWatch]'s real entry points, the calls the recording service makes
 * ([ReturnWatch.begin], [ReturnWatch.onKeptPoint], [ReturnWatch.onFix], [ReturnWatch.end]) and the
 * screen's ([ReturnWatch.startReturn], [ReturnWatch.setStartPoint], [ReturnWatch.stopReturn]).
 *
 * The bug this closes: on the 2026-10-10 walk both phones posted off-track during the drive-off, after
 * Back by and the sundown alerts had judged arrival (RECORD -841, -843), because the return never
 * ended. Arrival is the radius Back by and the sundown alerts use ([hasArrived]: the larger of 15 m and
 * twice the fix's accuracy; every fix here reports 4 m, so 15 m), on a GPS fix, and only once the walker
 * has been outside that circle since Return was tapped ("Must leave first", RECORD -845).
 *
 * Distances: 0.001 deg of latitude is 111 m; 0.000225 deg is 25 m.
 */
class ReturnWatchArrivalTest {

    private val delivered = mutableListOf<Alert>()
    private val watch = ReturnWatch(ComputeReturnToStartUseCase(), { delivered += it })

    private fun point(lat: Double, t: Long) = TrackPoint(lat, -122.0, null, 4f, t)

    /** The service has begun `track-1`; the walk out, 45.000 to 45.003, is kept; the start is 45.000. */
    private fun begunWithWalkOut() = watch.apply {
        begin("track-1", TrackRecordingMode.HIGH_ACCURACY)
        (0..6).forEach { i -> onKeptPoint(point(45.0 + i * 0.0005, t = 1_000L + i * 10_000L)) }
        setStartPoint("track-1", point(45.0, t = 1_000L))
    }

    /** Back along the walk out, from 45.003 to 45.0005 (56 m from the start), a reading every 5 s from [fromMillis]: on the path all the way. */
    private fun walkBackAlmostToTheStart(fromMillis: Long) {
        (6 downTo 1).forEachIndexed { step, i -> watch.onFix(point(45.0 + i * 0.0005, t = fromMillis + step * 5_000L), FixProvider.GPS) }
    }

    /** Away from the start northward at driving pace, 222 m a second for [seconds] s: well past the off-track rule's 15 s hold. */
    private fun driveOff(fromMillis: Long, seconds: Int = 40) {
        (1..seconds).forEach { s -> watch.onFix(point(45.0 + s * 0.002, t = fromMillis + s * 1_000L), FixProvider.GPS) }
    }

    @Test
    fun `reaching the start after walking back ends the return, with reason arrival, and the recording goes on`() {
        begunWithWalkOut().startReturn("track-1")
        walkBackAlmostToTheStart(fromMillis = 100_000L)
        assertTrue("precondition: still returning 56 m out", watch.state.value.isReturning)

        watch.onFix(point(45.0, t = 130_000L), FixProvider.GPS)

        val state = watch.state.value
        assertFalse("the return ended at the start", state.isReturning)
        assertTrue("ended by arrival", state.arrivedAfterReturn)
        assertFalse(state.isOffTrack)
        assertTrue("the recording is still begun", state.isBegun)
        assertEquals("track-1", state.trackId)
        assertEquals("nothing delivered on the way back", 0, delivered.size)
    }

    @Test
    fun `after arrival, driving away neither evaluates nor posts off-track`() {
        begunWithWalkOut().startReturn("track-1")
        walkBackAlmostToTheStart(fromMillis = 100_000L)
        watch.onFix(point(45.0, t = 130_000L), FixProvider.GPS)

        driveOff(fromMillis = 130_000L)

        assertEquals("no off-track alert on the drive-off", 0, delivered.size)
        assertFalse(watch.state.value.isOffTrack)
        assertTrue("the recording is still begun", watch.state.value.isBegun)
        assertTrue("the drive-off is still measured for the button's distance", (watch.state.value.returnToStart?.distanceMeters ?: 0.0) > 8_000.0)
    }

    @Test
    fun `must leave first - Return tapped at the start does not end until the walker has left the circle and come back`() {
        watch.begin("track-1", TrackRecordingMode.HIGH_ACCURACY)
        watch.setStartPoint("track-1", point(45.0, t = 1_000L))
        watch.onFix(point(45.0, t = 2_000L), FixProvider.GPS)
        watch.startReturn("track-1")

        // A minute standing at the start, a reading every 5 s.
        (1..12).forEach { i -> watch.onFix(point(45.0 + (i % 2) * 0.00005, t = 2_000L + i * 5_000L), FixProvider.GPS) }
        assertTrue("still returning: the walker has not left the circle since Return", watch.state.value.isReturning)

        // 25 m out, outside the 15 m circle, then back.
        watch.onFix(point(45.000225, t = 70_000L), FixProvider.GPS)
        assertTrue("outside the circle is not arrival", watch.state.value.isReturning)
        watch.onFix(point(45.0, t = 75_000L), FixProvider.GPS)

        assertFalse("left and came back: arrived", watch.state.value.isReturning)
    }

    @Test
    fun `a network fix neither leaves the circle nor arrives - only GPS decides, as for Back by`() {
        watch.begin("track-1", TrackRecordingMode.HIGH_ACCURACY)
        watch.setStartPoint("track-1", point(45.0, t = 1_000L))
        watch.startReturn("track-1")

        watch.onFix(point(45.000225, t = 10_000L), FixProvider.NETWORK)
        watch.onFix(point(45.0, t = 15_000L), FixProvider.GPS)
        assertTrue("a network reading outside did not count as having left", watch.state.value.isReturning)

        watch.onFix(point(45.000225, t = 20_000L), FixProvider.GPS)
        watch.onFix(point(45.0, t = 25_000L), FixProvider.NETWORK)
        assertTrue("a network reading at the start did not count as arrival", watch.state.value.isReturning)

        watch.onFix(point(45.0, t = 30_000L), FixProvider.GPS)
        assertFalse(watch.state.value.isReturning)
    }

    @Test
    fun `straying before arrival still posts off-track, and the return goes on`() {
        begunWithWalkOut().startReturn("track-1")
        // 0.0015 deg north and 60 m east of the path: off it, four readings over 15 s.
        repeat(4) { i -> watch.onFix(TrackPoint(45.0015, -122.0 + 60.0 / 78_620.0, null, 4f, 100_000L + i * 5_000L), FixProvider.GPS) }

        assertEquals(listOf(Alert(kind = AlertKind.OFF_TRACK, overridesSilence = false)), delivered)
        assertTrue(watch.state.value.isOffTrack)
        assertTrue(watch.state.value.isReturning)
    }

    @Test
    fun `Back by and the sundown alerts judge arrival while returning and after a return ended on arrival, and at no other time`() {
        begunWithWalkOut()
        assertFalse("no return", watch.arrivalJudgedFor("track-1"))

        watch.startReturn("track-1")
        assertTrue("returning", watch.arrivalJudgedFor("track-1"))
        assertFalse("another track", watch.arrivalJudgedFor("track-2"))

        walkBackAlmostToTheStart(fromMillis = 100_000L)
        watch.onFix(point(45.0, t = 130_000L), FixProvider.GPS)
        assertTrue("ended on arrival", watch.arrivalJudgedFor("track-1"))

        // The display's exit, tapped while it holds "Arrived": the return had already ended, by arrival.
        watch.stopReturn("track-1")
        assertTrue("closing the display after arrival leaves it", watch.arrivalJudgedFor("track-1"))

        // A new Return, ended by the walker before arriving: as before this change, no arrival is judged.
        watch.startReturn("track-1")
        watch.stopReturn("track-1")
        assertFalse("ended by the walker", watch.arrivalJudgedFor("track-1"))

        watch.startReturn("track-1")
        watch.onFix(point(45.002, t = 200_000L), FixProvider.GPS)
        watch.onFix(point(45.0, t = 205_000L), FixProvider.GPS)
        assertTrue(watch.arrivalJudgedFor("track-1"))
        watch.end("track-1")
        assertFalse("the recording ended", watch.arrivalJudgedFor("track-1"))
    }

    @Test
    fun `a new Return after arrival watches again, from a fresh must-leave-first`() {
        begunWithWalkOut().startReturn("track-1")
        walkBackAlmostToTheStart(fromMillis = 100_000L)
        watch.onFix(point(45.0, t = 130_000L), FixProvider.GPS)
        assertFalse(watch.state.value.isReturning)

        watch.startReturn("track-1")
        assertTrue(watch.state.value.isReturning)
        assertFalse("a new return clears the arrival", watch.state.value.arrivedAfterReturn)
        watch.onFix(point(45.0, t = 135_000L), FixProvider.GPS)
        assertTrue("at the start again, but not left since this Return", watch.state.value.isReturning)
    }
}
