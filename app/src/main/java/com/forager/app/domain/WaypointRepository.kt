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

    /**
     * Every waypoint dropped on one local day — Journal Stage 2a's derived-trip read. Half-open
     * range `[dayStartInclusiveEpochMillis, dayEndExclusiveEpochMillis)` — see
     * [com.forager.app.domain.LocalDayRange]'s own doc comment for why.
     */
    suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Waypoint>>

    /** The waypoint with this id, or `null` if none — how [GetTrackOriginWaypointUseCase] follows a track's origin pointer. */
    suspend fun getById(id: String): Result<Waypoint?>

    /**
     * Every waypoint dropped while the track with id [trackId] was recording, oldest first — the
     * read path for [com.forager.app.domain.model.Waypoint.trackId] (HUD-foundations dispatch,
     * Item 3). Empty, not a failure, for a track with none or an id that no longer exists.
     */
    suspend fun getForTrack(trackId: String): Result<List<Waypoint>>

    /**
     * Nulls [com.forager.app.domain.model.Waypoint.trackId] on every waypoint linked to [trackId],
     * leaving the waypoints themselves in place — run by [DeleteTrackUseCase] ahead of the track's
     * own deletion. A no-op, not a failure, if nothing was linked.
     */
    suspend fun detachFromTrack(trackId: String): Result<Unit>

    /** Inserts [waypoint], or replaces the stored one with the same id if one already exists. */
    suspend fun save(waypoint: Waypoint): Result<Unit>

    /** Removes the waypoint with this id. A no-op, not a failure, if none exists. */
    suspend fun delete(id: String): Result<Unit>
}
