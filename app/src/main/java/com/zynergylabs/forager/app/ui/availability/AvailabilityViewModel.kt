package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.EntryReferenceCountSource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zynergylabs.forager.app.domain.DEFAULT_DARKNESS_MARGIN_MINUTES
import com.zynergylabs.forager.app.domain.AppThemePreferenceRepository
import com.zynergylabs.forager.app.domain.BasemapPreferenceRepository
import com.zynergylabs.forager.app.domain.MapIconClusterPlacement
import com.zynergylabs.forager.app.domain.MapIconClusterPlacementRepository
import com.zynergylabs.forager.app.domain.CachedSearchSummary
import com.zynergylabs.forager.app.domain.DeletePlannedTripUseCase
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.ForagingSelection
import com.zynergylabs.forager.app.domain.GetAvailabilityUseCase
import com.zynergylabs.forager.app.domain.GetConditionsUseCase
import com.zynergylabs.forager.app.domain.GetPlannedTripsUseCase
import com.zynergylabs.forager.app.domain.GetRecentSearchesUseCase
import com.zynergylabs.forager.app.domain.GetSeasonalPatternUseCase
import com.zynergylabs.forager.app.domain.GetSightingsUseCase
import com.zynergylabs.forager.app.domain.GetTodaysForecastUseCase
import com.zynergylabs.forager.app.domain.GetTripWindowsUseCase
import com.zynergylabs.forager.app.domain.LastKnownLocationSource
import com.zynergylabs.forager.app.domain.NoLastKnownLocation
import com.zynergylabs.forager.app.domain.NoBarometer
import com.zynergylabs.forager.app.domain.NoSeaLevelConverter
import com.zynergylabs.forager.app.domain.PressureProvider
import com.zynergylabs.forager.app.domain.SeaLevelConverter
import com.zynergylabs.forager.app.domain.LocationProvider
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.ForecastCellStore
import com.zynergylabs.forager.app.domain.MapLayerPreferencesRepository
import com.zynergylabs.forager.app.domain.MapPreferencesRepository
import com.zynergylabs.forager.app.domain.MapRecords
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.NoReturnLeg
import com.zynergylabs.forager.app.domain.NoStoredWaypointNavigation
import com.zynergylabs.forager.app.domain.PredictAvailabilityUseCase
import com.zynergylabs.forager.app.domain.ReturnLeg
import com.zynergylabs.forager.app.domain.WaypointNavigation
import com.zynergylabs.forager.app.domain.WaypointNavigationRepository
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.SystemCurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointResult
import com.zynergylabs.forager.app.domain.SavePlannedTripUseCase
import com.zynergylabs.forager.app.domain.SearchTaxaUseCase
import com.zynergylabs.forager.app.domain.estimateOfflineTileCount
import com.zynergylabs.forager.app.domain.estimateServedOfflineTileCount
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.ui.log.PendingDeleteCommitScope
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.layers.ColourFieldMove
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AvailabilityViewModel(
    private val locationProvider: LocationProvider,
    /** See [AvailabilityUiState.liveLocation]'s own doc comment for why this is collected separately from [locationProvider]'s one-shot fetch. */
    private val locationTracker: LocationTracker,
    /**
     * The ranked-list search, cache-aware: live first, the offline cache only when live fails.
     * [PredictAvailabilityUseCase] is deliberately *not* a dependency of this class any more — the
     * fallback decision belongs in one place (see [GetAvailabilityUseCase]) rather than as another
     * branch in [refresh].
     */
    private val getAvailability: GetAvailabilityUseCase,
    private val getRecentSearches: GetRecentSearchesUseCase,
    private val getSightings: GetSightingsUseCase,
    private val searchTaxa: SearchTaxaUseCase,
    private val getConditions: GetConditionsUseCase,
    private val getTripWindows: GetTripWindowsUseCase,
    private val getPlannedTrips: GetPlannedTripsUseCase,
    private val savePlannedTrip: SavePlannedTripUseCase,
    private val deletePlannedTrip: DeletePlannedTripUseCase,
    private val getSeasonalPattern: GetSeasonalPatternUseCase,
    private val offlineMapRepository: OfflineMapRepository,
    /**
     * Logs a failure's throwable for diagnosis, without ever exposing its text to the user — see
     * [ErrorLog]'s own doc comment for why this exists rather than calling [android.util.Log]
     * directly. Defaults to discarding the throwable, which is exactly what makes every existing
     * test safe under a plain JVM run with no per-test setup; `MainActivity` wires the real
     * `Log.w`-backed one for production.
     */
    private val errorLog: ErrorLog = ErrorLog { _, _, _ -> },
    private val mapPreferencesRepository: MapPreferencesRepository,
    private val unitSystemPreferenceRepository: UnitSystemPreferenceRepository,
    private val appThemePreferenceRepository: AppThemePreferenceRepository,
    private val getTodaysForecast: GetTodaysForecastUseCase,
    /** How many journal entries keep each offline region, live, for its delete message (dispatch 2026-10-28-08). No default, so a factory that leaves it out does not compile (Sorrel's N1). */
    private val entryReferenceCounts: EntryReferenceCountSource,
    /**
     * Settings' "Automatically Save Location to Photos" preference, read and written as two plain
     * functions rather than by taking
     * [com.zynergylabs.forager.app.domain.PhotoLocationPreferenceRepository] itself — the same
     * "borrow the one capability, not the whole collaborator" shape the offline region counts
     * used here until dispatch 2026-10-28-08. Defaulted so
     * the thirteen existing suites that construct this ViewModel and never touch this setting need
     * no stub apiece; `MainActivity` wires both to the real repository. `Result`-returning, so the
     * read and write fold exactly the way [AvailabilitySettingsPrefs.loadNightModePreferences] does.
     */
    private val getAutoSaveLocationToPhotos: suspend () -> Result<Boolean> = { Result.success(true) },
    private val setAutoSaveLocationToPhotos: suspend (Boolean) -> Result<Unit> = { Result.success(Unit) },
    /**
     * Settings' "Sundown" section (dispatch 2026-09-28-592, plan task T4): the alerts switch and the
     * darkness margin, from [com.zynergylabs.forager.app.domain.SundownPreferencesRepository], the
     * same borrowed-capability shape as the pairs above, defaulted to that repository's defaults.
     */
    private val getSundownAlertsEnabled: suspend () -> Result<Boolean> = { Result.success(true) },
    private val setSundownAlertsEnabled: suspend (Boolean) -> Result<Unit> = { Result.success(Unit) },
    private val getDarknessMarginMinutes: suspend () -> Result<Int> = { Result.success(DEFAULT_DARKNESS_MARGIN_MINUTES) },
    private val setDarknessMarginMinutes: suspend (Int) -> Result<Unit> = { Result.success(Unit) },
    /**
     * Told a new margin once it is stored, so the sundown line's start-back time moves at once
     * ([com.zynergylabs.forager.app.domain.SundownWatch.onMarginChanged]); `MainActivity` wires it to
     * the watch. Not called when storing fails: the alerts read the stored margin, and the line must
     * not show a time the alerts do not use.
     */
    private val onDarknessMarginStored: (Int) -> Unit = {},
    /**
     * Settings' "Off-track reminder" (dispatch 2026-09-28-626, plan T14), from
     * [com.zynergylabs.forager.app.domain.OffTrackReminderPreferenceRepository]: the same
     * borrowed-capability shape as the Sundown pair, defaulted to that repository's default, on.
     */
    private val getOffTrackReminderEnabled: suspend () -> Result<Boolean> = { Result.success(true) },
    private val setOffTrackReminderEnabled: suspend (Boolean) -> Result<Unit> = { Result.success(Unit) },
    /**
     * The quick menu's "Battery saver" (dispatch 2026-09-28-767), from
     * [com.zynergylabs.forager.app.domain.BatterySaverPreferenceRepository]; the same shape as the pair
     * above, defaulted to that repository's default, off.
     */
    private val getBatterySaverEnabled: suspend () -> Result<Boolean> = { Result.success(false) },
    private val setBatterySaverEnabled: suspend (Boolean) -> Result<Unit> = { Result.success(Unit) },
    /**
     * Where an offline-region delete still pending when this ViewModel is cleared is committed
     * (journal redesign J4): `viewModelScope` is cancelled by then. See [PendingDeleteCommitScope].
     */
    private val pendingDeleteCommitScope: CoroutineScope = PendingDeleteCommitScope,
    /** Map layers L0b, B2: every saved record the Maps tab draws. Defaulted like the borrowed capabilities above. */
    private val getMapRecords: suspend () -> MapRecords = { MapRecords.NONE },
    /** Map layers L0b, B3: where the Layers sheet's choices persist. */
    private val mapLayerPreferencesRepository: MapLayerPreferencesRepository = NoStoredMapLayerPreferences,
    /** Map layers L0b, B5: which forecast groups have cells to draw. */
    private val forecastCellStore: ForecastCellStore = AbsentForecastCellStore,
    /** Today, for the forecast's ISO week. */
    private val today: () -> LocalDate = { LocalDate.now() },
    /** Dispatch 2026-09-28-502: where the waypoint being navigated to is kept across the process. */
    private val waypointNavigationRepository: WaypointNavigationRepository = NoStoredWaypointNavigation,
    /** Dispatch 2026-09-28-502, step 6: the return a waypoint navigation pauses and picks back up. */
    internal val returnLeg: ReturnLeg = NoReturnLeg,
    /** Dispatch 2026-09-28-510: the platform's last known location, shown greyed until anything live arrives. */
    private val lastKnownLocation: LastKnownLocationSource = NoLastKnownLocation,
    /** Dispatch 2026-09-28-708: where the Maps tab's basemap persists across restarts. */
    private val basemapPreferenceRepository: BasemapPreferenceRepository = NoStoredBasemap,
    /** RECORD -711: where the Maps tab's icon cluster's side and height persist across restarts. */
    private val mapIconClusterPlacementRepository: MapIconClusterPlacementRepository = NoStoredMapIconClusterPlacement,
    private val crowdedIconsRepository: com.zynergylabs.forager.app.domain.CrowdedIconsPreferenceRepository = NoStoredCrowdedIcons,
    /** The maps' "no signal" state (NoSignalTrailMap.kt), read when Offline maps opens; defaulted to "no signal", so nothing fetches. */
    internal val noSignal: () -> Boolean = { true },
    /**
     * The service's report that it has stored a track's end (dispatch 2026-10-24-07, RECORD -1605):
     * `AppContainer.trackEnds`, the same report the recording screen reloads its track list on.
     * Defaulted to no report, as [com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel] does.
     */
    private val trackEnds: StateFlow<String?> = MutableStateFlow(null),
    /** Step 6 PR 1 (RECORD -1657): the waypoint being navigated to, read by id for its line. Defaulted to none stored. */
    private val readWaypoint: suspend (String) -> Result<Waypoint?> = { Result.success(null) },
    /** Step 6 PR 1 (RECORD -1655): the clock the waypoint line's freshness is read against. */
    private val currentTime: CurrentTimeProvider = SystemCurrentTimeProvider,
    /** Step 6 PR 3: the way to a waypoint along trails (AppContainer.findWayToWaypoint); `null`, none (tests), is today's straight line. */
    private val findWayToWaypoint: (suspend (LatLng, LatLng) -> WayToWaypointResult)? = null,
    /** Dispatch 2026-10-28-13: the barometer and the sea-level converter behind the live height ([AvailabilityLiveLocation]). Defaulted to none: "Alt unavailable". */
    private val pressureProvider: PressureProvider = NoBarometer,
    private val seaLevelConverter: SeaLevelConverter = NoSeaLevelConverter,
) : ViewModel() {

    // mapIconClusterPlacement starts null here, not at its data-class default: this ViewModel has a read to
    // wait for (loadMapIconClusterPlacement), and the screen draws no cluster until it lands. See the field.
    internal val _uiState = MutableStateFlow(AvailabilityUiState(mapIconClusterPlacement = null))
    val uiState: StateFlow<AvailabilityUiState> = _uiState.asStateFlow()

    /**
     * Whether the hosting Activity is between `ON_START` and `ON_STOP`. Gates every start of
     * [AvailabilityLiveLocation.begin] so a start cannot be triggered from the background — see
     * [onEnteredForeground].
     */
    private var isForegrounded: Boolean = false

    // The collaborators below must stay above `init`: Kotlin runs property initializers and `init` in source
    // order, and `init` calls them (UI split Phase 2.4).

    /**
     * The planned trips, moved to their own class by UI split Phase 2.4 PR 1. It writes this screen's state
     * through the setter here and launches in this ViewModel's own scope.
     */
    internal val plannedTrips = AvailabilityPlannedTrips(
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        getPlannedTrips = getPlannedTrips,
        savePlannedTrip = savePlannedTrip,
        deletePlannedTrip = deletePlannedTrip,
        errorLog = errorLog,
    )

    /**
     * The settings this screen stores, moved to their own class by UI split Phase 2.4 PR 2. It writes this
     * screen's state through the setter here and launches in this ViewModel's own scope.
     */
    internal val settingsPrefs = AvailabilitySettingsPrefs(
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        mapPreferencesRepository = mapPreferencesRepository,
        unitSystemPreferenceRepository = unitSystemPreferenceRepository,
        appThemePreferenceRepository = appThemePreferenceRepository,
        getAutoSaveLocationToPhotos = getAutoSaveLocationToPhotos,
        setAutoSaveLocationToPhotos = setAutoSaveLocationToPhotos,
        getSundownAlertsEnabled = getSundownAlertsEnabled,
        setSundownAlertsEnabled = setSundownAlertsEnabled,
        getDarknessMarginMinutes = getDarknessMarginMinutes,
        setDarknessMarginMinutes = setDarknessMarginMinutes,
        onDarknessMarginStored = onDarknessMarginStored,
        getOffTrackReminderEnabled = getOffTrackReminderEnabled,
        setOffTrackReminderEnabled = setOffTrackReminderEnabled,
        getBatterySaverEnabled = getBatterySaverEnabled,
        setBatterySaverEnabled = setBatterySaverEnabled,
        errorLog = errorLog,
        crowdedIconsRepository = crowdedIconsRepository,
    )

    /**
     * The Maps tab's records, layers, basemap and icon cluster placement, moved to their own class by UI
     * split Phase 2.4 PR 3. It reads and writes this screen's state through the getter and setter here and
     * launches in this ViewModel's own scope.
     */
    private val mapDisplay = AvailabilityMapDisplay(
        uiState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        getMapRecords = getMapRecords,
        forecastCellStore = forecastCellStore,
        today = today,
        mapLayerPreferencesRepository = mapLayerPreferencesRepository,
        basemapPreferenceRepository = basemapPreferenceRepository,
        mapIconClusterPlacementRepository = mapIconClusterPlacementRepository,
        errorLog = errorLog,
    )

    /**
     * The offline-map picker, the region list and the two downloads' launches, moved to their own class by UI
     * split Phase 2.4 PR 4. Whether a download starts stays in [onDownloadAgain] and [onDownloadOfflineMaps].
     * It writes this screen's state through the setter here and launches in this ViewModel's own scope.
     */
    internal val offlineMaps: AvailabilityOfflineMaps = AvailabilityOfflineMaps(
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        offlineMapRepository = offlineMapRepository,
        mapPreferencesRepository = mapPreferencesRepository,
        locationProvider = locationProvider,
        entryReferenceCounts = entryReferenceCounts,
        errorLog = errorLog,
        onDownloaded = { region -> offlineRoutes.noteDownloaded(region) },
    )

    internal val offlineRoutes: AvailabilityOfflineRoutes = AvailabilityOfflineRoutes(
        updateState = { transform -> _uiState.update(transform) },
        currentState = { _uiState.value },
        scope = { viewModelScope },
        offlineMapRepository = offlineMapRepository,
        reloadRegions = { offlineMaps.loadOfflineRegions() },
        errorLog = errorLog,
    )

    /**
     * The one offline region whose delete is pending (journal redesign J4), moved to its own class by UI split
     * Phase 2.4 PR 5. It reads and writes this screen's state through the getter and setter here and launches
     * in this ViewModel's own scope; [onCleared] commits what is still pending, at the same point as before.
     */
    internal val offlineRegionDeletes = AvailabilityOfflineRegionDeletes(
        uiState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        pendingDeleteCommitScope = pendingDeleteCommitScope,
        offlineMapRepository = offlineMapRepository,
        errorLog = errorLog,
        dropFromMapRecords = { id -> mapDisplay.dropOfflineRegionFromMapRecords(id) },
        reloadOfflineRegions = { offlineMaps.loadOfflineRegions() },
    )

    /**
     * Where the waypoint navigation is kept across the process, and whether the walker chose one here, moved
     * to their own class by UI split Phase 2.4 PR 6. [onNavigateToWaypoint] and [onStopWaypointNavigation]
     * stay in this class: they pause and resume the return. It writes this screen's state through the setter
     * here and launches in this ViewModel's own scope.
     */
    internal val waypointNavigationStore = AvailabilityWaypointNavigationStore(
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        waypointNavigationRepository = waypointNavigationRepository,
        errorLog = errorLog,
    )

    /** The straight line to the waypoint being navigated to, moved out of the screen by step 6 PR 1: see the class. */
    private val waypointLine = AvailabilityWaypointLine({ transform -> _uiState.update(transform) }, readWaypoint, currentTime, errorLog)
    /** The way to that waypoint along trails (step 6 PR 3): see the class. Started and ended beside the line, below. */
    private val waypointRoute =
        AvailabilityWaypointRoute({ transform -> _uiState.update(transform) }, readWaypoint, findWayToWaypoint, { _uiState.value.offlineRegions }, currentTime, errorLog)

    /**
     * The species lookup (its one job and the debounce) and the search inputs' plain writes, moved to their own
     * class by UI split Phase 2.4 PR 7. Whether a lookup starts, and each cancel, stay in this class's
     * functions: [onTaxonSearchQueryChanged], [onDismissTaxonSuggestions] and [clearSearch]. It writes this
     * screen's state through the setter here and launches in this ViewModel's own scope.
     */
    private val speciesLookup = AvailabilitySpeciesLookup(
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        searchTaxa = searchTaxa,
        errorLog = errorLog,
    )

    /**
     * The search's fetches and what they were fetched for, moved to their own class by UI split Phase 2.4 PR 8:
     * the parent job every search fetch runs in, the search last run, and the queries the map's sightings and
     * the seasonal pattern were loaded for. Whether each fetch starts, and the cancel and renewal of the parent,
     * stay in this class's functions: [refresh], [onMapTabSelected], [onSeasonalTabSelected] and [clearSearch].
     * It writes this screen's state through the setter here and launches in this ViewModel's own scope.
     */
    private val searchRuns = AvailabilitySearchRuns(
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        getAvailability = getAvailability,
        loadRecentSearches = { loadRecentSearches() },
        getSightings = getSightings,
        getConditions = getConditions,
        getTripWindows = getTripWindows,
        getSeasonalPattern = getSeasonalPattern,
        getTodaysForecast = getTodaysForecast,
        errorLog = errorLog,
    )

    /**
     * The live fix: the one collection of [LocationTracker.fixes] and what it writes, moved to its own class by
     * UI split Phase 2.4 PR 9. Whether it starts or stops stays in [onEnteredForeground], [onLeftForeground] and
     * [onLocationPermissionGranted], gated by [isForegrounded] as before. It writes this screen's state through
     * the setter here and launches in this ViewModel's own scope.
     */
    private val liveLocation = AvailabilityLiveLocation(
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        locationTracker = locationTracker,
        lastKnownLocation = lastKnownLocation,
        pressureProvider = pressureProvider,
        seaLevelConverter = seaLevelConverter,
        errorLog = errorLog,
    )

    /** The locate-me button, moved to its own class by dispatch 2026-10-28-13 (the modular rule): see the class. */
    private val locateMeButton = AvailabilityLocateMe({ _uiState.value }, { transform -> _uiState.update(transform) }, { viewModelScope }, locationProvider)

    init {
        // Independent of any search — see AvailabilityUiState.plannedTrips — so this loads once
        // up front rather than waiting on a region the way sightings and trip windows do.
        plannedTrips.loadPlannedTrips()
        // Same reasoning, and the reason it matters more here: the picker is how somebody with no
        // connection gets back to a result at all, so it has to be populated before the first
        // search rather than as a side effect of one.
        loadRecentSearches()
        offlineMaps.watchReferenceCounts()
        offlineMaps.loadOfflineRegions()
        offlineMaps.loadOfflineMapPreferences()
        settingsPrefs.loadUnitSystemPreference()
        settingsPrefs.loadNightModePreferences()
        settingsPrefs.loadAutoSaveLocationToPhotos()
        settingsPrefs.loadSundownPreferences()
        settingsPrefs.loadOffTrackReminder()
        settingsPrefs.loadBatterySaver()
        settingsPrefs.loadMapFullscreenPreference()
        settingsPrefs.loadThemeModePreference()
        mapDisplay.loadMapLayerPreferences()
        mapDisplay.loadBasemapPreference()
        mapDisplay.loadMapIconClusterPlacement()
        waypointNavigationStore.loadWaypointNavigation()
        // Step 6 PR 1: the waypoint line runs while a waypoint is navigated to, started when one is chosen or picked back
        // up and cancelled when it ends or another is chosen. Decided here, not in the collaborator (RECORD -1450).
        viewModelScope.launch {
            _uiState.map { it.waypointNavigation?.waypointId }.distinctUntilChanged().collectLatest { id ->
                val fixes = _uiState.map { it.liveFix }.distinctUntilChanged()
                // Step 6 PR 3: the way along trails runs beside the line, for the same navigation, and ends with it.
                coroutineScope {
                    launch { waypointRoute.follow(id, fixes) }
                    waypointLine.follow(id, fixes)
                }
            }
        }
        // Dispatch 2026-10-24-07 (RECORD -1605): a finished track is on the map right after Stop. Recording
        // happens on the Maps tab, so the tab-shown reload never runs at a Stop; the records reload when the
        // service reports the stored end instead, which is never before the row reads ended (TrackEnds).
        viewModelScope.launch { trackEnds.collect { endedTrackId -> if (endedTrackId != null) mapDisplay.loadMapRecords() } }
        // The compass strip's live coordinates are NOT started here any more. Construction-time
        // collection ran on viewModelScope, which cancels at onCleared() -- Activity destruction,
        // not stop -- so the OS listener stayed registered while the app was backgrounded and
        // `awaitClose { removeUpdates(listener) }` never ran. Collection is now driven by
        // [onEnteredForeground]/[onLeftForeground] from the hosting Activity's lifecycle, so the
        // subscription is released on ON_STOP and re-acquired on ON_START.
        //
        // "Any time the map is open" -- the explicit ask this answers -- is unchanged: the Activity
        // is STARTED whenever any of this app's screens is on top, so every screen that had a fix
        // before still has one. What changed is only what happens when no screen is.
    }

    /**
     * The hosting Activity reached `ON_START`. Acquires the live-fix subscription.
     *
     * **Why lifecycle and not construction.** `viewModelScope` cancels at `onCleared()` — Activity
     * *destruction*, not stop. A backgrounded Activity is stopped, not destroyed, so a
     * construction-time collection outlived every backgrounding: the `callbackFlow` was never
     * closed, its `awaitClose { locationManager.removeUpdates(listener) }` never ran, and the OS
     * listener stayed registered on both GPS and network providers with no screen showing. What
     * limited delivery was the platform withholding background location from an app that does not
     * declare `ACCESS_BACKGROUND_LOCATION` — not anything this app did.
     *
     * Idempotent: a start while already collecting is a no-op, so a re-delivered `ON_START` cannot
     * stack a second OS registration on top of the ones this app already runs while recording.
     *
     * **Deliberately not need-gating.** This still subscribes on every tab, at the same 1-second
     * floor, whether or not anything is consuming fixes. Narrowing *when* the app subscribes while
     * foregrounded is a separate, larger change; releasing on background is orthogonal to it and
     * survives it.
     */
    fun onEnteredForeground() {
        isForegrounded = true
        if (!liveLocation.isCollecting) liveLocation.begin()
    }

    /**
     * The hosting Activity reached `ON_STOP`. Releases the live-fix subscription.
     *
     * Cancelling the collection completes the `callbackFlow`, which is what actually runs
     * `removeUpdates(listener)` — the OS registration goes away rather than merely being ignored.
     *
     * **This does not touch a running recording.** `TrackRecordingService` collects
     * [LocationTracker.fixes] on its own (`TrackRecordingService.kt`), as a foreground service, and
     * `TrackRecordingViewModel` collects its own stream. Neither is this job, and neither is
     * affected by this call.
     *
     * **Corrected (resync dispatch).** This paragraph used to add that the recording ViewModel's
     * stream is "bounded by the recording itself — started when recording starts and cancelled when
     * it stops". That held only for a stop made through the ViewModel. A stop from the notification
     * shade calls `TrackRecordingService.stopRecording()`, a different method of the same name on a
     * different class, so its `locationJob` outlived the recording and kept a platform registration
     * alive. `TrackRecordingViewModel` now has its own `onEnteredForeground`/`onLeftForeground`,
     * which resynchronize against the track's row rather than releasing unconditionally the way
     * this one does — see that class for why an unconditional release would regress the origin
     * waypoint and the off-track alert.
     */
    fun onLeftForeground() {
        isForegrounded = false
        liveLocation.cancel()
    }

    /**
     * The OS just granted location permission — `MainActivity`'s one permission launcher reports
     * this from its result callback, for either action it was launched for. Starts a fresh
     * collection of [LocationTracker.fixes] if the construction-time one has already completed
     * (see [AvailabilityLiveLocation.begin]: it completes, rather than idling, when permission wasn't held at
     * its start). The tracker re-checks permission at every collection start, so the new
     * collection is the one that actually registers OS listeners.
     *
     * A no-op while a collection is still live — so a grant reported while fixes are already
     * flowing (the launcher answers immediately for an already-held permission, on every locate
     * tap) never stacks a second OS listener on the four this app already runs while recording.
     * Deliberately *not* driven from [locateMe]/[useCurrentLocation] succeeding: "a one-shot fix
     * resolved" and "the permission was just granted" are different facts that happen to coincide
     * on first launch, and the next reader should not have to know that. Not driven from
     * `ON_RESUME` either — see the dispatch's own constraint: re-registering on every resume would
     * mask this bug and add churn. Never requests the permission itself; it only reacts to the
     * grant the existing launcher already delivers.
     */
    fun onLocationPermissionGranted() {
        // Gated on the foreground flag as well as the job: a grant delivered while the Activity is
        // stopped must not re-acquire the subscription behind a backgrounded app. On the ordinary
        // path the grant arrives from a launcher callback while the app is visible, so this is
        // true; ON_START would start it anyway if it were not.
        if (!isForegrounded) return
        if (liveLocation.isCollecting) return
        liveLocation.begin()
    }

    /** The search radius slider: see [AvailabilitySpeciesLookup.onRadiusChanged]. */
    fun onRadiusChanged(radiusKm: Int) = speciesLookup.onRadiusChanged(radiusKm)

    fun onMonthSelected(month: Int) {
        _uiState.update { it.copy(selectedMonth = month) }
        _uiState.value.region?.let { refresh(it, month, _uiState.value.taxonFilter) }
    }

    fun onTaxonSearchQueryChanged(query: String) {
        _uiState.update { it.copy(taxonSearchQuery = query, taxonSearchHasNoResults = false) }
        speciesLookup.cancel()

        if (query.trim().length < SearchTaxaUseCase.MIN_QUERY_LENGTH) {
            _uiState.update { it.copy(taxonSearchResults = emptyList(), isSearchingTaxa = false, taxonSearchErrorMessage = null) }
            return
        }

        speciesLookup.beginLookup(query)
    }

    /**
     * Collapses the species-search suggestion popup without touching the typed query, so it can't
     * be left showing over other content it wasn't meant to sit above — e.g. the drawer opening
     * over the app bar. The in-flight search job is cancelled too: with no results to eventually
     * show, letting it run to completion in the background would only re-populate
     * [AvailabilityUiState.taxonSearchResults] and silently reopen the popup this was just told to
     * close.
     */
    fun onDismissTaxonSuggestions() {
        speciesLookup.cancel()
        _uiState.update { it.copy(taxonSearchResults = emptyList(), isSearchingTaxa = false, taxonSearchHasNoResults = false) }
    }

    /** A species suggestion picked: see [AvailabilitySpeciesLookup.onTaxonSearchResultSelected]. */
    fun onTaxonSearchResultSelected(result: TaxonSearchResult) = speciesLookup.onTaxonSearchResultSelected(result)

    /**
     * Reopens the suggestion dropdown for the last species search, so picking a different result
     * doesn't mean retyping the query from scratch — tapped from the summary strip
     * ([AvailabilityUiState.lastTaxonSearchQuery]'s doc comment has the full picture). Re-runs the
     * search rather than replaying the old [AvailabilityUiState.taxonSearchResults]: this app
     * doesn't own the freshness of iNaturalist's taxonomy data, so showing a list from however
     * long ago without a live query would be a stale result presented as a current one. A no-op
     * when nothing has been searched yet, rather than opening an empty dropdown.
     */
    fun onReopenTaxonSuggestions() {
        val query = _uiState.value.lastTaxonSearchQuery
        if (query.isNotBlank()) onTaxonSearchQueryChanged(query)
    }

    /** The manual latitude field: see [AvailabilitySpeciesLookup.onManualLatChanged]. */
    fun onManualLatChanged(text: String) = speciesLookup.onManualLatChanged(text)

    /** The manual longitude field: see [AvailabilitySpeciesLookup.onManualLngChanged]. */
    fun onManualLngChanged(text: String) = speciesLookup.onManualLngChanged(text)

    fun searchManualCoordinates() {
        val state = _uiState.value
        val lat = state.manualLatText.toDoubleOrNull()
        val lng = state.manualLngText.toDoubleOrNull()
        if (lat == null || lat !in -90.0..90.0 || lng == null || lng !in -180.0..180.0) {
            _uiState.update { it.copy(errorMessage = "Enter a valid latitude (-90 to 90) and longitude (-180 to 180).") }
            return
        }
        val region = Region(lat, lng, state.radiusKm)
        _uiState.update { it.copy(region = region, locationPermissionDenied = false, searchFrameSerial = it.searchFrameSerial + 1) }
        refresh(region, state.selectedMonth, state.taxonFilter)
    }

    fun useCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = locationProvider.getCurrentLocation()) {
                is LocationResult.Success -> {
                    val region = Region(result.lat, result.lng, _uiState.value.radiusKm)
                    _uiState.update {
                        it.copy(
                            region = region,
                            locationPermissionDenied = false,
                            manualLatText = result.lat.toString(),
                            manualLngText = result.lng.toString(),
                            searchFrameSerial = it.searchFrameSerial + 1,
                        )
                    }
                    refresh(region, _uiState.value.selectedMonth, _uiState.value.taxonFilter)
                }
                LocationResult.PermissionDenied -> _uiState.update {
                    it.copy(isLoading = false, locationPermissionDenied = true)
                }
                LocationResult.LocationUnavailable -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = COULD_NOT_FIND_LOCATION_MESSAGE)
                }
            }
        }
    }

    fun onPermissionDenied() {
        _uiState.update { it.copy(locationPermissionDenied = true) }
    }

    /** The map icon stack's locate-me button: see [AvailabilityLocateMe.locateMe]. */
    fun locateMe() = locateMeButton.locateMe()

    /** See [AvailabilityLocateMe.onLocateMePermissionDenied]. */
    fun onLocateMePermissionDenied() = locateMeButton.onLocateMePermissionDenied()

    /** See [AvailabilityMapDisplay.onMapShown]. */
    fun onMapShown() = mapDisplay.onMapShown()

    /** See [AvailabilityLiveLocation.onMapsTabShownChanged]. */
    fun onMapsTabShownChanged(shown: Boolean) = liveLocation.onMapsTabShownChanged(shown)

    /** See [AvailabilityMapDisplay.onFindDeleted]. */
    fun onFindDeleted(id: String) = mapDisplay.onFindDeleted(id)

    /** See [AvailabilityMapDisplay.onPhotoDeleted]. */
    fun onPhotoDeleted(id: String) = mapDisplay.onPhotoDeleted(id)

    /** See [AvailabilityMapDisplay.onMapLayerVisibilityChanged]. */
    fun onMapLayerVisibilityChanged(layerId: String, visible: Boolean) = mapDisplay.onMapLayerVisibilityChanged(layerId, visible)

    /** See [AvailabilityMapDisplay.onMapLayerOpacityChanged]. */
    fun onMapLayerOpacityChanged(layerId: String, opacity: Float) = mapDisplay.onMapLayerOpacityChanged(layerId, opacity)

    /** See [AvailabilityMapDisplay.onColourFieldMoved]. */
    fun onColourFieldMoved(layerId: String, move: ColourFieldMove) = mapDisplay.onColourFieldMoved(layerId, move)

    /** See [AvailabilityMapDisplay.onMapModeSelected]. */
    fun onMapModeSelected(mode: MapMode) = mapDisplay.onMapModeSelected(mode)

    /** See [AvailabilityMapDisplay.onMapIconClusterPlacementChanged]. */
    fun onMapIconClusterPlacementChanged(placement: MapIconClusterPlacement) = mapDisplay.onMapIconClusterPlacementChanged(placement)

    /**
     * Called when the map tab becomes visible. Sightings are fetched lazily, only for the
     * region+month+filter actually being viewed, rather than on every list search, since a
     * map view the user never opens shouldn't cost an extra API call.
     */
    fun onMapTabSelected() {
        val query = searchRuns.activeSearch ?: return
        val (region, month, filter) = query
        if (searchRuns.loadedSightingsQuery == query) return

        searchRuns.fetchSightings(query, region, month, filter)
    }

    /**
     * Called when the Seasonal tab becomes visible. Mirrors [onMapTabSelected]: fetched lazily,
     * only for the region+month+filter actually being viewed, and cached against that key so
     * revisiting the tab without changing the search doesn't refetch — a fresh
     * [GetSeasonalPatternUseCase] call means a fresh historical-weather fetch, not something to
     * repeat on every tab switch.
     */
    fun onSeasonalTabSelected() {
        val query = searchRuns.activeSearch ?: return
        val (region, month, filter) = query
        if (searchRuns.loadedSeasonalPatternQuery == query) return

        searchRuns.fetchSeasonalPattern(query, region, month, filter)
    }

    private fun refresh(region: Region, month: Int, filter: TaxonFilter) {
        searchRuns.startNewSearch(region, month, filter)

        // Read now, while the selection is the one this search was started for.
        val speciesGroup = speciesGroupToStore(filter)
        searchRuns.fetchAvailability(region, month, filter, speciesGroup)

        // Recent rainfall is only meaningful for the current month: searching "what's typical
        // in November" while it's April doesn't make today's rain relevant to that answer.
        if (month == LocalDate.now().monthValue) {
            // Independent of the forecast fetch above: a conditions failure must not block or
            // fail the main forecast, same independence pattern as onMapTabSelected's sightings
            // fetch.
            searchRuns.fetchConditions(region)
            // Independent fetch from the one above: a separate provider method (and, in
            // production, a separate identical-parameter request — see GetTodaysForecastUseCase's
            // own doc comment for why that duplication is accepted rather than threaded through
            // GetTripWindowsUseCase's unrelated, already-tested return type).
            searchRuns.fetchTodaysForecast(region)
        } else {
            _uiState.update { it.withoutTodaysConditions() }
        }

        // Trip windows are about the days ahead of today, not about the browsed month, so unlike
        // conditions above they are fetched regardless of which month is selected for the ranked
        // list — browsing "what's typical in November" in August doesn't make this week's rain
        // and forecast irrelevant to planning a trip this week.
        searchRuns.fetchTripWindows(region)
    }

    /**
     * The group to store with a search for [filter], so reopening it from the recent searches keeps it
     * (dispatch 2026-09-28-695, amendment 1): the current selection's group when the selection is that
     * species, and null for a category (its group is in the filter) or a selection that is not [filter].
     */
    private fun speciesGroupToStore(filter: TaxonFilter): String? =
        _uiState.value.foragingSelection
            .takeIf { filter is TaxonFilter.SpecificTaxon && it.filter == filter }
            ?.iconicTaxonName

    private fun loadRecentSearches() {
        viewModelScope.launch {
            _uiState.update { it.copy(recentSearches = getRecentSearches()) }
        }
    }

    /**
     * RECORD -723, the search bar's Clear: "One tap removes the observations from the map, clears the bar's summary
     * back to 'Search a location', and leaves recent searches untouched." The search's region and every result
     * fetched for it go: the map's sightings, the ranked list, rainfall, today's forecast, trip windows and the
     * seasonal pattern, so no tab goes on showing results for a search the bar says is not there. Fetches still
     * running are cancelled first ([AvailabilitySearchRuns.cancelRunning]). Kept: recent searches, the month, the radius and the coordinate
     * fields, which are the user's settings for the next search, not results.
     *
     * RECORD -750, item 3 (the owner: "simply reset the search panel back to default and clear the name"), replacing
     * -728's "Clear keeps the species": the species or category selection goes back to the default a fresh start has
     * ([TaxonFilter.FUNGI], its [ForagingSelection]), with the species field's text, its suggestions and the query a
     * suggestion was picked from, so the bar's summary reads its default again ("<month> · Search a location").
     */
    fun clearSearch() {
        searchRuns.cancelRunning()
        // RECORD -750, item 3: a species lookup still running would put its suggestions back over a reset field.
        speciesLookup.cancel()
        searchRuns.renewParent()
        searchRuns.forgetSearch()
        _uiState.update { it.withSearchCleared() }
    }

    /**
     * Re-runs one of the drawer's recent searches: restores the region, month and filter it was
     * made with, then goes through the ordinary [refresh] flow.
     *
     * Deliberately **not** a "load this from the cache" path. [refresh] already tries live first
     * and falls back to the cache, so tapping a recent search while online returns fresh results
     * for it rather than replaying a stored copy, and tapping the same one offline lands on the
     * cached copy with the banner that says so. A separate cache-only path would have been a
     * second way to reach results, with its own failure and staleness rules to keep in step.
     *
     * The radius and coordinate fields are restored alongside the search itself so the drawer's
     * slider, the manual-coordinate boxes and the summary strip all describe the search that just
     * ran — the same fields [useCurrentLocation] fills in for the same reason.
     */
    fun onRecentSearchSelected(summary: CachedSearchSummary) {
        val region = summary.region
        _uiState.update { it.withRecentSearch(region, summary) }
        refresh(region, summary.month, summary.filter)
    }

    /** The trip-planning flow's confirmed pin: see [AvailabilityPlannedTrips.onPlaceTripPin]. */
    fun onPlaceTripPin(location: LatLng, date: LocalDate, name: String) = plannedTrips.onPlaceTripPin(location, date, name)

    fun onDeletePlannedTrip(id: String) = plannedTrips.onDeletePlannedTrip(id)

    /**
     * Reads everything this ViewModel holds from the database again, after a restore, and returns when it is done:
     * the planned trips ([AvailabilityPlannedTrips.loadPlannedTrips]), the offline regions with their reference counts ([AvailabilityOfflineMaps.loadOfflineRegions]),
     * and the Maps tab's records ([AvailabilityMapDisplay.loadMapRecords]). The journal highlights are derived from those, the entries and the
     * waypoints, in the screen, so they follow. `loadRecentSearches` is not reloaded: the search cache is not restored.
     */
    suspend fun reloadAfterRestore() {
        // A restore can bring a deleted record back: what was committed to deletion is forgotten first.
        _uiState.update { it.copy(committedOfflineRegionDeleteIds = emptySet()) }
        plannedTrips.loadPlannedTrips().join()
        offlineMaps.loadOfflineRegions().join()
        mapDisplay.loadMapRecords()
    }

    override fun onCleared() {
        // Journal redesign J4: a region still pending is committed there, since no snackbar is left
        // to end. viewModelScope is already cancelled, so the delete runs on pendingDeleteCommitScope.
        offlineRegionDeletes.commitOnCleared()
    }

    internal companion object {
        /** RECORD -750, item 3: what a fresh start shows, which Clear puts the species selection back to. */
        val DEFAULT_STATE = AvailabilityUiState()
        const val SEARCH_DEBOUNCE_MS = 300L
        const val TAG = "AvailabilityViewModel"
    }
}
