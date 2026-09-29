package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.MapSlot
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/**
 * The owner's four calls on the landscape L (continuation `2026-09-28-172`, `prompts/preserved/2026-09-29-30.md`), verbatim: "1 2 3
 * I'll take your recommendations". All in phone short landscape only (`w823dp-h384dp-land`, `ROTATION_90` with the cluster on the left
 * and `ROTATION_270` with it on the right); portrait and the tablet are unchanged.
 *
 * - (a) the top limit pushes the L down as well as pulling it up: its top is never above the search bar's bottom;
 * - (b) the search notice makes room for the L (8 + the L's width + 8 on the L's side) and the L no longer follows the notice's floor;
 * - (c) the minimise handle's touch box is 20 x 48 dp, centred on the locate row, reaching neither the compass nor the Layers row;
 * - (d) every button takes touches across its full 48 x 48 square, corners included, and a point 2 dp outside a square reaches the map.
 *
 * Every touch is a real coordinate touch. T7 and T9 are existing tests and are not edited: they must pass on their own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class LandscapeLRulingsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private class TapMapSlot {
        var taps = 0
        val slot: MapSlot = { _, _, _, _, _, onTap, _, _, modifier ->
            Box(modifier.testTag(LAYOUT_FIXES_MAP_TAG).pointerInput(Unit) { detectTapGestures(onTap = { taps++; onTap() }) })
        }
    }

    private val map = TapMapSlot()
    private var recordTouches = 0
    private var returnTouches = 0
    private var state by mutableStateOf(LAYOUT_FIXES_FIX_STATE)

    private fun setScreen(rotation: Int, withNotice: Boolean = false) {
        state = if (withNotice) LAYOUT_FIXES_FIX_STATE.copy(errorMessage = "A notice is showing, and it says something long enough to need a second line on a narrow strip.") else LAYOUT_FIXES_FIX_STATE
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        var rotationSeen: Int? = null
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            LayoutFixesScreen(
                uiState = state,
                mapSlot = map.slot,
                isRecording = true,
                onToggleRecording = { recordTouches++ },
                onToggleReturning = { returnTouches++ },
            )
        }
        settle()
        assertEquals("the screen must see the pinned rotation", rotation, rotationSeen)
    }

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun tag(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun described(description: String): DpRect = composeRule.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()
    private fun cluster(): DpRect = tag(MAP_ICON_CLUSTER_TAG)
    private fun handle(): DpRect = tag("map-icon-bar-minimize-handle")
    private fun layersRow(): DpRect = composeRule.onNode(hasContentDescription("Layers:", substring = true)).getUnclippedBoundsInRoot()
    private fun handleCentre(): Pair<Dp, Dp> = handle().let { (it.left + it.right) / 2 to (it.top + it.bottom) / 2 }

    /** The cluster's side of the screen: left at ROTATION_90 by default, right at ROTATION_270. */
    private fun snapTo(left: Boolean) {
        val (x, y) = handleCentre()
        val isLeft = (cluster().left + cluster().right) / 2 < (tag(LAYOUT_FIXES_MAP_TAG).left + tag(LAYOUT_FIXES_MAP_TAG).right) / 2
        if (isLeft == left) return
        composeRule.longPressDrag(x, y, if (left) (-500).dp else 500.dp, 0.dp)
    }

    private fun drag(dy: Dp) {
        val (x, y) = handleCentre()
        composeRule.longPressDrag(x, y, 0.dp, dy)
    }

    // ── (a) The top limit pushes the L down too ──

    private fun assertTopLimit(rotation: Int) {
        setScreen(rotation)
        val searchBottom = tag(SEARCH_ENTRY_BAR_TAG).bottom
        assertTrue("at rest the L's top ${cluster().top.value} is not above the search bar's bottom ${searchBottom.value}", cluster().top >= searchBottom - 0.5.dp)
        drag((-800).dp)
        assertEquals("dragged to the top the L rests at the search bar's bottom ${searchBottom.value}", searchBottom.value, cluster().top.value, 1f)
        assertEquals("the L is still 296 tall", 296f, (cluster().bottom - cluster().top).value, 0.5f)
        assertTrue("the L ${cluster().describe()} fits inside the map area ${tag(LAYOUT_FIXES_MAP_TAG).describe()}", cluster().bottom <= tag(LAYOUT_FIXES_MAP_TAG).bottom + 0.5.dp)
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun `A1 at ROTATION_90 the L's top is never above the search bar's bottom`() = assertTopLimit(Surface.ROTATION_90)

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun `A1 at ROTATION_270 the L's top is never above the search bar's bottom`() = assertTopLimit(Surface.ROTATION_270)

    /**
     * The top limit is the search bar's bottom (topInset) and not topInset plus the strip clearance, on the planner's condition
     * (dispatch 2026-09-28-178): nothing else may be drawn in that band on the L's side. The compass strip is in the top corner on the
     * rail side (CompactMapUi, `CompassElevationStrip`'s landscape modifier), so the L snapped to the rail side and dragged to the top
     * must not intersect it.
     */
    private fun assertClearOfTheCompassStrip(rotation: Int, railSideIsLeft: Boolean) {
        setScreen(rotation)
        snapTo(railSideIsLeft)
        settle()
        drag((-800).dp)
        val strip = tag("compass-elevation-strip")
        assertFalse("the L ${cluster().describe()} at the top of the rail side does not intersect the compass strip ${strip.describe()}", strip.overlapsRect(cluster()))
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun `A2 at ROTATION_90 the L at the top of the rail side is clear of the compass strip`() = assertClearOfTheCompassStrip(Surface.ROTATION_90, railSideIsLeft = false)

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun `A2 at ROTATION_270 the L at the top of the rail side is clear of the compass strip`() = assertClearOfTheCompassStrip(Surface.ROTATION_270, railSideIsLeft = true)

    // ── (b) The notice makes room for the L; the L stays put ──

    private fun assertNoticeMakesRoom(rotation: Int, clusterLeft: Boolean) {
        setScreen(rotation)
        snapTo(clusterLeft)
        settle()
        val before = cluster()
        state = state.copy(errorMessage = "A notice is showing, and it says something long enough to need a second line on a narrow strip.")
        settle()
        val notice = tag(SEARCH_NOTICE_TAG)
        val after = cluster()
        assertEquals("the L did not move when the notice appeared (before ${before.describe()}, after ${after.describe()})", before.top.value, after.top.value, 0.5f)
        assertEquals("the L did not move sideways", before.left.value, after.left.value, 0.5f)
        assertFalse("the notice ${notice.describe()} and the L ${after.describe()} do not overlap", notice.overlapsRect(after))
        assertTrue("the notice ${notice.describe()} is still wide enough to read (at least 200 dp)", (notice.right - notice.left) >= 200.dp)
        // The L on the notice's own side gets the 8 + width + 8 inset; the notice stays clear of it by at least 8 dp.
        val gap = if (clusterLeft) notice.left - after.right else after.left - notice.right
        if (notice.overlapsRect(DpRect(after.left - 400.dp, after.top, after.right + 400.dp, after.bottom))) {
            assertTrue("the notice ${notice.describe()} is at least 8 dp from the L ${after.describe()} (gap ${gap.value})", gap >= 8.dp - 0.5.dp)
        }
        // Dragged to the top with the notice showing, the L rests at the search bar's bottom: it does not follow the notice's floor.
        drag((-800).dp)
        val searchBottom = tag(SEARCH_ENTRY_BAR_TAG).bottom
        assertEquals("with a notice showing the L still rests at the search bar's bottom ${searchBottom.value}, not the notice's ${notice.bottom.value}", searchBottom.value, cluster().top.value, 1f)
        assertTrue("the L's top ${cluster().top.value} is above the notice's bottom ${notice.bottom.value}: it does not follow the floor", cluster().top < notice.bottom)
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun `B1 at ROTATION_90 with the L on the left a notice makes room and the L does not move`() = assertNoticeMakesRoom(Surface.ROTATION_90, clusterLeft = true)

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun `B1 at ROTATION_90 with the L on the right a notice makes room and the L does not move`() = assertNoticeMakesRoom(Surface.ROTATION_90, clusterLeft = false)

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun `B1 at ROTATION_270 with the L on the right a notice makes room and the L does not move`() = assertNoticeMakesRoom(Surface.ROTATION_270, clusterLeft = false)

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun `B1 at ROTATION_270 with the L on the left a notice makes room and the L does not move`() = assertNoticeMakesRoom(Surface.ROTATION_270, clusterLeft = true)

    // ── (c) The minimise handle's box ──

    private fun assertHandleBox(rotation: Int) {
        setScreen(rotation)
        val h = handle()
        val locate = described("Center on my location")
        val compass = described("Reset orientation to north")
        val layers = layersRow()
        assertEquals("the handle's touch box is 20 dp wide (${h.describe()})", 20f, (h.right - h.left).value, 0.5f)
        assertEquals("the handle's touch box is 48 dp tall (${h.describe()})", 48f, (h.bottom - h.top).value, 0.5f)
        assertEquals("the handle is centred on the locate row ${locate.describe()} (${h.describe()})", (locate.top.value + locate.bottom.value) / 2f, (h.top.value + h.bottom.value) / 2f, 0.5f)
        assertFalse("the handle ${h.describe()} does not reach the compass row ${compass.describe()}", h.overlapsRect(compass))
        assertFalse("the handle ${h.describe()} does not reach the Layers row ${layers.describe()}", h.overlapsRect(layers))
        val mark = composeRule.onNode(hasTestTag("map-icon-bar-minimize-handle-mark"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("the mark is unchanged: 10 dp wide", 10f, (mark.right - mark.left).value, 0.5f)
        assertEquals("the mark is unchanged: 48 dp tall, and fits the box", 48f, (mark.bottom - mark.top).value, 0.5f)
    }

    @Test fun `C1 at ROTATION_90 the handle's box is 20 by 48, centred on the locate row`() = assertHandleBox(Surface.ROTATION_90)

    @Test fun `C1 at ROTATION_270 the handle's box is 20 by 48, centred on the locate row`() = assertHandleBox(Surface.ROTATION_270)

    // ── (d) Full-square touches, corners included ──

    /** The fractions of a button's own 48 x 48 square: the probe points that fell through the rounded end, and the other corners. */
    private val cornerFractions = listOf(0.03f to 0.03f, 0.97f to 0.03f, 0.03f to 0.97f, 0.97f to 0.97f, 0.1f to 0.9f, 0.9f to 0.9f, 0.1f to 0.1f, 0.9f to 0.1f)

    private fun touch(r: DpRect, fx: Float, fy: Float) = composeRule.touchAt(r.left + (r.right - r.left) * fx, r.top + (r.bottom - r.top) * fy)

    private fun assertTopRowCorners(rotation: Int) {
        setScreen(rotation)
        var fullscreen = false
        val mapBefore = map.taps
        cornerFractions.forEach { (fx, fy) ->
            val row = composeRule.onNode(hasContentDescription("Fullscreen") or hasContentDescription("Exit fullscreen")).getUnclippedBoundsInRoot()
            touch(row, fx, fy)
            settle()
            fullscreen = !fullscreen
            val exit = composeRule.onAllNodes(hasContentDescription("Exit fullscreen")).fetchSemanticsNodes().isNotEmpty()
            assertEquals("the touch at ($fx, $fy) of the top row's square reached its button (fullscreen should now be $fullscreen)", fullscreen, exit)
        }
        assertEquals("none of the top row's corner touches fell through to the map", mapBefore, map.taps)
    }

    private fun assertBottomRowCorners(rotation: Int) {
        setScreen(rotation)
        val mapBefore = map.taps
        cornerFractions.forEach { (fx, fy) ->
            touch(described("Plan a trip or log a find here"), fx, fy)
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
            assertEquals("the touch at ($fx, $fy) of the bottom row's square opened its menu", 1, composeRule.onAllNodes(hasTestTag(ADD_ACTION_TILE_TAG)).fetchSemanticsNodes().size)
            val m = tag(LAYOUT_FIXES_MAP_TAG)
            composeRule.touchAt((m.left + m.right) / 2, (m.top + m.bottom) / 2)
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
        }
        assertEquals("none of the bottom row's corner touches fell through to the map", mapBefore, map.taps)
    }

    private fun assertPillCorners(rotation: Int) {
        setScreen(rotation)
        val mapBefore = map.taps
        cornerFractions.forEach { (fx, fy) -> touch(tag("control-pill-record"), fx, fy) }
        assertEquals("every corner touch on the record button's square reached it", cornerFractions.size, recordTouches)
        cornerFractions.forEach { (fx, fy) -> touch(tag("control-pill-return-to-vehicle"), fx, fy) }
        assertEquals("every corner touch on the return button's square reached it", cornerFractions.size, returnTouches)
        assertEquals("none of the pill's corner touches fell through to the map", mapBefore, map.taps)
    }

    @Test fun `D1 at ROTATION_90 real touches across the top row's whole square, corners included, reach its button`() = assertTopRowCorners(Surface.ROTATION_90)

    @Test fun `D1 at ROTATION_270 real touches across the top row's whole square, corners included, reach its button`() = assertTopRowCorners(Surface.ROTATION_270)

    @Test fun `D2 at ROTATION_90 real touches across the bottom row's whole square, corners included, reach its button`() = assertBottomRowCorners(Surface.ROTATION_90)

    @Test fun `D2 at ROTATION_270 real touches across the bottom row's whole square, corners included, reach its button`() = assertBottomRowCorners(Surface.ROTATION_270)

    @Test fun `D3 at ROTATION_90 real touches across both pill buttons' whole squares, corners included, reach them`() = assertPillCorners(Surface.ROTATION_90)

    @Test fun `D3 at ROTATION_270 real touches across both pill buttons' whole squares, corners included, reach them`() = assertPillCorners(Surface.ROTATION_270)

    /** A point 2 dp outside a button's square reaches the map: nothing outside the squares takes touches. */
    private fun assertJustOutsideReachesTheMap(rotation: Int, clusterOnLeft: Boolean) {
        setScreen(rotation)
        val top = described("Fullscreen")
        val bottomRow = described("Plan a trip or log a find here")
        val pill = tag("control-pill")
        val bar = DpRect(top.left, top.top, top.right, bottomRow.bottom)
        val outerX = if (clusterOnLeft) bar.left - 2.dp else bar.right + 2.dp
        val innerBarX = if (clusterOnLeft) bar.right + 2.dp else bar.left - 2.dp
        val innerPillX = if (clusterOnLeft) pill.right + 2.dp else pill.left - 2.dp
        val outerPillX = if (clusterOnLeft) pill.left - 2.dp else pill.right + 2.dp
        val cases = listOf(
            "2 dp outside the top row's outer side" to (outerX to (top.top + top.bottom) / 2),
            "2 dp outside the bottom row's outer side" to (outerX to (bottomRow.top + bottomRow.bottom) / 2),
            "2 dp inboard of the top row" to (innerBarX to (top.top + top.bottom) / 2),
            "2 dp inboard of the bottom row" to (innerBarX to (bottomRow.top + bottomRow.bottom) / 2),
            "2 dp under the bottom row (in the gap)" to ((bar.left + bar.right) / 2 to bar.bottom + 2.dp),
            "2 dp above the pill (in the gap)" to ((pill.left + pill.right) / 2 to pill.top - 2.dp),
            "2 dp beyond the pill's inboard end" to (innerPillX to (pill.top + pill.bottom) / 2),
            "2 dp beyond the pill's outer end" to (outerPillX to (pill.top + pill.bottom) / 2),
            "2 dp under the pill" to ((pill.left + pill.right) / 2 to pill.bottom + 2.dp),
        )
        cases.forEach { (name, at) ->
            val before = map.taps
            composeRule.touchAt(at.first, at.second)
            assertEquals("a real touch $name reached the map (bar ${bar.describe()}, pill ${pill.describe()}, map ${tag(LAYOUT_FIXES_MAP_TAG).describe()})", before + 1, map.taps)
        }
    }

    @Test fun `D4 at ROTATION_90 a real touch 2 dp outside any button's square reaches the map`() = assertJustOutsideReachesTheMap(Surface.ROTATION_90, clusterOnLeft = true)

    @Test fun `D4 at ROTATION_270 a real touch 2 dp outside any button's square reaches the map`() = assertJustOutsideReachesTheMap(Surface.ROTATION_270, clusterOnLeft = false)
}
