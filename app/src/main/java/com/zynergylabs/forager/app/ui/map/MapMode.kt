package com.zynergylabs.forager.app.ui.map

/**
 * The two basemap looks a user picks between: Street and Topographical, both OpenStreetMap-derived,
 * from the Layers sheet's "Map type" row (`MapLayersSheet`) or the quick-fire `MapModePicker` (removed 2026-10-19, RECORD -1392).
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
enum class MapMode(
    val label: String,
    val basemap: Basemap,
    /**
     * What `map_preferences` holds for this mode (`DataStoreMapPreferencesRepository`). Never change
     * one: a stored key that stops matching reads as unknown and the user is moved to
     * [REPLACEMENT_FOR_UNKNOWN].
     */
    val storageKey: String,
) {
    STREET(label = "Street", basemap = Basemap.OSM_STANDARD, storageKey = "street"),
    TOPOGRAPHIC(label = "Topographical", basemap = Basemap.OPEN_TOPO_MAP, storageKey = "topographic"),
    ;

    companion object {
        /**
         * Street: what the map opens on when nothing is stored (dispatch 2026-10-18-04, Amendment 2; the owner, "1 go",
         * RECORD -1297). It was Topographical until then. A stored choice is kept, so anyone who picked Topographical
         * still opens on it; only someone with nothing stored (a new install, or a user who never picked) opens on Street.
         */
        val DEFAULT = STREET

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
