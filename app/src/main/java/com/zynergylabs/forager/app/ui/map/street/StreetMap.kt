package com.zynergylabs.forager.app.ui.map.street

import com.zynergylabs.forager.app.ui.map.AddedMapType
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapMode

/**
 * The Street map: OpenStreetMap's own standard raster, online only (RECORD -1554), the third chip, "Street" (dispatch
 * 2026-11-03-05 A2; it read "OSM" until then). Everything about it is here, so the app can drop it cleanly (D7, RECORD
 * -2001: "keep all OSM references detached and easily removable"): its [Basemap] and [MapMode], its tile address and
 * credit, the night raster a Topographical night draws below its fade, and its place as the raster that stands in for
 * Trail Map when Trail Map's style fails to load (-1513). The app reaches it only through [AddedMapType], registered by
 * one line in `ADDED_MAP_TYPES`.
 *
 * Its tile address, its credit and OpenStreetMap's tile policy stay word for word (A2). Its stored key "street" is
 * the one Trail Map held until this dispatch (A3, B1).
 */
internal object StreetMap : AddedMapType {
    /** OpenStreetMap's raster: no stored tile, ever (its tile policy forbids it, -1554). */
    val BASEMAP = Basemap(
        name = "STREET",
        label = "OpenStreetMap",
        description = "OpenStreetMap's own standard map: roads, paths and buildings. Online only: never downloaded (RECORD -1554).",
        maxZoom = 19,
        attribution = "© OpenStreetMap contributors",
        tileUrlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    )

    /**
     * The chip reads "Street" (A2, RECORD -1997, the owner: "The OSM button should be renamed to "Street" to differentiate
     * itself still. OSM credits can still apply"; it read "OSM" from dispatch 2026-10-22-01). Stored as "street" (RECORD
     * -1999: "just use street for OSM"); a phone's "openstreetmap" is moved to it once (B1).
     */
    override val mapMode = MapMode(name = "STREET", label = "Street", basemap = BASEMAP, storageKey = "street")

    /** -1513: "If our Street fails to load, the app quietly draws today's OpenStreetMap Street instead and logs it". */
    override val standsInForTrailMap = true

    /**
     * RECORD -324: at topo night, below 9.5, the layer is this map, which V1 turns dark at every zoom (`BasemapStyles.kt`,
     * `nightLowZoomBasemap`). Drawn only while Trail Map does not; with Trail Map drawing, Trail Map is below instead.
     */
    override val nightUnderTopoId = "basemap-street"

    /** D26: the credit never fades while these tiles are drawn, "as those tiles require". */
    override val creditNeverFades = true
}
