package com.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.ClusterForagingAreasUseCase
import com.forager.app.domain.CompassProvider
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
import com.forager.app.domain.GetTodaysForecastUseCase
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
import org.junit.Assert.assertNull
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

    // createAndroidComposeRule<ComponentActivity>(), not the plain createComposeRule() this file
    // used before — same underlying rule (createComposeRule() is implemented as exactly this call),
    // just with a static type that actually exposes .activity, needed below to read back a real
    // started-activity Intent via Shadows.shadowOf(activity) the same way
    // AvailabilityScreenSettingsPanelTest.kt's own share-sheet test already does.
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
        onToggleRecording: () -> Unit = {},
        returnToStart: ReturnToStartInfo? = null,
        isReturning: Boolean = false,
        isOffTrack: Boolean = false,
        onToggleReturning: () -> Unit = {},
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
            distanceUnitPreferenceRepository = IconStackStubDistanceUnitPreferenceRepository,
            appThemePreferenceRepository = IconStackStubAppThemePreferenceRepository,
            getTodaysForecast = GetTodaysForecastUseCase(IconStackStubTripPlanningWeatherProvider),
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
                onLocateMe = onLocateMe,
                isRecording = isRecording,
                onToggleRecording = onToggleRecording,
                returnToStart = returnToStart,
                isReturning = isReturning,
                isOffTrack = isOffTrack,
                onToggleReturning = onToggleReturning,
                compassProvider = compassProvider,
                mapSlot = mapSlot,
            )
        }
    }

    /**
     * Opens [AdvancedSearchDropdown] via the search summary bar and expands its "Enter coordinates
     * manually" section — map/navigation redesign dispatch C, item 1 moved advanced search out of
     * the Tools drawer entirely, to float over the map from where quick species search used to sit
     * (see [ActiveSearchSummary]'s own doc comment). No `performScrollTo()` calls needed: every
     * action below is a semantic `performClick`/`performTextReplacement`, which acts on the node
     * regardless of whether it is currently scrolled into view — [SearchDropdown] does carry a
     * `verticalScroll` (see its own doc comment), but that only matters for an `assertIsDisplayed()`,
     * and this helper makes none.
     */
    private fun searchAReferenceRegion() {
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performClick()
        composeRule.onNodeWithText("Advanced search").performClick()
        composeRule.onNodeWithText("Enter coordinates manually").performClick()
        composeRule.onNodeWithText("Latitude").performTextReplacement("45.326")
        composeRule.onNodeWithText("Longitude").performTextReplacement("-122.634")
        composeRule.onNodeWithText("Search this location").performClick()
        composeRule.waitForIdle()
    }

    /**
     * Registers a fake `https:` handler on [composeRule]'s own host activity, mirroring
     * [DirectionsIntentTest]'s `registerFakeMapsApp` for the same reason: Robolectric's package
     * manager starts with nothing able to resolve an implicit intent, so
     * [launchINaturalistObservation]'s own resolve-then-launch guard would otherwise always take
     * its "nothing installed" branch and never actually call `startActivity`.
     */
    private fun registerFakeBrowser() {
        val componentName = ComponentName(composeRule.activity, "com.example.fakebrowser.BrowserActivity")
        val shadowPackageManager = Shadows.shadowOf(composeRule.activity.packageManager)
        shadowPackageManager.addActivityIfNotPresent(componentName)
        shadowPackageManager.addIntentFilterForActivity(
            componentName,
            android.content.IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("https")
            },
        )
    }

    /**
     * The center of [tag]'s own node, in root-relative px — what [performTouchInput]'s own `click`
     * needs (screen coordinates), as opposed to [getUnclippedBoundsInRoot]'s Dp. Used only by the
     * real touch-routing tests below, per this dispatch's own item 5: `performTouchInput` at screen
     * coordinates, not a semantic node lookup, is the point of those tests.
     */
    private fun centerOfTag(tag: String): Offset {
        val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        return with(composeRule.density) {
            Offset(((bounds.left + bounds.right) / 2).toPx(), ((bounds.top + bounds.bottom) / 2).toPx())
        }
    }

    @Test
    fun `all five icon stack buttons are present`() {
        setScreen()
        searchAReferenceRegion()

        composeRule.onNodeWithContentDescription("Fullscreen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Reset orientation to north").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Center on my location").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Map mode: Topographical. Choose Street, Topographical, or Satellite. Night mode off.").assertIsDisplayed()
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
    fun `the Tools tab opens the drawer`() {
        setScreen()
        searchAReferenceRegion()

        composeRule.onNodeWithText("Tools").performClick()

        // The drawer's own content — proof Tools opened the real drawer, not a parallel search UI.
        composeRule.onNodeWithText("Trip Planner").assertIsDisplayed()
    }

    @Test
    fun `the add button opens the same plan-or-log chooser the long-press gesture used to, then the centre-pin picker, seeded at the region center`() {
        var startedLogEntryAt: LatLng? = null
        setScreen(onStartLogEntry = { location, _ -> startedLogEntryAt = location })
        searchAReferenceRegion()

        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").performClick()
        composeRule.onNodeWithText("Find").assertIsDisplayed()
        composeRule.onNodeWithText("Find").performClick()
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

        // "Tools" (bottom nav) and the "Fungi · August · 15 km" search summary (top strip) stand
        // in for the two chrome regions decision #5 hides together — there's no more app-bar tune
        // icon to check now that species/category search moved into the drawer and "Advanced
        // search" moved into AdvancedSearchDropdown. Matched by the summary's exact text rather
        // than a "15 km" substring: that dropdown's own "Search radius: 15 km" text would
        // otherwise double the substring match, if it stayed composed while closed the way the
        // drawer's own content does — it doesn't (AnimatedVisibility disposes it, not just moves
        // it off-screen), but matching the full summary avoids relying on that either way.
        composeRule.onNodeWithText("Tools").assertIsDisplayed()
        composeRule.onNodeWithText("Fungi · August · 15 km").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("map-slot").assertIsDisplayed()
        assertEquals("the map slot must not be torn down and recomposed from scratch on a chrome toggle", 0, CountingStubMapSlotState.compositionCount)
        composeRule.onAllNodesWithText("Tools").assertCountEquals(0)
        composeRule.onAllNodesWithText("Fungi · August · 15 km").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Exit fullscreen").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Tools").assertIsDisplayed()
        composeRule.onNodeWithText("Fungi · August · 15 km").assertIsDisplayed()
    }

    @Test
    fun `tapping the map while fullscreen restores chrome`() {
        setScreen(mapSlot = TappableStubMapSlot)
        searchAReferenceRegion()

        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Tools").assertCountEquals(0)

        composeRule.onNodeWithTag("map-slot").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Tools").assertIsDisplayed()
    }

    @Test
    fun `tapping an observation dot shows its species name and observed date`() {
        setScreen(mapSlot = SightingTappableStubMapSlot)
        searchAReferenceRegion()

        composeRule.onNodeWithTag("map-slot").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Chanterelle").assertIsDisplayed()
        composeRule.onNodeWithText("Cantharellus formosus").assertIsDisplayed()
        composeRule.onNodeWithText("Aug 1, 2026").assertIsDisplayed()
    }

    @Test
    fun `View on iNaturalist launches the observation's web page and dismisses the bubble`() {
        setScreen(mapSlot = SightingTappableStubMapSlot)
        registerFakeBrowser()
        searchAReferenceRegion()

        composeRule.onNodeWithTag("map-slot").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("View on iNaturalist").performClick()
        composeRule.waitForIdle()

        val started = Shadows.shadowOf(composeRule.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("https://www.inaturalist.org/observations/42", started?.data.toString())
        composeRule.onAllNodesWithText("Chanterelle").assertCountEquals(0)
    }

    @Test
    fun `tapping the bubble's close icon dismisses it without navigating anywhere`() {
        setScreen(mapSlot = SightingTappableStubMapSlot)
        searchAReferenceRegion()

        composeRule.onNodeWithTag("map-slot").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("observation-bubble-close").performClick()
        composeRule.waitForIdle()

        assertNull(Shadows.shadowOf(composeRule.activity).nextStartedActivity)
        composeRule.onAllNodesWithText("Chanterelle").assertCountEquals(0)
    }

    @Test
    fun `dismissing the bubble via its close icon does not let a later pan bring it back`() {
        setScreen(mapSlot = PannableSightingStubMapSlot)
        searchAReferenceRegion()

        composeRule.onNodeWithTag("sighting-dot").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("observation-bubble-close").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Chanterelle").assertCountEquals(0)

        // Simulates the map settling after a pan/zoom, the same re-projection
        // SightingsMap's own camera-idle listener fires — a real hardware report found the
        // bubble reappearing here with nothing tapped.
        composeRule.onNodeWithTag("simulate-pan").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Chanterelle").assertCountEquals(0)
    }

    @Test
    fun `a pan while the bubble is still showing keeps it glued to its marker`() {
        setScreen(mapSlot = PannableSightingStubMapSlot)
        searchAReferenceRegion()

        composeRule.onNodeWithTag("sighting-dot").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("simulate-pan").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Chanterelle").assertIsDisplayed()
    }

    @Test
    fun `tapping elsewhere on the map dismisses the observation bubble`() {
        setScreen(mapSlot = SightingAndPlainTapStubMapSlot)
        searchAReferenceRegion()

        composeRule.onNodeWithTag("sighting-dot").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Chanterelle").assertIsDisplayed()

        composeRule.onNodeWithTag("map-elsewhere").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Chanterelle").assertCountEquals(0)
    }

    /**
     * The project owner's own explicit ask for the bubble's arrow: "as long as it's not spinning
     * with the map and can read it legibly while rotating the map, and stays pointing directly on
     * the observation dot." The dot's own screen position here (300f, 400f — not [Offset.Zero],
     * unlike [SightingTappableStubMapSlot]) leaves real room in every direction for the bubble to
     * move, so a bearing change actually has somewhere to reposition it to. At bearing 0°,
     * [AnchoredAtScreenPoint]'s own base direction (315°, up-and-left of the dot — see that
     * composable's own doc comment) puts the whole bubble above the dot; real touch/rendering, not
     * a semantics-tree assumption, is what [AvailabilityScreenMapIconStackTest]'s own history (the
     * "quick-fire icon" regression this file is named for) says to trust here.
     *
     * (300f, 400f) is in [MapSlot]'s own coordinate space — the map box's own top-left, not the
     * screen root's — the same space [MapSlot.onSightingTap]'s own doc comment describes. Both
     * assertions below add "map-slot"'s own root-relative top back in before comparing, so the
     * dot's *root*-relative position is what the bubble's own root-relative bounds are actually
     * checked against; the two would silently disagree the moment the map box itself sits below
     * any other chrome, which it always does on this window class.
     */
    @Test
    fun `at bearing zero the observation bubble sits above the tapped dot`() {
        setScreen(mapSlot = bearingReportingStubMapSlot(bearingDeg = 0f))
        searchAReferenceRegion()

        composeRule.onNodeWithTag("sighting-dot").performClick()
        composeRule.waitForIdle()

        val bubbleBounds = composeRule.onNodeWithTag("observation-bubble").getUnclippedBoundsInRoot()
        val mapSlotTop = composeRule.onNodeWithTag("map-slot").getUnclippedBoundsInRoot().top
        val dotY = mapSlotTop + with(composeRule.density) { 400f.toDp() }
        assertTrue(
            "At bearing 0 the bubble should sit above the dot it names (bottom ${bubbleBounds.bottom} vs dot y $dotY).",
            bubbleBounds.bottom < dotY,
        )
    }

    /**
     * The other half of the same claim: rotating the map by 90° moves [AnchoredAtScreenPoint]'s own
     * base direction from 315° (up-left) to 225° (down-left) — see that composable's own doc
     * comment for the rotation formula — so the bubble should now sit *below* the dot instead of
     * above it. Two independent [setContent] calls, not one test comparing before/after: Compose's
     * test rule only allows setting content once per test.
     */
    @Test
    fun `after a 90 degree map rotation the observation bubble sits below the tapped dot`() {
        setScreen(mapSlot = bearingReportingStubMapSlot(bearingDeg = 90f))
        searchAReferenceRegion()

        composeRule.onNodeWithTag("sighting-dot").performClick()
        composeRule.waitForIdle()

        val bubbleBounds = composeRule.onNodeWithTag("observation-bubble").getUnclippedBoundsInRoot()
        val mapSlotTop = composeRule.onNodeWithTag("map-slot").getUnclippedBoundsInRoot().top
        val dotY = mapSlotTop + with(composeRule.density) { 400f.toDp() }
        assertTrue(
            "After a 90-degree rotation the bubble should sit below the dot it names (top ${bubbleBounds.top} vs dot y $dotY).",
            bubbleBounds.top > dotY,
        )
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

    /**
     * The bottom nav has five destinations now (List, Seasonal, Maps, Journal, Tools), not the
     * three [ResultsTab] drives above — and Tools is not a `compactTab` at all: tapping it opens
     * the drawer as an overlay over whichever tab was already showing (see the bottom nav's own
     * `onTabSelected` handler in `AvailabilityScreen`), rather than switching away from it the way
     * List/Seasonal/Maps/Journal do. Proven on List specifically, not Maps: the map slot's own
     * absence throughout is the signal that Tools never silently switched `compactTab` underneath
     * the drawer, and that closing the drawer lands back on List directly rather than being bounced
     * to some other tab (e.g. the Maps default) in between.
     */
    @Test
    fun `all five bottom nav destinations are present, and Tools opens the drawer as an overlay over List rather than switching tabs`() {
        setScreen()
        searchAReferenceRegion()

        composeRule.onNodeWithText("List").assertIsDisplayed()
        composeRule.onNodeWithText("Seasonal").assertIsDisplayed()
        composeRule.onNodeWithText("Maps").assertIsDisplayed()
        composeRule.onNodeWithText("Journal").assertIsDisplayed()
        composeRule.onNodeWithText("Tools").assertIsDisplayed()

        composeRule.onNodeWithText("List").performClick()
        composeRule.onNodeWithTag("map-slot").assertDoesNotExist()

        composeRule.onNodeWithText("Tools").performClick()
        composeRule.onNodeWithText("Trip Planner").assertIsDisplayed()
        composeRule.onNodeWithTag("map-slot").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Close search options").performClick()

        composeRule.onNodeWithText("Trip Planner").assertIsNotDisplayed()
        composeRule.onNodeWithTag("map-slot").assertDoesNotExist()
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

    /**
     * The two Trailhead/Return controls moved off [MapIconBar] and the compass strip's own
     * duplicate readout into one shared [ControlPill] this dispatch's Part B adds — see that
     * composable's own doc comment.
     */
    @Test
    fun `the control pill's return-to-vehicle button is disabled while not recording, and the record toggle still shows`() {
        setScreen(isRecording = false)
        searchAReferenceRegion()

        composeRule.onNodeWithContentDescription("Start recording track").assertIsDisplayed()
        composeRule.onNodeWithTag("control-pill-return-to-vehicle").assertIsNotEnabled()
    }

    @Test
    fun `recording with no fix yet shows a waiting message via contentDescription, and no distance arm yet`() {
        setScreen(isRecording = true, returnToStart = null)
        searchAReferenceRegion()

        composeRule.onNode(
            hasTestTag("control-pill-return-to-vehicle") and
                hasContentDescription("Recording — waiting for a fix to compute the way back"),
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Stop recording track").assertIsDisplayed()
        // isReturning defaults false in this test — DistanceArm only shows once return-to-vehicle
        // is actually toggled on, not merely while recording (see TrailheadControls' own doc
        // comment), so it stays out of the tree entirely rather than reserved-but-blank the way
        // the old compass-strip control's fixed-width slot used to.
        composeRule.onNodeWithTag("distance-arm").assertDoesNotExist()
    }

    /**
     * The exact bug field-test dispatch item 2 (an earlier dispatch) existed to fix: the same
     * [ReturnToStartInfo] used to reach a sighted user nowhere but a `contentDescription`
     * (TalkBack-only). This asserts both the full sentence via contentDescription on
     * [ControlPill]'s own return row, and the compact distance readout via [onNodeWithText] on
     * [DistanceArm] — the visible surface field testers can actually read. `isReturning = true` is
     * what makes the arm visible at all; without it, the button's contentDescription is still the
     * full sentence, but there is no visible arm to read "1.2 km" from (see the test above).
     */
    @Test
    fun `recording with a real fix and return-to-vehicle active shows the full sentence via contentDescription and the distance visibly`() {
        setScreen(
            isRecording = true,
            isReturning = true,
            returnToStart = ReturnToStartInfo(bearingDegrees = 180.0, distanceMeters = 1200.0, elevationDifferenceMeters = -45.0),
        )
        searchAReferenceRegion()

        composeRule.onNode(
            hasTestTag("control-pill-return-to-vehicle") and
                hasContentDescription("Return: 180° S · 1.2 km · -45 m"),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("1.2 km").assertIsDisplayed()
    }

    /**
     * This control is used on the return leg — walking, possibly dark, possibly gloved — so its
     * touch target must be a full 48dp regardless of how tall [ControlPill] itself ends up.
     */
    @Test
    fun `the control pill's return-to-vehicle button has a full 48dp touch target`() {
        setScreen(isRecording = true)
        searchAReferenceRegion()

        val bounds = composeRule.onNodeWithTag("control-pill-return-to-vehicle").getUnclippedBoundsInRoot()

        assertTrue("expected a 48dp-tall touch target, was ${bounds.height}", bounds.height >= 48.dp)
    }

    /**
     * Part A item 1 of this dispatch reverts the strip's own `heightIn(min = 48.dp)` pin — it
     * existed only to give the strip's now-removed return-to-vehicle control a real touch target
     * (see [CompassElevationStripContent]'s own doc comment) — back to wrapping its Row's natural
     * text-content height. `< 48.dp`, not `<= 64.dp` the way this test used to read: that older
     * bound would have passed identically whether or not the revert actually took effect (the
     * strip was always well under 64dp even while pinned to exactly 48dp), so it proved nothing
     * about this specific change. `< 48.dp` fails if the old pin — or the `fillMaxSize()` regression
     * this test originally guarded against — ever comes back.
     */
    @Test
    fun `the compass strip container wraps its text content, not a fixed touch-target height or the whole map's height`() {
        setScreen(isRecording = true)
        searchAReferenceRegion()

        val bounds = composeRule.onNodeWithTag("compass-elevation-strip").getUnclippedBoundsInRoot()

        assertTrue(
            "expected the compass strip to wrap its own text content height, well under the old " +
                "48dp touch-target pin (was ${bounds.height}) — see this test's own doc comment",
            bounds.height < 48.dp,
        )
    }

    @Test
    fun `tapping the control pill's return-to-vehicle button calls onToggleReturning`() {
        var toggleCalls = 0
        setScreen(isRecording = true, onToggleReturning = { toggleCalls++ })
        searchAReferenceRegion()

        composeRule.onNodeWithTag("control-pill-return-to-vehicle").performClick()
        composeRule.waitForIdle()

        assertEquals(1, toggleCalls)
    }

    @Test
    fun `an off-track fix tints the return-to-vehicle button with the error color's contentDescription state`() {
        // isOffTrack only changes tint color, not text/contentDescription — this asserts the state
        // reaches the control at all (enabled, present, still showing the right distance) rather
        // than the tint's actual pixel value, which this suite has no existing way to assert either.
        setScreen(
            isRecording = true,
            returnToStart = ReturnToStartInfo(bearingDegrees = 180.0, distanceMeters = 500.0, elevationDifferenceMeters = null),
            isReturning = true,
            isOffTrack = true,
        )
        searchAReferenceRegion()

        composeRule.onNodeWithTag("control-pill-return-to-vehicle").assertIsDisplayed()
        composeRule.onNodeWithText("500 m").assertIsDisplayed()
    }

    @Test
    fun `a return distance under a kilometer is shown in meters on the distance arm`() {
        setScreen(
            isRecording = true,
            isReturning = true,
            returnToStart = ReturnToStartInfo(bearingDegrees = 45.0, distanceMeters = 350.0, elevationDifferenceMeters = null),
        )
        searchAReferenceRegion()

        composeRule.onNode(
            hasTestTag("control-pill-return-to-vehicle") and
                hasContentDescription("Return: 45° NE · 350 m · elevation diff. unavailable"),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("350 m").assertIsDisplayed()
    }

    /**
     * Item 5 of this dispatch, the part that matters most: this codebase has shipped
     * pointer-interception regressions before this one (this file's own history already documents
     * two — one over the compass strip, one over the icon bar), always over a floating surface
     * added to a screen corner with zero [performTouchInput] coverage anywhere in the suite. This
     * is the first such coverage, scoped to the [TrailheadControls] corner this dispatch adds, on
     * this suite's own w360dp-h640dp viewport — the same short viewport the icon-bar interception
     * bug only reproduced on. Real [performTouchInput] at screen coordinates, not a semantic
     * [performClick]: a semantic click finds a node by tag and clicks it directly, bypassing real
     * hit-testing and z-order — exactly the mechanism that let those two earlier bugs ship
     * unnoticed.
     */
    @Test
    fun `a real touch at each trailhead control's own screen coordinates reaches that control`() {
        var recordCalls = 0
        var returnCalls = 0
        setScreen(
            isRecording = true,
            isReturning = true,
            returnToStart = ReturnToStartInfo(bearingDegrees = 90.0, distanceMeters = 500.0, elevationDifferenceMeters = null),
            onToggleRecording = { recordCalls++ },
            onToggleReturning = { returnCalls++ },
        )
        searchAReferenceRegion()

        composeRule.onRoot().performTouchInput { click(centerOfTag("control-pill-record")) }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { click(centerOfTag("control-pill-return-to-vehicle")) }
        composeRule.waitForIdle()

        assertEquals("a real touch on the record button must reach it", 1, recordCalls)
        assertEquals("a real touch on the return-to-vehicle button must reach it", 1, returnCalls)
    }

    /**
     * Same as above, `isReturning = false` — the circular-base addendum's own "resting state"
     * (`DistanceArm`'s own doc comment: the arm's right end is a circle the same diameter as
     * [ControlPill]'s own width, congruent with the pill's own bottom cap, present at every width
     * the arm can reach including its minimum). Whether or not that circle stays mounted while not
     * actively returning, [ControlPill] still composes after [DistanceArm] in [TrailheadControls]'
     * own `Box` and wins any hit-test overlap at their shared junction (see that composable's own
     * doc comment) — so a real touch at each control's own screen coordinates must reach that
     * control here exactly as it does in the extended state above, not just when the arm happens to
     * be fully grown.
     */
    @Test
    fun `a real touch at each trailhead control's own screen coordinates reaches that control while not returning`() {
        var recordCalls = 0
        var returnCalls = 0
        setScreen(
            isRecording = true,
            isReturning = false,
            onToggleRecording = { recordCalls++ },
            onToggleReturning = { returnCalls++ },
        )
        searchAReferenceRegion()

        composeRule.onRoot().performTouchInput { click(centerOfTag("control-pill-record")) }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { click(centerOfTag("control-pill-return-to-vehicle")) }
        composeRule.waitForIdle()

        assertEquals("a real touch on the record button must reach it", 1, recordCalls)
        assertEquals("a real touch on the return-to-vehicle button must reach it", 1, returnCalls)
    }

    /**
     * The other half of item 5: a tap in the real empty space around [TrailheadControls] — the gap
     * between [MapIconBar]'s own bottom edge and [ControlPill]'s top edge — must still reach the
     * map underneath, not get silently swallowed by either surface's own bounding box. Reuses the
     * same fullscreen-restore signal `tapping the map while fullscreen restores chrome` already
     * relies on for exactly this reason: it is a real, already-proven way to observe "this tap
     * reached the map slot's own onTap," not a new assertion mechanism invented for this test.
     */
    @Test
    fun `a real touch in the gap above the control pill still reaches the map`() {
        setScreen(
            mapSlot = TappableStubMapSlot,
            isRecording = true,
            isReturning = true,
            returnToStart = ReturnToStartInfo(bearingDegrees = 90.0, distanceMeters = 500.0, elevationDifferenceMeters = null),
        )
        searchAReferenceRegion()
        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Tools").assertCountEquals(0)

        val pillBounds = composeRule.onNodeWithTag("control-pill").getUnclippedBoundsInRoot()
        val gapPoint = with(composeRule.density) {
            Offset(((pillBounds.left + pillBounds.right) / 2).toPx(), (pillBounds.top - 4.dp).toPx())
        }
        composeRule.onRoot().performTouchInput { click(gapPoint) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Tools").assertIsDisplayed()
    }

    /**
     * Same as above, checked beside [DistanceArm]'s own left edge instead of above [ControlPill] —
     * the arm's own width is animated and content-measured (see that composable's own doc
     * comment), so this is a second, independent point rather than assuming the first point's
     * result generalizes to the arm's own bounding box.
     */
    @Test
    fun `a real touch beside the distance arm still reaches the map`() {
        setScreen(
            mapSlot = TappableStubMapSlot,
            isRecording = true,
            isReturning = true,
            returnToStart = ReturnToStartInfo(bearingDegrees = 90.0, distanceMeters = 500.0, elevationDifferenceMeters = null),
        )
        searchAReferenceRegion()
        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Tools").assertCountEquals(0)

        val armBounds = composeRule.onNodeWithTag("distance-arm").getUnclippedBoundsInRoot()
        val besideArmPoint = with(composeRule.density) {
            Offset((armBounds.left - 8.dp).toPx().coerceAtLeast(0f), ((armBounds.top + armBounds.bottom) / 2).toPx())
        }
        composeRule.onRoot().performTouchInput { click(besideArmPoint) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Tools").assertIsDisplayed()
    }

    /**
     * The circular-base addendum's own containment claim, checked against real measured bounds
     * rather than only the geometry worked out on paper in `DistanceArm`'s own doc comment: the
     * arm's right edge must coincide with [ControlPill]'s own right edge (so the arm's circular
     * base — diameter equal to the pill's own measured width — is centred on the pill's own
     * vertical spine, not offset from it), and the arm's own vertical centre must coincide with the
     * return-to-vehicle control's own vertical centre (not the pill's own bottom edge, and not the
     * pill's own shape-cap centre either — see `TrailheadControls`' own doc comment for why those
     * three are three different heights). Both within 1px, the rounding a real layout pass can
     * introduce that paper geometry doesn't have to account for.
     */
    @Test
    fun `the distance arm's own circular base is centred on the return-to-vehicle control`() {
        setScreen(
            isRecording = true,
            isReturning = true,
            returnToStart = ReturnToStartInfo(bearingDegrees = 90.0, distanceMeters = 500.0, elevationDifferenceMeters = null),
        )
        searchAReferenceRegion()

        val armBounds = composeRule.onNodeWithTag("distance-arm").getUnclippedBoundsInRoot()
        val pillBounds = composeRule.onNodeWithTag("control-pill").getUnclippedBoundsInRoot()
        val returnButtonBounds = composeRule.onNodeWithTag("control-pill-return-to-vehicle").getUnclippedBoundsInRoot()

        val armRight = armBounds.right.value
        val pillRight = pillBounds.right.value
        assertTrue(
            "the arm's own right edge ($armRight) should coincide with the pill's own right edge " +
                "($pillRight) — that's what makes the two circles congruent",
            kotlin.math.abs(armRight - pillRight) <= 1f,
        )

        val armCenterY = (armBounds.top.value + armBounds.bottom.value) / 2
        val returnButtonCenterY = (returnButtonBounds.top.value + returnButtonBounds.bottom.value) / 2
        assertTrue(
            "the arm's own vertical centre ($armCenterY) should coincide with the return-to-vehicle " +
                "control's own vertical centre ($returnButtonCenterY), not the pill's own bottom edge",
            kotlin.math.abs(armCenterY - returnButtonCenterY) <= 1f,
        )
    }

    /**
     * Map/navigation redesign dispatch C, item 1: the search summary bar's own trailing chevron —
     * not a bare icon nobody would read as interactive — is the "reads as expandable without a
     * tap" affordance that item's own text asked for, replacing the leading magnifying glass this
     * test used to check (now decorative, `contentDescription = null`, since the chevron alone
     * carries the meaning). Addressed by [SEARCH_DROPDOWN_CHEVRON_TAG], not a bare
     * `onNodeWithContentDescription`, per this dispatch's own standing constraint: "anything
     * asserted as visible gets `onNodeWithText` or a testTag, never `onNodeWithContentDescription`
     * alone."
     */
    @Test
    fun `the search summary bar shows a chevron marking it as expandable for advanced search`() {
        setScreen()

        // useUnmergedTree = true: ActiveSearchSummary's own Surface(onClick = ...) merges its
        // descendants' semantics into one clickable node (Compose's default for any clickable
        // container), so the chevron's own testTag isn't independently visible in the default
        // merged tree even though it renders with real, on-screen bounds — verified directly
        // (bounds=Rect.fromLTRB(652.0, 30.0, 688.0, 66.0), size=36x36) before landing on this fix.
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_CHEVRON_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `tapping the search summary bar opens the advanced search dropdown, tapping again closes it`() {
        setScreen()

        composeRule.onNodeWithText("Fungi · August · no location set", substring = true).performClick()
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).assertIsDisplayed()

        composeRule.onNodeWithText("Fungi · August · no location set", substring = true).performClick()
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).assertDoesNotExist()
    }

    @Test
    fun `the advanced search dropdown does not open the full Tools drawer`() {
        setScreen()

        composeRule.onNodeWithText("Fungi · August · no location set", substring = true).performClick()

        // "Trip Planner" (the Tools drawer's own first section header) stands in for "the drawer
        // is open" — "Recent searches" doesn't work for this any more: it moved into the same
        // SearchDropdown this test just opened, so it would be legitimately on screen there too.
        composeRule.onNodeWithText("Trip Planner").assertIsNotDisplayed()
    }

    /**
     * Category selection through the real [AvailabilityViewModel]. Species/category search moved
     * out of the Tools drawer a second time — first into the top bar's own quick-search panel,
     * then (map/navigation redesign dispatch C, item 1) into this dropdown's [SpeciesSearchControls]
     * alongside recent searches, leaving the Tools drawer with no search surface of its own any
     * more. Synchronous, no debounce, no Popup dropdown, so this stays free of the timing fragility
     * a debounced-search-to-dropdown test would need — [SearchTaxaUseCase]'s own debounce/dropdown
     * mechanics are shared, pre-existing logic with no dedicated test anywhere in this codebase yet,
     * compact or otherwise; not a gap this task introduced.
     */
    @Test
    fun `picking a category chip in the search dropdown applies the filter through the real ViewModel`() {
        setScreen()

        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performClick()
        composeRule.onNodeWithText("Plants").performClick()

        assertEquals(TaxonFilter.PLANTS, viewModel.uiState.value.taxonFilter)
    }

    /**
     * Map/navigation redesign dispatch C's own explicit ask: "this repo has shipped pointer
     * interception four times. Extend Dispatch A's performTouchInput coverage to this surface" —
     * [AdvancedSearchDropdown] floats over the map exactly like [ControlPill]/[DistanceArm] did,
     * and it's new content over that same surface, so it gets the same real-touch proof those two
     * did rather than trusting Understory rule 1 (no [Surface], so nothing here should intercept a
     * touch meant for a sibling) on inspection alone. Real [performTouchInput] at screen
     * coordinates, not a semantic [performClick]: a semantic click bypasses real hit-testing and
     * z-order, exactly the mechanism the three earlier regressions this file's own history already
     * documents shipped through unnoticed.
     *
     * "Set on map" specifically, not one of the simpler controls: it's the one Item 2 of this
     * dispatch asks to hand off to [CentrePinLocationPickerOverlay] over this tab's own real map —
     * the reachable-through-a-real-touch proof and the hand-off wiring proof in one test, rather
     * than two smaller ones that would each only cover half of what actually matters here.
     */
    @Test
    fun `a real touch on the advanced search dropdown's Set on map button reaches it and opens the centre-pin picker over the real map`() {
        setScreen()

        composeRule.onNodeWithText("Fungi · August · no location set", substring = true).performClick()
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Advanced search").performClick()

        val setOnMapBounds = composeRule.onNodeWithText("Set on map").getUnclippedBoundsInRoot()
        val setOnMapCenter = with(composeRule.density) {
            Offset(
                ((setOnMapBounds.left + setOnMapBounds.right) / 2).toPx(),
                ((setOnMapBounds.top + setOnMapBounds.bottom) / 2).toPx(),
            )
        }
        composeRule.onRoot().performTouchInput { click(setOnMapCenter) }
        composeRule.waitForIdle()

        // The dropdown closed and the centre-pin picker's own confirm row is up — proof the touch
        // actually reached "Set on map", not some other node it happened to land on.
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("OK").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun `the foraging areas toggle lives in the Tools drawer, not floating over the map`() {
        setScreen()
        searchAReferenceRegion()

        // searchAReferenceRegion leaves the drawer closed (a real search closes it) — foraging
        // areas must not be reachable without opening it, the opposite of the earlier revision
        // where it floated as an overlay on the map itself. The drawer sheet stays composed
        // off-screen while closed, so its toggle row is still in the tree — assertIsNotDisplayed,
        // not assertDoesNotExist, is what actually distinguishes "closed" from "open" here.
        composeRule.onNodeWithText("Foraging areas").assertIsNotDisplayed()

        composeRule.onNodeWithText("Tools").performClick()

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
private val TappableStubMapSlot: MapSlot = { _, _, _, _, _, onTap, _, _, modifier ->
    Column(modifier.testTag("map-slot").clickable(onClick = onTap)) {
        Text("map")
    }
}

/** A fixed [Sighting], reported by [SightingTappableStubMapSlot] regardless of the real sightings list. */
private val TAPPED_SIGHTING = Sighting(
    observationId = 42L,
    taxonId = 47348L,
    scientificName = "Cantharellus formosus",
    commonName = "Chanterelle",
    lat = 45.33,
    lng = -122.64,
    observedOn = LocalDate.of(2026, 8, 1),
    photoUrl = null,
)

/** Exposes [onSightingTap] as a clickable surface reporting [TAPPED_SIGHTING], for the observation bubble tests. */
private val SightingTappableStubMapSlot: MapSlot = { _, _, _, _, _, _, onSightingTap, _, modifier ->
    Column(modifier.testTag("map-slot").clickable(onClick = { onSightingTap(TAPPED_SIGHTING, Offset.Zero, 0f) })) {
        Text("map")
    }
}

/**
 * Reports [TAPPED_SIGHTING] tapped at a fixed, non-zero screen position (300f, 400f — unlike
 * [SightingTappableStubMapSlot]'s [Offset.Zero]) with a caller-chosen [bearingDeg], for the
 * bubble-rotation tests. A real, non-corner position matters here specifically: the bubble needs
 * genuine room on every side to actually move as the reported bearing changes, which
 * [Offset.Zero] — already hard against the map's own top-left corner — would immediately clamp
 * away.
 */
private fun bearingReportingStubMapSlot(bearingDeg: Float): MapSlot = { _, _, _, _, _, _, onSightingTap, _, modifier ->
    Column(modifier.testTag("map-slot")) {
        Text(
            "dot",
            modifier = Modifier.testTag("sighting-dot")
                .clickable(onClick = { onSightingTap(TAPPED_SIGHTING, Offset(300f, 400f), bearingDeg) }),
        )
    }
}

/**
 * Exposes both [onSightingTap] (via a small "sighting-dot" tag) and the plain [onTap] (via a
 * separate "map-elsewhere" tag) as independently clickable surfaces — [SightingTappableStubMapSlot]
 * collapses both into one tag, which can't distinguish "tap the dot" from "tap elsewhere on the
 * map" the way the bubble's dismiss-on-elsewhere-tap test needs to.
 *
 * "map-elsewhere" carries a large top padding, not zero: [onSightingTap] here reports
 * `Offset.Zero` as the tapped marker's screen position, which anchors `ObservationBubble` near the
 * screen's own top edge — real [performClick] in this Compose UI test version dispatches through
 * real hit-testing at the target node's own center, not a bare semantics-action invocation, so a
 * "map-elsewhere" node placed immediately below "sighting-dot" (as this fixture originally was)
 * sits inside the bubble's own footprint once that footprint's top clearance shrinks (this file's
 * own `compassStripClearance`, a real dimension that changes as the compass strip's own height
 * does) — a tap dispatched there lands on the bubble's own tap-consuming Surface, not on this
 * clickable, and the test fails for a reason that has nothing to do with [onTap] itself. Real
 * hardware never taps "elsewhere" at a point still covered by the very bubble being dismissed;
 * this offset keeps the fixture's own layout matching that, well clear of anywhere
 * `AnchoredAtScreenPoint` could ever place the bubble on this suite's own w360dp-h640dp viewport.
 */
private val SightingAndPlainTapStubMapSlot: MapSlot = { _, _, _, _, _, onTap, onSightingTap, _, modifier ->
    Column(modifier.testTag("map-slot")) {
        Text("dot", modifier = Modifier.testTag("sighting-dot").clickable(onClick = { onSightingTap(TAPPED_SIGHTING, Offset.Zero, 0f) }))
        Text(
            "elsewhere",
            modifier = Modifier
                .padding(top = 200.dp)
                .testTag("map-elsewhere")
                .clickable(onClick = onTap),
        )
    }
}

/**
 * Regression coverage for the bug [com.forager.app.ui.map.MapOverlayContent.focusedObservationId]'s
 * own doc comment describes: "simulate-pan" mimics what the real [com.forager.app.ui.map.SightingsMap]'s
 * camera-idle listener now does after that fix — re-fire [onSightingTap] only when
 * `content.focusedObservationId` still names [TAPPED_SIGHTING], not unconditionally the way the old
 * internal-only `focusedSighting` var did (which kept re-firing after a caller-side dismissal,
 * since nothing about that dismissal ever reached back down into the map). A stub that fired
 * unconditionally on "simulate-pan" would let the bug back in without this test noticing; this one
 * is shaped to actually exercise [AvailabilityScreen]'s own `tappedSighting`/`focusedObservationId`
 * wiring, the same as the real map does.
 */
private val PannableSightingStubMapSlot: MapSlot = { _, content, _, _, _, _, onSightingTap, _, modifier ->
    Column(modifier.testTag("map-slot")) {
        Text("dot", modifier = Modifier.testTag("sighting-dot").clickable(onClick = { onSightingTap(TAPPED_SIGHTING, Offset.Zero, 0f) }))
        Text(
            "simulate-pan",
            modifier = Modifier.testTag("simulate-pan").clickable(onClick = {
                if (content.focusedObservationId == TAPPED_SIGHTING.observationId) {
                    onSightingTap(TAPPED_SIGHTING, Offset.Zero, 0f)
                }
            }),
        )
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
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
}

/** [DistanceUnit.KILOMETERS] fixed — this file's assertions are hardcoded to "km" text and have nothing to do with the km/mi preference. */
private object IconStackStubDistanceUnitPreferenceRepository : DistanceUnitPreferenceRepository {
    override suspend fun getDistanceUnit(): Result<DistanceUnit> = Result.success(DistanceUnit.KILOMETERS)
    override suspend fun setDistanceUnit(unit: DistanceUnit): Result<Unit> = Result.success(Unit)
}

private object IconStackStubAppThemePreferenceRepository : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}
