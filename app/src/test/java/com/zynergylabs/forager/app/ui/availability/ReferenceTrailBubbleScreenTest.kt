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
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailRecord
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_DETAILS_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_DIRECTIONS_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_REFERENCE_NOTE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_REFERENCE_TITLE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.map.MapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.fanout.MapProbe
import com.zynergylabs.forager.app.ui.map.fanout.MapTapHandler
import com.zynergylabs.forager.app.ui.map.fanout.MapTapSinks
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutState
import com.zynergylabs.forager.app.ui.map.fanout.ProbedMarker
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.TapHit
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
import kotlin.math.abs
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
 * Step 8c (dispatch 2026-10-26-16, items 1, 3 and 4; RECORD -1680, -1681): a finger on an official-trails band opens
 * its bubble on the real [AvailabilityScreen], through the real tap handler over the real registry, and Back or a tap
 * elsewhere closes it. The touches are real coordinate touches on the map's node (CLAUDE.md, "A semantic performClick
 * asserts wiring, not routing"), at several points across the band's own width and along it.
 *
 * The map is a stand-in that draws one 10 dp band across the map (the band is 6 to 10 dp, forager-reference.mjs) and
 * answers the handler's queries as `queryRenderedFeatures` would, the band's feature carrying its agency's fields. Its
 * sinks are the host's own (`MapListeners.kt`), copied as the other screen tests here copy them. What it does not
 * reach: the real `MapView`, a real tile's feature, and a real finger's size on a real band: device items.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class ReferenceTrailBubbleScreenTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
            InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    /** One horizontal band across the map, [halfWidthPx] each side of [yPx], on [layerId], carrying [attributes]. */
    private class BandProbe(override val density: Float, val layerId: String, val yPx: Float, val halfWidthPx: Float, val attributes: Map<String, String>) : MapProbe {
        override fun hitsAt(xPx: Float, yPx: Float, layerIds: List<String>): List<TapHit> = hitsInBox(xPx, yPx, 0f, layerIds)

        override fun hitsInBox(xPx: Float, yPx: Float, halfPx: Float, layerIds: List<String>): List<TapHit> =
            if (layerId in layerIds && abs(this.yPx - yPx) <= halfWidthPx + halfPx) listOf(TapHit(layerId, null, attributes)) else emptyList()

        override fun markersInBox(xPx: Float, yPx: Float, halfPx: Float, layerIds: List<String>): List<ProbedMarker> = emptyList()

        override fun markersOf(keys: List<FanKey>): List<ProbedMarker> = emptyList()
    }

    private var bandLayer = MapLayerIds.REFERENCE_FS_BAND
    private var bandAttributes = mapOf("trail_no" to "017", "trail_name" to "Three Blaze")

    private val slot: MapSlot = { _, content, renderMode, _, _, onTap, _, _, modifier ->
        val density = LocalDensity.current.density
        val currentOnTap by rememberUpdatedState(onTap)
        val currentOnFeatureTap by rememberUpdatedState(renderMode.onFeatureTap)
        val currentOnCloseBubble by rememberUpdatedState(renderMode.onCloseBubble)
        val bubbleShown = content.focusedFeature != null || content.focusedObservationId != null
        val currentBubbleShown by rememberUpdatedState(bubbleShown)
        val handler = remember {
            MapTapHandler(
                fan = MarkerFanOutState(),
                probe = BandProbe(density, bandLayer, BAND_Y * density, BAND_HALF_DP * density, bandAttributes),
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
        Box(
            modifier
                .testTag(MAP_TAG)
                .pointerInput(Unit) { detectTapGestures { handler.onMapTap(LatLng(45.3, -115.0), it.x, it.y) } },
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

    private fun setScreen() {
        val store = OneCellStore()
        val viewModel = mapLayersViewModel(store = store)
        composeRule.setContent { MapLayersTestScreen(viewModel = viewModel, mapSlot = slot, store = store) }
        composeRule.waitForIdle()
    }

    private fun bubbleShown() = composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).fetchSemanticsNodes().isNotEmpty()

    private fun assertLine(tag: String, text: String) {
        composeRule.onNode(hasTestTag(tag) and hasAnyDescendant(hasText(text)), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a finger anywhere across the band's width, at several points along it, opens its bubble, and Back closes it`() {
        setScreen()
        val across = listOf(-BAND_HALF_DP + 1f, -BAND_HALF_DP / 2, 0f, BAND_HALF_DP / 2, BAND_HALF_DP - 1f)
        for (x in listOf(SPOT_X - 40f, SPOT_X, SPOT_X + 40f)) {
            for (dy in across) {
                assertFalse("no bubble before the touch at ($x, ${BAND_Y + dy})", bubbleShown())
                touchMap(x, BAND_Y + dy)
                assertTrue("the touch at ($x, ${BAND_Y + dy}) opened the band's bubble", bubbleShown())
                assertLine(MAP_BUBBLE_REFERENCE_TITLE_TAG, "Forest Service trail 017 · Three Blaze")
                assertLine(MAP_BUBBLE_REFERENCE_NOTE_TAG, "Official record. Routes follow the trail map, not this layer.")
                back()
                assertFalse("Back closed the bubble opened at ($x, ${BAND_Y + dy})", bubbleShown())
            }
        }
    }

    @Test
    fun `a tap elsewhere on the map closes the band's bubble`() {
        setScreen()
        touchMap(SPOT_X, BAND_Y)
        assertTrue("positive control: the band's bubble opened", bubbleShown())
        touchMap(SPOT_X, ELSEWHERE_Y)
        assertFalse("a tap clear of the band closed the bubble", bubbleShown())
    }

    @Test
    fun `the bubble is map chrome at 80 percent, with the two lines and no action`() {
        setScreen()
        touchMap(SPOT_X, BAND_Y)
        val container = composeRule.onNodeWithTag(MAP_BUBBLE_TAG).fetchSemanticsNode().config[MapChromeContainerColor]
        assertEquals("the bubble's fill is at the map chrome's opacity", MAP_CHROME_OVER_MAP_ALPHA, container.alpha, 0.002f)
        assertTrue("no Details action", composeRule.onAllNodesWithTag(MAP_BUBBLE_DETAILS_TAG, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue("no Directions action", composeRule.onAllNodesWithTag(MAP_BUBBLE_DIRECTIONS_TAG, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a BLM band reads its route name, and a band missing its parts reads only what it has`() {
        bandLayer = MapLayerIds.REFERENCE_BLM_BAND
        bandAttributes = mapOf("ROUTE_PRMRY_NM" to "Badlands Rock")
        setScreen()
        touchMap(SPOT_X, BAND_Y)
        assertLine(MAP_BUBBLE_REFERENCE_TITLE_TAG, "BLM trail · Badlands Rock")
        assertLine(MAP_BUBBLE_REFERENCE_NOTE_TAG, "Official record. Routes follow the trail map, not this layer.")
    }

    @Test
    fun `a Forest Service band with no name reads its number only`() {
        bandAttributes = mapOf("trail_no" to "017")
        setScreen()
        touchMap(SPOT_X, BAND_Y)
        assertLine(MAP_BUBBLE_REFERENCE_TITLE_TAG, "Forest Service trail 017")
    }

    private companion object {
        const val MAP_TAG = "reference-trail-bubble-map"
        const val SPOT_X = 150f
        const val BAND_Y = 420f
        const val BAND_HALF_DP = 5f
        const val ELSEWHERE_Y = 620f
    }
}
