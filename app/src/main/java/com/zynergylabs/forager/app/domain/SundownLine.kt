package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.SundownCountdown

/**
 * The one line about the end of the day that the map's top strip shows while recording, and the
 * navigation display (HUD) shows while navigating (dispatch 2026-09-28-592, plan tasks T3 and T4;
 * placement RECORD -421, "Strip, then HUD (Recommended)"; the owner's step path in RECORD -592 and
 * Amendment 1, RECORD -593). What it says is worked out here, without any Android type; the words
 * and the clock format are the screen's (`ui/availability/SundownLineText.kt`).
 *
 * Every case is explicit, as [SundownCountdown]'s are: there is no blank that could read as "fine".
 * Nothing here says there is enough time.
 */
sealed interface SundownLine {

    /**
     * No position yet. Never drawn: the owner dropped "Sunset: finding your position…" (RECORD -596,
     * "Yes hide it before a position is known"); see [isShown].
     */
    data object FindingPosition : SundownLine

    /**
     * The sun is up and sets at [sunsetAtEpochMillis].
     *
     * [startBackAtEpochMillis] is the leave-by alert's own time ([sundownLeaveByAt]), present only
     * when the walk back is known ("start back by 5:32", or "start back was 5:32" once it has passed).
     * `null` when it is not, and always on the navigation display without a recording, where the line
     * is the countdown and civil dusk instead ("in 1 h 12 min · dark 7:13").
     */
    data class BeforeSunset(
        val nowEpochMillis: Long,
        val sunsetAtEpochMillis: Long,
        val civilDuskAtEpochMillis: Long?,
        val startBackAtEpochMillis: Long?,
    ) : SundownLine

    /**
     * The sun has set, at [sunsetAtEpochMillis], today's, and it is not yet dark: "Sun set 6:42 PM ·
     * dark in 21 min". [civilDuskAtEpochMillis] is `null` where it does not get dark tonight (the sun
     * never reaches -6°, ordinary at high summer latitudes): the line then gives the sunset only.
     */
    data class AfterSunset(
        val nowEpochMillis: Long,
        val sunsetAtEpochMillis: Long,
        val civilDuskAtEpochMillis: Long?,
    ) : SundownLine

    /** Past civil dusk, today's: "Dark since 7:13 PM". No countdown to tomorrow (the owner's path). */
    data class DarkSince(val civilDuskAtEpochMillis: Long) : SundownLine

    /** Polar day: the sun does not set within the next day. */
    data object SunDoesNotSet : SundownLine

    /** Polar night: the sun stays below the horizon. */
    data object SunStaysDown : SundownLine
}

/**
 * The leave-by moment, the one place it is computed: the turnaround (sunset minus the darkness
 * margin) minus the walk back, or the turnaround alone when the walk back is unknown (`null`).
 * [DecideSundownAlertUseCase] decides the alerts on it and [SundownWatch] shows it on the line, so
 * the line's "start back by" is the alert's own time and never a second computation of it.
 */
fun sundownLeaveByAt(turnaroundAtEpochMillis: Long, walkBackMillis: Long?): Long =
    turnaroundAtEpochMillis - (walkBackMillis ?: 0L)

/**
 * The [SundownLine] for [countdown] at [position].
 *
 * - **Before sunset** the countdown's own sunset and dusk, with [startBackAtEpochMillis] passed
 *   through.
 * - **Past the countdown's sunset** (a sunset held by [SundownWatch] once it has passed): that
 *   sunset, and dark at the countdown's civil dusk.
 * - **The sun already down but the countdown on tomorrow's sunset** (a recording started after
 *   sunset, or nothing holding one): today's sunset found backwards
 *   ([SunCrossing.previousDescendingCrossing]) and its dusk forwards from it, so the line never
 *   counts down to tomorrow.
 *
 * "Down" is the sun's altitude at or below [SunCrossing.SUNSET_ALTITUDE_DEGREES] at [position] now,
 * the same threshold the crossing search uses.
 */
fun sundownLineFor(countdown: SundownCountdown, position: LatLng?, startBackAtEpochMillis: Long?): SundownLine = when (countdown) {
    SundownCountdown.NoPositionYet -> SundownLine.FindingPosition
    SundownCountdown.SunDoesNotSet -> SundownLine.SunDoesNotSet
    SundownCountdown.SunStaysDown -> SundownLine.SunStaysDown
    is SundownCountdown.Known -> when {
        countdown.isPastSunset -> afterSunset(countdown.nowEpochMillis, countdown.sunsetAtEpochMillis, countdown.civilDuskAtEpochMillis)
        position != null && sunIsDown(countdown.nowEpochMillis, position) -> {
            val setAt = SunCrossing.previousDescendingCrossing(
                fromEpochMillis = countdown.nowEpochMillis,
                withinMillis = DAY_MILLIS,
                latitude = position.lat,
                longitude = position.lng,
                altitudeDegrees = SunCrossing.SUNSET_ALTITUDE_DEGREES,
            )
            if (setAt == null) SundownLine.SunStaysDown else afterSunset(countdown.nowEpochMillis, setAt, civilDuskAfter(setAt, position))
        }
        else -> SundownLine.BeforeSunset(
            nowEpochMillis = countdown.nowEpochMillis,
            sunsetAtEpochMillis = countdown.sunsetAtEpochMillis,
            civilDuskAtEpochMillis = countdown.civilDuskAtEpochMillis,
            startBackAtEpochMillis = startBackAtEpochMillis,
        )
    }
}

/**
 * Civil dusk after the sunset at [sunsetAtEpochMillis]: the next -6° crossing, if it comes before
 * the sun could rise and set again. `null` when the sun does not reach -6° that night.
 */
fun civilDuskAfter(sunsetAtEpochMillis: Long, position: LatLng): Long? =
    SunCrossing.nextDescendingCrossing(
        fromEpochMillis = sunsetAtEpochMillis,
        withinMillis = DUSK_WITHIN_MILLIS,
        latitude = position.lat,
        longitude = position.lng,
        altitudeDegrees = CivilTwilight.NIGHT_ALTITUDE_DEGREES,
    )

private fun afterSunset(now: Long, sunsetAt: Long, civilDuskAt: Long?): SundownLine =
    if (civilDuskAt != null && now >= civilDuskAt) SundownLine.DarkSince(civilDuskAt) else SundownLine.AfterSunset(now, sunsetAt, civilDuskAt)

private fun sunIsDown(now: Long, position: LatLng): Boolean =
    CivilTwilight.sunAltitudeDegrees(now, position.lat, position.lng) <= SunCrossing.SUNSET_ALTITUDE_DEGREES

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/**
 * Twelve hours: a -6° crossing further than this after a sunset belongs to another evening, so that
 * night does not get dark. Generous; at the latitudes where dusk lags sunset most it is a few hours.
 */
private const val DUSK_WITHIN_MILLIS = 12 * 60 * 60 * 1000L

/**
 * The line stays hidden until this long before sunset (dispatch 2026-09-28-592, Amendment 2, RECORD
 * -595). The owner, on hiding it earlier in the day as clutter: "Option 1, but change it from 4 hours
 * to 2.5 hours before sunset". At exactly 2 h 30 min it shows.
 */
const val SUNDOWN_LINE_SUNSET_WINDOW_MILLIS: Long = 150L * 60L * 1_000L

/**
 * Or until the start-back time is less than this far away, whichever comes first (the same ruling,
 * Amendment 2: the line opens early when "the start-back time is less than 1 hour away"). At exactly
 * one hour it is not yet less, so it stays hidden; a start-back time already passed is less.
 */
const val SUNDOWN_LINE_START_BACK_WINDOW_MILLIS: Long = 60L * 60L * 1_000L

/**
 * Whether the line is drawn at all (Amendment 2). Before sunset, only inside either window above.
 * From sunset on (sun set, dark since) always, as confirmed. Polar night, where it is dark all day,
 * always; polar day, which has no sunset to approach, never.
 *
 * **No position, no line.** With no position there is no sunset, so neither window can be known to
 * have opened. The owner, RECORD -596: "Yes hide it before a position is known"; "Sunset: finding your
 * position…" is dropped from the path. Once a position gives a sunset, the two windows decide.
 */
fun SundownLine.isShown(): Boolean = when (this) {
    SundownLine.FindingPosition -> false
    SundownLine.SunDoesNotSet -> false
    SundownLine.SunStaysDown -> true
    is SundownLine.AfterSunset -> true
    is SundownLine.DarkSince -> true
    is SundownLine.BeforeSunset ->
        sunsetAtEpochMillis - nowEpochMillis <= SUNDOWN_LINE_SUNSET_WINDOW_MILLIS ||
            (startBackAtEpochMillis != null && startBackAtEpochMillis - nowEpochMillis < SUNDOWN_LINE_START_BACK_WINDOW_MILLIS)
}
