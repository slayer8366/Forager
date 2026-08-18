package com.forager.app.domain

import com.forager.app.domain.model.Waypoint
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
    suspend operator fun invoke(lat: Double, lng: Double, altitude: Double?, name: String, note: String = ""): Result<Waypoint> {
        val waypoint = Waypoint(
            id = idGenerator(),
            lat = lat,
            lng = lng,
            altitude = altitude,
            name = name,
            note = note,
            createdAtEpochMillis = currentTime.nowEpochMillis(),
        )
        return repository.save(waypoint).map { waypoint }
    }
}
