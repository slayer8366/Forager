package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.map.MapCameraMemory
import com.zynergylabs.forager.app.ui.map.MapCameraSnapshot
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.SearchFrameMove
import com.zynergylabs.forager.app.ui.map.cameraRequestStopsFollowing
import com.zynergylabs.forager.app.ui.map.cameraRestoreFor
import com.zynergylabs.forager.app.ui.map.cameraTargetTakenAsApplied
import com.zynergylabs.forager.app.ui.map.searchFrameMove
import com.zynergylabs.forager.app.ui.map.searchFrameSnapshot
import com.zynergylabs.forager.app.ui.map.shouldApplyCameraRequest
import com.zynergylabs.forager.app.ui.map.shouldMoveCameraToTarget
import com.zynergylabs.forager.app.ui.map.zoomForRadiusKm
import org.maplibre.android.location.modes.CameraMode

/** A stand-in map's camera: where it looks, how close, turned and tilted how far. */
internal data class StandInCamera(val target: LatLng, val zoom: Double, val bearing: Double, val tilt: Double)

/**
 * The map for the recreation tests (RECORD -2192 and -2190), standing in for SightingsMap, since a real MapView cannot run under
 * Robolectric. It reads and writes the screen's [MapCameraMemory] with the production rules at the places SightingsMap does:
 * - a new map restores the memory's saved camera through `cameraRestoreFor`, as its first style load does (MapStyleReload.kt:114),
 *   else opens as a fresh map does (the region move, then following, as a fresh map in the navigation view does);
 * - the search frame (`searchFrameMove` against `appliedSearchFrameId`, MapDataRefresh.kt:88-105), then the camera request
 *   (`cameraRequestStopsFollowing`, `shouldApplyCameraRequest` against `appliedCameraRequestId`, :109-125), then the region move
 *   (`shouldMoveCameraToTarget`, else `cameraTargetTakenAsApplied`);
 * - after each move it writes the memory's saved camera, as the camera idle listener does (MapListeners.kt:218).
 * What MapLibre does with a frame, and the navigation view's listener, are the S22's to show.
 */
internal class RecreationMapStandIn {
    /** Every camera request id applied, in order, across every map this slot drew. */
    val applied = mutableListOf<String>()

    /** Every search frame id applied, in order, across every map. */
    val searchFramesApplied = mutableListOf<Int>()
    var followingStops = 0
    var mapsComposed = 0
    var renderMode: MapRenderMode? = null

    /** Whether the current map restored a saved camera when it was made. */
    var restored = false
        private set
    private var current: StandInMap? = null

    val following: Boolean get() = current!!.following
    val camera: StandInCamera? get() = current!!.camera

    /** The user's Re-center: the current map follows again. */
    fun recenter() {
        current!!.apply { following = true }.idle()
    }

    /** The user's pan, zoom, turn or tilt: the camera goes to [to] and following ends. */
    fun pan(to: StandInCamera) {
        current!!.apply {
            camera = to
            following = false
        }.idle()
    }

    private class StandInMap(val memory: MapCameraMemory?) {
        var following = true
        var camera: StandInCamera? = null
        var lastAppliedTarget: Pair<Region, LatLng?>? = null
        var ownAppliedRequest: String? = null
        var ownAppliedSearchFrame = 0

        fun idle() {
            val now = camera ?: return
            memory?.saved = MapCameraSnapshot(now.target, now.zoom, now.bearing, now.tilt, following, lastAppliedTarget)
        }
    }

    val slot: MapSlot = { region, content, renderMode, focusOverride, _, _, _, _, modifier ->
        this.renderMode = renderMode
        val target = region to focusOverride
        val map = remember {
            val memory = renderMode.cameraMemory
            val restore = cameraRestoreFor(memory?.saved, previousCameraMode = null)
            restored = restore != null
            StandInMap(memory).apply {
                if (restore != null) {
                    camera = StandInCamera(restore.target, restore.zoom, restore.bearing, restore.tilt)
                    following = restore.cameraMode != CameraMode.NONE
                    lastAppliedTarget = restore.appliedTarget
                } else {
                    camera = regionCamera(target)
                    lastAppliedTarget = target
                }
            }
        }
        LaunchedEffect(Unit) {
            mapsComposed++
            current = map
        }
        val request = renderMode.cameraRequest
        val searchFrameId = content.searchFrameRequestId
        LaunchedEffect(request, searchFrameId, target) {
            val memory = map.memory
            val frame = searchFrameMove(map.following, searchFrameId, memory?.appliedSearchFrameId ?: map.ownAppliedSearchFrame)
            if (frame != SearchFrameMove.NONE) {
                if (frame == SearchFrameMove.STOP_FOLLOWING_AND_FRAME) {
                    map.following = false
                    followingStops++
                }
                map.camera = regionCamera(region to null)
                map.lastAppliedTarget = target
                searchFramesApplied += searchFrameId
                map.ownAppliedSearchFrame = searchFrameId
                memory?.appliedSearchFrameId = searchFrameId
                memory?.saved = searchFrameSnapshot(region, 0.0, 0.0, target)
                return@LaunchedEffect
            }
            val appliedId = memory?.appliedCameraRequestId ?: map.ownAppliedRequest
            if (cameraRequestStopsFollowing(map.following, request, appliedId)) {
                map.following = false
                followingStops++
            }
            if (request != null && shouldApplyCameraRequest(map.following, request, appliedId)) {
                applied += request.id
                map.ownAppliedRequest = request.id
                memory?.appliedCameraRequestId = request.id
                map.camera = fitCamera(request.id)
                map.lastAppliedTarget = target
            } else if (shouldMoveCameraToTarget(map.following, target, map.lastAppliedTarget)) {
                map.camera = regionCamera(target)
                map.lastAppliedTarget = target
            } else {
                map.lastAppliedTarget = cameraTargetTakenAsApplied(map.following, target, map.lastAppliedTarget)
            }
            map.idle()
        }
        Box(modifier.testTag("map-slot"))
    }

    companion object {
        /** Where the region move puts the camera: the focus, else the region's centre, at the region's zoom. */
        fun regionCamera(target: Pair<Region, LatLng?>): StandInCamera {
            val (region, focus) = target
            return StandInCamera(focus ?: LatLng(region.lat, region.lng), zoomForRadiusKm(region.radiusKm), 0.0, 0.0)
        }

        /** A fit's camera, told apart by the request it came from (MapLibre's fit cannot run here). */
        fun fitCamera(requestId: String): StandInCamera = StandInCamera(LatLng(45.0, -121.0), 10.0 + Math.floorMod(requestId.hashCode(), 50) / 10.0, 0.0, 0.0)
    }
}
