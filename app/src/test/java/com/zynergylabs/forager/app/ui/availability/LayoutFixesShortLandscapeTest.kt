package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
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
import kotlin.math.abs

/**
 * Part 1 layout fixes in the short landscape window (`w823dp-h384dp-land`, the S22 Ultra's landscape
 * window), at `ROTATION_90` and `ROTATION_270`, each pinned and seen by the screen.
 *
 * The owner's ruling "For icon column in short landscape: option A" (planner message `2026-09-28-99`):
 * the ControlPill sits beside the MapIconBar, on its inboard side and bottom-aligned (the placement this
 * dispatch's report proposes), so the cluster is the bar's height and fits the window. Also item 9 (the
 * search bar clear of the cluster) and item 5's wiring (the attribution end inset the map is handed).
 * Robolectric reports no status bar, navigation bar or cut-out, so the S22's own margins are device items.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class LayoutFixesShortLandscapeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val map = LayoutFixesMapSlot()
    private var recordTouches = 0
    private var returnTouches = 0

    private fun setScreen(rotation: Int, isRecording: Boolean = false) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        var rotationSeen: Int? = null
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            LayoutFixesScreen(
                uiState = LAYOUT_FIXES_FIX_STATE,
                mapSlot = map.slot,
                isRecording = isRecording,
                onToggleRecording = { recordTouches++ },
                onToggleReturning = { returnTouches++ },
            )
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
    private fun mapArea(): DpRect = tag(LAYOUT_FIXES_MAP_TAG)

    /**
     * The MapIconBar's own bounds: its first and last rows. Superseded 2026-09-29 (dispatch 2026-09-28-160, the owner's "A"): the
     * landscape bar has no spacing between rows and no end padding, so its bounds are its rows' (was: the rows and 4 dp of padding).
     */
    private fun bar(): DpRect = DpRect(fullscreenRow().left, fullscreenRow().top, fullscreenRow().right, addRow().bottom)

    // ── The reshape (option A) ──

    private fun assertClusterFits(rotation: Int) {
        setScreen(rotation, isRecording = true)
        val c = cluster()
        val b = bar()
        val m = mapArea()
        val ret = tag("control-pill-return-to-vehicle")
        // TR1 superseded 2026-09-29 (dispatch 2026-09-28-160). It pinned "the cluster is at or below the bar's span" for the
        // side-by-side shape. The owner's ruling, verbatim: "Have the icon bar shrink a little and turn the small pill 90°, then
        // half of the small pill can fit beneath the icon bar, and extend out", then, on the height, "A": the bar is 240, an 8 dp
        // gap, the 48 dp pill, 296 in all.
        assertEquals("the cluster ${c.describe()} is 296 tall: the 240 bar, the 8 gap, the 48 pill", 296f, (c.bottom - c.top).value, 0.5f)
        assertEquals("the bar ${b.describe()} is 240 tall", 240f, (b.bottom - b.top).value, 0.5f)
        assertTrue("the cluster ${c.describe()} lies inside the map area ${m.describe()}", c.top >= m.top && c.bottom <= m.bottom)
        assertEquals("the return button ${ret.describe()} is whole, 48 dp tall", 48f, (ret.bottom - ret.top).value, 0.5f)
        assertTrue("the return button ${ret.describe()} lies inside the cluster ${c.describe()}", ret.top >= c.top && ret.bottom <= c.bottom)
    }

    @Test fun `TR1 at ROTATION_90 the cluster fits the map area and its pill's rows are whole`() = assertClusterFits(Surface.ROTATION_90)

    @Test fun `TR1 at ROTATION_270 the cluster fits the map area and its pill's rows are whole`() = assertClusterFits(Surface.ROTATION_270)

    /**
     * The pill is beneath the bar, turned horizontal: its outer end flush with the bar's outer edge, record under the bar and
     * return extending inboard, 8 dp below the bar. Superseded 2026-09-29 (dispatch 2026-09-28-160): this pinned the pill beside
     * the bar, bottom-aligned, per the owner's "option A" for the short landscape column; the owner's later ruling, verbatim, is
     * "Oh yeah on either side it looks like an L. On the right side it just looks like an inverse L".
     */
    private fun assertPillInboardAndBottomAligned(clusterOnLeft: Boolean) {
        val p = pill()
        val b = bar()
        val record = tag("control-pill-record")
        val ret = tag("control-pill-return-to-vehicle")
        assertEquals("the pill ${p.describe()} is 8 dp below the bar ${b.describe()}", 8f, (p.top - b.bottom).value, 0.5f)
        assertEquals("the pill ${p.describe()} is 48 thick", 48f, (p.bottom - p.top).value, 0.5f)
        if (clusterOnLeft) {
            assertEquals("the pill ${p.describe()}'s outer (left) end is flush with the bar ${b.describe()}'s", b.left.value, p.left.value, 0.5f)
            assertTrue("the return button ${ret.describe()} extends inboard (right) of the record button ${record.describe()}", ret.left >= record.right - 0.5.dp)
        } else {
            assertEquals("the pill ${p.describe()}'s outer (right) end is flush with the bar ${b.describe()}'s", b.right.value, p.right.value, 0.5f)
            assertTrue("the return button ${ret.describe()} extends inboard (left) of the record button ${record.describe()}", ret.right <= record.left + 0.5.dp)
        }
    }

    @Test
    fun `TR2 at ROTATION_90 the pill sits beneath the bar, flush on its outer end, extending inboard`() {
        setScreen(Surface.ROTATION_90)
        assertPillInboardAndBottomAligned(clusterOnLeft = true)
    }

    @Test
    fun `TR2 at ROTATION_270 the pill sits beneath the bar, flush on its outer end, extending inboard`() {
        setScreen(Surface.ROTATION_270)
        assertPillInboardAndBottomAligned(clusterOnLeft = false)
    }

    /** Five real touches spread across each of the pill's two controls; each reaches it. */
    private fun assertPillControlsReachable(rotation: Int) {
        setScreen(rotation, isRecording = true)
        val fractions = listOf(0.2f to 0.2f, 0.8f to 0.2f, 0.5f to 0.5f, 0.2f to 0.8f, 0.8f to 0.8f)
        fun touchAcross(r: DpRect) = fractions.forEach { (fx, fy) ->
            composeRule.touchAt(r.left + (r.right - r.left) * fx, r.top + (r.bottom - r.top) * fy)
        }
        touchAcross(tag("control-pill-record"))
        assertEquals("all five touches across the record button reached it", 5, recordTouches)
        touchAcross(tag("control-pill-return-to-vehicle"))
        assertEquals("all five touches across the return button reached it", 5, returnTouches)
    }

    @Test fun `TR3 guard at ROTATION_90 real touches across the pill's record and return buttons reach them`() = assertPillControlsReachable(Surface.ROTATION_90)

    @Test fun `TR3 guard at ROTATION_270 real touches across the pill's record and return buttons reach them`() = assertPillControlsReachable(Surface.ROTATION_270)

    private fun handleCentre(): Pair<Dp, Dp> = tag("map-icon-bar-minimize-handle").let { (it.left + it.right) / 2 to (it.top + it.bottom) / 2 }

    @Test
    fun `TR4 at ROTATION_90 a snap to the far side keeps the pill beneath the bar, extending inboard`() {
        setScreen(Surface.ROTATION_90)
        val (x, y) = handleCentre()
        composeRule.longPressDrag(x, y, 200.dp, 0.dp)
        assertTrue("the cluster ${cluster().describe()} snapped to the right side", (cluster().left + cluster().right) / 2 > (mapArea().left + mapArea().right) / 2)
        assertPillInboardAndBottomAligned(clusterOnLeft = false)
    }

    @Test
    fun `TR4 at ROTATION_270 a snap to the far side keeps the pill beneath the bar, extending inboard`() {
        setScreen(Surface.ROTATION_270)
        val (x, y) = handleCentre()
        composeRule.longPressDrag(x, y, (-200).dp, 0.dp)
        assertTrue("the cluster ${cluster().describe()} snapped to the left side", (cluster().left + cluster().right) / 2 < (mapArea().left + mapArea().right) / 2)
        assertPillInboardAndBottomAligned(clusterOnLeft = true)
    }

    private fun assertDragAndHandlesWork(rotation: Int) {
        setScreen(rotation)
        val before = cluster()
        val (x, y) = handleCentre()
        composeRule.longPressDrag(x, y, 0.dp, 40.dp)
        val after = cluster()
        assertEquals("a 40 dp drag down moved the cluster 40 dp (${before.describe()} to ${after.describe()})", 40f, (after.top - before.top).value, 1f)

        val (hx, hy) = handleCentre()
        composeRule.touchAt(hx, hy)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertTrue("a touch on the minimise handle minimised the cluster", composeRule.onAllNodesWithTag(MAP_ICON_CLUSTER_TAG).fetchSemanticsNodes().isEmpty())
        val restore = described("Show map controls")
        composeRule.touchAt((restore.left + restore.right) / 2, (restore.top + restore.bottom) / 2)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertTrue("a touch on the restore handle brought it back at the same place (${cluster().describe()})", abs((cluster().top - after.top).value) <= 1f)
    }

    // Marked NATIVE on the planner's authority (dispatch 2026-09-28-178, option (1), quoted in the report): the assertion and the 40 dp drag are
    // unchanged; only the text metrics change, from Robolectric's legacy ones (the search bar 85 dp tall, which no device shows) to real
    // ones (45 dp), as T9 and the chip tests already are. Under the legacy metrics the L, held below the search bar by the owner's
    // ruling (a) of 2026-09-28-172, has 3 dp of travel in the 384 dp window and no drag of 40 dp can move it.
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun `TR5 at ROTATION_90 the handle drags the cluster down and minimises and restores it`() = assertDragAndHandlesWork(Surface.ROTATION_90)

    // Marked NATIVE on the planner's authority (dispatch 2026-09-28-178, option (1), quoted in the report): the assertion and the 40 dp drag are
    // unchanged; only the text metrics change, from Robolectric's legacy ones (the search bar 85 dp tall, which no device shows) to real
    // ones (45 dp), as T9 and the chip tests already are. Under the legacy metrics the L, held below the search bar by the owner's
    // ruling (a) of 2026-09-28-172, has 3 dp of travel in the 384 dp window and no drag of 40 dp can move it.
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun `TR5 at ROTATION_270 the handle drags the cluster down and minimises and restores it`() = assertDragAndHandlesWork(Surface.ROTATION_270)

    // ── Item 9 ──

    private fun assertSearchBarClear(rotation: Int) {
        setScreen(rotation)
        val bar = tag(SEARCH_ENTRY_BAR_TAG)
        assertFalse("the search bar ${bar.describe()} and the cluster ${cluster().describe()} do not intersect", bar.overlapsRect(cluster()))
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun `T9 at ROTATION_90 the search bar and the cluster do not intersect`() = assertSearchBarClear(Surface.ROTATION_90)

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun `T9 at ROTATION_270 the search bar and the cluster do not intersect`() = assertSearchBarClear(Surface.ROTATION_270)

    // ── Item 5, the wiring ──

    @Test
    fun `T5 at ROTATION_90 the map is handed the rail's width as the attribution end inset, and none in fullscreen`() {
        setScreen(Surface.ROTATION_90)
        val rail = tag(COMPACT_NAVIGATION_RAIL_TAG)
        assertTrue("the rail ${rail.describe()} is on the right, the attribution's end side", rail.right >= mapArea().right - 0.5.dp)
        assertEquals("the end inset is the rail's width", (rail.right - rail.left).value, map.renderMode!!.attributionEndInset.value, 0.5f)

        val fullscreen = fullscreenRow()
        composeRule.touchAt((fullscreen.left + fullscreen.right) / 2, (fullscreen.top + fullscreen.bottom) / 2)
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
        assertEquals("in fullscreen the end inset follows the caption to the edge", 0f, map.renderMode!!.attributionEndInset.value, 0.5f)
    }

    @Test
    fun `T5 guard at ROTATION_270 the attribution end inset is zero, the end side being the punch-hole side`() {
        setScreen(Surface.ROTATION_270)
        assertEquals(0f, map.renderMode!!.attributionEndInset.value, 0.5f)
    }
}
