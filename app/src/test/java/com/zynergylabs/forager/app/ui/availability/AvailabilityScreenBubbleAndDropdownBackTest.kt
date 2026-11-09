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
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.fanout.memberPositionDp
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.zynergylabs.forager.app.domain.model.LatLng
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
 * Dispatch 2026-09-28-312, item 11: the Back order between a map bubble, a fan and fullscreen, and the search
 * dropdown's own Back, through the real [AvailabilityScreen], real touches and the real activity dispatcher. The
 * map is the same stub [AvailabilityScreenFanBackOthersTest] uses.
 *
 * A bubble closes before the fan and after fullscreen, which was opened after it. The dropdown closes on Back and
 * stays closed, in touch mode.
 *
 * A bubble against the dropdown (item 11's other half) and a fan against the dropdown (item 12): the dropdown,
 * drawn on top and opened after them, closes first, and the bubble or the fan stays; the next Back closes the bubble
 * or folds the fan. Every test here runs in touch mode ([declareHostActivity]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenBubbleAndDropdownBackTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
            // Every test here drives the screen with a finger, and a finger puts the window in touch mode. Robolectric
            // starts a window out of it (ShadowWindowManagerGlobal, unless real graphics are on), and there
            // View.clearFocus re-assigns focus, which a touched phone does not do. Set before the activity's window is
            // added, which is when the window reads it.
            InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
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

    private fun composeFan() {
        val store = OneCellStore()
        val viewModel = mapLayersViewModel(store = store)
        composeRule.setContent { MapLayersTestScreen(viewModel = viewModel, mapSlot = slot, store = store, logUiState = log) }
        composeRule.waitForIdle()
        assertTrue("positive control: the window is in touch mode, so a pass below is not the harness ignoring the flag", composeRule.activity.window.decorView.isInTouchMode)
    }

    private fun fanOpen() {
        composeFan()
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

    private fun openBubbleOverFan() {
        fanOpen()
        val find = fan.members.single { it.key.featureId == "find-1" }
        val at = memberPositionDp(find, fan.progress)
        touchMap(at.xDp, at.yDp)
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        assertTrue("the fan is still open under the bubble", fan.isOpen)
    }

    private fun bubbleShown() = shown(MAP_BUBBLE_TAG)

    @Test
    fun `a bubble over a fan, then fullscreen on, Back leaves fullscreen, then closes the bubble, then folds the fan`() {
        openBubbleOverFan()
        composeRule.onNodeWithContentDescription("Fullscreen").performTouchInput { click() }
        settle()
        assertTrue("fullscreen is on", fullscreenOn())

        back()
        assertFalse("the first Back left fullscreen", fullscreenOn())
        assertTrue("the first Back left the bubble", bubbleShown())
        assertTrue("the first Back left the fan", fan.isOpen)

        back()
        assertFalse("the second Back closed the bubble", bubbleShown())
        assertTrue("the second Back left the fan", fan.isOpen)

        back()
        assertFalse("the third Back folded the fan", fan.isOpen)
    }

    @Test
    fun `a bubble over a fan, then the dropdown open, Back closes the dropdown, then the bubble, then folds the fan`() {
        openBubbleOverFan()
        openDropdown()

        back()
        assertFalse("the first Back closed the dropdown [seen: bubble=${bubbleShown()} dropdown=${shown(SEARCH_DROPDOWN_TAG)} fan=${fan.isOpen}]", shown(SEARCH_DROPDOWN_TAG))
        assertTrue("the first Back left the bubble", bubbleShown())
        assertTrue("the first Back left the fan", fan.isOpen)

        back()
        assertFalse("the second Back closed the bubble", bubbleShown())
        assertTrue("the second Back left the fan", fan.isOpen)

        back()
        assertFalse("the third Back folded the fan", fan.isOpen)
    }

    @Test
    fun `with a fan open and the dropdown open, Back closes the dropdown and the fan stays, then the next Back folds the fan`() {
        fanOpen()
        openDropdown()

        back()
        assertFalse("the first Back closed the dropdown [seen: dropdown=${shown(SEARCH_DROPDOWN_TAG)} fan=${fan.isOpen}]", shown(SEARCH_DROPDOWN_TAG))
        assertTrue("the first Back left the fan open", fan.isOpen)

        back()
        assertFalse("the second Back folded the fan", fan.isOpen)
    }

    /**
     * Covers the touch-mode case only, which is a finger on the screen. Out of touch mode (a hardware keyboard or a
     * D-pad) `View.clearFocus` hands focus back to the first focusable, the search field, whose focus callback
     * reopens the dropdown: that is what this harness did before [declareHostActivity] set touch mode, and it is
     * not covered here. See the completion report, Part B.
     */
    @Test
    fun `in touch mode with no fan, Back closes the dropdown and it is still closed after the screen settles`() {
        composeFan()
        openDropdown()

        back()
        assertFalse("Back closed the dropdown", shown(SEARCH_DROPDOWN_TAG))

        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertFalse("the dropdown stayed closed", shown(SEARCH_DROPDOWN_TAG))
    }

    /**
     * The other side of the owner's option (b): the scaffold ignores the field's focus gain only while its own
     * close is clearing focus. If that were ever left on, the field would stop opening the dropdown at all.
     */
    @Test
    fun `in touch mode, after Back closed the dropdown a touch on the field opens it again`() {
        composeFan()
        openDropdown()
        back()
        assertFalse("positive control: Back closed the dropdown", shown(SEARCH_DROPDOWN_TAG))

        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performTouchInput { click() }
        settle()

        assertTrue("a touch on the field after a Back close opened the dropdown again", shown(SEARCH_DROPDOWN_TAG))
    }

    private companion object {
        const val MAP_TAG = "bubble-dropdown-back-map"
        const val SPOT_X = 150f
        const val SPOT_Y = 420f
    }
}
