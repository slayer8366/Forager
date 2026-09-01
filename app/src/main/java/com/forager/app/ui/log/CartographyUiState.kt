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
 * [candidatesForEditingEntry]/[candidateOfflineRegionsForEditingEntry] are populated **only** for a
 * freshly-started entry, from its day's compiled trip report — the withhold/keep curation surface,
 * "the trip report... is the surface where the user picks what to keep." Reopening an
 * already-created entry later (from the Entries or Drafts list) leaves these `null`: curation is a
 * creation-time act, not something re-offered on every open — see [CartographyViewModel.onOpenEntry]'s
 * own doc comment for the reasoning and its disclosed limitation.
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
