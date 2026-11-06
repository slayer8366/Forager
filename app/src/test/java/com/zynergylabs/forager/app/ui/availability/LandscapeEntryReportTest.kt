package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.zynergylabs.forager.app.ui.adaptive.FixedWindowInfo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.FindDecision
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.ui.log.CARTOGRAPHY_MAP_TEST_TAG
import com.zynergylabs.forager.app.ui.log.ENTRY_REPORT_INFO_TAG
import com.zynergylabs.forager.app.ui.log.ENTRY_REPORT_TAG
import com.zynergylabs.forager.app.ui.log.TILE_FINDS
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.log.OFFLINE_TOGGLE_TEST_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.MapCameraRequest
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import java.time.LocalDate
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
 * The day entry report in a landscape window. Dispatch 2026-10-18-01 (RECORD -1258; the owner, S22 check group 1 item 2: "map
 * is squished in landscape. reduce the size to a 4:3 box, and have it on one side with the info on the other. I propose the
 * map on the left with the info on the right"; Q1, RECORD -1260: "Largest that leaves info ≥240 dp (Recommended)"): the map on
 * the left in a 4:3 box, the largest that fits the height and leaves the info column at least 240 dp; the header pinned at the
 * top of the right side, and under it the stats, the offline switch and the notes, scrolling on their own.
 *
 * This replaces L2 (dispatch `prompts/preserved/2026-09-28-47.md`), whose tests asserted the header above the map, the switch
 * below it and the map capped at 40% of a short window's height: an owner change of behaviour, so those assertions are
 * replaced here, not weakened. L2's real-touch checks on Back and the switch stay, at their new places. Short portrait windows
 * keep L2's cap (pinned below).
 *
 * Driven through the real [AvailabilityScreen], Journal tab, with a committed entry open, a kept find and waypoint that the map
 * frames, and a covering offline region so the offline switch shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class LandscapeEntryReportTest {

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

    private val entry = CartographyEntry.draft(id = "entry-l2", date = L2_DAY, updatedAtEpochMillis = 1_000L).copy(
        isDraft = false,
        text = "A wet morning on the ridge.",
        findDecisions = listOf(FindDecision("find-1", L2_DAY, "Golden chanterelle", hasPhotos = false, kept = true)),
        waypointDecisions = listOf(WaypointDecision("wp-1", "Old gate", 45.4, -122.5, kept = true)),
    )
    private val mapData = CartographyEntryMapData(
        trackPolylines = emptyList(),
        findMarkers = listOf(RecordPoint("find-1", LatLng(45.51, -122.61))),
        waypointMarkers = listOf(RecordPoint("wp-1", LatLng(45.4, -122.5))),
        photoMarkers = emptyList(),
        offlineRegionCircles = emptyList(),
    )
    private val region = OfflineRegionSummary(
        id = 7L, name = "Ridge", region = Region(45.45, -122.55, 10), minZoom = 0.0, maxZoom = 15.0,
        tileCount = 100, sizeBytes = 1_000_000L, createdAtEpochMillis = 0L,
    )
    private var editing by mutableStateOf<CartographyEntry?>(entry)
    private var closes = 0

    private val bubbles = BubbleMapSlot(listOf(StubGlyph(MapLayerIds.WAYPOINTS, "wp-1", 60.dp, 60.dp, LatLng(45.4, -122.5))))
    private val requests = mutableListOf<MapCameraRequest?>()
    private val recordingSlot: MapSlot = { region, content, renderMode, focusOverride, onLongPress, onTap, onSightingTap, onCameraIdle, modifier ->
        requests += renderMode.cameraRequest
        bubbles.slot(region, content, renderMode, focusOverride, onLongPress, onTap, onSightingTap, onCameraIdle, modifier)
    }

    /** [window], when given, is the window's own size (lint dispatch 2026-10-17-03: the screen reads the window, not the configuration). */
    private fun setScreen(window: DpSize? = null) {
        composeRule.setContent {
            val windowInfo = window?.let { FixedWindowInfo(LocalWindowInfo.current, it, LocalDensity.current.density) } ?: LocalWindowInfo.current
            CompositionLocalProvider(LocalWindowInfo provides windowInfo) {
            AvailabilityScreen(
                uiState = AvailabilityUiState(),
                searchInputs = AvailabilityScreenSearchInputs(
                    onUseCurrentLocation = {},
                    onManualLatChanged = {},
                    onManualLngChanged = {},
                    onSearchManualCoordinates = {},
                    onRadiusChanged = {},
                    onMonthSelected = {},
                    onMapTabSelected = {},
                    onSeasonalTabSelected = {},
                    onTaxonSearchQueryChanged = {},
                    onTaxonSearchResultSelected = {},
                    onDismissTaxonSuggestions = {},
                    onReopenTaxonSuggestions = {},
                    onPlaceTripPin = { _, _, _ -> },
                    onDeletePlannedTrip = {},
                    onRecentSearchSelected = {},
                ),
                offlineMaps = AvailabilityScreenOfflineMaps(
                    onOfflineMapLatChanged = {},
                    onOfflineMapLngChanged = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                ),
                settings = AvailabilityScreenSettings(
                    onNightModeMapsChanged = {},
                    onThemeModeChanged = {},
                ),
                mapSlot = recordingSlot,
                journalEntries = AvailabilityScreenJournalEntries(
                    cartographyUiState = CartographyUiState(entries = listOf(entry), editingEntry = editing),
                    onCloseCartographyEntry = { closes++; editing = null },
                ),
                entryReportData = AvailabilityScreenEntryReportData(
                    getCartographyEntryMapData = { _, _ -> mapData },
                    getCartographyEntryOfflineRegion = { _, _ -> region },
                ),
            )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("A wet morning on the ridge.").assertExists()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun root(): DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()
    private fun back() = composeRule.onNodeWithContentDescription("Back to Cartography")
    private fun options() = composeRule.onNodeWithContentDescription("Entry options")

    private fun touch(rect: DpRect, fx: Float, fy: Float) {
        val x = rect.left + (rect.right - rect.left) * fx
        val y = rect.top + (rect.bottom - rect.top) * fy
        composeRule.onRoot().performTouchInput { click(Offset(x.value * density, y.value * density)) }
        composeRule.waitForIdle()
    }

    private val fractions = listOf(0.5f to 0.5f, 0.2f to 0.2f, 0.8f to 0.2f, 0.2f to 0.8f, 0.8f to 0.8f)

    private fun info(): DpRect = bounds(ENTRY_REPORT_INFO_TAG)

    /** The split's geometry, in any landscape window: 4:3, on the left, the largest that leaves the info column 240 dp. */
    private fun assertSplit() {
        val report = bounds(ENTRY_REPORT_TAG)
        val map = bounds(CARTOGRAPHY_MAP_TEST_TAG)
        val mapWidth = (map.right - map.left).value
        val mapHeight = (map.bottom - map.top).value
        val reportWidth = (report.right - report.left).value
        val reportHeight = (report.bottom - report.top).value
        assertEquals("the map ($map) is 4:3", mapWidth * 3f / 4f, mapHeight, 1f)
        assertEquals("the map is at the report's left ($report)", report.left.value, map.left.value, 0.5f)
        assertEquals("the map is at the report's top ($report)", report.top.value, map.top.value, 0.5f)
        val expectedHeight = minOf(reportHeight, (reportWidth - 240f) * 3f / 4f)
        assertEquals("the map is the largest 4:3 box that fits the height ($reportHeight) and leaves 240 dp", expectedHeight, mapHeight, 1f)
        assertTrue("the info column beside the map is at least 240 dp (${(report.right - map.right).value})", (report.right - map.right).value >= 240f - 0.5f)
        val header = back().getUnclippedBoundsInRoot()
        assertTrue("Back ($header) is right of the map ($map)", header.left >= map.right)
        assertTrue("the entry options ($header) are right of the map", options().getUnclippedBoundsInRoot().left >= map.right)
        assertTrue("the info column (${info()}) is right of the map", info().left.value >= map.right.value - 0.5f)
        assertTrue("the info column starts below the header", info().top.value >= header.bottom.value - 0.5f)
    }

    // ── Tests first ──

    @Test
    fun `landscape the map is a 4 by 3 box on the left, the largest that leaves the info 240 dp, with the header and info on its right`() {
        setScreen()
        assertSplit()
    }

    /**
     * A landscape tablet. Dispatch 2026-10-18-10 (RECORD -1337, the owner: "The tall map in tablet mode would look better as a 1:1
     * box with the info next to it. Journal entry page can be spread further apart in the same way"; -1340: the Journal's column
     * 960 dp on a large screen) replaced this case's 4:3 box in the 640 dp column. It was
     * `landscape tablet the map box is limited by the 240 dp info column, still 4 by 3 and on the left`, which ran [assertSplit]
     * (`assertEquals("the map ($map) is 4:3", mapWidth * 3f / 4f, mapHeight, 1f)`) and then
     * `assertEquals("the info column is exactly 240 dp", 240f, (report.right - map.right).value, 1f)`. Now the page is the 960 dp
     * column and the map a square on the left, the largest that fits the height and leaves the info 240 dp. The phone's split
     * ([assertSplit], above) is unchanged. `TabletJournalLayoutTest` holds the owner's tablet sizes in both orientations.
     */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun `landscape tablet the map is a square on the left, the largest that leaves the info 240 dp, in the 960 dp column`() {
        setScreen()
        val report = bounds(ENTRY_REPORT_TAG)
        val map = bounds(CARTOGRAPHY_MAP_TEST_TAG)
        val mapWidth = (map.right - map.left).value
        val mapHeight = (map.bottom - map.top).value
        val reportHeight = (report.bottom - report.top).value
        assertEquals("the page is the tablet's 960 dp column", TABLET_JOURNAL_CONTENT_MAX_WIDTH.value, (report.right - report.left).value, 1f)
        assertEquals("the map ($map) is square", mapWidth, mapHeight, 1f)
        assertEquals("the map is at the page's left ($report)", report.left.value, map.left.value, 0.5f)
        assertEquals("the map is the largest square that fits the height ($reportHeight) and leaves 240 dp", minOf(reportHeight, TABLET_JOURNAL_CONTENT_MAX_WIDTH.value - 240f), mapWidth, 1f)
        assertTrue("the info column (${info()}) is right of the map", info().left.value >= map.right.value - 0.5f)
        assertTrue("the info column beside the map is at least 240 dp", (report.right - map.right).value >= 240f - 0.5f)
    }

    @Test
    fun `landscape the stats come before the offline switch and the notes, in the info column`() {
        setScreen()
        val finds = composeRule.onNodeWithText(TILE_FINDS, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val switch = composeRule.onNodeWithTag(OFFLINE_TOGGLE_TEST_TAG).getUnclippedBoundsInRoot()
        val notes = composeRule.onNodeWithText("A wet morning on the ridge.").getUnclippedBoundsInRoot()
        val map = bounds(CARTOGRAPHY_MAP_TEST_TAG)
        listOf("Finds tile" to finds, "switch" to switch, "notes" to notes).forEach { (what, r) ->
            assertTrue("the $what ($r) is right of the map ($map)", r.left >= map.right)
        }
        assertTrue("the Finds tile ($finds) is above the switch ($switch)", finds.bottom <= switch.top)
        assertTrue("the switch ($switch) is above the notes ($notes)", switch.bottom <= notes.top)
    }

    @Test
    fun `landscape the info column scrolls on its own, the map and header staying put`() {
        setScreen()
        // Notes long enough that the info column has to scroll in any landscape window.
        composeRule.runOnIdle { editing = entry.copy(text = "A wet morning on the ridge." + "\nChanterelles under the firs.".repeat(30)) }
        composeRule.waitForIdle()
        val map = bounds(CARTOGRAPHY_MAP_TEST_TAG)
        val header = back().getUnclippedBoundsInRoot()
        val notes = { composeRule.onNodeWithText("A wet morning on the ridge.", substring = true).getUnclippedBoundsInRoot() }
        val notesBefore = notes()
        composeRule.onNodeWithTag(ENTRY_REPORT_INFO_TAG).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        val notesAfter = notes()
        assertTrue("the info column scrolled (notes from ${notesBefore.top} to ${notesAfter.top})", notesAfter.top < notesBefore.top)
        assertEquals("the map did not move", map, bounds(CARTOGRAPHY_MAP_TEST_TAG))
        assertEquals("the header did not move", header, back().getUnclippedBoundsInRoot())
    }

    @Test
    fun `landscape Back takes real touches across its bounds`() {
        setScreen()
        back().assertIsDisplayed()
        val backRect = back().getUnclippedBoundsInRoot()
        // Each touch closes the entry; reopen it for the next.
        for ((fx, fy) in fractions) {
            touch(backRect, fx, fy)
            composeRule.runOnIdle { editing = entry }
            composeRule.waitForIdle()
        }
        assertEquals("every sampled touch on Back reached it", fractions.size, closes)
    }

    @Test
    fun `landscape the offline switch in the info column is shown whole in the window and takes real touches`() {
        setScreen()
        val switch = composeRule.onNodeWithTag(OFFLINE_TOGGLE_TEST_TAG)
        switch.performScrollTo()
        switch.assertIsDisplayed()
        val rect = switch.getUnclippedBoundsInRoot()
        val window = root()
        assertTrue("the switch $rect lies wholly inside the window $window", rect.top >= window.top && rect.bottom <= window.bottom && rect.right <= window.right)
        switch.assertIsOff()
        touch(rect, 0.5f, 0.5f)
        switch.assertIsOn()
        touch(rect, 0.2f, 0.3f)
        switch.assertIsOff()
        touch(rect, 0.8f, 0.7f)
        switch.assertIsOn()
    }

    @Test
    fun `landscape a real touch on the map opens it fullscreen across the whole report, and the map keeps its one opening frame`() {
        setScreen()
        val map = bounds("map-slot")
        // Away from the stub glyph (48 to 72 dp in from the map's top left).
        touch(DpRect(map.right - 40.dp, map.bottom - 40.dp, map.right - 20.dp, map.bottom - 20.dp), 0.5f, 0.5f)
        val report = bounds(ENTRY_REPORT_TAG)
        // The map slot's own node, not CARTOGRAPHY_MAP_TEST_TAG: that tag is not on the map's box in fullscreen (its `.testTag`
        // binds to the inner if-else of the box's modifier, not the fullscreen branch; found by instrumenting this test, CI run
        // 39128035537, whose tree dump showed the map slot filling the report with no node carrying the tag).
        val full = bounds("map-slot")
        assertEquals("fullscreen: the map spans the report's width", (report.right - report.left).value, (full.right - full.left).value, 0.5f)
        assertEquals("fullscreen: the map spans the report's height", (report.bottom - report.top).value, (full.bottom - full.top).value, 0.5f)
        assertEquals("one opening request across the switch", 1, requests.filterNotNull().distinct().size)
    }

    // ── Checks: M1's bubbles and the opening frame still work on the split map ──

    @Test
    fun `L2 a real touch on a glyph in the capped map opens its bubble, and the map asked for its opening frame once`() {
        setScreen()
        val mapRect = bounds("map-slot")
        touch(DpRect(mapRect.left + 48.dp, mapRect.top + 48.dp, mapRect.left + 72.dp, mapRect.top + 72.dp), 0.5f, 0.5f)
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        assertEquals("the glyph, not the map, took the touch", 0, bubbles.taps)
        assertEquals("one opening request", 1, requests.filterNotNull().distinct().size)
    }

    // ── Checks (pins, pass before and after by design): portrait is unchanged ──

    /** L2's cap, kept for a short window that is not landscape (a phone in split screen). */
    @Test
    @Config(qualifiers = "w411dp-h460dp-port")
    fun `L2 a short portrait window keeps the map capped at 40 percent of the window height, across the width`() {
        setScreen()
        val mapRect = bounds(CARTOGRAPHY_MAP_TEST_TAG)
        assertEquals("the map is 40% of the 460 dp window", 460f * 0.4f, (mapRect.bottom - mapRect.top).value, 0.5f)
        assertEquals("the map spans the width", (root().right - root().left).value, (mapRect.right - mapRect.left).value, 0.5f)
        assertTrue("the map starts below Back", mapRect.top >= back().getUnclippedBoundsInRoot().bottom)
    }


    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `L2 portrait keeps the 4 by 3 map across the width`() {
        setScreen()
        val mapRect = bounds(CARTOGRAPHY_MAP_TEST_TAG)
        val window = root()
        assertEquals("the map spans the width", (window.right - window.left).value, (mapRect.right - mapRect.left).value, 0.5f)
        assertEquals("the map is 4:3", (mapRect.right - mapRect.left).value * 3f / 4f, (mapRect.bottom - mapRect.top).value, 0.5f)
        assertTrue("the map starts below Back", mapRect.top >= back().getUnclippedBoundsInRoot().bottom)
    }
}

private val L2_DAY = LocalDate.of(2026, 9, 20)
