package com.zynergylabs.forager.app.ui.map

import android.util.Log
import com.zynergylabs.forager.app.domain.EntryMapFrame
import com.zynergylabs.forager.app.ui.map.fanout.CameraSpot
import com.zynergylabs.forager.app.ui.map.fanout.StackCamera
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap

/**
 * The crowded-stack zoom's camera over MapLibre (dispatch 2026-10-27-07). Every move is marked first, as every other camera
 * move this map makes is: the zoom's own with [CameraMoveClassifier.markStackZoomMove], so the fan it opens as it lands is not
 * folded by it; the return with the plain [CameraMoveClassifier.markAppMove], which folds a fan but, unlike a move the user
 * asked for ([CameraMoveClassifier.markUserAskedMove]), does not end the rest of a chain of returns (Amendment 1, S1).
 *
 * **Each step is instant** ([applyCameraFrame], the entry map's frame), and the fan is worked out straight after it from the
 * projection; since dispatch 2026-11-04-02 (A6) the move is then shown ([showMoveFrom]) and Back's return eased. That rests on MapLibre's `moveCamera` applying the camera synchronously (`jumpTo`) and only queueing its
 * listeners' calls (see [CameraMoveClassifier]'s doc comment for the queueing, read with `javap`). The synchronous projection
 * is not checked here: it is device-only, and the S22 steps on the PR check it.
 *
 * While the map follows the location the follower would pull the camera back, so the zoom stops following first, as a
 * search's frame does (`MapDataRefresh.kt`), and Back's return follows again in the mode it had. While navigating the
 * camera is the navigation view's: [spot] is `null`, and a crowded stack fans past the edge as before, logged.
 */
internal class MapStackCamera(
    private val map: MapLibreMap,
    private val state: SightingsMapState,
    private val density: Float,
) : StackCamera {
    override fun spot(): CameraSpot? {
        if (state.navigationView != null) return null
        val camera = map.cameraPosition
        val target = camera.target ?: return null
        val component = map.locationComponent
        val followMode = component.takeIf { it.isLocationComponentActivated }?.cameraMode?.takeIf { it != CameraMode.NONE }
        return CameraSpot(target.latitude, target.longitude, camera.zoom, camera.bearing, camera.tilt, followMode)
    }

    override fun frame(frame: EntryMapFrame): Boolean {
        val component = map.locationComponent
        val wasFollowing = component.isLocationComponentActivated && component.cameraMode != CameraMode.NONE
        val followMode = if (wasFollowing) component.cameraMode else null
        state.cameraMoveClassifier.markStackZoomMove()
        if (wasFollowing) component.cameraMode = CameraMode.NONE
        val moved = applyCameraFrame(map, frame, density)
        if (!moved && followMode != null) component.cameraMode = followMode
        if (!moved) Log.w(SIGHTINGS_MAP_TAG, "Crowded stack: the camera could not frame $frame.")
        return moved
    }

    override fun restore(spot: CameraSpot, animate: Boolean) {
        state.cameraMoveClassifier.markAppMove()
        val position = positionOf(spot)
        if (!animate) {
            map.cameraPosition = position
            followAgain(spot, "Back returned the camera")
            return
        }
        // Dispatch 2026-11-04-02, A6: eased back over the app's camera timing; following again once it has landed, since the
        // follower would pull the camera away mid-ease. A touch during it leaves the map where the user took it, not following.
        map.easeCamera(CameraUpdateFactory.newCameraPosition(position), NAVIGATION_VIEW_TRANSITION_MILLIS.toInt(), object : MapLibreMap.CancelableCallback {
            override fun onFinish() = followAgain(spot, "Back eased the camera back")

            override fun onCancel() {
                Log.i(SIGHTINGS_MAP_TAG, "Crowded stack: Back's eased return to zoom ${spot.zoom} was taken over; following stays off.")
            }
        })
    }

    /**
     * Dispatch 2026-11-04-02, A6 (S5, RECORD -2040): the zoom's steps have put the camera at its end, at once, in the turn of the main
     * thread that is still running, so nothing between has been drawn. The camera goes back to [from] at once and eases to that end
     * over [NAVIGATION_VIEW_TRANSITION_MILLIS] (750 ms, the app's one named camera timing), both marked as the zoom's so neither
     * folds the fan it is about to open. That no frame is drawn between the steps and the return to [from] rests on MapLibre's
     * `moveCamera` being synchronous on this thread and its render happening later; it is a phone check (the stray frame).
     */
    override fun showMoveFrom(from: CameraSpot, onArrived: () -> Unit, onTakenOver: () -> Unit) {
        val end = map.cameraPosition
        state.cameraMoveClassifier.markStackZoomMove()
        map.cameraPosition = positionOf(from)
        state.cameraMoveClassifier.markStackZoomMove()
        map.easeCamera(CameraUpdateFactory.newCameraPosition(end), NAVIGATION_VIEW_TRANSITION_MILLIS.toInt(), object : MapLibreMap.CancelableCallback {
            override fun onFinish() {
                Log.i(SIGHTINGS_MAP_TAG, "Crowded stack: the zoom's move landed at zoom ${map.cameraPosition.zoom} (asked ${end.zoom}).")
                onArrived()
            }

            override fun onCancel() = onTakenOver()
        })
    }

    private fun positionOf(spot: CameraSpot): CameraPosition = CameraPosition.Builder()
        .target(MapLibreLatLng(spot.lat, spot.lng))
        .zoom(spot.zoom)
        .bearing(spot.bearing)
        .tilt(spot.tilt)
        .build()

    /** Following again in the mode [spot] had, if it had one. */
    private fun followAgain(spot: CameraSpot, what: String) {
        val component = map.locationComponent
        val mode = spot.followMode
        if (mode != null && component.isLocationComponentActivated) {
            state.cameraMoveClassifier.markAppMove()
            component.cameraMode = mode
        }
        Log.i(SIGHTINGS_MAP_TAG, "Crowded stack: $what to zoom ${spot.zoom} (following=${mode != null}).")
    }
}
