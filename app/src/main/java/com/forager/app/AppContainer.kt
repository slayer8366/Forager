package com.forager.app

import android.content.Context
import com.forager.app.crash.CrashFileStore
import com.forager.app.data.local.ForagerDatabase
import com.forager.app.data.local.fungiindex.FungiIndexDatabase
import com.forager.app.data.repository.DataStoreAppThemePreferenceRepository
import com.forager.app.data.remote.INaturalistClient
import com.forager.app.data.remote.OpenMeteoArchiveClient
import com.forager.app.data.remote.OpenMeteoClient
import com.forager.app.data.repository.DataStoreDistanceUnitPreferenceRepository
import com.forager.app.data.repository.DataStoreMapPreferencesRepository
import com.forager.app.data.repository.INaturalistMushroomRepository
import com.forager.app.data.repository.LocalFungiIndexRepository
import com.forager.app.data.repository.OpenMeteoHistoricalWeatherProvider
import com.forager.app.data.repository.OpenMeteoWeatherProvider
import com.forager.app.data.repository.RoomCartographyEntryRepository
import com.forager.app.data.repository.RoomMushroomLogRepository
import com.forager.app.data.repository.RoomOfflineRegionDayIndex
import com.forager.app.data.repository.RoomPlannedTripRepository
import com.forager.app.data.repository.RoomSearchCacheRepository
import com.forager.app.data.repository.RoomTrackRepository
import com.forager.app.data.repository.RoomWaypointRepository
import com.forager.app.domain.AddPhotoToGalleryUseCase
import com.forager.app.domain.AddPhotoToLogEntryUseCase
import com.forager.app.domain.AppThemePreferenceRepository
import com.forager.app.domain.CompassProvider
import com.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.forager.app.domain.ComputeReturnToStartUseCase
import com.forager.app.domain.ComputeTrackStatisticsUseCase
import com.forager.app.domain.CartographyEntryRepository
import com.forager.app.domain.CommitCartographyEntryUseCase
import com.forager.app.domain.CommitDraftEntryUseCase
import com.forager.app.domain.ComputeTripWindowsUseCase
import com.forager.app.domain.CreateCartographyEntryUseCase
import com.forager.app.domain.CreateMushroomLogEntryUseCase
import com.forager.app.domain.CreateWaypointUseCase
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.DeleteCartographyEntryUseCase
import com.forager.app.domain.DeleteGalleryPhotoUseCase
import com.forager.app.domain.DeleteMushroomLogEntryUseCase
import com.forager.app.domain.DeletePlannedTripUseCase
import com.forager.app.domain.DeleteTrackUseCase
import com.forager.app.domain.DeleteWaypointUseCase
import com.forager.app.domain.DetectOffTrackUseCase
import com.forager.app.domain.DistanceUnitPreferenceRepository
import com.forager.app.domain.EndTrackUseCase
import com.forager.app.domain.GetAvailabilityUseCase
import com.forager.app.domain.GetCartographyDraftEntriesUseCase
import com.forager.app.domain.GetCartographyEntriesUseCase
import com.forager.app.domain.GetConditionsUseCase
import com.forager.app.domain.GetDerivedTripUseCase
import com.forager.app.domain.GetDraftEntriesUseCase
import com.forager.app.domain.GetEntryReferenceCountUseCase
import com.forager.app.domain.GetGalleryPhotosUseCase
import com.forager.app.domain.GetMushroomLogEntriesUseCase
import com.forager.app.domain.GetPlannedTripsUseCase
import com.forager.app.domain.GetRecentSearchesUseCase
import com.forager.app.domain.GetSeasonalPatternUseCase
import com.forager.app.domain.GetSightingsUseCase
import com.forager.app.domain.GetTracksUseCase
import com.forager.app.domain.GetTodaysForecastUseCase
import com.forager.app.domain.GetTripReportOfflineRegionsUseCase
import com.forager.app.domain.GetTripWindowsUseCase
import com.forager.app.domain.GetWaypointsUseCase
import com.forager.app.domain.HistoricalWeatherProvider
import com.forager.app.domain.LocationProvider
import com.forager.app.domain.LocationTracker
import com.forager.app.domain.MapPreferencesRepository
import com.forager.app.domain.MushroomLogRepository
import com.forager.app.domain.MushroomRepository
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.OfflineRegionDayIndex
import com.forager.app.domain.PhotoStore
import com.forager.app.domain.PlannedTripRepository
import com.forager.app.domain.PredictAvailabilityUseCase
import com.forager.app.domain.PullPhotoIntoEntryUseCase
import com.forager.app.domain.RecordTrackPointsUseCase
import com.forager.app.domain.RemovePhotoFromLogEntryUseCase
import com.forager.app.domain.SaveCartographyEntryUseCase
import com.forager.app.domain.SaveMushroomLogEntryUseCase
import com.forager.app.domain.SavePlannedTripUseCase
import com.forager.app.domain.SearchCacheRepository
import com.forager.app.domain.SearchTaxaUseCase
import com.forager.app.domain.StartEditingLogEntryUseCase
import com.forager.app.domain.TaxonSearchRepository
import com.forager.app.domain.StartTrackUseCase
import com.forager.app.domain.SystemCurrentTimeProvider
import com.forager.app.domain.TrackRepository
import com.forager.app.domain.TripPlanningWeatherProvider
import com.forager.app.domain.WaypointRepository
import com.forager.app.domain.WeatherProvider
import com.forager.app.location.AndroidLocationProvider
import com.forager.app.location.AndroidLocationTracker
import com.forager.app.map.MapLibreOfflineMapRepository
import com.forager.app.photo.CameraCaptureFiles
import com.forager.app.photo.FilePhotoStore
import com.forager.app.sensor.AndroidCompassProvider

/** Hand-wired dependency graph. No DI framework: the graph is small enough not to need one. */
class AppContainer(context: Context) {
    private val api = INaturalistClient.create(debug = BuildConfig.DEBUG)
    private val weatherApi = OpenMeteoClient.create(debug = BuildConfig.DEBUG)
    private val historicalWeatherApi = OpenMeteoArchiveClient.create(debug = BuildConfig.DEBUG)

    val mushroomRepository: MushroomRepository = INaturalistMushroomRepository(api)

    // One object, two owned interfaces, one API call behind both — see
    // TripPlanningWeatherProvider's doc comment for why they are separate interfaces.
    private val openMeteo = OpenMeteoWeatherProvider(weatherApi)
    val weatherProvider: WeatherProvider = openMeteo
    val tripPlanningWeatherProvider: TripPlanningWeatherProvider = openMeteo
    val historicalWeatherProvider: HistoricalWeatherProvider = OpenMeteoHistoricalWeatherProvider(historicalWeatherApi)
    val locationProvider: LocationProvider = AndroidLocationProvider(context.applicationContext)
    val locationTracker: LocationTracker = AndroidLocationTracker(context.applicationContext)
    val compassProvider: CompassProvider = AndroidCompassProvider(context.applicationContext)

    val crashFileStore = CrashFileStore.forContext(context.applicationContext)

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
    val plannedTripRepository: PlannedTripRepository = RoomPlannedTripRepository(database.plannedTripDao())
    val getPlannedTripsUseCase = GetPlannedTripsUseCase(plannedTripRepository)
    val savePlannedTripUseCase = SavePlannedTripUseCase(plannedTripRepository)
    val deletePlannedTripUseCase = DeletePlannedTripUseCase(plannedTripRepository)

    // The ranked-list search the ViewModel actually calls: PredictAvailabilityUseCase wrapped in
    // the cache read/write-through, so the raw one is not reachable from the UI by accident.
    val searchCacheRepository: SearchCacheRepository =
        RoomSearchCacheRepository(database.cachedSearchDao(), currentTimeProvider)
    val getAvailabilityUseCase = GetAvailabilityUseCase(predictAvailabilityUseCase, searchCacheRepository)
    val getRecentSearchesUseCase = GetRecentSearchesUseCase(searchCacheRepository)

    val offlineMapRepository: OfflineMapRepository = MapLibreOfflineMapRepository(context, database.offlineRegionDao())
    val getTripReportOfflineRegionsUseCase = GetTripReportOfflineRegionsUseCase(offlineMapRepository)
    val mapPreferencesRepository: MapPreferencesRepository = DataStoreMapPreferencesRepository(context)
    val distanceUnitPreferenceRepository: DistanceUnitPreferenceRepository = DataStoreDistanceUnitPreferenceRepository(context)
    val appThemePreferenceRepository: AppThemePreferenceRepository = DataStoreAppThemePreferenceRepository(context)

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

    // Journal Stage 2b: the Cartography entry — a new entity, distinct from MushroomLogEntry, see
    // CartographyEntry's own doc comment.
    val cartographyEntryRepository: CartographyEntryRepository = RoomCartographyEntryRepository(database.cartographyEntryDao())
    val getCartographyEntriesUseCase = GetCartographyEntriesUseCase(cartographyEntryRepository)
    val getCartographyDraftEntriesUseCase = GetCartographyDraftEntriesUseCase(cartographyEntryRepository)
    val createCartographyEntryUseCase = CreateCartographyEntryUseCase(cartographyEntryRepository)
    val saveCartographyEntryUseCase = SaveCartographyEntryUseCase(cartographyEntryRepository)
    val commitCartographyEntryUseCase = CommitCartographyEntryUseCase(cartographyEntryRepository)
    val deleteCartographyEntryUseCase = DeleteCartographyEntryUseCase(cartographyEntryRepository)
    val getEntryReferenceCountUseCase = GetEntryReferenceCountUseCase(cartographyEntryRepository)

    // Phase 1a of the Forager Navigator plan (docs/plans/forager-navigator-plan.md) — track
    // recording and waypoints. TrackRecordingService (com.forager.app.service) reaches these
    // through ForagerApplication.container, the same way every other Android-layer class in this
    // app reaches its dependencies; there is no separate service-scoped graph.
    val trackRepository: TrackRepository = RoomTrackRepository(database.trackDao())
    val startTrackUseCase = StartTrackUseCase(trackRepository)
    val recordTrackPointsUseCase = RecordTrackPointsUseCase(trackRepository)
    val endTrackUseCase = EndTrackUseCase(trackRepository)
    val getTracksUseCase = GetTracksUseCase(trackRepository)
    val deleteTrackUseCase = DeleteTrackUseCase(trackRepository)
    val computeTrackStatisticsUseCase = ComputeTrackStatisticsUseCase()
    val computeReturnToStartUseCase = ComputeReturnToStartUseCase()
    val detectOffTrackUseCase = DetectOffTrackUseCase()

    val waypointRepository: WaypointRepository = RoomWaypointRepository(database.waypointDao())
    val createWaypointUseCase = CreateWaypointUseCase(waypointRepository)
    val getWaypointsUseCase = GetWaypointsUseCase(waypointRepository)
    val deleteWaypointUseCase = DeleteWaypointUseCase(waypointRepository)

    // Journal Stage 2a's derived-trip read, consumed by 2b's trip-report surface — data-layer-only
    // when 2a landed, so never wired here until now.
    val offlineRegionDayIndex: OfflineRegionDayIndex = RoomOfflineRegionDayIndex(database.offlineRegionDao())
    val getDerivedTripUseCase = GetDerivedTripUseCase(
        mushroomLogRepository,
        trackRepository,
        waypointRepository,
        offlineRegionDayIndex,
    )
}
