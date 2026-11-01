package com.zynergylabs.forager.app

import android.content.Context
import com.zynergylabs.forager.app.data.repository.RoomEntryReferenceCountSource
import com.zynergylabs.forager.app.domain.EntryReferenceCountSource
import java.io.File
import com.zynergylabs.forager.app.domain.RunScheduledBackupUseCase
import com.zynergylabs.forager.app.domain.JournalBackup
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.BackByWatch
import com.zynergylabs.forager.app.domain.SettingsResetNotice
import com.zynergylabs.forager.app.domain.RecordingHalts
import com.zynergylabs.forager.app.domain.RecordingKeptTail
import com.zynergylabs.forager.app.domain.TrackEnds
import com.zynergylabs.forager.app.domain.BackupScheduler
import com.zynergylabs.forager.app.domain.BackupScheduleSettings
import com.zynergylabs.forager.app.domain.BackupSchedulePreferences
import com.zynergylabs.forager.app.domain.BackupFiles
import com.zynergylabs.forager.app.domain.BackupNotifier
import com.zynergylabs.forager.app.domain.ScheduledBackupReporter
import com.zynergylabs.forager.app.domain.CrowdedIconsPreferenceRepository
import com.zynergylabs.forager.app.data.repository.DataStoreBackupSchedulePreferences
import com.zynergylabs.forager.app.data.repository.RoomOfflineRegionIdReplacer
import com.zynergylabs.forager.app.data.backup.WorkManagerBackupScheduler
import com.zynergylabs.forager.app.data.backup.RoomJournalBackup
import com.zynergylabs.forager.app.data.backup.AndroidBackupNotifier
import com.zynergylabs.forager.app.data.backup.ContentResolverBackupFiles
import androidx.core.content.pm.PackageInfoCompat
import android.util.Log
import com.zynergylabs.forager.app.domain.wayhome.WayHomeProgress
import com.zynergylabs.forager.app.domain.wayhome.WayHomeWatch
import com.zynergylabs.forager.app.domain.wayhome.toPathHome
import com.zynergylabs.forager.app.routing.RoutingProfile
import com.zynergylabs.forager.app.routing.TrailRoutingEngine
import com.zynergylabs.forager.app.routing.WayHomeRouting
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
import com.zynergylabs.forager.app.domain.wayto.StreetDropOffEnds
import com.zynergylabs.forager.app.domain.mapdata.ReadNearbyMapData
import com.zynergylabs.forager.app.map.MapLibreOfflineBasemap
import com.zynergylabs.forager.app.routing.BRouterRouteWays
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.zynergylabs.forager.app.data.remote.ApiClients
import com.zynergylabs.forager.app.data.routedata.LocalRegionRouteFolders
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.data.local.fungiindex.FungiIndexDatabase
import com.zynergylabs.forager.app.data.repository.DataStoreAppThemePreferenceRepository
import com.zynergylabs.forager.app.data.repository.DataStoreCameraFormatRepository
import com.zynergylabs.forager.app.data.repository.DataStoreCameraGridModeRepository
import com.zynergylabs.forager.app.data.repository.DataStorePhotoLocationPreferenceRepository
import com.zynergylabs.forager.app.data.repository.DataStoreSundownPreferencesRepository
import com.zynergylabs.forager.app.data.repository.DataStoreWaypointNavigationRepository
import com.zynergylabs.forager.app.data.remote.INaturalistClient
import com.zynergylabs.forager.app.data.remote.OpenMeteoArchiveClient
import com.zynergylabs.forager.app.data.remote.OpenMeteoClient
import com.zynergylabs.forager.app.data.repository.DataStoreUnitSystemPreferenceRepository
import com.zynergylabs.forager.app.data.repository.DataStoreMapPreferencesRepository
import com.zynergylabs.forager.app.data.repository.INaturalistMushroomRepository
import com.zynergylabs.forager.app.data.repository.LocalFungiIndexRepository
import com.zynergylabs.forager.app.data.repository.OpenMeteoHistoricalWeatherProvider
import com.zynergylabs.forager.app.data.repository.OpenMeteoWeatherProvider
import com.zynergylabs.forager.app.data.repository.RoomCartographyEntryRepository
import com.zynergylabs.forager.app.data.repository.RoomKeptTrackPathRepository
import com.zynergylabs.forager.app.data.repository.RoomMushroomLogRepository
import com.zynergylabs.forager.app.data.repository.RoomOfflineRegionDayIndex
import com.zynergylabs.forager.app.data.repository.RoomPlannedTripRepository
import com.zynergylabs.forager.app.data.repository.RoomSearchCacheRepository
import com.zynergylabs.forager.app.data.repository.RoomTrackRepository
import com.zynergylabs.forager.app.data.repository.RoomWaypointRepository
import com.zynergylabs.forager.app.domain.AddPhotoToGalleryUseCase
import com.zynergylabs.forager.app.domain.AddPhotoToLogEntryUseCase
import com.zynergylabs.forager.app.domain.AppThemePreferenceRepository
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.PressureProvider
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.zynergylabs.forager.app.alert.AndroidAlertAudibility
import com.zynergylabs.forager.app.alert.AndroidBackgroundRunCheck
import com.zynergylabs.forager.app.data.repository.DataStoreOffTrackReminderPreferenceRepository
import com.zynergylabs.forager.app.data.repository.DataStoreBatterySaverPreferenceRepository
import com.zynergylabs.forager.app.domain.BatterySaverPreferenceRepository
import com.zynergylabs.forager.app.domain.OffTrackReminderCheck
import com.zynergylabs.forager.app.domain.OffTrackReminderPreferenceRepository
import com.zynergylabs.forager.app.alert.AndroidAlertDelivery
import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.AlertDelivery
import com.zynergylabs.forager.app.domain.AbandonedTrackSweepOnce
import com.zynergylabs.forager.app.domain.EndAbandonedTracksUseCase
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.shownTrailOf
import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.ComputeTrackStatisticsUseCase
import com.zynergylabs.forager.app.domain.CartographyEntryRepository
import com.zynergylabs.forager.app.domain.CommitCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.CommitDraftEntryUseCase
import com.zynergylabs.forager.app.domain.ComputeTripWindowsUseCase
import com.zynergylabs.forager.app.domain.CreateCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.CreateMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.CreateWaypointUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeleteCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.DeleteGalleryPhotoUseCase
import com.zynergylabs.forager.app.domain.DeleteMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.DeletePlannedTripUseCase
import com.zynergylabs.forager.app.domain.DeleteTrackUseCase
import com.zynergylabs.forager.app.domain.DeleteWaypointUseCase
import com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository
import com.zynergylabs.forager.app.domain.EndTrackUseCase
import com.zynergylabs.forager.app.domain.GetAvailabilityUseCase
import com.zynergylabs.forager.app.domain.GetCartographyDraftEntriesUseCase
import com.zynergylabs.forager.app.domain.GetCartographyEntriesUseCase
import com.zynergylabs.forager.app.domain.GetCartographyEntryMapDataUseCase
import com.zynergylabs.forager.app.domain.KeptTrackPathRepository
import com.zynergylabs.forager.app.domain.GetCartographyEntryOfflineRegionUseCase
import com.zynergylabs.forager.app.domain.GetCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.GetConditionsUseCase
import com.zynergylabs.forager.app.domain.GetDerivedTripUseCase
import com.zynergylabs.forager.app.domain.ImportGpxUseCase
import com.zynergylabs.forager.app.domain.GetDraftEntriesUseCase
import com.zynergylabs.forager.app.domain.GetGalleryPhotosUseCase
import com.zynergylabs.forager.app.domain.GetMushroomLogEntriesUseCase
import com.zynergylabs.forager.app.domain.GetPlannedTripsUseCase
import com.zynergylabs.forager.app.domain.GetRecentSearchesUseCase
import com.zynergylabs.forager.app.domain.GetSeasonalPatternUseCase
import com.zynergylabs.forager.app.domain.GetSightingsUseCase
import com.zynergylabs.forager.app.domain.GetTrackOriginWaypointUseCase
import com.zynergylabs.forager.app.domain.GetTracksUseCase
import com.zynergylabs.forager.app.domain.GetTodaysForecastUseCase
import com.zynergylabs.forager.app.domain.GetTripReportOfflineRegionsUseCase
import com.zynergylabs.forager.app.domain.GetTripWindowsUseCase
import com.zynergylabs.forager.app.domain.GetWaypointsUseCase
import com.zynergylabs.forager.app.domain.HistoricalWeatherProvider
import com.zynergylabs.forager.app.domain.LocationProvider
import com.zynergylabs.forager.app.domain.LastKnownLocationSource
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.MapPreferencesRepository
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.ForecastCellStore
import com.zynergylabs.forager.app.domain.GetMapRecordsUseCase
import com.zynergylabs.forager.app.domain.BasemapPreferenceRepository
import com.zynergylabs.forager.app.domain.MapIconClusterPlacementRepository
import com.zynergylabs.forager.app.domain.MapLayerPreferencesRepository
import com.zynergylabs.forager.app.domain.SundownPreferencesRepository
import com.zynergylabs.forager.app.domain.SundownWatch
import com.zynergylabs.forager.app.domain.WaypointNavigationRepository
import com.zynergylabs.forager.app.domain.MushroomLogRepository
import com.zynergylabs.forager.app.domain.MushroomRepository
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionDayIndex
import com.zynergylabs.forager.app.domain.CameraFormatRepository
import com.zynergylabs.forager.app.domain.CameraGridModeRepository
import com.zynergylabs.forager.app.domain.PhotoLocationPreferenceRepository
import com.zynergylabs.forager.app.domain.PhotoStore
import com.zynergylabs.forager.app.domain.PlannedTripRepository
import com.zynergylabs.forager.app.domain.PredictAvailabilityUseCase
import com.zynergylabs.forager.app.domain.PullPhotoIntoEntryUseCase
import com.zynergylabs.forager.app.domain.RecordTrackPointsUseCase
import com.zynergylabs.forager.app.domain.RemovePhotoFromLogEntryUseCase
import com.zynergylabs.forager.app.domain.SaveCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.SetCartographyEntryShownOnMapUseCase
import com.zynergylabs.forager.app.domain.SaveMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.SavePlannedTripUseCase
import com.zynergylabs.forager.app.domain.SearchCacheRepository
import com.zynergylabs.forager.app.domain.SearchTaxaUseCase
import com.zynergylabs.forager.app.domain.StartEditingLogEntryUseCase
import com.zynergylabs.forager.app.domain.TaxonSearchRepository
import com.zynergylabs.forager.app.domain.StartTrackUseCase
import com.zynergylabs.forager.app.domain.SystemCurrentTimeProvider
import com.zynergylabs.forager.app.domain.TrackRepository
import com.zynergylabs.forager.app.domain.TripPlanningWeatherProvider
import com.zynergylabs.forager.app.domain.UpdatePhotoLocationUseCase
import com.zynergylabs.forager.app.domain.WaypointRepository
import com.zynergylabs.forager.app.domain.WeatherProvider
import com.zynergylabs.forager.app.location.AndroidLocationProvider
import com.zynergylabs.forager.app.location.AndroidLastKnownLocationSource
import com.zynergylabs.forager.app.location.AndroidLocationTracker
import com.zynergylabs.forager.app.map.MapLibreOfflineMapRepository
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.FilePhotoStore
import com.zynergylabs.forager.app.sensor.AndroidCompassProvider
import com.zynergylabs.forager.app.sensor.AndroidPressureProvider
import com.zynergylabs.forager.app.sensor.AndroidDeclinationProvider

/** Hand-wired dependency graph. No DI framework: the graph is small enough not to need one. */
class AppContainer(context: Context, processStartedAtEpochMillis: Long) {
    /**
     * `Log.w`-backed, for the pieces that take an [ErrorLog] and are built here rather than in an
     * Activity. First in the container (dispatch 2026-09-28-658 moved it up from beside the
     * backup) because properties initialise in source order and the iNaturalist repository below
     * now takes it.
     */
    val errorLog: ErrorLog = ErrorLog { tag, message, error -> Log.w(tag, message, error) }

    /**
     * Raised when a settings file was corrupt and has been reset (RECORD -660, item 2). Every settings
     * repository below is handed it; `MainActivity` shows the one-time message and clears it. Built
     * before any of them, since properties initialise in source order.
     */
    val settingsResetNotice = SettingsResetNotice()

    /** The recording service's report of a recording it stopped on its own (RECORD -660, item 4); the recording screen reads it. */
    val recordingHalts = RecordingHalts()

    /** The recording service's report that it stored a track's end (dispatch 2026-10-21-02); the recording screen reloads its track list on it. */
    val trackEnds = TrackEnds()

    private val api = INaturalistClient.create(debug = BuildConfig.DEBUG)
    private val weatherApi = OpenMeteoClient.create(debug = BuildConfig.DEBUG)
    private val historicalWeatherApi = OpenMeteoArchiveClient.create(debug = BuildConfig.DEBUG)

    val mushroomRepository: MushroomRepository = INaturalistMushroomRepository(api, errorLog)

    // One object, two owned interfaces, one API call behind both — see
    // TripPlanningWeatherProvider's doc comment for why they are separate interfaces.
    private val openMeteo = OpenMeteoWeatherProvider(weatherApi)
    val weatherProvider: WeatherProvider = openMeteo
    val tripPlanningWeatherProvider: TripPlanningWeatherProvider = openMeteo
    val historicalWeatherProvider: HistoricalWeatherProvider = OpenMeteoHistoricalWeatherProvider(historicalWeatherApi)
    val locationProvider: LocationProvider = AndroidLocationProvider(context.applicationContext)
    /** Dispatch 2026-09-28-767: the quick menu's Battery saver switch, which every fix request follows. One per process, as DataStore requires. */
    val batterySaverPreferences: BatterySaverPreferenceRepository = DataStoreBatterySaverPreferenceRepository(context, settingsReset = settingsResetNotice)
    val locationTracker: LocationTracker = AndroidLocationTracker(context.applicationContext, batterySaverPreferences.enabled)
    /** Dispatch 2026-09-28-510: the platform's last known position, shown greyed until anything live arrives. */
    val lastKnownLocation: LastKnownLocationSource = AndroidLastKnownLocationSource(context.applicationContext)
    val compassProvider: CompassProvider = AndroidCompassProvider(context.applicationContext)

    /** The barometer, read by the recording service while a recording runs (RECORD -864, -867); see [PressureProvider]. */
    val pressureProvider: PressureProvider = AndroidPressureProvider(context.applicationContext)

    // HUD-foundations dispatch, Item 2: declination, and the one place magnetic heading becomes
    // true heading. Consumed by MainActivity, which hands it to the map screen (dispatch
    // 2026-09-28-658, F5: this said "no consumer yet", true when written); see
    // ComputeTrueHeadingUseCase's own doc comment for the strip/HUD consequence recorded there.
    val declinationProvider: DeclinationProvider = AndroidDeclinationProvider()
    val computeTrueHeadingUseCase = ComputeTrueHeadingUseCase(declinationProvider)

    /**
     * The one clock the app reads. Injected rather than called inline so the search cache's LRU
     * stamps and the relative times rendered from them are controllable in tests — see
     * [CurrentTimeProvider].
     */
    val currentTimeProvider: CurrentTimeProvider = SystemCurrentTimeProvider

    private val predictAvailabilityUseCase = PredictAvailabilityUseCase(mushroomRepository)
    val getSightingsUseCase = GetSightingsUseCase(mushroomRepository)

    // Species-name search reads a bundled, offline index instead of a live iNaturalist call — see
    // TaxonSearchRepository's doc comment. A separate Room database from `database` below
    // (FungiIndexDatabase's own doc comment has the reasoning), so it isn't part of this class's
    // main `database` block further down.
    private val fungiIndexDatabase = FungiIndexDatabase.create(context)
    val taxonSearchRepository: TaxonSearchRepository = LocalFungiIndexRepository(fungiIndexDatabase.fungiIndexDao())
    val searchTaxaUseCase = SearchTaxaUseCase(taxonSearchRepository)
    val getConditionsUseCase = GetConditionsUseCase(weatherProvider)
    val getTodaysForecastUseCase = GetTodaysForecastUseCase(tripPlanningWeatherProvider)
    val getTripWindowsUseCase = GetTripWindowsUseCase(
        tripPlanningWeatherProvider,
        ComputeTripWindowsUseCase(),
    )
    val getSeasonalPatternUseCase = GetSeasonalPatternUseCase(
        getSightingsUseCase,
        historicalWeatherProvider,
        ComputeFruitingLagDistributionUseCase(),
    )

    private val database = ForagerDatabase.create(context)

    // Journal backup and restore (dispatch 2026-09-28-127). The snapshot copies `forager.db` itself, so the
    // backup is built over the same file the database above opened. Scratch space is the cache folder: the
    // system may clear it, and nothing there is anyone's only copy.
    val journalBackup: JournalBackup = RoomJournalBackup(
        context = context.applicationContext,
        database = database,
        databaseFile = context.getDatabasePath(ForagerDatabase.DATABASE_NAME),
        filesDir = context.filesDir,
        scratchDir = File(context.cacheDir, "journal-backup"),
        appVersionCode = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)),
        errorLog = errorLog,
    )
    val backupSchedulePreferences: BackupSchedulePreferences = DataStoreBackupSchedulePreferences(context, settingsReset = settingsResetNotice)
    val backupFiles: BackupFiles = ContentResolverBackupFiles(context)

    /** Touches WorkManager only when the schedule changes, so a launch that never opens Backup never starts it. */
    val backupScheduler: BackupScheduler = object : BackupScheduler {
        override fun apply(settings: BackupScheduleSettings) =
            WorkManagerBackupScheduler(androidx.work.WorkManager.getInstance(context.applicationContext)).apply(settings)
    }
    val runScheduledBackupUseCase = RunScheduledBackupUseCase(journalBackup, backupSchedulePreferences, backupFiles)
    val backupNotifier: BackupNotifier = AndroidBackupNotifier(context.applicationContext)
    val scheduledBackupReporter = ScheduledBackupReporter(backupNotifier, backupSchedulePreferences, errorLog)
    val plannedTripRepository: PlannedTripRepository = RoomPlannedTripRepository(database.plannedTripRouteDao(), errorLog)
    val getPlannedTripsUseCase = GetPlannedTripsUseCase(plannedTripRepository)
    val savePlannedTripUseCase = SavePlannedTripUseCase(plannedTripRepository)
    val deletePlannedTripUseCase = DeletePlannedTripUseCase(plannedTripRepository)

    // The ranked-list search the ViewModel actually calls: PredictAvailabilityUseCase wrapped in
    // the cache read/write-through, so the raw one is not reachable from the UI by accident.
    val searchCacheRepository: SearchCacheRepository =
        RoomSearchCacheRepository(database.cachedSearchDao(), currentTimeProvider)
    val getAvailabilityUseCase = GetAvailabilityUseCase(predictAvailabilityUseCase, searchCacheRepository)
    val getRecentSearchesUseCase = GetRecentSearchesUseCase(searchCacheRepository)

    // Each offline region's trail route data, fetched with its tiles from the Worker (dispatch 2026-10-23-03). The key
    // is empty in any build made without it (CI's included), and then no routing request is sent at all.
    private val regionRouteFolders = LocalRegionRouteFolders.create(
        filesDir = context.filesDir,
        http = ApiClients.httpClient(debug = BuildConfig.DEBUG),
        appKey = BuildConfig.ROUTING_APP_KEY,
        log = { message, error -> Log.w("RegionRoutes", message, error) },
    )
    val offlineMapRepository: OfflineMapRepository = MapLibreOfflineMapRepository(context, database.offlineRegionDao(), RoomOfflineRegionIdReplacer(database), regionRouteFolders)
    val getTripReportOfflineRegionsUseCase = GetTripReportOfflineRegionsUseCase(offlineMapRepository)
    // One instance for both interfaces (map layers L0b, planner's ruling on F1): DataStore refuses a
    // second live instance on `map_preferences`, so the layer choices live in this same class.
    private val dataStoreMapPreferencesRepository = DataStoreMapPreferencesRepository(context, settingsReset = settingsResetNotice)
    val mapPreferencesRepository: MapPreferencesRepository = dataStoreMapPreferencesRepository
    val mapLayerPreferencesRepository: MapLayerPreferencesRepository = dataStoreMapPreferencesRepository
    val basemapPreferenceRepository: BasemapPreferenceRepository = dataStoreMapPreferencesRepository
    val mapIconClusterPlacementRepository: MapIconClusterPlacementRepository = dataStoreMapPreferencesRepository
    val crowdedIconsRepository: CrowdedIconsPreferenceRepository = dataStoreMapPreferencesRepository

    /**
     * Where the map's colour fields read their cells (map layers L0b, B5 and B6): the store that reports
     * "no forecast data" for every group and week, in every build type, until a real one is built.
     */
    val forecastCellStore: ForecastCellStore = AbsentForecastCellStore
    val unitSystemPreferenceRepository: UnitSystemPreferenceRepository = DataStoreUnitSystemPreferenceRepository(context, settingsReset = settingsResetNotice)
    val appThemePreferenceRepository: AppThemePreferenceRepository = DataStoreAppThemePreferenceRepository(context, settingsReset = settingsResetNotice)
    val sundownPreferencesRepository: SundownPreferencesRepository = DataStoreSundownPreferencesRepository(context, settingsReset = settingsResetNotice)

    /** Dispatch 2026-09-28-502: the waypoint being navigated to, picked back up when the app opens again. One per process, as DataStore requires. */
    val waypointNavigationRepository: WaypointNavigationRepository = DataStoreWaypointNavigationRepository(context, settingsReset = settingsResetNotice)
    val photoLocationPreferenceRepository: PhotoLocationPreferenceRepository = DataStorePhotoLocationPreferenceRepository(context, settingsReset = settingsResetNotice)
    val cameraGridModeRepository: CameraGridModeRepository = DataStoreCameraGridModeRepository(context, settingsReset = settingsResetNotice)
    /** Dispatch 2026-10-15-01: the format chip's choice, remembered across restarts (RECORD -1005). */
    val cameraFormatRepository: CameraFormatRepository = DataStoreCameraFormatRepository(context, settingsReset = settingsResetNotice)

    val photoStore: PhotoStore = FilePhotoStore(context)
    val cameraCaptureFiles = CameraCaptureFiles(context)

    val mushroomLogRepository: MushroomLogRepository = RoomMushroomLogRepository(database.mushroomLogDao())
    val getMushroomLogEntriesUseCase = GetMushroomLogEntriesUseCase(mushroomLogRepository)
    val getDraftEntriesUseCase = GetDraftEntriesUseCase(mushroomLogRepository)
    val createMushroomLogEntryUseCase = CreateMushroomLogEntryUseCase(mushroomLogRepository)
    val startEditingLogEntryUseCase = StartEditingLogEntryUseCase(mushroomLogRepository)
    val saveMushroomLogEntryUseCase = SaveMushroomLogEntryUseCase(mushroomLogRepository)
    val commitDraftEntryUseCase = CommitDraftEntryUseCase(mushroomLogRepository)
    val deleteMushroomLogEntryUseCase = DeleteMushroomLogEntryUseCase(mushroomLogRepository)
    val addPhotoToLogEntryUseCase = AddPhotoToLogEntryUseCase(photoStore, mushroomLogRepository)
    val addPhotoToGalleryUseCase = AddPhotoToGalleryUseCase(photoStore, mushroomLogRepository)
    val removePhotoFromLogEntryUseCase = RemovePhotoFromLogEntryUseCase(mushroomLogRepository)
    val getGalleryPhotosUseCase = GetGalleryPhotosUseCase(mushroomLogRepository)
    val pullPhotoIntoEntryUseCase = PullPhotoIntoEntryUseCase(mushroomLogRepository)
    val deleteGalleryPhotoUseCase = DeleteGalleryPhotoUseCase(mushroomLogRepository, photoStore)
    val updatePhotoLocationUseCase = UpdatePhotoLocationUseCase(mushroomLogRepository)

    // Journal Stage 2b: the Cartography entry — a new entity, distinct from MushroomLogEntry, see
    // CartographyEntry's own doc comment.
    val cartographyEntryRepository: CartographyEntryRepository = RoomCartographyEntryRepository(database.cartographyEntryDao())
    val getCartographyEntriesUseCase = GetCartographyEntriesUseCase(cartographyEntryRepository)
    val getCartographyDraftEntriesUseCase = GetCartographyDraftEntriesUseCase(cartographyEntryRepository)
    val createCartographyEntryUseCase = CreateCartographyEntryUseCase(cartographyEntryRepository)
    val saveCartographyEntryUseCase = SaveCartographyEntryUseCase(cartographyEntryRepository)
    val getCartographyEntryUseCase = GetCartographyEntryUseCase(cartographyEntryRepository)
    val commitCartographyEntryUseCase = CommitCartographyEntryUseCase(cartographyEntryRepository)
    val deleteCartographyEntryUseCase = DeleteCartographyEntryUseCase(cartographyEntryRepository)
    // J8: the report menu's "Show on map" and the Maps-tab chip's "Hide" write shownOnMap through this.
    val setCartographyEntryShownOnMapUseCase = SetCartographyEntryShownOnMapUseCase(cartographyEntryRepository)
    // Dispatch 2026-10-28-08: how many journal entries keep each waypoint, track, offline region and photo, live.
    // Replaced the one-shot GetEntryReferenceCountUseCase, whose four ViewModel callers were its only ones.
    val entryReferenceCountSource: EntryReferenceCountSource = RoomEntryReferenceCountSource(database.cartographyEntryDao())

    // Phase 1a of the Forager Navigator plan (docs/navigation/forager-navigator-plan.md) — track
    // recording and waypoints. TrackRecordingService (com.zynergylabs.forager.app.service) reaches these
    // through ForagerApplication.container, the same way every other Android-layer class in this
    // app reaches its dependencies; there is no separate service-scoped graph.
    val trackRepository: TrackRepository = RoomTrackRepository(database.trackDao())
    val startTrackUseCase = StartTrackUseCase(trackRepository)
    val recordTrackPointsUseCase = RecordTrackPointsUseCase(trackRepository)
    val endTrackUseCase = EndTrackUseCase(trackRepository)
    val getTracksUseCase = GetTracksUseCase(trackRepository)

    // Map layers L0b, B2: every saved record the Maps tab draws, through the read paths the Journal
    // and Records already use.
    val getMapRecordsUseCase = GetMapRecordsUseCase(
        getFinds = getMushroomLogEntriesUseCase,
        getTracks = getTracksUseCase,
        getPhotos = getGalleryPhotosUseCase,
        offlineMapRepository = offlineMapRepository,
    )
    val computeTrackStatisticsUseCase = ComputeTrackStatisticsUseCase()
    val computeReturnToStartUseCase = ComputeReturnToStartUseCase()

    // Alert-delivery dispatch: the one path by which the app interrupts the user, and the
    // trip-start read of whether the device would let it. Built here, at process start, so the
    // alert's notification channel exists before any recording and delivery never depends on the
    // Activity's composition — see AlertDelivery's doc comment, including the swipe-away hole.
    val alertDelivery: AlertDelivery = AndroidAlertDelivery(context.applicationContext)
    val alertAudibility: AlertAudibility = AndroidAlertAudibility(context.applicationContext)

    // The off-track decision and its state, held here so they outlive the Activity (dispatch
    // 2026-09-28-400, Amendment 2). TrackRecordingService begins, feeds and ends it;
    // TrackRecordingViewModel calls it for Return and copies its state. See ReturnWatch.
    // Dispatch 2026-09-28-626 (plan T14): Settings' "Off-track reminder" and the check made when a
    // recording starts. The watch reads the checkbox at the moment it decides to alert.
    val offTrackReminderPreferences: OffTrackReminderPreferenceRepository = DataStoreOffTrackReminderPreferenceRepository(context, settingsReset = settingsResetNotice)
    val offTrackReminderCheck = OffTrackReminderCheck(AndroidBackgroundRunCheck(context.applicationContext), offTrackReminderPreferences, errorLog)
    val returnWatch: ReturnWatch = ReturnWatch(
        computeReturnToStartUseCase,
        alertDelivery,
        isReminderOn = offTrackReminderPreferences::enabledNow,
        // Dispatch 2026-10-25-08 (RECORD -1631) and -09 (RECORD -1635, -1640): the way home's lines count as on course, the
        // dotted part only while heading back along it. Reads the line, then the place under the way-home watch's lock; see
        // shownTrailOf for the order. wayHomeWatch is declared below, and this runs only on a fix. Both watches are typed
        // explicitly: each reads the other, and Kotlin cannot infer either type otherwise (CI 40171062417).
        wayHomeTrail = { id -> shownTrailOf(wayHomeWatch, id) },
    )

    // Tracks an earlier process left open become finished tracks (dispatch 2026-09-28-400,
    // Amendment 3, Part 3b). The rule is the use case's; AbandonedTrackSweepOnce runs it once per
    // process, launched by the first recording ViewModel to be created.
    val endAbandonedTracksUseCase = EndAbandonedTracksUseCase(trackRepository, watchedTrackId = { returnWatch.state.value.trackId }, errorLog = errorLog)

    val abandonedTrackSweepOnce = AbandonedTrackSweepOnce(endAbandonedTracksUseCase, processStartedAtEpochMillis) { sweep ->
        if (sweep.ended.isNotEmpty()) Log.i(SWEEP_TAG, "Ended ${sweep.ended.size} track(s) left open by an earlier process, each at its last stored point.")
        sweep.ended.filter { it.clampedFromEpochMillis != null }.forEach {
            Log.w(SWEEP_TAG, "Track '${it.trackId}': its last stored point is at ${it.clampedFromEpochMillis}, before its start at ${it.endedAtEpochMillis}; ended at its start.")
        }
        if (sweep.endedWithNoStoredPoint.isNotEmpty()) Log.i(SWEEP_TAG, "Ended ${sweep.endedWithNoStoredPoint.size} track(s) left open with nothing recorded in them, each at its own start time.")
        if (sweep.failed > 0) Log.w(SWEEP_TAG, "${sweep.failed} open track(s) could not be read or ended and are left open.")
    }

    val waypointRepository: WaypointRepository = RoomWaypointRepository(database.waypointDao())

    /**
     * The wake-up alarms for Back by and the sundown alerts (dispatch 2026-09-28-796, RECORD -797): inexact,
     * allow-while-idle, no permission. See [com.zynergylabs.forager.app.alert.AndroidWakeUpAlarms].
     */
    val wakeUpAlarms: com.zynergylabs.forager.app.domain.WakeUpAlarms = com.zynergylabs.forager.app.alert.AndroidWakeUpAlarms(context)

    // The way home along trails (dispatch 2026-10-24-10 Amendment 1, PR C; RECORD -1613: "Yes, keep working"), held
    // here so it outlives the Activity, as ReturnWatch is. The recording ViewModel finds and stops it; the service feeds
    // it fixes and ends it; the HUD's tick and sundown read where the walker is on it. Its engine is the shared one below.
    // The app's one trail engine (dispatch 2026-10-26-10, step 6 PR 2), shared by the way home and the way to a waypoint:
    // BRouter runs one route at a time in the whole process, so a second engine would gain nothing. Built, and the routing
    // profile installed, on first use by either (TrailRoutingEngine).
    private val trailRoutingEngine = TrailRoutingEngine(installProfile = { RoutingProfile.install(context) }, errorLog = errorLog)
    private val wayHomeRouting = WayHomeRouting(engines = trailRoutingEngine, offlineMapRepository = offlineMapRepository, errorLog = errorLog)

    /**
     * The way to a waypoint along trails (dispatch 2026-10-26-10, step 6 PR 2), on the shared engine. Called since step 6
     * PR 3 (#413) from the waypoint navigation, in the foreground only (RECORD -1652 Q5): `AvailabilityViewModelFactory`
     * hands it to the ViewModel, whose `AvailabilityWaypointRoute` asks it at the first fix.
     */
    val findWayToWaypoint = FindWayToWaypointUseCase(
        offlineMapRepository = offlineMapRepository,
        engines = trailRoutingEngine,
        errorLog = errorLog,
        // Way-to dispatch 2026-10-28, PR 5: the street drop-off and the gates, on the offline map MapLibre keeps
        // (files/mbgl-offline.db, MapLibreStorage.kt) and on the stored route squares' own ways.
        ends = StreetDropOffEnds(
            mapData = ReadNearbyMapData(MapLibreOfflineBasemap(File(context.filesDir, "mbgl-offline.db"))),
            routeWays = BRouterRouteWays(profileDir = { routeWaysProfileDir }),
        ),
    )

    /** The profile folder the route-square reader takes lookups.dat from: installed once, on its first read, off the main thread. */
    private val routeWaysProfileDir: File by lazy { RoutingProfile.install(context) }
    // Dispatch 2026-10-26-05 (RECORD -1651): the recording's newest kept points, written or not, held here so they outlive
    // the Activity, as the watches are. The service adds each kept point and ends it; the recording screen draws its line
    // from the stored track and these (TrackRecordingUiState.recordingLine).
    val recordingKeptTail = RecordingKeptTail()

    val wayHomeWatch: WayHomeWatch = WayHomeWatch(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        readTrack = trackRepository::getById,
        findWayHome = wayHomeRouting::find,
        // Returning now, not after arrival: the way home is for the walk back, and arrival ends it.
        isReturning = { id -> returnWatch.state.value.let { it.trackId == id && it.isReturning } },
        errorLog = errorLog,
    )

    // The three sundown alerts, held here so they outlive the Activity, as ReturnWatch is
    // (dispatch 2026-09-28-516). TrackRecordingService begins, feeds, ticks and ends it.
    val sundownWatch = SundownWatch(
        alertDelivery = alertDelivery,
        clock = currentTimeProvider,
        preferences = sundownPreferencesRepository,
        readTrack = trackRepository::getById,
        readWaypoint = waypointRepository::getById,
        // RECORD -844, -845: while returning, and once the return has ended by arrival, so arrival is judged as before.
        isReturning = returnWatch::arrivalJudgedFor,
        errorLog = errorLog,
        lastKnownLocation = lastKnownLocation,
        alarms = wakeUpAlarms,
        // RECORD -1589, "app-wide", "Track until Return": the way home shown, while returning and placed on it.
        wayHomeFor = { id -> (wayHomeWatch.progressFor(id) as? WayHomeProgress.Placed)?.place?.toPathHome(pointCount = 0) },
    )

    // Back by (dispatch 2026-09-28-645, plan task T15), held here for the same reason and driven by
    // TrackRecordingService the same way; its arrival rule is the sundown watch's.
    val backByWatch = BackByWatch(
        alertDelivery = alertDelivery,
        clock = currentTimeProvider,
        readTrack = trackRepository::getById,
        readWaypoint = waypointRepository::getById,
        // RECORD -844, -845: while returning, and once the return has ended by arrival, so arrival is judged as before.
        isReturning = returnWatch::arrivalJudgedFor,
        errorLog = errorLog,
        alarms = wakeUpAlarms,
    )
    val createWaypointUseCase = CreateWaypointUseCase(waypointRepository)
    val getWaypointsUseCase = GetWaypointsUseCase(waypointRepository)
    val deleteWaypointUseCase = DeleteWaypointUseCase(waypointRepository)
    // After waypointRepository (Kotlin initialises properties in source order): deleting a track
    // now detaches its waypoints first — HUD-foundations dispatch, Item 3, see DeleteTrackUseCase.
    val keptTrackPathRepository: KeptTrackPathRepository = RoomKeptTrackPathRepository(database.cartographyEntryDao())
    val deleteTrackUseCase = DeleteTrackUseCase(trackRepository, waypointRepository, keptTrackPathRepository)
    // The origin-waypoint read path for Track.originWaypointId. Consumed by MainActivity's
    // recording ViewModel (dispatch 2026-09-28-658, F5: this said "no consumer until the navigation
    // HUD dispatch", true when written); see GetTrackOriginWaypointUseCase's own doc comment.
    val getTrackOriginWaypointUseCase = GetTrackOriginWaypointUseCase(trackRepository, waypointRepository)

    // Journal Stage 2d: CartographyEntryReportScreen's own map, resolving kept references
    // (tracks/finds live-fetched, waypoints/photos/offline-regions already in the entry's own
    // snapshot) — see GetCartographyEntryMapDataUseCase's own doc comment.
    val getCartographyEntryMapDataUseCase = GetCartographyEntryMapDataUseCase(trackRepository, mushroomLogRepository, keptTrackPathRepository, errorLog)

    // Journal Stage 2e-i: the same screen's manual offline-map toggle — see
    // GetCartographyEntryOfflineRegionUseCase's own doc comment.
    val getCartographyEntryOfflineRegionUseCase = GetCartographyEntryOfflineRegionUseCase(offlineMapRepository)

    // Plan T16, GPX import: Records > Tracks > "Import GPX" and Open with or Share (GpxImportActivity) both
    // save through this one use case. It writes ended tracks and their waypoints, nothing else.
    val importGpxUseCase = ImportGpxUseCase(trackRepository, waypointRepository, currentTimeProvider, errorLog)

    // Journal Stage 2a's derived-trip read, consumed by 2b's trip-report surface — data-layer-only
    // when 2a landed, so never wired here until now.
    val offlineRegionDayIndex: OfflineRegionDayIndex = RoomOfflineRegionDayIndex(database.offlineRegionDao())
    val getDerivedTripUseCase = GetDerivedTripUseCase(
        mushroomLogRepository,
        trackRepository,
        waypointRepository,
        offlineRegionDayIndex,
    )

    private companion object {
        const val SWEEP_TAG = "EndAbandonedTracks"
    }
}
