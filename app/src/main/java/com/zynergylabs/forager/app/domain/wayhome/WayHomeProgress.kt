package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.HOP_FAR_ENTER_ABOVE_METERS
import com.zynergylabs.forager.app.domain.HopBand
import com.zynergylabs.forager.app.domain.LIVE_FIX_MAX_ACCURACY_METERS
import com.zynergylabs.forager.app.domain.PathHome
import com.zynergylabs.forager.app.domain.RouteLinePartKind
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.nextHopBand
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * A [WayHome] as a line the walker's place can be found on (dispatch 2026-10-24-10 Amendment 1, PR B): its points,
 * with every stretch longer than [WAY_HOME_LINE_STEP_METERS] filled in so no point of the line is more than half a
 * step from a node, each node's distance along the line, and what kind of part each stretch is.
 *
 * The distances along each part add up to that part's own metres ([WayHomePart.meters]): the trail part's are the
 * engine's, its joints included, and the track parts' the self-joined track's. So the line's length is
 * [WayHome.totalMeters] exactly, and the HUD's figure at Return is the figure the way home was found with.
 *
 * [trailAllowanceMeters] is what [placeOnWayHome] adds to the figure while its node is on the trail part: the way home's
 * [WAY_HOME_TRAIL_PLACE_ALLOWANCE_METERS] by default, 0 for the way to a waypoint (RECORD -1671; see that constant).
 */
class WayHomeLine(val wayHome: WayHome, val trailAllowanceMeters: Double = WAY_HOME_TRAIL_PLACE_ALLOWANCE_METERS) {
    val nodes: List<LatLng>
    val alongMeters: DoubleArray

    /** The kind of the stretch from node `i` to node `i + 1`; the last node takes its stretch's kind. */
    val kinds: List<RouteLinePartKind>

    init {
        val points = ArrayList<LatLng>()
        val along = ArrayList<Double>()
        val kindOf = ArrayList<RouteLinePartKind>()
        var total = 0.0
        for (part in listOf(wayHome.trackBack, wayHome.trail, wayHome.trackToStart)) {
            val kind = when (part.kind) {
                WayHomePartKind.TRAIL -> RouteLinePartKind.TRAIL
                // The way to a waypoint's straight legs (dispatch 2026-10-26-10): not the walker's track, so never dotted.
                WayHomePartKind.STRAIGHT -> RouteLinePartKind.STRAIGHT
                WayHomePartKind.TRACK_BACK, WayHomePartKind.TRACK_TO_START -> RouteLinePartKind.TRACK
            }
            val geometric = part.points.zipWithNext { a, b -> GeoDistance.metersBetween(a, b) }
            val sum = geometric.sum()
            val scale = if (sum > 0.0) part.meters / sum else 0.0
            if (points.isEmpty() && part.points.isNotEmpty()) {
                points += part.points.first()
                along += total
                kindOf += kind
            }
            for (i in geometric.indices) {
                val from = part.points[i]
                val to = part.points[i + 1]
                val steps = ceil(geometric[i] / WAY_HOME_LINE_STEP_METERS).toInt().coerceAtLeast(1)
                kindOf[kindOf.lastIndex] = kind
                for (s in 1..steps) {
                    val t = s.toDouble() / steps
                    points += LatLng(from.lat + (to.lat - from.lat) * t, from.lng + (to.lng - from.lng) * t)
                    along += total + geometric[i] * scale * t
                    kindOf += kind
                }
                total += geometric[i] * scale
            }
        }
        nodes = points
        alongMeters = along.toDoubleArray()
        kinds = kindOf
    }

    val totalMeters: Double get() = alongMeters.lastOrNull() ?: 0.0

    /** Metres from [point] to the nearest node: within half a [WAY_HOME_LINE_STEP_METERS] of the distance to the line. */
    fun metersFrom(point: LatLng): Double = nodes.minOfOrNull { GeoDistance.metersBetween(point, it) } ?: Double.POSITIVE_INFINITY
}

/**
 * Where the walker is on a [WayHomeLine], and how far home along it.
 *
 * [index] is the node they are placed at, which the needle and the greyed walked part read; [hopMeters] the straight line
 * from their fix to it, [hopBand] that hop's band ([nextHopBand], passed back as the next call's previous band).
 * [remainingMeters] is the distance home, counted from its own node, the earliest within [WAY_HOME_FIGURE_REACH_METERS]
 * anywhere on the line ([placeOnWayHome]; Amendments 3 and 4), plus the line's [WayHomeLine.trailAllowanceMeters] when that node is on the trail part
 * ([onTrail]), plus, for a walker past the line's first point, their distance back to it ([metersPastLineStart]; RECORD
 * -1630).
 */
data class WayHomePlace(
    val index: Int,
    val hopMeters: Double,
    val hopBand: HopBand,
    val onTrail: Boolean,
    val remainingMeters: Double,
)

/**
 * This place's distance home as a [PathHome], the shape the walking time and the HUD's route read: [remainingMeters] as
 * `trackMeters`, whole. The hop is kept as measured but not counted (band NONE): since Amendment 3 the figure's reach
 * already covers the fix's distance from the line.
 */
fun WayHomePlace.toPathHome(pointCount: Int): PathHome =
    PathHome(trackMeters = remainingMeters, hopMeters = hopMeters, hopBand = HopBand.NONE, originLegMeters = null, pointCount = pointCount)

/** What [placeOnWayHome] found: a place on the line, or the walker more than the far band from it. */
sealed interface WayHomeProgress {
    data class Placed(val place: WayHomePlace) : WayHomeProgress

    /** No node within the hop's far band: the HUD withholds the route, as the far hop does today. [hopBand] is [HopBand.FAR]. */
    data class OffLine(val metersFromLine: Double) : WayHomeProgress
}

/**
 * The walker's place on [line] from the fix [current] (RECORD -1613, the owner: "A, plus 25 m (Recommended)"; dispatch
 * 2026-10-24-10 Amendment 1, ruling 1). **Not** a nearest-point projection, which `PathHome.kt`'s owner ruling rules out
 * because its error can be short ("Its error is never short, and that's the only property that matters in a safety
 * input").
 *
 * The place is the **earliest** node, in walking order and not before [previous]'s, within
 * [WAY_HOME_TRAIL_JOIN_METERS] of the fix; failing that, within the far band ([HOP_FAR_ENTER_ABOVE_METERS]); and only
 * when nothing from [previous] on is that near, the earliest anywhere on the line (a walker who has turned back). The
 * earliest node near the walker is never further along than where they truly are, so the line's metres from it are
 * never short. A switchback's later leg within reach is never chosen over the leg the walker is on. That place is what
 * the needle and the greyed walked part read.
 *
 * **The figure counts from its own node** (RECORD -1618, the owner: "2: within 50 m (Recommended)"; dispatch
 * 2026-10-24-10 Amendment 3, replacing Amendment 2's item 1 after Sorrel's B-395-1): the earliest node within
 * [WAY_HOME_FIGURE_REACH_METERS] of the fix **anywhere on the line**, ignoring [previous], plus the line's
 * [WayHomeLine.trailAllowanceMeters] (the way home's [WAY_HOME_TRAIL_PLACE_ALLOWANCE_METERS]; 0 for a waypoint's, RECORD
 * -1671) when that node is on the trail part. "Not before the last place" made the figure
 * short on a hairpin (one fix 30 m off leg 1, nearer leg 2, read 245 m against 420 m), and so would a 25 m reach. The
 * reach is the gate's 50 m plus the line's node step (RECORD -1620, Amendment 4), so it covers every fix the gate admits
 * and the figure is never short, as `PathHome.kt`'s ruling requires ("Its error is never short"). The owner accepted
 * that it reads about 50 to 75 m long on an ordinary trail (-1618), and up to 10 m more with the node step (-1620). No
 * hop is added to it: the reach already covers the fix's distance from the line. The one exception is a walker past the
 * line's first point (RECORD -1630): the figure then adds their distance back to it ([metersPastLineStart]).
 */
fun placeOnWayHome(line: WayHomeLine, current: LatLng, previous: WayHomePlace?, previousHopBand: HopBand = previous?.hopBand ?: HopBand.NONE): WayHomeProgress {
    val nodes = line.nodes
    if (nodes.isEmpty()) return WayHomeProgress.OffLine(Double.POSITIVE_INFINITY)
    val distances = DoubleArray(nodes.size) { GeoDistance.metersBetween(current, nodes[it]) }
    val from = previous?.index ?: 0
    fun earliest(range: IntRange, within: Double): Int? = range.firstOrNull { distances[it] <= within }
    val index = earliest(from..nodes.lastIndex, WAY_HOME_TRAIL_JOIN_METERS)
        ?: earliest(from..nodes.lastIndex, HOP_FAR_ENTER_ABOVE_METERS)
        ?: earliest(nodes.indices, WAY_HOME_TRAIL_JOIN_METERS)
        ?: earliest(nodes.indices, HOP_FAR_ENTER_ABOVE_METERS)
        ?: return WayHomeProgress.OffLine(distances.min())
    val hop = distances[index]
    val band = nextHopBand(previousHopBand, hop)
    if (band == HopBand.FAR) return WayHomeProgress.OffLine(distances.min())
    // The place is within the far band, so a node within the reach always exists here.
    val figureIndex = earliest(nodes.indices, WAY_HOME_FIGURE_REACH_METERS) ?: index
    val onTrail = line.kinds[figureIndex] == RouteLinePartKind.TRAIL
    val remaining = line.totalMeters - line.alongMeters[figureIndex] +
        (if (onTrail) line.trailAllowanceMeters else 0.0) +
        (if (figureIndex == 0) metersPastLineStart(line, current) else 0.0)
    return WayHomeProgress.Placed(WayHomePlace(index, hop, band, onTrail, remaining))
}

/**
 * How far [current] is from the line's first point when the walker is past it (dispatch 2026-10-25-06; RECORD -1630, the
 * owner: "Add it, fix first (Recommended)"), and 0 otherwise. Past means not ahead of the first point along the line's
 * first stretch: the angle between that stretch and the way to [current] is at least a right angle. A line starts at
 * the newest kept track point, written or not (since dispatch 2026-10-25-10; Sorrel's #404 review S1, branch sorrel-404,
 * 9c3866e17), so a walker who walks on after a re-route (or after Return) is beyond it. Their figure
 * counted from node 0 and held at the line's total, short of the line drawn by the distance walked on (Hazel's S22
 * check, prompts/preserved/2026-10-25-hazel-s22-figure-check.md, section 3: 27 m). The figure adds this, so it is never
 * short of the line drawn; the line, the place, the needle and the greyed part are unchanged. Only called when the
 * figure counts from node 0, which it always does for a walker past the start within the far band (node 0 is then the
 * nearest node, within 50 m, inside the 60 m reach). A walker on the first stretch, ahead of node 0, gets nothing added,
 * as before. The first stretch is taken to the first node apart from node 0, so a repeated first point cannot make the
 * stretch a zero vector, which would read every walker as past.
 */
internal fun metersPastLineStart(line: WayHomeLine, current: LatLng): Double {
    val nodes = line.nodes
    val start = nodes.firstOrNull() ?: return 0.0
    val next = nodes.firstOrNull { GeoDistance.metersBetween(start, it) > WAY_HOME_LINE_START_EPSILON_METERS } ?: return 0.0
    val perDegree = GeoDistance.metersPerDegree(start.lat)
    val stretchEast = (next.lng - start.lng) * perDegree.lng
    val stretchNorth = (next.lat - start.lat) * perDegree.lat
    val toEast = (current.lng - start.lng) * perDegree.lng
    val toNorth = (current.lat - start.lat) * perDegree.lat
    return if (stretchEast * toEast + stretchNorth * toNorth <= 0.0) GeoDistance.metersBetween(start, current) else 0.0
}

/**
 * How far [current] is ahead of the line's first point along its first stretch, toward home (dispatch 2026-10-25-09
 * Amendment 2): the fix's distance from that point, projected on the stretch, so 0 at the point and below 0 past it. The
 * first stretch is taken as [metersPastLineStart] takes it, to the first node apart from node 0; 0 when there is none.
 */
internal fun metersAheadOfLineStart(line: WayHomeLine, current: LatLng): Double {
    val nodes = line.nodes
    val start = nodes.firstOrNull() ?: return 0.0
    val next = nodes.firstOrNull { GeoDistance.metersBetween(start, it) > WAY_HOME_LINE_START_EPSILON_METERS } ?: return 0.0
    val perDegree = GeoDistance.metersPerDegree(start.lat)
    val stretchEast = (next.lng - start.lng) * perDegree.lng
    val stretchNorth = (next.lat - start.lat) * perDegree.lat
    val toEast = (current.lng - start.lng) * perDegree.lng
    val toNorth = (current.lat - start.lat) * perDegree.lat
    return (stretchEast * toEast + stretchNorth * toNorth) / hypot(stretchEast, stretchNorth)
}

/** Nodes closer than this to the line's first point are the same point, for [metersPastLineStart]. */
private const val WAY_HOME_LINE_START_EPSILON_METERS = 0.5

/**
 * Added to the distance home whenever the figure's node is on the trail part (RECORD -1613, the owner: "A, plus 25 m
 * (Recommended)"; dispatch 2026-10-24-10 Amendment 1, ruling 1). It was ruled when the figure counted from the place,
 * the earliest node within [WAY_HOME_TRAIL_JOIN_METERS] of the fix, which could be up to that far ahead of the walker.
 * Since Amendments 3 and 4 (RECORD -1618, -1620) the figure counts from the earliest node within
 * [WAY_HOME_FIGURE_REACH_METERS], which on its own keeps it never short for every fix the gate admits, so the allowance
 * is now a margin on top: the trail part is the engine's line, not the walker's own track. Neither amendment removed
 * it, so it stays as ruled. **Provisional**, as the 25 m it matches is.
 *
 * **The way home only.** The way to a waypoint does without it (dispatch 2026-10-26-11 Amendment 3, item 5; RECORD
 * -1671, the owner: "Drop it (Recommended)", Juniper's O2): its line is built with [WayHomeLine.trailAllowanceMeters] 0,
 * so its figure is the reach alone, still never short for a walker on the drawn line, and reads 25 m less on the trail,
 * where a short final leg made it read several times what was left before "Arrived" (115 m with 16 m left on the S22
 * walk's shape, 90 m without). What it gives up, from Sorrel's check (S-1): a walker beside the drawn trail, from about
 * 7 m out, can read short by about half their offset (about 10 m short at 20 m beside), which the 25 m covers to about
 * 33 m. Nothing but the HUD reads the waypoint's figure (no sundown), so that trade was the owner's to make there; the
 * way home keeps the 25 m until walk data says otherwise.
 */
const val WAY_HOME_TRAIL_PLACE_ALLOWANCE_METERS = WAY_HOME_TRAIL_JOIN_METERS

/**
 * How far from the fix the distance home's node may be (RECORD -1620, the owner: "Widen to 60 m (Recommended)"; dispatch
 * 2026-10-24-10 Amendment 4, replacing Amendment 3's 50 m): the live-fix gate's ceiling,
 * [LIVE_FIX_MAX_ACCURACY_METERS], plus the line's node step, [WAY_HOME_LINE_STEP_METERS], so 60 m, and no figure of its
 * own. A fix the gate admits is within 50 m of the walker, and the node at or behind the walker is at most one step
 * behind them, so that node is always within the reach and the earliest node within it is never past where the walker
 * is: the figure is never short for any fix the gate admits (`PathHome.kt`'s "never short" ruling). With the gate's 50 m
 * alone, measured to nodes up to 10 m apart, it could be (Sorrel's B-395-2: short by up to a node step on a track part,
 * and 265 m against 419 m at the gate's edge beside a hairpin's later leg).
 */
val WAY_HOME_FIGURE_REACH_METERS: Double = LIVE_FIX_MAX_ACCURACY_METERS.toDouble() + WAY_HOME_LINE_STEP_METERS

/**
 * The longest stretch between two nodes of a [WayHomeLine]: longer stretches (an engine route's long straight, a gap in
 * the track) are filled in, so a walker in the middle of one is still near a node. Under the self-join ε (10 m), so the
 * filling is finer than any figure the place is judged by.
 */
const val WAY_HOME_LINE_STEP_METERS = 10.0
