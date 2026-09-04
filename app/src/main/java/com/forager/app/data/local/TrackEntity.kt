package com.forager.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room's on-disk shape for a [com.forager.app.domain.model.Track], minus its points — those live
 * in their own table, [TrackPointEntity], the same one-entity-per-table split
 * [MushroomLogEntryEntity]/[LogPhotoEntity] uses for an entry and its photos, and for the same
 * reason: a track can carry thousands of points, which does not belong flattened into one row.
 *
 * [startedAtEpochMillis]/[endedAtEpochMillis] are both indexed as of [MIGRATION_9_10], for
 * [TrackDao]'s day-scoped read — a track spanning midnight must match both days it touches, so that
 * query tests both columns together rather than either alone; see [TrackDao.getTracksForDay]'s own
 * doc comment.
 */
@Entity(tableName = "tracks", indices = [Index("startedAtEpochMillis"), Index("endedAtEpochMillis")])
data class TrackEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
)
