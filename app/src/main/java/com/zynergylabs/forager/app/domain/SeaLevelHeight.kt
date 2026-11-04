package com.zynergylabs.forager.app.domain

/**
 * A fix's height above sea level (dispatch 2026-10-28-13, RECORD -1792; the owner: "Sea level + barometer (Recommended)").
 *
 * The platform's `Location.getAltitude()` is the height above the WGS 84 ellipsoid, not above sea level. In western
 * Oregon sea level lies about 21 m below the ellipsoid, so the raw height reads about 21 m low there
 * (docs/audits/2026-10-28-altitude-report.md, section 2). The raw height is what is stored (the owner: "Keep raw, show
 * sea level (Recommended)"); this is what is shown.
 */
sealed interface SeaLevelHeight {
    /**
     * [meters] above sea level. [aboveRawMeters] is how far that is above the raw height it came from (sea level minus
     * the ellipsoid there, the geoid's depth below it, about +21 m in western Oregon), so another raw height nearby can
     * be put on the same footing without a second conversion; see [ShownHeight.Known.seaLevelOf].
     */
    data class Known(val meters: Double, val aboveRawMeters: Double) : SeaLevelHeight

    /**
     * The phone cannot convert: below Android 14, or the platform's model failed to load (logged once by the
     * converter). Shown as "Alt unavailable", never as the raw height (CLAUDE.md: an unsupported capability returns an
     * explicit "unsupported", never a fabricated plausible value).
     */
    data object Unsupported : SeaLevelHeight

    /** The fix carried no height at all. */
    data object NoHeight : SeaLevelHeight
}

/**
 * Converts a raw (ellipsoid) height to sea level, behind an interface this project owns (CLAUDE.md: external
 * integrations): the app's one is `location/AndroidSeaLevelConverter`, the only class that touches the platform's
 * converter; tests hand in their own.
 */
fun interface SeaLevelConverter {
    /** [rawMeters] is the fix's reported height, `null` when it reported none ([SeaLevelHeight.NoHeight]). May read from disk: call it off the main thread's critical path. */
    suspend fun seaLevel(lat: Double, lng: Double, rawMeters: Double?): SeaLevelHeight
}

/**
 * No converter at all: every height is [SeaLevelHeight.Unsupported] and a missing one [SeaLevelHeight.NoHeight]. The
 * default where none is given (tests that never look at height), so a forgotten wiring reads "Alt unavailable" rather
 * than passing a raw height off as sea level.
 */
object NoSeaLevelConverter : SeaLevelConverter {
    override suspend fun seaLevel(lat: Double, lng: Double, rawMeters: Double?): SeaLevelHeight =
        if (rawMeters == null) SeaLevelHeight.NoHeight else SeaLevelHeight.Unsupported
}
