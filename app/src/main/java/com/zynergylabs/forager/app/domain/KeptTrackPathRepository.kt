package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * The saved path of a track a journal entry kept (F3, dispatch 2026-09-28-195; owner, 2026-09-29: "Option B",
 * "All recommended"). Owned by this project so domain logic does not depend on Room; [com.zynergylabs.forager.app.data.repository.RoomKeptTrackPathRepository]
 * is the implementation.
 *
 * A path is written once, when the track is deleted ([DeleteTrackUseCase]), for every entry that has a ref row
 * naming the track. Entries read it only when the live track is gone (`GetCartographyEntryMapDataUseCase`).
 */
interface KeptTrackPathRepository {
    /**
     * Writes [path] into a row for **every** ref row naming [trackId]: kept or withheld, committed entry or draft.
     * Idempotent: running it again replaces each row with the same values, which is what lets [DeleteTrackUseCase]
     * run it first and retry a failed delete. [path] is the track's read-seam-filtered lat/lng in timestamp order.
     */
    suspend fun copyForTrack(trackId: String, path: List<LatLng>): Result<Unit>

    /** The saved paths of one entry, by track id; empty when the entry has none. */
    suspend fun getForEntry(entryId: String): Result<Map<String, List<LatLng>>>
}
