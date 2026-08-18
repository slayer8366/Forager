package com.forager.app.domain

import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.TrackRecordingMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSamplerTest {

    private val sampler = LocationSampler(TrackRecordingMode.BALANCED)

    @Test
    fun `the first point is always accepted once it clears the accuracy check`() {
        val first = point(lat = 45.0, lng = -122.0, accuracyMeters = 10f, timestampEpochMillis = 0L)

        assertTrue(sampler.shouldAccept(lastAccepted = null, candidate = first))
    }

    @Test
    fun `a point worse than the mode's accuracy threshold is rejected even as the first point`() {
        val badFix = point(lat = 45.0, lng = -122.0, accuracyMeters = 200f, timestampEpochMillis = 0L)

        assertFalse(sampler.shouldAccept(lastAccepted = null, candidate = badFix))
    }

    @Test
    fun `a point before the minimum interval has elapsed is rejected even if far enough away`() {
        val last = point(lat = 45.0, lng = -122.0, accuracyMeters = 10f, timestampEpochMillis = 0L)
        // Far enough (BALANCED needs 15m) but only 1 second after the last accepted point (BALANCED needs 15s).
        val tooSoon = point(lat = 45.001, lng = -122.0, accuracyMeters = 10f, timestampEpochMillis = 1_000L)

        assertFalse(sampler.shouldAccept(last, tooSoon))
    }

    @Test
    fun `a point after the interval but too close is rejected`() {
        val last = point(lat = 45.0, lng = -122.0, accuracyMeters = 10f, timestampEpochMillis = 0L)
        // Interval satisfied (20s > 15s) but essentially no movement.
        val tooClose = point(lat = 45.0, lng = -122.0, accuracyMeters = 10f, timestampEpochMillis = 20_000L)

        assertFalse(sampler.shouldAccept(last, tooClose))
    }

    @Test
    fun `a point satisfying both interval and distance is accepted`() {
        val last = point(lat = 45.0, lng = -122.0, accuracyMeters = 10f, timestampEpochMillis = 0L)
        val farEnoughAndLateEnough = point(lat = 45.001, lng = -122.0, accuracyMeters = 10f, timestampEpochMillis = 20_000L)

        assertTrue(sampler.shouldAccept(last, farEnoughAndLateEnough))
    }

    @Test
    fun `a point with no reported accuracy is never rejected on accuracy grounds`() {
        val noAccuracyFix = point(lat = 45.0, lng = -122.0, accuracyMeters = null, timestampEpochMillis = 0L)

        assertTrue(sampler.shouldAccept(lastAccepted = null, candidate = noAccuracyFix))
    }

    private fun point(lat: Double, lng: Double, accuracyMeters: Float?, timestampEpochMillis: Long) = TrackPoint(
        lat = lat,
        lng = lng,
        altitude = null,
        accuracyMeters = accuracyMeters,
        timestampEpochMillis = timestampEpochMillis,
    )
}
