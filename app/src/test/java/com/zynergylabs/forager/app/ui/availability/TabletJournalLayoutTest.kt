package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsSelected
import com.zynergylabs.forager.app.ui.theme.Spacing
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.FindDecision
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.ui.log.CARTOGRAPHY_MAP_TEST_TAG
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.log.ENTRIES_GRID_TAG
import com.zynergylabs.forager.app.ui.log.ENTRY_REPORT_INFO_TAG
import com.zynergylabs.forager.app.ui.log.ENTRY_REPORT_TAG
import com.zynergylabs.forager.app.ui.log.JournalTopTab
import com.zynergylabs.forager.app.ui.log.LARGE_SCREEN_MIN_SMALLEST_WIDTH_DP
import com.zynergylabs.forager.app.ui.log.RecordsSubTab
import com.zynergylabs.forager.app.ui.log.SHORT_HEADER_TAG
import com.zynergylabs.forager.app.ui.log.SHORT_SEARCH_TAG
import com.zynergylabs.forager.app.ui.log.entryCardTestTag
import com.zynergylabs.forager.app.ui.log.journalSwitchTestTag
import com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.io.File
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/**
 * The tablet Journal, dispatch 2026-10-18-10 and its Amendment 1 (Forager RECORD -1337, -1338, -1340). The owner: "The tall map in
 * tablet mode would look better as a 1:1 box with the info next to it. Journal entry page can be spread further apart in the same
 * way"; then "A: wider column (Recommended)", "Match content (Recommended)". On a large screen (smallest width 600 dp or more):
 *
 * - landscape: the Journal's column is 960 dp, centred beside the rail, for every Journal screen, its top line included, and the
 *   lists keep their column counts;
 * - Offline maps: a square map whose side is its height under the search gap, capped so the controls keep 240 dp, holding still
 *   through the search reveal (-1331 to -1334);
 * - the entry page: a square map on the left, the details beside it;
 * - portrait: the square on top, the info under it.
 *
 * Phones are unchanged: the phone cases here, and the phone classes (`AvailabilityScreenLandscapeB3DestinationsTest`,
 * `LandscapeEntryReportTest`, `LandscapeOfflinePickerTest`, `AvailabilityScreenJournalShortWindowTest`), are the check.
 *
 * Driven through the real [AvailabilityScreen]. Navigation is by semantic clicks (not a claim about touch); the search icon is a
 * real touch, as in the phone's reveal test. Each test first proves the screen saw the rotation and the smallest width it is about,
 * so a qualifier Robolectric did not apply cannot pass as a tablet. Sizes: the owner's tablet, 1318 x 824 dp in landscape and
 * 824 x 1318 dp in portrait (`AvailabilityScreenTabletAsPhoneTest`; `docs/audits/2026-09-28-tablet-layout-sanity-run-record.md`).
 * Robolectric reports zero insets, so the status and navigation bars are device items.
 *
 * Screenshots (native graphics) go to `build/test-results/testDebugUnitTest/tablet-journal-screenshots/`, inside the folder CI
 * uploads as its test report. The map is a stand-in and draws nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = TABLET_LANDSCAPE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletJournalLayoutTest {

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

    private var rotationSeen: Int? = null
    private var smallestWidthSeen: Int? = null

    private fun setScreen(rotation: Int, expectLarge: Boolean) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            smallestWidthSeen = LocalConfiguration.current.smallestScreenWidthDp
            var cartography by remember { mutableStateOf(CartographyUiState(entries = TABLET_ENTRIES)) }
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
                mapSlot = TABLET_STUB_MAP,
                journalEntries = AvailabilityScreenJournalEntries(
                    cartographyUiState = cartography,
                    onOpenCartographyEntry = { id -> cartography = cartography.copy(editingEntry = cartography.entries.first { it.id == id }) },
                    onCloseCartographyEntry = { cartography = cartography.copy(editingEntry = null) },
                ),
                entryReportData = AvailabilityScreenEntryReportData(
                    getCartographyEntryMapData = { _, _ -> TABLET_ENTRY_MAP_DATA },
                ),
            )
        }
        settle()
        assertEquals("the screen must see the rotation this test is about", rotation, rotationSeen)
        val sw = smallestWidthSeen ?: 0
        assertEquals(
            "the screen must see the smallest width this test is about (it saw $sw dp)",
            expectLarge, sw >= LARGE_SCREEN_MIN_SMALLEST_WIDTH_DP,
        )
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun exists(tag: String): Boolean = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun root(): DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()
    private fun width(r: DpRect): Float = (r.right - r.left).value
    private fun height(r: DpRect): Float = (r.bottom - r.top).value
    private fun centreX(r: DpRect): Float = (r.left.value + r.right.value) / 2f

    /** The window less the rail, on whichever side the rail is. */
    private fun areaBesideRail(): DpRect {
        val window = root()
        val rail = bounds(COMPACT_NAVIGATION_RAIL_TAG)
        return if (centreX(rail) > centreX(window)) DpRect(window.left, window.top, rail.left, window.bottom) else DpRect(rail.right, window.top, window.right, window.bottom)
    }

    private fun openJournal() {
        composeRule.onNodeWithText("Journal").performClick()
        settle()
    }

    private fun openOfflineMaps() {
        openJournal()
        composeRule.onNodeWithTag(journalSwitchTestTag(JournalTopTab.RECORDS)).performClick()
        settle()
        // Scrolled to first: on a phone the chip lies past the end of the chip row's visible part, where a click misses it (CI run
        // 39187881766's tree: the chip at 722-867 px, the row's visible part ending at 572 px), as OfflineNotDownloadedRegionTest does.
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.OFFLINE_MAPS)).performScrollTo().performClick()
        settle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.OFFLINE_MAPS)).assertIsSelected()
    }

    private fun openFirstEntry() {
        openJournal()
        composeRule.onNodeWithTag(entryCardTestTag(TABLET_ENTRIES[0].id)).performClick()
        settle()
        assertTrue("positive control: the entry's map is shown", exists(CARTOGRAPHY_MAP_TEST_TAG))
    }

    private fun touchSearchIcon() {
        val r = bounds(SHORT_SEARCH_TAG)
        composeRule.onRoot().performTouchInput { click(Offset(centreX(r) * density, (r.top.value + r.bottom.value) / 2f * density)) }
    }

    private fun near(a: DpRect, b: DpRect) = listOf(a.left - b.left, a.top - b.top, a.right - b.right, a.bottom - b.bottom).all { kotlin.math.abs(it.value) <= 0.5f }

    /** [what] is the 960 dp column: that wide (the area beside the rail is wider), and centred in it. */
    private fun assertTabletColumn(what: String, r: DpRect) {
        val area = areaBesideRail()
        assertTrue("positive control: the area beside the rail (${width(area)} dp) is wider than the column", width(area) > TABLET_COLUMN + 1f)
        assertEquals("$what ($r) is the 960 dp column", TABLET_COLUMN, width(r), 1f)
        assertEquals("$what ($r) is centred beside the rail ($area)", centreX(area), centreX(r), 1f)
    }

    /**
     * The top line is the column less its own side padding (`ShortWindowJournalHeader`'s `padding(horizontal = Spacing.lg)`, which
     * its tag sits inside), centred in the same place.
     */
    private fun assertTopLineInColumn(what: String, line: DpRect, column: Float) {
        val area = areaBesideRail()
        assertEquals("$what ($line) is the $column dp column less its 16 dp side padding", column - 2 * Spacing.lg.value, width(line), 1f)
        assertEquals("$what ($line) is centred beside the rail ($area)", centreX(area), centreX(line), 1f)
    }

    private fun screenshot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/test-results/testDebugUnitTest/tablet-journal-screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT ${File(dir, "$name.png").absolutePath}")
    }

    // ── Landscape tablet: Offline maps ──

    private fun checkOfflineMapsLandscape(rotation: Int) {
        setScreen(rotation, expectLarge = true)
        openOfflineMaps()
        val map = bounds(OFFLINE_PICKER_MAP_TAG)
        val controls = bounds(OFFLINE_PICKER_CONTROLS_SCROLL_TAG)
        val line = bounds(SHORT_HEADER_TAG)
        println("MEASURED tablet offline rotation=$rotation: map $map, controls $controls, line $line, area ${areaBesideRail()}")
        assertEquals("the map ($map) is square", width(map), height(map), 1f)
        assertTrue("the controls ($controls) are beside the map ($map), not over it", controls.left.value >= map.right.value - 0.5f)
        assertTrue("the controls keep at least 240 dp (${width(controls)})", width(controls) >= 240f - 0.5f)
        val span = DpRect(map.left, map.top, controls.right, map.bottom)
        assertTabletColumn("the map and the controls together", span)
        assertTopLineInColumn("the top line", line, TABLET_COLUMN)
        // The largest square: as tall as the room under the switch line and its gap, unless that would leave the controls under 240.
        val room = root().bottom.value - map.top.value
        val expected = minOf(room, TABLET_COLUMN - 240f)
        assertEquals("the map is the largest square that fits the height ($room) and leaves 240 dp", expected, width(map), 1f)
    }

    @Test fun `landscape tablet at ROTATION_90 Offline maps is a square map with the controls beside it, in the 960 dp column`() =
        checkOfflineMapsLandscape(Surface.ROTATION_90)

    @Test fun `landscape tablet at ROTATION_270 Offline maps is a square map with the controls beside it, in the 960 dp column`() =
        checkOfflineMapsLandscape(Surface.ROTATION_270)

    /** RECORD -1331 to -1334, at the tablet's size: the square map holds still, every frame, as the search bar comes and goes. */
    private fun checkOfflineMapsReveal(rotation: Int) {
        setScreen(rotation, expectLarge = true)
        openOfflineMaps()
        checkOfflineMapsRevealOnScreen()
    }

    private fun checkOfflineMapsRevealOnScreen() {
        assertTrue("hidden to begin with", !exists(SEARCH_ENTRY_BAR_TAG))
        val map = bounds(OFFLINE_PICKER_MAP_TAG)
        val lineHidden = bounds(SHORT_HEADER_TAG)
        composeRule.mainClock.autoAdvance = false
        touchSearchIcon()
        val inFrames = (1..6).map { composeRule.mainClock.advanceTimeByFrame(); bounds(SHORT_HEADER_TAG) to bounds(OFFLINE_PICKER_MAP_TAG) }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertTrue("positive control: the search bar is shown", exists(SEARCH_ENTRY_BAR_TAG))
        val lineShown = bounds(SHORT_HEADER_TAG)
        println("MEASURED tablet reveal: map $map, line hidden $lineHidden, shown $lineShown, frames ${inFrames.map { it.first.top.value }}")
        assertTrue(
            "positive control: part way in, the switch line is between its two places",
            inFrames.any { it.first.top > lineHidden.top + 0.5.dp && it.first.top < lineShown.top - 0.5.dp },
        )
        for ((i, f) in inFrames.withIndex()) assertTrue("frame ${i + 1} in: the map keeps its bounds ($map, then ${f.second})", near(map, f.second))
        assertTrue("search open: the map keeps its bounds", near(map, bounds(OFFLINE_PICKER_MAP_TAG)))
        assertTrue("search open: the switch line $lineShown does not reach the map $map", lineShown.bottom <= map.top + 0.5.dp)

        composeRule.mainClock.autoAdvance = false
        touchSearchIcon()
        val outFrames = (1..6).map { composeRule.mainClock.advanceTimeByFrame(); bounds(OFFLINE_PICKER_MAP_TAG) }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        for ((i, f) in outFrames.withIndex()) assertTrue("frame ${i + 1} out: the map keeps its bounds ($map, then $f)", near(map, f))
        assertTrue("hidden again", !exists(SEARCH_ENTRY_BAR_TAG))
    }

    @Test fun `landscape tablet at ROTATION_90 the square map holds still through the search reveal`() = checkOfflineMapsReveal(Surface.ROTATION_90)

    @Test fun `landscape tablet at ROTATION_270 the square map holds still through the search reveal`() = checkOfflineMapsReveal(Surface.ROTATION_270)

    /**
     * A large screen where the height decides the square (an unfolded foldable, 1000 x 700 dp). At the owner's tablet size under
     * Robolectric the controls' 240 dp floor and the held height come out close, so that size alone cannot tell "a square from the
     * height under the gap" from "a square from the width"; here the height is well under the width's cap, so it can. On a real
     * tablet the status and navigation bars take height, so this is the regime the tablet itself is in.
     */
    @Test
    @Config(qualifiers = FOLDABLE_LANDSCAPE)
    fun `landscape foldable where the height decides, the square is as tall as the room under the line and holds through the reveal`() {
        setScreen(Surface.ROTATION_90, expectLarge = true)
        openOfflineMaps()
        val map = bounds(OFFLINE_PICKER_MAP_TAG)
        val controls = bounds(OFFLINE_PICKER_CONTROLS_SCROLL_TAG)
        val room = root().bottom.value - map.top.value
        println("MEASURED foldable offline: map $map, controls $controls, room $room")
        assertEquals("the map ($map) is square", width(map), height(map), 1f)
        assertEquals("the square is as tall as the room under the line and its gap", room, height(map), 1f)
        assertTrue("positive control: here the height decides, the controls get more than 240 dp (${width(controls)})", width(controls) > 240f + 20f)
        assertTrue("the controls ($controls) are beside the map ($map)", controls.left.value >= map.right.value - 0.5f)
        checkOfflineMapsRevealOnScreen()
    }

    // ── Landscape tablet: the entry page ──

    private fun checkEntryLandscape(rotation: Int) {
        setScreen(rotation, expectLarge = true)
        openFirstEntry()
        val report = bounds(ENTRY_REPORT_TAG)
        val map = bounds(CARTOGRAPHY_MAP_TEST_TAG)
        val info = bounds(ENTRY_REPORT_INFO_TAG)
        println("MEASURED tablet entry rotation=$rotation: report $report, map $map, info $info")
        assertTabletColumn("the entry page", report)
        assertEquals("the map ($map) is square", width(map), height(map), 1f)
        assertEquals("the map is at the page's left", report.left.value, map.left.value, 0.5f)
        assertEquals("the map is at the page's top", report.top.value, map.top.value, 0.5f)
        assertTrue("the details ($info) are beside the map ($map), not over it", info.left.value >= map.right.value - 0.5f)
        assertEquals("the map and the details span the page", report.right.value, info.right.value, 0.5f)
        val expected = minOf(height(report), width(report) - 240f)
        assertEquals("the map is the largest square that fits the height (${height(report)}) and leaves 240 dp", expected, width(map), 1f)
    }

    @Test fun `landscape tablet at ROTATION_90 the entry page is a square map with the details beside it, in the 960 dp column`() =
        checkEntryLandscape(Surface.ROTATION_90)

    @Test fun `landscape tablet at ROTATION_270 the entry page is a square map with the details beside it, in the 960 dp column`() =
        checkEntryLandscape(Surface.ROTATION_270)

    // ── Landscape tablet: the lists ──

    @Test
    fun `landscape tablet Entries fills the 960 dp column, in two columns as on the phone`() {
        setScreen(Surface.ROTATION_90, expectLarge = true)
        openJournal()
        val grid = bounds(ENTRIES_GRID_TAG)
        assertTabletColumn("the Entries grid", grid)
        assertTopLineInColumn("the top line", bounds(SHORT_HEADER_TAG), TABLET_COLUMN)
        val first = bounds(entryCardTestTag(TABLET_ENTRIES[0].id))
        val second = bounds(entryCardTestTag(TABLET_ENTRIES[1].id))
        println("MEASURED tablet entries: grid $grid, first $first, second $second")
        assertEquals("two cards side by side: the first two share a row", first.top.value, second.top.value, 0.5f)
        assertTrue("two cards side by side: the second is right of the first", second.left >= first.right)
        assertTrue("two columns, not more: each card is over a third of the grid (${width(first)} of ${width(grid)})", width(first) > width(grid) / 3f)
    }

    @Test
    fun `landscape tablet Records' top line is the 960 dp column`() {
        setScreen(Surface.ROTATION_90, expectLarge = true)
        openJournal()
        composeRule.onNodeWithTag(journalSwitchTestTag(JournalTopTab.RECORDS)).performClick()
        settle()
        assertTopLineInColumn("Records' top line", bounds(SHORT_HEADER_TAG), TABLET_COLUMN)
    }

    // ── Amendment 2 (RECORD -1350): the search bar and its open panel in the 960 dp column ──
    //
    // The owner: "can the search bar in the journal be moved a bit from the edge? It sits very close to it. For tablets". On the
    // tablet (-1348) the revealed bar and the open panel started at the screen's edge, about 1,040 dp wide. The bar is opened by
    // a real touch on 🔍 and the panel by a real touch on the bar.

    private fun revealSearch() {
        touchSearchIcon()
        settle()
        assertTrue("positive control: the search bar is shown", exists(SEARCH_ENTRY_BAR_TAG))
    }

    private fun openSearchPanel() {
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        composeRule.onRoot().performTouchInput { click(Offset(centreX(bar) * density, (bar.top.value + bar.bottom.value) / 2f * density)) }
        settle()
        assertTrue("positive control: the search panel is open", exists(SEARCH_DROPDOWN_TAG))
    }

    /** [what]'s left and right edges are the 960 dp column's, within 1 dp; [column] is the Journal's content in that column. */
    private fun assertEdgesOf(what: String, r: DpRect, column: DpRect) {
        assertEquals("$what ($r) starts where the column ($column) starts", column.left.value, r.left.value, 1f)
        assertEquals("$what ($r) ends where the column ($column) ends", column.right.value, r.right.value, 1f)
    }

    private fun checkSearchInColumn(rotation: Int) {
        setScreen(rotation, expectLarge = true)
        openJournal()
        val column = bounds(ENTRIES_GRID_TAG)
        assertTabletColumn("positive control: the Entries grid", column)
        revealSearch()
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        println("MEASURED tablet search rotation=$rotation: bar $bar, column $column, area ${areaBesideRail()}")
        assertEdgesOf("the revealed search bar", bar, column)
        openSearchPanel()
        val panel = bounds(SEARCH_DROPDOWN_TAG)
        println("MEASURED tablet search panel rotation=$rotation: panel $panel")
        assertEdgesOf("the open search panel", panel, column)
        for (tag in listOf(SEARCH_DROPDOWN_SET_ON_MAP_TAG, SEARCH_DROPDOWN_SEARCH_TAG)) {
            val r = bounds(tag)
            assertTrue("$tag ($r) lies inside the column ($column)", r.left.value >= column.left.value - 0.5f && r.right.value <= column.right.value + 0.5f)
        }
    }

    @Test fun `landscape tablet at ROTATION_90 the revealed search bar and its open panel sit in the 960 dp column`() =
        checkSearchInColumn(Surface.ROTATION_90)

    @Test fun `landscape tablet at ROTATION_270 the revealed search bar and its open panel sit in the 960 dp column`() =
        checkSearchInColumn(Surface.ROTATION_270)

    // ── Amendment 3 (RECORD -1353): List and Seasonal's bar and open panel line up with their 640 dp column ──
    //
    // The owner: "Yes move those tabs also. Those were the ones I was mainly complaining about"; "Line up with their content
    // (Recommended)". This replaced Amendment 2's control `landscape tablet List keeps the search bar at the left, as the owner set
    // it`, which asserted -1238's place on a tablet: `assertEquals("List's bar starts at the area's left edge (no navigation bar
    // under Robolectric)", area.left.value, bar.left.value, 1f)` and `assertTrue("List's bar is wider than the Journal's column
    // (${width(bar)})", width(bar) > TABLET_COLUMN + 1f)`. -1353 supersedes -1238's "at the left" for tablets only; the phone
    // controls below hold it for phones.

    // Amendment 4 (RECORD -1355, the owner: "Same as the Journal (960)") widened List and Seasonal to the 960 dp column on a
    // large screen. The checks below asserted Amendment 3's 640 dp column there (`checkTabSearchInColumn` used
    // `readableColumn()`, 640 dp centred beside the rail, and the tests were named "... line up with its 640 dp column"); they now
    // take the 960 dp column. The phone controls keep 640.

    /** A column of [columnWidth] dp centred in the area beside the rail, as the scaffold's readable cap places it. */
    private fun readableColumn(columnWidth: Float = READABLE_CONTENT_MAX_WIDTH.value): DpRect {
        val area = areaBesideRail()
        val side = (width(area) - columnWidth) / 2f
        return DpRect(area.left + side.dp, area.top, area.right - side.dp, area.bottom)
    }

    private fun openTab(label: String) {
        composeRule.onNodeWithText(label).performClick()
        settle()
        assertTrue("positive control: the search bar shows on $label", exists(SEARCH_ENTRY_BAR_TAG))
    }

    private fun checkTabSearchInColumn(label: String, rotation: Int) {
        setScreen(rotation, expectLarge = true)
        openTab(label)
        val column = readableColumn(TABLET_COLUMN)
        // Dispatch 2026-10-19-06 (RECORD -1385): List's own content, measured here before the panel opens and asserted last, so a
        // failure on it shows the bar and panel checks above it passed. Until then only List's bar and panel were measured.
        val listContent = if (label == "List") bounds(LIST_CONTENT_TAG) else null
        if (label == "Seasonal") {
            // The column checked against the tab's own content. SEASONAL_CONTENT_TAG's node spans the whole column: the tag shares
            // its layout node with the Column's verticalScroll, which comes before padding(horizontal = Spacing.lg), and the node
            // reports the scroll's bounds (pre-main CI run 39199565610: 139 to 1099, the column's own edges). Until then this asserted
            // `assertEquals("positive control: Seasonal's content ($content) starts 16 dp inside the column ($column)",
            // column.left.value + Spacing.lg.value, content.left.value, 1f)` and the same at the right, a premise about the tag, not
            // the layout. So the node spans the column, and what it holds (the text) sits inside the 16 dp padding.
            val content = bounds(SEASONAL_CONTENT_TAG)
            assertEdgesOf("Seasonal's scrolling column", content, column)
            val children = composeRule.onNodeWithTag(SEASONAL_CONTENT_TAG).onChildren()
            val count = children.fetchSemanticsNodes().size
            assertTrue("positive control: Seasonal's column holds something to measure ($count nodes)", count > 0)
            for (i in 0 until count) {
                val r = children[i].getUnclippedBoundsInRoot()
                println("MEASURED tablet Seasonal child $i: $r")
                assertTrue("Seasonal's content node $i ($r) starts at least 16 dp inside the column ($column)", r.left.value >= column.left.value + Spacing.lg.value - 0.5f)
                assertTrue("Seasonal's content node $i ($r) ends at least 16 dp inside the column ($column)", r.right.value <= column.right.value - Spacing.lg.value + 0.5f)
            }
        }
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        println("MEASURED tablet $label search rotation=$rotation: bar $bar, column $column, area ${areaBesideRail()}")
        assertEdgesOf("$label's search bar", bar, column)
        openSearchPanel()
        val panel = bounds(SEARCH_DROPDOWN_TAG)
        println("MEASURED tablet $label search panel rotation=$rotation: panel $panel")
        assertEdgesOf("$label's open search panel", panel, column)
        for (tag in listOf(SEARCH_DROPDOWN_SET_ON_MAP_TAG, SEARCH_DROPDOWN_SEARCH_TAG)) {
            val r = bounds(tag)
            assertTrue("$tag ($r) lies inside the column ($column)", r.left.value >= column.left.value - 0.5f && r.right.value <= column.right.value + 0.5f)
        }
        if (listContent != null) {
            println("MEASURED tablet List content rotation=$rotation: $listContent")
            assertEdgesOf("List's content", listContent, column)
        }
    }

    @Test fun `landscape tablet at ROTATION_90 List's content, search bar and open panel share the 960 dp column`() = checkTabSearchInColumn("List", Surface.ROTATION_90)

    @Test fun `landscape tablet at ROTATION_270 List's content, search bar and open panel share the 960 dp column`() = checkTabSearchInColumn("List", Surface.ROTATION_270)

    @Test fun `landscape tablet at ROTATION_90 Seasonal's content, search bar and open panel share the 960 dp column`() = checkTabSearchInColumn("Seasonal", Surface.ROTATION_90)

    @Test fun `landscape tablet at ROTATION_270 Seasonal's content, search bar and open panel share the 960 dp column`() = checkTabSearchInColumn("Seasonal", Surface.ROTATION_270)

    private fun checkPhoneTabBarAtLeft(label: String) {
        setScreen(Surface.ROTATION_90, expectLarge = false)
        openTab(label)
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val area = areaBesideRail()
        println("MEASURED phone $label search: bar $bar, area $area")
        assertEquals("the phone's $label bar starts at the area's left edge (-1238)", area.left.value, bar.left.value, 1f)
        assertTrue("positive control: the phone's 640 dp column starts away from that edge", readableColumn().left.value > area.left.value + 10f)
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `phone landscape List's search bar is at the left, unchanged`() = checkPhoneTabBarAtLeft("List")

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `phone landscape Seasonal's search bar is at the left, unchanged`() = checkPhoneTabBarAtLeft("Seasonal")

    @Test
    fun `screenshot landscape tablet List open search panel`() {
        setScreen(Surface.ROTATION_90, expectLarge = true)
        openTab("List")
        openSearchPanel()
        screenshot("landscape-list-search-panel")
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `phone landscape the Journal's revealed search bar is at the left, unchanged`() {
        setScreen(Surface.ROTATION_90, expectLarge = false)
        openJournal()
        revealSearch()
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val area = areaBesideRail()
        println("MEASURED phone search: bar $bar, area $area")
        assertEquals("the phone's bar starts at the area's left edge", area.left.value, bar.left.value, 1f)
        val column = (area.right.value - area.left.value - READABLE_CONTENT_MAX_WIDTH.value) / 2f + area.left.value
        assertTrue("positive control: the phone's 640 dp column starts away from the area's edge ($column)", column > area.left.value + 10f)
    }

    @Test
    fun `screenshot landscape tablet open search panel`() {
        setScreen(Surface.ROTATION_90, expectLarge = true)
        openJournal()
        revealSearch()
        openSearchPanel()
        screenshot("landscape-search-panel")
    }

    // ── Portrait tablet: the square on top, the info under it ──

    @Test
    @Config(qualifiers = TABLET_PORTRAIT)
    fun `portrait tablet Offline maps is a square map across the width, the controls under it`() {
        setScreen(Surface.ROTATION_0, expectLarge = true)
        openOfflineMaps()
        val map = bounds(OFFLINE_PICKER_MAP_TAG)
        val name = composeRule.onNodeWithText("Name (optional)", substring = true).getUnclippedBoundsInRoot()
        println("MEASURED tablet portrait offline: map $map, name $name, window ${root()}")
        assertEquals("the map ($map) is square", width(map), height(map), 1f)
        assertEquals("the map spans the window's width", width(root()), width(map), 1f)
        assertTrue("the Name box ($name) is under the map ($map)", name.top.value >= map.bottom.value - 0.5f)
    }

    @Test
    @Config(qualifiers = TABLET_PORTRAIT)
    fun `portrait tablet the entry page is a square map across the width, the details under it`() {
        setScreen(Surface.ROTATION_0, expectLarge = true)
        openFirstEntry()
        val report = bounds(ENTRY_REPORT_TAG)
        val map = bounds(CARTOGRAPHY_MAP_TEST_TAG)
        val info = bounds(ENTRY_REPORT_INFO_TAG)
        println("MEASURED tablet portrait entry: report $report, map $map, info $info")
        assertEquals("the map ($map) is square", width(map), height(map), 1f)
        assertEquals("the map spans the page", width(report), width(map), 1f)
        assertTrue("the details ($info) are under the map ($map), not over it", info.top.value >= map.bottom.value - 0.5f)
    }

    // ── Phones: unchanged (controls in this class, beside the phone classes named above) ──

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `phone landscape Offline maps keeps half and half in the 640 dp column`() {
        setScreen(Surface.ROTATION_90, expectLarge = false)
        openOfflineMaps()
        val map = bounds(OFFLINE_PICKER_MAP_TAG)
        val controls = bounds(OFFLINE_PICKER_CONTROLS_SCROLL_TAG)
        println("MEASURED phone offline: map $map, controls $controls")
        assertEquals("the map and the controls share the 640 dp column", READABLE_CONTENT_MAX_WIDTH.value, (controls.right - map.left).value, 1f)
        assertEquals("the map is half of it, as before", width(controls), width(map), 1f)
        assertTrue("the map is not square on a phone (${width(map)} x ${height(map)})", kotlin.math.abs(width(map) - height(map)) > 10f)
        assertTopLineInColumn("the top line", bounds(SHORT_HEADER_TAG), READABLE_CONTENT_MAX_WIDTH.value)
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `phone landscape the entry page keeps the 4 by 3 map in the 640 dp column`() {
        setScreen(Surface.ROTATION_90, expectLarge = false)
        openFirstEntry()
        val report = bounds(ENTRY_REPORT_TAG)
        val map = bounds(CARTOGRAPHY_MAP_TEST_TAG)
        assertEquals("the page is the 640 dp column", READABLE_CONTENT_MAX_WIDTH.value, width(report), 1f)
        assertEquals("the map ($map) is 4:3", width(map) * 3f / 4f, height(map), 1f)
    }

    @Test
    @Config(qualifiers = PHONE_PORTRAIT)
    fun `phone portrait Offline maps keeps the 4 by 3 map`() {
        setScreen(Surface.ROTATION_0, expectLarge = false)
        openOfflineMaps()
        val map = bounds(OFFLINE_PICKER_MAP_TAG)
        assertEquals("the map ($map) is 4:3", width(map) * 3f / 4f, height(map), 1f)
    }

    // ── Screenshots, for the report ──

    @Test
    fun `screenshot landscape tablet Offline maps`() {
        setScreen(Surface.ROTATION_90, expectLarge = true)
        openOfflineMaps()
        screenshot("landscape-offline-maps")
    }

    @Test
    fun `screenshot landscape tablet entry page`() {
        setScreen(Surface.ROTATION_90, expectLarge = true)
        openFirstEntry()
        screenshot("landscape-entry-page")
    }

    @Test
    fun `screenshot landscape tablet Entries`() {
        setScreen(Surface.ROTATION_90, expectLarge = true)
        openJournal()
        screenshot("landscape-entries")
    }

    @Test
    @Config(qualifiers = TABLET_PORTRAIT)
    fun `screenshot portrait tablet Offline maps and entry page`() {
        setScreen(Surface.ROTATION_0, expectLarge = true)
        openOfflineMaps()
        screenshot("portrait-offline-maps")
        composeRule.onNodeWithTag(journalSwitchTestTag(JournalTopTab.CARTOGRAPHY)).performClick()
        settle()
        composeRule.onNodeWithTag(entryCardTestTag(TABLET_ENTRIES[0].id)).performClick()
        settle()
        assertTrue("positive control: the entry's map is shown", exists(CARTOGRAPHY_MAP_TEST_TAG))
        screenshot("portrait-entry-page")
    }
}

// ── fixtures ──

private const val TABLET_LANDSCAPE = "sw824dp-w1318dp-h824dp-land"
private const val TABLET_PORTRAIT = "sw824dp-w824dp-h1318dp-port"
private const val PHONE_LANDSCAPE = "w823dp-h384dp-land"
private const val FOLDABLE_LANDSCAPE = "sw700dp-w1000dp-h700dp-land"
private const val PHONE_PORTRAIT = "w384dp-h823dp-port"

/** TABLET_JOURNAL_CONTENT_MAX_WIDTH, as a number the assertions read. */
private val TABLET_COLUMN: Float = TABLET_JOURNAL_CONTENT_MAX_WIDTH.value

private const val TABLET_STUB_MAP_TAG = "tablet-stub-map"
private val TABLET_STUB_MAP: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag(TABLET_STUB_MAP_TAG)) }

private val TABLET_ENTRIES: List<CartographyEntry> = (12 downTo 1).map { day ->
    CartographyEntry.draft(id = "tab-$day", date = LocalDate.of(2026, 9, day), updatedAtEpochMillis = 0L).copy(
        isDraft = false,
        text = "Walk on the $day\nsecond line",
        findDecisions = listOf(FindDecision(findId = "f$day", foundOn = LocalDate.of(2026, 9, day), ownIdentification = "C. formosus", hasPhotos = false, kept = true)),
    )
}

/** One waypoint, so an open entry's page draws its map. */
private val TABLET_ENTRY_MAP_DATA = CartographyEntryMapData(
    emptyList(), emptyList(),
    listOf(RecordPoint("wp-entry", LatLng(45.52, -122.68))),
    emptyList(), emptyList(),
)
