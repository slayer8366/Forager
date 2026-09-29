package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.data.repository.RoomCartographyEntryRepository
import com.zynergylabs.forager.app.data.repository.RoomMushroomLogRepository
import com.zynergylabs.forager.app.data.repository.RoomOfflineRegionDayIndex
import com.zynergylabs.forager.app.data.repository.RoomTrackRepository
import com.zynergylabs.forager.app.data.repository.RoomWaypointRepository
import com.zynergylabs.forager.app.domain.CartographyEntryRepository
import com.zynergylabs.forager.app.domain.CommitCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.ComputeTrackStatisticsUseCase
import com.zynergylabs.forager.app.domain.CreateCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.DeleteCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.GetCartographyDraftEntriesUseCase
import com.zynergylabs.forager.app.domain.GetCartographyEntriesUseCase
import com.zynergylabs.forager.app.domain.GetCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.GetDerivedTripUseCase
import com.zynergylabs.forager.app.domain.GetTripReportOfflineRegionsUseCase
import com.zynergylabs.forager.app.domain.MapRecords
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.SaveCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.SetCartographyEntryShownOnMapUseCase
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.FindDecision
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.ui.log.CartographyViewModel
import com.zynergylabs.forager.app.ui.log.LEAVE_PROMPT_CANCEL_TEST_TAG
import com.zynergylabs.forager.app.ui.log.LEAVE_PROMPT_DISCARD_TEST_TAG
import com.zynergylabs.forager.app.ui.log.LEAVE_PROMPT_SAVE_TEST_TAG
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.map.JOURNAL_ENTRIES_CHIP_TAG
import com.zynergylabs.forager.app.ui.map.JOURNAL_ENTRIES_HIDE_ALL_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_ENTRY_COUNT_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.journalEntriesListRowTag
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.layerPaintFor
import com.zynergylabs.forager.app.ui.map.mapBubbleEntryLineTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import com.zynergylabs.forager.app.domain.HighlightedRecord
import com.zynergylabs.forager.app.domain.HighlightedRecordKind
import com.zynergylabs.forager.app.domain.MapLayerPreferences
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.PhotoAttachment
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.FEATURE_ID_PROPERTY
import com.zynergylabs.forager.app.ui.map.journalHighlightFeatureCollections
import com.zynergylabs.forager.app.ui.map.layers.JOURNAL_ENTRIES_SWITCH_LAYER_ID
import com.zynergylabs.forager.app.ui.map.layers.MapSourceIds
import com.zynergylabs.forager.app.ui.track.formatRecordTimestamp
import java.time.LocalDate
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
import org.robolectric.shadows.ShadowToast

private typealias EntriesOnMapRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

// The records: one saved find and one waypoint, each drawn by the stub map as a glyph.
private val FIND_AT = LatLng(45.51, -122.61)
private val FIND: MushroomLogEntry = MushroomLogEntry.draft(id = "find-1", location = FIND_AT, date = LocalDate.of(2026, 9, 12))
    .copy(isDraft = false, ownIdentification = "Golden chanterelle")
private val WAYPOINT = Waypoint("wp-1", 45.326, -122.634, null, "Creek pin", "", 1_000L)
private val RECORDS = MapRecords(
    findMarkers = listOf(RecordPoint(FIND.id, FIND_AT)),
    photoMarkers = emptyList(),
    trackPolylines = emptyList(),
    offlineRegionCircles = emptyList(),
    failures = emptyList(),
)

/** A saved entry keeping the find and the waypoint. */
private fun entryKeepingBoth(id: String, date: LocalDate, text: String, shown: Boolean) =
    CartographyEntry.draft(id = id, date = date, updatedAtEpochMillis = 1_000L).copy(
        isDraft = false,
        text = text,
        findDecisions = listOf(FindDecision(findId = FIND.id, foundOn = date, ownIdentification = "Golden chanterelle", hasPhotos = false, kept = true)),
        waypointDecisions = listOf(WaypointDecision(waypointId = WAYPOINT.id, name = WAYPOINT.name, lat = WAYPOINT.lat, lng = WAYPOINT.lng, kept = true)),
        shownOnMap = shown,
    )

private val ENTRY_A_TEXT = "A walk by the creek."
private val ENTRY_B_TEXT = "The other account."
private val TYPED_TEXT = "An edit not saved yet."
private fun entryA(shown: Boolean) = entryKeepingBoth("entry-a", LocalDate.of(2026, 9, 12), ENTRY_A_TEXT, shown)
private fun entryB(shown: Boolean) = CartographyEntry.draft(id = "entry-b", date = LocalDate.of(2026, 9, 5), updatedAtEpochMillis = 1_000L)
    .copy(isDraft = false, text = ENTRY_B_TEXT, shownOnMap = shown)

private fun hostActivityRule() = object : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }
}

private object EntriesOnMapNoOfflineMaps : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline downloads are not exercised by these tests"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline downloads are not exercised by these tests"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

/**
 * J8, Journal entries on the main map, through the real [AvailabilityScreen] (`prompts/preserved/2026-09-28-52.md`
 * J8-3 and J8-4, with the owner's rulings in `2026-09-28-53` and the planner's in `-64` and `-65`): the
 * report menu's toggle, the Maps-tab chip and its list, the Layers sheet's "Journal entries" switch,
 * a highlighted record's bubble lines and "Open entry" with the owner's prompt-first rule.
 *
 * The day entries go through the real [CartographyViewModel] over an in-memory [ForagerDatabase], and
 * every assertion about the field reads the store. The Maps tab's records come from the real
 * [AvailabilityViewModel] (`getMapRecords`), and the map is [BubbleMapSlot], which records what the
 * map is handed and reports a glyph's feature tap as `SightingsMap` does. Every touch that a claim
 * rests on is a real one at screen coordinates (CLAUDE.md, Testing).
 *
 * The compact tests ([JournalEntriesOnMapCompactTests]) run in portrait ([JournalEntriesOnMapPortraitTest])
 * and in the short landscape window ([JournalEntriesOnMapShortLandscapeTest]), each with its glyphs placed
 * clear of that window's chrome; [JournalEntriesOnMapWideTest] has the wide layout's own.
 */
internal abstract class JournalEntriesOnMapHarness {

    protected val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivityRule()).around(composeRule)

    /** Where the stub map draws the find's and the waypoint's glyphs, clear of this window's chrome. */
    protected abstract val glyphX: Dp
    protected abstract val glyphY: Dp

    /** Glyphs the stub map draws besides the find's and the waypoint's (the J8 follow-ups draw a photo's). */
    protected open val extraGlyphs: List<StubGlyph> get() = emptyList()

    protected val map: BubbleMapSlot by lazy {
        BubbleMapSlot(
            listOf(
                StubGlyph(MapLayerIds.FINDS, FIND.id, glyphX, glyphY, FIND_AT),
                StubGlyph(MapLayerIds.WAYPOINTS, WAYPOINT.id, glyphX, glyphY + 48.dp, LatLng(WAYPOINT.lat, WAYPOINT.lng)),
            ) + extraGlyphs,
        )
    }
    protected val layerPreferences = InMemoryLayerPreferences()

    /**
     * What the screen is handed, set before [setScreen]: the saved records the Maps tab draws
     * (`getMapRecords`), every waypoint (as `MainActivity` passes them; the screen picks the ones its map
     * draws) and the Journal's state. The find and the waypoint above by default.
     */
    protected var screenRecords: MapRecords = RECORDS
    protected var screenWaypoints: List<Waypoint> = listOf(WAYPOINT)
    protected var screenLog: MushroomLogUiState = MushroomLogUiState(entries = listOf(FIND))
    private lateinit var database: ForagerDatabase
    private lateinit var cartographyRepository: CartographyEntryRepository
    protected lateinit var cartographyViewModel: CartographyViewModel

    /** Makes every `shownOnMap` write fail, as a full disk would. */
    protected var failShownOnMapWrites = false

    @After
    fun closeDatabase() {
        if (::database.isInitialized) database.close()
    }

    protected fun setScreen(vararg entries: CartographyEntry) {
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), ForagerDatabase::class.java)
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .allowMainThreadQueries()
            .build()
        val room = RoomCartographyEntryRepository(database.cartographyEntryDao())
        cartographyRepository = object : CartographyEntryRepository by room {
            override suspend fun setShownOnMap(id: String, shown: Boolean): Result<Unit> =
                if (failShownOnMapWrites) Result.failure(IllegalStateException("write refused")) else room.setShownOnMap(id, shown)
        }
        runBlocking { entries.forEach { room.save(it).getOrThrow() } }
        cartographyViewModel = CartographyViewModel(
            getEntries = GetCartographyEntriesUseCase(cartographyRepository),
            getDraftEntries = GetCartographyDraftEntriesUseCase(cartographyRepository),
            createEntry = CreateCartographyEntryUseCase(cartographyRepository, now = { 1_000L }, idGenerator = { "entry-new" }),
            saveEntry = SaveCartographyEntryUseCase(cartographyRepository, now = { 2_000L }),
            getEntry = GetCartographyEntryUseCase(cartographyRepository),
            commitEntry = CommitCartographyEntryUseCase(cartographyRepository, now = { 1_000L }),
            deleteEntry = DeleteCartographyEntryUseCase(cartographyRepository),
            getDerivedTrip = GetDerivedTripUseCase(
                mushroomLogRepository = RoomMushroomLogRepository(database.mushroomLogDao()),
                trackRepository = RoomTrackRepository(database.trackDao()),
                waypointRepository = RoomWaypointRepository(database.waypointDao()),
                offlineRegionDayIndex = RoomOfflineRegionDayIndex(database.offlineRegionDao()),
            ),
            getTripReportOfflineRegions = GetTripReportOfflineRegionsUseCase(EntriesOnMapNoOfflineMaps),
            computeTrackStatistics = ComputeTrackStatisticsUseCase(),
            setShownOnMap = SetCartographyEntryShownOnMapUseCase(cartographyRepository),
            now = { 1_000L },
        )
        val availabilityViewModel = mapLayersViewModel(getMapRecords = { screenRecords }, layerPreferences = layerPreferences)
        prepareAvailability(availabilityViewModel)
        composeRule.setContent {
            val uiState by availabilityViewModel.uiState.collectAsState()
            val cartographyUiState by cartographyViewModel.uiState.collectAsState()
            AvailabilityScreen(
                uiState = uiState,
                logUiState = screenLog,
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
                onMapFullscreenChanged = availabilityViewModel::onMapFullscreenChanged,
                mapSlot = map.slot,
                onMapShown = availabilityViewModel::onMapShown,
                onMapLayerVisibilityChanged = availabilityViewModel::onMapLayerVisibilityChanged,
                onMapLayerOpacityChanged = availabilityViewModel::onMapLayerOpacityChanged,
                onColourFieldMoved = availabilityViewModel::onColourFieldMoved,
                waypoints = screenWaypoints,
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
                onSetCartographyEntryShownOnMap = cartographyViewModel::onSetShownOnMap,
                onCartographyShownOnMapErrorDismissed = cartographyViewModel::onShownOnMapErrorDismissed,
            )
        }
        composeRule.waitForIdle()
    }

    /** A hook for a layout that needs the ViewModel set up before the screen composes (the wide map needs a search). */
    protected open fun prepareAvailability(viewModel: AvailabilityViewModel) = Unit

    // ── Helpers ──

    protected fun touchCentreOf(node: SemanticsNodeInteraction) {
        node.performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    protected fun touchCentreOf(tag: String) = touchCentreOf(composeRule.onNodeWithTag(tag))

    /**
     * A real touch at [xDp], [yDp] on the app's own window. The first root, not `onRoot()`: a touch that
     * lands on the chip opens its list in a popup window of its own, and `onRoot()` then finds two roots
     * and throws, so the test would error instead of counting that touch as one that missed the map.
     */
    protected fun touchAt(xDp: Dp, yDp: Dp) {
        val at = with(composeRule.density) { Offset(xDp.toPx(), yDp.toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(at) }
        composeRule.waitForIdle()
    }

    /** A real touch at the centre of the nav item labelled [label], then it is selected. */
    protected fun touchNavItem(label: String) {
        touchCentreOf(composeRule.onNodeWithText(label))
        composeRule.onNodeWithText(label).assertIsSelected()
    }

    protected fun stored(id: String): CartographyEntry = runBlocking { cartographyRepository.getById(id).getOrThrow()!! }

    protected fun entryCard(id: String) = composeRule.onNode(hasTestTag("entry-card-$id") or hasTestTag("entry-row-$id"))

    /** The Journal's Entries, then the entry's card: its report. */
    protected fun openEntryReport(id: String, text: String) {
        touchNavItem("Journal")
        entryCard(id).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertReportShowing(text)
    }

    protected fun assertReportShowing(text: String) {
        // J6a: on the wide tree the report is in the right side's detail pane while the Entries list, whose
        // card carries the same text, stays in the left column, so the text is read inside the pane there.
        val inPane = composeRule.onAllNodesWithTag("journal-detail-pane").fetchSemanticsNodes().isNotEmpty()
        (if (inPane) composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag("journal-detail-pane"))) else composeRule.onNodeWithText(text)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
        assertEquals("the report, not the editor", 0, composeRule.onAllNodes(hasText("Your own account (optional)") and hasSetTextAction()).fetchSemanticsNodes().size)
    }

    protected fun openEntryMenu() {
        touchCentreOf(composeRule.onNodeWithContentDescription("Entry options"))
    }

    protected fun openEditorWithUnsavedText(id: String, text: String) {
        openEntryReport(id, text)
        openEntryMenu()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("Your own account (optional)") and hasSetTextAction()).performTextReplacement(TYPED_TEXT)
        composeRule.waitForIdle()
        assertTrue("the edit is held unsaved", cartographyViewModel.uiState.value.hasUnsavedChanges)
    }

    protected fun chipShown(): Boolean = composeRule.onAllNodesWithTag(JOURNAL_ENTRIES_CHIP_TAG).fetchSemanticsNodes().isNotEmpty()

    /** On the Maps tab, a real touch on the find's glyph, then on entry [id]'s date line in its bubble. */
    protected fun openEntryFromFindBubble(id: String) {
        touchNavItem("Maps")
        touchCentreOf(glyphTag(FIND.id))
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        touchCentreOf(mapBubbleEntryLineTag(id))
    }

    protected fun haloVisible(layerId: String): Boolean =
        layerPaintFor(MAP_LAYER_REGISTRY.single { it.id == layerId }, map.renderMode!!.layers).visible

    protected val halos = listOf(
        MapLayerIds.JOURNAL_ENTRY_REGIONS,
        MapLayerIds.JOURNAL_ENTRY_TRACKS,
        MapLayerIds.JOURNAL_ENTRY_WAYPOINTS,
        MapLayerIds.JOURNAL_ENTRY_FINDS,
        MapLayerIds.JOURNAL_ENTRY_PHOTOS,
    )

}

/** The compact tree's tests, run in portrait and in the short landscape window. */
internal abstract class JournalEntriesOnMapCompactTests : JournalEntriesOnMapHarness() {

    // ── J8-3: the report menu ──

    @Test
    fun `the report menu's Show on map writes the field, the Maps tab's chip then counts the entry, and the menu offers Hide from map`() {
        setScreen(entryA(shown = false), entryB(shown = false))
        openEntryReport("entry-a", ENTRY_A_TEXT)
        openEntryMenu()

        touchCentreOf(composeRule.onNodeWithText("Show on map"))

        assertEquals("the store has it", true, stored("entry-a").shownOnMap)
        assertEquals("and nothing else changed", ENTRY_A_TEXT, stored("entry-a").text)
        touchNavItem("Maps")
        composeRule.onNodeWithTag(JOURNAL_ENTRIES_CHIP_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("1 journal entry on map").assertIsDisplayed()

        touchNavItem("Journal")
        assertReportShowing(ENTRY_A_TEXT)
        openEntryMenu()
        composeRule.onNodeWithText("Hide from map").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Show on map").fetchSemanticsNodes().size)
    }

    // Dispatch 2026-09-28-104, item 7: "Show on map" only when the saved entry keeps at least one record
    // the map can highlight (its kept decisions and attached photos, as GetJournalEntryHighlightsUseCase
    // reads them). Hidden, not disabled, as the report's other absent controls are.
    @Test
    fun `the report menu does not offer Show on map on a saved entry that keeps nothing`() {
        setScreen(entryB(shown = false))
        openEntryReport("entry-b", ENTRY_B_TEXT)
        openEntryMenu()

        composeRule.onNodeWithText("Edit entry").assertIsDisplayed()
        composeRule.onNodeWithText("Delete entry").assertIsDisplayed()
        assertEquals("no Show on map", 0, composeRule.onAllNodesWithText("Show on map").fetchSemanticsNodes().size)
        assertEquals("nor a Hide from map", 0, composeRule.onAllNodesWithText("Hide from map").fetchSemanticsNodes().size)
    }

    @Test
    fun `the report menu still offers Hide from map on a shown entry that keeps nothing`() {
        setScreen(entryB(shown = true))
        openEntryReport("entry-b", ENTRY_B_TEXT)
        openEntryMenu()

        composeRule.onNodeWithText("Hide from map").assertIsDisplayed()
    }

    @Test
    fun `the report menu offers Show on map on a saved entry that keeps a record`() {
        setScreen(entryA(shown = false))
        openEntryReport("entry-a", ENTRY_A_TEXT)
        openEntryMenu()

        composeRule.onNodeWithText("Show on map").assertIsDisplayed()
    }

    @Test
    fun `a failed Show on map shows exactly Changes not applied, Try again, and changes nothing`() {
        failShownOnMapWrites = true
        setScreen(entryA(shown = false))
        openEntryReport("entry-a", ENTRY_A_TEXT)
        openEntryMenu()

        touchCentreOf(composeRule.onNodeWithText("Show on map"))

        assertEquals("Changes not applied. Try again.", ShadowToast.getTextOfLatestToast())
        assertEquals(false, stored("entry-a").shownOnMap)
        touchNavItem("Maps")
        assertEquals("no chip", false, chipShown())
    }

    // ── J8-3: the chip ──

    @Test
    fun `the chip lists the shown entries by date, a row's Hide hides that entry, and Hide all hides the rest`() {
        setScreen(entryA(shown = true), entryB(shown = true))
        touchNavItem("Maps")
        composeRule.onNodeWithText("2 journal entries on map").assertIsDisplayed()

        touchCentreOf(JOURNAL_ENTRIES_CHIP_TAG)
        composeRule.onNodeWithTag(journalEntriesListRowTag("entry-a")).assertIsDisplayed()
        composeRule.onNodeWithText("2026-09-12").assertIsDisplayed()
        composeRule.onNodeWithText("2026-09-05").assertIsDisplayed()
        touchCentreOf(journalEntriesListRowTag("entry-b"))

        assertEquals(false, stored("entry-b").shownOnMap)
        assertEquals(true, stored("entry-a").shownOnMap)
        composeRule.onNodeWithText("1 journal entry on map").assertIsDisplayed()

        if (composeRule.onAllNodesWithTag(JOURNAL_ENTRIES_HIDE_ALL_TAG).fetchSemanticsNodes().isEmpty()) touchCentreOf(JOURNAL_ENTRIES_CHIP_TAG)
        touchCentreOf(JOURNAL_ENTRIES_HIDE_ALL_TAG)

        assertEquals(false, stored("entry-a").shownOnMap)
        assertEquals("no entry is shown, so no chip", false, chipShown())
    }

    /**
     * Clears the map around the chip of other chrome before the touches below. A no-op where nothing
     * else is there; the short landscape window moves the icon cluster (see its override).
     */
    protected open fun clearTheChipsSurroundings() = Unit

    @Test
    fun `touches all around the chip reach the map, and a touch on the chip does not`() {
        setScreen(entryA(shown = true))
        touchNavItem("Maps")
        clearTheChipsSurroundings()
        val chip = composeRule.onNodeWithTag(JOURNAL_ENTRIES_CHIP_TAG).getUnclippedBoundsInRoot()
        val midY = (chip.top + chip.bottom) / 2
        val midX = (chip.left + chip.right) / 2
        val points = listOf(
            chip.left - 6.dp to midY,
            chip.right + 6.dp to midY,
            midX to chip.bottom + 6.dp,
            chip.left - 6.dp to chip.bottom + 6.dp,
            chip.right + 6.dp to chip.bottom + 6.dp,
        )
        // Each touch lands on the map and on no other chrome, or it could not tell the chip's own
        // footprint from something else's: checked here, so a point on other chrome fails the test
        // instead of passing or failing it for the wrong reason.
        val slot = composeRule.onNodeWithTag("map-slot").getUnclippedBoundsInRoot()
        val chrome = listOf(MAP_ICON_CLUSTER_TAG, SEARCH_ENTRY_BAR_TAG, COMPACT_NAVIGATION_RAIL_TAG, "compass-elevation-strip")
            .flatMap { tag -> composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().map { tag to it.boundsInRoot } }
        points.forEach { (x, y) ->
            assertTrue("($x, $y) is on the map $slot", x > slot.left && x < slot.right && y > slot.top && y < slot.bottom)
            chrome.forEach { (tag, px) ->
                val b = with(composeRule.density) { androidx.compose.ui.unit.DpRect(px.left.toDp(), px.top.toDp(), px.right.toDp(), px.bottom.toDp()) }
                assertTrue("($x, $y) is clear of $tag $b", x < b.left || x > b.right || y < b.top || y > b.bottom)
            }
        }
        val before = map.taps

        points.forEach { (x, y) -> touchAt(x, y) }
        assertEquals("all five touches around the chip reached the map", before + points.size, map.taps)

        touchAt(chip.left + 4.dp, midY)
        assertEquals("a touch on the chip is the chip's", before + points.size, map.taps)
    }

    // ── J8-3: the Layers switch ──

    @Test
    fun `the Journal entries switch hides every highlight together while the chip stays`() {
        setScreen(entryA(shown = true))
        touchNavItem("Maps")
        val highlights = map.content!!.journalHighlights
        assertEquals("the map is handed the kept find", listOf(FIND.id), highlights.findMarkers.map { it.recordId })
        assertEquals("and the kept waypoint", listOf(WAYPOINT.id), highlights.waypointMarkers.map { it.recordId })
        halos.forEach { assertTrue("$it drawn by default", haloVisible(it)) }

        touchCentreOf(composeRule.onNodeWithContentDescription(LAYERS_ROW_DESCRIPTION))
        touchCentreOf(composeRule.onNodeWithText("Journal entries").performScrollTo())

        halos.forEach { assertEquals("$it hidden", false, haloVisible(it)) }
        assertTrue("the find itself is still drawn", layerPaintFor(MAP_LAYER_REGISTRY.single { it.id == MapLayerIds.FINDS }, map.renderMode!!.layers).visible)
        assertTrue("the choice is stored", "visible ${MapLayerIds.JOURNAL_ENTRY_TRACKS} false" in layerPreferences.writes)
        assertEquals("the entry is still shown", true, stored("entry-a").shownOnMap)
        assertTrue("and the chip stays", chipShown())
    }

    // ── J8-4: the bubble and Open entry ──

    @Test
    fun `a highlighted record's bubble names its entry by date, and a touch on the date opens that entry's report on the Journal`() {
        setScreen(entryA(shown = true), entryB(shown = false))
        touchNavItem("Maps")
        touchCentreOf(glyphTag(FIND.id))
        composeRule.onNodeWithTag(mapBubbleEntryLineTag("entry-a")).assertIsDisplayed()
        composeRule.onNodeWithText("2026-09-12", useUnmergedTree = true).assertIsDisplayed()

        touchCentreOf(mapBubbleEntryLineTag("entry-a"))

        composeRule.onNodeWithText("Journal").assertIsSelected()
        assertReportShowing(ENTRY_A_TEXT)
        assertEquals("entry-a", cartographyViewModel.uiState.value.editingEntry?.id)
    }

    @Test
    fun `more than three keeping entries are one line, and a date in its list opens that entry`() {
        val day = LocalDate.of(2026, 9, 12)
        setScreen(
            entryKeepingBoth("entry-1", day, "First.", shown = true),
            entryKeepingBoth("entry-2", day.minusDays(1), "Second.", shown = true),
            entryKeepingBoth("entry-3", day.minusDays(2), "Third.", shown = true),
            entryKeepingBoth("entry-4", day.minusDays(3), "Fourth.", shown = true),
        )
        touchNavItem("Maps")
        touchCentreOf(glyphTag(FIND.id))
        composeRule.onNodeWithTag(MAP_BUBBLE_ENTRY_COUNT_TAG).assertTextEquals("Kept in 4 journal entries")

        touchCentreOf(MAP_BUBBLE_ENTRY_COUNT_TAG)
        touchCentreOf(mapBubbleEntryLineTag("entry-3"))

        composeRule.onNodeWithText("Journal").assertIsSelected()
        assertReportShowing("Third.")
    }

    @Test
    fun `opening an entry while another has unsaved edits asks first, and Discard keeps the stored text and opens it`() {
        setScreen(entryA(shown = true), entryB(shown = false))
        openEditorWithUnsavedText("entry-b", ENTRY_B_TEXT)

        openEntryFromFindBubble("entry-a")

        composeRule.onNodeWithTag(LEAVE_PROMPT_DISCARD_TEST_TAG).assertIsDisplayed()
        assertEquals("nothing opened yet", "entry-b", cartographyViewModel.uiState.value.editingEntry?.id)
        touchCentreOf(LEAVE_PROMPT_DISCARD_TEST_TAG)

        assertReportShowing(ENTRY_A_TEXT)
        assertEquals("the discarded edit never reached the store", ENTRY_B_TEXT, stored("entry-b").text)
    }

    @Test
    fun `opening an entry while another has unsaved edits asks first, and Save stores the edit and opens it`() {
        setScreen(entryA(shown = true), entryB(shown = false))
        openEditorWithUnsavedText("entry-b", ENTRY_B_TEXT)

        openEntryFromFindBubble("entry-a")

        composeRule.onNodeWithTag(LEAVE_PROMPT_SAVE_TEST_TAG).assertIsDisplayed()
        touchCentreOf(LEAVE_PROMPT_SAVE_TEST_TAG)

        assertReportShowing(ENTRY_A_TEXT)
        assertEquals("the saved edit is stored", TYPED_TEXT, stored("entry-b").text)
    }

    @Test
    fun `opening an entry while another has unsaved edits, Cancel keeps the edit open and unsaved and opens nothing`() {
        setScreen(entryA(shown = true), entryB(shown = false))
        openEditorWithUnsavedText("entry-b", ENTRY_B_TEXT)

        openEntryFromFindBubble("entry-a")
        touchCentreOf(LEAVE_PROMPT_CANCEL_TEST_TAG)

        assertEquals("entry-b", cartographyViewModel.uiState.value.editingEntry?.id)
        assertTrue("still unsaved", cartographyViewModel.uiState.value.hasUnsavedChanges)
        composeRule.onNode(hasText("Your own account (optional)") and hasSetTextAction()).assert(hasText(TYPED_TEXT, substring = true))
        assertEquals(ENTRY_B_TEXT, stored("entry-b").text)
    }

    @Test
    fun `opening an entry while an unchanged entry is open simply opens the new one, with no prompt`() {
        setScreen(entryA(shown = true), entryB(shown = false))
        openEntryReport("entry-b", ENTRY_B_TEXT)

        openEntryFromFindBubble("entry-a")

        assertEquals("no prompt", 0, composeRule.onAllNodesWithTag(LEAVE_PROMPT_SAVE_TEST_TAG).fetchSemanticsNodes().size)
        assertReportShowing(ENTRY_A_TEXT)
    }
}

/** Portrait, at the S22 Ultra's size; the glyphs on the left half of the map, below the chip. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
internal class JournalEntriesOnMapPortraitTest : JournalEntriesOnMapCompactTests() {
    override val glyphX: Dp = 60.dp
    override val glyphY: Dp = 380.dp
}

/** The short landscape window, `w823dp-h384dp`; the glyphs in the middle of the map. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
internal class JournalEntriesOnMapShortLandscapeTest : JournalEntriesOnMapCompactTests() {
    override val glyphX: Dp = 420.dp
    override val glyphY: Dp = 150.dp

    /**
     * In a short landscape window the chip sits under the search bar on the punch-hole side, where the
     * icon cluster also sits by default (Landscape B2, S3 and S6), so nothing around the chip is map
     * until the cluster moves. A real drag of the cluster's handle takes it to the port side, as a
     * user does (the B2 tests' own drag).
     */
    override fun clearTheChipsSurroundings() {
        val handle = composeRule.onNodeWithTag("map-icon-bar-minimize-handle").getUnclippedBoundsInRoot()
        val start = with(composeRule.density) { Offset(((handle.left + handle.right) / 2).toPx(), ((handle.top + handle.bottom) / 2).toPx()) }
        val delta = with(composeRule.density) { Offset(400.dp.toPx(), 0f) }
        composeRule.onRoot().performTouchInput {
            down(start)
            advanceEventTime(600)
            moveTo(start + delta)
            advanceEventTime(50)
            up()
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val cluster = composeRule.onNodeWithTag(MAP_ICON_CLUSTER_TAG).getUnclippedBoundsInRoot()
        val chip = composeRule.onNodeWithTag(JOURNAL_ENTRIES_CHIP_TAG).getUnclippedBoundsInRoot()
        assertTrue("the cluster $cluster is off the chip's side now ($chip)", cluster.left > chip.right + 24.dp)
    }
}

/**
 * The wide layout (`w1280dp`, as M1's wide bubble tests: at `w840dp` the map is too narrow for a
 * bubble): the chip in the row with the taxon chip at the map's top centre, and "Open entry" into the
 * drawer's log panel. The wide map shows only once a region is searched, so a search runs first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h900dp-mdpi")
internal class JournalEntriesOnMapWideTest : JournalEntriesOnMapHarness() {
    override val glyphX: Dp = 60.dp
    override val glyphY: Dp = 300.dp

    override fun prepareAvailability(viewModel: AvailabilityViewModel) {
        viewModel.onManualLatChanged("45.5")
        viewModel.onManualLngChanged("-122.6")
        viewModel.searchManualCoordinates()
    }

    @Test
    fun `on the wide layout the chip sits at the top of the map, and touches all around it reach the map`() {
        setScreen(entryA(shown = true))
        val chip = composeRule.onNodeWithTag(JOURNAL_ENTRIES_CHIP_TAG).assertIsDisplayed().getUnclippedBoundsInRoot()
        val slot = composeRule.onNodeWithTag("map-slot").getUnclippedBoundsInRoot()
        composeRule.onNodeWithText("1 journal entry on map").assertIsDisplayed()
        assertTrue("on the map ($chip, $slot)", chip.left >= slot.left && chip.right <= slot.right && chip.top >= slot.top)
        // J6c (ruling 3): the compass strip runs across the top of the map with the chip row below it, so the chip is at
        // the top of what the strip leaves (this read "< 32.dp" from the map's own top).
        val strip = composeRule.onNodeWithTag("compass-elevation-strip").getUnclippedBoundsInRoot()
        assertTrue("below the strip, at the top of the map ($chip, $strip, $slot)", chip.top >= strip.bottom && chip.top - strip.bottom < 32.dp)
        val midY = (chip.top + chip.bottom) / 2
        val before = map.taps

        touchAt(chip.left - 12.dp, midY)
        touchAt(chip.right + 12.dp, midY)
        touchAt((chip.left + chip.right) / 2, chip.bottom + 12.dp)
        assertEquals("all three touches around the chip reached the map", before + 3, map.taps)
    }

    @Test
    fun `on the wide layout a date line opens the entry's report in the drawer's log panel`() {
        setScreen(entryA(shown = true), entryB(shown = false))
        touchCentreOf(glyphTag(FIND.id))

        touchCentreOf(mapBubbleEntryLineTag("entry-a"))

        // J6a header ruling (prompts/preserved/2026-09-29-25.md): the panel's header reads "Journal".
        composeRule.onNodeWithText("Journal").assertExists()
        assertReportShowing(ENTRY_A_TEXT)
        assertEquals("entry-a", cartographyViewModel.uiState.value.editingEntry?.id)
    }
}

// ── J8 follow-ups (dispatch 2026-09-28-70, widened by continuation 2026-09-28-87) ──

private val KEPT_PHOTO_AT = LatLng(BUBBLE_PHOTO.photo.latitude!!, BUBBLE_PHOTO.photo.longitude!!)
private val ORIGIN_WAYPOINT = Waypoint("wp-origin", 45.40, -122.70, null, "Track start", "", 1_000L, designation = WaypointDesignation.ORIGIN)
private val END_WAYPOINT = Waypoint("wp-end", 45.41, -122.71, null, "Track end", "", 1_000L, designation = WaypointDesignation.END)

/** A saved entry that keeps only [BUBBLE_PHOTO], attached to it (J8 highlights an entry's attached photos). */
private fun entryKeepingPhoto(id: String, date: LocalDate, text: String, shown: Boolean) =
    CartographyEntry.draft(id = id, date = date, updatedAtEpochMillis = 1_000L).copy(
        isDraft = false,
        text = text,
        photos = listOf(PhotoAttachment(BUBBLE_PHOTO.photo.id, attachedAtEpochMillis = 1_000L)),
        shownOnMap = shown,
    )

/**
 * The J8 follow-ups through the real [AvailabilityScreen], as the J8 tests above, in portrait at the
 * S22 Ultra's size:
 * - item 1 (`-70`; the owner, "Option B"): while a photo's bubble shows J8's keeping-entry lines, its
 *   attachment line leaves out its "Kept in" part, and is left out when nothing else is left; with no
 *   highlight, or the "Journal entries" switch off, the line is as it was;
 * - item 2 (`-87`): the rings are computed from the waypoints the map draws, so an ORIGIN or END
 *   waypoint the map does not draw gets none.
 *
 * The photo is [BUBBLE_PHOTO], drawn by the stub map below the find and the waypoint. The album's count
 * of the entries keeping it (`MushroomLogUiState.cartographyEntryPhotoReferenceCounts`, loaded by the
 * Journal's own ViewModel in the app) is set by each test to the number of saved entries it stores
 * keeping the photo, shown on the map or not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
internal class JournalEntriesOnMapFollowUpsTest : JournalEntriesOnMapHarness() {
    override val glyphX: Dp = 60.dp
    override val glyphY: Dp = 380.dp
    override val extraGlyphs: List<StubGlyph> get() = listOf(StubGlyph(MapLayerIds.PHOTOS, BUBBLE_PHOTO.photo.id, glyphX, glyphY + 96.dp, KEPT_PHOTO_AT))

    private val photoDate = formatRecordTimestamp(BUBBLE_PHOTO.photo.createdAtEpochMillis!!)
    private val day = LocalDate.of(2026, 9, 12)

    /** The Maps tab draws the photo, which [findIds] use and [entryCount] saved entries keep in all. */
    private fun drawThePhoto(findIds: List<String>, entryCount: Int) {
        screenRecords = RECORDS.copy(photoMarkers = listOf(RecordPoint(BUBBLE_PHOTO.photo.id, KEPT_PHOTO_AT)))
        screenLog = MushroomLogUiState(
            entries = listOf(FIND),
            galleryPhotos = listOf(GalleryPhoto(BUBBLE_PHOTO.photo, referencingEntryIds = findIds)),
            cartographyEntryPhotoReferenceCounts = mapOf(BUBBLE_PHOTO.photo.id to entryCount),
        )
    }

    /** On the Maps tab, a real touch on the photo's glyph: its bubble. */
    private fun openPhotoBubble() {
        touchNavItem("Maps")
        touchCentreOf(glyphTag(BUBBLE_PHOTO.photo.id))
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
    }

    /** Every line of text on the bubble, top to bottom, as drawn (each [Text] once). */
    private fun bubbleTexts(): List<String> =
        composeRule.onAllNodes(hasAnyAncestor(hasTestTag(MAP_BUBBLE_TAG)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    // ── Item 1: one "Kept in" ──

    @Test
    fun `a highlighted photo in a find, kept by up to three shown entries, says In the find and its date lines, with no Kept in`() {
        drawThePhoto(findIds = listOf(FIND.id), entryCount = 2)
        setScreen(
            entryKeepingPhoto("entry-a", day, "Shown on the map.", shown = true),
            entryKeepingPhoto("entry-b", day.minusDays(7), "Not shown.", shown = false),
        )

        openPhotoBubble()

        assertEquals(listOf(photoDate, "In Golden chanterelle", "2026-09-12", "View photo"), bubbleTexts())
    }

    @Test
    fun `a highlighted photo kept by more than three shown entries has exactly one Kept in line, the tappable count`() {
        drawThePhoto(findIds = listOf(FIND.id), entryCount = 5)
        setScreen(
            entryKeepingPhoto("entry-1", day, "First.", shown = true),
            entryKeepingPhoto("entry-2", day.minusDays(1), "Second.", shown = true),
            entryKeepingPhoto("entry-3", day.minusDays(2), "Third.", shown = true),
            entryKeepingPhoto("entry-4", day.minusDays(3), "Fourth.", shown = true),
            entryKeepingPhoto("entry-5", day.minusDays(4), "Not shown.", shown = false),
        )

        openPhotoBubble()

        val texts = bubbleTexts()
        assertEquals("one Kept in line on the bubble: $texts", listOf("Kept in 4 journal entries"), texts.filter { "Kept in" in it })
        composeRule.onNodeWithTag(MAP_BUBBLE_ENTRY_COUNT_TAG).assertTextEquals("Kept in 4 journal entries")
        assertEquals(listOf(photoDate, "In Golden chanterelle", "Kept in 4 journal entries", "View photo"), texts)
    }

    @Test
    fun `a highlighted photo in no find has no attachment line, and never says it is not in a journal entry`() {
        drawThePhoto(findIds = emptyList(), entryCount = 1)
        setScreen(entryKeepingPhoto("entry-a", day, "Shown on the map.", shown = true))

        openPhotoBubble()

        val texts = bubbleTexts()
        assertEquals("never the not-attached line: $texts", emptyList<String>(), texts.filter { it == "Not in a find or a journal entry" })
        assertEquals(listOf(photoDate, "2026-09-12", "View photo"), texts)
    }

    /**
     * "Not highlighted: exactly as today" is pinned here, on the way to the highlighted case, so this
     * test fails at base on the highlighted half: the not-highlighted half cannot, since it pins a line
     * this item leaves as it was.
     */
    @Test
    fun `a photo not highlighted keeps its whole line, and once its entry is shown on the map the line drops its Kept in`() {
        drawThePhoto(findIds = listOf(FIND.id), entryCount = 1)
        setScreen(entryKeepingPhoto("entry-a", day, "Shown later.", shown = false))
        openPhotoBubble()
        assertEquals("not highlighted: as it was", listOf(photoDate, "In Golden chanterelle · Kept in 1 journal entry", "View photo"), bubbleTexts())

        // Shown on the map from its report, as a user does it (J8-3).
        openEntryReport("entry-a", "Shown later.")
        openEntryMenu()
        touchCentreOf(composeRule.onNodeWithText("Show on map"))
        assertEquals(true, stored("entry-a").shownOnMap)
        openPhotoBubble()

        assertEquals("highlighted", listOf(photoDate, "In Golden chanterelle", "2026-09-12", "View photo"), bubbleTexts())
    }

    /**
     * "Switch off: exactly as today", pinned on the way to the switch being turned on, for the reason the
     * test above gives. The switch starts off as a stored choice, restored as every overlay switch is,
     * and is turned on in the Layers sheet with the bubble open, which re-reads its lines.
     */
    @Test
    fun `with the Journal entries switch off the photo keeps its whole line, and turned on the line drops its Kept in`() {
        layerPreferences.stored = MapLayerPreferences.NONE.copy(visibility = mapOf(JOURNAL_ENTRIES_SWITCH_LAYER_ID to false))
        drawThePhoto(findIds = listOf(FIND.id), entryCount = 2)
        setScreen(
            entryKeepingPhoto("entry-a", day, "Shown on the map.", shown = true),
            entryKeepingPhoto("entry-b", day.minusDays(7), "Not shown.", shown = false),
        )
        openPhotoBubble()
        assertEquals("switch off: as it was", listOf(photoDate, "In Golden chanterelle · Kept in 2 journal entries", "View photo"), bubbleTexts())

        touchCentreOf(composeRule.onNodeWithContentDescription(LAYERS_ROW_DESCRIPTION))
        touchCentreOf(composeRule.onNodeWithText("Journal entries").performScrollTo())

        assertTrue("the switch is on", "visible $JOURNAL_ENTRIES_SWITCH_LAYER_ID true" in layerPreferences.writes)
        assertEquals("switch on", listOf(photoDate, "In Golden chanterelle", "2026-09-12", "View photo"), bubbleTexts())
    }

    // ── Item 2: no ring on a waypoint the map does not draw ──

    @Test
    fun `an ORIGIN and an END waypoint kept by a shown entry get no ring, and an ordinary kept waypoint still does`() {
        screenWaypoints = listOf(WAYPOINT, ORIGIN_WAYPOINT, END_WAYPOINT)
        setScreen(
            CartographyEntry.draft(id = "entry-a", date = day, updatedAtEpochMillis = 1_000L).copy(
                isDraft = false,
                text = "Kept all three.",
                waypointDecisions = screenWaypoints.map { WaypointDecision(waypointId = it.id, name = it.name, lat = it.lat, lng = it.lng, kept = true) },
                shownOnMap = true,
            ),
        )
        touchNavItem("Maps")

        val content = map.content!!
        assertEquals("the map draws the ordinary waypoint alone (not navigating)", listOf(WAYPOINT.id), content.waypoints.map { it.id })
        assertEquals("rings on the waypoints the map draws", listOf(WAYPOINT.id), content.journalHighlights.waypointMarkers.map { it.recordId })
        val ringSource = journalHighlightFeatureCollections(content.journalHighlights).getValue(MapSourceIds.JOURNAL_ENTRY_WAYPOINTS)
        assertEquals("what the ring layer's source is fed", listOf(WAYPOINT.id), ringSource.features()!!.map { it.getStringProperty(FEATURE_ID_PROPERTY) })
        assertEquals(
            "keeping-entry lines for the drawn waypoint only",
            listOf(HighlightedRecord(HighlightedRecordKind.WAYPOINT, WAYPOINT.id)),
            content.journalHighlights.keptIn.keys.filter { it.kind == HighlightedRecordKind.WAYPOINT },
        )
    }
}
