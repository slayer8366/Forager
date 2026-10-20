package com.zynergylabs.forager.app.ui.map

import android.content.res.Resources
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.RecordPolyline
import com.zynergylabs.forager.app.domain.model.RecordRegion
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.Waypoint
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.location.modes.CameraMode

/**
 * The way home and the data + camera refresh: two effects, moved whole and in their order from [SightingsMap] by UI split
 * Phase 2.2 (dispatch 2026-10-19-29), called from the place they stood there and keyed exactly as they were (the
 * refresh on its sixteen values). Every parameter but [state] is a value [SightingsMap] already had, captured when an
 * effect launches, as before; [cameraRequest] stays a parameter so its smart cast holds. The camera bookkeeping (the
 * last applied target, request id and search frame) is read and written through [state], on the same States and
 * objects as before. The comments inside are the effects' own.
 */
@Composable
internal fun MapDataRefresh(
    state: SightingsMapState,
    route: RouteOnMap?,
    region: Region,
    sightings: List<Sighting>,
    plannedTrips: List<PlannedTrip>,
    focusOverride: LatLng?,
    breadcrumbPoints: List<LatLng>,
    waypoints: List<Waypoint>,
    focusedObservationId: Long?,
    keptTrackPolylines: List<RecordPolyline>,
    findMarkers: List<RecordPoint>,
    photoMarkers: List<RecordPoint>,
    offlineRegionCircles: List<RecordRegion>,
    showSearchCentre: Boolean,
    cameraRequest: MapCameraRequest?,
    journalHighlights: JournalEntryHighlights,
    searchFrameRequestId: Int,
    resources: Resources,
) {
    // Data + camera refresh. Runs on every relevant prop change *and* whenever a new style just
    // finished loading (loadedStyle changing is what makes this re-populate a freshly blank style
    // after a basemap swap) — the same "rebuild content every update, regardless of why the update
    // fired" behaviour the deleted osmdroid version had in its single `update` block, split here
    // because MapLibre's own API separates "style ready" from "camera/property changed".
    // Dispatch -497: the way back, pushed whenever it or the style changes (a new style starts empty).
    LaunchedEffect(state.loadedStyle, route) {
        state.loadedStyle?.let { updateRouteHomeLayers(it, route) }
    }
    LaunchedEffect(
        state.loadedStyle, region, sightings, plannedTrips, focusOverride, breadcrumbPoints, waypoints, focusedObservationId,
        keptTrackPolylines, findMarkers, photoMarkers, offlineRegionCircles, showSearchCentre, cameraRequest, journalHighlights,
        searchFrameRequestId,
    ) {
        val style = state.loadedStyle ?: return@LaunchedEffect
        val map = state.mapLibreMap ?: return@LaunchedEffect
        refreshOverlayData(
            style, region, sightings, plannedTrips, breadcrumbPoints, waypoints, focusedObservationId,
            keptTrackPolylines, findMarkers, photoMarkers, offlineRegionCircles, showSearchCentre, journalHighlights,
        )

        // Once the live-location "puck" is actively tracking (the default once permission is
        // granted — see activateLiveLocationIfPermitted), it owns the camera continuously, on its
        // own internal update loop, independent of recomposition. Jumping the camera to the search
        // region here too — on every sightings/breadcrumb update this effect already keys on,
        // which includes roughly every 15s while a track is recording — would fight it, snapping the
        // view back to the search center out from under a walker watching their live position. Once
        // a pan/zoom/the CameraMode.NONE break in activateLiveLocationIfPermitted's own doc comment
        // drops tracking, this resumes controlling the camera exactly as it did before that existed.
        val isGpsTrackingBefore = map.locationComponent.isLocationComponentActivated &&
            map.locationComponent.cameraMode != CameraMode.NONE
        val target = region to focusOverride
        // RECORD -750, item 5 (the owner: "Yes, fly to the search (Recommended)"): a new search's frame stops the camera
        // following the GPS fix, which would otherwise block the move below, and frames the searched region. Once per id.
        val appliedSearchFrameId = state.cameraMemory?.appliedSearchFrameId ?: state.localAppliedSearchFrameId[0]
        val searchFrame = searchFrameMove(isGpsTrackingBefore, searchFrameRequestId, appliedSearchFrameId)
        if (searchFrame != SearchFrameMove.NONE) {
            state.cameraMoveClassifier.markAppMove()
            if (searchFrame == SearchFrameMove.STOP_FOLLOWING_AND_FRAME) map.locationComponent.cameraMode = CameraMode.NONE
            map.cameraPosition = CameraPosition.Builder()
                .target(MapLibreLatLng(region.lat, region.lng))
                .zoom(zoomForRadiusKm(region.radiusKm))
                .build()
            state.lastAppliedCameraTarget = target
            state.localAppliedSearchFrameId[0] = searchFrameRequestId
            state.cameraMemory?.appliedSearchFrameId = searchFrameRequestId
            Log.i(SEARCH_FRAME_LOG_TAG, "search frame $searchFrameRequestId applied ($searchFrame) at ${region.lat}, ${region.lng}")
            return@LaunchedEffect
        }
        val isGpsTracking = isGpsTrackingBefore
        // A one-shot frame (the entry map's opening frame) stands in for this target's move: the
        // target is recorded as applied too, so the region move below does not follow it and undo it.
        // A later target change (a locate-me pan) still moves the camera, as before.
        val frameWanted = cameraRequest != null && shouldApplyCameraRequest(isGpsTracking, cameraRequest, state.lastAppliedCameraRequestId)
        if (frameWanted) state.cameraMoveClassifier.markAppMove()
        val frameApplied = frameWanted && applyCameraFrame(map, cameraRequest.frame, resources.displayMetrics.density)
        if (frameApplied) {
            state.lastAppliedCameraRequestId = cameraRequest.id
            state.lastAppliedCameraTarget = target
        } else if (shouldMoveCameraToTarget(isGpsTracking, target, state.lastAppliedCameraTarget)) {
            val center = MapLibreLatLng(region.lat, region.lng)
            // focusOverride pans the camera without moving the search-location marker or the
            // zoom-from-radius heuristic below, both of which stay anchored to region — see
            // MapSlot's doc comment on this parameter for why the two are kept independent.
            val cameraTarget = focusOverride?.let { MapLibreLatLng(it.lat, it.lng) } ?: center
            state.cameraMoveClassifier.markAppMove()
            map.cameraPosition = CameraPosition.Builder()
                .target(cameraTarget)
                .zoom(zoomForRadiusKm(region.radiusKm))
                .build()
            state.lastAppliedCameraTarget = target
        } else {
            // Dispatch 2026-10-19-36: while following, the target is taken as applied (cameraTargetTakenAsApplied), so a
            // gesture that ends following leaves no stale target for the next refresh, or a return, to move the camera to.
            state.lastAppliedCameraTarget = cameraTargetTakenAsApplied(isGpsTracking, target, state.lastAppliedCameraTarget)
        }
    }
}
