package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.data.repository.RoomCartographyEntryRepository
import com.zynergylabs.forager.app.data.repository.RoomMushroomLogRepository
import com.zynergylabs.forager.app.data.repository.RoomOfflineRegionDayIndex
import com.zynergylabs.forager.app.data.repository.RoomTrackRepository
import com.zynergylabs.forager.app.data.repository.RoomWaypointRepository
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.CommitCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.ComputeTrackStatisticsUseCase
import com.zynergylabs.forager.app.domain.CreateCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.DeleteCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.EntryGroup
import com.zynergylabs.forager.app.domain.GetCartographyDraftEntriesUseCase
import com.zynergylabs.forager.app.domain.GetCartographyEntriesUseCase
import com.zynergylabs.forager.app.domain.GetCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.GetDerivedTripUseCase
import com.zynergylabs.forager.app.domain.GetTripReportOfflineRegionsUseCase
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.SaveCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.SetCartographyEntryShownOnMapUseCase
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
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
import org.robolectric.shadows.ShadowLooper

/**
 * Data part A (dispatch 2026-09-28-667) on screen. The editor's "In this entry" panel (the owner in
 * RECORD -656: "Summary first, open to adjust (Recommended)" and "'Leave out' (Recommended)") and the
 * entry report's tiles, height profile and waypoint table ("Summary tiles + height profile
 * (Recommended)"), driven through the real [CartographyScreen] (in its editor, or its report for a
 * saved entry) and the real [CartographyViewModel] over an in-memory
 * [ForagerDatabase]. Every touch on a group or a switch is a coordinate touch (`performTouchInput`), at
 * several points across the target where the claim is that a finger there reaches it (CLAUDE.md,
 * Testing). Every data assertion reads the ViewModel's entry or the database.
 *
 * The day: one recorded walk ("Ridge Loop") with its Start, its End and a waypoint dropped while it
 * recorded ("Big fir"), a waypoint dropped with no recording ("Creek pin"), and a find. The entry is a
 * draft, so every change is stored at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class EntryDataScreensTest {

    private val composeRule = createComposeRule()

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
    private lateinit var viewModel: CartographyViewModel
    private lateinit var entryRepository: RoomCartographyEntryRepository

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    private fun dayStartMillis(): Long = DAY.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** [mode] EDIT shows the draft in its editor; VIEW finishes it first and shows its report. [heights] `false` records the walk with no altitudes. */
    /** The entry map's render mode, as the screen last handed it to the map slot; `null` while no map is drawn. */
    private var capturedRenderMode: MapRenderMode? = null

    private fun setScreen(mode: CartographyEntryMode = CartographyEntryMode.EDIT, heights: Boolean = true, mapData: CartographyEntryMapData = EMPTY_MAP_DATA) {
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, ForagerDatabase::class.java)
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .allowMainThreadQueries()
            .build()
        entryRepository = RoomCartographyEntryRepository(database.cartographyEntryDao())
        val logRepository = RoomMushroomLogRepository(database.mushroomLogDao())
        val trackRepository = RoomTrackRepository(database.trackDao())
        val waypointRepository = RoomWaypointRepository(database.waypointDao())
        val t0 = dayStartMillis() + 9 * 3_600_000L
        runBlocking {
            trackRepository.create(Track(id = TRACK_ID, name = "Ridge Loop", startedAtEpochMillis = t0, endedAtEpochMillis = t0 + 110_000L, points = emptyList())).getOrThrow()
            trackRepository.appendPoints(
                TRACK_ID,
                (0 until 12).map { i -> TrackPoint(lat = 45.0 + 0.001 * i, lng = -122.0, altitude = if (heights) 100.0 + 5.0 * i else null, accuracyMeters = 5f, timestampEpochMillis = t0 + i * 10_000L) },
            ).getOrThrow()
            trackRepository.end(TRACK_ID, t0 + 110_000L).getOrThrow()
            waypointRepository.save(Waypoint(id = START_ID, lat = 45.0, lng = -122.0, altitude = null, name = "Start", note = "", createdAtEpochMillis = t0, trackId = TRACK_ID, designation = WaypointDesignation.ORIGIN)).getOrThrow()
            waypointRepository.save(Waypoint(id = DROPPED_ID, lat = 45.005, lng = -122.0, altitude = null, name = "Big fir", note = "", createdAtEpochMillis = t0 + 50_000L, trackId = TRACK_ID)).getOrThrow()
            waypointRepository.save(Waypoint(id = END_ID, lat = 45.011, lng = -122.0, altitude = null, name = "End", note = "", createdAtEpochMillis = t0 + 110_000L, trackId = TRACK_ID, designation = WaypointDesignation.END)).getOrThrow()
            waypointRepository.save(Waypoint(id = LOOSE_ID, lat = 45.2, lng = -122.2, altitude = null, name = "Creek pin", note = "", createdAtEpochMillis = t0 + 3_600_000L)).getOrThrow()
            logRepository.save(MushroomLogEntry.draft(id = FIND_ID, location = LatLng(45.5, -122.6), date = DAY).copy(isDraft = false, ownIdentification = "Chanterelle")).getOrThrow()
        }
        viewModel = CartographyViewModel(
            getEntries = GetCartographyEntriesUseCase(entryRepository),
            getDraftEntries = GetCartographyDraftEntriesUseCase(entryRepository),
            createEntry = CreateCartographyEntryUseCase(entryRepository, now = { 1_000L }, idGenerator = { ENTRY_ID }),
            saveEntry = SaveCartographyEntryUseCase(entryRepository, now = { 1_000L }),
            getEntry = GetCartographyEntryUseCase(entryRepository),
            commitEntry = CommitCartographyEntryUseCase(entryRepository, now = { 1_000L }),
            deleteEntry = DeleteCartographyEntryUseCase(entryRepository),
            getDerivedTrip = GetDerivedTripUseCase(
                mushroomLogRepository = logRepository,
                trackRepository = trackRepository,
                waypointRepository = waypointRepository,
                offlineRegionDayIndex = RoomOfflineRegionDayIndex(database.offlineRegionDao()),
            ),
            getTripReportOfflineRegions = GetTripReportOfflineRegionsUseCase(PanelNoRegionsRepository),
            computeTrackStatistics = ComputeTrackStatisticsUseCase(),
            setShownOnMap = SetCartographyEntryShownOnMapUseCase(entryRepository),
            now = { 1_000L },
        )
        viewModel.onStartEntry(DAY)
        ShadowLooper.idleMainLooper()
        if (mode == CartographyEntryMode.VIEW) {
            viewModel.onFinishEntry()
            ShadowLooper.idleMainLooper()
            assertTrue("the entry is saved, so it opens in its report", !entry().isDraft)
        }
        composeRule.setContent {
            val uiState by viewModel.uiState.collectAsState()
            CartographyScreen(
                uiState = uiState,
                galleryPhotos = emptyList(),
                isLoadingGalleryPhotos = false,
                galleryLoadErrorMessage = null,
                galleryPhotoEntryReferenceCounts = emptyMap(),
                onDeleteGalleryPhoto = {},
                onOpenCameraForAlbum = {},
                onOpenCameraForEntry = {},
                onAddGalleryPhoto = {},
                distanceUnit = DistanceUnit.MILES,
                mapSlot = { _, _, renderMode, _, _, _, _, _, _ -> capturedRenderMode = renderMode },
                night = false,
                getMapData = { _, _ -> mapData },
                getCoveringOfflineRegion = { _, _ -> null },
                getCurrentLocation = { LocationResult.LocationUnavailable },
                onOpenEntry = viewModel::onOpenEntry,
                onStartEntry = viewModel::onStartEntry,
                onCloseEntry = viewModel::onCloseEntry,
                onTextChanged = viewModel::onTextChanged,
                onTagsChanged = viewModel::onTagsChanged,
                onSetFindDecision = viewModel::onSetFindDecision,
                onSetTrackDecision = viewModel::onSetTrackDecision,
                onSetWaypointDecision = viewModel::onSetWaypointDecision,
                onSetOfflineRegionDecision = viewModel::onSetOfflineRegionDecision,
                onToggleKeptPhoto = viewModel::onToggleKeptPhoto,
                onAcquirePhotoForEntry = {},
                onFinishEntry = viewModel::onFinishEntry,
                onSaveEntry = viewModel::onSaveEntry,
                onDiscardEntryChanges = viewModel::onDiscardEntryChanges,
                onSaveEntryAsDraft = viewModel::onSaveEntryAsDraft,
                onDeleteEntry = viewModel::onDeleteEntry,
                entryModeState = remember { mutableStateOf(mode) },
                onSetEntryGroupIncluded = viewModel::onSetEntryGroupIncluded,
            )
        }
        composeRule.waitForIdle()
        if (mode == CartographyEntryMode.EDIT) {
            composeRule.onNodeWithText("Your own account (optional)").assertIsDisplayed()
        } else {
            composeRule.onNodeWithText("Your own account (optional)").assertDoesNotExist()
        }
    }

    private fun entry(): CartographyEntry = viewModel.uiState.value.editingEntry!!
    private fun stored(): CartographyEntry = runBlocking { entryRepository.getById(ENTRY_ID).getOrThrow()!! }

    private fun kept(entry: CartographyEntry): Map<String, Boolean> =
        entry.trackDecisions.associate { it.trackId to it.kept } +
            entry.waypointDecisions.associate { it.waypointId to it.kept } +
            entry.findDecisions.associate { it.findId to it.kept }

    /** A touch on a group's row at a fraction of its width, away from its switch at the far end. */
    private fun touchGroupRow(group: EntryGroup, xFraction: Float) {
        composeRule.onNodeWithTag(entryGroupRowTag(group)).performScrollTo().performTouchInput { click(Offset(width * xFraction, height / 2f)) }
        composeRule.waitForIdle()
    }

    /** Points across a switch's own bounds: its centre, and in from each edge. */
    private val switchSamples = listOf(0.5f to 0.5f, 0.2f to 0.5f, 0.8f to 0.5f, 0.5f to 0.25f, 0.5f to 0.75f)

    @Test
    fun `the panel opens on its summary, with its groups closed and the owner's words`() {
        setScreen()

        composeRule.onNodeWithTag(ENTRY_SUMMARY_LINE_TAG).performScrollTo().assert(hasText("In this entry: 1 track, 2 waypoints, 1 find"))
        composeRule.onNodeWithTag(entryGroupRowTag(EntryGroup.TRACKS)).performScrollTo().assert(hasText("All included"))
        composeRule.onNodeWithTag(entryGroupRowTag(EntryGroup.WAYPOINTS)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(entryGroupRowTag(EntryGroup.FINDS)).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Offline maps").assertCountEquals(0)
        // Closed: no item rows yet.
        composeRule.onAllNodesWithText("Ridge Loop").assertCountEquals(0)
        composeRule.onAllNodesWithText("Big fir").assertCountEquals(0)
        // The old words are gone.
        composeRule.onAllNodesWithText("Withhold", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Keep").assertCountEquals(0)
    }

    /**
     * A touch on the Tracks row (not its switch) opens it, with the walk's Start, dropped waypoint and
     * End indented beneath the walk in the order they were made; the next touch closes it. Three
     * touches across the row's width, each toggling.
     */
    @Test
    fun `a touch anywhere on a group row opens and closes it, with a track's waypoints indented under it`() {
        setScreen()

        touchGroupRow(EntryGroup.TRACKS, 0.1f)
        val trackRow = composeRule.onNodeWithTag(entryItemRowTag(trackItemKey(TRACK_ID))).performScrollTo().getUnclippedBoundsInRoot()
        val rows = listOf(START_ID, DROPPED_ID, END_ID).map { composeRule.onNodeWithTag(entryItemRowTag(waypointItemKey(it))).performScrollTo().getUnclippedBoundsInRoot() }
        rows.forEach { assertTrue("a waypoint row $it is indented past the track row $trackRow", it.left > trackRow.left) }
        assertTrue("Start, Big fir, End in that order, under the track", trackRow.top < rows[0].top && rows[0].top < rows[1].top && rows[1].top < rows[2].top)
        composeRule.onNodeWithText("Ridge Loop").assertIsDisplayed()
        // 11 steps of 0.001° = 1 223 m = 0.76 mi, over 110 s: each figure with its unit.
        composeRule.onNodeWithTag(entryItemRowTag(trackItemKey(TRACK_ID))).assert(hasAnyDescendant(hasText("0.8 mi · 1 min")))
        // The loose waypoint is not under the walk.
        composeRule.onAllNodesWithText("Creek pin").assertCountEquals(0)

        touchGroupRow(EntryGroup.TRACKS, 0.4f)
        composeRule.onAllNodesWithText("Ridge Loop").assertCountEquals(0)

        touchGroupRow(EntryGroup.TRACKS, 0.7f)
        composeRule.onNodeWithText("Ridge Loop").assertIsDisplayed()
    }

    /**
     * Touches across the Tracks switch flip the whole group each time: the walk, its Start, its End and
     * the waypoint dropped on it, while the loose waypoint and the find stay included. Read from the
     * ViewModel, from the database (a draft stores at once), and from the switch itself.
     */
    @Test
    fun `touches across a group's switch include or leave out everything in the group, and only that`() {
        setScreen()
        val switch = composeRule.onNodeWithTag(entryGroupSwitchTag(EntryGroup.TRACKS)).performScrollTo()
        switch.assertIsOn()

        switchSamples.forEachIndexed { index, (fx, fy) ->
            composeRule.onNodeWithTag(entryGroupSwitchTag(EntryGroup.TRACKS)).performTouchInput { click(Offset(width * fx, height * fy)) }
            composeRule.waitForIdle()
            val included = index % 2 == 1
            val expected = mapOf(
                TRACK_ID to included, START_ID to included, DROPPED_ID to included, END_ID to included,
                LOOSE_ID to true, FIND_ID to true,
            )
            assertEquals("touch $index at ($fx, $fy)", expected, kept(entry()))
            assertEquals("touch $index stored", expected, kept(stored()))
            if (included) {
                composeRule.onNodeWithTag(entryGroupSwitchTag(EntryGroup.TRACKS)).assertIsOn()
            } else {
                composeRule.onNodeWithTag(entryGroupSwitchTag(EntryGroup.TRACKS)).assertIsOff()
                composeRule.onNodeWithTag(entryGroupRowTag(EntryGroup.TRACKS)).assert(hasText("All left out"))
                composeRule.onNodeWithTag(ENTRY_SUMMARY_LINE_TAG).assert(hasText("In this entry: 1 waypoint, 1 find"))
            }
        }
    }

    /** One item's switch changes that item only, and the group's line and the summary say so. */
    @Test
    fun `touches across an item's switch change that item only`() {
        setScreen()
        touchGroupRow(EntryGroup.TRACKS, 0.2f)
        val tag = entryItemSwitchTag(waypointItemKey(DROPPED_ID))
        composeRule.onNodeWithTag(tag).performScrollTo().assertIsOn()

        switchSamples.forEachIndexed { index, (fx, fy) ->
            composeRule.onNodeWithTag(tag).performScrollTo().performTouchInput { click(Offset(width * fx, height * fy)) }
            composeRule.waitForIdle()
            val included = index % 2 == 1
            val expected = mapOf(TRACK_ID to true, START_ID to true, DROPPED_ID to included, END_ID to true, LOOSE_ID to true, FIND_ID to true)
            assertEquals("touch $index at ($fx, $fy)", expected, kept(stored()))
            composeRule.onNodeWithTag(entryGroupRowTag(EntryGroup.TRACKS)).assert(hasText(if (included) "All included" else "Some left out"))
            composeRule.onNodeWithTag(ENTRY_SUMMARY_LINE_TAG).assert(hasText(if (included) "In this entry: 1 track, 2 waypoints, 1 find" else "In this entry: 1 track, 1 waypoint, 1 find"))
        }
    }

    /**
     * A waypoint added to the day after the entry was started is new: flagged at the top under the
     * heading, with Include and Leave out, and not counted until chosen. Include records it included and
     * it leaves the top.
     */
    @Test
    fun `an item new since the last save is flagged at the top, and Include settles it`() {
        setScreen()
        addLaterWaypointAndReopen()

        composeRule.onNodeWithText(NEW_ITEMS_HEADING).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(entryItemRowTag(waypointItemKey(LATER_ID))).performScrollTo().assert(hasAnyDescendant(hasText("Later pin"))).assert(hasAnyDescendant(hasText("Waypoint")))
        composeRule.onNodeWithTag(ENTRY_SUMMARY_LINE_TAG).assert(hasText("In this entry: 1 track, 2 waypoints, 1 find"))

        composeRule.onNodeWithTag(entryNewIncludeTag(waypointItemKey(LATER_ID))).performScrollTo().performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals(true, stored().waypointDecisions.single { it.waypointId == LATER_ID }.kept)
        composeRule.onAllNodesWithText(NEW_ITEMS_HEADING).assertCountEquals(0)
        composeRule.onNodeWithTag(ENTRY_SUMMARY_LINE_TAG).assert(hasText("In this entry: 1 track, 3 waypoints, 1 find"))
    }

    /** Leave out on a new item records it left out; it then sits in its group with its switch off. */
    @Test
    fun `Leave out on a new item records it left out, in its group`() {
        setScreen()
        addLaterWaypointAndReopen()

        composeRule.onNodeWithTag(entryNewLeaveOutTag(waypointItemKey(LATER_ID))).performScrollTo().performTouchInput { click() }
        composeRule.waitForIdle()

        assertEquals(false, stored().waypointDecisions.single { it.waypointId == LATER_ID }.kept)
        composeRule.onAllNodesWithText(NEW_ITEMS_HEADING).assertCountEquals(0)
        touchGroupRow(EntryGroup.WAYPOINTS, 0.3f)
        composeRule.onNodeWithTag(entryItemSwitchTag(waypointItemKey(LATER_ID))).performScrollTo().assertIsOff()
        composeRule.onNodeWithTag(entryItemSwitchTag(waypointItemKey(LOOSE_ID))).performScrollTo().assertIsOn()
        composeRule.onNodeWithTag(entryGroupRowTag(EntryGroup.WAYPOINTS)).assert(hasText("Some left out"))
    }

    // ── The report ──

    /**
     * The saved walk's report: the date as "Aug 1, 2026", then Distance, Time out, Climb and Finds, each with its
     * unit, then the height chart's labels. Figures by hand: 11 steps of 0.001° = 1 223 m = 0.8 mi over 110 s;
     * heights 100 m to 155 m in 5 m steps gain 55 m = 180 ft, lowest 328 ft, highest 509 ft. The walk carries no
     * pressure, so its heights are smoothed GPS (trackHeights), and a steady climb comes through smoothing unchanged.
     * The chart was hidden from part 2 until barometer height landed in part 5 (RECORD -864, "Keep it, once height is
     * fixed"); these chart assertions are the ones part 2 inverted, restored.
     */
    @Test
    fun `the report shows labelled tiles and the height chart's labels`() {
        setScreen(mode = CartographyEntryMode.VIEW)

        composeRule.onNodeWithText("Aug 1, 2026").assertIsDisplayed()
        mapOf(TILE_DISTANCE to "0.8 mi", TILE_TIME_OUT to "1 min", TILE_CLIMB to "180 ft", TILE_FINDS to "1").forEach { (label, value) ->
            composeRule.onNodeWithTag(entryTileTag(label)).performScrollTo().assert(hasText(label)).assert(hasText(value))
        }
        composeRule.onNodeWithTag(ENTRY_HEIGHT_PROFILE_TAG).performScrollTo().assertIsDisplayed()
        listOf(PROFILE_TITLE, "509 ft", "328 ft", PROFILE_START_LABEL).forEach { label ->
            composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(ENTRY_HEIGHT_PROFILE_TAG))).assertExists()
        }
        composeRule.onNode(hasText("0.8 mi") and hasAnyAncestor(hasTestTag(ENTRY_HEIGHT_PROFILE_TAG))).assertExists()
        composeRule.onAllNodesWithTag(ENTRY_HEIGHT_PROFILE_LINE_TAG).assertCountEquals(0)
        // The owner's "0 ft · 0m": no minutes written as "m" anywhere on the report.
        composeRule.onAllNodesWithText("0m", substring = true).assertCountEquals(0)
    }

    /**
     * RECORD -867 (the owner: "Keep notes, tags, photos and the toggle"): the page under the map is the tiles. The
     * walk, its waypoints and the find are all included, and none of them is listed: no "Tracks" or "Waypoints"
     * heading, no track name, no waypoint name, no find line, no table header. Until 2026-10-11 the report drew all
     * of them; the tests that read the waypoint table's rows, its coordinate taps and the track rows' touches are
     * removed with them (named in the part-2 report).
     */
    @Test
    fun `the report lists no tracks, finds or waypoints, though all are included`() {
        setScreen(mode = CartographyEntryMode.VIEW)
        assertTrue("the walk, its waypoints and the find are included", kept(entry()).values.all { it } && kept(entry()).size == 6)

        composeRule.onNodeWithTag(entryTileTag(TILE_FINDS)).performScrollTo().assert(hasText("1"))
        listOf("Tracks", "Waypoints", "Ridge Loop", "Big fir", "Creek pin", "Find on Aug 1, 2026", "Chanterelle", "From start").forEach { text ->
            composeRule.onAllNodesWithText(text).assertCountEquals(0)
        }
    }

    /**
     * RECORD -867 ("Add it to the icon's details"): a tap on a waypoint on the entry's map opens its bubble, which now
     * gives the distance walked from the start of its walk, the figure the removed table had. Big fir was dropped
     * 50 s in, five steps along: 556 m = 0.3 mi. Creek pin is on no walk, so its bubble has no such line. Driven
     * through the map's own feature-tap hook, as the map's tap handler calls it.
     */
    @Test
    fun `a waypoint tapped on the entry map gives the distance walked from the start`() {
        setScreen(
            mode = CartographyEntryMode.VIEW,
            mapData = EMPTY_MAP_DATA.copy(
                waypointMarkers = listOf(RecordPoint(DROPPED_ID, LatLng(45.005, -122.0)), RecordPoint(LOOSE_ID, LatLng(45.2, -122.2))),
            ),
        )
        fun tap(id: String, at: LatLng) {
            val mode = checkNotNull(capturedRenderMode) { "the entry map was drawn" }
            composeRule.runOnUiThread { mode.onFeatureTap(MapFeatureTap(MapLayerIds.WAYPOINTS, id, Offset(100f, 100f), 0f, at)) }
            composeRule.waitForIdle()
        }

        tap(DROPPED_ID, LatLng(45.005, -122.0))
        composeRule.onNode(hasText("Big fir") and hasAnyAncestor(hasTestTag(MAP_BUBBLE_TAG))).assertExists()
        composeRule.onNode(hasText("0.3 mi walked from the start") and hasAnyAncestor(hasTestTag(MAP_BUBBLE_TAG))).assertExists()

        composeRule.runOnUiThread { checkNotNull(capturedRenderMode).onCloseBubble() }
        composeRule.waitForIdle()
        tap(LOOSE_ID, LatLng(45.2, -122.2))
        composeRule.onNode(hasText("Creek pin") and hasAnyAncestor(hasTestTag(MAP_BUBBLE_TAG))).assertExists()
        composeRule.onAllNodesWithText("walked from the start", substring = true).assertCountEquals(0)
    }

    /** A walk recorded with no heights draws no chart: one plain line says why, and Climb says it was not recorded (restored in part 5, with the chart). */
    @Test
    fun `a walk with no heights says so in one line instead of drawing a chart`() {
        setScreen(mode = CartographyEntryMode.VIEW, heights = false)

        composeRule.onNodeWithTag(ENTRY_HEIGHT_PROFILE_LINE_TAG).performScrollTo().assert(hasText(PROFILE_TOO_FEW_HEIGHTS_LINE))
        composeRule.onAllNodesWithTag(ENTRY_HEIGHT_PROFILE_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag(entryTileTag(TILE_CLIMB)).performScrollTo().assert(hasText(CLIMB_NOT_RECORDED))
    }

    private fun addLaterWaypointAndReopen() {
        runBlocking {
            RoomWaypointRepository(database.waypointDao()).save(
                Waypoint(id = LATER_ID, lat = 45.3, lng = -122.3, altitude = null, name = "Later pin", note = "", createdAtEpochMillis = dayStartMillis() + 15 * 3_600_000L),
            ).getOrThrow()
        }
        composeRule.runOnIdle {
            viewModel.onCloseEntry()
            viewModel.onOpenEntry(ENTRY_ID)
        }
        composeRule.waitForIdle()
        assertTrue("the later waypoint has no decision yet", entry().waypointDecisions.none { it.waypointId == LATER_ID })
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 8, 1)
        const val ENTRY_ID = "entry-panel"
        const val TRACK_ID = "track-ridge"
        const val START_ID = "wp-start"
        const val DROPPED_ID = "wp-fir"
        const val END_ID = "wp-end"
        const val LOOSE_ID = "wp-creek"
        const val LATER_ID = "wp-later"
        const val FIND_ID = "find-1"
        val EMPTY_MAP_DATA = CartographyEntryMapData(
            trackPolylines = emptyList(),
            findMarkers = emptyList(),
            waypointMarkers = emptyList(),
            photoMarkers = emptyList(),
            offlineRegionCircles = emptyList(),
        )
    }
}

/** No offline regions: this test's day has none. */
private object PanelNoRegionsRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (downloaded: Int, total: Int) -> Unit): Result<OfflineRegionSummary> =
        error("not used by this test")

    override suspend fun deleteRegion(id: Long): Result<Unit> = error("not used by this test")

    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}
