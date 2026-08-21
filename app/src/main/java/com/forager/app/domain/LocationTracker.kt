package com.forager.app.domain

import kotlinx.coroutines.flow.Flow

/**
 * Owned abstraction over continuous device location, for track recording. Distinct from
 * [LocationProvider] on purpose: that interface answers "where am I right now", once; this one is
 * a running stream a foreground service collects for as long as a track is being recorded, and it
 * needs to carry accuracy and a timestamp per fix — which [LocationResult] has no reason to.
 */
interface LocationTracker {
    /**
     * Raw fixes as the platform reports them — unfiltered and unsampled.
     * [com.forager.app.domain.LocationSampler] decides which of these become persisted
     * [com.forager.app.domain.model.TrackPoint]s; this stream's job is only to report what the
     * device actually saw.
     *
     * Emits [LocationFix.PermissionDenied] once and completes if location permission isn't
     * granted when collection starts — the same "explicit unsupported, not a silent empty stream"
     * rule [CompassProvider.heading] already follows for a missing sensor.
     */
    val fixes: Flow<LocationFix>
}

sealed interface LocationFix {
    /**
     * [altitude] and [accuracyMeters] are `null` whenever the underlying fix didn't report them,
     * same rule as [com.forager.app.domain.LocationResult.Success.altitude].
     */
    data class Update(
        val lat: Double,
        val lng: Double,
        val altitude: Double?,
        val accuracyMeters: Float?,
        val timestampEpochMillis: Long,
    ) : LocationFix

    data object PermissionDenied : LocationFix
}
