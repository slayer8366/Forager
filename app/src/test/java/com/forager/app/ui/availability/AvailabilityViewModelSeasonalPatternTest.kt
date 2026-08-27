package com.forager.app.ui.availability

import com.forager.app.domain.ClusterForagingAreasUseCase
import com.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.forager.app.domain.ComputeTripWindowsUseCase
import com.forager.app.domain.DeletePlannedTripUseCase
import com.forager.app.domain.DistanceUnitPreferenceRepository
import com.forager.app.domain.GetAvailabilityUseCase
import com.forager.app.domain.GetConditionsUseCase
import com.forager.app.domain.GetPlannedTripsUseCase
import com.forager.app.domain.GetRecentSearchesUseCase
import com.forager.app.domain.GetSeasonalPatternUseCase
import com.forager.app.domain.GetSightingsUseCase
import com.forager.app.domain.GetTripWindowsUseCase
import com.forager.app.domain.HistoricalWeatherProvider
import com.forager.app.domain.InMemorySearchCacheRepository
import com.forager.app.domain.LocationFix
import com.forager.app.domain.LocationProvider
import com.forager.app.domain.LocationResult
import com.forager.app.domain.LocationTracker
import com.forager.app.domain.MushroomRepository
import com.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.forager.app.domain.MapPreferencesRepository
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.PlannedTripRepository
import com.forager.app.domain.PredictAvailabilityUseCase
import com.forager.app.domain.SavePlannedTripUseCase
import com.forager.app.domain.SearchTaxaUseCase
import com.forager.app.domain.TripPlanningWeatherProvider
import com.forager.app.domain.WeatherProvider
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.DistanceUnit
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Seasonal tab's lazy-fetch, keyed-caching wiring in [AvailabilityViewModel] — the same
 * pattern [onMapTabSelected] already has for sightings, exercised here through the real ViewModel
 * over fakes, per this project's "real ViewModel over fakes" style
 * ([AvailabilityViewModelFilterTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AvailabilityViewModelSeasonalPatternTest {

    private val dispatcher = StandardTestDispatcher()
    private val region = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Records the filter every getSightings call carried, and returns a fixed dated sighting so a fetch actually happens. */
    private class RecordingRepository : MushroomRepository {
        val filtersSeen = mutableListOf<TaxonFilter>()
        var callCount = 0

        override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
            Result.success(emptyList<SpeciesObservationCount>())

        override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter): Result<SightingsPage> {
            filtersSeen.add(filter)
            callCount++
            return Result.success(
                SightingsPage(
                    sightings = listOf(
                        Sighting(
                            observationId = callCount.toLong(),
                            taxonId = 1L,
                            scientificName = "Species",
                            commonName = null,
                            lat = region.lat,
                            lng = region.lng,
                            observedOn = LocalDate.of(2025, 8, 1),
                            photoUrl = null,
                        ),
                    ),
                    totalResults = 1,
                ),
            )
        }

        override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
    }

    /** Records every call, so the test can assert lazy fetch and keyed caching by call count. */
    private class RecordingHistoricalWeatherProvider : HistoricalWeatherProvider {
        var callCount = 0
        override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> {
            callCount++
            return Result.success(emptyList())
        }
    }

    private class FailingHistoricalWeatherProvider(private val message: String) : HistoricalWeatherProvider {
        override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
            Result.failure(IllegalStateException(message))
    }

    private object UnusedLocationProvider : LocationProvider {
        override suspend fun getCurrentLocation(): LocationResult =
            error("getCurrentLocation() is not part of this test's path and must not be called")
    }

    private object NoOpLocationTracker : LocationTracker {
        override val fixes: Flow<LocationFix> = emptyFlow()
    }

    private object StubWeatherProvider : WeatherProvider {
        override suspend fun getRecentPrecipitation(region: Region) =
            Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
    }

    private object StubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
        override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
            Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
    }

    private object StubPlannedTripRepository : PlannedTripRepository {
        override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(emptyList())
        override suspend fun save(trip: PlannedTrip): Result<Unit> = Result.failure(UnsupportedOperationException("not exercised"))
        override suspend fun delete(id: String): Result<Unit> = Result.failure(UnsupportedOperationException("not exercised"))
    }

    /** Not exercised by this test's assertions; listRegions() succeeds with an empty list since it runs on every ViewModel init. */
    private object StubOfflineMapRepository : OfflineMapRepository {
        override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
            Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
        override suspend fun deleteRegion(id: Long): Result<Unit> =
            Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
        override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
    }

    private object StubMapPreferencesRepository : MapPreferencesRepository {
        override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
        override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
        override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
        override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
        override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
        override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    }

    private object StubDistanceUnitPreferenceRepository : DistanceUnitPreferenceRepository {
        override suspend fun getDistanceUnit(): Result<DistanceUnit> = Result.success(DistanceUnit.MILES)
        override suspend fun setDistanceUnit(unit: DistanceUnit): Result<Unit> = Result.success(Unit)
    }

    private fun viewModel(
        repository: MushroomRepository,
        historicalWeatherProvider: HistoricalWeatherProvider,
    ): AvailabilityViewModel {
        val searchCache = InMemorySearchCacheRepository()
        return AvailabilityViewModel(
            locationProvider = UnusedLocationProvider,
            locationTracker = NoOpLocationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(repository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(repository),
            searchTaxa = SearchTaxaUseCase(repository),
            getConditions = GetConditionsUseCase(StubWeatherProvider),
            clusterForagingAreas = ClusterForagingAreasUseCase(),
            getTripWindows = GetTripWindowsUseCase(StubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(StubPlannedTripRepository),
            savePlannedTrip = SavePlannedTripUseCase(StubPlannedTripRepository),
            deletePlannedTrip = DeletePlannedTripUseCase(StubPlannedTripRepository),
            getSeasonalPattern = GetSeasonalPatternUseCase(
                GetSightingsUseCase(repository),
                historicalWeatherProvider,
                ComputeFruitingLagDistributionUseCase(),
            ),
            offlineMapRepository = StubOfflineMapRepository,
            mapPreferencesRepository = StubMapPreferencesRepository,
            distanceUnitPreferenceRepository = StubDistanceUnitPreferenceRepository,
        )
    }

    private fun AvailabilityViewModel.searchReferenceRegion() {
        onManualLatChanged(region.lat.toString())
        onManualLngChanged(region.lng.toString())
        searchManualCoordinates()
    }

    @Test
    fun `nothing is fetched until the Seasonal tab is actually opened`() = runTest(dispatcher) {
        val weatherProvider = RecordingHistoricalWeatherProvider()
        val vm = viewModel(RecordingRepository(), weatherProvider)

        vm.searchReferenceRegion()
        advanceUntilIdle()

        assertEquals(0, weatherProvider.callCount)
        assertNull(vm.uiState.value.seasonalPattern)
    }

    @Test
    fun `opening the Seasonal tab after a search fetches and populates the distribution`() = runTest(dispatcher) {
        val weatherProvider = RecordingHistoricalWeatherProvider()
        val vm = viewModel(RecordingRepository(), weatherProvider)
        vm.searchReferenceRegion()
        advanceUntilIdle()

        vm.onSeasonalTabSelected()
        advanceUntilIdle()

        assertEquals(1, weatherProvider.callCount)
        assertTrue(vm.uiState.value.seasonalPattern != null)
        assertEquals(false, vm.uiState.value.isLoadingSeasonalPattern)
        assertNull(vm.uiState.value.seasonalPatternErrorMessage)
    }

    @Test
    fun `opening the Seasonal tab before any region is chosen is a no-op`() = runTest(dispatcher) {
        val weatherProvider = RecordingHistoricalWeatherProvider()
        val vm = viewModel(RecordingRepository(), weatherProvider)

        vm.onSeasonalTabSelected()
        advanceUntilIdle()

        assertEquals(0, weatherProvider.callCount)
    }

    @Test
    fun `revisiting the Seasonal tab without changing the search does not refetch`() = runTest(dispatcher) {
        val weatherProvider = RecordingHistoricalWeatherProvider()
        val vm = viewModel(RecordingRepository(), weatherProvider)
        vm.searchReferenceRegion()
        advanceUntilIdle()

        vm.onSeasonalTabSelected()
        advanceUntilIdle()
        vm.onSeasonalTabSelected()
        advanceUntilIdle()

        assertEquals(1, weatherProvider.callCount)
    }

    @Test
    fun `changing the category invalidates the cached seasonal pattern and refetches on next open`() = runTest(dispatcher) {
        val weatherProvider = RecordingHistoricalWeatherProvider()
        val vm = viewModel(RecordingRepository(), weatherProvider)
        vm.searchReferenceRegion()
        advanceUntilIdle()
        vm.onSeasonalTabSelected()
        advanceUntilIdle()
        assertEquals(1, weatherProvider.callCount)

        vm.onCategorySelected(TaxonFilter.PLANTS)
        advanceUntilIdle()

        // The stale distribution from the previous filter must not linger on screen mid-refetch.
        assertNull(vm.uiState.value.seasonalPattern)

        vm.onSeasonalTabSelected()
        advanceUntilIdle()

        assertEquals(2, weatherProvider.callCount)
    }

    @Test
    fun `an IconicCategory filter and a SpecificTaxon filter both reach the seasonal fetch unchanged`() = runTest(dispatcher) {
        val repository = RecordingRepository()
        val vm = viewModel(repository, RecordingHistoricalWeatherProvider())
        vm.searchReferenceRegion()
        advanceUntilIdle()

        // Default filter is Fungi, an IconicCategory: pools every matched species.
        vm.onSeasonalTabSelected()
        advanceUntilIdle()

        // Lichens is a SpecificTaxon: scopes to one taxon. No new picker UI — the existing
        // category chip is what drives this, the same as the List and Map tabs.
        vm.onCategorySelected(TaxonFilter.LICHENS)
        advanceUntilIdle()
        vm.onSeasonalTabSelected()
        advanceUntilIdle()

        assertTrue(repository.filtersSeen.any { it is TaxonFilter.IconicCategory && it == TaxonFilter.FUNGI })
        assertTrue(repository.filtersSeen.any { it is TaxonFilter.SpecificTaxon && it == TaxonFilter.LICHENS })
    }

    @Test
    fun `a seasonal pattern fetch failure is reported, not swallowed, without the provider's own message`() = runTest(dispatcher) {
        val vm = viewModel(RecordingRepository(), FailingHistoricalWeatherProvider("archive api down"))
        vm.searchReferenceRegion()
        advanceUntilIdle()

        vm.onSeasonalTabSelected()
        advanceUntilIdle()

        assertNull(vm.uiState.value.seasonalPattern)
        assertEquals(false, vm.uiState.value.isLoadingSeasonalPattern)
        // Error-presentation spec's absolute rule: "archive api down" must never reach state,
        // however recognizable it is.
        assertEquals("Couldn't load the seasonal pattern.", vm.uiState.value.seasonalPatternErrorMessage)
    }
}
