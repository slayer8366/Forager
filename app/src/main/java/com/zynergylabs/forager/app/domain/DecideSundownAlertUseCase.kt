package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.SundownCountdown

/**
 * The three moments a recording alerts on (dispatch 2026-09-28-516, RECORD -515): [HEADS_UP]
 * [SUNDOWN_HEADS_UP_LEAD_MILLIS] before the leave-by time, [LEAVE_BY] at it, and [SUNSET].
 */
enum class SundownAlert { HEADS_UP, LEAVE_BY, SUNSET }

/**
 * How long before the leave-by time the heads-up sounds. Fixed, not a setting (the owner, RECORD
 * -515).
 */
const val SUNDOWN_HEADS_UP_LEAD_MILLIS: Long = 30L * 60L * 1_000L

/**
 * What this decision leaves behind. [fire] is the one alert to deliver now, or `null`. [spent] is
 * every alert that must never fire again for this recording, which is **not** the same set as
 * "the one we just fired": see [DecideSundownAlertUseCase] for why a passed moment is spent even
 * when it was never delivered. [leaveByAtEpochMillis] is the leave-by time this decision was made
 * against, `null` when there was none to compute.
 */
data class SundownAlertDecision(
    val fire: SundownAlert?,
    val spent: Set<SundownAlert>,
    val leaveByAtEpochMillis: Long? = null,
)

/**
 * Decides whether a recording should alert, given where the day is, how long the walk back is,
 * and what has already fired.
 *
 * Pure and stateless: the caller owns the memory of what has fired ([SundownWatch]).
 *
 * ## The leave-by time
 *
 * Sunset, minus the darkness margin ([SundownCountdown.Known.turnaroundAtEpochMillis] is sunset
 * minus the margin), minus the walk back. With the walk back unknown (`null`), it falls back to
 * sunset minus the margin: the dispatch's fallback, and never a confident figure the estimate
 * does not have. It moves earlier as the walk back grows, which is why it is recomputed on every
 * call rather than fixed at the start.
 *
 * ## Edge-triggered, once each
 *
 * All three are moments, not conditions. Passing [SundownAlertDecision.spent] back in is what
 * makes each fire once for a recording.
 *
 * ## A later moment supersedes earlier ones that were never delivered
 *
 * If more than one is already behind (a recording started late, or a walk back that grew past
 * now), only the latest fires and the earlier ones are marked spent without sounding. Two alarms
 * in the same second is noise, and the later one is the truer.
 *
 * ## What does not alert
 *
 * [SundownCountdown.NoPositionYet] has no place to compute against.
 * [SundownCountdown.SunDoesNotSet] has no sunset to be late for. [SundownCountdown.SunStaysDown]
 * is polar night: the light did not go while they were out, it was never there.
 */
class DecideSundownAlertUseCase {

    operator fun invoke(
        countdown: SundownCountdown,
        walkBackMillis: Long?,
        alreadyFired: Set<SundownAlert>,
    ): SundownAlertDecision {
        if (countdown !is SundownCountdown.Known) {
            return SundownAlertDecision(fire = null, spent = alreadyFired)
        }

        val leaveBy = sundownLeaveByAt(countdown.turnaroundAtEpochMillis, walkBackMillis)
        val now = countdown.nowEpochMillis
        val due = buildSet {
            if (now >= leaveBy - SUNDOWN_HEADS_UP_LEAD_MILLIS) add(SundownAlert.HEADS_UP)
            if (now >= leaveBy) add(SundownAlert.LEAVE_BY)
            if (countdown.isPastSunset) add(SundownAlert.SUNSET)
        }
        val spent = alreadyFired + due
        val undelivered = due - alreadyFired

        val fire = when {
            SundownAlert.SUNSET in undelivered -> SundownAlert.SUNSET
            SundownAlert.LEAVE_BY in undelivered -> SundownAlert.LEAVE_BY
            SundownAlert.HEADS_UP in undelivered -> SundownAlert.HEADS_UP
            else -> null
        }
        return SundownAlertDecision(fire = fire, spent = spent, leaveByAtEpochMillis = leaveBy)
    }
}
