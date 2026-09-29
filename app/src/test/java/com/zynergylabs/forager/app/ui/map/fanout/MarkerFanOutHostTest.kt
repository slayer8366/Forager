package com.zynergylabs.forager.app.ui.map.fanout

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The fan-out's clock and its Back (dispatch 2026-09-28-197; the owner's rules 5 and 6: "Give it a
 * .4s animation speed", the system's animations off means "spread out at once", Back closes the
 * fan-out before anything else). The host is the composable `SightingsMap` composes beside its
 * `MapView`, so these run without a map. The main clock is driven by hand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MarkerFanOutHostTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val state = MarkerFanOutState()

    private val members = fanOffsets(3).mapIndexed { i, off ->
        FanMember(FanKey(MapLayerIds.PHOTOS, "p$i"), 45.0, -122.0, 200f, 300f, off)
    }

    @After
    fun animationsBackOn() {
        setAnimatorScale(1f)
    }

    private fun setAnimatorScale(scale: Float) {
        val resolver = ApplicationProvider.getApplicationContext<Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit = {}) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MarkerFanOutHost(state)
            MarkerFanOutBackHandler(state)
            content()
        }
    }

    private fun advance(ms: Long) = composeRule.mainClock.advanceTimeBy(ms)

    /** A state write on the UI thread, applied, with no frame after it: the timing tests count frames themselves. */
    private fun write(block: () -> Unit) {
        composeRule.runOnUiThread { block(); Snapshot.sendApplyNotifications() }
    }

    /** A state write on the UI thread, applied, then frames for the recomposition it causes (the clock is paused). */
    private fun ui(block: () -> Unit) {
        composeRule.runOnUiThread { block(); Snapshot.sendApplyNotifications() }
        advance(48)
    }

    /** A Back press, then a frame, since the clock is paused and a handler leaves or joins on recomposition. */
    private fun pressBack() {
        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
            Snapshot.sendApplyNotifications()
        }
        advance(48)
    }

    private companion object {
        const val FRAME_MS = 16

        /** The owner's ".4s", written out on purpose: comparing to the production constant would move with it. */
        const val OWNER_DURATION_MS = 400
    }

    @Test
    fun `the duration constant is the owner's 400 ms`() {
        assertEquals(400, com.zynergylabs.forager.app.ui.map.fanout.FAN_DURATION_MS)
    }

    // Rule 6: 0.4 s.

    /**
     * Frames stepped, from the first frame on which [progress] has left its start until it reaches
     * [end]. The animation's own clock starts on the frame after the effect launches, so counting
     * from the first frame that moved keeps the figure independent of when the effect launched.
     */
    private fun framesToReach(end: Float, start: Float): Int {
        var frames = 0
        var moved = false
        repeat(400) {
            composeRule.mainClock.advanceTimeByFrame()
            val p = state.progress
            if (!moved && p != start) moved = true else if (moved) frames++
            if (moved && p == end) return frames
        }
        error("progress never reached $end; it is ${state.progress}")
    }

    @Test
    fun `the fan-out takes 0_4 s`() {
        show()
        write { state.open(members) }
        val frames = framesToReach(end = 1f, start = 0f)
        // The first moved frame is one frame in, so the animation ran (frames + 1) frames of FRAME_MS.
        val ran = (frames + 1) * FRAME_MS
        assertTrue("the fan-out ran $ran ms, not $OWNER_DURATION_MS", ran in OWNER_DURATION_MS..OWNER_DURATION_MS + FRAME_MS)
        assertTrue(state.isOpen)
    }

    @Test
    fun `the fan-out is part way at 200 ms and not done a frame before 400`() {
        show()
        write { state.open(members) }
        composeRule.mainClock.advanceTimeByFrame() // effect launches
        composeRule.mainClock.advanceTimeByFrame() // animation clock starts
        advance(200)
        assertTrue("part way at about 200 ms, was ${state.progress}", state.progress in 0.05f..0.95f)
        advance(OWNER_DURATION_MS - 200L - 2 * FRAME_MS)
        assertTrue("about 384 ms in, not done: ${state.progress}", state.progress < 1f)
    }

    @Test
    fun `the fold-back takes the same 0_4 s and then lets go of its markers`() {
        show()
        write { state.open(members) }
        framesToReach(end = 1f, start = 0f)

        write { state.fold() }
        assertFalse("folding is no longer open", state.isOpen)
        assertEquals("its markers are still drawn while it folds", 3, state.members.size)
        val frames = framesToReach(end = 0f, start = 1f)
        val ran = (frames + 1) * FRAME_MS
        assertTrue("the fold-back ran $ran ms, not $OWNER_DURATION_MS", ran in OWNER_DURATION_MS..OWNER_DURATION_MS + FRAME_MS)
        assertTrue("released once folded", state.members.isEmpty())
    }

    @Test
    fun `with the system's animations off the markers are spread at once, and fold at once`() {
        setAnimatorScale(0f)
        show()
        write { state.open(members) }
        advance(16)
        assertEquals("spread after one frame", 1f, state.progress, 0f)

        write { state.fold() }
        advance(16)
        assertEquals(0f, state.progress, 0f)
        assertTrue(state.members.isEmpty())
    }

    // Rule 5: Back.

    @Test
    fun `Back folds an open fan and does not reach a handler that was there first`() {
        var earlier = 0
        var earlierEnabled by mutableStateOf(true)
        show { BackHandler(enabled = earlierEnabled) { earlier++ } }
        write { state.open(members) }
        advance(16); advance(500)

        pressBack()
        assertFalse("the fan folded", state.isOpen)
        assertEquals("the earlier handler was not reached", 0, earlier)

        pressBack()
        assertEquals("the second Back reaches what was under it", 1, earlier)
    }

    @Test
    fun `Back folds the fan before a bubble's own handler, even one enabled after the fan opened`() {
        var bubbleBack = 0
        var bubbleShowing by mutableStateOf(false)
        show { BackHandler(enabled = bubbleShowing) { bubbleBack++ } }
        write { state.open(members) }
        advance(16); advance(500)
        // Tapping a fanned marker opens its bubble while the fan stays open.
        ui { bubbleShowing = true } // the control test below proves this enables the handler

        pressBack()
        assertFalse(state.isOpen)
        assertEquals("the bubble's Back has not run yet", 0, bubbleBack)

        pressBack()
        assertEquals(1, bubbleBack)
    }

    @Test
    fun `control - a handler enabled after composition is reached by Back when no fan is open`() {
        var bubbleBack = 0
        var bubbleShowing by mutableStateOf(false)
        show { BackHandler(enabled = bubbleShowing) { bubbleBack++ } }
        ui { bubbleShowing = true }
        pressBack()
        assertEquals("this harness enables a late handler, so the test above tests the order, not the enabling", 1, bubbleBack)
    }

    @Test
    fun `with no fan open, Back is not taken`() {
        var earlier = 0
        show { BackHandler(enabled = true) { earlier++ } }
        pressBack()
        assertEquals(1, earlier)
    }

    // The whole path: the handler and the host together.

    @Test
    fun `a stack tap opens over 0_4 s, and a tap on each fanned marker then opens its bubble`() {
        val scene = FanOutTestScene()
        val sinks = RecordingSinks()
        val handler = MapTapHandler(state, scene, { orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT) }, sinks)
        scene.hidden = { state.members.map { it.key }.toSet() }
        (1..4).forEach { scene.add(MapLayerIds.PHOTOS, "p$it") }
        show()

        write { handler.onMapTap(LatLng(45.0, -122.0), scene.xPx(-122.0), scene.yPx(45.0)) }
        advance(16); advance(500)
        assertEquals(1f, state.progress, 0f)
        assertEquals(emptyList<String>(), sinks.events)

        for (member in state.members.toList()) {
            val at = memberPositionDp(member, state.progress)
            write { handler.onMapTap(LatLng(45.0, -122.0), at.xDp * scene.density, at.yDp * scene.density) }
        }
        assertEquals((1..4).map { "feature:${MapLayerIds.PHOTOS}:p$it" }.toSet(), sinks.events.toSet())
        assertEquals(4, sinks.events.size)
    }
}
