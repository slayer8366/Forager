package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntSize
import com.zynergylabs.forager.app.ui.map.fanout.CameraMoveClassifier
import com.zynergylabs.forager.app.ui.map.fanout.FanReopenCoordinator
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutState
import org.maplibre.android.maps.CameraMapFixture
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * A [SightingsMapState] over fresh States for a [CameraMapFixture] map, as `SightingsMap` remembers one per `MapView`
 * (dispatch 2026-11-07-01 Amendment 1). The same shape as `RouteCameraOnReturnTest`'s own, plus a real [PlainMapNudge]
 * (spring-back off, not navigating), because the locate effect in [MapLocationDot] calls it before following resumes;
 * the parts `MapDataRefresh` and the locate effect read are real, the rest are inert objects with no constructor run.
 */
@Composable
internal fun rememberCameraFixtureMapState(
    map: MapLibreMap,
    cameraMemory: State<MapCameraMemory?>,
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
        appliedPositionLook = arrayOf(PositionLook.PRECISE),
        navigationModeChange = NavigationModeChange(),
        fanCircleColour = arrayOfNulls(1),
        cameraMoveClassifier = CameraMoveClassifier({ task -> task() }),
        touchDownAt = FloatArray(2),
        nudgeFlingGuard = inert(NudgeFlingGuard::class.java),
        nudgeElastic = inert(NudgeElasticDriver::class.java),
        nudgeSpringBackState = mutableStateOf(false),
        plainNudge = PlainMapNudge(NoNudgeMap, springBack = { false }, navigating = { false }, thresholdPx = 1f),
        coverPicture = null,
        fanOut = MarkerFanOutState(),
        tapHandlerRef = TapHandlerRef(),
        fanReopen = inert(FanReopenCoordinator::class.java),
        localAppliedSearchFrameId = intArrayOf(0),
    )
}

/** The nudge's view of the map, for a map with spring-back off: nothing is ever shown or cleared. */
private object NoNudgeMap : PlainNudgeMap {
    override fun cameraMode(): Int? = null
    override fun showPadding(padding: DoubleArray) = Unit
    override fun clearPaddingKeepingPicture() = Unit
    override var flingEnabled: Boolean = true
    override fun paddingShown(): Boolean = false
}
