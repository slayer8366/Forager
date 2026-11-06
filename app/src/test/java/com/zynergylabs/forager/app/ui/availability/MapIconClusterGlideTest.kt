package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
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
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Motion Part 1, item 6 (dispatch 2026-09-28-652; scout B14, B15; the owner, RECORD -651: "Glide to its side", and back when
 * released short; Amendment 1, RECORD -657: "At release", the arrangement switches the moment the finger lifts and touches stay
 * as today throughout). The cluster is long-pressed and dragged by its minimise handle with real touches, and the moment after
 * the finger lifts is read with the clock stopped, so the glide is caught in flight.
 *
 * What is checked: the cluster's touch box lands at once, where it always landed, so a tap where the finger let go reaches the
 * map mid-glide (the dispatch's "must not sweep across the map catching touches"); the handle is already on its new side
 * mid-glide; a tap on the landed box mid-glide reaches the row there, as it would have before the glide existed; after landing
 * the map around it takes touches as before. Under the phone's animations turned off it lands at once, as before.
 *
 * What cannot be checked here: that the drawing actually travels from the finger to the edge. That is draw-only by design, and
 * Robolectric's pixels are not trusted for it; it is a device check. Nor does anything here show that a glide is in flight at
 * the moment of the mid-glide taps (the clock is stopped two frames after the lift, which is inside the glide as written, but
 * nothing in the tree reports it): these tests pin that touches are as they always were, which holds with or without a glide.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapIconClusterGlideTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private var mapTaps = 0
    private var mapLongPresses = 0
    private val mapSlot: MapSlot = { _, _, _, _, _, onTap, _, _, modifier ->
        Box(modifier.testTag(LAYOUT_FIXES_MAP_TAG).pointerInput(Unit) { detectTapGestures(onLongPress = { mapLongPresses++ }, onTap = { mapTaps++; onTap() }) })
    }

    private val resolver get() = ApplicationProvider.getApplicationContext<Application>().contentResolver

    @After
    fun clockAndAnimationsBack() {
        composeRule.mainClock.autoAdvance = true
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun setScreen() {
        composeRule.setContent { ForagerTheme { LayoutFixesScreen(uiState = LAYOUT_FIXES_FIX_STATE, mapSlot = mapSlot, isRecording = true) } }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun tag(tag: String): DpRect = with(composeRule.density) {
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.let { DpRect(it.left.toDp(), it.top.toDp(), it.right.toDp(), it.bottom.toDp()) }
    }

    private fun described(description: String): DpRect = with(composeRule.density) {
        composeRule.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot.let { DpRect(it.left.toDp(), it.top.toDp(), it.right.toDp(), it.bottom.toDp()) }
    }

    private fun px(x: Dp, y: Dp) = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }


    /**
     * Long-presses the minimise handle and drags it by [dx], with the clock running, then stops the clock and lifts the finger,
     * and lets one frame through so the release is laid out. Returns where the finger lifted.
     */
    private fun dragAndLiftThenHold(dx: Dp): Pair<Dp, Dp> {
        val handle = tag("map-icon-bar-minimize-handle")
        val startX = (handle.left + handle.right) / 2
        val startY = (handle.top + handle.bottom) / 2
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput {
            down(px(startX, startY))
            advanceEventTime(600)
            moveTo(px(startX + dx, startY))
            advanceEventTime(50)
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { up() }
        // With the clock stopped, the release's state writes reach the next frame only once applied (as the fan's tests do it).
        composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        return startX + dx to startY
    }

    private fun land() {
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun tapStopped(x: Dp, y: Dp) {
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(px(x, y)) }
        composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
        composeRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `dragged across, the touch box lands at once and a tap where the finger lifted reaches the map mid-glide`() {
        setScreen()
        val map = tag(LAYOUT_FIXES_MAP_TAG)
        assertTrue("the cluster starts on the right", tag(MAP_ICON_CLUSTER_TAG).left > (map.left + map.right) / 2)

        val (liftX, liftY) = dragAndLiftThenHold((-220).dp)
        val cluster = tag(MAP_ICON_CLUSTER_TAG)
        assertTrue("the touch box is already on the left, where it lands: ${cluster.describe()}", cluster.right < (map.left + map.right) / 2)
        assertTrue("the finger lifted clear of the landed box", liftX > cluster.right + 24.dp)

        val before = mapTaps
        tapStopped(liftX, liftY)
        assertEquals("a tap where the finger lifted, mid-glide, reached the map", before + 1, mapTaps)
        land()
    }

    @Test
    fun `dragged across, the handle is on its new outer side the moment the finger lifts`() {
        setScreen()
        dragAndLiftThenHold((-220).dp)
        val cluster = tag(MAP_ICON_CLUSTER_TAG)
        val handle = tag("map-icon-bar-minimize-handle")
        assertTrue(
            "mid-glide the handle is already on the cluster's left, its new outer side: handle ${handle.describe()}, cluster ${cluster.describe()}",
            handle.right < (cluster.left + cluster.right) / 2,
        )
        land()
        val landed = tag(MAP_ICON_CLUSTER_TAG)
        val handleLanded = tag("map-icon-bar-minimize-handle")
        assertTrue("and stays there: handle ${handleLanded.describe()}, cluster ${landed.describe()}", handleLanded.right < (landed.left + landed.right) / 2)
    }

    @Test
    fun `a tap on the landed box mid-glide reaches the row there, as before the glide existed`() {
        setScreen()
        dragAndLiftThenHold((-220).dp)
        val row = described("Fullscreen")
        val before = mapTaps
        tapStopped((row.left + row.right) / 2, (row.top + row.bottom) / 2)
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals("mid-glide the tap toggled fullscreen", 1, composeRule.onAllNodes(hasContentDescription("Exit fullscreen")).fetchSemanticsNodes().size)
        assertEquals("and did not reach the map", before, mapTaps)
        land()
    }

    @Test
    fun `after a glide the map beside the cluster takes a long-press, and the cluster's rows do not pass it on`() {
        setScreen()
        dragAndLiftThenHold((-220).dp)
        land()
        val cluster = tag(MAP_ICON_CLUSTER_TAG)
        listOf("Fullscreen", "Center on my location", "Plan a trip or log a find here").map { described(it) }.forEach { row ->
            val before = mapLongPresses
            composeRule.onAllNodes(isRoot()).onFirst().performTouchInput {
                down(px(cluster.right + 6.dp, (row.top + row.bottom) / 2))
                advanceEventTime(700)
                up()
            }
            composeRule.waitForIdle()
            assertEquals("a long-press 6 dp beside the landed cluster, level with ${row.describe()}, reached the map", before + 1, mapLongPresses)
        }
        val before = mapLongPresses
        val compass = described("Reset orientation to north")
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput {
            down(px((compass.left + compass.right) / 2, (compass.top + compass.bottom) / 2))
            advanceEventTime(700)
            up()
        }
        composeRule.waitForIdle()
        assertEquals("a long-press on a row of the landed cluster did not reach the map", before, mapLongPresses)
    }

    @Test
    fun `released short, its touch box is back at rest at once`() {
        setScreen()
        val restBefore = tag(MAP_ICON_CLUSTER_TAG)
        dragAndLiftThenHold((-40).dp)
        val cluster = tag(MAP_ICON_CLUSTER_TAG)
        assertEquals("the touch box is back at its rest at once", restBefore.left.value, cluster.left.value, 0.5f)
        land()
        assertEquals("and stays there", restBefore.left.value, tag(MAP_ICON_CLUSTER_TAG).left.value, 0.5f)
    }

    @Test
    fun `with the phone's animations off, it lands at once on its new side, as before`() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        setScreen()
        dragAndLiftThenHold((-220).dp)
        val cluster = tag(MAP_ICON_CLUSTER_TAG)
        val handle = tag("map-icon-bar-minimize-handle")
        assertTrue("the handle is already on the new outer side: handle ${handle.describe()}, cluster ${cluster.describe()}", handle.right < (cluster.left + cluster.right) / 2)
        land()
    }
}
