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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.map.CENTRE_PIN_CONFIRM_ROW_TAG
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.fanout.FanOutTestScene
import com.zynergylabs.forager.app.ui.map.fanout.MapTapHandler
import com.zynergylabs.forager.app.ui.map.fanout.MapTapSinks
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutBackHandler
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutHost
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutState
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
 * With a fan open and another of the Maps tab's own open things up, Back closes that thing and the fan stays
 * open; the next Back folds the fan (dispatch 2026-09-28-298, the owner: "apply the change to the other Back
 * cases", after -293 did it for the Tools drawer). The real [AvailabilityScreen], each thing opened through
 * its real control, the fan opened by a real touch, Back through the real activity dispatcher.
 *
 * The map is the stub [AvailabilityScreenFanBackDrawerTest] uses, carrying its own copy of the line in
 * `SightingsMap` that hands the fan's Back handler `renderMode.backEnabled`. What the screen decides, the
 * gate itself at the compact scaffold's and the compact map tab's `mapSlot` calls, is the real code; what this
 * does not reach is `SightingsMap`'s own pass-through of the flag (unchanged since -293).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenFanBackOthersTest {

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

    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun fullscreenOn() = runCatching { composeRule.onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed() }.isSuccess

    private fun openDropdown() {
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performTouchInput { click() }
        settle()
        assertTrue("the search dropdown is open", shown(SEARCH_DROPDOWN_TAG))
    }

    private fun openAddMenu() {
        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").performTouchInput { click() }
        settle()
        assertTrue("the add-action menu is open", shown(ADD_ACTION_TILE_TAG))
    }

    /** Back closes the thing, [isOpen] false, the fan stays; the next Back folds the fan. */
    private fun assertThingThenFan(thing: String, isOpen: () -> Boolean) {
        assertTrue("$thing is open over the fan", isOpen() && fan.isOpen)

        back()
        assertFalse("the first Back closed $thing", isOpen())
        assertTrue("the first Back left the fan open", fan.isOpen)

        back()
        assertFalse("the second Back folded the fan", fan.isOpen)
    }

    @Test
    fun `with a fan open and nothing else, Back folds the fan, as it did`() {
        fanOpen()

        back()

        assertFalse(fan.isOpen)
    }

    @Test
    fun `with a fan open in fullscreen, Back leaves fullscreen first and then folds the fan`() {
        fanOpen()
        composeRule.onNodeWithContentDescription("Fullscreen").performTouchInput { click() }
        settle()

        assertThingThenFan("fullscreen", ::fullscreenOn)
    }

    @Test
    fun `with a fan open and the add-action menu open, Back closes the menu and then folds the fan`() {
        fanOpen()
        openAddMenu()

        assertThingThenFan("the add-action menu") { shown(ADD_ACTION_TILE_TAG) }
    }

    @Test
    fun `with a fan open and a pin picker open from the menu, Back closes the picker and then folds the fan`() {
        fanOpen()
        openAddMenu()
        composeRule.onNodeWithText("Find").performTouchInput { click() }
        settle()

        assertThingThenFan("the Log a find picker") { shown(CENTRE_PIN_CONFIRM_ROW_TAG) }
    }

    @Test
    fun `with a fan open and the search dropdown's Set on map picker open, Back closes the picker and then folds the fan`() {
        fanOpen()
        openDropdown()
        composeRule.onNodeWithText("Set on map").performScrollTo().performTouchInput { click() }
        settle()

        assertThingThenFan("the Set on map picker") { shown(CENTRE_PIN_CONFIRM_ROW_TAG) }
    }

    private companion object {
        const val MAP_TAG = "fan-back-others-map"
        const val SPOT_X = 150f
        const val SPOT_Y = 420f
    }
}
