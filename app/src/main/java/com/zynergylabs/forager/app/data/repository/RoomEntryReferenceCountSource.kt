package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.data.local.CartographyEntryDao
import com.zynergylabs.forager.app.domain.EntryReferenceCountSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [EntryReferenceCountSource] over the live count queries on [CartographyEntryDao] (dispatch 2026-10-28-08). */
class RoomEntryReferenceCountSource(private val dao: CartographyEntryDao) : EntryReferenceCountSource {
    override fun waypointCounts(): Flow<Map<String, Int>> =
        dao.observeWaypointReferenceCounts().map { rows -> rows.associate { it.id to it.count } }

    override fun trackCounts(): Flow<Map<String, Int>> =
        dao.observeTrackReferenceCounts().map { rows -> rows.associate { it.id to it.count } }

    override fun offlineRegionCounts(): Flow<Map<Long, Int>> =
        dao.observeOfflineRegionReferenceCounts().map { rows -> rows.associate { it.id to it.count } }

    override fun photoCounts(): Flow<Map<String, Int>> =
        dao.observePhotoReferenceCounts().map { rows -> rows.associate { it.id to it.count } }
}
