package com.zynergylabs.forager.app.domain.route

import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * A route plotted between waypoints and saved with a planned trip (dispatch 2026-10-30-02, step 1; the plan is
 * docs/plans/2026-10-29-waypoint-route-plotting-plan.md, RECORD -1817 to -1827). Pure Kotlin: no Android, no Room.
 *
 * The route keeps its own copy of its stops, so deleting a waypoint later changes nothing (the plan, "A waypoint deleted
 * later"). Leg *i* joins stop *i* to stop *i + 1*.
 *
 * Refused at construction, so neither the plotting nor a stored row can hold one:
 * - fewer than two stops, or a leg count other than one fewer than the stops;
 * - two consecutive stops at the same position. The owner: "Ignore the second tap (Recommended)"; the domain refuses
 *   them and A → B → A is allowed (Amendment 1).
 * - a leg that does not start at its first stop and end at its second.
 */
data class PlottedRoute(val stops: List<RouteStop>, val legs: List<RouteLeg>) {
    init {
        require(stops.size >= 2) { "a route has at least two stops; got ${stops.size}" }
        require(legs.size == stops.size - 1) { "a route of ${stops.size} stops has ${stops.size - 1} legs; got ${legs.size}" }
        stops.zipWithNext().forEachIndexed { i, (a, b) ->
            require(a.position != b.position) { "stops $i and ${i + 1} are at the same position" }
        }
        legs.forEachIndexed { i, leg ->
            require(leg.line.first() == stops[i].position && leg.line.last() == stops[i + 1].position) {
                "leg $i does not run from stop $i to stop ${i + 1}"
            }
        }
    }

    /** The route's length: every leg's, the joins to and from the trail included (Amendment 1). */
    val totalMeters: Double get() = legs.sumOf { it.meters }
}

/** A copy of a waypoint as it was when the route was plotted: its name and position. No waypoint id is kept. */
data class RouteStop(val name: String, val position: LatLng)

/** One leg of a [PlottedRoute]: along trails, or a straight line with the reason there is no trail route. */
sealed interface RouteLeg {
    /** The leg's line, from its first stop to its second. */
    val line: List<LatLng>

    /** The leg's length in metres, as plotted. */
    val meters: Double

    /**
     * Along trails. [line] runs stop → trail → stop: `line[trailFrom..trailTo]` is the trail part, and the stretches
     * before and after it are the straight joins from each stop to the trail point nearest it. The owner: "Dashed link to
     * trail (Recommended)": the joins are drawn dashed and count in [meters], so they are kept apart from the trail part
     * here (Amendment 1).
     *
     * [trailHeightsMetres] is the trail data's height at each point of the trail part, `line[trailFrom..trailTo]`, in order;
     * `null` where the data has none (`TrailRoute.heightsMetres`, #447). The joins carry none: a stop is a name and a
     * position only. Defaults to all missing. Only plotting reads it (dispatch 2026-10-30-08, Amendment 8, RECORD -1896):
     * a saved route does not store it until step 3b's migration, so a route read back from the database has none.
     */
    data class Trail(
        override val line: List<LatLng>,
        override val meters: Double,
        val trailFrom: Int,
        val trailTo: Int,
        val trailHeightsMetres: List<Double?> = List((trailTo - trailFrom + 1).coerceAtLeast(0)) { null },
    ) : RouteLeg {
        init {
            require(line.size >= 2) { "a leg has at least two points; got ${line.size}" }
            require(trailFrom in 0 until trailTo && trailTo <= line.lastIndex) {
                "the trail part $trailFrom..$trailTo is not inside a line of ${line.size} points"
            }
            require(trailHeightsMetres.size == trailTo - trailFrom + 1) {
                "${trailHeightsMetres.size} heights for a trail part of ${trailTo - trailFrom + 1} points"
            }
        }
    }

    /** The straight line between the two stops, and [reason], why there is no trail route for it. */
    data class Straight(override val line: List<LatLng>, override val meters: Double, val reason: StraightReason) : RouteLeg {
        init {
            require(line.size >= 2) { "a leg has at least two points; got ${line.size}" }
        }
    }
}

/**
 * Why a leg is a straight line. No on-screen text: the words are the owner's, at step 3 (the dispatch: "Any new word on
 * screen is the owner's"). Stored by name, so a name is never changed once shipped.
 *
 * Not `WayToWaypointNote`, whose text is written for a walker ("You're far from any trail"); here both ends are stops.
 */
enum class StraightReason {
    /** Neither stop is inside a downloaded area. */
    NO_REGION_COVERS,

    /** No one downloaded area holds both stops. */
    IN_DIFFERENT_AREAS,

    /** An area holding both has routes not on the phone: never fetched, or the fetch failed. */
    ROUTES_NOT_DOWNLOADED,

    /** An area holding both is fetching its routes now. */
    ROUTES_FETCHING,

    /** An area holding both had its routes refused because of this app's version. */
    ROUTES_NEED_APP_UPDATE,

    /** The routes could not be read: the areas could not be listed, the routes are damaged, or a file is unreadable. */
    ROUTES_UNREADABLE,

    /** The server has no routes for the area, or the build lacks a stop's square. */
    NO_TRAIL_ROUTES,

    /** No trail within the engine's reach of the leg's first stop. */
    START_FAR_FROM_TRAIL,

    /** No trail within the engine's reach of the leg's second stop. */
    END_FAR_FROM_TRAIL,

    /** Both stops are near trails and no route joins them, the engine failed, or it could not be built. */
    NO_ROUTE_FOUND,

    /** The trail route runs outside the downloaded area. */
    LEAVES_AREA,
}
