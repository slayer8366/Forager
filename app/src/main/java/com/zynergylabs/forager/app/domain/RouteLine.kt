package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * The way back as the map draws it while returning (dispatch 2026-09-28-497, plan task T7, the
 * planner's ruling B). [atReturn] is the whole route as it was when Return was tapped, drawn dimmed
 * and grey; [ahead] is the latest route from the walker's assumed place to the start, drawn bright
 * over it. Whatever of the line at Return the bright line no longer covers is what the walker has
 * passed: no projection onto the route, by the owner's ruling for the route home, and a detour's
 * abandoned stretch stays grey too. [aheadIsCurrent] is false while the route is withheld: the last
 * line ahead stays, faded (the owner: "having your tracks disappear is a scary thing").
 *
 * [aheadParts] is [ahead] in its kinds, the walker's track dotted and the trail solid in one colour (dispatch
 * 2026-10-24-10; RECORD -1589); empty for the track home, drawn solid as before. [atReturnGeneration] is the
 * [RouteHome.Ahead.generation] [atReturn] was taken from.
 *
 * [aheadIsOffline] draws the line ahead grey: the way to a waypoint's line kept once location is lost, as its straight
 * line is (dispatch 2026-10-26-11 Amendment 2, item 1; RECORD -1670, Sorrel's S-413-1). The way home never sets it.
 *
 * [trailDimmed] dims the line ahead's trail part while the walker is off the trail (plan step 7 PR 3; RECORD -1631,
 * -1676, the owner: "A: trail dims only"); the dotted track is left as it is. Set only on the line the screen draws
 * (`TrackRecordingUiState.routeLineShown`), never by [nextRouteLine].
 */
data class RouteLine(
    val atReturn: List<LatLng>,
    val ahead: List<LatLng>,
    val aheadIsCurrent: Boolean,
    val aheadParts: List<RouteLinePart> = emptyList(),
    val atReturnGeneration: Long = 0,
    val aheadIsOffline: Boolean = false,
    val trailDimmed: Boolean = false,
)

/**
 * One run of the line ahead and what it is: the map draws [RouteLinePartKind.TRACK] dotted, [RouteLinePartKind.TRAIL]
 * solid and [RouteLinePartKind.STRAIGHT] thin and dashed.
 */
data class RouteLinePart(val kind: RouteLinePartKind, val points: List<LatLng>)

/**
 * [STRAIGHT] is a straight leg that is not the walker's track, the way to a waypoint's lead-in and final leg (dispatch
 * 2026-10-26-10), drawn thin and dashed by its own layer since step 6 PR 3 (`ui/map/RouteHomeLayers`). The way home never
 * makes one.
 */
enum class RouteLinePartKind { TRACK, TRAIL, STRAIGHT }

/**
 * The line after [routeHome]: the first route of a return becomes the line at Return, later routes
 * move only the line ahead, a withheld route keeps the last line ahead and marks it faded (and draws
 * nothing when there is no line yet), and no result keeps what there is. The caller clears the line
 * when the return ends.
 *
 * A route of another generation (the way home along trails swapping in, a re-route, or the track again after one)
 * replaces the line at Return once (dispatch 2026-10-24-10): kept, the old line's unwalked stretches would read as
 * passed.
 */
fun nextRouteLine(previous: RouteLine?, routeHome: RouteHome?): RouteLine? = when (routeHome) {
    null -> previous
    is RouteHome.Ahead -> RouteLine(
        atReturn = previous?.takeIf { it.atReturnGeneration == routeHome.generation }?.atReturn ?: routeHome.route,
        ahead = routeHome.routeAhead,
        aheadIsCurrent = true,
        aheadParts = routeHome.aheadParts,
        atReturnGeneration = routeHome.generation,
    )
    is RouteHome.Withheld -> previous?.copy(aheadIsCurrent = false)
}
