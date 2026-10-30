package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The climbing allowance in the walk back's time (dispatch 2026-10-26-19 Amendment 1; RECORD -1687, -1689). The owner,
 * -1687: "(a) ~2.2 s per metre (Recommended)"; -1689: "Add it as ruled (Recommended)". So the walking time is the
 * pace-based time ([returnWalkingTime]) plus [CLIMB_ALLOWANCE_SECONDS_PER_METER] for every metre climbed **and** every
 * metre descended on the way back.
 *
 * ## The figure
 *
 * From Juniper's fit on the owner's 2026-10-24 walk (`docs/navigation/2026-10-26-hilly-walk-climbs.md`, branch
 * s26-hilly-walk-report, section 3): 2.23 s per metre climbed (90 % 1.27 to 3.20) and 3.19 s per metre descended (90 %
 * 2.15 to 4.20). The owner chose one figure for both, so for descents it sits near the bottom of that walk's interval.
 * One outing, one walker, modest relief: a bound, not a calibration.
 *
 * ## It is not a pad
 *
 * [returnWalkingTime]'s header records the owner's earlier condition that the walk-back time is "never padded", and the
 * conservatism lives in the darkness margin. This is a modelled term the owner ruled on (-1687), the time a climb or a
 * descent costs on the way back, not a margin for safety.
 *
 * **Known double count, accepted (-1689, C2 "Add it as ruled"):** the pace ([movingPace]) is measured over the walk so
 * far, hills included, so on a way back that retraces a hilly way out part of this climb is already in the pace. It
 * errs long. Correcting one half without the other would make the figure read short; neither is changed alone.
 *
 * ## Barometric heights only (-1689, C3 "No allowance there (Recommended)")
 *
 * The climb is counted only when the track's heights come from the barometer ([isBarometricTrack], the rule
 * [trackHeights] uses). Smoothed GPS heights over-read a walk's climb two to five times on the 10-24 walk (Juniper,
 * section 3: 106 to 168 m against a 70 m hill), which at 2.2 s per metre moves the sundown alerts by minutes of noise.
 * So on a phone or a track without the barometer the allowance is zero, [WayBackClimb.NoBarometer], and
 * `SundownWatch` logs that once per recording.
 *
 * ## How the climb is counted
 *
 * The heights are [barometricHeights] over the whole track (the same heights the journal's Climb reads, with dispatch
 * -18's bridging of a missed reading), taken in walking order along the way back, and banked through the journal's
 * [ElevationHysteresis.THRESHOLD_METERS] exactly as [ComputeTrackStatisticsUseCase] banks Climb and Descent
 * ([bankedClimb]). On a way home along trails the trail part's are the trail data's where every route point has one
 * (dispatch 2026-10-30-06, `wayhome/WayHomeHeights.kt`). A straight leg with no stored heights (the hop to the walker's
 * fix, the leg to the origin waypoint, the straight joints at a trail's ends where the trail data's heights are used)
 * is counted flat.
 */
const val CLIMB_ALLOWANCE_SECONDS_PER_METER = 2.2

/** The climb and descent along a way back, or why it was not counted. See [CLIMB_ALLOWANCE_SECONDS_PER_METER]. */
sealed interface WayBackClimb {
    /** Metres climbed and descended along the way back, from barometric heights, banked through the hysteresis. */
    data class Measured(val climbMeters: Double, val descentMeters: Double) : WayBackClimb {
        val totalMeters: Double get() = climbMeters + descentMeters
    }

    /** The track's heights are not barometric: no allowance (-1689, C3). */
    data object NoBarometer : WayBackClimb
}

/** The allowance for [climb], in milliseconds: zero when it is [WayBackClimb.NoBarometer] or not measured (`null`). */
fun climbAllowanceMillis(climb: WayBackClimb?): Long = when (climb) {
    is WayBackClimb.Measured -> (climb.totalMeters * CLIMB_ALLOWANCE_SECONDS_PER_METER * 1_000.0).roundToLong()
    WayBackClimb.NoBarometer, null -> 0L
}

/** Whether [points]' heights are barometric: [trackHeights]' rule, at least half the points carry a pressure. */
fun isBarometricTrack(points: List<TrackPoint>): Boolean {
    val withPressure = points.count { it.pressureHpa != null }
    return withPressure > 0 && withPressure * 2 >= points.size
}

/** One barometric height per stored point, as [trackHeights] gives them, or `null` when the track is not barometric. */
fun barometricTrackHeightsOrNull(points: List<TrackPoint>): List<Double?>? =
    if (isBarometricTrack(points)) barometricHeights(points) else null

/**
 * The climb and descent along the stored points [route] names (indices into [points], in walking order), from the
 * whole track's barometric heights; [WayBackClimb.NoBarometer] when the track is not barometric.
 */
fun climbAlongRoute(points: List<TrackPoint>, route: List<Int>): WayBackClimb {
    val heights = barometricTrackHeightsOrNull(points) ?: return WayBackClimb.NoBarometer
    return bankedClimb(route.map { heights[it] })
}

/**
 * Climb and descent over [heights] in walking order, banked as [ComputeTrackStatisticsUseCase] banks the journal's
 * Climb and Descent: a change banks once it reaches [ElevationHysteresis.THRESHOLD_METERS] from the last banked height
 * (or from the run's first), and a missing height resets the reference, so nothing is bridged across it. Kept as its
 * own function, not a call into the use case, because that one also sums distance in the same loop; the two are the
 * same rule and a change to one is a change to both.
 */
fun bankedClimb(heights: List<Double?>): WayBackClimb.Measured {
    var climb = 0.0
    var descent = 0.0
    var reference: Double? = null
    for (i in 1 until heights.size) {
        val previous = heights[i - 1]
        val current = heights[i]
        if (previous == null || current == null) {
            reference = null
            continue
        }
        val from = reference ?: previous
        val delta = current - from
        if (abs(delta) >= ElevationHysteresis.THRESHOLD_METERS) {
            if (delta > 0) climb += delta else descent += -delta
            reference = current
        } else {
            reference = from
        }
    }
    return WayBackClimb.Measured(climb, descent)
}
