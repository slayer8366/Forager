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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import com.zynergylabs.forager.app.ui.log.ENTRIES_ALBUM_TAG
import com.zynergylabs.forager.app.ui.log.ENTRIES_FAB_TAG
import com.zynergylabs.forager.app.ui.log.ENTRIES_HOME_TAG
import com.zynergylabs.forager.app.ui.log.ENTRIES_VIEW_ALBUM_TAG
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
 * Intent 2026-09-28-28 (`prompts/preserved/2026-09-28-28.md`): with the Tools drawer open over the
 * Journal, system Back closes the drawer and leaves the Journal behind it exactly as it was. Before
 * the fix, the Journal's own Back handlers, registered after the drawer's top-level one
 * (`AvailabilityScreen`'s `BackHandler(enabled = isDrawerOpen)`), took the press, and the drawer stayed
 * open.
 *
 * The screen is the real [AvailabilityScreen] with the real [MushroomLogViewModel] and
 * [CartographyViewModel] over an in-memory [ForagerDatabase], the fixture of
 * `LeavingTheJournalInvestigationTest` on branch `leave-journal-investigation` (`a89b240`), copied.
 * The drawer is opened by a real touch at the centre of the Tools nav item (bottom bar or rail); Back
 * goes through the Activity's `onBackPressedDispatcher`.
 *
 * No find is open in any test here: tapping Tools with a find open already closes it
 * (`AvailabilityCompactScaffold`'s tab handler treats Tools as leaving the Journal), which is the
 * Leaving-the-Journal behaviour decided for after M1 and not this fix. What Back must leave alone on the
 * finds side is Records and its Finds chip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class DrawerBackOverJournalTest {

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
            locationProvider = DrawerBackUnavailableLocationProvider,
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
            getTripReportOfflineRegions = GetTripReportOfflineRegionsUseCase(DrawerBackStubOfflineMapRepository),
            computeTrackStatistics = ComputeTrackStatisticsUseCase(),
            setShownOnMap = SetCartographyEntryShownOnMapUseCase(cartographyRepository),
            now = { 1_000L },
        )
        val plannedTrips = DrawerBackInMemoryPlannedTripRepository()
        val availabilityViewModel = AvailabilityViewModel(
            locationProvider = DrawerBackUnavailableLocationProvider,
            locationTracker = DrawerBackNoOpLocationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(DrawerBackEmptyRepository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(DrawerBackEmptyRepository),
            searchTaxa = SearchTaxaUseCase(DrawerBackEmptyRepository),
            getConditions = GetConditionsUseCase(DrawerBackStubWeatherProvider),
            getTripWindows = GetTripWindowsUseCase(DrawerBackStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(plannedTrips),
            savePlannedTrip = SavePlannedTripUseCase(plannedTrips),
            deletePlannedTrip = DeletePlannedTripUseCase(plannedTrips),
            getSeasonalPattern = GetSeasonalPatternUseCase(
                GetSightingsUseCase(DrawerBackEmptyRepository),
                DrawerBackStubHistoricalWeatherProvider,
                ComputeFruitingLagDistributionUseCase(),
            ),
            offlineMapRepository = DrawerBackStubOfflineMapRepository,
            mapPreferencesRepository = DrawerBackStubMapPreferencesRepository,
            unitSystemPreferenceRepository = DrawerBackStubUnitSystemPreferenceRepository,
            appThemePreferenceRepository = DrawerBackStubAppThemePreferenceRepository,
            getTodaysForecast = GetTodaysForecastUseCase(DrawerBackStubTripPlanningWeatherProvider),
        )
        composeRule.setContent {
            val uiState by availabilityViewModel.uiState.collectAsState()
            val logUiState by logViewModel.uiState.collectAsState()
            val cartographyUiState by cartographyViewModel.uiState.collectAsState()
            AvailabilityScreen(
                uiState = uiState,
                searchInputs = AvailabilityScreenSearchInputs(
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
                ),
                offlineMaps = AvailabilityScreenOfflineMaps(
                    onOfflineMapLatChanged = availabilityViewModel::onOfflineMapLatChanged,
                    onOfflineMapLngChanged = availabilityViewModel::onOfflineMapLngChanged,
                    onOfflineMapRadiusChanged = availabilityViewModel::onOfflineMapRadiusChanged,
                    onOfflineMapNameChanged = availabilityViewModel::onOfflineMapNameChanged,
                    onOfflineMapsOpened = availabilityViewModel::onOfflineMapsOpened,
                    onDownloadOfflineMaps = availabilityViewModel::onDownloadOfflineMaps,
                    onDeleteOfflineRegion = availabilityViewModel::onDeleteOfflineRegion,
                ),
                settings = AvailabilityScreenSettings(
                    onNightModeMapsChanged = availabilityViewModel::onNightModeMapsChanged,
                    onThemeModeChanged = availabilityViewModel::onThemeModeChanged,
                ),
                journalEntries = AvailabilityScreenJournalEntries(
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
                ),
                findsLog = AvailabilityScreenFindsLog(
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
                    onRequestDeleteGalleryPhoto = logViewModel::requestDeleteGalleryPhoto,
                ),
                compassProvider = DrawerBackFakeCompassProvider,
                mapSlot = DrawerBackStubMapSlot,
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


    /** A real touch at the centre of the Tools nav item (bottom bar or rail), then checks the drawer opened. */
    private fun touchTools() {
        val item = composeRule.onNodeWithText("Tools").getUnclippedBoundsInRoot()
        val x = (item.left.value + item.right.value) / 2f
        val y = (item.top.value + item.bottom.value) / 2f
        composeRule.onRoot().performTouchInput { click(Offset(x * density, y * density)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Trip Planner").assertIsDisplayed()
    }

    /** System Back through the Activity's own dispatcher, the route AvailabilityScreenBackNavigationTest uses. */
    private fun pressBack() {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
    }

    private fun drawerIsOpen(): Boolean =
        composeRule.onAllNodesWithText("Trip Planner").fetchSemanticsNodes().isNotEmpty() &&
            runCatching { composeRule.onNodeWithText("Trip Planner").assertIsDisplayed() }.isSuccess

    // ── What each test checks after Back ──

    /**
     * One message for both halves of the claim, so a failure says which one broke: the drawer
     * ("Trip Planner", its first section header, displayed) and the Journal state behind it.
     */
    private fun assertDrawerClosedAnd(journalStateHeld: Boolean, journalState: String) {
        val drawerOpen = drawerIsOpen()
        assertTrue(
            "after Back with the drawer open: expected the drawer closed and $journalState held; " +
                "drawer open = $drawerOpen, Journal state held = $journalStateHeld",
            !drawerOpen && journalStateHeld,
        )
    }

    private fun dayEntryReportViewShowing(): Boolean =
        cartographyViewModel.uiState.value.editingEntry?.id == COMMITTED_DAY_ENTRY.id &&
            composeRule.onAllNodesWithContentDescription("Entry options").fetchSemanticsNodes().isNotEmpty() &&
            composeRule.onAllNodesWithText("Your own account (optional)").fetchSemanticsNodes().isEmpty()

    private fun dayEntryEditorShowing(): Boolean =
        cartographyViewModel.uiState.value.editingEntry?.id == COMMITTED_DAY_ENTRY.id &&
            composeRule.onAllNodesWithText("Your own account (optional)").fetchSemanticsNodes().isNotEmpty()

    private fun findsChipSelectedOnRecords(): Boolean =
        runCatching { composeRule.onNodeWithText("Records").assertIsSelected() }.isSuccess &&
            runCatching { composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.FINDS)).assertIsSelected() }.isSuccess

    private fun albumViewShowing(): Boolean =
        composeRule.onAllNodesWithTag(ENTRIES_ALBUM_TAG).fetchSemanticsNodes().isNotEmpty()

    // ── A day entry ──

    private fun checkDayEntryReportView(rail: Boolean) {
        setScreen()
        assertRailShown(rail)
        openCommittedDayEntry()
        touchTools()
        assertTrue("precondition: the day entry is still open in its report view behind the drawer", dayEntryReportViewShowing())

        pressBack()

        assertDrawerClosedAnd(dayEntryReportViewShowing(), "the day entry open in its report view")
        // With the drawer closed, Back is the entry's again: unchanged from before this fix.
        pressBack()
        assertEquals("a second Back closes the entry, as it does with no drawer", null, cartographyViewModel.uiState.value.editingEntry)
        composeRule.onNodeWithTag(ENTRIES_HOME_TAG).assertIsDisplayed()
    }

    private fun checkDayEntryEditor(rail: Boolean) {
        setScreen()
        assertRailShown(rail)
        openCommittedDayEntryEditor()
        touchTools()
        assertTrue("precondition: the day entry is still open in its editor behind the drawer", dayEntryEditorShowing())

        pressBack()

        assertDrawerClosedAnd(dayEntryEditorShowing(), "the day entry open in its editor")
        pressBack()
        assertEquals("a second Back closes the unchanged entry, as it does with no drawer", null, cartographyViewModel.uiState.value.editingEntry)
    }

    @Test
    fun `portrait, Back with the drawer open over a day entry's report view closes the drawer and keeps the report`() =
        checkDayEntryReportView(rail = false)

    @Test
    fun `portrait, Back with the drawer open over a day entry's editor closes the drawer and keeps the editor`() =
        checkDayEntryEditor(rail = false)

    @Test
    @Config(qualifiers = "w823dp-h384dp-land")
    fun `short landscape, Back with the drawer open from the rail over a day entry's report view closes the drawer and keeps the report`() =
        checkDayEntryReportView(rail = true)

    @Test
    @Config(qualifiers = "w823dp-h384dp-land")
    fun `short landscape, Back with the drawer open from the rail over a day entry's editor closes the drawer and keeps the editor`() =
        checkDayEntryEditor(rail = true)

    // ── The finds side: Tools already closes an open find, so what Back must not touch is Records ──

    @Test
    fun `portrait, Back with the drawer open over the Finds chip on Records closes the drawer and keeps the Finds chip`() {
        setScreen()
        openFindsGallery()
        assertTrue("precondition: Records is showing the Finds chip", findsChipSelectedOnRecords())
        touchTools()

        pressBack()

        assertDrawerClosedAnd(findsChipSelectedOnRecords(), "Records with the Finds chip selected")
    }

    // ── The album view of Entries ──

    @Test
    fun `portrait, Back with the drawer open over the Entries album view closes the drawer and keeps the album`() {
        setScreen()
        touchNavItem("Journal")
        composeRule.onNodeWithTag(ENTRIES_VIEW_ALBUM_TAG).performClick()
        composeRule.waitForIdle()
        assertTrue("precondition: the album view is showing", albumViewShowing())
        touchTools()

        pressBack()

        assertDrawerClosedAnd(albumViewShowing(), "the Entries album view")
    }

    // ── RECORD -868 and -871: the Tools drawer's "Offline maps" row, and Back from where it leads ──

    /** A real touch at [fraction] of the Tools drawer's "Offline maps" row's own bounds. */
    private fun touchOfflineMapsRow(fraction: Offset = Offset(0.5f, 0.5f)) {
        val row = composeRule.onNodeWithTag(TOOLS_OFFLINE_MAPS_ROW_TAG).getUnclippedBoundsInRoot()
        val x = row.left.value + (row.right.value - row.left.value) * fraction.x
        val y = row.top.value + (row.bottom.value - row.top.value) * fraction.y
        composeRule.onRoot().performTouchInput { click(Offset(x * density, y * density)) }
        composeRule.waitForIdle()
    }

    private fun chipSelected(filter: RecordsSubTab): Boolean =
        runCatching { composeRule.onNodeWithTag(recordsFilterChipTestTag(filter)).assertIsSelected() }.isSuccess

    private fun navSelected(label: String): Boolean =
        runCatching { composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(COMPACT_BOTTOM_NAV_TAG))).assertIsSelected() }.isSuccess

    /** Journal > Records on the Offline maps chip, with the drawer closed: where the row leads. */
    private fun assertOnOfflineMapsWithDrawerClosed(after: String) {
        val drawerOpen = drawerIsOpen()
        val journal = navSelected("Journal")
        val records = runCatching { composeRule.onNodeWithText("Records").assertIsSelected() }.isSuccess
        val offlineMaps = chipSelected(RecordsSubTab.OFFLINE_MAPS)
        assertTrue(
            "after $after: expected the drawer closed on Journal > Records > Offline maps; " +
                "drawer open = $drawerOpen, Journal = $journal, Records = $records, Offline maps chip = $offlineMaps",
            !drawerOpen && journal && records && offlineMaps,
        )
    }

    /** Samples real touches across the row (CLAUDE.md: a finger is not a point), from the Maps tab each time. */
    @Test
    fun `portrait, real touches across the Tools drawer's Offline maps row open Records on the Offline maps chip, drawer closed`() {
        setScreen()
        for (fraction in listOf(Offset(0.1f, 0.25f), Offset(0.5f, 0.5f), Offset(0.9f, 0.75f))) {
            assertTrue("precondition: on the Maps tab", navSelected("Maps"))
            touchTools()
            touchOfflineMapsRow(fraction)
            assertOnOfflineMapsWithDrawerClosed("a touch at $fraction of the row")
            // Back twice retraces the way in (the next test's claim), which puts this loop back on the Maps tab.
            pressBack()
            pressBack()
        }
    }

    @Test
    fun `portrait, from Maps, Back from Offline maps reopens Tools over Maps, and the next Back closes it on Maps`() {
        setScreen()
        touchTools()
        touchOfflineMapsRow()
        assertOnOfflineMapsWithDrawerClosed("the row")

        pressBack()
        assertTrue("the first Back reopens Tools", drawerIsOpen())
        assertTrue("over the Maps tab", navSelected("Maps"))

        pressBack()
        assertTrue("the second Back closes Tools", !drawerIsOpen())
        assertTrue("on the Maps tab", navSelected("Maps"))
        composeRule.onNodeWithTag("map-slot").assertExists()
    }

    @Test
    fun `portrait, from Seasonal, Back from Offline maps reopens Tools over Seasonal, and the next Back closes it on Seasonal`() {
        setScreen()
        touchNavItem("Seasonal")
        touchTools()
        touchOfflineMapsRow()
        assertOnOfflineMapsWithDrawerClosed("the row, from Seasonal")

        pressBack()
        assertTrue("the first Back reopens Tools", drawerIsOpen())
        assertTrue("over Seasonal, the page the row was tapped from", navSelected("Seasonal"))

        pressBack()
        assertTrue("the second Back closes Tools", !drawerIsOpen())
        assertTrue("on Seasonal", navSelected("Seasonal"))
    }

    @Test
    fun `portrait, from Records on the Waypoints chip, Back from Offline maps reopens Tools there, and the next Back closes it on Waypoints`() {
        setScreen()
        touchNavItem("Journal")
        composeRule.onNodeWithText("Records").performClick()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.WAYPOINTS)).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue("precondition: Records on the Waypoints chip", chipSelected(RecordsSubTab.WAYPOINTS))
        touchTools()
        touchOfflineMapsRow()
        assertOnOfflineMapsWithDrawerClosed("the row, from the Waypoints chip")

        pressBack()
        assertTrue("the first Back reopens Tools", drawerIsOpen())
        assertTrue("over Records on the Waypoints chip", navSelected("Journal") && chipSelected(RecordsSubTab.WAYPOINTS))

        pressBack()
        assertTrue("the second Back closes Tools", !drawerIsOpen())
        assertTrue("on Records' Waypoints chip", chipSelected(RecordsSubTab.WAYPOINTS))
    }

    @Test
    fun `portrait, once the user moves to another chip after Offline maps, Back works normally from there`() {
        setScreen()
        touchNavItem("Seasonal")
        touchTools()
        touchOfflineMapsRow()
        assertOnOfflineMapsWithDrawerClosed("the row")

        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.RECORDED_TRACKS)).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue("precondition: moved on to the Tracks chip", chipSelected(RecordsSubTab.RECORDED_TRACKS))

        pressBack()
        assertTrue("Back does not reopen Tools any more", !drawerIsOpen())
        assertTrue("it steps to Finds, as from any other chip", chipSelected(RecordsSubTab.FINDS) && navSelected("Journal"))

        pressBack()
        assertTrue("then out of Records to Entries, still no drawer", !drawerIsOpen())
        composeRule.onNodeWithTag(ENTRIES_HOME_TAG).assertIsDisplayed()
    }

    private companion object {
        val FIND_DATE: LocalDate = LocalDate.of(2026, 8, 2)
        val DAY_DATE: LocalDate = LocalDate.of(2026, 8, 1)
        const val DRAFT_OF_FIND_ID = "draft-of-find-1"
        const val NEW_DAY_ENTRY_ID = "day-new"
        const val TYPED_TEXT = "Typed on the trail, not saved."
        // A find tile shows the name the user gave the find (data part D, RECORD -702), COMMITTED_FIND's own identification.
        val FIND_TILE_TEXT = "Chanterelle"

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

private val DrawerBackStubMapSlot: MapSlot = { _, _, _, _, _, _, _, onCameraIdle, modifier ->
    Column(modifier.testTag("map-slot")) {
        Button(onClick = { onCameraIdle(LatLng(45.326, -122.634)) }) { Text("Simulate pan to test location") }
    }
}

private class DrawerBackFakeCompassProviderImpl : CompassProvider {
    override val heading: Flow<CompassReading?> = MutableStateFlow(null)
}
private val DrawerBackFakeCompassProvider = DrawerBackFakeCompassProviderImpl()

private object DrawerBackUnavailableLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult = LocationResult.LocationUnavailable
}

private object DrawerBackNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object DrawerBackEmptyRepository : MushroomRepository, TaxonSearchRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object DrawerBackStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object DrawerBackStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

private object DrawerBackStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

private class DrawerBackInMemoryPlannedTripRepository : PlannedTripRepository {
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

private object DrawerBackStubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

private object DrawerBackStubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getMapFullscreen(): Result<Boolean> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
}

private object DrawerBackStubUnitSystemPreferenceRepository : UnitSystemPreferenceRepository {
    override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(UnitSystem.IMPERIAL)
    override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
}

private object DrawerBackStubAppThemePreferenceRepository : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}
