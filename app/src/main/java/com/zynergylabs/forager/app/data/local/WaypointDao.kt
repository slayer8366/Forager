package com.zynergylabs.forager.app.data.local

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
     * dayEndExclusive)` — see [com.zynergylabs.forager.app.domain.LocalDayRange]'s own doc comment for why.
     */
    @Query("SELECT * FROM waypoints WHERE createdAtEpochMillis >= :dayStartInclusive AND createdAtEpochMillis < :dayEndExclusive")
    suspend fun getForDay(dayStartInclusive: Long, dayEndExclusive: Long): List<WaypointEntity>

    @Query("SELECT * FROM waypoints WHERE id = :id")
    suspend fun getById(id: String): WaypointEntity?

    /**
     * Every waypoint dropped while [trackId] was recording — HUD-foundations dispatch, Item 3,
     * against the `trackId` index [MIGRATION_12_13] adds. Oldest first, so the order a walker
     * dropped them in is the order they come back.
     */
    @Query("SELECT * FROM waypoints WHERE trackId = :trackId ORDER BY createdAtEpochMillis ASC")
    suspend fun getForTrack(trackId: String): List<WaypointEntity>

    /**
     * Nulls the link on every waypoint that pointed at [trackId] — what
     * [com.zynergylabs.forager.app.domain.DeleteTrackUseCase] runs before removing the track, so the column
     * can never dangle (owner decision, HUD-foundations dispatch, Item 3).
     */
    @Query("UPDATE waypoints SET trackId = NULL WHERE trackId = :trackId")
    suspend fun clearTrackId(trackId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WaypointEntity)

    @Query("DELETE FROM waypoints WHERE id = :id")
    suspend fun deleteById(id: String)
}
