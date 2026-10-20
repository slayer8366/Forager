package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.ForagingSelection
import com.zynergylabs.forager.app.domain.SearchTaxaUseCase
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.Companion.SEARCH_DEBOUNCE_MS
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The species lookup of [AvailabilityViewModel] and the search inputs' plain writes (UI split Phase 2.4,
 * PR 7, dispatch 2026-10-19-33, RECORD -1453), moved out of the ViewModel unchanged. It holds the lookup's
 * one job, [taxonSearchJob], and nothing else. Every write to the screen's state goes through
 * [updateState], and the lookup launches in [scope], the ViewModel's own `viewModelScope`, read each time
 * rather than kept.
 *
 * **Whether a lookup starts or stops is not decided here** (RECORD -1450's reading, applied by the planner
 * to this series). `onTaxonSearchQueryChanged` keeps its cancel, its short-query return and the call to
 * [beginLookup] where the launch was; `onDismissTaxonSuggestions` and `clearSearch` call [cancel] where
 * they cancelled the job before. Each is synchronous.
 */
internal class AvailabilitySpeciesLookup(
    private val updateState: ((AvailabilityUiState) -> AvailabilityUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val searchTaxa: SearchTaxaUseCase,
    private val errorLog: ErrorLog,
) {

    /** The species lookup waiting out its debounce or running, if any: see [beginLookup]. */
    private var taxonSearchJob: Job? = null

    fun onRadiusChanged(radiusKm: Int) {
        updateState { it.copy(radiusKm = Region.clampRadiusKm(radiusKm)) }
    }

    /**
     * The species lookup's launch, with its debounce, moved from [AvailabilityViewModel.onTaxonSearchQueryChanged]
     * unchanged. The ViewModel decides whether to look up (the cancel before it and the short-query return stay
     * there) and calls this where the launch was.
     */
    fun beginLookup(query: String) {
        taxonSearchJob = scope().launch {
            delay(SEARCH_DEBOUNCE_MS)
            updateState { it.copy(isSearchingTaxa = true, taxonSearchErrorMessage = null) }
            searchTaxa(query).fold(
                onSuccess = { results ->
                    updateState {
                        it.copy(
                            isSearchingTaxa = false,
                            taxonSearchResults = results,
                            taxonSearchHasNoResults = results.isEmpty(),
                        )
                    }
                },
                onFailure = { error ->
                    errorLog.w(TAG, "Species search failed.", error)
                    updateState {
                        it.copy(
                            isSearchingTaxa = false,
                            taxonSearchResults = emptyList(),
                            taxonSearchHasNoResults = false,
                            taxonSearchErrorMessage = "Species search failed.",
                        )
                    }
                },
            )
        }
    }

    /** Cancels a lookup still waiting or running: the `taxonSearchJob?.cancel()` the ViewModel had at each of its three sites. */
    fun cancel() {
        taxonSearchJob?.cancel()
    }

    fun onTaxonSearchResultSelected(result: TaxonSearchResult) {
        val filter = result.toFilter()
        updateState {
            it.copy(
                taxonFilter = filter,
                foragingSelection = ForagingSelection.fromSearchResult(result),
                // The query that produced the list [result] was picked from — remembered here,
                // at the one point it's guaranteed non-blank (a result can't have been picked
                // from an empty query), so it survives taxonSearchQuery being cleared right below.
                lastTaxonSearchQuery = it.taxonSearchQuery,
                taxonSearchQuery = "",
                taxonSearchResults = emptyList(),
                taxonSearchHasNoResults = false,
            )
        }
        // RECORD -725, the owner: "instead of having it automatically search, just keep the keyboard open and fill
        // it in, no search yet". The species is selected (the bar's summary names it) and nothing is fetched; the
        // next search the user runs carries it. Before this, a region already searched was searched again here.
    }

    fun onManualLatChanged(text: String) {
        updateState { it.copy(manualLatText = text) }
    }

    fun onManualLngChanged(text: String) {
        updateState { it.copy(manualLngText = text) }
    }
}
