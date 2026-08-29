package com.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.CachedSearchSummary
import com.forager.app.domain.MutableClock
import com.forager.app.domain.model.AvailabilityEntry
import com.forager.app.domain.model.AvailabilityForecast
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.SpeciesObservationCount
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.ui.map.MapSlot
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
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
 * The offline cache's two pieces of UI, asserted to be **on screen** — not merely present in the
 * ViewModel's state.
 *
 * Both exist to tell the user something the results themselves cannot: that the ranking in front
 * of them came out of storage rather than off the network, and that there are saved searches to go
 * back to. A banner that is in the tree with no area on screen tells nobody anything — this
 * project has shipped exactly that before (the Conditions card, invisible for two builds; see
 * [AvailabilityScreenLayoutTest]) — so every assertion here is [assertIsDisplayed] on the real
 * rendered text.
 *
 * The clock is a [MutableClock] fixed at [NOW], which is the whole reason [AvailabilityScreen]
 * takes a [com.forager.app.domain.CurrentTimeProvider] at all: "saved 3 hours ago" is only
 * assertable text if the test decides what "now" is.
 *
 * The map is stubbed for the same reason as in [AvailabilityScreenLayoutTest]: composing the real
 * one starts osmdroid's tile threads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenOfflineCacheTest {

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

    private val selectedRecentSearches = mutableListOf<CachedSearchSummary>()

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
                onRecentSearchSelected = { selectedRecentSearches += it },
                currentTime = MutableClock(now = NOW),
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                onNightModeMapsChanged = {},
                onThemeModeChanged = {},
                mapSlot = StubMapSlot,
            )
        }
    }

    @Test
    fun `the offline banner is displayed on the list tab when results came from the cache`() {
        setScreen(
            CACHED_STATE.copy(
                isShowingCachedResults = true,
                cachedResultsAsOfEpochMillis = NOW - THREE_HOURS_MILLIS,
            ),
        )

        composeRule.onNodeWithText("List").performClick()

        composeRule.onNodeWithText("Offline — showing results saved 3 hours ago").assertIsDisplayed()
        composeRule.onNodeWithText(
            "iNaturalist couldn't be reached, so this is the last ranking saved for this region, " +
                "month and category.",
        ).assertIsDisplayed()
        // The ranking itself is still shown — the banner labels the results, it does not replace
        // them.
        composeRule.onNodeWithText("artist's bracket").assertIsDisplayed()
    }

    /** The other half of the gate: a live result must not carry the banner. */
    @Test
    fun `the offline banner is absent when the results are live`() {
        setScreen(CACHED_STATE)

        composeRule.onNodeWithText("List").performClick()

        composeRule.onNodeWithText("Offline — showing results saved 3 hours ago").assertDoesNotExist()
        composeRule.onNodeWithText("artist's bracket").assertIsDisplayed()
    }

    /** A younger cached copy has to read as one, so the age is rendered rather than boilerplate. */
    @Test
    fun `the offline banner reports the age of the cached results it is labelling`() {
        setScreen(
            CACHED_STATE.copy(
                isShowingCachedResults = true,
                cachedResultsAsOfEpochMillis = NOW - MINUTE_MILLIS,
            ),
        )

        composeRule.onNodeWithText("List").performClick()

        composeRule.onNodeWithText("Offline — showing results saved 1 minute ago").assertIsDisplayed()
    }

    @Test
    fun `the recent searches picker lists its entries on screen`() {
        setScreen(CACHED_STATE.copy(recentSearches = listOf(RECENT_FUNGI, RECENT_PLANTS)))

        composeRule.onNodeWithText("Tools").performClick()
        composeRule.onNodeWithText("Recent searches").performClick()

        composeRule.onNodeWithText("Fungi · ${monthName(8)}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("45.3260, -122.6340 · 15 km").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("cached 3 hours ago").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Plants · ${monthName(10)}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("47.6060, -122.3320 · 25 km").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("cached 2 days ago").performScrollTo().assertIsDisplayed()
    }

    /** An empty picker says so rather than expanding to blank space. */
    @Test
    fun `the recent searches picker states when nothing is saved yet`() {
        setScreen(CACHED_STATE.copy(recentSearches = emptyList()))

        composeRule.onNodeWithText("Tools").performClick()
        composeRule.onNodeWithText("Recent searches").performClick()

        composeRule.onNodeWithText(
            "No searches saved yet. Each search you run is saved here, and the last five can be " +
                "reopened without a connection.",
        ).performScrollTo().assertIsDisplayed()
    }

    /**
     * Tapping an entry has to hand back the entry that was tapped — the picker's entire job — and
     * close the drawer, since the search it starts renders behind the sheet.
     */
    @Test
    fun `tapping a recent search reports that entry and closes the drawer`() {
        setScreen(CACHED_STATE.copy(recentSearches = listOf(RECENT_FUNGI, RECENT_PLANTS)))
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.onNodeWithText("Recent searches").performClick()

        composeRule.onNodeWithText("Plants · ${monthName(10)}").performScrollTo().performClick()

        assertEquals(listOf(RECENT_PLANTS), selectedRecentSearches)
        composeRule.onNodeWithText("Fungi · ${monthName(8)}").assertIsNotDisplayed()
    }

    private companion object {
        /**
         * The month label the screen renders, computed the same way it does rather than hardcoded
         * in English — the same reason [AvailabilityScreenConditionsMonthTest] computes it.
         */
        fun monthName(month: Int): String =
            Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())

        /** Fixed "now". Nothing here reads the wall clock, so every rendered age is stated by the test. */
        const val NOW = 1_700_000_000_000L
        const val MINUTE_MILLIS = 60_000L
        const val THREE_HOURS_MILLIS = 3 * 60 * MINUTE_MILLIS
        const val TWO_DAYS_MILLIS = 2 * 24 * 60 * MINUTE_MILLIS

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

        /**
         * A searched region showing a ranking; the cached-results flags are what each test varies.
         * [DistanceUnit.KILOMETERS] fixed explicitly — this file's own assertions are hardcoded to
         * "15 km" text and have nothing to do with the km/mi preference, so it must not drift with
         * [AvailabilityUiState.distanceUnit]'s own default.
         */
        val CACHED_STATE = AvailabilityUiState(region = REGION, selectedMonth = 8, forecast = FORECAST, distanceUnit = DistanceUnit.KILOMETERS)

        val RECENT_FUNGI = CachedSearchSummary(
            region = REGION,
            month = 8,
            filter = TaxonFilter.FUNGI,
            cachedAtEpochMillis = NOW - THREE_HOURS_MILLIS,
        )

        /** A different region from [RECENT_FUNGI]'s, so each row's coordinate line names one row. */
        val OTHER_REGION = Region(lat = 47.606, lng = -122.332, radiusKm = 25)

        val RECENT_PLANTS = CachedSearchSummary(
            region = OTHER_REGION,
            month = 10,
            filter = TaxonFilter.PLANTS,
            cachedAtEpochMillis = NOW - TWO_DAYS_MILLIS,
        )
    }
}

/** Stands in for the real map; see [AvailabilityScreenLayoutTest]'s own stub for why. */
private val StubMapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier ->
    Box(modifier.testTag("offline-cache-map-slot"))
}
