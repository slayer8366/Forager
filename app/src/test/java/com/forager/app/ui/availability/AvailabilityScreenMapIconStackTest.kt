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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
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
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.ReturnToStartInfo
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.SightingsPage
import com.forager.app.domain.model.SpeciesObservationCount
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import com.forager.app.domain.model.WeatherSeries
import com.forager.app.ui.map.MapSlot
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
        onStartLogEntry: (LatLng?, LocalDate) -> Unit = { _, _ -> },
        mapSlot: MapSlot = CountingStubMapSlot,
        locationProvider: LocationProvider = IconStackUnusedLocationProvider,
        locationTracker: LocationTracker = IconStackNoOpLocationTracker,
        isRecording: Boolean = false,
        returnToStart: ReturnToStartInfo? = null,
        mushroomRepository: MushroomRepository = IconStackEmptyRepository,
    ) {
        val plannedTripRepository = IconStackInMemoryPlannedTripRepository()
        viewModel = AvailabilityViewModel(
            locationProvider = locationProvider,
            locationTracker = locationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(IconStackEmptyRepository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(IconStackEmptyRepository),
            searchTaxa = SearchTaxaUseCase(mushroomRepository),
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
            mapPreferencesRepository = IconStackStubMapPreferencesRepository,
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
                onStartLogEntry = onStartLogEntry,
                onLocateMe = onLocateMe,
                isRecording = isRecording,
                returnToStart = returnToStart,
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
        composeRule.onNodeWithContentDescription("Showing topo mode. Switch to regular mode. Night mode off.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").assertIsDisplayed()
    }

    /**
     * Drives the real long-press gesture through [MapStackIconButton]'s `combinedClickable`, per
     * CLAUDE.md's rule that pointer-input behaviour over the map needs a Robolectric test driving
     * the actual gesture, not a direct call to `onToggleNightMode`. `automaticNight` resolves to
     * `false` here — no test in this suite sets `uiState.liveLocation`, and `CivilTwilight` is
     * never consulted without a fix — so the layers button starts "Night mode off." deterministically,
     * with no dependency on wall-clock time.
     */
    @Test
    fun `long-pressing the layers icon toggles night mode without also toggling the basemap`() {
        setScreen()
        searchAReferenceRegion()

        val dayDescription = "Showing topo mode. Switch to regular mode. Night mode off."
        val nightDescription = "Showing topo mode. Switch to regular mode. Night mode on."

        composeRule.onNodeWithContentDescription(dayDescription).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(dayDescription).performTouchInput { longClick() }
        composeRule.waitForIdle()

        // Still "topo mode" throughout: a long press must not also fire onToggleMapMode.
        composeRule.onNodeWithContentDescription(nightDescription).assertIsDisplayed()

        // MapNightMode.toggled: pressing twice returns to automatic rather than leaving a hold
        // that happens to agree, so this must land back on "Night mode off.", not toggle again.
        composeRule.onNodeWithContentDescription(nightDescription).performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(dayDescription).assertIsDisplayed()
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
    fun `the add button opens the same plan-or-log chooser the long-press gesture used to, then the centre-pin picker, seeded at the region center`() {
        var startedLogEntryAt: LatLng? = null
        setScreen(onStartLogEntry = { location, _ -> startedLogEntryAt = location })
        searchAReferenceRegion()

        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").performClick()
        composeRule.onNodeWithText("Add...").assertIsDisplayed()
        composeRule.onNodeWithText("Log a find").performClick()
        composeRule.waitForIdle()
        // Choosing an action opens the centre-pin picker rather than firing immediately — the
        // stub map never pans, so the pin stays at the seeded region center and OK confirms it.
        composeRule.onNodeWithText("OK").performClick()
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
        composeRule.onNodeWithText("Coordinates unavailable").assertIsDisplayed()
    }

    @Test
    fun `the compass elevation strip reflects a fake heading without any real sensor`() {
        setScreen(compassProvider = FakeCompassProvider(90f))
        searchAReferenceRegion()

        composeRule.onNodeWithText("90° E").assertIsDisplayed()
    }

    @Test
    fun `the compass strip shows a real MGRS grid reference by default once a live fix arrives, not the combined line`() {
        // Same Portland-OR point MgrsConverterTest pins, not a value invented for this test — if
        // MgrsConverter's own logic ever regresses, that test fails; this one only needs to prove
        // the strip actually renders whatever MgrsConverter.convert(location) returns. The combined
        // "<mgrs> · Lat. X Long. Y" line this strip used to show is gone — it truncated mid-
        // coordinate on a metro-width screen, a real hardware finding (see coordinatesStripText's
        // own doc comment) — so this also asserts the old combined string is *not* what's displayed.
        //
        // Driven through the continuous LocationTracker, not locate-me's one-shot locationProvider:
        // the strip's own coordinates now come from AvailabilityUiState.liveLocation, which only
        // AvailabilityViewModel's locationTracker.fixes collection populates (see that ViewModel's
        // init block) — locate-me's fetch still drives the map's own GPS pan/camera, but no longer
        // this strip, per the "any time the map is open" live-tracking scope this redesign answers.
        // replay = 1 (not a bare MutableSharedFlow()): tryEmit below runs synchronously from the
        // test body, with no guarantee AvailabilityViewModel's own viewModelScope.launch { fixes.
        // collect {} } (started inside its init, from setScreen's AvailabilityViewModel(...) call
        // above) has actually subscribed by the time the emit happens — a bare zero-buffer
        // SharedFlow's tryEmit silently drops the value with no subscriber ready yet. A one-slot
        // replay buffer guarantees the fix is delivered whenever the collector does start.
        val fixes = MutableSharedFlow<LocationFix>(replay = 1)
        setScreen(locationTracker = IconStackFakeLocationTracker(fixes))
        searchAReferenceRegion()

        fixes.tryEmit(LocationFix.Update(lat = 45.5152, lng = -122.6784, altitude = 210.0, accuracyMeters = null, timestampEpochMillis = 0L))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("10T ER 25118 40235").assertIsDisplayed()
        composeRule.onNodeWithText("10T ER 25118 40235 · Lat. 45.5152 Long. -122.6784").assertDoesNotExist()
        composeRule.onNodeWithText("Lat. 45.5152 Long. -122.6784").assertDoesNotExist()
    }

    @Test
    fun `tapping the coordinates segment reveals labeled decimal degrees, and tapping again returns to MGRS`() {
        val fixes = MutableSharedFlow<LocationFix>(replay = 1)
        setScreen(locationTracker = IconStackFakeLocationTracker(fixes))
        searchAReferenceRegion()

        fixes.tryEmit(LocationFix.Update(lat = 45.5152, lng = -122.6784, altitude = 210.0, accuracyMeters = null, timestampEpochMillis = 0L))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("10T ER 25118 40235").performClick()
        composeRule.onNodeWithText("Lat. 45.5152 Long. -122.6784").assertIsDisplayed()
        composeRule.onNodeWithText("10T ER 25118 40235").assertDoesNotExist()

        composeRule.onNodeWithText("Lat. 45.5152 Long. -122.6784").performClick()
        composeRule.onNodeWithText("10T ER 25118 40235").assertIsDisplayed()
    }

    @Test
    fun `the coordinates segment is not tappable before a first fix arrives`() {
        setScreen(compassProvider = FakeCompassProvider(null))
        searchAReferenceRegion()

        // "Coordinates unavailable" has nothing to toggle between — clicking it should be a no-op,
        // not silently reveal a fabricated decimal-degree pair for a location that was never fixed.
        composeRule.onNodeWithText("Coordinates unavailable").performClick()
        composeRule.onNodeWithText("Coordinates unavailable").assertIsDisplayed()
    }

    @Test
    fun `the return-to-vehicle line is blank while not recording, and the record toggle still shows`() {
        setScreen(isRecording = false)
        searchAReferenceRegion()

        composeRule.onNodeWithContentDescription("Start recording track").assertIsDisplayed()
        composeRule.onAllNodesWithText("Return:", substring = true).assertCountEquals(0)
    }

    @Test
    fun `recording with no fix yet shows a waiting message, not a guessed return`() {
        setScreen(isRecording = true, returnToStart = null)
        searchAReferenceRegion()

        composeRule.onNodeWithText("Recording — waiting for a fix to compute the way back").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Stop recording track").assertIsDisplayed()
    }

    @Test
    fun `recording with a real fix shows bearing, distance, and elevation difference back to the start`() {
        setScreen(
            isRecording = true,
            returnToStart = ReturnToStartInfo(bearingDegrees = 180.0, distanceMeters = 1200.0, elevationDifferenceMeters = -45.0),
        )
        searchAReferenceRegion()

        composeRule.onNodeWithText("Return: 180° S · 1.2 km · -45 m").assertIsDisplayed()
    }

    @Test
    fun `a return distance under a kilometer is shown in meters`() {
        setScreen(
            isRecording = true,
            returnToStart = ReturnToStartInfo(bearingDegrees = 45.0, distanceMeters = 350.0, elevationDifferenceMeters = null),
        )
        searchAReferenceRegion()

        composeRule.onNodeWithText("Return: 45° NE · 350 m · elevation diff. unavailable").assertIsDisplayed()
    }

    @Test
    fun `the search summary bar shows a magnifying glass marking it as tappable for search`() {
        setScreen()

        composeRule.onNodeWithContentDescription("Quick species search").assertIsDisplayed()
    }

    /**
     * [ModalNavigationDrawer] keeps the drawer's own [SpeciesSearchControls] composed even while
     * closed — see [QuickSearchPanel]'s own doc comment — so queries here are scoped to
     * [QUICK_SEARCH_PANEL_TAG] rather than matching by text/tag alone, which would find both the
     * (closed, off-screen) drawer copy and this panel's own.
     */
    private fun quickSearchNodeWithText(text: String) =
        composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(QUICK_SEARCH_PANEL_TAG)))

    @Test
    fun `tapping the search summary bar opens a quick search panel, tapping again closes it`() {
        setScreen()

        composeRule.onNodeWithText("Fungi · August · no location set", substring = true).performClick()
        composeRule.onNodeWithTag(QUICK_SEARCH_PANEL_TAG).assertIsDisplayed()

        composeRule.onNodeWithText("Fungi · August · no location set", substring = true).performClick()
        composeRule.onNodeWithTag(QUICK_SEARCH_PANEL_TAG).assertDoesNotExist()
    }

    @Test
    fun `the quick search panel does not open the full search drawer`() {
        setScreen()

        composeRule.onNodeWithText("Fungi · August · no location set", substring = true).performClick()

        // The drawer's "Advanced search" entry point structurally exists either way (see
        // QuickSearchPanel's own doc comment on ModalNavigationDrawer) — the real assertion is
        // that it isn't showing, not that it's absent from the tree.
        composeRule.onNodeWithText("Advanced search").assertIsNotDisplayed()
    }

    /**
     * Category selection, not species text search: [SpeciesSearchControls]' category chips apply
     * synchronously (no debounce, no Popup dropdown), so this exercises quick search wired to the
     * real [AvailabilityViewModel] without the timing fragility a debounced-search-to-dropdown test
     * would need — [SearchTaxaUseCase]'s own debounce/dropdown mechanics are shared, pre-existing
     * logic with no dedicated test anywhere in this codebase yet, compact or otherwise; not a gap
     * this task introduced.
     */
    @Test
    fun `picking a category chip in quick search applies the filter through the real ViewModel`() {
        setScreen()

        composeRule.onNodeWithText("Fungi · August · no location set", substring = true).performClick()
        quickSearchNodeWithText("Plants").performClick()

        assertEquals(TaxonFilter.PLANTS, viewModel.uiState.value.taxonFilter)
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

private val CountingStubMapSlot: MapSlot = { _, _, _, _, _, _, _, modifier ->
    androidx.compose.runtime.remember { CountingStubMapSlotState.compositionCount++ }
    Column(modifier.testTag("map-slot")) {
        Text("map")
    }
}

/** Exposes [onTap] as a clickable surface, for the "tap the map to restore chrome" test. */
private val TappableStubMapSlot: MapSlot = { _, _, _, _, _, onTap, _, modifier ->
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

private object IconStackNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private class IconStackFakeLocationTracker(override val fixes: MutableSharedFlow<LocationFix>) : LocationTracker

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
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

private object IconStackStubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
}
