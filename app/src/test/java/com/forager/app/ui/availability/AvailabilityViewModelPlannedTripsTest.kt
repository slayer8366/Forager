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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The planned-trips wiring, end to end: the real [AvailabilityViewModel] over a hand-written
 * in-memory [PlannedTripRepository] fake, driven through its real public callbacks — the same
 * "real ViewModel over fakes" style as [AvailabilityViewModelFilterTest].
 */
private class PlannedTripsInMemoryRepository : PlannedTripRepository {
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

private object PlannedTripsUnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult =
        error("getCurrentLocation() is not part of this test's path and must not be called")
}

private object PlannedTripsEmptyRepository : MushroomRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object PlannedTripsStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object PlannedTripsStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

private object PlannedTripsStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

/** Not exercised by this test's assertions; getStatus() succeeds with "nothing downloaded" since it runs on every ViewModel init. */
private object PlannedTripsStubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

private object PlannedTripsStubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(60)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
}

class AvailabilityViewModelPlannedTripsTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /**
     * The offline search cache is not what this file is about. An empty in-memory one keeps the
     * ViewModel's new dependency real — every search still writes through it — without changing
     * anything these tests assert; the cache's own behaviour is covered by
     * `RoomSearchCacheRepositoryTest` and `GetAvailabilityUseCaseTest`.
     */
    private val searchCache = InMemorySearchCacheRepository()

    private fun viewModel(repository: PlannedTripRepository): AvailabilityViewModel = AvailabilityViewModel(
        locationProvider = PlannedTripsUnusedLocationProvider,
        getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(PlannedTripsEmptyRepository), searchCache),
        getRecentSearches = GetRecentSearchesUseCase(searchCache),
        getSightings = GetSightingsUseCase(PlannedTripsEmptyRepository),
        searchTaxa = SearchTaxaUseCase(PlannedTripsEmptyRepository),
        getConditions = GetConditionsUseCase(PlannedTripsStubWeatherProvider),
        clusterForagingAreas = ClusterForagingAreasUseCase(),
        getTripWindows = GetTripWindowsUseCase(PlannedTripsStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
        getPlannedTrips = GetPlannedTripsUseCase(repository),
        savePlannedTrip = SavePlannedTripUseCase(repository),
        deletePlannedTrip = DeletePlannedTripUseCase(repository),
        getSeasonalPattern = GetSeasonalPatternUseCase(
            GetSightingsUseCase(PlannedTripsEmptyRepository),
            PlannedTripsStubHistoricalWeatherProvider,
            ComputeFruitingLagDistributionUseCase(),
        ),
        offlineMapRepository = PlannedTripsStubOfflineMapRepository,
        mapPreferencesRepository = PlannedTripsStubMapPreferencesRepository,
    )

    @Test
    fun `previously saved trips are loaded into state on creation`() = runTest(dispatcher) {
        val repository = PlannedTripsInMemoryRepository()
        repository.save(PlannedTrip(id = "existing", name = "Existing Trip", location = LatLng(45.0, -122.0), date = LocalDate.now()))

        val vm = viewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf("existing"), vm.uiState.value.plannedTrips.map { it.id })
    }

    @Test
    fun `placing a trip pin saves it and it appears in state`() = runTest(dispatcher) {
        val vm = viewModel(PlannedTripsInMemoryRepository())
        val location = LatLng(45.4, -122.7)
        val date = LocalDate.now().plusDays(3)

        vm.onPlaceTripPin(location, date, "Trip 1")
        advanceUntilIdle()

        val trip = vm.uiState.value.plannedTrips.single()
        assertEquals(location, trip.location)
        assertEquals(date, trip.date)
        assertEquals("Trip 1", trip.name)
        assertEquals(null, vm.uiState.value.plannedTripsErrorMessage)
    }

    @Test
    fun `deleting a planned trip removes it from state`() = runTest(dispatcher) {
        val vm = viewModel(PlannedTripsInMemoryRepository())
        vm.onPlaceTripPin(LatLng(45.4, -122.7), LocalDate.now(), "Trip 1")
        advanceUntilIdle()
        val id = vm.uiState.value.plannedTrips.single().id

        vm.onDeletePlannedTrip(id)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.plannedTrips.isEmpty())
    }

    @Test
    fun `a trip placed for today is promoted to the front alongside an earlier future one`() = runTest(dispatcher) {
        val vm = viewModel(PlannedTripsInMemoryRepository())
        val today = LocalDate.now()

        vm.onPlaceTripPin(LatLng(45.1, -122.1), today.plusDays(1), "Trip 1")
        advanceUntilIdle()
        vm.onPlaceTripPin(LatLng(45.2, -122.2), today, "Trip 2")
        advanceUntilIdle()

        val first = vm.uiState.value.plannedTrips.first()
        assertEquals(today, first.date)
    }
}
