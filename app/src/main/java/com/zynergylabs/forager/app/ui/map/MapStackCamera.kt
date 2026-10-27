package com.zynergylabs.forager.app.ui.map

import android.util.Log
import com.zynergylabs.forager.app.domain.EntryMapFrame
import com.zynergylabs.forager.app.ui.map.fanout.CameraSpot
import com.zynergylabs.forager.app.ui.map.fanout.StackCamera
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap

/**
 * The crowded-stack zoom's camera over MapLibre (dispatch 2026-10-27-07). Every move is marked first, as every other camera
 * move this map makes is ([CameraMoveClassifier.markAppMove]): the zoom's own with `keepsFan`, so the fan it opens as it
 * lands is not folded by it; the return with the plain mark.
 *
 * **The move is instant** ([applyCameraFrame], the entry map's frame), and the fan is worked out straight after it from the
 * projection. That rests on MapLibre's `moveCamera` applying the camera synchronously (`jumpTo`) and only queueing its
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
        state.cameraMoveClassifier.markAppMove(keepsFan = true)
        if (wasFollowing) component.cameraMode = CameraMode.NONE
        val moved = applyCameraFrame(map, frame, density)
        if (!moved && followMode != null) component.cameraMode = followMode
        if (!moved) Log.w(SIGHTINGS_MAP_TAG, "Crowded stack: the camera could not frame $frame.")
        return moved
    }

    override fun restore(spot: CameraSpot) {
        state.cameraMoveClassifier.markAppMove()
        map.cameraPosition = CameraPosition.Builder()
            .target(MapLibreLatLng(spot.lat, spot.lng))
            .zoom(spot.zoom)
            .bearing(spot.bearing)
            .tilt(spot.tilt)
            .build()
        val component = map.locationComponent
        val mode = spot.followMode
        if (mode != null && component.isLocationComponentActivated) {
            state.cameraMoveClassifier.markAppMove()
            component.cameraMode = mode
        }
        Log.i(SIGHTINGS_MAP_TAG, "Crowded stack: Back returned the camera to zoom ${spot.zoom} (following=${mode != null}).")
    }
}
