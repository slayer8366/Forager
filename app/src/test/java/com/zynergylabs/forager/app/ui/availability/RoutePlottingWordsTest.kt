package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_PLOT_ROUTE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.DISCARD_ROUTE_CONFIRM_TAG
import com.zynergylabs.forager.app.ui.route.DISCARD_ROUTE_DIALOG_TAG
import com.zynergylabs.forager.app.ui.route.DISCARD_ROUTE_KEEP_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_CANCEL_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_LEGS_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SAVE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_UNDO_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_ADD_DATE_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_CONFIRM_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_DATE_PICKER_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_DIALOG_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_FAILED_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_NAME_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_REMOVE_DATE_TAG
import com.zynergylabs.forager.app.ui.route.routeCardLegNameTag
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-30-08, Amendment 5, N-3 (Sorrel's review of #449): every word the owner approved for route plotting is
 * asserted as text, written out here as the owner approved it rather than read from the constant the screen uses, so a changed
 * word fails a test. The words are the dispatch's "Words on screen" and Amendment 1's approved list, with Amendment 6's card
 * words (RECORD -1887) in place of "Tap the next waypoint", which is gone: "1 Waypoint selected, tap another to route",
 * "<from> to <to>" and "N Waypoints routed", singular "Waypoint" only for 1. "Elevation:" is not on
 * screen until the follow-up on #447; `RouteCardLayoutTest` asserts it at the card's seam.
 *
 * One walk through the real screen with real touches: the waypoint's bubble, the card while plotting, the expanded rows, the
 * save dialog with its date and its failure line, then the prompt a saved route's pin raises mid-plot. Each word is read from
 * the node that shows it, found by its tag where it has one and otherwise as the only text of its kind inside its container.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RoutePlottingWordsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 60.dp, 300.dp, LatLng(CREEK.lat, CREEK.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 60.dp, 360.dp, LatLng(RIDGE.lat, RIDGE.lng)),
            StubGlyph(MapLayerIds.PLANNED_TRIPS, ROUTE_TRIP.id, 60.dp, 420.dp, ROUTE_TRIP.location),
        ),
    )
    private val plotting = RoutePlottingViewModel(
        // The router suspends once before it answers, as the app's does (PlotRouteUseCase reads the downloaded areas from the
        // database). A router that answers without suspending is RoutePlottingImmediateRouterTest's (Amendment 7); this test is
        // about the words, so its router stays as it was.
        routeLeg = { a, b -> yield(); straight(a, b) },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("the store refused the write")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private fun setScreen() {
        val viewModel = mapLayersViewModel(plannedTrips = listOf(ROUTE_TRIP))
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

    private fun touchCentre(node: SemanticsNodeInteraction) {
        node.performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        composeRule.waitForIdle()
    }

    private fun touchCentreOf(tag: String) = touchCentre(composeRule.onNodeWithTag(tag))

    /** The text a tagged node shows, its own and its children's merged, as a reader hears it. */
    private fun shown(tag: String): String =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""

    /** Every text inside [container] (the unmerged tree, so a field's label and a title are their own nodes). */
    private fun textsIn(container: String): List<String> =
        composeRule.onAllNodes(SemanticsMatcher("has text") { it.config.getOrNull(SemanticsProperties.Text) != null } and hasAnyAncestor(hasTestTag(container)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    private fun assertWord(word: String, shownAs: String, where: String) = assertEquals("$where: the approved word", word, shownAs)

    private fun assertWordIn(word: String, container: String, where: String) {
        val texts = textsIn(container)
        assertEquals("$where: \"$word\" shown once in $container, which shows $texts", 1, texts.count { it == word })
    }

    @Test
    fun `every approved word for route plotting is on screen as the owner approved it`() {
        setScreen()

        touchCentreOf(glyphTag(CREEK.id))
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertExists()
        assertWord("Plot route", shown(MAP_BUBBLE_PLOT_ROUTE_TAG), "the waypoint's bubble")

        touchCentreOf(MAP_BUBBLE_PLOT_ROUTE_TAG)
        assertWord("1 Waypoint selected, tap another to route", shown(ROUTE_CARD_COUNT_TAG), "the card at one stop")
        assertEquals("the old hint is gone", 0, composeRule.onAllNodes(hasText("Tap the next waypoint", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertWord("Undo", shown(ROUTE_CARD_UNDO_TAG), "the card")
        assertWord("Cancel", shown(ROUTE_CARD_CANCEL_TAG), "the card")
        assertWord("Save", shown(ROUTE_CARD_SAVE_TAG), "the card")

        touchCentreOf(glyphTag(RIDGE.id))
        assertWord("2 Waypoints routed", shown(ROUTE_CARD_ROUTED_COUNT_TAG), "the card at two stops")
        touchCentreOf(ROUTE_CARD_HANDLE_TAG)
        assertWordIn("No trail data, straight line", ROUTE_CARD_LEGS_TAG, "a leg with no trail data, in the expanded rows")
        assertWord("Creek to Ridge", shown(routeCardLegNameTag(0)), "the first leg's row")
        assertWord("2 Waypoints routed", shown(ROUTE_CARD_ROUTED_COUNT_TAG), "the expanded list's last row, two stops")
        touchCentreOf(ROUTE_CARD_HANDLE_TAG)

        touchCentreOf(glyphTag(CREEK.id))
        assertWord("3 Waypoints routed", shown(ROUTE_CARD_ROUTED_COUNT_TAG), "the card at three stops, A to B to A")
        touchCentreOf(ROUTE_CARD_HANDLE_TAG)
        assertWord("Ridge to Creek", shown(routeCardLegNameTag(1)), "the second leg's row")
        assertWord("3 Waypoints routed", shown(ROUTE_CARD_ROUTED_COUNT_TAG), "the expanded list's last row, three stops")
        touchCentreOf(ROUTE_CARD_HANDLE_TAG)

        touchCentreOf(ROUTE_CARD_SAVE_TAG)
        assertWordIn("Save route", SAVE_ROUTE_DIALOG_TAG, "the save dialog's title")
        assertWordIn("Route name", SAVE_ROUTE_NAME_TAG, "the name field's label")
        assertWord("Add date", shown(SAVE_ROUTE_ADD_DATE_TAG), "the save dialog")
        touchCentreOf(SAVE_ROUTE_ADD_DATE_TAG)
        assertWordIn("Add date", SAVE_ROUTE_DATE_PICKER_TAG, "the date picker's confirm")
        touchCentre(composeRule.onNode(hasText("Add date") and hasAnyAncestor(hasTestTag(SAVE_ROUTE_DATE_PICKER_TAG))))
        assertWord("Remove date", shown(SAVE_ROUTE_REMOVE_DATE_TAG), "the save dialog with a date")
        touchCentreOf(SAVE_ROUTE_CONFIRM_TAG)
        assertWord("Couldn't save the route. Try again.", shown(SAVE_ROUTE_FAILED_TAG), "a save that failed")

        composeRule.runOnUiThread { plotting.onDismissSaveDialog() }
        composeRule.waitForIdle()
        touchCentreOf(glyphTag(ROUTE_TRIP.id))
        composeRule.onNodeWithTag(DISCARD_ROUTE_DIALOG_TAG).assertExists()
        assertWordIn("Discard this route?", DISCARD_ROUTE_DIALOG_TAG, "the prompt's title")
        assertWord("Discard", shown(DISCARD_ROUTE_CONFIRM_TAG), "the prompt")
        assertWord("Keep plotting", shown(DISCARD_ROUTE_KEEP_TAG), "the prompt")
    }
}
