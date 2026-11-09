package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.wayhome.Ground.at

/** Way homes built straight from points on [Ground], for the part 2 tests (dispatch 2026-10-24-10 Amendment 1, PR B). */
internal object WayHomeLines {
    fun part(kind: WayHomePartKind, vararg eastNorth: Pair<Double, Double>, meters: Double? = null): WayHomePart {
        val points = eastNorth.map { (e, n) -> at(e, n) }
        return WayHomePart(kind, points, meters ?: points.zipWithNext { a, b -> GeoDistance.metersBetween(a, b) }.sum())
    }

    fun wayHome(trackBack: WayHomePart, trail: WayHomePart, trackToStart: WayHomePart) =
        WayHome(trackBack, trail, trackToStart, trailJointMeters = 0.0, regionId = 1)

    /**
     * A hairpin: the walker's track 50 m north onto the trail at (0, 0); the trail east 200 m, north 20 m, back west
     * 200 m to (0, 20); then the walker's own track 100 m north to the start. 570 m in all.
     */
    val hairpin = wayHome(
        part(WayHomePartKind.TRACK_BACK, 0.0 to -50.0, 0.0 to 0.0),
        part(WayHomePartKind.TRAIL, 0.0 to 0.0, 200.0 to 0.0, 200.0 to 20.0, 0.0 to 20.0),
        part(WayHomePartKind.TRACK_TO_START, 0.0 to 20.0, 0.0 to 120.0),
    )
}
