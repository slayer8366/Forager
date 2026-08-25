package com.forager.app.ui.log

import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.MushroomLogEntry

/**
 * [entries] is the browsing list, most-recently-found first, **committed entries only**
 * (see [com.forager.app.domain.GetMushroomLogEntriesUseCase] — Workstream L4b, owner decision #6:
 * nothing appears here until the user has put something there and it's been committed).
 *
 * [draftEntries] is every currently-[MushroomLogEntry.isDraft] row (Workstream L4b) — in normal
 * operation this is only ever populated by a crash: a live edit session resolves its own draft via
 * [MushroomLogViewModel.onSaveEntry]/[MushroomLogViewModel.onCancelEditing]/
 * [MushroomLogViewModel.onLeaveEditingIncidentally] before this ViewModel would ever be recreated,
 * so anything still draft at a fresh [MushroomLogViewModel.loadEntries] is orphaned. Rendered with
 * its own "Draft" indicator, the same precedent as the existing "Incomplete" badge, so recovered
 * entries surface one at a time rather than as one bulk prompt (owner's own reasoning: seeing
 * entries survive one by one is reassuring).
 *
 * [editingEntry] doubles as this screen's own navigation state: non-null means "showing this
 * entry's detail/edit form", null means "showing the list" — see [MushroomLogViewModel.onOpenEntry]/
 * [MushroomLogViewModel.onCloseEntry]. A separate list/detail enum was considered and rejected:
 * which entry (if any) is open already carries that distinction, and a second field for the same
 * fact could drift out of sync with it. While open, [editingEntry] may itself be a draft — see
 * [MushroomLogViewModel]'s own doc comment on the persisted-draft model.
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
)
