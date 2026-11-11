package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.AvailabilitySearchResult
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GetAvailabilityUseCase
import com.zynergylabs.forager.app.domain.GetConditionsUseCase
import com.zynergylabs.forager.app.domain.GetSeasonalPatternUseCase
import com.zynergylabs.forager.app.domain.GetSightingsUseCase
import com.zynergylabs.forager.app.domain.GetTodaysForecastUseCase
import com.zynergylabs.forager.app.domain.GetTripWindowsUseCase
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The search runs of [AvailabilityViewModel] (UI split Phase 2.4, PR 8, dispatch 2026-10-19-33, RECORD
 * -1453), moved out of the ViewModel unchanged: [searchJobs], the parent every search fetch runs in; the
 * search last run ([activeSearch]); the queries the map's sightings and the seasonal pattern were loaded
 * for; and the launch bodies of the six fetches. Every write to the screen's
 * state goes through [updateState], and every launch goes through [scope], the ViewModel's own
 * `viewModelScope`, read each time rather than kept.
 *
 * **Whether a fetch starts, and when the parent is cancelled and renewed, is not decided here** (RECORD
 * -1450's reading, applied by the planner to this series). `refresh` keeps the species group it stores and
 * the this-month branch, the two tab functions keep their guards, and `clearSearch` keeps its cancel,
 * lookup cancel and renewal in order; each calls in here where its launch or statement was.
 */
internal class AvailabilitySearchRuns(
    private val updateState: ((AvailabilityUiState) -> AvailabilityUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val getAvailability: GetAvailabilityUseCase,
    /** The ViewModel's `loadRecentSearches`, called where the ranked-list fetch called it. */
    private val loadRecentSearches: () -> Unit,
    private val getSightings: GetSightingsUseCase,
    private val getConditions: GetConditionsUseCase,
    private val getTripWindows: GetTripWindowsUseCase,
    private val getSeasonalPattern: GetSeasonalPatternUseCase,
    private val getTodaysForecast: GetTodaysForecastUseCase,
    private val errorLog: ErrorLog,
) {

    /** The region+month+filter the current [AvailabilityUiState.sightings] were fetched for, or null if none fetched yet. */
    var loadedSightingsQuery: Triple<Region, Int, TaxonFilter>? = null
        private set

    /** The region+month+filter the current [AvailabilityUiState.seasonalPattern] was fetched for, or null if none fetched yet. */
    var loadedSeasonalPatternQuery: Triple<Region, Int, TaxonFilter>? = null
        private set

    /**
     * RECORD -723: the parent of every fetch a search starts (the ranked list, rainfall, today's forecast, trip
     * windows, and the map's sightings and the seasonal pattern fetched for it), so [AvailabilityViewModel.clearSearch] can cancel the
     * ones still running; otherwise a fetch finishing after Clear would put the cleared search back. A child of
     * the ViewModel's own scope, so it is cancelled with it; a supervisor, so one failed fetch cancels no other,
     * as when each was launched on the scope directly.
     */
    private var searchJobs = SupervisorJob(scope().coroutineContext[Job])

    /**
     * RECORD -725: the region, month and filter of the search last run ([AvailabilityViewModel.refresh]), which the map's sightings and
     * the seasonal pattern are fetched for. Read instead of the UI state's current values because picking a species
     * suggestion now selects it without searching ("no search yet"); the map must go on showing the search that ran,
     * not fetch the newly picked species for the old region. Null before any search and after [AvailabilityViewModel.clearSearch].
     */
    var activeSearch: Triple<Region, Int, TaxonFilter>? = null
        private set

    /**
     * Dispatch 2026-11-11-01, F7: the search whose ranked list last failed, or null. Kept apart from the failure's line in
     * `errorMessage`, which a mistyped coordinate or a failed locate can replace, so "Try again" still runs both halves of a
     * search whose two halves failed (C3, the owner: "One line (Recommended)": "tapping it runs both again").
     */
    var listFailedQuery: Triple<Region, Int, TaxonFilter>? = null
        private set

    /** A new search, before its fetches: [AvailabilityViewModel.refresh]'s opening lines, moved unchanged. */
    fun startNewSearch(region: Region, month: Int, filter: TaxonFilter) {
        activeSearch = Triple(region, month, filter)
        // A new search invalidates any sightings loaded for a previous region/month/filter.
        loadedSightingsQuery = null
        updateState {
            // searchSerial: so the screen fetches the sightings again even when this search is the one
            // already showing (RECORD -723; see AvailabilityUiState.searchSerial).
            // isRetryingSearch: a new search is not a retry (dispatch 2026-11-11-01, E4); retryFailedSearch sets it after this.
            it.copy(sightings = emptyList(), sightingsErrorMessage = null, isRetryingSearch = false, searchSerial = it.searchSerial + 1)
        }

        // Same invalidation for the Seasonal tab's own lazily-fetched, separately-keyed data.
        loadedSeasonalPatternQuery = null
        updateState {
            it.copy(seasonalPattern = null, seasonalPatternErrorMessage = null)
        }
    }

    /** The ranked list's fetch, launched in [searchJobs]: [AvailabilityViewModel.refresh] decides and calls it. */
    fun fetchAvailability(region: Region, month: Int, filter: TaxonFilter, speciesGroup: String?) {
        scope().launch(searchJobs) {
            listFailedQuery = null
            updateState { it.copy(isLoading = true, errorMessage = null) }
            getAvailability(region, month, filter, speciesGroup).fold(
                onSuccess = { result ->
                    updateState { state ->
                        when (result) {
                            is AvailabilitySearchResult.Live -> state.copy(
                                isLoading = false,
                                forecast = result.forecast,
                                isShowingCachedResults = false,
                                cachedResultsAsOfEpochMillis = null,
                            )
                            // Both fields set together, from one result, so the banner can never
                            // be shown without the age it is supposed to state.
                            is AvailabilitySearchResult.Cached -> state.copy(
                                isLoading = false,
                                forecast = result.forecast,
                                isShowingCachedResults = true,
                                cachedResultsAsOfEpochMillis = result.cachedAtEpochMillis,
                            )
                        }
                    }
                },
                onFailure = { error ->
                    // Live failed and nothing was cached for this exact search, so there are no
                    // results on screen at all: the cached-results flags are cleared rather than
                    // left over from a previous search the banner would then mislabel.
                    errorLog.w(TAG, "Search failed.", error)
                    listFailedQuery = Triple(region, month, filter)
                    updateState {
                        it.copy(
                            isLoading = false,
                            isShowingCachedResults = false,
                            cachedResultsAsOfEpochMillis = null,
                            errorMessage = LIST_SEARCH_FAILED_MESSAGE,
                        )
                    }
                },
            )
            // Either outcome can have changed the cache: a live search writes a new entry (and may
            // evict one), and a cache hit moves its entry to the front of the LRU order.
            loadRecentSearches()
        }
    }

    /** Recent rainfall's fetch, launched in [searchJobs]: [AvailabilityViewModel.refresh] decides (this month only) and calls it. */
    fun fetchConditions(region: Region) {
        scope().launch(searchJobs) {
            updateState { it.copy(isLoadingConditions = true, conditionsErrorMessage = null) }
            getConditions(region).fold(
                onSuccess = { conditions -> updateState { it.copy(isLoadingConditions = false, conditions = conditions) } },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't load recent rainfall.", error)
                    updateState {
                        it.copy(
                            isLoadingConditions = false,
                            conditions = null,
                            // Not belief-changing — the user wanted rainfall data, not a report
                            // on the network. See docs/error-presentation-spec.md's per-field
                            // table: neutral "unavailable" wording, not "Couldn't load...".
                            conditionsErrorMessage = "Rainfall data unavailable.",
                        )
                    }
                },
            )
        }
    }

    /** Today's forecast's fetch, launched in [searchJobs]: [AvailabilityViewModel.refresh] decides (this month only) and calls it. */
    fun fetchTodaysForecast(region: Region) {
        scope().launch(searchJobs) {
            updateState { it.copy(isLoadingTodaysForecast = true, todaysForecastErrorMessage = null) }
            getTodaysForecast(region).fold(
                onSuccess = { forecast -> updateState { it.copy(isLoadingTodaysForecast = false, todaysForecast = forecast) } },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't load today's forecast.", error)
                    updateState {
                        it.copy(
                            isLoadingTodaysForecast = false,
                            todaysForecast = null,
                            todaysForecastErrorMessage = "Forecast unavailable.",
                        )
                    }
                },
            )
        }
    }

    /** The trip windows' fetch, launched in [searchJobs]: [AvailabilityViewModel.refresh] calls it for every search. */
    fun fetchTripWindows(region: Region) {
        scope().launch(searchJobs) {
            updateState { it.copy(isLoadingTripWindows = true, tripWindowsErrorMessage = null) }
            getTripWindows(region).fold(
                onSuccess = { report -> updateState { it.copy(isLoadingTripWindows = false, tripWindowReport = report) } },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't load trip-window weather.", error)
                    updateState {
                        it.copy(
                            isLoadingTripWindows = false,
                            tripWindowReport = null,
                            tripWindowsErrorMessage = "Couldn't load trip-window weather.",
                        )
                    }
                },
            )
        }
    }

    /** The map's sightings fetch, launched in [searchJobs]: [AvailabilityViewModel.onMapTabSelected] keeps its guards and calls it. */
    fun fetchSightings(query: Triple<Region, Int, TaxonFilter>, region: Region, month: Int, filter: TaxonFilter) {
        scope().launch(searchJobs) {
            updateState { it.copy(isLoadingSightings = true, sightingsErrorMessage = null) }
            getSightings(region, month, filter).fold(
                onSuccess = { page ->
                    loadedSightingsQuery = query
                    updateState {
                        it.copy(isLoadingSightings = false, isRetryingSearch = false, sightings = page.sightings)
                    }
                },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't load sightings for the map.", error)
                    updateState {
                        it.copy(
                            isLoadingSightings = false,
                            isRetryingSearch = false,
                            sightingsErrorMessage = "Couldn't load sightings for the map.",
                        )
                    }
                },
            )
        }
    }

    /** The seasonal pattern's fetch, launched in [searchJobs]: [AvailabilityViewModel.onSeasonalTabSelected] keeps its guards and calls it. */
    fun fetchSeasonalPattern(query: Triple<Region, Int, TaxonFilter>, region: Region, month: Int, filter: TaxonFilter) {
        scope().launch(searchJobs) {
            updateState { it.copy(isLoadingSeasonalPattern = true, seasonalPatternErrorMessage = null) }
            getSeasonalPattern(region, month, filter).fold(
                onSuccess = { distribution ->
                    loadedSeasonalPatternQuery = query
                    updateState { it.copy(isLoadingSeasonalPattern = false, seasonalPattern = distribution) }
                },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't load the seasonal pattern.", error)
                    updateState {
                        it.copy(
                            isLoadingSeasonalPattern = false,
                            seasonalPattern = null,
                            seasonalPatternErrorMessage = "Couldn't load the seasonal pattern.",
                        )
                    }
                },
            )
        }
    }

    /** [AvailabilityViewModel.clearSearch]'s first line: cancels every fetch still running in [searchJobs]. */
    fun cancelRunning() {
        searchJobs.cancel()
    }

    /** [AvailabilityViewModel.clearSearch]'s third line: a fresh parent for the next search's fetches. */
    fun renewParent() {
        searchJobs = SupervisorJob(scope().coroutineContext[Job])
    }

    /** [AvailabilityViewModel.clearSearch]'s next three lines: no search has run, and nothing is loaded for one. */
    fun forgetSearch() {
        activeSearch = null
        loadedSightingsQuery = null
        loadedSeasonalPatternQuery = null
        listFailedQuery = null
    }
}

/**
 * The ranked list's failure line (the text it always had). Named so the Maps tab can tell this failure from the other things
 * `errorMessage` carries, a coordinate the user mistyped, say: when the map's sightings fail too, the Maps tab shows one line for
 * both (dispatch 2026-11-11-01, C3, the owner: "One line (Recommended)"); see [mapsTabSearchNoticeState].
 */
internal const val LIST_SEARCH_FAILED_MESSAGE = "Request failed. Check your connection and try again."
