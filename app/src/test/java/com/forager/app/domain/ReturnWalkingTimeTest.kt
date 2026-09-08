package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [returnWalkingTime] — the model in one line, and the degraded form under every condition that
 * should trigger it, asserted per condition. Tracks step due north by 0.000135° (15.011336 m) every
 * 15 s; walking times are worked by hand from that: 60 legs are 900.68015 m, which at a stored
 * 0.9f (0.899999976 as a double) is 1000.7557 s. The default is 3.2 km/h = 0.888… m/s.
 */
class ReturnWalkingTimeTest {

    @Test
    fun `a settled Doppler pace on a fresh fix at the last point is a plain walking time with no degrade`() {
        // 61 points: 60 intervals, 900 s of moving time — fifteen minutes, the settled bar.
        val track = track(northPoints(count = 61, speed = 0.9f))

        val estimate = estimate(track, current = lastOf(track))

        assertEquals(1_000_756L, estimate.walkingMillis)
        assertEquals(emptySet<DegradeReason>(), estimate.degradeReasons)
        assertFalse(estimate.isAtLeast)
        assertEquals(PaceSource.DOPPLER, estimate.pace.source)
        assertEquals(900.68015, estimate.path.totalMeters, 0.001)
    }

    @Test
    fun `before the data bar the default pace is used and the estimate reads at least`() {
        // 12 points: 11 intervals, 165 s. 165.12469 m ÷ 0.888… m/s = 185.7653 s.
        val track = track(northPoints(count = 12))

        val estimate = estimate(track, current = lastOf(track))

        assertEquals(185_765L, estimate.walkingMillis)
        assertEquals(setOf(DegradeReason.NO_MEASURED_PACE), estimate.degradeReasons)
        assertTrue(estimate.isAtLeast)
    }

    @Test
    fun `a pace measured for less than fifteen minutes is freshly measured`() {
        val track = track(northPoints(count = 21, speed = 0.9f)) // 300 s: measured, not settled

        val estimate = estimate(track, current = lastOf(track))

        assertEquals(setOf(DegradeReason.PACE_FRESHLY_MEASURED), estimate.degradeReasons)
        assertEquals(PaceSource.DOPPLER, estimate.pace.source)
    }

    @Test
    fun `a stale fix degrades, and a lost fix or no fix withholds the estimate`() {
        val track = track(northPoints(count = 61, speed = 0.9f))

        val stale = returnWalkingTime(track, origin = null, current = lastOf(track), fixFreshness = FixFreshness.STALE) as ReturnWalkingTime.Estimate
        assertEquals(setOf(DegradeReason.STALE_FIX), stale.degradeReasons)
        assertEquals(1_000_756L, stale.walkingMillis)

        assertEquals(ReturnWalkingTime.Withheld(WithholdReason.NO_FIX), returnWalkingTime(track, origin = null, current = lastOf(track), fixFreshness = FixFreshness.LOST))
        assertEquals(ReturnWalkingTime.Withheld(WithholdReason.NO_FIX), returnWalkingTime(track, origin = null, current = null, fixFreshness = FixFreshness.FRESH))
    }

    @Test
    fun `a walker more than 50 m off the track has the hop added and the estimate degraded`() {
        val track = track(northPoints(count = 61, speed = 0.9f))
        // 0.00054° beyond the last point: 60.0453 m. (900.68015 + 60.04534) ÷ 0.899999976 = 1067.4728 s.
        val estimate = estimate(track, current = LatLng(track.points.last().lat + 0.00054, LNG))

        assertEquals(1_067_473L, estimate.walkingMillis)
        assertEquals(setOf(DegradeReason.FAR_FROM_TRACK), estimate.degradeReasons)
    }

    /** The band's other half at this level: a walker who was far and has come back to 47.8 m is still far, and the reason stays. */
    @Test
    fun `the far-from-track degrade holds through the band until the hop drops below 45 m`() {
        val track = track(northPoints(count = 61, speed = 0.9f))
        val at47 = LatLng(track.points.last().lat + 0.00043, LNG) // 47.8139 m

        val stillFar = returnWalkingTime(track, origin = null, current = at47, fixFreshness = FixFreshness.FRESH, previousHopBand = HopBand.FAR) as ReturnWalkingTime.Estimate
        assertEquals(setOf(DegradeReason.FAR_FROM_TRACK), stillFar.degradeReasons)
        assertEquals(HopBand.FAR, stillFar.path.hopBand)

        val fresh = returnWalkingTime(track, origin = null, current = at47, fixFreshness = FixFreshness.FRESH) as ReturnWalkingTime.Estimate
        assertEquals(emptySet<DegradeReason>(), fresh.degradeReasons)
        assertEquals(HopBand.COUNTED, fresh.path.hopBand)
    }

    @Test
    fun `an excluded tenth of the stored points degrades the path as under-measured, and less does not`() {
        val points = northPoints(count = 61, speed = 0.9f)

        val tenth = estimate(track(points, excludedPointCount = 7), current = lastOf(track(points))) // 7 of 68 = 10.3 %
        assertEquals(setOf(DegradeReason.PATH_UNDER_MEASURED), tenth.degradeReasons)

        val under = estimate(track(points, excludedPointCount = 6), current = lastOf(track(points))) // 6 of 67 = 9.0 %
        assertEquals(emptySet<DegradeReason>(), under.degradeReasons)
    }

    @Test
    fun `a track that is mostly network fixes degrades on that and on being under-measured`() {
        val points = northPoints(count = 61, speed = 0.9f)

        val estimate = estimate(track(points, excludedPointCount = 200), current = lastOf(track(points))) // 200 of 261 = 76.6 %

        assertEquals(setOf(DegradeReason.MOSTLY_NETWORK_FIXES, DegradeReason.PATH_UNDER_MEASURED), estimate.degradeReasons)
    }

    @Test
    fun `fewer than ten surviving points degrades`() {
        val track = track(northPoints(count = 9))

        val estimate = estimate(track, current = lastOf(track))

        assertEquals(setOf(DegradeReason.FEW_POINTS, DegradeReason.NO_MEASURED_PACE), estimate.degradeReasons)
    }

    @Test
    fun `no usable points withholds the estimate rather than measuring a path that is not there`() {
        val oneSurvivor = track(northPoints(count = 1), excludedPointCount = 5)
        assertEquals(ReturnWalkingTime.Withheld(WithholdReason.NO_USABLE_POINTS), returnWalkingTime(oneSurvivor, origin = null, current = LatLng(45.0, LNG), fixFreshness = FixFreshness.FRESH))

        val empty = track(emptyList())
        assertEquals(ReturnWalkingTime.Withheld(WithholdReason.NO_USABLE_POINTS), returnWalkingTime(empty, origin = null, current = LatLng(45.0, LNG), fixFreshness = FixFreshness.FRESH))
    }

    @Test
    fun `an origin waypoint adds its leg without degrading, and a track with no origin is not degraded for it`() {
        val track = track(northPoints(count = 61, speed = 0.9f), originWaypointId = "wp-origin")
        // 0.0005° south of the first point: 55.5975 m. (900.68015 + 55.59754) ÷ 0.899999976 = 1062.5308 s.
        val origin = Waypoint(id = "wp-origin", lat = 44.9995, lng = LNG, altitude = null, name = "Start", note = "", createdAtEpochMillis = 0L, trackId = "t")

        val withOrigin = returnWalkingTime(track, origin = origin, current = lastOf(track), fixFreshness = FixFreshness.FRESH) as ReturnWalkingTime.Estimate
        assertEquals(1_062_531L, withOrigin.walkingMillis)
        assertEquals(emptySet<DegradeReason>(), withOrigin.degradeReasons)

        val withoutOrigin = estimate(track(northPoints(count = 61, speed = 0.9f)), current = lastOf(track))
        assertEquals(1_000_756L, withoutOrigin.walkingMillis)
        assertEquals(emptySet<DegradeReason>(), withoutOrigin.degradeReasons)
    }

    /**
     * The whole point of the owner's ruling, as a direct test: the same track with an eight-minute
     * stop inserted (later timestamps shifted, positions untouched, so the distance remaining is
     * the same) gives the same figure. On a differencing track, deliberately: with Doppler samples
     * all pinned to one value the mean cannot move whatever the floor does, and this test would
     * pass with the floor removed — the check would never see the rows that could fail it.
     */
    @Test
    fun `the estimate does not move when a long stop is inserted with the same distance remaining`() {
        val walk = northPoints(count = 81) // 80 legs of 15.011336 m in 15 s each: 1200 s at 1.0007557 m/s
        val withStop = walk.mapIndexed { i, p -> if (i > 40) p.copy(timestampEpochMillis = p.timestampEpochMillis + 480_000L) else p }

        val plain = estimate(track(walk), current = lastOf(track(walk)))
        val stopped = estimate(track(withStop), current = lastOf(track(withStop)))

        assertEquals(1_200_000.0, plain.walkingMillis.toDouble(), 1.0) // 1200.90687 m ÷ 1.0007557 m/s = 1200 s
        assertEquals(PaceSource.DIFFERENCING, stopped.pace.source)
        assertEquals(plain.walkingMillis, stopped.walkingMillis)
        assertEquals(plain.degradeReasons, stopped.degradeReasons)
        assertEquals(emptySet<DegradeReason>(), stopped.degradeReasons)
    }

    private fun estimate(track: Track, current: LatLng) =
        returnWalkingTime(track, origin = null, current = current, fixFreshness = FixFreshness.FRESH) as ReturnWalkingTime.Estimate

    private fun lastOf(track: Track) = LatLng(track.points.last().lat, track.points.last().lng)

    private fun track(points: List<TrackPoint>, excludedPointCount: Int = 0, originWaypointId: String? = null) = Track(
        id = "t",
        name = null,
        startedAtEpochMillis = 0L,
        endedAtEpochMillis = null,
        points = points,
        originWaypointId = originWaypointId,
        excludedPointCount = excludedPointCount,
    )

    private companion object {
        const val LNG = -122.0

        fun northPoints(count: Int, speed: Float? = null): List<TrackPoint> =
            (0 until count).map { i ->
                TrackPoint(lat = 45.0 + i * 0.000135, lng = LNG, altitude = null, accuracyMeters = null, timestampEpochMillis = 1_700_000_000_000L + i * 15_000L, speedMetersPerSecond = speed)
            }
    }
}
