package com.forager.app.domain

import com.forager.app.domain.model.Waypoint
import com.forager.app.domain.model.WaypointDesignation
import java.util.UUID

/**
 * Creates and persists a new waypoint. [currentTime] and [idGenerator] are injected for the same
 * reason as [StartTrackUseCase]: a test can fix both instead of racing the clock or asserting
 * against a random id.
 */
class CreateWaypointUseCase(
    private val repository: WaypointRepository,
    private val currentTime: CurrentTimeProvider = SystemCurrentTimeProvider,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    /**
     * [trackId] and [designation] are the navigation HUD's auto-created origin/end waypoints (see
     * [WaypointDesignation]); both default to `null`, which is an ordinary user-dropped waypoint,
     * so every existing caller is unchanged.
     */
    suspend operator fun invoke(
        lat: Double,
        lng: Double,
        altitude: Double?,
        name: String,
        note: String = "",
        trackId: String? = null,
        designation: WaypointDesignation? = null,
    ): Result<Waypoint> {
        val waypoint = Waypoint(
            id = idGenerator(),
            lat = lat,
            lng = lng,
            altitude = altitude,
            name = name,
            note = note,
            createdAtEpochMillis = currentTime.nowEpochMillis(),
            trackId = trackId,
            designation = designation,
        )
        return repository.save(waypoint).map { waypoint }
    }
}
