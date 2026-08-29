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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.forager.app.domain.AppThemePreferenceRepository
import com.forager.app.domain.DistanceUnitPreferenceRepository
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
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.SightingsPage
import com.forager.app.domain.model.SpeciesObservationCount
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import com.forager.app.domain.model.Waypoint
import com.forager.app.domain.model.WeatherSeries
import com.forager.app.ui.map.MapSlot
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The drop-a-waypoint flow end to end, and the drawer's Waypoints section — the
 * waypoint counterpart to [AvailabilityScreenTripPlanningFlowTest], same harness shape. Waypoint
 * state isn't owned by [AvailabilityViewModel] (see [com.forager.app.ui.track.TrackRecordingViewModel],
 * already covered by its own `TrackRecordingViewModelTest`), so this drives [AvailabilityScreen]'s
 * `waypoints`/`onDropWaypoint`/`onDeleteWaypoint` parameters directly against test-owned state
 * rather than a real ViewModel — [AvailabilityViewModel] is only along for the ride to satisfy
 * search/region, exactly as in the trip-planning test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenWaypointFlowTest {

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

    private var droppedWaypoints = mutableListOf<Pair<LatLng, String>>()
    private var deletedWaypointIds = mutableListOf<String>()

    private fun setScreen(initialWaypoints: List<Waypoint> = emptyList(), waypointsErrorMessage: String? = null) {
        val plannedTripRepository = WaypointFlowInMemoryPlannedTripRepository()
        viewModel = AvailabilityViewModel(
            locationProvider = WaypointFlowUnusedLocationProvider,
            locationTracker = WaypointFlowNoOpLocationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(WaypointFlowEmptyRepository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(WaypointFlowEmptyRepository),
            searchTaxa = SearchTaxaUseCase(WaypointFlowEmptyRepository),
            getConditions = GetConditionsUseCase(WaypointFlowStubWeatherProvider),
            clusterForagingAreas = ClusterForagingAreasUseCase(),
            getTripWindows = GetTripWindowsUseCase(WaypointFlowStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(plannedTripRepository),
            savePlannedTrip = SavePlannedTripUseCase(plannedTripRepository),
            deletePlannedTrip = DeletePlannedTripUseCase(plannedTripRepository),
            getSeasonalPattern = GetSeasonalPatternUseCase(
                GetSightingsUseCase(WaypointFlowEmptyRepository),
                WaypointFlowStubHistoricalWeatherProvider,
                ComputeFruitingLagDistributionUseCase(),
            ),
            offlineMapRepository = WaypointFlowStubOfflineMapRepository,
            mapPreferencesRepository = WaypointFlowStubMapPreferencesRepository,
            distanceUnitPreferenceRepository = WaypointFlowStubDistanceUnitPreferenceRepository,
            appThemePreferenceRepository = WaypointFlowStubAppThemePreferenceRepository,
        )
        composeRule.setContent {
            val uiState by viewModel.uiState.collectAsState()
            var waypoints by remember(initialWaypoints) { mutableStateOf(initialWaypoints) }
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
                onDarkThemeChanged = viewModel::onDarkThemeChanged,
                waypoints = waypoints,
                waypointsErrorMessage = waypointsErrorMessage,
                onDropWaypoint = { location, name ->
                    droppedWaypoints += location to name
                    waypoints = waypoints + Waypoint(
                        id = "waypoint-${waypoints.size + 1}",
                        lat = location.lat,
                        lng = location.lng,
                        altitude = null,
                        name = name,
                        note = "",
                        createdAtEpochMillis = 0L,
                    )
                },
                onDeleteWaypoint = { id ->
                    deletedWaypointIds += id
                    waypoints = waypoints.filterNot { it.id == id }
                },
                mapSlot = TriggerableWaypointMapSlot,
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

    /** Opens the chooser, picks "Drop a waypoint", pans the stub map to [WAYPOINT_TEST_LOCATION], and confirms the centre-pin picker with OK. */
    private fun openChooserPanAndConfirm() {
        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").performClick()
        composeRule.onNodeWithText("Waypoint").performClick()
        composeRule.onNodeWithText("Simulate pan to test location").performClick()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `choosing Drop a waypoint, panning to a location, and confirming a name drops a waypoint at that location`() {
        setScreen()
        searchAReferenceRegion()

        openChooserPanAndConfirm()
        composeRule.onNodeWithText("Drop waypoint").assertIsDisplayed()

        composeRule.onNodeWithText("Drop waypoint").performClick()
        composeRule.waitForIdle()

        val (location, name) = droppedWaypoints.single()
        assertEquals(WAYPOINT_TEST_LOCATION, location)
        // No waypoint existed before this one, so the name field's computed default is
        // "Waypoint 1" — see defaultWaypointName. Confirming without editing it proves that
        // default actually reaches the callback, not just the text field.
        assertEquals("Waypoint 1", name)
    }

    @Test
    fun `the waypoint name field can be edited before confirming, and the edited name is used`() {
        setScreen()
        searchAReferenceRegion()

        openChooserPanAndConfirm()
        composeRule.onNodeWithText("Waypoint name").performTextReplacement("Trailhead")
        composeRule.onNodeWithText("Drop waypoint").performClick()
        composeRule.waitForIdle()

        assertEquals("Trailhead", droppedWaypoints.single().second)
    }

    @Test
    fun `a second waypoint defaults its name to Waypoint 2`() {
        setScreen()
        searchAReferenceRegion()

        openChooserPanAndConfirm()
        composeRule.onNodeWithText("Drop waypoint").performClick()
        composeRule.waitForIdle()

        openChooserPanAndConfirm()
        composeRule.onNodeWithText("Waypoint name").assertIsDisplayed()
        composeRule.onNodeWithText("Drop waypoint").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("Waypoint 1", "Waypoint 2"), droppedWaypoints.map { it.second })
    }

    @Test
    fun `clearing the waypoint name disables confirming`() {
        setScreen()
        searchAReferenceRegion()

        openChooserPanAndConfirm()
        composeRule.onNodeWithText("Waypoint name").performTextReplacement("")

        composeRule.onNodeWithText("Drop waypoint").assertIsNotEnabled()
    }

    @Test
    fun `dismissing the name dialog after choosing Drop a waypoint saves nothing`() {
        setScreen()
        searchAReferenceRegion()

        openChooserPanAndConfirm()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertTrue(droppedWaypoints.isEmpty())
    }

    @Test
    fun `the drawer's Waypoints section shows a no-waypoints message when empty`() {
        setScreen()

        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithText("Waypoints").performClick()

        composeRule.onNodeWithText("No waypoints dropped yet. Tap the add button on the map to drop one.")
            .assertIsDisplayed()
    }

    @Test
    fun `every Waypoints drawer control is reachable, and delete calls onDeleteWaypoint with its id`() {
        val waypoint = Waypoint(
            id = "reachable-waypoint",
            lat = 45.40,
            lng = -122.70,
            altitude = null,
            name = "Reachable Waypoint",
            note = "",
            createdAtEpochMillis = 0L,
        )
        setScreen(initialWaypoints = listOf(waypoint))

        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithText("Waypoints").performClick()

        composeRule.onNodeWithText("Reachable Waypoint").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("45.4000, -122.7000").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Directions to Reachable Waypoint")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove waypoint Reachable Waypoint")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("reachable-waypoint"), deletedWaypointIds)
    }

    /**
     * The error-presentation spec's belief-changing branch for `waypointsErrorMessage`: a failed
     * load/add/remove replaces the list entirely, same [TripWindowsCard]-shape branch order
     * `WaypointsSection` now uses, rather than showing a possibly-stale list beside the error.
     */
    @Test
    fun `a set waypointsErrorMessage replaces the list, not just adds to it`() {
        val waypoint = Waypoint(
            id = "hidden-waypoint",
            lat = 45.40,
            lng = -122.70,
            altitude = null,
            name = "Hidden Waypoint",
            note = "",
            createdAtEpochMillis = 0L,
        )
        setScreen(initialWaypoints = listOf(waypoint), waypointsErrorMessage = "Couldn't load waypoints.")

        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithText("Waypoints").performClick()

        composeRule.onNodeWithText("Couldn't load waypoints.").assertIsDisplayed()
        composeRule.onNodeWithText("Hidden Waypoint").assertDoesNotExist()
        composeRule.onNodeWithText("No waypoints dropped yet. Tap the add button on the map to drop one.").assertDoesNotExist()
    }

    /** The absence side of the same branch: no error set, the list renders exactly as before this task. */
    @Test
    fun `with waypointsErrorMessage unset, the list renders and no error text appears`() {
        val waypoint = Waypoint(
            id = "reachable-waypoint",
            lat = 45.40,
            lng = -122.70,
            altitude = null,
            name = "Reachable Waypoint",
            note = "",
            createdAtEpochMillis = 0L,
        )
        setScreen(initialWaypoints = listOf(waypoint))

        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithText("Waypoints").performClick()

        composeRule.onNodeWithText("Reachable Waypoint").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Couldn't load waypoints.").assertDoesNotExist()
    }
}

private val WAYPOINT_TEST_LOCATION = LatLng(45.40, -122.70)

/** Same shape as [AvailabilityScreenTripPlanningFlowTest]'s `TriggerableMapSlot` — see that file's own doc comment. */
private val TriggerableWaypointMapSlot: MapSlot = { _, _, _, _, _, _, _, onCameraIdle, modifier ->
    Column(modifier.testTag("map-slot")) {
        Button(onClick = { onCameraIdle(WAYPOINT_TEST_LOCATION) }) {
            Text("Simulate pan to test location")
        }
    }
}

private object WaypointFlowUnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult =
        error("getCurrentLocation() is not part of this test's path and must not be called")
}

private object WaypointFlowNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object WaypointFlowEmptyRepository : MushroomRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object WaypointFlowStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object WaypointFlowStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

private object WaypointFlowStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

private class WaypointFlowInMemoryPlannedTripRepository : PlannedTripRepository {
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

/** Not exercised by this test's assertions; getStatus() succeeds with "nothing downloaded" since it runs on every ViewModel init. */
private object WaypointFlowStubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

private object WaypointFlowStubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
}

private object WaypointFlowStubDistanceUnitPreferenceRepository : DistanceUnitPreferenceRepository {
    override suspend fun getDistanceUnit(): Result<DistanceUnit> = Result.success(DistanceUnit.MILES)
    override suspend fun setDistanceUnit(unit: DistanceUnit): Result<Unit> = Result.success(Unit)
}

private object WaypointFlowStubAppThemePreferenceRepository : AppThemePreferenceRepository {
    override suspend fun getDarkTheme(): Result<Boolean> = Result.success(false)
    override suspend fun setDarkTheme(dark: Boolean): Result<Unit> = Result.success(Unit)
}
