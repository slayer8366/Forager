package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.AppThemePreferenceRepository
import com.zynergylabs.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.zynergylabs.forager.app.domain.ComputeTripWindowsUseCase
import com.zynergylabs.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.zynergylabs.forager.app.domain.DeletePlannedTripUseCase
import com.zynergylabs.forager.app.domain.BasemapPreferenceRepository
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.MapIconClusterPlacement
import com.zynergylabs.forager.app.domain.MapIconClusterPlacementRepository
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
import com.zynergylabs.forager.app.domain.LastKnownLocationSource
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.NoLastKnownLocation
import com.zynergylabs.forager.app.domain.MapLayerPreferences
import com.zynergylabs.forager.app.domain.MapLayerPreferencesRepository
import com.zynergylabs.forager.app.domain.MapPreferencesRepository
import com.zynergylabs.forager.app.domain.MapRecords
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
import com.zynergylabs.forager.app.domain.NoReturnLeg
import com.zynergylabs.forager.app.domain.NoStoredWaypointNavigation
import com.zynergylabs.forager.app.domain.ReturnLeg
import com.zynergylabs.forager.app.domain.WaypointNavigationRepository
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
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.map.MapOverlayContent
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.layers.COLOUR_FIELDS
import com.zynergylabs.forager.app.ui.map.layers.ForecastCellsShown
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.log.CartographyUiState

/**
 * Shared fixtures for the Layers-sheet and legend screen tests (map layers L0b): a real
 * [AvailabilityViewModel] with fakes behind every collaborator, so a touch on the real screen goes
 * through the ViewModel's own handlers to the state the map slot is handed; a map slot that records
 * what it is handed and counts the plain taps that reach it; and the screen wired to the ViewModel.
 */

/** The Layers row's description at the default basemap (the dispatch, B1): Street since it became the opening map (dispatch 2026-10-18-04, Amendment 2; RECORD -1297); was Topographical. */
internal const val LAYERS_ROW_DESCRIPTION = "Layers: Street map. Choose the map type and overlays."

/** Both synthetic groups, as the debug store reports them with the switch on. */
internal val BOTH_FORECAST_GROUPS: Set<String> = COLOUR_FIELDS.map { it.group }.toSet()

/** A Monday: the ISO week the tests' stores are asked about. */
internal val MAP_LAYERS_TEST_WEEK: LocalDate = LocalDate.of(2026, 9, 28)

/** Stores what the sheet writes, as `verb id value` lines; reads back [stored]. */
internal class InMemoryLayerPreferences(var stored: MapLayerPreferences = MapLayerPreferences.NONE) : MapLayerPreferencesRepository {
    val writes = mutableListOf<String>()
    override suspend fun getMapLayerPreferences(): Result<MapLayerPreferences> = Result.success(stored)
    override suspend fun setLayerVisible(layerId: String, visible: Boolean): Result<Unit> = Result.success(Unit).also { writes += "visible $layerId $visible" }
    override suspend fun setLayerOpacity(layerId: String, opacity: Float): Result<Unit> = Result.success(Unit).also { writes += "opacity $layerId $opacity" }
    override suspend fun setLayerOrder(layerIds: List<String>): Result<Unit> = Result.success(Unit).also { writes += "order ${layerIds.joinToString(",")}" }
}

/** Reads back [stored] (nothing, by default) and keeps every key written, in order (dispatch 2026-09-28-708). */
internal class InMemoryBasemapPreference(var stored: String? = null) : BasemapPreferenceRepository {
    val writes = mutableListOf<String>()
    override suspend fun getBasemapKey(): Result<String?> = Result.success(stored)
    override suspend fun setBasemapKey(key: String): Result<Unit> = Result.success(Unit).also {
        writes += key
        stored = key
    }
}

/** Reads back [stored] (nothing, by default) and keeps every placement written, in order (RECORD -711). */
internal class InMemoryClusterPlacement(var stored: MapIconClusterPlacement? = null) : MapIconClusterPlacementRepository {
    val writes = mutableListOf<MapIconClusterPlacement>()
    override suspend fun getMapIconClusterPlacement(): Result<MapIconClusterPlacement?> = Result.success(stored)
    override suspend fun setMapIconClusterPlacement(placement: MapIconClusterPlacement): Result<Unit> = Result.success(Unit).also {
        writes += placement
        stored = placement
    }
}

/** A store with data for [groups] (none: "no forecast data") and no cells: the tests' map slot draws nothing. */
internal class FixedForecastStore(private val groups: Set<String>) : ForecastCellStore {
    override suspend fun availability(week: LocalDate): ForecastAvailability =
        if (groups.isEmpty()) ForecastAvailability.NoForecastData else ForecastAvailability.Groups(groups)

    override suspend fun cells(group: String, week: LocalDate, blocks: Set<ForecastBlock>): ForecastCellsResult =
        ForecastCellsResult.NoForecastData
}

/**
 * A map slot that records the [MapRenderMode] and [MapOverlayContent] it was last handed, counts every
 * plain tap that reaches it (so a test can tell a touch reached the map, not a control over it), and
 * reports [shown] as the dates of the cells in view through the forecast feed, as `SightingsMap` does.
 */
/**
 * [zoomedOut] is what this stand-in map reports through `MapForecastFeed.onZoomedOutChanged`, as the real
 * map does at each camera idle (data part C): whether the camera is below the colour fields' minimum zoom.
 */
internal class LayersRecordingMapSlot(
    private val shown: Map<String, ForecastCellsShown> = emptyMap(),
    private val zoomedOut: Boolean = false,
) {
    var renderMode: MapRenderMode? = null
    var content: MapOverlayContent? = null
    var taps = 0

    val slot: MapSlot = { _, content, renderMode, _, _, onTap, _, _, modifier ->
        this.renderMode = renderMode
        this.content = content
        val feed = renderMode.forecast
        LaunchedEffect(feed?.groupsByLayer) {
            feed?.onCellsShown?.invoke(shown.filterKeys { it in feed.groupsByLayer })
            feed?.onZoomedOutChanged?.invoke(zoomedOut)
        }
        Box(
            modifier
                .testTag("map-slot")
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    taps++
                    onTap()
                },
        )
    }
}

internal fun mapLayersViewModel(
    getMapRecords: suspend () -> MapRecords = { MapRecords.NONE },
    layerPreferences: MapLayerPreferencesRepository = InMemoryLayerPreferences(),
    store: ForecastCellStore = AbsentForecastCellStore,
    errorLog: ErrorLog = ErrorLog { _, _, _ -> },
    // M1: records the ViewModel loads at start, for the bubbles' lookups.
    plannedTrips: List<PlannedTrip> = emptyList(),
    offlineRegions: List<OfflineRegionSummary> = emptyList(),
    // Dispatch 2026-09-28-312: a store whose delete a test can hold; by default the fixed list, whose delete fails.
    offlineMapRepository: OfflineMapRepository = MapLayersUiOfflineMapRepository(offlineRegions),
    // Dispatch 2026-09-28-502: where the waypoint navigation is kept, and the return it pauses. Defaulted to none, as before.
    waypointNavigationRepository: WaypointNavigationRepository = NoStoredWaypointNavigation,
    returnLeg: ReturnLeg = NoReturnLeg,
    // Dispatch 2026-09-28-510: the live fixes and the platform's last known location. Defaulted to none, as before.
    locationTracker: LocationTracker = MapLayersUiLocationTracker,
    lastKnownLocation: LastKnownLocationSource = NoLastKnownLocation,
    // Dispatch 2026-09-28-592: the Sundown settings, stored and handed to the watch. Defaulted to the repository's defaults.
    getSundownAlertsEnabled: suspend () -> Result<Boolean> = { Result.success(true) },
    setSundownAlertsEnabled: suspend (Boolean) -> Result<Unit> = { Result.success(Unit) },
    getDarknessMarginMinutes: suspend () -> Result<Int> = { Result.success(60) },
    setDarknessMarginMinutes: suspend (Int) -> Result<Unit> = { Result.success(Unit) },
    onDarknessMarginStored: (Int) -> Unit = {},
    // Dispatch 2026-09-28-626: Settings' "Off-track reminder". Defaulted to the repository's default, on.
    getOffTrackReminderEnabled: suspend () -> Result<Boolean> = { Result.success(true) },
    setOffTrackReminderEnabled: suspend (Boolean) -> Result<Unit> = { Result.success(Unit) },
    // Dispatch 2026-09-28-767: the quick menu's "Battery saver". Defaulted to the repository's default, off.
    getBatterySaverEnabled: suspend () -> Result<Boolean> = { Result.success(false) },
    setBatterySaverEnabled: suspend (Boolean) -> Result<Unit> = { Result.success(Unit) },
    // Dispatch 2026-09-28-708: where the Maps tab's basemap is kept. Defaulted to nothing stored, which opens on the default, Street since Amendment 2 of dispatch 2026-10-18-04.
    basemapPreferences: BasemapPreferenceRepository = InMemoryBasemapPreference(),
    // RECORD -711: where the icon cluster's side and height are kept. Defaulted to nothing stored, which opens it where it always opened.
    clusterPlacements: MapIconClusterPlacementRepository = InMemoryClusterPlacement(),
): AvailabilityViewModel {
    val searchCache = InMemorySearchCacheRepository()
    val plannedTripRepository = MapLayersUiPlannedTripRepository(plannedTrips)
    return AvailabilityViewModel(
        locationProvider = MapLayersUiLocationProvider,
        locationTracker = locationTracker,
        getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(MapLayersUiEmptyRepository), searchCache),
        getRecentSearches = GetRecentSearchesUseCase(searchCache),
        getSightings = GetSightingsUseCase(MapLayersUiEmptyRepository),
        searchTaxa = SearchTaxaUseCase(MapLayersUiEmptyRepository),
        getConditions = GetConditionsUseCase(MapLayersUiWeatherProvider),
        getTripWindows = GetTripWindowsUseCase(MapLayersUiTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
        getPlannedTrips = GetPlannedTripsUseCase(plannedTripRepository),
        savePlannedTrip = SavePlannedTripUseCase(plannedTripRepository),
        deletePlannedTrip = DeletePlannedTripUseCase(plannedTripRepository),
        getSeasonalPattern = GetSeasonalPatternUseCase(
            GetSightingsUseCase(MapLayersUiEmptyRepository),
            MapLayersUiHistoricalWeatherProvider,
            ComputeFruitingLagDistributionUseCase(),
        ),
        offlineMapRepository = offlineMapRepository,
        errorLog = errorLog,
        mapPreferencesRepository = MapLayersUiMapPreferencesRepository,
        unitSystemPreferenceRepository = MapLayersUiUnitSystemPreferenceRepository,
        appThemePreferenceRepository = MapLayersUiAppThemePreferenceRepository,
        getTodaysForecast = GetTodaysForecastUseCase(MapLayersUiTripPlanningWeatherProvider),
        getMapRecords = getMapRecords,
        mapLayerPreferencesRepository = layerPreferences,
        forecastCellStore = store,
        today = { MAP_LAYERS_TEST_WEEK },
        waypointNavigationRepository = waypointNavigationRepository,
        returnLeg = returnLeg,
        lastKnownLocation = lastKnownLocation,
        getSundownAlertsEnabled = getSundownAlertsEnabled,
        setSundownAlertsEnabled = setSundownAlertsEnabled,
        getDarknessMarginMinutes = getDarknessMarginMinutes,
        setDarknessMarginMinutes = setDarknessMarginMinutes,
        onDarknessMarginStored = onDarknessMarginStored,
        getOffTrackReminderEnabled = getOffTrackReminderEnabled,
        setOffTrackReminderEnabled = setOffTrackReminderEnabled,
        getBatterySaverEnabled = getBatterySaverEnabled,
        setBatterySaverEnabled = setBatterySaverEnabled,
        basemapPreferenceRepository = basemapPreferences,
        mapIconClusterPlacementRepository = clusterPlacements,
    )
}

/** The real screen, every input from [viewModel], and the Layers sheet's three changes wired to it. */
@Composable
internal fun MapLayersTestScreen(
    viewModel: AvailabilityViewModel,
    mapSlot: MapSlot,
    store: ForecastCellStore,
    logUiState: MushroomLogUiState = MushroomLogUiState(),
    // M1: the records the glyph bubbles look up, and the find routes.
    waypoints: List<Waypoint> = emptyList(),
    tracks: List<Track> = emptyList(),
    onOpenLogEntry: (String) -> Unit = {},
    onCloseLogEntry: () -> Unit = {},
    // Item 8 (dispatch 2026-09-29-57): the report's delete and its edit and save, for the return-to-map tests.
    onDeleteLogEntry: (String) -> Unit = {},
    onStartEditingLogEntry: () -> Unit = {},
    onSaveLogEntry: () -> Unit = {},
    cartographyUiState: CartographyUiState = CartographyUiState(),
    getCartographyEntryMapData: suspend (CartographyEntry, List<GalleryPhoto>) -> CartographyEntryMapData = { _, _ -> CartographyEntryMapData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()) },
    // Applied to the ViewModel's state before the screen reads it, for a state the ViewModel cannot reach (sightings
    // held with no region); identity by default.
    uiStateTransform: (AvailabilityUiState) -> AvailabilityUiState = { it },
) {
    val viewModelState by viewModel.uiState.collectAsState()
    val uiState = uiStateTransform(viewModelState)
    AvailabilityScreen(
        uiState = uiState,
        logUiState = logUiState,
        onUseCurrentLocation = viewModel::useCurrentLocation,
        onManualLatChanged = viewModel::onManualLatChanged,
        onManualLngChanged = viewModel::onManualLngChanged,
        onSearchManualCoordinates = viewModel::searchManualCoordinates,
        onRadiusChanged = viewModel::onRadiusChanged,
        onMonthSelected = viewModel::onMonthSelected,
        onMapTabSelected = viewModel::onMapTabSelected,
        onSeasonalTabSelected = viewModel::onSeasonalTabSelected,
        onTaxonSearchQueryChanged = viewModel::onTaxonSearchQueryChanged,
        onTaxonSearchResultSelected = viewModel::onTaxonSearchResultSelected,
        onDismissTaxonSuggestions = viewModel::onDismissTaxonSuggestions,
        onReopenTaxonSuggestions = viewModel::onReopenTaxonSuggestions,
        onPlaceTripPin = viewModel::onPlaceTripPin,
        onDeletePlannedTrip = viewModel::onDeletePlannedTrip,
        onRecentSearchSelected = viewModel::onRecentSearchSelected,
        onOfflineMapLatChanged = viewModel::onOfflineMapLatChanged,
        onOfflineMapLngChanged = viewModel::onOfflineMapLngChanged,
        onOfflineMapRadiusChanged = viewModel::onOfflineMapRadiusChanged,
        onOfflineMapNameChanged = viewModel::onOfflineMapNameChanged,
        onOfflineMapsOpened = viewModel::onOfflineMapsOpened,
        onDownloadOfflineMaps = viewModel::onDownloadOfflineMaps,
        onDeleteOfflineRegion = viewModel::onDeleteOfflineRegion,
        onNightModeMapsChanged = viewModel::onNightModeMapsChanged,
        onThemeModeChanged = viewModel::onThemeModeChanged,
        onMapFullscreenChanged = viewModel::onMapFullscreenChanged,
        mapSlot = mapSlot,
        onMapShown = viewModel::onMapShown,
        onMapLayerVisibilityChanged = viewModel::onMapLayerVisibilityChanged,
        onMapLayerOpacityChanged = viewModel::onMapLayerOpacityChanged,
        onColourFieldMoved = viewModel::onColourFieldMoved,
        onMapModeSelected = viewModel::onMapModeSelected,
        onMapIconClusterPlacementChanged = viewModel::onMapIconClusterPlacementChanged,
        forecastCellStore = store,
        waypoints = waypoints,
        tracks = tracks,
        onOpenLogEntry = onOpenLogEntry,
        onCloseLogEntry = onCloseLogEntry,
        onDeleteLogEntry = onDeleteLogEntry,
        onStartEditingLogEntry = onStartEditingLogEntry,
        onSaveLogEntry = onSaveLogEntry,
        cartographyUiState = cartographyUiState,
        getCartographyEntryMapData = getCartographyEntryMapData,
    )
}

private object MapLayersUiMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getMapFullscreen(): Result<Boolean> = Result.success(false)
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = Result.success(Unit)
}

private object MapLayersUiUnitSystemPreferenceRepository : UnitSystemPreferenceRepository {
    override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(UnitSystem.METRIC)
    override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
}

private object MapLayersUiLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult = LocationResult.LocationUnavailable
}

private object MapLayersUiLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object MapLayersUiEmptyRepository : MushroomRepository, TaxonSearchRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object MapLayersUiWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object MapLayersUiTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows are not exercised by these tests"))
}

private object MapLayersUiHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("the seasonal pattern is not exercised by these tests"))
}

private class MapLayersUiPlannedTripRepository(initial: List<PlannedTrip> = emptyList()) : PlannedTripRepository {
    private val trips = initial.associateByTo(mutableMapOf()) { it.id }
    override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(trips.values.toList())
    override suspend fun save(trip: PlannedTrip): Result<Unit> = Result.success(Unit).also { trips[trip.id] = trip }
    override suspend fun delete(id: String): Result<Unit> = Result.success(Unit).also { trips.remove(id) }
}

private class MapLayersUiOfflineMapRepository(private val regions: List<OfflineRegionSummary>) : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline downloads are not exercised by these tests"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline downloads are not exercised by these tests"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(regions)
}

private object MapLayersUiAppThemePreferenceRepository : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}
