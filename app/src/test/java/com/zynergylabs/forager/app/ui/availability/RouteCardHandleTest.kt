package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-30-08, Amendment 5, N-9 (Sorrel's review of #449): the route card's handle takes a touch across a 48 dp tall
 * target, Material's minimum, with the same look: the handle's bar and the summary line under it stay where they were.
 *
 * Real touches at the target's edges, measured from the card's own top, at three points across its width: 2 dp and 46 dp down
 * (inside a 48 dp target; the second is below the old 32 dp handle, on the summary line), and the middle. Each touch must
 * toggle the card between peek and expanded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardHandleTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = RouteMapSlot(listOf(StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 60.dp, 360.dp, LatLng(RIDGE.lat, RIDGE.lng))))
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("not saved in this test")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private fun setScreen() {
        val viewModel = mapLayersViewModel()
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE),
                routePlotting = RoutePlottingControls(state, plotting),
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { plotting.onStartPlotting(CREEK.stop()) }
        composeRule.waitForIdle()
        val ridge = composeRule.onNodeWithTag(glyphTag(RIDGE.id)).getUnclippedBoundsInRoot()
        touchAt((ridge.left + ridge.right) / 2, (ridge.top + ridge.bottom) / 2)
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun touchAt(x: Dp, y: Dp) {
        val p = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(p) }
        composeRule.waitForIdle()
    }

    private fun expanded() = plotting.uiState.value.expanded

    @Test
    fun `real touches at the handle's 48 dp target's edges and across its width each toggle the card`() {
        setScreen()
        val downs = listOf(2.dp, 24.dp, 46.dp)
        val across = listOf(0.1f, 0.5f, 0.9f)
        downs.forEach { down ->
            across.forEach { fx ->
                val card = bounds(ROUTE_CARD_TAG)
                val before = expanded()
                val x = card.left + (card.right - card.left) * fx
                touchAt(x, card.top + down)
                assertEquals("a touch $down below the card's top at $fx of its width toggles the card", !before, expanded())
            }
        }
        val handle = bounds(ROUTE_CARD_HANDLE_TAG)
        assertTrue("the handle's touch target is at least 48 dp tall, was ${handle.bottom - handle.top}", handle.bottom - handle.top >= 48.dp)
    }

    @Test
    fun `the handle keeps its look - the summary line starts 32 dp below the card's top, peek and expanded (guard, not shown to bite)`() {
        setScreen()
        listOf(false, true).forEach { open ->
            composeRule.runOnUiThread { plotting.onSetExpanded(open) }
            composeRule.waitForIdle()
            val gap = bounds(ROUTE_CARD_COUNT_TAG).top - bounds(ROUTE_CARD_TAG).top
            assertEquals("expanded=$open: the summary line's offset from the card's top, in dp", 32f, gap.value, 0.5f)
        }
    }
}
