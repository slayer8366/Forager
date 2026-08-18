package com.forager.app.ui.availability

import com.forager.app.domain.CachedSearchSummary
import com.forager.app.domain.ForagingSelection
import com.forager.app.domain.model.AvailabilityForecast
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.ForagingAreas
import com.forager.app.domain.model.FruitingLagDistribution
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import com.forager.app.domain.model.TripWindowReport
import java.time.LocalDate

data class AvailabilityUiState(
    val region: Region? = null,
    val selectedMonth: Int = LocalDate.now().monthValue,
    val radiusKm: Int = 15,
    val manualLatText: String = "",
    val manualLngText: String = "",
    val forecast: AvailabilityForecast? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val locationPermissionDenied: Boolean = false,
    val sightings: List<Sighting> = emptyList(),
    val isLoadingSightings: Boolean = false,
    val sightingsErrorMessage: String? = null,
    /**
     * Whether the Map tab's foraging-areas layer is switched on. Display only — see [foragingAreas].
     *
     * On by default. At a realistic radius the individual observation pins overlap into a pile
     * that can't be read, and the clustered areas are the answer to the question the map is
     * being asked ("where should I go"), so grouping is the starting view and switching this off
     * is how you drop back to the raw observations. The clustering itself is unconditional and
     * unchanged either way — this flag only decides whether the layer is drawn.
     */
    val showForagingAreas: Boolean = true,
    /**
     * Clustering of [sightings], recomputed whenever they change. Null before any sightings have
     * been loaded; a [ForagingAreas.None] once they have but nothing clustered, which the UI
     * must render as an explicit message rather than an empty layer.
     */
    val foragingAreas: ForagingAreas? = null,
    val taxonFilter: TaxonFilter = TaxonFilter.FUNGI,
    val taxonSearchQuery: String = "",
    val taxonSearchResults: List<TaxonSearchResult> = emptyList(),
    val isSearchingTaxa: Boolean = false,
    val taxonSearchErrorMessage: String? = null,
    /**
     * The query text behind [taxonSearchResults] at the moment a result was last picked — kept
     * around after [taxonSearchQuery] itself is cleared back to blank on selection, so tapping the
     * summary strip can restore and re-search it without the user retyping. See
     * [AvailabilityViewModel.onReopenTaxonSuggestions][com.forager.app.ui.availability.AvailabilityViewModel.onReopenTaxonSuggestions].
     */
    val lastTaxonSearchQuery: String = "",
    val conditions: ConditionsSummary? = null,
    val isLoadingConditions: Boolean = false,
    val conditionsErrorMessage: String? = null,
    /**
     * Which group's weather guidance text applies to [taxonFilter], carried alongside it because
     * [TaxonFilter] alone cannot answer that — see [ForagingSelection]'s doc comment.
     */
    val foragingSelection: ForagingSelection = ForagingSelection.forChip(TaxonFilter.FUNGI),
    val tripWindowReport: TripWindowReport? = null,
    val isLoadingTripWindows: Boolean = false,
    val tripWindowsErrorMessage: String? = null,
    /**
     * Trips the user has placed on the map for a future date, independent of any region search —
     * these are absolute map points, not tied to species/category/search state. Sorted by
     * [GetPlannedTripsUseCase][com.forager.app.domain.GetPlannedTripsUseCase] with any dated today
     * promoted to the front.
     */
    val plannedTrips: List<PlannedTrip> = emptyList(),
    val plannedTripsErrorMessage: String? = null,
    /**
     * The Seasonal tab's test of [com.forager.app.domain.FruitingPatternAssumptions.FRUITING_LAG_DAYS]
     * against real historical sightings and rainfall for the current search — see
     * [com.forager.app.domain.GetSeasonalPatternUseCase]. Fetched lazily on tab open, keyed on
     * region+month+filter, the same pattern [sightings] already uses for the Map tab.
     */
    val seasonalPattern: FruitingLagDistribution? = null,
    val isLoadingSeasonalPattern: Boolean = false,
    val seasonalPatternErrorMessage: String? = null,
    /**
     * Whether [forecast] came out of the offline cache rather than off the network.
     *
     * The List tab must say so out loud when this is true — CLAUDE.md: a fallback result is
     * reported as a fallback, never rendered identically to a live one. See
     * [AvailabilitySearchResult][com.forager.app.domain.AvailabilitySearchResult], the type this
     * and [cachedResultsAsOfEpochMillis] are set from together.
     */
    val isShowingCachedResults: Boolean = false,
    /**
     * When the cached [forecast] was originally fetched, for the banner's "saved 3 hours ago".
     *
     * Non-null whenever [isShowingCachedResults] is true, because both are written from the same
     * `Cached` result in one update. The screen still handles the impossible combination rather
     * than asserting it away, since a banner that claims an age it doesn't have would be the exact
     * dishonesty the banner exists to prevent.
     */
    val cachedResultsAsOfEpochMillis: Long? = null,
    /**
     * The offline cache's recent searches, most recently used first, for the drawer's picker.
     * Independent of the current search — like [plannedTrips], it is loaded once at start-up and
     * refreshed after each search rather than being derived from the search in progress.
     */
    val recentSearches: List<CachedSearchSummary> = emptyList(),
    /**
     * The standalone region picker in the "Offline Maps" submenu — independent of [region], per
     * this project's own decision: a downloaded region has nothing to do with whatever's currently
     * searched in the List/Map tabs. Set by long-pressing the picker map there (see
     * `OfflineMapsPanel` in `AvailabilityScreen.kt`), not by typing — `String`, same representation
     * [manualLatText]/[manualLngText] use, rather than a nullable `Double`, so "nothing picked yet"
     * and "picked" are both representable without a separate flag.
     */
    val offlineMapLatText: String = "",
    val offlineMapLngText: String = "",
    val offlineMapRadiusKm: Int = 15,
    val offlineMapStatus: OfflineMapStatus = OfflineMapStatus.NotDownloaded,
) {
    val hasSearched: Boolean get() = region != null
}
