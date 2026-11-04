package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.domain.CrowdedIconsMode
import com.zynergylabs.forager.app.domain.CrowdedIconsPreferenceRepository
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.map.CROWDED_LIST_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-04-02, S6 (RECORD -2040: "the standard rule, plus keeping clear of the cluster column in portrait"): the crowded
 * stack's bubble keeps clear of the icon cluster's column at any height, on the cluster's side, as the old list did, so its rows
 * never sit under the cluster, which is composed over it and would take their touches (CI run 40580543726, the old list's finding).
 *
 * The stack is carried by a pan to just inside the column's left edge and below the cluster, where nothing of the cluster is drawn:
 * there the standard bubble's clamp alone would put the card's right edge inside the column (its tail ends at the stack), and the
 * column's own inset is what moves it clear. [CrowdedListBubbleTests] checks clearance at stacks away from the column, which the
 * clamp alone already gives (revert check 11, run 41793114744, did not fail there); this is the case that tells the two apart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class CrowdedListBubbleClusterColumnTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val map = CrowdedStackStubMap(SPOT_X, SPOT_Y, 300f, 300f)
    private val finds = (0 until 3).map { i -> BUBBLE_FIND.copy(id = "find-$i", ownIdentification = "Chanterelle %02d".format(i)) }

    private val density get() = composeRule.density.density

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun `a stack carried under the cluster's column, below the cluster, opens its bubble clear of the column`() {
        val viewModel = mapLayersViewModel(store = OneCellStore(), crowdedIcons = ListMode())
        composeRule.setContent {
            MapLayersTestScreen(viewModel = viewModel, mapSlot = map.slot, store = OneCellStore(), logUiState = MushroomLogUiState(entries = finds), currentTime = ROUTE_CARD_NOON_CLOCK)
        }
        settle()
        val s = checkNotNull(map.scene)
        finds.forEach { s.add(MapLayerIds.FINDS, it.id, s.centre.lat, s.centre.lng) }
        val m = bounds(CROWDED_STUB_MAP_TAG)
        composeRule.onNodeWithTag(CROWDED_STUB_MAP_TAG).performTouchInput { click(Offset(SPOT_X * density, SPOT_Y * density)) }
        settle()
        assertTrue("positive control: the list opened", composeRule.onAllNodesWithTag(CROWDED_LIST_TAG).fetchSemanticsNodes().isNotEmpty())

        val cluster = bounds(MAP_ICON_CLUSTER_TAG)
        assertTrue("positive control: the cluster is on the right, at $cluster", cluster.center.x > m.center.x)
        // Into the column, 20 dp in from its left edge, and 40 dp below the cluster's foot.
        val target = Offset(cluster.left + 20f * density, cluster.bottom + 40f * density)
        assertTrue("positive control: the place $target is on the map $m", m.contains(target))
        val stackNow = m.topLeft + Offset(SPOT_X * density, SPOT_Y * density)
        composeRule.runOnUiThread { map.pan(target.x - stackNow.x, target.y - stackNow.y) }
        settle()

        val card = bounds(CROWDED_LIST_TAG)
        assertEquals("positive control: the cluster did not move", cluster, bounds(MAP_ICON_CLUSTER_TAG))
        assertTrue("the bubble $card is clear of the cluster's column, which starts at x ${cluster.left}", card.right <= cluster.left)
    }

    private class ListMode : CrowdedIconsPreferenceRepository {
        override suspend fun getCrowdedIconsMode(unknown: (String) -> Unit): Result<CrowdedIconsMode?> = Result.success(CrowdedIconsMode.LIST)
        override suspend fun setCrowdedIconsMode(mode: CrowdedIconsMode): Result<Unit> = Result.success(Unit)
    }

    private companion object {
        const val SPOT_X = 150f
        const val SPOT_Y = 420f
    }
}
