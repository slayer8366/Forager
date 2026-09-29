package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.data.local.CartographyEntryDao
import com.zynergylabs.forager.app.domain.KeptTrackPathRepository
import com.zynergylabs.forager.app.domain.TrackPathCodec
import com.zynergylabs.forager.app.domain.model.LatLng

/** Room-backed [KeptTrackPathRepository]: the only place `cartography_entry_track_paths` rows and [TrackPathCodec] bytes meet. */
class RoomKeptTrackPathRepository(
    private val dao: CartographyEntryDao,
) : KeptTrackPathRepository {
    override suspend fun copyForTrack(trackId: String, path: List<LatLng>): Result<Unit> = runCatchingCancellable {
        dao.copyTrackPathToEveryRef(trackId, TrackPathCodec.encode(path))
    }

    override suspend fun getForEntry(entryId: String): Result<Map<String, List<LatLng>>> = runCatchingCancellable {
        dao.getTrackPaths(entryId).associate { it.trackId to TrackPathCodec.decode(it.path) }
    }
}
