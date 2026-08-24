package com.forager.app.domain

import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry

/**
 * Owned abstraction over mushroom-log persistence — the same pattern as [PlannedTripRepository].
 * Domain and UI code depend on this interface, never on Room directly. The Room-backed
 * implementation lives in `data/repository/`.
 *
 * Photos are a first-class gallery, not part of an entry's own row — see [LogPhoto]'s own doc
 * comment (owner decision, 2026-08-22). [MushroomLogEntry.photos] is populated on read by [getAll]
 * (a join, not a stored list), and [addPhotoToGallery]/[attachPhotoToEntry]/[detachPhotoFromEntry]
 * are the only writes that touch it — [save] never does, deliberately (see its own doc comment).
 */
interface MushroomLogRepository {
    /** Every log entry currently stored, in no particular order, with its referenced photos joined in — ordering is a use-case concern. */
    suspend fun getAll(): Result<List<MushroomLogEntry>>

    /**
     * Inserts [entry], or replaces the stored entry with the same id if one already exists — how
     * both creation and the deferred-observation edit flow are saved. **Never touches [entry.photos]** —
     * a photo reference is added or removed only through [attachPhotoToEntry]/[detachPhotoFromEntry],
     * one row at a time, so an unrelated field edit (autosaved on every keystroke) never rewrites
     * the entry's whole photo-reference set the way the pre-gallery-ownership shape did.
     */
    suspend fun save(entry: MushroomLogEntry): Result<Unit>

    /** Removes the entry with this id and its own photo *references* — never the referenced photos themselves. A no-op, not a failure, if no such entry is stored. */
    suspend fun delete(id: String): Result<Unit>

    /** Adds [photo] to the gallery — the photo existing at all, independent of any entry referencing it. */
    suspend fun addPhotoToGallery(photo: LogPhoto): Result<Unit>

    /** References [photoId] from [entryId] — the only way an entry's [MushroomLogEntry.photos] gains an entry. */
    suspend fun attachPhotoToEntry(entryId: String, photoId: String): Result<Unit>

    /** Removes [entryId]'s reference to [photoId]. Never deletes the gallery photo itself — that's a gallery-deletion concern, not an entry one. */
    suspend fun detachPhotoFromEntry(entryId: String, photoId: String): Result<Unit>
}
