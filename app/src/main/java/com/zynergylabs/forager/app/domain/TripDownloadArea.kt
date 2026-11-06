package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.routedata.contains

/**
 * Where a trip's Download Maps in the Trip Planner opens the Offline Maps page (dispatch 2026-11-05-08, RECORD -2167, -2171).
 * The owner, verbatim: "Then tap Download Maps button from the Trip Planner menu to be taken to the Offline Maps page with the
 * coordinates already fixed into the Offline Maps, so all they have to do is adjust tile size and download." Pure Kotlin.
 */
sealed interface TripDownloadArea {
    /** Where the pin goes. */
    val center: LatLng

    /** A plain trip: the pin on the trip's own point. The page keeps the radius it already holds (the dispatch's B2). */
    data class AtTrip(override val center: LatLng) : TripDownloadArea

    /**
     * A route trip (Amendment 1, A4; the owner: "The whole line (Recommended)"). [center] is the middle of the route's line and
     * [radiusKm] the smallest whole kilometre from 1 to 24 whose download holds every point of it, or 24 when none does.
     * [lineCovered] says whether it does. [stopsOutside] is whether a stop is outside the area at [radiusKm]: the case the
     * owner's note is for (A3, A6). A route whose stops all fit but whose line doesn't is [lineCovered] false and [stopsOutside]
     * false (Amendment 1, C2).
     */
    data class AroundRoute(
        override val center: LatLng,
        val radiusKm: Int,
        val lineCovered: Boolean,
        val stopsOutside: Boolean,
    ) : TripDownloadArea
}

/**
 * [trip]'s [TripDownloadArea].
 *
 * **"Covered" is the downloaded rectangle, not a circle** (A4: "Say whether a circle test or the rectangle is used, and
 * why"). A point is covered when it lies inside [GeoDistance.boundingBox] at the centre and radius, the rectangle the download
 * itself asks for (`RegionRouteData.kt`, `MapLibreOfflineMapRepository`). The page's radius sets that rectangle and nothing
 * else, so the rectangle is the only test that says whether a point will be offline. A circle sits inside it, so a circle
 * test would start the radius larger than needed (by up to 1.41 times for a route along a diagonal) for no point it doesn't
 * already hold, spending tiles and reaching the 24 km cap sooner.
 *
 * **The line is every leg's points**, trail legs and straight legs alike (A4). Every leg starts and ends at a stop
 * ([com.zynergylabs.forager.app.domain.route.PlottedRoute]'s own rule), so the stops are among them. Between two points that
 * are inside, the drawn segment stays inside: the rectangle is aligned with latitude and longitude, and a segment's latitude
 * and longitude each run between its ends.
 *
 * **The centre is the middle of the line's extent**: halfway between its southernmost and northernmost points and between
 * its westernmost and easternmost ones, as [GeoDistance.boundingRegion] takes the middle. The rectangle reaches equally far
 * each way from its centre, so this is the centre that needs the least radius to reach both ends of each span.
 *
 * **The radius is found by trying each whole kilometre**, smallest first, with the same [GeoDistance.boundingBox] the download
 * uses, rather than by a formula that could round differently from it. 24 tries of a few thousand points at most.
 *
 * Not handled: a line that crosses the antimeridian, whose middle this reads as the far side of the world. The offline maps
 * cover the continental United States only (`OFFLINE_US_CONTINENTAL_NOTE`); such a route would open at 24 km with its stops
 * outside, and say so with the note.
 */
fun tripDownloadArea(trip: PlannedTrip): TripDownloadArea = TripDownloadArea.AtTrip(trip.location) // STUB: tests first
