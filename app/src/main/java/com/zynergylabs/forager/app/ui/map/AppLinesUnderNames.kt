package com.zynergylabs.forager.app.ui.map

import android.util.Log
import com.zynergylabs.forager.app.ui.map.layers.LayerRenderer
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
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
 * Why the top run and not the first symbol layer (the Category I pre-build report's premise 1): Trail Map v3 draws the
 * Forest Service trail number, `reference_fs_number`, among the roads, as v4 did until I8 (index 84 of its 182), so under
 * the first symbol layer every road, rail line, bridge and boundary would draw over the app's lines. The top run starts
 * at `address_label` on v1 to v3. Since I8 (dispatch 2026-11-06-01 Amendment 1; the owner: "Update v4 in place") v4 draws
 * the trail number at the bottom of its labels, so on v4 the run starts with it (index 116 of 182) and the app's lines
 * go under it too, by the same rule.
 *
 * Why this keeps every other map as it is: Topographical and Street are each one raster picture, their names part of
 * the image; Topographical at night lays the Topographical picture over Trail Map as the top layer
 * ([trailWithTopoOverBuilder]); the offline style has no symbol layer. In each the top layer is not a name or icon.
 *
 * [layers] is the whole of `style.layers`; only the basemap's own count ([isBasemapOwnLayer]). I9 (dispatch 2026-11-06-01
 * Amendment 1, RECORD -2183), from the S22 check: after a style reload MapLibre's position-dot layers are already in the
 * style, symbol layers on top, and counting them put Topographical's and Street's app layers under the dot instead of
 * keeping today's placement. Trail Map's anchor is the same with or without them.
 */
internal fun namesAnchorOf(layers: List<BasemapLayer>): String? {
    val basemap = basemapOwnLayers(layers)
    return basemap.getOrNull(basemap.indexOfLast { !it.namesOrIcons } + 1)?.id
}

/**
 * The prefix of every layer MapLibre 13.5.0's position dot adds (`LocationComponentConstants`, read with `javap` on the
 * pinned AAR: `mapbox-location-shadow-layer`, `-foreground-layer`, `-background-layer`, `-accuracy-layer`,
 * `-bearing-layer`, `-pulsing-circle-layer`). `MapLibreMap.notifyStyleLoaded` has `LocationComponent.onFinishLoadingStyle`
 * add them before the app's `setStyle` callback runs, on every style load after the dot was first turned on.
 */
internal const val MAPLIBRE_LOCATION_LAYER_PREFIX = "mapbox-location-"

/**
 * Every layer the app adds on a style load: the registry's own (not the style's own band layers, which are the
 * basemap's) and the adds after it, the way home, the plotted route, the waypoint names and the fan-out. None is in the
 * style when the anchor is read today, since `setStyle` drops them and the anchor is read before the first add; I9 asks
 * for them to be left out should that change. The Topographical picture laid over Trail Map at night is not here: it is
 * the map the user sees, and it is what keeps that map at today's placement.
 */
internal val APP_OVERLAY_LAYER_IDS: Set<String> by lazy {
    MAP_LAYER_REGISTRY.filter { it.renderer != LayerRenderer.STYLE_LAYER }.map { it.id }.toSet() + setOf(
        RouteHomeIds.STRAIGHT_LAYER, RouteHomeIds.STRAIGHT_OFFLINE_LAYER, RouteHomeIds.PASSED_LAYER, RouteHomeIds.AHEAD_CASING_LAYER,
        RouteHomeIds.AHEAD_LAYER, RouteHomeIds.AHEAD_DOTTED_LAYER, RouteHomeIds.AHEAD_STRAIGHT_LAYER, RouteHomeIds.ARRIVED_LAYER,
        PlottedRouteIds.CASING_LAYER, PlottedRouteIds.STRAIGHT_CASING_LAYER, PlottedRouteIds.TRAIL_LAYER, PlottedRouteIds.STRAIGHT_LAYER,
        PlottedRouteIds.STOPS_LAYER, WaypointNameIds.LAYER, WaypointNameIds.STOP_LAYER,
        FanOutIds.LEGS_CASING_LAYER, FanOutIds.LEGS_LAYER, FanOutIds.DOTS_LAYER, FanOutIds.ICONS_LAYER,
    )
}

/** Whether the layer [id] is the basemap's own: neither MapLibre's position dot nor one the app adds. */
internal fun isBasemapOwnLayer(id: String): Boolean = !id.startsWith(MAPLIBRE_LOCATION_LAYER_PREFIX) && id !in APP_OVERLAY_LAYER_IDS

/** The basemap's own layers among [layers], in their order ([isBasemapOwnLayer]). */
internal fun basemapOwnLayers(layers: List<BasemapLayer>): List<BasemapLayer> = layers.filter { isBasemapOwnLayer(it.id) }

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

/**
 * The line logged once per style load: where the app's layers went on the basemap of [layers] (all of `style.layers`),
 * and why. The layer number and count are among the basemap's own layers; any layer left out ([isBasemapOwnLayer]) is
 * named, so the S22 logcat step can see what was in the style (I9).
 */
internal fun namesAnchorLogLine(namesAnchor: String?, layers: List<BasemapLayer>): String {
    val basemap = basemapOwnLayers(layers)
    val placement = if (namesAnchor != null) {
        "App lines under the map's names: the forecast colours up to the way home go below $namesAnchor " +
            "(layer ${basemap.indexOfFirst { it.id == namesAnchor }} of ${basemap.size}); markers, names and the fan-out stay on top."
    } else {
        "App layers over the whole map (${basemap.size} layers), today's placement: " +
            "its top layer ${basemap.lastOrNull()?.id ?: "(none)"} is not a name or icon layer."
    }
    val leftOut = layers.filterNot { isBasemapOwnLayer(it.id) }
    return if (leftOut.isEmpty()) placement else "$placement Left out as not the map's own: ${leftOut.joinToString { it.id }}."
}

/**
 * The loaded [style]'s names anchor ([namesAnchorOf]), read before any app layer is added, and logged. Every layer in the
 * style is handed on, MapLibre's position dot included; [namesAnchorOf] counts only the basemap's own (I9).
 */
internal fun readNamesAnchor(style: Style): String? {
    val layers = style.layers.map { BasemapLayer(it.id, it is SymbolLayer) }
    val anchor = namesAnchorOf(layers)
    Log.i(SIGHTINGS_MAP_TAG, namesAnchorLogLine(anchor, layers))
    return anchor
}

/** Adds [layer] to [style] where [placement] says. */
internal fun addOverlayLayer(style: Style, layer: Layer, placement: OverlayPlacement) {
    when (placement) {
        OverlayPlacement.OnTop -> style.addLayer(layer)
        is OverlayPlacement.Below -> style.addLayerBelow(layer, placement.layerId)
    }
}
