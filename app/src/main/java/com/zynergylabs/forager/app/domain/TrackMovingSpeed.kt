package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint

/**
 * A walk's moving speed for the track sheet's "Moving speed" tile (dispatch 2026-09-28-677, Amendment 1, RECORD -680; the
 * owner: "Moving speed (Recommended)", the app's existing moving pace, not distance over first-to-last time). It is
 * [movingPace]'s own figure, the one the walk-back estimate divides by, so a stop to photograph a find does not drag it down.
 *
 * One difference from the estimate, deliberately: where [movingPace] has too little moving time to measure
 * ([PaceSource.DEFAULT], under [MEASURED_PACE_MIN_MOVING_MILLIS]), the estimate assumes the default walking pace; a record of
 * a walk must not show an assumed pace as if measured (CLAUDE.md: no fabricated plausible value), so this is `null` there
 * and the sheet shows a dash.
 */
fun trackMovingSpeedMetersPerSecond(points: List<TrackPoint>): Double? {
    val pace = movingPace(points)
    return if (pace.source == PaceSource.DEFAULT) null else pace.speedMetersPerSecond
}
