package com.zynergylabs.forager.app.ui.map

import android.util.Log
import com.zynergylabs.forager.app.ui.map.layers.MapLayerSpec
import com.zynergylabs.forager.app.ui.map.layers.ZGroup
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.SymbolLayer

// Names and icons over the app's lines (dispatch 2026-11-06-01 Amendment 1, category I; RECORD -2183). The owner, verbatim:
// "can we have street and trail names overlap the navigation line rather than be written under?"; "All lines the app
// draws"; "Names and icons on top"; on Trail Map, "Under all names, trail no. too" (I5); for the forecast colours, the
// offline tint and the journal rings, "Move down with the lines (Recommended)" (I6); for the fan-out legs, "Stay with the
// markers" (I7). The planner's reading after I7: on Trail Map the app's block from the forecast colours up to its top line
// moves, in its present order, to just under Trail Map's first name or icon layer; Topographical (day and night), Street
// and the offline style keep today's placement, logged once per style load.
//
// How the block moves without a second ordering: `initializeOverlayLayers` adds each registry layer either on top, as
// before, or just below the names anchor ([overlayPlacement]). Every later app layer is added relative to a registry
// layer, so it follows its own: the way home and the way to a waypoint above the kept tracks, the plotted route and its
// stop dots below the way home, so all of them land under the anchor too; the arrival ring and the waypoint names above
// the waypoints, and the fan-out on top, so they stay over the whole map. Adding in place was chosen over moving the
// layers after they are added: MapLibre 13.5.0's Android API has no move, only remove and add again, and a layer lost
// between the two would be a line missing from the map.

/** A basemap layer as the placement reads it: its id, and whether it writes or marks something (a symbol layer). */
internal data class BasemapLayer(val id: String, val namesOrIcons: Boolean)

/**
 * The basemap layer the app's block goes just under: the first of the basemap's top run of name and icon layers, or
 * `null` when the basemap's top layer is not one, and the app keeps today's placement, over the whole basemap.
 *
 * Why the top run and not the first symbol layer (the Category I pre-build report's premise 1): Trail Map v3 and v4 draw
 * the Forest Service trail number, `reference_fs_number`, among the roads (index 84 of v4's 182), so under the first
 * symbol layer every road, rail line, bridge and boundary would draw over the app's lines. The top run starts at
 * `address_label` on every served Trail Map, v1 to v4, and would stay the anchor's start if the style moved the trail
 * number up among the labels.
 *
 * Why this keeps every other map as it is: Topographical and Street are each one raster picture, their names part of
 * the image; Topographical at night lays the Topographical picture over Trail Map as the top layer
 * ([trailWithTopoOverBuilder]); the offline style has no symbol layer. In each the top layer is not a name or icon.
 */
internal fun namesAnchorOf(basemap: List<BasemapLayer>): String? =
    basemap.getOrNull(basemap.indexOfLast { !it.namesOrIcons } + 1)?.id

/**
 * The registry groups that go under the names: the lines (I2), and with them the forecast colours, the areas (the
 * offline tint) and the decorations (the journal rings), so they stay under the lines as now (I6). The markers stay on top.
 */
internal val GROUPS_UNDER_NAMES: Set<ZGroup> = setOf(ZGroup.COLOUR_FIELDS, ZGroup.AREAS, ZGroup.DECORATIONS, ZGroup.LINES)

/** Where `initializeOverlayLayers` adds a registry layer. */
internal sealed interface OverlayPlacement {
    /** On top of everything added so far: today's placement. */
    data object OnTop : OverlayPlacement

    /** Just below [layerId], the names anchor, and so above every app layer already put there. */
    data class Below(val layerId: String) : OverlayPlacement
}

/** Where [spec] goes on a basemap whose names anchor is [namesAnchor] (`null`: today's placement). */
internal fun overlayPlacement(spec: MapLayerSpec, namesAnchor: String?): OverlayPlacement =
    if (namesAnchor != null && spec.zGroup in GROUPS_UNDER_NAMES) OverlayPlacement.Below(namesAnchor) else OverlayPlacement.OnTop

/** The line logged once per style load: where the app's layers went on [basemap], and why. */
internal fun namesAnchorLogLine(namesAnchor: String?, basemap: List<BasemapLayer>): String =
    if (namesAnchor != null) {
        "App lines under the map's names: the forecast colours up to the way home go below $namesAnchor " +
            "(layer ${basemap.indexOfFirst { it.id == namesAnchor }} of ${basemap.size}); markers, names and the fan-out stay on top."
    } else {
        "App layers over the whole map (${basemap.size} layers), today's placement: " +
            "its top layer ${basemap.lastOrNull()?.id ?: "(none)"} is not a name or icon layer."
    }

/** The loaded [style]'s names anchor ([namesAnchorOf]), read before any app layer is added, and logged. */
internal fun readNamesAnchor(style: Style): String? {
    val basemap = style.layers.map { BasemapLayer(it.id, it is SymbolLayer) }
    val anchor = namesAnchorOf(basemap)
    Log.i(SIGHTINGS_MAP_TAG, namesAnchorLogLine(anchor, basemap))
    return anchor
}

/** Adds [layer] to [style] where [placement] says. */
internal fun addOverlayLayer(style: Style, layer: Layer, placement: OverlayPlacement) {
    when (placement) {
        OverlayPlacement.OnTop -> style.addLayer(layer)
        is OverlayPlacement.Below -> style.addLayerBelow(layer, placement.layerId)
    }
}
