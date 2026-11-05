package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.WaypointNavigation
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.log.RecordType
import com.zynergylabs.forager.app.ui.log.RecordsSubTab
import com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag
import com.zynergylabs.forager.app.ui.log.swipeToDeleteTag
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_DETAILS_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-05-05, A5 (RECORD -2140, the owner: "In the Details card add the Directions button for external maps to
 * the right of the in-app Navigate button and center the two buttons"): the waypoint's card, wherever it opens with
 * Navigate (the Maps tab's bubble, Records, and a journal entry's map), has Directions to Navigate's right, the two centred;
 * Directions hands the waypoint to a maps app, and the card stays open (A12). Where the card has no Navigate it has no
 * Directions either (C1): RecordDetailsSheetTest's sheets without Navigate pin that.
 *
 * Through the real [AvailabilityScreen] and [AvailabilityViewModel]; every touch on a control is a real one, sampled at five
 * points across its own bounds ([ACROSS]). Getting to a tab may be a semantic click.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class WaypointCardDirectionsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val waypoint = BUBBLE_WAYPOINT
    private var map = BubbleMapSlot(listOf(StubGlyph(MapLayerIds.WAYPOINTS, waypoint.id, 60.dp, 380.dp, LatLng(waypoint.lat, waypoint.lng))))
    private var cartography by mutableStateOf(CartographyUiState())
    private lateinit var viewModel: AvailabilityViewModel

    private fun setScreen(entryMapData: CartographyEntryMapData? = null) {
        viewModel = mapLayersViewModel()
        composeRule.setContent {
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(waypoint),
                cartographyUiState = cartography,
                getCartographyEntryMapData = { _, _ -> entryMapData ?: CartographyEntryMapData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()) },
                navigation = AvailabilityScreenNavigation(
                    onNavigateToWaypoint = viewModel::onNavigateToWaypoint,
                    onNavigateToTrip = viewModel::onNavigateToTrip,
                    onStopWaypointNavigation = viewModel::onStopWaypointNavigation,
                ),
            )
        }
        composeRule.waitForIdle()
        registerMapsApp(composeRule.activity)
    }

    private fun cardShowing() = composeRule.onAllNodesWithTag(SHEET).fetchSemanticsNodes().isNotEmpty()
    private fun label(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    private fun openFromMapsTab() {
        composeRule.onNodeWithTag(glyphTag(waypoint.id)).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MAP_BUBBLE_DETAILS_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun openFromRecords() {
        composeRule.onNodeWithText("Journal").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Records").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.WAYPOINTS)).performScrollTo().performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.WAYPOINTS, waypoint.id)).performScrollTo().performTouchInput { click(Offset(width * 0.2f, height * 0.5f)) }
        composeRule.waitForIdle()
    }

    /** Sets the screen with a saved entry keeping the waypoint, open on the Journal with its map, and opens its card there. */
    private fun setEntryScreenAndOpen() {
        val entry = CartographyEntry.draft(id = "entry-1", date = BUBBLE_DAY, updatedAtEpochMillis = 1_000L).copy(
            isDraft = false,
            text = "Along the creek.",
            waypointDecisions = listOf(WaypointDecision(waypoint.id, waypoint.name, waypoint.lat, waypoint.lng, kept = true)),
        )
        cartography = CartographyUiState(entries = listOf(entry), editingEntry = entry)
        map = BubbleMapSlot(listOf(StubGlyph(MapLayerIds.WAYPOINTS, waypoint.id, 60.dp, 120.dp, LatLng(waypoint.lat, waypoint.lng))))
        setScreen(CartographyEntryMapData(emptyList(), emptyList(), listOf(RecordPoint(waypoint.id, LatLng(waypoint.lat, waypoint.lng))), emptyList(), emptyList()))
        composeRule.onNodeWithText("Journal").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Along the creek.").assertExists()
        openFromMapsTab()
    }

    /** Navigate then Directions, on one row, centred in the card. */
    private fun assertCentredPair(where: String) {
        assertTrue("$where: the card is open", cardShowing())
        composeRule.onNodeWithTag(DIRECTIONS).performScrollTo()
        assertEquals("Navigate", label(NAVIGATE))
        assertEquals("Directions", label(DIRECTIONS))
        val navigate = composeRule.onNodeWithTag(NAVIGATE).getUnclippedBoundsInRoot()
        val directions = composeRule.onNodeWithTag(DIRECTIONS).getUnclippedBoundsInRoot()
        val card = composeRule.onNodeWithTag(SHEET).getUnclippedBoundsInRoot()
        assertTrue("$where: Directions to the right of Navigate (${directions.left} vs ${navigate.right})", directions.left > navigate.right)
        assertEquals("$where: one row", navigate.top.value, directions.top.value, 0.5f)
        val pairMiddle = (navigate.left + directions.right) / 2
        val cardMiddle = (card.left + card.right) / 2
        assertTrue("$where: centred, the pair's middle $pairMiddle and the card's $cardMiddle", abs((pairMiddle - cardMiddle).value) <= 1f)
    }

    /** Real touches across Directions each hand the waypoint on, and the card stays open; nothing is navigated to in the app. */
    private fun assertDirectionsHandsOn(where: String) {
        ACROSS.forEach { (fx, fy) ->
            composeRule.touchAcrossTag(DIRECTIONS, fx, fy)
            assertEquals("$where, a touch at ($fx, $fy)", "geo:0,0?q=45.326,-122.634(Creek%20pin)", takeStartedGeo(composeRule.activity))
            assertTrue("$where, a touch at ($fx, $fy): the card stays open", cardShowing())
            assertEquals("$where: nothing navigated to in the app", null, viewModel.uiState.value.waypointNavigation)
        }
    }

    @Test
    fun `on the Maps tab the waypoint's card has Directions to the right of Navigate, centred, and Directions hands it on`() {
        setScreen()
        openFromMapsTab()

        assertCentredPair("Maps tab")
        assertDirectionsHandsOn("Maps tab")
    }

    @Test
    fun `in Records the waypoint's card has Directions to the right of Navigate, centred, and Directions hands it on`() {
        setScreen()
        openFromRecords()

        assertCentredPair("Records")
        assertDirectionsHandsOn("Records")
    }

    @Test
    fun `on a journal entry's map the waypoint's card has Directions to the right of Navigate, centred, and Directions hands it on`() {
        setEntryScreenAndOpen()

        assertCentredPair("entry map")
        assertDirectionsHandsOn("entry map")
    }

    @Test
    fun `the card's Navigate, beside Directions now, still navigates to the waypoint at every touch across it`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            composeRule.runOnIdle { viewModel.onStopWaypointNavigation() }
            composeRule.waitForIdle()
            openFromMapsTab()

            composeRule.touchAcrossTag(NAVIGATE, fx, fy)

            assertEquals("a touch at ($fx, $fy)", WaypointNavigation(waypoint.id, resumesReturn = false), viewModel.uiState.value.waypointNavigation)
            assertTrue("a touch at ($fx, $fy): the card closes", !cardShowing())
        }
    }

    private companion object {
        // Literals, so this compiles against the base it was written before.
        const val SHEET = "record-details-sheet"
        const val NAVIGATE = "record-details-navigate"
        const val DIRECTIONS = "record-details-directions"
    }
}
