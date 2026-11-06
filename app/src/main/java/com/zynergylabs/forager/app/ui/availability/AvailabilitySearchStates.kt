package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CachedSearchSummary
import com.zynergylabs.forager.app.domain.ForagingSelection
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.Companion.DEFAULT_STATE

/*
 * State changes of [AvailabilityViewModel], as plain functions on [AvailabilityUiState] (nav step 2, PR 3, dispatch
 * 2026-10-21-01; Plan A, RECORD -1519). Each was the body of one `_uiState.update { it.copy(...) }` in the ViewModel and is
 * moved out unchanged: the ViewModel still decides when each happens, in the same place and order, and calls
 * `_uiState.update { it.withX(...) }`. None of them decides anything.
 */

/**
 * RECORD -723 and -750 item 3: what the search bar's Clear leaves, [AvailabilityViewModel.clearSearch]'s state change (its doc
 * comment gives the rules). Moved out of that function unchanged.
 */
internal fun AvailabilityUiState.withSearchCleared(): AvailabilityUiState =
    copy(
        region = null,
        forecast = null,
        isLoading = false,
        errorMessage = null,
        isShowingCachedResults = false,
        cachedResultsAsOfEpochMillis = null,
        sightings = emptyList(),
        isLoadingSightings = false,
        sightingsErrorMessage = null,
        conditions = null,
        isLoadingConditions = false,
        conditionsErrorMessage = null,
        todaysForecast = null,
        isLoadingTodaysForecast = false,
        todaysForecastErrorMessage = null,
        tripWindowReport = null,
        isLoadingTripWindows = false,
        tripWindowsErrorMessage = null,
        seasonalPattern = null,
        isLoadingSeasonalPattern = false,
        seasonalPatternErrorMessage = null,
        // RECORD -750, item 3: the selection back to a fresh start's default.
        taxonFilter = DEFAULT_STATE.taxonFilter,
        foragingSelection = DEFAULT_STATE.foragingSelection,
        taxonSearchQuery = DEFAULT_STATE.taxonSearchQuery,
        taxonSearchResults = DEFAULT_STATE.taxonSearchResults,
        isSearchingTaxa = false,
        taxonSearchErrorMessage = null,
        taxonSearchHasNoResults = false,
        lastTaxonSearchQuery = DEFAULT_STATE.lastTaxonSearchQuery,
    )

/**
 * Today's rainfall and forecast cleared, for a search of another month: the branch of [AvailabilityViewModel]'s `refresh`
 * that does not fetch them. Moved out of that function unchanged.
 */
internal fun AvailabilityUiState.withoutTodaysConditions(): AvailabilityUiState =
    copy(
        conditions = null,
        isLoadingConditions = false,
        conditionsErrorMessage = null,
        todaysForecast = null,
        isLoadingTodaysForecast = false,
        todaysForecastErrorMessage = null,
    )

/**
 * A recent search's region, month and filter restored with the fields that describe them:
 * [AvailabilityViewModel.onRecentSearchSelected]'s state change (its doc comment gives why). Moved out of that function unchanged.
 */
internal fun AvailabilityUiState.withRecentSearch(region: Region, summary: CachedSearchSummary): AvailabilityUiState =
    copy(
        region = region,
        radiusKm = region.radiusKm,
        manualLatText = region.lat.toString(),
        manualLngText = region.lng.toString(),
        selectedMonth = summary.month,
        taxonFilter = summary.filter,
        foragingSelection = ForagingSelection.fromRecentSearch(summary),
        taxonSearchQuery = "",
        taxonSearchResults = emptyList(),
        locationPermissionDenied = false,
        searchFrameSerial = searchFrameSerial + 1,
    )

/**
 * A download refused because [estimatedTiles] is more than the [remainingBudget] of the tile budget: the one state change
 * [AvailabilityViewModel.onDownloadOfflineMaps] and [AvailabilityViewModel.onDownloadAgain] each made, word for word. Moved out
 * of both unchanged.
 */
internal fun AvailabilityUiState.withTileBudgetExceeded(estimatedTiles: Int, remainingBudget: Long): AvailabilityUiState =
    copy(
        offlineDownloadStatus = OfflineMapStatus.Failed(
            "This region needs about $estimatedTiles tiles, but only $remainingBudget remain in your " +
                "${OfflineMapRepository.TILE_COUNT_LIMIT}-tile budget. Delete a region or pick a smaller radius.",
        ),
    )
