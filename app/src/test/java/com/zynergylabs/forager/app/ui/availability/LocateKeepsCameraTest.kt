package com.zynergylabs.forager.app.ui.availability

import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.zynergylabs.forager.app.domain.ComputeTripWindowsUseCase
import com.zynergylabs.forager.app.domain.DeletePlannedTripUseCase
import com.zynergylabs.forager.app.domain.GetAvailabilityUseCase
import com.zynergylabs.forager.app.domain.GetConditionsUseCase
import com.zynergylabs.forager.app.domain.GetPlannedTripsUseCase
import com.zynergylabs.forager.app.domain.GetRecentSearchesUseCase
import com.zynergylabs.forager.app.domain.GetSeasonalPatternUseCase
import com.zynergylabs.forager.app.domain.GetSightingsUseCase
import com.zynergylabs.forager.app.domain.GetTodaysForecastUseCase
import com.zynergylabs.forager.app.domain.GetTripWindowsUseCase
import com.zynergylabs.forager.app.domain.InMemorySearchCacheRepository
import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.domain.LocationProvider
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.MushroomRepository
import com.zynergylabs.forager.app.domain.NoEntryReferenceCounts
import com.zynergylabs.forager.app.domain.PredictAvailabilityUseCase
import com.zynergylabs.forager.app.domain.SavePlannedTripUseCase
import com.zynergylabs.forager.app.domain.SearchTaxaUseCase
import com.zynergylabs.forager.app.domain.TaxonSearchRepository
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.SightingsPage
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.ui.map.MapDataRefresh
import com.zynergylabs.forager.app.ui.map.MapLocationDot
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.PositionLook
import com.zynergylabs.forager.app.ui.map.SightingsMapState
import com.zynergylabs.forager.app.ui.map.rememberCameraFixtureMapState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.CameraMapFixture
import org.maplibre.android.maps.StyleLifecycleFixture
import java.time.Duration
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-07-01, Amendment 1 (the owner: "Yes, stop the jump", "Locate just glides to you with your zoom, like
 * Re-center"): on the Maps tab, a locate tap after panning away leaves the camera where the user left it, at their zoom,
 * until following takes it to them. The tap's own one-shot fix moves nothing.
 *
 * Through the real screen and the real [AvailabilityViewModel], with a real touch on the icon bar's locate row. The map slot
 * runs the app's own [MapDataRefresh] and then [MapLocationDot] (the order `SightingsMap` composes them in, which is the order
 * their effects run in) over a real MapLibre map ([CameraMapFixture]).
 *
 * Copied here rather than run (each mirrors the code it names):
 * - `MainActivity`'s locate-me, which asks for the permission and, with it already held, gets its answer posted to the main
 *   looper (androidx `ComponentActivity`'s registry posts a synchronous result) and then calls `locateMe()`: here a post.
 * - the user's pan, a camera set on the map.
 *
 * What it cannot show: the glide itself. Following is MapLibre's own transition to the puck (750 ms, zoom kept), which needs a
 * location the fixture's component does not have; the test reads that following was turned on, and that nothing else moved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class LocateKeepsCameraTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val start = camera(44.0, -121.0, 9.0)
    private val panned = camera(43.0, -118.0, 16.5)

    /** The fixes the locate-me fetch answers, in order: the app-open locate, then the tap's. */
    private val fixes = ArrayDeque(listOf(LocationResult.Success(42.0, -117.0), LocationResult.Success(42.5, -117.5)))
    private var fetches = 0

    private lateinit var made: Pair<org.maplibre.android.maps.MapLibreMap, CameraMapFixture.Recording>
    private lateinit var mapState: SightingsMapState

    private fun camera(lat: Double, lng: Double, zoom: Double): CameraPosition =
        CameraPosition.Builder().target(MapLibreLatLng(lat, lng)).zoom(zoom).bearing(0.0).tilt(0.0).build()

    private val slot: MapSlot = { region, content, renderMode, focusOverride, _, _, _, _, modifier ->
        val map = remember { CameraMapFixture.map(start = start, fit = start).also { made = it } }
        val state = rememberCameraFixtureMapState(map.first, rememberUpdatedState(renderMode.cameraMemory))
        remember { mapState = state }
        val style = remember { StyleLifecycleFixture.loadedStyle() }
        LaunchedEffect(Unit) { state.loadedStyle = style }
        Box(modifier)
        MapDataRefresh(
            state = state,
            route = null,
            region = region,
            sightings = emptyList(),
            plannedTrips = emptyList(),
            focusOverride = focusOverride,
            breadcrumbPoints = emptyList(),
            waypoints = emptyList(),
            focusedObservationId = null,
            keptTrackPolylines = emptyList(),
            findMarkers = emptyList(),
            photoMarkers = emptyList(),
            offlineRegionCircles = emptyList(),
            showSearchCentre = false,
            cameraRequest = renderMode.cameraRequest,
            journalHighlights = JournalEntryHighlights.NONE,
            searchFrameRequestId = content.searchFrameRequestId,
            resources = LocalContext.current.resources,
        )
        MapLocationDot(
            state = state,
            appLocationEngine = null,
            mapPosition = null,
            positionLook = PositionLook.PRECISE,
            trackLiveLocation = renderMode.trackLiveLocation,
            resumeTrackingRequestId = content.resumeTrackingRequestId,
            context = LocalContext.current,
            appCompassEngine = null,
        )
    }

    private fun setScreen() {
        val searchCache = InMemorySearchCacheRepository()
        val vm = AvailabilityViewModel(
            locationProvider = object : LocationProvider {
                override suspend fun getCurrentLocation(): LocationResult {
                    fetches++
                    return fixes.removeFirstOrNull() ?: LocationResult.LocationUnavailable
                }
            },
            locationTracker = object : com.zynergylabs.forager.app.domain.LocationTracker {
                override val fixes = kotlinx.coroutines.flow.emptyFlow<com.zynergylabs.forager.app.domain.LocationFix>()
            },
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(NoSightings), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(NoSightings),
            searchTaxa = SearchTaxaUseCase(NoSightings),
            getConditions = GetConditionsUseCase(RigNoWeather),
            getTripWindows = GetTripWindowsUseCase(RigNoWeather, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(RigNoTrips),
            savePlannedTrip = SavePlannedTripUseCase(RigNoTrips),
            deletePlannedTrip = DeletePlannedTripUseCase(RigNoTrips),
            getSeasonalPattern = GetSeasonalPatternUseCase(GetSightingsUseCase(NoSightings), RigNoWeather, ComputeFruitingLagDistributionUseCase()),
            offlineMapRepository = RigNoOfflineMaps,
            mapPreferencesRepository = RigNoMapPreferences,
            unitSystemPreferenceRepository = RigMetricUnits,
            appThemePreferenceRepository = RigLightTheme,
            getTodaysForecast = GetTodaysForecastUseCase(RigNoWeather),
            entryReferenceCounts = NoEntryReferenceCounts,
        )
        // As MainActivity's locate-me reaches the ViewModel with the permission held: its answer posted to the main looper.
        val main = Handler(Looper.getMainLooper())
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
                mapControls = AvailabilityScreenMapControls(onLocateMe = { main.post { vm.locateMe() } }),
                mapSlot = slot,
            )
        }
        settle()
    }

    /** Lets the screen, the ViewModel and the posted locate-me answer finish (Robolectric's paused looper holds a post). */
    private fun settle() {
        composeRule.waitForIdle()
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        composeRule.waitForIdle()
    }

    private fun describe(c: CameraPosition?) = "(${c?.target?.latitude}, ${c?.target?.longitude}) at zoom ${c?.zoom}"

    private fun touchLocate() {
        val node = composeRule.onNode(hasContentDescription("Center on my location"))
        val b = node.fetchSemanticsNode().boundsInRoot
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(Offset(b.center.x, b.center.y)) }
        settle()
    }

    @Test
    fun `a locate tap after panning away moves no camera itself - following takes over at the user's zoom`() {
        setScreen()
        val recording = made.second
        assertEquals("the app-open locate fetched one fix", 1, fetches)

        // The pan, as a drag leaves it: the camera moved and following dismissed (the map opens following; MapLibre's move
        // gesture ends it, a mode set on the SDK's controller here).
        composeRule.runOnUiThread {
            made.first.cameraPosition = panned
            recording.setModeDirectly(CameraMode.NONE)
        }
        composeRule.waitForIdle()
        val jumpsAfterPan = recording.jumps
        assertEquals("not following after the pan", CameraMode.NONE, recording.cameraMode)

        touchLocate()

        assertEquals("the tap fetched its own fix", 2, fetches)
        assertEquals("the tap turned following on (MapLibre's own transition, zoom kept)", CameraMode.TRACKING, recording.cameraMode)
        assertEquals(
            "the locate tap moved the camera instantly (to ${describe(recording.camera)}) instead of leaving it to following",
            jumpsAfterPan,
            recording.jumps,
        )
        assertTrue("no animated move either: following alone moves the camera, eases ${recording.eases}", recording.eases.isEmpty())
        val camera = recording.camera
        assertTrue(
            "the camera is where the user left it, at their zoom: expected ${describe(panned)}, was ${describe(camera)}",
            camera?.target == panned.target && camera?.zoom == panned.zoom,
        )
    }
}

/** No sightings and no species anywhere: this test's map shows none. */
private object NoSightings : MushroomRepository, TaxonSearchRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter): Result<List<SpeciesObservationCount>> =
        Result.success(emptyList())

    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter): Result<SightingsPage> =
        Result.success(SightingsPage(sightings = emptyList(), totalResults = 0))

    override suspend fun searchTaxa(query: String): Result<List<TaxonSearchResult>> = Result.success(emptyList())
}
