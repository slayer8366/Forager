package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.CartographyEntry

/**
 * In-memory [CartographyEntryRepository] for the use-case tests that need only "what was handed to
 * `save`" and "what `getAll` returns" — [SaveCartographyEntryUseCaseTest] and
 * [GetCartographyEntriesUseCaseTest]. Not a substitute for the real Room repository where a test's
 * claim is about persistence (those go through `RoomCartographyEntryRepository` in
 * `CartographyViewModelTest`); this exists so a pure ordering or stamping claim can be asserted
 * without a database and without Robolectric.
 */
internal class RecordingCartographyEntryRepository(
    initial: List<CartographyEntry> = emptyList(),
) : CartographyEntryRepository {
    val saved = mutableListOf<CartographyEntry>()
    private val stored = initial.associateBy { it.id }.toMutableMap()

    override suspend fun getAll(): Result<List<CartographyEntry>> = Result.success(stored.values.filterNot { it.isDraft })

    override suspend fun getAllDrafts(): Result<List<CartographyEntry>> = Result.success(stored.values.filter { it.isDraft })

    override suspend fun getById(id: String): Result<CartographyEntry?> = Result.success(stored[id])

    override suspend fun save(entry: CartographyEntry): Result<Unit> {
        saved += entry
        stored[entry.id] = entry
        return Result.success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        stored.remove(id)
        return Result.success(Unit)
    }

    override suspend fun countEntriesReferencingTrack(trackId: String): Result<Int> = error("not used by these tests")

    override suspend fun countEntriesReferencingWaypoint(waypointId: String): Result<Int> = error("not used by these tests")

    override suspend fun countEntriesReferencingOfflineRegion(offlineRegionId: Long): Result<Int> = error("not used by these tests")

    override suspend fun countEntriesReferencingPhoto(photoId: String): Result<Int> = error("not used by these tests")
}
