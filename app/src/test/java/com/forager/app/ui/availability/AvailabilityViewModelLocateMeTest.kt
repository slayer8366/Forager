package com.forager.app.ui.availability

import com.forager.app.domain.ClusterForagingAreasUseCase
import com.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.forager.app.domain.ComputeTripWindowsUseCase
import com.forager.app.domain.DeletePlannedTripUseCase
import com.forager.app.domain.GetAvailabilityUseCase
import com.forager.app.domain.GetConditionsUseCase
import com.forager.app.domain.GetPlannedTripsUseCase
import com.forager.app.domain.GetRecentSearchesUseCase
import com.forager.app.domain.GetSeasonalPatternUseCase
import com.forager.app.domain.GetSightingsUseCase
import com.forager.app.domain.GetTripWindowsUseCase
import com.forager.app.domain.HistoricalWeatherProvider
import com.forager.app.domain.InMemorySearchCacheRepository
import com.forager.app.domain.LocationProvider
import com.forager.app.domain.LocationResult
import com.forager.app.domain.MushroomRepository
import com.forager.app.domain.MapPreferencesRepository
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.PlannedTripRepository
import com.forager.app.domain.PredictAvailabilityUseCase
import com.forager.app.domain.SavePlannedTripUseCase
import com.forager.app.domain.SearchTaxaUseCase
import com.forager.app.domain.TripPlanningWeatherProvider
import com.forager.app.domain.WeatherProvider
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.SightingsPage
import com.forager.app.domain.model.SpeciesObservationCount
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import com.forager.app.domain.model.WeatherSeries
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Headless cover of [AvailabilityViewModel.locateMe] and the [LocationResult.altitude] field it
 * reads — the map redesign's GPS/locate-me button, decisions #3.2 and #8 in
 * `docs/plans/map-redesign.md`. No Robolectric needed: the ViewModel is Android-framework-free
 * apart from `viewModelScope`, which `Dispatchers.setMain` is enough to satisfy — mirrors
 * [AvailabilityViewModelFilterTest].
 */
class AvailabilityViewModelLocateMeTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val searchCache = InMemorySearchCacheRepository()

    private fun viewModel(locationProvider: LocationProvider): AvailabilityViewModel {
        val plannedTripRepository = LocateMeInMemoryPlannedTripRepository()
        return AvailabilityViewModel(
            locationProvider = locationProvider,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(LocateMeEmptyRepository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(LocateMeEmptyRepository),
            searchTaxa = SearchTaxaUseCase(LocateMeEmptyRepository),
            getConditions = GetConditionsUseCase(LocateMeStubWeatherProvider),
            clusterForagingAreas = ClusterForagingAreasUseCase(),
            getTripWindows = GetTripWindowsUseCase(LocateMeStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(plannedTripRepository),
            savePlannedTrip = SavePlannedTripUseCase(plannedTripRepository),
            deletePlannedTrip = DeletePlannedTripUseCase(plannedTripRepository),
            getSeasonalPattern = GetSeasonalPatternUseCase(
                GetSightingsUseCase(LocateMeEmptyRepository),
                LocateMeStubHistoricalWeatherProvider,
                ComputeFruitingLagDistributionUseCase(),
            ),
            offlineMapRepository = LocateMeStubOfflineMapRepository,
            mapPreferencesRepository = LocateMeStubMapPreferencesRepository,
        )
    }

    @Test
    fun `a fix with altitude reports Located carrying that altitude`() = runTest(dispatcher) {
        val vm = viewModel(LocateMeFixedLocationProvider(LocationResult.Success(45.5, -122.6, altitude = 123.4)))

        vm.locateMe()
        advanceUntilIdle()

        val status = vm.uiState.value.locateMeStatus
        assertEquals(LocateMeStatus.Located(LatLng(45.5, -122.6), 123.4), status)
    }

    @Test
    fun `a fix with no altitude (a network-based fix) reports Located with null altitude, never a guessed value`() = runTest(dispatcher) {
        val vm = viewModel(LocateMeFixedLocationProvider(LocationResult.Success(45.5, -122.6, altitude = null)))

        vm.locateMe()
        advanceUntilIdle()

        val status = vm.uiState.value.locateMeStatus as LocateMeStatus.Located
        assertNull(status.altitude)
    }

    @Test
    fun `permission denied reports LocateMeStatus PermissionDenied, distinct from the search-region control's flag`() = runTest(dispatcher) {
        val vm = viewModel(LocateMeFixedLocationProvider(LocationResult.PermissionDenied))

        vm.locateMe()
        advanceUntilIdle()

        assertEquals(LocateMeStatus.PermissionDenied, vm.uiState.value.locateMeStatus)
        // The unrelated "use current location for search region" flag must be untouched.
        assertEquals(false, vm.uiState.value.locationPermissionDenied)
    }

    @Test
    fun `location unavailable reports LocateMeStatus Unavailable, not a silent fallback`() = runTest(dispatcher) {
        val vm = viewModel(LocateMeFixedLocationProvider(LocationResult.LocationUnavailable))

        vm.locateMe()
        advanceUntilIdle()

        assertEquals(LocateMeStatus.Unavailable, vm.uiState.value.locateMeStatus)
    }

    @Test
    fun `locateMe never touches the search region, unlike useCurrentLocation`() = runTest(dispatcher) {
        val vm = viewModel(LocateMeFixedLocationProvider(LocationResult.Success(45.5, -122.6, altitude = 10.0)))

        vm.locateMe()
        advanceUntilIdle()

        assertNull("locateMe must not perform a search or set a region", vm.uiState.value.region)
    }

    @Test
    fun `an OS-level permission denial (before the provider is ever asked) also reports PermissionDenied`() {
        val vm = viewModel(LocateMeUnusedLocationProvider)

        vm.onLocateMePermissionDenied()

        assertEquals(LocateMeStatus.PermissionDenied, vm.uiState.value.locateMeStatus)
    }
}

private class LocateMeFixedLocationProvider(private val result: LocationResult) : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult = result
}

private object LocateMeUnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult =
        error("getCurrentLocation() is not part of this test's path and must not be called")
}

private object LocateMeEmptyRepository : MushroomRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object LocateMeStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object LocateMeStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

private object LocateMeStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

private class LocateMeInMemoryPlannedTripRepository : PlannedTripRepository {
    private val trips = mutableMapOf<String, PlannedTrip>()

    override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(trips.values.toList())
    override suspend fun save(trip: PlannedTrip): Result<Unit> {
        trips[trip.id] = trip
        return Result.success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        trips.remove(id)
        return Result.success(Unit)
    }
}

private object LocateMeStubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

private object LocateMeStubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(60)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
}
