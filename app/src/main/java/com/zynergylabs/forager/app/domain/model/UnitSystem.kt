package com.zynergylabs.forager.app.domain.model

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Which system of units this person reads — the one place that question is answered (return-
 * estimate dispatch, Item 4, owner ruling). Before this, [DistanceUnit] was the app's only unit
 * preference, and it was doing the job of a units *system* under the name of one dimension: it
 * chose the offline-radius default (not a distance display) while rainfall, soil temperature and
 * elevation each decided "metric" by not deciding at all. Three misses under one wrong name is why
 * this exists rather than [DistanceUnit]'s meaning being widened.
 *
 * [DistanceUnit] is **derived** from this ([distanceUnit]), never chosen separately; the two enums
 * are in bijection ([forDistanceUnit]), which is what lets every existing `DistanceUnit` reader and
 * the Settings control's existing callback keep their shape. Readers today: every distance display
 * (through [distanceUnit]), rainfall ([formatRainfall]), and since dispatch 2026-09-28-549 elevation,
 * the rise from the start and position accuracy ([formatWholeLength], [formatElevationChange]) and
 * soil temperature ([formatSoilTemperature], and the guidance text's band). Those last were left
 * waiting by the first ruling ("build the preference here, convert rainfall only, report the
 * others"; `docs/navigation/2026-09-07-return-estimate-prebuild-report.md` §4.4); the owner chose
 * "Yes, both" for elevation and soil temperature (RECORD -547) and added accuracy (RECORD -557).
 *
 * [IMPERIAL] is the default, as [DistanceUnit.MILES] was — the app's users are US foragers.
 */
enum class UnitSystem(val label: String, val distanceUnit: DistanceUnit) {
    METRIC("Metric", DistanceUnit.KILOMETERS),
    IMPERIAL("Imperial (US)", DistanceUnit.MILES),
    ;

    companion object {
        /** The system whose distance unit is [unit] — total, since the two enums are in bijection. */
        fun forDistanceUnit(unit: DistanceUnit): UnitSystem = entries.first { it.distanceUnit == unit }
    }
}

/** Millimetres per inch, exactly, by definition of the international inch. */
private const val MM_PER_INCH = 25.4

/**
 * A precipitation depth in the user's units — return-estimate dispatch, Item 4. Values stay
 * millimetres everywhere else (Open-Meteo returns them, `FruitingPatternAssumptions`' thresholds
 * are in them); only the label a person reads converts, the same rule [formatDistanceKm] set.
 *
 * **Metric** keeps each call site's existing precision ([metricDecimals]) and its existing
 * no-space form ("12.4mm"), byte-identical to what those sites printed before this existed, so no
 * metric reader sees a change.
 *
 * **Imperial** (owner ruling): **tenths of an inch, with a trace floor** — millimetres carry one
 * useful digit at these magnitudes and inches carry two, so 12 mm is "0.5 in" and 2 mm (the
 * rain-day threshold, 0.08 in) is "0.1 in". Anything above zero that would print "0.0 in" prints
 * `"< 0.1 in"` instead: a printed zero after real rain would be a lie, and the domain may count
 * that day as a rain day. Exactly zero prints "0.0 in" — no rain is not a trace. `Locale.US` for
 * the decimal point, deliberately: a device set to a comma locale would otherwise print "0,5 in".
 */
fun formatRainfall(mm: Double, unitSystem: UnitSystem, metricDecimals: Int = 1): String = when (unitSystem) {
    UnitSystem.METRIC -> String.format(Locale.US, "%.${metricDecimals}f", mm) + "mm"
    UnitSystem.IMPERIAL -> {
        val inches = mm / MM_PER_INCH
        when {
            mm == 0.0 -> "0.0 in"
            inches < 0.05 -> "< 0.1 in"
            else -> String.format(Locale.US, "%.1f in", inches)
        }
    }
}

/** Metres per foot, exactly, by definition of the international foot. */
private const val METERS_PER_FOOT = 0.3048

/**
 * A whole-number length a person reads — an elevation, or a position's accuracy — in the user's
 * units (dispatch 2026-09-28-549, and its Amendment 1 for accuracy, RECORD -557). The value stays
 * metres everywhere else (the fix's `altitude`, `positionalAccuracyMeters`); only the label converts,
 * the rule [formatRainfall] follows.
 *
 * **Metric:** whole metres, " m", byte-identical to what the sites printed before this existed.
 * **Imperial:** whole feet, metres ÷ 0.3048, rounded the same way (`roundToInt`), " ft". Whole feet,
 * not tens of feet: the metric side shows whole metres, so this is like for like (the dispatch
 * considered and did not adopt coarser rounding for GPS height error).
 */
fun formatWholeLength(meters: Double, unitSystem: UnitSystem): String = when (unitSystem) {
    UnitSystem.METRIC -> "${meters.roundToInt()} m"
    UnitSystem.IMPERIAL -> "${(meters / METERS_PER_FOOT).roundToInt()} ft"
}

/**
 * A signed elevation change ("+33 ft", "-12 ft") in the user's units — the rise from the start in
 * the Return control's screen-reader sentence. The sign is taken from the metre value, as before,
 * so a small fall rounding to zero still reads "0" rather than "-0".
 */
fun formatElevationChange(meters: Double, unitSystem: UnitSystem): String =
    "${if (meters >= 0) "+" else ""}${formatWholeLength(meters, unitSystem)}"

/** Degrees Fahrenheit for [celsius]: °F = °C × 9/5 + 32. */
fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0

/**
 * A soil temperature in the user's units, one decimal, no space before the degree sign
 * ("11.3°C", "52.3°F"), as the site wrote °C before. Values stay °C everywhere else (Open-Meteo
 * returns them; `FruitingPatternAssumptions` is in them). `Locale.US` for the decimal point in both
 * systems (Amendment 1, RECORD -557): before this the metric figure took the phone's locale and
 * printed "11,3°C" on a comma-language phone; that one case changes, by the owner's ruling.
 */
fun formatSoilTemperature(celsius: Double, unitSystem: UnitSystem): String = when (unitSystem) {
    UnitSystem.METRIC -> String.format(Locale.US, "%.1f°C", celsius)
    UnitSystem.IMPERIAL -> String.format(Locale.US, "%.1f°F", celsiusToFahrenheit(celsius))
}
