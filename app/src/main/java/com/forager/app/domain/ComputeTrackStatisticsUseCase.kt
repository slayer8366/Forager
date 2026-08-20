package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.TrackStatistics

/**
 * Derives [TrackStatistics] from a track's recorded points. Pure domain, no dependency on how the
 * points were persisted or recorded.
 *
 * Distance sums [GeoDistance.metersBetween] over consecutive points rather than any straight-line
 * shortcut — the whole reason a track is worth recording is that it is not a straight line.
 * Elevation gain/loss only considers consecutive pairs where *both* points reported an altitude;
 * a gap in altitude reporting is skipped rather than treated as a zero-elevation change, since a
 * skipped delta and a genuinely flat one are different facts.
 */
class ComputeTrackStatisticsUseCase {
    operator fun invoke(points: List<TrackPoint>): TrackStatistics {
        if (points.isEmpty()) {
            return TrackStatistics(distanceMeters = 0.0, durationMillis = 0L, elevationGainMeters = null, elevationLossMeters = null)
        }

        var distanceMeters = 0.0
        var gainMeters = 0.0
        var lossMeters = 0.0
        var sawElevationDelta = false

        for (i in 1 until points.size) {
            val previous = points[i - 1]
            val current = points[i]
            distanceMeters += GeoDistance.metersBetween(
                LatLng(previous.lat, previous.lng),
                LatLng(current.lat, current.lng),
            )

            val previousAltitude = previous.altitude
            val currentAltitude = current.altitude
            if (previousAltitude != null && currentAltitude != null) {
                sawElevationDelta = true
                val delta = currentAltitude - previousAltitude
                if (delta > 0) gainMeters += delta else lossMeters += -delta
            }
        }

        val durationMillis = (points.last().timestampEpochMillis - points.first().timestampEpochMillis)
            .coerceAtLeast(0L)

        return TrackStatistics(
            distanceMeters = distanceMeters,
            durationMillis = durationMillis,
            elevationGainMeters = if (sawElevationDelta) gainMeters else null,
            elevationLossMeters = if (sawElevationDelta) lossMeters else null,
        )
    }
}
