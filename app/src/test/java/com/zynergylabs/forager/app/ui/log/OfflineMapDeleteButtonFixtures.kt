package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.domain.AppThemePreferenceRepository
import com.zynergylabs.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.zynergylabs.forager.app.domain.ComputeTripWindowsUseCase
import com.zynergylabs.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.zynergylabs.forager.app.domain.DeletePlannedTripUseCase
import com.zynergylabs.forager.app.domain.FakeEntryReferenceCountSource
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
import com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository
import com.zynergylabs.forager.app.domain.WeatherProvider
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.ConditionsSummary
import com.zynergylabs.forager.app.domain.model.DailyWeather
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.SightingsPage
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.WeatherSeries
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

// Fixtures for OfflineMapDeleteButtonTest (dispatch 2026-10-29-10). In their own file, beside the test, because the
// pending-delete harness they mirror (JournalPendingDeleteTest's, all private) sits in a 1679-line file this dispatch may
// not grow, and the swipe's own tests stay untouched. Same shapes as that file's fakes, under an "Omd" prefix.

/** Two downloaded regions, as JournalPendingDeleteTest's: Ridge (id 7, used in 2 entries) and Saddle (id 8, used in none). */
internal val OMD_REGIONS = listOf(
    OfflineRegionSummary(id = 7L, name = "Ridge", region = Region(45.5, -122.6, 5), minZoom = 10.0, maxZoom = 15.0, tileCount = 100, sizeBytes = 1_000_000L, createdAtEpochMillis = 1_758_300_000_000L),
    OfflineRegionSummary(id = 8L, name = "Saddle", region = Region(45.3, -122.4, 5), minZoom = 10.0, maxZoom = 15.0, tileCount = 50, sizeBytes = 1_000_000L, createdAtEpochMillis = 1_758_200_000_000L),
)

/** Regions kept in memory; every [deleteRegion] call (MapLibre's tile delete and the row, in the real one) is recorded. */
internal class OmdOfflineMapRepository(initial: List<OfflineRegionSummary>) : OfflineMapRepository {
    private val regions = initial.toMutableList()
    val deletedIds = mutableListOf<Long>()

    override suspend fun download(name: String, region: Region, onProgress: (downloaded: Int, total: Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("downloading is not part of this test's path"))

    override suspend fun deleteRegion(id: Long): Result<Unit> {
        deletedIds += id
        regions.removeAll { it.id == id }
        return Result.success(Unit)
    }

    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(regions.toList())
}

/** The real [AvailabilityViewModel] over [offlineMapRepository], with every other dependency inert. */
internal fun omdAvailabilityViewModel(offlineMapRepository: OfflineMapRepository, regionReferenceCounts: Map<Long, Int>): AvailabilityViewModel {
    val searchCache = InMemorySearchCacheRepository()
    return AvailabilityViewModel(
        locationProvider = OmdUnusedLocationProvider,
        locationTracker = OmdNoOpLocationTracker,
        getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(OmdEmptyRepository), searchCache),
        getRecentSearches = GetRecentSearchesUseCase(searchCache),
        getSightings = GetSightingsUseCase(OmdEmptyRepository),
        searchTaxa = SearchTaxaUseCase(OmdEmptyRepository),
        getConditions = GetConditionsUseCase(OmdWeather),
        getTripWindows = GetTripWindowsUseCase(OmdWeather, ComputeTripWindowsUseCase()),
        getPlannedTrips = GetPlannedTripsUseCase(OmdPlannedTrips),
        savePlannedTrip = SavePlannedTripUseCase(OmdPlannedTrips),
        deletePlannedTrip = DeletePlannedTripUseCase(OmdPlannedTrips),
        getSeasonalPattern = GetSeasonalPatternUseCase(GetSightingsUseCase(OmdEmptyRepository), OmdWeather, ComputeFruitingLagDistributionUseCase()),
        offlineMapRepository = offlineMapRepository,
        mapPreferencesRepository = OmdMapPreferences,
        unitSystemPreferenceRepository = OmdUnitSystem,
        appThemePreferenceRepository = OmdTheme,
        getTodaysForecast = GetTodaysForecastUseCase(OmdWeather),
        entryReferenceCounts = FakeEntryReferenceCountSource(offlineRegions = regionReferenceCounts),
    )
}

private object OmdNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object OmdUnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult = LocationResult.LocationUnavailable
}

private object OmdEmptyRepository : MushroomRepository, TaxonSearchRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object OmdWeather : WeatherProvider, TripPlanningWeatherProvider, HistoricalWeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows are not part of this test's path"))
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern is not part of this test's path"))
}

private object OmdPlannedTrips : PlannedTripRepository {
    override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(emptyList())
    override suspend fun save(trip: PlannedTrip): Result<Unit> = Result.failure(UnsupportedOperationException("planned trips are not part of this test's path"))
    override suspend fun delete(id: String): Result<Unit> = Result.failure(UnsupportedOperationException("planned trips are not part of this test's path"))
}

private object OmdMapPreferences : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getMapFullscreen(): Result<Boolean> = Result.success(false)
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = Result.success(Unit)
}

private object OmdUnitSystem : UnitSystemPreferenceRepository {
    override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(UnitSystem.IMPERIAL)
    override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
}

private object OmdTheme : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}
