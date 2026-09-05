package com.forager.app.ui.availability

import com.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.forager.app.domain.ComputeTripWindowsUseCase
import com.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.forager.app.domain.DeletePlannedTripUseCase
import com.forager.app.domain.AppThemePreferenceRepository
import com.forager.app.domain.DistanceUnitPreferenceRepository
import com.forager.app.domain.GetAvailabilityUseCase
import com.forager.app.domain.GetConditionsUseCase
import com.forager.app.domain.GetPlannedTripsUseCase
import com.forager.app.domain.GetRecentSearchesUseCase
import com.forager.app.domain.GetSeasonalPatternUseCase
import com.forager.app.domain.GetSightingsUseCase
import com.forager.app.domain.GetTodaysForecastUseCase
import com.forager.app.domain.GetTripWindowsUseCase
import com.forager.app.domain.HistoricalWeatherProvider
import com.forager.app.domain.InMemorySearchCacheRepository
import com.forager.app.domain.LocationFix
import com.forager.app.domain.LocationProvider
import com.forager.app.domain.LocationResult
import com.forager.app.domain.LocationTracker
import com.forager.app.domain.MapPreferencesRepository
import com.forager.app.domain.MushroomRepository
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.PlannedTripRepository
import com.forager.app.domain.PredictAvailabilityUseCase
import com.forager.app.domain.SavePlannedTripUseCase
import com.forager.app.domain.SearchTaxaUseCase
import com.forager.app.domain.TaxonSearchRepository
import com.forager.app.domain.TripPlanningWeatherProvider
import com.forager.app.domain.WeatherProvider
import com.forager.app.domain.model.AppThemeMode
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
import java.io.IOException
import java.time.LocalDate
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

/**
 * Settings' "Offline Maps" state machine, end to end: the real [AvailabilityViewModel] over a
 * hand-written [OfflineMapRepository] fake this test fully controls — the same "real ViewModel
 * over fakes" style as [AvailabilityViewModelPlannedTripsTest] and [AvailabilityViewModelFilterTest].
 *
 * Always downloads from the one fixed source [OfflineMapRepository] hardcodes — there is no style
 * choice any more (see that interface's doc comment for why that parameter was removed and for the
 * current source's identity), so unlike an earlier revision of this file, nothing here asserts on
 * "which style" a download targeted.
 *
 * What this cannot cover: actual tile download/delete I/O and MapLibre `OfflineManager` behavior —
 * that's Android file and network I/O, unverifiable headlessly, and is
 * `com.forager.app.map.MapLibreOfflineMapRepository`'s own concern rather than this ViewModel's.
 * This file covers the loading → success/failure state machine the UI actually renders from.
 */
private val REFERENCE_REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

private val REFERENCE_REGION_SUMMARY = OfflineRegionSummary(
    id = 1L,
    name = "Chanterelle Ridge",
    region = REFERENCE_REGION,
    minZoom = OfflineMapRepository.MIN_ZOOM,
    maxZoom = OfflineMapRepository.MAX_ZOOM,
    tileCount = 234,
    sizeBytes = 4_200_000L,
    createdAtEpochMillis = 1_755_000_000_000L,
)

private object OfflineMapsNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object OfflineMapsUnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult =
        error("getCurrentLocation() is not part of this test's path and must not be called")
}

private object OfflineMapsEmptyRepository : MushroomRepository, TaxonSearchRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object OfflineMapsStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

private object OfflineMapsStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object OfflineMapsStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

private object OfflineMapsStubPlannedTripRepository : PlannedTripRepository {
    override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(emptyList())
    override suspend fun save(trip: PlannedTrip): Result<Unit> =
        Result.failure(UnsupportedOperationException("planned trips not exercised by this test"))
    override suspend fun delete(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("planned trips not exercised by this test"))
}

/** Always Idle/absent — this ViewModel's own preferences-load failure path is log-only (see [AvailabilityViewModel.loadOfflineMapPreferences]), nothing here exercises it. */
private object OfflineMapsStubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getMapFullscreen(): Result<Boolean> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
}

private object OfflineMapsStubDistanceUnitPreferenceRepository : DistanceUnitPreferenceRepository {
    override suspend fun getDistanceUnit(): Result<DistanceUnit> = Result.success(DistanceUnit.MILES)
    override suspend fun setDistanceUnit(unit: DistanceUnit): Result<Unit> = Result.success(Unit)
}

private object OfflineMapsStubAppThemePreferenceRepository : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}

/**
 * Fully controlled by each test: [listRegionsResult] answers `listRegions()` (called once on every
 * ViewModel init, and again after every successful download/delete), [downloadResult] and
 * [progressSteps] answer `download()`, [deleteRegionResult] answers `deleteRegion()`.
 * [downloadCalled] proves invalid input never reaches the repository at all. A successful
 * `download()`/`deleteRegion()` updates [listRegionsResult] itself, mirroring how the real
 * Room-backed implementation makes a completed write immediately visible to the next read.
 */
private class RecordingOfflineMapRepository(
    var listRegionsResult: Result<List<OfflineRegionSummary>> = Result.success(emptyList()),
) : OfflineMapRepository {
    var downloadResult: Result<OfflineRegionSummary> = Result.failure(IllegalStateException("downloadResult not configured"))
    var progressSteps: List<Pair<Int, Int>> = emptyList()
    var deleteRegionResult: Result<Unit> = Result.success(Unit)

    var downloadCalled = false
    var lastName: String? = null
    var lastRegion: Region? = null
    var lastDeletedId: Long? = null

    override suspend fun download(
        name: String,
        region: Region,
        onProgress: (downloaded: Int, total: Int) -> Unit,
    ): Result<OfflineRegionSummary> {
        downloadCalled = true
        lastName = name
        lastRegion = region
        progressSteps.forEach { (downloaded, total) -> onProgress(downloaded, total) }
        downloadResult.onSuccess { summary -> listRegionsResult = Result.success((listRegionsResult.getOrNull().orEmpty()) + summary) }
        return downloadResult
    }

    override suspend fun deleteRegion(id: Long): Result<Unit> {
        lastDeletedId = id
        deleteRegionResult.onSuccess {
            listRegionsResult = Result.success(listRegionsResult.getOrNull().orEmpty().filterNot { it.id == id })
        }
        return deleteRegionResult
    }

    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = listRegionsResult
}

class AvailabilityViewModelOfflineMapsTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val searchCache = InMemorySearchCacheRepository()

    private fun viewModel(offlineMapRepository: OfflineMapRepository): AvailabilityViewModel = AvailabilityViewModel(
        locationProvider = OfflineMapsUnusedLocationProvider,
        locationTracker = OfflineMapsNoOpLocationTracker,
        getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(OfflineMapsEmptyRepository), searchCache),
        getRecentSearches = GetRecentSearchesUseCase(searchCache),
        getSightings = GetSightingsUseCase(OfflineMapsEmptyRepository),
        searchTaxa = SearchTaxaUseCase(OfflineMapsEmptyRepository),
        getConditions = GetConditionsUseCase(OfflineMapsStubWeatherProvider),
        getTripWindows = GetTripWindowsUseCase(OfflineMapsStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
        getPlannedTrips = GetPlannedTripsUseCase(OfflineMapsStubPlannedTripRepository),
        savePlannedTrip = SavePlannedTripUseCase(OfflineMapsStubPlannedTripRepository),
        deletePlannedTrip = DeletePlannedTripUseCase(OfflineMapsStubPlannedTripRepository),
        getSeasonalPattern = GetSeasonalPatternUseCase(
            GetSightingsUseCase(OfflineMapsEmptyRepository),
            OfflineMapsStubHistoricalWeatherProvider,
            ComputeFruitingLagDistributionUseCase(),
        ),
        offlineMapRepository = offlineMapRepository,
        mapPreferencesRepository = OfflineMapsStubMapPreferencesRepository,
        distanceUnitPreferenceRepository = OfflineMapsStubDistanceUnitPreferenceRepository,
        appThemePreferenceRepository = OfflineMapsStubAppThemePreferenceRepository,
        getTodaysForecast = GetTodaysForecastUseCase(OfflineMapsStubTripPlanningWeatherProvider),
    )

    /** Mirrors how [AvailabilityScreen]'s picker map now sets these — panning and confirming with OK, not typing. */
    private fun AvailabilityViewModel.pickReferenceRegion() {
        onOfflineMapLatChanged(REFERENCE_REGION.lat.toString())
        onOfflineMapLngChanged(REFERENCE_REGION.lng.toString())
        onOfflineMapRadiusChanged(REFERENCE_REGION.radiusKm)
    }

    @Test
    fun `starts Idle with no regions when nothing is on disk`() = runTest(dispatcher) {
        val vm = viewModel(RecordingOfflineMapRepository(listRegionsResult = Result.success(emptyList())))
        advanceUntilIdle()

        assertEquals(OfflineMapStatus.Idle, vm.uiState.value.offlineDownloadStatus)
        assertTrue(vm.uiState.value.offlineRegions.isEmpty())
    }

    @Test
    fun `reflects what listRegions reports as already downloaded, on startup`() = runTest(dispatcher) {
        val vm = viewModel(RecordingOfflineMapRepository(listRegionsResult = Result.success(listOf(REFERENCE_REGION_SUMMARY))))
        advanceUntilIdle()

        assertEquals(listOf(REFERENCE_REGION_SUMMARY), vm.uiState.value.offlineRegions)
        assertNull(vm.uiState.value.offlineRegionsErrorMessage)
    }

    /**
     * CLAUDE.md: a failure is reported, not swallowed into a plausible-looking empty list. Per the
     * error-presentation spec's belief-changing rule, a region-list-load failure isn't the same
     * category as a download failure — it's reported via [AvailabilityUiState.offlineRegionsErrorMessage],
     * not [AvailabilityUiState.offlineDownloadStatus].
     */
    @Test
    fun `a listRegions read failure is reported via offlineRegionsErrorMessage, not silently as an empty list`() = runTest(dispatcher) {
        val vm = viewModel(RecordingOfflineMapRepository(listRegionsResult = Result.failure(IOException("corrupt metadata blob"))))
        advanceUntilIdle()

        // Error-presentation spec's absolute rule: the exception's own message never reaches a
        // user-facing field, however recognizable — this used to read "corrupt metadata blob" verbatim.
        assertEquals("Couldn't read offline regions.", vm.uiState.value.offlineRegionsErrorMessage)
        assertTrue(vm.uiState.value.offlineRegions.isEmpty())
    }

    @Test
    fun `invalid coordinates are rejected without ever calling the repository`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onOfflineMapLatChanged("not a number")
        vm.onOfflineMapLngChanged("-122.634")
        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        assertFalse(repository.downloadCalled)
        val status = vm.uiState.value.offlineDownloadStatus
        assertTrue(status is OfflineMapStatus.Failed)
        assertTrue((status as OfflineMapStatus.Failed).message.contains("valid latitude"))
    }

    @Test
    fun `an out-of-range latitude is rejected the same way`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository()
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onOfflineMapLatChanged("120")
        vm.onOfflineMapLngChanged("-122.634")
        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        assertFalse(repository.downloadCalled)
        assertTrue(vm.uiState.value.offlineDownloadStatus is OfflineMapStatus.Failed)
    }

    @Test
    fun `a successful download ends Succeeded, targeting the picked region under a default name`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository().apply {
            progressSteps = listOf(0 to 10, 5 to 10, 10 to 10)
            downloadResult = Result.success(REFERENCE_REGION_SUMMARY)
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.pickReferenceRegion()

        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        assertTrue(repository.downloadCalled)
        assertEquals(REFERENCE_REGION, repository.lastRegion)
        // The name field was never typed into (see pickReferenceRegion) — a blank name defaults to
        // "Region N" rather than blocking the download.
        assertEquals("Region 1", repository.lastName)
        assertEquals(OfflineMapStatus.Succeeded, vm.uiState.value.offlineDownloadStatus)
    }

    /**
     * The in-flight case, tested separately from the success case above rather than by collecting
     * every [AvailabilityUiState] the download passes through: [MutableStateFlow]'s own StateFlow
     * conflates updates a collector isn't actively suspended between, so a fake `download()` with no
     * real suspension point between progress calls can post several updates a collector never
     * observes — that's a property of StateFlow, not a bug in the ViewModel. Parking the fake
     * mid-download with [awaitCancellation] instead makes "state reflects the latest progress"
     * directly observable: nothing later overwrites it, because nothing later runs.
     */
    @Test
    fun `an in-flight download reports Downloading with the latest progress reported so far`() = runTest(dispatcher) {
        val repository = object : OfflineMapRepository {
            override suspend fun download(
                name: String,
                region: Region,
                onProgress: (downloaded: Int, total: Int) -> Unit,
            ): Result<OfflineRegionSummary> {
                onProgress(0, 10)
                onProgress(4, 10)
                awaitCancellation()
            }

            override suspend fun deleteRegion(id: Long): Result<Unit> = Result.success(Unit)
            override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.pickReferenceRegion()

        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        val status = vm.uiState.value.offlineDownloadStatus
        assertTrue(status is OfflineMapStatus.Downloading)
        assertEquals(4, (status as OfflineMapStatus.Downloading).downloaded)
        assertEquals(10, status.total)
    }

    @Test
    fun `a failed download is reported as Failed, not silently as success, without the repository's raw message`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository().apply {
            downloadResult = Result.failure(IOException("3 of 40 tiles failed to download."))
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.pickReferenceRegion()

        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        val status = vm.uiState.value.offlineDownloadStatus
        assertTrue(status is OfflineMapStatus.Failed)
        assertEquals("Couldn't download offline maps.", (status as OfflineMapStatus.Failed).message)
    }

    @Test
    fun `deleting a region removes it from offlineRegions`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository(listRegionsResult = Result.success(listOf(REFERENCE_REGION_SUMMARY)))
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertEquals(listOf(REFERENCE_REGION_SUMMARY), vm.uiState.value.offlineRegions)

        vm.onDeleteOfflineRegion(REFERENCE_REGION_SUMMARY.id)
        advanceUntilIdle()

        assertEquals(REFERENCE_REGION_SUMMARY.id, repository.lastDeletedId)
        assertTrue(vm.uiState.value.offlineRegions.isEmpty())
    }

    /**
     * Per the error-presentation spec's belief-changing rule, a failed delete is reported the same
     * neutral way a region-list-load failure is (via [AvailabilityUiState.offlineRegionsErrorMessage]),
     * not through [AvailabilityUiState.offlineDownloadStatus] — deletion isn't a download, and the
     * region simply staying in the list already shows the delete didn't take effect.
     */
    @Test
    fun `a delete failure is reported via offlineRegionsErrorMessage rather than silently leaving the region`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository(listRegionsResult = Result.success(listOf(REFERENCE_REGION_SUMMARY))).apply {
            deleteRegionResult = Result.failure(IOException("couldn't delete tiles"))
        }
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onDeleteOfflineRegion(REFERENCE_REGION_SUMMARY.id)
        advanceUntilIdle()

        assertEquals("Couldn't delete that region.", vm.uiState.value.offlineRegionsErrorMessage)
        assertEquals(listOf(REFERENCE_REGION_SUMMARY), vm.uiState.value.offlineRegions)
    }

    /**
     * Tile-estimate dispatch: a radius that fits the 6000-tile budget must not be refused. The
     * slider's 50 km maximum at the owner's latitude is 4772 tiles against the served ceiling and
     * was being refused at 18696 against MAX_ZOOM. Driven through the real pre-flight gate
     * (onDownloadOfflineMaps), asserting the repository was actually asked to download that
     * region — not merely that no Failed status appeared. Fails with the estimate reverted to
     * MAX_ZOOM.
     */
    @Test
    fun `the slider's maximum radius fits the budget at the owner's latitude and reaches the repository`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository().apply { downloadResult = Result.success(REFERENCE_REGION_SUMMARY) }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.onOfflineMapLatChanged("45.357")
        vm.onOfflineMapLngChanged("-122.607")
        vm.onOfflineMapRadiusChanged(50)

        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        assertTrue("expected the pre-flight gate to let a 50 km region through to the repository", repository.downloadCalled)
        assertEquals(Region(lat = 45.357, lng = -122.607, radiusKm = 50), repository.lastRegion)
        assertTrue(vm.uiState.value.offlineDownloadStatus !is OfflineMapStatus.Failed)
    }

    @Test
    fun `the offline map region radius is clamped the same way the search radius is`() = runTest(dispatcher) {
        val vm = viewModel(RecordingOfflineMapRepository())
        advanceUntilIdle()

        vm.onOfflineMapRadiusChanged(500)
        assertEquals(Region.MAX_RADIUS_KM, vm.uiState.value.offlineMapRadiusKm)

        vm.onOfflineMapRadiusChanged(-5)
        assertEquals(Region.MIN_RADIUS_KM, vm.uiState.value.offlineMapRadiusKm)
    }

    @Test
    fun `the offline map region is independent of the main search region`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository().apply {
            downloadResult = Result.success(REFERENCE_REGION_SUMMARY)
        }
        val vm = viewModel(repository)
        advanceUntilIdle()

        // The main search region is never touched by this test, and must stay null: the offline
        // map's region picker is standalone, per this task's own decisions.
        vm.pickReferenceRegion()
        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        assertNull(vm.uiState.value.region)
        assertEquals(REFERENCE_REGION, repository.lastRegion)
    }
}
