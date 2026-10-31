package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.CoverPhase
import com.zynergylabs.forager.app.ui.map.MapCameraMemory
import com.zynergylabs.forager.app.ui.map.MapCameraSnapshot
import com.zynergylabs.forager.app.ui.map.MapDataRefresh
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.NavigationModeChange
import com.zynergylabs.forager.app.ui.map.PositionLook
import com.zynergylabs.forager.app.ui.map.SightingsMapState
import com.zynergylabs.forager.app.ui.map.TapHandlerRef
import com.zynergylabs.forager.app.ui.map.applyCameraRestore
import com.zynergylabs.forager.app.ui.map.cameraRestoreFor
import com.zynergylabs.forager.app.ui.map.fanout.CameraMoveClassifier
import com.zynergylabs.forager.app.ui.map.fanout.FanReopenCoordinator
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutState
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
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
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.StyleLifecycleFixture
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-30-08, Amendment 5, S-1 (Sorrel's review of #449; the owner: "Fix: keep my view (Recommended)"): a saved
 * route fits once, when it is opened. After that, a return to the Maps tab keeps the camera the user left and leaves following
 * as the user set it.
 *
 * Through the real screen, the real [RoutePlottingViewModel] and a real tab change (touches on the bottom nav's Journal, then
 * Maps), which disposes the map with its tab as `TabCrossfade` does in the app. The map slot runs the app's own
 * [MapDataRefresh] on a real MapLibre map ([CameraMapFixture]), over a fresh [SightingsMapState] for each time the map is
 * composed, as `SightingsMap` remembers one per `MapView`, and with the screen's own [MapCameraMemory] from the render mode.
 * The route opens by a real touch on its pin.
 *
 * Copied here rather than run, because a real `MapView` cannot be built under Robolectric (each mirrors the code it names):
 * - the restore a returning map makes on its first style (`MapStyleReload.kt`: [cameraRestoreFor], [applyCameraRestore], the
 *   region target taken as applied, then the loaded style);
 * - the snapshot the camera-idle listener saves (`MapListeners.kt`), in [settle];
 * - the user's pan, a camera set on the map, and the locate tap that turns following back on, a mode set on the SDK's
 *   controller.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCameraOnReturnTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val pin = StubGlyph(MapLayerIds.PLANNED_TRIPS, ROUTE_TRIP.id, 200.dp, 300.dp, ROUTE_TRIP.location)
    private val glyphs = RouteMapSlot(listOf(pin))
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("not saved in this test")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    /** One per time the map was composed, in order: the map, what its native side recorded, its state and the memory it had. */
    private class MountedMap(
        val map: MapLibreMap,
        val recording: CameraMapFixture.Recording,
        val state: SightingsMapState,
        val memory: MapCameraMemory?,
    )

    private val mounts = mutableListOf<MountedMap>()

    /** The mode a map composed for the first time starts in; a returning map takes the saved one, as the app's restore does. */
    private var firstMode = CameraMode.NONE

    private val start = camera(45.20, -122.50, 10.0)
    private val fit = camera(45.331, -122.639, 13.5)
    private val panned = camera(45.40, -122.80, 15.25)

    private fun camera(lat: Double, lng: Double, zoom: Double): CameraPosition =
        CameraPosition.Builder().target(MapLibreLatLng(lat, lng)).zoom(zoom).bearing(0.0).tilt(0.0).build()

    private val slot: MapSlot = { region, content, renderMode, focusOverride, onLongPress, onTap, onSightingTap, onCameraIdle, modifier ->
        glyphs.slot(region, content, renderMode, focusOverride, onLongPress, onTap, onSightingTap, onCameraIdle, modifier)
        val made = remember { CameraMapFixture.map(start = start, fit = fit, cameraMode = if (mounts.isEmpty()) firstMode else CameraMode.NONE) }
        val memory by rememberUpdatedState(renderMode.cameraMemory)
        val state = rememberTestMapState(made.first, rememberUpdatedState(renderMode.cameraMemory))
        remember { mounts.add(MountedMap(made.first, made.second, state, renderMode.cameraMemory)) }
        val style = remember { StyleLifecycleFixture.loadedStyle() }
        LaunchedEffect(Unit) {
            cameraRestoreFor(memory?.saved, previousCameraMode = null)?.let {
                applyCameraRestore(made.first, it)
                made.second.setModeDirectly(it.cameraMode)
                state.lastAppliedCameraTarget = it.appliedTarget
            }
            state.loadedStyle = style
        }
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
    }

    private fun setScreen() {
        val viewModel = mapLayersViewModel(plannedTrips = listOf(ROUTE_TRIP))
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE),
                routePlotting = RoutePlottingControls(state, plotting),
            )
        }
        composeRule.waitForIdle()
    }

    private fun touchCentre(node: SemanticsNodeInteraction) {
        val b = node.getUnclippedBoundsInRoot()
        val p = with(composeRule.density) { Offset(((b.left + b.right) / 2).toPx(), ((b.top + b.bottom) / 2).toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(p) }
        composeRule.waitForIdle()
    }

    private fun navItem(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag(COMPACT_BOTTOM_NAV_TAG)))

    /** The route's pin, by a real touch: "Opens the route straight away" (Amendment 1, Q7). */
    private fun openByPin() {
        touchCentre(composeRule.onNode(hasTestTag(glyphTag(ROUTE_TRIP.id))))
        assertTrue("the route opened from its pin, mode was ${plotting.uiState.value.mode}", plotting.uiState.value.mode is RoutePlotMode.Showing)
    }

    /** Journal, then Maps, each by a real touch on the bottom nav: the map leaves with its tab and a new one comes back. */
    private fun journalAndBack() {
        val before = mounts.size
        touchCentre(navItem("Journal"))
        navItem("Journal").assertIsSelected()
        touchCentre(navItem("Maps"))
        navItem("Maps").assertIsSelected()
        composeRule.waitForIdle()
        assertEquals("a new map was composed for the return to Maps (the old one left with its tab)", before + 1, mounts.size)
    }

    /** The camera the idle listener saves when a camera settles (`MapListeners.kt`), for the map that comes back. */
    private fun settle(mounted: MountedMap) {
        val camera = mounted.map.cameraPosition
        val target = camera.target!!
        mounted.memory!!.saved = MapCameraSnapshot(
            target = LatLng(target.latitude, target.longitude),
            zoom = camera.zoom,
            bearing = camera.bearing,
            tilt = camera.tilt,
            following = mounted.map.locationComponent.isLocationComponentActivated &&
                mounted.map.locationComponent.cameraMode != CameraMode.NONE,
            appliedTarget = mounted.state.lastAppliedCameraTarget,
        )
    }

    private fun describe(c: CameraPosition?) = "(${c?.target?.latitude}, ${c?.target?.longitude}) at zoom ${c?.zoom}"

    private fun assertCamera(message: String, expected: CameraPosition, actual: CameraPosition?) {
        val same = actual?.target?.latitude == expected.target!!.latitude &&
            actual.target?.longitude == expected.target!!.longitude && actual.zoom == expected.zoom
        assertTrue("$message: expected ${describe(expected)}, was ${describe(actual)}", same)
    }

    @Test
    fun `a saved route fits once - open, pan, Journal, Maps, and the camera is where the user left it`() {
        setScreen()
        val first = mounts.single()
        assertTrue("the screen's camera memory reached the map", first.memory != null)

        openByPin()
        assertCamera("the open fitted the route", fit, first.recording.camera)

        composeRule.runOnUiThread {
            first.map.cameraPosition = panned
            settle(first)
        }
        composeRule.waitForIdle()
        journalAndBack()

        val back = mounts.last()
        assertCamera(
            "back on Maps with the route still showing: the fit was applied again over the camera the user moved",
            panned,
            back.recording.camera,
        )
        assertTrue("the route is still showing", plotting.uiState.value.mode is RoutePlotMode.Showing)
    }

    @Test
    fun `a saved route opened again after a return still fits (guard, not shown to bite)`() {
        setScreen()
        openByPin()
        composeRule.runOnUiThread {
            mounts.last().map.cameraPosition = panned
            settle(mounts.last())
            plotting.onCloseSavedRoute()
        }
        composeRule.waitForIdle()
        journalAndBack()
        val back = mounts.last()
        assertCamera("closed before leaving: the return kept the camera", panned, back.recording.camera)

        openByPin()
        assertCamera("a second open of the same route fits it", fit, back.recording.camera)
    }

    @Test
    fun `following turned back on after the open is left on by a return to Maps`() {
        firstMode = CameraMode.TRACKING
        setScreen()
        val first = mounts.single()

        openByPin()
        assertEquals("the open ended following (the planner accepted it)", CameraMode.NONE, first.recording.cameraMode)

        composeRule.runOnUiThread {
            first.map.cameraPosition = panned
            first.recording.setModeDirectly(CameraMode.TRACKING)
            settle(first)
        }
        composeRule.waitForIdle()
        journalAndBack()

        val back = mounts.last()
        assertEquals(
            "back on Maps: following, which the user turned back on, was ended again (camera mode)",
            CameraMode.TRACKING,
            back.recording.cameraMode,
        )
        assertEquals("back on Maps: the SDK was told of no camera-mode change", 0, back.recording.trackingChanges)
        assertCamera("back on Maps: the camera stayed where it was left", panned, back.recording.camera)
    }
}

/**
 * A [SightingsMapState] over fresh States, as `SightingsMap` remembers one per `MapView`. The parts `MapDataRefresh` reads are
 * real (the map, the style, the camera bookkeeping, the move classifier, the memory); the rest are inert objects with no
 * constructor run ([CameraMapFixture.inert]), since nothing here calls them.
 */
@Composable
private fun rememberTestMapState(
    map: MapLibreMap,
    cameraMemory: androidx.compose.runtime.State<MapCameraMemory?>,
): SightingsMapState = remember {
    fun <T> inert(type: Class<T>): T = CameraMapFixture.inert(type)
    SightingsMapState(
        mapView = inert(MapView::class.java),
        onTapState = mutableStateOf({}),
        onSightingTapState = mutableStateOf({ _, _, _ -> }),
        onLongPressState = mutableStateOf({ _ -> }),
        onCameraIdleState = mutableStateOf({ _ -> }),
        onUserCameraGestureState = mutableStateOf({}),
        navigationViewState = mutableStateOf(null),
        positionLookState = mutableStateOf(PositionLook.PRECISE),
        onFeatureTapState = mutableStateOf({ _ -> }),
        onCloseBubbleState = mutableStateOf({}),
        layersStateState = mutableStateOf(inert(com.zynergylabs.forager.app.ui.map.layers.MapLayersState::class.java)),
        chromeColourState = mutableStateOf(0),
        forecastState = mutableStateOf(null),
        sightingsState = mutableStateOf(emptyList()),
        focusedObservationIdState = mutableStateOf(null),
        focusedFeatureState = mutableStateOf(null),
        cameraMemoryState = cameraMemory,
        returnMemoryState = mutableStateOf(null),
        plannedTripsState = mutableStateOf(emptyList()),
        waypointsState = mutableStateOf(emptyList()),
        findMarkersState = mutableStateOf(emptyList()),
        photoMarkersState = mutableStateOf(emptyList()),
        mapLibreMapState = mutableStateOf(map),
        loadedStyleState = mutableStateOf(null),
        appliedStyleState = mutableStateOf(null),
        coverPhaseState = mutableStateOf(CoverPhase.GONE),
        lastAppliedCameraTargetState = mutableStateOf(null),
        lastAppliedCameraRequestIdState = mutableStateOf(null),
        attributionDefaultMarginsState = mutableStateOf(null),
        cameraIdleCountState = mutableIntStateOf(0),
        navigationMapHeightPxState = mutableIntStateOf(0),
        mapSizePxState = mutableStateOf(IntSize.Zero),
        creditWidthPxState = mutableIntStateOf(0),
        appliedPositionLook = arrayOf(PositionLook.PRECISE),
        navigationModeChange = NavigationModeChange(),
        fanCircleColour = arrayOfNulls(1),
        cameraMoveClassifier = CameraMoveClassifier({ task -> task() }),
        touchDownAt = FloatArray(2),
        nudgeFlingGuard = inert(com.zynergylabs.forager.app.ui.map.NudgeFlingGuard::class.java),
        nudgeElastic = inert(com.zynergylabs.forager.app.ui.map.NudgeElasticDriver::class.java),
        nudgeSpringBackState = mutableStateOf(false),
        plainNudge = inert(com.zynergylabs.forager.app.ui.map.PlainMapNudge::class.java),
        coverPicture = null,
        fanOut = MarkerFanOutState(),
        tapHandlerRef = TapHandlerRef(),
        fanReopen = inert(FanReopenCoordinator::class.java),
        localAppliedSearchFrameId = intArrayOf(0),
    )
}
