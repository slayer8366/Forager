package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.format.displayDate
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.DISCARD_ROUTE_CONFIRM_TAG
import com.zynergylabs.forager.app.ui.route.DISCARD_ROUTE_DIALOG_TAG
import com.zynergylabs.forager.app.ui.route.DISCARD_ROUTE_KEEP_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SAVE_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_ADD_DATE_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_CANCEL_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_CONFIRM_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_DATE_PICKER_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_DATE_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_DIALOG_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_FAILED
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_FAILED_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_NAME_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_REMOVE_DATE_TAG
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The route's two dialogs (dispatch 2026-10-30-08, Amendment 3; the first stop, (h) 8), through the real screen and a real
 * [RoutePlottingViewModel]: "Discard this route?" (a route trip's pin tapped mid-plot), and "Save route" with its optional date
 * and its failure line. Every touch is a real one sampled across the control's own bounds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RoutePlottingDialogsTest {

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

    /** Every call the save made, as (name, date); [failSaves] makes each one fail, as a store that cannot write would. */
    private val saves = mutableListOf<Pair<String, LocalDate?>>()
    private var failSaves = false
    private val logged = mutableListOf<String>()
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date ->
            saves += name to date
            if (failSaves) Result.failure(IllegalStateException("the store refused the write"))
            else Result.success(PlannedTrip("new", name, route.stops.first().position, date, route))
        },
        onSaved = {},
        errorLog = { _, message, _ -> logged += message },
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

    private fun touchAcross(node: SemanticsNodeInteraction, fx: Float, fy: Float) {
        node.performTouchInput { click(Offset(width * fx, height * fy)) }
        composeRule.waitForIdle()
    }

    private fun touchCentreOf(tag: String) = touchAcross(composeRule.onNodeWithTag(tag), 0.5f, 0.5f)

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun text(tag: String): String =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    private fun typedName(): String =
        composeRule.onNodeWithTag(SAVE_ROUTE_NAME_TAG).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun mode() = plotting.uiState.value.mode

    /** Plotting from Creek with Ridge added by a real touch on its glyph: one leg, so Save is on. */
    private fun plotCreekToRidge() {
        composeRule.runOnUiThread { plotting.onStartPlotting(CREEK.stop()) }
        composeRule.waitForIdle()
        touchCentreOf(glyphTag(RIDGE.id))
    }

    private fun openSaveDialog() {
        plotCreekToRidge()
        touchCentreOf(ROUTE_CARD_SAVE_TAG)
        composeRule.onNodeWithTag(SAVE_ROUTE_DIALOG_TAG).assertIsDisplayed()
    }

    /** The picker's own confirm, "Add date": the one under the picker's tag, not the save dialog's button of the same words. */
    private fun pickerConfirm(): SemanticsNodeInteraction =
        composeRule.onNode(hasText("Add date") and hasAnyAncestor(hasTestTag(SAVE_ROUTE_DATE_PICKER_TAG)))

    // (a) "Discard this route?"

    @Test
    fun `real touches across Keep plotting each keep the route being plotted and close the prompt`() {
        setScreen()
        plotCreekToRidge()
        val draft = (mode() as RoutePlotMode.Plotting).draft
        ACROSS.forEach { (fx, fy) ->
            val where = "a touch at ($fx, $fy) on Keep plotting"
            touchCentreOf(glyphTag(ROUTE_TRIP.id))
            composeRule.onNodeWithTag(DISCARD_ROUTE_DIALOG_TAG).assertIsDisplayed()

            touchAcross(composeRule.onNodeWithTag(DISCARD_ROUTE_KEEP_TAG), fx, fy)

            assertEquals("$where: the route being plotted is kept", RoutePlotMode.Plotting(draft), mode())
            assertNull("$where: nothing waits on the prompt", plotting.uiState.value.pendingDiscard)
            assertEquals("$where: the prompt closed", 0, count(DISCARD_ROUTE_DIALOG_TAG))
        }
    }

    @Test
    fun `real touches across Discard each drop the route being plotted and open the saved one`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            val where = "a touch at ($fx, $fy) on Discard"
            composeRule.runOnUiThread { plotting.onCloseSavedRoute() }
            plotCreekToRidge()
            touchCentreOf(glyphTag(ROUTE_TRIP.id))
            composeRule.onNodeWithTag(DISCARD_ROUTE_DIALOG_TAG).assertIsDisplayed()

            touchAcross(composeRule.onNodeWithTag(DISCARD_ROUTE_CONFIRM_TAG), fx, fy)

            val showing = mode() as? RoutePlotMode.Showing
            assertEquals("$where: the saved route opened, was ${mode()}", ROUTE_TRIP, showing?.trip)
            assertFalse("$where: opened from its pin, not the Trip Planner", showing!!.fromTripPlanner)
            assertEquals("$where: the prompt closed", 0, count(DISCARD_ROUTE_DIALOG_TAG))
            assertEquals("$where: the map draws the saved route's stops", 2, map.content?.plottedRoute?.stopDots?.size)
        }
    }

    // (b) "Save route": Add date and Remove date

    @Test
    fun `real touches across Add date open the picker, its Add date sets the date, and Remove date clears it`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            val where = "touches at ($fx, $fy)"
            openSaveDialog()

            touchAcross(composeRule.onNodeWithTag(SAVE_ROUTE_ADD_DATE_TAG), fx, fy)
            composeRule.onNodeWithTag(SAVE_ROUTE_DATE_PICKER_TAG).assertIsDisplayed()

            val before = LocalDate.now()
            touchAcross(pickerConfirm(), fx, fy)
            val after = LocalDate.now()
            assertEquals("$where: the picker closed", 0, count(SAVE_ROUTE_DATE_PICKER_TAG))
            val shown = text(SAVE_ROUTE_DATE_TAG)
            assertTrue("$where: the date row shows today, read \"$shown\"", shown == displayDate(before) || shown == displayDate(after))
            assertEquals("$where: Add date gave way to the date", 0, count(SAVE_ROUTE_ADD_DATE_TAG))

            touchAcross(composeRule.onNodeWithTag(SAVE_ROUTE_REMOVE_DATE_TAG), fx, fy)
            assertEquals("$where: the date row went", 0, count(SAVE_ROUTE_DATE_TAG))
            composeRule.onNodeWithTag(SAVE_ROUTE_ADD_DATE_TAG).assertIsDisplayed()

            touchCentreOf(SAVE_ROUTE_CANCEL_TAG)
            composeRule.runOnUiThread { plotting.onCancel() }
            composeRule.waitForIdle()
        }
        assertEquals("nothing was saved", emptyList<Pair<String, LocalDate?>>(), saves)
    }

    @Test
    fun `a date added through the picker is the date Save passes on`() {
        setScreen()
        openSaveDialog()
        touchCentreOf(SAVE_ROUTE_ADD_DATE_TAG)
        val before = LocalDate.now()
        touchAcross(pickerConfirm(), 0.5f, 0.5f)
        val after = LocalDate.now()

        touchAcross(composeRule.onNodeWithTag(SAVE_ROUTE_CONFIRM_TAG), 0.9f, 0.9f)

        assertEquals("one save", 1, saves.size)
        val (name, date) = saves.single()
        assertEquals("the default name", "Creek to Ridge", name)
        assertTrue("the picked date, today (was $date)", date == before || date == after)
        assertEquals("the save ended plotting", RoutePlotMode.Off, mode())
        assertEquals("the dialog closed", 0, count(SAVE_ROUTE_DIALOG_TAG))
    }

    @Test
    fun `a date added then removed leaves Save passing no date`() {
        setScreen()
        openSaveDialog()
        touchCentreOf(SAVE_ROUTE_ADD_DATE_TAG)
        touchAcross(pickerConfirm(), 0.5f, 0.5f)
        composeRule.onNodeWithTag(SAVE_ROUTE_DATE_TAG).assertIsDisplayed()
        touchCentreOf(SAVE_ROUTE_REMOVE_DATE_TAG)

        touchAcross(composeRule.onNodeWithTag(SAVE_ROUTE_CONFIRM_TAG), 0.1f, 0.1f)

        assertEquals(listOf("Creek to Ridge" to null), saves)
        assertEquals("the save ended plotting", RoutePlotMode.Off, mode())
    }

    // (c) A failed save

    @Test
    fun `a failed save keeps the dialog, the typed name and the route, and says so, at every touch across Save`() {
        failSaves = true
        setScreen()
        openSaveDialog()
        composeRule.onNodeWithTag(SAVE_ROUTE_NAME_TAG).performTextReplacement("Morning loop")
        composeRule.waitForIdle()
        val draft = (mode() as RoutePlotMode.Plotting).draft

        ACROSS.forEachIndexed { i, (fx, fy) ->
            val where = "a touch at ($fx, $fy) on Save"
            touchAcross(composeRule.onNodeWithTag(SAVE_ROUTE_CONFIRM_TAG), fx, fy)

            assertEquals("$where: the save was tried with the typed name", List(i + 1) { "Morning loop" to null }, saves)
            composeRule.onNodeWithTag(SAVE_ROUTE_DIALOG_TAG).assertIsDisplayed()
            assertEquals("$where: the name field keeps the typed name", "Morning loop", typedName())
            assertEquals("$where: the failure is said", SAVE_ROUTE_FAILED, text(SAVE_ROUTE_FAILED_TAG))
            composeRule.onNodeWithTag(SAVE_ROUTE_FAILED_TAG).assertIsDisplayed()
            assertEquals("$where: plotting goes on with the same route", RoutePlotMode.Plotting(draft), mode())
            assertTrue("$where: the state says the save failed", plotting.uiState.value.saveFailed)
            assertFalse("$where: no save is left running", plotting.uiState.value.saving)
            assertEquals("$where: each failure is logged", i + 1, logged.count { it == "Couldn't save the route." })
        }
        assertEquals("the route kept is Creek to Ridge", listOf(CREEK.stop(), RIDGE.stop()), draft.stops)
    }
}
