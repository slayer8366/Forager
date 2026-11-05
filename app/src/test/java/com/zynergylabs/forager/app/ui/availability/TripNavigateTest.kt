package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.NavigationTargetKind
import com.zynergylabs.forager.app.domain.WaypointNavigation
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.formatDistanceWithAccuracy
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointNote
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointResult
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_DETAILS_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_NAVIGATE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Dispatch 2026-11-05-05, Amendment 1, A8 and B1 (RECORD -2148, the owner: "As a trip (Recommended)", "Navigate goes to the
 * trip itself, with the same screen, the way along trails, the drop-off, and the arrival. Back returns to the trip. Its
 * messages say "trip" where they'd say "waypoint""):
 * - Navigate from the trip's bubble or its card navigates to the trip: the HUD aims at it, the map is handed the dashed line
 *   to it, and the way along trails is asked for its spot;
 * - Back returns to the trip's bubble or card, one step;
 * - the trip deleted while navigating, or while the app was closed, ends it with "Stopped navigating: that trip was deleted.";
 * - a kept trip navigation is picked back up after a restart, and waits for the trips to load rather than ending as deleted;
 * - on arrival the trip's flag is left out under the ring, as a waypoint's pin is (the planner, RECORD -2147);
 * - the way-to notes that name the waypoint name the trip;
 * - a waypoint navigation kept before the update still resumes as a waypoint's.
 *
 * Through the real [AvailabilityScreen] and the real [AvailabilityViewModel], wired as `MainActivity` wires them, as
 * [AvailabilityScreenWaypointNavigateTest] is. Every touch on a control is a real one at screen coordinates; the map is
 * [BubbleMapSlot], which records what it is handed: how MapLibre draws the line, the ring and the flag is device-only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class TripNavigateTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val t = 1_700_000_000_000L
    private val trip = SADDLE
    private val creek = Waypoint("wp-1", 45.30, -122.60, null, "Creek pin", "", 1_000L)

    /** A fix [metersSouth] metres due south of the trip, with the S22's constant 3.79 m accuracy. */
    private fun southOfTrip(metersSouth: Double) = LocationFix.Update(
        lat = trip.location.lat - metersSouth / 111_195.08,
        lng = trip.location.lng,
        altitude = 50.0,
        accuracyMeters = 3.79f,
        timestampEpochMillis = t,
        provider = FixProvider.GPS,
    )

    private val publishedFix = MutableStateFlow<LocationFix.Update?>(null)
    private var fixState by mutableStateOf(southOfTrip(500.0))
    private var fix: LocationFix.Update
        get() = fixState
        set(value) {
            fixState = value
            publishedFix.value = value
        }
    private val tracker = object : LocationTracker { override val fixes: Flow<LocationFix> = publishedFix.filterNotNull() }
    private val clock = CurrentTimeProvider { t + 1_000L }
    private val trips = HeldTripRepository(listOf(trip))
    private val store = InMemoryWaypointNavigation()
    private val asked = mutableListOf<LatLng>()
    private var wayTo: WayToWaypointResult? = null
    private val map = BubbleMapSlot(listOf(SADDLE_GLYPH, StubGlyph(MapLayerIds.WAYPOINTS, creek.id, 60.dp, 500.dp, LatLng(creek.lat, creek.lng))))
    private lateinit var viewModel: AvailabilityViewModel

    private fun setScreen() {
        publishedFix.value = fixState
        val answer = wayTo
        viewModel = mapLayersViewModel(
            tripRepository = trips,
            waypointNavigationRepository = store,
            locationTracker = tracker,
            readWaypoint = { id -> Result.success(listOf(creek).firstOrNull { it.id == id }) },
            currentTime = clock,
            findWayToWaypoint = if (answer == null) null else { _, to, _ -> asked += to; answer },
        )
        viewModel.onEnteredForeground()
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            AvailabilityScreen(
                uiState = state.copy(liveFix = fix),
                searchInputs = AvailabilityScreenSearchInputs(
                    onUseCurrentLocation = {},
                    onManualLatChanged = {},
                    onManualLngChanged = {},
                    onSearchManualCoordinates = {},
                    onRadiusChanged = {},
                    onMonthSelected = {},
                    onMapTabSelected = {},
                    onSeasonalTabSelected = {},
                    onTaxonSearchQueryChanged = {},
                    onTaxonSearchResultSelected = {},
                    onDismissTaxonSuggestions = {},
                    onReopenTaxonSuggestions = {},
                    onPlaceTripPin = { _, _, _ -> },
                    onDeletePlannedTrip = viewModel::onDeletePlannedTrip,
                    onRecentSearchSelected = {},
                ),
                offlineMaps = AvailabilityScreenOfflineMaps(
                    onOfflineMapLatChanged = {},
                    onOfflineMapLngChanged = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                ),
                settings = AvailabilityScreenSettings(
                    onNightModeMapsChanged = {},
                    onThemeModeChanged = {},
                ),
                mapSlot = map.slot,
                records = AvailabilityScreenRecords(waypoints = listOf(creek), waypointsLoaded = true),
                navigation = AvailabilityScreenNavigation(
                    onNavigateToWaypoint = viewModel::onNavigateToWaypoint,
                    onNavigateToTrip = viewModel::onNavigateToTrip,
                    onStopWaypointNavigation = viewModel::onStopWaypointNavigation,
                ),
                compassProvider = FixedCompass(0f),
                computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                currentTime = clock,
            )
        }
        composeRule.waitForIdle()
    }

    // ── Helpers ──

    private fun touchCentreOf(tag: String) {
        composeRule.scrollIntoViewIfScrollable(tag)
        composeRule.onNodeWithTag(tag).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun text(tag: String): String =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    private fun controlLabel(): String? =
        composeRule.onNodeWithTag(RETURN_CONTROL_TAG).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    private fun openBubble() {
        composeRule.onNodeWithTag(glyphTag(trip.id)).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun openCard() {
        openBubble()
        touchCentreOf(MAP_BUBBLE_DETAILS_TAG)
        assertTrue("the trip's card is open", shown(SHEET))
    }

    private fun navigatingTo(): WaypointNavigation? = viewModel.uiState.value.waypointNavigation

    private val toTrip = WaypointNavigation(trip.id, resumesReturn = false, kind = NavigationTargetKind.TRIP)

    private fun hasMessage(text: String) = composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** The HUD aims at the trip, due north of a walker facing north, with the straight distance to it; the map has the line to it. */
    private fun assertHudAimsAtTrip() {
        composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).assertIsDisplayed()
        assertEquals("the needle aims at the trip", "ahead 0°", text(NAVIGATION_HUD_TARGET_TAG))
        val straight = GeoDistance.metersBetween(LatLng(fix.lat, fix.lng), trip.location)
        assertEquals(formatDistanceWithAccuracy(straight, fix.accuracyMeters, viewModel.uiState.value.distanceUnit), text(NAVIGATION_HUD_DISTANCE_TAG))
        assertEquals("Stop navigating", controlLabel())
        assertEquals("the dashed line, from the walker to the trip", listOf(LatLng(fix.lat, fix.lng), trip.location), map.content!!.route!!.straight)
    }

    private fun assertNotNavigating() {
        assertNull(navigatingTo())
        assertFalse("no HUD", shown(NAVIGATION_HUD_TAG))
        assertNull("no line on the map", map.content!!.route)
    }

    // ── Navigate, from the bubble and from the card ──

    @Test
    fun `from the trip's bubble, Navigate navigates to the trip as a trip, and its flag stays drawn`() {
        setScreen()
        openBubble()

        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)

        assertEquals(toTrip, navigatingTo())
        assertEquals(toTrip, store.stored)
        assertFalse("the bubble closes", shown(MAP_BUBBLE_TAG))
        assertHudAimsAtTrip()
        assertTrue("the trip's flag is drawn while on the way", map.content!!.plannedTrips.any { it.id == trip.id })
    }

    @Test
    fun `from the trip's card, Navigate navigates to the trip, and the card closes`() {
        setScreen()
        openCard()

        touchCentreOf(NAVIGATE)

        assertEquals(toTrip, navigatingTo())
        assertFalse("the card closes", shown(SHEET))
        assertHudAimsAtTrip()
    }

    // ── Back returns to the trip ──

    @Test
    fun `Back from a navigation started in the trip's bubble ends it and reopens that bubble, and the next Back closes it`() {
        setScreen()
        openBubble()
        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)

        back()

        assertNotNavigating()
        composeRule.onNode(hasText("Saddle loop") and hasAnyAncestor(hasTestTag(MAP_BUBBLE_TAG)), useUnmergedTree = true).assertIsDisplayed()
        back()
        assertFalse("one step at a time: the bubble closes next", shown(MAP_BUBBLE_TAG))
    }

    @Test
    fun `Back from a navigation started in the trip's card ends it and reopens that card`() {
        setScreen()
        openCard()
        touchCentreOf(NAVIGATE)

        back()

        assertNotNavigating()
        assertTrue("the card is open again", shown(SHEET))
        assertEquals("Saddle loop", text(TITLE))
        assertTrue("the trip's own card, with its date row", shown("record-details-field-trip-date"))
    }

    // ── Deleted meanwhile, or while the app was closed ──

    @Test
    fun `the trip deleted while navigating to it ends the navigation and says so in the trip's words`() {
        setScreen()
        openBubble()
        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)

        composeRule.runOnIdle { viewModel.onDeletePlannedTrip(trip.id) }
        composeRule.waitForIdle()

        assertNotNavigating()
        composeRule.onNodeWithText(TRIP_GONE).assertIsDisplayed()
        assertFalse("not the waypoint's words", hasMessage(WAYPOINT_NAVIGATION_GONE_TEXT))
        assertNull("nothing is left to pick back up", store.stored)
    }

    @Test
    fun `picked back up for a trip deleted while the app was closed, it ends with the trip's words`() {
        store.stored = WaypointNavigation("trip-gone", resumesReturn = false, kind = NavigationTargetKind.TRIP)

        setScreen()

        assertNotNavigating()
        composeRule.onNodeWithText(TRIP_GONE).assertIsDisplayed()
        assertNull(store.stored)
    }

    // ── Picked back up after a restart ──

    @Test
    fun `the app opened again picks the trip navigation back up`() {
        store.stored = toTrip

        setScreen()

        assertEquals(toTrip, navigatingTo())
        assertHudAimsAtTrip()
    }

    @Test
    fun `before the trips have loaded, a trip navigation picked back up waits rather than ending as deleted`() {
        store.stored = toTrip
        trips.gate = CompletableDeferred()

        setScreen()

        assertEquals("still kept", toTrip, navigatingTo())
        assertEquals("still kept for the next start", toTrip, store.stored)
        assertFalse("no HUD yet", shown(NAVIGATION_HUD_TAG))
        assertFalse("no trip message", hasMessage(TRIP_GONE))
        assertFalse("no waypoint message", hasMessage(WAYPOINT_NAVIGATION_GONE_TEXT))

        trips.gate!!.complete(Unit)
        composeRule.waitForIdle()

        assertHudAimsAtTrip()
    }

    @Test
    fun `a waypoint navigation kept before the update, with no kind, still resumes as the waypoint's`() {
        store.stored = WaypointNavigation(creek.id, resumesReturn = false)

        setScreen()

        assertEquals(WaypointNavigation(creek.id, resumesReturn = false, kind = NavigationTargetKind.WAYPOINT), navigatingTo())
        composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).assertIsDisplayed()
        assertEquals("the line ends at the waypoint, not the trip", LatLng(creek.lat, creek.lng), map.content!!.route!!.straight!!.last())
    }

    // ── Arrival ──

    @Test
    fun `within 15 m of the trip the ring is drawn in place of its flag, the line ends, and the HUD reads Arrived`() {
        setScreen()
        openBubble()
        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)

        composeRule.runOnIdle { fix = southOfTrip(10.0) }
        composeRule.waitForIdle()

        val route = map.content!!.route!!
        assertEquals(trip.location, route.arrivedAt)
        assertNull("the dashed line ends", route.straight)
        assertTrue("the trip's flag is left out under the ring", map.content!!.plannedTrips.none { it.id == trip.id })
        assertTrue("the waypoint's pin is untouched", map.content!!.waypoints.any { it.id == creek.id })
        assertEquals(ARRIVED_TEXT, text(NAVIGATION_HUD_DISTANCE_TAG))
        assertEquals("navigation stays on until ended", toTrip, navigatingTo())
    }

    // ── The way along trails, and its notes in the trip's words ──

    @Test
    fun `the way along trails is asked for the trip's spot, and a note that names the waypoint names the trip`() {
        wayTo = WayToWaypointResult.StraightLine(WayToWaypointNote.WAY_IN_BLOCKED)
        setScreen()
        openBubble()
        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)

        assertEquals("asked once, for the trip's own spot", listOf(trip.location), asked)
        val status = text(NAVIGATION_HUD_STATUS_TAG)
        assertTrue(
            "the trip's words, whole or short: '$status'",
            status == "No clear way in to the trip · straight line" || status == "No clear way in to the trip",
        )
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        const val RETURN_CONTROL_TAG = "control-pill-return-to-vehicle"
        // Literals, so this compiles against the base it was written before.
        const val SHEET = "record-details-sheet"
        const val TITLE = "record-details-title"
        const val NAVIGATE = "record-details-navigate"
        const val TRIP_GONE = "Stopped navigating: that trip was deleted."
    }
}
