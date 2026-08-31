package com.forager.app.ui.availability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forager.app.domain.AppThemePreferenceRepository
import com.forager.app.domain.AvailabilitySearchResult
import com.forager.app.domain.CachedSearchSummary
import com.forager.app.domain.ClusterForagingAreasUseCase
import com.forager.app.domain.DeletePlannedTripUseCase
import com.forager.app.domain.ErrorLog
import com.forager.app.domain.ForagingSelection
import com.forager.app.domain.GetAvailabilityUseCase
import com.forager.app.domain.GetConditionsUseCase
import com.forager.app.domain.GetPlannedTripsUseCase
import com.forager.app.domain.GetRecentSearchesUseCase
import com.forager.app.domain.GetSeasonalPatternUseCase
import com.forager.app.domain.GetSightingsUseCase
import com.forager.app.domain.GetTodaysForecastUseCase
import com.forager.app.domain.GetTripWindowsUseCase
import com.forager.app.domain.LocationFix
import com.forager.app.domain.LocationProvider
import com.forager.app.domain.LocationResult
import com.forager.app.domain.LocationTracker
import com.forager.app.domain.DistanceUnitPreferenceRepository
import com.forager.app.domain.MapPreferencesRepository
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.PredictAvailabilityUseCase
import com.forager.app.domain.SavePlannedTripUseCase
import com.forager.app.domain.SearchTaxaUseCase
import com.forager.app.domain.estimateOfflineTileCount
import com.forager.app.domain.model.AppThemeMode
import com.forager.app.domain.model.DistanceUnit
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
    /** See [AvailabilityUiState.liveLocation]'s own doc comment for why this is collected separately from [locationProvider]'s one-shot fetch. */
    private val locationTracker: LocationTracker,
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
    /**
     * Logs a failure's throwable for diagnosis, without ever exposing its text to the user — see
     * [ErrorLog]'s own doc comment for why this exists rather than calling [android.util.Log]
     * directly. Defaults to discarding the throwable, which is exactly what makes every existing
     * test safe under a plain JVM run with no per-test setup; `MainActivity` wires the real
     * `Log.w`-backed one for production.
     */
    private val errorLog: ErrorLog = ErrorLog { _, _, _ -> },
    private val mapPreferencesRepository: MapPreferencesRepository,
    private val distanceUnitPreferenceRepository: DistanceUnitPreferenceRepository,
    private val appThemePreferenceRepository: AppThemePreferenceRepository,
    private val getTodaysForecast: GetTodaysForecastUseCase,
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
        loadOfflineRegions()
        loadOfflineMapPreferences()
        loadDistanceUnitPreference()
        loadNightModePreferences()
        loadThemeModePreference()
        // The compass strip's live coordinates — see AvailabilityUiState.liveLocation's own doc
        // comment. Runs for this ViewModel's whole lifetime, not gated on a search or a track
        // recording: "any time the map is open" was the explicit ask this answers. A denied/
        // unsupported permission just never emits an Update here — locationTracker.fixes' own doc
        // comment covers that "explicit unsupported, not a silent empty stream" contract; this
        // ViewModel doesn't duplicate that signaling, since the strip's own "Coordinates
        // unavailable" text already covers the null case honestly either way.
        viewModelScope.launch {
            locationTracker.fixes.collect { fix ->
                if (fix is LocationFix.Update) {
                    _uiState.update {
                        it.copy(liveLocation = LatLng(fix.lat, fix.lng), liveAltitudeMeters = fix.altitude)
                    }
                }
            }
        }
    }

    fun onRadiusChanged(radiusKm: Int) {
        _uiState.update { it.copy(radiusKm = Region.clampRadiusKm(radiusKm)) }
    }

    fun onMonthSelected(month: Int) {
        _uiState.update { it.copy(selectedMonth = month) }
        _uiState.value.region?.let { refresh(it, month, _uiState.value.taxonFilter) }
    }

    fun onTaxonSearchQueryChanged(query: String) {
        _uiState.update { it.copy(taxonSearchQuery = query, taxonSearchHasNoResults = false) }
        taxonSearchJob?.cancel()

        if (query.trim().length < SearchTaxaUseCase.MIN_QUERY_LENGTH) {
            _uiState.update { it.copy(taxonSearchResults = emptyList(), isSearchingTaxa = false, taxonSearchErrorMessage = null) }
            return
        }

        taxonSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearchingTaxa = true, taxonSearchErrorMessage = null) }
            searchTaxa(query).fold(
                onSuccess = { results ->
                    _uiState.update {
                        it.copy(
                            isSearchingTaxa = false,
                            taxonSearchResults = results,
                            taxonSearchHasNoResults = results.isEmpty(),
                        )
                    }
                },
                onFailure = { error ->
                    errorLog.w(TAG, "Species search failed.", error)
                    _uiState.update {
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
        _uiState.update { it.copy(taxonSearchResults = emptyList(), isSearchingTaxa = false, taxonSearchHasNoResults = false) }
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
                taxonSearchHasNoResults = false,
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
                    errorLog.w(TAG, "Couldn't load sightings for the map.", error)
                    _uiState.update {
                        it.copy(
                            isLoadingSightings = false,
                            foragingAreas = null,
                            sightingsErrorMessage = "Couldn't load sightings for the map.",
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
                    errorLog.w(TAG, "Couldn't load the seasonal pattern.", error)
                    _uiState.update {
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
                    errorLog.w(TAG, "Search failed.", error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isShowingCachedResults = false,
                            cachedResultsAsOfEpochMillis = null,
                            errorMessage = "Request failed. Check your connection and try again.",
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
                        errorLog.w(TAG, "Couldn't load recent rainfall.", error)
                        _uiState.update {
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
            // Independent fetch from the one above: a separate provider method (and, in
            // production, a separate identical-parameter request — see GetTodaysForecastUseCase's
            // own doc comment for why that duplication is accepted rather than threaded through
            // GetTripWindowsUseCase's unrelated, already-tested return type).
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingTodaysForecast = true, todaysForecastErrorMessage = null) }
                getTodaysForecast(region).fold(
                    onSuccess = { forecast -> _uiState.update { it.copy(isLoadingTodaysForecast = false, todaysForecast = forecast) } },
                    onFailure = { error ->
                        errorLog.w(TAG, "Couldn't load today's forecast.", error)
                        _uiState.update {
                            it.copy(
                                isLoadingTodaysForecast = false,
                                todaysForecast = null,
                                todaysForecastErrorMessage = "Forecast unavailable.",
                            )
                        }
                    },
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    conditions = null,
                    isLoadingConditions = false,
                    conditionsErrorMessage = null,
                    todaysForecast = null,
                    isLoadingTodaysForecast = false,
                    todaysForecastErrorMessage = null,
                )
            }
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
                    errorLog.w(TAG, "Couldn't load trip-window weather.", error)
                    _uiState.update {
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
                    errorLog.w(TAG, "Couldn't load planned trips.", error)
                    _uiState.update {
                        it.copy(plannedTripsErrorMessage = "Couldn't load planned trips.")
                    }
                },
            )
        }
    }

    /** Called from the trip-planning flow once a date and name are confirmed for a pin placed via [com.forager.app.ui.map.CentrePinLocationPicker]. */
    fun onPlaceTripPin(location: LatLng, date: LocalDate, name: String) {
        viewModelScope.launch {
            savePlannedTrip(location, date, name).fold(
                onSuccess = { loadPlannedTrips() },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't save the planned trip.", error)
                    _uiState.update {
                        it.copy(plannedTripsErrorMessage = "Couldn't save the planned trip.")
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
                    errorLog.w(TAG, "Couldn't delete the planned trip.", error)
                    _uiState.update {
                        it.copy(plannedTripsErrorMessage = "Couldn't delete the planned trip.")
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

    fun onOfflineMapNameChanged(text: String) {
        _uiState.update { it.copy(offlineMapNameText = text) }
    }

    /**
     * Called every time the "Offline Maps" submenu is opened. Two things happen:
     *
     * 1. Always tries the device's current location as the picker's opening default, overriding
     *    whatever centre was showing before (including a centre [loadOfflineMapPreferences]
     *    restored from a prior pick). The project owner's own call, after using the
     *    last-picked-centre default the design doc originally specified: opening the picker away
     *    from home is more common than opening it away from wherever was last downloaded, so "near
     *    me" should win on every open, not just the first. A denial or unavailable fix leaves
     *    whatever centre was already showing in place rather than clearing a good default just
     *    because this particular fetch failed.
     *
     * 2. Re-reads [OfflineMapRepository.listRegions] rather than trusting whatever
     *    [loadOfflineRegions] loaded once at ViewModel construction. Hardware testing found the
     *    list could come up empty right after a cold start with many regions already on disk
     *    (survived the restart), consistent with `OfflineManager`'s native store still finishing
     *    its own initialization at construction time. Re-reading on open is good practice
     *    regardless: this screen should show current state whenever it's opened.
     *
     * Both calls are safe unconditionally: [LocationProvider.getCurrentLocation] only checks
     * whether permission is already granted, never triggering the OS permission dialog itself, and
     * a `listRegions` re-read has no side effect beyond what [loadOfflineRegions] already does on
     * every call.
     */
    fun onOfflineMapsOpened() {
        viewModelScope.launch {
            val result = locationProvider.getCurrentLocation()
            if (result is LocationResult.Success) {
                _uiState.update { it.copy(offlineMapPickerDefaultCenter = LatLng(result.lat, result.lng)) }
            }
        }
        loadOfflineRegions()
    }

    /**
     * Reads every region currently on disk — once at startup, and again every time
     * [onOfflineMapsOpened] fires. Same reasoning as [loadPlannedTrips] for the startup call:
     * downloaded regions have nothing to do with the region search, so this isn't gated behind one.
     *
     * A read failure (e.g. a corrupt metadata blob) is reported via
     * [AvailabilityUiState.offlineRegionsErrorMessage] — but per the error-presentation spec's
     * belief-changing rule, this is a region-*list-load* failure, not a download failure: the user
     * isn't in the middle of an action here, they just want to see what's already on disk, so this
     * renders as an absorbed, neutral read-failure the same way [conditionsErrorMessage]/
     * [plannedTripsErrorMessage] do, not with the error-red treatment [onDownloadOfflineMaps]'s
     * failure gets. The prior list is kept on a failed refresh rather than cleared, so a transient
     * read error doesn't make regions that are still on disk disappear.
     */
    private fun loadOfflineRegions() {
        viewModelScope.launch {
            offlineMapRepository.listRegions().fold(
                onSuccess = { regions -> _uiState.update { it.copy(offlineRegions = regions, offlineRegionsErrorMessage = null) } },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't read offline regions.", error)
                    _uiState.update { it.copy(offlineRegionsErrorMessage = "Couldn't read offline regions.") }
                },
            )
        }
    }

    /**
     * Restores the picker's remembered radius and the staleness badge threshold from
     * [MapPreferencesRepository] — both remembered user intent rather than derived state. The
     * restored centre only matters as a fallback now: [onOfflineMapsOpened] overrides it with the
     * device's current location on every open when that succeeds — this is what the picker shows
     * before that ever runs, or if a later location fetch fails.
     *
     * A read failure here leaves the built-in fallbacks in place
     * ([AvailabilityUiState.offlineMapPickerDefaultCenter] stays `null`,
     * [AvailabilityUiState.offlineStaleThresholdDays] stays [com.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS])
     * rather than blocking the picker on a preferences read that isn't essential to using it — this
     * is deliberately not surfaced to [AvailabilityUiState] at all, since there is no belief for the
     * user to hold about a preference they never see; the throwable is still logged, not silently
     * discarded, per CLAUDE.md.
     */
    private fun loadOfflineMapPreferences() {
        viewModelScope.launch {
            mapPreferencesRepository.getLastPickedRegion().fold(
                onSuccess = { region ->
                    if (region != null) {
                        _uiState.update {
                            it.copy(
                                offlineMapPickerDefaultCenter = LatLng(region.lat, region.lng),
                                offlineMapRadiusKm = region.radiusKm,
                            )
                        }
                    }
                },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the last-picked offline region.", error) },
            )
            mapPreferencesRepository.getStaleThresholdDays().fold(
                onSuccess = { days -> _uiState.update { it.copy(offlineStaleThresholdDays = days) } },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the offline staleness threshold.", error) },
            )
        }
    }

    /** Restores Settings' "Night Maps" checkbox — same read-failure treatment as [loadOfflineMapPreferences]. */
    private fun loadNightModePreferences() {
        viewModelScope.launch {
            mapPreferencesRepository.getNightModeMaps().fold(
                onSuccess = { night -> _uiState.update { it.copy(nightModeMaps = night) } },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the night-maps preference.", error) },
            )
        }
    }

    /**
     * Settings' "Night Maps" checkbox. Updates [AvailabilityUiState.nightModeMaps] immediately —
     * the map should not wait on a DataStore round-trip to reflect what was just checked — then
     * persists in the background; a persist failure is logged but not surfaced, the same
     * not-essential-to-using-it treatment [loadNightModePreferences] gives a read failure.
     */
    fun onNightModeMapsChanged(night: Boolean) {
        _uiState.update { it.copy(nightModeMaps = night) }
        viewModelScope.launch {
            mapPreferencesRepository.setNightModeMaps(night).fold(
                onSuccess = {},
                onFailure = { error -> errorLog.w(TAG, "Couldn't persist the night-maps preference.", error) },
            )
        }
    }

    /** Restores Settings' theme choice (Light/Dark/System Default) — same read-failure treatment as [loadOfflineMapPreferences]. */
    private fun loadThemeModePreference() {
        viewModelScope.launch {
            appThemePreferenceRepository.getThemeMode().fold(
                onSuccess = { mode -> _uiState.update { it.copy(themeMode = mode) } },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the app theme preference.", error) },
            )
        }
    }

    /**
     * Settings' theme choice — the app-wide theme [MainActivity][com.forager.app.MainActivity]
     * resolves and renders via [com.forager.app.ui.theme.ForagerTheme]. Same immediate-update-then-
     * persist shape as [onNightModeMapsChanged].
     */
    fun onThemeModeChanged(mode: AppThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch {
            appThemePreferenceRepository.setThemeMode(mode).fold(
                onSuccess = {},
                onFailure = { error -> errorLog.w(TAG, "Couldn't persist the app theme preference.", error) },
            )
        }
    }

    /**
     * Restores the persisted display unit at startup — see [AvailabilityUiState.distanceUnit]'s own
     * doc comment for the bug this fixes. A read failure keeps [AvailabilityUiState.distanceUnit] at
     * its default rather than surfacing an error the user never asked for, the same reasoning
     * [loadOfflineMapPreferences] applies to its own reads; the throwable is still logged.
     */
    private fun loadDistanceUnitPreference() {
        viewModelScope.launch {
            distanceUnitPreferenceRepository.getDistanceUnit().fold(
                onSuccess = { unit -> _uiState.update { it.copy(distanceUnit = unit) } },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the saved distance unit.", error) },
            )
        }
    }

    /** The Settings panel's km/mi toggle — updates the UI immediately and persists in the background. */
    fun onDistanceUnitSelected(unit: DistanceUnit) {
        _uiState.update { it.copy(distanceUnit = unit) }
        viewModelScope.launch {
            distanceUnitPreferenceRepository.setDistanceUnit(unit).fold(
                onSuccess = {},
                onFailure = { error -> errorLog.w(TAG, "Couldn't save the selected distance unit.", error) },
            )
        }
    }

    /**
     * Adds a region to whatever's already downloaded — see
     * [com.forager.app.domain.OfflineMapRepository]'s doc comment for why this no longer replaces a
     * prior download. A blank name defaults to "Region N" rather than blocking the download, the
     * same "default rather than require" pattern [com.forager.app.domain.model.PlannedTrip.name]
     * established for planned trips.
     *
     * Refuses before ever calling [offlineMapRepository] if [estimateOfflineTileCount] projects
     * this region would push the total over [OfflineMapRepository.TILE_COUNT_LIMIT] — see that
     * constant's doc comment for why this app-side check exists at all: MapLibre's own
     * `setOfflineMapboxTileCountLimit` does not actually stop an explicit region download from
     * exceeding it, confirmed on hardware (three regions totalling 9118 tiles downloaded against a
     * "limit" of 6000). This is the real enforcement.
     *
     * A download failure is belief-changing — the user just tapped Download and is owed a yes or
     * no — so it's reported through [AvailabilityUiState.offlineDownloadStatus] with the error-red
     * treatment, unlike [loadOfflineRegions]'s neutral read-failure.
     */
    fun onDownloadOfflineMaps() {
        val state = _uiState.value
        val lat = state.offlineMapLatText.toDoubleOrNull()
        val lng = state.offlineMapLngText.toDoubleOrNull()
        if (lat == null || lat !in -90.0..90.0 || lng == null || lng !in -180.0..180.0) {
            _uiState.update {
                it.copy(
                    offlineDownloadStatus = OfflineMapStatus.Failed(
                        "Enter a valid latitude (-90 to 90) and longitude (-180 to 180).",
                    ),
                )
            }
            return
        }
        val region = Region(lat, lng, state.offlineMapRadiusKm)
        val name = state.offlineMapNameText.trim().ifBlank { "Region ${state.offlineRegions.size + 1}" }

        val tilesAlreadyUsed = state.offlineRegions.sumOf { it.tileCount }
        val estimatedTiles = estimateOfflineTileCount(region, OfflineMapRepository.MIN_ZOOM, OfflineMapRepository.MAX_ZOOM)
        val remainingBudget = OfflineMapRepository.TILE_COUNT_LIMIT - tilesAlreadyUsed
        if (estimatedTiles > remainingBudget) {
            _uiState.update {
                it.copy(
                    offlineDownloadStatus = OfflineMapStatus.Failed(
                        "This region needs about $estimatedTiles tiles, but only $remainingBudget remain in your " +
                            "${OfflineMapRepository.TILE_COUNT_LIMIT}-tile budget. Delete a region or pick a smaller radius.",
                    ),
                )
            }
            return
        }

        _uiState.update { it.copy(offlineDownloadStatus = OfflineMapStatus.Downloading(downloaded = 0, total = 0)) }
        viewModelScope.launch {
            offlineMapRepository.download(name, region) { downloaded, total ->
                _uiState.update { it.copy(offlineDownloadStatus = OfflineMapStatus.Downloading(downloaded, total)) }
            }.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            offlineDownloadStatus = OfflineMapStatus.Succeeded,
                            offlineMapLatText = "",
                            offlineMapLngText = "",
                            offlineMapNameText = "",
                        )
                    }
                    loadOfflineRegions()
                    viewModelScope.launch { mapPreferencesRepository.setLastPickedRegion(region) }
                },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't download offline maps.", error)
                    _uiState.update {
                        it.copy(offlineDownloadStatus = OfflineMapStatus.Failed("Couldn't download offline maps."))
                    }
                },
            )
        }
    }

    /**
     * Per-region delete, replacing the old all-or-nothing delete. A failure here is reported
     * through the same neutral, non-belief-changing channel [loadOfflineRegions] uses
     * ([AvailabilityUiState.offlineRegionsErrorMessage]), not [AvailabilityUiState.offlineDownloadStatus]
     * — deletion isn't a download, and the region simply staying in the list on failure already
     * shows the delete didn't take effect, the same signal a stale list already carries.
     */
    fun onDeleteOfflineRegion(id: Long) {
        viewModelScope.launch {
            offlineMapRepository.deleteRegion(id).fold(
                onSuccess = { loadOfflineRegions() },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't delete that region.", error)
                    _uiState.update {
                        it.copy(offlineRegionsErrorMessage = "Couldn't delete that region.")
                    }
                },
            )
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val TAG = "AvailabilityViewModel"
    }
}
