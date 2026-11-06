package com.zynergylabs.forager.app.ui.availability

// UI split Phase 2.3, PR 2 (dispatch 2026-10-19-35): moved out of CompactMapTab as a plain function, called at the line where
// the tab worked this out before. Not composable, so composition is unchanged.

import com.zynergylabs.forager.app.domain.model.Sighting

/**
 * The taxon filter chip's label while "View on Map" from a List-tab row filters the map: the species' name and how many of
 * [filteredSightings] it has; null with no filter. See [compactMapFilteredSightings].
 */
internal fun compactMapTaxonFilterLabel(uiState: AvailabilityUiState, taxonFilter: Long?, filteredSightings: List<Sighting>): String? =
    taxonFilter?.let { id ->
        val name = uiState.forecast?.entries?.firstOrNull { it.species.taxonId == id }?.species
            ?.let { it.commonName ?: it.scientificName }
            ?: filteredSightings.firstOrNull()?.let { it.commonName ?: it.scientificName }
            ?: "this species"
        "$name (${filteredSightings.size})"
    }
