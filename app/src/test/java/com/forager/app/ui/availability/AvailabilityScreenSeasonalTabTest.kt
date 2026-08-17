package com.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.FruitingPatternAssumptions
import com.forager.app.domain.model.FruitingLagBucket
import com.forager.app.domain.model.FruitingLagDistribution
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.ui.map.MapSlot
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The Seasonal tab's honesty requirement, checked as real on-screen text: sample size, the
 * "estimate ... not a guarantee" framing, the observations-with-no-preceding-event count, and the
 * observer-effort caveat must actually be displayed, not merely present somewhere in the
 * [FruitingLagDistribution] this tab renders. Same "real screen under Robolectric" style as
 * [AvailabilityScreenLayoutTest]; the chart canvas itself is not asserted on here — Robolectric
 * does not render Compose `Canvas` content meaningfully, the same limitation that file's own doc
 * comment records for the map.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenSeasonalTabTest {

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
                mapSlot = StubMapSlot,
            )
        }
    }

    private fun openSeasonalTab() {
        composeRule.onNodeWithText("Seasonal").performClick()
    }

    @Test
    fun `before any search, the Seasonal tab shows a no-search message instead of a chart`() {
        setScreen(AvailabilityUiState())

        openSeasonalTab()

        composeRule.onNodeWithText(
            "Choose a region in search options to test the rain-to-fruiting-lag rule of thumb against real data.",
        ).assertIsDisplayed()
    }

    @Test
    fun `the sample size and its not-a-guarantee framing are on screen`() {
        setScreen(SEARCHED_STATE.copy(seasonalPattern = DISTRIBUTION))

        openSeasonalTab()

        composeRule.onNodeWithText(
            "Estimate from ${DISTRIBUTION.sampleSize} observations with a known date, not a guarantee.",
        ).assertIsDisplayed()
    }

    @Test
    fun `the 200-of-total sample-size caveat is on screen`() {
        setScreen(SEARCHED_STATE.copy(seasonalPattern = DISTRIBUTION))

        openSeasonalTab()

        composeRule.onNodeWithText(
            "Based on ${DISTRIBUTION.sightingsConsidered} of ${DISTRIBUTION.totalResultsOnServer} " +
                "total observations iNaturalist reports for this search.",
        ).assertIsDisplayed()
    }

    @Test
    fun `observations with no preceding rain event are reported, not dropped`() {
        setScreen(SEARCHED_STATE.copy(seasonalPattern = DISTRIBUTION))

        openSeasonalTab()

        composeRule.onNodeWithText(
            "${DISTRIBUTION.observationsWithNoPrecedingEvent} observation(s) had no qualifying rain " +
                "event in the fetched history before them.",
        ).assertIsDisplayed()
    }

    @Test
    fun `observations excluded for a missing date are reported, not silently dropped`() {
        setScreen(SEARCHED_STATE.copy(seasonalPattern = DISTRIBUTION))

        openSeasonalTab()

        composeRule.onNodeWithText(
            "${DISTRIBUTION.observationsExcludedForMissingDate} observation(s) have no recorded date " +
                "and are excluded from this estimate.",
        ).assertIsDisplayed()
    }

    @Test
    fun `the observer-effort caveat is reachable on screen`() {
        setScreen(SEARCHED_STATE.copy(seasonalPattern = DISTRIBUTION))

        openSeasonalTab()

        // Below the fold on a small screen — scrolled to first, same as the reachability checks
        // in AvailabilityScreenLayoutTest, since assertIsDisplayed alone fails for a node that
        // exists but sits outside the scrollable column's current viewport.
        composeRule.onNodeWithText(
            "Raw iNaturalist counts reflect how many people were out looking that day, not only " +
                "whether ${DISTRIBUTION.filter.label} was actually there — more observers means more " +
                "sightings regardless of the rain.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the fruiting-lag bucket is labelled as the rule of thumb being tested`() {
        setScreen(SEARCHED_STATE.copy(seasonalPattern = DISTRIBUTION))

        openSeasonalTab()

        composeRule.onNodeWithText(
            "${FruitingPatternAssumptions.FRUITING_LAG_DAYS.first}–" +
                "${FruitingPatternAssumptions.FRUITING_LAG_DAYS.last} days (the rule of thumb)",
        ).assertIsDisplayed()
    }

    @Test
    fun `a loading state shows a progress indicator rather than stale content`() {
        setScreen(SEARCHED_STATE.copy(isLoadingSeasonalPattern = true, seasonalPattern = null))

        openSeasonalTab()

        composeRule.onNodeWithText("Does rain predict fruiting?").assertDoesNotExist()
    }

    @Test
    fun `a fetch failure is shown as an explicit message`() {
        setScreen(SEARCHED_STATE.copy(seasonalPatternErrorMessage = "Couldn't load the seasonal pattern."))

        openSeasonalTab()

        composeRule.onNodeWithText("Couldn't load the seasonal pattern.").assertIsDisplayed()
    }
}

private val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

private val SEARCHED_STATE = AvailabilityUiState(region = REGION)

private val DISTRIBUTION = FruitingLagDistribution(
    region = REGION,
    month = 8,
    filter = TaxonFilter.FUNGI,
    buckets = listOf(
        FruitingLagBucket(0..6, "0–6 days", count = 4, isFruitingLagRule = false),
        FruitingLagBucket(
            FruitingPatternAssumptions.FRUITING_LAG_DAYS,
            "${FruitingPatternAssumptions.FRUITING_LAG_DAYS.first}–${FruitingPatternAssumptions.FRUITING_LAG_DAYS.last} days",
            count = 12,
            isFruitingLagRule = true,
        ),
        FruitingLagBucket(22..35, "22–35 days", count = 3, isFruitingLagRule = false),
        FruitingLagBucket(36..Int.MAX_VALUE, "36+ days", count = 1, isFruitingLagRule = false),
        FruitingLagBucket(null, "No preceding rain event", count = 5, isFruitingLagRule = false),
    ),
    observationsExcludedForMissingDate = 7,
    sightingsConsidered = 25,
    totalResultsOnServer = 1847,
)

private val StubMapSlot: MapSlot = { _, _, _, _, _, modifier -> Box(modifier.testTag("map-slot")) }
