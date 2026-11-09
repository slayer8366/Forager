package com.zynergylabs.forager.app.domain.route

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint

/*
 * A route's stop made from what was tapped (dispatch 2026-11-06-05, Amendment 2): its name and position copied, as since route
 * plotting began, and since schema 24 a soft link to it, so Navigate on the saved route's card knows where to go. One place for the
 * four taps that add a stop (a waypoint's bubble, a trip's bubble, and a waypoint or trip tapped while plotting).
 */

/** This waypoint as a route stop, linked to it. */
fun Waypoint.asRouteStop(): RouteStop = RouteStop(name, LatLng(lat, lng), RouteStopLink.Waypoint(id))

/** This planned trip as a route stop, linked to it (dispatch 2026-11-05-05, A11: a trip can be a stop). */
fun PlannedTrip.asRouteStop(): RouteStop = RouteStop(name, location, RouteStopLink.Trip(id))
