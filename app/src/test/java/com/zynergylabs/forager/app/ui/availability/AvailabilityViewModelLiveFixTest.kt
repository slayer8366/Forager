package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.AppThemePreferenceRepository
import com.zynergylabs.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.zynergylabs.forager.app.domain.ComputeTripWindowsUseCase
import com.zynergylabs.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.zynergylabs.forager.app.domain.DeletePlannedTripUseCase
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
import com.zynergylabs.forager.app.domain.ageMillis
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.ConditionsSummary
import com.zynergylabs.forager.app.domain.model.DailyWeather
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.SightingsPage
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.domain.model.WeatherSeries
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
 * HUD-foundations dispatch, Item 1: the ViewModel now holds the whole [LocationFix.Update] as
 * [AvailabilityUiState.liveFix], so a consumer can read accuracy and age, while the existing
 * `liveLocation`/`liveAltitudeMeters` readers see exactly the values they always did. Every
 * expected value is a pinned literal; ages are hand-computed from two pinned clock readings, and
 * the "fixes stop" case is simply no second emission.
 */
class AvailabilityViewModelLiveFixTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val searchCache = InMemorySearchCacheRepository()

    private fun viewModel(locationTracker: LocationTracker): AvailabilityViewModel {
        val plannedTripRepository = LiveFixInMemoryPlannedTripRepository()
        return AvailabilityViewModel(
            locationProvider = LiveFixUnusedLocationProvider,
            locationTracker = locationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(LiveFixEmptyRepository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(LiveFixEmptyRepository),
            searchTaxa = SearchTaxaUseCase(LiveFixEmptyRepository),
            getConditions = GetConditionsUseCase(LiveFixStubWeatherProvider),
            getTripWindows = GetTripWindowsUseCase(LiveFixStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(plannedTripRepository),
            savePlannedTrip = SavePlannedTripUseCase(plannedTripRepository),
            deletePlannedTrip = DeletePlannedTripUseCase(plannedTripRepository),
            getSeasonalPattern = GetSeasonalPatternUseCase(
                GetSightingsUseCase(LiveFixEmptyRepository),
                LiveFixStubHistoricalWeatherProvider,
                ComputeFruitingLagDistributionUseCase(),
            ),
            offlineMapRepository = LiveFixStubOfflineMapRepository,
            mapPreferencesRepository = LiveFixStubMapPreferencesRepository,
            unitSystemPreferenceRepository = LiveFixStubUnitSystemPreferenceRepository,
            appThemePreferenceRepository = LiveFixStubAppThemePreferenceRepository,
            getTodaysForecast = GetTodaysForecastUseCase(LiveFixStubTripPlanningWeatherProvider),
        )
    }

    private val fix = LocationFix.Update(
        lat = 45.52,
        lng = -122.68,
        altitude = 50.0,
        accuracyMeters = 12.5f,
        timestampEpochMillis = 1_700_000_000_000L,
    )

    @Test
    fun `accuracy and timestamp reach the UI state, and the existing readers see the same lat lng altitude`() = runTest(dispatcher) {
        val fixes = MutableSharedFlow<LocationFix>(replay = 1)
        val vm = viewModel(LiveFixFakeLocationTracker(fixes))
        advanceUntilIdle()

        fixes.emit(fix)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(12.5f, state.liveFix?.accuracyMeters)
        assertEquals(1_700_000_000_000L, state.liveFix?.timestampEpochMillis)
        // The pre-existing consumers' values, unchanged in meaning: the compass strip reads these.
        assertEquals(LatLng(45.52, -122.68), state.liveLocation)
        assertEquals(50.0, state.liveAltitudeMeters)
    }

    @Test
    fun `when fixes stop the held fix's age keeps increasing`() = runTest(dispatcher) {
        val fixes = MutableSharedFlow<LocationFix>(replay = 1)
        val vm = viewModel(LiveFixFakeLocationTracker(fixes))
        advanceUntilIdle()
        fixes.emit(fix)
        advanceUntilIdle()

        // No further emission: the same fix is still held, and only the caller's clock moves.
        val held = requireNotNull(vm.uiState.value.liveFix)
        assertEquals(2_000L, held.ageMillis(nowEpochMillis = 1_700_000_002_000L))
        assertEquals(1_200_000L, held.ageMillis(nowEpochMillis = 1_700_001_200_000L))
    }

    /**
     * Location-accuracy dispatch, item 1: a fix worse than 50 m never becomes the live fix; the
     * previous one is held. And the held fix **ages** — the consequence the owner accepted, not a
     * side effect — so the age is asserted directly against the *first* fix's timestamp at a
     * clock reading taken after the rejected fix arrived. Fails with the gate removed (the 60 m fix
     * lands: accuracy 60, and the age collapses to 10 s).
     */
    @Test
    fun `a fix worse than 50 m is dropped, the previous fix is held, and the held fix ages`() = runTest(dispatcher) {
        val fixes = MutableSharedFlow<LocationFix>(replay = 1)
        val vm = viewModel(LiveFixFakeLocationTracker(fixes))
        advanceUntilIdle()

        fixes.emit(fix.copy(accuracyMeters = 40f, timestampEpochMillis = 1_700_000_000_000L))
        advanceUntilIdle()
        fixes.emit(fix.copy(lat = 45.525, accuracyMeters = 60f, timestampEpochMillis = 1_700_000_050_000L))
        advanceUntilIdle()

        val held = requireNotNull(vm.uiState.value.liveFix)
        assertEquals(40f, held.accuracyMeters)
        assertEquals(45.52, held.lat, 0.0)
        assertEquals(1_700_000_000_000L, held.timestampEpochMillis)
        // 60 s after the first fix, 10 s after the rejected one: the age is the first fix's.
        assertEquals(60_000L, held.ageMillis(nowEpochMillis = 1_700_000_060_000L))
    }

    @Test
    fun `a fix at exactly 50 m passes the gate, and a null accuracy passes it too`() = runTest(dispatcher) {
        val fixes = MutableSharedFlow<LocationFix>(replay = 1)
        val vm = viewModel(LiveFixFakeLocationTracker(fixes))
        advanceUntilIdle()

        fixes.emit(fix.copy(accuracyMeters = 50f, timestampEpochMillis = 1_700_000_000_000L))
        advanceUntilIdle()
        assertEquals(50f, vm.uiState.value.liveFix?.accuracyMeters)

        fixes.emit(fix.copy(accuracyMeters = null, timestampEpochMillis = 1_700_000_001_000L))
        advanceUntilIdle()
        assertEquals(1_700_000_001_000L, vm.uiState.value.liveFix?.timestampEpochMillis)
        assertNull(vm.uiState.value.liveFix?.accuracyMeters)
    }

    @Test
    fun `an unreported accuracy stays null, distinct from a reported zero`() = runTest(dispatcher) {
        val fixes = MutableSharedFlow<LocationFix>(replay = 1)
        val vm = viewModel(LiveFixFakeLocationTracker(fixes))
        advanceUntilIdle()

        fixes.emit(fix.copy(accuracyMeters = null))
        advanceUntilIdle()
        assertNull(vm.uiState.value.liveFix?.accuracyMeters)

        fixes.emit(fix.copy(accuracyMeters = 0f))
        advanceUntilIdle()
        assertEquals(0f, vm.uiState.value.liveFix?.accuracyMeters)
    }

    @Test
    fun `before any fix arrives there is no live fix and the derived readers are null`() = runTest(dispatcher) {
        val vm = viewModel(LiveFixFakeLocationTracker(MutableSharedFlow()))
        advanceUntilIdle()

        assertNull(vm.uiState.value.liveFix)
        assertNull(vm.uiState.value.liveLocation)
        assertNull(vm.uiState.value.liveAltitudeMeters)
    }

    // ---- First-launch dispatch (owner-found on device) ----------------------------------------
    // The sequence that used to fail: permission not held when the ViewModel is constructed, so
    // the tracker's first collection completes with PermissionDenied; then the grant arrives and
    // fixes flow — all without reconstructing the ViewModel. A test that only covered "permission
    // already held" passed before the fix and proved nothing about this.

    @Test
    fun `first launch - permission granted after construction - fixes reach the strip without a restart`() = runTest(dispatcher) {
        val tracker = FirstLaunchFakeLocationTracker()
        val vm = viewModel(tracker)
        advanceUntilIdle()
        // The construction-time collection has already completed on the not-yet-granted permission.
        assertEquals(1, tracker.collectionsStarted)
        assertNull(vm.uiState.value.liveFix)

        tracker.permissionHeld = true
        vm.onLocationPermissionGranted()
        advanceUntilIdle()
        tracker.updates.emit(fix)
        advanceUntilIdle()

        assertEquals(2, tracker.collectionsStarted)
        assertEquals(LatLng(45.52, -122.68), vm.uiState.value.liveLocation)
        assertEquals(50.0, vm.uiState.value.liveAltitudeMeters)
        assertEquals(12.5f, vm.uiState.value.liveFix?.accuracyMeters)
    }

    @Test
    fun `a grant reported while fixes are already flowing does not start a second collection`() = runTest(dispatcher) {
        val tracker = FirstLaunchFakeLocationTracker().apply { permissionHeld = true }
        val vm = viewModel(tracker)
        advanceUntilIdle()
        assertEquals(1, tracker.collectionsStarted)

        // The launcher answers immediately for an already-held permission, on every locate tap.
        vm.onLocationPermissionGranted()
        vm.onLocationPermissionGranted()
        advanceUntilIdle()
        tracker.updates.emit(fix)
        advanceUntilIdle()

        assertEquals(1, tracker.collectionsStarted)
        assertEquals(LatLng(45.52, -122.68), vm.uiState.value.liveLocation)
    }

    @Test
    fun `denial is unchanged - no live fix, the locate-me status reports it, and nothing crashes`() = runTest(dispatcher) {
        val tracker = FirstLaunchFakeLocationTracker()
        val vm = viewModel(tracker)
        advanceUntilIdle()

        vm.onLocateMePermissionDenied()
        advanceUntilIdle()

        assertEquals(LocateMeStatus.PermissionDenied, vm.uiState.value.locateMeStatus)
        assertNull(vm.uiState.value.liveFix)
        assertEquals(1, tracker.collectionsStarted)
    }
}

/**
 * Mirrors the real [com.zynergylabs.forager.app.location.AndroidLocationTracker]'s contract exactly as
 * [LocationTracker.fixes] documents it: permission is checked at each collection start; not held
 * means one [LocationFix.PermissionDenied] and completion, held means the live stream. The counter
 * is what lets a test assert "a new collection started" or "no second collection started" on a
 * real number rather than infer it.
 */
private class FirstLaunchFakeLocationTracker : LocationTracker {
    var permissionHeld = false
    var collectionsStarted = 0
    val updates = MutableSharedFlow<LocationFix>()

    override val fixes: Flow<LocationFix> = flow {
        collectionsStarted++
        if (!permissionHeld) {
            emit(LocationFix.PermissionDenied)
            return@flow
        }
        emitAll(updates)
    }
}

private class LiveFixFakeLocationTracker(override val fixes: Flow<LocationFix>) : LocationTracker

private object LiveFixUnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult =
        error("getCurrentLocation() is not part of this test's path and must not be called")
}

private object LiveFixEmptyRepository : MushroomRepository, TaxonSearchRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object LiveFixStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object LiveFixStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

private object LiveFixStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

private class LiveFixInMemoryPlannedTripRepository : PlannedTripRepository {
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

private object LiveFixStubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

private object LiveFixStubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getMapFullscreen(): Result<Boolean> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
}

private object LiveFixStubUnitSystemPreferenceRepository : UnitSystemPreferenceRepository {
    override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(UnitSystem.IMPERIAL)
    override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
}

private object LiveFixStubAppThemePreferenceRepository : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}
