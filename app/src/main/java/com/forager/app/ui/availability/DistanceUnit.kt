package com.forager.app.ui.availability

import kotlin.math.roundToInt

/**
 * The unit distances are displayed in — a Settings preference, same lifetime and reasoning as
 * [com.forager.app.ui.map.MapService]: session-local Compose state in [AvailabilityScreen], not
 * threaded into the ViewModel or persisted, since it changes nothing about what's searched or
 * fetched. The cost, stated rather than hidden: it resets to [KILOMETERS] on process death, the
 * same cost [AvailabilityScreen]'s own doc comment already accepts for `selectedMapService`.
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
 * same rounding [com.forager.app.ui.availability.CompassElevationStripContent] already applies to
 * heading/elevation, so a distance label reads as cleanly as either of those rather than carrying
 * decimal places nothing else in this app's distance displays has ever shown.
 */
fun formatDistanceKm(radiusKm: Int, unit: DistanceUnit): String = when (unit) {
    DistanceUnit.KILOMETERS -> "$radiusKm km"
    DistanceUnit.MILES -> "${(radiusKm * MILES_PER_KM).roundToInt()} mi"
}
