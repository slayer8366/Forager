package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.hasAnyAncestor
import com.zynergylabs.forager.app.domain.AppThemePreferenceRepository
import com.zynergylabs.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.zynergylabs.forager.app.domain.ComputeTripWindowsUseCase
import com.zynergylabs.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.zynergylabs.forager.app.domain.DeletePlannedTripUseCase
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
import com.zynergylabs.forager.app.domain.LocationProvider
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
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.SightingsPage
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.WeatherSeries
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel
import java.time.LocalDate
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.Alert
import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.AlertAudibilityState
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.AddPhotoToGalleryUseCase
import com.zynergylabs.forager.app.domain.AddPhotoToLogEntryUseCase
import com.zynergylabs.forager.app.domain.CommitDraftEntryUseCase
import com.zynergylabs.forager.app.domain.CreateMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.DeleteGalleryPhotoUseCase
import com.zynergylabs.forager.app.domain.DeleteMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.GetDraftEntriesUseCase
import com.zynergylabs.forager.app.domain.GetGalleryPhotosUseCase
import com.zynergylabs.forager.app.domain.GetMushroomLogEntriesUseCase
import com.zynergylabs.forager.app.domain.MushroomLogRepository
import com.zynergylabs.forager.app.domain.PhotoStore
import com.zynergylabs.forager.app.domain.PullPhotoIntoEntryUseCase
import com.zynergylabs.forager.app.domain.RemovePhotoFromLogEntryUseCase
import com.zynergylabs.forager.app.domain.SaveMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.StartEditingLogEntryUseCase
import com.zynergylabs.forager.app.domain.UpdatePhotoLocationUseCase
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.domain.CreateWaypointUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeleteTrackUseCase
import com.zynergylabs.forager.app.domain.InMemoryKeptTrackPaths
import com.zynergylabs.forager.app.domain.DeleteWaypointUseCase
import com.zynergylabs.forager.app.domain.DetectOffTrackUseCase
import com.zynergylabs.forager.app.domain.GetTracksUseCase
import com.zynergylabs.forager.app.domain.GetWaypointsUseCase
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.RingerMode
import com.zynergylabs.forager.app.domain.StartTrackUseCase
import com.zynergylabs.forager.app.domain.TrackRepository
import com.zynergylabs.forager.app.domain.WaypointRepository
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.SaveCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.SetCartographyEntryShownOnMapUseCase
import com.zynergylabs.forager.app.domain.OfflineRegionMetadata
import com.zynergylabs.forager.app.domain.OfflineRegionDayIndex
import com.zynergylabs.forager.app.domain.GetTripReportOfflineRegionsUseCase
import com.zynergylabs.forager.app.domain.GetDerivedTripUseCase
import com.zynergylabs.forager.app.domain.GetCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.GetCartographyEntriesUseCase
import com.zynergylabs.forager.app.domain.GetCartographyDraftEntriesUseCase
import com.zynergylabs.forager.app.domain.DeleteCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.CreateCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.ComputeTrackStatisticsUseCase
import com.zynergylabs.forager.app.domain.CommitCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.CartographyEntryRepository
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Journal redesign J4 (`prompts/preserved/2026-09-27-21.md`): delete with swipe and Undo, driven
 * through the real [JournalTab] rows, the real owning ViewModel over a fake repository, a real
 * [SnackbarHost], and [PendingDeleteSnackbarEffects] fed by the same notice builders `MainActivity`
 * uses. Every delete assertion reads the fake repository's own call list, not a callback.
 *
 * Swipes are real `performTouchInput { swipeLeft() }` gestures on the rows, on more than one row
 * position. The snackbar's text is asserted whole. The snackbar's timeout is the host's own
 * [androidx.compose.material3.SnackbarDuration.Long], reached by advancing the test clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class JournalPendingDeleteTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val waypointRepository = PendingDeleteWaypointRepository(PD_WAYPOINTS)
    private lateinit var trackViewModel: TrackRecordingViewModel
    private val offlineMapRepository = PendingDeleteOfflineMapRepository(PD_REGIONS)
    private lateinit var availabilityViewModel: AvailabilityViewModel
    private lateinit var logRepository: PendingDeleteLogRepository
    private lateinit var logViewModel: MushroomLogViewModel
    private lateinit var photoStore: PendingDeletePhotoStore
    private lateinit var cartographyRepository: PendingDeleteCartographyRepository
    private lateinit var cartographyViewModel: CartographyViewModel
    private val searchCache = InMemorySearchCacheRepository()

    private fun setScreen(
        waypointReferenceCounts: Map<String, Int> = mapOf("wp-creek" to 2, "wp-oak" to 1),
        chip: RecordsSubTab = RecordsSubTab.WAYPOINTS,
        regionReferenceCounts: Map<Long, Int> = mapOf(7L to 2, 8L to 0),
        finds: List<MushroomLogEntry> = emptyList(),
        cartographyEntries: List<CartographyEntry> = emptyList(),
        openRecords: Boolean = true,
        photos: List<GalleryPhoto> = emptyList(),
        photoJournalCounts: Map<String, Int> = emptyMap(),
    ) {
        logRepository = PendingDeleteLogRepository(finds, photos)
        val trackRepository = PendingDeleteTrackRepository()
        photoStore = PendingDeletePhotoStore()
        logViewModel = MushroomLogViewModel(
            getEntries = GetMushroomLogEntriesUseCase(logRepository),
            getDraftEntries = GetDraftEntriesUseCase(logRepository),
            createEntry = CreateMushroomLogEntryUseCase(logRepository, today = { LocalDate.of(2026, 9, 27) }, idGenerator = { "find-new" }),
            startEditingEntry = StartEditingLogEntryUseCase(logRepository, idGenerator = { "draft-of-find" }),
            saveEntry = SaveMushroomLogEntryUseCase(logRepository),
            commitDraftEntry = CommitDraftEntryUseCase(logRepository),
            deleteEntry = DeleteMushroomLogEntryUseCase(logRepository),
            addPhoto = AddPhotoToLogEntryUseCase(photoStore, logRepository),
            addPhotoToGallery = AddPhotoToGalleryUseCase(photoStore, logRepository),
            removePhoto = RemovePhotoFromLogEntryUseCase(logRepository),
            getGalleryPhotos = GetGalleryPhotosUseCase(logRepository),
            pullPhotoIntoEntry = PullPhotoIntoEntryUseCase(logRepository),
            deleteGalleryPhoto = DeleteGalleryPhotoUseCase(logRepository, photoStore),
            locationProvider = PendingDeleteUnusedLocationProvider,
            updatePhotoLocation = UpdatePhotoLocationUseCase(logRepository),
            getPhotoEntryReferenceCount = { id -> photoJournalCounts[id] ?: 0 },
        )
        availabilityViewModel = AvailabilityViewModel(
            locationProvider = PendingDeleteUnusedLocationProvider,
            locationTracker = PendingDeleteNoOpLocationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(PendingDeleteEmptyRepository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(PendingDeleteEmptyRepository),
            searchTaxa = SearchTaxaUseCase(PendingDeleteEmptyRepository),
            getConditions = GetConditionsUseCase(PendingDeleteWeather),
            getTripWindows = GetTripWindowsUseCase(PendingDeleteWeather, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(PendingDeletePlannedTrips),
            savePlannedTrip = SavePlannedTripUseCase(PendingDeletePlannedTrips),
            deletePlannedTrip = DeletePlannedTripUseCase(PendingDeletePlannedTrips),
            getSeasonalPattern = GetSeasonalPatternUseCase(
                GetSightingsUseCase(PendingDeleteEmptyRepository),
                PendingDeleteWeather,
                ComputeFruitingLagDistributionUseCase(),
            ),
            offlineMapRepository = offlineMapRepository,
            mapPreferencesRepository = PendingDeleteMapPreferences,
            unitSystemPreferenceRepository = PendingDeleteUnitSystem,
            appThemePreferenceRepository = PendingDeleteTheme,
            getTodaysForecast = GetTodaysForecastUseCase(PendingDeleteWeather),
            getOfflineRegionReferenceCount = { id -> regionReferenceCounts[id] ?: 0 },
        )
        trackViewModel = TrackRecordingViewModel(
            trackRepository = trackRepository,
            startTrack = StartTrackUseCase(trackRepository, currentTime = PD_TIME, idGenerator = { "track-new" }),
            getWaypoints = GetWaypointsUseCase(waypointRepository),
            createWaypoint = CreateWaypointUseCase(waypointRepository, currentTime = PD_TIME, idGenerator = { "wp-new" }),
            deleteWaypoint = DeleteWaypointUseCase(waypointRepository),
            deleteTrack = DeleteTrackUseCase(trackRepository, waypointRepository, InMemoryKeptTrackPaths()),
            computeReturnToStart = ComputeReturnToStartUseCase(),
            detectOffTrack = DetectOffTrackUseCase(),
            locationTracker = PendingDeleteNoOpLocationTracker,
            getTracks = GetTracksUseCase(trackRepository),
            alertDelivery = { _: Alert -> },
            alertAudibility = PendingDeleteAudible,
            getWaypointReferenceCount = { id -> waypointReferenceCounts[id] ?: 0 },
        )
        cartographyRepository = PendingDeleteCartographyRepository(cartographyEntries)
        cartographyViewModel = CartographyViewModel(
            getEntries = GetCartographyEntriesUseCase(cartographyRepository),
            getDraftEntries = GetCartographyDraftEntriesUseCase(cartographyRepository),
            createEntry = CreateCartographyEntryUseCase(cartographyRepository, now = { 1_000L }, idGenerator = { "entry-new" }),
            saveEntry = SaveCartographyEntryUseCase(cartographyRepository, now = { 1_000L }),
            getEntry = GetCartographyEntryUseCase(cartographyRepository),
            commitEntry = CommitCartographyEntryUseCase(cartographyRepository, now = { 1_000L }),
            deleteEntry = DeleteCartographyEntryUseCase(cartographyRepository),
            getDerivedTrip = GetDerivedTripUseCase(
                mushroomLogRepository = logRepository,
                trackRepository = trackRepository,
                waypointRepository = waypointRepository,
                offlineRegionDayIndex = PendingDeleteNoRegionsDayIndex,
            ),
            getTripReportOfflineRegions = GetTripReportOfflineRegionsUseCase(offlineMapRepository),
            computeTrackStatistics = ComputeTrackStatisticsUseCase(),
            setShownOnMap = SetCartographyEntryShownOnMapUseCase(cartographyRepository),
            now = { 1_000L },
        )
        composeRule.setContent {
            val hostState = remember { SnackbarHostState() }
            val track by trackViewModel.uiState.collectAsState()
            val availability by availabilityViewModel.uiState.collectAsState()
            val log by logViewModel.uiState.collectAsState()
            val cartography by cartographyViewModel.uiState.collectAsState()
            val journalState = rememberJournalScreenState()
            Box(modifier = Modifier.fillMaxSize()) {
                JournalTab(
                    // What MainActivity passes: the state with a pending find left out.
                    uiState = log.hidingPendingDelete(),
                    onOpenCameraForLogEntry = {},
                    onOpenCameraForAlbum = {},
                    onOpenCameraForCartographyEntry = {},
                    mapSlot = PD_STUB_MAP,
                    pickerRegion = Region(lat = 45.326, lng = -122.634, radiusKm = 15),
                    basemap = Basemap.DEFAULT,
                    onOpenEntry = logViewModel::onOpenEntry,
                    onCloseEntry = logViewModel::onCloseEntry,
                    onStartEntry = logViewModel::onStartNewEntry,
                    onEntryChanged = logViewModel::onEntryEdited,
                    onStartEditingEntry = logViewModel::onStartEditingEntry,
                    onSaveEntry = logViewModel::onSaveEntry,
                    onCancelEditing = logViewModel::onCancelEditing,
                    onLeaveEditingIncidentally = logViewModel::onLeaveEditingIncidentally,
                    onAddPhoto = {},
                    onRemovePhoto = {},
                    onPullPhoto = {},
                    onDeleteEntry = logViewModel::requestDeleteEntry,
                    // J4b L1/L3: what MainActivity passes for a find tile's Edit and a photo's Delete.
                    onOpenEntryForEditing = logViewModel::onOpenEntryForEditing,
                    onRequestDeleteGalleryPhoto = logViewModel::requestDeleteGalleryPhoto,
                    // What the compact scaffold passes the album: the visible state's photos and counts.
                    galleryPhotos = log.hidingPendingDelete().galleryPhotos,
                    galleryPhotoEntryReferenceCounts = log.cartographyEntryPhotoReferenceCounts,
                    onSaveErrorDismissed = {},
                    // What MainActivity passes: the state with a pending entry left out, and the
                    // card's pending-delete request beside the report's own immediate delete.
                    cartographyUiState = cartography.hidingPendingDelete(),
                    onOpenCartographyEntry = cartographyViewModel::onOpenEntry,
                    onStartCartographyEntry = {},
                    onCloseCartographyEntry = cartographyViewModel::onCloseEntry,
                    onCartographyTextChanged = {},
                    onCartographyTagsChanged = {},
                    onSetFindDecision = { _, _ -> },
                    onSetTrackDecision = { _, _ -> },
                    onSetWaypointDecision = { _, _ -> },
                    onSetOfflineRegionDecision = { _, _ -> },
                    onToggleKeptPhoto = {},
                    onFinishCartographyEntry = {},
                    onDeleteCartographyEntry = cartographyViewModel::onDeleteEntry,
                    onRequestDeleteCartographyEntry = cartographyViewModel::requestDeleteEntry,
                    getCartographyEntryMapData = { _, _ -> PD_EMPTY_MAP_DATA },
                    getCartographyEntryOfflineRegion = { _, _ -> null },
                    getCartographyEntryCurrentLocation = { LocationResult.LocationUnavailable },
                    // What MainActivity passes: the real state, and the pending-delete request.
                    availabilityUiState = availability,
                    distanceUnit = DistanceUnit.MILES,
                    currentTime = CurrentTimeProvider { 0L },
                    onOfflineMapLatChanged = {},
                    onOfflineMapLngChanged = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = availabilityViewModel::requestDeleteOfflineRegion,
                    tracks = track.tracks,
                    onTracksOpened = {},
                    // What MainActivity passes: the visible list, and the pending-delete request.
                    waypoints = track.visibleWaypoints,
                    waypointsErrorMessage = track.waypointsErrorMessage,
                    onDeleteWaypoint = trackViewModel::requestRemoveWaypoint,
                    waypointEntryReferenceCounts = track.waypointEntryReferenceCounts,
                    journalState = journalState,
                )
                SnackbarHost(hostState, modifier = Modifier.align(Alignment.BottomCenter))
            }
            PendingDeleteSnackbarEffects(
                notices = listOfNotNull(
                    waypointDeleteNotice(track.pendingWaypointDelete, trackViewModel::undoRemoveWaypoint, trackViewModel::commitRemoveWaypoint),
                    offlineRegionDeleteNotice(
                        availability.pendingOfflineRegionDelete,
                        availabilityViewModel::undoDeleteOfflineRegion,
                        availabilityViewModel::commitDeleteOfflineRegion,
                    ),
                    findDeleteNotice(log.pendingDelete, logViewModel::undoDeleteEntry, logViewModel::commitDeleteEntry),
                    cartographyEntryDeleteNotice(
                        cartography.pendingDelete,
                        cartographyViewModel::undoDeleteEntry,
                        cartographyViewModel::commitDeleteEntry,
                    ),
                    galleryPhotoDeleteNotice(log.pendingPhotoDelete, logViewModel::undoDeleteGalleryPhoto, logViewModel::commitDeleteGalleryPhoto),
                ),
                hostState = hostState,
            )
        }
        composeRule.waitForIdle()
        if (!openRecords) return
        composeRule.onNodeWithText("Records").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(chip)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun swipeRowLeft(type: RecordType, id: String) {
        composeRule.onNodeWithTag(swipeToDeleteTag(type, id)).performScrollTo().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
    }

    /** Past [androidx.compose.material3.SnackbarDuration.Long] (10 s) and the exit animation. */
    private fun letSnackbarTimeOut() {
        composeRule.mainClock.advanceTimeBy(SNACKBAR_LONG_MILLIS + 1_000L)
        composeRule.waitForIdle()
    }

    private fun touchUndo() {
        composeRule.onNodeWithText("Undo").assert(hasClickAction()).performTouchInput { click() }
        composeRule.waitForIdle()
    }

    // ── Waypoints (D2) ──

    @Test
    fun `swiping a waypoint row end to start hides it, warns of its references, and deletes nothing yet`() {
        setScreen()

        // The top row, then (after Undo) the second: more than one row position.
        swipeRowLeft(RecordType.WAYPOINTS, "wp-creek")
        composeRule.onNodeWithText("Waypoint deleted · used in 2 journal entries").assertIsDisplayed()
        composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.WAYPOINTS, "wp-creek")).assertDoesNotExist()
        composeRule.onNodeWithText("Creek pin").assertDoesNotExist()
        assertEquals(emptyList<String>(), waypointRepository.deletedIds)
        touchUndo()

        swipeRowLeft(RecordType.WAYPOINTS, "wp-oak")
        composeRule.onNodeWithText("Waypoint deleted · used in 1 journal entry").assertIsDisplayed()
        composeRule.onNodeWithText("Big oak").assertDoesNotExist()
        composeRule.onNodeWithText("Creek pin").assertIsDisplayed()
        assertEquals(emptyList<String>(), waypointRepository.deletedIds)
    }

    @Test
    fun `a waypoint no entry uses says only Waypoint deleted`() {
        setScreen(waypointReferenceCounts = emptyMap())

        swipeRowLeft(RecordType.WAYPOINTS, "wp-oak")

        composeRule.onNodeWithText("Waypoint deleted").assertIsDisplayed()
        composeRule.onNodeWithText("used in", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the waypoint row has no trash icon any more`() {
        setScreen()
        composeRule.onNodeWithText("Creek pin").assertIsDisplayed()
        composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.WAYPOINTS, "wp-creek")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasContentDescription("Remove waypoint Creek pin")).assertDoesNotExist()
    }

    @Test
    fun `swiping start to end does not delete a waypoint`() {
        setScreen()

        composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.WAYPOINTS, "wp-creek")).performScrollTo().performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Creek pin").assertIsDisplayed()
        composeRule.onNodeWithText("Waypoint deleted", substring = true).assertDoesNotExist()
        letSnackbarTimeOut()
        assertEquals(emptyList<String>(), waypointRepository.deletedIds)
    }

    @Test
    fun `Undo brings the waypoint row back and deletes nothing, even after the timeout would have passed`() {
        setScreen()
        swipeRowLeft(RecordType.WAYPOINTS, "wp-creek")

        touchUndo()

        composeRule.onNodeWithText("Creek pin").assertIsDisplayed()
        composeRule.onNodeWithText("Waypoint deleted", substring = true).assertDoesNotExist()
        letSnackbarTimeOut()
        assertEquals(emptyList<String>(), waypointRepository.deletedIds)
        composeRule.onNodeWithText("Creek pin").assertIsDisplayed()
    }

    @Test
    fun `when the snackbar times out, exactly one delete runs, with the swiped waypoint's id`() {
        setScreen()
        swipeRowLeft(RecordType.WAYPOINTS, "wp-oak")
        assertEquals(emptyList<String>(), waypointRepository.deletedIds)

        letSnackbarTimeOut()

        assertEquals(listOf("wp-oak"), waypointRepository.deletedIds)
        composeRule.onNodeWithText("Waypoint deleted", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Big oak").assertDoesNotExist()
        composeRule.onNodeWithText("Creek pin").assertIsDisplayed()
    }

    @Test
    fun `a second swipe commits the first waypoint when its snackbar replaces the first`() {
        setScreen()
        swipeRowLeft(RecordType.WAYPOINTS, "wp-creek")

        swipeRowLeft(RecordType.WAYPOINTS, "wp-oak")

        assertEquals(listOf("wp-creek"), waypointRepository.deletedIds)
        composeRule.onNodeWithText("Waypoint deleted · used in 1 journal entry").assertIsDisplayed()
        composeRule.onNodeWithText("Waypoint deleted · used in 2 journal entries").assertDoesNotExist()
        letSnackbarTimeOut()
        assertEquals(listOf("wp-creek", "wp-oak"), waypointRepository.deletedIds)
    }

    @Test
    fun `the waypoint row's Delete accessibility action does the same pending delete`() {
        setScreen()
        val node = composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.WAYPOINTS, "wp-oak")).performScrollTo().fetchSemanticsNode()
        val delete = node.config[SemanticsActions.CustomActions].single { it.label == "Delete" }

        composeRule.runOnUiThread { delete.action() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Waypoint deleted · used in 1 journal entry").assertIsDisplayed()
        composeRule.onNodeWithText("Big oak").assertDoesNotExist()
        assertEquals(emptyList<String>(), waypointRepository.deletedIds)
        letSnackbarTimeOut()
        assertEquals(listOf("wp-oak"), waypointRepository.deletedIds)
    }

    @Test
    fun `a pending waypoint is gone from its chip count, from All, and from All's count`() {
        setScreen(chip = RecordsSubTab.ALL)
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.WAYPOINTS)).assert(hasText("2"))
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.ALL)).assert(hasText("4"))
        composeRule.onNodeWithTag(logbookRowTag(RecordType.WAYPOINTS, "wp-oak")).assertExists()

        // Swiped in All itself: the logbook's row is swipeable too.
        swipeRowLeft(RecordType.WAYPOINTS, "wp-oak")

        composeRule.onNodeWithTag(logbookRowTag(RecordType.WAYPOINTS, "wp-oak")).assertDoesNotExist()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.WAYPOINTS)).assert(hasText("1"))
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.ALL)).assert(hasText("3"))
        composeRule.onNodeWithText("Waypoint deleted · used in 1 journal entry").assertIsDisplayed()
        assertEquals(emptyList<String>(), waypointRepository.deletedIds)
    }
    // ── Offline regions (D3) ──

    @Test
    fun `swiping a region row hides it, warns of its references, and runs no tile delete yet`() {
        setScreen(chip = RecordsSubTab.OFFLINE_MAPS)
        composeRule.onNodeWithText("Tile budget: 150", substring = true).assertExists()

        // Two row positions: the first region, then (after Undo) the second.
        swipeRowLeft(RecordType.OFFLINE_MAPS, "7")
        composeRule.onNodeWithText("Offline map deleted · used in 2 journal entries").assertIsDisplayed()
        composeRule.onNodeWithText("Ridge").assertDoesNotExist()
        // Its tiles are still on disk, so the budget still counts them until the delete runs.
        composeRule.onNodeWithText("Tile budget: 150", substring = true).assertExists()
        assertEquals(emptyList<Long>(), offlineMapRepository.deletedIds)
        touchUndo()
        composeRule.onNodeWithText("Ridge").assertExists()

        swipeRowLeft(RecordType.OFFLINE_MAPS, "8")
        composeRule.onNodeWithText("Offline map deleted").assertIsDisplayed()
        composeRule.onNodeWithText("Saddle").assertDoesNotExist()
        assertEquals(emptyList<Long>(), offlineMapRepository.deletedIds)
    }

    @Test
    fun `the region row has no Delete button any more`() {
        setScreen(chip = RecordsSubTab.OFFLINE_MAPS)
        composeRule.onNodeWithText("Ridge").assertExists()
        composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.OFFLINE_MAPS, "7")).assertExists()
        composeRule.onNode(hasText("Delete") and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `Undo brings the region back and deletes no tiles, even after the timeout`() {
        setScreen(chip = RecordsSubTab.OFFLINE_MAPS)
        swipeRowLeft(RecordType.OFFLINE_MAPS, "7")

        touchUndo()
        letSnackbarTimeOut()

        composeRule.onNodeWithText("Ridge").assertExists()
        assertEquals(emptyList<Long>(), offlineMapRepository.deletedIds)
    }

    @Test
    fun `the region's tile delete runs once, with its id, only when the snackbar times out`() {
        setScreen(chip = RecordsSubTab.OFFLINE_MAPS)
        swipeRowLeft(RecordType.OFFLINE_MAPS, "7")
        composeRule.mainClock.advanceTimeBy(SNACKBAR_LONG_MILLIS / 2)
        composeRule.waitForIdle()
        assertEquals("not before the snackbar ends", emptyList<Long>(), offlineMapRepository.deletedIds)

        letSnackbarTimeOut()

        assertEquals(listOf(7L), offlineMapRepository.deletedIds)
        composeRule.onNodeWithText("Ridge").assertDoesNotExist()
        composeRule.onNodeWithText("Saddle").assertExists()
    }

    @Test
    fun `the region row's Delete accessibility action does the same pending delete`() {
        setScreen(chip = RecordsSubTab.OFFLINE_MAPS)
        val node = composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.OFFLINE_MAPS, "8")).performScrollTo().fetchSemanticsNode()
        val delete = node.config[SemanticsActions.CustomActions].single { it.label == "Delete" }

        composeRule.runOnUiThread { delete.action() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Offline map deleted").assertIsDisplayed()
        assertEquals(emptyList<Long>(), offlineMapRepository.deletedIds)
        letSnackbarTimeOut()
        assertEquals(listOf(8L), offlineMapRepository.deletedIds)
    }

    @Test
    fun `a pending region is gone from its chip count, from All, and from All's count`() {
        setScreen(chip = RecordsSubTab.ALL)
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.OFFLINE_MAPS)).assert(hasText("2"))
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.ALL)).assert(hasText("4"))

        swipeRowLeft(RecordType.OFFLINE_MAPS, "8")

        composeRule.onNodeWithTag(logbookRowTag(RecordType.OFFLINE_MAPS, "8")).assertDoesNotExist()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.OFFLINE_MAPS)).assert(hasText("1"))
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.ALL)).assert(hasText("3"))
        assertEquals(emptyList<Long>(), offlineMapRepository.deletedIds)
    }

    @Test
    fun `a region swipe after a waypoint swipe commits the waypoint when the region's snackbar replaces it`() {
        setScreen(chip = RecordsSubTab.ALL)
        swipeRowLeft(RecordType.WAYPOINTS, "wp-creek")
        composeRule.onNodeWithText("Waypoint deleted · used in 2 journal entries").assertIsDisplayed()

        swipeRowLeft(RecordType.OFFLINE_MAPS, "7")

        assertEquals(listOf("wp-creek"), waypointRepository.deletedIds)
        composeRule.onNodeWithText("Offline map deleted · used in 2 journal entries").assertIsDisplayed()
        composeRule.onNodeWithText("Waypoint deleted · used in 2 journal entries").assertDoesNotExist()
        assertEquals(emptyList<Long>(), offlineMapRepository.deletedIds)
        letSnackbarTimeOut()
        assertEquals(listOf(7L), offlineMapRepository.deletedIds)
    }
    // ── Finds, from the report (D4) ──

    private fun openFindReport(date: String) {
        composeRule.onNodeWithText("Find on $date", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
    }

    private fun deleteFromReport() {
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Delete entry").performClick()
        composeRule.waitForIdle()
    }

    private fun findTiles(date: String) = composeRule.onAllNodesWithText("Find on $date", useUnmergedTree = true)

    @Test
    fun `deleting a find from its report closes the report, hides the find, says Find deleted, and deletes nothing yet`() {
        setScreen(chip = RecordsSubTab.FINDS, finds = listOf(PD_FIND_A, PD_FIND_B))
        openFindReport("2026-09-20")

        deleteFromReport()

        composeRule.onNodeWithContentDescription("Entry options").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("New log entry").assertIsDisplayed()
        findTiles("2026-09-20").assertCountEquals(0)
        findTiles("2026-09-21").assertCountEquals(1)
        composeRule.onNodeWithText("Find deleted").assertIsDisplayed()
        assertEquals(emptyList<String>(), logRepository.deletedIds)
    }

    @Test
    fun `when the find's snackbar times out, exactly one delete runs, with the find's id`() {
        setScreen(chip = RecordsSubTab.FINDS, finds = listOf(PD_FIND_A, PD_FIND_B))
        openFindReport("2026-09-21")
        deleteFromReport()
        assertEquals(emptyList<String>(), logRepository.deletedIds)

        letSnackbarTimeOut()

        assertEquals(listOf("find-b"), logRepository.deletedIds)
        findTiles("2026-09-21").assertCountEquals(0)
        findTiles("2026-09-20").assertCountEquals(1)
    }

    @Test
    fun `Undo brings the find back to the gallery, leaves its report closed, and deletes nothing`() {
        setScreen(chip = RecordsSubTab.FINDS, finds = listOf(PD_FIND_A, PD_FIND_B))
        openFindReport("2026-09-20")
        deleteFromReport()

        touchUndo()
        letSnackbarTimeOut()

        findTiles("2026-09-20").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Entry options").assertDoesNotExist()
        assertEquals(emptyList<String>(), logRepository.deletedIds)
    }

    private fun openFindEditForm(date: String) {
        openFindReport(date)
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.waitForIdle()
    }

    private fun deleteFromEditForm() {
        composeRule.onNodeWithContentDescription("Delete this entry").performClick()
        composeRule.waitForIdle()
    }

    // J4b L4 (owner: "Say 'Changes discarded' (Recommended)"). Rewritten from J4's "deleting from the
    // find's edit form is the same pending delete, of the open draft", which asserted "Find deleted"
    // for this case; the deferral and the id deleted are unchanged, only the message is new.
    @Test
    fun `Delete in a re-edited find's form discards only the draft and says Changes discarded, not Find deleted`() {
        setScreen(chip = RecordsSubTab.FINDS, finds = listOf(PD_FIND_A))
        openFindEditForm("2026-09-20")

        deleteFromEditForm()

        composeRule.onNodeWithContentDescription("Delete this entry").assertDoesNotExist()
        composeRule.onNodeWithText("Changes discarded").assertIsDisplayed()
        composeRule.onNodeWithText("Find deleted").assertDoesNotExist()
        composeRule.onNodeWithText("Undo").assert(hasClickAction())
        assertEquals(emptyList<String>(), logRepository.deletedIds)
        letSnackbarTimeOut()
        // The form was editing the re-edit's draft row, so that row is what is deleted, as before J4.
        assertEquals(listOf("draft-of-find"), logRepository.deletedIds)
        findTiles("2026-09-20").assertCountEquals(1)
    }

    @Test
    fun `Undo on Changes discarded deletes nothing, even after the timeout`() {
        setScreen(chip = RecordsSubTab.FINDS, finds = listOf(PD_FIND_A))
        openFindEditForm("2026-09-20")
        deleteFromEditForm()
        composeRule.onNodeWithText("Changes discarded").assertIsDisplayed()

        touchUndo()
        letSnackbarTimeOut()

        assertEquals(emptyList<String>(), logRepository.deletedIds)
        findTiles("2026-09-20").assertCountEquals(1)
    }

    @Test
    fun `Delete in a new find's form, with no committed original, still says Find deleted`() {
        setScreen(chip = RecordsSubTab.FINDS, finds = listOf(PD_FIND_A))
        composeRule.onNodeWithContentDescription("New log entry").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Delete this entry").assertIsDisplayed()

        deleteFromEditForm()

        composeRule.onNodeWithText("Find deleted").assertIsDisplayed()
        composeRule.onNodeWithText("Changes discarded").assertDoesNotExist()
        assertEquals(emptyList<String>(), logRepository.deletedIds)
        letSnackbarTimeOut()
        assertEquals(listOf("find-new"), logRepository.deletedIds)
    }

    @Test
    fun `a pending find is gone from the Finds count, from All, and from All's count`() {
        setScreen(chip = RecordsSubTab.ALL, finds = listOf(PD_FIND_A, PD_FIND_B))
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.FINDS)).assert(hasText("2"))
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.ALL)).assert(hasText("6"))
        composeRule.onNodeWithTag(logbookRowTag(RecordType.FINDS, "find-a")).performScrollTo().performClick()
        composeRule.waitForIdle()

        deleteFromReport()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.ALL)).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(logbookRowTag(RecordType.FINDS, "find-a")).assertDoesNotExist()
        composeRule.onNodeWithTag(logbookRowTag(RecordType.FINDS, "find-b")).assertExists()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.FINDS)).assert(hasText("1"))
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.ALL)).assert(hasText("5"))
        composeRule.onNodeWithText("Find deleted").assertIsDisplayed()
        assertEquals(emptyList<String>(), logRepository.deletedIds)
    }

    @Test
    fun `find tiles have no swipe`() {
        setScreen(chip = RecordsSubTab.ALL, finds = listOf(PD_FIND_A))
        composeRule.onNodeWithTag(logbookRowTag(RecordType.FINDS, "find-a")).assertExists()
        composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.FINDS, "find-a")).assertDoesNotExist()
        composeRule.onNodeWithTag(logbookRowTag(RecordType.FINDS, "find-a")).performScrollTo().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Find deleted").assertDoesNotExist()
        letSnackbarTimeOut()
        assertEquals(emptyList<String>(), logRepository.deletedIds)
    }

    // ── J4b L6: waypoint and region rows swipe in two stages ──
    // (`prompts/preserved/2026-09-27-23.md`.) A short swipe settles a row open with its actions
    // behind it; J4's full-swipe tests above still run a full `swipeLeft()` and still delete. Neither
    // type has an edit path in the app today (the J4b report cites the search), so each reveals Delete
    // only.

    /** A short end-to-start swipe: about [distanceDp] of finger travel from near the row's end edge, slowly. */
    private fun shortSwipeLeft(tag: String, distanceDp: Float = 64f) {
        composeRule.onNodeWithTag(tag).performScrollTo().performTouchInput {
            val y = centerY
            val startX = right - 8f
            swipe(start = Offset(startX, y), end = Offset(startX - distanceDp.dp.toPx(), y), durationMillis = 600)
        }
        composeRule.waitForIdle()
    }

    /** Several real touches across [tag]'s own bounds, each a fresh attempt set up by [before]. */
    private fun touchAcross(tag: String, before: () -> Unit, after: (Int) -> Unit) {
        listOf(0.2f to 0.3f, 0.5f to 0.5f, 0.8f to 0.7f).forEachIndexed { index, (fx, fy) ->
            before()
            composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * fx, height * fy)) }
            composeRule.waitForIdle()
            after(index)
        }
    }

    @Test
    fun `a short swipe on a waypoint row leaves it open with only Delete behind it, and deletes nothing`() {
        setScreen()
        val creek = swipeToDeleteTag(RecordType.WAYPOINTS, "wp-creek")
        val oak = swipeToDeleteTag(RecordType.WAYPOINTS, "wp-oak")

        for (row in listOf(creek, oak)) {
            shortSwipeLeft(row)
            composeRule.onNodeWithTag(twoStageSwipeDeleteTag(row)).assertIsDisplayed()
            composeRule.onNodeWithTag(twoStageSwipeEditTag(row)).assertDoesNotExist()
            composeRule.onNodeWithTag(row).assertExists()
        }
        composeRule.onNodeWithText("Waypoint deleted", substring = true).assertDoesNotExist()
        letSnackbarTimeOut()
        assertEquals(emptyList<String>(), waypointRepository.deletedIds)
    }

    @Test
    fun `touching a waypoint row's revealed Delete, anywhere on it, is J4's pending delete with its snackbar`() {
        setScreen()
        val oak = swipeToDeleteTag(RecordType.WAYPOINTS, "wp-oak")
        touchAcross(
            tag = twoStageSwipeDeleteTag(oak),
            before = { shortSwipeLeft(oak) },
            after = {
                composeRule.onNodeWithText("Waypoint deleted · used in 1 journal entry").assertIsDisplayed()
                composeRule.onNodeWithText("Big oak").assertDoesNotExist()
                assertEquals(emptyList<String>(), waypointRepository.deletedIds)
                touchUndo()
                composeRule.onNodeWithText("Big oak").assertIsDisplayed()
            },
        )
        shortSwipeLeft(oak)
        composeRule.onNodeWithTag(twoStageSwipeDeleteTag(oak)).performTouchInput { click(center) }
        composeRule.waitForIdle()
        letSnackbarTimeOut()
        assertEquals(listOf("wp-oak"), waypointRepository.deletedIds)
    }

    @Test
    fun `a revealed waypoint row closes on a swipe back, and opening another row closes the first`() {
        setScreen()
        val creek = swipeToDeleteTag(RecordType.WAYPOINTS, "wp-creek")
        val oak = swipeToDeleteTag(RecordType.WAYPOINTS, "wp-oak")
        shortSwipeLeft(creek)
        composeRule.onNodeWithTag(twoStageSwipeDeleteTag(creek)).assertIsDisplayed()

        composeRule.onNodeWithTag(creek).performTouchInput { swipeRight(startX = left + 8f, endX = left + 8f + 96.dp.toPx(), durationMillis = 600) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(twoStageSwipeDeleteTag(creek)).assertDoesNotExist()

        shortSwipeLeft(creek)
        shortSwipeLeft(oak)
        composeRule.onNodeWithTag(twoStageSwipeDeleteTag(oak)).assertIsDisplayed()
        composeRule.onNodeWithTag(twoStageSwipeDeleteTag(creek)).assertDoesNotExist()
        assertEquals(emptyList<String>(), waypointRepository.deletedIds)
    }

    @Test
    fun `waypoint and region rows carry a Delete accessibility action and no Edit, since neither has an edit path`() {
        setScreen(chip = RecordsSubTab.ALL)
        for (row in listOf(swipeToDeleteTag(RecordType.WAYPOINTS, "wp-oak"), swipeToDeleteTag(RecordType.OFFLINE_MAPS, "7"))) {
            val labels = composeRule.onNodeWithTag(row).performScrollTo().fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
            assertEquals(listOf("Delete"), labels)
        }
    }

    @Test
    fun `a short swipe on a region row reveals Delete, whose touch pends the region and runs no tile delete until the snackbar ends`() {
        setScreen(chip = RecordsSubTab.OFFLINE_MAPS)
        val first = swipeToDeleteTag(RecordType.OFFLINE_MAPS, "7")
        val second = swipeToDeleteTag(RecordType.OFFLINE_MAPS, "8")
        shortSwipeLeft(second)
        composeRule.onNodeWithTag(twoStageSwipeDeleteTag(second)).assertIsDisplayed()
        composeRule.onNodeWithTag(twoStageSwipeEditTag(second)).assertDoesNotExist()

        touchAcross(
            tag = twoStageSwipeDeleteTag(first),
            before = { shortSwipeLeft(first) },
            after = {
                composeRule.onNodeWithText("Offline map deleted · used in 2 journal entries").assertIsDisplayed()
                assertEquals(emptyList<Long>(), offlineMapRepository.deletedIds)
                touchUndo()
            },
        )
        shortSwipeLeft(first)
        composeRule.onNodeWithTag(twoStageSwipeDeleteTag(first)).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(SNACKBAR_LONG_MILLIS / 2)
        composeRule.waitForIdle()
        assertEquals("not before the snackbar ends", emptyList<Long>(), offlineMapRepository.deletedIds)
        letSnackbarTimeOut()
        assertEquals(listOf(7L), offlineMapRepository.deletedIds)
    }

    /** A slow end-to-start swipe (no fling) over [fraction] of the row's own width, from near its end edge. */
    private fun slowSwipeLeft(tag: String, fraction: Float) {
        composeRule.onNodeWithTag(tag).performScrollTo().performTouchInput {
            val y = centerY
            val startX = right - 4f
            swipe(start = Offset(startX, y), end = Offset(startX - width * fraction, y), durationMillis = 3_000)
        }
        composeRule.waitForIdle()
    }

    // The delete threshold by position alone: a slow swipe carries no fling, so where it ends
    // decides. Past half of the way from open to the row's whole width deletes; short of it rests open.
    @Test
    fun `a slow swipe past the delete threshold deletes, and one short of it only opens the row`() {
        setScreen()
        val oak = swipeToDeleteTag(RecordType.WAYPOINTS, "wp-oak")

        val creek = swipeToDeleteTag(RecordType.WAYPOINTS, "wp-creek")

        slowSwipeLeft(oak, fraction = 0.45f)
        composeRule.onNodeWithTag(twoStageSwipeDeleteTag(oak)).assertIsDisplayed()
        composeRule.onNodeWithText("Waypoint deleted", substring = true).assertDoesNotExist()

        // From closed (a different row), so the whole distance is this one swipe's.
        slowSwipeLeft(creek, fraction = 0.8f)
        composeRule.onNodeWithText("Waypoint deleted · used in 2 journal entries").assertIsDisplayed()
        composeRule.onNodeWithText("Creek pin").assertDoesNotExist()
        assertEquals(emptyList<String>(), waypointRepository.deletedIds)
    }

    @Test
    fun `a revealed row in All closes when a touch lands elsewhere on the list`() {
        setScreen(chip = RecordsSubTab.ALL)
        val oak = swipeToDeleteTag(RecordType.WAYPOINTS, "wp-oak")
        shortSwipeLeft(oak)
        composeRule.onNodeWithTag(twoStageSwipeDeleteTag(oak)).assertIsDisplayed()

        composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.WAYPOINTS, "wp-creek")).performScrollTo().performTouchInput { click(Offset(width * 0.3f, centerY)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(twoStageSwipeDeleteTag(oak)).assertDoesNotExist()
        composeRule.onNodeWithText("Waypoint deleted", substring = true).assertDoesNotExist()
        assertEquals(emptyList<String>(), waypointRepository.deletedIds)
    }

    // ── J4b L2: entry cards swipe in two stages ──
    // (`prompts/preserved/2026-09-27-23.md`.) A short swipe reveals Edit and Delete; Edit opens the
    // entry editor; Delete, a full swipe, and the "Delete" accessibility action go through the new
    // Cartography-entry pending holder with its Undo snackbar.

    private fun entryRow(id: String) = entrySwipeTag(id)

    private fun assertEditorShowsEntry(id: String) {
        assertEquals(id, cartographyViewModel.uiState.value.editingEntry?.id)
        composeRule.onNodeWithText("Your own account (optional)").assertIsDisplayed()
    }

    private fun closeOpenEntry() {
        composeRule.onNodeWithContentDescription("Back to Cartography").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ENTRIES_HOME_TAG).assertExists()
    }

    @Test
    fun `a short swipe on an entry card leaves it open with Edit and Delete behind it, and deletes nothing`() {
        setScreen(cartographyEntries = listOf(PD_ENTRY_A, PD_ENTRY_B), openRecords = false)

        for (id in listOf(PD_ENTRY_A.id, PD_ENTRY_B.id)) {
            shortSwipeLeft(entryRow(id), distanceDp = 110f)
            composeRule.onNodeWithTag(twoStageSwipeEditTag(entryRow(id))).assertIsDisplayed()
            composeRule.onNodeWithTag(twoStageSwipeDeleteTag(entryRow(id))).assertIsDisplayed()
        }
        composeRule.onNodeWithText("Entry deleted").assertDoesNotExist()
        letSnackbarTimeOut()
        assertEquals(emptyList<String>(), cartographyRepository.deletedIds)
    }

    @Test
    fun `touching a card's revealed Edit, anywhere on it, opens that entry in the editor`() {
        setScreen(cartographyEntries = listOf(PD_ENTRY_A, PD_ENTRY_B), openRecords = false)
        val row = entryRow(PD_ENTRY_B.id)
        touchAcross(
            tag = twoStageSwipeEditTag(row),
            before = { shortSwipeLeft(row, distanceDp = 110f) },
            after = {
                assertEditorShowsEntry(PD_ENTRY_B.id)
                closeOpenEntry()
            },
        )
        assertEquals(emptyList<String>(), cartographyRepository.deletedIds)
    }

    @Test
    fun `touching a card's revealed Delete, anywhere on it, hides it and says Entry deleted, and deletes only on timeout`() {
        setScreen(cartographyEntries = listOf(PD_ENTRY_A, PD_ENTRY_B), openRecords = false)
        val row = entryRow(PD_ENTRY_A.id)
        touchAcross(
            tag = twoStageSwipeDeleteTag(row),
            before = { shortSwipeLeft(row, distanceDp = 110f) },
            after = {
                composeRule.onNodeWithText("Entry deleted").assertIsDisplayed()
                composeRule.onNodeWithTag(row).assertDoesNotExist()
                composeRule.onNodeWithTag(entryRow(PD_ENTRY_B.id)).assertExists()
                assertEquals(emptyList<String>(), cartographyRepository.deletedIds)
                touchUndo()
                composeRule.onNodeWithTag(row).assertExists()
            },
        )
        letSnackbarTimeOut()
        assertEquals("Undo deleted nothing", emptyList<String>(), cartographyRepository.deletedIds)

        shortSwipeLeft(row, distanceDp = 110f)
        composeRule.onNodeWithTag(twoStageSwipeDeleteTag(row)).performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals(emptyList<String>(), cartographyRepository.deletedIds)
        letSnackbarTimeOut()
        assertEquals(listOf(PD_ENTRY_A.id), cartographyRepository.deletedIds)
    }

    @Test
    fun `a full swipe on an entry card deletes it through the same snackbar, once, on timeout`() {
        setScreen(cartographyEntries = listOf(PD_ENTRY_A, PD_ENTRY_B), openRecords = false)

        composeRule.onNodeWithTag(entryRow(PD_ENTRY_B.id)).performScrollTo().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Entry deleted").assertIsDisplayed()
        composeRule.onNodeWithTag(entryRow(PD_ENTRY_B.id)).assertDoesNotExist()
        assertEquals(emptyList<String>(), cartographyRepository.deletedIds)
        letSnackbarTimeOut()
        assertEquals(listOf(PD_ENTRY_B.id), cartographyRepository.deletedIds)
    }

    @Test
    fun `a tap on a revealed card's body closes it without opening the entry`() {
        setScreen(cartographyEntries = listOf(PD_ENTRY_A, PD_ENTRY_B), openRecords = false)
        val row = entryRow(PD_ENTRY_A.id)
        shortSwipeLeft(row, distanceDp = 110f)
        composeRule.onNodeWithTag(twoStageSwipeEditTag(row)).assertIsDisplayed()

        composeRule.onNodeWithTag(row).performTouchInput { click(Offset(width * 0.15f, height * 0.5f)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(twoStageSwipeEditTag(row)).assertDoesNotExist()
        assertNull(cartographyViewModel.uiState.value.editingEntry)
        composeRule.onNodeWithTag(ENTRIES_HOME_TAG).assertExists()
    }

    @Test
    fun `opening a second card closes the first`() {
        setScreen(cartographyEntries = listOf(PD_ENTRY_A, PD_ENTRY_B), openRecords = false)
        shortSwipeLeft(entryRow(PD_ENTRY_A.id), distanceDp = 110f)

        shortSwipeLeft(entryRow(PD_ENTRY_B.id), distanceDp = 110f)

        composeRule.onNodeWithTag(twoStageSwipeEditTag(entryRow(PD_ENTRY_B.id))).assertIsDisplayed()
        composeRule.onNodeWithTag(twoStageSwipeEditTag(entryRow(PD_ENTRY_A.id))).assertDoesNotExist()
    }

    @Test
    fun `a plain tap on a card still opens its report, not the editor`() {
        setScreen(cartographyEntries = listOf(PD_ENTRY_A, PD_ENTRY_B), openRecords = false)
        composeRule.onNodeWithTag(entryRow(PD_ENTRY_A.id)).performTouchInput { click(Offset(width * 0.3f, height * 0.5f)) }
        composeRule.waitForIdle()

        assertEquals(PD_ENTRY_A.id, cartographyViewModel.uiState.value.editingEntry?.id)
        composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
        composeRule.onNodeWithText("Your own account (optional)").assertDoesNotExist()
    }

    @Test
    fun `a card's Edit and Delete accessibility actions open the editor and pend the delete`() {
        setScreen(cartographyEntries = listOf(PD_ENTRY_A, PD_ENTRY_B), openRecords = false)
        fun action(id: String, label: String) = composeRule.onNodeWithTag(entryRow(id)).performScrollTo().fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single { it.label == label }
        assertEquals(listOf("Edit", "Delete"), composeRule.onNodeWithTag(entryRow(PD_ENTRY_A.id)).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label })

        val edit = action(PD_ENTRY_A.id, "Edit")
        composeRule.runOnUiThread { edit.action() }
        composeRule.waitForIdle()
        assertEditorShowsEntry(PD_ENTRY_A.id)
        closeOpenEntry()

        val delete = action(PD_ENTRY_B.id, "Delete")
        composeRule.runOnUiThread { delete.action() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Entry deleted").assertIsDisplayed()
        assertEquals(emptyList<String>(), cartographyRepository.deletedIds)
        letSnackbarTimeOut()
        assertEquals(listOf(PD_ENTRY_B.id), cartographyRepository.deletedIds)
    }

    @Test
    fun `a draft deleted from the drafts list says Draft deleted, and its Edit opens that draft`() {
        setScreen(cartographyEntries = listOf(PD_ENTRY_A, PD_DRAFT_1, PD_DRAFT_2), openRecords = false)
        composeRule.onNodeWithTag("entries-drafts-continue").performClick()
        composeRule.waitForIdle()
        val row = entryRow(PD_DRAFT_2.id)

        shortSwipeLeft(row, distanceDp = 110f)
        composeRule.onNodeWithTag(twoStageSwipeEditTag(row)).performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals(PD_DRAFT_2.id, cartographyViewModel.uiState.value.editingEntry?.id)
        composeRule.onNodeWithText("Your own account (optional)").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to Cartography").performClick()
        composeRule.waitForIdle()

        shortSwipeLeft(row, distanceDp = 110f)
        composeRule.onNodeWithTag(twoStageSwipeDeleteTag(row)).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Draft deleted").assertIsDisplayed()
        composeRule.onNodeWithText("Entry deleted").assertDoesNotExist()
        assertEquals(emptyList<String>(), cartographyRepository.deletedIds)
        letSnackbarTimeOut()
        assertEquals(listOf(PD_DRAFT_2.id), cartographyRepository.deletedIds)
    }

    // ── J4b L1: find tiles open a long-press menu ──
    // (The owner's "grids long-press": find tiles and album photos keep long-press.) Real
    // long-presses at several points of each tile; the menu's items chosen by coordinate touches.

    private fun findTile(date: String) = composeRule.onNodeWithText("Find on $date")

    private fun menuItems() = composeRule.onAllNodes(hasClickAction() and hasAnyAncestor(isPopup()))

    private fun touchMenuItem(tag: String) {
        composeRule.onNodeWithTag(tag).assertIsDisplayed().performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private val tilePoints = listOf(0.25f to 0.3f, 0.5f to 0.5f, 0.75f to 0.8f)

    @Test
    fun `a long-press anywhere on a find tile opens a menu of exactly Edit and Delete`() {
        setScreen(chip = RecordsSubTab.FINDS, finds = listOf(PD_FIND_A, PD_FIND_B))
        for (date in listOf("2026-09-20", "2026-09-21")) {
            for ((fx, fy) in tilePoints) {
                findTile(date).performTouchInput { longClick(Offset(width * fx, height * fy)) }
                composeRule.waitForIdle()
                menuItems().assertCountEquals(2)
                composeRule.onNodeWithTag(TILE_OPTIONS_EDIT_TAG).assert(hasText("Edit"))
                composeRule.onNodeWithTag(TILE_OPTIONS_DELETE_TAG).assert(hasText("Delete"))
                // Leave the menu by choosing Delete and undoing it, so every point is a fresh attempt.
                touchMenuItem(TILE_OPTIONS_DELETE_TAG)
                touchUndo()
            }
        }
        letSnackbarTimeOut()
        assertEquals(emptyList<String>(), logRepository.deletedIds)
    }

    @Test
    fun `a find tile's Edit opens that find in its edit form`() {
        setScreen(chip = RecordsSubTab.FINDS, finds = listOf(PD_FIND_A, PD_FIND_B))
        findTile("2026-09-21").performTouchInput { longClick(Offset(width * 0.5f, height * 0.4f)) }
        composeRule.waitForIdle()

        touchMenuItem(TILE_OPTIONS_EDIT_TAG)

        composeRule.onNodeWithContentDescription("Delete this entry").assertIsDisplayed()
        val editing = logViewModel.uiState.value.editingEntry
        assertEquals("the edit form works on a draft of that find", PD_FIND_B.id, editing?.draftOfEntryId)
    }

    @Test
    fun `a find tile's Delete hides it, says Find deleted, and deletes it once on timeout`() {
        setScreen(chip = RecordsSubTab.FINDS, finds = listOf(PD_FIND_A, PD_FIND_B))
        findTile("2026-09-20").performTouchInput { longClick(Offset(width * 0.3f, height * 0.6f)) }
        composeRule.waitForIdle()

        touchMenuItem(TILE_OPTIONS_DELETE_TAG)

        findTiles("2026-09-20").assertCountEquals(0)
        findTiles("2026-09-21").assertCountEquals(1)
        composeRule.onNodeWithText("Find deleted").assertIsDisplayed()
        assertEquals(emptyList<String>(), logRepository.deletedIds)
        letSnackbarTimeOut()
        assertEquals(listOf(PD_FIND_A.id), logRepository.deletedIds)
    }

    @Test
    fun `a find tile's Edit and Delete accessibility actions, and its long-press label`() {
        setScreen(chip = RecordsSubTab.FINDS, finds = listOf(PD_FIND_A, PD_FIND_B))
        val node = findTile("2026-09-20").fetchSemanticsNode()
        assertEquals("Options for Find on 2026-09-20", node.config[SemanticsActions.OnLongClick].label)
        assertEquals(listOf("Edit", "Delete"), node.config[SemanticsActions.CustomActions].map { it.label })

        val delete = node.config[SemanticsActions.CustomActions].single { it.label == "Delete" }
        composeRule.runOnUiThread { delete.action() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Find deleted").assertIsDisplayed()
        touchUndo()

        val edit = findTile("2026-09-21").fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Edit" }
        composeRule.runOnUiThread { edit.action() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Delete this entry").assertIsDisplayed()
        assertEquals(PD_FIND_B.id, logViewModel.uiState.value.editingEntry?.draftOfEntryId)
        assertEquals(emptyList<String>(), logRepository.deletedIds)
    }

    @Test
    fun `a plain tap on a find tile still opens its report`() {
        setScreen(chip = RecordsSubTab.FINDS, finds = listOf(PD_FIND_A))
        findTile("2026-09-20").performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Delete this entry").assertDoesNotExist()
        menuItems().assertCountEquals(0)
    }

    @Test
    fun `a find tile in All has the same menu, whose Delete pends it and whose Edit opens its form under the Finds chip`() {
        setScreen(chip = RecordsSubTab.ALL, finds = listOf(PD_FIND_A, PD_FIND_B))
        val rowA = composeRule.onNodeWithTag(logbookRowTag(RecordType.FINDS, "find-a")).performScrollTo()
        rowA.performTouchInput { longClick(Offset(width * 0.4f, height * 0.6f)) }
        composeRule.waitForIdle()
        menuItems().assertCountEquals(2)
        touchMenuItem(TILE_OPTIONS_DELETE_TAG)
        composeRule.onNodeWithTag(logbookRowTag(RecordType.FINDS, "find-a")).assertDoesNotExist()
        composeRule.onNodeWithText("Find deleted").assertIsDisplayed()
        assertEquals(emptyList<String>(), logRepository.deletedIds)

        composeRule.onNodeWithTag(logbookRowTag(RecordType.FINDS, "find-b")).performScrollTo()
            .performTouchInput { longClick(Offset(width * 0.6f, height * 0.4f)) }
        composeRule.waitForIdle()
        touchMenuItem(TILE_OPTIONS_EDIT_TAG)
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.FINDS)).assert(androidx.compose.ui.test.isSelected())
        composeRule.onNodeWithContentDescription("Delete this entry").assertIsDisplayed()
        assertEquals(PD_FIND_B.id, logViewModel.uiState.value.editingEntry?.draftOfEntryId)
    }

    // ── J4b L3: album photos open a long-press menu ──
    // Delete only: the app has no photo details or location editing screen (the J4b report cites the
    // search), so there is no Edit to open, and none is built (the dispatch's stop condition).

    private fun openAlbum() {
        composeRule.onNodeWithTag(ENTRIES_VIEW_ALBUM_TAG).performClick()
        composeRule.waitForIdle()
    }

    private fun photoTile(id: String) = composeRule.onNodeWithTag(albumPhotoTestTag(id))

    // Chosen, before F5, to stay off the corner delete button (the top-end 48 dp); F5 removed it, and
    // the corner itself is sampled by the F5 test below.
    private val photoPoints = listOf(0.2f to 0.3f, 0.5f to 0.7f, 0.8f to 0.85f)

    @Test
    fun `a long-press anywhere on an album photo opens a menu of exactly Delete`() {
        setScreen(photos = listOf(PD_PHOTO_A, PD_PHOTO_B), openRecords = false)
        openAlbum()
        for (id in listOf(PD_PHOTO_A.photo.id, PD_PHOTO_B.photo.id)) {
            for ((fx, fy) in photoPoints) {
                photoTile(id).performTouchInput { longClick(Offset(width * fx, height * fy)) }
                composeRule.waitForIdle()
                menuItems().assertCountEquals(1)
                composeRule.onNodeWithTag(TILE_OPTIONS_DELETE_TAG).assert(hasText("Delete"))
                composeRule.onNodeWithTag(TILE_OPTIONS_EDIT_TAG).assertDoesNotExist()
                touchMenuItem(TILE_OPTIONS_DELETE_TAG)
                touchUndo()
            }
        }
        letSnackbarTimeOut()
        assertEquals(emptyList<String>(), logRepository.deletedPhotoIds)
        assertEquals(emptyList<String>(), photoStore.deletedPhotoIds)
    }

    @Test
    fun `an album photo's Delete hides it and warns of its uses, and deletes its row and file only when the snackbar ends`() {
        setScreen(photos = listOf(PD_PHOTO_A, PD_PHOTO_B), photoJournalCounts = mapOf(PD_PHOTO_A.photo.id to 2), openRecords = false)
        openAlbum()
        photoTile(PD_PHOTO_A.photo.id).performTouchInput { longClick(Offset(width * 0.3f, height * 0.6f)) }
        composeRule.waitForIdle()

        touchMenuItem(TILE_OPTIONS_DELETE_TAG)

        photoTile(PD_PHOTO_A.photo.id).assertDoesNotExist()
        photoTile(PD_PHOTO_B.photo.id).assertExists()
        composeRule.onNodeWithText("Photo deleted · used in 1 find and 2 journal entries").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(SNACKBAR_LONG_MILLIS / 2)
        composeRule.waitForIdle()
        assertEquals("no row delete before the snackbar ends", emptyList<String>(), logRepository.deletedPhotoIds)
        assertEquals("no file delete before the snackbar ends", emptyList<String>(), photoStore.deletedPhotoIds)
        letSnackbarTimeOut()
        assertEquals(listOf(PD_PHOTO_A.photo.id), logRepository.deletedPhotoIds)
        assertEquals(listOf(PD_PHOTO_A.photo.id), photoStore.deletedPhotoIds)
    }

    @Test
    fun `Undo on an album photo brings it back and deletes neither row nor file`() {
        setScreen(photos = listOf(PD_PHOTO_A, PD_PHOTO_B), openRecords = false)
        openAlbum()
        photoTile(PD_PHOTO_B.photo.id).performTouchInput { longClick(Offset(width * 0.5f, height * 0.7f)) }
        composeRule.waitForIdle()
        touchMenuItem(TILE_OPTIONS_DELETE_TAG)
        composeRule.onNodeWithText("Photo deleted").assertIsDisplayed()

        touchUndo()
        letSnackbarTimeOut()

        photoTile(PD_PHOTO_B.photo.id).assertExists()
        assertEquals(emptyList<String>(), logRepository.deletedPhotoIds)
        assertEquals(emptyList<String>(), photoStore.deletedPhotoIds)
    }

    @Test
    fun `an album photo's Delete accessibility action and long-press label, and no Edit action`() {
        setScreen(photos = listOf(PD_PHOTO_A, PD_PHOTO_B), openRecords = false)
        openAlbum()
        val node = composeRule.onNode(hasAnyAncestor(hasTestTag(albumPhotoTestTag(PD_PHOTO_B.photo.id))) and hasClickAction() and hasLongClickLabel("Options for photo"))
            .fetchSemanticsNode()
        assertEquals(listOf("Delete"), node.config[SemanticsActions.CustomActions].map { it.label })

        val delete = node.config[SemanticsActions.CustomActions].single()
        composeRule.runOnUiThread { delete.action() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Photo deleted").assertIsDisplayed()
        photoTile(PD_PHOTO_B.photo.id).assertDoesNotExist()
        letSnackbarTimeOut()
        assertEquals(listOf(PD_PHOTO_B.photo.id), logRepository.deletedPhotoIds)
        assertEquals(listOf(PD_PHOTO_B.photo.id), photoStore.deletedPhotoIds)
    }

    /**
     * Picker-fixes dispatch F5 (owner: "Remove the corner button (Recommended)", then "Remove
     * everywhere now"): the album photo has no corner trash button, so long-press Delete with Undo
     * is the only delete in the album. The corner the button used to cover is sampled by a real
     * long-press, the one region [photoPoints] above stayed off: with the button there, that touch
     * went to the button and opened no menu.
     */
    @Test
    fun `an album photo has no corner delete control, and a long-press at that corner opens the Delete menu`() {
        setScreen(photos = listOf(PD_PHOTO_A, PD_PHOTO_B), openRecords = false)
        openAlbum()
        photoTile(PD_PHOTO_A.photo.id).assertExists()
        composeRule.onAllNodesWithContentDescription("Delete this photo").assertCountEquals(0)
        composeRule.onAllNodes(hasAnyAncestor(hasTestTag(albumPhotoTestTag(PD_PHOTO_A.photo.id))) and hasClickAction())
            .assertCountEquals(1) // the photo itself, nothing else touchable on the tile

        photoTile(PD_PHOTO_A.photo.id).performTouchInput { longClick(Offset(width * 0.88f, height * 0.12f)) }
        composeRule.waitForIdle()
        menuItems().assertCountEquals(1)
        touchMenuItem(TILE_OPTIONS_DELETE_TAG)
        composeRule.onNodeWithText("Photo deleted · used in 1 find").assertIsDisplayed()
        letSnackbarTimeOut()
        assertEquals(listOf(PD_PHOTO_A.photo.id), logRepository.deletedPhotoIds)
        assertEquals(listOf(PD_PHOTO_A.photo.id), photoStore.deletedPhotoIds)
    }

    @Test
    fun `a plain tap on an album photo still opens the viewer`() {
        setScreen(photos = listOf(PD_PHOTO_A, PD_PHOTO_B), openRecords = false)
        openAlbum()
        photoTile(PD_PHOTO_A.photo.id).performTouchInput { click(Offset(width * 0.3f, height * 0.7f)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).assertExists()
        menuItems().assertCountEquals(0)
    }

    // ── ci-flake (-296) scratch probes, never merged ──
    // DecodeProbe (DecodedPhoto.kt, scratch) holds each tile's IO decode, so the placeholder-to-Image
    // swap can be put at a chosen point of a real gesture. The clock is taken off auto-advance while a
    // pointer is held, so no long-press timeout can fire except where an arm advances past it.

    private val probePoint = 0.3f to 0.7f

    private fun decodedNodes(id: String) = composeRule.onAllNodes(
        hasAnyAncestor(hasTestTag(albumPhotoTestTag(id))) and androidx.compose.ui.test.hasContentDescription("Log photo"),
    ).fetchSemanticsNodes().size

    private fun probeOpenAlbumHeld(): java.util.concurrent.CountDownLatch {
        val gate = java.util.concurrent.CountDownLatch(1)
        DecodeProbe.gate = gate
        DecodeProbe.finished = java.util.concurrent.CountDownLatch(2)
        setScreen(photos = listOf(PD_PHOTO_A, PD_PHOTO_B), openRecords = false)
        openAlbum()
        assertEquals("reachability: tile A still shows the placeholder while the gate is shut", 0, decodedNodes(PD_PHOTO_A.photo.id))
        return gate
    }

    /** Opens the gate, waits for both decodes, then runs single frames until tile A has swapped. */
    private fun probeSwapNow(gate: java.util.concurrent.CountDownLatch) {
        gate.countDown()
        check(DecodeProbe.finished!!.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "decodes did not finish" }
        var frames = 0
        while (decodedNodes(PD_PHOTO_A.photo.id) == 0 && frames < 10) { composeRule.mainClock.advanceTimeByFrame(); frames++ }
        println("PROBE swap applied after $frames frame(s)")
        assertEquals("reachability: tile A swapped to the decoded Image", 1, decodedNodes(PD_PHOTO_A.photo.id))
    }

    private fun probeReset() { DecodeProbe.gate?.countDown(); DecodeProbe.gate = null; DecodeProbe.finished = null; composeRule.mainClock.autoAdvance = true }

    private fun probeDown() = photoTile(PD_PHOTO_A.photo.id).performTouchInput { down(Offset(width * probePoint.first, height * probePoint.second)) }

    private fun probeUp() = photoTile(PD_PHOTO_A.photo.id).performTouchInput { up() }

    @Test
    fun `PROBE tap, swap between down and up`() {
        try {
            val gate = probeOpenAlbumHeld()
            composeRule.mainClock.autoAdvance = false
            probeDown()
            probeSwapNow(gate)
            probeUp()
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).assertExists()
        } finally { probeReset() }
    }

    @Test
    fun `PROBE tap, swap before down`() {
        try {
            val gate = probeOpenAlbumHeld()
            composeRule.mainClock.autoAdvance = false
            probeSwapNow(gate)
            probeDown()
            composeRule.mainClock.advanceTimeByFrame()
            probeUp()
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).assertExists()
        } finally { probeReset() }
    }

    @Test
    fun `PROBE tap, swap after up`() {
        try {
            val gate = probeOpenAlbumHeld()
            composeRule.mainClock.autoAdvance = false
            probeDown()
            composeRule.mainClock.advanceTimeByFrame()
            probeUp()
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            composeRule.mainClock.autoAdvance = false
            probeSwapNow(gate)
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).assertExists()
        } finally { probeReset() }
    }

    @Test
    fun `PROBE long-press, swap between down and the long-press timeout`() {
        try {
            val gate = probeOpenAlbumHeld()
            composeRule.mainClock.autoAdvance = false
            probeDown()
            probeSwapNow(gate)
            composeRule.mainClock.advanceTimeBy(1_000L)
            probeUp()
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            menuItems().assertCountEquals(1)
            touchMenuItem(TILE_OPTIONS_DELETE_TAG)
        } finally { probeReset() }
    }

    @Test
    fun `PROBE long-press, swap after the long-press fired but before up`() {
        try {
            val gate = probeOpenAlbumHeld()
            composeRule.mainClock.autoAdvance = false
            probeDown()
            composeRule.mainClock.advanceTimeBy(1_000L)
            probeSwapNow(gate)
            probeUp()
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            menuItems().assertCountEquals(1)
            touchMenuItem(TILE_OPTIONS_DELETE_TAG)
        } finally { probeReset() }
    }

    @Test
    fun `PROBE long-press, swap before down`() {
        try {
            val gate = probeOpenAlbumHeld()
            composeRule.mainClock.autoAdvance = false
            probeSwapNow(gate)
            probeDown()
            composeRule.mainClock.advanceTimeBy(1_000L)
            probeUp()
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            menuItems().assertCountEquals(1)
            touchMenuItem(TILE_OPTIONS_DELETE_TAG)
        } finally { probeReset() }
    }

}

private const val SNACKBAR_LONG_MILLIS = 10_000L

private val PD_TIME = CurrentTimeProvider { 1_000L }

private val PD_WAYPOINTS = listOf(
    Waypoint(id = "wp-creek", lat = 45.5, lng = -122.6, altitude = null, name = "Creek pin", note = "", createdAtEpochMillis = 1_758_100_000_000L),
    Waypoint(id = "wp-oak", lat = 45.6, lng = -122.7, altitude = null, name = "Big oak", note = "", createdAtEpochMillis = 1_758_000_000_000L),
)

private val PD_EMPTY_MAP_DATA = CartographyEntryMapData(
    trackPolylines = emptyList(),
    findMarkers = emptyList(),
    waypointMarkers = emptyList(),
    photoMarkers = emptyList(),
    offlineRegionCircles = emptyList(),
)

private val PD_STUB_MAP: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }

private object PendingDeleteNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object PendingDeleteAudible : AlertAudibility {
    override fun current(): AlertAudibilityState =
        AlertAudibilityState(ringerMode = RingerMode.NORMAL, doNotDisturbOn = false, notificationsEnabled = true)
}

/** Waypoints kept in memory; every [delete] call is recorded, in order, for the tests to read. */
private class PendingDeleteWaypointRepository(initial: List<Waypoint>) : WaypointRepository {
    private val waypoints = initial.associateByTo(LinkedHashMap()) { it.id }
    val deletedIds = mutableListOf<String>()

    override suspend fun getAll(): Result<List<Waypoint>> = Result.success(waypoints.values.toList())
    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Waypoint>> =
        Result.failure(UnsupportedOperationException("getForDay is not part of this test's path"))
    override suspend fun getById(id: String): Result<Waypoint?> = Result.success(waypoints[id])
    override suspend fun getForTrack(trackId: String): Result<List<Waypoint>> =
        Result.failure(UnsupportedOperationException("getForTrack is not part of this test's path"))
    override suspend fun detachFromTrack(trackId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("detachFromTrack is not part of this test's path"))
    override suspend fun save(waypoint: Waypoint): Result<Unit> {
        waypoints[waypoint.id] = waypoint
        return Result.success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        deletedIds += id
        waypoints.remove(id)
        return Result.success(Unit)
    }
}

/** No tracks; nothing here records one. */
private class PendingDeleteTrackRepository : TrackRepository {
    override suspend fun getAll(): Result<List<Track>> = Result.success(emptyList())
    override suspend fun getById(id: String): Result<Track?> = Result.success(null)
    override suspend fun getFullRecord(id: String): Result<List<TrackPointRecord>> = Result.success(emptyList())
    override suspend fun getForDay(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<Track>> =
        Result.success(emptyList())
    override suspend fun create(track: Track): Result<Unit> = Result.failure(UnsupportedOperationException("recording is not part of this test's path"))
    override suspend fun appendPoints(trackId: String, points: List<TrackPoint>): Result<Unit> = Result.success(Unit)
    override suspend fun end(trackId: String, endedAtEpochMillis: Long): Result<Unit> = Result.success(Unit)
    override suspend fun setOriginWaypoint(trackId: String, waypointId: String): Result<Unit> = Result.success(Unit)
    override suspend fun delete(id: String): Result<Unit> = Result.failure(UnsupportedOperationException("tracks are not deletable"))
}

private val PD_REGIONS = listOf(
    OfflineRegionSummary(id = 7L, name = "Ridge", region = Region(45.5, -122.6, 5), minZoom = 10.0, maxZoom = 15.0, tileCount = 100, sizeBytes = 1_000_000L, createdAtEpochMillis = 1_758_300_000_000L),
    OfflineRegionSummary(id = 8L, name = "Saddle", region = Region(45.3, -122.4, 5), minZoom = 10.0, maxZoom = 15.0, tileCount = 50, sizeBytes = 1_000_000L, createdAtEpochMillis = 1_758_200_000_000L),
)

/** Regions kept in memory; every [deleteRegion] call (MapLibre's tile delete and the row, in the real one) is recorded. */
private class PendingDeleteOfflineMapRepository(initial: List<OfflineRegionSummary>) : OfflineMapRepository {
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

private object PendingDeleteUnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult = LocationResult.LocationUnavailable
}

private object PendingDeleteEmptyRepository : MushroomRepository, TaxonSearchRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object PendingDeleteWeather : WeatherProvider, TripPlanningWeatherProvider, HistoricalWeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows are not part of this test's path"))
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern is not part of this test's path"))
}

private object PendingDeletePlannedTrips : PlannedTripRepository {
    override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(emptyList())
    override suspend fun save(trip: PlannedTrip): Result<Unit> = Result.failure(UnsupportedOperationException("planned trips are not part of this test's path"))
    override suspend fun delete(id: String): Result<Unit> = Result.failure(UnsupportedOperationException("planned trips are not part of this test's path"))
}

private object PendingDeleteMapPreferences : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getMapFullscreen(): Result<Boolean> = Result.success(false)
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = Result.success(Unit)
}

private object PendingDeleteUnitSystem : UnitSystemPreferenceRepository {
    override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(UnitSystem.IMPERIAL)
    override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
}

private object PendingDeleteTheme : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}

private val PD_FIND_A = MushroomLogEntry.draft(id = "find-a", location = null, date = LocalDate.of(2026, 9, 20)).copy(isDraft = false)
private val PD_FIND_B = MushroomLogEntry.draft(id = "find-b", location = null, date = LocalDate.of(2026, 9, 21)).copy(isDraft = false)

/**
 * Finds kept in memory, and (J4b L3) gallery photos; every [delete] and [deletePhotoFromGallery]
 * call is recorded, in order.
 */
private class PendingDeleteLogRepository(initial: List<MushroomLogEntry>, photos: List<GalleryPhoto> = emptyList()) : MushroomLogRepository {
    private val entries = initial.associateByTo(LinkedHashMap()) { it.id }
    private val galleryPhotos = photos.associateByTo(LinkedHashMap()) { it.photo.id }
    val deletedIds = mutableListOf<String>()
    val deletedPhotoIds = mutableListOf<String>()

    override suspend fun getAll(): Result<List<MushroomLogEntry>> = Result.success(entries.values.toList())
    override suspend fun getForDay(foundOnKey: String): Result<List<MushroomLogEntry>> =
        Result.success(entries.values.filter { it.foundOn.toString() == foundOnKey })
    override suspend fun getAllPhotos(): Result<List<GalleryPhoto>> = Result.success(galleryPhotos.values.toList())
    override suspend fun save(entry: MushroomLogEntry): Result<Unit> {
        entries[entry.id] = entry
        return Result.success(Unit)
    }
    override suspend fun commitDraft(draftId: String, committed: MushroomLogEntry): Result<Unit> {
        entries[committed.id] = committed
        if (committed.id != draftId) entries.remove(draftId)
        return Result.success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        deletedIds += id
        entries.remove(id)
        return Result.success(Unit)
    }
    override suspend fun addPhotoToGallery(photo: LogPhoto): Result<Unit> =
        Result.failure(UnsupportedOperationException("photos are not part of this test's path"))
    override suspend fun updatePhotoLocation(photoId: String, latitude: Double, longitude: Double): Result<Unit> =
        Result.failure(UnsupportedOperationException("photos are not part of this test's path"))
    override suspend fun attachPhotoToEntry(entryId: String, photoId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("photos are not part of this test's path"))
    override suspend fun detachPhotoFromEntry(entryId: String, photoId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("photos are not part of this test's path"))
    override suspend fun deletePhotoFromGallery(photoId: String): Result<Unit> {
        deletedPhotoIds += photoId
        galleryPhotos.remove(photoId)
        return Result.success(Unit)
    }
}

/** Persists nothing; every file [delete] is recorded (J4b L3: the file delete a pending photo defers). */
private class PendingDeletePhotoStore : PhotoStore {
    val deletedPhotoIds = mutableListOf<String>()
    override suspend fun persist(source: PhotoSource): Result<LogPhoto> =
        Result.failure(UnsupportedOperationException("photos are not part of this test's path"))
    override suspend fun delete(photo: LogPhoto): Result<Unit> {
        deletedPhotoIds += photo.id
        return Result.success(Unit)
    }
}

private val PD_ENTRY_A = CartographyEntry.draft(id = "entry-a", date = LocalDate.of(2026, 9, 20), updatedAtEpochMillis = 2_000L)
    .copy(isDraft = false, text = "Chanterelles along the ridge")
private val PD_ENTRY_B = CartographyEntry.draft(id = "entry-b", date = LocalDate.of(2026, 9, 14), updatedAtEpochMillis = 1_000L)
    .copy(isDraft = false, text = "Scouting Molalla")
private val PD_DRAFT_1 = CartographyEntry.draft(id = "draft-1", date = LocalDate.of(2026, 9, 21), updatedAtEpochMillis = 3_000L).copy(text = "Half a day")
private val PD_DRAFT_2 = CartographyEntry.draft(id = "draft-2", date = LocalDate.of(2026, 9, 22), updatedAtEpochMillis = 4_000L).copy(text = "Rained out")

/** Cartography entries in memory; every [delete] call is recorded, in order (J4b L2). */
private class PendingDeleteCartographyRepository(initial: List<CartographyEntry>) : CartographyEntryRepository {
    private val entries = initial.associateByTo(LinkedHashMap()) { it.id }
    val deletedIds = mutableListOf<String>()

    override suspend fun getAll(): Result<List<CartographyEntry>> =
        Result.success(entries.values.filterNot { it.isDraft }.sortedByDescending { it.updatedAtEpochMillis })
    override suspend fun getAllDrafts(): Result<List<CartographyEntry>> =
        Result.success(entries.values.filter { it.isDraft }.sortedByDescending { it.updatedAtEpochMillis })
    override suspend fun getById(id: String): Result<CartographyEntry?> = Result.success(entries[id])
    override suspend fun save(entry: CartographyEntry): Result<Unit> {
        entries[entry.id] = entry
        return Result.success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        deletedIds += id
        entries.remove(id)
        return Result.success(Unit)
    }
    override suspend fun countEntriesReferencingTrack(trackId: String): Result<Int> = Result.success(0)
    override suspend fun countEntriesReferencingWaypoint(waypointId: String): Result<Int> = Result.success(0)
    override suspend fun countEntriesReferencingOfflineRegion(offlineRegionId: Long): Result<Int> = Result.success(0)
    override suspend fun countEntriesReferencingPhoto(photoId: String): Result<Int> = Result.success(0)
    // J8: the interface gained it; nothing in this class shows an entry on the map.
    override suspend fun setShownOnMap(id: String, shown: Boolean): Result<Unit> = error("not used by these tests")
}

private object PendingDeleteNoRegionsDayIndex : OfflineRegionDayIndex {
    override suspend fun getRegionsCreatedOn(dayStartInclusiveEpochMillis: Long, dayEndExclusiveEpochMillis: Long): Result<List<OfflineRegionMetadata>> =
        Result.success(emptyList())
}

private val PD_PHOTO_A = GalleryPhoto(
    photo = LogPhoto(id = "photo-a", relativePath = "photos/none-a.jpg", createdAtEpochMillis = 1_758_300_000_000L),
    referencingEntryIds = listOf("find-x"),
)
private val PD_PHOTO_B = GalleryPhoto(
    photo = LogPhoto(id = "photo-b", relativePath = "photos/none-b.jpg", createdAtEpochMillis = 1_758_300_100_000L),
    referencingEntryIds = emptyList(),
)

/** A node whose long-click action carries [label] (J4b: the tile's "Options for ..."). */
private fun hasLongClickLabel(label: String): SemanticsMatcher =
    SemanticsMatcher("long-click label is '$label'") { it.config.getOrNull(SemanticsActions.OnLongClick)?.label == label }
