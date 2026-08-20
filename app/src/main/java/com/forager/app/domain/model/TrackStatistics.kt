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
) {
    /** `null` when [durationMillis] is zero — a single-point or zero-duration track has no rate. */
    val averageSpeedMetersPerSecond: Double? =
        if (durationMillis <= 0L) null else distanceMeters / (durationMillis / 1000.0)
}
