package com.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.ClusterForagingAreasUseCase
import com.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.forager.app.domain.ComputeTripWindowsUseCase
import com.forager.app.domain.DeletePlannedTripUseCase
import com.forager.app.domain.GetAvailabilityUseCase
import com.forager.app.domain.GetConditionsUseCase
import com.forager.app.domain.GetPlannedTripsUseCase
import com.forager.app.domain.GetRecentSearchesUseCase
import com.forager.app.domain.GetSeasonalPatternUseCase
import com.forager.app.domain.GetSightingsUseCase
import com.forager.app.domain.GetTripWindowsUseCase
import com.forager.app.domain.HistoricalWeatherProvider
import com.forager.app.domain.InMemorySearchCacheRepository
import com.forager.app.domain.LocationFix
import com.forager.app.domain.LocationProvider
import com.forager.app.domain.LocationResult
import com.forager.app.domain.LocationTracker
import com.forager.app.domain.MushroomRepository
import com.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.forager.app.domain.MapPreferencesRepository
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.PlannedTripRepository
import com.forager.app.domain.PredictAvailabilityUseCase
import com.forager.app.domain.SavePlannedTripUseCase
import com.forager.app.domain.SearchTaxaUseCase
import com.forager.app.domain.TripPlanningWeatherProvider
import com.forager.app.domain.WeatherProvider
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.SightingsPage
import com.forager.app.domain.model.SpeciesObservationCount
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import com.forager.app.domain.model.WeatherSeries
import com.forager.app.ui.map.MapSlot
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The Conditions card, end to end: the real [AvailabilityScreen] over the real
 * [AvailabilityViewModel], driven by real taps and typing.
 *
 * Two separate things have to hold for this card to behave, and they live in different places:
 *
 * 1. **It has to be on screen at all.** It shipped measured to zero height and nobody saw it for
 *    two builds. That is a layout property and is asserted here by [assertIsDisplayed], which
 *    fails both for a node that isn't in the tree and for one with no area on screen.
 * 2. **It is only meaningful for the current month.** Recent rainfall says nothing about "what's
 *    typical in November" when it is August, so [AvailabilityViewModel.refresh] clears
 *    [AvailabilityUiState.conditions] for any other month. The screen has no month logic of its
 *    own — it renders the card whenever the state carries conditions — so asserting the gate by
 *    handing the screen a hand-made state would only restate the screen's own `if`. Driving the
 *    real ViewModel through the drawer's real month dropdown exercises the gate that actually
 *    exists.
 *
 * The map is stubbed for the same reason as in [AvailabilityScreenLayoutTest]: composing the real
 * one starts osmdroid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenConditionsMonthTest {

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
     * The offline search cache is not what this file is about. An empty in-memory one keeps the
     * ViewModel's new dependency real — every search still writes through it — without changing
     * anything these tests assert; the cache's own behaviour is covered by
     * `RoomSearchCacheRepositoryTest` and `GetAvailabilityUseCaseTest`.
     */
    private val searchCache = InMemorySearchCacheRepository()

    private fun setScreen(weatherProvider: WeatherProvider = FakeWeatherProvider) {
        val viewModel = AvailabilityViewModel(
            locationProvider = UnusedLocationProvider,
            locationTracker = NoOpLocationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(FakeRepository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(FakeRepository),
            searchTaxa = SearchTaxaUseCase(FakeRepository),
            getConditions = GetConditionsUseCase(weatherProvider),
            clusterForagingAreas = ClusterForagingAreasUseCase(),
            getTripWindows = GetTripWindowsUseCase(FakeTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(FakePlannedTripRepository),
            savePlannedTrip = SavePlannedTripUseCase(FakePlannedTripRepository),
            deletePlannedTrip = DeletePlannedTripUseCase(FakePlannedTripRepository),
            getSeasonalPattern = GetSeasonalPatternUseCase(
                GetSightingsUseCase(FakeRepository),
                FakeHistoricalWeatherProvider,
                ComputeFruitingLagDistributionUseCase(),
            ),
            offlineMapRepository = FakeOfflineMapRepository,
            mapPreferencesRepository = FakeMapPreferencesRepository,
        )
        composeRule.setContent {
            // Wired exactly as MainActivity wires it, so these are the real entry points.
            val uiState by viewModel.uiState.collectAsState()
            AvailabilityScreen(
                uiState = uiState,
                onUseCurrentLocation = viewModel::useCurrentLocation,
                onManualLatChanged = viewModel::onManualLatChanged,
                onManualLngChanged = viewModel::onManualLngChanged,
                onSearchManualCoordinates = viewModel::searchManualCoordinates,
                onRadiusChanged = viewModel::onRadiusChanged,
                onMonthSelected = viewModel::onMonthSelected,
                onMapTabSelected = viewModel::onMapTabSelected,
                onSeasonalTabSelected = viewModel::onSeasonalTabSelected,
                onToggleForagingAreas = viewModel::onToggleForagingAreas,
                onCategorySelected = viewModel::onCategorySelected,
                onTaxonSearchQueryChanged = viewModel::onTaxonSearchQueryChanged,
                onTaxonSearchResultSelected = viewModel::onTaxonSearchResultSelected,
                onDismissTaxonSuggestions = viewModel::onDismissTaxonSuggestions,
                onReopenTaxonSuggestions = viewModel::onReopenTaxonSuggestions,
                onPlaceTripPin = viewModel::onPlaceTripPin,
                onDeletePlannedTrip = viewModel::onDeletePlannedTrip,
                onRecentSearchSelected = viewModel::onRecentSearchSelected,
                onOfflineMapLatChanged = viewModel::onOfflineMapLatChanged,
                onOfflineMapLngChanged = viewModel::onOfflineMapLngChanged,
                onOfflineMapRadiusChanged = viewModel::onOfflineMapRadiusChanged,
                onOfflineMapNameChanged = viewModel::onOfflineMapNameChanged,
                onOfflineMapsOpened = viewModel::onOfflineMapsOpened,
                onDownloadOfflineMaps = viewModel::onDownloadOfflineMaps,
                onDeleteOfflineRegion = viewModel::onDeleteOfflineRegion,
                onNightModeMapsChanged = viewModel::onNightModeMapsChanged,
                mapSlot = StubMapSlot,
            )
        }
    }

    /**
     * Opens the drawer via the icon stack's "Search" icon — [CompactMapTab] shows a real map (and
     * so the icon stack) from its very first composition, not only once a region has been searched,
     * so this works identically before and after the first search.
     *
     * That icon lives inside [CompactMapTab], which only composes while the Maps bottom-nav tab is
     * selected — this test also drives the List tab (to check the Conditions card), so this
     * switches back to Maps first rather than assuming it's already there.
     */
    private fun openDrawer() {
        composeRule.onNodeWithText("Maps").performClick()
        composeRule.onNodeWithContentDescription("Search").performClick()
    }

    /**
     * Opens the drawer and expands its "Advanced search" section, exactly as a user would tap it.
     * The drawer sheet stays composed (just animated off-screen) while closed, so the section's
     * own expand/collapse state survives a close-and-reopen — this only taps the header when the
     * section isn't already open, rather than blindly toggling it shut again.
     */
    private fun openDrawerToAdvancedSearch() {
        openDrawer()
        val alreadyExpanded = composeRule.onAllNodesWithText("Latitude").fetchSemanticsNodes().isNotEmpty()
        if (!alreadyExpanded) {
            composeRule.onNodeWithText("Advanced search").performClick()
        }
    }

    /** Types coordinates into the drawer and searches, exactly as a user would. */
    private fun searchAReferenceRegion() {
        openDrawerToAdvancedSearch()
        composeRule.onNodeWithText("Latitude").performScrollTo().performTextReplacement("45.326")
        composeRule.onNodeWithText("Longitude").performScrollTo().performTextReplacement("-122.634")
        // Closes the drawer itself, which is why nothing closes it here.
        composeRule.onNodeWithText("Search this location").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun selectMonth(month: Month) {
        openDrawerToAdvancedSearch()
        composeRule.onNodeWithText("Month").performScrollTo().performClick()
        composeRule.onNodeWithText(month.getDisplayName(TextStyle.FULL, Locale.getDefault()))
            .performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `the conditions card is on screen for the current month`() {
        setScreen()

        searchAReferenceRegion()
        composeRule.onNodeWithText("List").performClick()

        composeRule.onNodeWithText("Current Conditions").assertIsDisplayed()
        composeRule.onNodeWithText("12.4mm of rain in the last 14 days").assertIsDisplayed()
        composeRule.onNodeWithText("2 days since last rain.").assertIsDisplayed()
    }

    @Test
    fun `the conditions card is gone once a month other than this one is selected`() {
        setScreen()

        searchAReferenceRegion()
        composeRule.onNodeWithText("List").performClick()
        composeRule.onNodeWithText("Current Conditions").assertIsDisplayed()

        selectMonth(aMonthOtherThanThisOne())

        composeRule.onNodeWithText("Current Conditions").assertDoesNotExist()
        composeRule.onNodeWithText("12.4mm of rain in the last 14 days").assertDoesNotExist()
    }

    /** January, unless it is January now, in which case February. */
    private fun aMonthOtherThanThisOne(): Month {
        val now = LocalDate.now().monthValue
        return Month.of(if (now == 1) 2 else 1)
    }

    /**
     * [AvailabilityUiState.conditionsErrorMessage]'s neutral, non-belief-changing empty state — see
     * docs/error-presentation-spec.md's per-field table and [ConditionsCard]'s own doc comment. The
     * card's own "Current Conditions" heading stays (same branch order [TripWindowsCard] uses for
     * its own error case), only the body swaps from rain figures to the fixed unavailable text.
     */
    @Test
    fun `a failed rainfall fetch shows the neutral unavailable text, not the exception's own message`() {
        setScreen(weatherProvider = FailingWeatherProvider)

        searchAReferenceRegion()
        composeRule.onNodeWithText("List").performClick()

        composeRule.onNodeWithText("Current Conditions").assertIsDisplayed()
        composeRule.onNodeWithText("Rainfall data unavailable.").assertIsDisplayed()
        composeRule.onNodeWithText("archive api down for this fetch", substring = true).assertDoesNotExist()
    }

    @Test
    fun `with a successful rainfall fetch, no unavailable text appears`() {
        setScreen()

        searchAReferenceRegion()
        composeRule.onNodeWithText("List").performClick()

        composeRule.onNodeWithText("Rainfall data unavailable.").assertDoesNotExist()
    }
}

private val StubMapSlot: MapSlot = { _, _, _, _, _, _, _, modifier -> Box(modifier.testTag("map-slot")) }

/** The coordinate path is what this test drives, so the device-location path is never reached. */
private object NoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object UnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult =
        error("getCurrentLocation() is not part of this test's path and must not be called")
}

private object FakeRepository : MushroomRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(
            listOf(
                SpeciesObservationCount(
                    taxonId = 48473L,
                    scientificName = "Ganoderma applanatum",
                    commonName = "artist's bracket",
                    rank = "species",
                    observationCount = 14,
                    photoUrl = null,
                    wikipediaUrl = null,
                ),
            ),
        )

    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))

    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

/**
 * Returns rainfall for any region. The figures are the ones the assertions name, so a card that
 * rendered stale or default values would fail rather than pass on the heading alone.
 */
private object FakeWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) = Result.success(
        ConditionsSummary(
            region = region,
            totalPrecipitationMm = 12.4,
            daysSinceSignificantRain = 2,
        ),
    )
}

/**
 * The exception's own message is a recognizable, distinctive string on purpose — the regression
 * guard for [AvailabilityScreenConditionsMonthTest]'s failed-fetch test needs something that would
 * be unmistakable if it leaked into the rendered text, the same shape as
 * `AvailabilityViewModelOfflineCacheTest`'s own DNS-failure regression guard.
 */
private object FailingWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.failure<ConditionsSummary>(IllegalStateException("archive api down for this fetch"))
}

/** Not exercised by this test's assertions, so a failure is the honest, low-effort stand-in. */
private object FakeTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

/** Not exercised by this test's assertions, so a failure is the honest, low-effort stand-in. */
private object FakeHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

/** Not exercised by this test's assertions; empty rather than failing, since it loads on every ViewModel init. */
private object FakePlannedTripRepository : PlannedTripRepository {
    override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(emptyList())
    override suspend fun save(trip: PlannedTrip): Result<Unit> =
        Result.failure(UnsupportedOperationException("planned trips not exercised by this test"))
    override suspend fun delete(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("planned trips not exercised by this test"))
}

/** Not exercised by this test's assertions; listRegions() succeeds with an empty list since it runs on every ViewModel init. */
private object FakeOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

private object FakeMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
}
