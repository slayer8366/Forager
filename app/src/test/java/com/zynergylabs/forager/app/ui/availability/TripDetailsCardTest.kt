package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.NavigationTargetKind
import com.zynergylabs.forager.app.domain.WaypointNavigation
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_DETAILS_TAG
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.map.MapChromeContainerColor
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-05-05: a trip's Details card, opened from its bubble on the Maps tab (C1).
 * - A9 (RECORD -2148, the owner: "Leave both off (Recommended)", "Name, the trip's date, MGRS and coordinates, then Navigate
 *   and Directions, centred. Nothing on it is empty or always the same."): no Created and no Used in, and an undated trip
 *   has no date row.
 * - A10 (the owner: "Trip date (Recommended)"): the date's label.
 * - A5: Navigate and Directions side by side and centred, Directions on the right, handing the trip to a maps app.
 * - A12 (the planner, RECORD -2147): the card stays open after Directions.
 * - A7: over the map, at the map chrome's 80%.
 *
 * Through the real [AvailabilityScreen] and the real [AvailabilityViewModel]; every touch on a control is a real one at five
 * points across its own bounds ([ACROSS]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class TripDetailsCardTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = BubbleMapSlot(listOf(SADDLE_GLYPH))
    private lateinit var viewModel: AvailabilityViewModel

    private fun setScreen(trip: PlannedTrip = SADDLE) {
        viewModel = mapLayersViewModel(plannedTrips = listOf(trip))
        composeRule.setContent {
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                navigation = AvailabilityScreenNavigation(
                    onNavigateToWaypoint = viewModel::onNavigateToWaypoint,
                    onNavigateToTrip = viewModel::onNavigateToTrip,
                    onStopWaypointNavigation = viewModel::onStopWaypointNavigation,
                ),
            )
        }
        composeRule.waitForIdle()
    }

    /** The trip's card, by real touches: its flag, then its bubble's Details. */
    private fun openCard() {
        composeRule.onNodeWithTag(glyphTag(SADDLE.id)).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MAP_BUBBLE_DETAILS_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertTrue("the card is open", cardShowing())
    }

    private fun cardShowing() = composeRule.onAllNodesWithTag(SHEET).fetchSemanticsNodes().isNotEmpty()
    private fun field(key: String) = composeRule.onNodeWithTag("record-details-field-$key")
    private fun fieldShown(key: String) = composeRule.onAllNodesWithTag("record-details-field-$key").fetchSemanticsNodes().isNotEmpty()
    private fun label(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    @Test
    fun `the card shows the trip's name, Trip date, MGRS and coordinates, in that order, and nothing else`() {
        setScreen()
        openCard()

        composeRule.onNodeWithTag(TITLE).assertTextEquals("Saddle loop")
        field("trip-date").assertTextEquals("Trip date", "Oct 3, 2026")
        field("mgrs").assertTextEquals("MGRS", gridOf(SADDLE.location))
        field("coordinates").assertTextEquals("Coordinates", SADDLE_COORDINATES)
        val tops = listOf(TITLE, "record-details-field-trip-date", "record-details-field-mgrs", "record-details-field-coordinates", NAVIGATE)
            .map { composeRule.onNodeWithTag(it).getUnclippedBoundsInRoot().top }
        assertEquals("top to bottom: $tops", tops.sortedBy { it.value }, tops)
        for (missing in listOf("created", "used-in", "track")) assertTrue("no $missing row", !fieldShown(missing))
        assertEquals("no Created row by its words either", 0, composeRule.onAllNodesWithText("Created").fetchSemanticsNodes().size)
        assertEquals("no Used in row by its words either", 0, composeRule.onAllNodesWithText("Used in").fetchSemanticsNodes().size)
    }

    @Test
    fun `an undated trip's card has no date row, and the rest as before`() {
        setScreen(SADDLE.copy(date = null))
        openCard()

        composeRule.onNodeWithTag(TITLE).assertTextEquals("Saddle loop")
        assertTrue("no date row", !fieldShown("trip-date"))
        assertEquals(0, composeRule.onAllNodesWithText("Trip date").fetchSemanticsNodes().size)
        field("mgrs").assertTextEquals("MGRS", gridOf(SADDLE.location))
        field("coordinates").assertTextEquals("Coordinates", SADDLE_COORDINATES)
    }

    @Test
    fun `Navigate and Directions sit side by side, Directions on the right, centred in the card`() {
        setScreen()
        openCard()
        composeRule.onNodeWithTag(DIRECTIONS).performScrollTo()

        assertEquals("Navigate", label(NAVIGATE))
        assertEquals("Directions", label(DIRECTIONS))
        val navigate = composeRule.onNodeWithTag(NAVIGATE).getUnclippedBoundsInRoot()
        val directions = composeRule.onNodeWithTag(DIRECTIONS).getUnclippedBoundsInRoot()
        val card = composeRule.onNodeWithTag(SHEET).getUnclippedBoundsInRoot()
        assertTrue("Directions to the right of Navigate: ${directions.left} vs ${navigate.right}", directions.left > navigate.right)
        assertEquals("one row", navigate.top.value, directions.top.value, 0.5f)
        val pairMiddle = (navigate.left + directions.right) / 2
        val cardMiddle = (card.left + card.right) / 2
        assertTrue("the pair is centred: its middle $pairMiddle, the card's $cardMiddle", abs((pairMiddle - cardMiddle).value) <= 1f)
        assertTrue("not at the left any more: Navigate starts at ${navigate.left}", navigate.left > card.left + 24.dp)
    }

    @Test
    fun `real touches across Navigate each start navigating to the trip, and the card closes`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            val where = "a touch at ($fx, $fy) on the card's Navigate"
            composeRule.runOnIdle { viewModel.onStopWaypointNavigation() }
            composeRule.waitForIdle()
            openCard()

            composeRule.touchAcrossTag(NAVIGATE, fx, fy)

            assertEquals(where, WaypointNavigation(SADDLE.id, resumesReturn = false, kind = NavigationTargetKind.TRIP), viewModel.uiState.value.waypointNavigation)
            assertTrue("$where: the card closes", !cardShowing())
        }
    }

    @Test
    fun `real touches across Directions each hand the trip to a maps app, and the card stays open`() {
        setScreen()
        registerMapsApp(composeRule.activity)
        openCard()
        ACROSS.forEach { (fx, fy) ->
            val where = "a touch at ($fx, $fy) on the card's Directions"

            composeRule.touchAcrossTag(DIRECTIONS, fx, fy)

            assertEquals(where, SADDLE_GEO, takeStartedGeo(composeRule.activity))
            assertTrue("$where: the card stays open (A12)", cardShowing())
            assertNull("$where: nothing is navigated to in the app", viewModel.uiState.value.waypointNavigation)
        }
    }

    @Test
    fun `over the map the card is at the map chrome's 80 percent`() {
        setScreen()
        openCard()

        val fill = composeRule.onNodeWithTag(SHEET).fetchSemanticsNode().config[MapChromeContainerColor]
        assertEquals(MAP_CHROME_OVER_MAP_ALPHA, fill.alpha, 0.001f)
    }

    private companion object {
        // Literals, so this compiles against the base it was written before.
        const val SHEET = "record-details-sheet"
        const val TITLE = "record-details-title"
        const val NAVIGATE = "record-details-navigate"
        const val DIRECTIONS = "record-details-directions"
    }
}
