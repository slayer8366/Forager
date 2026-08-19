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
import kotlinx.coroutines.awaitCancellation
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

private val REFERENCE_INFO = OfflineMapInfo(
    region = REFERENCE_REGION,
    tileCount = 234,
    sizeBytes = 4_200_000L,
    downloadedAtEpochMillis = 1_755_000_000_000L,
)

private object OfflineMapsUnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult =
        error("getCurrentLocation() is not part of this test's path and must not be called")
}

private object OfflineMapsEmptyRepository : MushroomRepository {
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

/**
 * Fully controlled by each test: [statusResult] answers `getStatus()` (called once on every
 * ViewModel init), [downloadResult] and [progressSteps] answer `download()`, [deleteResult]
 * answers `delete()`. [downloadCalled] proves invalid input never reaches the repository at all.
 */
private class RecordingOfflineMapRepository(
    var statusResult: Result<OfflineMapInfo?> = Result.success(null),
) : OfflineMapRepository {
    var downloadResult: Result<OfflineMapInfo> = Result.failure(IllegalStateException("downloadResult not configured"))
    var progressSteps: List<Pair<Int, Int>> = emptyList()
    var deleteResult: Result<Unit> = Result.success(Unit)

    var downloadCalled = false
    var lastRegion: Region? = null

    override suspend fun download(
        region: Region,
        onProgress: (downloaded: Int, total: Int) -> Unit,
    ): Result<OfflineMapInfo> {
        downloadCalled = true
        lastRegion = region
        progressSteps.forEach { (downloaded, total) -> onProgress(downloaded, total) }
        return downloadResult
    }

    override suspend fun delete(): Result<Unit> = deleteResult

    override suspend fun getStatus(): Result<OfflineMapInfo?> = statusResult
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
        getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(OfflineMapsEmptyRepository), searchCache),
        getRecentSearches = GetRecentSearchesUseCase(searchCache),
        getSightings = GetSightingsUseCase(OfflineMapsEmptyRepository),
        searchTaxa = SearchTaxaUseCase(OfflineMapsEmptyRepository),
        getConditions = GetConditionsUseCase(OfflineMapsStubWeatherProvider),
        clusterForagingAreas = ClusterForagingAreasUseCase(),
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
    )

    /** Mirrors how [AvailabilityScreen]'s picker map now sets these — a long-press, not typing. */
    private fun AvailabilityViewModel.pickReferenceRegion() {
        onOfflineMapLatChanged(REFERENCE_REGION.lat.toString())
        onOfflineMapLngChanged(REFERENCE_REGION.lng.toString())
        onOfflineMapRadiusChanged(REFERENCE_REGION.radiusKm)
    }

    @Test
    fun `starts as NotDownloaded when nothing is on disk`() = runTest(dispatcher) {
        val vm = viewModel(RecordingOfflineMapRepository(statusResult = Result.success(null)))
        advanceUntilIdle()

        assertEquals(OfflineMapStatus.NotDownloaded, vm.uiState.value.offlineMapStatus)
    }

    @Test
    fun `reflects what getStatus reports as already downloaded, on startup`() = runTest(dispatcher) {
        val vm = viewModel(RecordingOfflineMapRepository(statusResult = Result.success(REFERENCE_INFO)))
        advanceUntilIdle()

        val status = vm.uiState.value.offlineMapStatus
        assertTrue(status is OfflineMapStatus.Downloaded)
        status as OfflineMapStatus.Downloaded
        assertEquals(REFERENCE_INFO.region, status.region)
        assertEquals(REFERENCE_INFO.tileCount, status.tileCount)
        assertEquals(REFERENCE_INFO.sizeBytes, status.sizeBytes)
        assertEquals(REFERENCE_INFO.downloadedAtEpochMillis, status.downloadedAtEpochMillis)
    }

    /** CLAUDE.md: a failure is reported, not swallowed into a plausible-looking "nothing downloaded". */
    @Test
    fun `a getStatus read failure is reported as Failed rather than defaulting to NotDownloaded`() = runTest(dispatcher) {
        val vm = viewModel(RecordingOfflineMapRepository(statusResult = Result.failure(IOException("corrupt sidecar"))))
        advanceUntilIdle()

        val status = vm.uiState.value.offlineMapStatus
        assertTrue(status is OfflineMapStatus.Failed)
        assertEquals("corrupt sidecar", (status as OfflineMapStatus.Failed).message)
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
        val status = vm.uiState.value.offlineMapStatus
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
        assertTrue(vm.uiState.value.offlineMapStatus is OfflineMapStatus.Failed)
    }

    @Test
    fun `a successful download ends Downloaded, targeting the picked region`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository().apply {
            progressSteps = listOf(0 to 10, 5 to 10, 10 to 10)
            downloadResult = Result.success(REFERENCE_INFO)
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.pickReferenceRegion()

        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        assertTrue(repository.downloadCalled)
        assertEquals(REFERENCE_REGION, repository.lastRegion)
        assertTrue(vm.uiState.value.offlineMapStatus is OfflineMapStatus.Downloaded)
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
                region: Region,
                onProgress: (downloaded: Int, total: Int) -> Unit,
            ): Result<OfflineMapInfo> {
                onProgress(0, 10)
                onProgress(4, 10)
                awaitCancellation()
            }

            override suspend fun delete(): Result<Unit> = Result.success(Unit)
            override suspend fun getStatus(): Result<OfflineMapInfo?> = Result.success(null)
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.pickReferenceRegion()

        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        val status = vm.uiState.value.offlineMapStatus
        assertTrue(status is OfflineMapStatus.Downloading)
        assertEquals(4, (status as OfflineMapStatus.Downloading).downloaded)
        assertEquals(10, status.total)
    }

    @Test
    fun `a failed download is reported as Failed with the repository's message, not silently as success`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository().apply {
            downloadResult = Result.failure(IOException("3 of 40 tiles failed to download."))
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.pickReferenceRegion()

        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        val status = vm.uiState.value.offlineMapStatus
        assertTrue(status is OfflineMapStatus.Failed)
        assertEquals("3 of 40 tiles failed to download.", (status as OfflineMapStatus.Failed).message)
    }

    @Test
    fun `deleting a downloaded region moves status back to NotDownloaded`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository(statusResult = Result.success(REFERENCE_INFO))
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.offlineMapStatus is OfflineMapStatus.Downloaded)

        vm.onDeleteOfflineMaps()
        advanceUntilIdle()

        assertEquals(OfflineMapStatus.NotDownloaded, vm.uiState.value.offlineMapStatus)
    }

    @Test
    fun `a delete failure is reported as Failed rather than silently staying Downloaded`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository(statusResult = Result.success(REFERENCE_INFO)).apply {
            deleteResult = Result.failure(IOException("couldn't delete tiles"))
        }
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onDeleteOfflineMaps()
        advanceUntilIdle()

        val status = vm.uiState.value.offlineMapStatus
        assertTrue(status is OfflineMapStatus.Failed)
        assertEquals("couldn't delete tiles", (status as OfflineMapStatus.Failed).message)
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
            downloadResult = Result.success(REFERENCE_INFO)
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
