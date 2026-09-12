package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.SundownCountdown

/**
 * Turns a position and an instant into a [SundownCountdown].
 *
 * Pure and Android-framework-free, so it is unit-testable headless, the same shape as
 * [ComputeTripWindowsUseCase] and [ComputeReturnToStartUseCase].
 *
 * ## Why it is stateless, and why that matters more than it looks
 *
 * Sunset is a function of clock and position. It does not depend on how long the recording has
 * been running, on what happened earlier in the trip, or on anything this class remembers between
 * calls, because it remembers nothing. An unexpected stop, a process death, a device restart: the
 * next call recomputes from scratch and is immediately right again, with no gap to reconcile and
 * nothing to persist. The owner's requirement that a restart must not spell disaster is met by the
 * shape of the problem rather than by recovery code, which is the cheapest way to meet a
 * requirement.
 *
 * The walk-back estimate does not have this property, and that is the difference between the two:
 * it reads the walked track, so a recording gap costs it real information. See the amendment in
 * `docs/audits/2026-09-11-sundown-countdown-prebuild-report.md`.
 */
class ComputeSundownCountdownUseCase {

    /**
     * @param nowEpochMillis the instant to report against.
     * @param position the last known position, or `null` when none has been fixed yet.
     * @param fixAtEpochMillis when [position] was observed, used only to report its age.
     * @param darknessMarginMillis how far before sunset the turnaround moment sits. Not validated
     *   against sunrise: a margin longer than the whole day yields a turnaround already in the
     *   past, which renders as "past turnaround" and is the honest reading of that setting.
     */
    operator fun invoke(
        nowEpochMillis: Long,
        position: LatLng?,
        fixAtEpochMillis: Long?,
        darknessMarginMillis: Long,
    ): SundownCountdown {
        if (position == null) return SundownCountdown.NoPositionYet

        val sunset = SunCrossing.nextDescendingCrossing(
            fromEpochMillis = nowEpochMillis,
            withinMillis = SEARCH_WINDOW_MILLIS,
            latitude = position.lat,
            longitude = position.lng,
            altitudeDegrees = SunCrossing.SUNSET_ALTITUDE_DEGREES,
        ) ?: return polarStateAt(nowEpochMillis, position)

        // Civil dusk is searched from sunset rather than from now, so that a call made between
        // sunset and dusk still reports *today's* dusk instead of skipping to tomorrow's. Searched
        // independently and allowed to come back null: a day can end without the sun reaching -6.
        val civilDusk = SunCrossing.nextDescendingCrossing(
            fromEpochMillis = sunset,
            withinMillis = SEARCH_WINDOW_MILLIS,
            latitude = position.lat,
            longitude = position.lng,
            altitudeDegrees = CivilTwilight.NIGHT_ALTITUDE_DEGREES,
        )

        return SundownCountdown.Known(
            nowEpochMillis = nowEpochMillis,
            sunsetAtEpochMillis = sunset,
            civilDuskAtEpochMillis = civilDusk,
            turnaroundAtEpochMillis = sunset - darknessMarginMillis,
            fixAgeMillis = fixAtEpochMillis?.let { (nowEpochMillis - it).coerceAtLeast(0L) } ?: 0L,
        )
    }

    /**
     * No crossing inside a full day means one of two opposite situations, and the current altitude
     * is what tells them apart. This is the only place the polar cases are named, and they are
     * *read off* rather than detected by a latitude test: a latitude threshold would be a second,
     * approximate copy of a fact the altitude already states exactly.
     */
    private fun polarStateAt(nowEpochMillis: Long, position: LatLng): SundownCountdown {
        val altitude = CivilTwilight.sunAltitudeDegrees(
            nowEpochMillis, position.lat, position.lng,
        )
        return if (altitude > SunCrossing.SUNSET_ALTITUDE_DEGREES) {
            SundownCountdown.SunDoesNotSet
        } else {
            SundownCountdown.SunStaysDown
        }
    }

    private companion object {
        /**
         * A full day. Long enough that a `null` from the search means the sun genuinely does not
         * cross rather than that the window ended too soon, which is what lets [polarStateAt]
         * treat `null` as a statement about the sky instead of about the search.
         */
        const val SEARCH_WINDOW_MILLIS = 24 * 60 * 60 * 1000L
    }
}
