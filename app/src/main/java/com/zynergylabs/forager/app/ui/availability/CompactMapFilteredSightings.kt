package com.zynergylabs.forager.app.ui.availability

// UI split Phase 2.3, PR 2 (dispatch 2026-10-19-35): moved out of CompactMapTab as a plain function, called at the line where
// the tab worked this out before. Not composable, so composition is unchanged.

import com.zynergylabs.forager.app.domain.model.Sighting

/**
 * The sightings the map plots. Sightings are only real once a search has actually run ([hasSearched]); before that the
 * viewport has no sightings plotted on it. "View on Map" from a List-tab row: filtered against uiState.sightings itself
 * (what the map actually draws), not against uiState.forecast's own observationCount — the two can legitimately disagree
 * (the forecast is a separate historical query; the map only shows what actually loaded for this region), so the count
 * in mapTaxonFilterLabel is what's really on screen. (That reason is the removed tablet MapTab's comment, verbatim but for one
 * capital and its "below", as of 3e973bbc^, AvailabilityWideLayoutUi.kt:395-400.)
 */
internal fun compactMapFilteredSightings(uiState: AvailabilityUiState, hasSearched: Boolean, taxonFilter: Long?): List<Sighting> = when {
    !hasSearched -> emptyList()
    taxonFilter != null -> uiState.sightings.filter { it.taxonId == taxonFilter }
    else -> uiState.sightings
}
