package com.zynergylabs.forager.app.domain

/**
 * Owned abstraction over magnetic declination — the angle between magnetic north and true north
 * at a place and time. Domain and UI code depend on this interface, never on
 * `android.hardware.GeomagneticField` directly, the same shape [CompassProvider] gives the compass
 * sensor (CLAUDE.md: wrap external integrations behind an interface this project owns).
 *
 * HUD-foundations dispatch, Item 2. [CompassProvider.heading] is measured from **magnetic** north;
 * [GeoDistance.initialBearingDegrees] (and so
 * [com.zynergylabs.forager.app.domain.model.ReturnToStartInfo.bearingDegrees]) is computed from **true** north.
 * Nothing reconciled the two before this — a target needle built from them as they stood would
 * have pointed about 15° wrong in the Pacific Northwest, confidently. [ComputeTrueHeadingUseCase]
 * is where the two now meet; this interface is only the declination source it reads.
 *
 * **Sign convention:** positive is **east** — magnetic north lies east of true north, so
 * `true heading = magnetic heading + declination`. Negative is west. Portland, Oregon is about
 * +14.5° in 2026; Boston is about −14°. This matches `GeomagneticField.getDeclination()`'s own
 * convention, and every implementation must preserve it (see `AndroidDeclinationProviderTest`,
 * which pins both a positive and a negative site so a flipped sign fails outright).
 */
interface DeclinationProvider {
    /**
     * Declination in degrees at the given place and time, positive east of true north.
     *
     * A plain one-shot function, not a flow: declination is a pure computation from a geomagnetic
     * model, with no sensor to subscribe to and no cadence of its own. How often to recompute is
     * the caller's policy — [ComputeTrueHeadingUseCase] recomputes past roughly 10 km of movement
     * or a day of elapsed time and otherwise reuses its last value.
     *
     * [altitudeMeters] is `null` when the fix reported none; implementations use sea level then,
     * which moves the result by hundredths of a degree per kilometre of altitude — documented here
     * rather than left as a silent default, since it is the one input that is genuinely optional.
     */
    fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float
}
