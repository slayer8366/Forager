package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.domain.MapRecords
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.ui.log.FIND_OVER_VIEW_TAG
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_OPEN_FIND_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
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
 * Dispatch 2026-10-27-07 Amendment 1, S2 (RECORD -1753): the crowded stack's camera return survives a tab change, as the
 * fan does. The owner's step path: tap crowded > zoom > fan > icon > bubble > Open in Journal > Back > the fan (with the
 * find's bubble over it, as "Open in Journal" has always come back) > Back closes the bubble > Back folds the fan > Back
 * returns the camera > Back is the usual one.
 *
 * Through the real screen, the real Journal and the real Back dispatcher, with every touch a real one. The map is
 * [CrowdedStackStubMap], which is rebuilt when the Maps tab comes back, as the phone's is ([CrowdedStackStubMap.builds] is the
 * positive control: with one map the host could survive in the tab and this would prove nothing).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenCrowdedStackTabTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val map = CrowdedStackStubMap(SPOT_X, SPOT_Y, VIEW_W_DP, VIEW_H_DP)
    private val finds = (0 until 20).map { i -> BUBBLE_FIND.copy(id = "find-$i", ownIdentification = "Chanterelle %02d".format(i)) }
    private var log by mutableStateOf(MushroomLogUiState(entries = finds))

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
        // The records the Maps tab draws, as the ViewModel loads them: the finds at the scene's spot, so a bubble can come back.
        val viewModel = mapLayersViewModel(
            getMapRecords = {
                MapRecords(
                    findMarkers = finds.map { RecordPoint(it.id, SPOT) },
                    photoMarkers = emptyList(),
                    trackPolylines = emptyList(),
                    offlineRegionCircles = emptyList(),
                    failures = emptyList(),
                )
            },
        )
        composeRule.setContent {
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                logUiState = log,
                onOpenLogEntry = { id -> log = log.copy(editingEntry = log.entries.firstOrNull { it.id == id }) },
                onCloseLogEntry = { log = log.copy(editingEntry = null) },
            )
        }
        settle()
    }

    private val density get() = composeRule.density.density

    @Test
    fun `after Open in Journal from the zoom's fan and Back, Back still folds the fan, then returns the camera, then is the usual one`() {
        show()
        val s = checkNotNull(map.scene)
        s.centre = SPOT
        // 20 finds at the spot and 10 photos about 950 m east: one stack of 30 at zoom 10, a fan of 20 once zoomed.
        finds.forEach { s.add(MapLayerIds.FINDS, it.id, SPOT.lat, SPOT.lng) }
        repeat(10) { s.add(MapLayerIds.PHOTOS, "p$it", SPOT.lat, SPOT.lng + 0.012) }

        composeRule.onNodeWithTag(CROWDED_STUB_MAP_TAG).performTouchInput { click(Offset(SPOT_X * density, SPOT_Y * density)) }
        settle()
        val firstFan = checkNotNull(map.fan)
        assertTrue("the zoom's fan opened", firstFan.isOpen)
        val zoomed = s.zoom
        assertTrue("zoomed in: $zoomed", zoomed > START_ZOOM)

        // A real touch on one fanned icon, where its copy is drawn: the one placed nearest the spot.
        val member = firstFan.members.minBy { m -> (m.trueXDp + m.offset.xDp - SPOT_X).let { it * it } + (m.trueYDp + m.offset.yDp - SPOT_Y).let { it * it } }
        val name = finds.first { it.id == member.key.featureId }.ownIdentification!!
        composeRule.onNodeWithTag(CROWDED_STUB_MAP_TAG)
            .performTouchInput { click(Offset((member.trueXDp + member.offset.xDp) * density, (member.trueYDp + member.offset.yDp) * density)) }
        settle()
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(name, useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag(MAP_BUBBLE_OPEN_FIND_TAG).performTouchInput { click(center) }
        settle()
        composeRule.onNodeWithTag(FIND_OVER_VIEW_TAG).assertIsDisplayed()

        back()
        assertEquals("positive control: the Maps tab came back with a new map", 2, map.builds)
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        val reopened = checkNotNull(map.fan)
        assertTrue("the fan is back on the new map", reopened !== firstFan && reopened.isOpen)

        back()
        assertTrue("Back closed the bubble and left the fan", reopened.isOpen)
        back()
        assertFalse("Back folded the fan", reopened.isOpen)
        assertEquals("the camera stays zoomed until the next Back", zoomed, s.zoom, 1e-9)

        back()
        assertEquals("then Back returned the camera to before the tap", START_ZOOM, s.zoom, 1e-9)
        assertEquals("through the live map's camera", 1, map.cameras.last().restores.size)
        assertTrue("not the old map's", map.cameras.first().restores.isEmpty())

        ShadowToast.reset()
        back()
        assertEquals("the Maps tab's own Back", "Tap Back Button Again to Exit", ShadowToast.getTextOfLatestToast())
    }

    private companion object {
        const val SPOT_X = 150f
        const val SPOT_Y = 420f
        const val VIEW_W_DP = 300f
        const val VIEW_H_DP = 600f
        const val START_ZOOM = 10.0
        val SPOT = com.zynergylabs.forager.app.domain.model.LatLng(45.0, -122.0)
    }
}
