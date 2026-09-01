package com.forager.app.domain

/**
 * The three counts behind Records' 4b deletion warning ("This track appears in 2 journal entries") —
 * one class rather than three, since all three are the same one-line read against
 * [CartographyEntryRepository] and a caller only ever needs the one matching what it's about to
 * delete. Track/waypoint/offline-region only, per the dispatch's own 4b list — see
 * [com.forager.app.data.local.CartographyEntryFindRefEntity]'s doc comment for why finds are not
 * included.
 */
class GetEntryReferenceCountUseCase(
    private val repository: CartographyEntryRepository,
) {
    suspend fun forTrack(trackId: String): Result<Int> = repository.countEntriesReferencingTrack(trackId)

    suspend fun forWaypoint(waypointId: String): Result<Int> = repository.countEntriesReferencingWaypoint(waypointId)

    suspend fun forOfflineRegion(offlineRegionId: Long): Result<Int> =
        repository.countEntriesReferencingOfflineRegion(offlineRegionId)
}
