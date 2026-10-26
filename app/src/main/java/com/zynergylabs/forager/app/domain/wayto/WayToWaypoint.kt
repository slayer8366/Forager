package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.WayHomePartKind

/**
 * What [FindWayToWaypointUseCase] found (dispatch 2026-10-26-10, step 6 PR 2; the rulings are RECORD -1652 and -1653).
 * Nothing draws or reads this yet: PR 3 puts it on the map and in the HUD.
 */
sealed interface WayToWaypointResult {
    /**
     * The way to the waypoint along trails, in [WayHome]'s three slots so the way home's placing, figure, line ahead and
     * drawing take it unchanged (the step 6 report's choice, accepted by the dispatch):
     * - [WayHome.trackBack] holds the **lead-in**, a straight leg from the walker to the trail point nearest them (Q1 (a));
     * - [WayHome.trail] holds the **trail route** between the two trail points;
     * - [WayHome.trackToStart] holds the **final leg**, a straight leg from the trail point nearest the waypoint to the
     *   waypoint (Q2).
     *
     * Both legs are [WayHomePartKind.STRAIGHT], never a track kind: dotted means "your own track" (RECORD -1589). The
     * slot names read oddly here ("trackToStart" holding the final leg); that is naming debt the report chose over a
     * second line type and copies of the four functions that read one.
     */
    data class AlongTrails(val way: WayHome) : WayToWaypointResult

    /** No trail route: today's straight line to the waypoint, with [note] saying why. */
    data class StraightLine(val note: WayToWaypointNote) : WayToWaypointResult
}

/**
 * Why the way to a waypoint is today's straight line (RECORD -1652 Q4, the owner: "Approve as written"; confirmed at
 * -1653): the way home's notes with "· straight line" in place of "· following your track", since the fallback here is
 * the straight line, and three of the waypoint's own.
 */
enum class WayToWaypointNote(val text: String) {
    /** No downloaded region covers either end, the server has no routes for the area, or the build lacks an end's square. */
    NO_TRAIL_ROUTES("No trail routes here · straight line"),

    /** An end's region has routes not on the phone: never fetched, or the fetch failed (RECORD -1592, -1595). */
    ROUTES_NOT_DOWNLOADED("Trail routes not downloaded · straight line"),

    /** An end's region's routes were refused because of this app (RECORD -1597). */
    ROUTES_NEED_APP_UPDATE("Trail routes need an app update · straight line"),

    /** An end's region's routes are being fetched now (RECORD -1592). */
    ROUTES_FETCHING("Getting trail routes… · straight line"),

    /** Routes are stored and cannot be read: a damaged region, a file missing or corrupt, or the regions unreadable. */
    ROUTES_UNREADABLE("Trail routes couldn't be read · straight line"),

    /** Both ends are near trails and no trail route joins them, the engine failed, or it could not be built. */
    NO_ROUTE_FOUND("Couldn't find a trail route · straight line"),

    /**
     * The trail route runs outside the downloaded area. The way home's sentence as it stands: it never ended "· following
     * your track", so there was no tail to swap (the coder's reading of Q4, reported to the planner).
     */
    LEAVES_AREA("This route leaves your downloaded area. Download a larger area to route there."),

    /** No trail within the engine's reach of the waypoint, 250 m (Q2: "a waypoint over 250 m from any trail"). */
    WAYPOINT_FAR_FROM_TRAIL("The waypoint is far from any trail · straight line"),

    /** No trail within the engine's reach of the walker, 250 m. */
    WALKER_FAR_FROM_TRAIL("You're far from any trail · straight line"),

    /** The walker and the waypoint are not both inside one downloaded area with routes. */
    WAYPOINT_IN_ANOTHER_AREA("The waypoint is in another area · straight line"),
}
