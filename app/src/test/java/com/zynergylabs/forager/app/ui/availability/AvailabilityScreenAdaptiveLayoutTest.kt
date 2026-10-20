package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.AvailabilityEntry
import com.zynergylabs.forager.app.domain.model.AvailabilityForecast
import com.zynergylabs.forager.app.domain.model.FruitingLagBucket
import com.zynergylabs.forager.app.domain.model.FruitingLagDistribution
import com.zynergylabs.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.centrePinFillPixel
import com.zynergylabs.forager.app.ui.theme.MapPalette
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proves the M3 adaptive-layout behavior added to [AvailabilityScreen]: a compact window keeps
 * the screen's original modal-drawer, one-pane-at-a-time behavior unchanged (every other test in
 * this package already covers that in detail — these two "unchanged" assertions exist only to
 * pin the compact/wide boundary itself), and a medium+ window instead shows the drawer's search
 * panel permanently and reveals List and Map together rather than behind a tab switch.
 *
 * Measured through the real Compose tree under Robolectric, same approach and same reasons as
 * [AvailabilityScreenLayoutTest] — see that file's doc comment for why a measured layout, not a
 * hand-computed one, is what these tests assert against.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenCompactWidthDrawerTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    /**
     * "Settings" itself no longer works as this pin: it's the drawer's own sticky entry row now
     * (map/navigation redesign dispatch B's `CompactToolsDrawerContent.showSettings`), so it
     * renders exactly whenever the rest of the drawer's content does, telling this test nothing
     * about open vs. closed on its own. "Recent searches" doesn't work either any more — dispatch C
     * moved it into `SearchDropdown`, over the map, not behind this drawer at all. "Trip Planner"
     * — the drawer's own first section header now — replaces both as the "behind the modal drawer"
     * marker this test exists to pin.
     */
    @Test
    fun `the drawer's search controls are not shown until the drawer is opened`() {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = SEARCHED_STATE,
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
                mapSlot = StubMapSlot,
            )
        }

        composeRule.onNodeWithText("Trip Planner").assertIsNotDisplayed()
    }

    /**
     * Regression test for a real miss this task's touch-target audit found: [MapModeToggle] used
     * to size itself off its icon plus padding (a 40dp circle) rather than M3's 48x48dp minimum —
     * see that composable's own doc comment. The Map tab is selected by default (no tab click
     * needed), so the toggle is on screen from the first composition.
     */
    @Test
    fun `the map mode toggle meets the 48dp minimum touch target`() {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = SEARCHED_STATE,
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
                mapSlot = StubMapSlot,
            )
        }

        val bounds = composeRule
            .onNodeWithContentDescription("Layers: Street map. Choose the map type and overlays.")
            .getUnclippedBoundsInRoot()

        assertTrue("Width was ${bounds.width}, must be >= 48dp", bounds.width >= 48.dp)
        assertTrue("Height was ${bounds.height}, must be >= 48dp", bounds.height >= 48.dp)
    }
}

/**
 * A tablet-width window — past both the medium (600dp) and expanded (840dp) breakpoints, so this
 * also stands in for a medium-width phone-in-landscape/foldable window: [AvailabilityScreen]'s
 * `windowWidthClass` branch treats MEDIUM and EXPANDED identically today (see
 * `com.zynergylabs.forager.app.ui.adaptive.WindowWidthClass`'s own doc comment on why the two collapse).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w840dp-h1024dp-mdpi")
class AvailabilityScreenWideWindowLayoutTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private fun setScreen(
        uiState: AvailabilityUiState,
        onPlaceTripPin: (LatLng, LocalDate, String) -> Unit = { _, _, _ -> },
        mapSlot: MapSlot = StubMapSlot,
        logUiState: com.zynergylabs.forager.app.ui.log.MushroomLogUiState = com.zynergylabs.forager.app.ui.log.MushroomLogUiState(),
    ) {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = uiState,
                findsLog = AvailabilityScreenFindsLog(
                    logUiState = logUiState,
                ),
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
                    onReopenTaxonSuggestions = {},
                    onPlaceTripPin = onPlaceTripPin,
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

    /**
     * The M3 "readable content width" rule: Seasonal isn't part of [CombinedResultsPane] (see
     * that composable's call site in [AvailabilityScreen]), so at this window's ~840dp of content
     * width it would otherwise stretch a paragraph edge to edge. [SEASONAL_CONTENT_TAG] measures
     * the actual capped column rather than trusting the constant alone — a regression here would
     * be the cap silently not applying, not just the number changing.
     */
    @Test
    fun `the seasonal tab's content is capped to a readable width, not stretched to the window`() {
        setScreen(SEARCHED_STATE.copy(seasonalPattern = DISTRIBUTION))

        composeRule.onNodeWithText("Seasonal").performClick()

        val content = composeRule.onNodeWithTag(SEASONAL_CONTENT_TAG).getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()

        assertTrue(
            "Seasonal content measured ${content.width} wide, root is ${root.width} — the cap " +
                "should keep it well short of the full window.",
            content.width < root.width,
        )
        assertTrue(
            "Seasonal content measured ${content.width}, must not exceed the 640dp readable cap",
            content.width <= 640.dp,
        )
    }
}

private val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

private fun sighting(index: Int) = Sighting(
    observationId = index.toLong(),
    taxonId = 48473L,
    scientificName = "Ganoderma applanatum",
    commonName = "artist's bracket",
    lat = REGION.lat + index * 0.001,
    lng = REGION.lng + index * 0.001,
    observedOn = LocalDate.of(2025, 8, 14),
    photoUrl = null,
)

private val SEARCHED_STATE = AvailabilityUiState(
    region = REGION,
    sightings = List(12) { sighting(it) },
)

/** A single ranked entry, so [ResultsSection] renders something List-unique to assert on. */
private val FORECAST = AvailabilityForecast(
    region = REGION,
    month = 8,
    filter = TaxonFilter.FUNGI,
    entries = listOf(
        AvailabilityEntry(
            species = SpeciesObservationCount(
                taxonId = 48473L,
                scientificName = "Ganoderma applanatum",
                commonName = "artist's bracket",
                rank = "species",
                observationCount = 14,
                photoUrl = null,
                wikipediaUrl = null,
            ),
            relativeLikelihood = 1.0f,
        ),
    ),
)

private val DISTRIBUTION = FruitingLagDistribution(
    region = REGION,
    month = 8,
    filter = TaxonFilter.FUNGI,
    buckets = listOf(
        FruitingLagBucket(0..6, "0–6 days", count = 4, isFruitingLagRule = false),
        FruitingLagBucket(7..21, "7–21 days", count = 12, isFruitingLagRule = true),
        FruitingLagBucket(22..35, "22–35 days", count = 3, isFruitingLagRule = false),
    ),
    observationsExcludedForMissingDate = 7,
    sightingsConsidered = 19,
    totalResultsOnServer = 200,
    // Data part C: the histogram the chart draws. This file measures the column's width, not the chart.
    histogram = ComputeFruitingLagDistributionUseCase.equalSpanHistogram(List(19) { it }, spanDays = 7, spanCount = 6),
)

/** Same stub as [AvailabilityScreenLayoutTest]'s — see that file for why the real map isn't used here. */
private val StubMapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier ->
    Box(modifier.testTag("map-slot"))
}

private val WIDE_WINDOW_TEST_LOCATION = LatLng(45.40, -122.70)

/** Exposes [MapSlot.onCameraIdle] as a button — same pattern [AvailabilityScreenTripPlanningFlowTest]'s `TriggerableMapSlot` uses. */
private val TriggerableWideStubMapSlot: MapSlot = { _, _, _, _, _, _, _, onCameraIdle, modifier ->
    Column(modifier.testTag("map-slot")) {
        Button(onClick = { onCameraIdle(WIDE_WINDOW_TEST_LOCATION) }) {
            Text("Simulate pan to test location")
        }
    }
}
