package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputeTrackStatisticsUseCaseTest {

    private val useCase = ComputeTrackStatisticsUseCase()

    @Test
    fun `an empty track has zero distance and duration, and no elevation data`() {
        val stats = useCase(emptyList())

        assertEquals(0.0, stats.distanceMeters, 1e-9)
        assertEquals(0L, stats.durationMillis)
        assertNull(stats.elevationGainMeters)
        assertNull(stats.elevationLossMeters)
        assertNull(stats.averageSpeedMetersPerSecond)
        assertNull(stats.elevationCoverage)
    }

    @Test
    fun `distance sums consecutive great-circle legs, not a straight line end to end`() {
        // Three points roughly 111km apart in latitude each (1 degree) — a real zig-zag, not a
        // straight line, so summed distance should exceed the direct start-to-end distance.
        val points = listOf(
            point(lat = 0.0, lng = 0.0, altitude = null, t = 0L),
            point(lat = 1.0, lng = 0.0, altitude = null, t = 1_000L),
            point(lat = 1.0, lng = 1.0, altitude = null, t = 2_000L),
        )

        val stats = useCase(points)

        val directDistance = GeoDistance.metersBetween(LatLng(0.0, 0.0), LatLng(1.0, 1.0))
        assertTrue("summed leg distance (${stats.distanceMeters}) should exceed the direct distance ($directDistance)", stats.distanceMeters > directDistance)
    }

    @Test
    fun `duration is the span between the first and last point`() {
        val points = listOf(
            point(lat = 0.0, lng = 0.0, altitude = null, t = 10_000L),
            point(lat = 0.0, lng = 0.001, altitude = null, t = 70_000L),
        )

        val stats = useCase(points)

        assertEquals(60_000L, stats.durationMillis)
    }

    @Test
    fun `elevation gain and loss accumulate only over consecutive pairs that both report altitude`() {
        val points = listOf(
            point(lat = 0.0, lng = 0.0, altitude = 100.0, t = 0L),
            point(lat = 0.0, lng = 0.001, altitude = 150.0, t = 1_000L), // +50 gain
            point(lat = 0.0, lng = 0.002, altitude = null, t = 2_000L), // gap: skipped, not a zero delta
            point(lat = 0.0, lng = 0.003, altitude = 120.0, t = 3_000L), // no altitude on previous point: skipped
            point(lat = 0.0, lng = 0.004, altitude = 90.0, t = 4_000L), // -30 loss
        )

        val stats = useCase(points)

        assertEquals(50.0, stats.elevationGainMeters!!, 1e-9)
        assertEquals(30.0, stats.elevationLossMeters!!, 1e-9)
        // 4 of the 5 points report an altitude (only the gap point doesn't) — coverage counts
        // points, not banked deltas, so it's unaffected by the gap being skipped above.
        assertEquals(0.8, stats.elevationCoverage!!, 1e-9)
    }

    @Test
    fun `a stationary track with realistic GPS altitude noise reports approximately zero ascent and descent`() {
        // A phone sitting still: every reading wanders within +-3.5m of 250.0, comfortably under
        // ElevationHysteresis.THRESHOLD_METERS (4.0), so nothing should ever bank. Without
        // hysteresis, a naive delta sum over these same readings would report tens of metres of
        // fictional gain and loss.
        val points = listOf(
            point(lat = 0.0, lng = 0.000, altitude = 250.0, t = 0L),
            point(lat = 0.0, lng = 0.001, altitude = 253.5, t = 1_000L),
            point(lat = 0.0, lng = 0.002, altitude = 246.8, t = 2_000L),
            point(lat = 0.0, lng = 0.003, altitude = 251.2, t = 3_000L),
            point(lat = 0.0, lng = 0.004, altitude = 248.9, t = 4_000L),
            point(lat = 0.0, lng = 0.005, altitude = 252.7, t = 5_000L),
            point(lat = 0.0, lng = 0.006, altitude = 247.3, t = 6_000L),
            point(lat = 0.0, lng = 0.007, altitude = 250.5, t = 7_000L),
        )

        val stats = useCase(points)

        assertEquals(0.0, stats.elevationGainMeters!!, 1e-9)
        assertEquals(0.0, stats.elevationLossMeters!!, 1e-9)
        assertEquals(1.0, stats.elevationCoverage!!, 1e-9)
    }

    @Test
    fun `a staircase with known total gain spread across sub-threshold steps is recovered within one threshold's tolerance`() {
        // 20 steps of +1.5m each (each individually under the 4.0m threshold) for a true total
        // climb of 30.0m from 100.0 to 130.0. Hysteresis only banks once cumulative drift from the
        // running reference crosses the threshold, so the very last partial climb (whatever hadn't
        // yet crossed the threshold when the track ends) is legitimately left unbanked — recovery
        // is asserted within one threshold's worth of tolerance, not exactly.
        val altitudes = generateSequence(100.0) { it + 1.5 }.take(21).toList()
        val points = altitudes.mapIndexed { index, altitude ->
            point(lat = 0.0, lng = index * 0.001, altitude = altitude, t = index * 1_000L)
        }

        val stats = useCase(points)

        assertEquals(30.0, stats.elevationGainMeters!!, ElevationHysteresis.THRESHOLD_METERS)
        assertEquals(0.0, stats.elevationLossMeters!!, 1e-9)
        assertEquals(1.0, stats.elevationCoverage!!, 1e-9)
    }

    @Test
    fun `a much longer staircase keeps its unbanked tail under one threshold, regardless of track length`() {
        // The property a single short staircase can't demonstrate on its own: the unbanked
        // remainder is bounded by ElevationHysteresis.THRESHOLD_METERS because banking resets the
        // reference every time it fires, not because the track happened to be short. 200 steps of
        // +1.5m each — 10x the other staircase test's length, same per-step size — for a true total
        // climb of 300.0m. If the implementation accumulated drift instead of resetting on each
        // bank, the shortfall here would grow with length instead of staying pinned under one
        // threshold.
        val altitudes = generateSequence(100.0) { it + 1.5 }.take(201).toList()
        val points = altitudes.mapIndexed { index, altitude ->
            point(lat = 0.0, lng = index * 0.001, altitude = altitude, t = index * 1_000L)
        }

        val stats = useCase(points)

        val trueGain = 200 * 1.5
        val shortfall = trueGain - stats.elevationGainMeters!!
        assertTrue(
            "shortfall ($shortfall) should be under one threshold (${ElevationHysteresis.THRESHOLD_METERS})",
            shortfall < ElevationHysteresis.THRESHOLD_METERS,
        )
        assertEquals(0.0, stats.elevationLossMeters!!, 1e-9)
    }

    @Test
    fun `elevation coverage reports the real fraction of points with altitude, not zero and not null`() {
        // 3 of 5 points report an altitude; the other 2 (including a leading and a trailing gap)
        // don't. Coverage should read a plain 0.6, not collapse into either of elevationGainMeters'
        // two special cases (null for "no data at all," 0.0 for "flat").
        val points = listOf(
            point(lat = 0.0, lng = 0.000, altitude = null, t = 0L),
            point(lat = 0.0, lng = 0.001, altitude = 100.0, t = 1_000L),
            point(lat = 0.0, lng = 0.002, altitude = 108.0, t = 2_000L),
            point(lat = 0.0, lng = 0.003, altitude = 116.0, t = 3_000L),
            point(lat = 0.0, lng = 0.004, altitude = null, t = 4_000L),
        )

        val stats = useCase(points)

        assertEquals(0.6, stats.elevationCoverage!!, 1e-9)
    }

    @Test
    fun `elevation gain and loss are null, not zero, when no point reports an altitude`() {
        val points = listOf(
            point(lat = 0.0, lng = 0.0, altitude = null, t = 0L),
            point(lat = 0.0, lng = 0.001, altitude = null, t = 1_000L),
        )

        val stats = useCase(points)

        assertNull(stats.elevationGainMeters)
        assertNull(stats.elevationLossMeters)
        assertEquals(0.0, stats.elevationCoverage!!, 1e-9)
    }

    @Test
    fun `average speed is null for a zero-duration track`() {
        val points = listOf(point(lat = 0.0, lng = 0.0, altitude = null, t = 5_000L))

        val stats = useCase(points)

        assertNull(stats.averageSpeedMetersPerSecond)
    }

    private fun point(lat: Double, lng: Double, altitude: Double?, t: Long) = TrackPoint(
        lat = lat,
        lng = lng,
        altitude = altitude,
        accuracyMeters = null,
        timestampEpochMillis = t,
    )
}
