package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.domain.PendingDelete
import com.zynergylabs.forager.app.domain.withoutPending
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry

/**
 * [entries] is the browsing list, most-recently-found first, **committed entries only**
 * (see [com.zynergylabs.forager.app.domain.GetMushroomLogEntriesUseCase] — Workstream L4b, owner decision #6:
 * "a draft never appears in the log"). Workstream L4b-R (2026-08-25): an entry currently being
 * re-edited stays in this list showing its **last-saved** values — its draft is a separate row (see
 * [MushroomLogEntry.draftOfEntryId]) that never touches this one until Save.
 *
 * [draftEntries] is every currently-[MushroomLogEntry.isDraft] row — Workstream L4b-R broadens this
 * from a crash-recovery special case into the feed for the **Drafts** filter (owner decision,
 * 2026-08-25: "unsaved work is held in a Drafts section"), toggled alongside [entries] in the same
 * list/gallery screen. A row lands here for one of two reasons, indistinguishable by this field
 * alone and treated identically either way: a live edit session (open right now, or persisted after
 * an incidental exit — decision 2026-08-25: "persists the draft, it does not commit") or one a crash
 * orphaned. Rendered with its own "Draft" indicator, the same precedent as the then-existing "Incomplete"
 * badge, so recovered entries surface one at a time rather than as one bulk prompt (owner's own
 * reasoning: seeing entries survive one by one is reassuring).
 *
 * [editingEntry] doubles as this screen's own navigation state: non-null means "showing this
 * entry's detail/edit form or report", null means "showing the list" — see
 * [MushroomLogViewModel.onOpenEntry]/[MushroomLogViewModel.onCloseEntry]. A separate list/detail
 * enum was considered and rejected: which entry (if any) is open already carries that distinction,
 * and a second field for the same fact could drift out of sync with it. Its identity can change
 * mid-session: opening a committed entry shows that entry itself (report, `isDraft == false`); the
 * moment editing starts ([MushroomLogViewModel.onStartEditingEntry]), it switches to a *separate*
 * draft row with a new id — see [MushroomLogViewModel]'s own doc comment on the standalone-draft
 * model.
 *
 * [galleryPhotos]/[isLoadingGalleryPhotos]/[galleryLoadErrorMessage] are the photo album's own
 * state (Workstream G2; the standalone Photo Gallery screen that first read them was removed in J6) — deliberately separate loading/error fields from [entries]' own, mirroring
 * how [isLoadingEntries]/[loadErrorMessage] are entry-specific rather than one shared "is something
 * loading" flag: the gallery and the entry list are independent reads
 * ([com.zynergylabs.forager.app.domain.GetGalleryPhotosUseCase] vs. [com.zynergylabs.forager.app.domain.GetMushroomLogEntriesUseCase]),
 * and one failing must not read as the other having failed too.
 */
data class MushroomLogUiState(
    val entries: List<MushroomLogEntry> = emptyList(),
    val draftEntries: List<MushroomLogEntry> = emptyList(),
    val isLoadingEntries: Boolean = false,
    val loadErrorMessage: String? = null,
    val editingEntry: MushroomLogEntry? = null,
    val isSavingPhoto: Boolean = false,
    val saveErrorMessage: String? = null,
    val galleryPhotos: List<GalleryPhoto> = emptyList(),
    val isLoadingGalleryPhotos: Boolean = false,
    val galleryLoadErrorMessage: String? = null,
    /** How many Cartography entries currently keep each photo (by id) attached — Journal Stage 2b's 4b deletion warning, extended to photos per the owner's own reasoning (a wordless entry can consist mostly of attached photos). Loaded alongside [galleryPhotos]. */
    val cartographyEntryPhotoReferenceCounts: Map<String, Int> = emptyMap(),
    /**
     * The find whose delete was asked for from its report or edit form (journal redesign J4) and has
     * not run yet: the Undo snackbar is still up. Finds carry no Cartography reference count
     * ([com.zynergylabs.forager.app.domain.GetEntryReferenceCountUseCase] has none for finds), so its
     * [PendingDelete.entryReferenceCount] is always `null`. See [MushroomLogViewModel.requestDeleteEntry].
     */
    val pendingDelete: PendingDelete<MushroomLogEntry>? = null,
    /**
     * The gallery photo whose delete was asked for from its album tile (journal redesign J4b L3) and
     * has not run yet. Its [PendingDelete.entryReferenceCount] is how many Cartography (journal)
     * entries keep it, from [cartographyEntryPhotoReferenceCounts] when the delete was asked for; how
     * many finds use it is [GalleryPhoto.referencingEntryIds] on the held item. See
     * [MushroomLogViewModel.requestDeleteGalleryPhoto].
     */
    val pendingPhotoDelete: PendingDelete<GalleryPhoto>? = null,
    /**
     * The ids of finds whose delete has left its Undo window and was started (or has finished): the Maps
     * tab never draws them again (dispatch 2026-09-28-312, item 9). [pendingDelete] covers the Undo window
     * and nothing after it; this covers the rest. An id goes in **in the same state update** that clears
     * [pendingDelete] (there is no moment the find is in neither), stays after the delete succeeds so a
     * map-records load that read the row before the delete and lands after it cannot draw it again, and
     * comes out only when the delete fails (the find is still saved). Cleared by a restore, which can bring
     * a deleted record back. Not read by any Journal list: J4's pending-delete behaviour is [pendingDelete]'s.
     */
    val committedFindDeleteIds: Set<String> = emptySet(),
    /** [committedFindDeleteIds], for album photos: [pendingPhotoDelete] covers the Undo window, this the rest. */
    val committedPhotoDeleteIds: Set<String> = emptySet(),
) {
    /**
     * This state with [pendingDelete] left out of [entries] and [draftEntries]: what `MainActivity`
     * hands the screen, so a pending find is gone from the Finds gallery, the Finds chip's count and
     * the All logbook at once (J4), and comes back on Undo.
     *
     * Also [pendingPhotoDelete] (J4b L3): left out of [galleryPhotos] (the album, the drawer's
     * gallery, and the Cartography screens' id-to-photo joins, which read this list) and out of each
     * listed find's own `photos` (the Finds gallery's cover photo and the find report), so the photo
     * is gone everywhere a list shows it until Undo. [editingEntry] is left as it is: it is the open
     * form's own working copy, which loadEntries refreshes once the delete has run.
     */
    fun hidingPendingDelete(): MushroomLogUiState {
        if (pendingDelete == null && pendingPhotoDelete == null) return this
        val hiddenPhotoId = pendingPhotoDelete?.item?.photo?.id
        fun List<MushroomLogEntry>.visible(): List<MushroomLogEntry> {
            val withoutFind = withoutPending(pendingDelete) { it.id }
            if (hiddenPhotoId == null) return withoutFind
            return withoutFind.map { entry ->
                if (entry.photos.none { it.id == hiddenPhotoId }) entry else entry.copy(photos = entry.photos.filterNot { it.id == hiddenPhotoId })
            }
        }
        return copy(
            entries = entries.visible(),
            draftEntries = draftEntries.visible(),
            galleryPhotos = galleryPhotos.withoutPending(pendingPhotoDelete) { it.photo.id },
        )
    }
}
