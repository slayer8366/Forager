package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MapLibreLatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Dispatch 2026-11-07-01, Amendment 1 (the owner, for the journal entry's map: "Glide, keep zoom, no follow"; "Glides to your
 * position, keeps the zoom, doesn't follow"): whether the data+camera refresh's target changed only by a locate-me pan, the
 * same region with a new [focusOverride][MapDataRefresh] point. Such a move glides to the point at the zoom the user has
 * ([glideToLocate]); a new region (a search, an entry map opening on its place) is still framed at once at the region's zoom.
 *
 * Nothing applied yet ([lastAppliedCameraTarget] null) is the map's first framing, not a pan, so it is not one.
 */
internal fun isLocatePanOnly(target: Pair<Region, LatLng?>, lastAppliedCameraTarget: Pair<Region, LatLng?>?): Boolean =
    lastAppliedCameraTarget != null && target.first == lastAppliedCameraTarget.first && target.second != null &&
        target.second != lastAppliedCameraTarget.second

/**
 * The locate-me pan as an animated move: an ease to [to] over Re-center's duration ([NAVIGATION_VIEW_TRANSITION_MILLIS], the
 * navigation view's camera moves, and MapLibre's own 750 ms transition into following), keeping the zoom, the bearing and the
 * tilt the camera has. It ends no following and starts none.
 */
internal fun glideToLocate(map: MapLibreMap, to: LatLng) {
    map.easeCamera(CameraUpdateFactory.newLatLng(MapLibreLatLng(to.lat, to.lng)), NAVIGATION_VIEW_TRANSITION_MILLIS.toInt())
}
