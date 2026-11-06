package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The track sheet's moving speed (dispatch 2026-09-28-677, Amendment 1, RECORD -680). Literals worked by hand: 0.01° of
 * latitude is 1111.95 m.
 */
class TrackMovingSpeedTest {

    private val t0 = 1_790_000_000_000L
    private val minute = 60_000L

    private fun point(lat: Double, atMinute: Long) =
        TrackPoint(lat = lat, lng = -122.0, altitude = null, accuracyMeters = 5f, timestampEpochMillis = t0 + atMinute * minute)

    /**
     * 1111.95 m in 10 min, an hour stopped in one place, then 1111.95 m in 10 min. Moving: 2223.9 m in 20 min, 1.853 m/s.
     * Over first-to-last time it would be 2223.9 m in 80 min, 0.463 m/s: the stop is what this leaves out.
     */
    @Test
    fun `a long stop does not slow the moving speed`() {
        val walk = listOf(point(45.00, 0), point(45.01, 10), point(45.01, 70), point(45.02, 80))
        assertEquals(1.853, trackMovingSpeedMetersPerSecond(walk)!!, 0.001)
    }

    @Test
    fun `the same walk with no stop reads the same`() {
        val walk = listOf(point(45.00, 0), point(45.01, 10), point(45.02, 20))
        assertEquals(1.853, trackMovingSpeedMetersPerSecond(walk)!!, 0.001)
    }

    /** Under five minutes of moving time the walk-back estimate assumes a pace; a record shows none rather than that one. */
    @Test
    fun `too little moving time is no figure, not the assumed default`() {
        val walk = listOf(point(45.00, 0), point(45.01, 4))
        assertNull(trackMovingSpeedMetersPerSecond(walk))
        assertNull(trackMovingSpeedMetersPerSecond(emptyList()))
    }
}
