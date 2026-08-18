package com.forager.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room's on-disk shape for one [com.forager.app.domain.model.LogPhoto], keyed to its owning
 * [MushroomLogEntryEntity] by [entryId]. A separate table rather than a column on the entry itself
 * because an entry has a variable number of photos — the same "one row per variable-length item"
 * reasoning as [PlannedTripEntity] vs. this table, just one level further: an entry:photos
 * relationship, not a bare list.
 */
@Entity(tableName = "log_photos")
data class LogPhotoEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    /** Relative to app-private storage — see [com.forager.app.domain.model.LogPhoto.relativePath]. */
    val relativePath: String,
)
