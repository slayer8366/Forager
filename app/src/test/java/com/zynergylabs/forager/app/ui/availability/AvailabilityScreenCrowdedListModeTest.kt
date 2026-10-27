package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.domain.CrowdedIconsMode
import com.zynergylabs.forager.app.domain.CrowdedIconsPreferenceRepository
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.map.CROWDED_LIST_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.crowdedListRowTag
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Settings > Map > "Crowded icons" = List (dispatch 2026-10-27-14, RECORD -1750; the owner's step path), on the real
 * [AvailabilityScreen] with the real [AvailabilityViewModel] reading the stored mode at start: a tap on a stack of two or
 * more opens the list at once, with no zoom and no fan, even a stack whose fan would fit; a real touch at several points
 * across each row opens that record's bubble and Back brings the list back; Back closes the list with no camera step,
 * then is the Maps tab's own; a tap on the map closes the list.
 *
 * The map is [CrowdedStackStubMap] (a `MapView` cannot be built under Robolectric): the real [MapTapHandler] and
 * `CrowdedStackZoom` over a scene whose camera ([com.zynergylabs.forager.app.ui.map.fanout.SceneStackCamera]) records
 * every frame and restore, with the host handed through the real `TapHandlerRef`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenCrowdedListModeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val map = CrowdedStackStubMap(SPOT_X, SPOT_Y, VIEW_W_DP, VIEW_H_DP)
    private val finds = (0 until 3).map { i -> BUBBLE_FIND.copy(id = "find-$i", ownIdentification = "Chanterelle %02d".format(i)) }

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

    /** The real screen, with "Crowded icons" stored as [mode] and read back by the ViewModel at start. */
    private fun show(mode: CrowdedIconsMode = CrowdedIconsMode.LIST) {
        val viewModel = mapLayersViewModel(store = OneCellStore(), crowdedIcons = StoredCrowdedIcons(mode))
        composeRule.setContent {
            MapLayersTestScreen(viewModel = viewModel, mapSlot = map.slot, store = OneCellStore(), logUiState = MushroomLogUiState(entries = finds))
        }
        settle()
        assertEquals("positive control: the ViewModel read the stored mode", mode, viewModel.uiState.value.crowdedIconsMode)
    }

    /** Three finds on one spot: a stack whose fan fits the 300 dp room (a fan of 20 fits it). */
    private fun threeFindsAtTheSpot() {
        val s = checkNotNull(map.scene)
        finds.forEach { s.add(MapLayerIds.FINDS, it.id, s.centre.lat, s.centre.lng) }
    }

    private fun tapTheSpot() {
        composeRule.onNodeWithTag(CROWDED_STUB_MAP_TAG).performTouchInput { click(Offset(SPOT_X * density, SPOT_Y * density)) }
        settle()
    }

    private fun bubbleShown(): Boolean =
        composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).fetchSemanticsNodes().isNotEmpty() &&
            runCatching { composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed() }.isSuccess

    private fun listShown(): Boolean = composeRule.onAllNodesWithTag(CROWDED_LIST_TAG).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `in List mode a stack whose fan would fit opens the list at once, with no zoom and no fan`() {
        show()
        threeFindsAtTheSpot()

        tapTheSpot()

        assertFalse("no fan in List mode", checkNotNull(map.fan).isOpen)
        composeRule.onNodeWithTag(CROWDED_LIST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("3 records at this spot").assertIsDisplayed()
        assertTrue("no zoom", map.cameras.single().frames.isEmpty())
        assertEquals("the camera did not move", START_ZOOM, checkNotNull(map.scene).zoom, 1e-9)
    }

    @Test
    fun `in List mode a real touch at several points across each row opens its bubble, Back brings the list back, then closes it with no camera step, then is the Maps tab's own`() {
        show()
        threeFindsAtTheSpot()
        tapTheSpot()
        composeRule.onNodeWithTag(CROWDED_LIST_TAG).assertIsDisplayed()

        // A finger is not a point (CLAUDE.md): five places across each row, each tried, and each miss named.
        val missed = mutableListOf<String>()
        for (i in finds.indices) {
            val key = FanKey(MapLayerIds.FINDS, "find-$i")
            val name = "Chanterelle %02d".format(i)
            val size = composeRule.onNodeWithTag(crowdedListRowTag(key)).fetchSemanticsNode().size
            val points = listOf(
                Offset(size.width * 0.1f, size.height * 0.5f),
                Offset(size.width * 0.5f, size.height * 0.5f),
                Offset(size.width * 0.9f, size.height * 0.5f),
                Offset(size.width * 0.5f, size.height * 0.15f),
                Offset(size.width * 0.5f, size.height * 0.85f),
            )
            for (point in points) {
                val rowBounds: Rect = composeRule.onNodeWithTag(crowdedListRowTag(key)).fetchSemanticsNode().boundsInRoot
                composeRule.onNodeWithTag(crowdedListRowTag(key)).performTouchInput { click(point) }
                settle()
                if (!bubbleShown()) {
                    missed += "row $i at $point (${rowBounds.topLeft + point} in the root); row bounds $rowBounds; the list ${if (listShown()) "still up" else "closed"}"
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
        assertEquals("the map is where it was", START_ZOOM, checkNotNull(map.scene).zoom, 1e-9)

        ShadowToast.reset()
        back()
        assertEquals("no camera step: the next Back is the Maps tab's own", "Tap Back Button Again to Exit", ShadowToast.getTextOfLatestToast())
        assertTrue("and the camera never moved", map.cameras.single().restores.isEmpty())
    }

    @Test
    fun `in List mode a tap on the map closes the list`() {
        show()
        threeFindsAtTheSpot()
        tapTheSpot()
        composeRule.onNodeWithTag(CROWDED_LIST_TAG).assertIsDisplayed()

        val list = composeRule.onNodeWithTag(CROWDED_LIST_TAG).fetchSemanticsNode().boundsInRoot
        val mapBounds = composeRule.onNodeWithTag(CROWDED_STUB_MAP_TAG).fetchSemanticsNode().boundsInRoot
        val above = Offset(list.center.x, list.top - 120f * density)
        assertFalse("positive control: the point is outside the list", list.contains(above))
        composeRule.onNodeWithTag(CROWDED_STUB_MAP_TAG).performTouchInput { click(above - mapBounds.topLeft) }
        settle()

        composeRule.onNodeWithTag(CROWDED_LIST_TAG).assertDoesNotExist()
        assertFalse("and no bubble", bubbleShown())
    }

    @Test
    fun `in Zoom in, then fan, the default, a stack whose fan fits still fans`() {
        show(CrowdedIconsMode.ZOOM_THEN_FAN)
        threeFindsAtTheSpot()

        tapTheSpot()

        assertTrue("fanned, as before", checkNotNull(map.fan).isOpen)
        composeRule.onNodeWithTag(CROWDED_LIST_TAG).assertDoesNotExist()
    }

    /** A stored "Crowded icons" value; writes are kept so a test could read them. */
    private class StoredCrowdedIcons(private var mode: CrowdedIconsMode?) : CrowdedIconsPreferenceRepository {
        override suspend fun getCrowdedIconsMode(unknown: (String) -> Unit): Result<CrowdedIconsMode?> = Result.success(mode)
        override suspend fun setCrowdedIconsMode(mode: CrowdedIconsMode): Result<Unit> {
            this.mode = mode
            return Result.success(Unit)
        }
    }

    private companion object {
        /** Where the stack is drawn, dp of the map, and the camera's view about it: as AvailabilityScreenCrowdedStackTest's. */
        const val SPOT_X = 150f
        const val SPOT_Y = 420f
        const val VIEW_W_DP = 300f
        const val VIEW_H_DP = 600f

        /** The scene's zoom at the start. */
        const val START_ZOOM = 10.0
    }
}
