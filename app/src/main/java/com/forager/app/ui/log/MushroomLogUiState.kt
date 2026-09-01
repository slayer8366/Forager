package com.forager.app.ui.log

import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.MushroomLogEntry

/**
 * [entries] is the browsing list, most-recently-found first, **committed entries only**
 * (see [com.forager.app.domain.GetMushroomLogEntriesUseCase] — Workstream L4b, owner decision #6:
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
 * orphaned. Rendered with its own "Draft" indicator, the same precedent as the existing "Incomplete"
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
 * [galleryPhotos]/[isLoadingGalleryPhotos]/[galleryLoadErrorMessage] are [PhotoGalleryScreen]'s own
 * state (Workstream G2) — deliberately separate loading/error fields from [entries]' own, mirroring
 * how [isLoadingEntries]/[loadErrorMessage] are entry-specific rather than one shared "is something
 * loading" flag: the gallery and the entry list are independent reads
 * ([com.forager.app.domain.GetGalleryPhotosUseCase] vs. [com.forager.app.domain.GetMushroomLogEntriesUseCase]),
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
)
