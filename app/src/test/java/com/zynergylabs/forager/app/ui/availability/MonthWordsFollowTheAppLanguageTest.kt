package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CachedSearchSummary
import com.zynergylabs.forager.app.domain.MutableClock
import com.zynergylabs.forager.app.domain.model.AvailabilityEntry
import com.zynergylabs.forager.app.domain.model.AvailabilityForecast
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import org.junit.After
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
 * Lint dispatch 2026-10-17-03, Phase 2 (RECORD -1195), NonObservableLocale: month names are written in the language
 * the screen's configuration carries (`LocalConfiguration.current.locales`), which Compose observes, rather than the
 * process-wide `Locale.getDefault()`, which it does not. That is what lets the words follow a language change while the
 * app is open.
 *
 * Each test composes the real [AvailabilityScreen] with the app's configuration in French (this class's qualifiers)
 * while the JVM's default locale is set back to English, and reads one of the four month sites lint named in
 * `AvailabilityResultsUi.kt` and `AvailabilitySearchUi.kt`. Before the fix each shows the English month.
 *
 * The French comes from the qualifiers, not from a `CompositionLocalProvider(LocalConfiguration …)` around the screen:
 * the Month menu is a popup, and a popup's own view provides its own `LocalConfiguration` (Compose UI 1.12,
 * `ComposeViewContext.ProvideCompositionLocals`), so an override from outside would never reach it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "fr-rFR-w360dp-h640dp-xhdpi")
class MonthWordsFollowTheAppLanguageTest {

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

    private val originalDefault: Locale = Locale.getDefault()

    @After
    fun restoreDefaultLocale() {
        Locale.setDefault(originalDefault)
    }

    private fun setScreen(uiState: AvailabilityUiState) {
        Locale.setDefault(Locale.US)
        var configurationLanguage = ""
        composeRule.setContent {
            configurationLanguage = LocalConfiguration.current.locales[0].language
            run {
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
                    currentTime = MutableClock(now = NOW),
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
        composeRule.waitForIdle()
        assertEquals("the configuration says French", "fr", configurationLanguage)
        assertEquals("the JVM default says English, so only the configuration can say French", "en", Locale.getDefault().language)
    }

    /**
     * Counts both languages before asserting either, so a failure says which language was on screen: `(0, 1)` is the
     * English month in place of the French one, the reason these tests exist; `(0, 0)` would be a harness problem (the
     * text not reached at all), which is a different failure and not evidence for the fix.
     */
    private fun assertFrenchNotEnglish(french: SemanticsMatcher, english: SemanticsMatcher) {
        val shown = composeRule.onAllNodes(french).fetchSemanticsNodes().size to composeRule.onAllNodes(english).fetchSemanticsNodes().size
        assertEquals("(French, English) nodes: the French month once, the English nowhere", 1 to 0, shown)
    }

    private fun openSearchPanel() {
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performClick()
        composeRule.waitForIdle()
    }

    /** `AvailabilityResultsUi.kt`, the List tab's "Based on … for <month>." line. */
    @Test
    fun `the List tab's forecast line names the month in the configuration's language`() {
        setScreen(STATE)
        composeRule.onNodeWithText("List").performClick()
        composeRule.waitForIdle()

        assertFrenchNotEnglish(hasText("for ${french(8)}.", substring = true), hasText("for ${english(8)}.", substring = true))
        composeRule.onNodeWithText("for ${french(8)}.", substring = true).assertIsDisplayed()
    }

    /** `AvailabilitySearchUi.kt`, `RecentSearchRow`: "<filter> · <month>". */
    @Test
    fun `a recent search names its month in the configuration's language`() {
        setScreen(STATE.copy(recentSearches = listOf(RECENT_PLANTS)))
        openSearchPanel()
        composeRule.onNodeWithText("Recent searches").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertFrenchNotEnglish(hasText("Plants · ${french(10)}"), hasText("Plants · ${english(10)}"))
        composeRule.onNodeWithText("Plants · ${french(10)}").performScrollTo().assertIsDisplayed()
    }

    /** `AvailabilitySearchUi.kt`, `MonthSelector`: the selected month shown in the Month field. */
    @Test
    fun `the Month field shows the selected month in the configuration's language`() {
        setScreen(STATE)
        openSearchPanel()

        val inPanel = hasAnyAncestor(hasTestTag(SEARCH_DROPDOWN_TAG))
        assertFrenchNotEnglish(hasText(french(8)) and inPanel, hasText(english(8)) and inPanel)
        composeRule.onNode(hasText(french(8)) and inPanel).performScrollTo().assertIsDisplayed()
    }

    /** `AvailabilitySearchUi.kt`, `MonthSelector`'s menu: the twelve choices. */
    @Test
    fun `the Month menu lists the months in the configuration's language`() {
        setScreen(STATE)
        openSearchPanel()
        composeRule.onNodeWithText("Month").performScrollTo().performClick()
        composeRule.waitForIdle()

        val inMenu = hasAnyAncestor(hasTestTag(MONTH_MENU_TAG))
        for (month in listOf(1, 12)) {
            assertFrenchNotEnglish(hasText(french(month)) and inMenu, hasText(english(month)) and inMenu)
        }
    }

    private companion object {
        fun french(month: Int): String = Month.of(month).getDisplayName(TextStyle.FULL, Locale.FRANCE)
        fun english(month: Int): String = Month.of(month).getDisplayName(TextStyle.FULL, Locale.US)

        const val NOW = 1_700_000_000_000L

        val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

        val FORECAST = AvailabilityForecast(
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

        val STATE = AvailabilityUiState(region = REGION, selectedMonth = 8, forecast = FORECAST, unitSystem = UnitSystem.METRIC)

        val RECENT_PLANTS = CachedSearchSummary(
            region = Region(lat = 47.606, lng = -122.332, radiusKm = 25),
            month = 10,
            filter = TaxonFilter.PLANTS,
            cachedAtEpochMillis = NOW - 3_600_000L,
        )
    }
}

/** Stands in for the real map; see [AvailabilityScreenLayoutTest]'s own stub for why. */
private val StubMapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier ->
    Box(modifier.testTag("month-words-map-slot"))
}
