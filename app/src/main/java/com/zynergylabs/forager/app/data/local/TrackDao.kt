package com.zynergylabs.forager.app.data.local

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
     *
     * Leaves out imported tracks (`importedAtEpochMillis IS NOT NULL`): plan T16, the owner's "Records
     * and map only" (-636). An imported track was not a walk on that day in this app's sense, and this
     * read is only the Journal's.
     */
    @Query(
        """
        SELECT * FROM tracks
        WHERE startedAtEpochMillis < :dayEndExclusive
        AND (endedAtEpochMillis IS NULL OR endedAtEpochMillis >= :dayStartInclusive)
        AND importedAtEpochMillis IS NULL
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

    /**
     * A whole track, its row and its points, in one transaction: plan T16's GPX import, so an import can
     * never leave a track row with only some of its points (see
     * [com.zynergylabs.forager.app.domain.TrackRepository.createWithPoints]).
     */
    @Transaction
    open suspend fun insertTrackWithPoints(entity: TrackEntity, points: List<TrackPointEntity>) {
        insertTrack(entity)
        insertPoints(points)
    }

    @Query("UPDATE tracks SET endedAtEpochMillis = :endedAtEpochMillis WHERE id = :id")
    abstract suspend fun updateEndedAt(id: String, endedAtEpochMillis: Long)

    /**
     * As [updateEndedAt], but only for a row that has no end time yet. Returns how many rows it
     * changed: 1, or 0 when the row was already ended or does not exist. The condition is in the
     * statement itself, so no read-then-write gap exists in which another writer's end time could
     * be replaced (dispatch 2026-09-28-400, Amendment 3, ruling C). No schema change.
     */
    @Query("UPDATE tracks SET endedAtEpochMillis = :endedAtEpochMillis WHERE id = :id AND endedAtEpochMillis IS NULL")
    abstract suspend fun updateEndedAtIfOpen(id: String, endedAtEpochMillis: Long): Int

    /** See [com.zynergylabs.forager.app.domain.TrackRepository.setOriginWaypoint]. */
    @Query("UPDATE tracks SET originWaypointId = :waypointId WHERE id = :id")
    abstract suspend fun updateOriginWaypointId(id: String, waypointId: String)

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
