package com.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.ForagingArea
import com.forager.app.domain.model.ForagingAreas
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.ui.map.MapSlot
import java.time.LocalDate
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

    @Test
    fun `the drawer's Settings entry is not shown until the drawer is opened`() {
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
                onDownloadOfflineMaps = {},
                onDeleteOfflineMaps = {},
                mapSlot = StubMapSlot,
            )
        }

        composeRule.onNodeWithText("Settings").assertIsNotDisplayed()
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
                onDownloadOfflineMaps = {},
                onDeleteOfflineMaps = {},
                mapSlot = StubMapSlot,
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

/** Same stub as [AvailabilityScreenLayoutTest]'s — see that file for why the real map isn't used here. */
private val StubMapSlot: MapSlot = { _, _, _, _, _, _, modifier ->
    Box(modifier.testTag("map-slot"))
}
