package com.forager.app.domain

/**
 * The navigation HUD's arithmetic (stage one), kept pure so a sign error is caught by a pinned
 * literal rather than surviving a self-consistent Compose test.
 */

/**
 * Where the target lies relative to where the user faces, in degrees clockwise `[0, 360)`: 0 is
 * dead ahead, 90 is to the right, 270 is to the left. Both inputs must share a reference —
 * [bearingDegrees] from [GeoDistance.initialBearingDegrees] is true north, so [headingDegrees]
 * must be the true heading from [ComputeTrueHeadingUseCase], never the raw magnetic one.
 *
 * Facing north-east (45°) with the target north-west (315°): 270, a quarter turn to the left.
 */
fun relativeBearingDegrees(bearingDegrees: Double, headingDegrees: Float): Float {
    val relative = (bearingDegrees - headingDegrees) % 360.0
    return ((relative + 360.0) % 360.0).toFloat()
}

/**
 * "Approaching" — never "arrived": the app does not declare arrival, because GPS accuracy under
 * canopy is routinely 10–20 m and any fixed radius fires early or never. The threshold is derived
 * from the fix's own reported accuracy (owner decision: twice it), so a good fix approaches at
 * 10 m and a poor one at 40 m, each honest to what the device actually knows. With no accuracy
 * reported there is no basis for the word at all, so it is never shown — an explicit unknown, not
 * a guessed constant.
 *
 * **On the owner's device this is a decision made from a field with no signal in it.** The
 * instrument walk of 2026-09-07 (`docs/audits/2026-09-07-fix-log-walk-findings.md`) found every
 * GPS fix on that phone reporting the same accuracy, `3.7900925` m, 289 of 289 — a placeholder,
 * not a measurement — so "Approaching" there fires at a fixed 7.58 m, whatever the sky, and the
 * "honest to what the device actually knows" reasoning above holds only on hardware that reports
 * a varying value. This is the serious reader of that field (owner's ranking): the gate and the
 * two formatters *display* a wrong number, this function *decides* from one — it also withholds
 * the needle, through the same threshold — and a decision is what a constant can silently
 * mis-make. One device so far; the beta trip report asks whether the "within" number ever
 * changes on other phones. Until a device is known to report real accuracy, do not build on this
 * threshold as if it tracked fix quality, and do not tune the multiplier against that device.
 */
fun isApproaching(distanceMeters: Double, accuracyMeters: Float?): Boolean {
    if (accuracyMeters == null) return false
    return distanceMeters <= APPROACHING_ACCURACY_MULTIPLIER * accuracyMeters
}

const val APPROACHING_ACCURACY_MULTIPLIER = 2.0

/**
 * How the HUD treats the fix's age — HUD only, by the foundations dispatch's own rule that no
 * staleness policy lives anywhere else in the app. Thresholds are an owner-approved starting
 * point, **tunable after field use**: raw fixes arrive about once a second while the radio has a
 * lock, so half a minute without one is already unusual, and five minutes is long enough that a
 * distance to the target would be a number about somewhere the user no longer is.
 */
enum class FixFreshness {
    /** Younger than [STALE_AFTER_MILLIS]: shown as current. */
    FRESH,

    /** Between the two thresholds: distance still shown, de-emphasised, with its age beside it. */
    STALE,

    /** Older than [LOST_AFTER_MILLIS]: distance and needle withheld; only the age is shown. */
    LOST,
}

const val STALE_AFTER_MILLIS = 30_000L
const val LOST_AFTER_MILLIS = 5L * 60L * 1_000L

fun fixFreshness(ageMillis: Long): FixFreshness = when {
    ageMillis >= LOST_AFTER_MILLIS -> FixFreshness.LOST
    ageMillis >= STALE_AFTER_MILLIS -> FixFreshness.STALE
    else -> FixFreshness.FRESH
}
