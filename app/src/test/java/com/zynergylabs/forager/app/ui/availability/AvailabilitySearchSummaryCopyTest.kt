package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.theme.Spacing
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.map.MapSlot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The search bar's copy before any search (owner, 2026-09-28: "Change it to 'September · Search a
 * location'", so the line gives "a spark or motivation to action"), and, on the medium/expanded
 * layout, the owner's ruling that the summary's tap opens the search panel ("Make the tap open
 * search (Recommended)", planner message 2026-09-28-35) even before any search has run.
 */
private fun declareHostActivity() = object : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }
}

/** Nothing searched yet: no region, no species query; September, so the month is fixed. */
private val UNSEARCHED_SEPTEMBER = AvailabilityUiState(region = null, selectedMonth = 9)

private const val SEPTEMBER_SEARCH_A_LOCATION = "September · Search a location"

private val SummaryStubMapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag("map-slot")) }

private fun ComposeContentTestRule.setUnsearchedScreen(onReopenTaxonSuggestions: () -> Unit = {}) {
    setContent {
        AvailabilityScreen(
            uiState = UNSEARCHED_SEPTEMBER,
            searchInputs = AvailabilityScreenSearchInputs(
                onUseCurrentLocation = {},
                onManualLatChanged = {},
                onManualLngChanged = {},
                onSearchManualCoordinates = {},
                onRadiusChanged = {},
                onMonthSelected = {},
                onMapTabSelected = {},
                onSeasonalTabSelected = {},
                onTaxonSearchQueryChanged = {},
                onTaxonSearchResultSelected = {},
                onDismissTaxonSuggestions = {},
                onReopenTaxonSuggestions = onReopenTaxonSuggestions,
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
            mapSlot = SummaryStubMapSlot,
        )
    }
    waitForIdle()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class CompactSearchBarCopyTest {

    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity()).around(composeRule)

    @Test
    fun `before any search the compact search bar reads September, Search a location`() {
        composeRule.setUnsearchedScreen()

        composeRule.onNodeWithText(SEPTEMBER_SEARCH_A_LOCATION, useUnmergedTree = true).assertIsDisplayed()
    }
}

/**
 * Continuations 2026-09-28-39 and -40, as reordered by dispatch 2026-09-28-697: what a real touch on
 * the compact search bar shows. The coordinates show at once and first, with no "Advanced search" or
 * "Enter coordinates manually" fold (owner: "remove the drop down functions for the advanced
 * search"), and the dropdown opens at its top, not scrolled: the Latitude field's top is the panel's
 * own padding below the panel's top. "Search coordinates" shows under the fields (RECORD -700). "Use
 * current location" (renamed Search) and "Search this location" are gone as labels; Set on map and
 * Search are in the panel.
 */
private fun ComposeContentTestRule.assertCompactBarTapShowsCoordinatesFirst() {
    setUnsearchedScreen()
    onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performTouchInput { click(center) }
    waitForIdle()

    onNodeWithTag(SEARCH_DROPDOWN_TAG).assertIsDisplayed()
    onNodeWithText("Latitude").assertIsDisplayed()
    onNodeWithText("Longitude").assertIsDisplayed()
    val panelTop = onNodeWithTag(SEARCH_DROPDOWN_TAG).getUnclippedBoundsInRoot().top
    val latitudeTop = onNodeWithTag(SEARCH_DROPDOWN_LATITUDE_TAG).getUnclippedBoundsInRoot().top
    assertEquals("the dropdown opened at its top, not scrolled", Spacing.lg.value, (latitudeTop - panelTop).value, 0.5f)
    onNodeWithTag(SEARCH_DROPDOWN_SEARCH_COORDINATES_TAG).assertIsDisplayed()
    onNodeWithTag(SEARCH_DROPDOWN_SET_ON_MAP_TAG).assertExists()
    onNodeWithTag(SEARCH_DROPDOWN_SEARCH_TAG).assertExists()
    for (removed in listOf("Advanced search", "Enter coordinates manually", "Use current location", "Search this location")) {
        assertEquals("\"$removed\" is gone", 0, onAllNodesWithText(removed, substring = true).fetchSemanticsNodes().size)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class CompactSearchBarLocationControlsTest {

    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity()).around(composeRule)

    @Test
    fun `in portrait a real touch on the compact search bar shows the coordinates first, at the top, without scrolling`() {
        composeRule.assertCompactBarTapShowsCoordinatesFirst()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class CompactSearchBarLocationControlsShortLandscapeTest {

    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity()).around(composeRule)

    @Test
    fun `in short landscape a real touch on the compact search bar shows the coordinates first, at the top, without scrolling`() {
        composeRule.assertCompactBarTapShowsCoordinatesFirst()
    }
}
