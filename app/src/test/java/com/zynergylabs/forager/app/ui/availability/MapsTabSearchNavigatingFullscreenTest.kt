package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
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
 * Dispatch 2026-11-11-01, L1 (the owner: "Under the card in fullscreen too"; its option read "fullscreen + navigating + list
 * search fails > banner shows just below the navigation card, above the 'Search failed' notice") and the planner's M1. Through
 * the real screen and ViewModel, navigating and recording, in fullscreen: the banner (here carrying a mistyped coordinate's line,
 * made before fullscreen, while the bar shows) and the search-failed notice both show just below the navigation card, banner
 * first; neither covers the other, the card, the strip or the icon cluster; 80% fill, opaque text; real touches across "Try
 * again" all run the search; long-presses below and beside reach the map.
 *
 * In a landscape window (N1, the owner: "Show it there too") the banner is just above the notice, wherever the notice is placed.
 *
 * Fullscreen when not navigating is not ruled: the banner is not shown there, as before this dispatch, and is not tested here.
 */
abstract class MapsTabSearchNavigatingFullscreenTests(private val portrait: Boolean = true) {

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

    private fun chrome(): List<Pair<String, Rect>> =
        allBounds(STRIP_TAG).map { "the compass strip" to it } +
            allBounds(NAVIGATION_HUD_TAG).map { "the navigation card" to it } +
            allBounds(MAP_ICON_CLUSTER_TAG).map { "the icon cluster" to it } +
            allBounds(COMPACT_NAVIGATION_RAIL_TAG).map { "the rail" to it }

    /** The map's half fails, a mistyped coordinate gives the banner a line, then fullscreen. */
    private fun bannerAndNoticeInFullscreen() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        rig.tapBar()
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_LATITUDE_TAG).performTextReplacement("999")
        rig.touch(composeRule.onNodeWithTag(SEARCH_DROPDOWN_SEARCH_COORDINATES_TAG))
        if (rig.dropdownShown()) composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        rig.settle()
        rig.touch(composeRule.onAllNodesWithContentDescription("Fullscreen").onFirst())
        rig.settle()
        assertTrue("positive control: fullscreen hid the search bar", !shown(SEARCH_ENTRY_BAR_TAG))
        assertTrue("positive control: navigating, the compass strip shows", shown(STRIP_TAG))
        assertTrue("fullscreen while navigating: the banner is not shown", shown(SEARCH_NOTICE_TAG))
        assertTrue("fullscreen while navigating: the notice is not shown", shown(SEARCH_FAILED_NOTICE_TAG))
    }

    @Test
    fun `fullscreen while navigating, the banner is just below the card and above the notice, overlapping nothing, at the map chrome's fill`() {
        bannerAndNoticeInFullscreen()
        val banner = bounds(SEARCH_NOTICE_TAG)
        val notice = bounds(SEARCH_FAILED_NOTICE_TAG)
        if (portrait) {
            val card = bounds(STRIP_TAG)
            assertTrue("L1: the banner ${banner.d()} is not just below the navigation card ${card.d()}", banner.top >= card.bottom - 1f && banner.top <= card.bottom + 1f)
        } else {
            // N1 (the owner: "Show it there too"): just above the notice, wherever the notice sits.
            assertTrue("N1: the banner ${banner.d()} is not just above the notice ${notice.d()}", banner.bottom >= notice.top - 1f && banner.bottom <= notice.top + 1f)
        }
        assertTrue("the banner ${banner.d()} is not above the notice ${notice.d()}", banner.bottom <= notice.top + 1f)
        assertTrue("the banner ${banner.d()} and the notice ${notice.d()} overlap", !banner.overlaps(notice))
        for ((name, r) in chrome()) {
            assertTrue("the banner ${banner.d()} overlaps $name ${r.d()}", !banner.overlaps(r))
            assertTrue("the notice ${notice.d()} overlaps $name ${r.d()}", !notice.overlaps(r))
        }
        for (tag in listOf(SEARCH_NOTICE_TAG, SEARCH_FAILED_NOTICE_TAG)) {
            val fill = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[MapChromeContainerColor]
            assertEquals("$tag's fill is not at the map chrome's alpha", MAP_CHROME_OVER_MAP_ALPHA, fill.alpha, 0.01f)
        }
        val message = rig.viewModel.uiState.value.errorMessage ?: error("no banner line")
        for (text in listOf(message, SEARCH_FAILED_NOTICE_TEXT)) {
            val colour = composeRule.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode().config[MapChromeContentColor]
            assertEquals("the text <$text> is not opaque", 1f, colour.alpha, 0.01f)
        }
    }

    @Test
    fun `fullscreen while navigating, with both showing, touches across Try again all run and long-presses below and beside reach the map`() {
        bannerAndNoticeInFullscreen()
        val missed = mutableListOf<String>()
        for ((fx, fy) in listOf(0.15f to 0.25f, 0.85f to 0.25f, 0.5f to 0.5f, 0.15f to 0.75f, 0.85f to 0.75f)) {
            val before = rig.sightingsFetches.size
            rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true), fx, fy)
            if (rig.sightingsFetches.size != before + 1) missed += "($fx, $fy)"
            assertTrue("positive control: the banner still shows after the touch at ($fx, $fy)", shown(SEARCH_NOTICE_TAG))
        }
        assertEquals("fullscreen while navigating, real touches on Try again that did not run the search", emptyList<String>(), missed)

        val banner = bounds(SEARCH_NOTICE_TAG)
        val notice = bounds(SEARCH_FAILED_NOTICE_TAG)
        val root = composeRule.onAllNodes(isRoot()).onFirst().fetchSemanticsNode().boundsInRoot
        val others = chrome().map { it.second } + allBounds(COMPACT_NAVIGATION_RAIL_TAG)
        val points = (
            listOf(0.2f, 0.5f, 0.8f).map { Offset(notice.left + notice.width * it, notice.bottom + 40f) } +
                listOf(Offset(banner.left - 40f, banner.center.y), Offset(banner.right + 40f, banner.center.y)) +
                listOf(Offset(notice.left - 40f, notice.center.y), Offset(notice.right + 40f, notice.center.y))
            ).filter { p -> root.contains(p) && others.none { it.contains(p) } }
        assertTrue("positive control: more than one point below or beside the banner and the notice", points.size > 1)
        for (at in points) {
            val before = rig.mapLongPresses
            composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { longClick(at) }
            rig.settle()
            assertEquals("fullscreen while navigating, a long-press at $at did not reach the map (banner ${banner.d()}, notice ${notice.d()})", before + 1, rig.mapLongPresses)
        }
    }

    /**
     * P1 (Sorrel's fourth review): the banner alone in fullscreen while navigating. Only the list search fails and the map's half
     * loads, so there is no "Search failed" notice and the banner carries the list's own failure line (not folded, C3 folds it only
     * while the map's half has failed too). It is placed where it is when both show: just below the card in portrait; at the top of
     * the placed column, the map's top, in a landscape window.
     */
    @Test
    fun `fullscreen while navigating, the banner alone is placed where it is with the notice and overlaps nothing`() {
        rig.setScreen()
        rig.availabilityFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        assertEquals("positive control: the list failed", LIST_SEARCH_FAILED_MESSAGE, rig.viewModel.uiState.value.errorMessage)
        rig.touch(composeRule.onAllNodesWithContentDescription("Fullscreen").onFirst())
        rig.settle()
        assertTrue("positive control: fullscreen hid the search bar", !shown(SEARCH_ENTRY_BAR_TAG))
        assertTrue("positive control: the map's half loaded, so there is no search-failed notice", !shown(SEARCH_FAILED_NOTICE_TAG))
        assertTrue("fullscreen while navigating: the banner alone is not shown", shown(SEARCH_NOTICE_TAG))

        val banner = bounds(SEARCH_NOTICE_TAG)
        if (portrait) {
            val card = bounds(STRIP_TAG)
            assertTrue("the banner alone ${banner.d()} is not just below the navigation card ${card.d()}", banner.top >= card.bottom - 1f && banner.top <= card.bottom + 1f)
        } else {
            val map = bounds(LAYOUT_FIXES_MAP_TAG)
            assertTrue("the banner alone ${banner.d()} is not at the map's top ${map.d()}, where the placed pair starts", banner.top >= map.top - 1f && banner.top <= map.top + 1f)
        }
        for ((name, r) in chrome()) {
            assertTrue("the banner alone ${banner.d()} overlaps $name ${r.d()}", !banner.overlaps(r))
        }
        val fill = composeRule.onNodeWithTag(SEARCH_NOTICE_TAG, useUnmergedTree = true).fetchSemanticsNode().config[MapChromeContainerColor]
        assertEquals("the banner alone's fill is not at the map chrome's alpha", MAP_CHROME_OVER_MAP_ALPHA, fill.alpha, 0.01f)
        val colour = composeRule.onNode(hasText(LIST_SEARCH_FAILED_MESSAGE), useUnmergedTree = true).fetchSemanticsNode().config[MapChromeContentColor]
        assertEquals("the banner alone's text is not opaque", 1f, colour.alpha, 0.01f)

        val root = composeRule.onAllNodes(isRoot()).onFirst().fetchSemanticsNode().boundsInRoot
        val others = chrome().map { it.second }
        val points = (
            listOf(0.2f, 0.5f, 0.8f).map { Offset(banner.left + banner.width * it, banner.bottom + 40f) } +
                listOf(Offset(banner.left - 40f, banner.center.y), Offset(banner.right + 40f, banner.center.y))
            ).filter { p -> root.contains(p) && others.none { it.contains(p) } }
        assertTrue("positive control: more than one point below or beside the banner ${banner.d()}", points.size > 1)
        for (at in points) {
            val before = rig.mapLongPresses
            composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { longClick(at) }
            rig.settle()
            assertEquals("fullscreen while navigating, a long-press at $at by the banner alone ${banner.d()} did not reach the map", before + 1, rig.mapLongPresses)
        }
    }

    private companion object {
        const val STRIP_TAG = "compass-elevation-strip"
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapsTabSearchNavigatingFullscreenPortraitTest : MapsTabSearchNavigatingFullscreenTests()

// N1 (the owner: "Show it there too"): the M1 probe classes (branch mtsf-lm-probe, run 43129275024, where the banner was absent).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class MapsTabSearchNavigatingFullscreenShortLandscapeTest : MapsTabSearchNavigatingFullscreenTests(portrait = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1318dp-h824dp-land")
class MapsTabSearchNavigatingFullscreenLandscapeTabletTest : MapsTabSearchNavigatingFullscreenTests(portrait = false)
