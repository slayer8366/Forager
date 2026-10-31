package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailRecord
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
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
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back while plotting with a crowded stack fanned over the map (dispatch 2026-10-30-08, Amendment 3; the first stop, (h),
 * "Back"): the fan folds first, then plotting is cancelled. The map is the fan stub of [AvailabilityScreenFanBackDrawerTest]
 * (a `MapView` cannot be built under Robolectric): the real [MapTapHandler] over a [FanOutTestScene], with the copy of
 * `SightingsMap`'s line that composes the fan's Back while it is open. The stack is fanned by a real touch on the map at its
 * spot; Back goes through the real activity dispatcher. What this does not reach is `SightingsMap` itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RoutePlottingFanBackTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val fan = MarkerFanOutState()
    private var scene: FanOutTestScene? = null
    private val log = MushroomLogUiState(entries = listOf(BUBBLE_FIND), galleryPhotos = listOf(BUBBLE_PHOTO))

    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

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
                    override fun onReferenceTrailTap(layerId: String, record: ReferenceTrailRecord, xPx: Float, yPx: Float, at: LatLng) =
                        currentOnFeatureTap(MapFeatureTap.forReferenceTrail(layerId, record, Offset(xPx, yPx), 0f, at))
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

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun mode() = plotting.uiState.value.mode

    private fun setScreen() {
        val store = OneCellStore()
        val viewModel = mapLayersViewModel(store = store)
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = slot,
                store = store,
                logUiState = log,
                waypoints = listOf(CREEK, RIDGE),
                routePlotting = RoutePlottingControls(state, plotting),
            )
        }
        composeRule.waitForIdle()
        val s = checkNotNull(scene)
        s.addAtScreen(MapLayerIds.FINDS, "find-1", SPOT_X * density, SPOT_Y * density)
        s.addAtScreen(MapLayerIds.PHOTOS, "ph-1", SPOT_X * density, SPOT_Y * density)
    }

    @Test
    fun `a stack fanned while plotting folds on the first Back, and the second Back cancels plotting`() {
        setScreen()
        composeRule.runOnUiThread {
            plotting.onStartPlotting(CREEK.stop())
            plotting.onWaypointTapped(RIDGE.stop())
        }
        settle()
        composeRule.onNodeWithTag(ROUTE_CARD_TAG).assertIsDisplayed()

        // The stack's spot, a single point as in AvailabilityScreenFanBackDrawerTest: the stack is a point under the finger.
        composeRule.onNodeWithTag(MAP_TAG).performTouchInput { click(Offset(SPOT_X * density, SPOT_Y * density)) }
        settle()
        assertTrue("the stack fanned", fan.isOpen)
        assertTrue("fanning did not touch the route", mode() is RoutePlotMode.Plotting)

        back()
        assertFalse("first Back: the fan folded", fan.isOpen)
        assertEquals("first Back: plotting stays", listOf(CREEK.stop(), RIDGE.stop()), (mode() as? RoutePlotMode.Plotting)?.draft?.stops)

        back()
        assertEquals("second Back: plotting cancelled", RoutePlotMode.Off, mode())
    }

    private companion object {
        const val MAP_TAG = "route-fan-back-map"
        const val SPOT_X = 150f
        const val SPOT_Y = 420f
    }
}
