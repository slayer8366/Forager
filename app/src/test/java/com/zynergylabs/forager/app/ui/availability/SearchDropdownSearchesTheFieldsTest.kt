package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.ui.map.MapSlot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RECORD -700 (Amendment 2 to dispatch 2026-09-28-697): the search dropdown's "Search coordinates", the small button under
 * Latitude and Longitude ("Small button under the fields"), searches the coordinates in the fields: left as the fields hold
 * them, it searches those; typed over, it searches what was typed. The bottom row's Search reads no field: it is "Use current
 * location" renamed and moved (owner: "There are no fields to fill in").
 *
 * The screen is the real [AvailabilityScreen]; the coordinate fields' state is held here the way the ViewModel holds it
 * (`onManualLatChanged`/`onManualLngChanged` write it, the fields read it back), and `onSearchManualCoordinates` records the
 * fields as they stand when it is called — what the ViewModel's `searchManualCoordinates` reads. That function's parsing,
 * validation and error text are unchanged; the screen tests that drive the real ViewModel through this button (the
 * `searchAReferenceRegion` helpers, e.g. [AvailabilityScreenConditionsMonthTest]) carry the search the rest of the way.
 *
 * Every touch is real, at screen coordinates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class SearchDropdownSearchesTheFieldsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private var state by mutableStateOf(AvailabilityUiState())
    private val searched = mutableListOf<Pair<String, String>>()
    private var currentLocation = 0

    private val mapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag(LAYOUT_FIXES_MAP_TAG)) }


    private fun setScreen(initial: AvailabilityUiState) {
        state = initial
        composeRule.setContent { Screen() }
        composeRule.waitForIdle()
    }

    private fun openDropdown() {
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG, useUnmergedTree = true).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    /** A real touch on the node tagged [tag], at ([fx], [fy]) of its own bounds, at screen coordinates. */
    private fun touch(tag: String, fx: Float = 0.5f, fy: Float = 0.5f) {
        composeRule.onNodeWithTag(tag).performScrollTo()
        composeRule.waitForIdle()
        val b = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val at = Offset(b.left + b.width * fx, b.top + b.height * fy)
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(at) }
        composeRule.waitForIdle()
    }

    // One touch per test: this window is out of touch mode, where closing the dropdown hands focus back to the search field,
    // so a second tap on it opens nothing (RECORD -717; SearchDropdownReopenTest covers the reopen in touch mode). The touch
    // is sampled across the button in AvailabilityScreenLayoutTest, one sample per test.

    @Test
    fun `Search coordinates with the fields left as they are searches the coordinates they hold`() {
        setScreen(AvailabilityUiState(manualLatText = "45.5231", manualLngText = "-122.6765"))
        openDropdown()

        touch(SEARCH_DROPDOWN_SEARCH_COORDINATES_TAG)

        assertEquals(listOf("45.5231" to "-122.6765"), searched)
        assertEquals("never the current-location path", 0, currentLocation)
    }

    @Test
    fun `Search coordinates after typing over the fields searches what was typed`() {
        setScreen(AvailabilityUiState(manualLatText = "45.5231", manualLngText = "-122.6765"))
        openDropdown()

        composeRule.onNodeWithTag(SEARCH_DROPDOWN_LATITUDE_TAG).performTextReplacement("44.0521")
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_LONGITUDE_TAG).performTextReplacement("-123.0868")
        touch(SEARCH_DROPDOWN_SEARCH_COORDINATES_TAG)

        assertEquals(listOf("44.0521" to "-123.0868"), searched)
        assertEquals("never the current-location path", 0, currentLocation)
    }

    @Test
    fun `the bottom Search reads no field and runs the current-location path`() {
        setScreen(AvailabilityUiState(manualLatText = "45.5231", manualLngText = "-122.6765"))
        openDropdown()
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_LATITUDE_TAG).performTextReplacement("44.0521")

        touch(SEARCH_DROPDOWN_SEARCH_TAG)

        assertEquals("Search ran the current-location path", 1, currentLocation)
        assertEquals("and no typed-coordinates search", emptyList<Pair<String, String>>(), searched)
    }

    @Composable
    private fun Screen() {
        AvailabilityScreen(
            uiState = state,
            searchInputs = AvailabilityScreenSearchInputs(
                onUseCurrentLocation = { currentLocation++ },
                onManualLatChanged = { state = state.copy(manualLatText = it) },
                onManualLngChanged = { state = state.copy(manualLngText = it) },
                onSearchManualCoordinates = { searched += state.manualLatText to state.manualLngText },
                onRadiusChanged = {},
                onMonthSelected = {},
                onMapTabSelected = {},
                onSeasonalTabSelected = {},
                onTaxonSearchQueryChanged = {},
                onTaxonSearchResultSelected = {},
                onDismissTaxonSuggestions = {},
                onReopenTaxonSuggestions = {},
                onPlaceTripPin = { _, _, _ -> },
                onDeletePlannedTrip = {},
                onRecentSearchSelected = {},
            ),
            offlineMaps = AvailabilityScreenOfflineMaps(
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
            ),
            settings = AvailabilityScreenSettings(
                onNightModeMapsChanged = {},
                onThemeModeChanged = {},
            ),
            mapSlot = mapSlot,
        )
    }
}
