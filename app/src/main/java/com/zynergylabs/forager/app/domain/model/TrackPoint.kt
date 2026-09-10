package com.zynergylabs.forager.app.domain.model

/**
 * One recorded fix along a [Track].
 *
 * [altitude] and [accuracyMeters] are `null` whenever the underlying fix didn't report them —
 * genuinely "not provided", never defaulted to a guessed value, the same rule
 * [com.zynergylabs.forager.app.domain.LocationResult.Success.altitude] already follows for a one-shot fix.
 * [accuracyMeters] is kept on the persisted point (not discarded once a sampling decision is made)
 * so statistics or a later re-filter can still see what the device actually reported.
 *
 * [speedMetersPerSecond] and [speedAccuracyMetersPerSecond] — return-estimate dispatch, Item 3
 * (owner-authorised after the instrument walk): the platform's Doppler-derived speed over ground
 * and its reported accuracy, as `Location.getSpeed()` / `getSpeedAccuracyMetersPerSecond()`
 * delivered them, carried only when the platform's own `hasSpeed()` / `hasSpeedAccuracy()` said the
 * value was meaningful. **`null` is a real state, not a convenience**: every point recorded before
 * schema version 15 has no value, every network-provider fix reports none (55 of 55 on the walk),
 * and a GPX import has none — so anything that reads either field handles `null` explicitly and
 * says how many rows it skipped (the dispatch's generalised parsing hazard; CLAUDE.md, Testing,
 * "a check that passes because it never saw the data that could fail it"). The reader is
 * [com.zynergylabs.forager.app.domain.movingPace]; the point-differencing path there is what a `null`-speed
 * track gets, and it is a first-class path, not a fallback. Doppler speed is derived from carrier
 * frequency shift, not from differencing positions, which is why it is worth a column: it does
 * not inherit position error. Both default to `null` so no existing constructor site changes.
 */
data class TrackPoint(
    val lat: Double,
    val lng: Double,
    val altitude: Double?,
    val accuracyMeters: Float?,
    val timestampEpochMillis: Long,
    val speedMetersPerSecond: Float? = null,
    val speedAccuracyMetersPerSecond: Float? = null,
)
