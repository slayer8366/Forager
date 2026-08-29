package com.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import com.forager.app.domain.model.AppThemeMode
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
 * The plan-a-trip flow end to end: the add button opening the three-way chooser, a real pan
 * reported by the map slot's [MapSlot.onCameraIdle], a real
 * [com.forager.app.ui.map.CentrePinLocationPicker] OK confirmation, a real
 * [androidx.compose.material3.DatePickerDialog] confirmation, and a real save through the real
 * [AvailabilityViewModel] — driven through [AvailabilityScreen]'s actual entry points rather than
 * calling [AvailabilityViewModel.onPlaceTripPin] directly, per CLAUDE.md ("exercise
 * user-triggered behavior through its real entry point").
 *
 * The map itself is stubbed — see [AvailabilityScreenLayoutTest] for why — but the stub here also
 * exposes a button that invokes the slot's real [MapSlot] `onCameraIdle` callback, which is the
 * one piece of the real map's behaviour this flow depends on (see [MapSlot]'s own doc comment for
 * why the coordinate now comes from the camera, not a gesture).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenTripPlanningFlowTest {

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

    /**
     * The offline search cache is not what this file is about. An empty in-memory one keeps the
     * ViewModel's new dependency real — every search still writes through it — without changing
     * anything these tests assert; the cache's own behaviour is covered by
     * `RoomSearchCacheRepositoryTest` and `GetAvailabilityUseCaseTest`.
     */
    private val searchCache = InMemorySearchCacheRepository()

    private fun setScreen(onStartLogEntry: (LatLng?, LocalDate) -> Unit = { _, _ -> }) {
        // A fresh instance per test, not a shared singleton: this repository is mutable, and each
        // test's assertions depend on starting from an empty store.
        val plannedTripRepository = TripFlowInMemoryPlannedTripRepository()
        viewModel = AvailabilityViewModel(
            locationProvider = TripFlowUnusedLocationProvider,
            locationTracker = TripFlowNoOpLocationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(TripFlowEmptyRepository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(TripFlowEmptyRepository),
            searchTaxa = SearchTaxaUseCase(TripFlowEmptyRepository),
            getConditions = GetConditionsUseCase(TripFlowStubWeatherProvider),
            clusterForagingAreas = ClusterForagingAreasUseCase(),
            getTripWindows = GetTripWindowsUseCase(TripFlowStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(plannedTripRepository),
            savePlannedTrip = SavePlannedTripUseCase(plannedTripRepository),
            deletePlannedTrip = DeletePlannedTripUseCase(plannedTripRepository),
            getSeasonalPattern = GetSeasonalPatternUseCase(
                GetSightingsUseCase(TripFlowEmptyRepository),
                TripFlowStubHistoricalWeatherProvider,
                ComputeFruitingLagDistributionUseCase(),
            ),
            offlineMapRepository = TripFlowStubOfflineMapRepository,
            mapPreferencesRepository = TripFlowStubMapPreferencesRepository,
            distanceUnitPreferenceRepository = TripFlowStubDistanceUnitPreferenceRepository,
            appThemePreferenceRepository = TripFlowStubAppThemePreferenceRepository,
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
                onOfflineMapNameChanged = viewModel::onOfflineMapNameChanged,
                onOfflineMapsOpened = viewModel::onOfflineMapsOpened,
                onDownloadOfflineMaps = viewModel::onDownloadOfflineMaps,
                onDeleteOfflineRegion = viewModel::onDeleteOfflineRegion,
                onNightModeMapsChanged = viewModel::onNightModeMapsChanged,
                onThemeModeChanged = viewModel::onThemeModeChanged,
                onStartLogEntry = onStartLogEntry,
                mapSlot = TriggerableMapSlot,
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

    /** Opens the chooser, picks [choice], pans the stub map to [TEST_LOCATION], and confirms the centre-pin picker with OK. */
    private fun openChooserPanAndConfirm(choice: String) {
        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").performClick()
        composeRule.onNodeWithText(choice).performClick()
        composeRule.onNodeWithText("Simulate pan to test location").performClick()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `choosing Plan a trip, panning to a location, and confirming saves a planned trip at that location`() {
        setScreen()
        searchAReferenceRegion()

        openChooserPanAndConfirm("Trip")
        composeRule.onNodeWithText("Plan trip").assertIsDisplayed()

        composeRule.onNodeWithText("Plan trip").performClick()
        composeRule.waitForIdle()

        val trip = viewModel.uiState.value.plannedTrips.single()
        assertEquals(TEST_LOCATION, trip.location)
        // No trip existed before this one, so the name field's computed default is "Trip 1" — see
        // defaultTripName. Confirming without editing it proves that default actually reaches the
        // saved trip, not just the text field.
        assertEquals("Trip 1", trip.name)
    }

    @Test
    fun `the trip name field can be edited before confirming, and the edited name is saved`() {
        setScreen()
        searchAReferenceRegion()

        openChooserPanAndConfirm("Trip")
        composeRule.onNodeWithText("Trip name").performTextReplacement("Chanterelle Ridge")
        composeRule.onNodeWithText("Plan trip").performClick()
        composeRule.waitForIdle()

        val trip = viewModel.uiState.value.plannedTrips.single()
        assertEquals("Chanterelle Ridge", trip.name)
    }

    @Test
    fun `a second planned trip defaults its name to Trip 2`() {
        setScreen()
        searchAReferenceRegion()

        openChooserPanAndConfirm("Trip")
        composeRule.onNodeWithText("Plan trip").performClick()
        composeRule.waitForIdle()

        openChooserPanAndConfirm("Trip")
        composeRule.onNodeWithText("Trip name").assertIsDisplayed()
        composeRule.onNodeWithText("Plan trip").performClick()
        composeRule.waitForIdle()

        assertEquals(
            setOf("Trip 1", "Trip 2"),
            viewModel.uiState.value.plannedTrips.map { it.name }.toSet(),
        )
    }

    @Test
    fun `clearing the trip name disables confirming`() {
        setScreen()
        searchAReferenceRegion()

        openChooserPanAndConfirm("Trip")
        composeRule.onNodeWithText("Trip name").performTextReplacement("")

        composeRule.onNodeWithText("Plan trip").assertIsNotEnabled()
    }

    @Test
    fun `dismissing the plan-or-log chooser without picking either saves nothing`() {
        setScreen()
        searchAReferenceRegion()

        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").performClick()
        composeRule.onNodeWithTag(ADD_ACTION_TILE_SCRIM_TAG).performClick()
        composeRule.waitForIdle()

        assertTrue(viewModel.uiState.value.plannedTrips.isEmpty())
    }

    @Test
    fun `dismissing the centre-pin picker after choosing Plan a trip saves nothing`() {
        setScreen()
        searchAReferenceRegion()

        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").performClick()
        composeRule.onNodeWithText("Trip").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertTrue(viewModel.uiState.value.plannedTrips.isEmpty())
    }

    @Test
    fun `dismissing the date picker after confirming a location saves nothing`() {
        setScreen()
        searchAReferenceRegion()

        openChooserPanAndConfirm("Trip")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertTrue(viewModel.uiState.value.plannedTrips.isEmpty())
    }

    @Test
    fun `choosing Log a find calls onStartLogEntry with the picked location instead of planning a trip`() {
        var startedLogEntryAt: LatLng? = null
        setScreen(onStartLogEntry = { location, _ -> startedLogEntryAt = location })
        searchAReferenceRegion()

        openChooserPanAndConfirm("Find")

        assertEquals(TEST_LOCATION, startedLogEntryAt)
        assertTrue("choosing Log a find must not also plan a trip", viewModel.uiState.value.plannedTrips.isEmpty())
    }
}

private val TEST_LOCATION = LatLng(45.40, -122.70)

/**
 * Fills the map's box and exposes the one piece of real map behaviour this flow depends on: a
 * button that reports a pan to a fixed location, the same way a real pan reports one via
 * [MapSlot.onCameraIdle] once the camera settles — see that parameter's own doc comment.
 */
private val TriggerableMapSlot: MapSlot = { _, _, _, _, _, _, _, onCameraIdle, modifier ->
    Column(modifier.testTag("map-slot")) {
        Button(onClick = { onCameraIdle(TEST_LOCATION) }) {
            Text("Simulate pan to test location")
        }
    }
}

private object TripFlowUnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult =
        error("getCurrentLocation() is not part of this test's path and must not be called")
}

private object TripFlowNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object TripFlowEmptyRepository : MushroomRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object TripFlowStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object TripFlowStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

private object TripFlowStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

private class TripFlowInMemoryPlannedTripRepository : PlannedTripRepository {
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
private object TripFlowStubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

private object TripFlowStubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
}

private object TripFlowStubDistanceUnitPreferenceRepository : DistanceUnitPreferenceRepository {
    override suspend fun getDistanceUnit(): Result<DistanceUnit> = Result.success(DistanceUnit.MILES)
    override suspend fun setDistanceUnit(unit: DistanceUnit): Result<Unit> = Result.success(Unit)
}

private object TripFlowStubAppThemePreferenceRepository : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}
