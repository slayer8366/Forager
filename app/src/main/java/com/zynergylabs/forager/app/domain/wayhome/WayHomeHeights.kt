package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.WayBackClimb
import com.zynergylabs.forager.app.domain.bankedClimb
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.TrackPoint

/**
 * The heights along the way home shown, for the walk back's climbing allowance (dispatch 2026-10-26-19 Amendment 1;
 * RECORD -1689, C1, the owner: "(b) Your track as a stand-in (Recommended)").
 *
 * - **The two track parts** carry their own stored points' barometric heights, kept through
 *   [FindWayHomeUseCase]'s track parts rather than dropped with the positions.
 * - **The trail part has no heights of its own.** The route files carry no elevation, the routing adapter drops the
 *   engine's per-point elevation (`routing/BRouterTrailRouter.kt`, where it maps the engine's nodes to positions) and
 *   [com.zynergylabs.forager.app.domain.routing.TrailRoute] has no field for one. **So its heights are a stand-in:** the
 *   walker's own track between the same two trail points, the stretch the length check already measures, laid along
 *   the trail part in proportion to length. Its net rise is the same; it can read short where the trail crosses a rise
 *   the walker's track went round. It stays a stand-in until the trail routes carry elevation; when they do, the
 *   adapter and `TrailRoute` have to carry it too (the note for that work, RECORD -1689).
 * - **No barometer, no allowance** (C3): every part is [PartHeights.NoBarometer] and so is the line.
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
    if (heights == null || heights.size >= 0) return PartHeights.Barometric(emptyList()) // STUB (fail first)
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

/** The way home's heights in line metres ([WayHomeLine.alongMeters]), or why there are none. */
sealed interface LineHeights {
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
        val heights = part.heights as? PartHeights.Barometric ?: return null
        heights.samples.forEach { samples += PartHeight(start + it.alongMeters, it.height) }
    }
    return LineHeights.Barometric(samples)
}

/**
 * The climb and descent from [alongMeters] on the line to its end: banked from the last height at or before
 * [alongMeters] (the first, when none is), so the climb starts no later than the distance does.
 */
internal fun climbFrom(heights: LineHeights?, alongMeters: Double): WayBackClimb? = when (heights) {
    is LineHeights -> null // STUB (fail first)
    null -> null
    LineHeights.NoBarometer -> WayBackClimb.NoBarometer
    is LineHeights.Barometric -> {
        val start = heights.samples.indexOfLast { it.alongMeters <= alongMeters }.coerceAtLeast(0)
        bankedClimb(heights.samples.drop(start).map { it.height })
    }
}

private fun TrackPoint.latLng() = LatLng(lat, lng)
