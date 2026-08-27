package com.forager.app.ui.availability

import com.forager.app.domain.ClusterForagingAreasUseCase
import com.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.forager.app.domain.ComputeTripWindowsUseCase
import com.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The km/mi persistence fix (2026-08-27): [AvailabilityUiState.distanceUnit] used to be plain
 * Compose state in `AvailabilityScreen`, resetting to the default on any configuration change (a
 * system theme switch among them) — a real device report. This covers the two halves of the fix at
 * the ViewModel level, headless, the same [DistanceUnitPreferenceRepository] contract
 * [AvailabilityViewModelLocateMeTest] mirrors for its own preference dependency: the value is
 * restored from the repository at startup (not hardcoded), and a selection both updates the UI
 * state immediately and persists through the repository, using a fake that records what it was
 * called with rather than asserting on a proxy like "no exception was thrown."
 */
class AvailabilityViewModelDistanceUnitTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val searchCache = InMemorySearchCacheRepository()

    private fun viewModel(distanceUnitPreferenceRepository: DistanceUnitPreferenceRepository): AvailabilityViewModel {
        val plannedTripRepository = DistanceUnitInMemoryPlannedTripRepository()
        return AvailabilityViewModel(
            locationProvider = DistanceUnitUnusedLocationProvider,
            locationTracker = DistanceUnitNoOpLocationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(DistanceUnitEmptyRepository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(DistanceUnitEmptyRepository),
            searchTaxa = SearchTaxaUseCase(DistanceUnitEmptyRepository),
            getConditions = GetConditionsUseCase(DistanceUnitStubWeatherProvider),
            clusterForagingAreas = ClusterForagingAreasUseCase(),
            getTripWindows = GetTripWindowsUseCase(DistanceUnitStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(plannedTripRepository),
            savePlannedTrip = SavePlannedTripUseCase(plannedTripRepository),
            deletePlannedTrip = DeletePlannedTripUseCase(plannedTripRepository),
            getSeasonalPattern = GetSeasonalPatternUseCase(
                GetSightingsUseCase(DistanceUnitEmptyRepository),
                DistanceUnitStubHistoricalWeatherProvider,
                ComputeFruitingLagDistributionUseCase(),
            ),
            offlineMapRepository = DistanceUnitStubOfflineMapRepository,
            mapPreferencesRepository = DistanceUnitStubMapPreferencesRepository,
            distanceUnitPreferenceRepository = distanceUnitPreferenceRepository,
        )
    }

    @Test
    fun `restores the persisted unit at startup rather than always defaulting to miles`() = runTest(dispatcher) {
        val repository = DistanceUnitRecordingPreferenceRepository(initial = DistanceUnit.KILOMETERS)

        val vm = viewModel(repository)
        advanceUntilIdle()

        assertEquals(DistanceUnit.KILOMETERS, vm.uiState.value.distanceUnit)
    }

    @Test
    fun `selecting a unit updates the UI state immediately`() = runTest(dispatcher) {
        val repository = DistanceUnitRecordingPreferenceRepository(initial = DistanceUnit.MILES)
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onDistanceUnitSelected(DistanceUnit.KILOMETERS)

        assertEquals(DistanceUnit.KILOMETERS, vm.uiState.value.distanceUnit)
    }

    @Test
    fun `selecting a unit persists it through the repository, not just in memory`() = runTest(dispatcher) {
        val repository = DistanceUnitRecordingPreferenceRepository(initial = DistanceUnit.MILES)
        val vm = viewModel(repository)
        advanceUntilIdle()

        vm.onDistanceUnitSelected(DistanceUnit.KILOMETERS)
        advanceUntilIdle()

        assertEquals(listOf(DistanceUnit.KILOMETERS), repository.savedUnits)
    }
}

/** Records every [setDistanceUnit] call rather than silently succeeding, so a test can assert persistence actually happened. */
private class DistanceUnitRecordingPreferenceRepository(initial: DistanceUnit) : DistanceUnitPreferenceRepository {
    private var stored = initial
    val savedUnits = mutableListOf<DistanceUnit>()

    override suspend fun getDistanceUnit(): Result<DistanceUnit> = Result.success(stored)

    override suspend fun setDistanceUnit(unit: DistanceUnit): Result<Unit> {
        stored = unit
        savedUnits += unit
        return Result.success(Unit)
    }
}

private object DistanceUnitUnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult =
        error("getCurrentLocation() is not part of this test's path and must not be called")
}

private object DistanceUnitNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object DistanceUnitEmptyRepository : MushroomRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object DistanceUnitStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object DistanceUnitStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

private object DistanceUnitStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

private class DistanceUnitInMemoryPlannedTripRepository : PlannedTripRepository {
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

private object DistanceUnitStubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

private object DistanceUnitStubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
}
