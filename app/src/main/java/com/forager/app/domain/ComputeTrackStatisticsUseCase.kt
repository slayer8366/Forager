package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.TrackStatistics
import kotlin.math.abs

/**
 * Derives [TrackStatistics] from a track's recorded points. Pure domain, no dependency on how the
 * points were persisted or recorded.
 *
 * Distance sums [GeoDistance.metersBetween] over consecutive points rather than any straight-line
 * shortcut — the whole reason a track is worth recording is that it is not a straight line.
 * Elevation gain/loss only considers consecutive pairs where *both* points reported an altitude;
 * a gap in altitude reporting resets the running elevation reference rather than being bridged, so
 * a real change is never invented from two readings that aren't actually adjacent — a skipped delta
 * and a genuinely flat one are different facts.
 *
 * Ascent/descent is hysteresis-filtered, not a raw delta sum: consumer GPS altitude noise runs
 * roughly ±10-15 m, so a phone sitting still for an hour would otherwise accumulate hundreds of
 * metres of fictional climb (see [ElevationHysteresis]). A delta only banks into
 * [TrackStatistics.elevationGainMeters]/[elevationLossMeters] once the *cumulative* change since the
 * last banked point (or the start of the current unbroken run of altitude readings, if nothing has
 * banked yet) exceeds [ElevationHysteresis.THRESHOLD_METERS] — until then the running reference
 * stays put, so small back-and-forth jitter around one spot never banks at all, while a sustained
 * climb or descent still accumulates and is eventually recovered once it crosses the threshold.
 * [ComputeTrackStatisticsUseCaseTest] covers both a purely stationary noisy track (asserts ~0) and a
 * staircase with a known total climb spread across many sub-threshold steps (asserts the true total
 * is recovered within one threshold's worth of tolerance — the unavoidable unbanked remainder is
 * whatever partial climb hadn't yet crossed the threshold when the track ended, and stays under one
 * threshold regardless of how long the staircase is — banking resets the reference every time it
 * fires, so the unbanked tail never compounds with track length).
 *
 * [TrackStatistics.elevationCoverage] carries what a bare gain/loss number can't: a track missing
 * altitude on part of its points reports a real, lower gain — the same conservative under-report a
 * gap already gets, not a fabricated bridge across the missing readings (see the gap-reset note
 * above) — and coverage is what turns that into a reportable fact instead of a silently-too-low one.
 */
class ComputeTrackStatisticsUseCase {
    operator fun invoke(points: List<TrackPoint>): TrackStatistics {
        if (points.isEmpty()) {
            return TrackStatistics(
                distanceMeters = 0.0,
                durationMillis = 0L,
                elevationGainMeters = null,
                elevationLossMeters = null,
                pointsWithAltitude = 0,
                totalPoints = 0,
            )
        }

        var distanceMeters = 0.0
        var gainMeters = 0.0
        var lossMeters = 0.0
        var sawElevationDelta = false

        // The altitude a bank is measured from — null whenever the current run of consecutive,
        // both-altitude-present pairs hasn't banked anything yet, in which case the *previous*
        // point's altitude is used as the reference (see below). Reset to null on any gap so a
        // change spanning missing data is never compared as if it were adjacent.
        var referenceAltitude: Double? = null

        for (i in 1 until points.size) {
            val previous = points[i - 1]
            val current = points[i]
            distanceMeters += GeoDistance.metersBetween(
                LatLng(previous.lat, previous.lng),
                LatLng(current.lat, current.lng),
            )

            val previousAltitude = previous.altitude
            val currentAltitude = current.altitude
            if (previousAltitude == null || currentAltitude == null) {
                referenceAltitude = null
                continue
            }

            sawElevationDelta = true
            val reference = referenceAltitude ?: previousAltitude
            val delta = currentAltitude - reference
            if (abs(delta) >= ElevationHysteresis.THRESHOLD_METERS) {
                if (delta > 0) gainMeters += delta else lossMeters += -delta
                referenceAltitude = currentAltitude
            } else {
                referenceAltitude = reference
            }
        }

        val durationMillis = (points.last().timestampEpochMillis - points.first().timestampEpochMillis)
            .coerceAtLeast(0L)

        return TrackStatistics(
            distanceMeters = distanceMeters,
            durationMillis = durationMillis,
            elevationGainMeters = if (sawElevationDelta) gainMeters else null,
            elevationLossMeters = if (sawElevationDelta) lossMeters else null,
            pointsWithAltitude = points.count { it.altitude != null },
            totalPoints = points.size,
        )
    }
}

/**
 * 4 m, the midpoint of the 3-5 m range typical for filtering consumer GPS altitude noise (which
 * itself runs roughly ±10-15 m per reading) without also filtering out a real, if gentle, sustained
 * climb. An adjustable assumption in the same spirit as [ClusterForagingAreasUseCase]'s clustering
 * thresholds — not a data-derived constant, since this project has no field data yet on real
 * device altitude-noise characteristics to derive one from.
 */
object ElevationHysteresis {
    const val THRESHOLD_METERS = 4.0
}
