package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.WayBackClimb
import com.zynergylabs.forager.app.domain.bankedClimb
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.routing.TrailRoute

/**
 * The heights along the way home shown, for the walk back's climbing allowance (dispatch 2026-10-26-19 Amendment 1;
 * RECORD -1689; the trail part's from the trail data since dispatch 2026-10-30-06, RECORD -1836, -1838).
 *
 * - **The two track parts** carry their own stored points' barometric heights, kept through
 *   [FindWayHomeUseCase]'s track parts rather than dropped with the positions.
 * - **The trail part carries the trail data's heights** ([PartHeights.TrailData], [trailDataHeights]) when every point of
 *   the engine's route has one. The route squares carry 3-inch heights since the route build of 2026-10-28 (RECORD
 *   -1835); the routing adapter keeps them ([com.zynergylabs.forager.app.domain.routing.TrailRoute.heightsMetres]).
 *   - **The two joints carry no height** (Amendment 1, J1, the owner: "Don't count the step (Recommended)"). The trail
 *     data's heights are the terrain model's and the track's are the barometer's, anchored to GPS; the two can sit metres
 *     apart, and the line is banked as one run ([climbFrom]), so a height at each joint would count that offset as a
 *     climb at one end and a descent at the other. A missing height resets the banking ([bankedClimb]), so the step is
 *     never counted, and the short straight joints count flat.
 * - **Otherwise the trail part's heights are a stand-in** ([standInHeights]): the walker's own track between the same
 *   two trail points, the stretch the length check already measures, laid along the trail part in proportion to length
 *   (RECORD -1689, C1: "(b) Your track as a stand-in (Recommended)"). Its net rise is the same; it can read short where
 *   the trail crosses a rise the walker's track went round. That is a route on data with no heights (downloaded before
 *   2026-10-28, or one of the five squares built without them), and a route with only some points heighted (Amendment 1,
 *   P1, the owner: "Use your track instead (Recommended)"). [FindWayHomeUseCase] logs it once per call, with the counts.
 * - **No barometer, no allowance** (C3; Amendment 1, the owner: "Keep it off (Recommended)"): every part is
 *   [PartHeights.NoBarometer] and so is the line, the trail part too, whatever heights the trail data has.
 *
 * The climb from the walker's place is banked from the last height at or before the figure's own node, so it never
 * leaves out climb the distance counts ([climbFrom]).
 */
sealed interface PartHeights {
    /**
     * Barometric heights at [samples], in walking order. [standIn] is true for the trail part, whose heights are the
     * walker's own track's between the same two trail points (see the file header).
     */
    data class Barometric(val samples: List<PartHeight>, val standIn: Boolean = false) : PartHeights

    /**
     * The trail data's heights at [samples], in walking order: the trail part's when every route point has one, with no
     * height at the two joints (see the file header).
     */
    data class TrailData(val samples: List<PartHeight>) : PartHeights

    /** The track's heights are not barometric (RECORD -1689, C3). */
    data object NoBarometer : PartHeights
}

/** One height on a part: [alongMeters] from the part's start, in the part's own metres ([WayHomePart.meters]). */
data class PartHeight(val alongMeters: Double, val height: Double?)

/**
 * The heights of the stored points [indices] names, in that (walking) order, each at its distance along them; or
 * [PartHeights.NoBarometer] when [heights], the whole track's barometric heights, is `null`.
 */
internal fun trackPartHeights(stored: List<TrackPoint>, indices: List<Int>, heights: List<Double?>?): PartHeights {
    if (heights == null) return PartHeights.NoBarometer
    var along = 0.0
    return PartHeights.Barometric(
        indices.mapIndexed { k, i ->
            if (k > 0) along += GeoDistance.metersBetween(stored[indices[k - 1]].latLng(), stored[i].latLng())
            PartHeight(along, heights[i])
        },
    )
}

/**
 * The trail part's heights: [track]'s, the walker's own track between the same two trail points ([trackMeters] long),
 * laid along the trail part's [trailMeters] in proportion. See the file header.
 */
internal fun standInHeights(track: PartHeights?, trackMeters: Double, trailMeters: Double): PartHeights? = when (track) {
    null -> null
    PartHeights.NoBarometer -> PartHeights.NoBarometer
    is PartHeights.Barometric -> {
        val scale = if (trackMeters > 0.0) trailMeters / trackMeters else 0.0
        PartHeights.Barometric(track.samples.map { it.copy(alongMeters = it.alongMeters * scale) }, standIn = true)
    }
}

/**
 * The trail part's heights from the trail data: [trailPoints] are the leave point, [route]'s points, then the entry
 * point, and the part is [trailMeters] long. `null` unless every one of [route]'s points has a height (Amendment 1, P1).
 * The joints carry no height (J1). A route point sits at the leave joint's length plus its distance along the route,
 * scaled so the route's own stretch is the engine's [TrailRoute.lengthMetres]; the entry point sits at [trailMeters].
 */
internal fun trailDataHeights(trailPoints: List<LatLng>, route: TrailRoute, trailMeters: Double): PartHeights.TrailData? = null // STUB

/** The way home's heights in line metres ([WayHomeLine.alongMeters]), or why there are none. */
sealed interface LineHeights {
    /** The line's heights: the track parts' barometric ones, and the trail part's from the trail data or the stand-in. */
    data class Barometric(val samples: List<PartHeight>) : LineHeights

    data object NoBarometer : LineHeights
}

/**
 * The line's heights from its [parts], each with the line metres it starts at. [LineHeights.NoBarometer] when any part
 * says so; `null` when any part carries none (the way to a waypoint's straight legs: nothing reads its time).
 */
internal fun lineHeightsOf(parts: List<Pair<WayHomePart, Double>>): LineHeights? {
    if (parts.any { it.first.heights == PartHeights.NoBarometer }) return LineHeights.NoBarometer
    val samples = ArrayList<PartHeight>()
    for ((part, start) in parts) {
        val partSamples = when (val heights = part.heights) {
            is PartHeights.Barometric -> heights.samples
            is PartHeights.TrailData -> heights.samples
            PartHeights.NoBarometer, null -> return null
        }
        partSamples.forEach { samples += PartHeight(start + it.alongMeters, it.height) }
    }
    return LineHeights.Barometric(samples)
}

/**
 * The climb and descent from [alongMeters] on the line to its end: banked from the last height at or before
 * [alongMeters] (the first, when none is), so the climb starts no later than the distance does.
 */
internal fun climbFrom(heights: LineHeights?, alongMeters: Double): WayBackClimb? = when (heights) {
    null -> null
    LineHeights.NoBarometer -> WayBackClimb.NoBarometer
    is LineHeights.Barometric -> {
        val start = heights.samples.indexOfLast { it.alongMeters <= alongMeters }.coerceAtLeast(0)
        bankedClimb(heights.samples.drop(start).map { it.height })
    }
}

private fun TrackPoint.latLng() = LatLng(lat, lng)
