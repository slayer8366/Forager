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

    /** Replace-on-conflict: also how [com.forager.app.map.MapLibreOfflineMapRepository] self-heals a row rebuilt from the region's own metadata blob. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(region: OfflineRegionEntity)

    @Query("DELETE FROM offline_regions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
