package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.TrackPoint

/**
 * The walker's average moving speed from a track's points — return-estimate dispatch, Item 2. The
 * speed half of `walking time = distance remaining ÷ average moving speed` ([returnWalkingTime]).
 *
 * ## The problem this models, and what it deliberately does not (owner ruling)
 *
 * Foragers stop, constantly — to examine a find, photograph it, write a note; one user of this app
 * takes long exposures and is stationary for minutes at a time, and that is ordinary conduct, not
 * an edge case. So an average over elapsed time is near zero and says "you will never make it",
 * and a moving pace alone is optimistic, because they will stop on the way back too. The ruling:
 * **walking time only, moving speed only, no stop allowance, and no attempt to model how much this
 * person stops.** A user must be able to stop freely without the number changing under them; an
 * estimate that swings because someone spent ten minutes photographing is worse than one that does
 * not move. The estimate is therefore systematically optimistic for someone who stops, accepted on
 * two conditions recorded at [returnWalkingTime]: it is labelled walking time, never arrival time,
 * and the conservatism lives in the turnaround alert's margin, never padded in here.
 *
 * ## Two instruments, both first-class
 *
 * **Point differencing** — distance between consecutive stored points ÷ time between them — is a
 * working, tested path that carries real traffic, not a fallback: every track recorded before
 * schema version 15 has no stored speed, and it is the validation check on the other instrument
 * (below), so it runs on every track regardless.
 *
 * **Doppler speed** — [TrackPoint.speedMetersPerSecond], the receiver's own speed over ground from
 * carrier frequency shift, persisted since version 15. Independent of position error, which is why
 * it is the better instrument where it exists: differenced speed inherits every point's position
 * error and the sampler's own distance threshold. Confirmed populated on every GPS fix and absent
 * on every network fix on one device (`docs/audits/2026-09-07-fix-log-walk-findings.md`); one
 * device, so nothing here breaks if another populates it on network fixes or omits it on GPS ones
 * — a point with `null` speed is simply not a Doppler sample.
 *
 * ## "Moving": the interval, then the fix
 *
 * The sampler writes no points while stationary, so the gaps between stored points already contain
 * the stops. An interval between consecutive points is **moving** when its implied speed
 * (distance ÷ time) is at or above [MOVING_SPEED_FLOOR_METERS_PER_SECOND]; a ten-minute photograph
 * is one interval of a few metres over six hundred seconds and drops out. Moving time and the
 * differenced speed are summed over moving intervals only — that is what makes the estimate stable
 * across a stop, and it has a direct test.
 *
 * A Doppler sample is the speed stored at the point that *ends* a moving interval, weighted by that
 * interval's duration, and counted only when it is itself at or above the floor (the dispatch's
 * rule: a fix counts toward the average only at 0.5 m/s or above). The point after a stop carries a
 * moving speed but ends a stopped interval, so it is not a sample — its interval was the stop. This
 * is what "over the same window" means for the comparison below: the two instruments are averaged
 * over the same moving intervals, so a difference between them is a difference of instrument, not
 * of window. **Every point examined for a Doppler sample is counted** — with a speed, without one
 * (`null`), below the floor — and the counts ride on the result: a reader that silently filtered to
 * the rows that agree with it is the family CLAUDE.md names (Testing, "a check that passes because
 * it never saw the data that could fail it"), and the walk's own first analysis was an instance.
 *
 * ## Which instrument governs, and the data bar
 *
 * **Five minutes of accumulated moving time** ([MEASURED_PACE_MIN_MOVING_MILLIS]) before a measured
 * pace replaces the default — moving time past the floor, not wall clock. The bar is applied per
 * instrument: Doppler governs when the moving intervals that carry a counted Doppler sample sum to
 * the bar; otherwise differencing governs when all moving intervals do; otherwise the default. So a
 * pre-migration track, a device that never populates speed, and a track that straddles the
 * migration each land on differencing until Doppler has five minutes of its own — a judgement this
 * dispatch did not make, recorded in its completion report. The walk is a warning about the bar:
 * 4.8 minutes of wall clock gave 3.5 minutes of moving time on a brisk test walk with no foraging
 * stops, so a real trip takes considerably longer to qualify and the default is in use far more
 * often than the bar suggests. The bar does not change; it is why the default's value matters.
 *
 * ## Speed accuracy — what is not done (owner ruling)
 *
 * **No confidence interval, error bar or weighting is computed from
 * [TrackPoint.speedAccuracyMetersPerSecond].** On the walk it was a two-state flag, not a
 * measurement: 199 moving fixes at 0.72, the rest of the moving cluster 0.67–0.81, stopped fixes at
 * 0.01–0.07. Error propagation on it is unjustified — the same smell as the constant position
 * accuracy field ([LIVE_FIX_MAX_ACCURACY_METERS]'s doc). The pre-build report's bar of "under
 * about half a metre per second" per fix is retired, not failed: it was written for a per-fix
 * consumer and this model has none — it consumes one average over five minutes. The standard-error
 * argument for that average is kept only as a floor: consecutive 1 Hz Doppler fixes share a
 * receiver, a satellite geometry and a multipath environment, so their errors are correlated and
 * √N overstates the benefit by an unknown factor. **Validate against differencing instead**
 * ([MovingPace.comparison]): both instruments over the same window, reported, never gated on — if
 * they diverge materially on real tracks that is a finding worth having, and a gate would suppress
 * it. The column is persisted because a future reader may want the flag; nothing reads it here.
 *
 * ## Two things recorded so nobody "corrects" one without the other
 *
 * GPS jitter inflates a differenced path — a stationary receiver walks a few metres between fixes,
 * a moving one records slightly more path than the ground walked — which inflates the differenced
 * speed (short direction) *and* the remaining path length (long direction), partly cancelling in
 * distance ÷ speed. Doppler does not inflate, so with it the path's inflation is uncancelled and the
 * estimate leans long. Both safe or neutral. And the filter's exclusions leave gaps that look like
 * stops but are not; an excluded point inside a moving stretch merges two intervals into one longer
 * one, still moving, so the differenced speed survives — the path length does not, which is
 * [returnWalkingTime]'s under-measured degrade, not this function's concern.
 */
fun movingPace(points: List<TrackPoint>): MovingPace {
    var movingMillis = 0L
    var movingMeters = 0.0
    var intervalsMoving = 0
    var dopplerWeightedSum = 0.0 // Σ speed × interval millis, over counted samples
    var dopplerMovingMillis = 0L
    var dopplerMovingMeters = 0.0 // the differenced distance over the same intervals, for the comparison
    var pointsWithoutSpeed = 0
    var pointsBelowFloor = 0
    var pointsCounted = 0

    for (i in 1 until points.size) {
        val previous = points[i - 1]
        val point = points[i]
        val intervalMillis = point.timestampEpochMillis - previous.timestampEpochMillis
        if (intervalMillis <= 0L) continue // out-of-order or duplicate stamps: no interval to speak of
        val meters = GeoDistance.metersBetween(LatLng(previous.lat, previous.lng), LatLng(point.lat, point.lng))
        val impliedSpeed = meters / (intervalMillis / 1_000.0)
        if (impliedSpeed < MOVING_SPEED_FLOOR_METERS_PER_SECOND) continue

        intervalsMoving += 1
        movingMillis += intervalMillis
        movingMeters += meters

        val speed = point.speedMetersPerSecond
        when {
            speed == null -> pointsWithoutSpeed += 1
            speed < MOVING_SPEED_FLOOR_METERS_PER_SECOND -> pointsBelowFloor += 1
            else -> {
                pointsCounted += 1
                dopplerWeightedSum += speed.toDouble() * intervalMillis
                dopplerMovingMillis += intervalMillis
                dopplerMovingMeters += meters
            }
        }
    }

    return MovingPace(
        intervalsExamined = (points.size - 1).coerceAtLeast(0),
        intervalsMoving = intervalsMoving,
        movingMillis = movingMillis,
        movingMeters = movingMeters,
        dopplerMovingMillis = dopplerMovingMillis,
        dopplerMovingMeters = dopplerMovingMeters,
        dopplerWeightedSpeedSum = dopplerWeightedSum,
        pointsWithoutSpeed = pointsWithoutSpeed,
        pointsBelowFloor = pointsBelowFloor,
        pointsCounted = pointsCounted,
    )
}

/** See [movingPace]. Speeds in metres per second, times in milliseconds. */
data class MovingPace(
    /** Consecutive pairs looked at: one fewer than the points. */
    val intervalsExamined: Int,
    /** Of those, at or above the floor by implied speed. */
    val intervalsMoving: Int,
    /** Σ duration over moving intervals — the accumulated moving time the data bar is measured in. */
    val movingMillis: Long,
    /** Σ differenced distance over moving intervals. */
    val movingMeters: Double,
    /** Σ duration over the moving intervals that carry a counted Doppler sample. */
    val dopplerMovingMillis: Long,
    /** Σ differenced distance over those same intervals — the comparison's other side. */
    val dopplerMovingMeters: Double,
    /** Σ Doppler speed × interval duration over counted samples. */
    val dopplerWeightedSpeedSum: Double,
    /** Points ending a moving interval with no stored speed (pre-migration rows, a device that reports none). */
    val pointsWithoutSpeed: Int,
    /** Points ending a moving interval whose stored speed is below the floor. */
    val pointsBelowFloor: Int,
    /** Points whose stored speed was counted. Examined = counted + without speed + below floor = [intervalsMoving]. */
    val pointsCounted: Int,
) {
    /** Differenced average over all moving intervals; `null` with no moving interval. */
    val differencingSpeed: Double? get() = if (movingMillis > 0L) movingMeters / (movingMillis / 1_000.0) else null

    /** Duration-weighted Doppler average over counted samples; `null` with none. */
    val dopplerSpeed: Double? get() = if (dopplerMovingMillis > 0L) dopplerWeightedSpeedSum / dopplerMovingMillis else null

    /**
     * Which instrument the bar admits — see [movingPace], "Which instrument governs". Safe only
     * while [MEASURED_PACE_MIN_MOVING_MILLIS] is positive: the reverted-variant check that zeroed
     * the bar made `dopplerMovingMillis >= 0` true for a track with no Doppler sample at all, and
     * [speedMetersPerSecond] then threw on [dopplerSpeed]. A zero bar is not a value anyone would
     * set, so no guard — but whoever tunes the bar should know the edge is there.
     */
    val source: PaceSource get() = when {
        dopplerMovingMillis >= MEASURED_PACE_MIN_MOVING_MILLIS -> PaceSource.DOPPLER
        movingMillis >= MEASURED_PACE_MIN_MOVING_MILLIS -> PaceSource.DIFFERENCING
        else -> PaceSource.DEFAULT
    }

    /** The moving time behind [speedMetersPerSecond]: the governing instrument's own. */
    val governingMovingMillis: Long get() = when (source) {
        PaceSource.DOPPLER -> dopplerMovingMillis
        PaceSource.DIFFERENCING -> movingMillis
        PaceSource.DEFAULT -> 0L
    }

    /** The pace the estimate divides by. */
    val speedMetersPerSecond: Double get() = when (source) {
        PaceSource.DOPPLER -> dopplerSpeed!!
        PaceSource.DIFFERENCING -> differencingSpeed!!
        PaceSource.DEFAULT -> DEFAULT_MOVING_SPEED_METERS_PER_SECOND
    }

    /**
     * Both instruments over the same moving intervals — the ones that carry a counted Doppler
     * sample — reported, never gated on. `null` until there is at least one such interval.
     */
    val comparison: PaceComparison? get() =
        if (dopplerMovingMillis > 0L) {
            PaceComparison(
                dopplerSpeed = dopplerWeightedSpeedSum / dopplerMovingMillis,
                differencingSpeed = dopplerMovingMeters / (dopplerMovingMillis / 1_000.0),
                intervals = pointsCounted,
                movingMillis = dopplerMovingMillis,
            )
        } else {
            null
        }
}

enum class PaceSource { DEFAULT, DIFFERENCING, DOPPLER }

/** See [MovingPace.comparison]. */
data class PaceComparison(
    val dopplerSpeed: Double,
    val differencingSpeed: Double,
    val intervals: Int,
    val movingMillis: Long,
) {
    /** Doppler ÷ differencing: 1.0 is agreement; below it Doppler reads slower than the positions moved. */
    val ratio: Double get() = dopplerSpeed / differencingSpeed
}

/**
 * A fix, or an interval between stored points, counts as moving only at this speed or above.
 *
 * **0.5 m/s, the middle of the 0.3–0.7 m/s band** the pre-build report established from the
 * sampler's own constants (a HIGH_ACCURACY point is at least 5 m and 5 s from the last, so a
 * stationary walker's jitter implies well under 0.3 m/s; a slow forager on rough ground walks
 * above 0.7) — and **the band is measured to be insensitive**: on the owner's walk of 2026-09-07
 * (289 GPS fixes at 1 Hz) floors of 0.3, 0.5 and 0.7 m/s kept 217, 211 and 194 fixes with the
 * kept average moving by 0.01 m/s per step (0.88, 0.89, 0.91 m/s), because the speed histogram is
 * bimodal — a cluster at 0.0, a cluster around 1.0 m/s, and the floor sits in the trough between.
 * The choice inside the band does not matter, which is the best outcome for a constant argued
 * before it was measured. **1.0 m/s is the boundary of the safe range**: it kept 40 of 289 and
 * discarded 86 % of a real walk. One walk, one walker. The platform also zeroes speed and drops
 * bearing together when stopped (69 of 69 fixes on that walk), a free stop signal — not used here,
 * because the floor also has to catch slow drift, which that does not.
 * Record: `docs/audits/2026-09-07-fix-log-walk-findings.md`.
 */
const val MOVING_SPEED_FLOOR_METERS_PER_SECOND = 0.5

/**
 * The pace used until the data bar is met: **2 mph, stored as 3.2 km/h** — the imperial figure is
 * the source of the rounding, since this app's users are US foragers and a round number where the
 * user lives is worth an unround one in storage.
 *
 * **It is deliberately slower than an average adult walking pace, and that is intentional — not a
 * typo for a walking speed, and not to be "corrected" upward.** This feeds a safety estimate. A
 * default faster than the user's real pace makes the estimate short, in the one direction that gets
 * someone caught out after dark. Rough ground under trees is slower than pavement, and the people
 * who walk more slowly than average are often the ones who most need the warning. The default
 * governs only the first minutes of a trip, when nobody is near their turnaround — but see the bar's
 * note in [movingPace]: at a forager's stop ratio those minutes are more of the trip than they look.
 *
 * Chosen from foraging behaviour before any data existed. **The first instrumented walk
 * (2026-09-07, owner's device) measured 0.890 m/s = 1.99 mph** average speed over the 211 fixes at
 * or above the floor — an agreement, not yet a calibration: one walk, one walker, and a brisk test
 * walk rather than a foraging one. Record: `docs/audits/2026-09-07-fix-log-walk-findings.md`.
 */
const val DEFAULT_MOVING_SPEED_KM_PER_HOUR = 3.2

/** [DEFAULT_MOVING_SPEED_KM_PER_HOUR] in the unit the arithmetic runs in: 3.2 / 3.6 = 0.888… m/s. */
const val DEFAULT_MOVING_SPEED_METERS_PER_SECOND = DEFAULT_MOVING_SPEED_KM_PER_HOUR / 3.6

/**
 * Accumulated moving time before a measured pace replaces [DEFAULT_MOVING_SPEED_METERS_PER_SECOND]
 * — **five minutes**, the owner's instinct, kept after the pre-build report's reasoning per
 * recording mode: ~60 kept intervals in HIGH_ACCURACY, ~20 in BALANCED, ~5 in BATTERY_SAVER, the
 * last being the minimum that gives the differenced average five steps long enough that position
 * error is a small fraction of each. One bar for every mode and both instruments, rather than a
 * table the user cannot see. Below it the default is doing the honest job; at the switch the
 * estimate can move by ~30 % in the short direction if the measured pace is faster, which is why
 * the switch must not happen on thin data and why [MEASURED_PACE_SETTLED_MOVING_MILLIS] exists.
 */
const val MEASURED_PACE_MIN_MOVING_MILLIS = 5L * 60L * 1_000L

/**
 * Moving time after which a measured pace is no longer "fresh" — **fifteen minutes**, three times
 * the bar (accepted degrade table, pre-build report Proposal 3): the switch to a measured pace just
 * moved the estimate, possibly by ~30 % in the short direction, on the day's flattest early leg, and
 * three bars is enough walking to include some variation of ground. Until then the estimate reads
 * "at least".
 */
const val MEASURED_PACE_SETTLED_MOVING_MILLIS = 15L * 60L * 1_000L
