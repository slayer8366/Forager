package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.TrackRecordingMode

/**
 * Decides whether a raw location fix becomes a persisted [TrackPoint], independent of how the fix
 * was obtained. Pure domain so the sampling policy is testable without a real device or a running
 * foreground service.
 */
class LocationSampler(private val mode: TrackRecordingMode) {

    /**
     * `true` if [candidate] should be persisted. A fix worse than
     * [TrackRecordingMode.maxAcceptableAccuracyMeters] is rejected outright, regardless of timing —
     * a low-accuracy fix would distort the track's geometry, not just its density. Otherwise
     * [lastAccepted] (`null` for the very first fix, which is always accepted once it clears the
     * accuracy check) must be both [TrackRecordingMode.minIntervalMillis] old and
     * [TrackRecordingMode.minDistanceMeters] away — both thresholds, not either, so a stationary
     * period doesn't keep writing points once the interval elapses with no real movement.
     */
    fun shouldAccept(lastAccepted: TrackPoint?, candidate: TrackPoint): Boolean {
        val accuracy = candidate.accuracyMeters
        if (accuracy != null && accuracy > mode.maxAcceptableAccuracyMeters) return false
        if (lastAccepted == null) return true

        val elapsedMillis = candidate.timestampEpochMillis - lastAccepted.timestampEpochMillis
        if (elapsedMillis < mode.minIntervalMillis) return false

        val distanceMeters = GeoDistance.metersBetween(
            LatLng(lastAccepted.lat, lastAccepted.lng),
            LatLng(candidate.lat, candidate.lng),
        )
        return distanceMeters >= mode.minDistanceMeters
    }
}
