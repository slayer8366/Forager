package com.zynergylabs.forager.app.ui.availability

// UI split Phase 2.3, PR 2 (dispatch 2026-10-19-35): moved out of CompactMapTab as a plain function, called at the line where
// the tab worked this out before. Not composable, so composition is unchanged.

import com.zynergylabs.forager.app.domain.model.Region

/**
 * The map's own viewport, never null — unlike uiState.region (only set once a real
 * search has run), so the map always has *something* to show rather than the earlier
 * revision's "choose a region" placeholder: the real search region once one exists,
 * otherwise the device's live location once locate-me above resolves one, otherwise a
 * fixed fallback while that's still pending. The project owner's own framing: the Maps
 * tab should already be showing a real map, centred on the user, the moment the app
 * opens — not a message asking them to search first.
 */
internal fun compactMapDisplayRegion(uiState: AvailabilityUiState): Region {
    val located = (uiState.locateMeStatus as? LocateMeStatus.Located)?.location
    return uiState.region
        ?: located?.let { Region(lat = it.lat, lng = it.lng, radiusKm = JOURNAL_PICKER_DEFAULT_REGION.radiusKm) }
        ?: JOURNAL_PICKER_DEFAULT_REGION
}
