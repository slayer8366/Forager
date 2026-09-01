package com.forager.app.ui.log

import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.DerivedTrip

/**
 * Cartography's own list/create/edit state — Journal Stage 2b. Deliberately a separate `ViewModel`/
 * state from [MushroomLogUiState]: [CartographyEntry] is a new entity with its own repository (see
 * that type's own doc comment for why it is not an extension of [com.forager.app.domain.model.MushroomLogEntry]).
 *
 * [entries] is committed-only, most recently updated first; [draftEntries] is the complement — same
 * split shape as [MushroomLogUiState.entries]/[MushroomLogUiState.draftEntries], for the Entries/
 * Drafts submenus respectively.
 *
 * [editingEntry] doubles as navigation state, the same convention [MushroomLogUiState.editingEntry]
 * uses: non-null means "showing this entry's edit screen," null means "showing a list."
 *
 * [candidatesForEditingEntry]/[candidateOfflineRegionsForEditingEntry] are the entry's day's freshly
 * compiled trip report — reloaded on *every* open, creation or reopen alike (Stage 2b follow-up
 * dispatch, point 2), not just at creation. [CartographyEntryEditScreen] merges these live candidates
 * against [CartographyEntry]'s own persisted decisions to render the three states a candidate can be
 * in: kept, withheld, or not yet decided (a candidate present here with no matching decision on the
 * entry). `null` only transiently, while [isLoadingCandidates].
 */
data class CartographyUiState(
    val entries: List<CartographyEntry> = emptyList(),
    val draftEntries: List<CartographyEntry> = emptyList(),
    val isLoadingEntries: Boolean = false,
    val loadErrorMessage: String? = null,
    val editingEntry: CartographyEntry? = null,
    val candidatesForEditingEntry: DerivedTrip? = null,
    val candidateOfflineRegionsForEditingEntry: List<OfflineRegionSummary> = emptyList(),
    val isLoadingCandidates: Boolean = false,
    val candidatesErrorMessage: String? = null,
    val saveErrorMessage: String? = null,
)
