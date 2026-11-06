package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.provider.Settings
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.map.MAP_BAR_PRESS_HIGHLIGHT_TAG
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_TAG
import com.zynergylabs.forager.app.ui.map.MapBarHighlight
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.motion.PressBounceScaleKey
import com.zynergylabs.forager.app.ui.motion.PressHighlightShapeKey
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import kotlin.math.hypot
import kotlin.math.min
import org.junit.After
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
 * Motion Part 1 (dispatch 2026-09-28-652), items 2 and 4, on the map icon bar and the record | return pill, driven through the
 * real screen under the app's theme ([ForagerTheme], which provides the reduce-motion setting from its real source).
 *
 * - **The highlight fits the bar** (the owner, RECORD -651: "Rounded, fits the bar"): every row's highlight is read from the
 *   laid-out tree, its bounds and shape, and checked to lie inside the bar's or the pill's stadium, corner arcs included; the Add
 *   and Record highlights are their 36 dp badges' circles.
 * - **Touch areas unchanged**: real coordinate touches sampled across each row's bounds, in the strip between the row's edge and
 *   its now smaller highlight, still reach that row's own action; the minimise and restore handles still take touches across
 *   their boxes; a long-press just beside the cluster still reaches the map.
 * - **The bounce** dips the icon while a real finger is down, and not under the phone's animations turned off, where the press
 *   still lands.
 *
 * What these cannot show: what the highlight looks like (a ripple's pixels under Robolectric are not trusted here), and the
 * bounce's feel. Both are device checks.
 */
abstract class MapBarPressFeedbackTests(private val rotation: Int, private val landscape: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private class CountingMap {
        var taps = 0
        var longPresses = 0
        val slot: MapSlot = { _, _, _, _, _, onTap, _, _, modifier ->
            Box(modifier.testTag(LAYOUT_FIXES_MAP_TAG).pointerInput(Unit) { detectTapGestures(onLongPress = { longPresses++ }, onTap = { taps++; onTap() }) })
        }
    }

    private val map = CountingMap()
    private var recordToggles = 0
    private var returnToggles = 0

    private val resolver get() = ApplicationProvider.getApplicationContext<Application>().contentResolver

    @After
    fun animationsBackOn() {
        composeRule.mainClock.autoAdvance = true
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun setScreen() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent {
            ForagerTheme {
                LayoutFixesScreen(
                    uiState = LAYOUT_FIXES_FIX_STATE,
                    mapSlot = map.slot,
                    isRecording = true,
                    onToggleRecording = { recordToggles++ },
                    onToggleReturning = { returnToggles++ },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun dp(px: Float): Dp = with(composeRule.density) { px.toDp() }
    private fun SemanticsNode.dpBounds(): DpRect = boundsInRoot.let { DpRect(dp(it.left), dp(it.top), dp(it.right), dp(it.bottom)) }
    private fun tag(tag: String): DpRect = composeRule.onNodeWithTag(tag).fetchSemanticsNode().dpBounds()
    private fun described(description: String): DpRect = composeRule.onNodeWithContentDescription(description).fetchSemanticsNode().dpBounds()

    private fun touchAt(x: Dp, y: Dp) = composeRule.touchAt(x, y)

    private fun longPressAt(x: Dp, y: Dp) {
        val at = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput {
            down(at)
            advanceEventTime(700)
            up()
        }
        composeRule.waitForIdle()
    }

    // ── The highlight's shape and fit ──

    private data class Highlight(val bounds: DpRect, val shape: Shape)

    private fun highlights(): List<Highlight> =
        composeRule.onAllNodes(hasTestTag(MAP_BAR_PRESS_HIGHLIGHT_TAG), useUnmergedTree = true).fetchSemanticsNodes().map {
            Highlight(it.dpBounds(), it.config[PressHighlightShapeKey])
        }

    private fun highlightOf(description: String): Highlight =
        composeRule.onNode(hasTestTag(MAP_BAR_PRESS_HIGHLIGHT_TAG) and hasAnyAncestor(hasContentDescription(description)), useUnmergedTree = true)
            .fetchSemanticsNode().let { Highlight(it.dpBounds(), it.config[PressHighlightShapeKey]) }

    private fun highlightInTag(tag: String): Highlight =
        composeRule.onNode(hasTestTag(MAP_BAR_PRESS_HIGHLIGHT_TAG) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .fetchSemanticsNode().let { Highlight(it.dpBounds(), it.config[PressHighlightShapeKey]) }

    /** Distance from ([x], [y]) to the stadium's spine: the segment its end circles' centres lie on. */
    private fun distanceToSpine(stadium: DpRect, x: Float, y: Float): Float {
        val w = (stadium.right - stadium.left).value
        val h = (stadium.bottom - stadium.top).value
        val r = min(w, h) / 2f
        return if (h >= w) {
            val cx = (stadium.left.value + stadium.right.value) / 2f
            val sy = y.coerceIn(stadium.top.value + r, stadium.bottom.value - r)
            hypot(x - cx, y - sy)
        } else {
            val cy = (stadium.top.value + stadium.bottom.value) / 2f
            val sx = x.coerceIn(stadium.left.value + r, stadium.right.value - r)
            hypot(x - sx, y - cy)
        }
    }

    /** Whether [h] lies inside [stadium]: its corner circles (or, for a circle, itself) within the stadium's radius of its spine. */
    private fun fits(h: Highlight, stadium: DpRect): Boolean {
        val r = min((stadium.right - stadium.left).value, (stadium.bottom - stadium.top).value) / 2f
        val b = h.bounds
        val tolerance = 0.5f
        return if (h.shape === CircleShape) {
            val radius = (b.right - b.left).value / 2f
            distanceToSpine(stadium, (b.left.value + b.right.value) / 2f, (b.top.value + b.bottom.value) / 2f) + radius <= r + tolerance
        } else {
            val corner = 12f // MapBarHighlight.ROUNDED_SQUARE's Spacing.md, written out so a drift here fails the shape check below
            listOf(
                b.left.value + corner to b.top.value + corner,
                b.right.value - corner to b.top.value + corner,
                b.left.value + corner to b.bottom.value - corner,
                b.right.value - corner to b.bottom.value - corner,
            ).all { (x, y) -> distanceToSpine(stadium, x, y) + corner <= r + tolerance }
        }
    }

    @Test
    fun `every row's highlight lies inside the bar or the pill, corners included`() {
        setScreen()
        val bar = tag(MAP_ICON_BAR_TAG)
        val pill = tag("control-pill")
        val all = highlights()
        assertEquals("seven rows: five in the bar, two in the pill (found ${all.map { it.bounds.describe() }})", 7, all.size)
        all.forEach { h ->
            val cx = (h.bounds.left + h.bounds.right) / 2
            val cy = (h.bounds.top + h.bounds.bottom) / 2
            val home = listOf(bar, pill).single { cx >= it.left && cx <= it.right && cy >= it.top && cy <= it.bottom }
            assertTrue("the highlight ${h.bounds.describe()} (${h.shape}) lies inside its stadium ${home.describe()}", fits(h, home))
        }
    }

    @Test
    fun `plain rows have a 40 dp rounded square, the Add and Record badges a 36 dp circle`() {
        setScreen()
        fun assertCentredIn(name: String, h: Highlight, row: DpRect, size: Float) {
            assertEquals("$name highlight width", size, (h.bounds.right - h.bounds.left).value, 0.5f)
            assertEquals("$name highlight height", size, (h.bounds.bottom - h.bounds.top).value, 0.5f)
            assertEquals("$name highlight centred across", ((row.left + row.right) / 2).value, ((h.bounds.left + h.bounds.right) / 2).value, 0.5f)
            assertEquals("$name highlight centred down", ((row.top + row.bottom) / 2).value, ((h.bounds.top + h.bounds.bottom) / 2).value, 0.5f)
        }
        listOf("Fullscreen", "Reset orientation to north", "Center on my location").forEach { name ->
            val h = highlightOf(name)
            assertTrue("$name is a rounded square", h.shape === MapBarHighlight.ROUNDED_SQUARE.shape)
            assertCentredIn(name, h, described(name), 40f)
        }
        val add = highlightOf("Plan a trip or log a find here")
        assertTrue("Add is round", add.shape === CircleShape)
        assertCentredIn("Add", add, described("Plan a trip or log a find here"), 36f)
        val record = highlightInTag("control-pill-record")
        assertTrue("Record is round", record.shape === CircleShape)
        assertCentredIn("Record", record, tag("control-pill-record"), 36f)
        val ret = highlightInTag("control-pill-return-to-vehicle")
        assertTrue("Return is a rounded square", ret.shape === MapBarHighlight.ROUNDED_SQUARE.shape)
    }

    // ── Touch areas unchanged ──

    /**
     * Points in the strip a row keeps for touches but its highlight no longer covers: 2 dp in from each edge's middle, and (in the
     * landscape L, whose rows take touches at their full squares by the owner's ruling (d)) 2 dp in from each corner. Points inside
     * the minimise handle's box are left out: the handle owns them by design, before and after.
     */
    private fun edgePoints(row: DpRect): List<Pair<Dp, Dp>> {
        val handle = tag("map-icon-bar-minimize-handle")
        val w = row.right - row.left
        val h = row.bottom - row.top
        val f = listOf(0.5f to 0.04f, 0.04f to 0.5f, 0.96f to 0.5f, 0.5f to 0.96f) +
            if (landscape) listOf(0.04f to 0.04f, 0.96f to 0.04f, 0.04f to 0.96f, 0.96f to 0.96f) else emptyList()
        return f.map { (fx, fy) -> row.left + w * fx to row.top + h * fy }
            .filterNot { (x, y) -> x >= handle.left && x <= handle.right && y >= handle.top && y <= handle.bottom }
    }

    @Test
    fun `touches between the fullscreen row's edge and its highlight still toggle fullscreen`() {
        setScreen()
        val before = map.taps
        var expectFullscreen = false
        val points = edgePoints(described("Fullscreen"))
        assertTrue("sampled ${points.size} points", points.size >= 3)
        points.indices.forEach { i ->
            // Read again each time: fullscreen moves the cluster with the search bar.
            val row = composeRule.onNode(hasContentDescription("Fullscreen") or hasContentDescription("Exit fullscreen")).fetchSemanticsNode().dpBounds()
            val (x, y) = edgePoints(row)[i]
            touchAt(x, y)
            composeRule.mainClock.advanceTimeBy(2_000)
            composeRule.waitForIdle()
            expectFullscreen = !expectFullscreen
            val shown = composeRule.onAllNodes(hasContentDescription(if (expectFullscreen) "Exit fullscreen" else "Fullscreen")).fetchSemanticsNodes()
            assertEquals("the touch at ${x.value}, ${y.value} toggled fullscreen", 1, shown.size)
        }
        assertEquals("no touch fell through to the map", before, map.taps)
    }

    @Test
    fun `touches between the record and return buttons' edges and their highlights still reach them`() {
        setScreen()
        val before = map.taps
        val recordPoints = edgePoints(tag("control-pill-record"))
        recordPoints.forEach { (x, y) -> touchAt(x, y) }
        assertEquals("each touch on the record button's edge strip toggled recording", recordPoints.size, recordToggles)
        val returnPoints = edgePoints(tag("control-pill-return-to-vehicle"))
        returnPoints.forEach { (x, y) -> touchAt(x, y) }
        assertEquals("each touch on the return button's edge strip toggled the return", returnPoints.size, returnToggles)
        assertEquals("no touch fell through to the map", before, map.taps)
    }

    @Test
    fun `touches between the add row's edge and its round highlight still open the add menu`() {
        setScreen()
        val before = map.taps
        edgePoints(described("Plan a trip or log a find here")).forEach { (x, y) ->
            touchAt(x, y)
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
            assertEquals("the touch at ${x.value}, ${y.value} opened the add menu", 1, composeRule.onAllNodes(hasTestTag(ADD_ACTION_TILE_TAG)).fetchSemanticsNodes().size)
            val m = tag(LAYOUT_FIXES_MAP_TAG)
            touchAt((m.left + m.right) / 2, (m.top + m.bottom) / 2) // its scrim closes it
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
        }
        assertEquals("no touch fell through to the map", before, map.taps)
    }

    @Test
    fun `the minimise handle takes touches across its box, and the restore handle across its own`() {
        setScreen()
        val handle = tag("map-icon-bar-minimize-handle")
        assertEquals("the handle is still the recorded 20 dp wide", 20f, (handle.right - handle.left).value, 0.5f)
        val fractions = listOf(0.5f to 0.5f, 0.15f to 0.1f, 0.85f to 0.1f, 0.15f to 0.9f, 0.85f to 0.9f)
        fractions.forEach { (fx, fy) ->
            val h = tag("map-icon-bar-minimize-handle")
            touchAt(h.left + (h.right - h.left) * fx, h.top + (h.bottom - h.top) * fy)
            composeRule.mainClock.advanceTimeBy(2_000)
            composeRule.waitForIdle()
            assertEquals("the touch at ($fx, $fy) of the minimise handle hid the cluster", 0, composeRule.onAllNodes(hasTestTag(MAP_ICON_CLUSTER_TAG)).fetchSemanticsNodes().size)
            val r = described("Show map controls")
            touchAt(r.left + (r.right - r.left) * fx, r.top + (r.bottom - r.top) * fy)
            composeRule.mainClock.advanceTimeBy(2_000)
            composeRule.waitForIdle()
            assertEquals("the same touch on the restore handle brought it back", 1, composeRule.onAllNodes(hasTestTag(MAP_ICON_CLUSTER_TAG)).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `a long-press just beside the cluster reaches the map, and one on a row does not`() {
        setScreen()
        val cluster = tag(MAP_ICON_CLUSTER_TAG)
        val clusterOnLeft = cluster.left < tag(LAYOUT_FIXES_MAP_TAG).let { (it.left + it.right) / 2 }
        val besideX = if (clusterOnLeft) cluster.right + 6.dp else cluster.left - 6.dp
        val rows = listOf("Fullscreen", "Reset orientation to north", "Center on my location", "Plan a trip or log a find here").map { described(it) }
        rows.forEach { row ->
            val before = map.longPresses
            longPressAt(besideX, (row.top + row.bottom) / 2)
            assertEquals("a long-press 6 dp beside the cluster, level with ${row.describe()}, reached the map", before + 1, map.longPresses)
        }
        // The positive control: on a row itself the map must not get it, or the four above prove nothing.
        val before = map.longPresses
        val compass = described("Reset orientation to north")
        longPressAt((compass.left + compass.right) / 2, (compass.top + compass.bottom) / 2)
        assertEquals("a long-press on the compass row did not reach the map", before, map.longPresses)
    }

    // ── The crossfade (item 5) ──

    /**
     * Mid-swap both pictures are composed (that is the crossfade); the row must still say one thing, the new state.
     *
     * **A pin, not a guard (revert check R4, 2026-10-07):** putting the description back on the icons does not fail this. The
     * outgoing icon is recomposed with the row's current description, so both icons say the new thing; the worry that moved the
     * description onto the row (two descriptions mid-swap) was wrong for this composable. Kept because it pins what TalkBack
     * hears mid-swap, but it is not evidence that the move was needed.
     */
    @Test
    fun `while the fullscreen icon crossfades, the row carries one description, the new one`() {
        setScreen()
        val row = described("Fullscreen")
        composeRule.mainClock.autoAdvance = false
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput {
            click(with(composeRule.density) { Offset(((row.left + row.right) / 2).toPx(), ((row.top + row.bottom) / 2).toPx()) })
        }
        // With the clock stopped, the click's state write reaches the next frame only once applied.
        composeRule.runOnUiThread { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals("one node says Exit fullscreen mid-swap", 1, composeRule.onAllNodes(hasContentDescription("Exit fullscreen")).fetchSemanticsNodes().size)
        assertEquals("none still says Fullscreen mid-swap", 0, composeRule.onAllNodes(hasContentDescription("Fullscreen")).fetchSemanticsNodes().size)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    // ── The bounce ──

    private fun scaleOf(description: String): Float =
        composeRule.onNodeWithContentDescription(description).fetchSemanticsNode().config[PressBounceScaleKey]

    @Test
    fun `while a finger is down on a row its icon dips, and springs back when it lifts`() {
        setScreen()
        val row = described("Reset orientation to north")
        val at = with(composeRule.density) { Offset(((row.left + row.right) / 2).toPx(), ((row.top + row.bottom) / 2).toPx()) }
        assertEquals("at rest", 1f, scaleOf("Reset orientation to north"), 0.001f)
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { down(at) }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        val pressed = scaleOf("Reset orientation to north")
        assertTrue("dipped while pressed: $pressed", pressed < 0.97f && pressed >= MotionTokens.PRESS_BOUNCE_SCALE - 0.05f)
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertEquals("back at rest after the finger lifts", 1f, scaleOf("Reset orientation to north"), 0.001f)
    }

    @Test
    fun `with the phone's animations off the icon does not dip, and the press still lands`() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        setScreen()
        val row = described("Fullscreen")
        val at = with(composeRule.density) { Offset(((row.left + row.right) / 2).toPx(), ((row.top + row.bottom) / 2).toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { down(at) }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        assertEquals("no dip under reduced motion", 1f, scaleOf("Fullscreen"), 0.001f)
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("the press still landed: fullscreen is on", 1, composeRule.onAllNodes(hasContentDescription("Exit fullscreen")).fetchSemanticsNodes().size)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapBarPressFeedbackPortraitTest : MapBarPressFeedbackTests(Surface.ROTATION_0, landscape = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class MapBarPressFeedbackLandscape90Test : MapBarPressFeedbackTests(Surface.ROTATION_90, landscape = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class MapBarPressFeedbackLandscape270Test : MapBarPressFeedbackTests(Surface.ROTATION_270, landscape = true)
