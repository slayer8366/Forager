package com.forager.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Room access to both the entry table and its photos table together. An abstract class rather than
 * an interface, and one DAO spanning both tables rather than two, for the same reason as
 * [CachedSearchDao]: saving or deleting an entry has to touch both tables atomically — a crash
 * between "replace the entry row" and "replace its photo rows" must not leave an entry pointing at
 * stale or duplicate photos — and `@Transaction` with a body is what makes that atomic.
 */
@Dao
abstract class MushroomLogDao {

    @Query("SELECT * FROM mushroom_log_entries")
    abstract suspend fun getAllEntries(): List<MushroomLogEntryEntity>

    @Query("SELECT * FROM log_photos")
    abstract suspend fun getAllPhotos(): List<LogPhotoEntity>

    /** Replace-on-conflict: [upsertEntryWithPhotos] saves and updates through the same call. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertEntry(entity: MushroomLogEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPhotos(photos: List<LogPhotoEntity>)

    @Query("DELETE FROM log_photos WHERE entryId = :entryId")
    abstract suspend fun deletePhotosForEntry(entryId: String)

    @Query("DELETE FROM mushroom_log_entries WHERE id = :id")
    abstract suspend fun deleteEntryById(id: String)

    /**
     * Replaces [entity]'s row and its entire set of photo rows with [photos] — a full replace of
     * the photo set rather than a diff, since the domain layer always hands over the complete,
     * current photo list for an entry (see `RoomMushroomLogRepository.save`) and there is no
     * partial-update call site that would make a diff worth the extra complexity.
     */
    @Transaction
    open suspend fun upsertEntryWithPhotos(entity: MushroomLogEntryEntity, photos: List<LogPhotoEntity>) {
        upsertEntry(entity)
        deletePhotosForEntry(entity.id)
        if (photos.isNotEmpty()) insertPhotos(photos)
    }

    @Transaction
    open suspend fun deleteEntryAndPhotos(id: String) {
        deletePhotosForEntry(id)
        deleteEntryById(id)
    }
}
