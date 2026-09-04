package com.forager.app.domain

/**
 * The counts behind the 4b deletion warning ("This track appears in 2 journal entries") — one class
 * rather than one per type, since each is the same one-line read against [CartographyEntryRepository]
 * and a caller only ever needs the one matching what it's about to delete. Track/waypoint/offline-
 * region were the dispatch's own original 4b list; [forPhoto] extends it — a wordless entry can
 * consist mostly of attached photos, so deleting one deserves the same warning. Finds are the one
 * kept-item type still not covered — see [com.forager.app.data.local.CartographyEntryFindRefEntity]'s
 * doc comment for why.
 */
class GetEntryReferenceCountUseCase(
    private val repository: CartographyEntryRepository,
) {
    suspend fun forTrack(trackId: String): Result<Int> = repository.countEntriesReferencingTrack(trackId)

    suspend fun forWaypoint(waypointId: String): Result<Int> = repository.countEntriesReferencingWaypoint(waypointId)

    suspend fun forOfflineRegion(offlineRegionId: Long): Result<Int> =
        repository.countEntriesReferencingOfflineRegion(offlineRegionId)

    suspend fun forPhoto(photoId: String): Result<Int> = repository.countEntriesReferencingPhoto(photoId)
}
