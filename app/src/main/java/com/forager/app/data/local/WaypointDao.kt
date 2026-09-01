package com.forager.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WaypointDao {
    @Query("SELECT * FROM waypoints")
    suspend fun getAll(): List<WaypointEntity>

    /**
     * Every waypoint dropped on one local day — Journal Stage 2a's derived-trip read, against the
     * `createdAtEpochMillis` index [MIGRATION_9_10] adds. Half-open range, `[dayStartInclusive,
     * dayEndExclusive)` — see [com.forager.app.domain.LocalDayRange]'s own doc comment for why.
     */
    @Query("SELECT * FROM waypoints WHERE createdAtEpochMillis >= :dayStartInclusive AND createdAtEpochMillis < :dayEndExclusive")
    suspend fun getForDay(dayStartInclusive: Long, dayEndExclusive: Long): List<WaypointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WaypointEntity)

    @Query("DELETE FROM waypoints WHERE id = :id")
    suspend fun deleteById(id: String)
}
