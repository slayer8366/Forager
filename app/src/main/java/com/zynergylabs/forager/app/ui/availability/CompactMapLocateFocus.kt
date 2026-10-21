package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * Where the Maps tab's map pans for a locate-me once a search has run (MapSlot's `focusOverride`), or null for none (RECORD
 * -1525; the owner's rule from -1468: "come back to Maps > the map is exactly where you left it … only a new search or a
 * tap on locate moves it").
 *
 * A function of the state, not something remembered, so the tab gives the same answer every time it is composed: after the
 * sightings spinner, after a tab change, after a process restart. A fix counts only for the search it arrived under
 * ([LocateMeStatus.Located.searchFrameSerial]): the app-open locate, or one from an earlier search, is not this search's
 * locate-me, and a new search (a new frame) drops it, as the remembered value used to.
 */
internal fun compactMapLocateFocus(uiState: AvailabilityUiState): LatLng? {
    if (uiState.region == null) return null
    val located = uiState.locateMeStatus as? LocateMeStatus.Located ?: return null
    return located.location // REVERT RD: frame number ignored
}
