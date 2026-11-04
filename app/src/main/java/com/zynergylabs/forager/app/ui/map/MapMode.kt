package com.zynergylabs.forager.app.ui.map

/**
 * The basemap looks a user picks between, left to right in the map type panel the Layers button opens ([MapTypePanel]): Trail (Forager Trail
 * Map), Topo and Street, the Street map, OpenStreetMap's raster (dispatch 2026-10-22-01, RECORD -1554, -1556: the chips read
 * "Trail", "Topo" and "OSM" until dispatch 2026-11-03-05 A2 renamed the third "Street"; it lives in `ui/map/street/`, D7).
 * Before that, two: Street (then the name of what became Trail Map) and Topographical. Picked from the Layers sheet's "Map type" row (L0b to 2026-10-28, now [MapTypePanel]) or the quick-fire `MapModePicker` (removed 2026-10-19, RECORD -1392).
 * Replaced the deleted `MapService` entirely, per the project owner's own request.
 *
 * `MapService` used to split this choice into two decisions with two different lifetimes — which
 * *service* (occasional, Settings) and which *mode* that service was in (frequent, a quick-fire
 * icon over the map). That split existed because `Basemap.USGS_TOPO` and `Basemap.USGS_IMAGERY_TOPO`
 * were live alternatives to OpenStreetMap's own topo/regular pair, worth choosing between. Neither
 * is any more: [STREET] and [TOPOGRAPHIC] are pinned to OpenStreetMap outright, so there is exactly
 * one decision — which look this map currently has. The Settings "Choose Maps Service" section this
 * superseded is deleted, not left dead.
 *
 * **Satellite was a third mode, removed by dispatch 2026-09-28-708**, to be revisited once the
 * forecast's habitat layers are on the map. The owner: "Satellite loses quality fast and is less
 * useful when zoomed in", then "Remove now, revisit later (Recommended)". See [Basemap]'s class doc.
 *
 * **The choice persists across restarts** (same dispatch). The owner, to the step path: "have the app
 * remember which map modes you had it on last so we don't have to keep switching to the favorite".
 * It is stored by [storageKey], not by enum name, so renaming an entry cannot orphan what a phone
 * already holds; [forStoredKey] reads it back. Before this the mode was session-only and reset to
 * [DEFAULT] on every launch.
 */
class MapMode internal constructor(
    /** A stable name for logs and test tags (`mapModeChipTag`), as an enum's `name` was before dispatch 2026-11-03-05 D7. */
    val name: String,
    val label: String,
    val basemap: Basemap,
    /**
     * What `map_preferences` holds for this mode (`DataStoreMapPreferencesRepository`). Never change
     * one: a stored key that stops matching reads as unknown and the user is moved to
     * [REPLACEMENT_FOR_UNKNOWN].
     */
    val storageKey: String,
) {
    override fun toString(): String = name

    companion object {
        /**
         * Forager Trail Map, the app's own map, stored as "trail" since dispatch 2026-11-03-05 (A1, RECORD -1999: our map's
         * code name is "trail"). From dispatch 2026-10-22-01 Amendment 2 (RECORD -1556, option (a)) it kept the key "street",
         * which before that named Street; this dispatch gives "street" to the Street map, OpenStreetMap's (A2), on purpose
         * (A3), so a phone's "street" is moved to "trail" once, before the first read (`BasemapKeyTrailStreetMigration`, B1),
         * and every user keeps the map they chose.
         */
        val TRAIL = MapMode(name = "TRAIL", label = "Trail", basemap = Basemap.FORAGER_TRAIL, storageKey = "trail")
        val TOPOGRAPHIC = MapMode(name = "TOPOGRAPHIC", label = "Topo", basemap = Basemap.OPEN_TOPO_MAP, storageKey = "topographic")

        /**
         * Every map type, in chip order: the app's own two, then each [AddedMapType]'s (dispatch 2026-11-03-05 D7, RECORD
         * -2001). The Street map is one, in `ui/map/street/`, registered in [ADDED_MAP_TYPES].
         */
        val entries: List<MapMode> by lazy { listOf(TRAIL, TOPOGRAPHIC) + ADDED_MAP_TYPES.map { it.mapMode } }

        /**
         * Trail: what the map opens on when nothing is stored, or when the stored choice cannot be read (dispatch
         * 2026-10-22-01, RECORD -1554: "New user → opens on Trail Map"). It was Street from -1297, and Topographical
         * before that. A stored choice is kept, so anyone who picked Topographical still opens on it.
         */
        val DEFAULT = TRAIL

        /**
         * Where a stored key that names no mode goes: Topographical. The case it was written for is a
         * removed mode (Satellite, and any later removal), and the owner's answer for anyone who had
         * Satellite chosen was "Topo (Recommended)". Named apart from [DEFAULT] because it is that
         * answer, not the opening default. They were equal until Amendment 2 of dispatch 2026-10-18-04 made the default Street;
         * this one was deliberately left on Topographical (RECORD -1297).
         */
        val REPLACEMENT_FOR_UNKNOWN = TOPOGRAPHIC

        /** The mode that draws [basemap]; the two are one to one, so this is a lookup, not a choice. */
        fun forBasemap(basemap: Basemap): MapMode = entries.first { it.basemap == basemap }

        /**
         * The mode stored as [key], or `null` when no mode has that key (a removed mode such as
         * Satellite's, or anything else). The caller decides what an unknown key becomes and logs it
         * (CLAUDE.md: no unlogged fallback); see `AvailabilityViewModel`'s basemap load.
         */
        fun forStoredKey(key: String): MapMode? = entries.firstOrNull { it.storageKey == key }
    }
}
