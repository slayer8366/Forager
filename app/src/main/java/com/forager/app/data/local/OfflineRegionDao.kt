package com.forager.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OfflineRegionDao {
    @Query("SELECT * FROM offline_regions")
    suspend fun getAll(): List<OfflineRegionEntity>

    @Query("SELECT * FROM offline_regions WHERE id = :id")
    suspend fun getById(id: Long): OfflineRegionEntity?

    /**
     * Every region downloaded on one local day — Journal Stage 2a's derived-trip read, against the
     * `createdAtEpochMillis` index [MIGRATION_9_10] adds. This is *when the row was created*, not
     * when the data it covers was collected — see [OfflineRegionEntity.createdAtEpochMillis]'s own
     * doc comment — so this answers "what did the app download on this day," not "what offline
     * coverage does this day's finds/tracks/waypoints fall under" (that's tile-membership territory,
     * a separate, date-independent question — see `com.forager.app.domain.isCoordinateWithinRegionTiles`).
     * Half-open range, `[dayStartInclusive, dayEndExclusive)` — see
     * [com.forager.app.domain.LocalDayRange]'s own doc comment for why.
     */
    @Query("SELECT * FROM offline_regions WHERE createdAtEpochMillis >= :dayStartInclusive AND createdAtEpochMillis < :dayEndExclusive")
    suspend fun getForDay(dayStartInclusive: Long, dayEndExclusive: Long): List<OfflineRegionEntity>

    /** Replace-on-conflict: also how [com.forager.app.map.MapLibreOfflineMapRepository] self-heals a row rebuilt from the region's own metadata blob. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(region: OfflineRegionEntity)

    @Query("DELETE FROM offline_regions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
