package com.forager.app.ui.availability

import com.forager.app.domain.model.AvailabilityForecast
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.ForagingAreas
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
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
    /** Whether the Map tab's foraging-areas layer is switched on. Display only — see [foragingAreas]. */
    val showForagingAreas: Boolean = false,
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
    val conditions: ConditionsSummary? = null,
    val isLoadingConditions: Boolean = false,
    val conditionsErrorMessage: String? = null,
) {
    val hasSearched: Boolean get() = region != null
}
