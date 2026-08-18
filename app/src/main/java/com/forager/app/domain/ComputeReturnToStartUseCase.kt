package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.ReturnToStartInfo
import com.forager.app.domain.model.TrackPoint

/** Computes [ReturnToStartInfo] from the current position back to a track's first recorded point. */
class ComputeReturnToStartUseCase {
    operator fun invoke(current: TrackPoint, start: TrackPoint): ReturnToStartInfo {
        val currentLatLng = LatLng(current.lat, current.lng)
        val startLatLng = LatLng(start.lat, start.lng)

        val startAltitude = start.altitude
        val currentAltitude = current.altitude
        val elevationDifferenceMeters =
            if (startAltitude != null && currentAltitude != null) startAltitude - currentAltitude else null

        return ReturnToStartInfo(
            bearingDegrees = GeoDistance.initialBearingDegrees(currentLatLng, startLatLng),
            distanceMeters = GeoDistance.metersBetween(currentLatLng, startLatLng),
            elevationDifferenceMeters = elevationDifferenceMeters,
        )
    }
}
