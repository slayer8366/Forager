package com.zynergylabs.forager.app.ui.map

/**
 * A map type the app offers beside its own two, Trail Map and Topographical, kept in a place of its own so it can be
 * deleted cleanly (dispatch 2026-11-03-05 D7, RECORD -2001). The owner: "We will migrate away from OSM altogether so we
 * should keep all OSM references detached and easily removable from this point on", and on its scope, "The Street map
 * first (Recommended)". The Street map, OpenStreetMap's own raster, is the one there is: `ui/map/street/`.
 *
 * The rest of the app asks these questions of the registered types and never names one. Deleting a type's package and
 * its line in [ADDED_MAP_TYPES] leaves the app compiling and working: its chip goes, a stored choice of it reads as
 * unknown and moves to [MapMode.REPLACEMENT_FOR_UNKNOWN] with a log line, and the roles below fall to the app's own
 * maps (CI's street-removable job builds and tests exactly that).
 *
 * A map-type plug-in only, not a layer system: the owner's later direction (RECORD -2002), our own street base with
 * trails, relief and topo as layers, is not built here.
 */
internal interface AddedMapType {
    /** Its chip, its stored key and, through [MapMode.basemap], its tiles, credit and zoom limit. */
    val mapMode: MapMode

    /**
     * Whether this raster draws in Trail Map's place when Trail Map's style fails to load (`OwnTrailFallback`, RECORD
     * -1513). With none, [trailMapStandIn] is `null` and the owner's "Downloads, then Topo" applies (RECORD -2003).
     */
    val standsInForTrailMap: Boolean

    /**
     * The source and layer id of this raster when a night Topographical style draws it below the night fade, while
     * Trail Map does not draw (RECORD -324, -1513); `null` when it never does. With none, topo at night draws topo alone
     * then.
     */
    val nightUnderTopoId: String?

    /** Whether this map's tiles ask for their credit never to fade (D26: OpenStreetMap's tile policy). */
    val creditNeverFades: Boolean
}

/** The added map types, in chip order after the app's own two. */
internal val ADDED_MAP_TYPES: List<AddedMapType> = buildList {
    add(com.zynergylabs.forager.app.ui.map.street.StreetMap) // The Street map (D7): delete this line with ui/map/street/.
}

/** The raster drawn in Trail Map's place when its style fails to load, or `null` when no added type stands in. */
internal val trailMapStandIn: Basemap?
    get() = ADDED_MAP_TYPES.firstOrNull { it.standsInForTrailMap }?.mapMode?.basemap

/** The added type a night Topographical style draws below its fade while Trail Map does not draw, if any. */
internal val nightUnderTopo: AddedMapType?
    get() = ADDED_MAP_TYPES.firstOrNull { it.nightUnderTopoId != null }

/** Whether [this] is an added type's raster whose credit never fades ([AddedMapType.creditNeverFades]). */
internal fun Basemap.creditNeverFades(): Boolean = ADDED_MAP_TYPES.any { it.creditNeverFades && it.mapMode.basemap == this }
