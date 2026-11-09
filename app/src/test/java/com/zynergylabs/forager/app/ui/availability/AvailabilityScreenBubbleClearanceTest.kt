package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_CLOSE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
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
 * Item 3 of dispatch 2026-09-29-57 (amendment -262, the owner's "Push the card clear"): in the short landscape window a
 * bubble's card stays clear of the L and of the overlaid rail. A real touch on a glyph opens the bubble (through the stub
 * map's `renderMode.onFeatureTap`, as `SightingsMap`'s sink does), and the card's own bounds and its close X are compared
 * with the L's and the rail's measured bounds. Robolectric reports no cut-out and no system bars, so the real cut-out is a
 * device item; the L's and the rail's own measured bounds are what these tests hold the card to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class AvailabilityScreenBubbleClearanceTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val glyphs = mutableStateListOf<StubGlyph>()
    private val map = BubbleMapSlot(glyphs)

    private fun setScreen(rotation: Int) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        var rotationSeen: Int? = null
        val viewModel = mapLayersViewModel()
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            MapLayersTestScreen(viewModel = viewModel, mapSlot = map.slot, store = com.zynergylabs.forager.app.domain.AbsentForecastCellStore, waypoints = listOf(BUBBLE_WAYPOINT))
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("the screen must see the pinned rotation", rotation, rotationSeen)
    }

    private fun tag(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    /** A glyph whose centre is at root ([x], [y]) dp (the map slot is full-bleed, so its own origin is looked up, not assumed). */
    private fun addGlyphAtRoot(x: Dp, y: Dp) {
        val slot = tag("map-slot")
        glyphs.add(StubGlyph(MapLayerIds.WAYPOINTS, "wp-1", x - slot.left, y - slot.top, LatLng(45.326, -122.634)))
        composeRule.waitForIdle()
    }

    private fun openBubbleOnGlyph() {
        composeRule.onNodeWithTag(glyphTag("wp-1")).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
    }

    private fun assertCardClear(what: String, card: DpRect, obstacle: DpRect) =
        assertFalse("the card ${card.describe()} does not overlap $what ${obstacle.describe()}", card.overlapsRect(obstacle))

    /** The card and its close X are clear of the L and of the rail. */
    private fun assertBubbleClearOfChrome() {
        val card = tag(MAP_BUBBLE_TAG)
        val close = tag(MAP_BUBBLE_CLOSE_TAG)
        val cluster = tag(MAP_ICON_CLUSTER_TAG)
        val rail = tag(COMPACT_NAVIGATION_RAIL_TAG)
        assertCardClear("the L", card, cluster)
        assertCardClear("the rail", card, rail)
        assertCardClear("the L (close X)", close, cluster)
        assertCardClear("the rail (close X)", close, rail)
    }

    @Test
    fun `at ROTATION_90 a bubble for a glyph 30 dp from the L on its inboard side opens clear of the L and the rail`() {
        setScreen(Surface.ROTATION_90)
        val cluster = tag(MAP_ICON_CLUSTER_TAG)
        assertTrue("the L ${cluster.describe()} starts on the left at 90", (cluster.left + cluster.right) / 2 < tag("map-slot").right / 2)
        addGlyphAtRoot(cluster.right + 30.dp, (cluster.top + cluster.bottom) / 2)

        openBubbleOnGlyph()

        assertBubbleClearOfChrome()
    }

    // RECORD -956 (the owner: "One should look exactly line the other"): the rail is on the right at 270 too, so the glyph goes 30 dp inboard of its
    // left edge. Was: "the rail is on the left at 270", `rail.left <= map-slot.left`, and the glyph at `rail.right + 30.dp`.
    @Test
    fun `at ROTATION_270 a bubble for a glyph 30 dp from the rail's inner edge opens clear of the rail and the L`() {
        setScreen(Surface.ROTATION_270)
        val rail = tag(COMPACT_NAVIGATION_RAIL_TAG)
        assertTrue("the rail ${rail.describe()} is on the right at 270", rail.right >= tag("map-slot").right - 0.5.dp)
        addGlyphAtRoot(rail.left - 30.dp, 200.dp)

        openBubbleOnGlyph()

        assertBubbleClearOfChrome()
    }

    @Test
    fun `at ROTATION_90 with the L on the right a bubble for a glyph 30 dp inboard of it stays clear of the L and the rail`() {
        setScreen(Surface.ROTATION_90)
        val handle = tag("map-icon-bar-minimize-handle")
        composeRule.longPressDrag((handle.left + handle.right) / 2, (handle.top + handle.bottom) / 2, 500.dp, 0.dp)
        val cluster = tag(MAP_ICON_CLUSTER_TAG)
        assertTrue("the L ${cluster.describe()} snapped to the right", (cluster.left + cluster.right) / 2 > tag("map-slot").right / 2)
        addGlyphAtRoot(cluster.left - 30.dp, (cluster.top + cluster.bottom) / 2)

        openBubbleOnGlyph()

        assertBubbleClearOfChrome()
    }
}
