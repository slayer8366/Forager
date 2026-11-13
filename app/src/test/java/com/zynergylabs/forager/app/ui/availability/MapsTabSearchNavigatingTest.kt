package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.map.MapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.MapChromeContentColor
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Amendment R (the owner: "The try again notice for search should go into the search bar itself, in place of the October text";
 * "\"Try again\" takes Clear's place (Recommended)"; "One line, in the bar"): out of fullscreen, while navigating, a failed search is
 * in the bar, with "Try again" in Clear's place, and no notice floats. The tests below were rewritten to that ruling; each says
 * what it asserted before. The banner's other lines (here a mistyped coordinate's) show where they did: below the navigation card
 * in portrait (J1), under the bar in landscape.
 *
 * Before Amendment R: dispatch 2026-11-11-01, F1 then G1 (the owner: "Under the navigation card"; its option read "search fails while navigating >
 * the navigation card stays where it is > notice shows just below the card; not navigating: under the search bar, as now"), and
 * the planner's H1. Through the real screen and ViewModel, navigating and recording, a failed search: real touches at several
 * points across "Try again" all run the search; long-presses on the map below the notice (and beside it, where the notice is
 * narrower than the window) reach the map; the notice is at the map chrome's 80% and overlaps neither the navigation card, the
 * compass strip nor the icon cluster. In portrait it sits just below the card (G1). In a landscape window the notice is where
 * it was (under the bar); these classes test where it lands there.
 *
 * F1's probe (branch mtsf-f1-probe, run 43061560607) is where this began: before G1 the notice sat inside the navigating card
 * and three of five touches on "Try again" went to the card.
 */
abstract class MapsTabSearchNavigatingTests(private val portrait: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(
        composeRule,
        countsLongPresses = true,
        recording = AvailabilityScreenRecording(isRecording = true),
        navigation = AvailabilityScreenNavigation(isReturning = true),
    )

    private fun shown(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun allBounds(tag: String): List<Rect> =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun Rect.d() = "[%.1f, %.1f][%.1f, %.1f]".format(left, top, right, bottom)

    private fun barText(text: String) = composeRule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun searchThatFails() {
        rig.setScreen()
        assertTrue("positive control: navigating, the compass strip shows", shown(STRIP_TAG))
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        assertEquals("positive control: one fetch, failed", 1, rig.sightingsFetches.size)
        assertTrue("positive control: the bar reads \"$SEARCH_BAR_FAILED_TEXT\"", barText(SEARCH_BAR_FAILED_TEXT))
    }

    /** The chrome nothing may cover: the strip (in portrait it carries the navigation card), the navigation card, the cluster. */
    private fun chrome(): List<Pair<String, Rect>> =
        allBounds(STRIP_TAG).map { "the compass strip" to it } +
            allBounds(NAVIGATION_HUD_TAG).map { "the navigation card" to it } +
            allBounds(MAP_ICON_CLUSTER_TAG).map { "the icon cluster" to it }

    /** Was: "while navigating, real touches across Try again all run the search", on the notice below the card. */
    @Test
    fun `while navigating, real touches across Try again in the bar all run the search`() {
        searchThatFails()
        val where = "bar ${bounds(SEARCH_ENTRY_BAR_TAG).d()}, Try again ${bounds(SEARCH_FAILED_TRY_AGAIN_TAG).d()}; " +
            chrome().joinToString { (name, r) -> "$name ${r.d()}" }
        val points = listOf(0.15f to 0.25f, 0.85f to 0.25f, 0.5f to 0.5f, 0.15f to 0.75f, 0.85f to 0.75f)
        val missed = mutableListOf<String>()
        for ((fx, fy) in points) {
            val before = rig.sightingsFetches.size
            rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true), fx, fy)
            if (rig.sightingsFetches.size != before + 1) missed += "($fx, $fy)"
        }
        assertEquals("while navigating, real touches on Try again that did not run the search ($where)", emptyList<String>(), missed)
    }

    /**
     * Was: "while navigating, the notice overlaps no navigation chrome and is at the map chrome's fill" (and, in portrait, just
     * below the card). Now: the failure is in the bar, "Try again" wholly in it and covered by nothing, no notice floats, and the
     * bar is at the map chrome's fill.
     */
    @Test
    fun `while navigating, the failure is in the bar, Try again covered by nothing, and no notice floats`() {
        searchThatFails()
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val tryAgain = bounds(SEARCH_FAILED_TRY_AGAIN_TAG)
        assertTrue("Try again ${tryAgain.d()} is not wholly in the bar ${bar.d()}", tryAgain.left >= bar.left - 1f && tryAgain.right <= bar.right + 1f && tryAgain.top >= bar.top - 1f && tryAgain.bottom <= bar.bottom + 1f)
        for ((name, r) in chrome()) {
            assertTrue("while navigating, Try again ${tryAgain.d()} is covered by $name ${r.d()}", !tryAgain.overlaps(r))
        }
        assertTrue("while navigating, a notice floats out of fullscreen", !shown(SEARCH_FAILED_NOTICE_TAG))
        assertTrue("while navigating, Clear shows while the search has failed (SB1)", !shown(SEARCH_BAR_CLEAR_TAG))
        val fill = composeRule.onNodeWithTag(SEARCH_ENTRY_BAR_TAG, useUnmergedTree = true).fetchSemanticsNode().config[MapChromeContainerColor]
        assertEquals("while navigating, the bar's fill is not at the map chrome's alpha", MAP_CHROME_OVER_MAP_ALPHA, fill.alpha, 0.01f)
    }

    /** The lowest edge of the chrome hanging from the top across [r]'s width: the bar, and the strip or card where they meet it. */
    private fun chromeBottomOver(r: Rect): Float =
        (listOf(bounds(SEARCH_ENTRY_BAR_TAG)) + allBounds(STRIP_TAG)).filter { it.left < r.right && it.right > r.left && it.top <= r.bottom + 1f }.maxOf { it.bottom }

    /** Was: "while navigating, long-presses on the map below and beside the notice reach the map". Now below the bar and card. */
    @Test
    fun `while navigating, with the search failed, long-presses on the map below the bar and card reach the map`() {
        searchThatFails()
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val y = chromeBottomOver(bar) + 40f
        val root = composeRule.onAllNodes(isRoot()).onFirst().fetchSemanticsNode().boundsInRoot
        val others = chrome().map { it.second } + allBounds(SEARCH_ENTRY_BAR_TAG) + allBounds(COMPACT_NAVIGATION_RAIL_TAG)
        val points = listOf(0.3f, 0.5f).map { Offset(bar.left + bar.width * it, y) }.filter { p -> root.contains(p) && others.none { it.contains(p) } }
        assertTrue("positive control: there is map below the bar ${bar.d()}", points.isNotEmpty())
        for (at in points) {
            val before = rig.mapLongPresses
            composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { longClick(at) }
            rig.settle()
            assertEquals("while navigating, a long-press at $at, below the bar ${bar.d()}, did not reach the map", before + 1, rig.mapLongPresses)
        }
    }

    /** Only the map's half fails, then a mistyped coordinate gives the banner a line of its own; the bar carries the failure. */
    private fun bannerLineAndFailureInTheBar() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        rig.tapBar()
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_LATITUDE_TAG).performTextReplacement("999")
        rig.touch(composeRule.onNodeWithTag(SEARCH_DROPDOWN_SEARCH_COORDINATES_TAG))
        if (rig.dropdownShown()) composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        rig.settle()
        assertTrue("positive control: the banner is composed", shown(SEARCH_NOTICE_TAG))
        assertTrue("positive control: the bar reads \"$SEARCH_BAR_FAILED_TEXT\"", barText(SEARCH_BAR_FAILED_TEXT))
    }

    /**
     * Was: "while navigating, the banner and the notice both show in order, overlapping nothing" (both halves failing, the notice
     * below the banner). Now (SB2, the owner: "One line, in the bar"): the failure is in the bar and the banner's other line
     * shows where it did, just below the card in portrait (J1); it covers nothing, at the map chrome's fill with opaque text, and
     * a long-press below it reaches the map.
     */
    @Test
    fun `while navigating, the banner's other line shows where it did, with the failure in the bar, overlapping nothing`() {
        bannerLineAndFailureInTheBar()
        val banner = bounds(SEARCH_NOTICE_TAG)
        for ((name, r) in chrome()) {
            assertTrue("while navigating, the banner ${banner.d()} overlaps $name ${r.d()}", !banner.overlaps(r))
        }
        assertTrue("while navigating, the banner ${banner.d()} overlaps the bar", !banner.overlaps(bounds(SEARCH_ENTRY_BAR_TAG)))
        if (portrait) {
            val card = bounds(STRIP_TAG)
            assertTrue("J1: the banner ${banner.d()} is not just below the navigation card ${card.d()}", banner.top >= card.bottom - 1f && banner.top <= card.bottom + 1f)
        }
        val fill = composeRule.onNodeWithTag(SEARCH_NOTICE_TAG, useUnmergedTree = true).fetchSemanticsNode().config[MapChromeContainerColor]
        assertEquals("while navigating, the banner's fill is not at the map chrome's alpha", MAP_CHROME_OVER_MAP_ALPHA, fill.alpha, 0.01f)
        val message = rig.viewModel.uiState.value.errorMessage ?: error("no banner line")
        val text = composeRule.onNode(hasText(message), useUnmergedTree = true).fetchSemanticsNode().config[MapChromeContentColor]
        assertEquals("while navigating, the banner's text is not opaque", 1f, text.alpha, 0.01f)
        val at = Offset(banner.left + banner.width * 0.3f, banner.bottom + 40f)
        val before = rig.mapLongPresses
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { longClick(at) }
        rig.settle()
        assertEquals("while navigating, a long-press at $at, below the banner, did not reach the map", before + 1, rig.mapLongPresses)
    }

    /** J1: only the list fails while navigating: the banner alone, not covered (the H2 probe, run 43089700275, now on the branch). */
    @Test
    fun `while navigating, the search-error banner alone is not covered by the navigation card`() {
        rig.setScreen()
        rig.availabilityFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        assertEquals("positive control: the list failed", LIST_SEARCH_FAILED_MESSAGE, rig.viewModel.uiState.value.errorMessage)
        assertTrue("positive control: the banner is composed", shown(SEARCH_NOTICE_TAG))
        val banner = bounds(SEARCH_NOTICE_TAG)
        for ((name, r) in chrome()) {
            assertTrue("while navigating, the banner ${banner.d()} is covered by $name ${r.d()}", !banner.overlaps(r))
        }
    }

    /**
     * M4, rewritten for Amendment R. Was: "while navigating, with the banner and the notice both showing, touches across Try again
     * all run and long-presses reach the map". Now the failure is in the bar: touches across the bar's "Try again" each run the
     * map's half again and the banner's line stays through them; long-presses below and beside the banner reach the map.
     */
    @Test
    fun `while navigating, with the banner showing and the failure in the bar, touches across Try again all run and long-presses reach the map`() {
        bannerLineAndFailureInTheBar()
        val missed = mutableListOf<String>()
        for ((fx, fy) in listOf(0.15f to 0.25f, 0.85f to 0.25f, 0.5f to 0.5f, 0.15f to 0.75f, 0.85f to 0.75f)) {
            val before = rig.sightingsFetches.size
            rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true), fx, fy)
            if (rig.sightingsFetches.size != before + 1) missed += "($fx, $fy)"
            assertTrue("positive control: the banner still shows after the touch at ($fx, $fy)", shown(SEARCH_NOTICE_TAG))
        }
        assertEquals("while navigating, real touches on Try again in the bar that did not run the search", emptyList<String>(), missed)

        val banner = bounds(SEARCH_NOTICE_TAG)
        val root = composeRule.onAllNodes(isRoot()).onFirst().fetchSemanticsNode().boundsInRoot
        val others = chrome().map { it.second } + allBounds(SEARCH_ENTRY_BAR_TAG) + allBounds(COMPACT_NAVIGATION_RAIL_TAG)
        val points = (
            listOf(0.2f, 0.5f, 0.8f).map { Offset(banner.left + banner.width * it, banner.bottom + 40f) } +
                listOf(Offset(banner.left - 40f, banner.center.y), Offset(banner.right + 40f, banner.center.y))
            ).filter { p -> root.contains(p) && others.none { it.contains(p) } }
        assertTrue("positive control: more than one point below or beside the banner", points.size > 1)
        for (at in points) {
            val before = rig.mapLongPresses
            composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { longClick(at) }
            rig.settle()
            assertEquals("while navigating with the banner showing, a long-press at $at did not reach the map (banner ${banner.d()})", before + 1, rig.mapLongPresses)
        }
    }

    private companion object {
        const val STRIP_TAG = "compass-elevation-strip"
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapsTabSearchNavigatingPortraitTest : MapsTabSearchNavigatingTests(portrait = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class MapsTabSearchNavigatingShortLandscapeTest : MapsTabSearchNavigatingTests(portrait = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1318dp-h824dp-land")
class MapsTabSearchNavigatingLandscapeTabletTest : MapsTabSearchNavigatingTests(portrait = false)
