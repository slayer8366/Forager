package com.forager.app.ui.map

/**
 * The two tile providers a user picks between in Settings, each grouping the two [Basemap]s that
 * render its "topo" and "regular" look.
 *
 * This sits one level above [Basemap] rather than replacing it: [Basemap] is still the catalogue
 * of what osmdroid actually draws from (label, coverage, zoom ceiling, attribution), unchanged by
 * this file. What changed in the redesign this type belongs to is *how a basemap gets chosen* —
 * previously one flat four-way dropdown in the app bar (`BasemapSelector`, since removed), now two
 * separate decisions with two different lifetimes: which **service** to use (occasional — Settings,
 * see `AvailabilityScreen`'s `ChooseMapsServiceSection`) and which **mode** that service is in right
 * now (frequent, a during-the-walk decision — the quick-fire icon overlaid on the map itself, see
 * `MapModeToggle`). [isTopoMode][MapModeToggle] is independent `Boolean` state that survives a
 * service switch: "if a map has two modes, toggle the two" is the whole rule, so switching from
 * OpenStreetMap-in-topo-mode to USGS lands on USGS Topo, not on whatever USGS's own default would
 * otherwise be.
 *
 * ## Why the default changed from [Basemap]'s own default (USGS Topo, PR #13) to OpenStreetMap
 *
 * PR #13 shipped USGS Topo as the app's opening basemap, reasoned there as the right trade for
 * *deciding where to walk* — terrain beats roads. That basemap is still on offer and still the
 * better read for a wooded search, unchanged. But USGS covers the United States only (see
 * [Basemap]'s own doc comment), and defaulting to a US-only source is a worse trade for *opening
 * the app for the first time* than it is for a browsing choice you can already see and change:
 * every user outside the US would open on a blank map before ever touching a control. OpenStreetMap
 * has no such gap. This is the project owner's explicit call, not a reversal for its own sake — USGS
 * remains one tap away in Settings, same as before.
 *
 * ## Why offline downloading only ever reaches [USGS]
 *
 * See `OfflineMapRepository`'s doc comment and the "Offline Maps" section of `AvailabilityScreen`'s
 * Settings panel for the full reasoning (osmdroid's own [TileSourcePolicy][org.osmdroid.tileprovider.tilesource.TileSourcePolicy]
 * marks OpenStreetMap's `MAPNIK` source `FLAG_NO_BULK`, citing the OSM tile usage policy, and this
 * project's own research — one step removed from the primary source, this environment's egress
 * proxy blocks both operations.osmfoundation.org and opentopomap.org directly — found OpenTopoMap's
 * usage policy states the same for its own tile server). The gate that matters is structural, not
 * conventional: [MapService] is the only type the offline-download UI is ever handed a basemap
 * through, and [OPEN_STREET_MAP] simply has no download entry point wired to it — see
 * `AvailabilityScreen`'s `OfflineMapsSection`, gated on `selectedMapService == MapService.USGS`.
 */
enum class MapService(
    val label: String,
    val topoBasemap: Basemap,
    val regularBasemap: Basemap,
) {
    OPEN_STREET_MAP(
        label = "OpenStreetMap",
        topoBasemap = Basemap.OPEN_TOPO_MAP,
        regularBasemap = Basemap.OSM_STANDARD,
    ),

    USGS(
        label = "USGS",
        topoBasemap = Basemap.USGS_TOPO,
        regularBasemap = Basemap.USGS_IMAGERY_TOPO,
    ),
    ;

    /** The basemap this service resolves to right now, given the shared topo/regular mode flag. */
    fun basemapFor(isTopoMode: Boolean): Basemap = if (isTopoMode) topoBasemap else regularBasemap

    companion object {
        /** OpenStreetMap, not USGS — see this file's doc comment for why the default changed. */
        val DEFAULT = OPEN_STREET_MAP
    }
}
