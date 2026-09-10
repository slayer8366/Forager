package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.data.local.OfflineRegionDao
import com.zynergylabs.forager.app.data.local.OfflineRegionEntity
import com.zynergylabs.forager.app.domain.OfflineRegionDayIndex
import com.zynergylabs.forager.app.domain.OfflineRegionMetadata
import com.zynergylabs.forager.app.domain.model.Region

/**
 * Room-backed [OfflineRegionDayIndex]; the only place [OfflineRegionEntity] and
 * [OfflineRegionMetadata] meet. See [OfflineRegionDayIndex]'s own doc comment for why this reads
 * [OfflineRegionDao] directly rather than going through `com.zynergylabs.forager.app.map.MapLibreOfflineMapRepository`
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
