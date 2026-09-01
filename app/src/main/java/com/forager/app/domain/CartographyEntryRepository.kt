package com.forager.app.domain

import com.forager.app.domain.model.CartographyEntry

/**
 * Owned abstraction over Cartography entry persistence — the same pattern as [MushroomLogRepository],
 * a separate table family entirely (see [CartographyEntry]'s own doc comment for why this is not an
 * extension of [MushroomLogRepository]). The Room-backed implementation lives in `data/repository/`.
 */
interface CartographyEntryRepository {
    /** Every committed entry ([CartographyEntry.isDraft] `false`), in no particular order — ordering is a use-case concern. */
    suspend fun getAll(): Result<List<CartographyEntry>>

    /** Every draft entry ([CartographyEntry.isDraft] `true`) — the complement of committed entries, same shape as [MushroomLogRepository.getAll]'s draft/committed split. */
    suspend fun getAllDrafts(): Result<List<CartographyEntry>>

    /** One entry by id, or `null` if none exists. */
    suspend fun getById(id: String): Result<CartographyEntry?>

    /** Inserts [entry], or replaces the stored entry with the same id if one already exists — including its kept-item ref rows, replaced wholesale (never merged) since an entry's own edit screen always writes its complete current kept-item set. */
    suspend fun save(entry: CartographyEntry): Result<Unit>

    /** Removes the entry with this id and its own kept-item ref rows. A no-op, not a failure, if no such entry is stored. */
    suspend fun delete(id: String): Result<Unit>

    /** How many entries currently keep a reference to track [trackId] — the count behind Records' 4b deletion warning. */
    suspend fun countEntriesReferencingTrack(trackId: String): Result<Int>

    /** How many entries currently keep a reference to waypoint [waypointId] — the count behind Records' 4b deletion warning. */
    suspend fun countEntriesReferencingWaypoint(waypointId: String): Result<Int>

    /** How many entries currently keep a reference to offline region [offlineRegionId] — the count behind Records' 4b deletion warning. */
    suspend fun countEntriesReferencingOfflineRegion(offlineRegionId: Long): Result<Int>
}
