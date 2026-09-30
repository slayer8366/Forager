package com.zynergylabs.forager.app.ui.log

// SCRATCH, ci-flake (-296) only, NEVER MERGE. A copy of JournalPendingDeleteTest's harness (setScreen and its
// file-private fakes, unchanged) holding only decode-race probe tests; JournalPendingDeleteTest.kt itself is untouched.

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
class AlbumGestureSwapProbeTest {

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

    // ── Offline regions (D3) ──

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

    /**
     * Picker-fixes dispatch F5 (owner: "Remove the corner button (Recommended)", then "Remove
     * everywhere now"): the album photo has no corner trash button, so long-press Delete with Undo
     * is the only delete in the album. The corner the button used to cover is sampled by a real
     * long-press, the one region [photoPoints] above stayed off: with the button there, that touch
     * went to the button and opened no menu.
     */
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
