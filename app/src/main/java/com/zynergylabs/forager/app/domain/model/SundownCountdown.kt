package com.zynergylabs.forager.app.domain.model

/**
 * What the recording screen and the turnaround alert know about the end of the day.
 *
 * Every case is explicit. There is no "unknown" that renders as a blank or as a zero, because the
 * whole point of this feature is that someone is deciding whether to keep foraging, and a blank
 * where a time should be reads as "fine" to a person glancing at a screen in failing light.
 *
 * ## What this deliberately does not say
 *
 * Nothing here answers "do I have enough time". It cannot: the walk back is not estimated yet
 * ([com.zynergylabs.forager.app.domain.returnWalkingTime] has no production caller, on purpose),
 * and even with one, terrain and light are not the same question. Whatever renders this must not
 * turn a countdown into a reassurance.
 */
sealed interface SundownCountdown {

    /**
     * Sunset is known for this position.
     *
     * [turnaroundAtEpochMillis] is sunset minus the user's darkness margin, and it is the moment
     * the first alert fires. The margin exists because **sunset is not when the light runs out**:
     * under canopy or west of a ridge, useful light can end half an hour or more earlier. The
     * margin is named for darkness rather than for "lead time" on the owner's ruling, so that the
     * separate allowance for a walk back can be added later without the two silently merging into
     * one number that double-counts.
     *
     * [civilDuskAtEpochMillis] is the second marker: the sun at −6°, the standard civil-twilight
     * boundary and the point at which the horizon stops being discernible. It is nullable because
     * a place can see the sun set and never reach −6° that day, which is ordinary at high summer
     * latitudes and not an error.
     *
     * [fixAgeMillis] is how old the position this was computed from is. It is carried rather than
     * hidden because a countdown derived from an hour-old fix is a different claim from one
     * derived from a fresh one, and the app's existing posture (`LiveFixGate`'s held fix ageing
     * into "Last fix 45 s ago") is to say so rather than to quietly present stale data as current.
     */
    data class Known(
        val nowEpochMillis: Long,
        val sunsetAtEpochMillis: Long,
        val civilDuskAtEpochMillis: Long?,
        val turnaroundAtEpochMillis: Long,
        val fixAgeMillis: Long,
    ) : SundownCountdown {

        /** Negative once the turnaround moment has passed. Callers render the sign, not an absolute. */
        val millisUntilTurnaround: Long get() = turnaroundAtEpochMillis - nowEpochMillis

        /** Negative once the sun has set. */
        val millisUntilSunset: Long get() = sunsetAtEpochMillis - nowEpochMillis

        val isPastTurnaround: Boolean get() = millisUntilTurnaround <= 0L

        val isPastSunset: Boolean get() = millisUntilSunset <= 0L
    }

    /**
     * The sun does not set at this position within the next day: polar day. Distinguished from
     * [SunStaysDown] by the sun being above the sunset threshold right now.
     *
     * Reported as its own state rather than as a very large number, because "the sun is not going
     * to set" and "the sun sets in 19 hours" are different things to tell someone.
     */
    data object SunDoesNotSet : SundownCountdown

    /**
     * The sun is below the sunset threshold and does not come back up to cross it within the next
     * day: polar night. There is no turnaround to count down to because the light has already gone.
     */
    data object SunStaysDown : SundownCountdown

    /**
     * No position has been fixed yet, so there is nothing to compute against. Explicit rather than
     * a null, so that a screen has to decide what to show and cannot accidentally show nothing.
     */
    data object NoPositionYet : SundownCountdown
}
