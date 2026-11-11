package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
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
 * Dispatch 2026-11-11-01, F6 (Sorrel's review): in a landscape window the notice is as wide as the search bar, not the map, so a
 * long-press on the map beside it, at the notice's own height, must reach the map; under the bar and while fullscreen. Through
 * the real screen and ViewModel; the map stand-in counts long-presses as the real map takes them.
 */
abstract class MapsTabSearchNoticeTouchTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule, countsLongPresses = true)

    private fun boundsOrNull(tag: String): Rect? =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    private fun Rect.d() = "[%.1f, %.1f][%.1f, %.1f]".format(left, top, right, bottom)

    /** Points 40 px to the left and right of the notice, at its middle, that are on the screen and on no other chrome. */
    private fun besideTheNotice(): Pair<Rect, List<Offset>> {
        val notice = boundsOrNull(SEARCH_FAILED_NOTICE_TAG) ?: error("no search-failed notice")
        val root = composeRule.onAllNodes(isRoot()).onFirst().fetchSemanticsNode().boundsInRoot
        val chrome = listOf(SEARCH_ENTRY_BAR_TAG, "compass-elevation-strip", MAP_ICON_CLUSTER_TAG, COMPACT_NAVIGATION_RAIL_TAG).mapNotNull { boundsOrNull(it) }
        val y = notice.center.y
        val points = listOf(Offset(notice.left - 40f, y), Offset(notice.right + 40f, y))
            .filter { p -> root.contains(p) && chrome.none { it.contains(p) } }
        return notice to points
    }

    private fun assertLongPressesBesideReachTheMap(where: String) {
        val (notice, points) = besideTheNotice()
        assertTrue("positive control ($where): there is map beside the notice ${notice.d()}", points.isNotEmpty())
        for (at in points) {
            val before = rig.mapLongPresses
            composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { longClick(at) }
            rig.settle()
            assertEquals("$where: a long-press at $at, beside the notice ${notice.d()}, did not reach the map", before + 1, rig.mapLongPresses)
        }
    }

    private fun searchThatFails() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        assertTrue("positive control: the notice shows", boundsOrNull(SEARCH_FAILED_NOTICE_TAG) != null)
    }

    @Test
    fun `a long-press on the map beside the notice under the bar reaches the map`() {
        searchThatFails()
        assertLongPressesBesideReachTheMap("under the bar")
    }

    @Test
    fun `a long-press on the map beside the fullscreen notice reaches the map`() {
        searchThatFails()
        rig.touch(composeRule.onAllNodesWithContentDescription("Fullscreen").onFirst())
        rig.settle()
        assertTrue("positive control: fullscreen hid the search bar", boundsOrNull(SEARCH_ENTRY_BAR_TAG) == null)
        assertLongPressesBesideReachTheMap("fullscreen")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class MapsTabSearchNoticeTouchShortLandscapeTest : MapsTabSearchNoticeTouchTests()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1318dp-h824dp-land")
class MapsTabSearchNoticeTouchLandscapeTabletTest : MapsTabSearchNoticeTouchTests()
