package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.ui.adaptive.ProvideConfigurationAndWindow
import android.app.Application
import android.content.ComponentName
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.FindDecision
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.log.CARTOGRAPHY_MAP_TEST_TAG
import com.zynergylabs.forager.app.ui.map.MapSlot
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
 * Journal redesign J5, short windows (`prompts/preserved/2026-09-27-26.md`; plan L1-L3 and the
 * owner's ruling 1, "Hide header; icon reveals it (Recommended)"), driven through the real
 * [AvailabilityScreen] at `w823dp-h384dp-land`, both rotations where the rail's side matters.
 *
 * - The app-wide search header is hidden on the Journal tab in a short window, stays on other tabs
 *   and in portrait, and L1's search icon brings it up.
 * - L1: one pinned 48 dp row holding the Entries | Records switch, the search icon and "New".
 * - L2: no floating button; "New" (timeline) and the photo button (album) sit in the L1 row.
 * - L3: the drafts chip in a second row with the view toggle, hiding on a scroll down and
 *   returning on a scroll up.
 * - The height budget (the dispatch's "before building"), measured and asserted to leave two card
 *   rows.
 *
 * Every control a finger taps is touched with `performTouchInput` at several points across its own
 * bounds (CLAUDE.md, "A semantic `performClick` asserts wiring, not routing"); a rail item or the
 * Records switch used only to *get to* a screen may be a semantic click. Tags are literals, so these
 * compile against the base they were written before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class AvailabilityScreenJournalShortWindowTest {

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

    private var rotationSeenByScreen: Int? = null
    private val startedEntries = mutableListOf<LocalDate>()
    private val openedEntries = mutableListOf<String>()
    private val cameraTargets = mutableListOf<com.zynergylabs.forager.app.ui.log.InAppCameraTarget>()

    private fun setScreen(
        rotation: Int,
        entries: List<CartographyEntry> = MANY_ENTRIES,
        drafts: List<CartographyEntry> = emptyList(),
        entryMapData: com.zynergylabs.forager.app.domain.CartographyEntryMapData? = null,
    ) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent {
            rotationSeenByScreen = LocalView.current.display?.rotation
            var cartography by androidx.compose.runtime.remember { mutableStateOf(CartographyUiState(entries = entries, draftEntries = drafts)) }
            ShortWindowScreenUnderTest(
                cartography = cartography,
                onStart = { date -> startedEntries += date },
                onOpen = { id ->
                    openedEntries += id
                    cartography = cartography.copy(editingEntry = (cartography.entries + cartography.draftEntries).first { it.id == id })
                },
                onClose = { cartography = cartography.copy(editingEntry = null) },
                onOpenCamera = { target -> cameraTargets += target },
                entryMapData = entryMapData,
            )
        }
        composeRule.waitForIdle()
        assertEquals("the screen must actually see the rotation this test is about", rotation, rotationSeenByScreen)
    }

    private fun openJournal() {
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Journal").assertIsSelected()
    }

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)
    private fun bounds(tag: String): DpRect = node(tag).getUnclippedBoundsInRoot()
    private fun exists(tag: String): Boolean = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** A real touch at a fraction of [tag]'s own bounds. */
    private fun touch(tag: String, fraction: Offset) {
        val r = bounds(tag)
        val x = (r.left + (r.right - r.left) * fraction.x).value
        val y = (r.top + (r.bottom - r.top) * fraction.y).value
        composeRule.onRoot().performTouchInput { click(Offset(x * density, y * density)) }
        composeRule.waitForIdle()
    }

    // ── Ruling 1: the app-wide search header ──

    private fun checkHeaderHiddenOnJournal(rotation: Int) {
        setScreen(rotation)
        openJournal()
        assertTrue("the app-wide search header is hidden on the Journal tab in a short window", !exists(SEARCH_ENTRY_BAR_TAG))
        node(SWITCH_RECORDS).performClick()
        composeRule.waitForIdle()
        assertTrue("and on Records", !exists(SEARCH_ENTRY_BAR_TAG))
    }

    @Test fun `ruling 1 at ROTATION_90 the search header is hidden on the Journal tab in a short window`() = checkHeaderHiddenOnJournal(Surface.ROTATION_90)
    @Test fun `ruling 1 at ROTATION_270 the search header is hidden on the Journal tab in a short window`() = checkHeaderHiddenOnJournal(Surface.ROTATION_270)

    @Test
    fun `ruling 1 the search header still shows on List and Seasonal in a short window`() {
        setScreen(Surface.ROTATION_90)
        composeRule.onNodeWithText("List").performClick()
        composeRule.waitForIdle()
        node(SEARCH_ENTRY_BAR_TAG).assertIsDisplayed()
        openJournal()
        composeRule.onNodeWithText("Seasonal").performClick()
        composeRule.waitForIdle()
        node(SEARCH_ENTRY_BAR_TAG).assertIsDisplayed()
    }

    private fun checkSearchIconReveals(rotation: Int) {
        setScreen(rotation)
        openJournal()
        var revealed = 0
        for (fraction in TOUCH_SAMPLES) {
            assertTrue("hidden before the touch", !exists(SEARCH_ENTRY_BAR_TAG))
            touch(SEARCH_ICON, fraction)
            node(SEARCH_ENTRY_BAR_TAG).assertIsDisplayed()
            revealed++
            // Dismissed by the same icon.
            touch(SEARCH_ICON, Offset(0.5f, 0.5f))
            assertTrue("the icon touched again hides the header", !exists(SEARCH_ENTRY_BAR_TAG))
        }
        assertEquals("every sampled point revealed the header", TOUCH_SAMPLES.size, revealed)
        // Dismissed by Back too.
        touch(SEARCH_ICON, Offset(0.5f, 0.5f))
        node(SEARCH_ENTRY_BAR_TAG).assertIsDisplayed()
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        assertTrue("Back hides the revealed header", !exists(SEARCH_ENTRY_BAR_TAG))
        composeRule.onNodeWithText("Journal").assertIsSelected()
    }

    @Test fun `ruling 1 at ROTATION_90 real touches on the search icon reveal the header, and the icon or Back hides it`() = checkSearchIconReveals(Surface.ROTATION_90)
    @Test fun `ruling 1 at ROTATION_270 real touches on the search icon reveal the header, and the icon or Back hides it`() = checkSearchIconReveals(Surface.ROTATION_270)

    @Test
    fun `ruling 1 on Records the search icon reveals the header too`() {
        setScreen(Surface.ROTATION_90)
        openJournal()
        node(SWITCH_RECORDS).performClick()
        composeRule.waitForIdle()
        touch(SEARCH_ICON, Offset(0.3f, 0.6f))
        node(SEARCH_ENTRY_BAR_TAG).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `ruling 1 portrait keeps the search header on the Journal tab, and has no L1 row`() {
        setScreen(Surface.ROTATION_0)
        openJournal()
        node(SEARCH_ENTRY_BAR_TAG).assertIsDisplayed()
        assertTrue("portrait has no short-window header row", !exists(SHORT_HEADER))
        assertTrue("portrait has no search icon of its own", !exists(SEARCH_ICON))
        // Portrait's own chrome is unchanged: the floating button and the full-width switch.
        node(ENTRIES_FAB).assertIsDisplayed()
        node(SWITCH).assertIsDisplayed()
    }

    // ── RECORD -1244: the revealed header slides down, and up when it hides ──

    /**
     * The owner (RECORD -1244): "And have the search bar slide up and down when revealing/hiding". On the test clock: a frame part
     * way through each transition shows the pill partly above its place and the row below it part way down, so it is animated, not
     * a jump; the end states are the pill at its place (the left, as on List and Seasonal) and then no pill.
     */
    @Test
    fun `RECORD -1244 the revealed header slides down into place, and slides up when hidden`() {
        setScreen(Surface.ROTATION_90)
        openJournal()
        val rowHidden = bounds(SHORT_HEADER).top
        composeRule.mainClock.autoAdvance = false
        node(SEARCH_ICON).performTouchInput { click(center) }
        repeat(4) { composeRule.mainClock.advanceTimeByFrame() }
        val midPill = bounds(SEARCH_ENTRY_BAR_TAG)
        val midRow = bounds(SHORT_HEADER).top
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        val pill = bounds(SEARCH_ENTRY_BAR_TAG)
        val rowShown = bounds(SHORT_HEADER).top
        assertTrue("revealed, the row below sits under the pill ($rowShown, pill bottom ${pill.bottom})", rowShown >= pill.bottom - 0.5.dp && rowShown > rowHidden)
        assertTrue("part way in, the pill $midPill is above its place ${pill.top}", midPill.top < pill.top - 0.5.dp)
        assertTrue("part way in, the row ($midRow) is between its two places ($rowHidden, $rowShown)", midRow > rowHidden + 0.5.dp && midRow < rowShown - 0.5.dp)
        assertEquals("the pill takes the left place, as on the other tabs (no insets: the window's left)", 0f, pill.left.value, 0.5f)

        composeRule.mainClock.autoAdvance = false
        node(SEARCH_ICON).performTouchInput { click(center) }
        repeat(4) { composeRule.mainClock.advanceTimeByFrame() }
        val outPill = bounds(SEARCH_ENTRY_BAR_TAG)
        val outRow = bounds(SHORT_HEADER).top
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertTrue("part way out, the pill $outPill is above its place ${pill.top}", outPill.top < pill.top - 0.5.dp)
        assertTrue("part way out, the row ($outRow) is between its two places", outRow > rowHidden + 0.5.dp && outRow < rowShown - 0.5.dp)
        assertTrue("hidden at the end", !exists(SEARCH_ENTRY_BAR_TAG))
        assertEquals("the row is back at the top", rowHidden.value, bounds(SHORT_HEADER).top.value, 0.5f)
    }

    // ── RECORD -1327 to -1330: an open Journal entry hides the search bar on the Journal only ──

    private fun assertPillOnScreen(where: String) {
        assertTrue("$where: the search pill is composed", exists(SEARCH_ENTRY_BAR_TAG))
        node(SEARCH_ENTRY_BAR_TAG).assertIsDisplayed()
        val pill = bounds(SEARCH_ENTRY_BAR_TAG)
        assertTrue("$where: the search pill has a size ($pill)", pill.right - pill.left > 40.dp && pill.bottom - pill.top > 20.dp)
        // F1's focus race (LeavingTheJournalFixesTest, RECORD -692): the bar coming back must not take focus and open its dropdown.
        assertTrue("$where: the bar's dropdown has not opened by itself", !exists(SEARCH_DROPDOWN_TAG))
    }

    private fun goTo(tab: String) {
        composeRule.onNodeWithText(tab).performClick()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(tab).assertIsSelected()
    }

    private fun openFirstEntry() {
        composeRule.onNodeWithTag(cardTag(MANY_ENTRIES[0].id)).performClick()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("positive control: the entry opened", listOf(MANY_ENTRIES[0].id), openedEntries)
    }

    @Test
    fun `RECORD -1330 from the start, List and Seasonal show the search pill in landscape`() {
        setScreen(Surface.ROTATION_90)
        goTo("List")
        assertPillOnScreen("List")
        goTo("Seasonal")
        assertPillOnScreen("Seasonal")
    }

    @Test
    fun `RECORD -1330 after the Journal with the pill hidden, List and Seasonal show the search pill`() {
        setScreen(Surface.ROTATION_90)
        openJournal()
        assertTrue("positive control: hidden on the Journal", !exists(SEARCH_ENTRY_BAR_TAG))
        goTo("List")
        assertPillOnScreen("List after the Journal")
        goTo("Seasonal")
        assertPillOnScreen("Seasonal after the Journal")
    }

    /** The owner's S26 path (RECORD -1330): an entry open on the Journal, then List, then Seasonal. Failed on every build until -1330. */
    @Test
    fun `RECORD -1330 with a Journal entry left open, List and Seasonal show the search pill`() {
        setScreen(Surface.ROTATION_90)
        openJournal()
        openFirstEntry()
        goTo("List")
        assertPillOnScreen("List with a Journal entry open")
        goTo("Seasonal")
        assertPillOnScreen("Seasonal with a Journal entry open")
    }

    /** -692 kept: back on the Journal with the entry still open, the bar and the search icon stay hidden there. */
    @Test
    fun `RECORD -1330 back on the Journal with the entry still open, the bar and the search icon stay hidden`() {
        setScreen(Surface.ROTATION_90)
        openJournal()
        openFirstEntry()
        assertTrue("on the open entry: no bar", !exists(SEARCH_ENTRY_BAR_TAG))
        goTo("List")
        assertPillOnScreen("List with a Journal entry open")
        goTo("Journal")
        assertTrue("back on the open entry: no bar", !exists(SEARCH_ENTRY_BAR_TAG))
        assertTrue("back on the open entry: no search icon", !exists(SEARCH_ICON))
    }

    // ── RECORD -1330 to -1333: the switch line slides down with the pill; a map under it does not move ──

    private fun near(a: DpRect, b: DpRect) = listOf(a.left - b.left, a.top - b.top, a.right - b.right, a.bottom - b.bottom).all { kotlin.math.abs(it.value) <= 0.5f }

    private fun checkOfflineMapsReveal(rotation: Int) {
        setScreen(rotation)
        openJournal()
        node(SWITCH_RECORDS).performClick()
        composeRule.waitForIdle()
        node(CHIP_OFFLINE).performClick()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertTrue("hidden to begin with", !exists(SEARCH_ENTRY_BAR_TAG))
        val lineHidden = bounds(SHORT_HEADER)
        val map = bounds(SHORT_STUB_MAP_TAG)
        assertEquals("search hidden: the switch line is at the top, with no strip above it", 0f, lineHidden.top.value, 0.5f)

        composeRule.mainClock.autoAdvance = false
        touch(SEARCH_ICON, Offset(0.5f, 0.5f))
        val inFrames = (1..6).map { composeRule.mainClock.advanceTimeByFrame(); bounds(SHORT_HEADER) to bounds(SHORT_STUB_MAP_TAG) }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        val pill = bounds(SEARCH_ENTRY_BAR_TAG)
        val lineShown = bounds(SHORT_HEADER)
        println("MEASURED -1333 rotation=$rotation: line hidden $lineHidden, shown $lineShown, pill $pill, map $map, frames ${inFrames.map { it.first.top.value }}")
        assertTrue("positive control: part way in, the switch line is between its two places", inFrames.any { it.first.top > lineHidden.top + 0.5.dp && it.first.top < lineShown.top - 0.5.dp })
        for ((i, f) in inFrames.withIndex()) assertTrue("frame ${i + 1} in: the map keeps its bounds ($map, then ${f.second})", near(map, f.second))
        assertEquals("search open: the switch line has moved down by the bar's height", (pill.bottom - pill.top).value, (lineShown.top - lineHidden.top).value, 0.5f)
        assertTrue("search open: the map keeps its bounds ($map, then ${bounds(SHORT_STUB_MAP_TAG)})", near(map, bounds(SHORT_STUB_MAP_TAG)))
        assertTrue("search open: the bar $pill does not reach the map $map", pill.bottom <= map.top + 0.5.dp)
        assertTrue("search open: the switch line $lineShown does not reach the map $map", lineShown.bottom <= map.top + 0.5.dp)
        assertEquals("search open: the small gap between the switch line and the map", JOURNAL_MAP_TOP_GAP.value, (map.top - lineShown.bottom).value, 0.5f)

        composeRule.mainClock.autoAdvance = false
        touch(SEARCH_ICON, Offset(0.5f, 0.5f))
        val outFrames = (1..6).map { composeRule.mainClock.advanceTimeByFrame(); bounds(SHORT_STUB_MAP_TAG) }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        for ((i, f) in outFrames.withIndex()) assertTrue("frame ${i + 1} out: the map keeps its bounds ($map, then $f)", near(map, f))
        assertTrue("hidden again", !exists(SEARCH_ENTRY_BAR_TAG))
        assertEquals("search closed: the switch line is back at the top", lineHidden.top.value, bounds(SHORT_HEADER).top.value, 0.5f)
        assertTrue("search closed: the map keeps its bounds", near(map, bounds(SHORT_STUB_MAP_TAG)))
    }

    @Test fun `RECORD -1333 at ROTATION_90 on Offline maps the switch line slides down with the bar and the map does not move`() = checkOfflineMapsReveal(Surface.ROTATION_90)
    @Test fun `RECORD -1333 at ROTATION_270 on Offline maps the switch line slides down with the bar and the map does not move`() = checkOfflineMapsReveal(Surface.ROTATION_270)

    @Test
    fun `RECORD -1333 on Entries the switch line sits at the top and slides down with the bar, and back`() {
        setScreen(Surface.ROTATION_90)
        openJournal()
        val hidden = bounds(SHORT_HEADER)
        assertEquals("search hidden: the switch line is at the top", 0f, hidden.top.value, 0.5f)
        touch(SEARCH_ICON, Offset(0.5f, 0.5f))
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val pill = bounds(SEARCH_ENTRY_BAR_TAG)
        assertEquals("search open: the line has moved down by the bar's height", (pill.bottom - pill.top).value, (bounds(SHORT_HEADER).top - hidden.top).value, 0.5f)
        touch(SEARCH_ICON, Offset(0.5f, 0.5f))
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("search closed: the line is back at the top", hidden.top.value, bounds(SHORT_HEADER).top.value, 0.5f)
    }

    /**
     * The owner (RECORD -1333): "Make sure the map doesn't move." An open entry's map, opening and closing the entry, with search
     * hidden and with it open beforehand. The map's arrival a moment after the report opens (GrowIn, -682) stays, on the owner's
     * word (RECORD -1334), so the map is checked from the frame it reaches its place to the end of the opening, then at every frame
     * of the close while it is on screen. Search cannot show over an open entry (it is held hidden on the Journal, -692), so there
     * is no search case while the entry is open.
     */
    private fun checkEntryMapStill(searchOpenFirst: Boolean) {
        setScreen(Surface.ROTATION_90, entryMapData = ENTRY_MAP_DATA)
        openJournal()
        if (searchOpenFirst) {
            touch(SEARCH_ICON, Offset(0.5f, 0.5f))
            composeRule.mainClock.advanceTimeBy(2_000)
            composeRule.waitForIdle()
            assertTrue("positive control: search is open before the entry opens", exists(SEARCH_ENTRY_BAR_TAG))
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(cardTag(MANY_ENTRIES[0].id)).performClick()
        val opening = mutableListOf<DpRect>()
        repeat(40) {
            composeRule.mainClock.advanceTimeByFrame()
            if (exists(CARTOGRAPHY_MAP_TEST_TAG)) opening += bounds(CARTOGRAPHY_MAP_TEST_TAG)
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("positive control: the entry opened", listOf(MANY_ENTRIES[0].id), openedEntries)
        assertTrue("positive control: the entry's map is shown", exists(CARTOGRAPHY_MAP_TEST_TAG))
        val settled = bounds(CARTOGRAPHY_MAP_TEST_TAG)
        assertTrue("with the entry open, search is held hidden", !exists(SEARCH_ENTRY_BAR_TAG))
        assertEquals("with the entry open, the switch line is at the top", 0f, bounds(SHORT_HEADER).top.value, 0.5f)
        println("MEASURED -1333 entry map, search open first=$searchOpenFirst: settled $settled, opening tops ${opening.map { it.top.value }}, heights ${opening.map { (it.bottom - it.top).value }}")
        assertTrue("positive control: the map was seen while the entry opened (${opening.size} frames)", opening.isNotEmpty())
        // RECORD -1334 (the owner: "That map sliding is fine since it's acting like it's opening the map. ... Keep it."): the
        // map's arrival (GrowIn, -682) is left to play; from the frame it reaches its place, it holds there.
        val arrived = opening.indexOfFirst { near(it, settled) }
        assertTrue("the map's arrival ends in its place within the frames watched (tops ${opening.map { it.top.value }})", arrived >= 0)
        for (i in arrived until opening.size) assertTrue("opening, frame ${i + 1}, after the arrival: the map holds its place (${opening[i]}, settled $settled)", near(opening[i], settled))

        composeRule.mainClock.autoAdvance = false
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        val closing = mutableListOf<DpRect>()
        repeat(40) {
            composeRule.mainClock.advanceTimeByFrame()
            if (exists(CARTOGRAPHY_MAP_TEST_TAG)) closing += bounds(CARTOGRAPHY_MAP_TEST_TAG)
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        println("MEASURED -1333 entry map closing tops ${closing.map { it.top.value }}, heights ${closing.map { (it.bottom - it.top).value }}")
        assertTrue("positive control: the map was seen while the entry closed (${closing.size} frames)", closing.isNotEmpty())
        for ((i, f) in closing.withIndex()) {
            assertEquals("closing, frame ${i + 1}: the map's top does not move", settled.top.value, f.top.value, 0.5f)
            assertEquals("closing, frame ${i + 1}: the map's height does not change", (settled.bottom - settled.top).value, (f.bottom - f.top).value, 0.5f)
        }
        assertTrue("the entry closed", !exists(CARTOGRAPHY_MAP_TEST_TAG))
    }

    @Test fun `RECORD -1333 an open entry's map does not move as the entry opens and closes, search hidden`() = checkEntryMapStill(searchOpenFirst = false)
    @Test fun `RECORD -1333 an open entry's map does not move as the entry opens and closes, search open first`() = checkEntryMapStill(searchOpenFirst = true)

    // ── L1: one pinned 48 dp header row ──

    private fun checkHeaderRow(rotation: Int) {
        setScreen(rotation)
        openJournal()
        val row = bounds(SHORT_HEADER)
        assertEquals("the L1 row is 48 dp tall", 48f, row.height.value, 0.5f)
        for (tag in listOf(SWITCH, SEARCH_ICON, NEW_ICON)) {
            val r = bounds(tag)
            assertTrue("$tag $r sits inside the L1 row $row", r.top >= row.top - 0.5.dp && r.bottom <= row.bottom + 0.5.dp && r.left >= row.left - 0.5.dp && r.right <= row.right + 0.5.dp)
        }
        assertTrue("the switch is at the row's start, the icons after it", bounds(SWITCH).right <= bounds(SEARCH_ICON).left)
        // The switch's two segments take real touches across their bounds.
        for (fraction in TOUCH_SAMPLES) {
            touch(SWITCH_RECORDS, fraction)
            node(SWITCH_RECORDS).assertIsSelected()
            touch(SWITCH_ENTRIES, fraction)
            node(SWITCH_ENTRIES).assertIsSelected()
        }
    }

    @Test fun `L1 at ROTATION_90 one 48 dp row holds the switch, the search icon and New`() = checkHeaderRow(Surface.ROTATION_90)
    @Test fun `L1 at ROTATION_270 one 48 dp row holds the switch, the search icon and New`() = checkHeaderRow(Surface.ROTATION_270)

    @Test
    fun `L1 the header row stays pinned while the entries scroll`() {
        setScreen(Surface.ROTATION_90)
        openJournal()
        val before = bounds(SHORT_HEADER)
        composeRule.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        val after = bounds(SHORT_HEADER)
        assertEquals("the header row did not move", before.top.value, after.top.value, 0.5f)
        node(SHORT_HEADER).assertIsDisplayed()
    }

    @Test
    fun `L1 on Records the row holds the switch and the search icon, and no New`() {
        setScreen(Surface.ROTATION_270)
        openJournal()
        node(SWITCH_RECORDS).performClick()
        composeRule.waitForIdle()
        // On All: Records opens on Finds since RECORD -868, whose gallery puts "New find" in this row (RECORD -753).
        node(CHIP_ALL).performClick()
        composeRule.waitForIdle()
        node(SHORT_HEADER).assertIsDisplayed()
        node(SEARCH_ICON).assertIsDisplayed()
        assertTrue("Records has no New (portrait's Records has no floating button to move)", !exists(NEW_ICON))
    }

    // ── L2: no floating button; New and the photo button in the L1 row ──

    private fun checkNewInRow(rotation: Int) {
        setScreen(rotation)
        openJournal()
        assertTrue("no floating button in a short window", !exists(ENTRIES_FAB))
        var started = 0
        for (fraction in TOUCH_SAMPLES) {
            touch(NEW_ICON, fraction)
            started++
            assertEquals("each touch on New starts one entry today", started, startedEntries.size)
            assertEquals(LocalDate.now(), startedEntries.last())
        }
    }

    @Test fun `L2 at ROTATION_90 no floating button, and real touches on New in the row start an entry`() = checkNewInRow(Surface.ROTATION_90)
    @Test fun `L2 at ROTATION_270 no floating button, and real touches on New in the row start an entry`() = checkNewInRow(Surface.ROTATION_270)

    @Test
    fun `L2 on the album the row's photo button opens Take photo and Import, and there is no floating button`() {
        setScreen(Surface.ROTATION_90)
        openJournal()
        node(VIEW_ALBUM).performClick()
        composeRule.waitForIdle()
        assertTrue("no floating button on the album either", !exists(ENTRIES_FAB))
        assertTrue("the album's row has no New", !exists(NEW_ICON))
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(android.Manifest.permission.CAMERA)
        for ((i, fraction) in TOUCH_SAMPLES.withIndex()) {
            touch(PHOTO_ICON, fraction)
            node(MENU_TAKE_PHOTO).assertIsDisplayed()
            node(MENU_IMPORT).assertIsDisplayed()
            assertEquals("the button alone opens no camera", i, cameraTargets.size)
            // Take photo closes the menu and opens the album's camera (Back cannot reach a popup
            // window under Robolectric, so the menu is closed by using it).
            node(MENU_TAKE_PHOTO).performTouchInput { click(Offset(width * fraction.x, height * fraction.y)) }
            composeRule.waitForIdle()
            assertEquals(i + 1, cameraTargets.size)
            assertEquals(com.zynergylabs.forager.app.ui.log.InAppCameraTarget.ALBUM, cameraTargets.last())
            assertTrue("the menu closed", !exists(MENU_TAKE_PHOTO))
        }
        val row = bounds(SHORT_HEADER)
        val photo = bounds(PHOTO_ICON)
        assertTrue("the photo button $photo is in the L1 row $row", photo.top >= row.top && photo.bottom <= row.bottom)
    }

    // ── L3: the drafts chip in the second row, which hides on scroll ──

    @Test
    fun `L3 the drafts chip sits in the second row with the view toggle, and the banner is gone`() {
        setScreen(Surface.ROTATION_90, drafts = TWO_DRAFTS)
        openJournal()
        assertTrue("no drafts banner in a short window", !exists(DRAFTS_BANNER))
        composeRule.onNodeWithText("✎ 2 drafts ›").assertIsDisplayed()
        val chip = bounds(DRAFTS_CHIP)
        val toggle = bounds(VIEW_TIMELINE)
        val header = bounds(SHORT_HEADER)
        assertEquals("the chip and the toggle share one row", (chip.top + chip.bottom).value / 2, (toggle.top + toggle.bottom).value / 2, 2f)
        assertTrue("that row is below the L1 row", chip.top >= header.bottom - 0.5.dp)
    }

    @Test
    fun `L3 with several drafts, real touches on the chip open the full-screen list`() {
        var opened = 0
        for (fraction in TOUCH_SAMPLES) {
            setScreenOnce(drafts = TWO_DRAFTS)
            touch(DRAFTS_CHIP, fraction)
            node(DRAFTS_LIST).assertIsDisplayed()
            opened++
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
            composeRule.waitForIdle()
            node(DRAFTS_CHIP).assertIsDisplayed()
        }
        assertEquals(TOUCH_SAMPLES.size, opened)
    }

    @Test
    fun `L3 with one draft, a real touch on the chip opens that draft`() {
        setScreen(Surface.ROTATION_270, drafts = listOf(TWO_DRAFTS.first()))
        openJournal()
        composeRule.onNodeWithText("✎ 1 draft ›").assertIsDisplayed()
        touch(DRAFTS_CHIP, Offset(0.8f, 0.4f))
        assertEquals(listOf(TWO_DRAFTS.first().id), openedEntries)
    }

    private var screenSet = false
    private fun setScreenOnce(drafts: List<CartographyEntry>) {
        if (!screenSet) {
            setScreen(Surface.ROTATION_90, drafts = drafts)
            openJournal()
            screenSet = true
        }
    }

    @Test
    fun `L3 the second row hides on a real scroll down and returns on a scroll up`() {
        setScreen(Surface.ROTATION_90, drafts = TWO_DRAFTS)
        openJournal()
        node(VIEW_TIMELINE).assertIsDisplayed()
        node(DRAFTS_CHIP).assertIsDisplayed()
        val grid = composeRule.onNode(hasScrollToIndexAction())
        grid.performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        assertTrue("the toggle row hides on a scroll down", !exists(VIEW_TIMELINE))
        assertTrue("with the drafts chip", !exists(DRAFTS_CHIP))
        node(SHORT_HEADER).assertIsDisplayed()
        grid.performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        node(VIEW_TIMELINE).assertIsDisplayed()
        node(DRAFTS_CHIP).assertIsDisplayed()
    }

    // ── RECORD -1243: on Records, the chips are on the switch's line, in landscape ──
    //
    // The owner (RECORD -1243): "In offline maps: still have the chips be on the same line as the entries | records. But
    // centered between the entries | records and search icon, scrollable with offline maps showing and highlighted", then
    // "All of Records, landscape (Recommended)". The chip row's own line (J5's L3 second row on Records) is gone, so is its
    // hide-on-scroll: this replaces `L3 on Records the filter chip row hides on a real scroll down and returns on a scroll up`,
    // which asserted that the row hid on a scroll down, came back on a scroll up, and sat below L1.

    private fun checkChipsOnSwitchLine(rotation: Int) {
        setScreen(rotation)
        openJournal()
        node(SWITCH_RECORDS).performClick()
        composeRule.waitForIdle()
        assertEquals("one chip row", 1, composeRule.onAllNodesWithTag(CHIP_ROW).fetchSemanticsNodes().size)
        val header = bounds(SHORT_HEADER)
        val row = bounds(CHIP_ROW)
        val switch = bounds(SWITCH)
        val icon = bounds(SEARCH_ICON)
        assertTrue("the chip row $row sits in the switch's line $header", row.top >= header.top - 0.5.dp && row.bottom <= header.bottom + 0.5.dp)
        assertTrue("after the switch ($switch)", row.left >= switch.right - 0.5.dp)
        assertTrue("before the search icon ($icon)", row.right <= icon.left + 0.5.dp)
        // Centred in the row's space between the switch and the icon (the slot the row lays out there; the icons' tagged bounds
        // are their drawn size, inside a larger touch target, so their edges are not the slot's).
        val middle = bounds("journal-short-header-middle")
        assertTrue("the space $middle lies between the switch $switch and the icon $icon", middle.left >= switch.right - 0.5.dp && middle.right <= icon.left + 0.5.dp)
        assertEquals("centred in that space", ((middle.left + middle.right) / 2).value, ((row.left + row.right) / 2).value, 0.5f)
    }

    @Test fun `RECORD -1243 at ROTATION_90 the Records chips sit on the switch's line, centred before the search icon`() = checkChipsOnSwitchLine(Surface.ROTATION_90)
    @Test fun `RECORD -1243 at ROTATION_270 the Records chips sit on the switch's line, centred before the search icon`() = checkChipsOnSwitchLine(Surface.ROTATION_270)

    @Test
    fun `RECORD -1243 the selected chip is scrolled into view and highlighted when Records opens`() {
        setScreen(Surface.ROTATION_90)
        openJournal()
        node(SWITCH_RECORDS).performClick()
        composeRule.waitForIdle()
        node(CHIP_OFFLINE).performScrollTo().performClick()
        composeRule.waitForIdle()
        // Away to Entries and back: Records opens again, its chips laid out afresh.
        node(SWITCH_ENTRIES).performClick()
        composeRule.waitForIdle()
        node(SWITCH_RECORDS).performClick()
        composeRule.waitForIdle()
        val row = bounds(CHIP_ROW)
        val chip = bounds(CHIP_OFFLINE)
        assertTrue("the chip row $row is on the switch's line", row.bottom <= bounds(SHORT_HEADER).bottom + 0.5.dp)
        assertTrue("Offline maps $chip is wholly in view in the chip row $row", chip.left >= row.left - 0.5.dp && chip.right <= row.right + 0.5.dp)
        node(CHIP_OFFLINE).assertIsSelected()
    }

    @Test
    fun `RECORD -1243 on Records the chips stay on the line while the list scrolls`() {
        setScreen(Surface.ROTATION_270)
        openJournal()
        node(SWITCH_RECORDS).performClick()
        composeRule.waitForIdle()
        node(CHIP_ALL).performScrollTo().performClick()
        composeRule.waitForIdle()
        val before = bounds(CHIP_ROW)
        val list = composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("Pin 1")))
        list.performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        node(CHIP_ROW).assertIsDisplayed()
        assertEquals("the chip row did not move", before.top.value, bounds(CHIP_ROW).top.value, 0.5f)
    }

    // ── The height budget ──

    private fun checkHeightBudget(rotation: Int) {
        setScreen(rotation, drafts = TWO_DRAFTS)
        openJournal()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val l1 = bounds(SHORT_HEADER)
        val l3 = bounds(DRAFTS_CHIP).let { chip -> DpRect(chip.left, minOf(chip.top, bounds(VIEW_TIMELINE).top), chip.right, maxOf(chip.bottom, bounds(VIEW_TIMELINE).bottom)) }
        val grid = bounds(ENTRIES_GRID)
        val content = grid.bottom - grid.top
        println("J5-HEIGHT rotation=$rotation entries window=${root.height} l1=${l1.height} (${l1.top}..${l1.bottom}) l3=${l3.height} (${l3.top}..${l3.bottom}) content=$content (${grid.top}..${grid.bottom})")
        val first = bounds(cardTag(MANY_ENTRIES[0].id))
        val third = bounds(cardTag(MANY_ENTRIES[2].id))
        assertTrue("two card rows fit in the content area: the second row ($third) ends by ${grid.bottom}", third.bottom <= grid.bottom + 0.5.dp)
        assertTrue("the first card row starts in the content area", first.top >= grid.top - 0.5.dp)
        node(SWITCH_RECORDS).performClick()
        composeRule.waitForIdle()
        // Records opens on Finds since RECORD -868; the budget is measured on the All logbook, as before.
        node(CHIP_ALL).performClick()
        composeRule.waitForIdle()
        val rl1 = bounds(SHORT_HEADER)
        val chips = bounds(CHIP_ROW)
        val list = bounds(RECORDS_ALL_LIST)
        println("J5-HEIGHT rotation=$rotation records l1=${rl1.height} chips=${chips.height} (${chips.top}..${chips.bottom}) content=${list.bottom - list.top} (${list.top}..${list.bottom})")
        assertTrue("Records chrome (L1 + chip row) is under B3's 197 dp", chips.bottom < 197.dp)
    }

    /**
     * Native graphics: under Robolectric's default (legacy) graphics every glyph measures about one
     * pixel wide and text lines come out taller than real ones, so a height built from text is not a
     * real height there (found while building this stage; see the completion report). Native graphics
     * lays text out with real fonts. Still not the device's font or insets: the device figure is a
     * device item.
     */
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `height budget at ROTATION_90 the header hidden leaves two card rows on Entries`() = checkHeightBudget(Surface.ROTATION_90)

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `height budget at ROTATION_270 the header hidden leaves two card rows on Entries`() = checkHeightBudget(Surface.ROTATION_270)

    // ── L5a: the Records chips fit one line at 640 dp (continuation 2026-09-27-27, owner: "Tighter chips") ──

    private var fontScaleSeenByScreen: Float? = null

    /**
     * Records' All chip row through the real screen, with two-digit counts on All (38), Finds (12),
     * Tracks (10) and Waypoints (16) and none on Offline maps: the counts the owner's option (a) was
     * measured with in the J5 report (native graphics, "fits by about 6 dp").
     */
    private fun openRecordsWithCounts() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        composeRule.setContent {
            fontScaleSeenByScreen = androidx.compose.ui.platform.LocalDensity.current.fontScale
            ShortWindowScreenUnderTest(
                cartography = CartographyUiState(),
                onStart = {},
                onOpen = {},
                onClose = {},
                logState = com.zynergylabs.forager.app.ui.log.MushroomLogUiState(entries = (1..12).map { committedFind("find-$it") }),
                tracks = (1..10).map { recordedTrack("track-$it") },
            )
        }
        composeRule.waitForIdle()
        openJournal()
        node(SWITCH_RECORDS).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("38").assertExists()
    }

    /**
     * How far the chip row's content runs past the row: the last chip's end plus the row's own end
     * padding (taken equal to its start padding, the first chip's inset), less the row's end. Zero or
     * less means every chip, and the padding after the last, is on screen without scrolling.
     */
    private fun chipRowOverflow(): Pair<Float, String> {
        val row = bounds(CHIP_ROW)
        val first = bounds(CHIP_ALL)
        val last = bounds(CHIP_OFFLINE)
        val padding = first.left - row.left
        val contentEnd = last.right + padding
        val overflow = (contentEnd - row.right).value
        return overflow to "row ${row.left}..${row.right} (${row.right - row.left}), chips ${first.left}..${last.right}, padding $padding, content ends at $contentEnd"
    }

    /**
     * Was `L5a guard - at 640 dp the five Records chips, icons and counts, fit one line without scrolling` (continuation
     * 2026-09-27-27, the owner: "Tighter chips"), asserting a 640 dp row and no overflow. Superseded in landscape by RECORD -1243:
     * the chips share the switch's line and "Have the chips be scrollable". The landscape guard is now that the five chips stay
     * on one line, in the switch's line, and that a row narrower than its chips scrolls sideways. Portrait keeps J1's row and
     * spacing (`L5a portrait chips are unchanged`, below, unchanged).
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `L5a guard, landscape since RECORD -1243 - the five Records chips stay on one line in the switch's line, scrolling sideways`() {
        openRecordsWithCounts()
        assertEquals("this case is at the default font scale", 1f, fontScaleSeenByScreen)
        val row = bounds(CHIP_ROW)
        val header = bounds(SHORT_HEADER)
        assertTrue("the chip row $row is in the switch's line $header", row.top >= header.top - 0.5.dp && row.bottom <= header.bottom + 0.5.dp)
        val chips = listOf(CHIP_ALL, "records-chip-finds", "records-chip-tracks", CHIP_WAYPOINTS, CHIP_OFFLINE).map { bounds(it) }
        chips.forEach { assertEquals("one line", chips.first().top.value, it.top.value, 0.5f) }
        val (overflow, detail) = chipRowOverflow()
        println("J5-L5A fontScale=1.0 overflow=$overflow $detail")
        if (overflow > 0f) {
            assertTrue("a row narrower than its chips scrolls sideways: $detail", composeRule.onAllNodes(hasTestTag(CHIP_ROW) and hasScrollAction()).fetchSemanticsNodes().size == 1)
        }
    }

    /**
     * The same row at font scale 1.15. A record, not a guard, by the continuation's design: it
     * states the result either way, and the owner chose not to change the design further if it
     * overflows. It asserts only that the case ran at the scale it names.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(fontScale = 1.15f)
    fun `L5a record - at font scale 1_15 the chip row's fit is measured and stated`() {
        openRecordsWithCounts()
        assertEquals("this case runs at font scale 1.15", 1.15f, fontScaleSeenByScreen ?: 0f, 0.001f)
        val (overflow, detail) = chipRowOverflow()
        val result = if (overflow <= 0f) "fits, ${-overflow}dp to spare" else "OVERFLOWS by ${overflow}dp; the row scrolls"
        println("J5-L5A fontScale=1.15 $result: $detail")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `L5a portrait chips are unchanged - 16 dp row padding, 8 dp gaps, 24 dp icons`() {
        setScreen(Surface.ROTATION_0)
        openJournal()
        node(SWITCH_RECORDS).performClick()
        composeRule.waitForIdle()
        val row = bounds(CHIP_ROW)
        val all = bounds(CHIP_ALL)
        val finds = bounds("records-chip-finds")
        assertEquals("row padding", 16f, (all.left - row.left).value, 0.5f)
        assertEquals("gap between chips", 8f, (finds.left - all.right).value, 0.5f)
        val icon = composeRule.onNode(hasTestTag(CHIP_ALL), useUnmergedTree = true).onChildren()[0].getUnclippedBoundsInRoot()
        assertEquals("leading icon", 24f, (icon.right - icon.left).value, 0.5f)
    }

    // ── L7: state survives turning between portrait and a short landscape window ──

    /**
     * The manifest handles orientation (`configChanges`, J0 A3), so a rotation is a configuration
     * change inside one composition, not a recreation: this swaps [LocalConfiguration] in place, as
     * `AvailabilityScreenShortLandscapeTest` does, and the rotation with it.
     */
    private var landscape by mutableStateOf(false)

    private fun setTurnableScreen(drafts: List<CartographyEntry> = emptyList()) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_0)
        composeRule.setContent {
            val base = androidx.compose.ui.platform.LocalConfiguration.current
            val configuration = if (!landscape) {
                base
            } else {
                android.content.res.Configuration(base).apply {
                    orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    screenWidthDp = 823
                    screenHeightDp = 384
                }
            }
            var cartography by androidx.compose.runtime.remember { mutableStateOf(CartographyUiState(entries = MANY_ENTRIES, draftEntries = drafts)) }
            ProvideConfigurationAndWindow(configuration) {
                ShortWindowScreenUnderTest(
                    cartography = cartography,
                    onStart = { date -> startedEntries += date },
                    onOpen = { id -> cartography = cartography.copy(editingEntry = (cartography.entries + cartography.draftEntries).first { it.id == id }) },
                    onClose = { cartography = cartography.copy(editingEntry = null) },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun turn(toLandscape: Boolean) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(if (toLandscape) Surface.ROTATION_90 else Surface.ROTATION_0)
        landscape = toLandscape
        composeRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `L7 the Journal's switch, Records chip and Entries view survive turning to short landscape and back`() {
        setTurnableScreen()
        openJournal()
        node(SWITCH_RECORDS).performClick()
        composeRule.waitForIdle()
        node(CHIP_WAYPOINTS).performClick()
        composeRule.waitForIdle()

        turn(toLandscape = true)
        node(COMPACT_NAVIGATION_RAIL_TAG).assertIsDisplayed()
        node(SWITCH_RECORDS).assertIsSelected()
        node(CHIP_WAYPOINTS).assertIsSelected()

        node(SWITCH_ENTRIES).performClick()
        composeRule.waitForIdle()
        node(VIEW_ALBUM).performClick()
        composeRule.waitForIdle()

        turn(toLandscape = false)
        assertTrue("portrait again: no rail", !exists(COMPACT_NAVIGATION_RAIL_TAG))
        node(SWITCH_ENTRIES).assertIsSelected()
        node(VIEW_ALBUM).assertIsOn()
        node(SWITCH_RECORDS).performClick()
        composeRule.waitForIdle()
        node(CHIP_WAYPOINTS).assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `L7 an entry open in its editor stays open in its editor across the turn, both ways`() {
        setTurnableScreen(drafts = listOf(TWO_DRAFTS.first()))
        openJournal()
        node(DRAFTS_CONTINUE).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your own account (optional)").assertIsDisplayed()

        turn(toLandscape = true)
        node(COMPACT_NAVIGATION_RAIL_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Your own account (optional)").assertIsDisplayed()

        turn(toLandscape = false)
        composeRule.onNodeWithText("Your own account (optional)").assertIsDisplayed()
    }
}

// ── fixtures ──

private const val SEARCH_ICON = "journal-short-search"
private const val SHORT_HEADER = "journal-short-header"
private const val NEW_ICON = "journal-short-new"
private const val PHOTO_ICON = "journal-short-photo"
private const val SWITCH = "journal-switch"
private const val SWITCH_ENTRIES = "journal-switch-entries"
private const val SWITCH_RECORDS = "journal-switch-records"
private const val ENTRIES_FAB = "entries-fab"
private const val VIEW_ALBUM = "entries-view-album"
private const val VIEW_TIMELINE = "entries-view-timeline"
private const val MENU_TAKE_PHOTO = "entries-fab-menu-take-photo"
private const val MENU_IMPORT = "entries-fab-menu-import"
private const val DRAFTS_BANNER = "entries-drafts-banner"
private const val DRAFTS_CHIP = "entries-drafts-chip"
private const val DRAFTS_LIST = "entries-drafts-list"
private const val DRAFTS_CONTINUE = "entries-drafts-continue"
private const val CHIP_WAYPOINTS = "records-chip-waypoints"
private const val CHIP_ALL = "records-chip-all"
private const val CHIP_OFFLINE = "records-chip-offline-maps"

private fun committedFind(id: String): com.zynergylabs.forager.app.domain.model.MushroomLogEntry =
    com.zynergylabs.forager.app.domain.model.MushroomLogEntry.draft(id = id, location = null, date = LocalDate.of(2026, 9, 20)).copy(isDraft = false)

private fun recordedTrack(id: String): com.zynergylabs.forager.app.domain.model.Track = com.zynergylabs.forager.app.domain.model.Track(
    id = id,
    name = null,
    startedAtEpochMillis = 1_758_200_000_000L,
    endedAtEpochMillis = 1_758_203_600_000L,
    points = emptyList(),
)
private const val CHIP_ROW = "records-filter-chip-row"
private const val ENTRIES_GRID = "entries-grid"
private const val RECORDS_ALL_LIST = "records-logbook-list"
private fun cardTag(id: String) = "entry-card-$id"

/** Three touches spread across a control: near its start edge, its centre, near its end edge, at differing heights. */
private val TOUCH_SAMPLES = listOf(Offset(0.15f, 0.3f), Offset(0.5f, 0.5f), Offset(0.85f, 0.7f))

private fun committed(id: String, date: LocalDate): CartographyEntry =
    CartographyEntry.draft(id = id, date = date, updatedAtEpochMillis = 0L).copy(isDraft = false)

/** Twenty entries with writing and a kept find, newest first: more than a short window holds. */
private val MANY_ENTRIES: List<CartographyEntry> = (20 downTo 1).map { day ->
    committed("sep-$day", LocalDate.of(2026, 9, day)).copy(
        text = "Walk on the $day\nsecond line",
        findDecisions = listOf(FindDecision(findId = "f$day", foundOn = LocalDate.of(2026, 9, day), ownIdentification = "C. formosus", hasPhotos = false, kept = true)),
    )
}

private val TWO_DRAFTS: List<CartographyEntry> = listOf(
    CartographyEntry.draft(id = "draft-a", date = LocalDate.of(2026, 9, 25), updatedAtEpochMillis = 2L),
    CartographyEntry.draft(id = "draft-b", date = LocalDate.of(2026, 9, 24), updatedAtEpochMillis = 1L),
)

private val SHORT_STUB_MAP: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag(SHORT_STUB_MAP_TAG)) }

/** The stub map's tag (RECORD -1330's gap test measures the Offline maps picker's map). */
private const val SHORT_STUB_MAP_TAG = "short-stub-map"

/** An entry map with one waypoint, so an open entry's report draws its map (RECORD -1333's entry check). */
private val ENTRY_MAP_DATA = com.zynergylabs.forager.app.domain.CartographyEntryMapData(
    emptyList(), emptyList(),
    listOf(com.zynergylabs.forager.app.domain.model.RecordPoint("wp-entry", com.zynergylabs.forager.app.domain.model.LatLng(45.52, -122.68))),
    emptyList(), emptyList(),
)

/** Enough waypoints that the All logbook scrolls in a short window. */
private val MANY_WAYPOINTS = (1..16).map { i ->
    com.zynergylabs.forager.app.domain.model.Waypoint(
        id = "w$i", lat = 45.0, lng = -122.0, altitude = null, name = "Pin $i", note = "",
        createdAtEpochMillis = 1_790_000_000_000L - i * 86_400_000L,
    )
}

@androidx.compose.runtime.Composable
private fun ShortWindowScreenUnderTest(
    cartography: CartographyUiState,
    onStart: (LocalDate) -> Unit,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    onOpenCamera: (com.zynergylabs.forager.app.ui.log.InAppCameraTarget) -> Unit = {},
    logState: com.zynergylabs.forager.app.ui.log.MushroomLogUiState = com.zynergylabs.forager.app.ui.log.MushroomLogUiState(),
    tracks: List<com.zynergylabs.forager.app.domain.model.Track> = emptyList(),
    entryMapData: com.zynergylabs.forager.app.domain.CartographyEntryMapData? = null,
) {
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
        mapSlot = SHORT_STUB_MAP,
        journalEntries = AvailabilityScreenJournalEntries(
            cartographyUiState = cartography,
            onStartCartographyEntry = onStart,
            onOpenCartographyEntry = onOpen,
            onCloseCartographyEntry = onClose,
        ),
        cameraInputs = AvailabilityScreenCamera(onOpenCamera = onOpenCamera),
        findsLog = AvailabilityScreenFindsLog(
            logUiState = logState,
        ),
        records = AvailabilityScreenRecords(
            waypoints = MANY_WAYPOINTS,
            tracks = tracks,
        ),
        entryReportData = AvailabilityScreenEntryReportData(
            getCartographyEntryMapData = { _, _ ->
                entryMapData ?: com.zynergylabs.forager.app.domain.CartographyEntryMapData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
            },
        ),
    )
}
