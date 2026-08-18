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
