package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
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
 * Dismissal one layer at a time, and the stacking distance, through the real [AvailabilityScreen]
 * (dispatch 2026-09-29-57, items 6 and 7; amendment -255). The map is a stub that takes real pointer
 * input and hands each tap to the real [MapTapHandler] over a fake probe ([FanOutTestScene]), with the same
 * wiring `SightingsMap` has: a feature tap goes to `renderMode.onFeatureTap`, a plain tap to the slot's
 * `onTap`, and the fan and its Back handler are told whether the screen is showing a bubble (from
 * `content.focusedFeature`, which `SightingsMap` reads as `currentFocusedFeature`). Every touch is a real
 * coordinate touch on the map's node, and Back is the real activity dispatcher.
 *
 * What it does not reach: the real `MapView`, so the two lines in `SightingsMap` that read the focused
 * feature into the handler and the Back handler are exercised only through this stub's copy of them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenFanBubbleDismissalTest {

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
    private var plainTaps by mutableStateOf(0)

    private val find2 = BUBBLE_FIND.copy(id = "find-2", ownIdentification = "Second find")
    private val log = MushroomLogUiState(entries = listOf(BUBBLE_FIND, find2), galleryPhotos = listOf(BUBBLE_PHOTO))

    private val slot: MapSlot = { _, content, renderMode, _, _, onTap, _, _, modifier ->
        val density = LocalDensity.current.density
        val currentOnTap by rememberUpdatedState(onTap)
        val currentOnFeatureTap by rememberUpdatedState(renderMode.onFeatureTap)
        val bubbleShown = content.focusedFeature != null || content.focusedObservationId != null
        val currentBubbleShown by rememberUpdatedState(bubbleShown)
        val probe = remember { FanOutTestScene(density).also { scene = it; it.hidden = { fan.members.map { m -> m.key }.toSet() } } }
        val handler = remember {
            MapTapHandler(
                fan = fan,
                probe = probe,
                drawOrder = { orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT) },
                sinks = object : MapTapSinks {
                    override fun onPlainTap() {
                        plainTaps++
                        currentOnTap()
                    }
                    override fun onSightingTap(observationId: Long?, xPx: Float, yPx: Float) = currentOnTap()
                    override fun onFeatureTap(layerId: String, featureId: String, xPx: Float, yPx: Float, at: LatLng) =
                        currentOnFeatureTap(MapFeatureTap(layerId, featureId, Offset(xPx, yPx), 0f, at))
                    override fun onUnidentifiedFeature(layerId: String) = currentOnTap()
                },
                bubbleOpen = { currentBubbleShown },
            )
        }
        MarkerFanOutHost(fan)
        MarkerFanOutBackHandler(fan, bubbleOpen = bubbleShown)
        Box(
            modifier
                .testTag(MAP_TAG)
                .pointerInput(Unit) { detectTapGestures { handler.onMapTap(LatLng(0.0, 0.0), it.x, it.y) } },
        )
    }

    private fun setScreen() {
        val store = OneCellStore()
        val viewModel = mapLayersViewModel(store = store)
        composeRule.setContent { MapLayersTestScreen(viewModel = viewModel, mapSlot = slot, store = store, logUiState = log) }
        composeRule.waitForIdle()
    }

    private val density get() = composeRule.density.density

    private fun touchMap(xDp: Float, yDp: Float) {
        composeRule.onNodeWithTag(MAP_TAG).performTouchInput { click(Offset(xDp * density, yDp * density)) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    /** A find and a photo on one spot: the stack. */
    private fun stackAtSpot() {
        val s = checkNotNull(scene)
        s.addAtScreen(MapLayerIds.FINDS, "find-1", SPOT_X * density, SPOT_Y * density)
        s.addAtScreen(MapLayerIds.PHOTOS, "ph-1", SPOT_X * density, SPOT_Y * density)
    }

    /**
     * A bubble up and a fan open together. A tap on a fanned icon no longer leaves both (it folds the fan, dispatch 2026-09-28-381), so the state is reached
     * the way that still can: the find's bubble first, while it is alone on the spot, then a photo joins it and a tap on the stack fans it with the bubble up.
     */
    private fun fanWithFindBubbleOpen() {
        setScreen()
        val s = checkNotNull(scene)
        s.addAtScreen(MapLayerIds.FINDS, "find-1", SPOT_X * density, SPOT_Y * density)
        touchMap(SPOT_X, SPOT_Y)
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Golden chanterelle", useUnmergedTree = true).assertIsDisplayed()
        s.addAtScreen(MapLayerIds.PHOTOS, "ph-1", SPOT_X * density, SPOT_Y * density)
        touchMap(SPOT_X, SPOT_Y)
        assertTrue("the stack fanned with the bubble still up", fan.isOpen)
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
    }

    @Test
    fun `a real touch on a fanned find shows its bubble and folds the fan on that one tap`() {
        setScreen()
        stackAtSpot()
        touchMap(SPOT_X, SPOT_Y)
        assertTrue("the stack fanned", fan.isOpen)
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertDoesNotExist()
        val find = fan.members.single { it.key.featureId == "find-1" }
        val at = memberPositionDp(find, fan.progress)

        touchMap(at.xDp, at.yDp)

        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Golden chanterelle", useUnmergedTree = true).assertIsDisplayed()
        assertFalse("the fan folded on the same tap", fan.isOpen)
    }

    @Test
    fun `a real touch on a fanned photo shows its bubble and folds the fan on that one tap`() {
        setScreen()
        stackAtSpot()
        touchMap(SPOT_X, SPOT_Y)
        val photo = fan.members.single { it.key.featureId == "ph-1" }
        val at = memberPositionDp(photo, fan.progress)

        touchMap(at.xDp, at.yDp)

        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        assertFalse("the fan folded on the same tap", fan.isOpen)
    }

    @Test
    fun `an empty-map tap closes only the bubble and a second one folds the fan`() {
        fanWithFindBubbleOpen()
        val before = plainTaps

        touchMap(EMPTY_X, EMPTY_Y)
        assertEquals("the empty tap reached the map as a plain tap", before + 1, plainTaps)
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertDoesNotExist()
        assertTrue("the first empty tap left the fan open", fan.isOpen)

        touchMap(EMPTY_X, EMPTY_Y)
        assertFalse("the second empty tap folded the fan", fan.isOpen)
        assertTrue("and let go of its members", fan.members.isEmpty())
    }

    @Test
    fun `Back closes only the bubble and a second Back folds the fan`() {
        fanWithFindBubbleOpen()

        back()
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertDoesNotExist()
        assertTrue("the first Back left the fan open", fan.isOpen)

        back()
        assertFalse("the second Back folded the fan", fan.isOpen)
    }

    @Test
    fun `with no bubble open an empty-map tap folds the fan, as it did`() {
        setScreen()
        stackAtSpot()
        touchMap(SPOT_X, SPOT_Y)
        assertTrue(fan.isOpen)

        touchMap(EMPTY_X, EMPTY_Y)
        assertFalse(fan.isOpen)
    }

    @Test
    fun `with no bubble open Back folds the fan, as it did`() {
        setScreen()
        stackAtSpot()
        touchMap(SPOT_X, SPOT_Y)
        assertTrue(fan.isOpen)

        back()
        assertFalse(fan.isOpen)
    }

    @Test
    fun `a tap on another marker with a fan and a bubble open still folds the fan and opens that marker's bubble`() {
        fanWithFindBubbleOpen()
        checkNotNull(scene).addAtScreen(MapLayerIds.FINDS, "find-2", OTHER_X * density, OTHER_Y * density)

        touchMap(OTHER_X, OTHER_Y)
        assertFalse("a tap on a marker is not an empty tap, so the fan folds", fan.isOpen)
        composeRule.onNodeWithText("Second find", useUnmergedTree = true).assertIsDisplayed()
    }

    // Item 6, through the real screen and a real touch.

    @Test
    fun `two finds 35 dp apart do not fan, and a real touch on each opens its own bubble`() {
        setScreen()
        val s = checkNotNull(scene)
        s.addAtScreen(MapLayerIds.FINDS, "find-1", SPOT_X * density, SPOT_Y * density)
        s.addAtScreen(MapLayerIds.FINDS, "find-2", (SPOT_X + 15f) * density, (SPOT_Y + 35f) * density)

        touchMap(SPOT_X, SPOT_Y)
        assertFalse("35 dp apart vertically is not a stack", fan.isOpen)
        composeRule.onNodeWithText("Golden chanterelle", useUnmergedTree = true).assertIsDisplayed()

        touchMap(SPOT_X + 15f, SPOT_Y + 35f)
        assertFalse(fan.isOpen)
        composeRule.onNodeWithText("Second find", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `two finds 30 dp apart, the owner's screenshot pair, do not fan on a real touch (30 dp is the planner's estimate)`() {
        setScreen()
        val s = checkNotNull(scene)
        s.addAtScreen(MapLayerIds.FINDS, "find-1", SPOT_X * density, SPOT_Y * density)
        s.addAtScreen(MapLayerIds.FINDS, "find-2", SPOT_X * density, (SPOT_Y + 30f) * density)

        touchMap(SPOT_X, SPOT_Y)
        assertFalse("30 dp apart is not a stack under 26 dp", fan.isOpen)
        composeRule.onNodeWithText("Golden chanterelle", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `two finds 26 dp apart do not fan on a real touch`() {
        setScreen()
        val s = checkNotNull(scene)
        s.addAtScreen(MapLayerIds.FINDS, "find-1", SPOT_X * density, SPOT_Y * density)
        s.addAtScreen(MapLayerIds.FINDS, "find-2", SPOT_X * density, (SPOT_Y + 26f) * density)

        touchMap(SPOT_X, SPOT_Y)
        assertFalse("26 dp apart is not under the stacking distance", fan.isOpen)
        composeRule.onNodeWithText("Golden chanterelle", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `two finds 25 dp apart fan on a real touch`() {
        setScreen()
        val s = checkNotNull(scene)
        s.addAtScreen(MapLayerIds.FINDS, "find-1", SPOT_X * density, SPOT_Y * density)
        s.addAtScreen(MapLayerIds.FINDS, "find-2", SPOT_X * density, (SPOT_Y + 25f) * density)

        touchMap(SPOT_X, SPOT_Y)
        assertTrue("25 dp apart is under the stacking distance", fan.isOpen)
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertDoesNotExist()
    }

    @Test
    fun `two finds 20 dp apart fan on a real touch`() {
        setScreen()
        val s = checkNotNull(scene)
        s.addAtScreen(MapLayerIds.FINDS, "find-1", SPOT_X * density, SPOT_Y * density)
        s.addAtScreen(MapLayerIds.FINDS, "find-2", SPOT_X * density, (SPOT_Y + 20f) * density)

        touchMap(SPOT_X, SPOT_Y)
        assertTrue(fan.isOpen)
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertDoesNotExist()
    }

    private companion object {
        const val MAP_TAG = "fan-bubble-map"
        const val SPOT_X = 150f
        const val SPOT_Y = 420f
        const val OTHER_X = 60f
        const val OTHER_Y = 640f
        const val EMPTY_X = 240f
        const val EMPTY_Y = 640f
    }
}
