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
     *
     * **Because it completes, a collector that started before the permission was granted is
     * finished, not waiting.** Permission is checked at each collection start and never again, so
     * a caller that wants fixes after a grant must start a *new* collection once the grant arrives
     * — nothing here re-registers on its own. `AvailabilityViewModel.onLocationPermissionGranted`
     * is that restart for the compass strip's collector; the first-launch dispatch found that
     * collector starting at construction, completing on the not-yet-granted permission, and never
     * collecting again until the app was restarted. This sentence exists so the next collector
     * written against this interface doesn't repeat that.
     */
    val fixes: Flow<LocationFix>
}

sealed interface LocationFix {
    /**
     * [altitude] and [accuracyMeters] are `null` whenever the underlying fix didn't report them,
     * same rule as [com.forager.app.domain.LocationResult.Success.altitude].
     *
     * [speedMetersPerSecond] and [speedAccuracyMetersPerSecond] (return-estimate dispatch, Item 3)
     * follow the same rule against the platform's `hasSpeed()` / `hasSpeedAccuracy()`: `null` is
     * "not reported", never a zero standing in for it. Defaults so no existing constructor site
     * changes; the real tracker fills both. See [com.forager.app.domain.model.TrackPoint] for what
     * the persisted copy means and who reads it.
     */
    data class Update(
        val lat: Double,
        val lng: Double,
        val altitude: Double?,
        val accuracyMeters: Float?,
        val timestampEpochMillis: Long,
        val speedMetersPerSecond: Float? = null,
        val speedAccuracyMetersPerSecond: Float? = null,
    ) : LocationFix

    data object PermissionDenied : LocationFix
}

/**
 * The fix as a candidate [com.forager.app.domain.model.TrackPoint] for [LocationSampler] — field
 * for field, nothing computed, nothing dropped. Pulled out of `TrackRecordingService` (return-
 * estimate dispatch, Item 3) so that the one place a fix becomes a point can be asserted on
 * without a Robolectric recording: the speed columns exist to be read by the pace, and a mapping
 * that silently dropped them would leave every new track looking like a pre-migration one.
 */
fun LocationFix.Update.toTrackPoint() = com.forager.app.domain.model.TrackPoint(
    lat = lat,
    lng = lng,
    altitude = altitude,
    accuracyMeters = accuracyMeters,
    timestampEpochMillis = timestampEpochMillis,
    speedMetersPerSecond = speedMetersPerSecond,
    speedAccuracyMetersPerSecond = speedAccuracyMetersPerSecond,
)

/**
 * How old this fix is at [nowEpochMillis], in milliseconds — HUD-foundations dispatch, Item 1.
 *
 * A pure function of the fix and a clock the caller supplies, never a stored field: a stored age
 * is stale the moment it is written, while this stays correct for as long as the fix is held
 * (`AvailabilityUiState.liveFix` holds the last fix indefinitely once fixes stop — canopy, a tunnel,
 * the radio off — and this is how a consumer tells a twenty-minute-old fix from a fresh one).
 * Deliberately **no interpretation**: no "stale" threshold, no "GPS lost" state. Whether an age is
 * acceptable is the consumer's policy — the navigation HUD's, when it exists — not this function's.
 *
 * Not clamped at zero. A negative result means [nowEpochMillis] is earlier than the fix's own
 * timestamp — clock skew between the platform's fix time and the caller's clock — and returning the
 * raw difference reports that honestly rather than fabricating a plausible zero (CLAUDE.md, "an
 * unsupported feature or capability returns an explicit unsupported, never a fabricated plausible
 * value").
 */
fun LocationFix.Update.ageMillis(nowEpochMillis: Long): Long = nowEpochMillis - timestampEpochMillis
