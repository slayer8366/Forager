package com.forager.app.domain.model

import java.util.Locale

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
 * (through [distanceUnit]) and rainfall ([formatRainfall]). **Not yet readers, reported and queued
 * in `docs/audits/2026-09-07-return-estimate-prebuild-report.md` §4.4:** soil temperature (°C at
 * one site) and elevation (metres at three). They wait on this preference; they are not converted
 * here, per the ruling ("build the preference here, convert rainfall only, report the others").
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
