package com.forager.app.domain.model

/**
 * How aggressively a track recording samples location while it runs. A recorded track can span
 * hours, so how often the GPS is polled is a direct battery-life trade-off, not just a data-density
 * one.
 *
 * [minIntervalMillis] and [minDistanceMeters] are the two independent throttles
 * [com.forager.app.domain.LocationSampler] applies — a point is only accepted once *both*
 * thresholds have been met since the last accepted point, not either alone, so a stationary period
 * doesn't keep writing a point every few seconds just because the interval elapsed with no real
 * movement.
 * [maxAcceptableAccuracyMeters] is a separate throttle: a fix reporting worse accuracy than this is
 * rejected outright regardless of timing, since a low-accuracy fix is misleading track geometry,
 * not just a denser one.
 *
 * These numbers are labelled adjustable assumptions, not measured facts — chosen for a plausible
 * multi-hour foraging walk and not yet checked against a real recorded track's battery draw (see
 * `maplibre-migration.md`'s risk list on vector-map battery draw for the adjacent, still-open
 * question this doesn't answer).
 */
enum class TrackRecordingMode(
    val minIntervalMillis: Long,
    val minDistanceMeters: Float,
    val maxAcceptableAccuracyMeters: Float,
) {
    HIGH_ACCURACY(minIntervalMillis = 5_000L, minDistanceMeters = 5f, maxAcceptableAccuracyMeters = 30f),
    BALANCED(minIntervalMillis = 15_000L, minDistanceMeters = 15f, maxAcceptableAccuracyMeters = 50f),
    BATTERY_SAVER(minIntervalMillis = 60_000L, minDistanceMeters = 30f, maxAcceptableAccuracyMeters = 100f),
}
