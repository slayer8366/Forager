package com.zynergylabs.forager.app.ui.credits

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.domain.FruitingPatternAssumptions
import com.zynergylabs.forager.app.domain.model.ConditionsSummary
import com.zynergylabs.forager.app.domain.model.DailyRain
import com.zynergylabs.forager.app.domain.model.DailyWeather
import com.zynergylabs.forager.app.domain.model.FruitingLagBucket
import com.zynergylabs.forager.app.domain.model.FruitingLagDistribution
import com.zynergylabs.forager.app.domain.model.FruitingLagHistogram
import com.zynergylabs.forager.app.domain.model.LagSpan
import com.zynergylabs.forager.app.domain.model.RainEvent
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.SoilAvailability
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TripWindow
import com.zynergylabs.forager.app.domain.model.TripWindowReport
import com.zynergylabs.forager.app.ui.availability.AvailabilityUiState
import com.zynergylabs.forager.app.ui.availability.CONDITIONS_CARD_TAG
import com.zynergylabs.forager.app.ui.availability.SEASONAL_CONTENT_TAG
import com.zynergylabs.forager.app.ui.availability.SettingsPageHarness
import com.zynergylabs.forager.app.ui.availability.TRIP_WINDOWS_CARD_TAG
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * "Weather data by Open-Meteo.com", linked, beside every place Open-Meteo's figures are drawn (dispatch
 * 2026-10-13-05; their licence: "You must include a link next to any location Open-Meteo data are
 * displayed"). The places, traced from the code: the Seasonal tab, once, at the foot of its column (dispatch
 * 2026-10-18-01; until then its Current Conditions card and its fruiting-lag section each carried one), and
 * Tools > Trip Planner's Trip Windows card. Each is reached through the real screen, and "beside" is read as
 * geometry: exactly one credit inside that place's own bounds, below everything else in it. Where the place
 * shows no figure of theirs (a failed fetch, a load in progress), it carries no credit. The link is opened by
 * real touches across the credit, read as the Intent the app starts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class OpenMeteoCreditTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(SettingsPageHarness.declareHostActivity()).around(composeRule)

    // Dispatch 2026-10-18-01 (the owner, S22 check group 3: "open meteo is cited twice in the seasonal tab. Once at the bottom
    // is sufficient"): the Seasonal tab carries one credit, the last thing in its scrolling column, whenever any figure of
    // Open-Meteo's is on it. Until then the Current Conditions card and the fruiting-lag section each carried their own.

    @Test
    fun `Seasonal with conditions and the fruiting-lag pattern carries one credit, at the bottom, and it opens Open-Meteo`() {
        SettingsPageHarness.setScreen(composeRule, SEARCHED.copy(conditions = CONDITIONS, todaysForecast = TODAY, seasonalPattern = DISTRIBUTION))
        registerBrowser()
        composeRule.onNodeWithText("Seasonal").performClick()
        composeRule.waitForIdle()

        val credit = assertOneCreditAtTheBottomOfSeasonal()
        assertCreditOpensOpenMeteo(credit)
    }

    @Test
    fun `Seasonal with only the Current Conditions card carries one credit, at the bottom`() {
        SettingsPageHarness.setScreen(composeRule, SEARCHED.copy(conditions = CONDITIONS, todaysForecast = TODAY))
        composeRule.onNodeWithText("Seasonal").performClick()
        composeRule.waitForIdle()

        assertOneCreditAtTheBottomOfSeasonal()
    }

    @Test
    fun `Seasonal with only the fruiting-lag pattern carries one credit, at the bottom`() {
        SettingsPageHarness.setScreen(composeRule, SEARCHED.copy(seasonalPattern = DISTRIBUTION))
        composeRule.onNodeWithText("Seasonal").performClick()
        composeRule.waitForIdle()

        assertOneCreditAtTheBottomOfSeasonal()
    }

    @Test
    fun `a Current Conditions card whose fetches both failed shows no figure of theirs, and no credit`() {
        SettingsPageHarness.setScreen(
            composeRule,
            SEARCHED.copy(conditionsErrorMessage = "Couldn't load recent rain.", todaysForecastErrorMessage = "Couldn't load today's forecast."),
        )
        composeRule.onNodeWithText("Seasonal").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CONDITIONS_CARD_TAG).performScrollTo()
        assertEquals("credits on the Seasonal tab", 0, composeRule.onAllNodesWithTag(OPEN_METEO_CREDIT_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun `the Trip Windows card carries the credit under its windows, and it opens Open-Meteo`() {
        SettingsPageHarness.setScreen(composeRule, SEARCHED.copy(tripWindowReport = ONE_WINDOW))
        registerBrowser()
        openTripPlanner()

        val credit = assertOneCreditLastIn(TRIP_WINDOWS_CARD_TAG)
        assertCreditOpensOpenMeteo(credit)
    }

    @Test
    fun `a Trip Windows card still loading shows no credit`() {
        SettingsPageHarness.setScreen(composeRule, SEARCHED.copy(isLoadingTripWindows = true))
        openTripPlanner()

        composeRule.onNodeWithTag(TRIP_WINDOWS_CARD_TAG).performScrollTo()
        assertEquals("credits in the Tools drawer", 0, composeRule.onAllNodesWithTag(OPEN_METEO_CREDIT_TAG).fetchSemanticsNodes().size)
    }

    private fun openTripPlanner() {
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.onNodeWithText("Trip Planner").performClick()
        composeRule.waitForIdle()
    }

    /**
     * The one credit on the Seasonal tab: exactly one in the whole tree, inside the tab's scrolling column, with nothing else
     * in that column drawn below it (the fruiting-lag section's iNaturalist caveat included).
     */
    private fun assertOneCreditAtTheBottomOfSeasonal(): SemanticsNode {
        val all = composeRule.onAllNodesWithTag(OPEN_METEO_CREDIT_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("credits on the Seasonal tab", 1, all.size)
        composeRule.onNodeWithTag(OPEN_METEO_CREDIT_TAG).performScrollTo()
        val column = composeRule.onNodeWithTag(SEASONAL_CONTENT_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val credit = composeRule.onAllNodesWithTag(OPEN_METEO_CREDIT_TAG, useUnmergedTree = true).fetchSemanticsNodes().single()
        val inColumn = descendantsOf(column)
        assertTrue("the credit is in the Seasonal tab's column", inColumn.any { it.id == credit.id })
        assertEquals("the credit's words", listOf(OPEN_METEO_EXPECTED_TEXT), descendantsOf(credit).flatMap { it.texts() })
        val creditIds = (listOf(credit) + descendantsOf(credit)).map { it.id }.toSet()
        val others = inColumn.filter { it.id !in creditIds && it.boundsInRoot.height > 0f && it.texts().isNotEmpty() }
        assertTrue("the Seasonal tab has its figures", others.isNotEmpty())
        val lowest = others.maxBy { it.boundsInRoot.bottom }
        assertTrue(
            "the credit (top ${credit.boundsInRoot.top}) is under everything else on the tab (lowest ${lowest.texts()} at ${lowest.boundsInRoot.bottom})",
            credit.boundsInRoot.top >= lowest.boundsInRoot.bottom - 0.5f,
        )
        return credit
    }

    /** The one credit inside the node tagged [placeTag], and nothing else in that place drawn below it. */
    private fun assertOneCreditLastIn(placeTag: String): SemanticsNode {
        composeRule.onNodeWithTag(OPEN_METEO_CREDIT_TAG).performScrollTo()
        val place = composeRule.onNodeWithTag(placeTag, useUnmergedTree = true).fetchSemanticsNode()
        val credits = composeRule.onAllNodesWithTag(OPEN_METEO_CREDIT_TAG, useUnmergedTree = true).fetchSemanticsNodes()
            .filter { place.boundsInRoot.contains(it.boundsInRoot) }
        assertEquals("credits inside $placeTag", 1, credits.size)
        val credit = credits.single()
        assertEquals("the credit's words", listOf(OPEN_METEO_EXPECTED_TEXT), descendantsOf(credit).flatMap { it.texts() })
        val others = descendantsOf(place).filter { node -> node.id != credit.id && descendantsOf(credit).none { it.id == node.id } && node.boundsInRoot.height > 0f }
        assertTrue("$placeTag has its figures", others.any { it.texts().isNotEmpty() })
        val lowestOther = others.maxOf { it.boundsInRoot.bottom }
        assertTrue("the credit (top ${credit.boundsInRoot.top}) is under everything else in $placeTag (lowest $lowestOther)", credit.boundsInRoot.top >= lowestOther - 0.5f)
        return credit
    }

    /** Real touches at three points across the credit, each opening Open-Meteo's own site, as their licence links it. */
    private fun assertCreditOpensOpenMeteo(credit: SemanticsNode) {
        val bounds = credit.boundsInRoot
        listOf(0.05f, 0.5f, 0.95f).forEach { fraction ->
            touchAt(bounds, fraction)
            val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
            assertEquals("a touch at $fraction across the credit started a view", Intent.ACTION_VIEW, started?.action)
            assertEquals("a touch at $fraction across the credit opened Open-Meteo", "https://open-meteo.com/", started?.data.toString())
        }
    }

    private fun touchAt(bounds: Rect, fraction: Float) {
        composeRule.onRoot().performTouchInput { click(Offset(bounds.left + bounds.width * fraction, bounds.center.y)) }
        composeRule.waitForIdle()
    }

    private fun registerBrowser() {
        val activity = composeRule.activity
        val browser = ComponentName(activity, "com.example.browser.BrowserActivity")
        val packageManager = Shadows.shadowOf(activity.packageManager)
        packageManager.addActivityIfNotPresent(browser)
        packageManager.addIntentFilterForActivity(
            browser,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("https")
            },
        )
    }

    private fun Rect.contains(other: Rect): Boolean =
        other.left >= left - 0.5f && other.top >= top - 0.5f && other.right <= right + 0.5f && other.bottom <= bottom + 0.5f

    private fun descendantsOf(node: SemanticsNode): List<SemanticsNode> = node.children.flatMap { listOf(it) + descendantsOf(it) }

    private fun SemanticsNode.texts(): List<String> = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    private companion object {
        /** Open-Meteo's required wording, written out rather than read from the constant the app draws. */
        const val OPEN_METEO_EXPECTED_TEXT = "Weather data by Open-Meteo.com"

        val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)
        val SEARCHED = AvailabilityUiState(region = REGION)

        val CONDITIONS = ConditionsSummary(
            region = REGION,
            totalPrecipitationMm = 12.4,
            daysSinceSignificantRain = 2,
            dailyRain = (0 until 14).map { DailyRain(LocalDate.of(2025, 8, 1).plusDays(it.toLong()), if (it % 3 == 0) 4.0 else 0.0) },
        )

        val TODAY = DailyWeather(
            date = LocalDate.of(2025, 8, 15),
            isForecast = true,
            precipitationMm = 1.2,
            evapotranspirationMm = null,
            shallowSoilMoistureM3M3 = 0.27,
            deeperSoilMoistureM3M3 = null,
            soilTemperatureMeanC = null,
            soilTemperatureMinC = null,
            soilTemperatureMaxC = null,
        )

        val ONE_WINDOW = TripWindowReport(
            region = REGION,
            referenceDay = LocalDate.of(2025, 8, 14),
            horizonEnd = LocalDate.of(2025, 8, 21),
            rainEvents = emptyList(),
            windows = listOf(
                TripWindow(
                    startDate = LocalDate.of(2025, 8, 16),
                    endDate = LocalDate.of(2025, 8, 18),
                    precedingRainEvents = listOf(RainEvent(LocalDate.of(2025, 8, 5), LocalDate.of(2025, 8, 6), totalMm = 30.0, isForecast = false)),
                    daysAfterMostRecentRainAtStart = 10,
                    daysAfterMostRecentRainAtEnd = 12,
                    meanShallowSoilMoistureM3M3 = 0.27,
                    meanDeeperSoilMoistureM3M3 = null,
                    meanSoilTemperatureC = 11.3,
                    minSoilTemperatureC = null,
                    maxSoilTemperatureC = null,
                    evapotranspirationSinceRainMm = 9.0,
                    precipitationDuringWindowMm = 2.0,
                ),
            ),
            noWindowReason = null,
            soilAvailability = SoilAvailability(shallowMoistureBand = null, deeperMoistureBand = null, temperatureBand = null),
        )

        val DISTRIBUTION = FruitingLagDistribution(
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
            ),
            observationsExcludedForMissingDate = 0,
            sightingsConsidered = 20,
            totalResultsOnServer = 200,
            histogram = FruitingLagHistogram(
                spanDays = 7,
                spans = listOf(LagSpan(0, 6, 4), LagSpan(7, 13, 7), LagSpan(14, 20, 5), LagSpan(21, 27, 2), LagSpan(28, 34, 1), LagSpan(35, 41, 0)),
                beyondCount = 1,
            ),
        )
    }
}
