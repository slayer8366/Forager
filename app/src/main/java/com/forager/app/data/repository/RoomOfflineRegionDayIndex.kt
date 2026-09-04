package com.forager.app.data.repository

import com.forager.app.data.local.OfflineRegionDao
import com.forager.app.data.local.OfflineRegionEntity
import com.forager.app.domain.OfflineRegionDayIndex
import com.forager.app.domain.OfflineRegionMetadata
import com.forager.app.domain.model.Region

/**
 * Room-backed [OfflineRegionDayIndex]; the only place [OfflineRegionEntity] and
 * [OfflineRegionMetadata] meet. See [OfflineRegionDayIndex]'s own doc comment for why this reads
 * [OfflineRegionDao] directly rather than going through `com.forager.app.map.MapLibreOfflineMapRepository`
 * — a plain, indexed table read, no native `OfflineManager` call.
 */
class RoomOfflineRegionDayIndex(
    private val dao: OfflineRegionDao,
) : OfflineRegionDayIndex {

    override suspend fun getRegionsCreatedOn(
        dayStartInclusiveEpochMillis: Long,
        dayEndExclusiveEpochMillis: Long,
    ): Result<List<OfflineRegionMetadata>> = runCatchingCancellable {
        dao.getForDay(dayStartInclusiveEpochMillis, dayEndExclusiveEpochMillis).map(OfflineRegionEntity::toMetadata)
    }
}

private fun OfflineRegionEntity.toMetadata() = OfflineRegionMetadata(
    id = id,
    name = name,
    region = Region(lat = lat, lng = lng, radiusKm = radiusKm),
    minZoom = minZoom,
    maxZoom = maxZoom,
    createdAtEpochMillis = createdAtEpochMillis,
    isEntryCapture = isEntryCapture,
)
