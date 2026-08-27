package com.forager.app.ui.map

/**
 * The three basemap looks a user picks between from the map's own quick-fire "Map Mode" control —
 * replaces [MapService] entirely, per the project owner's own request: Street and Topographical no
 * longer offer a choice of tile provider (both are always OpenStreetMap-derived now), and Satellite
 * is new, always USGS.
 *
 * [MapService] used to split this choice into two decisions with two different lifetimes — which
 * *service* (occasional, Settings) and which *mode* that service was in (frequent, a quick-fire
 * icon over the map). That split existed because [Basemap.USGS_TOPO] and [Basemap.USGS_IMAGERY_TOPO]
 * were live alternatives to OpenStreetMap's own topo/regular pair, worth choosing between. Neither
 * is any more: [STREET] and [TOPOGRAPHIC] are pinned to OpenStreetMap outright, and [SATELLITE] is
 * pinned to USGS outright, so there is exactly one decision now — which of the three looks this map
 * currently has — made from the map itself via [MapModePicker], not from Settings. The Settings
 * "Choose Maps Service" section this superseded is deleted, not left dead: nothing reads
 * [Basemap.USGS_TOPO] any more, so it is deleted from [Basemap] too, rather than kept unreachable.
 *
 * [SATELLITE] uses [Basemap.USGS_IMAGERY_ONLY] specifically, not [Basemap.USGS_IMAGERY_TOPO] —
 * pure aerial orthoimagery, not imagery with topo labels drawn over it, per the project owner's own
 * distinction ("not the USGS topo map, the other one"). Confirmed live before adding: `curl`
 * against `USGSImageryOnly/MapServer?f=json` returns a real service (`mapName: USGSImageryOnly`,
 * description "USGS Imagery Only is a tile cache base map service of orthoimagery"), and its tiles
 * behave exactly like its siblings' — real JPEGs through z16, 404 from z17, 404 outside the US
 * (Paris) — the same three-part check `scripts/verify-usgs-basemap.sh` already runs for the other
 * two USGS sources, extended to cover this one too.
 */
enum class MapMode(val label: String, val basemap: Basemap) {
    STREET(label = "Street", basemap = Basemap.OSM_STANDARD),
    TOPOGRAPHIC(label = "Topographical", basemap = Basemap.OPEN_TOPO_MAP),
    SATELLITE(label = "Satellite", basemap = Basemap.USGS_IMAGERY_ONLY),
    ;

    companion object {
        /** Topographical, via OpenStreetMap — the same basemap [MapService.DEFAULT]'s topo mode already opened on. */
        val DEFAULT = TOPOGRAPHIC
    }
}
