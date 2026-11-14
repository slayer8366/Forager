package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import kotlinx.coroutines.flow.update

/*
 * Dispatch 2026-11-11-01, Amendment W (WA3): a search the user starts, told apart from a failed search run again. An extension
 * function on [AvailabilityViewModel], as [retryFailedSearch] is, so the ViewModel's file does not grow (795 lines).
 */

/**
 * A new search by the user: the bar's coordinates, Search (the current location), a month and a recent search. Counts it
 * ([AvailabilityUiState.newSearchSerial]) and runs it ([AvailabilityViewModel.refresh]). [retryFailedSearch] calls `refresh`
 * directly, so running a failed search again is not counted as a new one: the owner's "3 tries, then wait for me" counts the
 * tries of one failed search, and a new search starts the count again (`SearchAutoRetry.onSearch`).
 */
internal fun AvailabilityViewModel.beginSearch(region: Region, month: Int, filter: TaxonFilter) {
    _uiState.update { it.copy(newSearchSerial = it.newSearchSerial + 1) }
    refresh(region, month, filter)
}
