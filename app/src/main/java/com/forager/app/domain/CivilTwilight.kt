package com.forager.app.domain

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Whether it is dark enough at a place and time that the map should be in night mode.
 *
 * Pure Kotlin, no Android imports, no network — which is the whole point. This is consulted at
 * dusk, outdoors, on a phone that may well have no signal, so an implementation that asks a
 * weather API for `sunrise`/`sunset` would fail in exactly the situation it exists for. Open-Meteo
 * does return those fields and the app already calls it; that option was rejected for this reason,
 * not overlooked.
 *
 * ## Why solar altitude, not sunrise and sunset times
 *
 * This computes where the sun *is* and compares it to a threshold, rather than computing the times
 * it crosses the horizon and checking whether now falls between them. Two reasons, and the second
 * is the one that matters:
 *
 *  - It is one evaluation with no interval arithmetic, no "which day's sunset?" question near
 *    midnight, and no timezone handling at all — the input is an instant and a position.
 *  - **It has no polar special case.** Above the Arctic circle there are stretches of the year with
 *    no sunrise and no sunset to compute, and an algorithm built on those times has to detect that
 *    and branch. Altitude is always defined. Tromsø at midday in December returns about −4°, which
 *    is *not* night by the threshold below — correctly, because civil twilight around noon during
 *    polar night is real, usable light, and a map that blacked out there would be wrong.
 *
 * Accuracy is NOAA's low-precision solar position algorithm: better than a tenth of a degree for
 * dates near the present, which is far beyond what a threshold crossing needs — the sun moves
 * through the civil-twilight band in minutes, so an error of a tenth of a degree moves the switch
 * by seconds.
 */
object CivilTwilight {

    /**
     * Sun altitude at or below which the map switches to night mode, in degrees.
     *
     * −6° is the standard civil-twilight definition: the point at which the horizon is no longer
     * clearly discernible and artificial light is generally needed to read by. Chosen over
     * nautical (−12°) or astronomical (−18°) twilight because this switch is about when a *screen*
     * becomes uncomfortably bright and a map becomes hard to read, which happens at the civil
     * boundary, not hours later when the sky is fully dark.
     *
     * Not the same question as "is the sun up" (0°): between 0° and −6° there is still enough light
     * that a dimmed map would be the harder one to read.
     */
    const val NIGHT_ALTITUDE_DEGREES: Double = -6.0

    /** True when the sun is at or below [NIGHT_ALTITUDE_DEGREES] at this instant and position. */
    fun isNight(epochMillis: Long, latitude: Double, longitude: Double): Boolean =
        sunAltitudeDegrees(epochMillis, latitude, longitude) <= NIGHT_ALTITUDE_DEGREES

    /**
     * The sun's altitude above the horizon, in degrees; negative below it.
     *
     * `internal` rather than private so the tests can check it against published almanac values
     * directly. Asserting only [isNight] would test a boolean two steps removed from the
     * arithmetic, and a sign error large enough to matter could still land on the right side of
     * the threshold in every case someone happened to write down.
     */
    internal fun sunAltitudeDegrees(epochMillis: Long, latitude: Double, longitude: Double): Double {
        val julianDay = epochMillis / MILLIS_PER_DAY.toDouble() + JULIAN_DAY_AT_EPOCH
        val t = (julianDay - JULIAN_DAY_AT_J2000) / DAYS_PER_JULIAN_CENTURY

        // Geometric mean longitude and anomaly of the sun, degrees.
        val meanLongitude = (280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0)
        val meanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)

        // Equation of centre: the correction from a circular orbit to the real elliptical one.
        val centre = sin(meanAnomaly.rad) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin((2 * meanAnomaly).rad) * (0.019993 - 0.000101 * t) +
            sin((3 * meanAnomaly).rad) * 0.000289

        val trueLongitude = meanLongitude + centre
        val omega = 125.04 - 1934.136 * t
        val apparentLongitude = trueLongitude - 0.00569 - 0.00478 * sin(omega.rad)

        val meanObliquity = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val obliquity = meanObliquity + 0.00256 * cos(omega.rad)

        val declination = asin(sin(obliquity.rad) * sin(apparentLongitude.rad)).deg

        // Equation of time, in minutes: the gap between clock time and true solar time.
        val y = tan((obliquity / 2).rad).let { it * it }
        val equationOfTime = 4 * (
            y * sin(2 * meanLongitude.rad) -
                2 * eccentricity * sin(meanAnomaly.rad) +
                4 * eccentricity * y * sin(meanAnomaly.rad) * cos(2 * meanLongitude.rad) -
                0.5 * y * y * sin(4 * meanLongitude.rad) -
                1.25 * eccentricity * eccentricity * sin(2 * meanAnomaly.rad)
            ).deg

        val minutesUtc = (epochMillis.mod(MILLIS_PER_DAY)) / 60000.0
        val trueSolarTime = (minutesUtc + equationOfTime + 4 * longitude).mod(1440.0)
        val hourAngle = trueSolarTime / 4.0 - 180.0

        val cosZenith = sin(latitude.rad) * sin(declination.rad) +
            cos(latitude.rad) * cos(declination.rad) * cos(hourAngle.rad)
        // Guard the acos domain: accumulated floating-point error can push this a hair outside
        // [-1, 1] at the poles, where acos would return NaN and the caller would silently get
        // "not night" for every instant.
        return 90.0 - acos(cosZenith.coerceIn(-1.0, 1.0)).deg
    }

    private const val MILLIS_PER_DAY = 86_400_000L
    private const val JULIAN_DAY_AT_EPOCH = 2440587.5
    private const val JULIAN_DAY_AT_J2000 = 2451545.0
    private const val DAYS_PER_JULIAN_CENTURY = 36525.0

    private const val PI_OVER_180 = kotlin.math.PI / 180.0

    /**
     * `mod`, not `%`. Kotlin's `mod` takes the sign of the divisor, so it is non-negative here;
     * `%` takes the sign of the dividend and would return a negative hour angle for any instant
     * before the epoch, putting the sun on the wrong side of the sky.
     */
    private val Double.rad: Double get() = this * PI_OVER_180
    private val Double.deg: Double get() = this / PI_OVER_180
}
