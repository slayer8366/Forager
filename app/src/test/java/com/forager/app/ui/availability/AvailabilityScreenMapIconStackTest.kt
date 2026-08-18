package com.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import com.forager.app.domain.OfflineMapInfo
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
import com.forager.app.ui.map.MapSlot
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * The map redesign's right-edge icon stack, bottom nav, fullscreen toggle, and compass/elevation
 * strip — driven through [AvailabilityScreen]'s real entry points, per CLAUDE.md ("exercise
 * user-triggered behavior through its real entry point"), mirroring
 * [AvailabilityScreenTripPlanningFlowTest]'s setup. Compact-width only (`w360dp`, below the
 * `WindowWidthClass.MEDIUM` breakpoint) — see docs/plans/map-redesign.md's "Scope decision"
 * section for why the redesign itself is scoped there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenMapIconStackTest {

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

    private lateinit var viewModel: AvailabilityViewModel
    private val searchCache = InMemorySearchCacheRepository()

    private fun setScreen(
        onLocateMe: () -> Unit = {},
        compassProvider: CompassProvider = FakeCompassProvider(null),
        onStartLogEntry: (LatLng, LocalDate) -> Unit = { _, _ -> },
        mapSlot: MapSlot = CountingStubMapSlot,
    ) {
        val plannedTripRepository = IconStackInMemoryPlannedTripRepository()
        viewModel = AvailabilityViewModel(
            locationProvider = IconStackUnusedLocationProvider,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(IconStackEmptyRepository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(IconStackEmptyRepository),
            searchTaxa = SearchTaxaUseCase(IconStackEmptyRepository),
            getConditions = GetConditionsUseCase(IconStackStubWeatherProvider),
            clusterForagingAreas = ClusterForagingAreasUseCase(),
            getTripWindows = GetTripWindowsUseCase(IconStackStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(plannedTripRepository),
            savePlannedTrip = SavePlannedTripUseCase(plannedTripRepository),
            deletePlannedTrip = DeletePlannedTripUseCase(plannedTripRepository),
            getSeasonalPattern = GetSeasonalPatternUseCase(
                GetSightingsUseCase(IconStackEmptyRepository),
                IconStackStubHistoricalWeatherProvider,
                ComputeFruitingLagDistributionUseCase(),
            ),
            offlineMapRepository = IconStackStubOfflineMapRepository,
        )
        composeRule.setContent {
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
                onDownloadOfflineMaps = viewModel::onDownloadOfflineMaps,
                onDeleteOfflineMaps = viewModel::onDeleteOfflineMaps,
                onStartLogEntry = onStartLogEntry,
                onLocateMe = onLocateMe,
                compassProvider = compassProvider,
                mapSlot = mapSlot,
            )
        }
    }

    /**
     * Opens the drawer via the icon stack's "Search" icon — [CompactMapTab] now shows a real map
     * (and so the icon stack) even before a first search, GPS-centred or on a fixed fallback while
     * that's still pending, so this icon is reachable from the very first composition rather than
     * only once a region exists.
     */
    private fun searchAReferenceRegion() {
        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithText("Advanced search").performClick()
        composeRule.onNodeWithText("Latitude").performScrollTo().performTextReplacement("45.326")
        composeRule.onNodeWithText("Longitude").performScrollTo().performTextReplacement("-122.634")
        composeRule.onNodeWithText("Search this location").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `all five icon stack buttons are present`() {
        setScreen()
        searchAReferenceRegion()

        composeRule.onNodeWithContentDescription("Fullscreen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Center on my location").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Showing topo mode. Switch to regular mode.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").assertIsDisplayed()
    }

    @Test
    fun `the locate-me icon calls onLocateMe, not onUseCurrentLocation`() {
        var locateMeCalls = 0
        setScreen(onLocateMe = { locateMeCalls++ })
        composeRule.waitForIdle()
        // The compact scaffold pings location once on its own first composition — see
        // compactMainScaffold's own LaunchedEffect(Unit) — so one call is already counted before
        // the icon is ever tapped.
        val callsBeforeTap = locateMeCalls
        searchAReferenceRegion()

        composeRule.onNodeWithContentDescription("Center on my location").performClick()

        assertEquals(callsBeforeTap + 1, locateMeCalls)
    }

    @Test
    fun `the search icon opens the search drawer`() {
        setScreen()
        searchAReferenceRegion()

        composeRule.onNodeWithContentDescription("Search").performClick()

        // The drawer's own content — proof the icon opened the real drawer, not a parallel search UI.
        composeRule.onNodeWithText("Advanced search").assertIsDisplayed()
    }

    @Test
    fun `the add button opens the same plan-or-log chooser the long-press gesture opens, at the region center`() {
        var startedLogEntryAt: LatLng? = null
        setScreen(onStartLogEntry = { location, _ -> startedLogEntryAt = location })
        searchAReferenceRegion()

        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").performClick()
        composeRule.onNodeWithText("Add...").assertIsDisplayed()
        composeRule.onNodeWithText("Log a find").performClick()
        composeRule.waitForIdle()

        assertEquals(LatLng(45.326, -122.634), startedLogEntryAt)
    }

    @Test
    fun `fullscreen hides the top strip and bottom nav but keeps the map mounted`() {
        setScreen()
        searchAReferenceRegion()
        CountingStubMapSlotState.compositionCount = 0

        // "Settings" (bottom nav) and the "Fungi · August · 15 km" search summary (top strip) stand
        // in for the two chrome regions decision #5 hides together — there's no more app-bar tune
        // icon to check now that species/category search and "Advanced search" both moved into the
        // drawer. Matched by the summary's exact text rather than a "15 km" substring: the drawer
        // sheet stays composed off-screen while closed (see openSearchDrawer()'s doc comment
        // elsewhere in this suite) and its own "Search radius: 15 km" text would otherwise double
        // the substring match.
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Fungi · August · 15 km").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("map-slot").assertIsDisplayed()
        assertEquals("the map slot must not be torn down and recomposed from scratch on a chrome toggle", 0, CountingStubMapSlotState.compositionCount)
        composeRule.onAllNodesWithText("Settings").assertCountEquals(0)
        composeRule.onAllNodesWithText("Fungi · August · 15 km").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Exit fullscreen").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Fungi · August · 15 km").assertIsDisplayed()
    }

    @Test
    fun `tapping the map while fullscreen restores chrome`() {
        setScreen(mapSlot = TappableStubMapSlot)
        searchAReferenceRegion()

        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Settings").assertCountEquals(0)

        composeRule.onNodeWithTag("map-slot").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun `the bottom nav's three destinations select the same ResultsTab the old tab row did`() {
        setScreen()
        searchAReferenceRegion()

        composeRule.onNodeWithText("List").performClick()
        composeRule.onNodeWithTag("map-slot").assertDoesNotExist()

        composeRule.onNodeWithText("Seasonal").performClick()
        composeRule.onNodeWithTag("map-slot").assertDoesNotExist()

        composeRule.onNodeWithText("Maps").performClick()
        composeRule.onNodeWithTag("map-slot").assertIsDisplayed()
    }

    @Test
    fun `the compass elevation strip shows an explicit unavailable state, never a guessed value, with no sensor and no fix yet`() {
        setScreen(compassProvider = FakeCompassProvider(null))
        searchAReferenceRegion()

        composeRule.onNodeWithText("Compass unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Elevation unavailable").assertIsDisplayed()
    }

    @Test
    fun `the compass elevation strip reflects a fake heading without any real sensor`() {
        setScreen(compassProvider = FakeCompassProvider(90f))
        searchAReferenceRegion()

        composeRule.onNodeWithText("90° E").assertIsDisplayed()
    }

    @Test
    fun `the foraging areas toggle lives in the search drawer, not floating over the map`() {
        setScreen()
        searchAReferenceRegion()

        // searchAReferenceRegion leaves the drawer closed (a real search closes it) — foraging
        // areas must not be reachable without opening it, the opposite of the earlier revision
        // where it floated as an overlay on the map itself. The drawer sheet stays composed
        // off-screen while closed, so its toggle row is still in the tree — assertIsNotDisplayed,
        // not assertDoesNotExist, is what actually distinguishes "closed" from "open" here.
        composeRule.onNodeWithText("Foraging areas").assertIsNotDisplayed()

        composeRule.onNodeWithContentDescription("Search").performClick()

        composeRule.onNodeWithText("Foraging areas").assertIsDisplayed()
    }
}

/**
 * Counts real (re-)compositions of the map slot's content, so
 * `fullscreen hides ... but keeps the map mounted` can assert the fullscreen toggle didn't tear
 * down and recreate it — mirrors [MapSlot]'s real contract (a `remember` runs once per composition
 * lifetime, not once per recomposition).
 */
private object CountingStubMapSlotState {
    var compositionCount = 0
}

private val CountingStubMapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier ->
    androidx.compose.runtime.remember { CountingStubMapSlotState.compositionCount++ }
    Column(modifier.testTag("map-slot")) {
        Text("map")
    }
}

/** Exposes [onTap] as a clickable surface, for the "tap the map to restore chrome" test. */
private val TappableStubMapSlot: MapSlot = { _, _, _, _, _, _, _, onTap, modifier ->
    Column(modifier.testTag("map-slot").clickable(onClick = onTap)) {
        Text("map")
    }
}

private class FakeCompassProvider(initial: Float?) : CompassProvider {
    private val state = MutableStateFlow(initial)
    override val heading: Flow<Float?> = state
}

private object IconStackUnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult =
        error("getCurrentLocation() is not part of this test's path and must not be called")
}

private object IconStackEmptyRepository : MushroomRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object IconStackStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object IconStackStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

private object IconStackStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

private class IconStackInMemoryPlannedTripRepository : PlannedTripRepository {
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

private object IconStackStubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineMapInfo> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun delete(): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun getStatus(): Result<OfflineMapInfo?> = Result.success(null)
}
