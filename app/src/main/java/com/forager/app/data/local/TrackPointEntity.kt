package com.forager.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room's on-disk shape for one [com.forager.app.domain.model.TrackPoint], keyed to its owning
 * [TrackEntity] by [trackId] — the same entry:photos split [LogPhotoEntity] uses, one level up in
 * row count: a multi-hour recording writes a point every few seconds (see
 * [com.forager.app.domain.model.TrackRecordingMode]), so [trackId] is indexed — unlike
 * [LogPhotoEntity.entryId], which never has enough rows per entry for a missing index to matter.
 *
 * No `@ForeignKey` constraint, matching [LogPhotoEntity]'s precedent: `TrackDao.deleteTrack`'s own
 * `@Transaction` is what keeps a track's row and its points in sync, the same place
 * [MushroomLogDao] does it for an entry and its photos, rather than leaning on SQLite's cascade
 * behaviour for an invariant the DAO already owns.
 *
 * [id] is an auto-generated `Long`, not the `UUID` string every other table in this database uses
 * — a deliberate departure for this one table, since it is the only one written at a point-every-
 * few-seconds rate: a generated 64-bit rowid costs nothing to produce and eight bytes to store,
 * against a `UUID.randomUUID()` call and a 36-byte string per point. Nothing outside this table
 * ever needs to address one point by id, so there is no round-trip cost to the simpler key.
 */
@Entity(tableName = "track_points", indices = [Index("trackId")])
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val lat: Double,
    val lng: Double,
    val altitude: Double?,
    val accuracyMeters: Float?,
    val timestampEpochMillis: Long,
)
