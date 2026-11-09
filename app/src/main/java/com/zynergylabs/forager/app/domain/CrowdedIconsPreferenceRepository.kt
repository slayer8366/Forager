package com.zynergylabs.forager.app.domain

/**
 * Settings > Map > "Crowded icons" (dispatch 2026-10-27-14, RECORD -1750, -1758). The owner: "Have an option to show the
 * new set up or just a list in the settings menu for simplicity"; "Every group"; "Zoom in, then fan (Recommended)" as the
 * default.
 */
enum class CrowdedIconsMode(val label: String, internal val stored: String) {
    /** A crowded stack whose fan does not fit zooms in, then fans (dispatch 2026-10-27-07). The default. */
    ZOOM_THEN_FAN("Zoom in, then fan", "zoom_then_fan"),

    /** Any stack of two or more icons opens the list at once, with no zoom and no fan. */
    LIST("List", "list"),
    ;

    companion object {
        val DEFAULT = ZOOM_THEN_FAN

        /** The mode [stored] names, or `null` for a value no write here produces (the caller logs it and uses [DEFAULT]). */
        fun fromStored(stored: String): CrowdedIconsMode? = entries.firstOrNull { it.stored == stored }
    }
}

/**
 * Where "Crowded icons" persists across restarts. A flat preference with no relations, so DataStore, not Room (CLAUDE.md);
 * the real implementation is `DataStoreMapPreferencesRepository`, key `map.crowded_icons`. A sibling interface, as
 * [MapIconClusterPlacementRepository] is, so the many [MapPreferencesRepository] fakes are untouched.
 */
interface CrowdedIconsPreferenceRepository {
    /** `null` when nothing is stored, or the stored value names no mode (reported through [unknown]). */
    suspend fun getCrowdedIconsMode(unknown: (String) -> Unit): Result<CrowdedIconsMode?>

    suspend fun setCrowdedIconsMode(mode: CrowdedIconsMode): Result<Unit>
}
