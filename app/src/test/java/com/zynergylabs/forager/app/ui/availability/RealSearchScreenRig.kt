package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.zynergylabs.forager.app.domain.AppThemePreferenceRepository
import com.zynergylabs.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.zynergylabs.forager.app.domain.ComputeTripWindowsUseCase
import com.zynergylabs.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.zynergylabs.forager.app.domain.DeletePlannedTripUseCase
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
import com.zynergylabs.forager.app.domain.MapPreferencesRepository
import com.zynergylabs.forager.app.domain.MushroomRepository
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.PlannedTripRepository
import com.zynergylabs.forager.app.domain.PredictAvailabilityUseCase
import com.zynergylabs.forager.app.domain.SavePlannedTripUseCase
import com.zynergylabs.forager.app.domain.SearchTaxaUseCase
import com.zynergylabs.forager.app.domain.TaxonSearchRepository
import com.zynergylabs.forager.app.domain.TripPlanningWeatherProvider
import com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository
import com.zynergylabs.forager.app.domain.WeatherProvider
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.ConditionsSummary
import com.zynergylabs.forager.app.domain.model.DailyWeather
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.SightingsPage
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.WeatherSeries
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.rules.ExternalResource
import org.robolectric.Shadows

/**
 * RECORD -723 and -725/-726: the real [AvailabilityScreen] wired to the real [AvailabilityViewModel] exactly as
 * MainActivity wires it, with the iNaturalist and weather edges faked, for the recent-search, Clear and species
 * suggestion tests. What the map is handed is read from the [MapSlot] the screen composes: [mapSightings] is the
 * `content.sightings` of its latest composition. Every touch is real, at screen coordinates, in touch mode.
 */
internal class RealSearchScreenRig(private val composeRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>) {

    /** Every sightings fetch the ViewModel made: region, month and filter. */
    val sightingsFetches = mutableListOf<Triple<Region, Int, TaxonFilter>>()

    /** Every ranked-list (availability) fetch the ViewModel made. */
    val availabilityFetches = mutableListOf<Triple<Region, Int, TaxonFilter>>()

    /** The sightings the map was handed on its latest composition. */
    var mapSightings: List<Sighting> = emptyList()
        private set

    /** RECORD -750, item 5: the region the map was handed on its latest composition. */
    var mapRegion: Region? = null
        private set

    /** RECORD -750, item 5: the search frame request the map was handed on its latest composition (0 is none). */
    var mapSearchFrameRequestId: Int = 0
        private set

    /** RECORD -750, item 5: the locate button's resume-following token the map was handed on its latest composition. */
    var mapResumeTrackingRequestId: Int = 0
        private set

    /** RECORD -752: the camera memory the map was handed on its latest composition. */
    var mapCameraMemory: com.zynergylabs.forager.app.ui.map.MapCameraMemory? = null
        private set

    /** RECORD -761: whether the map was ever handed `leavingTab` (the tab crossfade's outgoing side). */
    var mapLeavingSeen = false

    /**
     * The map's sighting-tap callback as handed on its latest composition: what the real map calls with a dot's screen position
     * when it is tapped (LandscapeStripHeightTest opens the observation bubble through it at a chosen point).
     */
    var mapOnSightingTap: ((Sighting, Offset, Float) -> Unit)? = null
        private set

    /** RECORD -752: every measure of the map slot, in order, with the remembered camera at that moment. */
    val mapMeasures = mutableListOf<MapMeasure>()

    /** What the species lookup answers, for every query (RECORD -750 sets a long name). */
    var taxonResults: List<TaxonSearchResult> = listOf(CHANTERELLE)

    /** What [LocationProvider.getCurrentLocation] answers; counted in [locationRequests]. */
    var location: LocationResult = LocationResult.Success(45.0, -122.0)
    var locationRequests = 0
        private set

    val searchCache = InMemorySearchCacheRepository()

    /**
     * When set, every ranked-list fetch waits for it: a fetch still running, for the Clear-while-loading test. The
     * ranked list, not the map's sightings: while sightings load, the Maps tab is a spinner with no search bar on it.
     */
    var availabilityGate: CompletableDeferred<Unit>? = null

    private val repository = object : MushroomRepository, TaxonSearchRepository {
        override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter): Result<List<SpeciesObservationCount>> {
            availabilityFetches += Triple(region, month, filter)
            availabilityGate?.await()
            return Result.success(
                listOf(SpeciesObservationCount(48473L, "Ganoderma applanatum", "artist's bracket", "species", 14, null, null)),
            )
        }

        override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter): Result<SightingsPage> {
            sightingsFetches += Triple(region, month, filter)
            return Result.success(SightingsPage(sightings = listOf(sightingAt(region)), totalResults = 1))
        }

        override suspend fun searchTaxa(query: String): Result<List<TaxonSearchResult>> = Result.success(taxonResults)
    }

    lateinit var viewModel: AvailabilityViewModel
        private set

    fun setScreen() {
        viewModel = AvailabilityViewModel(
            locationProvider = object : LocationProvider {
                override suspend fun getCurrentLocation(): LocationResult {
                    locationRequests++
                    return location
                }
            },
            locationTracker = object : LocationTracker {
                override val fixes: Flow<LocationFix> = emptyFlow()
            },
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(repository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(repository),
            searchTaxa = SearchTaxaUseCase(repository),
            getConditions = GetConditionsUseCase(RigNoWeather),
            getTripWindows = GetTripWindowsUseCase(RigNoWeather, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(RigNoTrips),
            savePlannedTrip = SavePlannedTripUseCase(RigNoTrips),
            deletePlannedTrip = DeletePlannedTripUseCase(RigNoTrips),
            getSeasonalPattern = GetSeasonalPatternUseCase(GetSightingsUseCase(repository), RigNoWeather, ComputeFruitingLagDistributionUseCase()),
            offlineMapRepository = RigNoOfflineMaps,
            mapPreferencesRepository = RigNoMapPreferences,
            unitSystemPreferenceRepository = RigMetricUnits,
            appThemePreferenceRepository = RigLightTheme,
            getTodaysForecast = GetTodaysForecastUseCase(RigNoWeather),
        )
        val vm = viewModel
        val mapSlot: MapSlot = { region, content, renderMode, _, _, _, onSightingTap, _, modifier ->
            mapOnSightingTap = onSightingTap
            mapSightings = content.sightings
            mapRegion = region
            mapSearchFrameRequestId = content.searchFrameRequestId
            mapResumeTrackingRequestId = content.resumeTrackingRequestId
            mapCameraMemory = renderMode.cameraMemory
            if (renderMode.leavingTab) mapLeavingSeen = true
            val memory = renderMode.cameraMemory
            Box(
                modifier.testTag(LAYOUT_FIXES_MAP_TAG).layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    // RECORD -752: every size the map is measured at, with the camera it holds then (what a new MapView restores).
                    mapMeasures += MapMeasure(androidx.compose.ui.unit.IntSize(placeable.width, placeable.height), memory?.saved, composeRule.mainClock.currentTime)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
            )
        }
        composeRule.setContent {
            val uiState by vm.uiState.collectAsState()
            AvailabilityScreen(
                uiState = uiState,
                searchInputs = AvailabilityScreenSearchInputs(
                    onUseCurrentLocation = vm::useCurrentLocation,
                    onManualLatChanged = vm::onManualLatChanged,
                    onManualLngChanged = vm::onManualLngChanged,
                    onSearchManualCoordinates = vm::searchManualCoordinates,
                    onRadiusChanged = vm::onRadiusChanged,
                    onMonthSelected = vm::onMonthSelected,
                    onMapTabSelected = vm::onMapTabSelected,
                    onSeasonalTabSelected = vm::onSeasonalTabSelected,
                    onTaxonSearchQueryChanged = vm::onTaxonSearchQueryChanged,
                    onTaxonSearchResultSelected = vm::onTaxonSearchResultSelected,
                    onDismissTaxonSuggestions = vm::onDismissTaxonSuggestions,
                    onReopenTaxonSuggestions = vm::onReopenTaxonSuggestions,
                    onPlaceTripPin = vm::onPlaceTripPin,
                    onDeletePlannedTrip = vm::onDeletePlannedTrip,
                    onRecentSearchSelected = vm::onRecentSearchSelected,
                    onClearSearch = vm::clearSearch,
                ),
                offlineMaps = AvailabilityScreenOfflineMaps(
                    onOfflineMapLatChanged = vm::onOfflineMapLatChanged,
                    onOfflineMapLngChanged = vm::onOfflineMapLngChanged,
                    onOfflineMapRadiusChanged = vm::onOfflineMapRadiusChanged,
                    onOfflineMapNameChanged = vm::onOfflineMapNameChanged,
                    onOfflineMapsOpened = vm::onOfflineMapsOpened,
                    onDownloadOfflineMaps = vm::onDownloadOfflineMaps,
                    onDeleteOfflineRegion = vm::onDeleteOfflineRegion,
                ),
                settings = AvailabilityScreenSettings(
                    onNightModeMapsChanged = vm::onNightModeMapsChanged,
                    onThemeModeChanged = vm::onThemeModeChanged,
                ),
                mapSlot = mapSlot,
            )
        }
        settle()
    }

    /**
     * Lets the screen and the ViewModel finish. The main looper's clock is advanced as well as Compose's: a ViewModel
     * `delay` (the species search's 300 ms debounce) waits on the looper, which Robolectric's paused looper holds still.
     */
    fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000))
        composeRule.waitForIdle()
    }

    fun dropdownShown() = composeRule.onAllNodes(hasTestTag(SEARCH_DROPDOWN_TAG)).fetchSemanticsNodes().isNotEmpty()

    /** A real touch at screen coordinates at ([fx], [fy]) of [node]'s own bounds. */
    fun touch(node: SemanticsNodeInteraction, fx: Float = 0.5f, fy: Float = 0.5f) {
        val b = node.fetchSemanticsNode().boundsInRoot
        val at = Offset(b.left + b.width * fx, b.top + b.height * fy)
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(at) }
        settle()
    }

    fun tapBar() {
        touch(composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG, useUnmergedTree = true))
    }

    /** Opens the dropdown, types [lat]/[lng] and touches "Search coordinates", as a user would. */
    fun searchCoordinates(lat: String, lng: String) {
        tapBar()
        check(dropdownShown()) { "the bar's tap did not open the dropdown" }
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_LATITUDE_TAG).performTextReplacement(lat)
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_LONGITUDE_TAG).performTextReplacement(lng)
        touch(composeRule.onNodeWithTag(SEARCH_DROPDOWN_SEARCH_COORDINATES_TAG))
        check(!dropdownShown()) { "Search coordinates did not close the dropdown" }
    }

    companion object {
        val CHANTERELLE = TaxonSearchResult(47347L, "Cantharellus cibarius", "golden chanterelle", "species", "Fungi", null)

        /** One sighting per region, at its centre, with an id read from its latitude, so two regions' sightings differ. */
        fun sightingAt(region: Region) = Sighting(
            observationId = (region.lat * 10_000).toLong(),
            taxonId = 48473L,
            scientificName = "Ganoderma applanatum",
            commonName = "artist's bracket",
            lat = region.lat,
            lng = region.lng,
            observedOn = null,
            photoUrl = null,
        )

        /** Touch mode, as a finger puts a phone, set before the window is added (the SearchDropdownReopenTest precedent). */
        fun touchModeHost() = object : ExternalResource() {
            override fun before() {
                val app = ApplicationProvider.getApplicationContext<Application>()
                Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
                InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
            }
        }
    }
}

/** RECORD -752: one measure of the map slot: its size in px, the remembered camera handed with it, and the test clock's time then. */
internal data class MapMeasure(val size: androidx.compose.ui.unit.IntSize, val camera: com.zynergylabs.forager.app.ui.map.MapCameraSnapshot?, val atMs: Long)

internal object RigNoWeather : WeatherProvider, TripPlanningWeatherProvider, HistoricalWeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) = Result.failure<ConditionsSummary>(UnsupportedOperationException("not exercised"))
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> = Result.failure(UnsupportedOperationException("not exercised"))
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("not exercised"))
}

internal object RigNoTrips : PlannedTripRepository {
    override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(emptyList())
    override suspend fun save(trip: PlannedTrip): Result<Unit> = Result.failure(UnsupportedOperationException("not exercised"))
    override suspend fun delete(id: String): Result<Unit> = Result.failure(UnsupportedOperationException("not exercised"))
}

internal object RigNoOfflineMaps : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("not exercised"))
    override suspend fun deleteRegion(id: Long): Result<Unit> = Result.failure(UnsupportedOperationException("not exercised"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

internal object RigNoMapPreferences : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getMapFullscreen(): Result<Boolean> = Result.success(false)
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = Result.success(Unit)
}

internal object RigMetricUnits : UnitSystemPreferenceRepository {
    override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(UnitSystem.METRIC)
    override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
}

internal object RigLightTheme : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}
