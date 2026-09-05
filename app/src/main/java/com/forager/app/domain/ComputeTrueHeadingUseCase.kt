package com.forager.app.domain

import com.forager.app.domain.model.LatLng

/**
 * True-north heading from a magnetic one — HUD-foundations dispatch, Item 2, the one place
 * [CompassProvider.heading] (magnetic) and [GeoDistance.initialBearingDegrees] (true) are made
 * comparable. Both of those doc comments used to say reconciling them was "a UI-layer concern";
 * it now lives here, so the navigation HUD's target needle can subtract a true bearing from a true
 * heading without every composable re-deriving the sign, and so the arithmetic is testable headless.
 *
 * `true = magnetic + declination`, with [DeclinationProvider]'s positive-east convention, normalised
 * to `[0, 360)`. A magnetic heading of 350° at +15° declination is a true heading of 5°, not 365°.
 *
 * ## Recompute cadence
 *
 * Declination changes by roughly a degree per hundred kilometres across the continental US and by
 * about a tenth of a degree per year, so recomputing it on every fix is wasteful and never
 * recomputing it is wrong. This use case keeps the last declination it computed and reuses it
 * until the caller's position has moved more than [RECOMPUTE_DISTANCE_METERS] from where it was
 * computed, or more than [RECOMPUTE_INTERVAL_MILLIS] has passed since — either bound keeps the
 * reused value within about a tenth of a degree of the fresh one. That cache is the *only* state
 * here; one instance per consumer, not shared, so two callers at different places never trade
 * values.
 *
 * ## Consequence recorded for the HUD dispatch
 *
 * The compass strip (`CompassElevationStrip` in `AvailabilityScreen.kt`) is untouched by this
 * dispatch and keeps collecting the raw **magnetic** flow. Once a HUD consumes this use case's
 * **true** heading, the strip and the HUD will disagree by local declination — about 15° in the
 * Pacific Northwest — on the same screen. That is the HUD dispatch's decision to make, and the
 * strip will most likely move to true as well; written down here so it is not forgotten.
 */
class ComputeTrueHeadingUseCase(
    private val declinationProvider: DeclinationProvider,
) {
    private var cached: CachedDeclination? = null

    /**
     * [magneticHeadingDegrees] as [CompassProvider.heading] reports it; the position, altitude and
     * time are the fix the heading is being read at — declination depends on where and when, so a
     * caller with no position has no true heading, and should not call this rather than pass a
     * guess (an explicit "unknown", per CLAUDE.md, never a fabricated plausible value).
     */
    operator fun invoke(
        magneticHeadingDegrees: Float,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double?,
        epochMillis: Long,
    ): Float {
        val declination = declinationAt(latitude, longitude, altitudeMeters, epochMillis)
        return ((magneticHeadingDegrees + declination) % 360f + 360f) % 360f
    }

    private fun declinationAt(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float {
        val here = LatLng(latitude, longitude)
        val existing = cached
        if (existing != null &&
            GeoDistance.metersBetween(existing.position, here) <= RECOMPUTE_DISTANCE_METERS &&
            epochMillis - existing.epochMillis <= RECOMPUTE_INTERVAL_MILLIS
        ) {
            return existing.declinationDegrees
        }
        val fresh = declinationProvider.declinationDegrees(latitude, longitude, altitudeMeters, epochMillis)
        cached = CachedDeclination(position = here, epochMillis = epochMillis, declinationDegrees = fresh)
        return fresh
    }

    private data class CachedDeclination(
        val position: LatLng,
        val epochMillis: Long,
        val declinationDegrees: Float,
    )

    companion object {
        /** See the class doc comment's "Recompute cadence" — about a tenth of a degree of drift across this distance. */
        const val RECOMPUTE_DISTANCE_METERS = 10_000.0

        /** See the class doc comment's "Recompute cadence" — declination moves about a tenth of a degree per year. */
        const val RECOMPUTE_INTERVAL_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
