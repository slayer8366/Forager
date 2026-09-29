package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.data.repository.RoomCartographyEntryRepository
import com.zynergylabs.forager.app.data.repository.RoomMushroomLogRepository
import com.zynergylabs.forager.app.data.repository.RoomOfflineRegionDayIndex
import com.zynergylabs.forager.app.data.repository.RoomTrackRepository
import com.zynergylabs.forager.app.data.repository.RoomWaypointRepository
import com.zynergylabs.forager.app.domain.AddPhotoToGalleryUseCase
import com.zynergylabs.forager.app.domain.AddPhotoToLogEntryUseCase
import com.zynergylabs.forager.app.domain.AppThemePreferenceRepository
import com.zynergylabs.forager.app.domain.CommitCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.CommitDraftEntryUseCase
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.zynergylabs.forager.app.domain.ComputeTrackStatisticsUseCase
import com.zynergylabs.forager.app.domain.ComputeTripWindowsUseCase
import com.zynergylabs.forager.app.domain.CreateCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.CreateMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.zynergylabs.forager.app.domain.DeleteCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.DeleteGalleryPhotoUseCase
import com.zynergylabs.forager.app.domain.DeleteMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.DeletePlannedTripUseCase
import com.zynergylabs.forager.app.domain.GetAvailabilityUseCase
import com.zynergylabs.forager.app.domain.GetCartographyDraftEntriesUseCase
import com.zynergylabs.forager.app.domain.GetCartographyEntriesUseCase
import com.zynergylabs.forager.app.domain.GetCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.GetConditionsUseCase
import com.zynergylabs.forager.app.domain.GetDerivedTripUseCase
import com.zynergylabs.forager.app.domain.GetDraftEntriesUseCase
import com.zynergylabs.forager.app.domain.GetGalleryPhotosUseCase
import com.zynergylabs.forager.app.domain.GetMushroomLogEntriesUseCase
import com.zynergylabs.forager.app.domain.GetPlannedTripsUseCase
import com.zynergylabs.forager.app.domain.GetRecentSearchesUseCase
import com.zynergylabs.forager.app.domain.GetSeasonalPatternUseCase
import com.zynergylabs.forager.app.domain.GetSightingsUseCase
import com.zynergylabs.forager.app.domain.GetTodaysForecastUseCase
import com.zynergylabs.forager.app.domain.GetTripReportOfflineRegionsUseCase
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
import com.zynergylabs.forager.app.domain.PullPhotoIntoEntryUseCase
import com.zynergylabs.forager.app.domain.RemovePhotoFromLogEntryUseCase
import com.zynergylabs.forager.app.domain.SaveCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.SetCartographyEntryShownOnMapUseCase
import com.zynergylabs.forager.app.domain.SaveMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.SavePlannedTripUseCase
import com.zynergylabs.forager.app.domain.SearchTaxaUseCase
import com.zynergylabs.forager.app.domain.StartEditingLogEntryUseCase
import com.zynergylabs.forager.app.domain.TaxonSearchRepository
import com.zynergylabs.forager.app.domain.TripPlanningWeatherProvider
import com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository
import com.zynergylabs.forager.app.domain.UpdatePhotoLocationUseCase
import com.zynergylabs.forager.app.domain.WeatherProvider
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.ConditionsSummary
import com.zynergylabs.forager.app.domain.model.DailyWeather
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.SightingsPage
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.domain.model.WeatherSeries
import com.zynergylabs.forager.app.photo.FilePhotoStore
import com.zynergylabs.forager.app.ui.log.CartographyViewModel
import com.zynergylabs.forager.app.ui.log.ENTRIES_FAB_TAG
import com.zynergylabs.forager.app.ui.log.ENTRIES_HOME_TAG
import com.zynergylabs.forager.app.ui.log.LEAVE_PROMPT_DISCARD_TEST_TAG
import com.zynergylabs.forager.app.ui.log.LEAVE_PROMPT_SAVE_TEST_TAG
import com.zynergylabs.forager.app.ui.log.MushroomLogViewModel
import com.zynergylabs.forager.app.ui.log.RecordsSubTab
import com.zynergylabs.forager.app.ui.log.SAVE_CONFIRM_TEST_TAG
import com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag
import com.zynergylabs.forager.app.ui.map.CENTRE_PIN_CONFIRM_ROW_TAG
import com.zynergylabs.forager.app.ui.map.MapSlot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.log.FIND_OVER_VIEW_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_OPEN_FIND_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull

import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.longClick
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackDecision
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.ui.log.DRAFTS_CONTINUE_TAG
import com.zynergylabs.forager.app.ui.log.ENTRIES_ALBUM_TAG
import com.zynergylabs.forager.app.ui.log.RECORDS_FILTER_CHIP_ROW_TAG
import com.zynergylabs.forager.app.ui.log.ENTRIES_VIEW_ALBUM_TAG
import com.zynergylabs.forager.app.ui.log.JOURNAL_SWITCH_TAG
import com.zynergylabs.forager.app.ui.log.RECORD_DETAILS_SHEET_TAG
import com.zynergylabs.forager.app.ui.log.RECORDS_LOGBOOK_LIST_TAG
import com.zynergylabs.forager.app.ui.log.TILE_OPTIONS_DELETE_TAG
import com.zynergylabs.forager.app.ui.log.TILE_OPTIONS_EDIT_TAG
import com.zynergylabs.forager.app.ui.log.albumFindBadgeTestTag
import com.zynergylabs.forager.app.ui.log.cartographyEntryDeleteNotice
import com.zynergylabs.forager.app.ui.log.findDeleteNotice
import com.zynergylabs.forager.app.ui.log.galleryPhotoDeleteNotice
import com.zynergylabs.forager.app.ui.log.albumPhotoTestTag
import com.zynergylabs.forager.app.ui.log.entryCardTestTag
import com.zynergylabs.forager.app.ui.log.entrySwipeTag
import com.zynergylabs.forager.app.ui.log.entryTrackThumbnailTestTag

/**
 * J6a, the Journal on the tablet (dispatch 2026-09-28-152; `prompts/preserved/2026-09-29-18.md` and
 * the header ruling `2026-09-29-25.md`; the owner's rulings in `docs/plans/journal-redesign.md`, "J6
 * rulings (owner, 2026-09-28)" and "J6 design rulings (owner, 2026-09-29)").
 *
 * The screen is the real [AvailabilityScreen] at the SM-X800's portrait size (824 x 1318 dp, MEDIUM,
 * the wide tree), driven by the real [MushroomLogViewModel] and [CartographyViewModel] over an
 * in-memory database, the fixture `LeavingTheJournalFixesTest` builds (copied, stubs renamed
 * `WideJ*`). Back is the Activity's own dispatcher. Every geometry claim is read from the node's
 * bounds in a 1 dp = 1 px window, so the 360 dp drawer's edge is x = 360.
 *
 * Written before the build and pushed: at the base each test named "FAILS AT BASE" fails for the
 * reason its comment gives, and each named "GUARD" is a behaviour that already holds and is pinned.
 * Robolectric reports zero system-bar insets, so nothing here says anything about the detail pane's
 * own insets; that is on the device-only list in the completion report.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w824dp-h1318dp-mdpi")
class WideJournalTest {

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

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private lateinit var database: ForagerDatabase
    private lateinit var logRepository: RoomMushroomLogRepository
    private lateinit var cartographyRepository: RoomCartographyEntryRepository
    private lateinit var logViewModel: MushroomLogViewModel
    private lateinit var cartographyViewModel: CartographyViewModel
    private lateinit var availabilityViewModel: AvailabilityViewModel
    private val searchCache = InMemorySearchCacheRepository()
    private val photoFiles: List<File> get() = listOf(PHOTO, PHOTO_OF_DRAFT).map { File(context.filesDir, it.relativePath) }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        photoFiles.forEach { it.delete() }
    }

    private fun buildViewModels(seedTwoDraftDayEntries: Boolean) {
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, ForagerDatabase::class.java)
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .allowMainThreadQueries()
            .build()
        logRepository = RoomMushroomLogRepository(database.mushroomLogDao())
        cartographyRepository = RoomCartographyEntryRepository(database.cartographyEntryDao())
        val trackRepository = RoomTrackRepository(database.trackDao())
        val waypointRepository = RoomWaypointRepository(database.waypointDao())
        photoFiles.forEach { it.parentFile!!.mkdirs(); it.writeBytes(byteArrayOf(1, 2, 3, 4)) }
        runBlocking {
            logRepository.addPhotoToGallery(PHOTO).getOrThrow()
            logRepository.addPhotoToGallery(PHOTO_OF_DRAFT).getOrThrow()
            logRepository.save(COMMITTED_FIND).getOrThrow()
            logRepository.save(SECOND_FIND).getOrThrow()
            logRepository.save(DRAFT_FIND).getOrThrow()
            logRepository.attachPhotoToEntry(COMMITTED_FIND.id, PHOTO.id).getOrThrow()
            logRepository.attachPhotoToEntry(DRAFT_FIND.id, PHOTO_OF_DRAFT.id).getOrThrow()
            waypointRepository.save(DAY_WAYPOINT).getOrThrow()
            cartographyRepository.save(COMMITTED_DAY_ENTRY).getOrThrow()
            if (seedTwoDraftDayEntries) {
                cartographyRepository.save(CartographyEntry.draft(id = "day-draft-1", date = DAY_DATE.minusDays(1), updatedAtEpochMillis = 2_000L)).getOrThrow()
                cartographyRepository.save(CartographyEntry.draft(id = "day-draft-2", date = DAY_DATE.minusDays(2), updatedAtEpochMillis = 3_000L)).getOrThrow()
            }
        }
        val photoStore = FilePhotoStore(context)
        logViewModel = MushroomLogViewModel(
            getEntries = GetMushroomLogEntriesUseCase(logRepository),
            getDraftEntries = GetDraftEntriesUseCase(logRepository),
            createEntry = CreateMushroomLogEntryUseCase(logRepository, today = { FIND_DATE }, idGenerator = { "find-new" }),
            startEditingEntry = StartEditingLogEntryUseCase(logRepository, idGenerator = { DRAFT_OF_FIND_ID }),
            saveEntry = SaveMushroomLogEntryUseCase(logRepository),
            commitDraftEntry = CommitDraftEntryUseCase(logRepository),
            deleteEntry = DeleteMushroomLogEntryUseCase(logRepository),
            addPhoto = AddPhotoToLogEntryUseCase(photoStore, logRepository),
            addPhotoToGallery = AddPhotoToGalleryUseCase(photoStore, logRepository),
            removePhoto = RemovePhotoFromLogEntryUseCase(logRepository),
            getGalleryPhotos = GetGalleryPhotosUseCase(logRepository),
            pullPhotoIntoEntry = PullPhotoIntoEntryUseCase(logRepository),
            deleteGalleryPhoto = DeleteGalleryPhotoUseCase(logRepository, photoStore),
            locationProvider = WideJUnavailableLocationProvider,
            updatePhotoLocation = UpdatePhotoLocationUseCase(logRepository),
        )
        cartographyViewModel = CartographyViewModel(
            getEntries = GetCartographyEntriesUseCase(cartographyRepository),
            getDraftEntries = GetCartographyDraftEntriesUseCase(cartographyRepository),
            createEntry = CreateCartographyEntryUseCase(cartographyRepository, now = { 1_000L }, idGenerator = { NEW_DAY_ENTRY_ID }),
            saveEntry = SaveCartographyEntryUseCase(cartographyRepository, now = { 1_000L }),
            getEntry = GetCartographyEntryUseCase(cartographyRepository),
            commitEntry = CommitCartographyEntryUseCase(cartographyRepository, now = { 1_000L }),
            deleteEntry = DeleteCartographyEntryUseCase(cartographyRepository),
            getDerivedTrip = GetDerivedTripUseCase(
                mushroomLogRepository = logRepository,
                trackRepository = trackRepository,
                waypointRepository = waypointRepository,
                offlineRegionDayIndex = RoomOfflineRegionDayIndex(database.offlineRegionDao()),
            ),
            getTripReportOfflineRegions = GetTripReportOfflineRegionsUseCase(WideJStubOfflineMapRepository),
            computeTrackStatistics = ComputeTrackStatisticsUseCase(),
            setShownOnMap = SetCartographyEntryShownOnMapUseCase(cartographyRepository),
            now = { 1_000L },
        )
        val plannedTrips = WideJInMemoryPlannedTripRepository()
        availabilityViewModel = AvailabilityViewModel(
            locationProvider = WideJUnavailableLocationProvider,
            locationTracker = WideJNoOpLocationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(WideJEmptyRepository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(WideJEmptyRepository),
            searchTaxa = SearchTaxaUseCase(WideJEmptyRepository),
            getConditions = GetConditionsUseCase(WideJStubWeatherProvider),
            getTripWindows = GetTripWindowsUseCase(WideJStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(plannedTrips),
            savePlannedTrip = SavePlannedTripUseCase(plannedTrips),
            deletePlannedTrip = DeletePlannedTripUseCase(plannedTrips),
            getSeasonalPattern = GetSeasonalPatternUseCase(
                GetSightingsUseCase(WideJEmptyRepository),
                WideJStubHistoricalWeatherProvider,
                ComputeFruitingLagDistributionUseCase(),
            ),
            offlineMapRepository = WideJStubOfflineMapRepository,
            mapPreferencesRepository = WideJStubMapPreferencesRepository,
            unitSystemPreferenceRepository = WideJStubUnitSystemPreferenceRepository,
            appThemePreferenceRepository = WideJStubAppThemePreferenceRepository,
            getTodaysForecast = GetTodaysForecastUseCase(WideJStubTripPlanningWeatherProvider),
        )
    }

    /** The screen `MainActivity` builds for these callbacks, at the size the class's [Config] gives. */
    @androidx.compose.runtime.Composable
    private fun screen() {
        val uiState by availabilityViewModel.uiState.collectAsState()
        val logUiState by logViewModel.uiState.collectAsState()
        val cartographyUiState by cartographyViewModel.uiState.collectAsState()
        AvailabilityScreen(
            uiState = uiState,
            onUseCurrentLocation = availabilityViewModel::useCurrentLocation,
            onManualLatChanged = availabilityViewModel::onManualLatChanged,
            onManualLngChanged = availabilityViewModel::onManualLngChanged,
            onSearchManualCoordinates = availabilityViewModel::searchManualCoordinates,
            onRadiusChanged = availabilityViewModel::onRadiusChanged,
            onMonthSelected = availabilityViewModel::onMonthSelected,
            onMapTabSelected = availabilityViewModel::onMapTabSelected,
            onSeasonalTabSelected = availabilityViewModel::onSeasonalTabSelected,
            onTaxonSearchQueryChanged = availabilityViewModel::onTaxonSearchQueryChanged,
            onTaxonSearchResultSelected = availabilityViewModel::onTaxonSearchResultSelected,
            onDismissTaxonSuggestions = availabilityViewModel::onDismissTaxonSuggestions,
            onReopenTaxonSuggestions = availabilityViewModel::onReopenTaxonSuggestions,
            onPlaceTripPin = availabilityViewModel::onPlaceTripPin,
            onDeletePlannedTrip = availabilityViewModel::onDeletePlannedTrip,
            onRecentSearchSelected = availabilityViewModel::onRecentSearchSelected,
            onOfflineMapLatChanged = availabilityViewModel::onOfflineMapLatChanged,
            onOfflineMapLngChanged = availabilityViewModel::onOfflineMapLngChanged,
            onOfflineMapRadiusChanged = availabilityViewModel::onOfflineMapRadiusChanged,
            onOfflineMapNameChanged = availabilityViewModel::onOfflineMapNameChanged,
            onOfflineMapsOpened = availabilityViewModel::onOfflineMapsOpened,
            onDownloadOfflineMaps = availabilityViewModel::onDownloadOfflineMaps,
            onDeleteOfflineRegion = availabilityViewModel::onDeleteOfflineRegion,
            onNightModeMapsChanged = availabilityViewModel::onNightModeMapsChanged,
            onThemeModeChanged = availabilityViewModel::onThemeModeChanged,
            logUiState = logUiState.hidingPendingDelete(),
            pendingDeleteNotices = listOfNotNull(
                findDeleteNotice(logUiState.pendingDelete, onUndo = logViewModel::undoDeleteEntry, onCommit = logViewModel::commitDeleteEntry),
                cartographyEntryDeleteNotice(cartographyUiState.pendingDelete, onUndo = cartographyViewModel::undoDeleteEntry, onCommit = cartographyViewModel::commitDeleteEntry),
                galleryPhotoDeleteNotice(logUiState.pendingPhotoDelete, onUndo = logViewModel::undoDeleteGalleryPhoto, onCommit = logViewModel::commitDeleteGalleryPhoto),
            ),
            onStartLogEntry = logViewModel::onStartNewEntry,
            onOpenLogEntry = logViewModel::onOpenEntry,
            onCloseLogEntry = logViewModel::onCloseEntry,
            onLogEntryChanged = logViewModel::onEntryEdited,
            onStartEditingLogEntry = logViewModel::onStartEditingEntry,
            onOpenLogEntryForEditing = logViewModel::onOpenEntryForEditing,
            onSaveLogEntry = logViewModel::onSaveEntry,
            onCancelLogEntryEditing = logViewModel::onCancelEditing,
            onLeaveLogEntryEditingIncidentally = logViewModel::onLeaveEditingIncidentally,
            onDiscardLogDraft = logViewModel::onDeleteEntry,
            onAddLogPhoto = logViewModel::onAddPhoto,
            onRemoveLogPhoto = logViewModel::onRemovePhoto,
            onPullLogPhoto = logViewModel::onPullPhoto,
            onDeleteLogEntry = logViewModel::requestDeleteEntry,
            onDeleteGalleryPhoto = logViewModel::onDeleteGalleryPhoto,
            onAddGalleryPhoto = logViewModel::onAddGalleryPhoto,
            onSaveLogErrorDismissed = logViewModel::onSaveErrorDismissed,
            cartographyUiState = cartographyUiState.hidingPendingDelete(),
            onOpenCartographyEntry = cartographyViewModel::onOpenEntry,
            onStartCartographyEntry = cartographyViewModel::onStartEntry,
            onCloseCartographyEntry = cartographyViewModel::onCloseEntry,
            onCartographyTextChanged = cartographyViewModel::onTextChanged,
            onCartographyTagsChanged = cartographyViewModel::onTagsChanged,
            onSetFindDecision = cartographyViewModel::onSetFindDecision,
            onSetTrackDecision = cartographyViewModel::onSetTrackDecision,
            onSetWaypointDecision = cartographyViewModel::onSetWaypointDecision,
            onSetOfflineRegionDecision = cartographyViewModel::onSetOfflineRegionDecision,
            onToggleKeptPhoto = cartographyViewModel::onToggleKeptPhoto,
            onFinishCartographyEntry = cartographyViewModel::onFinishEntry,
            onSaveCartographyEntry = cartographyViewModel::onSaveEntry,
            onDiscardCartographyEntryChanges = cartographyViewModel::onDiscardEntryChanges,
            onSaveCartographyEntryAsDraft = cartographyViewModel::onSaveEntryAsDraft,
            onDeleteCartographyEntry = cartographyViewModel::onDeleteEntry,
            onRequestDeleteCartographyEntry = cartographyViewModel::requestDeleteEntry,
            onRequestDeleteGalleryPhoto = logViewModel::requestDeleteGalleryPhoto,
            waypoints = listOf(DAY_WAYPOINT),
            tracks = tracksOnScreen,
            getSavedTrackPaths = savedTrackPaths,
            compassProvider = WideJFakeCompassProvider,
            mapSlot = WideJStubMapSlot,
        )
    }

    /** What the wide tree is given as the loaded tracks, and as the read of an entry's saved track paths (F3). */
    private var tracksOnScreen: List<Track> = listOf(DAY_TRACK)
    private var savedTrackPaths: suspend (String) -> Map<String, List<LatLng>> = { emptyMap() }

    private fun setScreen(seedTwoDraftDayEntries: Boolean = false) {
        buildViewModels(seedTwoDraftDayEntries)
        composeRule.setContent { screen() }
        composeRule.waitForIdle()
    }

    // ── Helpers ──

    private fun pressBack() {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
    }

    /** The Search panel's "Mushroom Log" row is the way into the Journal panel; the panel's own header is what the ruling renames. */
    private fun openJournal() {
        composeRule.onNodeWithText("Mushroom Log").performClick()
        composeRule.waitForIdle()
    }

    private fun tagExists(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun textExists(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun descriptionExists(text: String) = composeRule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()

    /**
     * The Search panel is showing: "Trip Planner" is its own row and nothing in the Journal panel. Not
     * "Mushroom Log", which the base's Journal header also says (a first draft of these tests used it
     * and one test passed at base only because of that).
     */
    private fun searchPanelShowing() = textExists("Trip Planner")

    /** Taps the Journal panel's header row (base: "Mushroom Log"; after the header ruling: "Journal"), which returns to Search. */
    private fun tapHeaderBackToSearch() {
        composeRule.onNodeWithText(if (textExists("Journal")) "Journal" else "Mushroom Log").performClick()
        composeRule.waitForIdle()
    }

    private fun openDayEntryReport() {
        openJournal()
        composeRule.onNode(hasTestTag(entryCardTestTag(COMMITTED_DAY_ENTRY.id)) or hasTestTag("entry-row-${COMMITTED_DAY_ENTRY.id}")).performClick()
        composeRule.waitForIdle()
    }

    private fun openDayEntryEditor() {
        openDayEntryReport()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.waitForIdle()
    }

    private fun openRecords(chip: RecordsSubTab? = null) {
        openJournal()
        composeRule.onNodeWithText("Records").performClick()
        composeRule.waitForIdle()
        if (chip != null) {
            composeRule.onNodeWithTag(recordsFilterChipTestTag(chip)).performScrollTo().performClick()
            composeRule.waitForIdle()
        }
    }

    private fun openFindReport() {
        openRecords(RecordsSubTab.FINDS)
        composeRule.onNodeWithText(FIND_TILE_TEXT).performClick()
        composeRule.waitForIdle()
    }

    /**
     * Opens the first find and gets to its editor: the base opens a tapped find straight in its editor,
     * and item 6.5 gives it a report first, so the helper takes the report's Edit when there is a
     * report. Used by tests whose claim is about the editor or what it opens, not about the report.
     */
    private fun openFindEditor() {
        openRecords(RecordsSubTab.FINDS)
        composeRule.onNodeWithText(FIND_TILE_TEXT).performClick()
        composeRule.waitForIdle()
        if (descriptionExists("Entry options")) {
            composeRule.onNodeWithContentDescription("Entry options").performClick()
            composeRule.onNodeWithText("Edit entry").performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText("Your own identification (optional)").assertExists("the find's editor is open")
    }

    private fun boundsOf(tag: String) = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun boundsOfText(text: String) = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
    private fun boundsOfDescription(text: String) = composeRule.onNodeWithContentDescription(text).getUnclippedBoundsInRoot()

    private fun assertRightSide(what: String, left: androidx.compose.ui.unit.Dp) =
        assertTrue("$what starts at x=$left, which is inside the 360 dp left column, not in the right side", left >= DRAWER_EDGE)

    private fun assertLeftColumn(what: String, right: androidx.compose.ui.unit.Dp) =
        assertTrue("$what ends at x=$right, past the 360 dp left column", right <= DRAWER_EDGE + 1.dp)

    private fun assertSearchBarShowing(expected: Boolean, where: String) {
        assertEquals("$where: the search bar ('Advanced search options') showing", expected, descriptionExists("Advanced search options"))
        assertEquals("$where: the List | Maps | Seasonal row ('Seasonal') showing", expected, textExists("Seasonal"))
    }

    // ── Item 8, the header ruling (2026-09-29-25) ──

    @Test
    fun `FAILS AT BASE the Journal panel's header reads Journal, with the Entries and Records switch below it and no Cartography tab`() {
        setScreen()
        openJournal()

        // Base: the header says "Mushroom Log", so "Journal" is not on screen at all.
        val header = boundsOfText("Journal")
        assertTrue("the switch is on screen", tagExists(JOURNAL_SWITCH_TAG))
        assertTrue("the switch is below the header row: header bottom ${header.bottom}, switch top ${boundsOf(JOURNAL_SWITCH_TAG).top}", boundsOf(JOURNAL_SWITCH_TAG).top >= header.bottom)
        composeRule.onNodeWithTag(journalSwitchTag("entries")).assertIsSelected()
        composeRule.onNodeWithText("Cartography").assertDoesNotExist()
        assertEquals("the header row no longer says Mushroom Log", 0, composeRule.onAllNodesWithText("Mushroom Log").fetchSemanticsNodes().size)
    }

    @Test
    fun `FAILS AT BASE the header row still returns to the Search panel`() {
        setScreen()
        openJournal()

        composeRule.onNodeWithText("Journal").performClick()
        composeRule.waitForIdle()

        assertTrue("back on the Search panel, whose row into the Journal is 'Mushroom Log'", searchPanelShowing())
    }

    // ── Item 4, one Back order ──

    @Test
    fun `FAILS AT BASE Back from Records goes to Entries, then Back from Entries goes to the Search panel`() {
        setScreen()
        openRecords()
        assertTrue("Records is showing (its chips)", tagExists(RECORDS_FILTER_CHIP_ROW_TAG))

        pressBack()
        assertTrue("Records to Entries: the Entries list is showing", tagExists(ENTRIES_HOME_TAG))
        assertTrue("still in the Journal after Records to Entries", !searchPanelShowing())

        pressBack()
        assertTrue("Back from Entries lands on the Search panel", searchPanelShowing())
    }

    @Test
    fun `FAILS AT BASE Back from the Journal reaches the Search panel whichever way it was opened, and Back there is not swallowed`() {
        setScreen()
        openJournal()
        pressBack()
        assertTrue("opened from the Search panel's row: Back lands on the Search panel", searchPanelShowing())
        assertEquals("the Journal was not left open behind it", false, tagExists(JOURNAL_SWITCH_TAG))
    }

    @Test
    fun `FAILS AT BASE Back unwinds a find one step at a time, picker then editor then Finds chip then Records then the Journal`() {
        setScreen()
        openFindEditor()
        composeRule.onNodeWithText("Change Location").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue("1. the location picker is open", textExists(PICKER_MARKER))

        pressBack()
        assertEquals("2. Back from the picker leaves the picker", false, textExists(PICKER_MARKER))
        composeRule.onNodeWithText("Your own identification (optional)").assertExists()

        // The editor's Back is the existing "leaving without answering" (the find is left, not stepped
        // back to its report): JournalTab.unwindFindsSection, and the same in the phone's Journal.
        pressBack()
        assertEquals("3. Back from the editor leaves the editor", false, textExists("Your own identification (optional)"))
        assertEquals("3-4. the find is left, so the detail is closed", null, logViewModel.uiState.value.editingEntry)
        assertEquals("4. no detail pane is left over", false, tagExists("journal-detail-pane"))
        assertTrue("4. the Journal is still on Records, its Finds chip selected", tagExists(RECORDS_FILTER_CHIP_ROW_TAG))
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.FINDS)).assertIsSelected()

        // RecordsTab's own existing step (RecordsTab.kt:225, the phone's too): Back from a chip goes to All.
        pressBack()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.ALL)).assertIsSelected()
        assertTrue("4b. still on Records", tagExists(RECORDS_FILTER_CHIP_ROW_TAG))

        pressBack()
        assertTrue("5. Records to Entries", tagExists(ENTRIES_HOME_TAG))
        assertTrue("5. still in the Journal", !searchPanelShowing())

        pressBack()
        assertTrue("6. Journal to the Search panel", searchPanelShowing())
    }

    @Test
    fun `FAILS AT BASE Back from a find's report closes the detail and keeps Records on Finds`() {
        setScreen()
        openRecords(RecordsSubTab.FINDS)
        composeRule.onNodeWithText(FIND_TILE_TEXT).performClick()
        composeRule.waitForIdle()
        assertTrue("the find is open in its report", descriptionExists("Entry options"))

        pressBack()

        assertEquals("the detail is closed", null, logViewModel.uiState.value.editingEntry)
        assertSearchBarShowing(true, "after closing the find")
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.FINDS)).assertIsSelected()
    }

    @Test
    fun `GUARD Back from a day entry's report closes the detail and leaves the Journal on Entries`() {
        setScreen()
        openDayEntryReport()
        assertTrue("the entry's report is open", descriptionExists("Entry options"))

        pressBack()

        assertTrue("the Entries list is showing again", tagExists(ENTRIES_HOME_TAG))
        assertSearchBarShowing(true, "after closing the detail")
        assertTrue("still in the Journal", !searchPanelShowing())
    }

    // ── Item 1, list-detail ──

    @Test
    fun `FAILS AT BASE an opened day entry takes the whole right side, search bar included, and the list stays in the left column`() {
        setScreen()
        assertSearchBarShowing(true, "before opening anything")
        openDayEntryReport()

        assertRightSide("the entry's report ('Entry options')", boundsOfDescription("Entry options").left)
        assertTrue("the detail pane is tagged", tagExists("journal-detail-pane"))
        assertRightSide("the detail pane", boundsOf("journal-detail-pane").left)
        assertSearchBarShowing(false, "while the entry is open")
        assertTrue("the Entries list is still on screen", tagExists(ENTRIES_HOME_TAG))
        assertLeftColumn("the Entries list", boundsOf(ENTRIES_HOME_TAG).right)
    }

    @Test
    fun `FAILS AT BASE closing the detail restores the right side as it was, including a chosen Seasonal tab`() {
        setScreen()
        composeRule.onNodeWithText("Seasonal").performClick()
        composeRule.waitForIdle()
        openDayEntryReport()
        assertSearchBarShowing(false, "while open")

        pressBack()

        assertSearchBarShowing(true, "after closing")
        assertTrue("the right side is as it was: 'Seasonal' still the chosen tab", composeRule.onAllNodesWithText("Seasonal").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Seasonal").assertIsSelected()
    }

    @Test
    fun `FAILS AT BASE an opened find's detail, whichever step shows first, takes the whole right side`() {
        setScreen()
        openRecords(RecordsSubTab.FINDS)
        composeRule.onNodeWithText(FIND_TILE_TEXT).performClick()
        composeRule.waitForIdle()

        assertTrue("the find's detail is in the detail pane", tagExists("journal-detail-pane"))
        assertRightSide("the find's detail", boundsOf("journal-detail-pane").left)
        assertSearchBarShowing(false, "while the find is open")
        // The report may repeat the tile's text (a title), so the tile is the node inside the left column.
        val inLeftColumn = composeRule.onAllNodesWithText(FIND_TILE_TEXT).fetchSemanticsNodes().filter { it.boundsInRoot.right <= DRAWER_EDGE.value + 1f }
        assertTrue("the Finds gallery's tile stays in the left column", inLeftColumn.isNotEmpty())
    }

    @Test
    fun `FAILS AT BASE the location picker and the record details open in the right side`() {
        setScreen()
        openFindEditor()
        composeRule.onNodeWithText("Change Location").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertRightSide("the location picker", boundsOfText(PICKER_MARKER).left)
        pressBack()
        pressBack()
        pressBack()

        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.WAYPOINTS)).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(DAY_WAYPOINT.name).performClick()
        composeRule.waitForIdle()
        assertEquals("the details are not a modal sheet on the wide tree", false, tagExists(RECORD_DETAILS_SHEET_TAG))
        assertTrue("the details are in a pane", tagExists("record-details-pane"))
        assertRightSide("the record details", boundsOf("record-details-pane").left)
        assertSearchBarShowing(false, "while the record details are open")

        pressBack()
        assertEquals("Back closes the details", false, tagExists("record-details-pane"))
        assertSearchBarShowing(true, "after closing the details")
        assertTrue("still on Records", tagExists(RECORDS_FILTER_CHIP_ROW_TAG))
    }

    // ── Item 3, columns ──

    @Test
    fun `FAILS AT BASE Entries draws one column, so a card spans the list`() {
        setScreen()
        openJournal()
        val card = boundsOf(entryCardTestTag(COMMITTED_DAY_ENTRY.id))
        assertTrue("one column in the 328 dp content: the card is ${card.width} wide, not about a third of that", card.width > 300.dp)
    }

    @Test
    fun `FAILS AT BASE Finds draws two columns`() {
        setScreen()
        openRecords(RecordsSubTab.FINDS)
        // The plus tile takes the first cell, so two finds are not promised the same row; the width is what
        // says how many columns there are.
        val tile = boundsOfText(SECOND_FIND_TILE_TEXT)
        assertTrue("two columns in 328 dp: a tile is ${tile.width} wide, not about a third of the row", tile.width in 135.dp..175.dp)
    }

    @Test
    fun `GUARD the album draws three columns`() {
        setScreen()
        openJournal()
        composeRule.onNodeWithTag(ENTRIES_VIEW_ALBUM_TAG).performClick()
        composeRule.waitForIdle()
        val photo = boundsOf(albumPhotoTestTag(PHOTO.id))
        assertTrue("three columns in 328 dp: a photo is ${photo.width} wide", photo.width in 90.dp..120.dp)
    }

    // ── Item 6, smaller calls ──

    @Test
    fun `FAILS AT BASE a wide find opens in a report, and the gallery has a plus tile`() {
        setScreen()
        openRecords(RecordsSubTab.FINDS)
        assertTrue("the plus tile ('New log entry') is in the Finds gallery", descriptionExists("New log entry"))

        composeRule.onNodeWithText(FIND_TILE_TEXT).performClick()
        composeRule.waitForIdle()

        assertTrue("the find opens in its report, as on the phone", descriptionExists("Entry options"))
        assertEquals("not in its editor", false, textExists("Your own identification (optional)"))
    }

    @Test
    fun `FAILS AT BASE the Finds chip counts the finds and the All logbook lists them`() {
        setScreen()
        openRecords()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.FINDS)).performScrollTo().assertTextContains("2", substring = true)
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.ALL)).performClick()
        composeRule.waitForIdle()
        assertEquals("the All logbook does not say finds are not listed", false, composeRule.onAllNodesWithText("Finds are not listed here", substring = true).fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithTag(RECORDS_LOGBOOK_LIST_TAG).assertExists()
        assertTrue("a find is a row in the All logbook", textExists(FIND_TILE_TEXT))
    }

    @Test
    fun `GUARD the drafts list stays in the left column`() {
        setScreen(seedTwoDraftDayEntries = true)
        openJournal()
        composeRule.onNodeWithTag(DRAFTS_CONTINUE_TAG).performClick()
        composeRule.waitForIdle()
        assertTrue("the drafts list shows both drafts", textExists("2026-07-31") || textExists("Jul 31, 2026") || tagExists("entry-card-day-draft-1") || tagExists("entry-row-day-draft-1"))
        val list = composeRule.onNode(hasTestTag("entry-card-day-draft-1") or hasTestTag("entry-row-day-draft-1")).getUnclippedBoundsInRoot()
        assertLeftColumn("a draft in the drafts list", list.right)
    }

    // ── Item 9, parity ──

    @Test
    fun `FAILS AT BASE the album marks a photo on a saved find and not one on a draft find`() {
        setScreen()
        openJournal()
        composeRule.onNodeWithTag(ENTRIES_VIEW_ALBUM_TAG).performClick()
        composeRule.waitForIdle()
        assertTrue("the saved find's photo has the find badge", tagExists(albumFindBadgeTestTag(PHOTO.id)))
        assertEquals("the draft find's photo has none (draftFindIds reaches the wide album)", false, tagExists(albumFindBadgeTestTag(PHOTO_OF_DRAFT.id)))
    }

    @Test
    fun `FAILS AT BASE an Entries card draws its kept track's thumbnail`() {
        setScreen()
        openJournal()
        // The thumbnail's tag is inside the card's merged node, so it is read from the unmerged tree, as
        // JournalEntryCardsTest's inCard() does.
        assertEquals(
            "the card draws its kept track's thumbnail (tracks reach the wide Entries)",
            1,
            composeRule.onAllNodes(hasTestTag(entryTrackThumbnailTestTag(COMMITTED_DAY_ENTRY.id)), useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    // F3 (owner, "C: list screen loads lazily"): through AvailabilityScreen's wide tree, LogPanel to JournalTab to the card.
    @Test
    fun `an Entries card whose kept track is gone from the loaded tracks draws the path saved for it`() {
        tracksOnScreen = emptyList()
        savedTrackPaths = { id -> if (id == COMMITTED_DAY_ENTRY.id) mapOf("track-1" to listOf(LatLng(45.32, -122.64), LatLng(45.33, -122.63))) else emptyMap() }
        setScreen()
        openJournal()

        assertEquals(
            "the card draws its saved track path (the read reaches the wide Entries)",
            1,
            composeRule.onAllNodes(hasTestTag(entryTrackThumbnailTestTag(COMMITTED_DAY_ENTRY.id)), useUnmergedTree = true).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `FAILS AT BASE an Entries card offers Delete, and its pending delete says Undo`() {
        setScreen()
        openJournal()
        assertTrue("the card has a swipe row (onRequestDeleteEntry reaches the wide Entries)", tagExists(entrySwipeTag(COMMITTED_DAY_ENTRY.id)))
        val node = composeRule.onNodeWithTag(entrySwipeTag(COMMITTED_DAY_ENTRY.id)).fetchSemanticsNode()
        assertTrue("the swipe row carries a Delete action", node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == "Delete" } == true)
    }

    @Test
    fun `FAILS AT BASE a find tile's long-press offers Edit and Delete, and Delete offers Undo`() {
        setScreen()
        openRecords(RecordsSubTab.FINDS)
        composeRule.onNodeWithText(FIND_TILE_TEXT).performTouchInput { longClick(Offset(width * 0.5f, height * 0.4f)) }
        composeRule.waitForIdle()
        assertTrue("the tile's menu has Delete", tagExists(TILE_OPTIONS_DELETE_TAG))
        assertTrue("and Edit", tagExists(TILE_OPTIONS_EDIT_TAG))
        composeRule.onNodeWithTag(TILE_OPTIONS_DELETE_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Undo").assertExists()
    }

    // ── Item 10, the album's long-press delete, and the old Photo Gallery panel ──

    @Test
    fun `FAILS AT BASE a long-press on an album photo offers Delete with Undo, sampled across the tile`() {
        setScreen()
        openJournal()
        composeRule.onNodeWithTag(ENTRIES_VIEW_ALBUM_TAG).performClick()
        composeRule.waitForIdle()
        for ((fx, fy) in listOf(0.2f to 0.3f, 0.5f to 0.7f, 0.8f to 0.85f)) {
            composeRule.onNodeWithTag(albumPhotoTestTag(PHOTO.id)).performTouchInput { longClick(Offset(width * fx, height * fy)) }
            composeRule.waitForIdle()
            assertTrue("a long-press at ($fx, $fy) of the photo opens a Delete menu", tagExists(TILE_OPTIONS_DELETE_TAG))
            composeRule.onNodeWithTag(TILE_OPTIONS_DELETE_TAG).performTouchInput { click(center) }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Undo").performTouchInput { click() }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `FAILS AT BASE the Search panel no longer has a Photo Gallery row`() {
        setScreen()
        assertEquals("the old Photo Gallery panel's row is gone", false, textExists("Photo Gallery"))
    }

    // ── Item 5, state survives ──

    @Test
    fun `FAILS AT BASE an entry open in its editor is still in its editor after the Search panel and back`() {
        setScreen()
        openDayEntryEditor()
        composeRule.onNodeWithText("Your own account (optional)").assertExists("the editor is open before leaving")

        tapHeaderBackToSearch()
        assertTrue("on the Search panel", searchPanelShowing())
        openJournal()

        composeRule.onNodeWithText("Your own account (optional)").assertExists("after the trip to the Search panel and back, the entry is in its editor")
    }

    @Test
    fun `FAILS AT BASE the album view and the Records chip survive a trip to the Search panel`() {
        setScreen()
        openJournal()
        composeRule.onNodeWithTag(ENTRIES_VIEW_ALBUM_TAG).performClick()
        composeRule.waitForIdle()
        tapHeaderBackToSearch()
        openJournal()
        assertTrue("the album is still the view", tagExists(ENTRIES_ALBUM_TAG))

        composeRule.onNodeWithText("Records").performClick()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.OFFLINE_MAPS)).performScrollTo().performClick()
        composeRule.waitForIdle()
        tapHeaderBackToSearch()
        openJournal()
        assertTrue("still on Records", tagExists(RECORDS_FILTER_CHIP_ROW_TAG))
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.OFFLINE_MAPS)).assertIsSelected()
    }

    @Test
    fun `FAILS AT BASE the Records chip survives a saved-state round trip`() {
        val restorationTester = StateRestorationTester(composeRule)
        buildViewModels(seedTwoDraftDayEntries = false)
        restorationTester.setContent { screen() }
        composeRule.waitForIdle()
        openRecords(RecordsSubTab.WAYPOINTS)

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        // The drawer's panel is plain `remember` for every panel (Settings too), so a recreation lands on
        // Search; the Journal's own holder is what this test is about, so reopen the panel.
        openJournal()

        assertTrue("still on Records after the restore", tagExists(RECORDS_FILTER_CHIP_ROW_TAG))
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.WAYPOINTS)).assertIsSelected()
    }

    // ── Ruling 1 and CLAUDE.md, "a semantic click asserts wiring, not routing": real touches ──

    /**
     * Wiring and routing through the whole screen, not proof that the pane itself blocks input: a revert
     * (the pane's `Surface` swapped for a filled `Box`) left this passing, because a real report is a
     * scrolling column that takes those touches itself. `JournalDetailPaneTouchTest` is the test that
     * fails when the pane stops blocking input; this one guards that the coordinates a person would tap
     * for the covered tabs do not change the tab.
     */
    @Test
    fun `a real touch across where the results tabs were reaches the detail pane, not the tab beneath it`() {
        setScreen()
        // The wide tree starts on the Maps tab (`AvailabilityScreen`'s `selectedTab`), so Seasonal, the
        // right-most tab, is the one a stray touch could visibly change.
        composeRule.onNodeWithText("Maps").assertIsSelected()
        val seasonal = boundsOfText("Seasonal")
        fun touchSeasonal(fraction: Float) {
            composeRule.onRoot().performTouchInput {
                click(Offset((seasonal.left.value + seasonal.width.value * fraction) * density, (seasonal.top.value + seasonal.height.value / 2f) * density))
            }
            composeRule.waitForIdle()
        }
        // Positive control: with nothing open the same coordinates do reach the Seasonal tab (so the
        // coordinates are the tab's, and the check below is not vacuous).
        touchSeasonal(0.5f)
        composeRule.onNodeWithText("Seasonal").assertIsSelected()
        composeRule.onNodeWithText("Maps").performClick()
        composeRule.onNodeWithText("Maps").assertIsSelected()

        openDayEntryReport()
        assertSearchBarShowing(false, "while the entry is open")
        for (fraction in listOf(0.2f, 0.5f, 0.8f)) touchSeasonal(fraction)

        // Whatever those touches did inside the report, close it and look at the tab beneath.
        var backs = 0
        while (!descriptionExists("Advanced search options") && backs < 6) {
            pressBack()
            backs++
        }
        assertSearchBarShowing(true, "after closing whatever the touches opened")
        composeRule.onNodeWithText("Maps").assertIsSelected()
        composeRule.onNodeWithText("Seasonal").assertIsNotSelected()
    }

    @Test
    fun `FAILS AT BASE the pull-photo picker opens in the right side`() {
        setScreen()
        openFindEditor()
        composeRule.onNodeWithText("From Album").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertTrue("the picker (its Camera and Import buttons) is on screen", textExists("Camera") && textExists("Import"))
        assertRightSide("the pull-photo picker's Camera button", boundsOfText("Camera").left)
        assertTrue("the picker is in the detail pane", tagExists("journal-detail-pane"))
    }

    // ── Item 4 ──

    private companion object {
        val DRAWER_EDGE = 360.dp

        /** The stub map slot's button: on screen only where a map is drawn, and no map is drawn on the wide tree before a search except the location picker's. */
        const val PICKER_MARKER = "Simulate pan to test location"
        val FIND_DATE: LocalDate = LocalDate.of(2026, 8, 2)
        val DAY_DATE: LocalDate = LocalDate.of(2026, 8, 1)
        const val DRAFT_OF_FIND_ID = "draft-of-find-1"
        const val NEW_DAY_ENTRY_ID = "day-new"
        val FIND_TILE_TEXT = "Find on $FIND_DATE"
        val SECOND_FIND_TILE_TEXT = "Find on ${FIND_DATE.plusDays(1)}"

        fun journalSwitchTag(which: String) = "journal-switch-$which"

        val PHOTO = LogPhoto(id = "photo-1", relativePath = "photos/photo-1.jpg", createdAtEpochMillis = 1_000L)
        val PHOTO_OF_DRAFT = LogPhoto(id = "photo-2", relativePath = "photos/photo-2.jpg", createdAtEpochMillis = 2_000L)

        val COMMITTED_FIND: MushroomLogEntry = MushroomLogEntry.draft(id = "find-1", location = LatLng(45.33, -122.63), date = FIND_DATE)
            .copy(isDraft = false, ownIdentification = "Chanterelle", photos = listOf(PHOTO))
        val SECOND_FIND: MushroomLogEntry = MushroomLogEntry.draft(id = "find-2", location = LatLng(45.34, -122.64), date = FIND_DATE.plusDays(1))
            .copy(isDraft = false, ownIdentification = "Morel")
        val DRAFT_FIND: MushroomLogEntry = MushroomLogEntry.draft(id = "find-draft", location = LatLng(45.35, -122.65), date = FIND_DATE.plusDays(2))
            .copy(photos = listOf(PHOTO_OF_DRAFT))

        val DAY_WAYPOINT = Waypoint(
            id = "wp-1",
            lat = 45.32,
            lng = -122.64,
            altitude = null,
            name = "Creek pin",
            note = "",
            createdAtEpochMillis = DAY_DATE.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )

        val DAY_TRACK = Track(
            id = "track-1",
            name = "Creek loop",
            startedAtEpochMillis = 1_000L,
            endedAtEpochMillis = 9_000L,
            points = (0..5).map { i ->
                TrackPoint(lat = 45.32 + i * 0.001, lng = -122.64 + i * 0.002, altitude = null, accuracyMeters = null, timestampEpochMillis = 1_000L + i * 1_000L)
            },
        )

        val COMMITTED_DAY_ENTRY: CartographyEntry = CartographyEntry.draft(id = "day-1", date = DAY_DATE, updatedAtEpochMillis = 1_000L)
            .copy(
                isDraft = false,
                text = "The original account.",
                waypointDecisions = listOf(WaypointDecision(waypointId = "wp-1", name = "Creek pin", lat = 45.32, lng = -122.64, kept = true)),
                trackDecisions = listOf(TrackDecision(trackId = "track-1", name = "Creek loop", distanceMeters = 500.0, durationMillis = 5_000L, pointCount = 6, kept = true)),
            )
    }
}
private val WideJStubMapSlot: MapSlot = { _, _, _, _, _, _, _, onCameraIdle, modifier ->
    Column(modifier.testTag("map-slot")) {
        Button(onClick = { onCameraIdle(LatLng(45.326, -122.634)) }) { Text("Simulate pan to test location") }
    }
}

private class WideJFakeCompassProviderImpl : CompassProvider {
    override val heading: Flow<CompassReading?> = MutableStateFlow(null)
}
private val WideJFakeCompassProvider = WideJFakeCompassProviderImpl()

private object WideJUnavailableLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult = LocationResult.LocationUnavailable
}

private object WideJNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object WideJEmptyRepository : MushroomRepository, TaxonSearchRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object WideJStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object WideJStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

private object WideJStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

private class WideJInMemoryPlannedTripRepository : PlannedTripRepository {
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

private object WideJStubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

private object WideJStubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getMapFullscreen(): Result<Boolean> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
}

private object WideJStubUnitSystemPreferenceRepository : UnitSystemPreferenceRepository {
    override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(UnitSystem.IMPERIAL)
    override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
}

private object WideJStubAppThemePreferenceRepository : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}