package com.zynergylabs.forager.app.ui.map

import android.util.Log
import com.zynergylabs.forager.app.domain.EntryMapFrame
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToInt

/**
 * The data+camera refresh effect's own decision of whether to move the camera to [target] —
 * extracted as a plain function, the same reason [locationIndicatorTrackingAnimationMultiplier]
 * below is one, so the actual defect (not just the surrounding native-object plumbing) is
 * unit-testable. Real hardware report this fixes: that effect is keyed on `loadedStyle` (needed so
 * [refreshOverlayData] above re-runs after a basemap swap blanks the style), but with no guard, it
 * also re-ran the camera move below whenever GPS tracking wasn't active — including on a basemap
 * or night-mode swap (a night toggle reloads the style on every basemap) that changed neither `region` nor `focusOverride` — which read as "changing
 * map style brought the map back to my location" even though the GPS/locate-me icon is the only
 * control meant to do that. Comparing [target] against [lastAppliedCameraTarget] — what was
 * actually last applied, not merely that the effect ran again — is what tells "the search moved"
 * apart from "the style reloaded." [isGpsTracking] itself is left as a native-backed computation
 * at the call site (see that effect's own doc comment on why moving the camera while it's true
 * would fight the puck), not folded into this function, so this stays a pure comparison.
 */
/**
 * Whether a MapLibre camera-move-started [reason] is the user's touch — see
 * [MapRenderMode.onUserCameraGesture]. A plain function so the one constant it keys on is pinned
 * by a headless test rather than only by a device.
 */
internal fun isUserCameraGesture(reason: Int): Boolean =
    reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE

internal fun shouldMoveCameraToTarget(
    isGpsTracking: Boolean,
    target: Pair<Region, LatLng?>,
    lastAppliedCameraTarget: Pair<Region, LatLng?>?,
): Boolean = !isGpsTracking && target != lastAppliedCameraTarget

/**
 * Dispatch 2026-10-19-36 (RECORD -1468; the owner: "come back to Maps > the map is exactly where you left it, same place and
 * same zoom; only a new search or a tap on locate moves it"): the target the data+camera refresh records as applied when it
 * did not move the camera. While GPS tracking owns the camera, [target] is taken as applied: the puck is framing the user, so
 * the region the refresh would have framed is moot. Not recording it left the applied target stale (a locate fix that
 * changed while following), and once a gesture ended following, the next refresh, or the refresh after a return to the Maps
 * tab (which restores this value from [MapCameraSnapshot.appliedTarget]), saw "not following, and the target differs" and
 * moved the camera to the region at [zoomForRadiusKm]. Not tracking, nothing new is recorded: a target that changed then is
 * one the refresh has not framed yet. Rejected: taking the returning map's target as applied on restore, which would also
 * swallow a target that genuinely changed while the map was away (a search cleared on another tab) and leaves the same jump
 * reachable without a tab change.
 */
internal fun cameraTargetTakenAsApplied(
    isGpsTracking: Boolean,
    target: Pair<Region, LatLng?>,
    lastAppliedCameraTarget: Pair<Region, LatLng?>?,
): Pair<Region, LatLng?>? = lastAppliedCameraTarget

/**
 * Whether [request] moves the camera now: a request is applied once per id, and never while GPS
 * tracking owns the camera (the rule [shouldMoveCameraToTarget] follows).
 */
/** RECORD -750, item 5: what a search's frame request does to the camera. */
internal enum class SearchFrameMove {
    /** No new request: the region move below follows its own rule. */
    NONE,

    /** A new request while the camera is not following: frame the region. */
    FRAME,

    /** A new request while the camera follows the GPS fix: stop following, then frame the region. */
    STOP_FOLLOWING_AND_FRAME,
}

/**
 * RECORD -750, item 5: a search's frame request ([MapOverlayContent.searchFrameRequestId]) is applied once per id, and, unlike
 * [shouldMoveCameraToTarget] and [shouldApplyCameraRequest], also while GPS tracking owns the camera, which it ends: the user
 * asked to see the searched place. 0 is no request.
 */
internal fun searchFrameMove(isGpsTracking: Boolean, requestId: Int, lastAppliedId: Int): SearchFrameMove = when {
    requestId == 0 || requestId == lastAppliedId -> SearchFrameMove.NONE
    isGpsTracking -> SearchFrameMove.STOP_FOLLOWING_AND_FRAME
    else -> SearchFrameMove.FRAME
}

/** RECORD -750, item 5: the log tag for a search frame applied, so a device check can read it. */
internal const val SEARCH_FRAME_LOG_TAG = "ForagerSearchFrame"

internal fun shouldApplyCameraRequest(
    isGpsTracking: Boolean,
    request: MapCameraRequest?,
    lastAppliedRequestId: String?,
): Boolean = !isGpsTracking && request != null && request.id != lastAppliedRequestId

/**
 * What a map restores from a [MapCameraSnapshot] (Part 1 layout fixes, item 4): the camera, the region
 * target taken as already applied, and the location component's camera mode.
 */
internal data class MapCameraRestore(
    val target: LatLng,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double,
    val appliedTarget: Pair<Region, LatLng?>?,
    /** A [CameraMode] constant: [CameraMode.TRACKING] if the snapshot was following, [CameraMode.NONE] if not. */
    val cameraMode: Int,
)

/**
 * The restore a map makes as it first loads a style, or `null` for none (Part 1 layout fixes, item 4):
 * [saved], the camera the user left, when there is one and the location component has no mode of its
 * own yet ([previousCameraMode] `null`, a new `MapView`). A style swap on a live map keeps its own
 * camera and mode, as before. Following restores [CameraMode.TRACKING] and panned away
 * [CameraMode.NONE]; either is non-null, so the first activation's zoom-in does not run.
 */
internal fun cameraRestoreFor(saved: MapCameraSnapshot?, previousCameraMode: Int?): MapCameraRestore? {
    if (saved == null || previousCameraMode != null) return null
    return MapCameraRestore(
        target = saved.target,
        zoom = saved.zoom,
        bearing = saved.bearing,
        tilt = saved.tilt,
        appliedTarget = saved.appliedTarget,
        cameraMode = if (saved.following) CameraMode.TRACKING else CameraMode.NONE,
    )
}

/** Moves [map]'s camera to [restore]'s, at once, as the region move does. */
internal fun applyCameraRestore(map: MapLibreMap, restore: MapCameraRestore) {
    map.cameraPosition = CameraPosition.Builder()
        .target(MapLibreLatLng(restore.target.lat, restore.target.lng))
        .zoom(restore.zoom)
        .bearing(restore.bearing)
        .tilt(restore.tilt)
        .build()
}

/** The zoom a fitted frame opens at: [fittedZoom], capped at [maxZoom]. */
internal fun cappedFrameZoom(fittedZoom: Double, maxZoom: Double): Double = minOf(fittedZoom, maxZoom)

/**
 * Moves [map]'s camera to [frame], at once rather than eased, as the region move does. A
 * [EntryMapFrame.Fit] goes through MapLibre's own `getCameraForLatLngBounds`, with the frame's
 * padding in px on every side, and its zoom capped by [cappedFrameZoom]. Whether that fit is right
 * at the preview's size and at the phone's density is device-only: the native call cannot run
 * headless. `false`, logged, when MapLibre gives no camera for the bounds (it is `@Nullable`) or
 * rejects them, so the caller falls back to the region move instead of leaving the camera unset.
 */
internal fun applyCameraFrame(map: MapLibreMap, frame: EntryMapFrame, density: Float): Boolean {
    val position = when (frame) {
        is EntryMapFrame.SinglePoint -> CameraPosition.Builder()
            .target(MapLibreLatLng(frame.at.lat, frame.at.lng))
            .zoom(frame.zoom)
            .build()
        is EntryMapFrame.Fit -> {
            val bounds = try {
                LatLngBounds.from(frame.bounds.north, frame.bounds.east, frame.bounds.south, frame.bounds.west)
            } catch (e: IllegalArgumentException) {
                Log.w(SIGHTINGS_MAP_TAG, "Camera frame bounds ${frame.bounds} rejected; falling back to the region move.", e)
                return false
            }
            val paddingPx = (frame.paddingDp * density).roundToInt()
            val fitted = map.getCameraForLatLngBounds(bounds, intArrayOf(paddingPx, paddingPx, paddingPx, paddingPx))
            if (fitted == null) {
                Log.w(SIGHTINGS_MAP_TAG, "No camera for bounds ${frame.bounds}; falling back to the region move.")
                return false
            }
            CameraPosition.Builder(fitted).zoom(cappedFrameZoom(fitted.zoom, frame.maxZoom)).build()
        }
    }
    map.cameraPosition = position
    return true
}

/**
 * A visual-only heuristic mapping search radius to a legible starting zoom level, not a domain
 * prediction. Unchanged from the deleted osmdroid version — see that history for the full pixel-math
 * reasoning; it does not depend on which renderer draws the result. `internal` so
 * `SightingsMapOverlayDataTest` can assert it directly instead of duplicating the thresholds.
 */
internal fun zoomForRadiusKm(radiusKm: Int): Double = when {
    radiusKm <= 5 -> 13.0
    radiusKm <= 15 -> 12.0
    radiusKm <= 30 -> 10.5
    else -> 9.0
}
