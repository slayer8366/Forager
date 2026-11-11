package com.zynergylabs.forager.app.ui.availability

import kotlinx.coroutines.flow.update

/*
 * Dispatch 2026-11-11-01 (known bug 30): running a failed search again, from the Maps tab's "Try again" and when signal returns.
 * An extension function on [AvailabilityViewModel], as the offline maps' functions are (AvailabilityViewModelOfflineMaps.kt), so
 * the ViewModel's file stays under 800 lines (CLAUDE.md, Building); still a ViewModel function (RECORD -1450: every decision to
 * start a fetch stays in one). It reads `searchRuns` and calls `refresh`, which were made `internal` for it; nothing else changed.
 */

/**
 * Runs the search whose map sightings failed again: the owner's "Try again" (dispatch 2026-11-11-01, C1), and the retry when signal
 * returns (C4). Does nothing unless the map's sightings fetch failed for the search now showing, so a second tap while the first
 * retry runs (the fetch clears the failure as it starts) starts nothing more.
 *
 * - **Both failed** (the ranked list's [LIST_SEARCH_FAILED_MESSAGE] too): the whole search runs again, as searching again from the bar
 *   does ([AvailabilityViewModel.refresh]); the owner, C3: "tapping it runs both again". `refresh` renews the search's serial, and the
 *   screen fetches the map's sightings for it when the Maps tab shows, as for any search.
 * - **Only the map's sightings failed**: they are fetched again ([AvailabilityViewModel.onMapTabSelected], which fetches because a
 *   failed fetch never records its query as loaded). The ranked list, which loaded, is left alone.
 */
fun AvailabilityViewModel.retryFailedSearch() {
    val state = _uiState.value
    if (!mapsSearchFailed(state)) return
    val (region, month, filter) = searchRuns.activeSearch ?: return
    // E4: the notice reads "Trying again…" until the map's sightings fetch ends. Set after refresh, whose new search clears it.
    if (state.errorMessage == LIST_SEARCH_FAILED_MESSAGE) {
        refresh(region, month, filter)
        _uiState.update { it.copy(isRetryingSearch = true) }
    } else {
        _uiState.update { it.copy(isRetryingSearch = true) }
        onMapTabSelected()
    }
}
