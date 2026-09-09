package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.Waypoint

/**
 * The length of the walk back to a track's origin, following the recorded track — return-estimate
 * dispatch, Item 1. This is the distance half of `walking time = distance remaining along the
 * walked track ÷ average moving speed` ([returnWalkingTime]); the other half is [movingPace].
 *
 * **Not the straight-line distance.** [ComputeReturnToStartUseCase] already gives that and the HUD
 * shows it; the way back is the track, and it is longer.
 *
 * ## Where the walker is on the track: the most recent point (owner ruling)
 *
 * The current position is mapped onto the track at its **most recent point**, never by a
 * nearest-point projection. Nearest-point will look like an obvious improvement to someone later:
 * it is wrong here because its error can be *short*. Near a switchback, or where a loop passes
 * close to itself, a nearest-point search snaps the walker onto a segment they have not yet
 * reached and returns less path than the truth — the one direction that gets someone caught out
 * after dark. The most-recent-point rule's error is never short: after a double-back it
 * overstates by at most the length of the leg just walked twice, which the walker knows about.
 * "Its error is never short, and that's the only property that matters in a safety input."
 *
 * ## The hop
 *
 * The straight line from the current position to that most recent point. The recorded track lags
 * the walker by the sampler's interval and the flush buffer, so some hop is always there. It is
 * in one of three bands ([HopBand]), and **the band has hysteresis** — the compass pattern
 * ([CompassTrustJudge]): asymmetric thresholds, enter above one figure and leave only below a
 * lower one, so a walker hovering near a threshold does not see the number flicker.
 * - **Omitted** ([HopBand.NONE]): the hop is inside the noise of the fixes it is measured from.
 *   Entered from counted only below [HOP_LEAVE_BELOW_METERS].
 * - **Counted** ([HopBand.COUNTED]): added as-is. Entered above [HOP_ENTER_ABOVE_METERS].
 * - **Far** ([HopBand.FAR]): added, and the estimate is marked degraded ([hopIsFar]) — beyond
 *   50 m a straight line stops being a fair proxy for what will actually be walked, and the hop
 *   is a straight line by nature, short by nature. The number still includes it; the label is the
 *   honesty. Entered above [HOP_FAR_ENTER_ABOVE_METERS], left only below
 *   [HOP_FAR_LEAVE_BELOW_METERS]. Why 50 m: the live-fix gate's own ceiling
 *   ([LIVE_FIX_MAX_ACCURACY_METERS]) — a hop shorter than the worst position error the app
 *   displays cannot be told from that error. Nothing here reads a fix's *reported* accuracy (a
 *   constant on the owner's device — see the gate's doc).
 *
 * **Plain bands were tried first and went back (owner ruling, same day as the build).** The
 * build dispatch stated three memoryless bands (omit under 25, add to 50, degrade above), which
 * overwrote the accepted pre-build proposal's hysteresis without noticing it reversed a decision;
 * it was built as stated and flagged, and the owner restored the band. The flicker is real, not
 * theoretical: a walker standing near the 25 m boundary sees 25 m of remaining distance appear
 * and vanish on successive fixes — about 28 seconds of walking time at 0.89 m/s — on a number
 * they are checking because light is running out. The band is carried between calls as
 * [PathHome.hopBand], which the caller passes back as `previousHopBand`; this function is pure
 * and holds nothing, so the caller decides what one recording's state is (a new recording starts
 * at [HopBand.NONE]).
 *
 * ## The origin's last hop (owner ruling: include it)
 *
 * The leg from the first stored point back to the origin waypoint is real walking, so it is added
 * whenever the track has an origin. **Known weakness, recorded and not fixed here:** the origin
 * waypoint may itself be a network fix — if recording began before GPS settled, the origin can be
 * tens of metres from where the walker parked. Nothing here assumes the origin is accurate; the
 * leg is whatever the two stored positions say it is. Queued as its own dispatch.
 *
 * ## Which points
 *
 * [Track.points] as the repository read seam delivers them — network-provider fixes already
 * excluded (`RoomTrackRepository`, the one mapping). Excluded points are not walked distance; the
 * gaps they leave make the recorded path *shorter* than the ground walked, which is why
 * [returnWalkingTime] degrades the estimate when the exclusion is large. This function takes a
 * [Track], not a bare point list, so a caller cannot hand it points by any other route.
 *
 * ## The track is joined to itself (owner ruling, 2026-09-07)
 *
 * [trackMeters] is **not** the sum of the legs. That sum never decreases during a recording —
 * points only append — so on a plain out-and-back it read the whole trip while the walker stood
 * beside the car, and on a patch worked for twenty minutes it read 733 m for a 6 m walk. The
 * owner ruled the track-network candidate inside the retrace ruling: the track is joined to
 * itself wherever it passes within ε ([SELF_JOIN_EPSILON_METERS]) of itself, and the distance is
 * the shortest route home along the joined track ([joinedTrackHome]). Every edge of that route
 * is either a leg between consecutive stored points or a join between two points the walker
 * actually occupied, so the route never crosses more than ε of unwalked ground, and only where
 * the walker already stood within ε of it. The most-recent-point rule above is unchanged: the
 * hop still lands on the last stored point, and it is the *track* that is joined, at points,
 * never the walker projected onto a segment. See the ε constant's own doc for the ruling's
 * condition on it and why every request to raise it reopens the ruling. [PathHome.joinsOnRoute]
 * says how many joins the route crossed; zero means the plain retrace.
 *
 * ## Cost
 *
 * The join scan and the shortest-route search — see [joinedTrackHome], "Cost": linear-ish in the
 * point count with a grid hash, measured in the join dispatch's completion report. Recomputed on
 * the 15 s track poll, not per fix. Points only ever append during a recording, so the graph
 * could be kept incrementally; not done until a measurement says it is needed.
 *
 * `null` when the track has no usable points: there is no path to measure, and a straight-line
 * substitute would be exactly the confidently-short number this exists to avoid.
 */
fun pathHome(track: Track, current: LatLng, origin: Waypoint?, previousHopBand: HopBand = HopBand.NONE): PathHome? {
    val points = track.points
    if (points.isEmpty()) return null

    val joined = joinedTrackHome(points)
    val mostRecent = points.last()
    val hopMeters = GeoDistance.metersBetween(current, LatLng(mostRecent.lat, mostRecent.lng))
    val first = points.first()
    val originLegMeters = origin?.let { GeoDistance.metersBetween(LatLng(first.lat, first.lng), LatLng(it.lat, it.lng)) }

    return PathHome(
        trackMeters = joined.meters,
        hopMeters = hopMeters,
        hopBand = nextHopBand(previousHopBand, hopMeters),
        originLegMeters = originLegMeters,
        pointCount = points.size,
        joinsOnRoute = joined.joinsOnRoute,
    )
}

/**
 * The hop's band after this hop, given the last one — see [pathHome], "The hop". Enter above the
 * enter figure, stay until below the leave figure; a hop can cross two bands in one step (far to
 * omitted, or omitted to far) when the numbers say so.
 */
fun nextHopBand(previous: HopBand, hopMeters: Double): HopBand {
    val far = when (previous) {
        HopBand.FAR -> hopMeters >= HOP_FAR_LEAVE_BELOW_METERS
        else -> hopMeters > HOP_FAR_ENTER_ABOVE_METERS
    }
    if (far) return HopBand.FAR
    val counted = when (previous) {
        HopBand.NONE -> hopMeters > HOP_ENTER_ABOVE_METERS
        else -> hopMeters >= HOP_LEAVE_BELOW_METERS
    }
    return if (counted) HopBand.COUNTED else HopBand.NONE
}

/** See [pathHome], "The hop". */
enum class HopBand { NONE, COUNTED, FAR }

/** See [pathHome]. Every distance in metres. */
data class PathHome(
    /** Along the recorded points, most recent back to first, by the shortest route through the self-joined track — see [pathHome], "The track is joined to itself". */
    val trackMeters: Double,
    /** Straight line from the current position to the most recent point, as measured — before the band rule. */
    val hopMeters: Double,
    /** The band this hop landed in, given the previous one — pass it back as the next call's `previousHopBand`. */
    val hopBand: HopBand,
    /** First stored point back to the origin waypoint; `null` when the track has no origin (nothing omitted then — the path simply ends at the first point). */
    val originLegMeters: Double?,
    val pointCount: Int,
    /** How many self-joins the route home crosses ([JoinedTrackHome.joinsOnRoute]); zero means the plain retrace. */
    val joinsOnRoute: Int = 0,
) {
    /** The hop as counted: nothing in [HopBand.NONE], otherwise as measured. */
    val hopCountedMeters: Double get() = if (hopBand == HopBand.NONE) 0.0 else hopMeters

    /** [HopBand.FAR]: the straight-line hop is no longer a fair proxy for what will be walked. */
    val hopIsFar: Boolean get() = hopBand == HopBand.FAR

    /** The distance the walking time is computed from. */
    val totalMeters: Double get() = trackMeters + hopCountedMeters + (originLegMeters ?: 0.0)
}

/** The hop starts counting above this — see [pathHome], "The hop". */
const val HOP_ENTER_ABOVE_METERS = 25.0

/** A counted hop stops counting only below this — five metres of band under the enter figure (owner ruling). */
const val HOP_LEAVE_BELOW_METERS = 20.0

/** The hop degrades the estimate above this — the live-fix gate's ceiling, see [pathHome]. */
const val HOP_FAR_ENTER_ABOVE_METERS = 50.0

/** A far hop stops degrading only below this — five metres of band under the enter figure (owner ruling). */
const val HOP_FAR_LEAVE_BELOW_METERS = 45.0
