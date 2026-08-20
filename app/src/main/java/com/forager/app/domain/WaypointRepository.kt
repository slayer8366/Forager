package com.forager.app.domain

import com.forager.app.domain.model.Waypoint

/**
 * Owned abstraction over waypoint persistence — the same pattern as [MushroomLogRepository]. Domain
 * and UI code depend on this interface, never on Room directly. The Room-backed implementation
 * lives in `data/repository/`.
 */
interface WaypointRepository {
    /** Every waypoint currently stored, in no particular order — ordering is a use-case concern. */
    suspend fun getAll(): Result<List<Waypoint>>

    /** Inserts [waypoint], or replaces the stored one with the same id if one already exists. */
    suspend fun save(waypoint: Waypoint): Result<Unit>

    /** Removes the waypoint with this id. A no-op, not a failure, if none exists. */
    suspend fun delete(id: String): Result<Unit>
}
