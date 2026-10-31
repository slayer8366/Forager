package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.map.ATTRIBUTION_BUTTON_DP
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_PLOT_ROUTE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SAVE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_CANCEL_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_DIALOG_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-30-08, Amendment 9 (RECORD -1902), the owner: "\"i\" moves above the card (Recommended)". The map's attribution
 * "i" rises above the route card while the card shows, as the cluster, the pill and the legend chip do, and goes back when it
 * closes. On the S22 in fullscreen the "i" sat over the card beside Save (docs/audits/2026-10-31-s22-phone-checks.md).
 *
 * The "i" is MapLibre's own control, placed through `UiSettings.setAttributionMargins` from the render mode's
 * `attributionBottomInset` (`MapAttributionInsets`). A real MapView cannot be built under Robolectric, so the map here is a stub
 * and no "i" is drawn: what this asserts is the inset the screen hands the map, read from the render mode the map slot was given,
 * against the card's measured top. What MapLibre then draws is device-only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardAttributionTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 120.dp, 300.dp, LatLng(CREEK.lat, CREEK.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 120.dp, 360.dp, LatLng(RIDGE.lat, RIDGE.lng)),
        ),
    )
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private fun setScreen() {
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = mapLayersViewModel(),
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE),
                routePlotting = RoutePlottingControls(state, plotting),
            )
        }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun touchCentreOf(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        settle()
    }

    /** How far the "i" keeps from the map's bottom edge: the inset `MapAttributionInsets` hands MapLibre, before its own margin. */
    private fun attributionBottom(): Dp {
        val mode = map.renderMode ?: throw AssertionError("the map slot was given a render mode")
        return mode.attributionBottomInset ?: mode.bottomInset
    }

    private fun plotCreekToRidge() {
        touchCentreOf(glyphTag(CREEK.id))
        touchCentreOf(MAP_BUBBLE_PLOT_ROUTE_TAG)
        touchCentreOf(glyphTag(RIDGE.id))
        assertTrue("precondition: plotting with a leg", (plotting.uiState.value.mode as? RoutePlotMode.Plotting)?.draft?.legs?.size == 1)
    }

    private fun assertAboveCardThenBack(where: String) {
        val before = attributionBottom()
        val mapBottom = bounds("map-slot").bottom
        plotCreekToRidge()
        val card = bounds(ROUTE_CARD_TAG)
        val inset = attributionBottom()
        // The "i"'s bottom edge sits `inset` above the map's bottom (MapLibre adds its own margin on top of that, never less).
        assertTrue(
            "$where: the \"i\"'s bottom ${mapBottom - inset} (inset $inset, was $before) is at or above the card's top ${card.top}",
            mapBottom - inset <= card.top + 0.5.dp,
        )

        // The "i"'s old place: the bottom-end corner, its button's side square at the inset it had before the card. Save sits at
        // the card's bottom end, so a real touch there is the card's. Under Robolectric no "i" is drawn, so this touch cannot fail
        // on the "i"; it shows the card still takes a touch at that place. It is not the check that bites.
        val iOldCentreFromBottom = before + (ATTRIBUTION_BUTTON_DP / 2).dp
        val save = bounds(ROUTE_CARD_SAVE_TAG)
        val y = mapBottom - iOldCentreFromBottom
        if (y > save.top && y < save.bottom) {
            composeRule.onNodeWithTag(ROUTE_CARD_SAVE_TAG).performTouchInput {
                click(Offset(width * 0.9f, (y - save.top).toPx()))
            }
            settle()
            assertTrue("$where: a touch on Save at the \"i\"'s old place opened the save dialog", shown(SAVE_ROUTE_DIALOG_TAG))
            touchCentreOf(SAVE_ROUTE_CANCEL_TAG)
        } else {
            println("RouteCardAttributionTest $where: the \"i\"'s old centre (y $y) is not within Save (${save.top}..${save.bottom}); no touch there")
        }

        composeRule.runOnUiThread { plotting.onCancel() }
        settle()
        assertEquals(RoutePlotMode.Off, plotting.uiState.value.mode)
        assertEquals("$where: the card closed, the \"i\" is back where it was", before.value, attributionBottom().value, 0.5f)
    }

    @Test
    fun `in fullscreen, the attribution i rises above the card while it shows, and goes back when it closes`() {
        setScreen()
        composeRule.onNodeWithContentDescription("Fullscreen").performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        settle()
        assertTrue("fullscreen is on", composeRule.onAllNodesWithContentDescription("Exit fullscreen").fetchSemanticsNodes().isNotEmpty())
        assertAboveCardThenBack("fullscreen")
    }

    @Test
    fun `out of fullscreen, the attribution i rises above the card while it shows, and goes back when it closes`() {
        setScreen()
        assertAboveCardThenBack("portrait")
    }
}
