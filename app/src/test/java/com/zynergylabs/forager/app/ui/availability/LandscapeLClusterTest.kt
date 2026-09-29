package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.MapSlot
import org.junit.Assert.assertEquals
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
 * The landscape icon cluster as an L (dispatch `2026-09-28-160`). The owner's rulings, verbatim, from
 * `docs/plans/journal-redesign.md`:
 * - "Have the icon bar shrink a little and turn the small pill 90°, then half of the small pill can fit beneath the
 *   icon bar, and extend out." / "Make the pill the same size as the bar";
 * - "Oh yeah on either side it looks like an L. On the right side it just looks like an inverse L";
 * - "Only apply on landscape phone mode. Never on portrait or tablet mode.";
 * - on the height, "A": the bar is five 48 dp rows with no spacing or end padding (240), an 8 dp gap, then the
 *   horizontal pill (48 thick): 296 in all, with nothing drawn around the L.
 *
 * `w823dp-h384dp-land` at `ROTATION_90` (cluster on the left) and `ROTATION_270` (cluster on the right). Every touch is a
 * real coordinate touch (CLAUDE.md: a semantic click asserts wiring, not routing). Robolectric reports no window insets, so
 * the L against the S22's real ones is a device item.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class LandscapeLClusterTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    /** A map stand-in that takes pointer input over its whole bounds as the real `AndroidView` does, and counts its long-presses and taps. */
    private class LongPressMapSlot {
        var longPresses = 0
        var taps = 0
        val slot: MapSlot = { _, _, _, _, _, onTap, _, _, modifier ->
            Box(
                modifier.testTag(LAYOUT_FIXES_MAP_TAG).pointerInput(Unit) {
                    detectTapGestures(onLongPress = { longPresses++ }, onTap = { taps++; onTap() })
                },
            )
        }
    }

    private val map = LongPressMapSlot()

    private fun setScreen(rotation: Int, isRecording: Boolean = true, uiState: AvailabilityUiState = LAYOUT_FIXES_FIX_STATE) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        var rotationSeen: Int? = null
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            LayoutFixesScreen(uiState = uiState, mapSlot = map.slot, isRecording = isRecording)
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("the screen must see the pinned rotation", rotation, rotationSeen)
    }

    private fun tag(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun described(description: String): DpRect = composeRule.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()
    private fun cluster(): DpRect = tag(MAP_ICON_CLUSTER_TAG)
    private fun fullscreenRow(): DpRect = described("Fullscreen")
    private fun addRow(): DpRect = described("Plan a trip or log a find here")
    private fun pill(): DpRect = tag("control-pill")

    /** The MapIconBar's own bounds now that it has no end padding: its first row's top to its last row's bottom. */
    private fun bar(): DpRect = DpRect(fullscreenRow().left, fullscreenRow().top, fullscreenRow().right, addRow().bottom)

    private fun ComposeTestRule.longPressAt(x: Dp, y: Dp) {
        val at = with(density) { Offset(x.toPx(), y.toPx()) }
        onAllNodes(isRoot()).onFirst().performTouchInput {
            down(at)
            advanceEventTime(700)
            up()
        }
        waitForIdle()
    }

    private fun handleCentre(): Pair<Dp, Dp> = tag("map-icon-bar-minimize-handle").let { (it.left + it.right) / 2 to (it.top + it.bottom) / 2 }

    private fun assertNear(message: String, expected: Float, actual: Dp, tolerance: Float = 0.5f) =
        assertEquals("$message (bar ${bar().describe()}, pill ${pill().describe()}, cluster ${cluster().describe()})", expected, actual.value, tolerance)

    // ── The shape ──

    private fun assertShape(rotation: Int, clusterOnLeft: Boolean) {
        setScreen(rotation)
        val b = bar()
        val p = pill()
        val record = tag("control-pill-record")
        val ret = tag("control-pill-return-to-vehicle")
        assertNear("the bar is 48 wide", 48f, b.right - b.left)
        assertNear("the bar is 240 tall: five 48 dp rows with no spacing or end padding", 240f, b.bottom - b.top)
        assertNear("the compass row starts where the fullscreen row ends", b.top.value + 48f, described("Reset orientation to north").top)
        assertNear("the locate row is the third row, flush", b.top.value + 96f, described("Center on my location").top)
        assertNear("the add row is the fifth row, flush", b.top.value + 192f, addRow().top)
        assertNear("the gap between the bar and the pill is 8", 8f, p.top - b.bottom)
        assertNear("the pill is 48 thick", 48f, p.bottom - p.top)
        assertNear("the pill is 96 wide: record under the bar, return beside it", 96f, p.right - p.left)
        assertNear("the record button is 48 wide", 48f, record.right - record.left)
        // Continuation 2026-09-28-162, the owner: "the icons need to stack fully": record's box is exactly under the bar's column, both edges.
        assertNear("the record button's left edge equals the bar's", b.left.value, record.left)
        assertNear("the record button's right edge equals the bar's", b.right.value, record.right)
        assertNear("the return button is 48 wide", 48f, ret.right - ret.left)
        if (clusterOnLeft) {
            assertNear("the pill's outer end is flush with the bar's outer (left) edge", b.left.value, p.left)
            assertNear("the record button sits directly under the bar", b.left.value, record.left)
            assertTrue("the return button ${ret.describe()} extends inboard (right) of the record button ${record.describe()}", ret.left >= record.right - 0.5.dp)
        } else {
            assertNear("the pill's outer end is flush with the bar's outer (right) edge", b.right.value, p.right)
            assertNear("the record button sits directly under the bar", b.right.value, record.right)
            assertTrue("the return button ${ret.describe()} extends inboard (left) of the record button ${record.describe()}", ret.right <= record.left + 0.5.dp)
        }
        assertNear("the cluster is 296 tall: 240 + 8 + 48", 296f, cluster().bottom - cluster().top)
        assertNear("the cluster is as wide as the pill, 96", 96f, cluster().right - cluster().left)
    }

    @Test fun `L1 at ROTATION_90 the cluster is an L, bar 240, gap 8, pill 48 thick, 296 in all`() = assertShape(Surface.ROTATION_90, clusterOnLeft = true)

    @Test fun `L1 at ROTATION_270 the cluster is an inverse L, bar 240, gap 8, pill 48 thick, 296 in all`() = assertShape(Surface.ROTATION_270, clusterOnLeft = false)

    // ── Real touches on the bar's end rows, now that there is no end padding ──

    /** Five real touches across the row's own bounds: the four points at 0.2 and 0.8 of each side, and the centre. */
    private fun fractions() = listOf(0.2f to 0.2f, 0.8f to 0.2f, 0.5f to 0.5f, 0.2f to 0.8f, 0.8f to 0.8f)

    private fun touchAcross(r: DpRect, each: () -> Unit) = fractions().forEach { (fx, fy) ->
        composeRule.touchAt(r.left + (r.right - r.left) * fx, r.top + (r.bottom - r.top) * fy)
        each()
    }

    private fun assertFullscreenRowReachedAtEveryPoint(rotation: Int) {
        setScreen(rotation)
        var expectFullscreen = false
        val before = map.taps
        // The row's bounds are read again after every touch, once the layout has settled: each toggle of fullscreen hides or brings back
        // the search bar, and the L now moves with its top limit (owner's ruling (a), continuation 2026-09-28-172).
        fractions().forEach { (fx, fy) ->
            val row = composeRule.onNode(androidx.compose.ui.test.hasContentDescription("Fullscreen") or androidx.compose.ui.test.hasContentDescription("Exit fullscreen")).getUnclippedBoundsInRoot()
            composeRule.touchAt(row.left + (row.right - row.left) * fx, row.top + (row.bottom - row.top) * fy)
            composeRule.mainClock.advanceTimeBy(2_000)
            composeRule.waitForIdle()
            expectFullscreen = !expectFullscreen
            val shown = composeRule.onAllNodes(androidx.compose.ui.test.hasContentDescription(if (expectFullscreen) "Exit fullscreen" else "Fullscreen"))
            assertEquals("the touch at ($fx, $fy) toggled fullscreen (expected fullscreen=$expectFullscreen)", 1, shown.fetchSemanticsNodes().size)
        }
        assertEquals("none of the five touches on the top row fell through to the map", before, map.taps)
    }

    @Test fun `L2 at ROTATION_90 five real touches across the top row of the bar all reach it`() = assertFullscreenRowReachedAtEveryPoint(Surface.ROTATION_90)

    @Test fun `L2 at ROTATION_270 five real touches across the top row of the bar all reach it`() = assertFullscreenRowReachedAtEveryPoint(Surface.ROTATION_270)

    private fun assertAddRowReachedAtEveryPoint(rotation: Int) {
        setScreen(rotation)
        val before = map.taps
        val row = addRow()
        fractions().forEach { (fx, fy) ->
            composeRule.touchAt(row.left + (row.right - row.left) * fx, row.top + (row.bottom - row.top) * fy)
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
            assertEquals("the touch at ($fx, $fy) of the add row opened its menu", 1, composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ADD_ACTION_TILE_TAG)).fetchSemanticsNodes().size)
            // Dismiss through its scrim, a real touch at the screen's centre, before the next touch.
            val m = tag(LAYOUT_FIXES_MAP_TAG)
            composeRule.touchAt((m.left + m.right) / 2, (m.top + m.bottom) / 2)
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
        }
        assertEquals("none of the touches on the bottom row fell through to the map", before, map.taps)
    }

    @Test fun `L2 at ROTATION_90 five real touches across the bottom row of the bar all reach it`() = assertAddRowReachedAtEveryPoint(Surface.ROTATION_90)

    @Test fun `L2 at ROTATION_270 five real touches across the bottom row of the bar all reach it`() = assertAddRowReachedAtEveryPoint(Surface.ROTATION_270)

    /**
     * The compass row takes the full five-point spread (the minimise handle, 20 x 48 dp centred on the locate row since the owner's
     * ruling (c), no longer reaches it); the locate row is touched at its centre column and inboard 0.2/0.8 columns, because the handle
     * owns the outer 12 dp of the locate row by design.
     */
    private fun assertMiddleRowsTakeTouches(rotation: Int, clusterOnLeft: Boolean) {
        setScreen(rotation)
        val before = map.taps
        val inboard = if (clusterOnLeft) 0.8f else 0.2f
        val points = listOf(inboard to 0.2f, 0.5f to 0.5f, inboard to 0.8f, 0.5f to 0.2f, 0.5f to 0.8f)
        touchAcross(described("Reset orientation to north")) {}
        described("Center on my location").let { r -> points.forEach { (fx, fy) -> composeRule.touchAt(r.left + (r.right - r.left) * fx, r.top + (r.bottom - r.top) * fy) } }
        assertEquals("none of the ten touches on the compass and locate rows fell through to the map", before, map.taps)
    }

    @Test fun `L2 at ROTATION_90 real touches on the compass and locate rows do not fall through to the map`() = assertMiddleRowsTakeTouches(Surface.ROTATION_90, clusterOnLeft = true)

    @Test fun `L2 at ROTATION_270 real touches on the compass and locate rows do not fall through to the map`() = assertMiddleRowsTakeTouches(Surface.ROTATION_270, clusterOnLeft = false)

    // ── Nothing drawn around the L: the empty corner and the gap reach the map ──

    private fun assertCornerAndGapReachTheMap(rotation: Int, clusterOnLeft: Boolean) {
        setScreen(rotation)
        val b = bar()
        val p = pill()
        val midY = (b.top + b.bottom) / 2
        val cornerX = if (clusterOnLeft) b.right + 24.dp else b.left - 24.dp
        val outerX = (b.left + b.right) / 2
        val inboardOverExtensionX = if (clusterOnLeft) p.right - 12.dp else p.left + 12.dp
        val gapY = (b.bottom + p.top) / 2
        val cases = listOf(
            "the empty corner inboard of the bar" to (cornerX to midY),
            "the gap under the bar" to (outerX to gapY),
            "the gap above the pill's inboard extension" to (inboardOverExtensionX to gapY),
        )
        cases.forEach { (name, at) ->
            val before = map.longPresses
            composeRule.longPressAt(at.first, at.second)
            assertEquals("a real long-press in $name reached the map (bar ${b.describe()}, pill ${p.describe()})", before + 1, map.longPresses)
        }
        // The positive control: the same gesture on the bar itself must not reach the map, or the three above prove nothing.
        val before = map.longPresses
        composeRule.longPressAt(outerX, midY)
        assertEquals("a real long-press on the bar itself did not reach the map", before, map.longPresses)
        composeRule.longPressAt((p.left + p.right) / 2, (p.top + p.bottom) / 2)
        assertEquals("a real long-press on the pill itself did not reach the map", before, map.longPresses)
    }

    @Test fun `L3 at ROTATION_90 a real long-press in the empty corner and in the gap reaches the map`() = assertCornerAndGapReachTheMap(Surface.ROTATION_90, clusterOnLeft = true)

    @Test fun `L3 at ROTATION_270 a real long-press in the empty corner and in the gap reaches the map`() = assertCornerAndGapReachTheMap(Surface.ROTATION_270, clusterOnLeft = false)

    // ── The L's measured box drives the clamps ──

    private fun assertBottomClamp(rotation: Int) {
        setScreen(rotation)
        val (x, y) = handleCentre()
        composeRule.longPressDrag(x, y, 0.dp, 800.dp)
        val c = cluster()
        val m = tag(LAYOUT_FIXES_MAP_TAG)
        assertNear("the cluster is 296 tall after a long drag down", 296f, c.bottom - c.top)
        assertTrue("the cluster ${c.describe()} stays inside the map area ${m.describe()}", c.bottom <= m.bottom + 0.5.dp)
        composeRule.touchAt((fullscreenRow().left + fullscreenRow().right) / 2, (fullscreenRow().top + fullscreenRow().bottom) / 2)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val (x2, y2) = handleCentre()
        composeRule.longPressDrag(x2, y2, 0.dp, 800.dp)
        val f = cluster()
        // Not assertNear: its message reads the bar's rows, and "Fullscreen" is "Exit fullscreen" here.
        assertEquals("in fullscreen the cluster ${f.describe()} is 296 tall", 296f, (f.bottom - f.top).value, 0.5f)
        assertEquals("in fullscreen the cluster's bottom ${f.bottom.value} is the map area's ${m.bottom.value}", m.bottom.value, f.bottom.value, 1f)
    }

    @Test fun `L4 at ROTATION_90 the bottom clamp uses the L's 296 tall box, with the nav and in fullscreen`() = assertBottomClamp(Surface.ROTATION_90)

    @Test fun `L4 at ROTATION_270 the bottom clamp uses the L's 296 tall box, with the nav and in fullscreen`() = assertBottomClamp(Surface.ROTATION_270)

    // The notice-floor tests that were here (the cluster's top held at or below a notice, or the lowest edge where it does not fit) are
    // superseded by the owner's ruling (dispatch 2026-09-28-172, "1 2 3  I'll take your recommendations"): in landscape the L no
    // longer follows the notice's floor; the notice makes room for the L instead. See LandscapeLRulingsTest.

    // ── The AddActionTile anchor follows the L's row pitch ──

    /** Where the add menu's panel sits against the add row it opened from, in dp: its bottom edge less the add row's centre. */
    private fun panelBottomFromAddRowCentre(): Float {
        val row = addRow()
        composeRule.touchAt((row.left + row.right) / 2, (row.top + row.bottom) / 2)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        val panel = tag(ADD_ACTION_TILE_TAG)
        return panel.bottom.value - (row.top.value + row.bottom.value) / 2f
    }

    @Test fun `L6 at ROTATION_90 the add menu's panel is placed against the L's add row`() {
        setScreen(Surface.ROTATION_90)
        // A characterisation, pinned from the value read at the implementation (32 dp): the panel's own offsets are not derived here. What it
        // holds is the L's row pitch: anchoring with the portrait pitch (104 dp to the add row, not 96) moves the panel 8 dp, to 40.
        assertEquals("the add menu's panel bottom is 32 dp below the add row's centre (the anchor uses the L's 48 dp row pitch)", 32f, panelBottomFromAddRowCentre(), 0.5f)
    }
}
