package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LastKnownLocationSource
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.ShownPosition
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.MapOverlayContent
import com.zynergylabs.forager.app.ui.map.MapPosition
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.LocalMapPosition
import com.zynergylabs.forager.app.ui.map.PositionLook
import com.zynergylabs.forager.app.ui.map.positionLookOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Dispatch 2026-09-28-510 (RECORD -508, and the owner's answers in the coder's window) through the real
 * [AvailabilityScreen] and the real [AvailabilityViewModel], fixes arriving on the ViewModel's own live
 * collection (started by [AvailabilityViewModel.onEnteredForeground], as `MainActivity` starts it):
 * a network reading over 50 m shows the approximate dot and the strip says so; it never
 * becomes the gated fix, so never "Arrived" and never the waypoint's line; a far target shows "≈" with
 * the needle, a near one hides it with the message, and while returning there is no needle; a GPS fix
 * replaces it; with no reading the last known position shows with its age.
 *
 * Dispatch 2026-09-28-535 (RECORD -534, the owner: "the bubble message is repeating what the strip
 * says ... my vote is for the bubble message", and "Remove both"): no bubble under the dot, for an
 * approximate position or a last known one; the strip and the HUD carry the words.
 *
 * The map is [PositionMapSlot], which records the position the screen hands every map
 * ([LocalMapPosition]). How
 * MapLibre draws the dot and whether the camera centres on it is device-only: a real MapView cannot run
 * under Robolectric. What MapLibre is handed is `AppLocationEngineTest`'s and `PositionLookOptionsTest`'s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenApproximatePositionTest {

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
    private val creek = Waypoint("wp-1", 45.326, -122.634, null, "Creek pin", "", 1_000L)
    private val origin = Waypoint("origin", 45.31, -122.634, null, "Start", "", 500L, trackId = "t1", designation = WaypointDesignation.ORIGIN)

    /** Metres per degree of latitude on the sphere GeoDistance uses, as `AvailabilityScreenWaypointNavigateTest` has it. */
    private val metersPerDegree = 111_195.08

    /** A network reading [metersSouth] due south of [of], 120 m accuracy, stamped 123 ms past the second, [ageMillis] old. */
    private fun network(metersSouth: Double, of: Waypoint = creek, ageMillis: Long = 1_000L) =
        LocationFix.Update(lat = of.lat - metersSouth / metersPerDegree, lng = of.lng, altitude = null, accuracyMeters = 120f, timestampEpochMillis = t - ageMillis + 123, provider = FixProvider.NETWORK)

    /** A GPS fix [metersSouth] due south of the creek pin, the S22's 3.79 m, on the whole second. */
    private fun gps(metersSouth: Double) =
        LocationFix.Update(lat = creek.lat - metersSouth / metersPerDegree, lng = creek.lng, altitude = 50.0, accuracyMeters = 3.79f, timestampEpochMillis = t, provider = FixProvider.GPS)

    private val fixes = MutableSharedFlow<LocationFix>(replay = 1)
    private val tracker = object : LocationTracker {
        override val fixes: Flow<LocationFix> = this@AvailabilityScreenApproximatePositionTest.fixes
    }
    private var lastKnown: LocationFix.Update? = null
    private var recording = false
    private var returning = false

    private val map = PositionMapSlot()
    private lateinit var viewModel: AvailabilityViewModel

    private fun setScreen() {
        viewModel = mapLayersViewModel(locationTracker = tracker, lastKnownLocation = LastKnownLocationSource { lastKnown })
        // What MainActivity does on ON_START: the ViewModel's one live collection, and the last known read.
        viewModel.onEnteredForeground()
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            AvailabilityScreen(
                uiState = state,
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
                    onDeletePlannedTrip = {},
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
                records = AvailabilityScreenRecords(
                    waypoints = listOf(creek),
                    waypointsLoaded = true,
                ),
                recording = AvailabilityScreenRecording(
                    isRecording = recording,
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = returning,
                    onToggleReturning = { returning = !returning },
                    navigationTarget = if (recording) origin else null,
                    returnRoute = ReturnRoute.Ahead(LatLng(45.32, -122.634), 1_800.0),
                    onNavigateToWaypoint = viewModel::onNavigateToWaypoint,
                    onStopWaypointNavigation = viewModel::onStopWaypointNavigation,
                ),
                compassProvider = FixedCompass(0f),
                computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                currentTime = CurrentTimeProvider { t + 1_000L },
            )
        }
        composeRule.waitForIdle()
    }

    private fun arrive(fix: LocationFix) {
        fixes.tryEmit(fix)
        composeRule.waitForIdle()
    }

    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun text(tag: String): String =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    private fun textShown(text: String) = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /**
     * Dispatch 2026-09-28-535: nothing on screen reads exactly [words], the bubble's whole text. The strip
     * and the HUD carry the same words with ", finding GPS…" after them, so an exact match is the bubble's.
     */
    private fun assertNoBubble(words: String) = assertFalse("no bubble reading \"$words\" (dispatch -535)", textShown(words))

    private fun shownPosition(): ShownPosition = map.position!!.shown.value

    private fun look(): PositionLook = map.position!!.let { positionLookOf(it.shown.value, it.liveFix.value) }

    // ── The map and the strip ──

    @Test
    fun `a network reading over 50 m shows the approximate dot with no bubble, and the strip says so with no coordinates`() {
        setScreen()
        val reading = network(metersSouth = 3_240.0)

        arrive(reading)

        assertNull("the reading never becomes the gated fix, which \"Arrived\", the line and a find's location read", viewModel.uiState.value.liveFix)
        assertEquals("the map is handed the approximate position", ShownPosition.Approximate(reading), shownPosition())
        assertEquals("drawn as the soft dot", PositionLook.APPROXIMATE, look())
        assertNoBubble(APPROXIMATE_LOCATION_TEXT)
        // The strip: heading, then the note; no coordinates, no elevation.
        assertEquals("0° N", text(COMPASS_STRIP_HEADING_TAG))
        assertEquals("Approximate location, finding GPS…", text(COMPASS_STRIP_POSITION_NOTE_TAG))
        assertFalse("not \"Location services unavailable\"", shown(COMPASS_STRIP_NO_FIX_TAG))
        assertFalse("no coordinates for a reading known to 120 m", textShown(coordinatesStripText(LatLng(reading.lat, reading.lng), false)))
    }

    @Test
    fun `a GPS fix replaces it, the normal dot, the strip's coordinates`() {
        setScreen()
        arrive(network(metersSouth = 3_240.0))
        val fix = gps(metersSouth = 500.0)

        arrive(fix)

        assertEquals(fix, viewModel.uiState.value.liveFix)
        assertEquals(ShownPosition.Precise(fix), shownPosition())
        assertEquals(PositionLook.PRECISE, look())
        assertFalse("the strip's note goes", shown(COMPASS_STRIP_POSITION_NOTE_TAG))
        assertTrue("the strip shows the fix's coordinates", textShown(coordinatesStripText(LatLng(fix.lat, fix.lng), false)))
    }

    @Test
    fun `with no live reading the last known position shows, greyed, with its age, until anything newer arrives`() {
        lastKnown = LocationFix.Update(45.40, -122.70, null, 30f, t + 1_000L - 2L * 60L * 60L * 1_000L, provider = FixProvider.GPS)
        setScreen()

        assertEquals(ShownPosition.LastKnown(lastKnown!!), shownPosition())
        assertEquals("drawn grey", PositionLook.LAST_KNOWN, look())
        assertNoBubble("Last seen 2 h ago")
        assertEquals("Last seen 2 h ago, finding GPS…", text(COMPASS_STRIP_POSITION_NOTE_TAG))
        assertNull("never the gated fix", viewModel.uiState.value.liveFix)

        val reading = network(metersSouth = 3_240.0)
        arrive(reading)

        assertEquals("anything newer replaces it", ShownPosition.Approximate(reading), shownPosition())
        assertEquals("Approximate location, finding GPS…", text(COMPASS_STRIP_POSITION_NOTE_TAG))
        assertNoBubble(APPROXIMATE_LOCATION_TEXT)
    }

    // ── The HUD ──

    /**
     * Dispatch 2026-10-12-08 (RECORD -951, option (a)): what the position is (approximate, last seen) shows once, on the strip,
     * which stays while navigating; the navigation info's status line leaves it out.
     */
    private fun assertPositionMessageOnStripOnly(stripNote: String) {
        assertEquals("the strip's note", stripNote, text(COMPASS_STRIP_POSITION_NOTE_TAG))
        assertEquals("the card's status line leaves it out", "", text(NAVIGATION_HUD_STATUS_TAG))
        assertEquals("the message shows once", 1, composeRule.onAllNodesWithText(stripNote).fetchSemanticsNodes().size)
        assertEquals("and the card's own wording does not show", 0, composeRule.onAllNodesWithText(APPROXIMATE_HUD_TEXT).fetchSemanticsNodes().size)
    }


    @Test
    fun `navigating to a far waypoint, the HUD shows the approximate distance and the needle`() {
        setScreen()
        viewModel.onNavigateToWaypoint(creek.id)
        // 3,240 m due south, 120 m accuracy: far (beyond 240 m); step 200 m, 3,240 / 200 = 16.2 -> 16 -> 3.2 km.
        arrive(network(metersSouth = 3_240.0))

        composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).assertIsDisplayed()
        assertEquals("≈ 3.2 km", text(NAVIGATION_HUD_DISTANCE_TAG))
        assertEquals("the needle: the creek is due north of a walker facing north", "ahead 0°", text(NAVIGATION_HUD_TARGET_TAG))
        // Dispatch 2026-09-28-677: from an approximate reading the figure is the straight line, and says so.
        assertEquals("straight", text(NAVIGATION_HUD_DISTANCE_KIND_TAG))
        // Dispatch 2026-10-12-08 (RECORD -951, option (a): GPS status messages show on the strip only): the strip stays while
        // navigating and carries the note, once; the card's status line leaves it out. Was: `assertEquals(APPROXIMATE_HUD_TEXT,
        // text(NAVIGATION_HUD_STATUS_TAG))` and `assertFalse("the strip gives way to the HUD", shown(COMPASS_STRIP_POSITION_NOTE_TAG))`.
        assertPositionMessageOnStripOnly("Approximate location, finding GPS…")
        assertFalse("no coordinates for a reading known to 120 m", shown(COMPASS_STRIP_COORDINATES_TAG))
        assertNoBubble(APPROXIMATE_LOCATION_TEXT)
    }

    @Test
    fun `navigating to a waypoint inside twice the circle, the needle hides and the HUD reads the message`() {
        setScreen()
        viewModel.onNavigateToWaypoint(creek.id)
        // 150 m with 120 m accuracy: inside 240 m.
        arrive(network(metersSouth = 150.0))

        assertEquals("—", text(NAVIGATION_HUD_DISTANCE_TAG))
        assertEquals("no needle", "", text(NAVIGATION_HUD_TARGET_TAG))
        // RECORD -951 (a): the note on the strip, once; was APPROXIMATE_HUD_TEXT on the card's status line.
        assertPositionMessageOnStripOnly("Approximate location, finding GPS…")
    }

    @Test
    fun `an approximate reading at the waypoint itself never arrives, and draws no line`() {
        setScreen()
        viewModel.onNavigateToWaypoint(creek.id)

        arrive(network(metersSouth = 0.0))

        assertFalse("never \"Arrived\"", text(NAVIGATION_HUD_DISTANCE_TAG) == ARRIVED_TEXT)
        val route = map.content!!.route!!
        assertNull("no arrival ring", route.arrivedAt)
        assertTrue("the waypoint's own pin is still drawn", map.content!!.waypoints.any { it.id == "wp-1" })
        assertNull("the dashed line waits for GPS, as it always has", route.straight)
    }

    @Test
    fun `returning with an approximate position, there is no needle, and the distance is approximate`() {
        recording = true
        returning = true
        setScreen()
        // 3,240 m due south of the start.
        arrive(network(metersSouth = 3_240.0, of = origin))

        assertEquals("≈ 3.2 km", text(NAVIGATION_HUD_DISTANCE_TAG))
        assertEquals("no needle while returning (decision D5; the owner's answer)", "", text(NAVIGATION_HUD_TARGET_TAG))
        // RECORD -951 (a): the note on the strip, once; was APPROXIMATE_HUD_TEXT on the card's status line.
        assertPositionMessageOnStripOnly("Approximate location, finding GPS…")
    }

    @Test
    fun `navigating with only the last known position, no distance and no needle, and its age`() {
        lastKnown = LocationFix.Update(45.40, -122.70, null, 30f, t + 1_000L - 2L * 60L * 60L * 1_000L, provider = FixProvider.GPS)
        setScreen()
        viewModel.onNavigateToWaypoint(creek.id)
        composeRule.waitForIdle()

        assertEquals("—", text(NAVIGATION_HUD_DISTANCE_TAG))
        assertFalse("no needle", text(NAVIGATION_HUD_TARGET_TAG).startsWith("Turn"))
        // RECORD -951 (a): the note on the strip, once; was the same words on the card's status line.
        assertPositionMessageOnStripOnly("Last seen 2 h ago, finding GPS…")
        assertNoBubble("Last seen 2 h ago")
    }

    /** The map: records what the screen hands it and the position every map under the screen is given. */
    private class PositionMapSlot {
        var content: MapOverlayContent? = null
        var position: MapPosition? = null

        val slot: MapSlot = { _, content, _, _, _, _, _, _, modifier ->
            this.content = content
            this.position = LocalMapPosition.current
            Box(modifier.testTag(POSITION_MAP_TAG))
        }
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        const val POSITION_MAP_TAG = "approximate-position-map"
    }

    // ── Dispatch 2026-09-28-527: a 15 m fix acts only if it is GPS ──

    /** A 15 m fix at the creek pin from [provider], a second old: network stamped off the second, the others on it. */
    private fun at15m(provider: FixProvider) = LocationFix.Update(
        lat = creek.lat, lng = creek.lng, altitude = 50.0, accuracyMeters = 15f,
        timestampEpochMillis = if (provider == FixProvider.NETWORK) t + 123 else t, provider = provider,
    )

    private fun assertNeverActs(provider: FixProvider) {
        setScreen()
        viewModel.onNavigateToWaypoint(creek.id)
        val reading = at15m(provider)

        arrive(reading)

        assertNull("$provider: never the gated fix", viewModel.uiState.value.liveFix)
        assertFalse("$provider: never \"Arrived\"", text(NAVIGATION_HUD_DISTANCE_TAG) == ARRIVED_TEXT)
        val route = map.content!!.route!!
        assertNull("$provider: no arrival ring", route.arrivedAt)
        assertNull("$provider: no line to the waypoint", route.straight)
        assertEquals("$provider: shown as the approximate position", ShownPosition.Approximate(reading), shownPosition())
        assertEquals(PositionLook.APPROXIMATE, look())
        // Navigating, the HUD stands where the strip was (as in the -510 tests above): its message, and no
        // needle, since the waypoint is inside twice the reading's circle.
        // RECORD -951 (a): the note on the strip, once; was APPROXIMATE_HUD_TEXT on the card's status line.
        assertEquals("$provider: the strip's approximate note", "Approximate location, finding GPS…", text(COMPASS_STRIP_POSITION_NOTE_TAG))
        assertEquals("$provider: the card's status line leaves it out", "", text(NAVIGATION_HUD_STATUS_TAG))
        assertEquals("$provider: no needle", "", text(NAVIGATION_HUD_TARGET_TAG))
    }

    @Test
    fun `a 15 m network fix at the waypoint never arrives, draws no line, and shows as the approximate position`() =
        assertNeverActs(FixProvider.NETWORK)

    @Test
    fun `a 15 m fix from an unknown provider at the waypoint is treated as network`() =
        assertNeverActs(FixProvider.UNKNOWN)

    /** The control: the same fix from GPS arrives, as it does today, so the two above could have. */
    @Test
    fun `a 15 m GPS fix at the waypoint arrives, the precise dot`() {
        setScreen()
        viewModel.onNavigateToWaypoint(creek.id)
        val fix = at15m(FixProvider.GPS)

        arrive(fix)

        assertEquals(fix, viewModel.uiState.value.liveFix)
        assertEquals(ARRIVED_TEXT, text(NAVIGATION_HUD_DISTANCE_TAG))
        assertTrue("the arrival ring", map.content!!.route!!.arrivedAt != null)
        assertEquals(ShownPosition.Precise(fix), shownPosition())
        assertEquals(PositionLook.PRECISE, look())
    }
}
