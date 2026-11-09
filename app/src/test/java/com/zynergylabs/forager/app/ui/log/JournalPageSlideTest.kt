package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.availability.layoutFixesHostActivityRule
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.motion.PageLeavingKey
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Motion Part 3, item 1 (dispatch 2026-09-28-676; the owner, RECORD -651: Journal pages "Slide in, slide back": "Opened pages slide
 * in from the right; Back slides them out to the right"), through the real [JournalTab] under the app's theme: the Finds pages
 * (scout F1), Entries and Records (J1, J2), and a find opened over the view from a map bubble (F3).
 *
 * Every touch is a real coordinate touch, and Back is the activity's own Back. The clock is stopped for every mid-slide read, and
 * a state write made with it stopped is applied before frames are stepped (motion Part 1's report). Pages are told apart by their
 * frame's marker ([PageLeavingKey]): a leaving page keeps only that in the semantics tree, and an arriving one is the page frame not
 * yet at the left edge.
 *
 * Nothing here has been run: written with the code, before the build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class JournalPageSlideTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val resolver get() = ApplicationProvider.getApplicationContext<Application>().contentResolver

    @After
    fun restore() {
        composeRule.mainClock.autoAdvance = true
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    }

    private fun reduceMotionOn() = Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)

    private val find = MushroomLogEntry.draft(id = "find-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1))
        .copy(isDraft = false)

    private val uiState = mutableStateOf(MushroomLogUiState(entries = listOf(find)))
    private val pending = mutableStateOf<PendingJournalDestination?>(null)
    private val pendingFindId = mutableStateOf<String?>(null)
    private val opens = mutableListOf<String>()
    private var newEntriesStarted = 0

    private val mapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }

    private fun setScreen() {
        composeRule.setContent {
            ForagerTheme {
                JournalTab(
                    uiState = uiState.value,
                    onOpenCameraForLogEntry = {},
                    onOpenCameraForAlbum = {},
                    onOpenCameraForCartographyEntry = {},
                    mapSlot = mapSlot,
                    pickerRegion = Region(lat = 45.326, lng = -122.634, radiusKm = 15),
                    basemap = Basemap.DEFAULT,
                    onOpenEntry = { id ->
                        opens += id
                        uiState.value = uiState.value.copy(editingEntry = uiState.value.entries.first { it.id == id })
                    },
                    onCloseEntry = { uiState.value = uiState.value.copy(editingEntry = null) },
                    onStartEntry = { _, _ -> newEntriesStarted++ },
                    onEntryChanged = {},
                    onStartEditingEntry = {},
                    onSaveEntry = {},
                    onCancelEditing = { uiState.value = uiState.value.copy(editingEntry = null) },
                    onLeaveEditingIncidentally = { uiState.value = uiState.value.copy(editingEntry = null) },
                    onAddPhoto = {},
                    onRemovePhoto = {},
                    onPullPhoto = {},
                    onDeleteEntry = {},
                    onSaveErrorDismissed = {},
                    cartographyUiState = CartographyUiState(),
                    onOpenCartographyEntry = {},
                    onStartCartographyEntry = {},
                    onCloseCartographyEntry = {},
                    onCartographyTextChanged = {},
                    onCartographyTagsChanged = {},
                    onSetFindDecision = { _, _ -> },
                    onSetTrackDecision = { _, _ -> },
                    onSetWaypointDecision = { _, _ -> },
                    onSetOfflineRegionDecision = { _, _ -> },
                    onToggleKeptPhoto = {},
                    onFinishCartographyEntry = {},
                    onDeleteCartographyEntry = {},
                    getCartographyEntryMapData = { _, _ -> NO_MAP_DATA },
                    getCartographyEntryOfflineRegion = { _, _ -> null },
                    getCartographyEntryCurrentLocation = { com.zynergylabs.forager.app.domain.LocationResult.LocationUnavailable },
                    availabilityUiState = com.zynergylabs.forager.app.ui.availability.AvailabilityUiState(),
                    distanceUnit = com.zynergylabs.forager.app.domain.model.DistanceUnit.MILES,
                    currentTime = com.zynergylabs.forager.app.domain.CurrentTimeProvider { 0L },
                    onOfflineMapLatChanged = {},
                    onOfflineMapLngChanged = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                    tracks = emptyList(),
                    onTracksOpened = {},
                    waypoints = emptyList(),
                    waypointsErrorMessage = null,
                    onDeleteWaypoint = {},
                    pendingDestination = pending.value,
                    pendingFindId = pendingFindId.value,
                    onPendingDestinationConsumed = { pending.value = null },
                )
            }
        }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun pause() {
        composeRule.mainClock.autoAdvance = false
    }

    private fun applyWrites() = composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }

    private fun frame() = composeRule.mainClock.advanceTimeByFrame()

    private fun stepUntil(what: String, maxFrames: Int = 40, condition: () -> Boolean) {
        repeat(maxFrames) {
            frame()
            if (condition()) return
        }
        throw AssertionError("never saw: $what")
    }

    private fun width(): Dp = composeRule.onAllNodes(isRoot()).onFirst().getUnclippedBoundsInRoot().right

    private fun tapAt(x: Dp, y: Dp) {
        val at = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(at) }
    }

    private fun centreOf(b: DpRect) = Pair((b.left + b.right) / 2, (b.top + b.bottom) / 2)

    private fun back() = composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    private fun leftOf(node: SemanticsNode): Dp = with(composeRule.density) { node.boundsInRoot.left.toDp() }

    /** Leaving page frames, at whatever place their slide has them. */
    private fun leavingPages() = composeRule.onAllNodes(SemanticsMatcher.expectValue(PageLeavingKey, true)).fetchSemanticsNodes()

    /** Page frames that are arriving or settled; the one still to the right of the left edge is the one sliding in. */
    private fun pagesNotLeaving() = composeRule.onAllNodes(SemanticsMatcher.expectValue(PageLeavingKey, false)).fetchSemanticsNodes()

    private fun pageSlidingIn(): SemanticsNode? = pagesNotLeaving().firstOrNull { leftOf(it) > 1.dp }

    private fun goToFinds() {
        composeRule.onNodeWithTag(journalSwitchTestTag(JournalTopTab.RECORDS)).let { node ->
            val (x, y) = centreOf(node.getUnclippedBoundsInRoot())
            tapAt(x, y)
        }
        settle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.FINDS)).let { node ->
            val (x, y) = centreOf(node.getUnclippedBoundsInRoot())
            tapAt(x, y)
        }
        settle()
    }

    private fun findTile() = composeRule.onNodeWithContentDescription(findBlankTitleLabel(find)).getUnclippedBoundsInRoot()

    @Test
    fun `a find's report slides in from the right over the gallery, which takes no touch while it is covered`() {
        setScreen()
        goToFinds()
        val tile = findTile()
        // RECORD -751: the "New find" button replaced the "+" tile as the gallery's own control, so the touch goes to the
        // button's left end, which the arriving report reaches last of it (the button sits at the bottom end).
        val plus = composeRule.onNodeWithTag(FINDS_FAB_TAG).getUnclippedBoundsInRoot()
        val plusX = plus.left + 12.dp
        val plusY = (plus.top + plus.bottom) / 2

        pause()
        val (tileX, tileY) = centreOf(tile)
        tapAt(tileX, tileY)
        applyWrites()
        stepUntil("the report part-way in, the New find button's left end not yet covered") {
            val arriving = pageSlidingIn()
            arriving != null && leftOf(arriving) < width() - 1.dp && leftOf(arriving) > plusX + 4.dp
        }
        assertTrue("the gallery is still there beneath, leaving", leavingPages().isNotEmpty())

        // A real touch on the gallery's New find button, where it is still drawn and not yet covered: the gallery is leaving, so nothing.
        tapAt(plusX, plusY)
        applyWrites()
        frame()
        assertEquals("the covered gallery started no new find", 0, newEntriesStarted)

        settle()
        assertTrue("no page is left mid-slide", pageSlidingIn() == null)
        assertTrue("and none leaving", leavingPages().isEmpty())
        composeRule.onNodeWithContentDescription("Entry options").assertExists()
    }

    @Test
    fun `Back slides the report out to the right over the gallery, which has every touch at once`() {
        setScreen()
        goToFinds()
        val tile = findTile()
        val (tileX, tileY) = centreOf(tile)
        tapAt(tileX, tileY)
        settle()
        composeRule.onNodeWithContentDescription("Entry options").assertExists()
        opens.clear()

        pause()
        back()
        applyWrites()
        stepUntil("the report part-way out, the find's tile beneath uncovered") {
            val leaving = leavingPages()
            leaving.isNotEmpty() && leaving.any { leftOf(it) > tileX + 4.dp && leftOf(it) < width() - 1.dp }
        }
        // Back retraces: the gallery is in place already, under the report sliding away.
        assertTrue("the gallery is in place, not sliding", pageSlidingIn() == null)

        // A real touch where the tile is, now uncovered: the gallery takes it, so the find opens again.
        tapAt(tileX, tileY)
        applyWrites()
        frame()
        assertEquals("the arriving gallery took the touch", listOf(find.id), opens)
        settle()
    }

    @Test
    fun `a touch on the leaving report, where it is still drawn, reaches the gallery beneath and not the report`() {
        setScreen()
        goToFinds()
        val (tileX, tileY) = centreOf(findTile())
        tapAt(tileX, tileY)
        settle()
        opens.clear()

        pause()
        back()
        applyWrites()
        stepUntil("the report only just leaving, still over the find's tile") {
            val leaving = leavingPages()
            leaving.isNotEmpty() && leaving.any { leftOf(it) > 1.dp && leftOf(it) < tileX - 4.dp }
        }
        // The point is under the report as it is drawn now; the report takes no touch, so the gallery's tile beneath has it.
        tapAt(tileX, tileY)
        applyWrites()
        frame()
        assertEquals("the touch went through the leaving report to the gallery", listOf(find.id), opens)
        settle()
    }

    @Test
    fun `under reduced motion a find's report replaces the gallery at once`() {
        reduceMotionOn()
        setScreen()
        goToFinds()

        pause()
        val (tileX, tileY) = centreOf(findTile())
        tapAt(tileX, tileY)
        applyWrites()
        frame()
        frame()
        assertTrue("nothing leaving", leavingPages().isEmpty())
        assertTrue("nothing sliding in", pageSlidingIn() == null)
        composeRule.onNodeWithContentDescription("Entry options").assertExists()
    }

    @Test
    fun `Records slides in over Entries from the switch, and Back slides it out to the right over Entries in place`() {
        setScreen()
        val records = composeRule.onNodeWithTag(journalSwitchTestTag(JournalTopTab.RECORDS)).getUnclippedBoundsInRoot()

        pause()
        val (rx, ry) = centreOf(records)
        tapAt(rx, ry)
        applyWrites()
        stepUntil("Records part-way in") {
            val arriving = pageSlidingIn()
            arriving != null && leftOf(arriving) < width() - 1.dp
        }
        assertTrue("Entries is still there, leaving", leavingPages().isNotEmpty())
        settle()
        composeRule.onNodeWithTag(journalSwitchTestTag(JournalTopTab.RECORDS)).assertIsSelected()
        composeRule.onNodeWithTag(ENTRIES_HOME_TAG).assertDoesNotExist()

        pause()
        back()
        applyWrites()
        stepUntil("Records part-way out") {
            leavingPages().any { leftOf(it) > 1.dp && leftOf(it) < width() - 1.dp }
        }
        // Entries is uncovered where it settles, not sliding: Back retraces the way in.
        val entriesHome = composeRule.onNodeWithTag(ENTRIES_HOME_TAG).getUnclippedBoundsInRoot()
        assertEquals(0f, entriesHome.left.value, 1f)
        settle()
        composeRule.onNodeWithTag(journalSwitchTestTag(JournalTopTab.CARTOGRAPHY)).assertIsSelected()
        assertTrue(leavingPages().isEmpty())
    }

    @Test
    fun `a find opened over the view slides in, and nothing beneath takes a touch until it has arrived`() {
        setScreen()
        val records = composeRule.onNodeWithTag(journalSwitchTestTag(JournalTopTab.RECORDS)).getUnclippedBoundsInRoot()
        val (rx, ry) = centreOf(records)

        // The Maps tab's "Open in Journal": the find is opened, then the Journal is asked to show it over the view.
        pause()
        composeRule.runOnUiThread {
            uiState.value = uiState.value.copy(editingEntry = find)
            pendingFindId.value = find.id
            pending.value = PendingJournalDestination.VIEW_FIND
        }
        applyWrites()
        stepUntil("the find part-way in, the Records half of the switch not yet covered") {
            val over = composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(FIND_OVER_VIEW_TAG)).fetchSemanticsNodes()
            over.isNotEmpty() && leftOf(over.single()) < width() - 1.dp && leftOf(over.single()) > rx + 4.dp
        }

        // A real touch on Records, uncovered beside the arriving find: the view beneath takes nothing while the find comes in.
        tapAt(rx, ry)
        applyWrites()
        settle()
        composeRule.onNodeWithTag(journalSwitchTestTag(JournalTopTab.RECORDS)).assertIsNotSelected()
        composeRule.onNodeWithTag(FIND_OVER_VIEW_TAG).assertExists()
    }

    @Test
    fun `Back slides the find over the view out to the right, and the view beneath is in place and as it was`() {
        setScreen()
        composeRule.runOnUiThread {
            uiState.value = uiState.value.copy(editingEntry = find)
            pendingFindId.value = find.id
            pending.value = PendingJournalDestination.VIEW_FIND
        }
        settle()
        composeRule.onNodeWithTag(FIND_OVER_VIEW_TAG).assertExists()

        pause()
        back()
        applyWrites()
        stepUntil("the find over the view part-way out") {
            leavingPages().any { leftOf(it) > 1.dp && leftOf(it) < width() - 1.dp }
        }
        // The view it uncovers is in place from the first frame: Back retraces the way in. (What the leaving find draws, its
        // last page, is not readable here: a leaving page keeps only its marker in the semantics tree.)
        assertEquals(0f, composeRule.onNodeWithTag(ENTRIES_HOME_TAG).getUnclippedBoundsInRoot().left.value, 1f)
        settle()
        composeRule.onNodeWithTag(FIND_OVER_VIEW_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(ENTRIES_HOME_TAG).assertExists()
    }

    /**
     * RECORD -874 (the planner's call): opening a find from All is forward navigation, so the find's page slides in over
     * All and All stays in place; it must not slide out as if going back. Back slides the find out to the right over
     * All, still in place, with All still the selected chip.
     */
    @Test
    fun `a find tapped in All slides in over All, which stays in place, and Back slides it out over All`() {
        setScreen()
        composeRule.onNodeWithTag(journalSwitchTestTag(JournalTopTab.RECORDS)).let { node ->
            val (x, y) = centreOf(node.getUnclippedBoundsInRoot())
            tapAt(x, y)
        }
        settle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.ALL)).let { node ->
            val (x, y) = centreOf(node.getUnclippedBoundsInRoot())
            tapAt(x, y)
        }
        settle()
        val allLeft = composeRule.onNodeWithTag(RECORDS_LOGBOOK_LIST_TAG).getUnclippedBoundsInRoot().left
        val tile = composeRule.onNodeWithTag(logbookRowTag(RecordType.FINDS, find.id)).getUnclippedBoundsInRoot()

        pause()
        val (tx, ty) = centreOf(tile)
        tapAt(tx, ty)
        applyWrites()
        stepUntil("the find part-way in over All") {
            val over = composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(FIND_OVER_VIEW_TAG)).fetchSemanticsNodes()
            over.isNotEmpty() && leftOf(over.single()) > 1.dp && leftOf(over.single()) < width() - 1.dp
        }
        assertTrue("nothing is leaving: All does not slide out as if going back", leavingPages().isEmpty())
        assertEquals("All is in place under the arriving find", allLeft.value, composeRule.onNodeWithTag(RECORDS_LOGBOOK_LIST_TAG).getUnclippedBoundsInRoot().left.value, 1f)
        settle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.ALL)).assertIsSelected()

        pause()
        back()
        applyWrites()
        stepUntil("the find part-way out") {
            leavingPages().any { leftOf(it) > 1.dp && leftOf(it) < width() - 1.dp }
        }
        assertEquals("All is in place under the leaving find", allLeft.value, composeRule.onNodeWithTag(RECORDS_LOGBOOK_LIST_TAG).getUnclippedBoundsInRoot().left.value, 1f)
        settle()
        composeRule.onNodeWithTag(FIND_OVER_VIEW_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.ALL)).assertIsSelected()
    }

    // Amendment 1 (RECORD -681).

    /**
     * Rewritten for RECORD -868 and -871: Records opens on Finds, and Back from another chip returns to Finds, so the
     * base page the chip lists slide over is Finds. This was "… slides in over All, and Back slides it out with All in
     * place": it started on All, asserted All leaving as Waypoints arrived, and asserted the All logbook at the left
     * edge while Waypoints slid out and present after. It now starts on Finds and asserts the same of the Finds grid.
     */
    @Test
    fun `a Records chip's list slides in over Finds, and Back slides it out with Finds in place`() {
        setScreen()
        composeRule.onNodeWithTag(journalSwitchTestTag(JournalTopTab.RECORDS)).let { node ->
            val (x, y) = centreOf(node.getUnclippedBoundsInRoot())
            tapAt(x, y)
        }
        settle()
        // Records opens on Finds when nothing else was chosen; select Finds to be sure of the start.
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.FINDS)).let { node ->
            val (x, y) = centreOf(node.getUnclippedBoundsInRoot())
            tapAt(x, y)
        }
        settle()
        val findsGridLeft = composeRule.onNodeWithTag(FINDS_GRID_TAG).getUnclippedBoundsInRoot().left
        val chipRowTop = composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.WAYPOINTS)).getUnclippedBoundsInRoot().top

        pause()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.WAYPOINTS)).let { node ->
            val (x, y) = centreOf(node.getUnclippedBoundsInRoot())
            tapAt(x, y)
        }
        applyWrites()
        stepUntil("the Waypoints list part-way in") {
            val arriving = pageSlidingIn()
            arriving != null && leftOf(arriving) < width() - 1.dp
        }
        assertTrue("Finds is still there, leaving", leavingPages().isNotEmpty())
        assertEquals("the chip row stays still", chipRowTop.value, composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.WAYPOINTS)).getUnclippedBoundsInRoot().top.value, 0.5f)
        settle()

        pause()
        back()
        applyWrites()
        stepUntil("the Waypoints list part-way out") {
            leavingPages().any { leftOf(it) > 1.dp && leftOf(it) < width() - 1.dp }
        }
        assertEquals("the Finds grid is in place under the leaving list", findsGridLeft.value, composeRule.onNodeWithTag(FINDS_GRID_TAG).getUnclippedBoundsInRoot().left.value, 1f)
        settle()
        composeRule.onNodeWithTag(FINDS_GRID_TAG).assertExists()
    }

    @Test
    @Config(qualifiers = "w823dp-h384dp-land")
    fun `in a short window the switch row stays still while only the page below it slides`() {
        setScreen()
        val switchBefore = composeRule.onNodeWithTag(JOURNAL_SWITCH_TAG).getUnclippedBoundsInRoot()
        val records = composeRule.onNodeWithTag(journalSwitchTestTag(JournalTopTab.RECORDS)).getUnclippedBoundsInRoot()

        pause()
        val (rx, ry) = centreOf(records)
        tapAt(rx, ry)
        applyWrites()
        stepUntil("Records part-way in") {
            val arriving = pageSlidingIn()
            arriving != null && leftOf(arriving) < width() - 1.dp
        }
        val switchMid = composeRule.onNodeWithTag(JOURNAL_SWITCH_TAG).getUnclippedBoundsInRoot()
        assertEquals("the switch has not moved sideways", switchBefore.left.value, switchMid.left.value, 0.5f)
        assertEquals("nor up or down", switchBefore.top.value, switchMid.top.value, 0.5f)
        // One switch, not one in each page.
        assertEquals(1, composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(JOURNAL_SWITCH_TAG)).fetchSemanticsNodes().size)
        settle()
        composeRule.onNodeWithTag(journalSwitchTestTag(JournalTopTab.RECORDS)).assertIsSelected()
    }
}

private val NO_MAP_DATA = CartographyEntryMapData(
    trackPolylines = emptyList(),
    findMarkers = emptyList(),
    waypointMarkers = emptyList(),
    photoMarkers = emptyList(),
    offlineRegionCircles = emptyList(),
)
