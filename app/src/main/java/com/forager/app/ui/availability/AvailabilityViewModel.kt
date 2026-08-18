package com.forager.app.ui.availability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forager.app.domain.AvailabilitySearchResult
import com.forager.app.domain.CachedSearchSummary
import com.forager.app.domain.ClusterForagingAreasUseCase
import com.forager.app.domain.DeletePlannedTripUseCase
import com.forager.app.domain.ForagingSelection
import com.forager.app.domain.GetAvailabilityUseCase
import com.forager.app.domain.GetConditionsUseCase
import com.forager.app.domain.GetPlannedTripsUseCase
import com.forager.app.domain.GetRecentSearchesUseCase
import com.forager.app.domain.GetSeasonalPatternUseCase
import com.forager.app.domain.GetSightingsUseCase
import com.forager.app.domain.GetTripWindowsUseCase
import com.forager.app.domain.LocationProvider
import com.forager.app.domain.LocationResult
import com.forager.app.domain.OfflineMapInfo
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.PredictAvailabilityUseCase
import com.forager.app.domain.SavePlannedTripUseCase
import com.forager.app.domain.SearchTaxaUseCase
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AvailabilityViewModel(
    private val locationProvider: LocationProvider,
    /**
     * The ranked-list search, cache-aware: live first, the offline cache only when live fails.
     * [PredictAvailabilityUseCase] is deliberately *not* a dependency of this class any more — the
     * fallback decision belongs in one place (see [GetAvailabilityUseCase]) rather than as another
     * branch in [refresh].
     */
    private val getAvailability: GetAvailabilityUseCase,
    private val getRecentSearches: GetRecentSearchesUseCase,
    private val getSightings: GetSightingsUseCase,
    private val searchTaxa: SearchTaxaUseCase,
    private val getConditions: GetConditionsUseCase,
    private val clusterForagingAreas: ClusterForagingAreasUseCase,
    private val getTripWindows: GetTripWindowsUseCase,
    private val getPlannedTrips: GetPlannedTripsUseCase,
    private val savePlannedTrip: SavePlannedTripUseCase,
    private val deletePlannedTrip: DeletePlannedTripUseCase,
    private val getSeasonalPattern: GetSeasonalPatternUseCase,
    private val offlineMapRepository: OfflineMapRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AvailabilityUiState())
    val uiState: StateFlow<AvailabilityUiState> = _uiState.asStateFlow()

    /** The region+month+filter the current [AvailabilityUiState.sightings] were fetched for, or null if none fetched yet. */
    private var loadedSightingsQuery: Triple<Region, Int, TaxonFilter>? = null

    /** The region+month+filter the current [AvailabilityUiState.seasonalPattern] was fetched for, or null if none fetched yet. */
    private var loadedSeasonalPatternQuery: Triple<Region, Int, TaxonFilter>? = null
    private var taxonSearchJob: Job? = null

    init {
        // Independent of any search — see AvailabilityUiState.plannedTrips — so this loads once
        // up front rather than waiting on a region the way sightings and trip windows do.
        loadPlannedTrips()
        // Same reasoning, and the reason it matters more here: the picker is how somebody with no
        // connection gets back to a result at all, so it has to be populated before the first
        // search rather than as a side effect of one.
        loadRecentSearches()
        loadOfflineMapStatus()
    }

    fun onRadiusChanged(radiusKm: Int) {
        _uiState.update { it.copy(radiusKm = Region.clampRadiusKm(radiusKm)) }
    }

    fun onMonthSelected(month: Int) {
        _uiState.update { it.copy(selectedMonth = month) }
        _uiState.value.region?.let { refresh(it, month, _uiState.value.taxonFilter) }
    }

    fun onCategorySelected(category: TaxonFilter) {
        _uiState.update {
            it.copy(
                taxonFilter = category,
                foragingSelection = ForagingSelection.forChip(category),
                taxonSearchQuery = "",
                taxonSearchResults = emptyList(),
            )
        }
        _uiState.value.region?.let { refresh(it, _uiState.value.selectedMonth, category) }
    }

    fun onTaxonSearchQueryChanged(query: String) {
        _uiState.update { it.copy(taxonSearchQuery = query) }
        taxonSearchJob?.cancel()

        if (query.trim().length < SearchTaxaUseCase.MIN_QUERY_LENGTH) {
            _uiState.update { it.copy(taxonSearchResults = emptyList(), isSearchingTaxa = false, taxonSearchErrorMessage = null) }
            return
        }

        taxonSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearchingTaxa = true, taxonSearchErrorMessage = null) }
            searchTaxa(query).fold(
                onSuccess = { results -> _uiState.update { it.copy(isSearchingTaxa = false, taxonSearchResults = results) } },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSearchingTaxa = false,
                            taxonSearchResults = emptyList(),
                            taxonSearchErrorMessage = error.message ?: "Species search failed.",
                        )
                    }
                },
            )
        }
    }

    /**
     * Collapses the species-search suggestion popup without touching the typed query, so it can't
     * be left showing over other content it wasn't meant to sit above — e.g. the drawer opening
     * over the app bar. The in-flight search job is cancelled too: with no results to eventually
     * show, letting it run to completion in the background would only re-populate
     * [AvailabilityUiState.taxonSearchResults] and silently reopen the popup this was just told to
     * close.
     */
    fun onDismissTaxonSuggestions() {
        taxonSearchJob?.cancel()
        _uiState.update { it.copy(taxonSearchResults = emptyList(), isSearchingTaxa = false) }
    }

    fun onTaxonSearchResultSelected(result: TaxonSearchResult) {
        val filter = result.toFilter()
        _uiState.update {
            it.copy(
                taxonFilter = filter,
                foragingSelection = ForagingSelection.fromSearchResult(result),
                // The query that produced the list [result] was picked from — remembered here,
                // at the one point it's guaranteed non-blank (a result can't have been picked
                // from an empty query), so it survives taxonSearchQuery being cleared right below.
                lastTaxonSearchQuery = it.taxonSearchQuery,
                taxonSearchQuery = "",
                taxonSearchResults = emptyList(),
            )
        }
        _uiState.value.region?.let { refresh(it, _uiState.value.selectedMonth, filter) }
    }

    /**
     * Reopens the suggestion dropdown for the last species search, so picking a different result
     * doesn't mean retyping the query from scratch — tapped from the summary strip
     * ([AvailabilityUiState.lastTaxonSearchQuery]'s doc comment has the full picture). Re-runs the
     * search rather than replaying the old [AvailabilityUiState.taxonSearchResults]: this app
     * doesn't own the freshness of iNaturalist's taxonomy data, so showing a list from however
     * long ago without a live query would be a stale result presented as a current one. A no-op
     * when nothing has been searched yet, rather than opening an empty dropdown.
     */
    fun onReopenTaxonSuggestions() {
        val query = _uiState.value.lastTaxonSearchQuery
        if (query.isNotBlank()) onTaxonSearchQueryChanged(query)
    }

    fun onManualLatChanged(text: String) {
        _uiState.update { it.copy(manualLatText = text) }
    }

    fun onManualLngChanged(text: String) {
        _uiState.update { it.copy(manualLngText = text) }
    }

    fun searchManualCoordinates() {
        val state = _uiState.value
        val lat = state.manualLatText.toDoubleOrNull()
        val lng = state.manualLngText.toDoubleOrNull()
        if (lat == null || lat !in -90.0..90.0 || lng == null || lng !in -180.0..180.0) {
            _uiState.update { it.copy(errorMessage = "Enter a valid latitude (-90 to 90) and longitude (-180 to 180).") }
            return
        }
        val region = Region(lat, lng, state.radiusKm)
        _uiState.update { it.copy(region = region, locationPermissionDenied = false) }
        refresh(region, state.selectedMonth, state.taxonFilter)
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = locationProvider.getCurrentLocation()) {
                is LocationResult.Success -> {
                    val region = Region(result.lat, result.lng, _uiState.value.radiusKm)
                    _uiState.update {
                        it.copy(
                            region = region,
                            locationPermissionDenied = false,
                            manualLatText = result.lat.toString(),
                            manualLngText = result.lng.toString(),
                        )
                    }
                    refresh(region, _uiState.value.selectedMonth, _uiState.value.taxonFilter)
                }
                LocationResult.PermissionDenied -> _uiState.update {
                    it.copy(isLoading = false, locationPermissionDenied = true)
                }
                LocationResult.LocationUnavailable -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't determine your location. Enter coordinates manually instead.")
                }
            }
        }
    }

    fun onPermissionDenied() {
        _uiState.update { it.copy(locationPermissionDenied = true) }
    }

    /**
     * The map icon stack's GPS/locate-me button: recenters the map on the device's live location.
     * Deliberately independent of [useCurrentLocation] above — that one sets the *search region*
     * and re-runs the search; this one only reports a point for the map to pan to, touching
     * neither [AvailabilityUiState.region] nor any search state. [LocateMeStatus] carries its own
     * denied/unavailable states rather than reusing [AvailabilityUiState.locationPermissionDenied],
     * which belongs to the other control.
     */
    fun locateMe() {
        viewModelScope.launch {
            _uiState.update { it.copy(locateMeStatus = LocateMeStatus.Loading) }
            val status = when (val result = locationProvider.getCurrentLocation()) {
                is LocationResult.Success -> LocateMeStatus.Located(LatLng(result.lat, result.lng), result.altitude)
                LocationResult.PermissionDenied -> LocateMeStatus.PermissionDenied
                LocationResult.LocationUnavailable -> LocateMeStatus.Unavailable
            }
            _uiState.update { it.copy(locateMeStatus = status) }
        }
    }

    /**
     * The Activity denied the OS permission dialog before [locationProvider] was ever asked — see
     * [onPermissionDenied] for why that gate is the Activity's job, not this class's. Mirrors it
     * for [LocateMeStatus] rather than [AvailabilityUiState.locationPermissionDenied], which is the
     * other control's field.
     */
    fun onLocateMePermissionDenied() {
        _uiState.update { it.copy(locateMeStatus = LocateMeStatus.PermissionDenied) }
    }

    /**
     * Called when the map tab becomes visible. Sightings are fetched lazily, only for the
     * region+month+filter actually being viewed, rather than on every list search, since a
     * map view the user never opens shouldn't cost an extra API call.
     */
    fun onMapTabSelected() {
        val state = _uiState.value
        val region = state.region ?: return
        val query = Triple(region, state.selectedMonth, state.taxonFilter)
        if (loadedSightingsQuery == query) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSightings = true, sightingsErrorMessage = null) }
            getSightings(region, state.selectedMonth, state.taxonFilter).fold(
                onSuccess = { page ->
                    loadedSightingsQuery = query
                    // Clustering is a pure transform of what was just fetched — no extra API
                    // call — so it's computed up front and the toggle only controls display.
                    val areas = clusterForagingAreas(region, page.sightings)
                    _uiState.update {
                        it.copy(isLoadingSightings = false, sightings = page.sightings, foragingAreas = areas)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingSightings = false,
                            foragingAreas = null,
                            sightingsErrorMessage = error.message ?: "Couldn't load sightings for the map.",
                        )
                    }
                },
            )
        }
    }

    /** Toggles the Map tab's foraging-areas layer. Display only: the clustering is already computed. */
    fun onToggleForagingAreas(show: Boolean) {
        _uiState.update { it.copy(showForagingAreas = show) }
    }

    /**
     * Called when the Seasonal tab becomes visible. Mirrors [onMapTabSelected]: fetched lazily,
     * only for the region+month+filter actually being viewed, and cached against that key so
     * revisiting the tab without changing the search doesn't refetch — a fresh
     * [GetSeasonalPatternUseCase] call means a fresh historical-weather fetch, not something to
     * repeat on every tab switch.
     */
    fun onSeasonalTabSelected() {
        val state = _uiState.value
        val region = state.region ?: return
        val query = Triple(region, state.selectedMonth, state.taxonFilter)
        if (loadedSeasonalPatternQuery == query) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSeasonalPattern = true, seasonalPatternErrorMessage = null) }
            getSeasonalPattern(region, state.selectedMonth, state.taxonFilter).fold(
                onSuccess = { distribution ->
                    loadedSeasonalPatternQuery = query
                    _uiState.update { it.copy(isLoadingSeasonalPattern = false, seasonalPattern = distribution) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingSeasonalPattern = false,
                            seasonalPattern = null,
                            seasonalPatternErrorMessage = error.message ?: "Couldn't load the seasonal pattern.",
                        )
                    }
                },
            )
        }
    }

    private fun refresh(region: Region, month: Int, filter: TaxonFilter) {
        // A new search invalidates any sightings loaded for a previous region/month/filter, and
        // with them the areas clustered from those sightings.
        loadedSightingsQuery = null
        _uiState.update {
            it.copy(sightings = emptyList(), foragingAreas = null, sightingsErrorMessage = null)
        }

        // Same invalidation for the Seasonal tab's own lazily-fetched, separately-keyed data.
        loadedSeasonalPatternQuery = null
        _uiState.update {
            it.copy(seasonalPattern = null, seasonalPatternErrorMessage = null)
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            getAvailability(region, month, filter).fold(
                onSuccess = { result ->
                    _uiState.update { state ->
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
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isShowingCachedResults = false,
                            cachedResultsAsOfEpochMillis = null,
                            errorMessage = error.message ?: "Request failed. Check your connection and try again.",
                        )
                    }
                },
            )
            // Either outcome can have changed the cache: a live search writes a new entry (and may
            // evict one), and a cache hit moves its entry to the front of the LRU order.
            loadRecentSearches()
        }

        // Recent rainfall is only meaningful for the current month: searching "what's typical
        // in November" while it's April doesn't make today's rain relevant to that answer.
        if (month == LocalDate.now().monthValue) {
            // Independent of the forecast fetch above: a conditions failure must not block or
            // fail the main forecast, same independence pattern as onMapTabSelected's sightings
            // fetch.
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingConditions = true, conditionsErrorMessage = null) }
                getConditions(region).fold(
                    onSuccess = { conditions -> _uiState.update { it.copy(isLoadingConditions = false, conditions = conditions) } },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoadingConditions = false,
                                conditions = null,
                                conditionsErrorMessage = error.message ?: "Couldn't load recent rainfall.",
                            )
                        }
                    },
                )
            }
        } else {
            _uiState.update { it.copy(conditions = null, isLoadingConditions = false, conditionsErrorMessage = null) }
        }

        // Trip windows are about the days ahead of today, not about the browsed month, so unlike
        // conditions above they are fetched regardless of which month is selected for the ranked
        // list — browsing "what's typical in November" in August doesn't make this week's rain
        // and forecast irrelevant to planning a trip this week.
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTripWindows = true, tripWindowsErrorMessage = null) }
            getTripWindows(region).fold(
                onSuccess = { report -> _uiState.update { it.copy(isLoadingTripWindows = false, tripWindowReport = report) } },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingTripWindows = false,
                            tripWindowReport = null,
                            tripWindowsErrorMessage = error.message ?: "Couldn't load trip-window weather.",
                        )
                    }
                },
            )
        }
    }

    private fun loadRecentSearches() {
        viewModelScope.launch {
            _uiState.update { it.copy(recentSearches = getRecentSearches()) }
        }
    }

    /**
     * Re-runs one of the drawer's recent searches: restores the region, month and filter it was
     * made with, then goes through the ordinary [refresh] flow.
     *
     * Deliberately **not** a "load this from the cache" path. [refresh] already tries live first
     * and falls back to the cache, so tapping a recent search while online returns fresh results
     * for it rather than replaying a stored copy, and tapping the same one offline lands on the
     * cached copy with the banner that says so. A separate cache-only path would have been a
     * second way to reach results, with its own failure and staleness rules to keep in step.
     *
     * The radius and coordinate fields are restored alongside the search itself so the drawer's
     * slider, the manual-coordinate boxes and the summary strip all describe the search that just
     * ran — the same fields [useCurrentLocation] fills in for the same reason.
     */
    fun onRecentSearchSelected(summary: CachedSearchSummary) {
        val region = summary.region
        _uiState.update {
            it.copy(
                region = region,
                radiusKm = region.radiusKm,
                manualLatText = region.lat.toString(),
                manualLngText = region.lng.toString(),
                selectedMonth = summary.month,
                taxonFilter = summary.filter,
                foragingSelection = ForagingSelection.forChip(summary.filter),
                taxonSearchQuery = "",
                taxonSearchResults = emptyList(),
                locationPermissionDenied = false,
            )
        }
        refresh(region, summary.month, summary.filter)
    }

    private fun loadPlannedTrips() {
        viewModelScope.launch {
            getPlannedTrips().fold(
                onSuccess = { trips -> _uiState.update { it.copy(plannedTrips = trips, plannedTripsErrorMessage = null) } },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(plannedTripsErrorMessage = error.message ?: "Couldn't load planned trips.")
                    }
                },
            )
        }
    }

    /** Called from the map's long-press flow once a date and name are confirmed; see [com.forager.app.ui.map.SightingsMap]. */
    fun onPlaceTripPin(location: LatLng, date: LocalDate, name: String) {
        viewModelScope.launch {
            savePlannedTrip(location, date, name).fold(
                onSuccess = { loadPlannedTrips() },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(plannedTripsErrorMessage = error.message ?: "Couldn't save the planned trip.")
                    }
                },
            )
        }
    }

    fun onDeletePlannedTrip(id: String) {
        viewModelScope.launch {
            deletePlannedTrip(id).fold(
                onSuccess = { loadPlannedTrips() },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(plannedTripsErrorMessage = error.message ?: "Couldn't delete the planned trip.")
                    }
                },
            )
        }
    }

    fun onOfflineMapLatChanged(text: String) {
        _uiState.update { it.copy(offlineMapLatText = text) }
    }

    fun onOfflineMapLngChanged(text: String) {
        _uiState.update { it.copy(offlineMapLngText = text) }
    }

    fun onOfflineMapRadiusChanged(radiusKm: Int) {
        _uiState.update { it.copy(offlineMapRadiusKm = Region.clampRadiusKm(radiusKm)) }
    }

    /**
     * Reads whatever's on disk right now, once at startup — same reasoning as [loadPlannedTrips]:
     * a downloaded region has nothing to do with the region search, so it isn't gated behind one.
     * A read failure (e.g. a corrupt sidecar file) is reported through the same [OfflineMapStatus]
     * channel Download/Delete use, rather than silently defaulting to "nothing downloaded" — CLAUDE.md:
     * a failure is reported, not swallowed into a plausible-looking default.
     */
    private fun loadOfflineMapStatus() {
        viewModelScope.launch {
            offlineMapRepository.getStatus().fold(
                onSuccess = { info ->
                    _uiState.update {
                        it.copy(offlineMapStatus = info?.toUiStatus() ?: OfflineMapStatus.NotDownloaded)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(offlineMapStatus = OfflineMapStatus.Failed(error.message ?: "Couldn't read offline map status."))
                    }
                },
            )
        }
    }

    /**
     * Always downloads USGS Topo — see [com.forager.app.domain.OfflineMapRepository]'s doc comment
     * for why this no longer takes a style parameter. It used to be resolved from whichever mode
     * the quick-fire map icon was showing; the project owner's own call, after seeing that built,
     * was that offline downloads should just always target USGS regardless of that live toggle.
     */
    fun onDownloadOfflineMaps() {
        val state = _uiState.value
        val lat = state.offlineMapLatText.toDoubleOrNull()
        val lng = state.offlineMapLngText.toDoubleOrNull()
        if (lat == null || lat !in -90.0..90.0 || lng == null || lng !in -180.0..180.0) {
            _uiState.update {
                it.copy(
                    offlineMapStatus = OfflineMapStatus.Failed(
                        "Enter a valid latitude (-90 to 90) and longitude (-180 to 180).",
                    ),
                )
            }
            return
        }
        val region = Region(lat, lng, state.offlineMapRadiusKm)

        _uiState.update { it.copy(offlineMapStatus = OfflineMapStatus.Downloading(downloaded = 0, total = 0)) }
        viewModelScope.launch {
            offlineMapRepository.download(region) { downloaded, total ->
                _uiState.update { it.copy(offlineMapStatus = OfflineMapStatus.Downloading(downloaded, total)) }
            }.fold(
                onSuccess = { info -> _uiState.update { it.copy(offlineMapStatus = info.toUiStatus()) } },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(offlineMapStatus = OfflineMapStatus.Failed(error.message ?: "Couldn't download offline maps."))
                    }
                },
            )
        }
    }

    fun onDeleteOfflineMaps() {
        viewModelScope.launch {
            offlineMapRepository.delete().fold(
                onSuccess = { _uiState.update { it.copy(offlineMapStatus = OfflineMapStatus.NotDownloaded) } },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(offlineMapStatus = OfflineMapStatus.Failed(error.message ?: "Couldn't delete offline maps."))
                    }
                },
            )
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

private fun OfflineMapInfo.toUiStatus() = OfflineMapStatus.Downloaded(
    region = region,
    tileCount = tileCount,
    sizeBytes = sizeBytes,
    downloadedAtEpochMillis = downloadedAtEpochMillis,
)
