package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-09-28-510, the owner's rule 3 (RECORD -508): "an approximate reading never enters a
 * track, the off-track alert or 'Arrived'". "Arrived" is `AvailabilityScreenApproximatePositionTest`'s.
 * These two follow the reading the S22 gave indoors, a network fix of about 100 m, down the paths
 * recording and the return take, which this dispatch does not touch and which never read the screen's
 * state: so they pass before this dispatch as after it, and guard against a later change feeding the
 * screen's approximate reading into them. Each carries its own control, so it is seen able to fail.
 *
 * Not covered, because it is not true today and is outside this dispatch (the report's findings): a
 * **GPS** reading worse than 50 m is kept in a Battery saver track (ceiling 100 m) and counts for the
 * off-track judge, its line widened by its own accuracy.
 */
class ApproximateReadingNeverDecidesTest {

    /** A network reading of [accuracy] (120 m by default), as the tracker hands it on: stamped 123 ms past the second. */
    private fun network(lat: Double, t: Long, accuracy: Float = 120f) =
        LocationFix.Update(lat = lat, lng = -122.0, altitude = null, accuracyMeters = accuracy, timestampEpochMillis = t + 123, provider = FixProvider.NETWORK)

    /** The same, from GPS: on the whole second, 5 m. */
    private fun gps(lat: Double, t: Long) =
        LocationFix.Update(lat = lat, lng = -122.0, altitude = null, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)

    /**
     * The service's one place a fix becomes a point (`TrackRecordingService`: `toTrackPoint`, then the
     * sampler), then the read every track is shown through (`RoomTrackRepository`, `excludeNetworkProviderFixes`).
     */
    private fun shownInTrack(fix: LocationFix.Update, mode: TrackRecordingMode): List<TrackPoint> {
        val candidate = fix.toTrackPoint()
        val stored = if (LocationSampler(mode).shouldAccept(lastAccepted = null, candidate = candidate)) listOf(candidate) else emptyList()
        return excludeNetworkProviderFixes(stored)
    }

    @Test
    fun `a network reading over 50 m never becomes a point a track shows, in any recording mode`() {
        // 120 m: refused by the sampler in every mode, Battery saver's 100 m ceiling included.
        // 70 m: refused under High accuracy and Balanced; kept by Battery saver, and excluded when the track is read.
        // (Revert check c01 found the 120 m reading alone never reached the read: both are needed.)
        listOf(120f, 70f).forEach { accuracy ->
            TrackRecordingMode.entries.forEach { mode ->
                assertTrue("$mode, $accuracy m: refused by the sampler or excluded at the read", shownInTrack(network(45.01, 1_700_000_000_000L, accuracy), mode).isEmpty())
            }
        }
        // Control: a GPS fix the same way is shown, so this path can show a point.
        TrackRecordingMode.entries.forEach { mode ->
            assertEquals("$mode", 1, shownInTrack(gps(45.01, 1_700_000_000_000L), mode).size)
        }
    }

    private fun begunReturn(): Pair<ReturnWatch, MutableList<Alert>> {
        val alerts = mutableListOf<Alert>()
        val watch = ReturnWatch(computeReturnToStart = ComputeReturnToStartUseCase(), alertDelivery = { alerts += it })
        watch.begin("track-1", TrackRecordingMode.BALANCED)
        watch.setStartPoint("track-1", TrackPoint(45.0, -122.0, null, null, 1_000L))
        // The walk out, kept by the service: north from the start, every 56 m.
        (0..6).forEach { i -> watch.onKeptPoint(TrackPoint(45.0 + i * 0.0005, -122.0, null, null, 1_000L + i * 10_000L)) }
        watch.startReturn("track-1")
        return watch to alerts
    }

    @Test
    fun `while returning, network readings over 50 m walking well off the path never set off-track or alert`() {
        val (watch, alerts) = begunReturn()

        // Eight readings walking away east, 5 s apart, every one far off the path: what the service hands the watch.
        repeat(8) { i -> watch.onFix(network(45.002, 2_000_000L + i * 5_000L).let { it.copy(lng = -122.0 + 0.01 + i * 0.002) }.toTrackPoint(), FixProvider.NETWORK) }

        assertFalse(watch.state.value.isOffTrack)
        assertTrue(alerts.isEmpty())

        // Control: the same walk by GPS goes off and alerts once, so the watch above could have.
        val (gpsWatch, gpsAlerts) = begunReturn()
        repeat(8) { i -> gpsWatch.onFix(gps(45.002, 2_000_000L + i * 5_000L).let { it.copy(lng = -122.0 + 0.01 + i * 0.002) }.toTrackPoint(), FixProvider.GPS) }
        assertTrue(gpsWatch.state.value.isOffTrack)
        assertEquals(1, gpsAlerts.size)
    }

    // ── Dispatch 2026-09-28-527: the live reading is judged by its provider ──

    /** Eight readings walking away east, 5 s apart, every one well off the path, stamped [stamp] past the second. */
    private fun walkOff(watch: ReturnWatch, provider: FixProvider, stamp: Long, accuracy: Float = 15f) =
        repeat(8) { i ->
            val reading = TrackPoint(lat = 45.002, lng = -122.0 + 0.01 + i * 0.002, altitude = null, accuracyMeters = accuracy, timestampEpochMillis = 2_000_000L + i * 5_000L + stamp)
            watch.onFix(reading, provider)
        }

    @Test
    fun `while returning, 15 m network readings on the whole second never set off-track or alert`() {
        val (watch, alerts) = begunReturn()
        walkOff(watch, FixProvider.NETWORK, stamp = 0L)
        assertFalse(watch.state.value.isOffTrack)
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `while returning, 15 m readings from an unknown provider never set off-track or alert`() {
        val (watch, alerts) = begunReturn()
        walkOff(watch, FixProvider.UNKNOWN, stamp = 0L)
        assertFalse(watch.state.value.isOffTrack)
        assertTrue(alerts.isEmpty())
    }

    /** GPS readings stamped with milliseconds, as 3 of the S22's walk fixes were: they count, and the walk off alerts once. */
    @Test
    fun `while returning, GPS readings stamped with milliseconds count, and walking off alerts`() {
        val (watch, alerts) = begunReturn()
        walkOff(watch, FixProvider.GPS, stamp = 479L)
        assertTrue(watch.state.value.isOffTrack)
        assertEquals(1, alerts.size)
    }
}
