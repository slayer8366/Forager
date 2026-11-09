package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.fanout.FanOutTestScene
import com.zynergylabs.forager.app.ui.map.fanout.MapTapHandler
import com.zynergylabs.forager.app.ui.map.fanout.MapTapSinks
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutBackHandler
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutHost
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutState
import com.zynergylabs.forager.app.ui.map.fanout.memberPositionDp
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
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
 * Back closes the Tools drawer before it folds an open fan (dispatch 2026-09-28-293, the owner: "When a
 * fan is spread out and I call the tool panel, hitting the back button closes the fan instead of the tool
 * panel, when it's expected to close the tool panel"). The real [AvailabilityScreen], the real Tools
 * control, a real touch to open the fan, and Back through the real activity dispatcher.
 *
 * The map is the stub [AvailabilityScreenFanBubbleDismissalTest] uses, and it carries the same copy of the
 * line in `SightingsMap` that hands the fan's Back handler its gate, here reading `renderMode.backEnabled`.
 * What the screen decides, `backEnabled = !isDrawerOpen()` at the compact scaffold's `mapSlot` call, is the
 * real code; what this does not reach is `SightingsMap`'s own pass-through of the flag to the handler.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenFanBackDrawerTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val fan = MarkerFanOutState()
    private var scene: FanOutTestScene? = null
    private val log = MushroomLogUiState(entries = listOf(BUBBLE_FIND), galleryPhotos = listOf(BUBBLE_PHOTO))

    private val slot: MapSlot = { _, content, renderMode, _, _, onTap, _, _, modifier ->
        val density = LocalDensity.current.density
        val currentOnTap by rememberUpdatedState(onTap)
        val currentOnFeatureTap by rememberUpdatedState(renderMode.onFeatureTap)
        val currentOnCloseBubble by rememberUpdatedState(renderMode.onCloseBubble)
        val bubbleShown = content.focusedFeature != null || content.focusedObservationId != null
        val currentBubbleShown by rememberUpdatedState(bubbleShown)
        val probe = remember { FanOutTestScene(density).also { scene = it; it.hidden = { fan.members.map { m -> m.key }.toSet() } } }
        val handler = remember {
            MapTapHandler(
                fan = fan,
                probe = probe,
                drawOrder = { orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT) },
                sinks = object : MapTapSinks {
                    override fun onPlainTap() = currentOnTap()
                    override fun onSightingTap(observationId: Long?, xPx: Float, yPx: Float) = currentOnTap()
                    override fun onFeatureTap(layerId: String, featureId: String, xPx: Float, yPx: Float, at: LatLng) =
                        currentOnFeatureTap(MapFeatureTap(layerId, featureId, Offset(xPx, yPx), 0f, at))
                    override fun onUnidentifiedFeature(layerId: String) = currentOnTap()
                    override fun onCloseBubble() = currentOnCloseBubble()
                },
                bubbleOpen = { currentBubbleShown },
            )
        }
        MarkerFanOutHost(fan)
        MarkerFanOutBackHandler(fan, bubbleOpen = bubbleShown, backEnabled = renderMode.backEnabled)
        Box(
            modifier
                .testTag(MAP_TAG)
                .pointerInput(Unit) { detectTapGestures { handler.onMapTap(LatLng(0.0, 0.0), it.x, it.y) } },
        )
    }

    private val density get() = composeRule.density.density

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun touchMap(xDp: Float, yDp: Float) {
        composeRule.onNodeWithTag(MAP_TAG).performTouchInput { click(Offset(xDp * density, yDp * density)) }
        settle()
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun openTools() {
        composeRule.onNodeWithText("Tools").performTouchInput { click() }
        settle()
        composeRule.onNodeWithText("Trip Planner").assertIsDisplayed()
    }

    private fun fanOpen() {
        val store = OneCellStore()
        val viewModel = mapLayersViewModel(store = store)
        composeRule.setContent { MapLayersTestScreen(viewModel = viewModel, mapSlot = slot, store = store, logUiState = log) }
        composeRule.waitForIdle()
        val s = checkNotNull(scene)
        s.addAtScreen(MapLayerIds.FINDS, "find-1", SPOT_X * density, SPOT_Y * density)
        s.addAtScreen(MapLayerIds.PHOTOS, "ph-1", SPOT_X * density, SPOT_Y * density)
        touchMap(SPOT_X, SPOT_Y)
        assertTrue("the stack fanned", fan.isOpen)
    }

    @Test
    fun `with a fan open and the Tools drawer open, Back closes the drawer and the fan stays open`() {
        fanOpen()
        openTools()

        back()

        composeRule.onNodeWithText("Trip Planner").assertIsNotDisplayed()
        assertTrue("the first Back closed the drawer, not the fan", fan.isOpen)
    }

    @Test
    fun `a second Back, with the drawer gone, folds the fan`() {
        fanOpen()
        openTools()
        back()
        assertTrue(fan.isOpen)

        back()

        assertFalse("the second Back folded the fan", fan.isOpen)
    }

    @Test
    fun `with no drawer open Back folds the fan, as it did`() {
        fanOpen()

        back()

        assertFalse(fan.isOpen)
    }

    @Test
    fun `with a bubble open over the fan, Back closes the drawer, then the bubble, then the fan`() {
        fanOpen()
        val find = fan.members.single { it.key.featureId == "find-1" }
        val at = memberPositionDp(find, fan.progress)
        touchMap(at.xDp, at.yDp)
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        openTools()

        back()
        composeRule.onNodeWithText("Trip Planner").assertIsNotDisplayed()
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        assertTrue(fan.isOpen)

        back()
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertDoesNotExist()
        assertTrue("the bubble went before the fan", fan.isOpen)

        back()
        assertFalse(fan.isOpen)
    }

    private companion object {
        const val MAP_TAG = "fan-back-drawer-map"
        const val SPOT_X = 150f
        const val SPOT_Y = 420f
    }
}
