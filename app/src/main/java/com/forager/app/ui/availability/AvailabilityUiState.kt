package com.forager.app.ui.availability

import com.forager.app.domain.ForagingSelection
import com.forager.app.domain.model.AvailabilityForecast
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.ForagingAreas
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
     * The standalone region picker in Settings' "Offline Maps" section — independent of [region],
     * per the task this was built from: a downloaded region has nothing to do with whatever's
     * currently searched in the List/Map tabs. Text fields for the same reason [manualLatText]/
     * [manualLngText] are: they need to hold whatever the user is mid-typing, including invalid or
     * incomplete input, which a `Double` cannot represent.
     */
    val offlineMapLatText: String = "",
    val offlineMapLngText: String = "",
    val offlineMapRadiusKm: Int = 15,
    val offlineMapStatus: OfflineMapStatus = OfflineMapStatus.NotDownloaded,
) {
    val hasSearched: Boolean get() = region != null
}
