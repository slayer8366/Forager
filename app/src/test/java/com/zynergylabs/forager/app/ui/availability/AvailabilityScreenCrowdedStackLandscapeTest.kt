package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.map.CROWDED_LIST_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.crowdedListRowTag
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-27-07 Amendment 1, S3 (Sorrel's #430 review): in a short landscape window the crowded stack's list keeps
 * clear of the search bar and the strip at the top, as the bubble does (its minY). The list is at most 300 dp tall and the
 * window 384 dp, so without that clearance its top would reach about 68 dp from the window's top. The real screen in the
 * landscape window the other landscape tests use, the real Maps tab and [CrowdedStackStubMap]; real insets are zero here, so
 * what this does not show is the phone's status-bar inset, which the S22 step does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-xxhdpi")
class AvailabilityScreenCrowdedStackLandscapeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val map = CrowdedStackStubMap(SPOT_X, SPOT_Y, VIEW_W_DP, VIEW_H_DP)
    private val finds = (0 until 30).map { i -> BUBBLE_FIND.copy(id = "find-$i", ownIdentification = "Chanterelle %02d".format(i)) }

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun `in landscape the list keeps clear of the search bar and the strip, and its rows still open their bubbles`() {
        val viewModel = mapLayersViewModel()
        composeRule.setContent { MapLayersTestScreen(viewModel = viewModel, mapSlot = map.slot, store = OneCellStore(), logUiState = MushroomLogUiState(entries = finds)) }
        settle()
        val s = checkNotNull(map.scene)
        finds.forEach { s.add(MapLayerIds.FINDS, it.id, s.centre.lat, s.centre.lng) }

        val density = composeRule.density.density
        composeRule.onNodeWithTag(CROWDED_STUB_MAP_TAG).performTouchInput { click(Offset(SPOT_X * density, SPOT_Y * density)) }
        settle()

        composeRule.onNodeWithTag(CROWDED_LIST_TAG).assertIsDisplayed()
        val list = bounds(CROWDED_LIST_TAG)
        val top = listOf(SEARCH_ENTRY_BAR_TAG, LANDSCAPE_STRIP_TAG).map { tag ->
            assertTrue("positive control: $tag is on the screen", composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
            tag to bounds(tag)
        }
        val win = composeRule.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().first().boundsInRoot
        println("DIAG density=${composeRule.density.density} window=$win list=$list " + top.joinToString { "${it.first}=${it.second}" } + " cluster=${bounds(MAP_ICON_CLUSTER_TAG)} map=${bounds(CROWDED_STUB_MAP_TAG)}")
        for ((tag, chrome) in top) {
            assertFalse("the list $list is clear of $tag $chrome", list.overlaps(chrome))
        }

        // The list is shorter than 300 dp here, and a real touch on its top row still opens that record's bubble.
        val shownRows = finds.mapNotNull { find ->
            val tag = crowdedListRowTag(FanKey(MapLayerIds.FINDS, find.id))
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.let { node -> Triple(tag, find, node.boundsInRoot) }
        }
        assertTrue("rows are shown", shownRows.isNotEmpty())
        val (rowTag, rowFind, rowBounds) = shownRows.minBy { it.third.top }
        assertTrue("the top row $rowBounds is inside the list $list", list.contains(rowBounds.center))
        composeRule.onNodeWithTag(rowTag).performTouchInput { click(center) }
        settle()
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(rowFind.ownIdentification!!, useUnmergedTree = true).assertIsDisplayed()
    }

    private companion object {
        /** The spot, dp of the map: right of the search bar's middle, below the strip. */
        const val SPOT_X = 420f
        const val SPOT_Y = 220f
        const val VIEW_W_DP = 300f
        const val VIEW_H_DP = 300f
        const val LANDSCAPE_STRIP_TAG = "compass-elevation-strip"
    }
}
