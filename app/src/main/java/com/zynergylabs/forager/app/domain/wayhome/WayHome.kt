package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * The way home along trails (dispatch 2026-10-23-09, plan step 5; Amendment 1, part 1): the walker's own track back
 * to where they left the trail, the trail route from there to where they first joined it, and their own track from
 * there to the start (RECORD -1498: "The line follows the walker's own track back to where they left the trail, then
 * the trail route home, with no straight-line shortcut"; RECORD -1589, the far end: "Back on your track").
 *
 * **From the most recent stored point**, as `RouteHome`'s route is: the hop from the walker's fix to that point, and its
 * band, stay the caller's (part 2), so this is pure in the track and the route data and holds no fix.
 *
 * Nothing draws or reads this yet: part 2 puts it on the map (the track parts dotted, the trail part solid, one
 * colour; RECORD -1589) and into the HUD and sundown.
 *
 * [regionId] is the downloaded region whose routes the trail part was found on.
 */
data class WayHome(
    /** The walker's track from the most recent stored point back to the point they left the trail at. */
    val trackBack: WayHomePart,
    /**
     * The trail route, from the point the walker left the trail at to the point they first joined it. Its two ends are
     * those track points, joined to the engine's route by straight joints of at most [WAY_HOME_TRAIL_JOIN_METERS] each
     * ([trailJointMeters]): the engine's route starts and ends on the trail, the track points lie beside it.
     */
    val trail: WayHomePart,
    /** The walker's track from the point they first joined the trail back to the first stored point, then the origin waypoint when there is one. */
    val trackToStart: WayHomePart,
    /** The two straight joints inside [trail], summed. Part of [trail]'s metres; here so a test can check the arithmetic. */
    val trailJointMeters: Double,
    val regionId: Long,
) {
    /** The whole way home as one line, most recent stored point first, each part's shared end point once. */
    val line: List<LatLng>
        get() = trackBack.points + trail.points.drop(1) + trackToStart.points.drop(1)

    /** The length of [line]: the three parts' metres, summed. */
    val totalMeters: Double get() = trackBack.meters + trail.meters + trackToStart.meters

    /**
     * The engine's trail route alone: [trail] without its two straight joints (dispatch 2026-10-26-13 Amendment 2;
     * RECORD -1683, the owner: "The trail itself (Recommended)"), what the quiet off-trail state counts as on the trail.
     * Measured against the joints too, "on" reached up to [WAY_HOME_TRAIL_JOIN_METERS] further out along the track the
     * walker left by, and a re-route mid-stray put that joint out on the stray (Hazel's S22 check of #415, step 3).
     *
     * Told apart from data the line carries, no new figure: [FindWayHomeUseCase] makes [trail] as the leave point, the
     * engine's route, then the entry point, so the joints are its first and last segments; and [trailJointMeters] is
     * their length. Zero means there are none to drop: the way to a waypoint has its straight legs as parts of their
     * own (`FindWayToWaypointUseCase`, `trailJointMeters = 0.0`), and a way home whose joints are both zero long has its
     * end points on the route already, so the whole part is the same line.
     */
    val trailRoute: List<LatLng>
        get() = if (trailJointMeters > 0.0) trail.points.drop(1).dropLast(1) else trail.points
}

/**
 * One part of a [WayHome]: its [kind], its line in walking order, and its length. [heights] are its barometric heights
 * for the walk back's climbing allowance (`WayHomeHeights.kt`; RECORD -1689): the track parts' own, the trail part's a
 * stand-in from the walker's track; `null` where nothing measured them (the way to a waypoint's parts).
 */
data class WayHomePart(val kind: WayHomePartKind, val points: List<LatLng>, val meters: Double, val heights: PartHeights? = null)

/**
 * What a [WayHomePart] is, which part 2 draws differently: the track parts dotted, the trail part solid (RECORD -1589).
 * [STRAIGHT] is a straight leg that is not the walker's track: the way to a waypoint's lead-in and final leg (dispatch
 * 2026-10-26-10, `domain/wayto/WayToWaypoint`). The way home never makes one.
 */
enum class WayHomePartKind { TRACK_BACK, TRAIL, TRACK_TO_START, STRAIGHT }

/**
 * Why the way home is the walker's track alone, each with the line shown beside today's track line (RECORD -1586 and
 * -1589, the owner's words, "Accept all"). The way home never shows no line: every one of these keeps the track.
 */
enum class WayHomeNote(val text: String) {
    /**
     * The data says there are no trail routes here: no downloaded region covers the start (RECORD -1586), the server has
     * none for the area, or its build lacks the start's square. Never for routes not yet on the phone (RECORD -1592), which
     * is the rule `RegionRouteData.NotFetched` states.
     */
    NO_TRAIL_ROUTES("No trail routes here · following your track"),

    /**
     * The region's routes are not on the phone: never fetched, or the fetch failed (RECORD -1592, the owner: "Two honest
     * notes"; a failed fetch confirmed by the owner, RECORD -1595), except a fetch only an app update can fix
     * ([ROUTES_NEED_APP_UPDATE]).
     */
    ROUTES_NOT_DOWNLOADED("Trail routes not downloaded · following your track"),

    /**
     * The region's routes were refused because of this app, not the area: the server refused its key, or the build has
     * none (`RouteDataMissingReason.needsAppUpdate`). Only a different build fixes it, as the Offline maps row says ("Map
     * only. Trail routes need an app update."). RECORD -1597, the owner: "Say \"needs an update\"".
     */
    ROUTES_NEED_APP_UPDATE("Trail routes need an app update · following your track"),

    /** The region's routes are being fetched now (RECORD -1592). */
    ROUTES_FETCHING("Getting trail routes… · following your track"),

    /** Routes are stored and cannot be read: a damaged region, or the engine finding a file missing or corrupt. RECORD -1589. */
    ROUTES_UNREADABLE("Trail routes couldn't be read · following your track"),

    /** The walker's track never came within [WAY_HOME_TRAIL_JOIN_METERS] of a trail. RECORD -1589. */
    NEVER_NEAR_TRAIL("No trail on your way out · following your track"),

    /** Both ends are near trails and no trail route joins them, or the engine failed. RECORD -1589. */
    NO_ROUTE_FOUND("Couldn't find a trail route · following your track"),

    /** The trail route runs outside the downloaded area, or is so much longer than the track it reads as a detour around the area's edge. RECORD -1546, -1589. */
    LEAVES_AREA("This route leaves your downloaded area. Download a larger area to route there."),
}

/** What [FindWayHomeUseCase] found. */
sealed interface WayHomeResult {
    /** The way home along trails. */
    data class AlongTrails(val wayHome: WayHome) : WayHomeResult

    /**
     * The way home is the walker's track ([com.zynergylabs.forager.app.domain.routeHome], as today). [note] says why,
     * shown beside it. `null` when the track is the way home and there is nothing to explain: too few points to have a
     * way home at all, or a track that touches a trail only where it first joined one, so no trail route is shorter.
     */
    data class TrackOnly(val note: WayHomeNote?) : WayHomeResult
}

/**
 * How close a track point must be to a trail to count as on it: where the walker left the trail and where they joined
 * it (RECORD -1589, the owner: "25 m (Recommended)"). **Provisional**, tuned on a walk, here and nowhere else.
 * The report's reasons: above the self-join ε (10 m) and the hop's enter figure (25 m); near the spike's map-matching
 * spread on a real walk (median 8.3 m, 90th percentile 16.0 m, maximum 28.0 m, on roads); under the live-fix gate's 50 m.
 * It is also the most a straight joint between a track point and the trail route can be.
 */
const val WAY_HOME_TRAIL_JOIN_METERS = 25.0

/**
 * The trail route counts as leaving the area when it is longer than this many times the walker's own track between the
 * same two points (RECORD -1589, the planner's call on the owner's "Edge + length check"). **Provisional.** The walker
 * walked that track, so it is a known way between those points; a "best" trail route much longer than it is the
 * costlier detour BRouter returns when the true best path runs off the cut data (RECORD -1547). No walk has measured
 * how often a true trail route is this much longer than a track; the figure is a first guess to be tuned.
 */
const val WAY_HOME_DETOUR_RATIO = 1.5

/**
 * The [WAY_HOME_DETOUR_RATIO] check runs only when the walker's own track between the two trail points is longer than
 * this; under it the check is skipped and the trail route is used as found (RECORD -1600, the owner: "Switch to (b)
 * (Recommended)", comparing lengths only when the walker went over 100 m between the two trail points). **Provisional**,
 * like the 25 m and the 1.5×, and not yet measured on a walk.
 *
 * Why: a walk that crosses a trail or walks beside one has a short stretch between the points and a trail part of about
 * 50 to 80 m, mostly the two joints of up to [WAY_HOME_TRAIL_JOIN_METERS] each (Sorrel's review of #389, M-389-2), which
 * the ratio would flag as leaving the area when it does not. The rejected alternative, Amendment 3's option (c), gave
 * any trail part under 100 m the track home with no note; it dropped a real shortcut on a walk that loops off a trail
 * and crosses it again a short way along (RECORD -1600).
 */
const val WAY_HOME_DETOUR_CHECK_MIN_TRACK_METERS = 100.0

/**
 * The longest trail part used when the [WAY_HOME_DETOUR_RATIO] check is skipped, at [WAY_HOME_DETOUR_CHECK_MIN_TRACK_METERS]
 * or less walked between the two trail points (RECORD -1603, the owner: "Add a cap in part 2 (Recommended)"; RECORD -1613,
 * "150 m, no note (Recommended)"). Over it, the route is not used and the track is the way home, with no note.
 * **Provisional**, as the figures it is made of are.
 *
 * Why: with the check skipped, nothing but the area's rectangle bounds the route, so a crossing where the two matched
 * ways only meet far away could be handed a route of kilometres where the walker's own short track was the answer
 * (Sorrel's re-check of #389, L-389R-1). Why this figure: it is 1.5 × 100, the longest trail part the ratio check accepts
 * just over its floor, so the rule is continuous at 100 m and adds no free number of its own. The cases the floor was
 * built for stay under it: a crossing's trail part of 50 to 80 m (M-389-2) and the looped walk's 80 m shortcut.
 * Why no note: the walker's track between the points is 100 m or less, a short way they know; "leaves your downloaded
 * area" would be false, and so would "couldn't find a trail route", since one was found and refused.
 */
const val WAY_HOME_SHORT_WALK_MAX_TRAIL_METERS = WAY_HOME_DETOUR_RATIO * WAY_HOME_DETOUR_CHECK_MIN_TRACK_METERS

/**
 * How far apart, along the track, the points tested for a trail are: every point at least this far on from the last
 * one tested, plus the scan's first point. **Provisional**: the test's cost is one engine call per point (the S22
 * timing decides the method, RECORD -1589). Under [WAY_HOME_TRAIL_JOIN_METERS], so a walk along a trail is tested at
 * least once each stretch of that length; a trail only crossed, not walked, can fall between two tested points.
 */
const val WAY_HOME_SCAN_SPACING_METERS = 20.0
