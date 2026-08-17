package com.forager.app.domain

import com.forager.app.domain.model.PlannedTrip

/**
 * Owned abstraction over planned-trip persistence. Domain and UI code depend on this interface,
 * never on Room or any storage API directly (CLAUDE.md: wrap external integrations — including
 * local persistence, not only network calls — behind an interface this project owns), the same
 * pattern as [MushroomRepository] and [WeatherProvider]. The Room-backed implementation lives in
 * `data/repository/`; unit tests get a hand-written in-memory fake implementing this interface.
 */
interface PlannedTripRepository {
    /** Every planned trip currently stored, in no particular order — ordering is a use-case concern. */
    suspend fun getAll(): Result<List<PlannedTrip>>

    /** Inserts [trip], or replaces the stored trip with the same id if one already exists. */
    suspend fun save(trip: PlannedTrip): Result<Unit>

    /** Removes the trip with this id. A no-op, not a failure, if no such trip is stored. */
    suspend fun delete(id: String): Result<Unit>
}
