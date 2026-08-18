package com.forager.app.domain.model

/**
 * One recorded fix along a [Track].
 *
 * [altitude] and [accuracyMeters] are `null` whenever the underlying fix didn't report them —
 * genuinely "not provided", never defaulted to a guessed value, the same rule
 * [com.forager.app.domain.LocationResult.Success.altitude] already follows for a one-shot fix.
 * [accuracyMeters] is kept on the persisted point (not discarded once a sampling decision is made)
 * so statistics or a later re-filter can still see what the device actually reported.
 */
data class TrackPoint(
    val lat: Double,
    val lng: Double,
    val altitude: Double?,
    val accuracyMeters: Float?,
    val timestampEpochMillis: Long,
)
