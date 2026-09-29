package com.zynergylabs.forager.app.ui.map

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.fanout.FanMember
import com.zynergylabs.forager.app.ui.map.fanout.MapProbe
import com.zynergylabs.forager.app.ui.map.fanout.ProbedMarker
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.LayerKind
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.TapGroup
import com.zynergylabs.forager.app.ui.map.layers.TapHit
import com.zynergylabs.forager.app.ui.theme.MapPalette
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng as MapLibreLatLng

/*
 * The map-side half of the marker fan-out (dispatch 2026-09-28-197). The fanned markers are copies:
 * the originals of a fanned stack are filtered out of their own layers while the fan is up, and the
 * copies are drawn from four sources of this file's own, above every registry layer, moving from
 * their true spot to their ring place as the fan's progress goes 0 to 1. The arithmetic is in
 * `fanout/MarkerFanOut.kt`; the SDK-facing calls are here, and are device-only: a `MapView` cannot be
 * built under Robolectric, so what the tests reach is [fanFrameCollections] (the features pushed) and
 * [fanOutHiddenFilter] (the expression built), not that the map then draws them.
 */

internal object FanOutIds {
    const val LEGS_SOURCE = "fan-out-legs-source"
    const val LEGS_CASING_LAYER = "fan-out-legs-casing-layer"
    const val LEGS_LAYER = "fan-out-legs-layer"
    const val HALOS_SOURCE = "fan-out-halos-source"
    const val HALOS_LAYER = "fan-out-halos-layer"
    const val DOTS_SOURCE = "fan-out-dots-source"
    const val DOTS_LAYER = "fan-out-dots-layer"
    const val ICONS_SOURCE = "fan-out-icons-source"
    const val ICONS_LAYER = "fan-out-icons-layer"

    /** The feature property naming the bitmap a copy or a halo draws. */
    const val IMAGE_PROPERTY = "image"
}

/** The leg's own line width, in dp: "a thin line". Its casing is [CASING_WIDTH_DP] wider on each side, as a track's is. */
internal const val FAN_LEG_WIDTH_DP = 1.5f

/**
 * Adds the fan-out's sources and layers, empty, above everything already in [style]: legs (a casing
 * and its line), then halos, sighting dots, and marker icons. Called once per style load, after the
 * registry's layers, since a layer added later draws on top.
 */
internal fun addFanOutLayers(style: Style, palette: MapPalette) {
    val empty = FeatureCollection.fromFeatures(emptyList())
    listOf(FanOutIds.LEGS_SOURCE, FanOutIds.HALOS_SOURCE, FanOutIds.DOTS_SOURCE, FanOutIds.ICONS_SOURCE)
        .forEach { style.addSource(GeoJsonSource(it, empty)) }

    fun leg(id: String, colour: Int, widthDp: Float) = LineLayer(id, FanOutIds.LEGS_SOURCE).withProperties(
        PropertyFactory.lineColor(colour),
        PropertyFactory.lineWidth(widthDp),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
    )
    style.addLayer(leg(FanOutIds.LEGS_CASING_LAYER, palette.casing, FAN_LEG_WIDTH_DP + 2 * CASING_WIDTH_DP))
    style.addLayer(leg(FanOutIds.LEGS_LAYER, palette.searchCentre, FAN_LEG_WIDTH_DP))

    fun symbols(id: String, sourceId: String) = SymbolLayer(id, sourceId).withProperties(
        PropertyFactory.iconImage(Expression.get(FanOutIds.IMAGE_PROPERTY)),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
    )
    style.addLayer(symbols(FanOutIds.HALOS_LAYER, FanOutIds.HALOS_SOURCE))
    // The dot: the sighting layer's own paint, so a fanned dot is the dot it was, ring and all.
    style.addLayer(CircleLayer(FanOutIds.DOTS_LAYER, FanOutIds.DOTS_SOURCE).withProperties(*sightingCircleProperties(palette)))
    style.addLayer(symbols(FanOutIds.ICONS_LAYER, FanOutIds.ICONS_SOURCE))
}

/** The four sources' contents at one moment of the fan. */
internal data class FanFrame(
    val legs: FeatureCollection,
    val halos: FeatureCollection,
    val dots: FeatureCollection,
    val icons: FeatureCollection,
)

private val EMPTY_FRAME = FanFrame(
    FeatureCollection.fromFeatures(emptyList()),
    FeatureCollection.fromFeatures(emptyList()),
    FeatureCollection.fromFeatures(emptyList()),
    FeatureCollection.fromFeatures(emptyList()),
)

/**
 * What the four sources hold for [members], each at the place [at] gives (their moving position).
 *
 *  - **legs:** a line from each member's true position to where it is now, so the line back to the
 *    true spot is there at every frame, including the first;
 *  - **icons:** each bitmap marker's own image ([markerIconForLayer]), so a copy keeps its icon;
 *  - **halos:** the journal-entry halo image under a copy whose record the shown entries keep
 *    ([highlights]), as the original's was;
 *  - **dots:** a sighting, with its `observationId` and its `selected` flag from [focusedObservationId],
 *    the properties the dot layer's own paint reads.
 *
 * A member whose layer draws no marker of ours is left out and logged, never drawn as a guess.
 */
internal fun fanFrameCollections(
    members: List<FanMember>,
    at: (FanMember) -> LatLng,
    highlights: JournalEntryHighlights,
    focusedObservationId: Long?,
): FanFrame {
    if (members.isEmpty()) return EMPTY_FRAME
    val legs = mutableListOf<Feature>()
    val halos = mutableListOf<Feature>()
    val dots = mutableListOf<Feature>()
    val icons = mutableListOf<Feature>()
    for (member in members) {
        val now = at(member)
        val layerId = member.key.layerId
        val image = markerIconForLayer(layerId)?.imageId
        when {
            layerId == MapLayerIds.SIGHTINGS -> dots += Feature.fromGeometry(Point.fromLngLat(now.lng, now.lat)).apply {
                val observationId = member.key.featureId.toLongOrNull()
                if (observationId != null) addNumberProperty("observationId", observationId)
                addBooleanProperty("selected", observationId != null && observationId == focusedObservationId)
            }
            image != null -> {
                icons += Feature.fromGeometry(Point.fromLngLat(now.lng, now.lat)).apply {
                    addStringProperty(FanOutIds.IMAGE_PROPERTY, image)
                    addStringProperty(FEATURE_ID_PROPERTY, member.key.featureId)
                }
                haloImageFor(layerId, member.key.featureId, highlights)?.let { halo ->
                    halos += Feature.fromGeometry(Point.fromLngLat(now.lng, now.lat)).apply {
                        addStringProperty(FanOutIds.IMAGE_PROPERTY, halo)
                    }
                }
            }
            else -> {
                Log.w(FAN_OUT_TAG, "A fanned marker on $layerId has no icon to draw; it is left out.")
                continue
            }
        }
        legs += Feature.fromGeometry(
            LineString.fromLngLats(listOf(Point.fromLngLat(member.lng, member.lat), Point.fromLngLat(now.lng, now.lat))),
        )
    }
    return FanFrame(
        legs = FeatureCollection.fromFeatures(legs),
        halos = FeatureCollection.fromFeatures(halos),
        dots = FeatureCollection.fromFeatures(dots),
        icons = FeatureCollection.fromFeatures(icons),
    )
}

/** The halo image under a copy of record [featureId] on [layerId], when the shown entries keep that record; `null` otherwise. */
private fun haloImageFor(layerId: String, featureId: String, highlights: JournalEntryHighlights): String? {
    fun kept(points: List<RecordPoint>) = points.any { it.recordId == featureId }
    return when (layerId) {
        MapLayerIds.WAYPOINTS -> MarkerIcon.WAYPOINT_JOURNAL_HALO.takeIf { kept(highlights.waypointMarkers) }
        MapLayerIds.FINDS -> MarkerIcon.FIND_JOURNAL_HALO.takeIf { kept(highlights.findMarkers) }
        MapLayerIds.PHOTOS -> MarkerIcon.PHOTO_JOURNAL_HALO.takeIf { kept(highlights.photoMarkers) }
        else -> null
    }?.imageId
}

/**
 * The filter that hides [ids] from a layer: every feature whose [property] is not one of them.
 * `["all"]` (an `all` of nothing, which is true of every feature) for none, which is also how a hidden
 * layer is let go, since this SDK version has no "remove filter" call that this file has verified. Not
 * `literal(true)`: whether the native filter parser takes a bare boolean is unverified, while `["all"]`
 * is the canonical match-everything filter. Device-only either way.
 */
internal fun fanOutHiddenFilter(property: String, ids: List<Any>): Expression =
    if (ids.isEmpty()) {
        Expression.all()
    } else {
        Expression.all(*ids.map { id ->
            when (id) {
                is Number -> Expression.neq(Expression.get(property), Expression.literal(id))
                else -> Expression.neq(Expression.get(property), Expression.literal(id.toString()))
            }
        }.toTypedArray())
    }

/**
 * Hides the originals of [members] from their own layers, and their journal halos, or, for an empty
 * list, shows everything again. Every marker layer and marker halo is set each time, so a fan that
 * changes layers, or folds, restores exactly what the last one hid. Called when the set of fanned
 * markers changes, not per frame.
 */
internal fun applyFanOutHiding(style: Style, members: List<FanMember>) {
    val idsByLayer = members.groupBy { it.key.layerId }
    val affected = MAP_LAYER_REGISTRY.filter { it.tapGroup == TapGroup.MARKER || (it.kind == LayerKind.MARKER && it.drawnWith != null) }
    for (spec in affected) {
        // A halo hides the same records its marker does.
        val owner = spec.drawnWith ?: spec.id
        val isSighting = owner == MapLayerIds.SIGHTINGS
        val ids = idsByLayer[owner].orEmpty().map { if (isSighting) (it.key.featureId.toLongOrNull() ?: it.key.featureId) else it.key.featureId }
        val filter = fanOutHiddenFilter(if (isSighting) "observationId" else FEATURE_ID_PROPERTY, ids)
        when (val layer = style.getLayer(spec.id)) {
            is SymbolLayer -> layer.setFilter(filter)
            is CircleLayer -> layer.setFilter(filter)
            null -> Log.w(FAN_OUT_TAG, "Layer ${spec.id} is not in the loaded style; its originals were not hidden.")
            else -> Log.w(FAN_OUT_TAG, "Layer ${spec.id} is a ${layer::class.java.simpleName}; its originals were not hidden.")
        }
    }
}

/**
 * Pushes [frame] into the four sources. A source missing from the style is logged, not skipped
 * silently: every style load adds all four ([addFanOutLayers]).
 */
internal fun pushFanFrame(style: Style, frame: FanFrame) {
    listOf(
        FanOutIds.LEGS_SOURCE to frame.legs,
        FanOutIds.HALOS_SOURCE to frame.halos,
        FanOutIds.DOTS_SOURCE to frame.dots,
        FanOutIds.ICONS_SOURCE to frame.icons,
    ).forEach { (sourceId, collection) ->
        val source = style.getSourceAs<GeoJsonSource>(sourceId)
        if (source == null) Log.w(FAN_OUT_TAG, "The $sourceId source is not in the loaded style; the fan was not drawn.") else source.setGeoJson(collection)
    }
}

/**
 * Where [member] is at [progress]: its true position on the screen now, plus its offset scaled by the
 * progress, converted back to a coordinate. Screen space, so a camera that moves while the fan folds
 * carries the copies with their true spots rather than stranding them.
 */
internal fun fanMemberLatLng(map: MapLibreMap, member: FanMember, progress: Float, density: Float): LatLng {
    val truth = map.projection.toScreenLocation(MapLibreLatLng(member.lat, member.lng))
    val moved = map.projection.fromScreenLocation(
        PointF(truth.x + member.offset.xDp * density * progress, truth.y + member.offset.yDp * density * progress),
    )
    return LatLng(moved.latitude, moved.longitude)
}

/**
 * [MapProbe] over the live map: `queryRenderedFeatures` for hits, one layer per call because a
 * returned feature does not say which layer drew it (as the click listener always did), and the
 * projection for each marker's own position.
 */
internal class MapLibreProbe(private val map: MapLibreMap, override val density: Float) : MapProbe {
    override fun hitsAt(xPx: Float, yPx: Float, layerIds: List<String>): List<TapHit> =
        layerIds.flatMap { id -> map.queryRenderedFeatures(PointF(xPx, yPx), id).map { tapHitOf(id, it) } }

    override fun hitsInBox(xPx: Float, yPx: Float, halfPx: Float, layerIds: List<String>): List<TapHit> {
        val box = RectF(xPx - halfPx, yPx - halfPx, xPx + halfPx, yPx + halfPx)
        return layerIds.flatMap { id -> map.queryRenderedFeatures(box, id).map { tapHitOf(id, it) } }
    }

    override fun markersInBox(xPx: Float, yPx: Float, halfPx: Float, layerIds: List<String>): List<ProbedMarker> {
        val box = RectF(xPx - halfPx, yPx - halfPx, xPx + halfPx, yPx + halfPx)
        return layerIds.flatMap { id ->
            map.queryRenderedFeatures(box, id).mapNotNull { feature ->
                val point = feature.geometry() as? Point ?: return@mapNotNull null
                val featureId = tapHitOf(id, feature).featureId ?: return@mapNotNull null
                val screen = map.projection.toScreenLocation(MapLibreLatLng(point.latitude(), point.longitude()))
                ProbedMarker(FanKey(id, featureId), point.latitude(), point.longitude(), screen.x, screen.y)
            }
        }.distinctBy { it.key }
    }
}

private const val FAN_OUT_TAG = "MarkerFanOut"
