package com.forager.app.domain.model

/**
 * Derived summary of a [Track]'s recorded points. Always computed on demand from [Track.points]
 * (see `ComputeTrackStatisticsUseCase`), never stored redundantly alongside the track — the same
 * reason `AvailabilityForecast` is computed rather than cached: a stored copy could drift from the
 * points it was supposedly summarizing.
 *
 * [elevationGainMeters]/[elevationLossMeters] are `null` when no recorded point on the track has an
 * altitude, an explicit "unsupported for this track" rather than reporting a fabricated `0.0` that
 * would be indistinguishable from "recorded, and flat".
 */
data class TrackStatistics(
    val distanceMeters: Double,
    val durationMillis: Long,
    val elevationGainMeters: Double?,
    val elevationLossMeters: Double?,
    val pointsWithAltitude: Int,
    val totalPoints: Int,
) {
    /** `null` when [durationMillis] is zero — a single-point or zero-duration track has no rate. */
    val averageSpeedMetersPerSecond: Double? =
        if (durationMillis <= 0L) null else distanceMeters / (durationMillis / 1000.0)

    /**
     * What fraction of the track's points actually reported an altitude — the fact
     * [elevationGainMeters]/[elevationLossMeters] can't carry on their own. A hysteresis-filtered
     * 0.0 gain from a clean, fully-altitude-reporting track and a 0.0 from a track that's 40%
     * missing altitude data look identical without this: the second is a confidently-too-low
     * number, not a genuinely flat one. `null` only for a track with no points at all, matching
     * [elevationGainMeters]'s own empty-track case; a track with points but zero altitude readings
     * is a real, reportable `0.0`, not another null — see the class doc comment on why `0.0` gain
     * itself needs that same distinction from "no data."
     */
    val elevationCoverage: Double? =
        if (totalPoints == 0) null else pointsWithAltitude.toDouble() / totalPoints
}
