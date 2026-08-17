package com.forager.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlannedTripDao {
    @Query("SELECT * FROM planned_trips")
    suspend fun getAll(): List<PlannedTripEntity>

    /** Replace-on-conflict rather than plain insert: [RoomPlannedTripRepository] saves and updates through the same call. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(trip: PlannedTripEntity)

    @Query("DELETE FROM planned_trips WHERE id = :id")
    suspend fun deleteById(id: String)
}
