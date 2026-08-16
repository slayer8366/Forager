package com.forager.app.ui.availability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forager.app.domain.GetSightingsUseCase
import com.forager.app.domain.LocationProvider
import com.forager.app.domain.LocationResult
import com.forager.app.domain.PredictAvailabilityUseCase
import com.forager.app.domain.SearchTaxaUseCase
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AvailabilityViewModel(
    private val locationProvider: LocationProvider,
    private val predictAvailability: PredictAvailabilityUseCase,
    private val getSightings: GetSightingsUseCase,
    private val searchTaxa: SearchTaxaUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AvailabilityUiState())
    val uiState: StateFlow<AvailabilityUiState> = _uiState.asStateFlow()

    /** The region+month+filter the current [AvailabilityUiState.sightings] were fetched for, or null if none fetched yet. */
    private var loadedSightingsQuery: Triple<Region, Int, TaxonFilter>? = null
    private var taxonSearchJob: Job? = null

    fun onRadiusChanged(radiusKm: Int) {
        _uiState.update { it.copy(radiusKm = Region.clampRadiusKm(radiusKm)) }
    }

    fun onMonthSelected(month: Int) {
        _uiState.update { it.copy(selectedMonth = month) }
        _uiState.value.region?.let { refresh(it, month, _uiState.value.taxonFilter) }
    }

    fun onCategorySelected(category: TaxonFilter) {
        _uiState.update { it.copy(taxonFilter = category, taxonSearchQuery = "", taxonSearchResults = emptyList()) }
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

    fun onTaxonSearchResultSelected(result: TaxonSearchResult) {
        val filter = result.toFilter()
        _uiState.update {
            it.copy(
                taxonFilter = filter,
                taxonSearchQuery = "",
                taxonSearchResults = emptyList(),
            )
        }
        _uiState.value.region?.let { refresh(it, _uiState.value.selectedMonth, filter) }
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
                onSuccess = { sightings ->
                    loadedSightingsQuery = query
                    _uiState.update { it.copy(isLoadingSightings = false, sightings = sightings) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingSightings = false,
                            sightingsErrorMessage = error.message ?: "Couldn't load sightings for the map.",
                        )
                    }
                },
            )
        }
    }

    private fun refresh(region: Region, month: Int, filter: TaxonFilter) {
        // A new search invalidates any sightings loaded for a previous region/month/filter.
        loadedSightingsQuery = null
        _uiState.update { it.copy(sightings = emptyList(), sightingsErrorMessage = null) }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            predictAvailability(region, month, filter).fold(
                onSuccess = { forecast -> _uiState.update { it.copy(isLoading = false, forecast = forecast) } },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Request failed. Check your connection and try again.")
                    }
                },
            )
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
