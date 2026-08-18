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
import com.forager.app.domain.MutableClock
import com.forager.app.domain.OfflineMapInfo
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.PlannedTripRepository
import com.forager.app.domain.PredictAvailabilityUseCase
import com.forager.app.domain.SavePlannedTripUseCase
import com.forager.app.domain.SearchTaxaUseCase
import com.forager.app.domain.TripPlanningWeatherProvider
import com.forager.app.domain.WeatherProvider
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.SightingsPage
import com.forager.app.domain.model.SpeciesObservationCount
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import com.forager.app.domain.model.WeatherSeries
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The offline cache as the ViewModel exposes it: which state fields a cached fallback sets, and
 * what tapping a recent search actually does.
 *
 * Driven through the real public callbacks — `searchManualCoordinates`, `onMonthSelected`,
 * `onRecentSearchSelected` — over the real [GetAvailabilityUseCase], the way a user reaches them,
 * rather than by calling `refresh` with hand-made arguments (CLAUDE.md: exercise behaviour through
 * its real entry point).
 */
private val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

private val COUNTS = listOf(
    SpeciesObservationCount(
        taxonId = 48473L,
        scientificName = "Ganoderma applanatum",
        commonName = "artist's bracket",
        rank = "species",
        observationCount = 14,
        photoUrl = null,
        wikipediaUrl = null,
    ),
)

/**
 * An iNaturalist stand-in whose species-counts call can be switched to failing, recording every
 * query it was asked — which is how "tapping a recent search re-runs *that* search" is checked
 * against what was actually requested rather than only against what state says.
 */
private class SwitchableMushroomRepository : MushroomRepository {
    var failure: Throwable? = null
    val speciesCountQueries = mutableListOf<Triple<Region, Int, TaxonFilter>>()

    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter): Result<List<SpeciesObservationCount>> {
        speciesCountQueries += Triple(region, month, filter)
        return failure?.let { Result.failure(it) } ?: Result.success(COUNTS)
    }

    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter): Result<SightingsPage> =
        Result.success(SightingsPage(sightings = emptyList(), totalResults = 0))

    override suspend fun searchTaxa(query: String): Result<List<TaxonSearchResult>> =
        Result.success(emptyList())
}

private object OfflineCacheUnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation() = LocationResult.LocationUnavailable
}

/** Not exercised by these assertions; failing is the honest stand-in for "not part of this test". */
private object OfflineCacheStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region): Result<ConditionsSummary> =
        Result.failure(UnsupportedOperationException("conditions are not exercised by this test"))
}

private object OfflineCacheStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows are not exercised by this test"))
}

private object OfflineCacheStubPlannedTripRepository : PlannedTripRepository {
    override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(emptyList())
    override suspend fun save(trip: PlannedTrip): Result<Unit> =
        Result.failure(UnsupportedOperationException("planned trips are not exercised by this test"))
    override suspend fun delete(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("planned trips are not exercised by this test"))
}

private object OfflineCacheStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

private object OfflineCacheStubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineMapInfo> =
        Result.failure(UnsupportedOperationException("offline maps are not exercised by this test"))
    override suspend fun delete(): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps are not exercised by this test"))
    override suspend fun getStatus(): Result<OfflineMapInfo?> = Result.success(null)
}

class AvailabilityViewModelOfflineCacheTest {

    private val dispatcher = StandardTestDispatcher()
    private val remote = SwitchableMushroomRepository()
    private val clock = MutableClock(now = 1_000L)
    private val cache = InMemorySearchCacheRepository(clock)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AvailabilityViewModel(
        locationProvider = OfflineCacheUnusedLocationProvider,
        getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(remote), cache),
        getRecentSearches = GetRecentSearchesUseCase(cache),
        getSightings = GetSightingsUseCase(remote),
        searchTaxa = SearchTaxaUseCase(remote),
        getConditions = GetConditionsUseCase(OfflineCacheStubWeatherProvider),
        clusterForagingAreas = ClusterForagingAreasUseCase(),
        getTripWindows = GetTripWindowsUseCase(OfflineCacheStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
        getPlannedTrips = GetPlannedTripsUseCase(OfflineCacheStubPlannedTripRepository),
        savePlannedTrip = SavePlannedTripUseCase(OfflineCacheStubPlannedTripRepository),
        deletePlannedTrip = DeletePlannedTripUseCase(OfflineCacheStubPlannedTripRepository),
        getSeasonalPattern = GetSeasonalPatternUseCase(
            GetSightingsUseCase(remote),
            OfflineCacheStubHistoricalWeatherProvider,
            ComputeFruitingLagDistributionUseCase(),
        ),
        offlineMapRepository = OfflineCacheStubOfflineMapRepository,
    )

    /** Drives the real coordinate-entry callbacks rather than reaching into state. */
    private fun AvailabilityViewModel.searchTestRegion() {
        onManualLatChanged(REGION.lat.toString())
        onManualLngChanged(REGION.lng.toString())
        searchManualCoordinates()
    }

    @Test
    fun `a live search leaves the cached-results flags off`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.searchTestRegion()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isShowingCachedResults)
        assertNull(state.cachedResultsAsOfEpochMillis)
        assertNotNull(state.forecast)
    }

    @Test
    fun `falling back to the cache sets both cached-results fields`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.searchTestRegion()
        advanceUntilIdle()
        val liveForecast = vm.uiState.value.forecast

        // The same search again, offline. The clock has moved on; the reported age must be when
        // the result was fetched, not now.
        clock.now = 60_000L
        remote.failure = IOException("Unable to resolve host api.inaturalist.org")
        vm.searchManualCoordinates()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isShowingCachedResults)
        assertEquals(1_000L, state.cachedResultsAsOfEpochMillis)
        assertEquals(liveForecast, state.forecast)
        // A cached answer is not an error: the error strip must not also be claiming the search
        // failed while the list is showing results.
        assertNull(state.errorMessage)
    }

    @Test
    fun `a failed search with nothing cached reports the failure and shows no cached-results banner state`() =
        runTest(dispatcher) {
            val vm = viewModel()
            remote.failure = IOException("Unable to resolve host api.inaturalist.org")

            vm.searchTestRegion()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertFalse(state.isShowingCachedResults)
            assertNull(state.cachedResultsAsOfEpochMillis)
            assertEquals("Unable to resolve host api.inaturalist.org", state.errorMessage)
        }

    /** Coming back online has to take the banner down again, not leave it stuck on. */
    @Test
    fun `a live search after a cached one clears the cached-results flags`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.searchTestRegion()
        advanceUntilIdle()
        remote.failure = IOException("offline")
        vm.searchManualCoordinates()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isShowingCachedResults)

        remote.failure = null
        vm.searchManualCoordinates()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isShowingCachedResults)
        assertNull(vm.uiState.value.cachedResultsAsOfEpochMillis)
    }

    @Test
    fun `a completed search appears in the recent-searches state`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.searchTestRegion()
        advanceUntilIdle()

        val recent = vm.uiState.value.recentSearches
        assertEquals(1, recent.size)
        assertEquals(REGION, recent.single().region)
        assertEquals(TaxonFilter.FUNGI, recent.single().filter)
        assertEquals(1_000L, recent.single().cachedAtEpochMillis)
    }

    @Test
    fun `selecting a recent search re-runs it with that entry's region, month and filter`() = runTest(dispatcher) {
        val vm = viewModel()
        // Two different searches, so the picked one is distinguishable from the current state.
        vm.searchTestRegion()
        advanceUntilIdle()
        vm.onCategorySelected(TaxonFilter.PLANTS)
        vm.onMonthSelected(11)
        advanceUntilIdle()

        val savedFungiSearch = vm.uiState.value.recentSearches.single { it.filter == TaxonFilter.FUNGI }
        remote.speciesCountQueries.clear()
        vm.onRecentSearchSelected(savedFungiSearch)
        advanceUntilIdle()

        // What was actually requested, not only what state says afterwards.
        assertEquals(
            listOf(Triple(savedFungiSearch.region, savedFungiSearch.month, TaxonFilter.FUNGI)),
            remote.speciesCountQueries.filter { it.third == TaxonFilter.FUNGI },
        )
        val state = vm.uiState.value
        assertEquals(savedFungiSearch.region, state.region)
        assertEquals(savedFungiSearch.month, state.selectedMonth)
        assertEquals(TaxonFilter.FUNGI, state.taxonFilter)
        assertEquals(savedFungiSearch.region.radiusKm, state.radiusKm)
        assertNotNull(state.forecast)
        assertEquals(TaxonFilter.FUNGI, state.forecast!!.filter)
    }

    /**
     * The picker deliberately has no cache-only path: a tap goes through the ordinary search, so
     * with a connection it returns fresh results and only falls back to the stored copy when the
     * live call fails. Both halves are asserted here because "it re-ran the search" and "it
     * replayed the saved copy" look identical in state otherwise.
     */
    @Test
    fun `selecting a recent search asks the network first and falls back only when that fails`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.searchTestRegion()
        advanceUntilIdle()
        val saved = vm.uiState.value.recentSearches.single()

        remote.speciesCountQueries.clear()
        vm.onRecentSearchSelected(saved)
        advanceUntilIdle()
        assertEquals(1, remote.speciesCountQueries.size)
        assertFalse(vm.uiState.value.isShowingCachedResults)

        remote.failure = IOException("offline")
        vm.onRecentSearchSelected(saved)
        advanceUntilIdle()

        assertEquals(2, remote.speciesCountQueries.size)
        assertTrue(vm.uiState.value.isShowingCachedResults)
    }
}
