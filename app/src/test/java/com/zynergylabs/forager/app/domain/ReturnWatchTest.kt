package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ReturnWatch] on a plain JVM: no Activity, no Service, no Compose tree, no coroutine.
 *
 * ## Dispatch 2026-09-28-425 changed the rule these tests drive
 *
 * Off track was three readings' distance to the start rising by 25 m, with a 120 s repeat. It is
 * now [OffTrackJudge]'s: further from the path walked out than 40 m plus the reading's accuracy,
 * for 15 s and at least three readings, once per stray. The tests that walked away three readings a
 * second apart now walk away over 15 s ([walkAway]); the cooldown's tests are replaced by "once per
 * stray"; and the side-by-side test now holds the watch to a bare [OffTrackJudge] instead of the
 * retired `DetectOffTrackUseCase`. What each asserted before and after is listed in
 * `docs/navigation/2026-10-03-off-track-rule-report.md`.
 *
 * The path a return is measured against is the start, then the points the service kept before
 * Return ([ReturnWatch.onKeptPoint]). A test that gives no kept points measures from the start alone.
 */
class ReturnWatchTest {

    /** Every [Alert] the watch handed over, in order. Synchronized: the two-caller test delivers from two threads. */
    private val delivered = java.util.Collections.synchronizedList(mutableListOf<Alert>())

    private fun watch() = ReturnWatch(
        computeReturnToStart = ComputeReturnToStartUseCase(),
        alertDelivery = { delivered += it },
    )

    private fun point(lat: Double, lng: Double = -122.0, t: Long) =
        TrackPoint(lat = lat, lng = lng, altitude = null, accuracyMeters = null, timestampEpochMillis = t)

    /** A watch the service has begun for `track-1`, with the start at 45.0. */
    private fun begunWatch() = watch().apply {
        begin("track-1", MODE)
        setStartPoint("track-1", point(lat = 45.0, lng = -122.0, t = 1_000L))
    }

    /**
     * Four readings 111 m apart walking north from the start, 5 s apart from [fromMillis]: off the
     * path from the first, and gone off at the fourth, 15 s and four readings in.
     */
    private fun ReturnWatch.walkAway(fromMillis: Long, fromLat: Double = 45.001) {
        repeat(4) { i -> onFix(point(lat = fromLat + i * 0.001, t = fromMillis + i * 5_000L), FixProvider.GPS) }
    }

    /** The walk out, kept by the service: 45.000 to 45.003 north, every 0.0005° (56 m). */
    private fun ReturnWatch.keptWalkOut() {
        (0..6).forEach { i -> onKeptPoint(point(lat = 45.0 + i * 0.0005, t = 1_000L + i * 10_000L)) }
    }

    // ---- Moved from the ViewModel (dispatch -400), retimed for the rule of dispatch -425 ---------

    @Test
    fun `moving steadily away from the start while returning sets off-track`() {
        val watch = begunWatch()
        watch.startReturn("track-1")

        watch.walkAway(fromMillis = 2_000L)

        assertTrue(watch.state.value.isOffTrack)
    }

    /**
     * Exactly one delivery per event, asserted as a count, not as the absence of a second. Fails
     * with the `alertDelivery.deliver` call removed (0 deliveries).
     */
    @Test
    fun `going off-track delivers exactly one alert, not one per fix`() {
        val watch = begunWatch()
        watch.startReturn("track-1")
        assertEquals(0, delivered.size)

        watch.walkAway(fromMillis = 2_000L)
        assertTrue(watch.state.value.isOffTrack)
        assertEquals(1, delivered.size)
        // Off-track passes overridesSilence = false (owner ruling 2026-09-11, reversing the
        // original true) — asserted on the value the delivery received, since the parameter
        // exists precisely so it is never assumed.
        assertEquals(Alert(kind = AlertKind.OFF_TRACK, overridesSilence = false), delivered.single())

        // Still off on the next fix: once per stray, not once per fix.
        watch.onFix(point(lat = 45.005, t = 22_000L), FixProvider.GPS)
        assertEquals(1, delivered.size)
    }

    /** Replaces "a sustained drift alerts again once the cooldown elapses": the owner chose once per stray (dispatch -425, question 2). */
    @Test
    fun `a sustained drift alerts once, and not again however long it lasts`() {
        val watch = begunWatch()
        watch.startReturn("track-1")
        watch.walkAway(fromMillis = 2_000L)
        assertEquals(1, delivered.size)

        // Ten more minutes off the path, a reading every 5 s: no reminder.
        (1..120).forEach { i -> watch.onFix(point(lat = 45.005, t = 17_000L + i * 5_000L), FixProvider.GPS) }
        assertEquals(1, delivered.size)
        assertTrue(watch.state.value.isOffTrack)
    }

    /** New with the rule: back on the path for 10 s re-arms it, and a second stray alerts again. */
    @Test
    fun `back on the path for 10 s re-arms the alert for a second stray`() {
        val watch = begunWatch()
        watch.keptWalkOut()
        watch.startReturn("track-1")
        repeat(4) { i -> watch.onFix(point(lat = 45.0015, lng = -122.0 + 60.0 / 78_620.0, t = 100_000L + i * 5_000L), FixProvider.GPS) }
        assertEquals(1, delivered.size)
        repeat(3) { i -> watch.onFix(point(lat = 45.0015, t = 120_000L + i * 5_000L), FixProvider.GPS) }
        assertFalse("back on for 10 s", watch.state.value.isOffTrack)
        repeat(4) { i -> watch.onFix(point(lat = 45.0015, lng = -122.0 + 60.0 / 78_620.0, t = 140_000L + i * 5_000L), FixProvider.GPS) }
        assertEquals(2, delivered.size)
    }

    @Test
    fun `staying on track never delivers an alert`() {
        val watch = begunWatch()
        watch.keptWalkOut()
        watch.startReturn("track-1")

        // Back along the path walked out, from its far end.
        listOf(45.003, 45.0025, 45.002, 45.0015, 45.001).forEachIndexed { i, lat -> watch.onFix(point(lat = lat, t = 100_000L + i * 5_000L), FixProvider.GPS) }

        assertEquals(0, delivered.size)
    }

    /** Was "stopReturn resets the cooldown so a later return attempt can alert immediately": there is no cooldown; a new return starts armed. */
    @Test
    fun `stopReturn ends the stray, so a later return attempt can alert again`() {
        val watch = begunWatch()
        watch.startReturn("track-1")
        watch.walkAway(fromMillis = 2_000L)
        assertEquals(1, delivered.size)

        watch.stopReturn("track-1")
        watch.startReturn("track-1")
        // Still off, straight away: a new return's judge is armed.
        watch.walkAway(fromMillis = 30_000L)

        assertEquals(2, delivered.size)
    }

    @Test
    fun `moving steadily toward the start while returning stays on track`() {
        val watch = begunWatch()
        watch.keptWalkOut()
        watch.startReturn("track-1")

        listOf(45.003, 45.002, 45.001).forEachIndexed { i, lat -> watch.onFix(point(lat = lat, t = 100_000L + i * 10_000L), FixProvider.GPS) }

        assertFalse(watch.state.value.isOffTrack)
    }

    @Test
    fun `moving away from the start does not set off-track unless actively returning`() {
        val watch = begunWatch()

        // No startReturn() call — this is ordinary outbound travel.
        watch.walkAway(fromMillis = 2_000L)

        assertFalse(watch.state.value.isOffTrack)
        assertEquals(0, delivered.size)
    }

    @Test
    fun `stopping the recording clears returning and off-track state`() {
        val watch = begunWatch()
        watch.startReturn("track-1")
        watch.walkAway(fromMillis = 2_000L)
        assertTrue(watch.state.value.isOffTrack)

        watch.end("track-1") // what the service calls when the recording stops

        assertFalse(watch.state.value.isReturning)
        assertFalse(watch.state.value.isOffTrack)
    }

    // ---- The path a return is measured against (dispatch -425) ----------------------------------

    /**
     * The path is the kept points as they stood at Return. Points kept after it (the return leg,
     * a detour) are recorded, and are not part of what the return is measured against: a detour
     * stays off the path however far the track follows it.
     */
    @Test
    fun `the path is the points kept before Return, and a detour kept after it is still off the path`() {
        val watch = begunWatch()
        watch.keptWalkOut()
        watch.startReturn("track-1")
        assertEquals(7, watch.pathPointCount)

        // A detour 100 m east, kept by the service as it is walked, read for 15 s.
        repeat(4) { i ->
            val detour = point(lat = 45.0015, lng = -122.0 + 100.0 / 78_620.0, t = 100_000L + i * 5_000L)
            watch.onKeptPoint(detour)
            watch.onFix(detour, FixProvider.GPS)
        }
        assertEquals("still the path at Return", 7, watch.pathPointCount)
        assertEquals(1, delivered.size)
    }

    /** The owner's walk: a network reading claiming 400 m accuracy, far from the path, among GPS readings on it. Nothing. */
    @Test
    fun `a far network reading among GPS readings on the path does not alert`() {
        val watch = begunWatch()
        watch.keptWalkOut()
        watch.startReturn("track-1")
        repeat(30) { i ->
            watch.onFix(point(lat = 45.003 - i * 0.0001, t = 100_000L + i * 1_000L), FixProvider.GPS)
            watch.onFix(TrackPoint(lat = 45.0015, lng = -122.0 + 400.0 / 78_620.0, altitude = null, accuracyMeters = 400f, timestampEpochMillis = 100_000L + i * 1_000L + 567L), FixProvider.NETWORK)
        }
        assertEquals(0, delivered.size)
        assertFalse(watch.state.value.isOffTrack)
    }

    @Test
    fun `kept points before the service has begun the watch are not kept`() {
        val watch = watch()
        watch.startReturn("track-1")
        watch.keptWalkOut()
        watch.begin("track-1", MODE)
        watch.startReturn("track-1")
        assertEquals(0, watch.pathPointCount)
    }

    // ---- New with the class (dispatch -400), retimed for the rule of dispatch -425 --------------

    /**
     * Amendment 2, ruling 3: the service begins the watch, and that happens a moment after the
     * Record tap (the ViewModel creates the track row, `MainActivity` sends the start, the service
     * handles it). A Return tapped inside that moment must not be lost.
     */
    @Test
    fun `a return asked for before the service has begun the watch is kept for that track`() {
        val watch = watch()

        assertTrue("an early return for the track about to be recorded is accepted", watch.startReturn("track-1"))
        assertTrue("and the screen can show it at once", watch.state.value.isReturning)
        watch.setStartPoint("track-1", point(lat = 45.0, t = 1_000L))

        watch.begin("track-1", MODE)
        assertTrue("beginning the same track keeps the return", watch.state.value.isReturning)

        watch.walkAway(fromMillis = 2_000L)
        assertEquals("and the early start point was kept too, so the walk away alerts", 1, delivered.size)
    }

    @Test
    fun `an early return for one track is dropped when the service begins a different one`() {
        val watch = watch()
        watch.startReturn("track-1")
        watch.setStartPoint("track-1", point(lat = 45.0, t = 1_000L))

        watch.begin("track-2", MODE)

        assertEquals(ReturnWatchState(trackId = "track-2", isBegun = true, mode = MODE), watch.state.value)
    }

    @Test
    fun `while begun for one track, a return or a start point for another is refused and changes nothing`() {
        val watch = begunWatch()
        val before = watch.state.value

        assertFalse(watch.startReturn("track-2"))
        watch.setStartPoint("track-2", point(lat = 10.0, t = 1_000L))
        watch.stopReturn("track-2")
        watch.end("track-2")

        assertEquals(before, watch.state.value)
        watch.startReturn("track-1")
        watch.onFix(point(lat = 45.001, t = 2_000L), FixProvider.GPS)
        assertEquals("the start is still the one given for track-1", 111.2, watch.state.value.returnToStart!!.distanceMeters, 1.0)
    }

    @Test
    fun `a fix before the service has begun the watch is not a reading`() {
        val watch = watch()
        watch.startReturn("track-1")
        watch.setStartPoint("track-1", point(lat = 45.0, t = 1_000L))

        watch.walkAway(fromMillis = 2_000L)

        assertEquals(0, delivered.size)
        assertNull(watch.state.value.returnToStart)
    }

    @Test
    fun `with no start point there is nothing to measure against, so nothing is decided`() {
        val watch = watch()
        watch.begin("track-1", MODE)
        watch.startReturn("track-1")

        watch.walkAway(fromMillis = 2_000L)

        assertEquals(0, delivered.size)
        assertFalse(watch.state.value.isOffTrack)
        assertNull(watch.state.value.returnToStart)
    }

    @Test
    fun `every fix updates the distance to the start, returning or not`() {
        val watch = begunWatch()

        watch.onFix(point(lat = 45.001, t = 2_000L), FixProvider.GPS)
        assertEquals(111.2, watch.state.value.returnToStart!!.distanceMeters, 1.0)
        assertEquals(180.0, watch.state.value.returnToStart!!.bearingDegrees, 0.01)

        watch.startReturn("track-1")
        watch.onFix(point(lat = 45.002, t = 3_000L), FixProvider.GPS)
        assertEquals(222.4, watch.state.value.returnToStart!!.distanceMeters, 1.0)
    }

    @Test
    fun `ending the recording leaves nothing behind for the next one`() {
        val watch = begunWatch()
        watch.keptWalkOut()
        watch.startReturn("track-1")
        watch.walkAway(fromMillis = 200_000L, fromLat = 45.010)
        assertEquals(1, delivered.size)

        watch.end("track-1")
        assertEquals(ReturnWatchState(), watch.state.value)

        // The first recording's kept points and stray are gone: the second is measured from its own start.
        watch.begin("track-2", MODE)
        assertEquals(0, watch.pathPointCount)
        watch.setStartPoint("track-2", point(lat = 45.0, t = 5_000L))
        watch.startReturn("track-2")
        assertEquals(0, watch.pathPointCount)
        watch.walkAway(fromMillis = 300_000L)
        assertEquals(2, delivered.size)
    }

    @Test
    fun `ending with no track named ends whatever is begun, as the service does when it is destroyed`() {
        val watch = begunWatch()
        watch.startReturn("track-1")

        watch.end(null)

        assertEquals(ReturnWatchState(), watch.state.value)
    }

    /**
     * Amendment 3, step 1: the watch is the live source for "a recording is running". An open
     * track row is not: a killed process leaves the same row. So the state says whether the
     * service has begun it, for which track, and in which mode, and says so only between
     * [ReturnWatch.begin] and [ReturnWatch.end].
     */
    @Test
    fun `the watch says it is begun, for which track and in which mode, only between begin and end`() {
        val watch = watch()
        assertEquals(ReturnWatchState(), watch.state.value)

        watch.begin("track-1", TrackRecordingMode.BATTERY_SAVER)
        assertEquals(ReturnWatchState(trackId = "track-1", isBegun = true, mode = TrackRecordingMode.BATTERY_SAVER), watch.state.value)

        watch.end("track-1")
        assertEquals(ReturnWatchState(), watch.state.value)
    }

    /** A Return accepted early names a track, and is not a running recording: only the service's begin is. */
    @Test
    fun `a return accepted before the service has begun does not make the watch begun`() {
        val watch = watch()

        watch.startReturn("track-1")

        assertFalse(watch.state.value.isBegun)
        assertNull(watch.state.value.mode)
        assertEquals("track-1", watch.state.value.trackId)
    }

    /**
     * Was "keeping only the readings the check reads decides exactly as keeping every reading did",
     * which held the watch to the retired `DetectOffTrackUseCase`. Now: the watch decides exactly as
     * a bare [OffTrackJudge] over the same path and readings, so the lock, the start in front of the
     * path and the delivery add nothing to the decision and drop none of it. 200 random walks of 400
     * readings, some on the walk-out path and some off it, some network-stamped, the time moving so
     * the 15 s hold and the 10 s re-arm both open and close. Every reading's "off track now" and every
     * delivery must match.
     */
    @Test
    fun `the watch decides exactly as the judge does, over the same path and readings`() {
        val random = Random(20261003)
        val walkOut = (0..6).map { i -> point(lat = 45.0 + i * 0.0005, t = 1_000L + i * 10_000L) }
        repeat(200) { walk ->
            delivered.clear()
            val watch = begunWatch()
            walkOut.forEach(watch::onKeptPoint)
            watch.startReturn("track-1")
            val judge = OffTrackJudge(listOf(point(lat = 45.0, t = 1_000L)) + walkOut)

            var expectedDeliveries = 0
            var east = 0.0
            var t = 100_000L
            repeat(400) { step ->
                east = (east + random.nextDouble(-25.0, 25.0)).coerceIn(-150.0, 150.0)
                t += random.nextLong(1, 9) * 1_000L
                val network = random.nextInt(10) == 0
                val reading = point(lat = 45.0015, lng = -122.0 + east / 78_620.0, t = if (network) t + 321L else t)

                val verdict = judge.next(reading)
                if (verdict.alert) expectedDeliveries++
                watch.onFix(reading, if (network) FixProvider.NETWORK else FixProvider.GPS)

                assertEquals("walk $walk, reading $step: off track now", verdict.isOffTrack, watch.state.value.isOffTrack)
                assertEquals("walk $walk, reading $step: deliveries so far", expectedDeliveries, delivered.size)
            }
            assertTrue("walk $walk never went off track, so it compared nothing", expectedDeliveries > 0)
        }
    }

    /**
     * Amendment 2, ruling 7: two callers at once. The service feeds fixes on a background thread
     * while the ViewModel calls from the main thread. Here two threads feed the same watch the
     * same walk away from the start, released together. One alert is the only right answer: two
     * would mean both threads read "not alerted yet" before either wrote it down. Repeated, because
     * a race does not show every time. (Retimed for dispatch -425: the walk is off the path for 15 s
     * before the threads start, so each reading they feed can be the one that goes off.)
     */
    @Test
    fun `two threads feeding the same walk away deliver one alert, not two`() {
        repeat(300) { round ->
            delivered.clear()
            val watch = begunWatch()
            watch.startReturn("track-1")
            watch.onFix(point(lat = 45.001, t = 2_000L), FixProvider.GPS)
            watch.onFix(point(lat = 45.002, t = 3_000L), FixProvider.GPS)

            val together = CyclicBarrier(2)
            val failure = AtomicReference<Throwable?>(null)
            val done = CountDownLatch(2)
            repeat(2) { feeder ->
                thread {
                    try {
                        together.await()
                        repeat(50) { i -> watch.onFix(point(lat = 45.003 + (i * 2 + feeder) * 0.001, t = 17_000L + i * 1_000L), FixProvider.GPS) }
                    } catch (t: Throwable) {
                        failure.compareAndSet(null, t)
                    } finally {
                        done.countDown()
                    }
                }
            }
            done.await()

            assertNull("round $round: a feeder threw", failure.get())
            assertEquals("round $round: deliveries", 1, delivered.size)
        }
    }

    /** The same, with the second caller being the screen: a Return toggled while fixes arrive must never throw or leave a half-written state. */
    @Test
    fun `toggling the return from one thread while another feeds fixes never throws`() {
        val watch = begunWatch()
        val failure = AtomicReference<Throwable?>(null)
        val together = CyclicBarrier(2)
        val feeder = thread {
            try {
                together.await()
                repeat(20_000) { i ->
                    val p = point(lat = 45.0 + (i % 50) * 0.001, t = 2_000L + i * 1_000L)
                    watch.onKeptPoint(p)
                    watch.onFix(p, FixProvider.GPS)
                }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            }
        }
        val screen = thread {
            try {
                together.await()
                repeat(20_000) { i -> if (i % 2 == 0) watch.startReturn("track-1") else watch.stopReturn("track-1") }
            } catch (t: Throwable) {
                failure.compareAndSet(null, t)
            }
        }
        feeder.join()
        screen.join()

        assertNull(failure.get())
        watch.stopReturn("track-1")
        assertFalse(watch.state.value.isReturning)
        assertFalse(watch.state.value.isOffTrack)
        assertEquals("a stopped return keeps no path", 0, watch.pathPointCount)
    }

    private companion object {
        /** The mode the service is recording in, in these tests. The decision does not read it; the watch only carries it. */
        val MODE = TrackRecordingMode.HIGH_ACCURACY
    }
}
