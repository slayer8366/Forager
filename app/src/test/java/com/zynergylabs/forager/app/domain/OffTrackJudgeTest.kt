package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-09-28-425 (plan task T21): "off track" measured against the path walked out, as
 * the owner chose. The path runs due north from 45.000 to 45.003 (333 m) at -122.0, a point every
 * 0.0005° (56 m). At 45° N, 0.0001° of longitude is 7.86 m, so a reading at lng -122.0 + 0.000763
 * is 60 m east of the path. Readings come once a second on whole seconds, as GPS fixes do; a
 * reading stamped off the whole second is a network fix ([isNetworkProviderFix]).
 */
class OffTrackJudgeTest {

    private val path = (0..6).map { i -> TrackPoint(lat = 45.0 + i * 0.0005, lng = -122.0, altitude = null, accuracyMeters = 4f, timestampEpochMillis = i * 10_000L) }

    private fun at(lat: Double, eastMeters: Double, t: Long, accuracy: Float? = 4f) =
        TrackPoint(lat = lat, lng = -122.0 + eastMeters / 78_620.0, altitude = null, accuracyMeters = accuracy, timestampEpochMillis = t)

    /** A reading every second from [fromSecond] to [toSecond] inclusive, [eastMeters] east of the path's middle. */
    private fun OffTrackJudge.feed(fromSecond: Int, toSecond: Int, eastMeters: Double, accuracy: Float? = 4f): List<OffTrackVerdict> =
        (fromSecond..toSecond).map { s -> next(at(45.0015, eastMeters, T0 + s * 1_000L, accuracy)) }

    @Test
    fun `going off by more than 40 m for 15 s alerts once, at 15 s`() {
        val judge = OffTrackJudge(path)
        val verdicts = judge.feed(0, 30, eastMeters = 60.0)
        assertEquals("one alert", 1, verdicts.count { it.alert })
        assertEquals("at the reading 15 s after the first one off", 15, verdicts.indexOfFirst { it.alert })
        assertFalse("not off before then", verdicts.take(15).any { it.isOffTrack })
        assertTrue("off from then on", verdicts.drop(15).all { it.isOffTrack })
    }

    @Test
    fun `walking on the other side of a street never alerts`() {
        val judge = OffTrackJudge(path)
        val verdicts = judge.feed(0, 120, eastMeters = 15.0)
        assertEquals(0, verdicts.count { it.alert })
        assertFalse(verdicts.any { it.isOffTrack })
    }

    @Test
    fun `a network reading far away does not count, and does not count as back on either`() {
        val judge = OffTrackJudge(path)
        // On the path, then one network reading 400 m away (stamped off the whole second): nothing.
        judge.feed(0, 5, eastMeters = 2.0)
        val far = judge.next(TrackPoint(lat = 45.0015, lng = -122.0 + 400.0 / 78_620.0, altitude = null, accuracyMeters = 400f, timestampEpochMillis = T0 + 5_500L))
        assertFalse(far.alert)
        assertFalse(far.isOffTrack)
        assertEquals(0, judge.feed(6, 60, eastMeters = 2.0).count { it.alert })

        // Off the path by GPS, with network readings on the path in between: still off, still one alert at 15 s.
        val judge2 = OffTrackJudge(path)
        val alerts = (0..20).flatMap { s ->
            listOf(
                judge2.next(at(45.0015, 60.0, T0 + s * 1_000L)),
                judge2.next(TrackPoint(lat = 45.0015, lng = -122.0, altitude = null, accuracyMeters = 30f, timestampEpochMillis = T0 + s * 1_000L + 400L)),
            )
        }
        assertEquals("the network readings on the path did not count as back on", 1, alerts.count { it.alert })
    }

    @Test
    fun `a GPS reading reporting poor accuracy widens the line`() {
        // 50 m off: within 40 + 20 with 20 m accuracy, beyond 40 + 4 with 4 m.
        assertEquals(0, OffTrackJudge(path).feed(0, 30, eastMeters = 50.0, accuracy = 20f).count { it.alert })
        assertEquals(1, OffTrackJudge(path).feed(0, 30, eastMeters = 50.0, accuracy = 4f).count { it.alert })
    }

    @Test
    fun `one alert per stray, re-armed only after 10 s back on the path`() {
        val judge = OffTrackJudge(path)
        assertEquals("first stray", 1, judge.feed(0, 20, 60.0).count { it.alert })
        assertTrue("still off at the end of it", judge.next(at(45.0015, 60.0, T0 + 21_000L)).isOffTrack)
        // Back on for 5 s, not long enough to re-arm; off again for 20 s: no second alert.
        judge.feed(22, 26, 2.0)
        assertEquals("not re-armed after 5 s back on", 0, judge.feed(27, 47, 60.0).count { it.alert })
        // Back on for 10 s: re-armed, and off again for 15 s: a second alert.
        val backOn = judge.feed(48, 58, 2.0)
        assertFalse("back on clears off track", backOn.last().isOffTrack)
        assertEquals("re-armed after 10 s back on", 1, judge.feed(59, 80, 60.0).count { it.alert })
    }

    @Test
    fun `walking back toward the start along the path never alerts`() {
        val judge = OffTrackJudge(path)
        // From the far end back to the start, about 1.4 m/s, jittering 6 m either side.
        val verdicts = (0..240).map { s ->
            val lat = 45.003 - s * (0.003 / 240)
            judge.next(at(lat, if (s % 2 == 0) 6.0 else -6.0, T0 + s * 1_000L))
        }
        assertEquals(0, verdicts.count { it.alert })
        assertFalse(verdicts.any { it.isOffTrack })
    }

    @Test
    fun `an empty path decides nothing`() {
        val judge = OffTrackJudge(emptyList())
        assertEquals(0, judge.feed(0, 60, 500.0).count { it.alert })
    }

    @Test
    fun `three readings are needed, not only 15 s`() {
        val judge = OffTrackJudge(path)
        assertFalse(judge.next(at(45.0015, 60.0, T0)).alert)
        assertFalse("two readings 20 s apart are not enough", judge.next(at(45.0015, 60.0, T0 + 20_000L)).alert)
        assertTrue("the third is", judge.next(at(45.0015, 60.0, T0 + 21_000L)).alert)
    }

    @Test
    fun `a reading back on the path restarts the 15 s`() {
        val judge = OffTrackJudge(path)
        judge.feed(0, 10, 60.0)
        judge.feed(11, 11, 2.0)
        assertEquals("10 s off, one on, 10 s off: no alert", 0, judge.feed(12, 22, 60.0).count { it.alert })
        assertEquals("15 s off since the reading back on", 1, judge.feed(23, 27, 60.0).count { it.alert })
    }

    private companion object {
        const val T0 = 1_791_014_000_000L
    }
}
