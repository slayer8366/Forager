package com.zynergylabs.forager.app.domain.route

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint

/*
 * Where a saved route's stop leads, for the route card's Navigate and for picking a stop by its marker (dispatch 2026-11-06-05,
 * Amendment 2, item 3; RECORD -2187). Pure Kotlin: no Android, no Room.
 *
 * The owner: "The link has to be a soft pointer. If the waypoint is deleted, the link is cleared and the stop stays." So the stop's
 * name and spot are the stop, and its link only says where Navigate goes. In order:
 * 1. the stop's link, while its waypoint (or trip) is still on the phone, wherever it now stands;
 * 2. with no link (a route saved before schema 24, or a link cleared because its record was deleted, or a link to a record already
 *    gone that the next read will clear), a saved waypoint at exactly the stop's spot with exactly its name, else a trip so;
 * 3. otherwise none, and the card greys Navigate with the owner's words, "This stop's waypoint was moved or deleted".
 *
 * Exact spot: a stop's position is a copy of the waypoint's own lat and lng (RouteStopOf.kt), and SQLite keeps a REAL as it was
 * written, so an unmoved waypoint matches exactly, and any move at all is a move. Two records with the same name at the same spot
 * are the same place to a walker: the first in the list is taken, waypoints before trips.
 */

/** Where Navigate goes for [stop], or `null` where it cannot go (the reason is the card's). */
fun navigateTargetOf(stop: RouteStop, waypoints: List<Waypoint>, trips: List<PlannedTrip>): RouteStopLink? {
    when (val link = stop.link) {
        is RouteStopLink.Waypoint -> if (waypoints.any { it.id == link.id }) return link
        is RouteStopLink.Trip -> if (trips.any { it.id == link.id }) return link
        null -> Unit
    }
    waypoints.firstOrNull { it.name == stop.name && LatLng(it.lat, it.lng) == stop.position }?.let { return RouteStopLink.Waypoint(it.id) }
    trips.firstOrNull { it.name == stop.name && it.location == stop.position }?.let { return RouteStopLink.Trip(it.id) }
    return null
}

/**
 * The first of [stops] that leads to [tapped] (a waypoint's or trip's marker tapped on the map), or `null` when none does: the
 * owner's "Bubble opens and it's selected". A route that passes one waypoint twice (A, B, A) selects its first visit.
 */
fun routeStopIndexOf(stops: List<RouteStop>, tapped: RouteStopLink, waypoints: List<Waypoint>, trips: List<PlannedTrip>): Int? =
    stops.indexOfFirst { navigateTargetOf(it, waypoints, trips) == tapped }.takeIf { it >= 0 }
