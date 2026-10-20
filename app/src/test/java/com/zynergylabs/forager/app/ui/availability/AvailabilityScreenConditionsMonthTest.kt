package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.zynergylabs.forager.app.domain.ComputeTripWindowsUseCase
import com.zynergylabs.forager.app.domain.DeletePlannedTripUseCase
import com.zynergylabs.forager.app.domain.AppThemePreferenceRepository
import com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository
import com.zynergylabs.forager.app.domain.GetAvailabilityUseCase
import com.zynergylabs.forager.app.domain.GetConditionsUseCase
import com.zynergylabs.forager.app.domain.GetPlannedTripsUseCase
import com.zynergylabs.forager.app.domain.GetRecentSearchesUseCase
import com.zynergylabs.forager.app.domain.GetSeasonalPatternUseCase
import com.zynergylabs.forager.app.domain.GetSightingsUseCase
import com.zynergylabs.forager.app.domain.GetTodaysForecastUseCase
import com.zynergylabs.forager.app.domain.GetTripWindowsUseCase
import com.zynergylabs.forager.app.domain.HistoricalWeatherProvider
import com.zynergylabs.forager.app.domain.InMemorySearchCacheRepository
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationProvider
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.MushroomRepository
import com.zynergylabs.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.zynergylabs.forager.app.domain.MapPreferencesRepository
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.PlannedTripRepository
import com.zynergylabs.forager.app.domain.PredictAvailabilityUseCase
import com.zynergylabs.forager.app.domain.SavePlannedTripUseCase
import com.zynergylabs.forager.app.domain.SearchTaxaUseCase
import com.zynergylabs.forager.app.domain.TaxonSearchRepository
import com.zynergylabs.forager.app.domain.TripPlanningWeatherProvider
import com.zynergylabs.forager.app.domain.WeatherProvider
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.ConditionsSummary
import com.zynergylabs.forager.app.domain.model.DailyRain
import com.zynergylabs.forager.app.domain.model.DailyWeather
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.SightingsPage
import com.zynergylabs.forager.app.domain.model.SoilAvailability
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.domain.model.WeatherSeries
import com.zynergylabs.forager.app.ui.map.MapSlot
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
 * The card lives in the Seasonal tab (PANEL-CONTENTS-DISPATCH.md item 2 moved it out of List — see
 * [ConditionsCard]'s own doc comment), so every test here opens Seasonal, not List.
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

    private fun setScreen(
        weatherProvider: WeatherProvider = FakeWeatherProvider,
        tripPlanningWeatherProvider: TripPlanningWeatherProvider = FakeTripPlanningWeatherProvider,
    ) {
        val viewModel = AvailabilityViewModel(
            locationProvider = UnusedLocationProvider,
            locationTracker = NoOpLocationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(FakeRepository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(FakeRepository),
            searchTaxa = SearchTaxaUseCase(FakeRepository),
            getConditions = GetConditionsUseCase(weatherProvider),
            getTripWindows = GetTripWindowsUseCase(tripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
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
            unitSystemPreferenceRepository = FakeUnitSystemPreferenceRepository,
            appThemePreferenceRepository = FakeAppThemePreferenceRepository,
            getTodaysForecast = GetTodaysForecastUseCase(tripPlanningWeatherProvider),
        )
        composeRule.setContent {
            // Wired exactly as MainActivity wires it, so these are the real entry points.
            val uiState by viewModel.uiState.collectAsState()
            AvailabilityScreen(
                uiState = uiState,
                searchInputs = AvailabilityScreenSearchInputs(
                    onUseCurrentLocation = viewModel::useCurrentLocation,
                    onManualLatChanged = viewModel::onManualLatChanged,
                    onManualLngChanged = viewModel::onManualLngChanged,
                    onSearchManualCoordinates = viewModel::searchManualCoordinates,
                    onRadiusChanged = viewModel::onRadiusChanged,
                    onMonthSelected = viewModel::onMonthSelected,
                    onMapTabSelected = viewModel::onMapTabSelected,
                    onSeasonalTabSelected = viewModel::onSeasonalTabSelected,
                    onTaxonSearchQueryChanged = viewModel::onTaxonSearchQueryChanged,
                    onTaxonSearchResultSelected = viewModel::onTaxonSearchResultSelected,
                    onDismissTaxonSuggestions = viewModel::onDismissTaxonSuggestions,
                    onReopenTaxonSuggestions = viewModel::onReopenTaxonSuggestions,
                    onPlaceTripPin = viewModel::onPlaceTripPin,
                    onDeletePlannedTrip = viewModel::onDeletePlannedTrip,
                    onRecentSearchSelected = viewModel::onRecentSearchSelected,
                ),
                offlineMaps = AvailabilityScreenOfflineMaps(
                    onOfflineMapLatChanged = viewModel::onOfflineMapLatChanged,
                    onOfflineMapLngChanged = viewModel::onOfflineMapLngChanged,
                    onOfflineMapRadiusChanged = viewModel::onOfflineMapRadiusChanged,
                    onOfflineMapNameChanged = viewModel::onOfflineMapNameChanged,
                    onOfflineMapsOpened = viewModel::onOfflineMapsOpened,
                    onDownloadOfflineMaps = viewModel::onDownloadOfflineMaps,
                    onDeleteOfflineRegion = viewModel::onDeleteOfflineRegion,
                ),
                settings = AvailabilityScreenSettings(
                    onNightModeMapsChanged = viewModel::onNightModeMapsChanged,
                    onThemeModeChanged = viewModel::onThemeModeChanged,
                ),
                mapSlot = StubMapSlot,
            )
        }
    }

    /**
     * Opens [SearchDropdown] via the search summary bar — map/navigation redesign dispatch C, item
     * 1 moved location/radius/month out of the Tools drawer entirely, to float over the map from
     * where quick species search used to sit. The dropdown floats over whatever `compactTab` is
     * already showing, so switching to Maps first isn't strictly required for reachability — kept
     * anyway so this test's own tab state stays deterministic across the List-tab detour its own
     * assertions make.
     */
    private fun openSearchDropdown() {
        composeRule.onNodeWithText("Maps").performClick()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performClick()
    }

    /**
     * Opens the dropdown. Its Month and coordinates show with no fold to expand (dispatch
     * 2026-09-28-697 removed "Advanced search" and "Enter coordinates manually"), so no header is
     * tapped here; the two names are kept so the callers read as before.
     */
    private fun openSearchDropdownToAdvancedSearch() {
        openSearchDropdown()
    }

    /** Opens the dropdown to the manual coordinates, its first row since dispatch 2026-09-28-697. */
    private fun openSearchDropdownToManualCoordinates() {
        openSearchDropdownToAdvancedSearch()
    }

    /** Types coordinates into the dropdown and searches, exactly as a user would. */
    private fun searchAReferenceRegion() {
        openSearchDropdownToManualCoordinates()
        composeRule.onNodeWithText("Latitude").performTextReplacement("45.326")
        composeRule.onNodeWithText("Longitude").performTextReplacement("-122.634")
        // "Search coordinates", under the fields (RECORD -700), runs the typed-coordinates search.
        // performScrollTo() kept: harmless where it is already in view, and the tap would otherwise
        // land on off-screen bounds if it ever is not.
        // Closes the dropdown itself, which is why nothing closes it here.
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_SEARCH_COORDINATES_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun selectMonth(month: Month) {
        openSearchDropdownToAdvancedSearch()
        composeRule.onNodeWithText("Month").performClick()
        composeRule.onNodeWithText(month.getDisplayName(TextStyle.FULL, Locale.getDefault()))
            .performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `the conditions card is on screen for the current month`() {
        setScreen()

        searchAReferenceRegion()
        composeRule.onNodeWithText("Seasonal").performClick()

        composeRule.onNodeWithText("Current Conditions").assertIsDisplayed()
        // Data part C (dispatch -668): labelled rows, each one merged node. 2 days before yesterday,
        // the newest observed day, is 3 days ago.
        composeRule.onNode(hasText("Rain, last 14 days") and hasText("12.4mm")).assertIsDisplayed()
        composeRule.onNode(hasText("Last rainy day") and hasText("3 days ago")).assertIsDisplayed()
    }

    /**
     * Data part C (dispatch -668): the 14 observed days the provider returns reach the screen as the
     * daily rain chart, through the real ViewModel. The chart's canvas is not rendered under
     * Robolectric, so its content description, built from the same days, is what is read; its x-axis
     * ends are real text.
     */
    @Test
    fun `the daily rain chart shows the observed days, from the first to the last`() {
        setScreen()

        searchAReferenceRegion()
        composeRule.onNodeWithText("Seasonal").performClick()

        composeRule.onNodeWithText("Daily rain, last 14 days").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Daily rain from Sep 23, 2026 to Oct 6, 2026. Wettest day Oct 1, 2026, 6.0mm.",
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sep 23, 2026").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Oct 6, 2026").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the conditions card is gone once a month other than this one is selected`() {
        setScreen()

        searchAReferenceRegion()
        composeRule.onNodeWithText("Seasonal").performClick()
        composeRule.onNodeWithText("Current Conditions").assertIsDisplayed()

        selectMonth(aMonthOtherThanThisOne())

        composeRule.onNodeWithText("Current Conditions").assertDoesNotExist()
        composeRule.onNodeWithText("Rain, last 14 days").assertDoesNotExist()
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
        composeRule.onNodeWithText("Seasonal").performClick()

        composeRule.onNodeWithText("Current Conditions").assertIsDisplayed()
        composeRule.onNodeWithText("Rainfall data unavailable.").assertIsDisplayed()
        composeRule.onNodeWithText("archive api down for this fetch", substring = true).assertDoesNotExist()
    }

    @Test
    fun `with a successful rainfall fetch, no unavailable text appears`() {
        setScreen()

        searchAReferenceRegion()
        composeRule.onNodeWithText("Seasonal").performClick()

        composeRule.onNodeWithText("Rainfall data unavailable.").assertDoesNotExist()
    }

    /**
     * [AvailabilityUiState.todaysForecast] — the card's other half, alongside [ConditionsSummary]'s
     * observed rainfall. Fetched from [TripPlanningWeatherProvider.getWeatherSeries], a different
     * provider method from [WeatherProvider.getRecentPrecipitation] — see
     * [com.zynergylabs.forager.app.domain.GetTodaysForecastUseCase]'s own doc comment.
     */
    @Test
    fun `today's forecast shows the reference day's own forecast rainfall`() {
        setScreen(tripPlanningWeatherProvider = FakeSuccessfulTripPlanningWeatherProvider)

        searchAReferenceRegion()
        composeRule.onNodeWithText("Seasonal").performClick()

        // Data part C: the "Today's Forecast" heading became this labelled row.
        composeRule.onNode(hasText("Rain forecast today") and hasText("3.2mm")).assertIsDisplayed()
    }

    /**
     * Amendment 1 to -668 (RECORD -669): today's soil moisture is a row of the conditions table, on
     * the plain scale. 0.27 m³/m³ is Moist (Dry below 0.15, Wet from 0.30). Driven from the forecast
     * provider through the real ViewModel; the scale reads to TalkBack as one phrase, which is what
     * is asserted.
     */
    @Test
    fun `today's soil moisture shows on the plain scale in the conditions table`() {
        setScreen(tripPlanningWeatherProvider = FakeSuccessfulTripPlanningWeatherProvider)

        searchAReferenceRegion()
        composeRule.onNodeWithText("Seasonal").performClick()

        composeRule.onNode(hasText("Soil moisture") and hasContentDescription("Moist, 0.27 m³/m³")).performScrollTo().assertIsDisplayed()
    }

    /** No reading from the forecast, no row: the default provider here fails the forecast fetch. */
    @Test
    fun `with no soil moisture reading there is no soil moisture row`() {
        setScreen()

        searchAReferenceRegion()
        composeRule.onNodeWithText("Seasonal").performClick()

        composeRule.onNodeWithText("Soil moisture").assertDoesNotExist()
    }

    @Test
    fun `a failed forecast fetch shows the neutral unavailable text`() {
        setScreen()

        searchAReferenceRegion()
        composeRule.onNodeWithText("Seasonal").performClick()

        composeRule.onNode(hasText("Rain forecast today") and hasText("Forecast unavailable.")).assertIsDisplayed()
    }
}

private val StubMapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag("map-slot")) }

/** The coordinate path is what this test drives, so the device-location path is never reached. */
private object NoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object UnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult =
        error("getCurrentLocation() is not part of this test's path and must not be called")
}

private object FakeRepository : MushroomRepository, TaxonSearchRepository {
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
            // 14 observed days, Sep 23 to Oct 6: 2.4mm on Sep 30, 6.0mm on Oct 1 and 4.0mm on Oct 4
            // (12.4mm in all; Oct 4 is the last day at or over the 2mm rain-day threshold, 2 days
            // before Oct 6, matching daysSinceSignificantRain above).
            dailyRain = (0..13).map { offset ->
                val date = LocalDate.of(2026, 9, 23).plusDays(offset.toLong())
                DailyRain(date, mapOf(7 to 2.4, 8 to 6.0, 11 to 4.0)[offset] ?: 0.0)
            },
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

/**
 * The default trip-planning/forecast provider for this file: most tests here are about the
 * rainfall half of the card, not the forecast half or the drawer's Trip Planner section, so a
 * failure is the honest, low-effort stand-in — exercised directly by
 * `a failed forecast fetch shows the neutral unavailable text`.
 */
private object FakeTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

/**
 * Returns a forecast for the reference day, for the one test that asserts on the forecast half of
 * the card. The precipitation figure is the one that test names, so a card rendering a stale or
 * default value would fail rather than pass on the heading alone — same reasoning as
 * [FakeWeatherProvider].
 */
private object FakeSuccessfulTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> {
        val referenceDay = LocalDate.now()
        return Result.success(
            WeatherSeries(
                region = region,
                referenceDay = referenceDay,
                days = listOf(
                    DailyWeather(
                        date = referenceDay,
                        isForecast = true,
                        precipitationMm = 3.2,
                        evapotranspirationMm = null,
                        shallowSoilMoistureM3M3 = 0.27,
                        deeperSoilMoistureM3M3 = null,
                        soilTemperatureMeanC = null,
                        soilTemperatureMinC = null,
                        soilTemperatureMaxC = null,
                    ),
                ),
                soilAvailability = SoilAvailability(
                    shallowMoistureBand = null,
                    deeperMoistureBand = null,
                    temperatureBand = null,
                ),
            ),
        )
    }
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
    override suspend fun getMapFullscreen(): Result<Boolean> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
}

/** Metric, explicitly: this file's assertions pin "12.4mm"/"3.2mm" and are about month gating, not units — the units question is [AvailabilityScreenLayoutTest]'s. */
private object FakeUnitSystemPreferenceRepository : UnitSystemPreferenceRepository {
    override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(UnitSystem.METRIC)
    override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
}

private object FakeAppThemePreferenceRepository : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}
