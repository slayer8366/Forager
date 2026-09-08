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
     *
     * The accuracy rejection never fires on a GPS fix from the owner's device: that phone reports
     * a constant `3.7900925` m on every GPS fix (instrument walk of 2026-09-07,
     * `docs/audits/2026-09-07-fix-log-walk-findings.md`), below every mode's ceiling. It still
     * rejects that device's network fixes, whose accuracy genuinely varies to 70 m and more. One
     * device so far; see [LIVE_FIX_MAX_ACCURACY_METERS]'s doc for the other readers of the field.
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
