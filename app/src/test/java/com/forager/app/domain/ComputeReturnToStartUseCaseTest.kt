package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComputeReturnToStartUseCaseTest {

    private val useCase = ComputeReturnToStartUseCase()

    @Test
    fun `bearing and distance point back toward the start`() {
        val start = point(lat = 45.0, lng = -122.0, altitude = null)
        val current = point(lat = 46.0, lng = -122.0, altitude = null) // one degree north of start

        val info = useCase(current = current, start = start)

        assertEquals(180.0, info.bearingDegrees, 0.5) // start is due south of current
        assertEquals(GeoDistance.metersBetween(LatLng(46.0, -122.0), LatLng(45.0, -122.0)), info.distanceMeters, 1.0)
    }

    @Test
    fun `elevation difference is positive when the start point is higher`() {
        val start = point(lat = 45.0, lng = -122.0, altitude = 500.0)
        val current = point(lat = 45.01, lng = -122.0, altitude = 300.0)

        val info = useCase(current = current, start = start)

        assertEquals(200.0, info.elevationDifferenceMeters!!, 1e-9)
    }

    @Test
    fun `elevation difference is null when either point lacks an altitude`() {
        val start = point(lat = 45.0, lng = -122.0, altitude = null)
        val current = point(lat = 45.01, lng = -122.0, altitude = 300.0)

        val info = useCase(current = current, start = start)

        assertNull(info.elevationDifferenceMeters)
    }

    private fun point(lat: Double, lng: Double, altitude: Double?) = TrackPoint(
        lat = lat,
        lng = lng,
        altitude = altitude,
        accuracyMeters = null,
        timestampEpochMillis = 0L,
    )
}
