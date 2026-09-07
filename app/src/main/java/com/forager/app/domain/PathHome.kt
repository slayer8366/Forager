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
 * the walker by the sampler's interval and the flush buffer, so some hop is always there.
 * - Under [HOP_OMIT_BELOW_METERS]: omitted. Inside the noise of the fixes it is measured from.
 * - From there to [HOP_DEGRADE_ABOVE_METERS]: added as-is.
 * - Above that: added, and the estimate is marked degraded ([hopIsFar]) — beyond 50 m a straight
 *   line stops being a fair proxy for what will actually be walked, and the hop is a straight line
 *   by nature, short by nature. The number still includes it; the label is the honesty.
 *   Why 50 m: the live-fix gate's own ceiling ([LIVE_FIX_MAX_ACCURACY_METERS]) — a hop shorter
 *   than the worst position error the app displays cannot be told from that error. Nothing here
 *   reads a fix's *reported* accuracy (a constant on the owner's device — see the gate's doc).
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
 * ## Cost
 *
 * One haversine per consecutive pair, plus two: a few thousand points is well under a millisecond,
 * and this is recomputed on the 15 s track poll, not per fix. Points only ever append during a
 * recording, so the running sum could be kept incrementally; not done until a caller needs it.
 *
 * `null` when the track has no usable points: there is no path to measure, and a straight-line
 * substitute would be exactly the confidently-short number this exists to avoid.
 */
fun pathHome(track: Track, current: LatLng, origin: Waypoint?): PathHome? {
    val points = track.points
    if (points.isEmpty()) return null

    var trackMeters = 0.0
    for (i in 1 until points.size) {
        trackMeters += GeoDistance.metersBetween(
            LatLng(points[i - 1].lat, points[i - 1].lng),
            LatLng(points[i].lat, points[i].lng),
        )
    }
    val mostRecent = points.last()
    val hopMeters = GeoDistance.metersBetween(current, LatLng(mostRecent.lat, mostRecent.lng))
    val first = points.first()
    val originLegMeters = origin?.let { GeoDistance.metersBetween(LatLng(first.lat, first.lng), LatLng(it.lat, it.lng)) }

    return PathHome(
        trackMeters = trackMeters,
        hopMeters = hopMeters,
        originLegMeters = originLegMeters,
        pointCount = points.size,
    )
}

/** See [pathHome]. Every distance in metres. */
data class PathHome(
    /** Along the recorded points, most recent back to first. */
    val trackMeters: Double,
    /** Straight line from the current position to the most recent point, as measured — before the omit rule. */
    val hopMeters: Double,
    /** First stored point back to the origin waypoint; `null` when the track has no origin (nothing omitted then — the path simply ends at the first point). */
    val originLegMeters: Double?,
    val pointCount: Int,
) {
    /** The hop as counted: omitted below [HOP_OMIT_BELOW_METERS], otherwise as measured. */
    val hopCountedMeters: Double get() = if (hopMeters < HOP_OMIT_BELOW_METERS) 0.0 else hopMeters

    /** Above [HOP_DEGRADE_ABOVE_METERS]: the straight-line hop is no longer a fair proxy for what will be walked. */
    val hopIsFar: Boolean get() = hopMeters > HOP_DEGRADE_ABOVE_METERS

    /** The distance the walking time is computed from. */
    val totalMeters: Double get() = trackMeters + hopCountedMeters + (originLegMeters ?: 0.0)
}

/** Below this the hop is noise and is omitted — see [pathHome]. */
const val HOP_OMIT_BELOW_METERS = 25.0

/** Above this the hop is added and the estimate degrades — the live-fix gate's ceiling, see [pathHome]. */
const val HOP_DEGRADE_ABOVE_METERS = 50.0
