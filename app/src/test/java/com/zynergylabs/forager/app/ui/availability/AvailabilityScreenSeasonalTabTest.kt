package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.FruitingPatternAssumptions
import com.zynergylabs.forager.app.domain.model.FruitingLagBucket
import com.zynergylabs.forager.app.domain.model.FruitingLagDistribution
import com.zynergylabs.forager.app.domain.model.FruitingLagHistogram
import com.zynergylabs.forager.app.domain.model.LagSpan
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.ui.map.MapSlot
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

    /**
     * Data part C (dispatch -668): the chart's bars are equal week-long spans, and the chart says so
     * in words. The canvas is not rendered under Robolectric, so what is asserted is the drawing's
     * own content description, which [fruitingLagChart] builds from the same histogram the bars are
     * drawn from, plus the axis titles and the key, which are real text.
     */
    @Test
    fun `the fruiting-lag chart is in equal week spans, with its axes titled and the rule of thumb's shading keyed`() {
        setScreen(SEARCHED_STATE.copy(seasonalPattern = DISTRIBUTION))

        openSeasonalTab()

        composeRule.onNodeWithContentDescription(
            "Sightings by days after a soaking rain, in 7-day spans: 0–6 days, 4, 7–13 days, 7, " +
                "14–20 days, 5, 21–27 days, 2, 28–34 days, 1, 35–41 days, 0. Shaded: 7–21 days, the rule of thumb.",
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sightings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Days after a soaking rain").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Shaded: 7–21 days, the rule of thumb").performScrollTo().assertIsDisplayed()
    }

    /** The one lag past day 41 is said under the chart, not dropped from it silently. */
    @Test
    fun `a lag past the chart's last span is reported under it`() {
        setScreen(SEARCHED_STATE.copy(seasonalPattern = DISTRIBUTION))

        openSeasonalTab()

        composeRule.onNodeWithText(
            "1 observation(s) came more than 41 days after a soaking rain and are past the end of the chart.",
        ).performScrollTo().assertIsDisplayed()
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
    // The same 20 lags as the buckets above, in equal week spans (data part C): 4 in 0–6, 12 across
    // 7–13 and 14–20, 3 in 21–34, and the one 36+ lag past the chart's last span.
    histogram = FruitingLagHistogram(
        spanDays = 7,
        spans = listOf(
            LagSpan(0, 6, 4),
            LagSpan(7, 13, 7),
            LagSpan(14, 20, 5),
            LagSpan(21, 27, 2),
            LagSpan(28, 34, 1),
            LagSpan(35, 41, 0),
        ),
        beyondCount = 1,
    ),
)

private val StubMapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag("map-slot")) }
