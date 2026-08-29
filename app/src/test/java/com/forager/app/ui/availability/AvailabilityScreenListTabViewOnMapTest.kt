package com.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.AvailabilityEntry
import com.forager.app.domain.model.AvailabilityForecast
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.SpeciesObservationCount
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.ui.map.MapSlot
import java.time.LocalDate
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
 * "View on Map" on a List-tab species row: switches to the Maps tab (compact width, per this
 * file's own `w360dp` qualifier — see [AvailabilityScreenMapIconStackTest] for why the map
 * redesign this exercises is compact-only) and limits the map to that one species' own
 * [Sighting]s, driven by [AvailabilityScreen]'s own `mapTaxonFilter`/`onViewSpeciesOnMap` — see
 * that composable's doc comment on `mapTaxonFilter`. The filter is checked two ways: the actual
 * [com.forager.app.ui.map.MapOverlayContent.sightings] list [CapturingMapSlot] receives (the real
 * data the map would draw, not a proxy for it — CLAUDE.md), and the [TaxonMapFilterChip] banner
 * that says so on screen rather than silently showing fewer pins.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenListTabViewOnMapTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private var capturedSightings: List<Sighting>? = null

    private val CapturingMapSlot: MapSlot = { _, content, _, _, _, _, _, _, modifier ->
        capturedSightings = content.sightings
        Box(modifier.testTag("map-slot"))
    }

    private fun setScreen(uiState: AvailabilityUiState) {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = uiState,
                onUseCurrentLocation = {},
                onManualLatChanged = {},
                onManualLngChanged = {},
                onSearchManualCoordinates = {},
                onRadiusChanged = {},
                onMonthSelected = {},
                onMapTabSelected = {},
                onSeasonalTabSelected = {},
                onToggleForagingAreas = {},
                onCategorySelected = {},
                onTaxonSearchQueryChanged = {},
                onTaxonSearchResultSelected = {},
                onDismissTaxonSuggestions = {},
                onReopenTaxonSuggestions = {},
                onPlaceTripPin = { _, _, _ -> },
                onDeletePlannedTrip = {},
                onRecentSearchSelected = {},
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                onNightModeMapsChanged = {},
                onThemeModeChanged = {},
                mapSlot = CapturingMapSlot,
            )
        }
    }

    private fun openListTab() {
        composeRule.onNodeWithText("List").performClick()
    }

    @Test
    fun `tapping View on Map switches to the Maps tab and limits sightings to that species`() {
        setScreen(SEARCHED_STATE)

        openListTab()
        composeRule.onNodeWithText("View on Map").performClick()
        composeRule.waitForIdle()

        // The filter chip is only ever composed inside MapTab/CompactMapTab, so its presence on
        // screen is itself proof this switched compactTab to MAP, not a direct state check.
        composeRule.onNodeWithText("Showing: Pacific Golden Chanterelle (1)").assertIsDisplayed()
        assertEquals(listOf(MATCHING_SIGHTING), capturedSightings)
    }

    @Test
    fun `clearing the filter chip shows every sighting again`() {
        setScreen(SEARCHED_STATE)

        openListTab()
        composeRule.onNodeWithText("View on Map").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Show all species").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Showing: Pacific Golden Chanterelle (1)").assertDoesNotExist()
        assertEquals(listOf(MATCHING_SIGHTING, OTHER_SIGHTING), capturedSightings)
    }
}

private val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

private val SPECIES = SpeciesObservationCount(
    taxonId = 47348,
    scientificName = "Cantharellus formosus",
    commonName = "Pacific Golden Chanterelle",
    rank = "species",
    observationCount = 42,
    photoUrl = null,
    wikipediaUrl = null,
)

private val FORECAST = AvailabilityForecast(
    region = REGION,
    month = 8,
    filter = TaxonFilter.FUNGI,
    entries = listOf(AvailabilityEntry(species = SPECIES, relativeLikelihood = 1f)),
)

private val MATCHING_SIGHTING = Sighting(
    observationId = 1,
    taxonId = 47348,
    scientificName = "Cantharellus formosus",
    commonName = "Pacific Golden Chanterelle",
    lat = 45.33,
    lng = -122.64,
    observedOn = LocalDate.of(2026, 8, 1),
    photoUrl = null,
)

private val OTHER_SIGHTING = Sighting(
    observationId = 2,
    taxonId = 99999,
    scientificName = "Amanita muscaria",
    commonName = "Fly Agaric",
    lat = 45.34,
    lng = -122.65,
    observedOn = LocalDate.of(2026, 8, 2),
    photoUrl = null,
)

private val SEARCHED_STATE = AvailabilityUiState(
    region = REGION,
    forecast = FORECAST,
    sightings = listOf(MATCHING_SIGHTING, OTHER_SIGHTING),
)
