package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.zynergylabs.forager.app.domain.ComputeTripWindowsUseCase
import com.zynergylabs.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.zynergylabs.forager.app.domain.DeletePlannedTripUseCase
import com.zynergylabs.forager.app.domain.AppThemePreferenceRepository
import com.zynergylabs.forager.app.domain.EntryReferenceCountSource
import com.zynergylabs.forager.app.domain.NoEntryReferenceCounts
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
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.mapdata.DropOffMapPass
import com.zynergylabs.forager.app.domain.mapdata.RegionDropOffMap
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

private object DropOffNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object DropOffNoLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult = LocationResult.LocationUnavailable
}

private object DropOffEmptyRepository : MushroomRepository, TaxonSearchRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object DropOffStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

private object DropOffStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object DropOffStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

private object DropOffStubPlannedTripRepository : PlannedTripRepository {
    override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(emptyList())
    override suspend fun save(trip: PlannedTrip): Result<Unit> =
        Result.failure(UnsupportedOperationException("planned trips not exercised by this test"))
    override suspend fun delete(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("planned trips not exercised by this test"))
}

/** Always Idle/absent — this ViewModel's own preferences-load failure path is log-only (see [AvailabilityViewModel.loadOfflineMapPreferences]), nothing here exercises it. */
private object DropOffStubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getMapFullscreen(): Result<Boolean> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
}

private object DropOffStubUnitSystemPreferenceRepository : UnitSystemPreferenceRepository {
    override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(UnitSystem.IMPERIAL)
    override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
}

private object DropOffStubAppThemePreferenceRepository : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}


private fun summary(id: Long, routeData: RegionRouteData = stored(id), dropOffMap: RegionDropOffMap = RegionDropOffMap.NONE, isDownloaded: Boolean = true) =
    OfflineRegionSummary(
        id = id, name = "Region $id", region = Region(40.1, -71.5, 1), minZoom = OfflineMapRepository.MIN_ZOOM,
        maxZoom = OfflineMapRepository.MAX_ZOOM, tileCount = if (isDownloaded) 100 else 0, sizeBytes = if (isDownloaded) 1_000L else 0L,
        createdAtEpochMillis = 1_000L, isDownloaded = isDownloaded, routeData = routeData, dropOffMap = dropOffMap,
    )

private fun stored(id: Long) = RegionRouteData.Stored("run", "t", 10L, File("/r/$id"))

private val WHOLE = RegionDropOffMap(tiles = 6, emptyTiles = 0, sizeBytes = 60_000L, whole = true)

/**
 * Regions as listed, and every drop-off map pass and route fetch asked for. A pass makes its area whole in the listing,
 * as the real copy's folder does once nothing is wanted.
 */
private class DropOffRepository(regions: List<OfflineRegionSummary>) : OfflineMapRepository {
    var regions = regions
    val passes = mutableListOf<Long>()
    val routeFetches = mutableListOf<Long>()
    var downloadResult: Result<OfflineRegionSummary> = Result.failure(IllegalStateException("not configured"))

    override suspend fun download(name: String, region: Region, onProgress: (downloaded: Int, total: Int) -> Unit): Result<OfflineRegionSummary> {
        downloadResult.onSuccess { regions = regions + it }
        return downloadResult
    }
    override suspend fun deleteRegion(id: Long): Result<Unit> = Result.success(Unit)
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(regions)
    override suspend fun fetchRoutes(id: Long): Result<RegionRouteData> {
        routeFetches += id
        return Result.success(stored(id))
    }
    override suspend fun fetchDropOffMap(id: Long): Result<DropOffMapPass> {
        passes += id
        regions = regions.map { if (it.id == id) it.copy(dropOffMap = WHOLE) else it }
        return Result.success(DropOffMapPass.Whole(requests = 6, stored = 6, empty = 0))
    }
}

/**
 * The drop-off map copy's automatic pass (dispatch 2026-11-04-09; the owner: "Automatic (Recommended)"), through the real
 * [AvailabilityViewModel]'s entry points: opening Offline maps, signal returning while it is open, "Try again" and a
 * download's completion (B1 P2). Its selection is its own: an area whose routes are stored, as both the S22's are, still
 * gets its pass (the Phase 1 report's wrong premise).
 */
class AvailabilityViewModelDropOffMapsTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val searchCache = InMemorySearchCacheRepository()
    private val logged = mutableListOf<String>()

    private fun viewModel(offlineMapRepository: OfflineMapRepository, noSignal: () -> Boolean = { false }): AvailabilityViewModel = AvailabilityViewModel(
        locationProvider = DropOffNoLocationProvider,
        locationTracker = DropOffNoOpLocationTracker,
        getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(DropOffEmptyRepository), searchCache),
        getRecentSearches = GetRecentSearchesUseCase(searchCache),
        getSightings = GetSightingsUseCase(DropOffEmptyRepository),
        searchTaxa = SearchTaxaUseCase(DropOffEmptyRepository),
        getConditions = GetConditionsUseCase(DropOffStubWeatherProvider),
        getTripWindows = GetTripWindowsUseCase(DropOffStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
        getPlannedTrips = GetPlannedTripsUseCase(DropOffStubPlannedTripRepository),
        savePlannedTrip = SavePlannedTripUseCase(DropOffStubPlannedTripRepository),
        deletePlannedTrip = DeletePlannedTripUseCase(DropOffStubPlannedTripRepository),
        getSeasonalPattern = GetSeasonalPatternUseCase(
            GetSightingsUseCase(DropOffEmptyRepository),
            DropOffStubHistoricalWeatherProvider,
            ComputeFruitingLagDistributionUseCase(),
        ),
        offlineMapRepository = offlineMapRepository,
        errorLog = ErrorLog { _, message, _ -> logged += message },
        mapPreferencesRepository = DropOffStubMapPreferencesRepository,
        unitSystemPreferenceRepository = DropOffStubUnitSystemPreferenceRepository,
        appThemePreferenceRepository = DropOffStubAppThemePreferenceRepository,
        getTodaysForecast = GetTodaysForecastUseCase(DropOffStubTripPlanningWeatherProvider),
        entryReferenceCounts = NoEntryReferenceCounts,
        noSignal = noSignal,
    )

    @Test
    fun `opening with signal runs one pass for each downloaded area not yet whole, routes stored or not`() = runTest(dispatcher) {
        // Regions 3 and 8 are as both the S22's areas are: routes stored, so the route fetch takes neither.
        val repository = DropOffRepository(
            listOf(summary(3), summary(8), summary(4, routeData = RegionRouteData.NotFetched), summary(5, dropOffMap = WHOLE)),
        )
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onOfflineMapsOpened()
        advanceUntilIdle()

        assertEquals(listOf(3L, 8L, 4L), repository.passes)
        assertEquals("the route fetch keeps its own selection", listOf(4L), repository.routeFetches)
        assertTrue("the list reads the copies back", vm.uiState.value.offlineRegions.all { it.dropOffMap.whole })
    }

    @Test
    fun `opening with no signal runs no pass, and says so once in the log`() = runTest(dispatcher) {
        val repository = DropOffRepository(listOf(summary(3)))
        val vm = viewModel(repository, noSignal = { true })
        advanceUntilIdle()

        vm.onOfflineMapsOpened()
        advanceUntilIdle()

        assertEquals(emptyList<Long>(), repository.passes)
        assertEquals(logged.toString(), 1, logged.count { "no signal" in it && "drop-off map" in it })
    }

    @Test
    fun `an area restored from a backup and not downloaded here gets no pass`() = runTest(dispatcher) {
        val repository = DropOffRepository(listOf(summary(3), summary(-2, isDownloaded = false)))
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onOfflineMapsOpened()
        advanceUntilIdle()

        assertEquals(listOf(3L), repository.passes)
    }

    @Test
    fun `a whole area gets no pass, so opening again asks for nothing`() = runTest(dispatcher) {
        val repository = DropOffRepository(listOf(summary(3)))
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOfflineMapsOpened()
        advanceUntilIdle()
        assertEquals(listOf(3L), repository.passes)

        vm.onOfflineMapsOpened()
        advanceUntilIdle()

        assertEquals(listOf(3L), repository.passes)
    }

    @Test
    fun `signal returning while Offline maps is open runs the passes`() = runTest(dispatcher) {
        var noSignal = true
        val repository = DropOffRepository(listOf(summary(3)))
        val vm = viewModel(repository, noSignal = { noSignal })
        advanceUntilIdle()
        vm.onOfflineMapsOpened()
        advanceUntilIdle()
        assertEquals(emptyList<Long>(), repository.passes)

        noSignal = false
        vm.onOfflineMapsSignalReturned()
        advanceUntilIdle()

        assertEquals(listOf(3L), repository.passes)
    }

    @Test
    fun `Try again on an area's routes runs that area's pass too`() = runTest(dispatcher) {
        val repository = DropOffRepository(listOf(summary(3, routeData = RegionRouteData.NotFetched), summary(8)))
        val vm = viewModel(repository, noSignal = { true })
        advanceUntilIdle()
        vm.onOfflineMapsOpened()
        advanceUntilIdle()

        vm.onTryAgainRoutes(3L)
        advanceUntilIdle()

        assertEquals(listOf(3L), repository.passes)
    }

    @Test
    fun `a download's completion runs the new area's pass`() = runTest(dispatcher) {
        val repository = DropOffRepository(emptyList())
        repository.downloadResult = Result.success(summary(9))
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onOfflineMapLatChanged("40.1")
        vm.onOfflineMapLngChanged("-71.5")
        vm.onOfflineMapRadiusChanged(1)
        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        assertEquals(listOf(9L), repository.passes)
    }

    @Test
    fun `a pass that cannot run is logged`() = runTest(dispatcher) {
        val repository = object : OfflineMapRepository by DropOffRepository(listOf(summary(3))) {
            override suspend fun fetchDropOffMap(id: Long): Result<DropOffMapPass> = Result.failure(IOException("region $id is not downloaded here"))
        }
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onOfflineMapsOpened()
        advanceUntilIdle()

        assertEquals(logged.toString(), 1, logged.count { "drop-off map" in it && "region 3" in it })
    }
}
