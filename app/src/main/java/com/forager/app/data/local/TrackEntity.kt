package com.forager.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room's on-disk shape for a [com.forager.app.domain.model.Track], minus its points — those live
 * in their own table, [TrackPointEntity], the same one-entity-per-table split
 * [MushroomLogEntryEntity]/[LogPhotoEntity] uses for an entry and its photos, and for the same
 * reason: a track can carry thousands of points, which does not belong flattened into one row.
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
)
