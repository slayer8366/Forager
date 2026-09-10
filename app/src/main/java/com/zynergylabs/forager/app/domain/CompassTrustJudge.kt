package com.zynergylabs.forager.app.domain

/**
 * Decides, reading by reading, whether the compass can be trusted — compass-reliability dispatch.
 * Pure domain: no sensors, no clock of its own (the reading's own monotonic timestamp is the
 * clock), so the thresholds are pinned-literal tests, not device observations.
 *
 * ## The threshold, and why 15°
 *
 * The needle points a walker at a target and the HUD claims that to the degree. A walker following
 * a needle with heading error *e* for distance *d* ends *d*·sin(*e*) off the true line: over 100 m,
 * 15° is 26 m — outside the position's own error circle as the approach logic defines it (twice the
 * fix accuracy, typically 16–25 m). Past that the needle is less trustworthy than the fix it is drawn
 * from, which is the point at which drawing it confidently is a lie. Below it the miss stays inside
 * the band the app already calls "Approaching". Owner-approved; not a round number.
 *
 * ## Asymmetric in degrees and in time (owner decision)
 *
 * Enter unreliable **above [ENTER_DEGREES]**, leave only **below [LEAVE_DEGREES]** — a band, so a
 * reading jittering around 15° at sensor rate does not flicker the state several times a second.
 * And enter **immediately**, leave only after the signal has **held good for [CLEAR_HOLD_MILLIS]**:
 * a false warning costs a glance; a false all-clear costs someone walking the wrong way. The same
 * reasoning as the HUD's stale-fix states.
 *
 * ## The status-level path
 *
 * When the sensor gives no usable estimate, its accuracy status decides: `UNRELIABLE` and `LOW` are
 * bad (the platform's own words: "cannot be trusted", "calibration needed"), `MEDIUM` and `HIGH`
 * are good. Coarser than degrees by nature; the time hold applies to it unchanged, so both paths
 * produce the same user-visible state with the same asymmetry.
 */
class CompassTrustJudge(
    private val enterDegrees: Float = ENTER_DEGREES,
    private val leaveDegrees: Float = LEAVE_DEGREES,
    private val clearHoldMillis: Long = CLEAR_HOLD_MILLIS,
) {
    private var unreliable = false
    private var goodSinceMillis: Long? = null

    /** `true` while the heading is not to be trusted, after taking [reading] into account. */
    fun next(reading: CompassReading): Boolean {
        val bad = when (val u = reading.uncertainty) {
            is HeadingUncertainty.Estimated -> if (unreliable) u.degrees >= leaveDegrees else u.degrees > enterDegrees
            is HeadingUncertainty.Status -> u.level == CompassStatus.UNRELIABLE || u.level == CompassStatus.LOW
        }
        if (bad) {
            // Entering is immediate; any bad reading while unreliable restarts the clear hold.
            unreliable = true
            goodSinceMillis = null
            return true
        }
        if (!unreliable) return false
        val since = goodSinceMillis ?: reading.timestampMillis.also { goodSinceMillis = it }
        if (reading.timestampMillis - since >= clearHoldMillis) {
            unreliable = false
            goodSinceMillis = null
        }
        return unreliable
    }

    fun reset() {
        unreliable = false
        goodSinceMillis = null
    }

    companion object {
        const val ENTER_DEGREES = 15f
        const val LEAVE_DEGREES = 12f
        const val CLEAR_HOLD_MILLIS = 2_000L
    }
}
