package com.forager.app.ui.log

import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.MushroomLogEntry

/**
 * [entries] is the browsing list, most-recently-found first (see [com.forager.app.domain.GetMushroomLogEntriesUseCase]).
 *
 * [editingEntry] doubles as this screen's own navigation state: non-null means "showing this
 * entry's detail/edit form", null means "showing the list" — see [MushroomLogViewModel.openEntry]/
 * [MushroomLogViewModel.closeEntry]. A separate list/detail enum was considered and rejected: which
 * entry (if any) is open already carries that distinction, and a second field for the same fact
 * could drift out of sync with it.
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
    val isLoadingEntries: Boolean = false,
    val loadErrorMessage: String? = null,
    val editingEntry: MushroomLogEntry? = null,
    val isSavingPhoto: Boolean = false,
    val saveErrorMessage: String? = null,
    val galleryPhotos: List<GalleryPhoto> = emptyList(),
    val isLoadingGalleryPhotos: Boolean = false,
    val galleryLoadErrorMessage: String? = null,
)
