package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.AlertDelivery
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.RouteLinePartKind
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLines
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import com.zynergylabs.forager.app.domain.wayhome.WayHomeWatch
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.RouteOnMap
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel
import com.zynergylabs.forager.app.ui.track.WayHomeTestTracker
import com.zynergylabs.forager.app.ui.track.WayHomeTestTracks
import com.zynergylabs.forager.app.ui.track.wayHomeRecordingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
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
import org.robolectric.annotation.GraphicsMode

/**
 * The screen rig for the way home (dispatch 2026-10-24-10 Amendment 1, ruling 6): the real recording ViewModel composed
 * into the real [AvailabilityScreen], wired as `MainActivity` wires it (`returnRouteOf(routeHome, wayHomeNote)`,
 * `routeLine`, Return's toggle). Return is tapped with a real touch on the Return control; the way home swaps in; the
 * map is handed the dotted and solid line; and real long-presses below the navigation display still reach the map
 * (CLAUDE.md: a `Surface` intercepts touches across its bounds; a semantic click asserts wiring, not routing).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvailabilityScreenWayHomeRigTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val t = 1_700_000_000_000L
    private val here = Ground.at(0.0, -50.0)
    private val fix = LocationFix.Update(lat = here.lat, lng = here.lng, altitude = 50.0, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)

    private val tracks = WayHomeTestTracks()
    private val tracker = WayHomeTestTracker()
    private val returnWatch = ReturnWatch(ComputeReturnToStartUseCase(), AlertDelivery { })
    private val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val wayHome = WayHomeWatch(
        scope = watchScope,
        readTrack = tracks::getById,
        findWayHome = { _, _ -> WayHomeResult.AlongTrails(WayHomeLines.hairpin) },
        isReturning = { id -> returnWatch.state.value.let { it.trackId == id && it.isReturning } },
        errorLog = { _, _, _ -> },
    )
    private lateinit var viewModel: TrackRecordingViewModel

    private var longPresses = 0
    private var routeOnMap: RouteOnMap? = null

    private val map: MapSlot = { _, content, _, _, _, _, _, _, modifier ->
        routeOnMap = content.route
        Box(modifier.testTag(MAP_TAG).pointerInput(Unit) { detectTapGestures(onLongPress = { longPresses++ }) })
    }

    @After fun tearDown() {
        if (::viewModel.isInitialized) viewModel.stopRecording()
        watchScope.cancel()
    }

    private fun setScreen() {
        viewModel = wayHomeRecordingViewModel(tracks, tracker, returnWatch, wayHome, t)
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix),
                recording = AvailabilityScreenRecording(isRecording = state.isRecording),
                navigation = AvailabilityScreenNavigation(
                    returnToStart = state.returnToStart,
                    isReturning = state.isReturning,
                    isArrivalHeld = state.isArrivalHeld,
                    isOffTrack = state.isOffTrack,
                    onToggleReturning = { if (state.isReturning || state.isArrivalHeld) viewModel.stopReturn() else viewModel.startReturn() },
                    navigationTarget = state.originWaypoint,
                    returnRoute = returnRouteOf(state.routeHome, state.wayHomeNote),
                    routeLine = state.routeLine,
                    onRetryRoute = viewModel::retryRoute,
                ),
                compassProvider = FixedCompass(45f),
                computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                currentTime = CurrentTimeProvider { t + 1_000L },
                mapSlot = map,
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
                settings = AvailabilityScreenSettings(onNightModeMapsChanged = {}, onThemeModeChanged = {}),
            )
        }
        composeRule.waitForIdle()
    }

    private fun boundsOf(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun `Return by a real touch draws the way home on the map, and long-presses below the display still reach the map`() {
        setScreen()
        composeRule.runOnIdle { viewModel.startRecording() }
        composeRule.waitForIdle()
        runBlocking {
            tracks.appendPoints("track-1", listOf(TrackPoint(here.lat, here.lng, null, 5f, t), TrackPoint(Ground.at(0.0, -100.0).lat, Ground.at(0.0, -100.0).lng, null, 5f, t + 1), TrackPoint(here.lat, here.lng, null, 5f, t + 2)))
            tracker.emitted.emit(fix)
        }
        composeRule.waitForIdle()

        val pill = boundsOf(RETURN_PILL_TAG)
        val centre = with(composeRule.density) { Offset(((pill.left + pill.right) / 2).toPx(), ((pill.top + pill.bottom) / 2).toPx()) }
        composeRule.onRoot().performTouchInput { click(centre) }
        composeRule.waitForIdle()

        assertTrue("the real touch started the return", viewModel.uiState.value.isReturning)
        assertEquals(
            "the map is handed the way home, dotted and solid",
            listOf(RouteLinePartKind.TRACK, RouteLinePartKind.TRAIL, RouteLinePartKind.TRACK),
            routeOnMap?.line?.aheadParts?.map { it.kind },
        )
        longPressesBelowTheHudReachTheMap()
    }

    private fun longPressesBelowTheHudReachTheMap() {
        val hud = boundsOf(NAVIGATION_HUD_TAG)
        val controls = composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().map {
            with(composeRule.density) { DpRect(it.boundsInRoot.left.toDp(), it.boundsInRoot.top.toDp(), it.boundsInRoot.right.toDp(), it.boundsInRoot.bottom.toDp()) }
        }
        var sampled = 0
        for (row in listOf(6.dp, 20.dp)) {
            val y = hud.bottom + row
            for (i in 0 until SAMPLES) {
                val x = hud.left + 8.dp + (hud.right - hud.left - 16.dp) * (i.toFloat() / (SAMPLES - 1))
                if (controls.any { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }) continue
                val before = longPresses
                val p = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
                composeRule.onRoot().performTouchInput { longClick(p) }
                composeRule.waitForIdle()
                assertEquals("a long-press at ($x, $y), display bottom ${hud.bottom}, must reach the map", before + 1, longPresses)
                sampled++
            }
        }
        assertTrue("at least $MIN_SAMPLED points were sampled, not $sampled", sampled >= MIN_SAMPLED)
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        const val MAP_TAG = "way-home-rig-map"
        const val RETURN_PILL_TAG = "control-pill-return-to-vehicle"
        const val SAMPLES = 8
        const val MIN_SAMPLED = 8
    }
}
