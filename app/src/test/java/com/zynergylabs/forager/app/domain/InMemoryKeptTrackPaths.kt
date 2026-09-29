package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * A recording fake of [KeptTrackPathRepository] for tests that need to see what a delete asked it to copy, in
 * what order relative to the track's own delete, and to make it fail. Tests that need the real rows use
 * `RoomKeptTrackPathRepository` over an in-memory Room database instead.
 */
internal class InMemoryKeptTrackPaths : KeptTrackPathRepository {
    /** Each copy asked for, in order: the track id and the path it was given. */
    val copies = mutableListOf<Pair<String, List<LatLng>>>()

    /** Saved paths a test seeds for a read: entry id, then track id. */
    val saved = mutableMapOf<String, MutableMap<String, List<LatLng>>>()

    var failCopy = false
    var failRead = false

    /** Runs at the moment a copy is asked for, so a test can record what else was true then (for example, that the track still existed). */
    var onCopy: (String) -> Unit = {}

    override suspend fun copyForTrack(trackId: String, path: List<LatLng>): Result<Unit> {
        if (failCopy) return Result.failure(IllegalStateException("copy refused by test"))
        onCopy(trackId)
        copies += trackId to path
        return Result.success(Unit)
    }

    override suspend fun getForEntry(entryId: String): Result<Map<String, List<LatLng>>> =
        if (failRead) Result.failure(IllegalStateException("read refused by test")) else Result.success(saved[entryId].orEmpty())
}
