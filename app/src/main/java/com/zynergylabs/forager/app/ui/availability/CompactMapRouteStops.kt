package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.NavigationTargetKind
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.route.RouteStopLink
import com.zynergylabs.forager.app.domain.route.navigateTargetOf
import com.zynergylabs.forager.app.domain.route.routeStopIndexOf
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.SelectedStopMarker
import com.zynergylabs.forager.app.ui.map.WaypointNavigationOrigin
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.layerPaintFor
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls

/*
 * A saved route's selected stop on the Maps tab (dispatch 2026-11-06-05, Amendment 2, items 3 and 4; RECORD -2187): where the card's
 * Navigate goes, how a marker tap selects a stop, and the marker the map draws in the accent colour. Kept out of
 * CompactMapRoutePlotting.kt, which calls each (the modular-code skill). Where a stop leads is the domain's (RouteStopNavigateTarget.kt).
 */

/** The saved route's selected stop's target, or `null`: no saved route, or a stop that leads nowhere on this phone. */
internal fun selectedStopTarget(controls: RoutePlottingControls?, sources: MapRecordSources?): RouteStopLink? {
    val state = controls?.state ?: return null
    val stops = (state.mode as? RoutePlotMode.Showing)?.trip?.route?.stops ?: return null
    val stop = stops.getOrNull(state.selectedStop) ?: return null
    return navigateTargetOf(stop, sources?.waypoints.orEmpty(), sources?.plannedTrips.orEmpty())
}

/** The way the host navigates to [target], or `null` where it navigates to none (a map with no navigation, in tests). */
private fun navigateFor(target: RouteStopLink, sources: MapRecordSources?): ((WaypointNavigationOrigin) -> Unit)? = when (target) {
    is RouteStopLink.Waypoint -> sources?.onNavigateToWaypoint
    is RouteStopLink.Trip -> sources?.onNavigateToTrip
}

/** Whether the card's Navigate can start: the selected stop leads somewhere, and the host can navigate there. */
internal fun routeCardCanNavigate(controls: RoutePlottingControls?, sources: MapRecordSources?): Boolean =
    selectedStopTarget(controls, sources)?.let { navigateFor(it, sources) } != null

/**
 * The card's Navigate (item 3): the card is told first, so its Back tier stands aside, then the existing navigation starts, through the
 * same `startWaypointNavigation` a bubble's Navigate reaches, from the card. Nothing happens where it cannot go; the button is greyed then.
 */
internal fun startRouteCardNavigation(controls: RoutePlottingControls?, sources: MapRecordSources?, inFullscreen: Boolean = false) {
    val target = selectedStopTarget(controls, sources) ?: return
    val navigate = navigateFor(target, sources) ?: return
    // Amendment 3: whether fullscreen was already on, so Back undoes whichever of the two came last (RouteCardBackOrder.kt).
    controls?.actions?.onNavigatedFromCard(inFullscreen)
    val kind = if (target is RouteStopLink.Trip) NavigationTargetKind.TRIP else NavigationTargetKind.WAYPOINT
    navigate(WaypointNavigationOrigin.RouteCard(target.id, kind))
}

/**
 * A waypoint's or a trip's marker tapped while a saved route shows (item 4, the owner: "Bubble opens and it's selected"): the first
 * stop leading to it is selected. The bubble is left to open as before; a marker that is no stop of the route selects nothing.
 */
internal fun selectRouteStopOnTap(tap: MapFeatureTap, sources: MapRecordSources) {
    val plotting = sources.routePlotting ?: return
    val stops = (plotting.state.mode as? RoutePlotMode.Showing)?.trip?.route?.stops ?: return
    val tapped = when (tap.layerId) {
        MapLayerIds.WAYPOINTS -> RouteStopLink.Waypoint(tap.featureId)
        MapLayerIds.PLANNED_TRIPS -> RouteStopLink.Trip(tap.featureId)
        else -> return
    }
    routeStopIndexOf(stops, tapped, sources.waypoints, sources.plannedTrips)?.let(plotting.actions::onSelectStop)
}

/**
 * The marker drawn over the selected stop's waypoint (or trip) in the accent colour, where it stands now, which is where Navigate goes;
 * `null` with no saved route, or where the stop leads nowhere (its waypoint moved or deleted: there is no marker of it to colour).
 */
internal fun selectedStopMarker(
    controls: RoutePlottingControls?,
    sources: MapRecordSources?,
    waypointsDrawn: Boolean = true,
    tripsDrawn: Boolean = true,
): SelectedStopMarker? =
    when (val target = selectedStopTarget(controls, sources)) {
        // Amendment 3, the owner: "Hide with the layer". With the Waypoints switch off there is no pin to colour, so no accent either.
        is RouteStopLink.Waypoint -> if (!waypointsDrawn) null else sources?.waypoints?.firstOrNull { it.id == target.id }?.let { SelectedStopMarker(LatLng(it.lat, it.lng), trip = false, featureId = it.id) }
        // Amendment 3, last bullet, the owner: "Hide with its layer". A trip stop's flag goes with the Planned Trips switch, as above.
        is RouteStopLink.Trip -> if (!tripsDrawn) null else sources?.plannedTrips?.firstOrNull { it.id == target.id }?.let { SelectedStopMarker(it.location, trip = true, featureId = it.id) }
        null -> null
    }

/** Amendment 3: whether the map draws the waypoints layer under [layers] (the Map Layers sheet's Waypoints switch), as names read it. */
internal fun waypointsLayerDrawn(layers: MapLayersState): Boolean = layerDrawn(MapLayerIds.WAYPOINTS, layers)

/** Amendment 3, last bullet: whether the map draws the planned trips layer under [layers] (the Planned Trips switch). */
internal fun plannedTripsLayerDrawn(layers: MapLayersState): Boolean = layerDrawn(MapLayerIds.PLANNED_TRIPS, layers)

private fun layerDrawn(id: String, layers: MapLayersState): Boolean =
    MAP_LAYER_REGISTRY.firstOrNull { it.id == id }?.let { layerPaintFor(it, layers).visible } ?: false
