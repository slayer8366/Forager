package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [returnWalkingTimeAlong]: the walk back along the way home shown (dispatch 2026-10-24-10; RECORD -1589, "app-wide"),
 * with [returnWalkingTime]'s pace and every degrade and withhold rule, only the distance differing.
 */
class ReturnWalkingTimeAlongTest {

    private fun northPoints(count: Int, speed: Float? = null): List<TrackPoint> = (0 until count).map { i ->
        TrackPoint(lat = 45.0 + i * 0.000135, lng = -120.0, altitude = null, accuracyMeters = null, timestampEpochMillis = 1_700_000_000_000L + i * 15_000L, speedMetersPerSecond = speed)
    }

    private fun track(points: List<TrackPoint>) = Track(id = "t", name = null, startedAtEpochMillis = 0L, endedAtEpochMillis = null, points = points)

    private fun path(meters: Double, hopBand: HopBand = HopBand.NONE, hop: Double = 0.0) =
        PathHome(trackMeters = meters, hopMeters = hop, hopBand = hopBand, originLegMeters = null, pointCount = 100)

    @Test
    fun `the walking time is the shown line's metres at the track's own pace`() {
        val track = track(northPoints(count = 61, speed = 0.9f))
        val estimate = returnWalkingTimeAlong(track, path(1_800.0), FixFreshness.FRESH) as ReturnWalkingTime.Estimate
        // 1,800 m at a stored 0.9f (0.899999976 m/s): 2000.0000533 s.
        assertEquals(2_000_000L, estimate.walkingMillis)
        assertEquals(emptySet<DegradeReason>(), estimate.degradeReasons)
        assertEquals(PaceSource.DOPPLER, estimate.pace.source)
    }

    @Test
    fun `the track's degrade rules still apply, and a lost fix withholds`() {
        val early = track(northPoints(count = 12))
        val estimate = returnWalkingTimeAlong(early, path(500.0), FixFreshness.STALE) as ReturnWalkingTime.Estimate
        assertEquals(setOf(DegradeReason.NO_MEASURED_PACE, DegradeReason.STALE_FIX), estimate.degradeReasons)

        assertEquals(ReturnWalkingTime.Withheld(WithholdReason.NO_FIX), returnWalkingTimeAlong(early, path(500.0), FixFreshness.LOST))
        assertEquals(ReturnWalkingTime.Withheld(WithholdReason.NO_USABLE_POINTS), returnWalkingTimeAlong(track(emptyList()), path(500.0), FixFreshness.FRESH))
    }
}
