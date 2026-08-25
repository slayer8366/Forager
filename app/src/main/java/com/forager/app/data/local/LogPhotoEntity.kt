package com.forager.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room's on-disk shape for one [com.forager.app.domain.model.LogPhoto] — a standalone gallery row,
 * not keyed to any entry (owner decision, 2026-08-22: gallery ownership, see [LogEntryPhotoCrossRef]
 * for how an entry references one). Before `MIGRATION_7_8` this table carried a required `entryId`
 * column instead; see that migration's own doc comment for why a photo now exists independent of
 * any entry.
 */
@Entity(tableName = "log_photos")
data class LogPhotoEntity(
    @PrimaryKey val id: String,
    /** Relative to app-private storage — see [com.forager.app.domain.model.LogPhoto.relativePath]. */
    val relativePath: String,
    /** `null` only for a row migrated from before this column existed — see [com.forager.app.domain.model.LogPhoto.createdAtEpochMillis]. */
    val createdAtEpochMillis: Long?,
)

/**
 * The many-to-many join between [MushroomLogEntryEntity] and [LogPhotoEntity] — an entry can
 * reference several photos, and (owner decision, 2026-08-22: "the album model") a photo can be
 * referenced by several entries. Deliberately plain indexed columns, not `@ForeignKey` — same
 * reasoning as [MushroomLogEntryEntity.offlineRegionId]'s own doc comment: nothing here should
 * change as an automatic side effect of a row on either side being touched. Deleting an entry
 * removes its own cross-reference rows explicitly (`deleteCrossRefsForEntry`); deleting a gallery
 * photo removes *its* cross-reference rows explicitly too, after the warn-then-remove confirmation
 * that deletion flow owns — never an implicit cascade.
 *
 * **What the old one-to-many shape got wrong, and why many-to-many isn't just nicer:** before
 * `MIGRATION_7_8`, [LogPhotoEntity] carried `entryId` directly, one row per photo. Attaching that
 * same photo to a second entry meant inserting a second `log_photos` row with the same `id` —
 * `OnConflictStrategy.REPLACE` would silently overwrite the first row's `entryId` with the second,
 * with no error and no signal that the first entry's reference had just vanished. Not a crash to
 * catch, a silent loss to design out. A join table makes "referenced by two entries" a second row
 * instead of a conflicting write to the same one.
 */
@Entity(
    tableName = "log_entry_photos",
    primaryKeys = ["entryId", "photoId"],
    indices = [Index("photoId")],
)
data class LogEntryPhotoCrossRef(
    val entryId: String,
    val photoId: String,
)
