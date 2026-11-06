package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

/*
 * Dispatch 2026-10-18-01, item 2 (the owner, S22 check group 2, item 1: "Maps tab is fine, but the other tabs have the rail off
 * from the edge. On all tabs: Either have the rail extend fully to the side so there's no cutoff, or move the rail to the edge
 * like the maps tab has it. I propose the first, since it has to extend on the other side to accommodate the navbar anyway").
 *
 * In a landscape window the rail's own surface reaches the screen's edge on its side on every tab, covering the camera
 * cut-out's band there, and its items stay clear of that band. Until this dispatch the docked rail beside List, Seasonal and
 * Journal sat inside the content insets, which reserved the cut-out on both sides, so with the punch hole on the rail's side a
 * strip of page background showed between the rail and the edge; the Maps rail, an overlay on the full-bleed map, reached the
 * edge but padded its items by the navigation bar only.
 *
 * Driven through the real screen. Robolectric reports zero insets (CLAUDE.md, "Known pitfalls"), so a real display-cut-out inset
 * is dispatched to the screen after it is up, as TabsMapChromeShapeLandscapeNavBarLeftTest dispatches its navigation bar. Where
 * the S22's punch hole really falls, and how wide its band is, stays the device check's.
 */
abstract class RailReachesScreenEdgeTests(
    private val cutoutLeft: Dp,
    private val cutoutRight: Dp,
) {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(emptyList())
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()

    private fun setScreen() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        composeRule.setContent { MapChromeTestScreen(state, map, roles) }
        composeRule.waitForIdle()
        val density = composeRule.activity.resources.displayMetrics.density
        val insets = WindowInsetsCompat.Builder()
            .setInsets(
                WindowInsetsCompat.Type.displayCutout(),
                Insets.of((cutoutLeft.value * density).toInt(), 0, (cutoutRight.value * density).toInt(), 0),
            )
            .build()
        composeRule.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(composeRule.activity.window.decorView.findViewById(android.R.id.content), insets)
        }
        composeRule.waitForIdle()
    }

    /** Chooses [label] on the rail (a semantic click: it only gets the test to the tab; the rail's touches are not the claim). */
    private fun open(label: String) {
        composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(COMPACT_NAVIGATION_RAIL_TAG)), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    private fun rail(): DpRect = composeRule.onNodeWithTag(COMPACT_NAVIGATION_RAIL_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
    private fun window(): DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()

    /** The five items' touch targets and their labels, each read in root coordinates. */
    private fun railItems(): List<Pair<String, DpRect>> {
        val inRail = hasAnyAncestor(hasTestTag(COMPACT_NAVIGATION_RAIL_TAG))
        val targets = composeRule.onAllNodes(inRail and hasClickAction()).fetchSemanticsNodes().map { node ->
            "item ${node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }}" to node.boundsInRoot
        }
        val labels = CompactTab.entries.map { tab ->
            "label ${tab.label}" to composeRule.onNode(hasText(tab.label) and inRail, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        }
        assertEquals("five items on the rail", CompactTab.entries.size, targets.size)
        val density = composeRule.activity.resources.displayMetrics.density
        return (targets + labels).map { (name, r) -> name to DpRect((r.left / density).dp, (r.top / density).dp, (r.right / density).dp, (r.bottom / density).dp) }
    }

    private fun checkRail(label: String) {
        setScreen()
        if (label != "Maps") open(label)
        val rail = rail()
        val window = window()
        // The rail is on the screen's right in a landscape window (mainWindowRailEdgeFor, RECORD -956).
        assertEquals("$label: the rail's surface reaches the screen's right edge (rail $rail, window $window)", window.right.value, rail.right.value, 0.5f)
        railItems().forEach { (name, r) ->
            assertTrue(
                "$label: the rail's $name ($r) is clear of the ${cutoutRight} cut-out band on the right (ends by ${window.right - cutoutRight})",
                r.right.value <= (window.right - cutoutRight).value + 0.5f,
            )
            assertTrue("$label: the rail's $name ($r) is inside the rail ($rail)", r.left.value >= rail.left.value - 0.5f)
        }
    }

    @Test fun `on List the rail reaches the screen edge and its items are clear of the cut-out`() = checkRail("List")
    @Test fun `on Seasonal the rail reaches the screen edge and its items are clear of the cut-out`() = checkRail("Seasonal")
    @Test fun `on Journal the rail reaches the screen edge and its items are clear of the cut-out`() = checkRail("Journal")
    @Test fun `on Maps the rail reaches the screen edge and its items are clear of the cut-out`() = checkRail("Maps")

    /** The owner's comparison: Maps is the reference. The rail does not move across the tab switch. */
    @Test
    fun `Maps to Seasonal and back, the rail keeps the same bounds`() {
        setScreen()
        val onMaps = rail()
        open("Seasonal")
        val onSeasonal = rail()
        assertEquals("left", onMaps.left.value, onSeasonal.left.value, 0.5f)
        assertEquals("right", onMaps.right.value, onSeasonal.right.value, 0.5f)
        open("Maps")
        assertEquals("back on Maps: left", onMaps.left.value, rail().left.value, 0.5f)
    }

    /**
     * The page content beside the rail stays out of a cut-out band on the side away from the rail, and out from under the
     * rail. With no cut-out on the left this only pins that the content starts at the window's left or later.
     */
    @Test
    fun `on Seasonal the page content is clear of a left cut-out and of the rail`() {
        setScreen()
        open("Seasonal")
        val content = composeRule.onNodeWithTag(SEASONAL_CONTENT_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the content ($content) starts clear of the left cut-out ($cutoutLeft)", content.left.value >= cutoutLeft.value - 0.5f)
        assertTrue("the content ($content) ends before the rail (${rail()})", content.right.value <= rail().left.value + 0.5f)
    }
}

/** The bug's case: the punch hole on the rail's side, 32 dp (an assumed band; the S22's own is the device check's). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RailReachesScreenEdgeCutoutRightTest : RailReachesScreenEdgeTests(cutoutLeft = 0.dp, cutoutRight = 32.dp)

/** The other landscape direction: the punch hole away from the rail, beside the page content. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RailReachesScreenEdgeCutoutLeftTest : RailReachesScreenEdgeTests(cutoutLeft = 32.dp, cutoutRight = 0.dp)
