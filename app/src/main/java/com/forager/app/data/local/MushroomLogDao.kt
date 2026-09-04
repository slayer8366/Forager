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
 * **[upsertEntry] never touches [LogPhotoEntity]/[LogEntryPhotoCrossRef] rows at all** — the
 * pre-`MIGRATION_7_8` shape (`upsertEntryWithPhotos`) deleted and reinserted every photo row for an
 * entry on every save, which was correct when a photo row *was* the entry's own claim to a photo,
 * but would churn cross-reference rows on every autosaved field edit once a photo exists
 * independent of any entry (owner decision, 2026-08-22: gallery ownership). [insertCrossRef]/
 * [deleteCrossRef] are the only per-action writes to [LogEntryPhotoCrossRef] — each touching
 * exactly the one row a user action (add a photo, remove a photo) actually changed — and
 * [commitDraft] (Workstream L4b-R) is the only writer that moves a whole set of them at once, from
 * a draft's id onto its parent's, as part of Save.
 */
@Dao
abstract class MushroomLogDao {

    @Query("SELECT * FROM mushroom_log_entries")
    abstract suspend fun getAllEntries(): List<MushroomLogEntryEntity>

    /**
     * One local day's entries — Journal Stage 2a's derived-trip read, against the
     * [MushroomLogEntryEntity.foundOn] index [MIGRATION_9_10] adds. [foundOnKey] is
     * [com.forager.app.domain.LocalDayRange.foundOnKey] — an exact string match, not a range, since
     * [MushroomLogEntryEntity.foundOn] already stores a bare calendar date with no time component.
     * No draft/committed filtering here, the same raw, unconditional shape every other query in this
     * DAO already has; a caller wanting only committed entries filters afterward, the same as
     * [com.forager.app.domain.GetMushroomLogEntriesUseCase] already does for the unscoped read.
     */
    @Query("SELECT * FROM mushroom_log_entries WHERE foundOn = :foundOnKey")
    abstract suspend fun getEntriesForDay(foundOnKey: String): List<MushroomLogEntryEntity>

    @Query("SELECT * FROM log_photos")
    abstract suspend fun getAllPhotos(): List<LogPhotoEntity>

    @Query("SELECT * FROM log_entry_photos")
    abstract suspend fun getAllCrossRefs(): List<LogEntryPhotoCrossRef>

    /** Every cross-reference row for one entry — Workstream L4b-R, the read [commitDraft] repoints from a draft's own id onto its parent's. */
    @Query("SELECT * FROM log_entry_photos WHERE entryId = :entryId")
    abstract suspend fun getCrossRefsForEntry(entryId: String): List<LogEntryPhotoCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertEntry(entity: MushroomLogEntryEntity)

    /** Inserts a new gallery photo row — the photo existing at all, independent of any entry referencing it. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPhoto(photo: LogPhotoEntity)

    /**
     * Photo-geodata dispatch: patches [latitude]/[longitude] onto an already-persisted gallery
     * photo row — the fire-and-forget write [com.forager.app.ui.log.MushroomLogViewModel] issues
     * after a camera capture's live GPS fix resolves, separate from [insertPhoto] since the photo
     * row (and, for an attached photo, its cross-reference) must already exist by the time a fix
     * can possibly come back. A no-op if [photoId] no longer exists (e.g. the photo or its entry
     * was deleted while the fix was still in flight) — not a failure to report, the same "the row
     * being gone already answers the write" reasoning as this DAO's other delete-shaped no-ops.
     */
    @Query("UPDATE log_photos SET latitude = :latitude, longitude = :longitude WHERE id = :photoId")
    abstract suspend fun updatePhotoLocation(photoId: String, latitude: Double, longitude: Double)

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

    /**
     * Workstream L4b-R: commits [committedEntity] — Save. When [draftId] equals [committedEntity]'s
     * own id (a brand-new entry's draft, no parent), this is just the upsert: the row flips to
     * committed in place, and its cross-reference rows are already correct since they were always
     * keyed on this same id. When [draftId] differs (a re-edit's draft, a *separate* row from the
     * committed parent [committedEntity] represents), this additionally repoints every one of
     * [draftId]'s cross-reference rows onto [committedEntity]'s id — merging with, never duplicating,
     * whatever the parent already referenced, since [insertCrossRef]'s own composite primary key
     * makes a repeat insert a no-op — then deletes the now-empty draft row. All in one transaction:
     * a crash or failure partway through must never leave a photo reference repointed without its
     * draft row gone, or vice versa (see the L4b-R dispatch's own "one place this can silently drop
     * data" warning).
     */
    @Transaction
    open suspend fun commitDraft(committedEntity: MushroomLogEntryEntity, draftId: String) {
        upsertEntry(committedEntity)
        if (committedEntity.id != draftId) {
            getCrossRefsForEntry(draftId).forEach { crossRef ->
                insertCrossRef(LogEntryPhotoCrossRef(entryId = committedEntity.id, photoId = crossRef.photoId))
            }
            deleteCrossRefsForEntry(draftId)
            deleteEntryById(draftId)
        }
    }

    /** Every cross-reference row for one gallery photo removed — every entry losing its reference to it. */
    @Query("DELETE FROM log_entry_photos WHERE photoId = :photoId")
    abstract suspend fun deleteCrossRefsForPhoto(photoId: String)

    @Query("DELETE FROM log_photos WHERE id = :id")
    abstract suspend fun deletePhotoById(id: String)

    /**
     * Removes a gallery photo's own row and every cross-reference row pointing at it — Workstream
     * G3, closing the deletion gap G1 deliberately left open. Deletes by `photoId` directly rather
     * than requiring the caller to enumerate which entries currently reference it: a single
     * `DELETE ... WHERE photoId = :photoId` is correct regardless of how many entries reference the
     * photo or whether a caller's own view of that set (e.g. a UI-held `GalleryPhoto` snapshot) is
     * stale. Row-only — the file itself is [PhotoStore]'s concern, deleted by the caller after this
     * transaction commits (see [com.forager.app.domain.DeleteGalleryPhotoUseCase]'s own doc comment
     * on why file deletion happens last, outside this transaction).
     */
    @Transaction
    open suspend fun deletePhotoAndCrossRefs(photoId: String) {
        deleteCrossRefsForPhoto(photoId)
        deletePhotoById(photoId)
    }
}
