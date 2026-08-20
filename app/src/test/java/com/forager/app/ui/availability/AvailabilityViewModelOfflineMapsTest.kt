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
import com.forager.app.domain.MapPreferencesRepository
import com.forager.app.domain.MushroomRepository
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
import com.forager.app.domain.model.LatLng
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
 * hand-written [OfflineMapRepository]/[MapPreferencesRepository] fake this test fully controls —
 * the same "real ViewModel over fakes" style as [AvailabilityViewModelPlannedTripsTest] and
 * [AvailabilityViewModelFilterTest].
 *
 * Rewritten for the multi-region interface (`docs/plans/journal-trips-and-offline-regions.md`,
 * "Region management") — a download no longer replaces whatever was already downloaded, so this
 * file now exercises [AvailabilityUiState.offlineRegions] (the persisted list) and
 * [AvailabilityUiState.offlineDownloadStatus] (the picker's own last-attempt state) as two separate
 * things, rather than one combined `offlineMapStatus`.
 *
 * What this cannot cover: actual tile download/delete I/O and MapLibre `OfflineManager` behavior —
 * that's Android file and network I/O, unverifiable headlessly, and is
 * `com.forager.app.map.MapLibreOfflineMapRepository`'s own concern rather than this ViewModel's.
 * This file covers the loading → success/failure state machine the UI actually renders from.
 */
private val REFERENCE_REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

private val REFERENCE_SUMMARY = OfflineRegionSummary(
    id = 1L,
    name = "Chanterelle Ridge",
    region = REFERENCE_REGION,
    minZoom = 10.0,
    maxZoom = 14.0,
    tileCount = 234,
    sizeBytes = 4_200_000L,
    createdAtEpochMillis = 1_755_000_000_000L,
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

/** Answers every [MapPreferencesRepository] call with "nothing saved yet" unless overridden. */
private class RecordingMapPreferencesRepository(
    var lastPickedRegionResult: Result<Region?> = Result.success(null),
    var staleThresholdDaysResult: Result<Int> = Result.success(60),
) : MapPreferencesRepository {
    var lastSavedRegion: Region? = null

    override suspend fun getLastPickedRegion(): Result<Region?> = lastPickedRegionResult
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> {
        lastSavedRegion = region
        return Result.success(Unit)
    }
    override suspend fun getStaleThresholdDays(): Result<Int> = staleThresholdDaysResult
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
}

/**
 * Fully controlled by each test: [listRegionsResult] answers `listRegions()` (called once on every
 * ViewModel init, and again after a successful download/delete), [downloadResult] and
 * [progressSteps] answer `download()`, [deleteRegionResult] answers `deleteRegion()`.
 * [downloadCalled] proves invalid input never reaches the repository at all.
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
        return downloadResult
    }

    override suspend fun deleteRegion(id: Long): Result<Unit> {
        lastDeletedId = id
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

    private fun viewModel(
        offlineMapRepository: OfflineMapRepository,
        mapPreferencesRepository: MapPreferencesRepository = RecordingMapPreferencesRepository(),
        locationProvider: LocationProvider = OfflineMapsUnusedLocationProvider,
    ): AvailabilityViewModel = AvailabilityViewModel(
        locationProvider = locationProvider,
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
        mapPreferencesRepository = mapPreferencesRepository,
    )

    /** Mirrors how [AvailabilityScreen]'s picker map now sets these — a long-press, not typing. */
    private fun AvailabilityViewModel.pickReferenceRegion() {
        onOfflineMapLatChanged(REFERENCE_REGION.lat.toString())
        onOfflineMapLngChanged(REFERENCE_REGION.lng.toString())
        onOfflineMapRadiusChanged(REFERENCE_REGION.radiusKm)
    }

    @Test
    fun `starts with an empty region list when nothing is on disk`() = runTest(dispatcher) {
        val vm = viewModel(RecordingOfflineMapRepository(listRegionsResult = Result.success(emptyList())))
        advanceUntilIdle()

        assertEquals(emptyList<OfflineRegionSummary>(), vm.uiState.value.offlineRegions)
        assertNull(vm.uiState.value.offlineRegionsErrorMessage)
    }

    @Test
    fun `reflects what listRegions reports as already downloaded, on startup`() = runTest(dispatcher) {
        val vm = viewModel(RecordingOfflineMapRepository(listRegionsResult = Result.success(listOf(REFERENCE_SUMMARY))))
        advanceUntilIdle()

        assertEquals(listOf(REFERENCE_SUMMARY), vm.uiState.value.offlineRegions)
    }

    /** CLAUDE.md: a failure is reported, not swallowed into a plausible-looking empty list. */
    @Test
    fun `a listRegions read failure is reported rather than silently rendering an empty list`() = runTest(dispatcher) {
        val vm = viewModel(RecordingOfflineMapRepository(listRegionsResult = Result.failure(IOException("corrupt metadata blob"))))
        advanceUntilIdle()

        assertEquals("corrupt metadata blob", vm.uiState.value.offlineRegionsErrorMessage)
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
    fun `a successful download adds to the region list, targeting the picked region and name`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository(listRegionsResult = Result.success(listOf(REFERENCE_SUMMARY))).apply {
            progressSteps = listOf(0 to 10, 5 to 10, 10 to 10)
            downloadResult = Result.success(REFERENCE_SUMMARY)
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.pickReferenceRegion()
        vm.onOfflineMapNameChanged("Chanterelle Ridge")

        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        assertTrue(repository.downloadCalled)
        assertEquals("Chanterelle Ridge", repository.lastName)
        assertEquals(REFERENCE_REGION, repository.lastRegion)
        assertEquals(OfflineMapStatus.Succeeded, vm.uiState.value.offlineDownloadStatus)
        assertEquals(listOf(REFERENCE_SUMMARY), vm.uiState.value.offlineRegions)
    }

    @Test
    fun `a blank name defaults to Region N rather than blocking the download`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository().apply {
            downloadResult = Result.success(REFERENCE_SUMMARY)
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.pickReferenceRegion()

        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        assertTrue(repository.downloadCalled)
        assertEquals("Region 1", repository.lastName)
    }

    /** A download no longer replaces whatever was already on disk — see [OfflineMapRepository]'s doc comment. */
    @Test
    fun `the default region name counts up from what is already downloaded`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository(listRegionsResult = Result.success(listOf(REFERENCE_SUMMARY))).apply {
            downloadResult = Result.success(REFERENCE_SUMMARY)
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.pickReferenceRegion()

        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        assertEquals("Region 2", repository.lastName)
    }

    /**
     * The in-flight case, tested separately from the success case above rather than by collecting
     * every [AvailabilityUiState] the download passes through: [kotlinx.coroutines.flow.MutableStateFlow]'s
     * own StateFlow conflates updates a collector isn't actively suspended between, so a fake
     * `download()` with no real suspension point between progress calls can post several updates a
     * collector never observes — that's a property of StateFlow, not a bug in the ViewModel. Parking
     * the fake mid-download with [awaitCancellation] instead makes "state reflects the latest
     * progress" directly observable: nothing later overwrites it, because nothing later runs.
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
    fun `a failed download is reported as Failed with the repository's message, not silently as success`() = runTest(dispatcher) {
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
        assertEquals("3 of 40 tiles failed to download.", (status as OfflineMapStatus.Failed).message)
        // A failed download leaves the region list untouched — nothing was actually added.
        assertEquals(emptyList<OfflineRegionSummary>(), vm.uiState.value.offlineRegions)
    }

    @Test
    fun `deleting a region removes it from the list`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository(listRegionsResult = Result.success(listOf(REFERENCE_SUMMARY)))
        val vm = viewModel(repository)
        advanceUntilIdle()
        assertEquals(listOf(REFERENCE_SUMMARY), vm.uiState.value.offlineRegions)

        repository.listRegionsResult = Result.success(emptyList())
        vm.onDeleteOfflineRegion(REFERENCE_SUMMARY.id)
        advanceUntilIdle()

        assertEquals(REFERENCE_SUMMARY.id, repository.lastDeletedId)
        assertEquals(emptyList<OfflineRegionSummary>(), vm.uiState.value.offlineRegions)
    }

    @Test
    fun `a delete failure is reported rather than silently leaving the region off the list`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository(listRegionsResult = Result.success(listOf(REFERENCE_SUMMARY))).apply {
            deleteRegionResult = Result.failure(IOException("couldn't delete tiles"))
        }
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onDeleteOfflineRegion(REFERENCE_SUMMARY.id)
        advanceUntilIdle()

        assertEquals("couldn't delete tiles", vm.uiState.value.offlineRegionsErrorMessage)
        // The list is untouched — the repository never confirmed the delete, so nothing here claims it happened.
        assertEquals(listOf(REFERENCE_SUMMARY), vm.uiState.value.offlineRegions)
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
            downloadResult = Result.success(REFERENCE_SUMMARY)
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

    @Test
    fun `a successful download saves the picked region as the new last-picked default`() = runTest(dispatcher) {
        val repository = RecordingOfflineMapRepository().apply {
            downloadResult = Result.success(REFERENCE_SUMMARY)
        }
        val preferences = RecordingMapPreferencesRepository()
        val vm = viewModel(repository, preferences)
        advanceUntilIdle()
        vm.pickReferenceRegion()

        vm.onDownloadOfflineMaps()
        advanceUntilIdle()

        assertEquals(REFERENCE_REGION, preferences.lastSavedRegion)
    }

    /** The design doc's "Cold-start default": the picker restores the last picked centre/radius rather than a fixed point. */
    @Test
    fun `restores the last picked centre and radius from preferences at start-up`() = runTest(dispatcher) {
        val preferences = RecordingMapPreferencesRepository(lastPickedRegionResult = Result.success(REFERENCE_REGION))
        val vm = viewModel(RecordingOfflineMapRepository(), preferences)
        advanceUntilIdle()

        assertEquals(LatLng(REFERENCE_REGION.lat, REFERENCE_REGION.lng), vm.uiState.value.offlineMapPickerDefaultCenter)
        assertEquals(REFERENCE_REGION.radiusKm, vm.uiState.value.offlineMapRadiusKm)
        // Restoring the default viewport is not the same as picking a region — Download must stay
        // gated on an actual long-press, same as when nothing was ever picked.
        assertNull(vm.uiState.value.offlineMapLatText.toDoubleOrNull())
    }

    @Test
    fun `restores the stale threshold from preferences at start-up`() = runTest(dispatcher) {
        val preferences = RecordingMapPreferencesRepository(staleThresholdDaysResult = Result.success(30))
        val vm = viewModel(RecordingOfflineMapRepository(), preferences)
        advanceUntilIdle()

        assertEquals(30, vm.uiState.value.offlineStaleThresholdDays)
    }

    /** Opening the picker with no last-picked region tries the device's current location as the default. */
    @Test
    fun `opening the picker with no last-picked region defaults to the current location`() = runTest(dispatcher) {
        val locationProvider = OfflineMapsFixedLocationProvider(LocationResult.Success(lat = 45.5, lng = -122.6, altitude = null))
        val vm = viewModel(RecordingOfflineMapRepository(), locationProvider = locationProvider)
        advanceUntilIdle()

        vm.onOfflineMapsOpened()
        advanceUntilIdle()

        assertEquals(LatLng(45.5, -122.6), vm.uiState.value.offlineMapPickerDefaultCenter)
    }

    /** A denied/unavailable fix leaves the picker's existing continental-US-centroid fallback in place. */
    @Test
    fun `a failed current-location fetch leaves the picker default unset, not a fabricated location`() = runTest(dispatcher) {
        val locationProvider = OfflineMapsFixedLocationProvider(LocationResult.PermissionDenied)
        val vm = viewModel(RecordingOfflineMapRepository(), locationProvider = locationProvider)
        advanceUntilIdle()

        vm.onOfflineMapsOpened()
        advanceUntilIdle()

        assertNull(vm.uiState.value.offlineMapPickerDefaultCenter)
    }

    /**
     * The project owner's own call: opening the picker away from home is more common than opening
     * it away from wherever was last downloaded, so a fresh current-location fetch wins over a
     * previously-restored last-picked centre, not just over the continental-US fallback.
     */
    @Test
    fun `opening the picker overrides a previously-restored last-picked centre with the current location`() = runTest(dispatcher) {
        val preferences = RecordingMapPreferencesRepository(lastPickedRegionResult = Result.success(REFERENCE_REGION))
        val locationProvider = OfflineMapsFixedLocationProvider(LocationResult.Success(lat = 45.5, lng = -122.6, altitude = null))
        val vm = viewModel(RecordingOfflineMapRepository(), preferences, locationProvider)
        advanceUntilIdle()

        vm.onOfflineMapsOpened()
        advanceUntilIdle()

        assertEquals(LatLng(45.5, -122.6), vm.uiState.value.offlineMapPickerDefaultCenter)
    }

    /** A failed fetch must not clear a good default that was already showing — only a successful one may replace it. */
    @Test
    fun `a failed current-location fetch leaves a previously-restored centre in place`() = runTest(dispatcher) {
        val preferences = RecordingMapPreferencesRepository(lastPickedRegionResult = Result.success(REFERENCE_REGION))
        val locationProvider = OfflineMapsFixedLocationProvider(LocationResult.PermissionDenied)
        val vm = viewModel(RecordingOfflineMapRepository(), preferences, locationProvider)
        advanceUntilIdle()

        vm.onOfflineMapsOpened()
        advanceUntilIdle()

        assertEquals(LatLng(REFERENCE_REGION.lat, REFERENCE_REGION.lng), vm.uiState.value.offlineMapPickerDefaultCenter)
    }
}

/** Answers [getCurrentLocation] with a fixed, caller-supplied [result] — for asserting on what the ViewModel does with each outcome. */
private class OfflineMapsFixedLocationProvider(private val result: LocationResult) : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult = result
}
