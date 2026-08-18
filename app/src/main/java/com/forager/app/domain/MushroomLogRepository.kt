package com.forager.app.domain

import com.forager.app.domain.model.MushroomLogEntry

/**
 * Owned abstraction over mushroom-log persistence — the same pattern as [PlannedTripRepository].
 * Domain and UI code depend on this interface, never on Room directly. The Room-backed
 * implementation lives in `data/repository/`.
 */
interface MushroomLogRepository {
    /** Every log entry currently stored, in no particular order — ordering is a use-case concern. */
    suspend fun getAll(): Result<List<MushroomLogEntry>>

    /** Inserts [entry], or replaces the stored entry with the same id if one already exists — how both creation and the deferred-observation edit flow are saved. */
    suspend fun save(entry: MushroomLogEntry): Result<Unit>

    /** Removes the entry with this id. A no-op, not a failure, if no such entry is stored. */
    suspend fun delete(id: String): Result<Unit>
}
