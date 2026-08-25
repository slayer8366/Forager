package com.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.ForagingArea
import com.forager.app.domain.model.ForagingAreas
import com.forager.app.domain.model.FruitingLagBucket
import com.forager.app.domain.model.FruitingLagDistribution
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.ui.map.MapSlot
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
     * "Settings" itself no longer works as this pin: it moved to the bottom nav (see
     * `AvailabilityScreen`'s `CompactTab` and `ForagerBottomNav`) as part of the map redesign, so
     * it's always on screen now regardless of drawer state. "Recent searches" — the drawer's own
     * first section header, still exclusively drawer content — replaces it as the "behind the
     * modal drawer" marker this test exists to pin.
     */
    @Test
    fun `the drawer's search controls are not shown until the drawer is opened`() {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = SEARCHED_STATE,
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
                mapSlot = StubMapSlot,
            )
        }

        composeRule.onNodeWithText("Recent searches").assertIsNotDisplayed()
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
                mapSlot = StubMapSlot,
            )
        }

        val bounds = composeRule
            .onNodeWithContentDescription("Showing topo mode. Switch to regular mode.")
            .getUnclippedBoundsInRoot()

        assertTrue("Width was ${bounds.width}, must be >= 48dp", bounds.width >= 48.dp)
        assertTrue("Height was ${bounds.height}, must be >= 48dp", bounds.height >= 48.dp)
    }
}

/**
 * A tablet-width window — past both the medium (600dp) and expanded (840dp) breakpoints, so this
 * also stands in for a medium-width phone-in-landscape/foldable window: [AvailabilityScreen]'s
 * `windowWidthClass` branch treats MEDIUM and EXPANDED identically today (see
 * `com.forager.app.ui.adaptive.WindowWidthClass`'s own doc comment on why the two collapse).
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
        logUiState: com.forager.app.ui.log.MushroomLogUiState = com.forager.app.ui.log.MushroomLogUiState(),
    ) {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = uiState,
                logUiState = logUiState,
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
                onPlaceTripPin = onPlaceTripPin,
                onDeletePlannedTrip = {},
                onRecentSearchSelected = {},
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                mapSlot = mapSlot,
            )
        }
    }

    /**
     * The M3 "swapped" pattern: `ModalNavigationDrawer` becomes a `PermanentNavigationDrawer` at
     * medium+ width, so its content is on screen from the start — no drawer-open tap needed, the
     * opposite of [AvailabilityScreenCompactWidthDrawerTest] above.
     */
    @Test
    fun `the drawer's Settings entry is shown without opening the drawer`() {
        setScreen(SEARCHED_STATE)

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    /**
     * Workstream G2 (`docs/plans/pr26-rework.md`): the gallery is a top-level destination on both
     * window classes, not just compact — see `PhotoGalleryScreen`'s own doc comment. This is the
     * medium/expanded half; `AvailabilityScreenBackNavigationTest`'s own "Album" tab test covers
     * the compact half.
     */
    @Test
    fun `the drawer's Photo Gallery entry is shown without opening the drawer, and opens the gallery`() {
        val photo = com.forager.app.domain.model.GalleryPhoto(
            photo = com.forager.app.domain.model.LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = null),
            referencingEntryIds = emptyList(),
        )
        setScreen(SEARCHED_STATE, logUiState = com.forager.app.ui.log.MushroomLogUiState(galleryPhotos = listOf(photo)))
        composeRule.onNodeWithText("Photo Gallery").assertIsDisplayed()

        composeRule.onNodeWithText("Photo Gallery").performClick()

        // Proves the real PhotoGalleryScreen is hosted here, with the real gallery state — not
        // just that a panel switched to some empty placeholder.
        composeRule.onNodeWithText("Date unknown").assertIsDisplayed()
    }

    /**
     * The M3 "reveal" pattern: List and Map show together rather than one tab at a time. Proven
     * by asserting on content unique to each — [ConditionsCard]'s "Current Conditions" for List,
     * the foraging-areas toggle for Map — displayed simultaneously with no tab click in between.
     */
    @Test
    fun `list and map content are both displayed together without switching tabs`() {
        setScreen(SEARCHED_STATE.copy(conditions = CONDITIONS, selectedMonth = LocalDate.now().monthValue))

        composeRule.onNodeWithText("Current Conditions").assertIsDisplayed()
        composeRule.onNodeWithText("Foraging areas").assertIsDisplayed()
    }

    /**
     * The M3 "readable content width" rule: Seasonal isn't part of [CombinedResultsPane] (see
     * that composable's call site in [AvailabilityScreen]), so at this window's ~840dp of content
     * width it would otherwise stretch a paragraph edge to edge. [SEASONAL_CONTENT_TAG] measures
     * the actual capped column rather than trusting the constant alone — a regression here would
     * be the cap silently not applying, not just the number changing.
     */
    /**
     * `MapTab` (medium/expanded, shared for both window classes — see this class's own doc
     * comment) has no icon stack for a tile to grow out of, so its own add button (added alongside
     * this rework — see `MapTab`'s doc comment in `AvailabilityScreen.kt`) opens
     * [ThreeWayActionDialog] rather than [AddActionTile]'s corner-anchored tile, unlike the compact
     * layout's icon stack — see `AvailabilityScreenMapIconStackTest`'s equivalent for that layout.
     */
    @Test
    fun `the add button opens the three-way chooser on the medium and expanded layout too`() {
        setScreen(SEARCHED_STATE)

        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").performClick()

        composeRule.onNodeWithText("What would you like to do here?").assertIsDisplayed()
        composeRule.onNodeWithText("Plan a trip").assertIsDisplayed()
        composeRule.onNodeWithText("Log a find").assertIsDisplayed()
        composeRule.onNodeWithText("Drop a waypoint").assertIsDisplayed()
    }

    /**
     * `MapTab`'s own coordinate path end to end: the add button opens [ThreeWayActionDialog],
     * choosing "Plan a trip" opens [com.forager.app.ui.map.CentrePinLocationPickerOverlay], a real
     * pan reported by [MapSlot.onCameraIdle] moves the pin, OK confirms it, and the real
     * [androidx.compose.material3.DatePickerDialog] confirmation hands that exact coordinate to
     * [onPlaceTripPin] — the medium/expanded counterpart to
     * [AvailabilityScreenTripPlanningFlowTest]'s compact-layout version of the same round trip.
     */
    @Test
    fun `MapTab's add button carries a panned location all the way to onPlaceTripPin`() {
        var placedAt: LatLng? = null
        setScreen(
            SEARCHED_STATE,
            onPlaceTripPin = { location, _, _ -> placedAt = location },
            mapSlot = TriggerableWideStubMapSlot,
        )

        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").performClick()
        composeRule.onNodeWithText("Plan a trip").performClick()
        composeRule.onNodeWithText("Simulate pan to test location").performClick()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Plan trip").performClick()

        assertEquals(WIDE_WINDOW_TEST_LOCATION, placedAt)
    }

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

private fun area(visitOrder: Int) = ForagingArea(
    visitOrder = visitOrder,
    center = LatLng(REGION.lat + visitOrder * 0.01, REGION.lng + visitOrder * 0.01),
    sightings = List(4) { sighting(visitOrder * 10 + it) },
    distinctSpeciesCount = 3,
    mostRecentYear = 2025,
    undatedObservationCount = 0,
)

private val SEARCHED_STATE = AvailabilityUiState(
    region = REGION,
    sightings = List(12) { sighting(it) },
    foragingAreas = ForagingAreas.Found(areas = List(3) { area(it + 1) }, ungroupedObservationCount = 5),
    showForagingAreas = true,
)

private val CONDITIONS = ConditionsSummary(
    region = REGION,
    totalPrecipitationMm = 12.4,
    daysSinceSignificantRain = 2,
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
)

/** Same stub as [AvailabilityScreenLayoutTest]'s — see that file for why the real map isn't used here. */
private val StubMapSlot: MapSlot = { _, _, _, _, _, _, _, modifier ->
    Box(modifier.testTag("map-slot"))
}

private val WIDE_WINDOW_TEST_LOCATION = LatLng(45.40, -122.70)

/** Exposes [MapSlot.onCameraIdle] as a button — same pattern [AvailabilityScreenTripPlanningFlowTest]'s `TriggerableMapSlot` uses. */
private val TriggerableWideStubMapSlot: MapSlot = { _, _, _, _, _, _, onCameraIdle, modifier ->
    Column(modifier.testTag("map-slot")) {
        Button(onClick = { onCameraIdle(WIDE_WINDOW_TEST_LOCATION) }) {
            Text("Simulate pan to test location")
        }
    }
}
