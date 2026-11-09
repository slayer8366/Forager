package com.zynergylabs.forager.app.ui.map

import android.util.Log
import com.zynergylabs.forager.app.ui.theme.MapPalette
import com.zynergylabs.forager.app.ui.map.layers.LayerPaint
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayerSpec
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.OpacityProperty
import com.zynergylabs.forager.app.ui.map.layers.OpacityValue
import com.zynergylabs.forager.app.ui.map.layers.COLOUR_FIELDS
import com.zynergylabs.forager.app.ui.map.layers.layerPaintFor
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Adds every overlay source and layer this composable draws, all starting with empty
 * `FeatureCollection`s — called once per loaded [Style], from [SightingsMap]'s basemap-swap effect.
 *
 * Split from [refreshOverlayData] (which pushes the real data) because `setStyle` throws away the
 * previous style's sources and layers wholesale: a basemap swap needs both — the shape rebuilt here,
 * the content pushed there — while a plain data change (a new search, a new planned trip) only ever
 * needs the second.
 *
 * **Layer add order is the draw order (later added draws on top), and since map layers L0a that
 * order is the registry's**: this walks [drawOrder] (`orderedLayers` of `MAP_LAYER_REGISTRY`) and adds
 * one native layer per spec, each source the first time a layer needs it. Nothing here orders
 * anything by hand; to move a layer, move it in the registry, whose doc comment has the groups
 * (colour fields < areas < lines < markers) and the reasons for the order within each. The comment
 * this replaced ("search centre, sightings, planned trips last", from the osmdroid overlay list) was
 * stale before L0a: it predated the breadcrumb, the kept tracks and the offline circles, which were
 * all inserted between those. One visible change came with the registry and was accepted by the
 * owner: the search centre and the sighting dots now draw above the track lines.
 *
 * Each layer is built with today's paint and layout ([nativeLayerFor]), then given its visibility
 * and opacities from [layersState] ([layerPaintFor]) before it is added, so a layer's opacity is
 * written in one place, the registry's base opacities. At the default state that is exactly the
 * paint it always had.
 *
 * [palette] is the Night Maps palette ([MapPalette.forMode], colour build C2), so every colour here
 * follows the toggle. The point markers other than the sighting dot are bitmap [SymbolLayer]s, each
 * its own silhouette (`MarkerGlyphs.kt`, [MarkerIcon]) with its casing drawn into the bitmap and its
 * anchor at the bitmap's centre, hence `icon-anchor: center` for all of them. The sighting dot stays
 * a [CircleLayer] whose own ring is its casing; the tracks, the offline outline and its night border
 * are line layers ([trackLayerSpecs], [offlineRegionOutlineSpec], [offlineRegionBorderSpec]).
 *
 * The sighting layer's `circle-stroke-color`/`circle-stroke-width` are fixed expressions keyed on
 * each feature's own `"selected"` boolean property — see [sightingStrokeColorExpression]'s own doc
 * comment for why that property, not a paint-property expression comparing `observationId`
 * directly, is what actually selects the ring. Nothing here needs to know which sighting is
 * currently focused: [refreshOverlayData] bakes `"selected"` into the pushed data itself.
 *
 * Runs after the offline night recolour in the `setStyle` callback, never before: that recolour
 * walks every layer present, and the overlays must not be among them.
 */
internal fun initializeOverlayLayers(
    style: Style,
    density: Float,
    palette: MapPalette,
    drawOrder: List<MapLayerSpec>,
    layersState: MapLayersState,
    chromeColour: Int,
) {
    // Every bitmap marker's image, in this palette's colours. Registered here and nowhere else.
    MarkerIcon.entries.forEach { style.addImage(it.imageId, markerIconImage(it, palette, density).bitmap) }

    val addedSources = mutableSetOf<String>()
    for (spec in drawOrder) {
        val layer = nativeLayerFor(spec, palette)
        if (layer == null) {
            // A registry entry this function cannot build is a programming error, never a silent
            // gap: logged by id, and the rest of the overlays still draw.
            Log.w(SIGHTINGS_MAP_TAG, "No native layer is defined for registry layer ${spec.id}; it is not drawn.")
            continue
        }
        if (addedSources.add(spec.sourceId)) style.addSource(GeoJsonSource(spec.sourceId, emptyFeatureCollection()))
        layer.setProperties(*paintProperties(layerPaintFor(spec, layersState)))
        style.addLayer(layer)
    }
    // Dispatch -497: the way back while returning (RouteHomeLayers.kt), outside the registry as the
    // fan-out is: its lines above every track line (the kept tracks are the registry's last line, above
    // the live breadcrumb), below every marker; the arrival ring above the waypoints.
    if (style.getLayer(MapLayerIds.KEPT_TRACKS) != null && style.getLayer(MapLayerIds.WAYPOINTS) != null) {
        addRouteHomeLayers(style, palette, density, aboveLineLayerId = MapLayerIds.KEPT_TRACKS, aboveMarkerLayerId = MapLayerIds.WAYPOINTS)
    } else {
        Log.w(SIGHTINGS_MAP_TAG, "The kept-tracks or waypoints layer is missing; the way back is not drawn.")
    }
    // The marker fan-out's own layers, above every registry layer (FanOutLayers.kt).
    addFanOutLayers(style, palette, chromeColour, density)
}

/**
 * The native layer for [spec], with today's paint and layout and no opacity (that comes from the
 * registry, [paintProperties]); `null` for a registry id this file has no builder for.
 */
private fun nativeLayerFor(spec: MapLayerSpec, palette: MapPalette): Layer? {
    markerIconForLayer(spec.id)?.let { return markerSymbolLayer(spec.id, spec.sourceId, it) }
    // A colour field (map layers L0b): a fill whose colour is its ramp on each cell's chance. Its
    // opacity is the registry's (a base of 0.6, owner's ruling on Q10), set with the rest of its paint.
    COLOUR_FIELDS.firstOrNull { it.layerId == spec.id }?.let { field ->
        return FillLayer(spec.id, spec.sourceId).withProperties(PropertyFactory.fillColor(colourFieldFillColour(field.ramp)))
    }
    lineSpecForLayer(spec.id)?.let { return lineLayerFor(it, palette) }
    return when (spec.id) {
        // Lowest of all (the areas group), so a coverage circle never covers a marker or a line.
        OFFLINE_REGION_CIRCLE_LAYER_ID -> FillLayer(spec.id, spec.sourceId).withProperties(
            PropertyFactory.fillColor(palette.offlineRegion),
        )
        // One shared layer for every observation, styled once — unlike osmdroid, which built one
        // Drawable and stamped it per Marker, MapLibre draws every feature in the source with the
        // same layer properties, so there is nothing per-sighting to construct here.
        SIGHTING_LAYER_ID -> CircleLayer(spec.id, spec.sourceId).withProperties(*sightingCircleProperties(palette))
        else -> null
    }
}

/**
 * The bitmap marker a symbol layer draws, by layer id; `null` for a layer that is not a bitmap
 * marker. The search centre is a reticle since colour build C2 (d); planned trips are flags anchored
 * at the pole foot; waypoints pins anchored at the tip; finds a mushroom at its stem foot; photos a
 * rounded square with a camera, centred. `internal` so `MapLayerRegistryTest`'s palette check reads
 * what the map draws.
 */
internal fun markerIconForLayer(layerId: String): MarkerIcon? = when (layerId) {
    SEARCH_CENTER_LAYER_ID -> MarkerIcon.SEARCH_CENTRE
    PLANNED_TRIP_LAYER_ID -> MarkerIcon.PLANNED_TRIP
    WAYPOINT_LAYER_ID -> MarkerIcon.WAYPOINT
    FIND_LAYER_ID -> MarkerIcon.FIND
    PHOTO_LAYER_ID -> MarkerIcon.PHOTO
    // J8: the journal-entry halos under the three marker kinds.
    MapLayerIds.JOURNAL_ENTRY_WAYPOINTS -> MarkerIcon.WAYPOINT_JOURNAL_HALO
    MapLayerIds.JOURNAL_ENTRY_FINDS -> MarkerIcon.FIND_JOURNAL_HALO
    MapLayerIds.JOURNAL_ENTRY_PHOTOS -> MarkerIcon.PHOTO_JOURNAL_HALO
    else -> null
}

/**
 * The line a line layer draws, by layer id: the offline region's dashed outline (its casing, colour
 * build C2 (c)) and the night border beneath it ([offlineRegionBorderSpec]), and the breadcrumb and
 * kept tracks each with its casing ([trackLayerSpecs]: the breadcrumb dashed, see
 * [BREADCRUMB_DASH_PATTERN]; the kept tracks solid, see [keptTracksFeatureCollection]). `null` for a
 * layer that is not one of these.
 */
internal fun lineSpecForLayer(layerId: String): LineLayerSpec? =
    (listOf(offlineRegionBorderSpec(), offlineRegionOutlineSpec()) + trackLayerSpecs() + journalHaloLineSpecs()).singleOrNull { it.layerId == layerId }

/**
 * A bitmap marker's [SymbolLayer]. Every [MarkerIcon]'s image has its anchor at its exact centre
 * ([drawGlyph]), so `icon-anchor: center` puts the pin tip, stem foot, pole foot or centre on the
 * feature's coordinate, with no `icon-offset`.
 */
private fun markerSymbolLayer(layerId: String, sourceId: String, icon: MarkerIcon): SymbolLayer =
    SymbolLayer(layerId, sourceId).withProperties(
        PropertyFactory.iconImage(icon.imageId),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
    )

/**
 * [paint] as MapLibre property values: `visibility` (a layout property) and each opacity paint
 * property. Every [OpacityProperty] maps to its own `PropertyFactory` setter, each checked with
 * `javap` against the pinned `13.5.0` artifact (`visibility(String)`, `fillOpacity(Float)`,
 * `lineOpacity(Float)`, `circleOpacity(Float)`, `circleStrokeOpacity(Float)`, `iconOpacity(Float)`).
 */
private fun paintProperties(paint: LayerPaint): Array<PropertyValue<*>> = buildList<PropertyValue<*>> {
    add(PropertyFactory.visibility(if (paint.visible) Property.VISIBLE else Property.NONE))
    paint.opacities.forEach { add(opacityPropertyValue(it)) }
}.toTypedArray()

private fun opacityPropertyValue(value: OpacityValue): PropertyValue<Float> = when (value.property) {
    OpacityProperty.FILL -> PropertyFactory.fillOpacity(value.value)
    OpacityProperty.LINE -> PropertyFactory.lineOpacity(value.value)
    OpacityProperty.CIRCLE -> PropertyFactory.circleOpacity(value.value)
    OpacityProperty.CIRCLE_STROKE -> PropertyFactory.circleStrokeOpacity(value.value)
    OpacityProperty.ICON -> PropertyFactory.iconOpacity(value.value)
}

/**
 * Sets [paint] on the loaded style's layer of that id, in place. A layer the style does not have
 * is logged, not skipped silently: every registry layer is added on each style load, so a missing
 * one means that add failed.
 */
internal fun applyLayerPaint(style: Style, paint: LayerPaint) {
    val layer = style.getLayer(paint.layerId)
    if (layer == null) {
        Log.w(SIGHTINGS_MAP_TAG, "Layer ${paint.layerId} is not in the loaded style; its visibility and opacity were not set.")
        return
    }
    layer.setProperties(*paintProperties(paint))
}
