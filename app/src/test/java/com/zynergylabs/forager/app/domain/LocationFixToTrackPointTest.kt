package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one place a fix becomes a candidate point (return-estimate dispatch, Item 3): field for
 * field, the speed columns included — a mapping that dropped them would make every new track
 * read as a pre-migration one and the pace would never see a Doppler speed. Both directions of
 * the `null` rule: reported values travel, unreported ones stay `null` rather than becoming zero.
 */
class LocationFixToTrackPointTest {

    @Test
    fun `a fix with speed and speed accuracy becomes a point carrying both`() {
        val fix = LocationFix.Update(lat = 45.5, lng = -122.6, altitude = 312.0, accuracyMeters = 3.79f, timestampEpochMillis = 1_788_801_910_000L, speedMetersPerSecond = 0.96f, speedAccuracyMetersPerSecond = 0.6945308f)

        assertEquals(
            TrackPoint(lat = 45.5, lng = -122.6, altitude = 312.0, accuracyMeters = 3.79f, timestampEpochMillis = 1_788_801_910_000L, speedMetersPerSecond = 0.96f, speedAccuracyMetersPerSecond = 0.6945308f),
            fix.toTrackPoint(),
        )
    }

    @Test
    fun `a fix that reported no speed becomes a point with null speed, never zero`() {
        val fix = LocationFix.Update(lat = 45.5, lng = -122.6, altitude = null, accuracyMeters = 17.208f, timestampEpochMillis = 1_788_801_910_314L, speedMetersPerSecond = null, speedAccuracyMetersPerSecond = null)

        assertEquals(
            TrackPoint(lat = 45.5, lng = -122.6, altitude = null, accuracyMeters = 17.208f, timestampEpochMillis = 1_788_801_910_314L, speedMetersPerSecond = null, speedAccuracyMetersPerSecond = null),
            fix.toTrackPoint(),
        )
    }
}
