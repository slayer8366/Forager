package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.MAP_TYPE_PANEL_TAG
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.mapModeChipTag
import kotlin.math.abs
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
 * The map type panel in the landscape L (dispatch 2026-10-28-02): the L's bar has no gap between its rows, so the Layers
 * row's offset from the bar's centre is 48 dp, not 52 (`MAP_TYPE_PANEL_ANCHOR_OFFSET_LANDSCAPE`). On both sides of the
 * screen, at both landscape rotations, with real touches: the Layers row opens the panel level with it, overlapping it and
 * growing toward the map's middle; a touch on a chip, at several points across it, closes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class MapTypePanelLandscapeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val slot: MapSlot = { _, _, _, _, _, onTap, _, _, modifier ->
        Box(modifier.testTag(LAYOUT_FIXES_MAP_TAG).pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) })
    }

    private fun setScreen(rotation: Int) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent { LayoutFixesScreen(uiState = LAYOUT_FIXES_FIX_STATE, mapSlot = slot) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun tag(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun cluster(): DpRect = tag(MAP_ICON_CLUSTER_TAG)
    private fun layersRow(): DpRect = composeRule.onNode(hasContentDescription("Layers:", substring = true)).getUnclippedBoundsInRoot()

    private fun touchAt(x: Dp, y: Dp) {
        val at = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onRoot().performTouchInput { click(at) }
        composeRule.waitForIdle()
    }

    private fun clusterOnLeft(): Boolean {
        val map = tag(LAYOUT_FIXES_MAP_TAG)
        return (cluster().left + cluster().right) / 2 < (map.left + map.right) / 2
    }

    private fun snapTo(left: Boolean) {
        if (clusterOnLeft() == left) return
        val handle = tag("map-icon-bar-minimize-handle")
        composeRule.longPressDrag((handle.left + handle.right) / 2, (handle.top + handle.bottom) / 2, if (left) (-500).dp else 500.dp, 0.dp)
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun assertAnchoredAndChipsClose(rotation: Int, left: Boolean) {
        setScreen(rotation)
        snapTo(left)
        assertTrue("the cluster is on the ${if (left) "left" else "right"}", clusterOnLeft() == left)
        listOf(0.15f, 0.5f, 0.85f).forEach { fx ->
            val row = layersRow()
            touchAt((row.left + row.right) / 2, (row.top + row.bottom) / 2)
            val panel = composeRule.onNodeWithTag(MAP_TYPE_PANEL_TAG).assertIsDisplayed().getUnclippedBoundsInRoot()
            val rowMid = (row.top + row.bottom) / 2
            val panelMid = (panel.top + panel.bottom) / 2
            assertTrue("level with the Layers row ($panelMid vs $rowMid)", abs((panelMid - rowMid).value) <= 2f)
            assertTrue("overlaps the Layers row ($panel, $row)", panel.left < row.right && panel.right > row.left)
            if (left) assertTrue("grows rightward ($panel, $row)", panel.right > row.right) else assertTrue("grows leftward ($panel, $row)", panel.left < row.left)

            val chip = tag(mapModeChipTag(MapMode.TOPOGRAPHIC))
            touchAt(chip.left + (chip.right - chip.left) * fx, (chip.top + chip.bottom) / 2)
            assertTrue("a touch at $fx across the chip closed the panel", composeRule.onAllNodesWithTag(MAP_TYPE_PANEL_TAG).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test fun `at ROTATION_90 with the L on the left`() = assertAnchoredAndChipsClose(Surface.ROTATION_90, left = true)

    @Test fun `at ROTATION_90 with the L on the right`() = assertAnchoredAndChipsClose(Surface.ROTATION_90, left = false)

    @Test fun `at ROTATION_270 with the L on the left`() = assertAnchoredAndChipsClose(Surface.ROTATION_270, left = true)

    @Test fun `at ROTATION_270 with the L on the right`() = assertAnchoredAndChipsClose(Surface.ROTATION_270, left = false)
}

/**
 * Amendment 3 (RECORD -1770, N1; the owner: "Opening Tools closes panel (Recommended)"), in a short landscape window, where
 * the rail's Tools sits outside the panel's scrim: panel open > a real touch on Tools > the panel closes and the drawer
 * opens > Back > the drawer closes > the map, the panel still closed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class MapTypePanelToolsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val map = LayersRecordingMapSlot()

    private fun panelShown() = composeRule.onAllNodesWithTag(MAP_TYPE_PANEL_TAG).fetchSemanticsNodes().isNotEmpty()

    private fun drawerShown() = runCatching {
        composeRule.onNode(androidx.compose.ui.test.hasText("Trip Planner") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(TOOLS_DRAWER_SHEET_TAG))).assertIsDisplayed()
    }.isSuccess

    @Test
    fun `with the panel open, a real touch on the rail's Tools closes the panel and opens the drawer, and Back then closes the drawer`() {
        val viewModel = mapLayersViewModel()
        composeRule.setContent { MapLayersTestScreen(viewModel, map.slot, com.zynergylabs.forager.app.domain.AbsentForecastCellStore) }
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Layers:", substring = true)).performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertTrue("the panel opened", panelShown())

        composeRule.openTools()

        assertTrue("the drawer opened", drawerShown())
        assertTrue("and the panel closed", !panelShown())

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertTrue("Back closed the drawer", !drawerShown())
        assertTrue("the panel is still closed", !panelShown())
        composeRule.onNode(hasContentDescription("Layers:", substring = true)).assertIsDisplayed()
    }
}
