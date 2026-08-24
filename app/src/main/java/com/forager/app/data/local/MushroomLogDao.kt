package com.forager.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Room access to the entry table, the gallery's photo table, and the cross-reference table linking
 * them. One DAO spanning all three (the same "one DAO, atomic compound writes" reasoning as
 * [CachedSearchDao]) rather than three, since deleting an entry has to remove its own
 * cross-reference rows in the same transaction as the entry row itself.
 *
 * **Saving an entry never touches [LogPhotoEntity]/[LogEntryPhotoCrossRef] rows at all** — the
 * pre-`MIGRATION_7_8` shape (`upsertEntryWithPhotos`) deleted and reinserted every photo row for an
 * entry on every save, which was correct when a photo row *was* the entry's own claim to a photo,
 * but would churn cross-reference rows on every autosaved field edit once a photo exists
 * independent of any entry (owner decision, 2026-08-22: gallery ownership). [attachPhoto]/
 * [detachPhoto] are the only writes to [LogEntryPhotoCrossRef] now, each touching exactly the one
 * row a user action (add a photo, remove a photo) actually changed.
 */
@Dao
abstract class MushroomLogDao {

    @Query("SELECT * FROM mushroom_log_entries")
    abstract suspend fun getAllEntries(): List<MushroomLogEntryEntity>

    @Query("SELECT * FROM log_photos")
    abstract suspend fun getAllPhotos(): List<LogPhotoEntity>

    @Query("SELECT * FROM log_entry_photos")
    abstract suspend fun getAllCrossRefs(): List<LogEntryPhotoCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertEntry(entity: MushroomLogEntryEntity)

    /** Inserts a new gallery photo row — the photo existing at all, independent of any entry referencing it. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPhoto(photo: LogPhotoEntity)

    /** One cross-reference row — an entry gaining a reference to one photo it didn't have before. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCrossRef(crossRef: LogEntryPhotoCrossRef)

    /** One cross-reference row removed — an entry losing a reference, never the gallery photo itself. */
    @Query("DELETE FROM log_entry_photos WHERE entryId = :entryId AND photoId = :photoId")
    abstract suspend fun deleteCrossRef(entryId: String, photoId: String)

    @Query("DELETE FROM log_entry_photos WHERE entryId = :entryId")
    abstract suspend fun deleteCrossRefsForEntry(entryId: String)

    @Query("DELETE FROM mushroom_log_entries WHERE id = :id")
    abstract suspend fun deleteEntryById(id: String)

    /**
     * Removes an entry's own row and its cross-reference rows — **not** the gallery photos those
     * references pointed at. This is L1's reversal: correct under entry-owns-photos (where a photo
     * row's only reason to exist was one entry's reference to it), wrong under gallery ownership,
     * where a photo persists whether or not anything still references it.
     */
    @Transaction
    open suspend fun deleteEntryAndCrossRefs(id: String) {
        deleteCrossRefsForEntry(id)
        deleteEntryById(id)
    }
}
