package com.zynergylabs.forager.app.domain.model

import java.util.Locale

/**
 * An average walking speed in the user's display unit, one decimal: "1.9 mph", "3.1 km/h" (dispatch 2026-09-28-677, data
 * part B: the track sheet's Moving speed tile). The value stays metres per second;
 * only the label converts, the rule [formatWholeLength] follows. One decimal because walking speeds sit between 1 and 5 in
 * either unit, where a whole number would round a slow walk and a brisk one to the same figure. `Locale.US` for the decimal
 * point, as [formatRainfall] and [formatSoilTemperature] use. There was no speed formatter in the app before this.
 */
fun formatSpeed(metersPerSecond: Double, unit: DistanceUnit): String = when (unit) {
    DistanceUnit.KILOMETERS -> String.format(Locale.US, "%.1f km/h", metersPerSecond * SECONDS_PER_HOUR / METERS_PER_KILOMETER)
    DistanceUnit.MILES -> String.format(Locale.US, "%.1f mph", metersPerSecond * SECONDS_PER_HOUR / SPEED_METERS_PER_MILE)
}

private const val SECONDS_PER_HOUR = 3_600.0
private const val METERS_PER_KILOMETER = 1_000.0
private const val SPEED_METERS_PER_MILE = 1_609.344
