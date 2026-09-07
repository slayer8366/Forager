package com.forager.app.domain

import com.forager.app.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [movingPace] on hand-built point lists — return-estimate dispatch, Item 2. Every expected value
 * is worked by hand from pinned inputs, never from the pace calculation: points step due north by
 * 0.000135° every 15 s, which at π × 6 371 008.8 / 180 m per degree is 15.011336 m per step and an
 * implied 1.0007557 m/s. Doppler speeds are pinned floats. Each test that establishes a property
 * about speeds also asserts how many points were examined for a Doppler sample and how many were
 * skipped, and why — the dispatch's generalised parsing hazard (a check that never saw the rows
 * that could fail it).
 */
class MovingPaceTest {

    @Test
    fun `a track with no stored speeds is measured by point differencing, and every point is reported as skipped for Doppler`() {
        // 21 points, 20 intervals of 15 s: exactly the five-minute bar.
        val pace = movingPace(northPoints(count = 21))

        assertEquals(20, pace.intervalsExamined)
        assertEquals(20, pace.intervalsMoving)
        assertEquals(300_000L, pace.movingMillis)
        assertEquals(300.22672, pace.movingMeters, 0.001)
        assertEquals(1.0007557, pace.differencingSpeed!!, 1e-6)
        assertEquals(PaceSource.DIFFERENCING, pace.source)
        assertEquals(1.0007557, pace.speedMetersPerSecond, 1e-6)
        assertEquals(300_000L, pace.governingMovingMillis)
        // Rows examined for Doppler: 20 (one per moving interval); all 20 skipped as null; none counted.
        assertEquals(20, pace.pointsWithoutSpeed)
        assertEquals(0, pace.pointsBelowFloor)
        assertEquals(0, pace.pointsCounted)
        assertNull(pace.dopplerSpeed)
        assertNull(pace.comparison)
    }

    @Test
    fun `below five minutes of moving time the default applies, and at five minutes it stops applying`() {
        // 20 points: 19 intervals, 285 s of moving time — 15 s short of the bar.
        val short = movingPace(northPoints(count = 20))
        assertEquals(285_000L, short.movingMillis)
        assertEquals(PaceSource.DEFAULT, short.source)
        assertEquals(0.8888888888888889, short.speedMetersPerSecond, 1e-12) // 3.2 km/h ÷ 3.6
        assertEquals(0L, short.governingMovingMillis)

        val atBar = movingPace(northPoints(count = 21))
        assertEquals(PaceSource.DIFFERENCING, atBar.source)
        assertEquals(1.0007557, atBar.speedMetersPerSecond, 1e-6)
    }

    @Test
    fun `stored Doppler speeds govern once they cover five minutes, and the comparison against differencing is reported`() {
        val pace = movingPace(northPoints(count = 21, speed = 0.9f))

        assertEquals(PaceSource.DOPPLER, pace.source)
        assertEquals(0.9, pace.speedMetersPerSecond, 1e-6)
        assertEquals(300_000L, pace.dopplerMovingMillis)
        assertEquals(300_000L, pace.governingMovingMillis)
        // Rows: 20 examined, 20 counted, none null, none below the floor.
        assertEquals(20, pace.pointsCounted)
        assertEquals(0, pace.pointsWithoutSpeed)
        assertEquals(0, pace.pointsBelowFloor)

        val comparison = pace.comparison!!
        assertEquals(0.9, comparison.dopplerSpeed, 1e-6)
        assertEquals(1.0007557, comparison.differencingSpeed, 1e-6)
        assertEquals(0.89932, comparison.ratio, 1e-4) // 0.9 ÷ 1.0007557
        assertEquals(20, comparison.intervals)
        assertEquals(300_000L, comparison.movingMillis)
    }

    /**
     * The case the design exists for: a photographer stationary for eight minutes in the middle
     * of a walk. The stop is one interval of 3.34 m over 480 s (0.007 m/s, far below the floor)
     * and drops out; an average over elapsed time would read 0.56 m/s.
     */
    @Test
    fun `a multi-minute stop in the middle of a walk does not drag the moving speed down`() {
        val before = northPoints(count = 21)
        val last = before.last()
        // 0.00003° north of the last point, 480 s later: the stop.
        val afterStop = TrackPoint(lat = last.lat + 0.00003, lng = LNG, altitude = null, accuracyMeters = null, timestampEpochMillis = last.timestampEpochMillis + 480_000L)
        val after = northPoints(count = 20, startLat = afterStop.lat, startMillis = afterStop.timestampEpochMillis).drop(1)
        val points = before + afterStop + after

        val pace = movingPace(points)

        assertEquals(40, pace.intervalsExamined)
        assertEquals(39, pace.intervalsMoving)
        assertEquals(585_000L, pace.movingMillis)
        assertEquals(1.0007557, pace.speedMetersPerSecond, 1e-6)
        assertEquals(39, pace.pointsWithoutSpeed)
        assertEquals(0, pace.pointsCounted)
    }

    /**
     * The owner's ruling, as a direct test rather than an inference: the same walk with a long
     * stop inserted into it — later timestamps shifted, positions untouched — gives the same
     * speed, under both instruments.
     */
    @Test
    fun `the measured speed is stable across an inserted stop, by differencing and by Doppler`() {
        for (speed in listOf<Float?>(null, 0.9f)) {
            val walk = northPoints(count = 41, speed = speed) // 40 intervals, 600 s
            val withStop = walk.mapIndexed { i, p -> if (i > 20) p.copy(timestampEpochMillis = p.timestampEpochMillis + 480_000L) else p }

            val plain = movingPace(walk)
            val stopped = movingPace(withStop)

            assertEquals(plain.source, stopped.source)
            assertEquals(plain.speedMetersPerSecond, stopped.speedMetersPerSecond, 1e-9)
            assertEquals(40, plain.intervalsMoving)
            assertEquals(39, stopped.intervalsMoving)
            assertEquals(600_000L, plain.movingMillis)
            assertEquals(585_000L, stopped.movingMillis)
        }
    }

    @Test
    fun `an interval counts as moving at the floor and not below it`() {
        // 0.0000675° in 15 s is 7.5057 m, 0.50038 m/s — kept; 0.00006° is 6.6717 m, 0.4448 m/s — dropped.
        val points = listOf(
            point(lat = 45.0, t = 0L),
            point(lat = 45.0000675, t = 15_000L),
            point(lat = 45.0001275, t = 30_000L),
        )

        val pace = movingPace(points)

        assertEquals(2, pace.intervalsExamined)
        assertEquals(1, pace.intervalsMoving)
        assertEquals(15_000L, pace.movingMillis)
        assertEquals(7.50567, pace.movingMeters, 0.001)
    }

    @Test
    fun `a stored speed below the floor is skipped and counted as such, and one at the floor is counted`() {
        val points = listOf(
            point(lat = 45.0, t = 0L),
            point(lat = 45.000135, t = 15_000L, speed = 0.49f),
            point(lat = 45.000270, t = 30_000L, speed = 0.5f),
        )

        val pace = movingPace(points)

        assertEquals(2, pace.intervalsMoving)
        assertEquals(1, pace.pointsBelowFloor)
        assertEquals(1, pace.pointsCounted)
        assertEquals(0, pace.pointsWithoutSpeed)
        assertEquals(0.5, pace.dopplerSpeed!!, 1e-6)
        assertEquals(15_000L, pace.dopplerMovingMillis)
    }

    @Test
    fun `Doppler samples are weighted by their interval's duration`() {
        // 15 s at 1.0 then 30 s at 0.7: (1.0 × 15 + 0.7 × 30) ÷ 45 = 0.8.
        val points = listOf(
            point(lat = 45.0, t = 0L),
            point(lat = 45.000135, t = 15_000L, speed = 1.0f),
            point(lat = 45.000405, t = 45_000L, speed = 0.7f),
        )

        val pace = movingPace(points)

        assertEquals(2, pace.pointsCounted)
        assertEquals(0.8, pace.dopplerSpeed!!, 1e-6)
    }

    /** A track that straddles the migration, or a device that populates speed sporadically: Doppler has ten intervals, differencing has forty. */
    @Test
    fun `Doppler below its own five minutes yields to differencing, and the skipped rows are reported`() {
        val points = northPoints(count = 41).mapIndexed { i, p -> if (i in 1..10) p.copy(speedMetersPerSecond = 0.9f) else p }

        val pace = movingPace(points)

        assertEquals(PaceSource.DIFFERENCING, pace.source)
        assertEquals(1.0007557, pace.speedMetersPerSecond, 1e-6)
        assertEquals(150_000L, pace.dopplerMovingMillis)
        assertEquals(10, pace.pointsCounted)
        assertEquals(30, pace.pointsWithoutSpeed)
        assertEquals(0, pace.pointsBelowFloor)
        assertEquals(10, pace.comparison!!.intervals)
        assertEquals(0.9, pace.comparison!!.dopplerSpeed, 1e-6)
    }

    @Test
    fun `the point after a stop carries a moving speed but is not a Doppler sample, because its interval was the stop`() {
        val points = listOf(
            point(lat = 45.0, t = 0L),
            point(lat = 45.000135, t = 15_000L, speed = 0.9f),
            point(lat = 45.000165, t = 495_000L, speed = 1.2f), // 3.34 m over 480 s
        )

        val pace = movingPace(points)

        assertEquals(2, pace.intervalsExamined)
        assertEquals(1, pace.intervalsMoving)
        assertEquals(1, pace.pointsCounted)
        assertEquals(0, pace.pointsWithoutSpeed)
        assertEquals(0, pace.pointsBelowFloor)
        assertEquals(0.9, pace.dopplerSpeed!!, 1e-6)
    }

    @Test
    fun `no points or one point gives the default with nothing measured`() {
        for (points in listOf(emptyList(), listOf(point(lat = 45.0, t = 0L)))) {
            val pace = movingPace(points)
            assertEquals(0, pace.intervalsExamined)
            assertEquals(PaceSource.DEFAULT, pace.source)
            assertEquals(0.8888888888888889, pace.speedMetersPerSecond, 1e-12)
            assertNull(pace.differencingSpeed)
            assertNull(pace.dopplerSpeed)
            assertNull(pace.comparison)
        }
    }

    private fun point(lat: Double, t: Long, speed: Float? = null) =
        TrackPoint(lat = lat, lng = LNG, altitude = null, accuracyMeters = null, timestampEpochMillis = t, speedMetersPerSecond = speed)

    private companion object {
        const val LNG = -122.0

        /** [count] points due north from [startLat] at 0.000135° (15.011336 m) every 15 s. */
        fun northPoints(count: Int, speed: Float? = null, startLat: Double = 45.0, startMillis: Long = 1_700_000_000_000L): List<TrackPoint> =
            (0 until count).map { i ->
                TrackPoint(
                    lat = startLat + i * 0.000135,
                    lng = LNG,
                    altitude = null,
                    accuracyMeters = null,
                    timestampEpochMillis = startMillis + i * 15_000L,
                    speedMetersPerSecond = speed,
                )
            }
    }
}
