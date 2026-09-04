package com.forager.app.domain

import com.forager.app.domain.model.GalleryPhoto
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
 *
 * [deletePhotoFromGallery] (Workstream G3) is the one write that deletes a gallery photo's own
 * row — closing the gap G1 deliberately left open (G1 through G2, nothing here could delete a
 * photo at all). It is reached only from the gallery screen's own warn-then-remove confirmation,
 * never from an entry surface — consistent with the standing rule, not an exception to it: the
 * photo is being deleted directly, from where it lives, by the user, with the consequences (how
 * many entries reference it) stated first. What the rule forbids is data vanishing as a *side
 * effect* of something else, which this isn't.
 */
interface MushroomLogRepository {
    /** Every log entry currently stored, in no particular order, with its referenced photos joined in — ordering is a use-case concern. */
    suspend fun getAll(): Result<List<MushroomLogEntry>>

    /**
     * Every log entry found on one local day, photos joined in the same way [getAll] does — Journal
     * Stage 2a's derived-trip read. [foundOnKey] is [com.forager.app.domain.LocalDayRange.foundOnKey].
     * No draft/committed filtering — see `com.forager.app.data.local.MushroomLogDao.getEntriesForDay`'s own doc comment.
     */
    suspend fun getForDay(foundOnKey: String): Result<List<MushroomLogEntry>>

    /**
     * Every gallery photo, each paired with the entries (if any) currently referencing it —
     * Workstream G2. Deliberately the richer [GalleryPhoto] shape rather than a bare
     * `List<LogPhoto>`: G3's gallery-deletion flow must warn "used in N entries" before removing
     * one, and a thin read path here would just mean G3 building a second one — see
     * `docs/plans/pr26-rework.md`'s G2 dispatch for the reasoning this survives from. In no
     * particular order — [MushroomLogDao.getAllPhotos]'s own query has none, and imposing one is a
     * use-case concern the same way [getAll]'s ordering is.
     */
    suspend fun getAllPhotos(): Result<List<GalleryPhoto>>

    /**
     * Inserts [entry], or replaces the stored entry with the same id if one already exists — the
     * per-keystroke autosave write for whatever row is currently open (Workstream L4b-R: always a
     * *draft* row — either a brand-new entry's own row, or the separate draft row a re-edit creates,
     * never the committed parent it points at until [commitDraft] runs). **Never touches
     * [entry.photos]** — a photo reference is added or removed only through
     * [attachPhotoToEntry]/[detachPhotoFromEntry], one row at a time, so an unrelated field edit
     * never rewrites the entry's whole photo-reference set the way the pre-gallery-ownership shape
     * did. This stays true under the standalone-draft model too: repointing photo references from a
     * draft onto its parent is [commitDraft]'s job specifically, not something [save] ever does.
     */
    suspend fun save(entry: MushroomLogEntry): Result<Unit>

    /**
     * Commits a draft — Save (Workstream L4b-R). [draftId] is the draft row's own id; [committed] is
     * the final content to persist, under the *parent's* id when there is one (same as [draftId]
     * when there isn't — a brand-new entry's first commit). Transactionally: upserts [committed],
     * and — only when [draftId] differs from [committed]'s id — repoints [draftId]'s own photo
     * cross-references onto [committed]'s id (merging with whatever the parent already referenced,
     * never duplicating) and removes the draft row. See `MushroomLogDao.commitDraft`'s own doc
     * comment for why this must be one transaction: a photo reference repointed without its draft
     * row removed, or vice versa, is exactly the silent data loss this operation exists to prevent.
     */
    suspend fun commitDraft(draftId: String, committed: MushroomLogEntry): Result<Unit>

    /**
     * Removes the entry with this id and its own photo *references* — never the referenced photos
     * themselves. Doubles as Cancel (Workstream L4b-R): called with a draft's own id, this discards
     * exactly that row and the photo references it gained during the session, leaving any parent
     * entry (a re-edit's draft has one; a brand-new entry's does not) or any other row untouched — no
     * separate "cancel" operation is needed, since a draft and a committed entry are both just rows
     * in this same table. A no-op, not a failure, if no such entry is stored.
     */
    suspend fun delete(id: String): Result<Unit>

    /** Adds [photo] to the gallery — the photo existing at all, independent of any entry referencing it. */
    suspend fun addPhotoToGallery(photo: LogPhoto): Result<Unit>

    /**
     * Patches [latitude]/[longitude] onto an already-persisted gallery photo — photo-geodata
     * dispatch, `MushroomLogDao.updatePhotoLocation`'s own doc comment for why this is a separate
     * write from [addPhotoToGallery] rather than something [PhotoStore.persist] sets up front for a
     * camera capture. A no-op, not a failure, if [photoId] no longer exists.
     */
    suspend fun updatePhotoLocation(photoId: String, latitude: Double, longitude: Double): Result<Unit>

    /** References [photoId] from [entryId] — the only way an entry's [MushroomLogEntry.photos] gains an entry. */
    suspend fun attachPhotoToEntry(entryId: String, photoId: String): Result<Unit>

    /** Removes [entryId]'s reference to [photoId]. Never deletes the gallery photo itself — that's a gallery-deletion concern, not an entry one. */
    suspend fun detachPhotoFromEntry(entryId: String, photoId: String): Result<Unit>

    /**
     * Removes gallery photo [photoId]'s own row and every entry's cross-reference to it —
     * Workstream G3. Never touches the photo's file; that's [PhotoStore]'s job, deleted by the
     * caller after this succeeds (see [DeleteGalleryPhotoUseCase]'s own doc comment on the
     * ordering). A no-op, not a failure, if no such photo is stored.
     */
    suspend fun deletePhotoFromGallery(photoId: String): Result<Unit>
}
