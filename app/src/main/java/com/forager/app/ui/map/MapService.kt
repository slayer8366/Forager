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
 * ## Offline downloading has nothing to do with this enum at all
 *
 * An earlier revision gated Settings' "Offline Maps" section on `selectedMapService == USGS`, since
 * offline downloads only ever fetch USGS tiles. The project owner's own framing, after seeing it
 * built: since offline downloads only ever use USGS regardless, there's no reason for the *feature*
 * to react to which service is currently selected for live browsing — the two are independent
 * decisions, and coupling one's reachability to the other's live value was a leftover of an earlier
 * design where the download style *did* follow the live selection. So "Offline Maps" is reachable
 * unconditionally now; `com.forager.app.domain.OfflineMapRepository` hardcodes USGS Topo rather than
 * accepting a tile source at all, which is what actually keeps the feature USGS-only — see
 * `OsmdroidOfflineMapRepository`'s doc comment for the full citation trail behind *why* it's
 * USGS-only in the first place (osmdroid's own [TileSourcePolicy][org.osmdroid.tileprovider.tilesource.TileSourcePolicy]
 * marks OpenStreetMap's `MAPNIK` source `FLAG_NO_BULK`, and this project's own one-step-removed
 * research found OpenTopoMap's tile server states the same).
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
