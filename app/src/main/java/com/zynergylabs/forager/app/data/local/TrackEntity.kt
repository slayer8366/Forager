package com.zynergylabs.forager.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room's on-disk shape for a [com.zynergylabs.forager.app.domain.model.Track], minus its points — those live
 * in their own table, [TrackPointEntity], the same one-entity-per-table split
 * [MushroomLogEntryEntity]/[LogPhotoEntity] uses for an entry and its photos, and for the same
 * reason: a track can carry thousands of points, which does not belong flattened into one row.
 *
 * [startedAtEpochMillis]/[endedAtEpochMillis] are both indexed as of [MIGRATION_9_10], for
 * [TrackDao]'s day-scoped read — a track spanning midnight must match both days it touches, so that
 * query tests both columns together rather than either alone; see [TrackDao.getTracksForDay]'s own
 * doc comment.
 *
 * [originWaypointId] is a nullable link column as of [MIGRATION_12_13] — see
 * [com.zynergylabs.forager.app.domain.model.Track.originWaypointId] for what it points at. No `@ForeignKey`
 * (none in this database), and deliberately no index: the only reader looks a track up by its own
 * primary key and follows the pointer outward, never the reverse, so an index here would have no
 * query to serve.
 */
@Entity(tableName = "tracks", indices = [Index("startedAtEpochMillis"), Index("endedAtEpochMillis")])
data class TrackEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val originWaypointId: String? = null,
)
