package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_CANCEL_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SAVE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_UNDO_TAG
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
 * The route card in a short landscape window with the rail and in tablet windows (dispatch 2026-10-30-08, Amendment 3; the
 * first stop, (h) 2 and 4): Undo, Cancel and Save each answer real touches across them; the card keeps clear of the rail; a
 * long-press just above the card, and on a tablet beside it, reaches the map (CLAUDE.md, the Surface pitfall).
 *
 * Plotting is started on the ViewModel: the claim here is the card's touches, not how plotting starts (RoutePlottingScreenTest).
 * Robolectric reports zero insets (CLAUDE.md), so where the card sits against a real nav bar or cutout is device-only.
 */
abstract class RoutePlottingWindowTests(private val hasRail: Boolean, private val besideRoom: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = RouteMapSlot(emptyList())
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private fun setScreen() {
        val viewModel = mapLayersViewModel()
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE),
                routePlotting = RoutePlottingControls(state, plotting),
            )
        }
        composeRule.waitForIdle()
    }

    private fun touchAcross(node: SemanticsNodeInteraction, fx: Float, fy: Float) {
        node.performTouchInput { click(Offset(width * fx, height * fy)) }
        composeRule.waitForIdle()
    }

    private fun mode() = plotting.uiState.value.mode

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun plotCreekToRidge() {
        composeRule.runOnUiThread {
            plotting.onStartPlotting(CREEK.stop())
            plotting.onWaypointTapped(RIDGE.stop())
        }
        composeRule.waitForIdle()
        assertEquals("Creek to Ridge, its leg in", 1, (mode() as RoutePlotMode.Plotting).draft.legs.size)
        composeRule.onNodeWithTag(ROUTE_CARD_TAG).assertIsDisplayed()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun railBounds(): DpRect? = if (count(COMPACT_NAVIGATION_RAIL_TAG) == 0) null else bounds(COMPACT_NAVIGATION_RAIL_TAG)

    private fun longPressAt(x: Dp, y: Dp) {
        val at = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onRoot().performTouchInput { longClick(at) }
        composeRule.waitForIdle()
    }

    @Test
    fun `real touches across Undo each remove the last stop`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            plotCreekToRidge()
            touchAcross(composeRule.onNodeWithTag(ROUTE_CARD_UNDO_TAG), fx, fy)
            assertEquals("a touch at ($fx, $fy) on Undo", listOf(CREEK.stop()), (mode() as? RoutePlotMode.Plotting)?.draft?.stops)
        }
    }

    @Test
    fun `real touches across Cancel each drop the route and the card`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            plotCreekToRidge()
            touchAcross(composeRule.onNodeWithTag(ROUTE_CARD_CANCEL_TAG), fx, fy)
            assertEquals("a touch at ($fx, $fy) on Cancel", RoutePlotMode.Off, mode())
            assertEquals("a touch at ($fx, $fy) on Cancel: no card", 0, count(ROUTE_CARD_TAG))
        }
    }

    @Test
    fun `real touches across Save each open the save dialog`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            plotCreekToRidge()
            touchAcross(composeRule.onNodeWithTag(ROUTE_CARD_SAVE_TAG), fx, fy)
            assertTrue("a touch at ($fx, $fy) on Save: the dialog is open", plotting.uiState.value.saveDialogOpen)
            composeRule.onNodeWithTag(SAVE_ROUTE_DIALOG_TAG).assertIsDisplayed()
            touchAcross(composeRule.onNodeWithTag(SAVE_ROUTE_CANCEL_TAG), 0.5f, 0.5f)
            assertEquals("a touch at ($fx, $fy) on Save, then Cancel: the dialog closed", 0, count(SAVE_ROUTE_DIALOG_TAG))
            composeRule.runOnUiThread { plotting.onCancel() }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `the card keeps clear of the rail`() {
        setScreen()
        plotCreekToRidge()
        val rail = railBounds()
        if (!hasRail) {
            assertEquals("this window has no rail", null, rail)
            return
        }
        assertTrue("this window shows the rail", rail != null)
        val card = bounds(ROUTE_CARD_TAG)
        val clear = card.right <= rail!!.left || card.left >= rail.right
        assertTrue("the card $card is clear of the rail $rail", clear)
    }

    @Test
    fun `real long-presses just above the card, and beside it where there is room, reach the map`() {
        setScreen()
        plotCreekToRidge()
        val card = bounds(ROUTE_CARD_TAG)
        var presses = 0
        listOf(0.2f, 0.4f, 0.6f).forEach { fx ->
            longPressAt(card.left + (card.right - card.left) * fx, card.top - 12.dp)
            presses++
            assertEquals("a long-press at $fx across, just above the card $card", presses, map.longPresses)
        }
        if (!besideRoom) return
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val rail = railBounds()
        val leftEdge = if (rail != null && rail.right <= card.left) rail.right else root.left
        val rightEdge = if (rail != null && rail.left >= card.right) rail.left else root.right
        val midY = (card.top + card.bottom) / 2
        listOf("left" to (leftEdge + card.left) / 2, "right" to (card.right + rightEdge) / 2).forEach { (side, x) ->
            longPressAt(x, midY)
            presses++
            assertEquals("a long-press beside the card, on its $side at $x, $midY (card $card)", presses, map.longPresses)
        }
    }
}

/** A short landscape phone window, the rail on (dispatch 2026-10-30-08, Amendment 3). The gaps beside the 640 dp card are too narrow here to test a press in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi")
class RoutePlottingShortLandscapeTest : RoutePlottingWindowTests(hasRail = true, besideRoom = false)

/**
 * A landscape tablet: the phone's landscape layout with the rail (AvailabilityScreenTabletAsPhoneTest). At xhdpi (Amendment 4,
 * the owner: "Apply both (Recommended)"): at mdpi, with no density qualifier, only the centre of Undo, Cancel and Save took a
 * touch, and their touch bounds were their own bounds; at xhdpi the five samples all take. Why mdpi differs was not
 * determined; no real tablet runs at mdpi.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1318dp-h824dp-land-xhdpi")
class RoutePlottingTabletLandscapeTest : RoutePlottingWindowTests(hasRail = true, besideRoom = true)

/** A portrait tablet: the bottom nav, no rail; the card at its 640 dp cap leaves room beside it. At xhdpi, as the landscape tablet. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w824dp-h1318dp-xhdpi")
class RoutePlottingTabletPortraitTest : RoutePlottingWindowTests(hasRail = false, besideRoom = true)
