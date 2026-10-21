package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.ui.map.SearchFrameMove
import com.zynergylabs.forager.app.ui.map.searchFrameMove
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
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
 * RECORD -750 (dispatch 2026-09-28-750), items 3 and 5, through the real [AvailabilityScreen] and the real
 * [AvailabilityViewModel] ([RealSearchScreenRig]), every touch real.
 *
 * Item 3, the owner: "simply reset the search panel back to default and clear the name". Clear puts the species selection
 * back to a fresh start's default and the bar's summary to its default text; recent searches stay.
 *
 * Item 5, the owner: "Yes, fly to the search (Recommended)". Every location search hands the map a new search-frame request
 * for the searched region, which the real map applies by ending GPS following and framing the region
 * ([searchFrameMove], tested here too). A real MapLibre MapView cannot run under Robolectric, so what the camera then draws
 * is a device item; this tests what the screen hands the map and the rule the map applies.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class SearchClearAndFrameTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule)

    private val chanterelle = RealSearchScreenRig.CHANTERELLE

    private fun defaultSummary(): String {
        val month = LocalDate.now().month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        return "$month · Search a location"
    }

    private fun summaryShown(text: String, substring: Boolean = false) =
        composeRule.onAllNodes(androidx.compose.ui.test.hasText(text, substring = substring), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun pickChanterelle() {
        rig.tapBar()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG, useUnmergedTree = true).performTextInput("chant")
        rig.settle()
        composeRule.onNodeWithText(chanterelle.commonName!!).performTouchInput { click(center) }
        rig.settle()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        rig.settle()
    }

    // ── Item 3 ──

    @Test
    fun `Clear puts the species back to the default and the summary to its default text, keeping recent searches`() {
        rig.setScreen()
        assertTrue("positive control: a fresh start reads <${defaultSummary()}>", summaryShown(defaultSummary()))
        pickChanterelle()
        rig.searchCoordinates("45.326", "-122.634")
        assertEquals("positive control: the species is selected", chanterelle.toFilter(), rig.viewModel.uiState.value.taxonFilter)
        assertTrue("positive control: the bar names the species", summaryShown("${chanterelle.commonName} · ${defaultSummary().substringBefore(" · ")}", substring = true))
        val recentBefore = rig.viewModel.uiState.value.recentSearches
        assertTrue("positive control: the search is a recent search", recentBefore.isNotEmpty())

        rig.touch(composeRule.onNodeWithTag(SEARCH_BAR_CLEAR_TAG))

        val state = rig.viewModel.uiState.value
        assertEquals("the selection is a fresh start's default", AvailabilityUiState().taxonFilter, state.taxonFilter)
        assertEquals("which is Fungi", TaxonFilter.FUNGI, state.taxonFilter)
        assertEquals("its weather group is the default's", AvailabilityUiState().foragingSelection, state.foragingSelection)
        assertEquals("the field's text is empty", "", state.taxonSearchQuery)
        assertEquals("the query a suggestion came from is gone", "", state.lastTaxonSearchQuery)
        assertTrue("the bar reads its default <${defaultSummary()}>", summaryShown(defaultSummary()))
        assertEquals("the recent searches are untouched", recentBefore, state.recentSearches)
    }

    /**
     * RECORD -1525, the owner: "Clear removes it too (Recommended)". The map's "Showing: <species> (n)" chip, set by "View on
     * Map" on a List-tab row, goes with the search bar's Clear; before, it stayed, reading "Showing: this species (0)".
     */
    @Test
    fun `Clear also removes the map's View on Map chip`() {
        rig.setScreen()
        rig.searchCoordinates("45.326", "-122.634")
        rig.touch(composeRule.onNodeWithText("List"))
        rig.settle()
        rig.touch(composeRule.onNodeWithText("View on Map"))
        rig.settle()
        assertTrue(
            "positive control: View on Map shows the chip",
            composeRule.onAllNodes(hasTestTag(MAP_TAXON_FILTER_CHIP_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
        )

        rig.touch(composeRule.onNodeWithTag(SEARCH_BAR_CLEAR_TAG))
        rig.settle()

        assertTrue(
            "Clear removes the chip",
            composeRule.onAllNodes(hasTestTag(MAP_TAXON_FILTER_CHIP_TAG), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
        )
        assertTrue("and no \"Showing:\" text is left", !summaryShown("Showing:", substring = true))
    }

    // ── Item 5 ──

    private val near = Region(45.326, -122.634, 8)
    private val far = Region(-41.2865, 174.7762, 8)

    private fun touchRecentRow(coordinates: String) {
        rig.tapBar()
        composeRule.onNodeWithText("Recent searches").performScrollTo()
        rig.touch(composeRule.onNodeWithText("Recent searches"))
        composeRule.onNodeWithText(coordinates, substring = true).performScrollTo()
        composeRule.waitForIdle()
        rig.touch(composeRule.onNodeWithText(coordinates, substring = true))
    }

    @Test
    fun `a recent search far away hands the map its region and a new frame request, which ends following and frames it`() {
        rig.setScreen()
        rig.searchCoordinates("-41.2865", "174.7762")
        rig.searchCoordinates("45.326", "-122.634")
        assertEquals("positive control: the map shows the near search", near, rig.mapRegion)
        val before = rig.mapSearchFrameRequestId

        touchRecentRow("-41.2865, 174.7762")

        assertEquals("the map is handed the far region", far, rig.mapRegion)
        assertEquals("the map is handed a new frame request", before + 1, rig.mapSearchFrameRequestId)
        assertEquals(
            "the map, following the GPS fix, stops following and frames it",
            SearchFrameMove.STOP_FOLLOWING_AND_FRAME,
            searchFrameMove(isGpsTracking = true, requestId = rig.mapSearchFrameRequestId, lastAppliedId = before),
        )
    }

    @Test
    fun `each location search asks for a frame, a month change does not, and locate resumes following`() {
        rig.setScreen()
        val start = rig.mapSearchFrameRequestId
        rig.searchCoordinates("45.326", "-122.634")
        assertEquals("coordinates asked for a frame", start + 1, rig.mapSearchFrameRequestId)

        // Search, the bottom row's current-location search.
        rig.location = LocationResult.Success(-41.2865, 174.7762)
        rig.tapBar()
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_SEARCH_TAG).performScrollTo()
        rig.touch(composeRule.onNodeWithTag(SEARCH_DROPDOWN_SEARCH_TAG))
        assertEquals("positive control: Search ran", far.copy(radiusKm = rig.viewModel.uiState.value.radiusKm), rig.mapRegion)
        assertEquals("Search asked for a frame", start + 2, rig.mapSearchFrameRequestId)

        // Set on map: the picker's confirm runs the coordinates search.
        rig.tapBar()
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_SET_ON_MAP_TAG).performScrollTo()
        rig.touch(composeRule.onNodeWithTag(SEARCH_DROPDOWN_SET_ON_MAP_TAG))
        rig.touch(composeRule.onNodeWithText("OK"))
        assertEquals("Set on map asked for a frame", start + 3, rig.mapSearchFrameRequestId)

        // A month change searches the same place again and asks for no frame.
        val serial = rig.viewModel.uiState.value.searchSerial
        composeRule.runOnUiThread { rig.viewModel.onMonthSelected(if (rig.viewModel.uiState.value.selectedMonth == 1) 2 else 1) }
        rig.settle()
        assertEquals("positive control: the month change searched again", serial + 1, rig.viewModel.uiState.value.searchSerial)
        assertEquals("a month change asked for no frame", start + 3, rig.mapSearchFrameRequestId)

        // Locate: a real touch on the button resumes following.
        val resume = rig.mapResumeTrackingRequestId
        rig.touch(composeRule.onNodeWithContentDescription("Center on my location"))
        assertEquals("locate resumes following", resume + 1, rig.mapResumeTrackingRequestId)
    }

    /**
     * RECORD -1525: a search on the Maps tab puts the sightings spinner in place of the map, and the map that comes back must
     * not take a locate-me from before the search as its pan target, which panned the searched map away to the GPS fix on
     * the S22 (2026-10-21, frames 5 and 6). The locate-me target (`focusOverride`) is held above the spinner, so returning
     * from it does not replay an earlier Located status.
     */
    @Test
    fun `a locate from before a search is not the pan target of the map that returns from the spinner`() {
        rig.setScreen()
        rig.searchCoordinates("45.326", "-122.634")
        rig.settle()
        // Stands in for MainActivity's permission-granted call: the rig's locate button is not wired past the Activity's gate.
        composeRule.runOnUiThread { rig.viewModel.locateMe() }
        rig.settle()
        assertEquals("positive control: a locate after a search is the map's pan target", LatLng(45.0, -122.0), rig.mapFocusOverride)

        val gate = CompletableDeferred<Unit>()
        rig.sightingsGate = gate
        rig.searchCoordinates("-41.2865", "174.7762")
        rig.settle()
        assertTrue(
            "positive control: the spinner has replaced the map",
            composeRule.onAllNodes(hasTestTag(LAYOUT_FIXES_MAP_TAG), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
        )
        rig.sightingsGate = null
        gate.complete(Unit)
        rig.settle()

        assertEquals("positive control: the map is back, on the new search", far, rig.mapRegion)
        assertNull("the earlier locate is not replayed as the new search's pan target", rig.mapFocusOverride)
    }

    @Test
    fun `the map applies a frame request once per id, following or not`() {
        assertEquals(SearchFrameMove.NONE, searchFrameMove(isGpsTracking = true, requestId = 0, lastAppliedId = 0))
        assertEquals(SearchFrameMove.NONE, searchFrameMove(isGpsTracking = true, requestId = 3, lastAppliedId = 3))
        assertEquals(SearchFrameMove.STOP_FOLLOWING_AND_FRAME, searchFrameMove(isGpsTracking = true, requestId = 4, lastAppliedId = 3))
        assertEquals(SearchFrameMove.FRAME, searchFrameMove(isGpsTracking = false, requestId = 4, lastAppliedId = 3))
    }
}

private const val MAP_TAXON_FILTER_CHIP_TAG = "map-taxon-filter-chip"
