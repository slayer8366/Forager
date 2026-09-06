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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.Waypoint
import com.forager.app.domain.model.WaypointDesignation
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.DpOffset
import com.forager.app.domain.CompassProvider
import com.forager.app.domain.CompassReading
import com.forager.app.domain.CompassStatus
import com.forager.app.domain.HeadingUncertainty
import com.forager.app.domain.ComputeTrueHeadingUseCase
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.DeclinationProvider
import com.forager.app.domain.SystemCurrentTimeProvider
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
import com.forager.app.domain.TaxonSearchRepository
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
import com.forager.app.ui.map.CENTRE_PIN_CONFIRM_ROW_TAG
import com.forager.app.ui.map.MAP_MODE_PICKER_TAG
import com.forager.app.ui.map.MapSlot
import com.forager.app.ui.theme.Spacing
import com.forager.app.ui.track.TripStartWarning
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
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
        /** Navigation-chrome dispatch: a live `State` for tests that enter *and leave* navigation in one composition; when given, it overrides [isReturning]. */
        returning: State<Boolean>? = null,
        /** Alert-delivery dispatch, Item 3: the one-time silenced-phone warning shown in the map's Snackbar host. */
        tripStartWarning: TripStartWarning? = null,
        isOffTrack: Boolean = false,
        onToggleReturning: () -> Unit = {},
        mushroomRepository: TaxonSearchRepository = IconStackEmptyRepository,
        mapPreferencesRepository: MapPreferencesRepository = IconStackStubMapPreferencesRepository,
        // Navigation HUD stage one: a fake declination so the true-heading sign is pinned, the
        // HUD's target, and a clock the fix-age tests can hold still.
        computeTrueHeading: ComputeTrueHeadingUseCase = ComputeTrueHeadingUseCase(IconStackFixedDeclination(0f)),
        navigationTarget: Waypoint? = null,
        currentTime: CurrentTimeProvider = SystemCurrentTimeProvider,
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
            mapPreferencesRepository = mapPreferencesRepository,
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
                onMapFullscreenChanged = viewModel::onMapFullscreenChanged,
                onStartLogEntry = onStartLogEntry,
                onLocateMe = onLocateMe,
                isRecording = isRecording,
                tripStartWarning = tripStartWarning,
                onToggleRecording = onToggleRecording,
                returnToStart = returnToStart,
                isReturning = returning?.value ?: isReturning,
                isOffTrack = isOffTrack,
                onToggleReturning = onToggleReturning,
                compassProvider = compassProvider,
                mapSlot = mapSlot,
                computeTrueHeading = computeTrueHeading,
                navigationTarget = navigationTarget,
                currentTime = currentTime,
            )
        }
    }

    // ---- Navigation HUD stage one -----------------------------------------------------------
    // Fix at 45.52 N 122.68 W; the origin 0.01° of latitude due north (1112 m — "1.1 km", since
    // this harness's distance-unit stub reports kilometres). Magnetic 80° + a fake +15°
    // declination = 95° true, "95° E".

    private val hudFix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 12.5f, timestampEpochMillis = 1_700_000_000_000L)
    /** MgrsConverterTest's own Portland point ("10T ER 25118 40235"), with an altitude — for the HUD's second row. */
    private val portlandFix = LocationFix.Update(lat = 45.5152, lng = -122.6784, altitude = 210.0, accuracyMeters = null, timestampEpochMillis = 1_700_000_000_000L)
    private val hudOrigin = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start · Sep 5, 9:41 AM", note = "", createdAtEpochMillis = 1_700_000_000_000L, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private val hudClock = CurrentTimeProvider { 1_700_000_001_000L }

    private fun setNavigatingScreen(
        compassHeading: Float? = 80f,
        withFix: Boolean = true,
        fix: LocationFix.Update = hudFix,
        returning: State<Boolean>? = null,
        onToggleReturning: () -> Unit = {},
    ) = setScreen(
        compassProvider = FakeCompassProvider(compassHeading),
        locationTracker = if (withFix) IconStackFixedLocationTracker(fix) else IconStackNoOpLocationTracker,
        isRecording = true,
        isReturning = true,
        returning = returning,
        onToggleReturning = onToggleReturning,
        computeTrueHeading = ComputeTrueHeadingUseCase(IconStackFixedDeclination(15f)),
        navigationTarget = hudOrigin,
        currentTime = hudClock,
    )

    private fun textOfTag(tag: String): String =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    /**
     * The navigation-chrome dispatch's finding, stated plainly: this test's stage-one form asserted
     * the strip *and* the HUD both reading "95° E" while navigating — the "heading appears three
     * times" bug the owner saw on device, pinned here as a requirement. A test can encode a defect
     * as a requirement, and this one did; it stayed green through the whole of stage one because
     * it never questioned that both should be there. Now: while navigating the strip is not
     * composed at all, and the heading text appears exactly **once** on screen — asserted as a
     * node count, not merely the HUD's presence — and that one reading is *true* north (80°
     * magnetic + 15° declination). Fails with the strip composed while navigating (two "95° E"
     * nodes) and with the HUD back on magnetic ("80° E").
     */
    @Test
    fun `while navigating the compass strip is absent and the true heading appears exactly once`() {
        setNavigatingScreen()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("compass-elevation-strip").assertCountEquals(0)
        composeRule.onAllNodesWithText("95° E").assertCountEquals(1)
        assertEquals("95° E", textOfTag(NAVIGATION_HUD_HEADING_TAG))
    }

    // ── Compass-reliability dispatch ────────────────────────────────────────────────────────
    //
    // The fake bypasses AndroidCompassProvider (its own Robolectric tests drive the real sensor
    // path); here the reading's uncertainty and timestamp are handed straight to the judge in
    // rememberTrueHeading. 0.35 rad on the device is 20.05° — the literal used below is the
    // degrees the provider would have emitted.

    /**
     * The strip, not navigating: an untrusted heading reads "Compass unreliable" in the heading
     * slot, the number withheld; elevation and coordinates beside it are GPS and untouched. Then
     * recovery: good readings held for two seconds bring the number back — and the number is the
     * *new* reading exactly, not a blend with the 200° the sensor reported while distorted (the
     * smoother reset). Fails with the judge bypassed (the 20° reading shows as "90° E") and with the
     * reset removed (a blend, not "90° E", after recovery).
     */
    @Test
    fun `an untrusted heading reads Compass unreliable on the strip, and a recovered one snaps back to the new reading`() {
        // Seeded trusted at 200° first, so the smoother holds history when the reading goes bad:
        // without the reset, recovery at 90° would show a blend (~175°), not 90°.
        val compass = FakeCompassProvider(200f)
        setScreen(compassProvider = compass, locationTracker = IconStackFixedLocationTracker(portlandFix))
        searchAReferenceRegion()
        assertEquals("200° S", textOfTag(COMPASS_STRIP_HEADING_TAG))

        compass.emit(CompassReading(200f, HeadingUncertainty.Estimated(20.05f), timestampMillis = 0L))
        composeRule.waitForIdle()
        assertEquals("Compass unreliable", textOfTag(COMPASS_STRIP_HEADING_TAG))
        composeRule.onAllNodesWithText("200° S").assertCountEquals(0)
        composeRule.onNodeWithText("210 m").assertIsDisplayed()
        composeRule.onNodeWithText("10T ER 25118 40235").assertIsDisplayed()

        // Good readings at 0, 1000 and 2000 ms after the last bad one: still unreliable at 1000 ms
        // (the hold is 2 s), clear at 2000 ms — and the heading shown is 90° exactly.
        compass.emit(CompassReading(90f, HeadingUncertainty.Estimated(5f), timestampMillis = 1_000L))
        composeRule.waitForIdle()
        assertEquals("Compass unreliable", textOfTag(COMPASS_STRIP_HEADING_TAG))
        compass.emit(CompassReading(90f, HeadingUncertainty.Estimated(5f), timestampMillis = 2_000L))
        composeRule.waitForIdle()
        assertEquals("Compass unreliable", textOfTag(COMPASS_STRIP_HEADING_TAG))
        compass.emit(CompassReading(90f, HeadingUncertainty.Estimated(5f), timestampMillis = 3_000L))
        composeRule.waitForIdle()
        assertEquals("90° E", textOfTag(COMPASS_STRIP_HEADING_TAG))
    }

    /**
     * The HUD, navigating: the heading label reads the message, the needle and its text are
     * withheld, the distance is untouched — and the message is on screen exactly once (the strip is
     * absent while navigating). The status-level fallback path is driven here too: a LOW status
     * with no estimate produces the same state.
     */
    @Test
    fun `an untrusted heading on the HUD withholds the needle and its text and reads Compass unreliable once`() {
        // Seeded trusted at 80° ("95° E" true) so the smoother holds history; recovery below is at a
        // different heading, 170° (185° true), so a blend would be visible (~118° true) if the
        // reset were missing.
        val compass = FakeCompassProvider(80f)
        setScreen(
            compassProvider = compass,
            locationTracker = IconStackFixedLocationTracker(hudFix),
            isRecording = true,
            isReturning = true,
            computeTrueHeading = ComputeTrueHeadingUseCase(IconStackFixedDeclination(15f)),
            navigationTarget = hudOrigin,
            currentTime = hudClock,
        )
        composeRule.waitForIdle()
        assertEquals("95° E", textOfTag(NAVIGATION_HUD_HEADING_TAG))

        compass.emit(CompassReading(80f, HeadingUncertainty.Estimated(20.05f), timestampMillis = 0L))
        composeRule.waitForIdle()
        assertEquals("Compass unreliable", textOfTag(NAVIGATION_HUD_HEADING_TAG))
        assertEquals("", textOfTag(NAVIGATION_HUD_TARGET_TAG))
        assertEquals("1.1 km", textOfTag(NAVIGATION_HUD_DISTANCE_TAG))
        composeRule.onAllNodesWithText("Compass unreliable").assertCountEquals(1)
        composeRule.onAllNodesWithText("95° E").assertCountEquals(0)

        // The fallback path, on its own: no estimate, status LOW — the same state.
        compass.emit(CompassReading(80f, HeadingUncertainty.Status(CompassStatus.LOW), timestampMillis = 500L))
        composeRule.waitForIdle()
        assertEquals("Compass unreliable", textOfTag(NAVIGATION_HUD_HEADING_TAG))

        // Recovery through the status path: HIGH held for two seconds, then the needle is back —
        // at the new reading exactly: 170° magnetic + 15° declination = 185°, and the target due
        // north from a device facing 185° is a 175° turn.
        compass.emit(CompassReading(170f, HeadingUncertainty.Status(CompassStatus.HIGH), timestampMillis = 1_000L))
        composeRule.waitForIdle()
        assertEquals("Compass unreliable", textOfTag(NAVIGATION_HUD_HEADING_TAG))
        compass.emit(CompassReading(170f, HeadingUncertainty.Status(CompassStatus.HIGH), timestampMillis = 3_000L))
        composeRule.waitForIdle()
        assertEquals("185° S", textOfTag(NAVIGATION_HUD_HEADING_TAG))
        assertEquals("Turn 175°", textOfTag(NAVIGATION_HUD_TARGET_TAG))
    }

    /**
     * The other half of item 1: the strip comes back when navigation ends — asserted by leaving,
     * not by a second screen that never navigated. Heading still exactly once, now in the strip.
     */
    @Test
    fun `leaving navigation brings the compass strip back with the heading, still exactly once`() {
        val returning = mutableStateOf(true)
        setNavigatingScreen(returning = returning)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("compass-elevation-strip").assertCountEquals(0)

        composeRule.runOnUiThread { returning.value = false }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compass-elevation-strip").assertIsDisplayed()
        composeRule.onAllNodesWithTag(NAVIGATION_HUD_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithText("95° E").assertCountEquals(1)
        assertEquals("95° E", textOfTag(COMPASS_STRIP_HEADING_TAG))
    }

    @Test
    fun `the HUD shows the straight-line distance to the origin in the display unit and the turn to it`() {
        setNavigatingScreen()
        composeRule.waitForIdle()

        assertEquals("1.1 km", textOfTag(NAVIGATION_HUD_DISTANCE_TAG))
        // Target due north (0°) from a device facing 95° true: 265° relative, a left turn.
        assertEquals("Turn 265°", textOfTag(NAVIGATION_HUD_TARGET_TAG))
    }

    /**
     * Item 4, the HUD's side: with no fix the HUD says one thing, once — the heading label is a
     * dash, the status line carries the message, and there is no elevation/coordinates row to
     * repeat the same cause twice more. "Compass needs a fix" and "Waiting for a fix" are gone,
     * not kept alongside. Magnetic is still never shown (the "80° E" count).
     */
    @Test
    fun `with no fix the HUD shows one message, a dash for the heading, and no elevation or coordinates row`() {
        setNavigatingScreen(withFix = false)
        composeRule.waitForIdle()

        assertEquals("—", textOfTag(NAVIGATION_HUD_HEADING_TAG))
        assertEquals("Location services unavailable", textOfTag(NAVIGATION_HUD_STATUS_TAG))
        composeRule.onAllNodesWithText("Location services unavailable").assertCountEquals(1)
        composeRule.onAllNodesWithTag(NAVIGATION_HUD_COORDINATES_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(NAVIGATION_HUD_ELEVATION_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithText("Compass needs a fix").assertCountEquals(0)
        composeRule.onAllNodesWithText("Waiting for a fix").assertCountEquals(0)
        composeRule.onAllNodesWithText("80° E").assertCountEquals(0)
    }

    /**
     * Item 2: what the strip was carrying that the HUD was not — elevation and coordinates — now
     * on the HUD's second row. Pinned against MgrsConverterTest's own Portland point, the same
     * value the strip's own MGRS test uses, so the two readouts are held to one converter.
     */
    @Test
    fun `while navigating the HUD's second row shows the elevation and the MGRS grid reference`() {
        setNavigatingScreen(fix = portlandFix)
        composeRule.waitForIdle()

        assertEquals("210 m", textOfTag(NAVIGATION_HUD_ELEVATION_TAG))
        assertEquals("10T ER 25118 40235", textOfTag(NAVIGATION_HUD_COORDINATES_TAG))
        composeRule.onAllNodesWithText("10T ER 25118 40235").assertCountEquals(1)
    }

    /**
     * The affordance most at risk (dispatch verification list): the coordinates toggle, by real
     * coordinate touches at five points across its own bounds — not its centre alone, a finger is
     * not a point — in fullscreen with the icon cluster minimised, the pulse's tightest reachable
     * set (restore handle, this toggle, the map). Each touch must flip the format and nothing
     * else: a touch that fell through to the map would exit fullscreen (the nav's "Tools" would
     * return) and not count. Same shape as the exit's own test above.
     */
    @Test
    fun `the coordinates toggle is reachable by real touches across its bounds while navigating, in fullscreen with the cluster minimised`() {
        setNavigatingScreen(fix = portlandFix)
        composeRule.waitForIdle()
        touchFullscreenRow("Fullscreen")
        composeRule.onRoot().performTouchInput { click(centerOfContentDescription("Hide map controls")) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Show map controls").assertIsDisplayed()
        assertEquals("10T ER 25118 40235", textOfTag(NAVIGATION_HUD_COORDINATES_TAG))

        val bounds = composeRule.onNodeWithTag(NAVIGATION_HUD_COORDINATES_TAG).getUnclippedBoundsInRoot()
        val inset = 4.dp
        val samples = listOf(
            DpOffset((bounds.left + bounds.right) / 2, (bounds.top + bounds.bottom) / 2),
            DpOffset(bounds.left + inset, bounds.top + inset),
            DpOffset(bounds.right - inset, bounds.top + inset),
            DpOffset(bounds.left + inset, bounds.bottom - inset),
            DpOffset(bounds.right - inset, bounds.bottom - inset),
        )
        val formats = listOf("10T ER 25118 40235", "Lat. 45.5152 Long. -122.6784")
        samples.forEachIndexed { index, sample ->
            val point = with(composeRule.density) { Offset(sample.x.toPx(), sample.y.toPx()) }
            composeRule.onRoot().performTouchInput { click(point) }
            composeRule.waitForIdle()
            assertEquals("touch $index at $sample must flip the format", formats[(index + 1) % 2], textOfTag(NAVIGATION_HUD_COORDINATES_TAG))
            // Still in fullscreen: no touch fell through to the map. The nav is the witness.
            composeRule.onAllNodesWithText("Tools").assertCountEquals(0)
        }
    }

    /**
     * The toggle state is one value shared by the strip and the HUD (hoisted to `CompactMapTab`),
     * so a format chosen while navigating is the format the strip shows on exit — CLAUDE.md's UX
     * default: user-set state does not reset on its own. Fails with per-leaf state (the strip
     * would come back on MGRS). A semantic click is enough here: the claim is state, not routing —
     * routing is the real-touch test above.
     */
    @Test
    fun `a coordinate format chosen in the HUD is the format the strip shows after leaving navigation`() {
        val returning = mutableStateOf(true)
        setNavigatingScreen(fix = portlandFix, returning = returning)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(NAVIGATION_HUD_COORDINATES_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals("Lat. 45.5152 Long. -122.6784", textOfTag(NAVIGATION_HUD_COORDINATES_TAG))

        composeRule.runOnUiThread { returning.value = false }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compass-elevation-strip").assertIsDisplayed()
        composeRule.onNodeWithText("Lat. 45.5152 Long. -122.6784").assertIsDisplayed()
        composeRule.onAllNodesWithText("10T ER 25118 40235").assertCountEquals(0)
    }

    /**
     * Alert-delivery dispatch, Item 3: the trip-start warning is shown once, as a Snackbar in the
     * map Scaffold's existing host, and is absent when there is nothing to warn about — asserted
     * by node count in both directions. The copy is the owner's literal. Fails with the
     * `LaunchedEffect(tripStartWarning?.id)` removed from AvailabilityScreen (no node, times out).
     */
    @Test
    fun `a trip-start warning shows once as a Snackbar over the map and a null warning shows nothing`() {
        val message = "Your phone is silenced. If you go off track, the alert may not be felt."
        setScreen(isRecording = true, tripStartWarning = TripStartWarning(id = 1, message = message))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(message).assertCountEquals(1)
    }

    @Test
    fun `no trip-start warning means no Snackbar`() {
        val message = "Your phone is silenced. If you go off track, the alert may not be felt."
        setScreen(isRecording = true, tripStartWarning = null)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(message).assertCountEquals(0)
    }

    @Test
    fun `with no compass sensor the HUD shows the distance and no bearing text`() {
        setNavigatingScreen(compassHeading = null)
        composeRule.waitForIdle()

        assertEquals("Compass unavailable", textOfTag(NAVIGATION_HUD_HEADING_TAG))
        // Was "Bearing 0° N" until the two-data-corrections dispatch (Part C, owner-authorised
        // change to this assertion). Asserted by node count as well as by the tagged text: no node
        // anywhere on the screen carries a bearing.
        assertEquals("", textOfTag(NAVIGATION_HUD_TARGET_TAG))
        composeRule.onAllNodesWithText("Bearing", substring = true).assertCountEquals(0)
        assertEquals("1.1 km", textOfTag(NAVIGATION_HUD_DISTANCE_TAG))
    }

    @Test
    fun `a fix older than five minutes withholds the distance and says how old it is`() {
        setScreen(
            compassProvider = FakeCompassProvider(80f),
            locationTracker = IconStackFixedLocationTracker(hudFix),
            isRecording = true,
            isReturning = true,
            computeTrueHeading = ComputeTrueHeadingUseCase(IconStackFixedDeclination(15f)),
            navigationTarget = hudOrigin,
            currentTime = CurrentTimeProvider { 1_700_000_000_000L + 6L * 60L * 1_000L },
        )
        composeRule.waitForIdle()

        assertEquals("—", textOfTag(NAVIGATION_HUD_DISTANCE_TAG))
        assertEquals("No fix for 6 min", textOfTag(NAVIGATION_HUD_STATUS_TAG))
    }

    @Test
    fun `the HUD is not composed while not returning, and the strip is`() {
        setScreen(isRecording = true, isReturning = false, navigationTarget = hudOrigin)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(NAVIGATION_HUD_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag("compass-elevation-strip").assertIsDisplayed()
    }

    /**
     * The exit, by real coordinate touches at five points across the button's own bounds — not
     * its centre alone (CLAUDE.md: a finger is not a point) — in fullscreen with the icon cluster
     * minimised, the pulse's tightest reachable set. Each touch must reach the exit and nothing
     * else: a touch that fell through to the map would exit fullscreen instead and not count.
     */
    @Test
    fun `the exit is reachable by real touches across its bounds, in fullscreen with the cluster minimised`() {
        var exits = 0
        setNavigatingScreen(onToggleReturning = { exits++ })
        composeRule.waitForIdle()
        touchFullscreenRow("Fullscreen")
        composeRule.onRoot().performTouchInput { click(centerOfContentDescription("Hide map controls")) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Show map controls").assertIsDisplayed()

        val bounds = composeRule.onNodeWithTag(NAVIGATION_HUD_EXIT_TAG).getUnclippedBoundsInRoot()
        val inset = 6.dp
        val samples = listOf(
            DpOffset((bounds.left + bounds.right) / 2, (bounds.top + bounds.bottom) / 2),
            DpOffset(bounds.left + inset, bounds.top + inset),
            DpOffset(bounds.right - inset, bounds.top + inset),
            DpOffset(bounds.left + inset, bounds.bottom - inset),
            DpOffset(bounds.right - inset, bounds.bottom - inset),
        )
        samples.forEachIndexed { index, sample ->
            val point = with(composeRule.density) { Offset(sample.x.toPx(), sample.y.toPx()) }
            composeRule.onRoot().performTouchInput { click(point) }
            composeRule.waitForIdle()
            assertEquals("touch $index at $sample must reach the exit", index + 1, exits)
            // Still in fullscreen: no touch fell through to the map, whose tap would restore chrome
            // and slide the nav back in. The nav is the witness, not the icon bar's exit-fullscreen
            // row — that row is unmounted here precisely because the cluster is minimised.
            composeRule.onAllNodesWithText("Tools").assertCountEquals(0)
        }
    }

    /**
     * Navigation-chrome amendment, Fix 1: inside the approach threshold the HUD shows the distance
     * exactly once — the target column is empty under its dimmed icon. A first cut put the distance
     * in that column too and the owner read "9 ft · 9 ft · Approaching" on device. Node count, as
     * the heading test does — the duplicate heading was caught that way and this is the same class
     * of bug. The fix is 10 m north of the origin with 12.5 m accuracy (threshold 25 m).
     */
    @Test
    fun `inside the approach threshold the distance appears exactly once and the target column is empty`() {
        setNavigatingScreen(fix = hudFix.copy(lat = 45.53009))
        composeRule.waitForIdle()

        // Location-accuracy dispatch, item 2: 10 m from the origin with 12.5 m accuracy is inside
        // the error circle, so the slot reads the accuracy ("within 13 m", 12.5 rounded half up),
        // not a to-the-metre "10 m" the fix cannot support.
        assertEquals("within 13 m", textOfTag(NAVIGATION_HUD_DISTANCE_TAG))
        assertEquals("Approaching", textOfTag(NAVIGATION_HUD_STATUS_TAG))
        assertEquals("", textOfTag(NAVIGATION_HUD_TARGET_TAG))
        composeRule.onAllNodesWithText("within 13 m").assertCountEquals(1)
        composeRule.onAllNodesWithText("10 m").assertCountEquals(0)
    }

    /**
     * Location-accuracy dispatch, item 1, through the real entry point (the tracker's flow into
     * the ViewModel into the HUD): a 12.5 m fix 1.1 km from the origin lands; a 60 m fix 500 m
     * closer arrives next and must not — the HUD keeps reading the held fix's distance. Fails with
     * the gate removed: the rejected fix is 612 m from the origin, which with 60 m accuracy rounds
     * to the 100 m step, so "≈ 600 m" replaces "1.1 km".
     */
    @Test
    fun `a fix worse than 50 m does not reach the HUD - the held fix's distance stays`() {
        val fixes = MutableSharedFlow<LocationFix>(replay = 1)
        setScreen(
            compassProvider = FakeCompassProvider(80f),
            locationTracker = IconStackFakeLocationTracker(fixes),
            isRecording = true,
            isReturning = true,
            computeTrueHeading = ComputeTrueHeadingUseCase(IconStackFixedDeclination(15f)),
            navigationTarget = hudOrigin,
            currentTime = hudClock,
        )
        composeRule.waitForIdle()

        fixes.tryEmit(hudFix)
        composeRule.waitForIdle()
        assertEquals("1.1 km", textOfTag(NAVIGATION_HUD_DISTANCE_TAG))

        // 0.0045° of latitude north of the fix is 500 m; 60 m accuracy fails the 50 m gate.
        fixes.tryEmit(hudFix.copy(lat = 45.5245, accuracyMeters = 60f, timestampEpochMillis = 1_700_000_001_000L))
        composeRule.waitForIdle()

        assertEquals("1.1 km", textOfTag(NAVIGATION_HUD_DISTANCE_TAG))
        composeRule.onAllNodesWithText("≈ 600 m").assertCountEquals(0)
    }

    // ── Navigation-chrome amendment, Fix 2: back asks, and never exits ──────────────────────
    //
    // These press back through the Activity's OnBackPressedDispatcher (pressBack), which is where
    // AvailabilityScreen's BackHandlers live. On a device the open AlertDialog is its own window
    // and consumes the dismissing press itself; under Robolectric that press reaches the Activity
    // instead, where the same handler toggles the prompt off. So the "back dismisses the prompt"
    // tests below exercise the handler's TOGGLE path, not the dialog window's own dismiss — the
    // guarantee (back never exits navigation) holds by either route, but the dialog's own back
    // handling is device-only coverage. Said plainly so this does not read as coverage it isn't.

    /** The stage-one test here asserted `exits == 1` after one back press — the behaviour the amendment removes. Inverted, not absorbed. */
    @Test
    fun `system back while navigating raises the exit prompt and does not exit`() {
        var exits = 0
        setNavigatingScreen(onToggleReturning = { exits++ })
        composeRule.waitForIdle()

        pressBack()

        composeRule.onNodeWithTag(EXIT_NAVIGATION_PROMPT_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Exit navigation?").assertIsDisplayed()
        composeRule.onNodeWithText("Your track will keep recording.").assertIsDisplayed()
        composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).assertIsDisplayed()
        assertEquals(0, exits)
    }

    @Test
    fun `back while the exit prompt is open dismisses it and leaves navigation running`() {
        var exits = 0
        setNavigatingScreen(onToggleReturning = { exits++ })
        composeRule.waitForIdle()
        pressBack()
        composeRule.onNodeWithTag(EXIT_NAVIGATION_PROMPT_TAG).assertIsDisplayed()

        pressBack()

        composeRule.onAllNodesWithTag(EXIT_NAVIGATION_PROMPT_TAG).assertCountEquals(0)
        // Navigation still active — not merely "the dialog is gone".
        composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).assertIsDisplayed()
        assertEquals(0, exits)
    }

    /**
     * The whole guarantee: repeated presses never exit and never reach the app-exit handler. A
     * test of one press would not catch a second press confirming. Seven presses: the prompt
     * alternates open/closed, exits stays 0, the HUD stays, no "Tap Back Button Again to Exit"
     * toast (the exit handler's own witness in AvailabilityScreenBackNavigationTest), and the
     * Activity is not finishing.
     */
    @Test
    fun `repeated back presses while navigating never exit navigation and never close the app`() {
        var exits = 0
        setNavigatingScreen(onToggleReturning = { exits++ })
        composeRule.waitForIdle()

        repeat(7) { press ->
            pressBack()
            val promptExpected = press % 2 == 0
            composeRule.onAllNodesWithTag(EXIT_NAVIGATION_PROMPT_TAG).assertCountEquals(if (promptExpected) 1 else 0)
            composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).assertIsDisplayed()
            assertEquals("press ${press + 1} must not exit navigation", 0, exits)
            assertEquals("press ${press + 1} must not reach the app-exit handler", null, ShadowToast.getTextOfLatestToast())
            assertFalse("press ${press + 1} must not finish the Activity", composeRule.activity.isFinishing)
        }
    }

    @Test
    fun `Exit on the prompt exits navigation once - Keep navigating does not`() {
        var exits = 0
        setNavigatingScreen(onToggleReturning = { exits++ })
        composeRule.waitForIdle()

        pressBack()
        composeRule.onNodeWithTag(EXIT_NAVIGATION_PROMPT_KEEP_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(EXIT_NAVIGATION_PROMPT_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).assertIsDisplayed()
        assertEquals(0, exits)

        pressBack()
        composeRule.onNodeWithTag(EXIT_NAVIGATION_PROMPT_EXIT_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(1, exits)
        composeRule.onAllNodesWithTag(EXIT_NAVIGATION_PROMPT_TAG).assertCountEquals(0)
    }

    /**
     * The reordering (owner's ruling): navigation is the LAST thing backed out of. In fullscreen,
     * back exits fullscreen and leaves navigation untouched — no prompt, no exit; the next press
     * asks. Stage one did the reverse (CompactMapTab's handler, the deepest, exited navigation
     * first), which no test caught because none pressed back while navigating with anything
     * else open. The nav's "Tools" returning is the fullscreen-exit witness.
     */
    @Test
    fun `back while navigating in fullscreen exits fullscreen first and only the next press asks about navigation`() {
        var exits = 0
        setNavigatingScreen(onToggleReturning = { exits++ })
        composeRule.waitForIdle()
        touchFullscreenRow("Fullscreen")
        composeRule.onAllNodesWithText("Tools").assertCountEquals(0)

        pressBack()

        composeRule.onNodeWithText("Tools").assertIsDisplayed()
        composeRule.onAllNodesWithTag(EXIT_NAVIGATION_PROMPT_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).assertIsDisplayed()
        assertEquals(0, exits)

        pressBack()

        composeRule.onNodeWithTag(EXIT_NAVIGATION_PROMPT_TAG).assertIsDisplayed()
        assertEquals(0, exits)
    }

    // The search-dropdown ordering case lives in AvailabilityScreenBackNavigationTest: this
    // fixture hits the documented Robolectric dropdown-dismissal failure
    // (docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md) — a diagnostic run here
    // showed the dropdown's handler taking the press (no prompt, no exit) but the dropdown never
    // unmounting — so the claim is asserted where the dropdown demonstrably closes on back.

    /**
     * The ✕ is a deliberate press and still exits directly, with no prompt composed. (The control
     * pill's toggle is wired the same way — `onClick = onToggleReturning`, nothing in between —
     * but its semantic click is this suite's documented Robolectric no-op, see
     * docs/audits/2026-08-30-return-to-vehicle-semantics-click-noop.md, so it is not asserted
     * here; the real-touch exit test above covers the ✕ by coordinates.)
     */
    @Test
    fun `the HUD's close button exits directly without the prompt`() {
        var exits = 0
        setNavigatingScreen(onToggleReturning = { exits++ })
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(NAVIGATION_HUD_EXIT_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(1, exits)
        composeRule.onAllNodesWithTag(EXIT_NAVIGATION_PROMPT_TAG).assertCountEquals(0)
    }

    /**
     * Once navigation has ended, back reaches what it reached before this change: the go-home
     * exit handler, whose first press only warns. Its toast is the witness, the same one
     * AvailabilityScreenBackNavigationTest uses. A prompt left open when navigation ends is
     * dismissed with it.
     */
    @Test
    fun `after navigation ends back reaches the exit warning again, unchanged`() {
        val returning = mutableStateOf(true)
        setNavigatingScreen(returning = returning)
        composeRule.waitForIdle()
        pressBack()
        composeRule.onNodeWithTag(EXIT_NAVIGATION_PROMPT_TAG).assertIsDisplayed()

        composeRule.runOnUiThread { returning.value = false }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(EXIT_NAVIGATION_PROMPT_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(NAVIGATION_HUD_TAG).assertCountEquals(0)

        pressBack()

        assertEquals("Tap Back Button Again to Exit", ShadowToast.getTextOfLatestToast())
        assertFalse(composeRule.activity.isFinishing)
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
        composeRule.onNodeWithText("Latitude").performScrollTo().performTextReplacement("45.326")
        composeRule.onNodeWithText("Longitude").performTextReplacement("-122.634")
        // performScrollTo(): radius and month, promoted to SearchDropdown's own top level ahead of
        // this section (map/navigation redesign dispatch D), push "Search this location" below the
        // dropdown's own bounded, scrolled viewport on this suite's w360dp-h640dp config — without
        // this, performClick() reports the tap against this node's own (correct, but currently
        // off-screen) semantic bounds, which lands on nothing actually rendered there, so the
        // region never gets set and every downstream assertion in this file that depends on
        // searchAReferenceRegion having actually run a search fails silently.
        composeRule.onNodeWithText("Search this location").performScrollTo().performClick()
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

    /** Same as [centerOfTag], keyed by contentDescription — MapIconBarMinimizeHandle and MapIconBarRestoreHandle (fullscreen-fixes dispatch, Item 3) carry no testTag of their own, only a contentDescription. */
    private fun centerOfContentDescription(description: String): Offset {
        val bounds = composeRule.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()
        return with(composeRule.density) {
            Offset(((bounds.left + bounds.right) / 2).toPx(), ((bounds.top + bounds.bottom) / 2).toPx())
        }
    }

    /**
     * Icon-bar-drag-refinements dispatch: a real long-press-then-drag on [tag] (the minimize or
     * restore handle — the only two composables [mapIconBarDragModifier][CompactMapTab] is
     * attached to), by ([dxDp], [dyDp]) from its own current centre. `advanceEventTime(600)` clears
     * `detectDragGesturesAfterLongPress`'s own long-press threshold (Android's default is 500ms)
     * before the move, matching how a real hold-and-drag gesture actually behaves — a plain
     * `swipe`/`click` wouldn't clear that threshold and would just look like a tap or an ignored
     * short drag.
     */
    private fun dragIconBarHandle(tag: String, dxDp: Dp = 0.dp, dyDp: Dp = 0.dp) {
        val start = centerOfTag(tag)
        val delta = with(composeRule.density) { Offset(dxDp.toPx(), dyDp.toPx()) }
        composeRule.onRoot().performTouchInput {
            down(start)
            advanceEventTime(600)
            moveTo(start + delta)
            advanceEventTime(50)
            up()
        }
        composeRule.waitForIdle()
    }

    /**
     * A single [ComposeContentTestRule.waitForIdle] after [setScreen] is not always enough for
     * [ControlPill]'s *second* row (`control-pill-return-to-vehicle`) to have its own
     * `Modifier.clickable` gesture detector armed. Confirmed empirically, not by inspection: a real
     * [androidx.compose.ui.test.performClick]/[performTouchInput] on that row silently no-ops (the
     * semantics node is found, reports `Enabled`, and carries the right `OnClick` action, but
     * invoking it never reaches [onToggleReturning]) after only one `waitForIdle()`.
     *
     * **Correction to this comment's own earlier claim.** This used to say the failure was "only
     * reliable when each affected test runs alone" — a real cross-test JVM/Robolectric leak. That
     * turned out to be wrong: true `forkEvery = 1` per-test JVM isolation, with a single `--tests`
     * selection and no other test in the run at all, still fails deterministically at current HEAD.
     * A commit bisection then showed the failure is genuinely absent before `72f0a54` (the search-
     * bar redesign) and genuinely present after it — a real regression introduced by that commit,
     * not cross-test contamination. See
     * `docs/audits/2026-08-30-return-to-vehicle-semantics-click-noop.md` for the full investigation:
     * what's ruled out (the compass-strip-clearance padding specifically; a moved semantics merge
     * boundary around this row, checked by comparing the merged/unmerged tree before and after
     * `72f0a54`), the confirmed-working real-device result (three real `adb shell input tap` events
     * on unmodified `3df717b`, each producing the correct UI state change), and what's still open
     * (why this specific Robolectric-hosted semantics click no-ops when the identical production
     * wiring fires correctly on a real touch).
     */
    private fun retryClick(tag: String, calls: () -> Int, maxAttempts: Int = 10) {
        composeRule.waitForIdle()
        repeat(maxAttempts) {
            if (calls() > 0) return
            composeRule.onNodeWithTag(tag).performClick()
            composeRule.waitForIdle()
        }
    }

    private fun retryTouch(tag: String, calls: () -> Int, maxAttempts: Int = 10) {
        composeRule.waitForIdle()
        repeat(maxAttempts) {
            if (calls() > 0) return
            composeRule.onRoot().performTouchInput { click(centerOfTag(tag)) }
            composeRule.waitForIdle()
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

    // @Ignore: harness-only dismissal failure — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md
    @Ignore("Harness-only failure, confirmed working on a real device — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md")
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

    // @Ignore: harness-only dismissal failure — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md
    @Ignore("Harness-only failure, confirmed working on a real device — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md")
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

    // @Ignore: harness-only dismissal failure — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md
    @Ignore("Harness-only failure, confirmed working on a real device — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md")
    @Test
    fun `fullscreen hides the top strip and bottom nav but keeps the map mounted`() {
        setScreen()
        searchAReferenceRegion()
        CountingStubMapSlotState.compositionCount = 0

        // "Tools" (bottom nav) and SearchEntryBar's own query field (top strip) stand in for the
        // two chrome regions decision #5 hides together — there's no more app-bar tune icon to
        // check now that species/category search moved into the drawer and "Advanced search" moved
        // into AdvancedSearchDropdown. Matched by ACTIVE_SEARCH_SUMMARY_TAG rather than the read-
        // only summary text the top strip used to show: SearchEntryBar replaced that with a real,
        // always-present field (map/navigation redesign dispatch D), so there is no summary text
        // left to match at all.
        composeRule.onNodeWithText("Tools").assertIsDisplayed()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("map-slot").assertIsDisplayed()
        assertEquals("the map slot must not be torn down and recomposed from scratch on a chrome toggle", 0, CountingStubMapSlotState.compositionCount)
        composeRule.onAllNodesWithText("Tools").assertCountEquals(0)
        composeRule.onAllNodesWithTag(ACTIVE_SEARCH_SUMMARY_TAG).assertCountEquals(0)
        // Regression coverage for a real bug this exact toggle produced once: CompactMapTab's own
        // topInset (the clearance the compass strip/bubble/filter-chip need below SearchEntryBar's
        // overlay on the Map tab) was passed as a flat searchBarHeight with no isMapFullscreen
        // check, so while fullscreen -- where searchBarSlot itself correctly goes empty, per this
        // test's own "top strip" assertions above -- the strip still got pushed down by a bar-
        // shaped gap with nothing in it, confirmed on a real device by a screenshot showing the
        // strip stranded well below the status bar instead of flush against it.
        assertEquals(
            "the compass strip must sit flush against the screen's own top edge while fullscreen, " +
                "not leave a gap where the (now-hidden) search bar used to be",
            composeRule.onRoot().getUnclippedBoundsInRoot().top,
            composeRule.onNodeWithTag("compass-elevation-strip").getUnclippedBoundsInRoot().top,
        )

        composeRule.onNodeWithContentDescription("Exit fullscreen").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Tools").assertIsDisplayed()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).assertIsDisplayed()
    }

    /**
     * Fullscreen-fixes dispatch ("still shifting") — insets-independent regression coverage for
     * the planner's own diagnosis of what reads as "the map growing upward" on a real device: not
     * a resize (AvailabilityScreenLayoutTest's own measured-height test already covers that half —
     * the map's own Box never changes), but SearchEntryBar — an 80%-opacity overlay that never
     * reserved space to begin with — disappearing from composition while fullscreen, plus
     * CompassElevationStrip repositioning to fill the clearance `topInset` used to leave for it.
     * Unlike the dead-space and attribution-clearance bugs this same dispatch found, neither half
     * of this one depends on real window insets (CLAUDE.md's own "Known pitfalls"), so it's fully
     * reproducible here — this test is what should have existed already, and didn't.
     */
    @Test
    fun `fullscreen removes the search bar and the compass strip moves flush to the top edge, both restored on exit`() {
        setScreen()

        composeRule.onNodeWithTag(SEARCH_ENTRY_BAR_TAG).assertIsDisplayed()
        val rootTop = composeRule.onRoot().getUnclippedBoundsInRoot().top
        val compassStripTopBefore = composeRule.onNodeWithTag("compass-elevation-strip").getUnclippedBoundsInRoot().top
        assertTrue(
            "the compass strip should sit below SearchEntryBar's own space before fullscreen, " +
                "not flush against the root's own top edge already ($compassStripTopBefore vs $rootTop)",
            compassStripTopBefore > rootTop,
        )

        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SEARCH_ENTRY_BAR_TAG).assertDoesNotExist()
        assertEquals(
            "the compass strip must sit flush against the screen's own top edge while fullscreen, " +
                "not leave a gap where the (now-hidden) search bar used to be",
            rootTop,
            composeRule.onNodeWithTag("compass-elevation-strip").getUnclippedBoundsInRoot().top,
        )

        composeRule.onNodeWithContentDescription("Exit fullscreen").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SEARCH_ENTRY_BAR_TAG).assertIsDisplayed()
        assertEquals(
            "leaving fullscreen must restore the compass strip's own original clearance below " +
                "SearchEntryBar, not leave it stranded flush against the top",
            compassStripTopBefore,
            composeRule.onNodeWithTag("compass-elevation-strip").getUnclippedBoundsInRoot().top,
        )
    }

    // @Ignore: harness-only dismissal failure — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md
    @Ignore("Harness-only failure, confirmed working on a real device — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md")
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

    // @Ignore: harness-only dismissal failure — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md
    @Ignore("Harness-only failure, confirmed working on a real device — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md")
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

    // @Ignore: harness-only dismissal failure — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md
    @Ignore("Harness-only failure, confirmed working on a real device — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md")
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

    // @Ignore: harness-only dismissal failure — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md
    @Ignore("Harness-only failure, confirmed working on a real device — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md")
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

    // @Ignore: harness-only stub-pan-button dismissal failure — see docs/audits/2026-08-31-search-bar-overlay-stub-pan-not-registering.md
    @Ignore("Harness-only failure, confirmed working on a real device — see docs/audits/2026-08-31-search-bar-overlay-stub-pan-not-registering.md")
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

    // @Ignore: harness-only stub-pan-button dismissal failure — see docs/audits/2026-08-31-search-bar-overlay-stub-pan-not-registering.md
    @Ignore("Harness-only failure, confirmed working on a real device — see docs/audits/2026-08-31-search-bar-overlay-stub-pan-not-registering.md")
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

    // @Ignore: harness-only stub-pan-button dismissal failure — see docs/audits/2026-08-31-search-bar-overlay-stub-pan-not-registering.md
    @Ignore("Harness-only failure, confirmed working on a real device — see docs/audits/2026-08-31-search-bar-overlay-stub-pan-not-registering.md")
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
    // @Ignore: harness-only stub-pan-button dismissal failure — see docs/audits/2026-08-31-search-bar-overlay-stub-pan-not-registering.md
    @Ignore("Harness-only failure, confirmed working on a real device — see docs/audits/2026-08-31-search-bar-overlay-stub-pan-not-registering.md")
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
    // @Ignore: harness-only stub-pan-button dismissal failure — see docs/audits/2026-08-31-search-bar-overlay-stub-pan-not-registering.md
    @Ignore("Harness-only failure, confirmed working on a real device — see docs/audits/2026-08-31-search-bar-overlay-stub-pan-not-registering.md")
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

    /**
     * Navigation-chrome dispatch, item 4: with no fix the whole strip is one statement, not three
     * fragments ("Compass needs a fix · Elevation unavailable · Coordi…" read as broken on device).
     * This is the no-sensor-AND-no-fix combination on purpose — `rememberTrueHeading` reports
     * `NoSensor` before it checks for a fix, so a message keyed on `NeedsFix` would still show three
     * fragments here. Keyed on the missing fix, it is one. This test's previous form asserted the
     * three fragments; the sibling below keeps the no-sensor case distinct.
     */
    @Test
    fun `with no fix the compass strip shows one message, not three fragments - even with no sensor`() {
        setScreen(compassProvider = FakeCompassProvider(null))
        searchAReferenceRegion()

        composeRule.onNodeWithText("Location services unavailable").assertIsDisplayed()
        composeRule.onAllNodesWithText("Location services unavailable").assertCountEquals(1)
        composeRule.onAllNodesWithText("Compass unavailable").assertCountEquals(0)
        composeRule.onAllNodesWithText("Compass needs a fix").assertCountEquals(0)
        composeRule.onAllNodesWithText("Elevation unavailable").assertCountEquals(0)
        composeRule.onAllNodesWithText("Coordinates unavailable").assertCountEquals(0)
        composeRule.onAllNodesWithTag(COMPASS_STRIP_HEADING_TAG).assertCountEquals(0)
    }

    /**
     * Two causes, two messages: a phone with no magnetometer but a working fix has a location and
     * no heading — "Compass unavailable", with elevation and coordinates still shown. Asserted
     * separately from the no-fix case above, since a test covering only one would pass while the
     * other collapsed into it.
     */
    @Test
    fun `with a fix but no sensor the compass strip says the compass is unavailable and still shows elevation and coordinates`() {
        setScreen(compassProvider = FakeCompassProvider(null), locationTracker = IconStackFixedLocationTracker(portlandFix))
        searchAReferenceRegion()

        assertEquals("Compass unavailable", textOfTag(COMPASS_STRIP_HEADING_TAG))
        composeRule.onNodeWithText("210 m").assertIsDisplayed()
        composeRule.onNodeWithText("10T ER 25118 40235").assertIsDisplayed()
        composeRule.onAllNodesWithText("Location services unavailable").assertCountEquals(0)
    }

    /**
     * Premise updated by navigation HUD stage one (owner decision): the strip reads *true* north,
     * which needs a fix for declination, so a heading with no fix now reads "Compass needs a fix"
     * (the test below) rather than the raw magnetic value this test used to assert. With a fix and
     * this harness's zero-declination default, 90° magnetic is 90° true — the claim ("the strip
     * reflects a fake heading, no real sensor involved") is unchanged; only its precondition is.
     */
    @Test
    fun `the compass elevation strip reflects a fake heading without any real sensor`() {
        setScreen(compassProvider = FakeCompassProvider(90f), locationTracker = IconStackFixedLocationTracker(hudFix))
        searchAReferenceRegion()

        composeRule.onNodeWithText("90° E").assertIsDisplayed()
    }

    @Test
    fun `a heading with no fix reads the one no-fix message, never the magnetic value`() {
        setScreen(compassProvider = FakeCompassProvider(90f))
        searchAReferenceRegion()

        composeRule.onNodeWithText("Location services unavailable").assertIsDisplayed()
        composeRule.onAllNodesWithText("Compass needs a fix").assertCountEquals(0)
        composeRule.onAllNodesWithText("90° E").assertCountEquals(0)
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

    // @Ignore: harness-only dismissal failure — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md
    @Ignore("Harness-only failure, confirmed working on a real device — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md")
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
    fun `the strip's no-fix message is not a coordinates toggle - tapping it reveals nothing`() {
        setScreen(compassProvider = FakeCompassProvider(null))
        searchAReferenceRegion()

        // There is no coordinate pair to toggle between — a tap on the one message must be a no-op,
        // not silently reveal a fabricated decimal-degree pair for a location that was never fixed.
        composeRule.onNodeWithTag(COMPASS_STRIP_NO_FIX_TAG).performClick()
        composeRule.onNodeWithText("Location services unavailable").assertIsDisplayed()
        composeRule.onAllNodesWithText("Lat. ", substring = true).assertCountEquals(0)
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
    fun `recording with no fix yet shows a waiting message via contentDescription`() {
        setScreen(isRecording = true, returnToStart = null)
        searchAReferenceRegion()

        composeRule.onNode(
            hasTestTag("control-pill-return-to-vehicle") and
                hasContentDescription("Recording — waiting for a fix to compute the way back"),
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Stop recording track").assertIsDisplayed()
    }

    /**
     * The exact bug field-test dispatch item 2 (an earlier dispatch) existed to fix: the same
     * [ReturnToStartInfo] used to reach a sighted user nowhere but a `contentDescription`
     * (TalkBack-only). This asserts the full sentence via contentDescription on [ControlPill]'s
     * own return row. The *visible* distance used to be read off `DistanceArm` here; the arm is
     * gone (navigation-chrome dispatch, item 3) and the visible distance is the HUD's — see `the
     * HUD shows the straight-line distance to the origin` above, which computes it from the live
     * fix rather than from [ReturnToStartInfo].
     */
    @Test
    fun `recording with a real fix and return-to-vehicle active shows the full sentence via contentDescription`() {
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

    // @Ignore, not deleted or rewritten to pass — this test is the record of an unexplained
    // harness failure, and deleting it would remove the follow-up's own subject. Provenance:
    // - Commit boundary: fails deterministically from `72f0a54` (search-bar redesign) onward,
    //   including in true `forkEvery = 1` single-test JVM isolation; passes reliably (4/4 runs) at
    //   the immediately preceding commit `dc1d3e9`, in the identical isolated configuration.
    // - On-device result: at current HEAD (`3df717b`, unmodified), a real `adb shell input tap` at
    //   this row's actual screen coordinates on a real Android emulator fires `onToggleReturning`
    //   correctly — three taps, alternating state each time (DistanceArm extends/retracts, icon
    //   color toggles), plus a control tap on the record row confirming tap-targeting itself was
    //   sound. The product wiring works.
    // - Merge-tree finding: comparing the unmerged/merged Compose semantics tree around ControlPill
    //   before and after `72f0a54` shows identical topology — no new merge boundary between the
    //   record and return-to-vehicle rows, and merged/unmerged queries for this row's own tag agree
    //   on node id and `OnClick` action identity in both commits. Ruled out at the tag level.
    // - Open question: why a Robolectric-hosted Compose semantics click no-ops on this specific
    //   node when the identical production wiring fires correctly on a real touch. Not yet looked
    //   at: the gesture-detector/pointer-input node beneath the semantics layer, inside
    //   MapBarIconButton's own Icon child — below the level the merge-tree check compared.
    // Full writeup: docs/audits/2026-08-30-return-to-vehicle-semantics-click-noop.md
    @Ignore("Harness-only failure, confirmed working on a real device — see this test's own comment and the linked audit doc")
    @Test
    fun `tapping the control pill's return-to-vehicle button calls onToggleReturning`() {
        var toggleCalls = 0
        setScreen(isRecording = true, onToggleReturning = { toggleCalls++ })

        retryClick("control-pill-return-to-vehicle", calls = { toggleCalls })

        assertEquals(1, toggleCalls)
    }

    @Test
    fun `an off-track fix tints the return-to-vehicle button with the error color's contentDescription state`() {
        // isOffTrack only changes tint color, not text/contentDescription — this asserts the state
        // reaches the control at all (enabled, present, still carrying the right distance) rather
        // than the tint's actual pixel value, which this suite has no existing way to assert either.
        // The distance used to be read as visible text off DistanceArm; the arm is gone
        // (navigation-chrome dispatch, item 3), so it is read from the row's own sentence.
        setScreen(
            isRecording = true,
            returnToStart = ReturnToStartInfo(bearingDegrees = 180.0, distanceMeters = 500.0, elevationDifferenceMeters = null),
            isReturning = true,
            isOffTrack = true,
        )
        searchAReferenceRegion()

        composeRule.onNode(
            hasTestTag("control-pill-return-to-vehicle") and
                hasContentDescription("Return: 180° S · 500 m · elevation diff. unavailable"),
        ).assertIsDisplayed()
    }

    @Test
    fun `a return distance under a kilometer is shown in meters in the return row's sentence`() {
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
     *
     */
    // @Ignore for the same harness-only reason as `tapping the control pill's return-to-vehicle
    // button calls onToggleReturning` above — see that test's own comment for the full provenance
    // (commit boundary `72f0a54`, the on-device result on `3df717b`, the merge-tree finding, and
    // the open question) and docs/audits/2026-08-30-return-to-vehicle-semantics-click-noop.md.
    // Not deleted: same reason, this is the follow-up's own subject.
    @Ignore("Harness-only failure, confirmed working on a real device — see the sibling test's comment and the linked audit doc")
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
        retryTouch("control-pill-return-to-vehicle", calls = { returnCalls })

        assertEquals("a real touch on the record button must reach it", 1, recordCalls)
        assertEquals("a real touch on the return-to-vehicle button must reach it", 1, returnCalls)
    }

    /**
     * Same as above, `isReturning = false` — originally the "resting state" of the since-removed
     * `DistanceArm` (navigation-chrome dispatch, item 3), kept because its claim stands on its
     * own: a real touch at each control's own screen coordinates must reach that control whether
     * or not a return leg is active.
     */
    // @Ignore for the same harness-only reason as its twin above — see `tapping the control pill's
    // return-to-vehicle button calls onToggleReturning`'s own comment for the full provenance and
    // docs/audits/2026-08-30-return-to-vehicle-semantics-click-noop.md. Not deleted: same reason.
    @Ignore("Harness-only failure, confirmed working on a real device — see the sibling test's comment and the linked audit doc")
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
        retryTouch("control-pill-return-to-vehicle", calls = { returnCalls })

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
    // @Ignore: harness-only dismissal failure — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md
    @Ignore("Harness-only failure, confirmed working on a real device — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md")
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


    // Two DistanceArm tests were removed here with the arm itself (navigation-chrome dispatch,
    // item 3, owner-authorised): `a real touch beside the distance arm still reaches the map`
    // (@Ignored, and the one CI allowlist entry removed with it — see ci.yml) and `the distance
    // arm overlaps the pill's own bottom cap, flush on its outer edge`. Not silenced: there is no
    // arm left for either to test.

    /**
     * Icon-bar-drag-refinements dispatch, Item 3 ("the track recorder pills must move with the
     * bar"). Baseline half of the pair below: a real touch on the record button reaches it while
     * the bar sits at its own default (right) edge, before any drag has happened — control-pill-
     * record specifically, not control-pill-return-to-vehicle, which has its own pre-existing,
     * unrelated Robolectric-only click no-op documented in
     * docs/audits/2026-08-30-return-to-vehicle-semantics-click-noop.md.
     */
    @Test
    fun `a real touch on the record button reaches it while the bar is on its default right edge`() {
        var recordCalls = 0
        setScreen(onToggleRecording = { recordCalls++ })

        composeRule.onRoot().performTouchInput { click(centerOfTag("control-pill-record")) }
        composeRule.waitForIdle()

        assertEquals(1, recordCalls)
    }

    /**
     * The actual bug Item 3 exists to fix, reproduced and then disproven: previously, snapping the
     * bar to the left edge left [TrailheadControls] stranded on the right — visibly detached from
     * the bar it belongs to. A real long-press-and-drag ([dragIconBarHandle]) past the snap
     * threshold moves the bar; this then confirms the pill actually followed by touching it at its
     * *new* location, not by inspecting bounds alone.
     */
    @Test
    fun `after dragging the icon bar to the left edge, a real touch on the record button still reaches it there`() {
        var recordCalls = 0
        setScreen(onToggleRecording = { recordCalls++ })

        val fullscreenLeftBefore = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().left

        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dxDp = (-160).dp)

        val fullscreenLeftAfter = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().left
        assertTrue(
            "expected the bar to have snapped to the left edge (left=$fullscreenLeftAfter), not " +
                "stayed on the right (was $fullscreenLeftBefore) — this test's own later assertion " +
                "would otherwise pass by coincidence, still touching the bar at its old position",
            fullscreenLeftAfter.value < fullscreenLeftBefore.value,
        )

        composeRule.onRoot().performTouchInput { click(centerOfTag("control-pill-record")) }
        composeRule.waitForIdle()

        assertEquals(1, recordCalls)
    }

    /**
     * Item 4 ("clamp how high the bar can be dragged"). A real long-press-and-drag far upward —
     * well past any reasonable clamp — then checked against the compass strip's own real measured
     * bounds: the bar's own top edge must stay at or below the strip's own bottom edge, i.e. it
     * cannot rise into (or above) the space the search dropdown itself would occupy. No
     * [searchAReferenceRegion] call and the dropdown is never actually opened here — this checks
     * the clamp's own effect on the bar's position, not the dropdown itself, so opening it isn't
     * needed (and per this file's own established `docs/audits/2026-08-31-search-dropdown-dismiss-
     * chip-unmount.md` precedent, opening it here would risk tripping that same unrelated harness
     * bug for no reason).
     */
    @Test
    fun `the icon bar cannot be dragged above where the search dropdown would start`() {
        setScreen()
        val compassStripBottom = composeRule.onNodeWithTag("compass-elevation-strip").getUnclippedBoundsInRoot().bottom

        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dyDp = (-2000).dp)

        val barTopAfterDrag = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().top
        assertTrue(
            "expected the bar's own top edge ($barTopAfterDrag) to stay at or below the compass " +
                "strip's own bottom edge ($compassStripBottom) even after an extreme upward drag",
            barTopAfterDrag.value >= compassStripBottom.value,
        )
    }

    /**
     * Horizontal centre of [tag]'s own node, in dp — for the handle-mark geometry tests below.
     * `useUnmergedTree = true`: each handle's mark is a decorative child inside the handle's own
     * `clickable`, which merges its descendants, so the mark's tag only exists in the unmerged tree.
     */
    private fun centerXOfTag(tag: String): Float {
        val bounds = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        return (bounds.left.value + bounds.right.value) / 2
    }

    /**
     * Fullscreen-slide-out-fixes dispatch, Item 3 ("the drag handle sits inside the bar instead of
     * on its edge"): each handle's *visible mark* must straddle [MapIconBar]'s own outer edge —
     * centred on it, half overlapping the bar and half protruding — not sit inside the bar's width.
     * Checked against the bar's own real measured edge (its top row's own right edge; the rows are
     * exactly as wide as the bar), not a hardcoded inset. The restore handle's mark is checked the
     * same way after a real touch minimises the bar, against the edge captured *before* it left —
     * the owner's own call to extend Item 3 to that handle too. The 48dp tap boxes themselves are
     * covered separately by the two touch-target-floor tests above; this is only about where the
     * drawn mark sits inside them.
     */
    @Test
    fun `the handle marks straddle the bar's own outer edge, on the right by default`() {
        setScreen()

        val barOuterEdge = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().right.value
        val minimizeMarkCenter = centerXOfTag("map-icon-bar-minimize-handle-mark")
        assertTrue(
            "expected the minimize handle's mark to be centred on the bar's own right edge " +
                "($barOuterEdge), was centred at $minimizeMarkCenter",
            kotlin.math.abs(minimizeMarkCenter - barOuterEdge) <= 1f,
        )

        composeRule.onRoot().performTouchInput { click(centerOfContentDescription("Hide map controls")) }
        composeRule.waitForIdle()

        val restoreMarkCenter = centerXOfTag("map-icon-bar-restore-handle-mark")
        assertTrue(
            "expected the restore handle's mark to be centred where the bar's own right edge was " +
                "($barOuterEdge), was centred at $restoreMarkCenter",
            kotlin.math.abs(restoreMarkCenter - barOuterEdge) <= 1f,
        )
    }

    /** Item 3's own "this must hold at both edges" — the same check after a real drag-snap to the left. */
    @Test
    fun `after dragging the icon bar to the left edge, the minimize handle's mark straddles the bar's left edge`() {
        setScreen()

        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dxDp = (-160).dp)

        val barOuterEdge = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().left.value
        val markCenter = centerXOfTag("map-icon-bar-minimize-handle-mark")
        assertTrue(
            "expected the minimize handle's mark to be centred on the bar's own left edge " +
                "($barOuterEdge) after snapping left, was centred at $markCenter",
            kotlin.math.abs(markCenter - barOuterEdge) <= 1f,
        )
    }

    /**
     * Fullscreen-fixes dispatch, Item 3 ("the icon bar can minimise, with a peeking handle to
     * restore it"). Real [performTouchInput], not a semantic [performClick]: this handle is a
     * small `Box` at the map's own edge — precisely the shape of control that has silently
     * swallowed map touches three times before (this file's own history) — so a real touch at its
     * own screen coordinates is what actually proves it works, the same standard this file already
     * holds MapIconBar and TrailheadControls to. Covers both directions and the hide-together
     * claim in one test: minimising takes MapIconBar and TrailheadControls (`control-pill`) out of
     * the tree together — per the project owner's own "minimise means the chrome goes away, not
     * that it fragments" — and the restore handle brings both back together.
     *
     * **No [searchAReferenceRegion] call.** This test (and the other three in this group) first
     * failed with it present, tracing to the same pre-existing
     * `docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md` bug this file already
     * `@Ignore`s thirteen other tests for — `searchAReferenceRegion()`'s own "Search this location"
     * tap doesn't actually close `SearchDropdown` under Robolectric, and once that dropdown is
     * stuck open, state mutations from *any* later interaction silently stop propagating. But
     * unlike those thirteen, nothing here needs a searched region at all: [CompactMapTab] composes
     * [MapIconBar] and this handle unconditionally (only [MapTab]'s own, unrelated
     * `!uiState.hasSearched` branch gates on a search, and that composable isn't what compact width
     * renders) — so dropping the call entirely, rather than accepting the harness bug, gives real
     * coverage instead of another allowlist entry.
     */
    @Test
    fun `a real touch on the minimize handle hides MapIconBar and TrailheadControls together, and the restore handle brings both back`() {
        setScreen()

        composeRule.onNodeWithContentDescription("Fullscreen").assertIsDisplayed()
        composeRule.onNodeWithTag("control-pill").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hide map controls").assertIsDisplayed()

        composeRule.onRoot().performTouchInput { click(centerOfContentDescription("Hide map controls")) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Fullscreen").assertDoesNotExist()
        composeRule.onNodeWithTag("control-pill").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Show map controls").assertIsDisplayed()

        composeRule.onRoot().performTouchInput { click(centerOfContentDescription("Show map controls")) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Fullscreen").assertIsDisplayed()
        composeRule.onNodeWithTag("control-pill").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Show map controls").assertDoesNotExist()
    }

    /**
     * The other half of Item 3's own touch-handling requirement — a tap just outside the minimize
     * handle's own bounds must reach whatever is actually there, not the handle. MapIconBar's own
     * fullscreen row is the real target here: [MapIconBarMinimizeHandle]'s own doc comment expects
     * it to share the bar's vertical centre while being much shorter than the bar itself, so the
     * fullscreen row (the bar's topmost) should sit well above the handle's own bounds — asserted
     * directly against real measured bounds first, so this test is a real check of that geometry,
     * not a coincidence of whatever this suite's fixed viewport happens to produce. No
     * [searchAReferenceRegion] call — see the sibling test above's own doc comment for why none of
     * this group needs one.
     */
    @Test
    fun `a real touch just outside the minimize handle reaches MapIconBar's own fullscreen row, not the handle`() {
        setScreen()

        val handleBounds = composeRule.onNodeWithContentDescription("Hide map controls").getUnclippedBoundsInRoot()
        val fullscreenBounds = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot()
        assertTrue(
            "expected the fullscreen row (top=${fullscreenBounds.top}) to sit above the minimize " +
                "handle's own bounds (top=${handleBounds.top}) for this test's own tap point to be " +
                "a real check, not a coincidence",
            fullscreenBounds.top < handleBounds.top,
        )

        composeRule.onRoot().performTouchInput { click(centerOfContentDescription("Fullscreen")) }
        composeRule.waitForIdle()

        // Fullscreen toggled (proves the tap landed on the bar's own row)...
        composeRule.onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()
        // ...and the bar itself is still mounted, not minimized by the same tap.
        composeRule.onNodeWithContentDescription("Hide map controls").assertIsDisplayed()
    }

    /**
     * Item 3's own explicit ask was "the tappable area should meet MIN_TOUCH_TARGET even if the
     * visible outline is smaller." The handle-tap-area dispatch made a recorded exception to that
     * in one dimension (see [HANDLE_TAP_WIDTH]'s own doc comment and the comment at its use):
     * the box is exactly that wide, still at least 48dp tall, and the visible mark sits fully
     * inside it. This test used to assert width >= 48dp and is rewritten to pin the exception —
     * the one assertion that dispatch legitimately reverses, reported as such. A width that
     * drifts back to 48dp would silently re-cover the locate row, and this is what says so.
     */
    @Test
    fun `the minimize handle is exactly its recorded tap width, at least 48dp tall, with the mark inside it`() {
        setScreen()

        val minimizeBounds = composeRule.onNodeWithContentDescription("Hide map controls").getUnclippedBoundsInRoot()
        val markBounds = composeRule.onNodeWithTag("map-icon-bar-minimize-handle-mark", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val minimizeWidth = minimizeBounds.right - minimizeBounds.left
        // The literal, not HANDLE_TAP_WIDTH: comparing against the constant the box is built from
        // would pass at any value, including a drift back to 48dp — which is exactly the
        // regression this test exists to catch. Confirmed: against the constant it passed under
        // the 48dp reverted variant; against the literal it fails there.
        assertTrue("minimize handle width was $minimizeWidth, expected the recorded 20dp exception", abs((minimizeWidth - 20.dp).value) <= 0.5f)
        assertTrue("minimize handle width was $minimizeWidth, expected below the 48dp floor it deliberately excepts", minimizeWidth < 48.dp)
        assertTrue("minimize handle height was ${minimizeBounds.height}", minimizeBounds.height >= 48.dp)
        assertTrue(
            "expected the mark ($markBounds) fully inside the tap box ($minimizeBounds)",
            markBounds.left >= minimizeBounds.left && markBounds.right <= minimizeBounds.right &&
                markBounds.top >= minimizeBounds.top && markBounds.bottom <= minimizeBounds.bottom,
        )
    }

    /**
     * [MapIconBarRestoreHandle]'s own half of the same touch-target requirement — its own visible
     * outline is much smaller than its own 48dp square tap target (inset via padding, not a
     * shrunken hit area, per that composable's own doc comment). No [searchAReferenceRegion] call —
     * see the first test in this group's own doc comment.
     */
    @Test
    fun `the restore handle meets the 48dp touch-target floor`() {
        setScreen()

        composeRule.onRoot().performTouchInput { click(centerOfContentDescription("Hide map controls")) }
        composeRule.waitForIdle()

        val restoreBounds = composeRule.onNodeWithContentDescription("Show map controls").getUnclippedBoundsInRoot()
        val restoreWidth = restoreBounds.right - restoreBounds.left
        assertTrue("restore handle width was $restoreWidth", restoreWidth >= 48.dp)
        assertTrue("restore handle height was ${restoreBounds.height}", restoreBounds.height >= 48.dp)
    }

    /**
     * Owner's standing UX default (CLAUDE.md, "UX defaults"): user-set state survives navigating
     * away and back. This test used to assert the opposite — the fullscreen-fixes dispatch's own
     * "minimising resets when the user leaves the Map tab", a planner rule the owner has since
     * overruled in general — and is rewritten to assert the keep, the one assertion that
     * principle legitimately reverses here. Driven through the real tab-switch entry point, not
     * by inspecting where the state lives. Fails with the flag remembered inside `CompactMapTab`
     * (reverted variant: the full bar is back on return). No [searchAReferenceRegion] call — see
     * the first test in this group's own doc comment.
     */
    @Test
    fun `the icon bar stays minimised after leaving and returning to the Map tab`() {
        setScreen()

        composeRule.onRoot().performTouchInput { click(centerOfContentDescription("Hide map controls")) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Show map controls").assertIsDisplayed()

        composeRule.onNodeWithText("List").performClick()
        composeRule.onNodeWithText("Maps").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Show map controls").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Fullscreen").assertDoesNotExist()

        // And the restore handle still works there — the state survived as live state, not a snapshot.
        composeRule.onRoot().performTouchInput { click(centerOfContentDescription("Show map controls")) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Fullscreen").assertIsDisplayed()
    }

    /**
     * Map/navigation redesign dispatch D: [SearchEntryBar]'s own field, tapped, opens
     * [SearchDropdown] — no chevron any more (removed on the project owner's own direct call: the
     * leading search icon already reads as tappable, per that composable's own doc comment), and no
     * "tap again to close" either, since a real, focused text field doesn't conventionally close on
     * a second tap the way the old toggle button did. Closing is covered separately below, by the
     * dismiss-elsewhere scrim this redesign added specifically because tapping the field again no
     * longer works for it.
     */
    @Test
    fun `tapping the search field opens the search dropdown`() {
        setScreen()

        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performClick()

        composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).assertIsDisplayed()
    }

    /**
     * The project owner's own direct ask, map/navigation redesign dispatch D: "have the search
     * window extend just below the compass strip to avoid overlaying it so people can still track
     * it if needed." [setScreen]'s own default `compactTab` is [CompactTab.MAP], where the strip
     * exists at all — real measured bounds, not a hardcoded offset, so a Material change to either
     * surface can't quietly reintroduce the overlap this guards against.
     */
    @Test
    fun `the search dropdown starts below the compass strip, not over it`() {
        setScreen()

        val compassStripBottom = composeRule.onNodeWithTag("compass-elevation-strip").getUnclippedBoundsInRoot().bottom
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performClick()
        val searchDropdownTop = composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).getUnclippedBoundsInRoot().top

        assertTrue(
            "expected the search dropdown (top=$searchDropdownTop) to start at or below the " +
                "compass strip's own bottom edge ($compassStripBottom), not paint over it",
            searchDropdownTop >= compassStripBottom,
        )
    }

    /**
     * The compass-strip clearance above is Map-tab-only — no strip exists on List, so the dropdown
     * keeps its pre-dispatch-D behaviour of starting flush against [SearchEntryBar]'s own bottom
     * edge there, guarding the `compactTab == CompactTab.MAP` conditional itself, not just its
     * Map-tab branch.
     */
    @Test
    fun `the search dropdown starts flush at the top on the List tab, where there is no compass strip to avoid`() {
        setScreen()
        composeRule.onNodeWithText("List").performClick()

        val searchEntryBarBottom = composeRule.onNodeWithTag(SEARCH_ENTRY_BAR_TAG).getUnclippedBoundsInRoot().bottom
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performClick()
        val searchDropdownTop = composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).getUnclippedBoundsInRoot().top

        // A small tolerance, not exact equality: focusing SearchEntryBar's own species field (what
        // opens the dropdown) nudges that bar's measured height by a few dp independent of anything
        // this test cares about. What matters is that no full compass-strip-sized gap opened up —
        // see the Map-tab test above for that real assertion.
        assertTrue(
            "expected the search dropdown (top=$searchDropdownTop) to start close to " +
                "SearchEntryBar's own bottom edge ($searchEntryBarBottom) on the List tab, with no " +
                "compass-strip clearance applied",
            (searchDropdownTop - searchEntryBarBottom).value < 16f,
        )
    }

    /**
     * The dismiss-elsewhere half of the same redesign — see [SearchEntryBar]'s own call site,
     * `SEARCH_DROPDOWN_SCRIM_TAG`'s doc comment, for why this scrim exists at all: [SearchDropdown]
     * only covers its own (bounded, scrolled) content height, not the whole remaining screen below
     * [SearchEntryBar], so without it a tap on visible tab content past that edge would reach the
     * tab underneath with no way to close the panel except the back button.
     *
     * **Must tap below [SearchDropdown]'s own bottom edge, not the scrim's own geometric centre.**
     * [SEARCH_DROPDOWN_SCRIM_TAG]'s own node is `fillMaxSize()`, so its centre sits well inside
     * [SearchDropdown]'s own (opaque, composed-after-the-scrim, so on top per this file's own
     * composition-order rule) bounds — a plain `performTouchInput { click() }` on the scrim node
     * lands there and gets absorbed by [SearchDropdown] itself before it ever reaches the scrim's
     * own `detectTapGestures`, the same "opaque background blocks every touch within its bounds"
     * rule [SearchDropdown]'s own doc comment already documents. A real point past that panel's own
     * bottom edge, still within the scrim's `fillMaxSize()`, is what actually exercises dismiss.
     */
    // @Ignore: harness-only dismissal failure — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md
    @Ignore("Harness-only failure, confirmed working on a real device — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md")
    @Test
    fun `tapping outside the search dropdown closes it`() {
        setScreen()

        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performClick()
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).assertIsDisplayed()

        val dropdownBounds = composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).getUnclippedBoundsInRoot()
        val scrimBounds = composeRule.onNodeWithTag(SEARCH_DROPDOWN_SCRIM_TAG).getUnclippedBoundsInRoot()
        val belowDropdown = with(composeRule.density) {
            Offset(
                ((scrimBounds.left + scrimBounds.right) / 2).toPx(),
                ((dropdownBounds.bottom + scrimBounds.bottom) / 2).toPx(),
            )
        }
        // performTouchInput, not performClick: the scrim is a plain Modifier.pointerInput tap
        // catcher with no Modifier.clickable, so it carries no semantics OnClick action for
        // performClick to invoke — a real simulated touch is the only way to reach it, the same
        // reasoning this file's own trailhead-control touch-routing tests already rely on.
        composeRule.onRoot().performTouchInput { click(belowDropdown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).assertDoesNotExist()
    }

    @Test
    fun `the advanced search dropdown does not open the full Tools drawer`() {
        setScreen()

        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performClick()

        // "Trip Planner" (the Tools drawer's own first section header) stands in for "the drawer
        // is open" — "Recent searches" doesn't work for this any more: it moved into the same
        // SearchDropdown this test just opened, so it would be legitimately on screen there too.
        composeRule.onNodeWithText("Trip Planner").assertIsNotDisplayed()
    }

    /**
     * Map/navigation redesign dispatch C's own explicit ask: "this repo has shipped pointer
     * interception four times. Extend Dispatch A's performTouchInput coverage to this surface" —
     * [AdvancedSearchDropdown] floats over the map exactly like [ControlPill] does,
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
    // @Ignore: harness-only dismissal failure — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md
    @Ignore("Harness-only failure, confirmed working on a real device — see docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md")
    @Test
    fun `a real touch on the advanced search dropdown's Set on map button reaches it and opens the centre-pin picker over the real map`() {
        setScreen()

        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performClick()
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

    // ── Expanded-panels dispatch: MapModePicker/AddActionTile follow the bar's live position ──

    /** [MapIconBar]'s layers row, as it reads at this file's default (Topographical, night mode off). */
    private val layersRowDescription = "Map mode: Topographical. Choose Street, Topographical, or Satellite. Night mode off."

    /** [MapIconBar]'s add row. */
    private val addRowDescription = "Plan a trip or log a find here"

    /** Same as [centerOfTag], keyed by visible text — the two panels' own chips carry only a label. */
    private fun centerOfText(text: String): Offset {
        val bounds = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
        return with(composeRule.density) {
            Offset(((bounds.left + bounds.right) / 2).toPx(), ((bounds.top + bounds.bottom) / 2).toPx())
        }
    }

    /**
     * Same real long-press-then-drag as [dragIconBarHandle], from an arbitrary [start] point —
     * for the restore handle, which carries only a contentDescription ("Show map controls") and
     * no testTag of its own, so [centerOfContentDescription] has to supply the start.
     */
    private fun dragFrom(start: Offset, dxDp: Dp = 0.dp, dyDp: Dp = 0.dp) {
        val delta = with(composeRule.density) { Offset(dxDp.toPx(), dyDp.toPx()) }
        composeRule.onRoot().performTouchInput {
            down(start)
            advanceEventTime(600)
            moveTo(start + delta)
            advanceEventTime(50)
            up()
        }
        composeRule.waitForIdle()
    }

    /**
     * The bottom nav's own top edge, via its "Maps" destination — the nav itself carries no tag,
     * and each [NavigationBarItem] spans the bar's full height. What the drag clamp's downward
     * bound is checked against: the nav is composed *over* [MapIconBar] in [CompactMapTab]'s Box
     * (its own call-site comment explains why that ordering is load-bearing), so a row that ends
     * up under it is on screen yet untappable — the case the clamp exists to rule out.
     */
    private fun bottomNavTop(): Dp = composeRule.onNodeWithText("Maps").getUnclippedBoundsInRoot().top

    /**
     * The dispatch's own acceptance shape for both panels: opened *adjacent to the bar where it
     * now is* — the panel's own outer edge lands on the bar's own outer edge on whichever side the
     * bar is on, and the panel is vertically centred on the row that opened it — and *fully on
     * screen*, checked against the root's own real bounds. The panel is measured by its own
     * `Surface` ([MAP_MODE_PICKER_TAG]/[ADD_ACTION_TILE_TAG]), not by its chips: under
     * Robolectric's near-zero-width fonts each chip is under 48dp wide, so Material's
     * `minimumInteractiveComponentSize` wrapper pads it out and the chip's own semantics bounds
     * stop a font-dependent ~6dp short of the panel's real edge (measured, not assumed — see
     * [ADD_ACTION_TILE_TAG]'s own doc comment). The bar's edge is read from its own top row (the
     * rows are exactly as wide as the bar), the same handle the handle-mark tests above use;
     * nothing here is a hardcoded position. Tolerance is 1dp, for px rounding only.
     */
    private fun assertPanelAnchoredToBar(panelTag: String, openedFromRow: String, onLeftSide: Boolean) {
        val panel = composeRule.onNodeWithTag(panelTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val bar = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot()
        val row = composeRule.onNodeWithContentDescription(openedFromRow).getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val tolerance = 1f
        if (onLeftSide) {
            assertTrue(
                "expected the panel's own left edge (${panel.left}) to land on the bar's own left edge (${bar.left})",
                abs(panel.left.value - bar.left.value) <= tolerance,
            )
        } else {
            assertTrue(
                "expected the panel's own right edge (${panel.right}) to land on the bar's own right edge (${bar.right})",
                abs(panel.right.value - bar.right.value) <= tolerance,
            )
        }
        val panelCenterY = (panel.top.value + panel.bottom.value) / 2
        val rowCenterY = (row.top.value + row.bottom.value) / 2
        assertTrue(
            "expected the panel (centre y=$panelCenterY, bounds $panel) to be centred on the row that opened it " +
                "(centre y=$rowCenterY, bounds $row) — i.e. to have followed the bar to its dragged position",
            abs(panelCenterY - rowCenterY) <= tolerance,
        )
        assertTrue(
            "expected the panel ($panel) to sit fully inside the root's own bounds ($root)",
            panel.left >= root.left && panel.right <= root.right && panel.top >= root.top && panel.bottom <= root.bottom,
        )
    }

    /**
     * The sweep finding this dispatch's owner approved fixing ("fix the clamp"): the old downward
     * bound kept only the bar's *centre* MIN_TOUCH_TARGET above the Box's bottom, so a drag to the
     * bottom of the range carried the bar's own last two rows — layers and add, exactly the rows
     * the two panels anchor to — off the bottom of the screen, and under the bottom nav before
     * that. Reproduced as: a real long-press-and-drag far downward, then the add row's own bottom
     * edge checked against the nav's own real top edge. The "it actually moved" guard keeps this
     * from passing on a clamp that merely refuses all downward movement.
     */
    @Test
    fun `dragging the icon bar to the bottom of its range keeps its add row above the bottom nav`() {
        setScreen()
        val navTop = bottomNavTop()
        val addRowBottomBefore = composeRule.onNodeWithContentDescription(addRowDescription).getUnclippedBoundsInRoot().bottom

        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dyDp = 2000.dp)

        val addRowBottomAfter = composeRule.onNodeWithContentDescription(addRowDescription).getUnclippedBoundsInRoot().bottom
        assertTrue(
            "expected the bar to have actually moved down (add row bottom $addRowBottomBefore -> $addRowBottomAfter)",
            addRowBottomAfter.value > addRowBottomBefore.value,
        )
        assertTrue(
            "expected the add row's own bottom edge ($addRowBottomAfter) to stay at or above the " +
                "bottom nav's own top edge ($navTop) after an extreme downward drag",
            addRowBottomAfter.value <= navTop.value,
        )
    }

    /**
     * The other route to the same off-screen-rows outcome: the 48dp restore handle shares the
     * bar's one vertical offset, so it can be dragged lower than the ~272dp bar itself may go —
     * restoring from there used to bring the bar back with its bottom rows under the nav. The
     * clamp now re-applies when the measured height changes (the `LaunchedEffect` beside the drag
     * modifier), which this checks the same way as the test above, after a real minimise, drag
     * and restore.
     */
    @Test
    fun `restoring the bar after dragging its restore handle to the bottom keeps the add row above the bottom nav`() {
        setScreen()
        val navTop = bottomNavTop()

        composeRule.onRoot().performTouchInput { click(centerOfContentDescription("Hide map controls")) }
        composeRule.waitForIdle()
        dragFrom(centerOfContentDescription("Show map controls"), dyDp = 2000.dp)
        composeRule.onRoot().performTouchInput { click(centerOfContentDescription("Show map controls")) }
        composeRule.waitForIdle()

        val addRowBottom = composeRule.onNodeWithContentDescription(addRowDescription).getUnclippedBoundsInRoot().bottom
        assertTrue(
            "expected the restored bar's add row bottom ($addRowBottom) to stay at or above the " +
                "bottom nav's own top edge ($navTop)",
            addRowBottom.value <= navTop.value,
        )
    }

    /**
     * The dispatch's own bug, reproduced then disproven for [MapModePicker] on the default (right)
     * edge: the bar is dragged to the bottom of its range, the picker opened by a real touch on
     * the layers row *at its new position*, then checked against where the bar now is (see
     * [assertPanelAnchoredToBar]) — previously it opened at the bar's old, centred position. A real
     * touch on the "Street" chip then proves the panel's own buttons are tappable where it
     * landed: the layers row's own contentDescription reflects the new mode.
     */
    @Test
    fun `with the bar dragged to the bottom on the right, the map mode picker opens beside the layers row and its chips are tappable`() {
        setScreen()

        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dyDp = 2000.dp)
        composeRule.onRoot().performTouchInput { click(centerOfContentDescription(layersRowDescription)) }
        composeRule.waitForIdle()

        assertPanelAnchoredToBar(
            panelTag = MAP_MODE_PICKER_TAG,
            openedFromRow = layersRowDescription,
            onLeftSide = false,
        )

        composeRule.onRoot().performTouchInput { click(centerOfText("Street")) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Map mode: Street. Choose Street, Topographical, or Satellite. Night mode off.").assertIsDisplayed()
    }

    /** [MapModePicker], left edge — the horizontal case the dispatch asked to confirm, not assume: one drag snaps the bar left and drops it to the bottom of its range. */
    @Test
    fun `with the bar dragged to the bottom on the left, the map mode picker opens beside the layers row and its chips are tappable`() {
        setScreen()
        val fullscreenLeftBefore = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().left

        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dxDp = (-160).dp, dyDp = 2000.dp)

        val fullscreenLeftAfter = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().left
        assertTrue(
            "expected the bar to have snapped to the left edge (left=$fullscreenLeftAfter, was $fullscreenLeftBefore)",
            fullscreenLeftAfter.value < fullscreenLeftBefore.value,
        )

        composeRule.onRoot().performTouchInput { click(centerOfContentDescription(layersRowDescription)) }
        composeRule.waitForIdle()

        assertPanelAnchoredToBar(
            panelTag = MAP_MODE_PICKER_TAG,
            openedFromRow = layersRowDescription,
            onLeftSide = true,
        )

        composeRule.onRoot().performTouchInput { click(centerOfText("Street")) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Map mode: Street. Choose Street, Topographical, or Satellite. Night mode off.").assertIsDisplayed()
    }

    /**
     * `AddActionTile`, right edge, bar at the bottom of its range — the same shape as the picker
     * tests above. A real touch on "Trip" proves the chips are tappable where the tile landed:
     * [CentrePinLocationPickerOverlay]'s own confirm row comes up. No [searchAReferenceRegion]
     * call — that overlay is composed on `pendingAction` alone, not on a searched region.
     */
    @Test
    fun `with the bar dragged to the bottom on the right, the add tile opens beside the add row and its chips are tappable`() {
        setScreen()

        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dyDp = 2000.dp)
        composeRule.onRoot().performTouchInput { click(centerOfContentDescription(addRowDescription)) }
        composeRule.waitForIdle()

        assertPanelAnchoredToBar(
            panelTag = ADD_ACTION_TILE_TAG,
            openedFromRow = addRowDescription,
            onLeftSide = false,
        )

        composeRule.onRoot().performTouchInput { click(centerOfText("Trip")) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("OK").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    /** `AddActionTile`, left edge — see the left-edge picker test above for the one-drag snap-and-drop. */
    @Test
    fun `with the bar dragged to the bottom on the left, the add tile opens beside the add row and its chips are tappable`() {
        setScreen()
        val fullscreenLeftBefore = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().left

        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dxDp = (-160).dp, dyDp = 2000.dp)

        val fullscreenLeftAfter = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().left
        assertTrue(
            "expected the bar to have snapped to the left edge (left=$fullscreenLeftAfter, was $fullscreenLeftBefore)",
            fullscreenLeftAfter.value < fullscreenLeftBefore.value,
        )

        composeRule.onRoot().performTouchInput { click(centerOfContentDescription(addRowDescription)) }
        composeRule.waitForIdle()

        assertPanelAnchoredToBar(
            panelTag = ADD_ACTION_TILE_TAG,
            openedFromRow = addRowDescription,
            onLeftSide = true,
        )

        composeRule.onRoot().performTouchInput { click(centerOfText("Trip")) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("OK").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    /**
     * Owner finding on device: during a side-to-side drag only the bar followed the finger, and
     * the control pill jumped across once the side flipped at gesture end. Both must move as one
     * unit *while the finger is still down*, so this holds the gesture open — a real long-press,
     * a move of less than the snap threshold, no `up()` — and compares how far the bar's own top
     * row and the pill each moved from where they started. Checked mid-gesture, not after it:
     * after `up()` the side flip lands both on the same edge regardless, which is exactly the
     * "ends up right, looked wrong" case this test exists to distinguish.
     */
    @Test
    fun `while the icon bar is being dragged sideways, the control pill moves with it rather than waiting for the side to flip`() {
        setScreen()
        val barLeftBefore = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().left
        val pillLeftBefore = composeRule.onNodeWithTag("control-pill").getUnclippedBoundsInRoot().left

        val start = centerOfTag("map-icon-bar-minimize-handle")
        val delta = with(composeRule.density) { Offset((-40).dp.toPx(), 0f) }
        composeRule.onRoot().performTouchInput {
            down(start)
            advanceEventTime(600)
            moveTo(start + delta)
            advanceEventTime(50)
        }
        composeRule.waitForIdle()

        val barShift = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().left - barLeftBefore
        val pillShift = composeRule.onNodeWithTag("control-pill").getUnclippedBoundsInRoot().left - pillLeftBefore
        assertTrue(
            "expected the bar to have moved with the finger mid-drag (shift=$barShift) for this test's own pill check to mean anything",
            barShift.value < -1f,
        )
        assertTrue(
            "expected the control pill to have moved with the bar mid-drag (bar shift=$barShift, pill shift=$pillShift)",
            abs(pillShift.value - barShift.value) <= 1f,
        )

        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()
    }

    // ── Icon-bar-position-memory dispatch ──────────────────────────────────────────────────

    /** Real touch on the bar's own fullscreen row (its contentDescription flips between the two). */
    private fun touchFullscreenRow(description: String) {
        composeRule.onRoot().performTouchInput { click(centerOfContentDescription(description)) }
        composeRule.waitForIdle()
    }

    private fun addRowBottom(): Dp = composeRule.onNodeWithContentDescription(addRowDescription).getUnclippedBoundsInRoot().bottom

    /**
     * Enters fullscreen by touch and drags the bar to the bottom of fullscreen's own, lower range
     * (the Box bottom — the nav has slid away), returning the bar's top edge there. The guard
     * assertion is what makes the tests below meaningful: fullscreen's range must actually reach
     * below where the nav's top would be, or "pushed back above the nav on exit" is vacuous.
     *
     * Guard subject (icon-bar-unify-container dispatch, owner-approved): the *control pill's*
     * bottom, previously the add row's. The guard asserts "the lowest clamped element sits below
     * the nav's top"; that element was the add row while the clamp measured the bar alone, and is
     * the pill now that the clamp measures the whole cluster (the add row stops ~40dp above the
     * nav at the bottom of the range). A guard change, not a coverage change — the four tests'
     * own bodies and assertions are untouched. Confirmed still guarding: under the reverted
     * variant (clamp measuring the bar alone) this guard fails the same way the cluster tests do.
     */
    private fun enterFullscreenAndDragLow(navTop: Dp): Dp {
        touchFullscreenRow("Fullscreen")
        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dyDp = 2000.dp)
        val lowPillBottom = composeRule.onNodeWithTag("control-pill").getUnclippedBoundsInRoot().bottom
        assertTrue(
            "expected fullscreen's own drag range to reach below the nav's top ($navTop) for this " +
                "test to check anything — control pill bottom was only $lowPillBottom",
            lowPillBottom.value > navTop.value,
        )
        return composeRule.onNodeWithContentDescription("Exit fullscreen").getUnclippedBoundsInRoot().top
    }

    /**
     * Dispatch case 1: drag low in fullscreen, exit, and the bar clears the nav — the bounds
     * changed under it and the displayed position followed the clamp of the remembered one. The
     * animation itself completes under `waitForIdle`, so this checks where it lands, not the
     * motion. Fails with the bounds-change effect removed (bar left under the nav).
     */
    @Test
    fun `leaving fullscreen with the bar dragged low pushes it back up above the bottom nav`() {
        setScreen()
        val navTop = bottomNavTop()
        val lowTop = enterFullscreenAndDragLow(navTop)

        touchFullscreenRow("Exit fullscreen")

        val pushedUpTop = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().top
        assertTrue("expected the bar to have moved up on exit (top $lowTop -> $pushedUpTop)", pushedUpTop.value < lowTop.value)
        assertTrue(
            "expected the add row's bottom (${addRowBottom()}) at or above the nav's top ($navTop) after leaving fullscreen",
            addRowBottom().value <= navTop.value,
        )
    }

    /**
     * Dispatch case 2, the actual bug: re-entering fullscreen returns the bar to where the user
     * had put it, not to the pushed-up position. Fails with the effect targeting the *displayed*
     * offset instead of the remembered one (the previous one-way overwrite).
     */
    @Test
    fun `re-entering fullscreen returns the bar to where the user left it`() {
        setScreen()
        val navTop = bottomNavTop()
        val lowTop = enterFullscreenAndDragLow(navTop)
        touchFullscreenRow("Exit fullscreen")

        touchFullscreenRow("Fullscreen")

        val restoredTop = composeRule.onNodeWithContentDescription("Exit fullscreen").getUnclippedBoundsInRoot().top
        assertTrue(
            "expected the bar's top ($restoredTop) back at its remembered fullscreen position ($lowTop) on re-entry",
            abs(restoredTop.value - lowTop.value) <= 1f,
        )
    }

    /**
     * Dispatch case 3: a drag outside fullscreen replaces the memory, so entering fullscreen keeps
     * the new position rather than gliding back to the older fullscreen one. Fails with the drag
     * writing only the displayed offset and not the memory.
     */
    @Test
    fun `a drag outside fullscreen replaces the remembered fullscreen position`() {
        setScreen()
        val navTop = bottomNavTop()
        val lowTop = enterFullscreenAndDragLow(navTop)
        touchFullscreenRow("Exit fullscreen")

        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dyDp = (-100).dp)
        val draggedTop = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().top
        assertTrue("expected the outside-fullscreen drag to land somewhere other than the old low position ($lowTop)", abs(draggedTop.value - lowTop.value) > 1f)

        touchFullscreenRow("Fullscreen")

        val topInFullscreen = composeRule.onNodeWithContentDescription("Exit fullscreen").getUnclippedBoundsInRoot().top
        assertTrue(
            "expected the bar to stay at the new drag ($draggedTop) on entering fullscreen, not return to the older memory ($lowTop) — was $topInFullscreen",
            abs(topInFullscreen.value - draggedTop.value) <= 1f,
        )
    }

    /**
     * Direct owner request, reversing the position-memory dispatch's case 4 ("nothing survives
     * leaving the Map tab"): the cluster's position now survives switching tabs. The old test
     * asserted the reset and is rewritten here to assert the keep — the one assertion this change
     * legitimately reverses, reported as such. After a low fullscreen drag and an exit, leaving
     * and returning puts the bar back exactly where the exit had pushed it (the memory re-clamped
     * under the nav bound, since the tab change also exited fullscreen), and entering fullscreen
     * then glides it back to the remembered low position — the memory survived too. Fails with
     * the state left inside CompactMapTab (reverted variant: bar back at its default on return).
     */
    @Test
    fun `leaving the Map tab and returning keeps the cluster's position and its fullscreen memory`() {
        setScreen()
        val navTop = bottomNavTop()
        val defaultTop = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().top
        val lowTop = enterFullscreenAndDragLow(navTop)
        touchFullscreenRow("Exit fullscreen")
        val pushedUpTop = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().top
        assertTrue("expected the exit to have moved the bar from its default ($defaultTop) — was $pushedUpTop — for the return check to mean anything", abs(pushedUpTop.value - defaultTop.value) > 1f)

        composeRule.onNodeWithText("List").performClick()
        composeRule.onNodeWithText("Maps").performClick()
        composeRule.waitForIdle()

        val topOnReturn = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().top
        assertTrue("expected the bar back where the exit left it ($pushedUpTop) after a tab change, was $topOnReturn", abs(topOnReturn.value - pushedUpTop.value) <= 1f)

        touchFullscreenRow("Fullscreen")

        val topInFullscreen = composeRule.onNodeWithContentDescription("Exit fullscreen").getUnclippedBoundsInRoot().top
        assertTrue(
            "expected the fullscreen memory to survive the tab change: entering fullscreen should glide the bar back to $lowTop — was $topInFullscreen",
            abs(topInFullscreen.value - lowTop.value) <= 1f,
        )
    }

    // ── Persist-fullscreen dispatch: fullscreen survives a restart; nothing else about the cluster does ──

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    /**
     * A "restart" here is a fresh screen whose preference store already says fullscreen — the
     * only part of a restart this harness can stand in for. The tab starts in fullscreen: the
     * exit control is up and the nav has never composed. Fails with the ViewModel's launch-time
     * read removed (reverted variant: the screen starts with the nav showing). What is *not*
     * persisted — position, side, minimise — has no mechanism to test against: the repository
     * gained fullscreen keys only, and that is the whole of the guarantee.
     */
    @Test
    fun `a persisted fullscreen preference starts the Map tab in fullscreen`() {
        setScreen(mapPreferencesRepository = IconStackFakeMapPreferencesRepository(Result.success(true)))

        composeRule.onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()
        composeRule.onNodeWithText("Maps").assertDoesNotExist()
    }

    /**
     * The write side, through the two user-driven exits reachable from fullscreen: the icon
     * bar's toggle both ways, then system back. The third site, the tab handler's exit, cannot be
     * driven from fullscreen — the nav is off screen there — which is exactly why it is not an
     * exception to the UX rule; it is not exercised here for the same reason. No write happens
     * at launch: the persisted value is applied, never echoed back, so a persisted `true` is
     * never clobbered by the default `false` before the read lands. Fails with the writes at
     * the mutation sites removed.
     */
    @Test
    fun `entering and leaving fullscreen writes the preference through, and launch never writes it`() {
        val repository = IconStackFakeMapPreferencesRepository(Result.success(false))
        setScreen(mapPreferencesRepository = repository)
        assertEquals(emptyList<Boolean>(), repository.fullscreenWrites)

        touchFullscreenRow("Fullscreen")
        assertEquals(listOf(true), repository.fullscreenWrites)

        touchFullscreenRow("Exit fullscreen")
        assertEquals(listOf(true, false), repository.fullscreenWrites)

        touchFullscreenRow("Fullscreen")
        pressBack()
        composeRule.onNodeWithContentDescription("Fullscreen").assertIsDisplayed()
        assertEquals(listOf(true, false, true, false), repository.fullscreenWrites)
    }

    /** The persisted value is applied once at launch and never echoed back, even when the store says `true`. */
    @Test
    fun `a persisted fullscreen preference is applied without being written back`() {
        val repository = IconStackFakeMapPreferencesRepository(Result.success(true))
        setScreen(mapPreferencesRepository = repository)
        composeRule.onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()

        assertEquals(emptyList<Boolean>(), repository.fullscreenWrites)
    }

    // ── Handle-tap-area dispatch: the locate row belongs to locate, sampled across its bounds ──

    /**
     * The owner's device finding, reproduced then disproven: a real touch at the locate row's
     * own centre reached the minimize handle (composed after the container, 48dp wide, centred on
     * this very row) and minimised the cluster. Fails with the tap box back at 48dp wide: the
     * cluster disappears and locate is never called. The scaffold pings location once on its own
     * first composition, so the count is taken before the touch.
     */
    @Test
    fun `a real touch at the locate row's centre reaches locate, not the minimize handle`() {
        var locateMeCalls = 0
        setScreen(onLocateMe = { locateMeCalls++ })
        val callsBeforeTouch = locateMeCalls

        composeRule.onRoot().performTouchInput { click(centerOfContentDescription("Center on my location")) }
        composeRule.waitForIdle()

        assertEquals(callsBeforeTouch + 1, locateMeCalls)
        composeRule.onNodeWithContentDescription("Hide map controls").assertIsDisplayed()
    }

    /**
     * A finger is not a point (CLAUDE.md, Testing): the row is sampled at real touches off its
     * centre — 12dp out along the vertical axis and toward the map, 11dp toward the screen edge,
     * the region a thumb actually covers — and every one must reach locate. One test per sample,
     * since the harness allows one screen per test and a minimise from one sample must not mask
     * the next. The outer-side sample lands at 21dp from the screen edge, one dp inside locate's
     * territory: the handle's 20dp box owns 0–20dp *inclusive* (hit-testing gives a boundary
     * pixel to the box), so a sample at exactly 20dp is the handle's by rule, not by bug —
     * confirmed, that sample failed against the fixed code. That outer sample is the one that
     * was missing, and the one a 48dp handle takes.
     */
    private fun assertLocateReachedAt(dx: Dp, dy: Dp) {
        var locateMeCalls = 0
        setScreen(onLocateMe = { locateMeCalls++ })
        val callsBeforeTouch = locateMeCalls
        val centre = centerOfContentDescription("Center on my location")
        val delta = with(composeRule.density) { Offset(dx.toPx(), dy.toPx()) }
        val rowBounds = composeRule.onNodeWithContentDescription("Center on my location").getUnclippedBoundsInRoot()
        val point = centre + delta
        with(composeRule.density) {
            assertTrue(
                "sample ($dx, $dy) must lie inside locate's own row $rowBounds for this test to mean anything",
                point.x >= rowBounds.left.toPx() && point.x <= rowBounds.right.toPx() &&
                    point.y >= rowBounds.top.toPx() && point.y <= rowBounds.bottom.toPx(),
            )
        }

        composeRule.onRoot().performTouchInput { click(point) }
        composeRule.waitForIdle()

        assertEquals("sample ($dx, $dy) did not reach locate", callsBeforeTouch + 1, locateMeCalls)
        composeRule.onNodeWithContentDescription("Hide map controls").assertIsDisplayed()
    }

    /** The outer-side sample: 11dp toward the screen edge from locate's centre, i.e. 21dp in from the edge — one dp past the handle's own inclusive 20dp boundary. Fails with the tap box at 48dp. */
    @Test
    fun `a real touch 11dp toward the screen edge from the locate row's centre reaches locate`() = assertLocateReachedAt(dx = 11.dp, dy = 0.dp)

    @Test
    fun `a real touch 12dp toward the map from the locate row's centre reaches locate`() = assertLocateReachedAt(dx = (-12).dp, dy = 0.dp)

    @Test
    fun `a real touch 12dp above the locate row's centre reaches locate`() = assertLocateReachedAt(dx = 0.dp, dy = (-12).dp)

    @Test
    fun `a real touch 12dp below the locate row's centre reaches locate`() = assertLocateReachedAt(dx = 0.dp, dy = 12.dp)

    /** The handle still does its job from its own mark: a real touch there minimises. Passes before and after — a regression guard on the narrowed box, not evidence of the change. */
    @Test
    fun `a real touch on the minimize handle's own mark still minimises the cluster`() {
        setScreen()
        val markBounds = composeRule.onNodeWithTag("map-icon-bar-minimize-handle-mark", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val markCentre = with(composeRule.density) {
            Offset(((markBounds.left + markBounds.right) / 2).toPx(), ((markBounds.top + markBounds.bottom) / 2).toPx())
        }

        composeRule.onRoot().performTouchInput { click(markCentre) }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Show map controls").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Center on my location").assertDoesNotExist()
    }

    /** The side is part of the position too: snapped left, a tab change and return keeps it left. Fails with the state left inside CompactMapTab. */
    @Test
    fun `leaving the Map tab and returning keeps the cluster on the left edge`() {
        setScreen()
        val leftBefore = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().left
        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dxDp = (-160).dp)
        val leftAfterSnap = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().left
        assertTrue("expected the bar to have snapped left ($leftBefore -> $leftAfterSnap)", leftAfterSnap.value < leftBefore.value)

        composeRule.onNodeWithText("List").performClick()
        composeRule.onNodeWithText("Maps").performClick()
        composeRule.waitForIdle()

        val leftOnReturn = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().left
        assertTrue("expected the bar still on the left edge ($leftAfterSnap) after a tab change, was $leftOnReturn", abs(leftOnReturn.value - leftAfterSnap.value) <= 1f)
    }

    // ── Icon-bar-unify-container dispatch: the cluster, not the bar, is what stays in bounds ──

    private fun pillBottom(): Dp = composeRule.onNodeWithTag("control-pill").getUnclippedBoundsInRoot().bottom

    /**
     * The bug, reproduced then disproven: dragged to the bottom of fullscreen's own range, the
     * *pill's* bottom edge — not the bar's — must be on screen. Before the container, the clamp
     * measured the bar alone and the record pill hung off the bottom edge while the bar sat
     * legally. Fails with the clamp measuring the bar instead of the container (the reverted
     * variant this test was run against), by roughly the pill's own height plus the gap.
     */
    @Test
    fun `dragged to the bottom of its range in fullscreen, the control pill's bottom edge stays on screen`() {
        setScreen()
        val rootBottom = composeRule.onRoot().getUnclippedBoundsInRoot().bottom
        val pillBottomBefore = pillBottom()

        touchFullscreenRow("Fullscreen")
        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dyDp = 2000.dp)

        val pillBottomAfter = pillBottom()
        assertTrue("expected the cluster to have moved down (pill bottom $pillBottomBefore -> $pillBottomAfter)", pillBottomAfter.value > pillBottomBefore.value)
        assertTrue(
            "expected the control pill's bottom edge ($pillBottomAfter) at or above the screen's bottom ($rootBottom) at the bottom of fullscreen's drag range",
            pillBottomAfter.value <= rootBottom.value,
        )
    }

    /** Outside fullscreen the same bound is the nav's top: the pill, not just the bar, must clear it. */
    @Test
    fun `dragged to the bottom of its range outside fullscreen, the control pill's bottom edge stays above the bottom nav`() {
        setScreen()
        val navTop = bottomNavTop()
        val pillBottomBefore = pillBottom()

        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dyDp = 2000.dp)

        val pillBottomAfter = pillBottom()
        assertTrue("expected the cluster to have moved down (pill bottom $pillBottomBefore -> $pillBottomAfter)", pillBottomAfter.value > pillBottomBefore.value)
        assertTrue(
            "expected the control pill's bottom edge ($pillBottomAfter) at or above the nav's top ($navTop)",
            pillBottomAfter.value <= navTop.value,
        )
    }

    /**
     * The second half of the on-device finding: exiting fullscreen from the bottom of the range
     * used to move the bar up and clear the nav while leaving the directions pill sitting on it.
     * The whole cluster moves now — the pill clears the nav, and the bar and pill moved by the
     * same amount, i.e. as one unit.
     */
    @Test
    fun `exiting fullscreen from the bottom of the range moves the whole cluster above the nav, bar and pill together`() {
        setScreen()
        val navTop = bottomNavTop()
        touchFullscreenRow("Fullscreen")
        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dyDp = 2000.dp)
        val barTopLow = composeRule.onNodeWithContentDescription("Exit fullscreen").getUnclippedBoundsInRoot().top
        val pillBottomLow = pillBottom()

        touchFullscreenRow("Exit fullscreen")

        val barTopAfter = composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot().top
        val pillBottomAfter = pillBottom()
        val barShift = barTopAfter.value - barTopLow.value
        val pillShift = pillBottomAfter.value - pillBottomLow.value
        assertTrue("expected the cluster to have moved up on exit (bar shift $barShift)", barShift < -1f)
        assertTrue("expected the pill to have moved with the bar (bar shift $barShift, pill shift $pillShift)", abs(barShift - pillShift) <= 1f)
        assertTrue(
            "expected the control pill's bottom edge ($pillBottomAfter) at or above the nav's top ($navTop) after leaving fullscreen",
            pillBottomAfter.value <= navTop.value,
        )
    }

    /**
     * Owner finding on device: the centre-pin picker's OK/Cancel row sat under the app's bottom
     * nav (and under Android's own navigation bar in fullscreen). Opened the real way — add row,
     * "Trip" chip — then the confirm surface's own bottom edge is checked against the nav's real
     * top, and "OK" is real-touched to prove the row is where it can be hit. The fullscreen half
     * (system navigation-bar inset) is device-only by construction: Robolectric reports that inset
     * as zero, so a test of it here would pass without checking anything. Fails with the inset
     * removed (confirm row bottom at the screen's bottom, 80dp under the nav's top).
     */
    @Test
    fun `the centre-pin picker's confirm row sits above the bottom nav outside fullscreen, and OK is tappable there`() {
        var placed = 0
        setScreen(onStartLogEntry = { _, _ -> placed++ })
        val navTop = bottomNavTop()

        composeRule.onRoot().performTouchInput { click(centerOfContentDescription(addRowDescription)) }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { click(centerOfText("Find")) }
        composeRule.waitForIdle()

        val confirmRow = composeRule.onNodeWithTag(CENTRE_PIN_CONFIRM_ROW_TAG).getUnclippedBoundsInRoot()
        assertTrue(
            "expected the confirm row's bottom (${confirmRow.bottom}) at or above the nav's top ($navTop)",
            confirmRow.bottom.value <= navTop.value,
        )

        composeRule.onRoot().performTouchInput { click(centerOfText("OK")) }
        composeRule.waitForIdle()

        assertEquals(1, placed)
    }

    // ── Stale-clamp-bound dispatch: the drag path must read the same live bound as the effect ──

    /**
     * Owner finding on device (symptom A), reproduced here before the fix at 640dp against the
     * nav's 560dp top: the drag handler's `pointerInput(Unit)` block starts on the *first* pointer
     * event and never restarts, so the clamp it calls kept the `isFullscreen` value from the
     * user's first drag. A first drag in fullscreen therefore let every later drag, outside
     * fullscreen too, pass under the nav — while the bounds-change effect, reading the fresh
     * value, kept correcting the position the next drag undid. Fails with the live reads
     * reverted to plain captures.
     */
    @Test
    fun `after a first drag in fullscreen, a drag outside fullscreen still stops the cluster at the bottom nav`() {
        setScreen()
        val navTop = bottomNavTop()

        touchFullscreenRow("Fullscreen")
        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dyDp = 2000.dp)
        touchFullscreenRow("Exit fullscreen")
        assertTrue("expected the exit to have corrected the position first (pill bottom ${pillBottom()}, nav top $navTop)", pillBottom().value <= navTop.value)

        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dyDp = 2000.dp)

        assertTrue(
            "expected the control pill's bottom (${pillBottom()}) at or above the nav's top ($navTop) after a drag outside fullscreen that followed a drag in it",
            pillBottom().value <= navTop.value,
        )
    }

    /**
     * The other direction of the same capture (symptom B, which the owner saw as a theme
     * difference — it was a different first-drag order): a first drag outside fullscreen left the
     * nav bound in the drag path for good, so fullscreen drags were capped at the nav's former top
     * and the cluster never used the space fullscreen frees. Fails with the live reads reverted,
     * pill bottom stuck exactly at the nav's top. Not theme-keyed: the nav's height is the same in
     * both themes in the code and Robolectric reports zero insets, so a theme test here would
     * assert nothing.
     */
    @Test
    fun `after a first drag outside fullscreen, a drag in fullscreen still reaches below the nav's former top`() {
        setScreen()
        val navTop = bottomNavTop()

        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dyDp = 2000.dp)
        assertTrue("expected the first drag to stop at the nav (pill bottom ${pillBottom()}, nav top $navTop)", pillBottom().value <= navTop.value)

        touchFullscreenRow("Fullscreen")
        dragIconBarHandle(tag = "map-icon-bar-minimize-handle", dyDp = 2000.dp)

        assertTrue(
            "expected the control pill's bottom (${pillBottom()}) below the nav's former top ($navTop) in fullscreen, after a first drag outside it",
            pillBottom().value > navTop.value,
        )
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

/**
 * A magnetic heading with a trustworthy uncertainty (2°, well under the 15° threshold) at a fixed
 * timestamp — what every pre-existing compass test meant by "a heading of N". [emit] hands the
 * compass-reliability tests full control of the reading, uncertainty and timestamp.
 */
private class FakeCompassProvider(initial: Float?) : CompassProvider {
    private val state = MutableStateFlow(initial?.let { trusted(it) })
    override val heading: Flow<CompassReading?> = state
    fun emit(reading: CompassReading?) { state.value = reading }

    companion object {
        fun trusted(headingDegrees: Float, timestampMillis: Long = 0L) =
            CompassReading(headingDegrees, HeadingUncertainty.Estimated(2f), timestampMillis)
    }
}

private object IconStackUnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult =
        error("getCurrentLocation() is not part of this test's path and must not be called")
}

private object IconStackNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

/** One fix, then nothing more — the HUD's "current" position; its age is whatever the test's clock says. */
private class IconStackFixedLocationTracker(fix: LocationFix.Update) : LocationTracker {
    override val fixes: Flow<LocationFix> = flowOf(fix)
}

private class IconStackFixedDeclination(private val degrees: Float) : DeclinationProvider {
    override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = degrees
}

private class IconStackFakeLocationTracker(override val fixes: MutableSharedFlow<LocationFix>) : LocationTracker

private object IconStackEmptyRepository : MushroomRepository, TaxonSearchRepository {
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

/**
 * Persist-fullscreen dispatch: the one map preference this file exercises, held in memory — what
 * the screen read at launch ([persistedFullscreen]) and every value it wrote back
 * ([fullscreenWrites]). Everything else is the same explicit "not exercised" failure the stub
 * below returns.
 */
private class IconStackFakeMapPreferencesRepository(
    private val persistedFullscreen: Result<Boolean>,
) : MapPreferencesRepository {
    val fullscreenWrites = mutableListOf<Boolean>()
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getMapFullscreen(): Result<Boolean> = persistedFullscreen
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> {
        fullscreenWrites += fullscreen
        return Result.success(Unit)
    }
}

private object IconStackStubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun getMapFullscreen(): Result<Boolean> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = Result.failure(UnsupportedOperationException("map fullscreen preference not exercised by this test"))
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
