package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailRecord
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.map.CROWDED_LIST_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.crowdedListRowTag
import com.zynergylabs.forager.app.ui.map.fanout.CameraMoveClassifier
import com.zynergylabs.forager.app.ui.map.fanout.CrowdedStackZoom
import com.zynergylabs.forager.app.ui.map.fanout.FakeLooper
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.fanout.FanOutTestScene
import com.zynergylabs.forager.app.ui.map.fanout.FanRect
import com.zynergylabs.forager.app.ui.map.fanout.FanSpace
import com.zynergylabs.forager.app.ui.map.fanout.MapTapHandler
import com.zynergylabs.forager.app.ui.map.fanout.MapTapSinks
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutBackHandler
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutHost
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutState
import com.zynergylabs.forager.app.ui.map.fanout.SceneStackCamera
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
import org.robolectric.shadows.ShadowToast

/**
 * A crowded group zooms in, then fans, on the real [AvailabilityScreen] (dispatch 2026-10-27-07, the owner's step paths 2
 * to 4): the real Maps tab composes the list and its Back steps (`CompactMapCrowdedList`) from the host it hands the map,
 * a real touch on the map taps the stack, and Back goes through the real activity dispatcher, down to the Maps tab's own
 * Back ("Tap Back Button Again to Exit").
 *
 * The map is a stub, as in [AvailabilityScreenFanBackDrawerTest] (a `MapView` cannot be built under Robolectric): the real
 * [MapTapHandler] and [CrowdedStackZoom] over a [FanOutTestScene] whose camera ([SceneStackCamera]) moves its projection, with
 * the copy of `SightingsMap`'s lines that tell the host of the fan and compose the fan's Back. What it does not reach is
 * `SightingsMap` itself and the MapLibre camera (`MapStackCamera`), which the S22 steps cover.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenCrowdedStackTest {

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
    private var camera: SceneStackCamera? = null
    private var mapLongPresses = 0
    private val finds = (0 until 30).map { i ->
        BUBBLE_FIND.copy(id = "find-$i", ownIdentification = "Chanterelle %02d".format(i))
    }
    private val log = MushroomLogUiState(entries = finds)

    private val slot: MapSlot = { _, content, renderMode, _, _, onTap, _, _, modifier ->
        val density = LocalDensity.current.density
        val currentOnTap by rememberUpdatedState(onTap)
        val currentOnFeatureTap by rememberUpdatedState(renderMode.onFeatureTap)
        val currentOnCloseBubble by rememberUpdatedState(renderMode.onCloseBubble)
        val currentHost by rememberUpdatedState(renderMode.crowdedStacks)
        val bubbleShown = content.focusedFeature != null || content.focusedObservationId != null
        val currentBubbleShown by rememberUpdatedState(bubbleShown)
        val probe = remember {
            FanOutTestScene(density, SPOT_X * density to SPOT_Y * density).also { s ->
                scene = s
                s.hidden = { fan.members.map { m -> m.key }.toSet() }
            }
        }
        val handler = remember {
            val classifier = CameraMoveClassifier(FakeLooper()::post)
            val sceneCamera = SceneStackCamera(probe, VIEW_W_DP, VIEW_H_DP, classifier).also { camera = it }
            val space = object : FanSpace {
                override fun boundsPx() = FanRect(
                    (SPOT_X - VIEW_W_DP / 2) * density, (SPOT_Y - VIEW_H_DP / 2) * density,
                    (SPOT_X + VIEW_W_DP / 2) * density, (SPOT_Y + VIEW_H_DP / 2) * density,
                )
                override fun keepOutsPx(): List<FanRect> = emptyList()
            }
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
                space = space,
                bubbleOpen = { currentBubbleShown },
                crowded = CrowdedStackZoom(sceneCamera, { currentHost }, {}),
            )
        }
        // SightingsMap's own lines: the host is told of the fan, and the fan's Back is composed while it is open.
        SideEffect { renderMode.crowdedStacks?.fan = fan }
        MarkerFanOutHost(fan)
        MarkerFanOutBackHandler(fan, bubbleOpen = bubbleShown, backEnabled = renderMode.backEnabled)
        Box(
            modifier
                .testTag(MAP_TAG)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { handler.onMapTap(LatLng(0.0, 0.0), it.x, it.y) },
                        onLongPress = { mapLongPresses++ },
                    )
                },
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

    private fun show() {
        val store = OneCellStore()
        val viewModel = mapLayersViewModel(store = store)
        composeRule.setContent { MapLayersTestScreen(viewModel = viewModel, mapSlot = slot, store = store, logUiState = log) }
        composeRule.waitForIdle()
    }

    private fun tapTheSpot() {
        composeRule.onNodeWithTag(MAP_TAG).performTouchInput { click(Offset(SPOT_X * density, SPOT_Y * density)) }
        settle()
    }

    /**
     * `null` when the bubble a touch on a list row opens is displayed; otherwise what was missed: the row, the touch point (in
     * the row and in the root), the row's bounds, the bubble nodes and whether the list is still up. [dumpTree] sends the
     * screen's tree to the test's output, so the first failure also says what the touch reached.
     */
    private fun bubbleMissAfterRowTouch(row: Int, point: Offset, rowBounds: Rect, dumpTree: Boolean): String? {
        val bubbles = composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).fetchSemanticsNodes()
        val shown = bubbles.isNotEmpty() && runCatching { composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed() }.isSuccess
        if (shown) return null
        if (dumpTree) println("Screen after the touch on row $row at $point:\n" + composeRule.onRoot(useUnmergedTree = true).printToString())
        val listUp = composeRule.onAllNodesWithTag(CROWDED_LIST_TAG).fetchSemanticsNodes().isNotEmpty()
        return "row $row at $point in the row (${rowBounds.topLeft + point} in the root); row bounds $rowBounds; " +
            "bubble nodes ${bubbles.size}" + (bubbles.firstOrNull()?.let { ", the first at ${it.boundsInRoot}" } ?: "") +
            "; the list ${if (listUp) "still up" else "closed"}"
    }

    private fun assertMapsTabOwnBack() {
        ShadowToast.reset()
        back()
        assertEquals("the Maps tab's own Back", "Tap Back Button Again to Exit", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a crowded stack zooms in and fans by itself, and Back folds the fan, then returns the camera, then is the Maps tab's own`() {
        show()
        val s = checkNotNull(scene)
        val spot = s.centre
        repeat(20) { s.add(MapLayerIds.PHOTOS, "p$it", spot.lat, spot.lng) }
        repeat(10) { s.add(MapLayerIds.FINDS, "f$it", spot.lat, spot.lng + 0.012) }

        tapTheSpot()

        assertTrue("the fan opened after the zoom, with no second tap", fan.isOpen)
        assertEquals(20, fan.members.size)
        val zoomed = s.zoom
        assertTrue("zoomed in: $zoomed", zoomed > START_ZOOM)

        back()
        assertFalse("Back folded the fan", fan.isOpen)
        assertEquals("the camera stays zoomed until the next Back", zoomed, s.zoom, 1e-9)

        back()
        assertEquals("Back returned the camera to before the tap", START_ZOOM, s.zoom, 1e-9)
        assertEquals(spot, s.centre)
        assertEquals(1, checkNotNull(camera).restores.size)

        assertMapsTabOwnBack()
        assertEquals("no second camera step", 1, checkNotNull(camera).restores.size)
    }

    @Test
    fun `one spot at the closest zoom lists the stack, every part of a row opens its bubble, and Back retraces the list, then the camera`() {
        show()
        val s = checkNotNull(scene)
        val spot = s.centre
        finds.forEach { s.add(MapLayerIds.FINDS, it.id, spot.lat, spot.lng) }

        tapTheSpot()

        assertFalse("no fan past the edge", fan.isOpen)
        composeRule.onNodeWithTag(CROWDED_LIST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("30 records at this spot").assertIsDisplayed()

        // The first three rows by name: a real touch at five places across each opens that record's bubble, and Back
        // brings the list back. Every touch is tried and each one that opens no bubble is named, so a failure says which
        // parts of which rows a finger cannot reach.
        val missed = mutableListOf<String>()
        for (i in 0 until 3) {
            val key = FanKey(MapLayerIds.FINDS, "find-$i")
            val name = "Chanterelle %02d".format(i)
            val row = composeRule.onNodeWithTag(crowdedListRowTag(key))
            row.assertIsDisplayed()
            val size = row.fetchSemanticsNode().size
            val points = listOf(
                Offset(size.width * 0.1f, size.height * 0.5f),
                Offset(size.width * 0.5f, size.height * 0.5f),
                Offset(size.width * 0.9f, size.height * 0.5f),
                Offset(size.width * 0.5f, size.height * 0.15f),
                Offset(size.width * 0.5f, size.height * 0.85f),
            )
            for (point in points) {
                val rowBounds = composeRule.onNodeWithTag(crowdedListRowTag(key)).fetchSemanticsNode().boundsInRoot
                composeRule.onNodeWithTag(crowdedListRowTag(key)).performTouchInput { click(point) }
                settle()
                val miss = bubbleMissAfterRowTouch(row = i, point = point, rowBounds = rowBounds, dumpTree = missed.isEmpty())
                if (miss != null) {
                    missed += miss
                    continue
                }
                composeRule.onNodeWithText(name).assertIsDisplayed()
                composeRule.onNodeWithTag(CROWDED_LIST_TAG).assertDoesNotExist()

                back()
                composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertDoesNotExist()
                composeRule.onNodeWithTag(CROWDED_LIST_TAG).assertIsDisplayed()
            }
        }
        assertTrue("${missed.size} of 15 real touches on the rows opened no bubble:\n" + missed.joinToString("\n"), missed.isEmpty())

        back()
        composeRule.onNodeWithTag(CROWDED_LIST_TAG).assertDoesNotExist()
        assertEquals("the list closed before the camera moved", CROWDED_ZOOM, s.zoom, 1e-9)

        back()
        assertEquals("then Back returned the camera", START_ZOOM, s.zoom, 1e-9)

        assertMapsTabOwnBack()
    }

    @Test
    fun `a long-press on the map beside the list reaches the map`() {
        show()
        val s = checkNotNull(scene)
        finds.forEach { s.add(MapLayerIds.FINDS, it.id, s.centre.lat, s.centre.lng) }
        tapTheSpot()
        composeRule.onNodeWithTag(CROWDED_LIST_TAG).assertIsDisplayed()

        val list: Rect = composeRule.onNodeWithTag(CROWDED_LIST_TAG).fetchSemanticsNode().boundsInRoot
        val map: Rect = composeRule.onNodeWithTag(MAP_TAG).fetchSemanticsNode().boundsInRoot
        // Just above the list, further above it, and level with it past its side when there is room.
        val outside = buildList {
            add(Offset(list.center.x, list.top - 24f * density))
            add(Offset(list.center.x, list.top - 120f * density))
            if (list.left - map.left > 32f * density) add(Offset(map.left + 12f * density, list.center.y))
        }
        assertTrue("at least two places to press: $outside", outside.size >= 2)
        for (rootPoint in outside) {
            assertFalse("the point $rootPoint is outside the list $list", list.contains(rootPoint))
            val before = mapLongPresses
            composeRule.onNodeWithTag(MAP_TAG).performTouchInput { longClick(rootPoint - map.topLeft) }
            settle()
            assertEquals("the long-press at $rootPoint reached the map", before + 1, mapLongPresses)
        }
    }

    private companion object {
        const val MAP_TAG = "crowded-stack-map"

        /** Where the stack is drawn, dp of the map: a place on the bare map the other fan tests also touch. */
        const val SPOT_X = 150f
        const val SPOT_Y = 420f

        /** The fan's room and the camera's view: 300 x 600 dp about the spot (a fan of 20 fits; 30 does not). */
        const val VIEW_W_DP = 300f
        const val VIEW_H_DP = 600f

        /** The scene's zoom at the start ([FanOutTestScene.zoom]). */
        const val START_ZOOM = 10.0

        /** Where the list's stack is zoomed to: the closest zoom. */
        const val CROWDED_ZOOM = 17.0
    }
}
