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

    /**
     * Every track overlapping one local day — Journal Stage 2a's derived-trip read, against the
     * `startedAtEpochMillis`/`endedAtEpochMillis` indexes [MIGRATION_9_10] adds. An overlap test, not
     * an equality one: `startedAtEpochMillis < dayEndExclusive` catches a track that started before
     * or during the day, and `endedAtEpochMillis IS NULL OR endedAtEpochMillis >= dayStartInclusive`
     * catches one that is still recording or ended during or after the day started. Together, a
     * track from 10pm to 1am matches *both* the day it started on and the day it ended on — the
     * dispatch's own explicit requirement ("a track crossing midnight appears in both days'
     * reports") — and a still-recording track (`endedAtEpochMillis IS NULL`) matches every day from
     * its start until it actually ends, not just the day it began.
     */
    @Query(
        """
        SELECT * FROM tracks
        WHERE startedAtEpochMillis < :dayEndExclusive
        AND (endedAtEpochMillis IS NULL OR endedAtEpochMillis >= :dayStartInclusive)
        """,
    )
    abstract suspend fun getTracksForDay(dayStartInclusive: Long, dayEndExclusive: Long): List<TrackEntity>

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
