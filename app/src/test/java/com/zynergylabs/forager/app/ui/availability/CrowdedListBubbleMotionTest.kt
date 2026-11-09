package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.domain.CrowdedIconsMode
import com.zynergylabs.forager.app.domain.CrowdedIconsPreferenceRepository
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.map.CROWDED_LIST_CLOSE_TAG
import com.zynergylabs.forager.app.ui.map.CROWDED_LIST_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.crowdedListRowTag
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.MapPopUpScaleKey
import com.zynergylabs.forager.app.ui.motion.MotionTokens
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
 * Dispatch 2026-11-04-02, A5 and A6 (RECORD -2039, -2040), on the real [AvailabilityScreen] over [CrowdedStackStubMap], at a
 * fixed clock, with real touches.
 *
 * A5, the owner: "And have it animate open and shut please"; S4: the standard bubble's own motion, MapPopUp's fade and draw-only
 * grow from the stack ([MotionTokens.mapPopUpFadeSpec], [MotionTokens.mapPopUpGrowSpec], from [MotionTokens.MAP_POPUP_ENTER_SCALE]),
 * with its fade alone under Reduce Motion. The row-to-record and Back-to-list switches use the same motion: the one leaves as
 * the other comes. Caught mid-frame with the clock stopped, through the pop-up's live scale ([MapPopUpScaleKey]).
 *
 * A6 at the screen: in "Zoom in, then fan" the zoom's move is shown ([com.zynergylabs.forager.app.ui.map.fanout.StackCamera.showMoveFrom])
 * and Back's camera step eases back; under Reduce Motion both jump, as before. The ease itself is MapLibre's, device-only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class CrowdedListBubbleMotionTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val map = CrowdedStackStubMap(SPOT_X, SPOT_Y, VIEW_W_DP, VIEW_H_DP)
    private val finds = (0 until 3).map { i -> BUBBLE_FIND.copy(id = "find-$i", ownIdentification = "Chanterelle %02d".format(i)) }

    private val density get() = composeRule.density.density

    @After
    fun restartClock() {
        composeRule.mainClock.autoAdvance = true
    }

    private fun settle() {
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun show(mode: CrowdedIconsMode = CrowdedIconsMode.LIST, reduceMotion: Boolean = false) {
        val viewModel = mapLayersViewModel(store = OneCellStore(), crowdedIcons = StoredMode(mode))
        composeRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                MapLayersTestScreen(
                    viewModel = viewModel,
                    mapSlot = map.slot,
                    store = OneCellStore(),
                    logUiState = MushroomLogUiState(entries = finds),
                    currentTime = ROUTE_CARD_NOON_CLOCK,
                )
            }
        }
        settle()
        assertEquals("positive control: the stored mode was read", mode, viewModel.uiState.value.crowdedIconsMode)
        val s = checkNotNull(map.scene)
        finds.forEach { s.add(MapLayerIds.FINDS, it.id, s.centre.lat, s.centre.lng) }
    }

    /**
     * Stops the clock and lets [frames] frames through after [action], so an entrance or a leave just begun is caught (MapPopUpMotionTest's
     * pattern). Five, not its two: two frames into a leave the pop-up is still drawn at 1 (run 41785067129), its shrink not yet begun.
     */
    private fun pauseAfter(frames: Int = 5, action: () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        action()
        composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
        repeat(frames) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private fun tapTheStack() = composeRule.onNodeWithTag(CROWDED_STUB_MAP_TAG).performTouchInput { click(Offset(SPOT_X * density, SPOT_Y * density)) }

    private fun tapCentre(tag: String) = composeRule.onNodeWithTag(tag).performTouchInput { click(center) }

    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** The draw-only grow of the pop-up holding [tag]: 1 at rest (MapPopUpMotionTest's). */
    private fun popUpScale(tag: String): Float =
        composeRule.onNode(SemanticsMatcher.keyIsDefined(MapPopUpScaleKey) and hasAnyDescendant(hasTestTag(tag)), useUnmergedTree = true)
            .fetchSemanticsNode().config[MapPopUpScaleKey]

    /** [tag] is on screen and drawn small: its grow or shrink is under way, at or above where it starts and short of 1 (MapPopUpMotionTest's "growing: drawn small"). */
    private fun assertMidGrow(what: String, tag: String) {
        assertTrue("$what: $tag is on screen", shown(tag))
        val scale = popUpScale(tag)
        assertTrue("$what: $tag is drawn small, scale $scale, from ${MotionTokens.MAP_POPUP_ENTER_SCALE} and short of 1", scale >= MotionTokens.MAP_POPUP_ENTER_SCALE - 1e-4f && scale < 0.999f)
    }

    private val row get() = crowdedListRowTag(FanKey(MapLayerIds.FINDS, "find-0"))

    @Test
    fun `the bubble grows open from the stack and shrinks shut`() {
        show()
        pauseAfter { tapTheStack() }
        assertMidGrow("opening", CROWDED_LIST_TAG)
        settle()
        assertEquals("at rest", 1f, popUpScale(CROWDED_LIST_TAG), 0f)

        pauseAfter { tapCentre(CROWDED_LIST_CLOSE_TAG) }
        assertMidGrow("closing, still drawn as it leaves", CROWDED_LIST_TAG)
        settle()
        assertTrue("gone at rest", !shown(CROWDED_LIST_TAG))
    }

    @Test
    fun `a row's bubble comes as the list goes, and Back brings the list back the same way`() {
        show()
        tapTheStack()
        settle()

        pauseAfter { tapCentre(row) }
        assertMidGrow("the record's bubble coming", MAP_BUBBLE_TAG)
        assertMidGrow("the list going", CROWDED_LIST_TAG)
        settle()
        assertTrue(!shown(CROWDED_LIST_TAG))

        pauseAfter { composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() } }
        assertMidGrow("the list coming back", CROWDED_LIST_TAG)
        assertMidGrow("the record's bubble going", MAP_BUBBLE_TAG)
        settle()
        assertTrue(shown(CROWDED_LIST_TAG) && !shown(MAP_BUBBLE_TAG))
    }

    @Test
    fun `under Reduce Motion the bubble fades with no grow, as the standard bubble does`() {
        show(reduceMotion = true)
        pauseAfter { tapTheStack() }
        assertTrue("on screen at once", shown(CROWDED_LIST_TAG))
        assertEquals("no grow (S4: the standard fade)", 1f, popUpScale(CROWDED_LIST_TAG), 0f)
    }

    @Test
    fun `in Zoom in, then fan, the zoom's move is shown, and Back's camera step eases back`() {
        show(mode = CrowdedIconsMode.ZOOM_THEN_FAN)
        zoomThenBack()
        val camera = map.cameras.single()
        assertEquals("the zoom's move was shown", 1, camera.eases.size)
        assertEquals("Back's camera step eased", listOf(true), camera.restoresAnimated)
    }

    @Test
    fun `under Reduce Motion the zoom and Back's camera step jump, as before`() {
        show(mode = CrowdedIconsMode.ZOOM_THEN_FAN, reduceMotion = true)
        zoomThenBack()
        val camera = map.cameras.single()
        assertTrue("no move shown", camera.eases.isEmpty())
        assertEquals("Back's camera step jumped", listOf(false), camera.restoresAnimated)
    }

    /** 20 photos and 10 finds just east: one stack of 30, two apart once zoomed; tap it, then Back folds the fan and Back returns the camera. */
    private fun zoomThenBack() {
        val s = checkNotNull(map.scene)
        repeat(20) { s.add(MapLayerIds.PHOTOS, "p$it", s.centre.lat, s.centre.lng) }
        repeat(10) { s.add(MapLayerIds.FINDS, "f$it", s.centre.lat, s.centre.lng + 0.012) }
        tapTheStack()
        settle()
        assertTrue("positive control: the fan opened after the zoom", checkNotNull(map.fan).isOpen)
        repeat(2) {
            composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
            settle()
        }
        assertEquals("positive control: the camera is back", START_ZOOM, s.zoom, 1e-9)
    }

    private class StoredMode(private val mode: CrowdedIconsMode) : CrowdedIconsPreferenceRepository {
        override suspend fun getCrowdedIconsMode(unknown: (String) -> Unit): Result<CrowdedIconsMode?> = Result.success(mode)
        override suspend fun setCrowdedIconsMode(mode: CrowdedIconsMode): Result<Unit> = Result.success(Unit)
    }

    private companion object {
        /** As AvailabilityScreenCrowdedListModeTest's: the spot, and a 300 x 600 dp room in which a fan of 20 fits and 30 does not. */
        const val SPOT_X = 150f
        const val SPOT_Y = 420f
        const val VIEW_W_DP = 300f
        const val VIEW_H_DP = 600f
        const val START_ZOOM = 10.0
    }
}
