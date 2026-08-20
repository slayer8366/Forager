package com.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.ClusterForagingAreasUseCase
import com.forager.app.domain.CompassProvider
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
import com.forager.app.domain.LocationProvider
import com.forager.app.domain.LocationResult
import com.forager.app.domain.MushroomRepository
import com.forager.app.domain.MapPreferencesRepository
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.PlannedTripRepository
import com.forager.app.domain.PredictAvailabilityUseCase
import com.forager.app.domain.SavePlannedTripUseCase
import com.forager.app.domain.SearchTaxaUseCase
import com.forager.app.domain.TripPlanningWeatherProvider
import com.forager.app.domain.WeatherProvider
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.SightingsPage
import com.forager.app.domain.model.SpeciesObservationCount
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import com.forager.app.domain.model.WeatherSeries
import com.forager.app.ui.log.MushroomLogUiState
import com.forager.app.ui.map.MapSlot
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * System back on the compact layout, driven through the real
 * [androidx.activity.ComponentActivity.getOnBackPressedDispatcher] rather than by calling a
 * callback directly — the whole point of this suite is verifying the *priority* several
 * independently-declared [androidx.activity.compose.BackHandler]s resolve to when more than one is
 * enabled at once (e.g. the drawer open while the map is also fullscreen), which only the real
 * dispatcher's own stack can settle. Reasoning about `BackHandler` registration order is exactly
 * the kind of thing CLAUDE.md's "see the failure... don't describe code that wasn't actually read"
 * warns against trusting unverified.
 *
 * The rule: back always unwinds one step toward "home" (Maps tab, chrome visible, drawer closed)
 * before the exit-confirmation handler ever sees it. See `AvailabilityScreen`'s own back-handling
 * block, `CompactMapTab`'s `AddActionTile` handler, `JournalTab`'s, and `CompactSettingsTab`'s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenBackNavigationTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val searchCache = InMemorySearchCacheRepository()
    private lateinit var viewModel: AvailabilityViewModel

    private fun pressBack() {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
    }

    private fun setScreen(logUiState: MushroomLogUiState = MushroomLogUiState()) {
        val plannedTripRepository = BackNavInMemoryPlannedTripRepository()
        viewModel = AvailabilityViewModel(
            locationProvider = BackNavUnusedLocationProvider,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(BackNavEmptyRepository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(BackNavEmptyRepository),
            searchTaxa = SearchTaxaUseCase(BackNavEmptyRepository),
            getConditions = GetConditionsUseCase(BackNavStubWeatherProvider),
            clusterForagingAreas = ClusterForagingAreasUseCase(),
            getTripWindows = GetTripWindowsUseCase(BackNavStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(plannedTripRepository),
            savePlannedTrip = SavePlannedTripUseCase(plannedTripRepository),
            deletePlannedTrip = DeletePlannedTripUseCase(plannedTripRepository),
            getSeasonalPattern = GetSeasonalPatternUseCase(
                GetSightingsUseCase(BackNavEmptyRepository),
                BackNavStubHistoricalWeatherProvider,
                ComputeFruitingLagDistributionUseCase(),
            ),
            offlineMapRepository = BackNavStubOfflineMapRepository,
            mapPreferencesRepository = BackNavStubMapPreferencesRepository,
        )
        composeRule.setContent {
            val uiState by viewModel.uiState.collectAsState()
            var logState by remember { mutableStateOf(logUiState) }
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
                onDownloadOfflineMaps = viewModel::onDownloadOfflineMaps,
                onDeleteOfflineRegion = viewModel::onDeleteOfflineRegion,
                logUiState = logState,
                onStartLogEntry = { location, date ->
                    logState = logState.copy(
                        entries = logState.entries + com.forager.app.domain.model.MushroomLogEntry.draft(
                            id = "started-entry",
                            location = location,
                            date = date,
                        ),
                        editingEntry = com.forager.app.domain.model.MushroomLogEntry.draft(
                            id = "started-entry",
                            location = location,
                            date = date,
                        ),
                    )
                },
                onOpenLogEntry = { id -> logState = logState.copy(editingEntry = logState.entries.first { it.id == id }) },
                onCloseLogEntry = { logState = logState.copy(editingEntry = null) },
                compassProvider = BackNavFakeCompassProvider,
                mapSlot = BackNavStubMapSlot,
            )
        }
    }

    private fun searchAReferenceRegion() {
        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithText("Advanced search").performClick()
        composeRule.onNodeWithText("Latitude").performScrollTo().performTextReplacement("45.326")
        composeRule.onNodeWithText("Longitude").performScrollTo().performTextReplacement("-122.634")
        composeRule.onNodeWithText("Search this location").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `back closes the open drawer instead of warning to exit`() {
        setScreen()
        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithText("Advanced search").assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithText("Advanced search").assertIsNotDisplayed()
        assertEquals(null, ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `back exits fullscreen instead of warning to exit`() {
        setScreen()
        searchAReferenceRegion()
        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        // The bottom nav is conditionally composed away while fullscreen, not merely hidden.
        composeRule.onNodeWithText("Settings").assertDoesNotExist()

        pressBack()

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        assertEquals(null, ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `back returns to the Maps tab from another tab instead of warning to exit`() {
        setScreen()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Choose Maps Service").assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithText("Choose Maps Service").assertDoesNotExist()
        assertEquals(null, ShadowToast.getTextOfLatestToast())
    }

    /**
     * With the drawer open *and* the map fullscreen at once — reachable because the icon stack's
     * own Search button stays up while fullscreen — back must close the drawer first. Settles
     * whether the top-level fullscreen-exit `BackHandler` or the drawer-close one actually wins,
     * which only the real dispatcher's stack ordering can answer.
     */
    @Test
    fun `with the drawer open while fullscreen, back closes the drawer before exiting fullscreen`() {
        setScreen()
        searchAReferenceRegion()
        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithText("Advanced search").assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithText("Advanced search").assertIsNotDisplayed()
        // Still fullscreen — the bottom nav is conditionally composed away, not just hidden.
        composeRule.onNodeWithText("Settings").assertDoesNotExist()

        pressBack()

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    /**
     * A Journal entry's own edit form is more nested than "which bottom-nav tab is selected" — back
     * must unwind the entry (to its report) before it ever switches tabs. Proves `JournalTab`'s own
     * `BackHandler`, composed as part of this tab's content, takes priority over
     * `AvailabilityScreen`'s top-level "switch away from a non-Maps tab" handler.
     */
    @Test
    fun `back backs out of a Journal entry before switching away from the Journal tab`() {
        setScreen()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithContentDescription("New log entry").performClick()
        composeRule.onNodeWithText("Simulate long press").performClick()
        composeRule.onNodeWithText("Place entry here").performClick()
        composeRule.onNodeWithText("Photos").assertIsDisplayed()

        pressBack()

        // Back in the report, not the edit form (JournalTab's `when` mounts only one at a time,
        // so the edit form is fully unmounted, not merely hidden), and still on the Journal tab —
        // not bounced to Maps.
        composeRule.onNodeWithText("Photos").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
    }

    /**
     * The warning is a real [android.widget.Toast], not Compose UI, so it's read back via
     * [ShadowToast] — the same pattern `DirectionsIntentTest` uses — rather than
     * `onNodeWithText`, which only sees the Compose semantics tree.
     *
     * Doesn't use [pressBack]/`waitForIdle()` between the two presses: idling the compose test
     * clock fast-forwards through the exit-window's own `delay(DOUBLE_BACK_EXIT_WINDOW_MS)`
     * coroutine, resetting `backPressedOnce` back to `false` before the second press ever lands —
     * which would make this test fail for a reason that has nothing to do with whether back
     * navigation actually works, only with how the test clock settles a real elapsed-time window.
     * Advancing by a single frame at a time keeps that coroutine suspended across both presses,
     * the same way a real double-tap arrives well inside the window.
     */
    @Test
    fun `back on the home screen warns once, then exits on a second press`() {
        setScreen()
        composeRule.mainClock.autoAdvance = false

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals("Tap Back Button Again to Exit", ShadowToast.getTextOfLatestToast())
        assertFalse(composeRule.activity.isFinishing)

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue(composeRule.activity.isFinishing)
    }
}

private val BackNavStubMapSlot: MapSlot = { _, _, _, _, _, _, onLongPress, _, modifier ->
    Column(modifier.testTag("map-slot")) {
        Button(onClick = { onLongPress(LatLng(45.326, -122.634)) }) { Text("Simulate long press") }
    }
}

private class BackNavFakeCompassProviderImpl : CompassProvider {
    override val heading: Flow<Float?> = MutableStateFlow(null)
}
private val BackNavFakeCompassProvider = BackNavFakeCompassProviderImpl()

private object BackNavUnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult =
        error("getCurrentLocation() is not part of this test's path and must not be called")
}

private object BackNavEmptyRepository : MushroomRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object BackNavStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object BackNavStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

private object BackNavStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

private class BackNavInMemoryPlannedTripRepository : PlannedTripRepository {
    private val trips = mutableMapOf<String, PlannedTrip>()

    override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(trips.values.toList())
    override suspend fun save(trip: PlannedTrip): Result<Unit> {
        trips[trip.id] = trip
        return Result.success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        trips.remove(id)
        return Result.success(Unit)
    }
}

private object BackNavStubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

private object BackNavStubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(60)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
}
