package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.WaypointNavigation
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayto.FakeEngines
import com.zynergylabs.forager.app.domain.wayto.FakeTrailRouter
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointNote
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_NAVIGATE_TAG
import com.zynergylabs.forager.app.ui.map.RouteHomeIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.routeHomeFeatureCollections
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * Step 6 PR 3 (dispatch 2026-10-26-11), through the real [AvailabilityScreen] and the real [AvailabilityViewModel], with
 * the real [FindWayToWaypointUseCase] over a fake engine whose one trail runs east-west through [Ground]'s origin: Navigate
 * from the waypoint's bubble swaps the straight dashed line for the way along trails (a straight lead-in, the trail, a
 * straight final leg) and the HUD reads "by trail"; outside a downloaded area it stays today's straight line, with its
 * note. Arrival is still the straight distance's, and Back and the X still end it.
 *
 * Every touch on a control is a real one at screen coordinates (CLAUDE.md, Testing). The map is [BubbleMapSlot], which
 * records what it is handed; how MapLibre draws the three kinds of line is device-only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenWaypointRouteTest {

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
    private val there = at(500.0, -60.0)
    private val pin = Waypoint("wp-1", there.lat, there.lng, null, "Creek pin", "", 1_000L)

    private fun fixAt(p: LatLng) = LocationFix.Update(lat = p.lat, lng = p.lng, altitude = 50.0, accuracyMeters = 3.79f, timestampEpochMillis = t, provider = FixProvider.GPS)

    private val publishedFix = MutableStateFlow<LocationFix.Update?>(fixAt(at(0.0, 40.0)))
    private val tracker = object : LocationTracker { override val fixes: Flow<LocationFix> = publishedFix.filterNotNull() }
    private val clock = CurrentTimeProvider { t + 1_000L }
    private val router = FakeTrailRouter(listOf(at(-3_000.0, 0.0) to at(3_000.0, 0.0)))
    private val store = InMemoryWaypointNavigation()

    private val map = BubbleMapSlot(listOf(StubGlyph(MapLayerIds.WAYPOINTS, "wp-1", 60.dp, 380.dp, there)))
    private lateinit var viewModel: AvailabilityViewModel

    private fun setScreen(regions: List<OfflineRegionSummary> = listOf(Ground.region())) {
        val find = FindWayToWaypointUseCase(FakeRegions(*regions.toTypedArray()), FakeEngines(router), ErrorLog { _, _, _ -> })
        viewModel = mapLayersViewModel(
            waypointNavigationRepository = store,
            locationTracker = tracker,
            readWaypoint = { id -> Result.success(pin.takeIf { it.id == id }) },
            currentTime = clock,
            findWayToWaypoint = { from, to -> find(from, to) },
        )
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
                    waypoints = listOf(pin),
                    waypointsLoaded = true,
                ),
                recording = AvailabilityScreenRecording(
                    isRecording = false,
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = false,
                    onToggleReturning = {},
                    navigationTarget = null,
                    onNavigateToWaypoint = viewModel::onNavigateToWaypoint,
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

    private fun navigate() {
        touchCentreOf(glyphTag("wp-1"))
        touchCentreOf(MAP_BUBBLE_NAVIGATE_TAG)
    }

    private fun walkTo(p: LatLng) {
        composeRule.runOnIdle { publishedFix.value = fixAt(p) }
        composeRule.waitForIdle()
    }

    /** The kind named by each feature of the map's ahead source, in order. */
    private fun aheadKinds(): List<String?> =
        routeHomeFeatureCollections(map.content!!.route).getValue(RouteHomeIds.AHEAD_SOURCE).features()!!.map { it.getStringProperty(RouteHomeIds.PART_PROPERTY) }

    private fun assertNotNavigating() {
        assertNull(viewModel.uiState.value.waypointNavigation)
        assertNull("the way along trails is gone", viewModel.uiState.value.waypointRoute)
        assertFalse("no HUD", shown(NAVIGATION_HUD_TAG))
        assertNull("no line on the map", map.content!!.route)
    }

    // ── Inside a downloaded area with trail routes (step path 3.1) ──

    @Test
    fun `Navigate inside a downloaded area swaps the straight line for the way along trails, and the HUD reads by trail`() {
        setScreen()

        navigate()

        assertEquals(WaypointNavigation("wp-1", resumesReturn = false), viewModel.uiState.value.waypointNavigation)
        val route = map.content!!.route!!
        assertNull("no straight dashed line while the trail route is shown", route.straight)
        assertNotNull(route.line)
        assertEquals("the lead-in, the trail, the final leg", listOf(RouteHomeIds.PART_STRAIGHT, RouteHomeIds.PART_TRAIL, RouteHomeIds.PART_STRAIGHT), aheadKinds())
        assertEquals("by trail", text(NAVIGATION_HUD_DISTANCE_KIND_TAG))
        assertEquals("the distance along the line", formatDistanceMeters(600.0, viewModel.uiState.value.distanceUnit), text(NAVIGATION_HUD_DISTANCE_TAG))
        assertEquals("no note", "", text(NAVIGATION_HUD_STATUS_TAG))
    }

    // ── Outside a downloaded area (step path 3.2) ──

    @Test
    fun `outside a downloaded area it stays today's straight line, with the note`() {
        setScreen(regions = emptyList())

        navigate()

        val route = map.content!!.route!!
        assertEquals("the straight dashed line", listOf(at(0.0, 40.0), there).map { it.lat to it.lng }, route.straight!!.map { it.lat to it.lng })
        assertNull("no trail line", route.line)
        assertEquals("straight", text(NAVIGATION_HUD_DISTANCE_KIND_TAG))
        val note = WayToWaypointNote.NO_TRAIL_ROUTES
        val status = text(NAVIGATION_HUD_STATUS_TAG)
        assertTrue("the note, whole or short: $status", status == note.text || status == wayToWaypointNoteShortText(note))
    }

    // ── Arrival, on the straight distance (step path 3.4) ──

    @Test
    fun `arrival is judged on the straight distance to the waypoint, within 15 m`() {
        setScreen()
        navigate()

        walkTo(at(500.0, -44.0)) // 16 m from the waypoint, on the final leg
        assertFalse("16 m: not arrived", text(NAVIGATION_HUD_DISTANCE_TAG) == ARRIVED_TEXT)

        walkTo(at(500.0, -46.0)) // 14 m
        assertEquals(ARRIVED_TEXT, text(NAVIGATION_HUD_DISTANCE_TAG))
        val route = map.content!!.route!!
        assertEquals(there.lat to there.lng, route.arrivedAt!!.let { it.lat to it.lng })
        assertTrue("the line ends", aheadKinds().isEmpty())
        assertEquals("navigation stays on until ended", WaypointNavigation("wp-1", resumesReturn = false), viewModel.uiState.value.waypointNavigation)
    }

    // ── Back and the X still end it (step path 3.1 step 5, 3.4 step 3) ──

    @Test
    fun `the X-circle ends it and the way along trails goes`() {
        setScreen()
        navigate()
        assertNotNull(map.content!!.route!!.line)

        touchCentreOf(RETURN_CONTROL_TAG)

        assertNotNavigating()
    }

    @Test
    fun `Back ends it and the way along trails goes`() {
        setScreen()
        navigate()
        assertNotNull(map.content!!.route!!.line)

        back()

        assertNotNavigating()
    }

    // ── CLAUDE.md, the Surface pitfall ──

    /** With the way along trails shown and the HUD reading "by trail", real taps just below the HUD reach the map. */
    @Test
    fun `with the way along trails shown, real taps on the map below the HUD reach the map`() {
        setScreen()
        navigate()
        assertEquals("by trail", text(NAVIGATION_HUD_DISTANCE_KIND_TAG))
        val hud = composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).getUnclippedBoundsInRoot()
        val controls = composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
            .filterNot { it.config.getOrNull(SemanticsProperties.TestTag) == "map-slot" }
            .map { with(composeRule.density) { DpRect(it.boundsInRoot.left.toDp(), it.boundsInRoot.top.toDp(), it.boundsInRoot.right.toDp(), it.boundsInRoot.bottom.toDp()) } }
        var sampled = 0
        for (row in listOf(6.dp, 20.dp)) {
            val y = hud.bottom + row
            for (i in 0 until SAMPLES) {
                val x = hud.left + 8.dp + (hud.right - hud.left - 16.dp) * (i.toFloat() / (SAMPLES - 1))
                if (controls.any { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }) continue
                val before = map.taps
                val p = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
                composeRule.onRoot().performTouchInput { click(p) }
                composeRule.waitForIdle()
                assertEquals("a tap at ($x, $y) must reach the map", before + 1, map.taps)
                sampled++
            }
        }
        assertTrue("at least $MIN_SAMPLED points sampled, not $sampled", sampled >= MIN_SAMPLED)
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        const val RETURN_CONTROL_TAG = "control-pill-return-to-vehicle"
        const val SAMPLES = 8
        const val MIN_SAMPLED = 8
    }
}
