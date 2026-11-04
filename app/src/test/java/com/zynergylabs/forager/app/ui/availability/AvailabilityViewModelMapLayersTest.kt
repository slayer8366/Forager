package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.AppThemePreferenceRepository
import com.zynergylabs.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.zynergylabs.forager.app.domain.ComputeTripWindowsUseCase
import com.zynergylabs.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.zynergylabs.forager.app.domain.DeletePlannedTripUseCase
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.ForecastAvailability
import com.zynergylabs.forager.app.domain.ForecastBlock
import com.zynergylabs.forager.app.domain.ForecastCellStore
import com.zynergylabs.forager.app.domain.ForecastCellsResult
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
import com.zynergylabs.forager.app.domain.MapLayerPreferences
import com.zynergylabs.forager.app.domain.MapLayerPreferencesRepository
import com.zynergylabs.forager.app.domain.MapPreferencesRepository
import com.zynergylabs.forager.app.domain.MapRecordKind
import com.zynergylabs.forager.app.domain.MapRecordReadFailure
import com.zynergylabs.forager.app.domain.MapRecords
import com.zynergylabs.forager.app.domain.MushroomRepository
import com.zynergylabs.forager.app.domain.NoEntryReferenceCounts
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.PlannedTripRepository
import com.zynergylabs.forager.app.domain.PredictAvailabilityUseCase
import com.zynergylabs.forager.app.domain.SavePlannedTripUseCase
import com.zynergylabs.forager.app.domain.SearchTaxaUseCase
import com.zynergylabs.forager.app.domain.TaxonSearchRepository
import com.zynergylabs.forager.app.domain.TrackEnds
import com.zynergylabs.forager.app.domain.TripPlanningWeatherProvider
import com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository
import com.zynergylabs.forager.app.domain.WeatherProvider
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.ConditionsSummary
import com.zynergylabs.forager.app.domain.model.DailyWeather
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.RecordPolyline
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.SightingsPage
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.WeatherSeries
import com.zynergylabs.forager.app.ui.map.layers.ColourFieldMove
import com.zynergylabs.forager.app.ui.map.layers.LayerState
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Maps tab's records, layer choices and forecast availability at the ViewModel (map layers L0b,
 * B2, B3 and B5): what [AvailabilityViewModel.onMapShown] loads each time the Maps tab is shown, the
 * stored layer choices at start, and what each Layers-sheet change does to the state and the store.
 * Failures are logged through a recording [ErrorLog], never assumed.
 */
class AvailabilityViewModelMapLayersTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val searchCache = InMemorySearchCacheRepository()
    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logged += message }
    private val week = LocalDate.of(2026, 9, 28)

    private fun viewModel(
        getMapRecords: suspend () -> MapRecords = { MapRecords.NONE },
        layerPreferences: MapLayerPreferencesRepository = RecordingLayerPreferences(),
        store: ForecastCellStore = FixedStore(ForecastAvailability.NoForecastData),
        trackEnds: StateFlow<String?> = MutableStateFlow(null),
    ): AvailabilityViewModel {
        val plannedTripRepository = LayersInMemoryPlannedTripRepository()
        return AvailabilityViewModel(
            locationProvider = LayersUnusedLocationProvider,
            locationTracker = LayersNoOpLocationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(LayersEmptyRepository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(LayersEmptyRepository),
            searchTaxa = SearchTaxaUseCase(LayersEmptyRepository),
            getConditions = GetConditionsUseCase(LayersStubWeatherProvider),
            getTripWindows = GetTripWindowsUseCase(LayersStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(plannedTripRepository),
            savePlannedTrip = SavePlannedTripUseCase(plannedTripRepository),
            deletePlannedTrip = DeletePlannedTripUseCase(plannedTripRepository),
            getSeasonalPattern = GetSeasonalPatternUseCase(
                GetSightingsUseCase(LayersEmptyRepository),
                LayersStubHistoricalWeatherProvider,
                ComputeFruitingLagDistributionUseCase(),
            ),
            offlineMapRepository = LayersStubOfflineMapRepository,
            errorLog = errorLog,
            mapPreferencesRepository = LayersMapPreferencesRepository,
            unitSystemPreferenceRepository = LayersStubUnitSystemPreferenceRepository,
            appThemePreferenceRepository = LayersStubAppThemePreferenceRepository,
            getTodaysForecast = GetTodaysForecastUseCase(LayersStubTripPlanningWeatherProvider),
            getMapRecords = getMapRecords,
            mapLayerPreferencesRepository = layerPreferences,
            forecastCellStore = store,
            // A Sunday: the week the store is asked about is the Monday before it.
            today = { LocalDate.of(2026, 10, 4) },
            trackEnds = trackEnds,
            entryReferenceCounts = NoEntryReferenceCounts,
        )
    }

    @Test
    fun `showing the Maps tab loads the saved records, and a kind that failed is logged and drawn as absent`() = runTest(dispatcher) {
        val records = MapRecords(
            findMarkers = listOf(RecordPoint("find-1", LatLng(45.0, -122.0))),
            photoMarkers = emptyList(),
            trackPolylines = emptyList(),
            offlineRegionCircles = emptyList(),
            failures = listOf(MapRecordReadFailure(MapRecordKind.TRACKS, IOException("tracks unreadable"))),
        )
        val vm = viewModel(getMapRecords = { records })
        advanceUntilIdle()

        vm.onMapShown()
        advanceUntilIdle()

        assertEquals(records, vm.uiState.value.mapRecords)
        assertEquals(listOf("Couldn't load tracks for the map."), logged.filter { "for the map" in it })
    }

    @Test
    fun `the records reload every time the Maps tab is shown`() = runTest(dispatcher) {
        var calls = 0
        val vm = viewModel(
            getMapRecords = {
                calls++
                MapRecords.NONE.copy(findMarkers = List(calls) { RecordPoint("find-$it", LatLng(45.0, -122.0)) })
            },
        )
        advanceUntilIdle()

        vm.onMapShown()
        advanceUntilIdle()
        vm.onMapShown()
        advanceUntilIdle()

        assertEquals(2, calls)
        assertEquals(2, vm.uiState.value.mapRecords.findMarkers.size)
    }

    /**
     * Dispatch 2026-10-24-07 (RECORD -1605): recording happens on the Maps tab, so the tab-shown reload never
     * runs at a Stop, and the saved track's line was missing until a tab change. The service stores the end and
     * then reports it through [TrackEnds], the order `TrackRecordingService.stopRecording` uses, and the records
     * must reload on that report alone: no [AvailabilityViewModel.onMapShown] after it. The records read here
     * follow the store, so the track is only readable once its end is stored, as `GetMapRecordsUseCase` keeps
     * only ended tracks.
     */
    @Test
    fun `a track end reported through TrackEnds puts the finished track in the map records with no tab change`() = runTest(dispatcher) {
        val trackEnds = TrackEnds()
        val finished = RecordPolyline("track-1", listOf(LatLng(45.0, -122.0), LatLng(45.001, -122.0)))
        var storedEndedTracks = emptyList<RecordPolyline>()
        val vm = viewModel(getMapRecords = { MapRecords.NONE.copy(trackPolylines = storedEndedTracks) }, trackEnds = trackEnds.latest)
        advanceUntilIdle()
        // The Maps tab is shown while the track is still recording: it is not in the records yet.
        vm.onMapShown()
        advanceUntilIdle()
        assertEquals(emptyList<RecordPolyline>(), vm.uiState.value.mapRecords.trackPolylines)

        storedEndedTracks = listOf(finished)
        trackEnds.report("track-1")
        advanceUntilIdle()

        assertEquals("the finished track is in the map records after Stop", listOf(finished), vm.uiState.value.mapRecords.trackPolylines)
    }

    @Test
    fun `the stored layer choices are restored at start, and a refused one is logged`() = runTest(dispatcher) {
        val stored = RecordingLayerPreferences(
            MapLayerPreferences(
                visibility = mapOf(MapLayerIds.FINDS to false, MapLayerIds.SIGHTINGS to false),
                opacity = mapOf(MapLayerIds.FORECAST_CHANTERELLES to 0.4f),
                order = listOf(MapLayerIds.FORECAST_CHANTERELLES, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS),
            ),
        )
        val vm = viewModel(layerPreferences = stored)
        advanceUntilIdle()

        val layers = vm.uiState.value.mapLayers
        assertEquals(false, layers.stateOf(MapLayerIds.FINDS).visible)
        assertEquals(true, layers.stateOf(MapLayerIds.SIGHTINGS).visible)
        assertEquals(0.4f, layers.stateOf(MapLayerIds.FORECAST_CHANTERELLES).opacity)
        assertEquals(listOf(MapLayerIds.FORECAST_CHANTERELLES, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS), layers.reorderableOrder)
        assertEquals(1, logged.count { MapLayerIds.SIGHTINGS in it })
    }

    @Test
    fun `a failed read of the layer choices is logged and leaves every layer at its default`() = runTest(dispatcher) {
        val vm = viewModel(layerPreferences = RecordingLayerPreferences(read = Result.failure(IOException("unreadable"))))
        advanceUntilIdle()

        assertEquals(com.zynergylabs.forager.app.ui.map.layers.MapLayersState.DEFAULT, vm.uiState.value.mapLayers)
        assertEquals(listOf("Couldn't read the map layer choices."), logged.filter { "layer choices" in it })
    }

    @Test
    fun `each Layers-sheet change shows at once and is stored, and a failed write is logged`() = runTest(dispatcher) {
        val store = RecordingLayerPreferences()
        val vm = viewModel(layerPreferences = store)
        advanceUntilIdle()

        vm.onMapLayerVisibilityChanged(MapLayerIds.PHOTOS, false)
        vm.onMapLayerOpacityChanged(MapLayerIds.FORECAST_CHANTERELLES, 0.5f)
        vm.onColourFieldMoved(MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS, ColourFieldMove.UP)
        val layers = vm.uiState.value.mapLayers
        assertEquals("shown before the store answers", false, layers.stateOf(MapLayerIds.PHOTOS).visible)
        assertEquals(LayerState(visible = true, opacity = 0.5f), layers.stateOf(MapLayerIds.FORECAST_CHANTERELLES))
        assertEquals(listOf(MapLayerIds.FORECAST_CHANTERELLES, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS), layers.reorderableOrder)
        advanceUntilIdle()

        assertEquals(
            listOf(
                "visible ${MapLayerIds.PHOTOS} false",
                "opacity ${MapLayerIds.FORECAST_CHANTERELLES} 0.5",
                "order ${MapLayerIds.FORECAST_CHANTERELLES},${MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS}",
            ),
            store.writes,
        )

        store.failWrites = true
        vm.onMapLayerVisibilityChanged(MapLayerIds.WAYPOINTS, false)
        advanceUntilIdle()
        assertEquals(listOf("Couldn't store a map layer choice."), logged.filter { "layer choice" in it && "read" !in it })
    }

    @Test
    fun `an opacity outside 0 to 1 is refused and logged, not clamped`() = runTest(dispatcher) {
        val store = RecordingLayerPreferences()
        val vm = viewModel(layerPreferences = store)
        advanceUntilIdle()

        vm.onMapLayerOpacityChanged(MapLayerIds.FORECAST_CHANTERELLES, 1.2f)
        advanceUntilIdle()

        assertEquals(1f, vm.uiState.value.mapLayers.stateOf(MapLayerIds.FORECAST_CHANTERELLES).opacity)
        assertEquals(emptyList<String>(), store.writes)
        assertEquals(1, logged.count { "1.2" in it })
    }

    @Test
    fun `showing the Maps tab asks the store which groups have data for this ISO week`() = runTest(dispatcher) {
        val store = FixedStore(ForecastAvailability.Groups(setOf("chanterelles")))
        val vm = viewModel(store = store)
        advanceUntilIdle()

        vm.onMapShown()
        advanceUntilIdle()

        assertEquals(week, vm.uiState.value.forecastWeek)
        assertEquals(setOf("chanterelles"), vm.uiState.value.forecastGroups)
        assertEquals(listOf(week), store.asked)

        store.availability = ForecastAvailability.NoForecastData
        vm.onMapShown()
        advanceUntilIdle()
        assertTrue("no forecast data: no group", vm.uiState.value.forecastGroups.isEmpty())
    }
}

private class RecordingLayerPreferences(
    private val stored: MapLayerPreferences = MapLayerPreferences.NONE,
    private val read: Result<MapLayerPreferences> = Result.success(stored),
) : MapLayerPreferencesRepository {
    val writes = mutableListOf<String>()
    var failWrites = false

    private fun write(entry: String): Result<Unit> =
        if (failWrites) Result.failure(IOException("unwritable")) else Result.success(Unit).also { writes += entry }

    override suspend fun getMapLayerPreferences(): Result<MapLayerPreferences> = read
    override suspend fun setLayerVisible(layerId: String, visible: Boolean) = write("visible $layerId $visible")
    override suspend fun setLayerOpacity(layerId: String, opacity: Float) = write("opacity $layerId $opacity")
    override suspend fun setLayerOrder(layerIds: List<String>) = write("order ${layerIds.joinToString(",")}")
}

private class FixedStore(var availability: ForecastAvailability) : ForecastCellStore {
    val asked = mutableListOf<LocalDate>()
    override suspend fun availability(week: LocalDate): ForecastAvailability = availability.also { asked += week }
    override suspend fun cells(group: String, week: LocalDate, blocks: Set<ForecastBlock>): ForecastCellsResult = ForecastCellsResult.NoForecastData
}

private object LayersMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getMapFullscreen(): Result<Boolean> = Result.success(false)
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = Result.success(Unit)
}

private object LayersStubUnitSystemPreferenceRepository : UnitSystemPreferenceRepository {
    override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(UnitSystem.IMPERIAL)
    override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
}

private object LayersUnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult =
        error("getCurrentLocation() is not part of this test's path and must not be called")
}

private object LayersNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object LayersEmptyRepository : MushroomRepository, TaxonSearchRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object LayersStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object LayersStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

private object LayersStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

private class LayersInMemoryPlannedTripRepository : PlannedTripRepository {
    private val trips = mutableMapOf<String, PlannedTrip>()
    override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(trips.values.toList())
    override suspend fun save(trip: PlannedTrip): Result<Unit> = Result.success(Unit).also { trips[trip.id] = trip }
    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit).also { trips.remove(id) }
}

private object LayersStubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

private object LayersStubAppThemePreferenceRepository : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}
