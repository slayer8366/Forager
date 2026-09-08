package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.Waypoint
import kotlin.math.roundToLong

/**
 * How long the walk back takes — return-estimate dispatch, the model in one line:
 *
 * ```
 * walking time = distance remaining along the walked track ÷ average moving speed
 * ```
 *
 * [pathHome] is the distance, [movingPace] the speed. **No stop allowance. Not an arrival time.**
 * This answers "how long is the walk back", not "when will I be back", and whatever surfaces it
 * later must not blur that: the estimate is systematically optimistic for someone who stops, which
 * the owner accepted on the condition that it is labelled walking time and never padded — the
 * conservatism lives in the turnaround alert's margin, which is separate work, and a padded input
 * plus a margin double-counts by an amount nobody could name. **No surface exists yet, on purpose**
 * (the dispatch it was built for: "a number on screen that nothing acts on invites the user to act
 * on it themselves"); the alert brings the surface.
 *
 * ## Degrade honestly — the constraint that governs both halves
 *
 * This number will be used by someone deciding whether they have time to keep foraging. **An
 * estimate that is confidently short is the failure mode that gets someone caught out after dark**,
 * so it must never claim precision it does not have: when the data is poor it reads **"at least X"**
 * ([Estimate.isAtLeast]), never a confident figure, and every uncertainty rounds toward *more* time,
 * not less. The same principle as the accuracy-aware distance formatter, the suppressed needle and
 * the held fix ageing into honest silence. The triggers are the owner-accepted table (pre-build
 * report, Proposal 3); one is enough and the label is the same, so they do not stack:
 *
 * | Reason | Trigger |
 * |---|---|
 * | [DegradeReason.NO_MEASURED_PACE] | the governing instrument's moving time is under the five-minute bar — the figure is from the default, slow on purpose but unmeasured |
 * | [DegradeReason.PACE_FRESHLY_MEASURED] | measured, but under fifteen minutes — the switch just moved the estimate on the day's flattest early leg |
 * | [DegradeReason.STALE_FIX] | [FixFreshness.STALE] — the hop from fix to track is unknown; the track length is not |
 * | [DegradeReason.FAR_FROM_TRACK] | the hop is in [HopBand.FAR] — entered above [HOP_FAR_ENTER_ABOVE_METERS], left below [HOP_FAR_LEAVE_BELOW_METERS]; a straight line, short by nature |
 * | [DegradeReason.PATH_UNDER_MEASURED] | the read seam excluded at least [UNDER_MEASURED_EXCLUSION_FRACTION] of the stored points — a tenth of the track excluded is a bend flattened somewhere |
 * | [DegradeReason.MOSTLY_NETWORK_FIXES] | [isMostlyNetworkFixes] — the track is chords |
 * | [DegradeReason.FEW_POINTS] | fewer than [MOSTLY_NETWORK_FIXES_MIN_STORED_POINTS] surviving points — a handful of chords whatever the mode |
 *
 * And two cases where no number is honest, so none is given ([Withheld]): a **lost** fix
 * ([FixFreshness.LOST], matching the HUD, which withholds the distance — a number with no position
 * under it is a guess, not "at least"), and **no usable points** ([hasNoUsablePoints], or no points
 * at all — there is no path to measure). No fix at all is the same as a lost one. A track with no
 * origin waypoint is **not** degraded: the path ends at its first point and nothing is omitted.
 *
 * Nothing here reads a fix's reported accuracy: on the owner's device that field is a constant
 * ([LIVE_FIX_MAX_ACCURACY_METERS]'s doc), and this estimate takes no dependency on it.
 *
 * [previousHopBand] is the last [Estimate]'s `path.hopBand` for this recording, [HopBand.NONE] at
 * the start of one — the hop's hysteresis lives in the caller's hands because this function is
 * pure; a withheld result carries no band, and the caller keeps the last one it had.
 */
fun returnWalkingTime(
    track: Track,
    origin: Waypoint?,
    current: LatLng?,
    fixFreshness: FixFreshness,
    previousHopBand: HopBand = HopBand.NONE,
): ReturnWalkingTime {
    if (current == null || fixFreshness == FixFreshness.LOST) return ReturnWalkingTime.Withheld(WithholdReason.NO_FIX)
    if (track.hasNoUsablePoints()) return ReturnWalkingTime.Withheld(WithholdReason.NO_USABLE_POINTS)
    val path = pathHome(track, current, origin, previousHopBand) ?: return ReturnWalkingTime.Withheld(WithholdReason.NO_USABLE_POINTS)
    val pace = movingPace(track.points)

    val reasons = buildSet {
        when (pace.source) {
            PaceSource.DEFAULT -> add(DegradeReason.NO_MEASURED_PACE)
            PaceSource.DOPPLER, PaceSource.DIFFERENCING ->
                if (pace.governingMovingMillis < MEASURED_PACE_SETTLED_MOVING_MILLIS) add(DegradeReason.PACE_FRESHLY_MEASURED)
        }
        if (fixFreshness == FixFreshness.STALE) add(DegradeReason.STALE_FIX)
        if (path.hopIsFar) add(DegradeReason.FAR_FROM_TRACK)
        if (track.storedPointCount > 0 && track.excludedPointCount.toDouble() / track.storedPointCount >= UNDER_MEASURED_EXCLUSION_FRACTION) {
            add(DegradeReason.PATH_UNDER_MEASURED)
        }
        if (track.isMostlyNetworkFixes()) add(DegradeReason.MOSTLY_NETWORK_FIXES)
        if (track.points.size < MOSTLY_NETWORK_FIXES_MIN_STORED_POINTS) add(DegradeReason.FEW_POINTS)
    }

    return ReturnWalkingTime.Estimate(
        path = path,
        pace = pace,
        walkingMillis = (path.totalMeters / pace.speedMetersPerSecond * 1_000.0).roundToLong(),
        degradeReasons = reasons,
    )
}

/** See [returnWalkingTime]. */
sealed interface ReturnWalkingTime {
    /**
     * A walking time — **not an arrival time** — with everything it was computed from, so a surface
     * can show its parts and a test can check the arithmetic against the inputs rather than the
     * output. [isAtLeast] is the label: the number is the same, the wording is not.
     */
    data class Estimate(
        val path: PathHome,
        val pace: MovingPace,
        /** [PathHome.totalMeters] ÷ [MovingPace.speedMetersPerSecond], in milliseconds of walking. */
        val walkingMillis: Long,
        val degradeReasons: Set<DegradeReason>,
    ) : ReturnWalkingTime {
        val isAtLeast: Boolean get() = degradeReasons.isNotEmpty()
    }

    /** No number is honest here — see [returnWalkingTime]. */
    data class Withheld(val reason: WithholdReason) : ReturnWalkingTime
}

enum class DegradeReason { NO_MEASURED_PACE, PACE_FRESHLY_MEASURED, STALE_FIX, FAR_FROM_TRACK, PATH_UNDER_MEASURED, MOSTLY_NETWORK_FIXES, FEW_POINTS }

enum class WithholdReason { NO_FIX, NO_USABLE_POINTS }

/**
 * The excluded share of stored points at which the recorded path is taken to be under-measured
 * (accepted degrade table): one excluded point among hundreds is one chord; a tenth of the track
 * excluded is a bend flattened somewhere. The fraction is the tunable; "any exclusion" is the
 * stricter alternative and also defensible.
 */
const val UNDER_MEASURED_EXCLUSION_FRACTION = 0.10
