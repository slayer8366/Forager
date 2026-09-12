package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.SundownCountdown

/** The two moments a recording alerts on. */
enum class SundownAlert { TURNAROUND, SUNSET }

/**
 * What this decision leaves behind. [fire] is the one alert to deliver now, or `null`. [spent] is
 * every alert that must never fire again for this recording, which is **not** the same set as
 * "the one we just fired": see [DecideSundownAlertUseCase] for why a passed turnaround is spent
 * even when it was never delivered.
 */
data class SundownAlertDecision(
    val fire: SundownAlert?,
    val spent: Set<SundownAlert>,
)

/**
 * Decides whether a recording should alert, given where the day is and what has already fired.
 *
 * Pure and stateless: the caller owns the memory of what has fired, which is what lets this same
 * decision be driven from wherever alert ownership lives without the logic moving with it.
 *
 * ## Edge-triggered, once each
 *
 * Both alerts are moments, not conditions. Once the turnaround has passed it stays passed, so a
 * condition-shaped check would re-fire on every evaluation. Passing [SundownAlertDecision.spent]
 * back in is what makes each fire once for a recording.
 *
 * ## Sunset supersedes a turnaround that was never delivered
 *
 * If both are already behind — a recording started after sunset, or a margin wider than the
 * remaining day — only [SundownAlert.SUNSET] fires, and the turnaround is marked spent without
 * ever being delivered. Two alarms in the same second is noise, and of the two, "turn back now" is
 * the one that has stopped being true: telling someone to start heading back when the sun has
 * already gone misstates how much light they have. The later, truer alert wins.
 *
 * ## What does not alert
 *
 * [SundownCountdown.NoPositionYet] has no place to compute against.
 * [SundownCountdown.SunDoesNotSet] has no sunset to be late for. [SundownCountdown.SunStaysDown]
 * is polar night: the light did not go while they were out, it was never there, and an alarm
 * would be telling them something they have known all day.
 */
class DecideSundownAlertUseCase {

    operator fun invoke(
        countdown: SundownCountdown,
        alreadyFired: Set<SundownAlert>,
    ): SundownAlertDecision {
        if (countdown !is SundownCountdown.Known) {
            return SundownAlertDecision(fire = null, spent = alreadyFired)
        }

        val due = buildSet {
            if (countdown.isPastTurnaround) add(SundownAlert.TURNAROUND)
            if (countdown.isPastSunset) add(SundownAlert.SUNSET)
        }
        val spent = alreadyFired + due
        val undelivered = due - alreadyFired

        val fire = when {
            SundownAlert.SUNSET in undelivered -> SundownAlert.SUNSET
            SundownAlert.TURNAROUND in undelivered -> SundownAlert.TURNAROUND
            else -> null
        }
        return SundownAlertDecision(fire = fire, spent = spent)
    }
}
