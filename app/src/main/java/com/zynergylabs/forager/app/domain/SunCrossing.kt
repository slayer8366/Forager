package com.zynergylabs.forager.app.domain

/**
 * When the sun next passes **downward** through a given altitude at a place: sunset, civil dusk,
 * or any other threshold the caller names.
 *
 * ## Why this is a search and not a formula
 *
 * [CivilTwilight.sunAltitudeDegrees] answers "where is the sun now". A countdown needs the inverse,
 * "when will it be there", and the sundown pre-build report
 * (`docs/audits/2026-09-11-sundown-countdown-prebuild-report.md`) records why that inverse is a
 * search over the existing function rather than a second algorithm:
 *
 *  - **No new dependency.** The NOAA equations are already here and already tested
 *    (`CivilTwilightTest`). A library, or a hand-written second copy of the same arithmetic, would
 *    add a licence to vet and a drift risk CLAUDE.md's rule against duplicated logic exists to stop.
 *  - **The polar case stays an outcome, not a branch.** [CivilTwilight]'s own header records that it
 *    chose altitude over crossing times because "above the Arctic circle there are stretches of the
 *    year with no sunrise and no sunset to compute, and an algorithm built on those times has to
 *    detect that and branch." A search inherits that for free: where the sun never crosses, the scan
 *    finds no bracket and this returns `null`. Nobody has to remember to write the special case,
 *    because there is not one.
 *  - **It is cheap.** A coarse scan over a day plus a bisection is a few hundred evaluations of a
 *    closed-form expression, recomputed every fifteen seconds by the recording poll loop.
 *
 * ## Why sunset is −0.833° and not 0°
 *
 * "Sunset" conventionally means the moment the sun's **upper limb** meets the horizon, with standard
 * atmospheric refraction lifting the apparent disc. That is 0.833° below the geometric horizon:
 * roughly 0.267° for the solar radius and 0.567° for refraction. Using 0.0° instead runs about four
 * minutes early at temperate latitudes, which is small enough to look plausible and wrong enough to
 * matter in a feature about running out of light. The number is standard, not a fitted value.
 *
 * ## Verified against real sunsets, not only against itself
 *
 * Before this file existed, the algorithm was checked by porting [CivilTwilight]'s equations and
 * running this search against Open-Meteo's published sunsets: **16 sunsets across 8 places from
 * Ushuaia (−54.8°) to Reykjavík (64.1°) agreed to within 13 seconds, worst case.** Those fixtures
 * are the reference values in `SunCrossingTest`. Open-Meteo was the oracle because this project
 * already calls it, so the comparison is against a source already trusted elsewhere in the app. It
 * is not the runtime source: [CivilTwilight]'s header records why the app computes this offline
 * instead, and that reasoning is unchanged.
 */
object SunCrossing {

    /**
     * Sun altitude defining sunset, in degrees. See the class doc for why this is not zero.
     */
    const val SUNSET_ALTITUDE_DEGREES: Double = -0.833

    /**
     * How far apart the scan samples altitude while looking for a bracket.
     *
     * Ten minutes is comfortably finer than the fastest real crossing. The sun moves through the
     * civil-twilight band in minutes at temperate latitudes and slower, never faster, toward the
     * poles, so a descent cannot begin and end inside one step and be missed. Smaller steps cost
     * evaluations for no accuracy: the bisection that follows sets the precision, not this.
     */
    private const val COARSE_STEP_MILLIS: Long = 10 * 60 * 1000L

    /** The bisection stops once the bracket is this narrow. One second is finer than any caller reads. */
    private const val PRECISION_MILLIS: Long = 1_000L

    /**
     * The first instant in `[fromEpochMillis, fromEpochMillis + withinMillis]` at which the sun's
     * altitude passes **down** through [altitudeDegrees] at ([latitude], [longitude]), or `null`
     * when it does not cross in that window.
     *
     * `null` is the honest answer in three distinct situations, and the caller is expected to tell
     * them apart from context rather than from this function:
     *
     *  - polar day, where the sun stays above the threshold all window;
     *  - polar night, where it stays below;
     *  - a window that simply ends before the crossing.
     *
     * Ascending crossings are ignored on purpose. Sunrise is a different question and would need its
     * own call with the sign of the comparison reversed.
     */
    fun nextDescendingCrossing(
        fromEpochMillis: Long,
        withinMillis: Long,
        latitude: Double,
        longitude: Double,
        altitudeDegrees: Double,
    ): Long? {
        require(withinMillis > 0) { "withinMillis must be positive, was $withinMillis" }
        val end = fromEpochMillis + withinMillis

        var previousAt = fromEpochMillis
        var previousAbove = isAbove(previousAt, latitude, longitude, altitudeDegrees)
        var at = fromEpochMillis + COARSE_STEP_MILLIS

        while (at <= end) {
            val above = isAbove(at, latitude, longitude, altitudeDegrees)
            if (previousAbove && !above) {
                return bisect(previousAt, at, latitude, longitude, altitudeDegrees)
            }
            previousAt = at
            previousAbove = above
            at += COARSE_STEP_MILLIS
        }

        // The last partial step, so a crossing in the final minutes of the window is not lost to
        // the scan's stride. Without this, a window whose length is not a whole number of steps
        // silently ignores its own tail.
        if (previousAt < end && previousAbove && !isAbove(end, latitude, longitude, altitudeDegrees)) {
            return bisect(previousAt, end, latitude, longitude, altitudeDegrees)
        }
        return null
    }

    private fun isAbove(at: Long, latitude: Double, longitude: Double, altitudeDegrees: Double): Boolean =
        CivilTwilight.sunAltitudeDegrees(at, latitude, longitude) > altitudeDegrees

    /**
     * Narrows a bracket known to hold a descending crossing: [above] is above the threshold,
     * [below] is at or under it. Terminates on the bracket width, never on an iteration count, so
     * the precision is the stated one rather than whatever a loop bound happened to give.
     */
    private fun bisect(
        above: Long,
        below: Long,
        latitude: Double,
        longitude: Double,
        altitudeDegrees: Double,
    ): Long {
        var low = above
        var high = below
        while (high - low > PRECISION_MILLIS) {
            val middle = low + (high - low) / 2
            if (isAbove(middle, latitude, longitude, altitudeDegrees)) low = middle else high = middle
        }
        return low + (high - low) / 2
    }
}
