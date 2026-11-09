package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.MAP_LEGEND_CHIP_TAG
import com.zynergylabs.forager.app.ui.map.MapCameraSnapshot
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Part 1 layout fixes, item 2 in portrait (dispatch `2026-09-28-78`, planner message `-98`): with the
 * legend expanded the icon cluster rises above it, under the owner's Q4 ruling ("the cluster moves up
 * when the legend expands and back down when it collapses"). The window is `h740dp`, short enough that
 * the centred cluster overlaps the expanded legend (the S22's own overlap depends on its status and
 * navigation bars, which Robolectric reports as zero), and tall enough that the cluster can rise clear
 * of it under the dropdown's top. Rotation is pinned at 0.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h740dp-xxhdpi")
class LayoutFixesLegendPortraitTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val map = LayersRecordingMapSlot(LAYOUT_FIXES_DATES)

    @Test
    fun `T2 with the legend expanded the centred cluster rises clear of it, and collapsed it returns`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_0)
        var rotationSeen: Int? = null
        val store = FixedForecastStore(BOTH_FORECAST_GROUPS)
        val viewModel = mapLayersViewModel(store = store)
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            MapLayersTestScreen(viewModel, map.slot, store)
        }
        composeRule.waitForIdle()
        assertEquals("the screen must see the pinned rotation", Surface.ROTATION_0, rotationSeen)

        fun cluster(): DpRect = composeRule.onNodeWithTag(MAP_ICON_CLUSTER_TAG).getUnclippedBoundsInRoot()
        fun legend(): DpRect = composeRule.onNodeWithTag(MAP_LEGEND_CHIP_TAG).getUnclippedBoundsInRoot()

        val start = cluster()
        assertFalse("collapsed: the cluster ${start.describe()} is clear of the legend ${legend().describe()}", start.overlapsRect(legend()))

        composeRule.touchAt((legend().left + legend().right) / 2, (legend().top + legend().bottom) / 2)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("the legend expanded", "Hide legend", composeRule.onNodeWithTag(MAP_LEGEND_CHIP_TAG).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.OnClick].label)
        val raised = cluster()
        assertFalse("expanded: the cluster ${raised.describe()} is clear of the legend ${legend().describe()}", raised.overlapsRect(legend()))

        composeRule.touchAt((legend().left + legend().right) / 2, legend().top + 12.dp)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertTrue("collapsed again: the cluster is back where it started (${cluster().describe()} against ${start.describe()})", abs((cluster().top - start.top).value) <= 1f)
    }

    /**
     * Q4's legend bound stays in portrait (the owner's "2 A", planner message `2026-09-29-04`): however far the
     * cluster is dragged down, it stops above the legend chip, collapsed or expanded.
     */
    @Test
    fun `Q4 in portrait the legend still stops the cluster from being dragged down over it`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_0)
        val store = FixedForecastStore(BOTH_FORECAST_GROUPS)
        val viewModel = mapLayersViewModel(store = store)
        composeRule.setContent { MapLayersTestScreen(viewModel, map.slot, store) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        fun cluster(): DpRect = composeRule.onNodeWithTag(MAP_ICON_CLUSTER_TAG).getUnclippedBoundsInRoot()
        fun legend(): DpRect = composeRule.onNodeWithTag(MAP_LEGEND_CHIP_TAG).getUnclippedBoundsInRoot()
        val handle = composeRule.onNodeWithTag("map-icon-bar-minimize-handle").getUnclippedBoundsInRoot()
        composeRule.longPressDrag((handle.left + handle.right) / 2, (handle.top + handle.bottom) / 2, 0.dp, 400.dp)
        assertFalse("dragged far down: the cluster ${cluster().describe()} stops clear of the legend ${legend().describe()}", cluster().overlapsRect(legend()))
        assertTrue("the cluster's bottom ${cluster().bottom.value} is at or above the legend's top ${legend().top.value}", cluster().bottom <= legend().top)
    }
}

/**
 * Part 1 layout fixes, item 4, at the S22's portrait size (`w384dp-h823dp`), rotation pinned at 0.
 *
 * Item 4 (planner message `-98`): the camera the user left on the Maps tab survives a tab round trip.
 * The map stub stands in for `SightingsMap`, which writes the camera it settles on into the memory it is
 * handed; whether MapLibre then restores it is a device item.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class LayoutFixesPortraitTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val map = LayoutFixesMapSlot()

    private fun setScreen(showEntry: Boolean) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_0)
        var rotationSeen: Int? = null
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            LayoutFixesScreen(
                uiState = LAYOUT_FIXES_FIX_STATE,
                mapSlot = map.slot,
                cartographyUiState = if (showEntry) LAYOUT_FIXES_SHOWN_ENTRY_STATE else com.zynergylabs.forager.app.ui.log.CartographyUiState(),
            )
        }
        composeRule.waitForIdle()
        assertEquals("the screen must see the pinned rotation", Surface.ROTATION_0, rotationSeen)
    }

    private fun touchNav(label: String) {
        val item = composeRule.onNodeWithText(label).getUnclippedBoundsInRoot()
        composeRule.touchAt((item.left + item.right) / 2, (item.top + item.bottom) / 2)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    // ── Item 4 ──

    @Test
    fun `T4 the camera the user left on the Maps tab survives a tab round trip`() {
        setScreen(showEntry = false)
        val left = MapCameraSnapshot(
            target = LatLng(45.3512, -122.6011),
            zoom = 15.5,
            bearing = 30.0,
            tilt = 10.0,
            following = false,
            appliedTarget = LAYOUT_FIXES_REGION to null,
        )
        map.renderMode?.cameraMemory?.saved = left

        touchNav("List")
        assertTrue("the map left composition with the tab", composeRule.onAllNodesWithTag(LAYOUT_FIXES_MAP_TAG).fetchSemanticsNodes().isEmpty())
        touchNav("Maps")

        assertEquals("the camera the user left is what the map is handed back", left, map.renderMode?.cameraMemory?.saved)
    }
}
