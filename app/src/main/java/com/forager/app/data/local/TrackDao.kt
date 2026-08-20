package com.forager.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Room access to a track's row and its points together.
 *
 * An abstract class rather than an interface, same reason as [CachedSearchDao]/[MushroomLogDao]:
 * [deleteTrackAndPoints] has to remove both tables' rows atomically, and `@Transaction` with a body
 * is what makes that so. [insertPoints] is a plain batch `@Insert`, not wrapped in its own
 * `@Transaction` — Room already runs a multi-row `@Insert` as a single transaction, so wrapping it
 * again would only add overhead, not atomicity it doesn't already have.
 */
@Dao
abstract class TrackDao {

    @Query("SELECT * FROM tracks")
    abstract suspend fun getAllTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    abstract suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestampEpochMillis ASC")
    abstract suspend fun getPointsForTrack(trackId: String): List<TrackPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTrack(entity: TrackEntity)

    /** Batched: the caller hands over every point sampled since the last call in one list, not one `@Insert` per point. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("UPDATE tracks SET endedAtEpochMillis = :endedAtEpochMillis WHERE id = :id")
    abstract suspend fun updateEndedAt(id: String, endedAtEpochMillis: Long)

    @Query("DELETE FROM track_points WHERE trackId = :trackId")
    abstract suspend fun deletePointsForTrack(trackId: String)

    @Query("DELETE FROM tracks WHERE id = :id")
    abstract suspend fun deleteTrackById(id: String)

    @Transaction
    open suspend fun deleteTrackAndPoints(id: String) {
        deletePointsForTrack(id)
        deleteTrackById(id)
    }
}
