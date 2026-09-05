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
