package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.ATTRIBUTION_BUTTON_DP
import com.zynergylabs.forager.app.ui.map.ATTRIBUTION_CLEAR_GAP_DP
import com.zynergylabs.forager.app.ui.map.attributionEndInsetClearOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * Item 1 of dispatch 2026-09-29-57 (amendment -262, "Move the 'i'"), through the real screen: at 90 with the L snapped right
 * and dragged to its lower limit, the button's rectangle (from the margins the map is handed, MapLibre's own defaults assumed
 * as the run record measured them) does not intersect the record button's. The map is handed the L's measured bounds
 * (`renderMode.attributionKeepClear`); the function `SightingsMap` applies with them is the one under test. The real position of
 * MapLibre's "i" and a real tap on it are device-only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class AttributionClearOfLTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val map = LayoutFixesMapSlot()

    private fun setScreen(rotation: Int) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        var rotationSeen: Int? = null
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            LayoutFixesScreen(uiState = LAYOUT_FIXES_FIX_STATE, mapSlot = map.slot, isRecording = true)
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("the screen must see the pinned rotation", rotation, rotationSeen)
    }

    private fun tag(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    /** MapLibre's own margins, in dp, as the run record measured them on the S22 (right 146 px, bottom 11 px at 450 dpi). */
    private fun defaultsPx(): IntArray = with(composeRule.density) { intArrayOf(4.dp.roundToPx(), 4.dp.roundToPx(), 52.dp.roundToPx(), 4.dp.roundToPx()) }

    private fun dragClusterRightAndDown() {
        val handle = tag("map-icon-bar-minimize-handle")
        composeRule.longPressDrag((handle.left + handle.right) / 2, (handle.top + handle.bottom) / 2, 500.dp, 300.dp)
    }

    /** The button's rectangle in the map's own px for the end inset the host and the function settle on. */
    private fun buttonRect(): Rect {
        val density = composeRule.density
        val slot = tag(LAYOUT_FIXES_MAP_TAG)
        val mapWidth = with(density) { (slot.right - slot.left).roundToPx() }
        val mapHeight = with(density) { (slot.bottom - slot.top).roundToPx() }
        val renderMode = map.renderMode!!
        val defaults = defaultsPx()
        val bottomInsetPx = with(density) { (renderMode.attributionBottomInset ?: renderMode.bottomInset).roundToPx() }
        val endInsetPx = attributionEndInsetClearOf(
            keepClear = renderMode.attributionKeepClear,
            mapWidthPx = mapWidth,
            mapHeightPx = mapHeight,
            defaults = defaults,
            bottomInsetPx = bottomInsetPx,
            endInsetPx = with(density) { renderMode.attributionEndInset.roundToPx() },
            isRtl = false,
            buttonPx = with(density) { ATTRIBUTION_BUTTON_DP.dp.roundToPx() },
            gapPx = with(density) { ATTRIBUTION_CLEAR_GAP_DP.dp.roundToPx() },
        )
        val button = with(density) { ATTRIBUTION_BUTTON_DP.dp.roundToPx() }
        val right = (mapWidth - defaults[2] - endInsetPx).toFloat()
        val bottom = (mapHeight - defaults[3] - bottomInsetPx).toFloat()
        return Rect(right - button, bottom - button, right, bottom)
    }

    private fun DpRect.inMapPx(): Rect {
        val density = composeRule.density
        val slot = tag(LAYOUT_FIXES_MAP_TAG)
        return with(density) { Rect((left - slot.left).toPx(), (top - slot.top).toPx(), (right - slot.left).toPx(), (bottom - slot.top).toPx()) }
    }

    @Test
    fun `at ROTATION_90 with the L on the right at its lower limit the button does not intersect the record button or the L`() {
        setScreen(Surface.ROTATION_90)
        dragClusterRightAndDown()
        val cluster = tag(MAP_ICON_CLUSTER_TAG)
        val record = tag("control-pill-record")
        assertTrue("the L ${cluster.describe()} snapped to the right", (cluster.left + cluster.right) / 2 > tag(LAYOUT_FIXES_MAP_TAG).right / 2)
        assertTrue("the L ${cluster.describe()} is at its lower limit, the map area's bottom", cluster.bottom >= tag(LAYOUT_FIXES_MAP_TAG).bottom - 1.dp)

        val button = buttonRect()

        assertFalse("the button $button does not intersect the record button ${record.describe()}", button.overlaps(record.inMapPx()))
        assertFalse("the button $button does not intersect the L ${cluster.describe()}", button.overlaps(cluster.inMapPx()))
    }

    @Test
    fun `the map is handed the L's own measured bounds to keep clear of, in its own px`() {
        setScreen(Surface.ROTATION_90)
        dragClusterRightAndDown()
        val keepClear = map.renderMode!!.attributionKeepClear
        assertNotNull("the landscape L hands the map its bounds", keepClear)
        val measured = tag(MAP_ICON_CLUSTER_TAG).inMapPx()
        assertEquals(measured.left, keepClear!!.left, 1f)
        assertEquals(measured.top, keepClear.top, 1f)
        assertEquals(measured.right, keepClear.right, 1f)
        assertEquals(measured.bottom, keepClear.bottom, 1f)
    }

    @Test
    fun `at ROTATION_90 with the L on its own left side the button stays where the rail put it`() {
        setScreen(Surface.ROTATION_90)
        val rail = tag(COMPACT_NAVIGATION_RAIL_TAG)
        val density = composeRule.density
        val slot = tag(LAYOUT_FIXES_MAP_TAG)
        val expected = with(density) { (rail.right - rail.left).roundToPx() }
        val defaults = defaultsPx()
        val renderMode = map.renderMode!!
        val settled = attributionEndInsetClearOf(
            renderMode.attributionKeepClear, with(density) { (slot.right - slot.left).roundToPx() }, with(density) { (slot.bottom - slot.top).roundToPx() },
            defaults, with(density) { (renderMode.attributionBottomInset ?: renderMode.bottomInset).roundToPx() },
            with(density) { renderMode.attributionEndInset.roundToPx() }, false,
            with(density) { ATTRIBUTION_BUTTON_DP.dp.roundToPx() }, with(density) { ATTRIBUTION_CLEAR_GAP_DP.dp.roundToPx() },
        )
        assertEquals("the L is on the far side, so the end inset is the rail's width, unchanged", expected, settled)
    }
}
