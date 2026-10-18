package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.DpRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-10-18-03, item 3 (Forager RECORD -1269; the owner: "Fix item 4 too"). Elder's top-up audit, item 4: a
 * `key(compactTab())` around the search pill's AnimatedVisibility re-created the pill on every tab switch, so anything it held
 * itself (the field's focus, its cursor and selection) reset, while the query, hoisted in the state, survived.
 *
 * The dispatch: "Focus and the field's internal state must survive a tab switch. The query already does; keep it." And the key's
 * own job, kept by other means: arriving on a tab shows its pill as it stands, with no slide (RECORD -1244: only a reveal or a
 * hide on the Journal plays one).
 *
 * Through the real screen ([MapChromeTestScreen]: [AvailabilityScreen] in the app's theme, the query held as the ViewModel holds
 * it). The field is focused by a real touch; tabs are chosen by semantic clicks on the bottom bar or rail, which only get the
 * test to the tab (a state claim, not a routing one).
 */
abstract class SearchPillAcrossTabsTests(private val landscape: Boolean, private val rotation: Int? = null) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(emptyList())
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()

    private val navigationTag get() = if (landscape) COMPACT_NAVIGATION_RAIL_TAG else COMPACT_BOTTOM_NAV_TAG

    private fun setScreen() {
        if (rotation != null) Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent { MapChromeTestScreen(state, map, roles) }
        composeRule.waitForIdle()
    }

    private fun open(label: String) {
        composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(navigationTag)), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    private fun field(): SemanticsNodeInteraction = composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG, useUnmergedTree = true)
    private fun focused(): Boolean = field().fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }
    private fun selection(): TextRange = field().fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]

    /** On [from], a real touch focuses the field, "Ag" is typed (under three letters, so no suggestions open) and the cursor put after "A". */
    protected fun checkKept(from: String, to: String) {
        setScreen()
        open(from)
        field().performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertTrue("positive control: the touch focused the field on $from", focused())
        field().performTextInput("Ag")
        field().performTextInputSelection(TextRange(1))
        composeRule.waitForIdle()
        assertEquals("positive control: the query is in the state", "Ag", state.ui.taxonSearchQuery)
        assertEquals("positive control: the cursor is after the A", TextRange(1), selection())

        open(to)
        assertEquals("the query survives the switch to $to (it is hoisted)", "Ag", state.ui.taxonSearchQuery)
        assertTrue("the field is still focused after switching from $from to $to", focused())
        assertEquals("the field's cursor is where it was after switching from $from to $to", TextRange(1), selection())
    }

    @Test fun `List to Seasonal, the pill keeps its focus and its cursor`() = checkKept("List", "Seasonal")
    @Test fun `Seasonal to List, the pill keeps its focus and its cursor`() = checkKept("Seasonal", "List")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class SearchPillAcrossTabsPortraitTest : SearchPillAcrossTabsTests(landscape = false) {
    // In portrait the Journal shows the pill too (J5 hides it in a landscape window only).
    @Test fun `portrait, List to Journal, the pill keeps its focus and its cursor`() = checkKept("List", "Journal")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class SearchPillAcrossTabsLandscapeTest : SearchPillAcrossTabsTests(landscape = true, rotation = Surface.ROTATION_90)

/**
 * The key's job, kept: arriving on a tab shows its pill as it stands, with no slide. In a landscape window the Journal hides the
 * pill (J5) and the other tabs show it, so a switch between them flips its visibility in the same frame as the tab; the key made
 * that flip happen at once. Read on the test clock a few frames after each switch: the pill is already where it settles, or
 * already gone. The Journal's own reveal still slides (AvailabilityScreenJournalShortWindowTest, RECORD -1244).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class SearchPillArrivalNoSlideTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(emptyList())
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()

    private fun open(label: String) {
        composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(COMPACT_NAVIGATION_RAIL_TAG)), useUnmergedTree = true).performClick()
    }

    private fun selected(label: String): Boolean =
        composeRule.onAllNodes(hasText(label) and hasAnyAncestor(hasTestTag(COMPACT_NAVIGATION_RAIL_TAG))).fetchSemanticsNodes()
            .any { it.config.getOrElse(SemanticsProperties.Selected) { false } }

    /** Frames advanced, one at a time, until [label]'s rail item reads selected; fails past 30. */
    private fun framesUntilSelected(label: String): Int {
        var frames = 0
        while (!selected(label)) {
            assertTrue("$label was selected within 30 frames", frames < 30)
            composeRule.mainClock.advanceTimeByFrame()
            frames++
        }
        return frames
    }

    private fun pillShown(): Boolean = composeRule.onAllNodesWithTag(SEARCH_ENTRY_BAR_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun pill(): DpRect = composeRule.onNodeWithTag(SEARCH_ENTRY_BAR_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

    @Test
    fun `arriving on the Journal hides the pill at once, and arriving back on List shows it in place at once`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        composeRule.setContent { MapChromeTestScreen(state, map, roles) }
        composeRule.waitForIdle()
        open("List")
        composeRule.waitForIdle()
        assertTrue("positive control: the pill shows on List", pillShown())
        val settled = pill()

        composeRule.mainClock.autoAdvance = false
        open("Journal")
        // Positive control, and the reference frame: the frame on which the Journal became the selected tab. The first run of
        // this test counted three frames from the click without it, and failed on the base it was written to pass on.
        val journalAt = framesUntilSelected("Journal")
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
        assertTrue("two frames after the Journal was selected (frame $journalAt) the pill is gone, not sliding up (pill: ${if (pillShown()) pill() else "none"}, settled $settled)", !pillShown())

        open("List")
        val listAt = framesUntilSelected("List")
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
        assertTrue("two frames after List was selected (frame $listAt) the pill shows", pillShown())
        val early = pill()
        assertEquals("two frames after arriving back on List the pill is at its place, not sliding down (top)", settled.top.value, early.top.value, 0.5f)
        assertEquals("and at its full height (bottom)", settled.bottom.value, early.bottom.value, 0.5f)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }
}
