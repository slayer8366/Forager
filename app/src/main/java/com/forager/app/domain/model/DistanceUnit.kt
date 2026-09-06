package com.forager.app.domain.model

import kotlin.math.roundToInt

/**
 * The unit distances are displayed in — a Settings preference persisted via
 * [com.forager.app.domain.DistanceUnitPreferenceRepository]. Moved here from `ui/availability/`
 * (2026-08-27) alongside that persistence fix: it was plain Compose state in `AvailabilityScreen`,
 * which reset to [MILES] on any configuration change — a system theme switch among them, a real
 * device report, not just the process-death case its own doc comment used to accept as the only
 * cost. A domain-owned preference belongs in `domain.model` the same way [Region]/[LatLng] do, not
 * in a `ui` package a domain-level repository would otherwise have to depend on.
 *
 * [MILES], not [KILOMETERS], is the default (changed 2026-08-27, per the project owner) —
 * see [DistanceUnitPreferenceRepository.getDistanceUnit][com.forager.app.domain.DistanceUnitPreferenceRepository.getDistanceUnit]'s
 * own doc comment for where that default is actually applied.
 *
 * Distance **values** stay kilometers everywhere else in this app, always — [Region.radiusKm]
 * (searched, sent to iNaturalist) is never converted; only [formatDistanceKm] below, called at each
 * display site, converts the *label* a user reads. Search radius sliders keep their existing
 * 1..50 km-quantized steps regardless of this setting, for the same reason: this is a display
 * preference, not a change to what radius can be chosen or how it's searched.
 */
enum class DistanceUnit(val label: String) {
    KILOMETERS("Kilometers"),
    MILES("Miles"),
}

/** 1 km in miles, to the same precision the U.S. survey mile/international mile agree to. */
private const val MILES_PER_KM = 0.621371

/**
 * Renders [radiusKm] in whichever [unit] the user picked, rounded to the nearest whole unit — the
 * same rounding `CompassElevationStripContent` (in `ui/availability/AvailabilityScreen.kt`) already
 * applies to heading/elevation, so a distance label reads as cleanly as either of those rather than
 * carrying decimal places nothing else in this app's distance displays has ever shown.
 */
/**
 * The offline-map radius slider's starting value, chosen per display unit rather than converted
 * from one stored value (radius-default dispatch, Item 2): a single metric default cannot be round
 * in both units — 15 km read as "9 mi", and 5 mi is 8.05 km. Miles gets 8 km, the km value
 * [formatDistanceKm] rounds to "5 mi" (8 × 0.621371 = 4.97), the same reasoning the search
 * radius's own default of 8 already records; kilometres gets 10 km. Selected by [unit], and only
 * ever applied while the radius is untouched — see `AvailabilityViewModel.applyDistanceUnit`.
 */
fun defaultOfflineMapRadiusKm(unit: DistanceUnit): Int = when (unit) {
    DistanceUnit.KILOMETERS -> OFFLINE_MAP_DEFAULT_RADIUS_KM_FOR_KILOMETERS
    DistanceUnit.MILES -> OFFLINE_MAP_DEFAULT_RADIUS_KM_FOR_MILES
}

/** See [defaultOfflineMapRadiusKm]. 8 km displays as "5 mi". */
private const val OFFLINE_MAP_DEFAULT_RADIUS_KM_FOR_MILES = 8

/** See [defaultOfflineMapRadiusKm]. */
private const val OFFLINE_MAP_DEFAULT_RADIUS_KM_FOR_KILOMETERS = 10

fun formatDistanceKm(radiusKm: Int, unit: DistanceUnit): String = when (unit) {
    DistanceUnit.KILOMETERS -> "$radiusKm km"
    DistanceUnit.MILES -> "${(radiusKm * MILES_PER_KM).roundToInt()} mi"
}

/**
 * A metre-scale distance in the user's display unit — navigation HUD stage one, the first reader
 * of [DistanceUnit] below the kilometre scale. Metric: "412 m" below a kilometre, "1.2 km" at or
 * above. Miles: feet below a quarter mile ("328 ft"), tenths of a mile at or above ("0.3 mi",
 * "1.0 mi") — a quarter mile is where a walker stops counting in feet. Replaces the metric-only
 * `formatReturnDistance` the return-to-vehicle arm used to read, so the arm and the HUD render the
 * same distance the same way.
 */
fun formatDistanceMeters(distanceMeters: Double, unit: DistanceUnit): String = when (unit) {
    DistanceUnit.KILOMETERS ->
        if (distanceMeters < 1_000.0) "${distanceMeters.roundToInt()} m" else "${"%.1f".format(distanceMeters / 1_000.0)} km"
    DistanceUnit.MILES -> {
        val miles = distanceMeters / METERS_PER_MILE
        if (miles < 0.25) "${(distanceMeters * FEET_PER_METER).roundToInt()} ft" else "${"%.1f".format(miles)} mi"
    }
}

/**
 * [formatDistanceMeters], but never claiming more precision than the fix supports — location-
 * accuracy dispatch, item 2. The HUD showed "0 ft" on device with several metres of reported
 * accuracy: the number was not wrong, its resolution was a lie, the same class of problem as a
 * confident needle pointing nowhere. A new function so every other caller of
 * [formatDistanceMeters] keeps its exact output; the HUD's distance slot is the only reader.
 *
 * Three cases (owner decision — rounding, not "12 m ± 8 m", because one number is what reads at
 * arm's length in the woods on a two-row HUD):
 *
 * - **No accuracy reported** (`null`): today's formatting, unchanged, no marker. There is no basis
 *   for coarsening, and inventing a resolution would be the same fabrication in the other
 *   direction.
 * - **Inside the error circle** (`distance <= accuracy`): `"within 16 ft"` — the accuracy itself,
 *   formatted in the display unit. This is the "0 ft" case: the honest statement is "you are
 *   inside the circle", and it dovetails with "Approaching", which fires at twice accuracy.
 * - **Outside it**, below the km / quarter-mile switch: the distance rounded to a step no finer
 *   than the accuracy, from [METRE_STEPS] or [FOOT_STEPS], with a leading `"≈ "` whenever that
 *   step is coarser than the unit's natural resolution (1 m / 1 ft). 12 m with 8 m accuracy is
 *   `"≈ 10 m"`; 340 m with 15 m is `"≈ 350 m"`. At or above the switch the existing one-decimal
 *   formatting (100 m / 0.1 mi steps) is already coarser than any accuracy the live-fix gate lets
 *   through, so it is left exactly as it is.
 *
 * The `≈` glyph and the `within` wording are the owner's; a `~` was considered and rejected as
 * reading like a typo at `titleMedium`.
 */
fun formatDistanceWithAccuracy(distanceMeters: Double, accuracyMeters: Float?, unit: DistanceUnit): String {
    if (accuracyMeters == null) return formatDistanceMeters(distanceMeters, unit)
    val accuracy = accuracyMeters.toDouble()
    if (distanceMeters <= accuracy) return "within ${formatDistanceMeters(accuracy, unit)}"
    return when (unit) {
        DistanceUnit.KILOMETERS -> {
            if (distanceMeters >= 1_000.0) return formatDistanceMeters(distanceMeters, unit)
            val step = METRE_STEPS.first { it >= accuracy } // METRE_STEPS' last entry exceeds the gate's ceiling
            val rounded = (distanceMeters / step).roundToInt() * step
            if (step == 1) "$rounded m" else "≈ $rounded m"
        }
        DistanceUnit.MILES -> {
            if (distanceMeters / METERS_PER_MILE >= 0.25) return formatDistanceMeters(distanceMeters, unit)
            val accuracyFeet = accuracy * FEET_PER_METER
            val step = FOOT_STEPS.first { it >= accuracyFeet }
            val rounded = (distanceMeters * FEET_PER_METER / step).roundToInt() * step
            if (step == 1) "$rounded ft" else "≈ $rounded ft"
        }
    }
}

/** Rounding steps for [formatDistanceWithAccuracy], coarsest last; the last entry must exceed the live-fix gate's ceiling (50 m ≈ 164 ft) so `first { }` always finds one. */
private val METRE_STEPS = listOf(1, 5, 10, 50, 100, 500)
private val FOOT_STEPS = listOf(1, 5, 10, 50, 100, 500, 1_000)

private const val METERS_PER_MILE = 1_609.344
private const val FEET_PER_METER = 3.28084
