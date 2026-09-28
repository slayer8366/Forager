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
import com.zynergylabs.forager.app.ui.log.MushroomLogViewModel
import com.zynergylabs.forager.app.ui.log.RecordsSubTab
import com.zynergylabs.forager.app.ui.log.SAVE_CONFIRM_TEST_TAG
import com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
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

/**
 * Investigation, intent 2026-09-28-23 (`prompts/preserved/2026-09-28-23.md`): what happens today
 * when the Journal is left, by a real touch on the bottom nav (or the short-landscape rail), with a
 * find or a day entry open. **Every test here pins the current behaviour**, so each passes now; a
 * test's name says what it shows, not what should happen. Nothing in production code was changed
 * to run them.
 *
 * The screen is the real [AvailabilityScreen] (the compact scaffold, its bottom nav and its one
 * "Saved to Drafts" snackbar host), driven by the real [MushroomLogViewModel] and
 * [CartographyViewModel] over an in-memory [ForagerDatabase] (the Room repositories the app's
 * `AppContainer` builds) and a real [FilePhotoStore] writing under the test's `filesDir`. The
 * callbacks are the ones `MainActivity` passes (its `setContent` block): in particular
 * `onDiscardLogDraft = mushroomLogViewModel::onDeleteEntry`, the snackbar's Discard. `MainActivity`
 * itself is not launched; its `AppContainer` builds on-disk databases and platform providers this
 * JVM test cannot use, so its wiring is reproduced here line for line for the callbacks these
 * tests reach.
 *
 * Every data assertion reads the database through the repositories, or the photo file on disk,
 * never a callback count or the snackbar text alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class LeavingTheJournalInvestigationTest {

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
    private val searchCache = InMemorySearchCacheRepository()

    private val photoFile: File get() = File(context.filesDir, PHOTO.relativePath)

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        photoFile.delete()
    }

    private fun setScreen() {
        // Direct executors, as CartographyViewModelTest does: Room's suspend calls then complete on
        // the calling thread, so a ViewModel write has landed by the time waitForIdle returns.
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

        // The committed find, with one album photo attached, and that photo's real file.
        photoFile.parentFile!!.mkdirs()
        photoFile.writeBytes(byteArrayOf(1, 2, 3, 4))
        runBlocking {
            logRepository.addPhotoToGallery(PHOTO).getOrThrow()
            logRepository.save(COMMITTED_FIND).getOrThrow()
            logRepository.attachPhotoToEntry(COMMITTED_FIND.id, PHOTO.id).getOrThrow()
            waypointRepository.save(DAY_WAYPOINT).getOrThrow()
            cartographyRepository.save(COMMITTED_DAY_ENTRY).getOrThrow()
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
            locationProvider = LeaveUnavailableLocationProvider,
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
            getTripReportOfflineRegions = GetTripReportOfflineRegionsUseCase(LeaveStubOfflineMapRepository),
            computeTrackStatistics = ComputeTrackStatisticsUseCase(),
            now = { 1_000L },
        )
        val plannedTrips = LeaveInMemoryPlannedTripRepository()
        val availabilityViewModel = AvailabilityViewModel(
            locationProvider = LeaveUnavailableLocationProvider,
            locationTracker = LeaveNoOpLocationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(LeaveEmptyRepository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(LeaveEmptyRepository),
            searchTaxa = SearchTaxaUseCase(LeaveEmptyRepository),
            getConditions = GetConditionsUseCase(LeaveStubWeatherProvider),
            getTripWindows = GetTripWindowsUseCase(LeaveStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(plannedTrips),
            savePlannedTrip = SavePlannedTripUseCase(plannedTrips),
            deletePlannedTrip = DeletePlannedTripUseCase(plannedTrips),
            getSeasonalPattern = GetSeasonalPatternUseCase(
                GetSightingsUseCase(LeaveEmptyRepository),
                LeaveStubHistoricalWeatherProvider,
                ComputeFruitingLagDistributionUseCase(),
            ),
            offlineMapRepository = LeaveStubOfflineMapRepository,
            mapPreferencesRepository = LeaveStubMapPreferencesRepository,
            unitSystemPreferenceRepository = LeaveStubUnitSystemPreferenceRepository,
            appThemePreferenceRepository = LeaveStubAppThemePreferenceRepository,
            getTodaysForecast = GetTodaysForecastUseCase(LeaveStubTripPlanningWeatherProvider),
        )
        composeRule.setContent {
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
                // What MainActivity passes for the finds.
                logUiState = logUiState.hidingPendingDelete(),
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
                // What MainActivity passes for the day entries.
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
                compassProvider = LeaveFakeCompassProvider,
                mapSlot = LeaveStubMapSlot,
            )
        }
        composeRule.waitForIdle()
    }

    // ── Helpers ──

    /** A real touch at the centre of the nav item labelled [label]; asserts it lands on that tab. */
    private fun touchNavItem(label: String) {
        val item = composeRule.onNodeWithText(label).getUnclippedBoundsInRoot()
        val rails = composeRule.onAllNodesWithTag(COMPACT_NAVIGATION_RAIL_TAG).fetchSemanticsNodes()
        if (rails.isNotEmpty()) {
            val rail = composeRule.onNodeWithTag(COMPACT_NAVIGATION_RAIL_TAG).getUnclippedBoundsInRoot()
            assertTrue(
                "the $label item $item lies in the rail $rail",
                item.left >= rail.left && item.right <= rail.right && item.top >= rail.top && item.bottom <= rail.bottom,
            )
        }
        val x = (item.left.value + item.right.value) / 2f
        val y = (item.top.value + item.bottom.value) / 2f
        composeRule.onRoot().performTouchInput { click(Offset(x * density, y * density)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(label).assertIsSelected()
    }

    private fun assertRailShown(expected: Boolean) {
        assertEquals(
            "the rail is ${if (expected) "" else "not "}the nav in this window",
            expected,
            composeRule.onAllNodesWithTag(COMPACT_NAVIGATION_RAIL_TAG).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private fun openFindsGallery() {
        touchNavItem("Journal")
        composeRule.onNodeWithText("Records").performClick()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.FINDS)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun openFindReport() {
        openFindsGallery()
        // The merged tile, whose centre is on screen in both windows: the tile's own text sits at its
        // foot, below the edge of a short landscape window, so a click there reaches nothing.
        composeRule.onNodeWithText(FIND_TILE_TEXT).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Back to your log").assertExists()
        composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
    }

    private fun openFindEditor() {
        openFindReport()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your own identification (optional)").assertExists()
    }

    private fun snackbarShows(message: String): Boolean =
        composeRule.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()

    /** A real touch on the snackbar's Discard action. */
    private fun touchDiscard() {
        composeRule.onNodeWithText("Discard").performTouchInput { click() }
        composeRule.waitForIdle()
    }

    private fun letSnackbarTimeOut() {
        // SnackbarDuration.Short is 4 s; past it and the exit animation.
        composeRule.mainClock.advanceTimeBy(6_000L)
        composeRule.waitForIdle()
    }

    private fun storedFinds(): List<MushroomLogEntry> = runBlocking { logRepository.getAll().getOrThrow() }

    private fun storedPhotoReferences(): List<String> =
        runBlocking { logRepository.getAllPhotos().getOrThrow() }.single { it.photo.id == PHOTO.id }.referencingEntryIds

    private fun storedGalleryPhotoIds(): List<String> =
        runBlocking { logRepository.getAllPhotos().getOrThrow() }.map { it.photo.id }

    private fun storedDayEntry(id: String): CartographyEntry? = runBlocking { cartographyRepository.getById(id).getOrThrow() }

    private fun assertCommittedFindIntact() {
        val stored = storedFinds().single { it.id == COMMITTED_FIND.id }
        assertEquals("the committed find's row is unchanged", false, stored.isDraft)
        assertEquals("the committed find's own identification is unchanged", COMMITTED_FIND.ownIdentification, stored.ownIdentification)
        assertEquals("the committed find still references its photo", listOf(COMMITTED_FIND.id), storedPhotoReferences())
        assertTrue("the photo's file is still on disk", photoFile.exists())
    }

    private fun openCommittedDayEntry() {
        touchNavItem("Journal")
        composeRule.onNode(hasTestTag("entry-card-${COMMITTED_DAY_ENTRY.id}") or hasTestTag("entry-row-${COMMITTED_DAY_ENTRY.id}")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(COMMITTED_DAY_ENTRY.text).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
    }

    private fun openCommittedDayEntryEditor() {
        openCommittedDayEntry()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your own account (optional)").assertIsDisplayed()
    }

    private fun assertDayEntryReportView() {
        composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
        composeRule.onNodeWithText("Your own account (optional)").assertDoesNotExist()
        composeRule.onNodeWithTag(ENTRIES_HOME_TAG).assertDoesNotExist()
    }

    // ── Behaviour 1: "Saved to Drafts" and Discard on a committed find ──

    @Test
    fun `viewing a committed find then leaving for Maps shows Saved to Drafts with Discard, and no draft row is created`() {
        setScreen()
        openFindReport()

        touchNavItem("Maps")

        assertTrue("the Saved to Drafts snackbar shows", snackbarShows("Saved to Drafts"))
        composeRule.onNodeWithText("Discard").assertIsDisplayed()
        assertEquals("only the committed find is stored: viewing made no draft row", listOf(COMMITTED_FIND.id), storedFinds().map { it.id })
        assertCommittedFindIntact()

        // Ignored, the snackbar leaves everything as it was.
        letSnackbarTimeOut()
        assertTrue("the snackbar is gone", !snackbarShows("Saved to Drafts"))
        assertEquals(listOf(COMMITTED_FIND.id), storedFinds().map { it.id })
        assertCommittedFindIntact()
    }

    @Test
    fun `viewing a committed find then leaving shows Discard, and Discard deletes the committed find's row and photo reference but not the album photo or its file`() {
        setScreen()
        openFindReport()
        touchNavItem("Maps")

        touchDiscard()

        assertEquals("Discard deleted the committed find's row", emptyList<String>(), storedFinds().map { it.id })
        assertEquals("the album photo's row is still stored", listOf(PHOTO.id), storedGalleryPhotoIds())
        assertEquals("the photo is referenced by no find any more", emptyList<String>(), storedPhotoReferences())
        assertTrue("the photo's file is still on disk", photoFile.exists())
        // No Undo: the delete ran at once (onDeleteEntry, not the pending requestDeleteEntry).
        assertEquals(0, composeRule.onAllNodesWithText("Undo").fetchSemanticsNodes().size)
        // Back on the Journal, the find is gone from its gallery.
        touchNavItem("Journal")
        assertEquals(0, composeRule.onAllNodesWithText(FIND_TILE_TEXT, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = "w823dp-h384dp-land")
    fun `short landscape, viewing a committed find then leaving on the rail shows Discard, and Discard deletes the committed find's row`() {
        setScreen()
        assertRailShown(true)
        openFindReport()
        touchNavItem("Maps")

        assertTrue("the Saved to Drafts snackbar shows", snackbarShows("Saved to Drafts"))
        touchDiscard()

        assertEquals("Discard deleted the committed find's row", emptyList<String>(), storedFinds().map { it.id })
        assertTrue("the photo's file is still on disk", photoFile.exists())
    }

    @Test
    fun `a committed find opened in its editor and left unchanged shows Saved to Drafts with Discard, though no draft is kept, and Discard leaves the committed find untouched`() {
        setScreen()
        openFindEditor()
        assertEquals(
            "opening the editor stored a draft copy of the find",
            setOf(COMMITTED_FIND.id, DRAFT_OF_FIND_ID),
            storedFinds().map { it.id }.toSet(),
        )

        touchNavItem("Maps")

        assertTrue("the Saved to Drafts snackbar shows", snackbarShows("Saved to Drafts"))
        composeRule.onNodeWithText("Discard").assertIsDisplayed()
        assertEquals("the unchanged draft copy was deleted by leaving: nothing is in Drafts", listOf(COMMITTED_FIND.id), storedFinds().map { it.id })

        touchDiscard()

        assertEquals(listOf(COMMITTED_FIND.id), storedFinds().map { it.id })
        assertCommittedFindIntact()
    }

    @Test
    fun `a committed find changed in its editor then left shows Saved to Drafts, the draft row is stored, and Discard deletes only the draft`() {
        setScreen()
        openFindEditor()
        composeRule.onNode(hasText("Your own identification (optional)") and hasSetTextAction())
            .performScrollTo()
            .performTextReplacement("Changed, not saved")
        composeRule.waitForIdle()

        touchNavItem("Maps")

        assertTrue("the Saved to Drafts snackbar shows", snackbarShows("Saved to Drafts"))
        val draft = storedFinds().single { it.id == DRAFT_OF_FIND_ID }
        assertEquals(true, draft.isDraft)
        assertEquals(COMMITTED_FIND.id, draft.draftOfEntryId)
        assertEquals("Changed, not saved", draft.ownIdentification)
        assertCommittedFindIntact()

        touchDiscard()

        assertEquals("Discard deleted the draft only", listOf(COMMITTED_FIND.id), storedFinds().map { it.id })
        assertCommittedFindIntact()
    }

    // ── Behaviour 3: an open find is closed by the tab switch ──

    @Test
    fun `a committed find open in its report view is closed by the tab switch, and back on Journal the Finds gallery shows`() {
        setScreen()
        openFindReport()

        touchNavItem("Maps")
        letSnackbarTimeOut()
        touchNavItem("Journal")

        composeRule.onNodeWithContentDescription("Back to your log").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Entry options").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("New log entry").assertIsDisplayed()
        composeRule.onNodeWithText(FIND_TILE_TEXT, useUnmergedTree = true).assertExists()
        assertEquals("no find is open", null, logViewModel.uiState.value.editingEntry)
        assertCommittedFindIntact()
    }

    @Test
    fun `a committed find open in its editor with a change is closed by the tab switch, and back on Journal the gallery shows with the change kept as a draft`() {
        setScreen()
        openFindEditor()
        composeRule.onNode(hasText("Your own identification (optional)") and hasSetTextAction())
            .performScrollTo()
            .performTextReplacement("Changed, not saved")
        composeRule.waitForIdle()

        touchNavItem("Maps")
        letSnackbarTimeOut()
        touchNavItem("Journal")

        composeRule.onNodeWithText("Your own identification (optional)").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Entry options").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("New log entry").assertIsDisplayed()
        assertEquals("no find is open", null, logViewModel.uiState.value.editingEntry)
        assertEquals(listOf(DRAFT_OF_FIND_ID), logViewModel.uiState.value.draftEntries.map { it.id })
        assertEquals("Changed, not saved", storedFinds().single { it.id == DRAFT_OF_FIND_ID }.ownIdentification)
    }

    @Test
    fun `for comparison, a committed day entry open in its report view is still open back on Journal, with no snackbar`() {
        setScreen()
        openCommittedDayEntry()

        touchNavItem("Maps")
        assertTrue("no Saved to Drafts snackbar for a day entry", !snackbarShows("Saved to Drafts"))
        touchNavItem("Journal")

        assertDayEntryReportView()
        composeRule.onNodeWithText(COMMITTED_DAY_ENTRY.text).assertIsDisplayed()
        assertEquals(COMMITTED_DAY_ENTRY.id, cartographyViewModel.uiState.value.editingEntry?.id)
    }

    // ── Behaviour 2: a day entry left in its editor comes back in its report view ──

    @Test
    fun `a committed day entry's editor left with typed text comes back in its report view showing the typed text, which is not stored`() {
        setScreen()
        openCommittedDayEntryEditor()
        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement(TYPED_TEXT)
        composeRule.waitForIdle()

        touchNavItem("Maps")
        touchNavItem("Journal")

        assertDayEntryReportView()
        composeRule.onNodeWithText(TYPED_TEXT).assertIsDisplayed()
        composeRule.onNodeWithText(COMMITTED_DAY_ENTRY.text).assertDoesNotExist()
        assertEquals("the store still holds the original text", COMMITTED_DAY_ENTRY.text, storedDayEntry(COMMITTED_DAY_ENTRY.id)?.text)
        assertEquals("the edit is still held as unsaved", true, cartographyViewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `after that return, the report's back arrow closes with no leave prompt, Entries shows the typed text, and the store still holds the original`() {
        setScreen()
        openCommittedDayEntryEditor()
        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement(TYPED_TEXT)
        composeRule.waitForIdle()
        touchNavItem("Maps")
        touchNavItem("Journal")

        composeRule.onNodeWithContentDescription("Back to Cartography").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LEAVE_PROMPT_DISCARD_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(ENTRIES_HOME_TAG).assertIsDisplayed()
        assertEquals(
            "the in-memory list now carries the typed text",
            TYPED_TEXT,
            cartographyViewModel.uiState.value.entries.single { it.id == COMMITTED_DAY_ENTRY.id }.text,
        )
        assertEquals("nothing is marked unsaved any more", false, cartographyViewModel.uiState.value.hasUnsavedChanges)
        assertEquals("the store still holds the original text", COMMITTED_DAY_ENTRY.text, storedDayEntry(COMMITTED_DAY_ENTRY.id)?.text)
        // What the user sees on Entries: the card carries the typed text, not the stored one.
        composeRule.onNode(hasTestTag("entry-card-${COMMITTED_DAY_ENTRY.id}") or hasTestTag("entry-row-${COMMITTED_DAY_ENTRY.id}"))
            .assert(hasText(TYPED_TEXT, substring = true))
        composeRule.onNode(hasTestTag("entry-card-${COMMITTED_DAY_ENTRY.id}") or hasTestTag("entry-row-${COMMITTED_DAY_ENTRY.id}"))
            .assert(!hasText(COMMITTED_DAY_ENTRY.text, substring = true))

        // Reopened, the report shows the typed text though it was never stored.
        composeRule.onNode(hasTestTag("entry-card-${COMMITTED_DAY_ENTRY.id}") or hasTestTag("entry-row-${COMMITTED_DAY_ENTRY.id}")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(TYPED_TEXT).assertIsDisplayed()
        assertEquals(COMMITTED_DAY_ENTRY.text, storedDayEntry(COMMITTED_DAY_ENTRY.id)?.text)
    }

    @Test
    fun `after that return, Edit entry again shows the typed text still unsaved, and Save stores it`() {
        setScreen()
        openCommittedDayEntryEditor()
        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement(TYPED_TEXT)
        composeRule.waitForIdle()
        touchNavItem("Maps")
        touchNavItem("Journal")

        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasText("Your own account (optional)") and hasSetTextAction()).assertTextContains(TYPED_TEXT)
        assertEquals(true, cartographyViewModel.uiState.value.hasUnsavedChanges)
        composeRule.onNodeWithText("Save").performScrollTo().performClick()
        composeRule.onNodeWithTag(SAVE_CONFIRM_TEST_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(TYPED_TEXT, storedDayEntry(COMMITTED_DAY_ENTRY.id)?.text)
    }

    @Test
    fun `a committed day entry's editor left with a waypoint withheld comes back in its report view without that waypoint, and the store still keeps it`() {
        setScreen()
        openCommittedDayEntry()
        // The report shows the kept waypoint before any edit, so its absence below is the edit's.
        composeRule.onNodeWithText(DAY_WAYPOINT.name).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(DAY_WAYPOINT.name).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Withhold").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Keep").assertExists()

        touchNavItem("Maps")
        touchNavItem("Journal")

        assertDayEntryReportView()
        composeRule.onNodeWithText(DAY_WAYPOINT.name).assertDoesNotExist()
        assertEquals(
            "the store still keeps the waypoint",
            listOf(true),
            storedDayEntry(COMMITTED_DAY_ENTRY.id)?.waypointDecisions?.map { it.kept },
        )
        assertEquals(true, cartographyViewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `a new day entry, a draft, left in its editor comes back in its report view, its typed text stored as the draft`() {
        setScreen()
        touchNavItem("Journal")
        composeRule.onNodeWithTag(ENTRIES_FAB_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement(TYPED_TEXT)
        composeRule.waitForIdle()

        touchNavItem("Maps")
        touchNavItem("Journal")

        assertDayEntryReportView()
        composeRule.onNodeWithText(TYPED_TEXT).assertIsDisplayed()
        composeRule.onNodeWithText("Finish entry").assertDoesNotExist()
        val stored = storedDayEntry(NEW_DAY_ENTRY_ID)
        assertEquals(true, stored?.isDraft)
        assertEquals(TYPED_TEXT, stored?.text)
    }

    private companion object {
        val FIND_DATE: LocalDate = LocalDate.of(2026, 8, 2)
        val DAY_DATE: LocalDate = LocalDate.of(2026, 8, 1)
        const val DRAFT_OF_FIND_ID = "draft-of-find-1"
        const val NEW_DAY_ENTRY_ID = "day-new"
        const val TYPED_TEXT = "Typed on the trail, not saved."
        val FIND_TILE_TEXT = "Find on $FIND_DATE"

        val PHOTO = LogPhoto(id = "photo-1", relativePath = "photos/photo-1.jpg", createdAtEpochMillis = 1_000L)

        val COMMITTED_FIND: MushroomLogEntry = MushroomLogEntry.draft(id = "find-1", location = LatLng(45.33, -122.63), date = FIND_DATE)
            .copy(isDraft = false, ownIdentification = "Chanterelle", photos = listOf(PHOTO))

        val DAY_WAYPOINT = Waypoint(
            id = "wp-1",
            lat = 45.32,
            lng = -122.64,
            altitude = null,
            name = "Creek pin",
            note = "",
            createdAtEpochMillis = DAY_DATE.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )

        val COMMITTED_DAY_ENTRY: CartographyEntry = CartographyEntry.draft(id = "day-1", date = DAY_DATE, updatedAtEpochMillis = 1_000L)
            .copy(
                isDraft = false,
                text = "The original account.",
                waypointDecisions = listOf(WaypointDecision(waypointId = "wp-1", name = "Creek pin", lat = 45.32, lng = -122.64, kept = true)),
            )
    }
}

private val LeaveStubMapSlot: MapSlot = { _, _, _, _, _, _, _, onCameraIdle, modifier ->
    Column(modifier.testTag("map-slot")) {
        Button(onClick = { onCameraIdle(LatLng(45.326, -122.634)) }) { Text("Simulate pan to test location") }
    }
}

private class LeaveFakeCompassProviderImpl : CompassProvider {
    override val heading: Flow<CompassReading?> = MutableStateFlow(null)
}
private val LeaveFakeCompassProvider = LeaveFakeCompassProviderImpl()

private object LeaveUnavailableLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult = LocationResult.LocationUnavailable
}

private object LeaveNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object LeaveEmptyRepository : MushroomRepository, TaxonSearchRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object LeaveStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object LeaveStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

private object LeaveStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

private class LeaveInMemoryPlannedTripRepository : PlannedTripRepository {
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

private object LeaveStubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

private object LeaveStubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getMapFullscreen(): Result<Boolean> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
}

private object LeaveStubUnitSystemPreferenceRepository : UnitSystemPreferenceRepository {
    override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(UnitSystem.IMPERIAL)
    override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
}

private object LeaveStubAppThemePreferenceRepository : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}
