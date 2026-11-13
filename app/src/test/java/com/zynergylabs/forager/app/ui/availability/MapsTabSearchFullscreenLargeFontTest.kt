package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
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
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-11-01, Amendment T, TC3 (the owner: "\"Try again\" below the words (Recommended)"; option text "When a word would
 * break, \"Try again\" moves under the words inside the notice, which gives the words the notice's whole width"), and QA3's "Notice
 * keeps clear, words whole" in fullscreen. At font scale 2.0, through the real screen, in fullscreen: no word of the notice breaks
 * inside; "Try again" is under the words where [stacked] (landscape) and beside them otherwise; the notice covers none of the
 * strip, card, rail or cluster; every real touch across "Try again" runs the search; long-presses beside and below reach the map;
 * 80% fill, opaque text. SC4's measurement before this (run 43377459593): in short landscape the text box was 363 px and the
 * longest word 402 px, so "connection." broke.
 */
abstract class MapsTabSearchFullscreenLargeFontTests(private val navigating: Boolean, private val stacked: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = if (navigating) {
        RealSearchScreenRig(composeRule, countsLongPresses = true, recording = AvailabilityScreenRecording(isRecording = true), navigation = AvailabilityScreenNavigation(isReturning = true))
    } else {
        RealSearchScreenRig(composeRule, countsLongPresses = true)
    }

    private fun all(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun Rect.d() = "[%.1f, %.1f][%.1f, %.1f]".format(left, top, right, bottom)

    private fun chrome(): List<Pair<String, Rect>> =
        all("compass-elevation-strip").map { "the compass strip" to it } + all(NAVIGATION_HUD_TAG).map { "the navigation card" to it } +
            all(MAP_ICON_CLUSTER_TAG).map { "the icon cluster" to it } + all(COMPACT_NAVIGATION_RAIL_TAG).map { "the rail" to it }

    private fun failedInFullscreen() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        rig.touch(composeRule.onAllNodesWithContentDescription("Fullscreen").onFirst())
        rig.settle()
        assertTrue("positive control: the font is at scale 2", composeRule.activity.resources.configuration.fontScale >= 2f)
        assertTrue("positive control: fullscreen hid the search bar", all(SEARCH_ENTRY_BAR_TAG).isEmpty())
        assertTrue("positive control: the notice shows", all(SEARCH_FAILED_NOTICE_TAG).isNotEmpty())
    }

    @Test
    fun `at font 2 in fullscreen the notice's words are whole, Try again placed by room, overlapping nothing`() {
        failedInFullscreen()
        val text = composeRule.onNode(hasText(SEARCH_FAILED_NOTICE_TEXT), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        text.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        val t = SEARCH_FAILED_NOTICE_TEXT
        val broken = (0 until layout.lineCount - 1).filter { i -> val e = layout.getLineEnd(i); e in 1 until t.length && t[e - 1].isLetter() && t[e].isLetter() }
        val notice = bounds(SEARCH_FAILED_NOTICE_TAG)
        val tryAgain = bounds(SEARCH_FAILED_TRY_AGAIN_TAG)
        val words = text.boundsInRoot
        assertEquals("at font 2 words of the notice break inside (text box ${layout.size.width} px, notice ${notice.d()})", emptyList<Int>(), broken)
        if (stacked) {
            assertTrue("TC3: Try again ${tryAgain.d()} is not under the words ${words.d()}", tryAgain.top >= words.bottom - 1f)
        } else {
            assertTrue("Try again ${tryAgain.d()} is not beside the words ${words.d()}", tryAgain.top < words.bottom && tryAgain.bottom > words.top)
        }
        assertTrue("Try again ${tryAgain.d()} is not wholly in the notice ${notice.d()}", tryAgain.left >= notice.left - 1f && tryAgain.right <= notice.right + 1f && tryAgain.bottom <= notice.bottom + 1f)
        val root = composeRule.onAllNodes(isRoot()).onFirst().fetchSemanticsNode().boundsInRoot
        assertTrue("the notice ${notice.d()} runs off the screen ${root.d()}", notice.bottom <= root.bottom + 1f && notice.right <= root.right + 1f)
        for ((name, r) in chrome()) assertTrue("the notice ${notice.d()} overlaps $name ${r.d()}", !notice.overlaps(r))
        val fill = composeRule.onNodeWithTag(SEARCH_FAILED_NOTICE_TAG, useUnmergedTree = true).fetchSemanticsNode().config[MapChromeContainerColor]
        assertEquals("the notice's fill is not at the map chrome's alpha", MAP_CHROME_OVER_MAP_ALPHA, fill.alpha, 0.01f)
        assertEquals("the notice's text is not opaque", 1f, text.config[MapChromeContentColor].alpha, 0.01f)
    }

    @Test
    fun `at font 2 in fullscreen every real touch across Try again runs the search, and long-presses by the notice reach the map`() {
        failedInFullscreen()
        for ((fx, fy) in listOf(0.1f to 0.2f, 0.9f to 0.2f, 0.5f to 0.5f, 0.1f to 0.8f, 0.9f to 0.8f)) {
            val before = rig.sightingsFetches.size
            rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true), fx, fy)
            assertEquals("at font 2 a real touch at ($fx, $fy) of Try again did not run the search", before + 1, rig.sightingsFetches.size)
        }
        val notice = bounds(SEARCH_FAILED_NOTICE_TAG)
        val root = composeRule.onAllNodes(isRoot()).onFirst().fetchSemanticsNode().boundsInRoot
        val others = chrome().map { it.second }
        val points = (listOf(0.3f, 0.5f).map { Offset(notice.left + notice.width * it, notice.bottom + 40f) } +
            listOf(Offset(notice.left - 40f, notice.center.y), Offset(notice.right + 40f, notice.center.y)))
            .filter { p -> root.contains(p) && others.none { it.contains(p) } }
        assertTrue("positive control: there is map below or beside the notice ${notice.d()}", points.isNotEmpty())
        for (at in points) {
            val before = rig.mapLongPresses
            composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { longClick(at) }
            rig.settle()
            assertEquals("a long-press at $at by the notice ${notice.d()} did not reach the map", before + 1, rig.mapLongPresses)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapsTabSearchFullscreenLargeFontShortLandscapeTest : MapsTabSearchFullscreenLargeFontTests(navigating = false, stacked = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapsTabSearchFullscreenLargeFontShortLandscapeNavigatingTest : MapsTabSearchFullscreenLargeFontTests(navigating = true, stacked = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapsTabSearchFullscreenLargeFontLandscape780NavigatingTest : MapsTabSearchFullscreenLargeFontTests(navigating = true, stacked = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapsTabSearchFullscreenLargeFontPortrait360NavigatingTest : MapsTabSearchFullscreenLargeFontTests(navigating = true, stacked = false)
