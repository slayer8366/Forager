package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.zynergylabs.forager.app.domain.ComputeTripWindowsUseCase
import com.zynergylabs.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.zynergylabs.forager.app.domain.DeletePlannedTripUseCase
import com.zynergylabs.forager.app.domain.AppThemePreferenceRepository
import com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository
import com.zynergylabs.forager.app.domain.GetAvailabilityUseCase
import com.zynergylabs.forager.app.domain.GetConditionsUseCase
import com.zynergylabs.forager.app.domain.GetPlannedTripsUseCase
import com.zynergylabs.forager.app.domain.GetRecentSearchesUseCase
import com.zynergylabs.forager.app.domain.GetSeasonalPatternUseCase
import com.zynergylabs.forager.app.domain.GetSightingsUseCase
import com.zynergylabs.forager.app.domain.GetTodaysForecastUseCase
import com.zynergylabs.forager.app.domain.GetTripWindowsUseCase
import com.zynergylabs.forager.app.domain.HistoricalWeatherProvider
import com.zynergylabs.forager.app.domain.InMemorySearchCacheRepository
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationProvider
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.MapPreferencesRepository
import com.zynergylabs.forager.app.domain.MushroomRepository
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.PlannedTripRepository
import com.zynergylabs.forager.app.domain.PredictAvailabilityUseCase
import com.zynergylabs.forager.app.domain.SavePlannedTripUseCase
import com.zynergylabs.forager.app.domain.SearchTaxaUseCase
import com.zynergylabs.forager.app.domain.TaxonSearchRepository
import com.zynergylabs.forager.app.domain.TripPlanningWeatherProvider
import com.zynergylabs.forager.app.domain.WeatherProvider
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.ConditionsSummary
import com.zynergylabs.forager.app.domain.model.DailyWeather
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.SightingsPage
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.domain.model.WeatherSeries
import java.io.IOException
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routedata.RouteDataMissingReason
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import java.time.LocalDate
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private object RoutesNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object RoutesNoLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult = LocationResult.LocationUnavailable
}

private object RoutesEmptyRepository : MushroomRepository, TaxonSearchRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object RoutesStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

private object RoutesStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object RoutesStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

private object RoutesStubPlannedTripRepository : PlannedTripRepository {
    override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(emptyList())
    override suspend fun save(trip: PlannedTrip): Result<Unit> =
        Result.failure(UnsupportedOperationException("planned trips not exercised by this test"))
    override suspend fun delete(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("planned trips not exercised by this test"))
}

/** Always Idle/absent — this ViewModel's own preferences-load failure path is log-only (see [AvailabilityViewModel.loadOfflineMapPreferences]), nothing here exercises it. */
private object RoutesStubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getMapFullscreen(): Result<Boolean> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
}

private object RoutesStubUnitSystemPreferenceRepository : UnitSystemPreferenceRepository {
    override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(UnitSystem.IMPERIAL)
    override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
}

private object RoutesStubAppThemePreferenceRepository : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}


private fun summary(id: Long, routeData: RegionRouteData) = OfflineRegionSummary(
    id = id, name = "Region $id", region = Region(45.3, -122.6, 8), minZoom = OfflineMapRepository.MIN_ZOOM,
    maxZoom = OfflineMapRepository.MAX_ZOOM, tileCount = 100, sizeBytes = 1_000L, createdAtEpochMillis = 1_000L, routeData = routeData,
)

/** Regions as listed, and what each route fetch answers; every fetch is recorded. A fetch updates the listing, as the real one's storage does. */
private class RoutesRepository(regions: List<OfflineRegionSummary>) : OfflineMapRepository {
    var regions = regions
    val fetched = mutableListOf<Long>()
    var answer: (Long) -> Result<RegionRouteData> = { Result.success(RegionRouteData.Stored("run", "t", 10L, File("/r/$it"))) }
    var gate: CompletableDeferred<Unit>? = null
    var downloadResult: Result<OfflineRegionSummary> = Result.failure(IllegalStateException("not configured"))
    var listCalls = 0

    override suspend fun download(name: String, region: Region, onProgress: (downloaded: Int, total: Int) -> Unit): Result<OfflineRegionSummary> {
        downloadResult.onSuccess { regions = regions + it }
        return downloadResult
    }
    override suspend fun deleteRegion(id: Long): Result<Unit> = Result.success(Unit)
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> { listCalls++; return Result.success(regions) }
    override suspend fun fetchRoutes(id: Long): Result<RegionRouteData> {
        fetched += id
        gate?.await()
        val result = answer(id)
        result.onSuccess { data -> regions = regions.map { if (it.id == id) it.copy(routeData = data) else it } }
        return result
    }
}

/**
 * The automatic route fetch and "Try again" (dispatch 2026-10-23-03; the owner: "Automatic, button as backup
 * (Recommended)"), through the real [AvailabilityViewModel]'s entry points: opening Offline maps, Try again, Download.
 */
class AvailabilityViewModelOfflineRoutesTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val searchCache = InMemorySearchCacheRepository()

    private fun viewModel(
        offlineMapRepository: OfflineMapRepository,
        mapPreferencesRepository: MapPreferencesRepository = RoutesStubMapPreferencesRepository,
        getOfflineRegionReferenceCount: suspend (Long) -> Int = { 0 },
        pendingDeleteCommitScope: CoroutineScope? = null,
        noSignal: () -> Boolean = { false },
    ): AvailabilityViewModel = AvailabilityViewModel(
        locationProvider = RoutesNoLocationProvider,
        locationTracker = RoutesNoOpLocationTracker,
        getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(RoutesEmptyRepository), searchCache),
        getRecentSearches = GetRecentSearchesUseCase(searchCache),
        getSightings = GetSightingsUseCase(RoutesEmptyRepository),
        searchTaxa = SearchTaxaUseCase(RoutesEmptyRepository),
        getConditions = GetConditionsUseCase(RoutesStubWeatherProvider),
        getTripWindows = GetTripWindowsUseCase(RoutesStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
        getPlannedTrips = GetPlannedTripsUseCase(RoutesStubPlannedTripRepository),
        savePlannedTrip = SavePlannedTripUseCase(RoutesStubPlannedTripRepository),
        deletePlannedTrip = DeletePlannedTripUseCase(RoutesStubPlannedTripRepository),
        getSeasonalPattern = GetSeasonalPatternUseCase(
            GetSightingsUseCase(RoutesEmptyRepository),
            RoutesStubHistoricalWeatherProvider,
            ComputeFruitingLagDistributionUseCase(),
        ),
        offlineMapRepository = offlineMapRepository,
        mapPreferencesRepository = mapPreferencesRepository,
        unitSystemPreferenceRepository = RoutesStubUnitSystemPreferenceRepository,
        appThemePreferenceRepository = RoutesStubAppThemePreferenceRepository,
        getTodaysForecast = GetTodaysForecastUseCase(RoutesStubTripPlanningWeatherProvider),
        getOfflineRegionReferenceCount = getOfflineRegionReferenceCount,
        pendingDeleteCommitScope = pendingDeleteCommitScope ?: com.zynergylabs.forager.app.ui.log.PendingDeleteCommitScope,
        noSignal = noSignal,
    )

    private val missingNetwork = RegionRouteData.Missing(RouteDataMissingReason.NETWORK)

    @Test
    fun `opening with signal fetches each region that wants routes once, and none that does not`() = runTest(dispatcher) {
        val repository = RoutesRepository(
            listOf(
                summary(1, RegionRouteData.NotFetched),
                summary(2, missingNetwork),
                summary(3, RegionRouteData.Stored("run", "t", 10L, File("/r/3"))),
                summary(4, RegionRouteData.NoneHere),
                summary(5, RegionRouteData.Missing(RouteDataMissingReason.FORBIDDEN)),
                summary(6, RegionRouteData.Damaged("gone")),
            ),
        )
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onOfflineMapsOpened()
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L, 6L), repository.fetched)
        assertTrue(vm.uiState.value.offlineRouteFailedIds.isEmpty())
        assertTrue(vm.uiState.value.offlineRouteFetchingIds.isEmpty())
        assertTrue("the rows read the stored routes back", vm.uiState.value.offlineRegions.first { it.id == 1L }.routeData is RegionRouteData.Stored)
    }

    @Test
    fun `opening with no signal fetches nothing`() = runTest(dispatcher) {
        val repository = RoutesRepository(listOf(summary(1, RegionRouteData.NotFetched)))
        val vm = viewModel(repository, noSignal = { true })
        advanceUntilIdle()

        vm.onOfflineMapsOpened()
        advanceUntilIdle()

        assertEquals(emptyList<Long>(), repository.fetched)
        assertTrue(vm.uiState.value.offlineRouteFailedIds.isEmpty())
    }

    @Test
    fun `a failed automatic attempt is tried once per opening, not looped, and then offers Try again`() = runTest(dispatcher) {
        val repository = RoutesRepository(listOf(summary(1, RegionRouteData.NotFetched)))
        repository.answer = { Result.success(missingNetwork) }
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onOfflineMapsOpened()
        advanceUntilIdle()
        assertEquals(listOf(1L), repository.fetched)
        assertEquals(setOf(1L), vm.uiState.value.offlineRouteFailedIds)

        vm.onOfflineMapsOpened()
        advanceUntilIdle()
        assertEquals("one more attempt for the second opening", listOf(1L, 1L), repository.fetched)
    }

    @Test
    fun `a fetch that fails outright is logged and offers Try again`() = runTest(dispatcher) {
        val repository = RoutesRepository(listOf(summary(1, RegionRouteData.NotFetched)))
        repository.answer = { Result.failure(java.io.IOException("region 1 is not downloaded here")) }
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onOfflineMapsOpened()
        advanceUntilIdle()

        assertEquals(setOf(1L), vm.uiState.value.offlineRouteFailedIds)
    }

    @Test
    fun `Try again fetches that region, and success takes the button away`() = runTest(dispatcher) {
        val repository = RoutesRepository(listOf(summary(1, missingNetwork)))
        repository.answer = { Result.success(missingNetwork) }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOfflineMapsOpened()
        advanceUntilIdle()
        assertEquals(setOf(1L), vm.uiState.value.offlineRouteFailedIds)

        repository.answer = { Result.success(RegionRouteData.Stored("run", "t", 10L, File("/r/1"))) }
        vm.onTryAgainRoutes(1L)
        advanceUntilIdle()

        assertEquals(listOf(1L, 1L), repository.fetched)
        assertTrue(vm.uiState.value.offlineRouteFailedIds.isEmpty())
        assertTrue(vm.uiState.value.offlineRegions.single().routeData is RegionRouteData.Stored)
    }

    @Test
    fun `a region being fetched shows as fetching and is not fetched twice`() = runTest(dispatcher) {
        val repository = RoutesRepository(listOf(summary(1, RegionRouteData.NotFetched)))
        repository.gate = CompletableDeferred()
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onOfflineMapsOpened()
        advanceUntilIdle()
        assertEquals(setOf(1L), vm.uiState.value.offlineRouteFetchingIds)
        vm.onTryAgainRoutes(1L)
        vm.onOfflineMapsOpened()
        advanceUntilIdle()
        repository.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(1L), repository.fetched)
        assertTrue(vm.uiState.value.offlineRouteFetchingIds.isEmpty())
    }

    @Test
    fun `a download whose routes failed offers Try again at once`() = runTest(dispatcher) {
        val repository = RoutesRepository(emptyList())
        repository.downloadResult = Result.success(summary(9, missingNetwork))
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onOfflineMapLatChanged("45.3")
        vm.onOfflineMapLngChanged("-122.6")
        vm.onOfflineMapRadiusChanged(8)
        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        assertEquals(setOf(9L), vm.uiState.value.offlineRouteFailedIds)
        assertEquals(emptyList<Long>(), repository.fetched)
    }

    @Test
    fun `a download whose routes arrived offers nothing`() = runTest(dispatcher) {
        val repository = RoutesRepository(emptyList())
        repository.downloadResult = Result.success(summary(9, RegionRouteData.Stored("run", "t", 10L, File("/r/9"))))
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onOfflineMapLatChanged("45.3")
        vm.onOfflineMapLngChanged("-122.6")
        vm.onOfflineMapRadiusChanged(8)
        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.offlineRouteFailedIds.isEmpty())
    }
}
