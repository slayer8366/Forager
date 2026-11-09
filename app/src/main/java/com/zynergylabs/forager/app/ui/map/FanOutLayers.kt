package com.zynergylabs.forager.app.ui.map

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.createBitmap
import com.google.gson.JsonArray
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.fanout.FanMember
import com.zynergylabs.forager.app.ui.map.fanout.MapProbe
import com.zynergylabs.forager.app.ui.map.fanout.ProbedMarker
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.LayerKind
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayerSpec
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
import org.maplibre.android.style.layers.PropertyValue
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
 * copies are drawn from three sources of this file's own (the circles ride in the icons source as symbols), above every registry layer, moving from
 * their true spot to their ring place as the fan's progress goes 0 to 1. The arithmetic is in
 * `fanout/MarkerFanOut.kt`; the SDK-facing calls are here, and are device-only: a `MapView` cannot be
 * built under Robolectric, so what the tests reach is [fanFrameCollections] (the features pushed) and
 * [fanOutHiddenFilter] (the expression built), not that the map then draws them.
 */

internal object FanOutIds {
    const val LEGS_SOURCE = "fan-out-legs-source"
    const val LEGS_CASING_LAYER = "fan-out-legs-casing-layer"
    const val LEGS_LAYER = "fan-out-legs-layer"
    const val DOTS_SOURCE = "fan-out-dots-source"
    const val DOTS_LAYER = "fan-out-dots-layer"
    const val ICONS_SOURCE = "fan-out-icons-source"
    const val ICONS_LAYER = "fan-out-icons-layer"

    /** The image of the circle behind a copy, drawn as a symbol beside the copy's glyph (dispatch 2026-09-28-369, amendment 3, option A). */
    const val CIRCLE_IMAGE = "fan-out-circle-image"

    /** The feature property naming the bitmap a copy draws. */
    const val IMAGE_PROPERTY = "image"

    /** The feature property holding a copy's `icon-offset`, `[x, y]` in dp (dispatch 2026-09-28-275). */
    const val ICON_OFFSET_PROPERTY = "iconOffset"

    /** The feature property holding a circle's scale, 0 to 1 (dispatch 2026-09-28-299). */
    const val CIRCLE_SCALE_PROPERTY = "circleScale"

    /** The feature property holding a copy's `symbol-sort-key`: its registry layer's place in the draw order (dispatch 2026-09-28-318). */
    const val SORT_KEY_PROPERTY = "sortKey"

    /**
     * The selected stop's highlight over its copy while it fades or pulses (dispatch 2026-11-06-05, Amendment 7): the overlay's opacity and
     * size. Only that overlay carries them; every other copy is drawn full and at its size.
     */
    const val ACCENT_OPACITY_PROPERTY = "accentOpacity"
    const val ACCENT_SCALE_PROPERTY = "accentScale"
}

/** The leg's own line width, in dp: "a thin line". Its casing is [CASING_WIDTH_DP] wider on each side, as a track's is. */
internal const val FAN_LEG_WIDTH_DP = 1.5f

/**
 * Adds the fan-out's sources and layers, empty, above everything already in [style]: legs (a casing
 * and its line), then the background circles, sighting dots, and marker icons. Called once per style
 * load, after the registry's layers, since a layer added later draws on top. The live-location puck
 * is placed below the fan's lowest layer, [FanOutIds.LEGS_CASING_LAYER] (`liveLocationComponentOptions`
 * in SightingsMap.kt), so it draws above the registry's layers and below the whole fan; it is
 * activated after this runs, which is what lets it name a layer here. [chromeColour] is the
 * circles' colour to begin with; it is set again when the app's theme changes ([applyFanCircleStyle]).
 */
internal fun addFanOutLayers(style: Style, palette: MapPalette, chromeColour: Int, density: Float) {
    val empty = FeatureCollection.fromFeatures(emptyList())
    listOf(FanOutIds.LEGS_SOURCE, FanOutIds.DOTS_SOURCE, FanOutIds.ICONS_SOURCE)
        .forEach { style.addSource(GeoJsonSource(it, empty)) }
    // The circle behind each copy is a symbol in the icons layer, so it goes through the same pass as its glyph (below). Its image carries the colour.
    style.addImage(FanOutIds.CIRCLE_IMAGE, fanCircleBitmap(density, fanCircleStyle(chromeColour).colour))

    fun leg(id: String, colour: Int, widthDp: Float) = LineLayer(id, FanOutIds.LEGS_SOURCE).withProperties(
        PropertyFactory.lineColor(colour),
        PropertyFactory.lineWidth(widthDp),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
    )
    style.addLayer(leg(FanOutIds.LEGS_CASING_LAYER, palette.casing, FAN_LEG_WIDTH_DP + 2 * CASING_WIDTH_DP))
    style.addLayer(leg(FanOutIds.LEGS_LAYER, palette.searchCentre, FAN_LEG_WIDTH_DP))

    // The dot: the sighting layer's own paint, so a fanned dot is the dot it was, ring and all.
    style.addLayer(CircleLayer(FanOutIds.DOTS_LAYER, FanOutIds.DOTS_SOURCE).withProperties(*sightingCircleProperties(palette)))
    style.addLayer(SymbolLayer(FanOutIds.ICONS_LAYER, FanOutIds.ICONS_SOURCE).withProperties(*fanIconLayerProperties()))
}

/** The circle symbol's `symbol-sort-key`: below every glyph's (a glyph's is its layer's place in the draw order, or -1 when absent from it). */
internal const val FAN_CIRCLE_SORT_KEY = -1000

/**
 * The circle behind a copy at its rest size: [FAN_CIRCLE_DIAMETER_DP] across in [colour] at [FAN_CIRCLE_OPACITY], in device pixels like the glyph bitmaps, so
 * the symbol layer's `icon-size` (the progress) scales it. The colour is baked in, so a theme change re-registers the image ([applyFanCircleStyle]).
 */
internal fun fanCircleBitmap(density: Float, colour: Int): android.graphics.Bitmap {
    val px = kotlin.math.ceil(FAN_CIRCLE_DIAMETER_DP * density).toInt()
    val bitmap = createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = colour
        alpha = Math.round(255f * FAN_CIRCLE_OPACITY)
        style = android.graphics.Paint.Style.FILL
    }
    android.graphics.Canvas(bitmap).drawCircle(px / 2f, px / 2f, px / 2f, paint)
    return bitmap
}

/**
 * What one push of [frame] sends where: the legs, the dots, and the icons source with the circles in it. The circles are symbol features beside the glyph
 * copies (the same layer, so the same pass; ordered below every glyph by [FAN_CIRCLE_SORT_KEY]), so a circle and its glyph reach the renderer in one
 * `setGeoJson` and cannot be drawn from different pushes (amendment 3, option A). An empty frame is still three pushes, which clears every layer at once.
 */
internal fun fanPushPlan(frame: FanFrame): List<Pair<String, FeatureCollection>> = listOf(
    FanOutIds.LEGS_SOURCE to frame.legs,
    FanOutIds.DOTS_SOURCE to frame.dots,
    FanOutIds.ICONS_SOURCE to FeatureCollection.fromFeatures(frame.circles.features().orEmpty() + frame.icons.features().orEmpty()),
)

/**
 * The icon layer's own properties, a function so the test can read the values it is built with (a
 * `SymbolLayer` cannot be built under Robolectric, a `PropertyValue` can).
 */
internal fun fanIconLayerProperties(): Array<PropertyValue<*>> = arrayOf(
    PropertyFactory.iconImage(Expression.get(FanOutIds.IMAGE_PROPERTY)),
    PropertyFactory.iconAllowOverlap(true),
    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
    // A circle feature carries its scale (the fold's progress) and is drawn at that fraction of its rest size, so it grows from nothing as the fan
    // opens and is gone when it has folded (dispatch 2026-09-28-299); a glyph copy has no scale and is drawn at its size.
    PropertyFactory.iconSize(
        Expression.switchCase(
            Expression.has(FanOutIds.CIRCLE_SCALE_PROPERTY), Expression.number(Expression.get(FanOutIds.CIRCLE_SCALE_PROPERTY)),
            // Amendment 7: the selected stop's highlight swells once over its copy as it arrives.
            Expression.has(FanOutIds.ACCENT_SCALE_PROPERTY), Expression.number(Expression.get(FanOutIds.ACCENT_SCALE_PROPERTY)),
            Expression.literal(1f),
        ),
    ),
    // Amendment 7: the highlight over the selected stop's copy fades in (and a stop no longer selected fades out); every other copy is full.
    PropertyFactory.iconOpacity(Expression.coalesce(Expression.number(Expression.get(FanOutIds.ACCENT_OPACITY_PROPERTY)), Expression.literal(1f))),
    // Each copy's own offset: the glyph's centring offset times the fold's progress, so a copy's body is on its circle's
    // centre when spread and its anchor is on the coordinate, exactly where its original draws, when folded.
    PropertyFactory.iconOffset(Expression.get(FanOutIds.ICON_OFFSET_PROPERTY)),
    // Which copy is on top where they overlap: the place of its original's layer in the draw order, so the copies stack as
    // the originals do (dispatch 2026-09-28-318, fail 5). Without a sort key, `icon-allow-overlap` makes this layer order its
    // copies by viewport y, which says nothing about the registry, and the front glyph changed when the copies gave way to
    // the originals. Rejected: ordering the features alone, which the y-ordering overrides.
    PropertyFactory.symbolSortKey(Expression.get(FanOutIds.SORT_KEY_PROPERTY)),
)

/**
 * Whether the circle image must be registered again: when the colour the style holds is not the one wanted. A second registration of the same image at every
 * style load moved how the glyphs were sampled (the open fan's rest frame differed from the current build's in twice as many pixels, measured on the S22), so
 * the image is registered again only for a real change of colour: a night switch or a palette change.
 */
internal fun fanCircleNeedsRecolour(registered: Int?, wanted: Int): Boolean = registered != wanted

/**
 * Re-registers the circle image in [chromeColour] on a style already loaded, so a change of the app's theme recolours the circles without reloading the
 * map (the image was built with the colour current at style load). The old image is removed first so the symbols take the new one; an image the style does
 * not have is not an error (it is added).
 */
internal fun applyFanCircleStyle(style: Style, chromeColour: Int, density: Float) {
    style.removeImage(FanOutIds.CIRCLE_IMAGE)
    style.addImage(FanOutIds.CIRCLE_IMAGE, fanCircleBitmap(density, fanCircleStyle(chromeColour).colour))
}

/** The fan's collections at one moment (the circles and the icons are pushed together into the icons source: [fanPushPlan]). */
internal data class FanFrame(
    val legs: FeatureCollection,
    val circles: FeatureCollection,
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
 * What the fan's collections hold for [members], each at the place [at] gives (their moving position).
 *
 *  - **legs:** a line from each member's true position to where it is now, so the line back to the
 *    true spot is there at every frame, including the first;
 *  - **icons:** each bitmap marker's own image ([markerIconForLayer]), so a copy keeps its icon, and
 *    its [FanOutIds.ICON_OFFSET_PROPERTY]: [fanCentringOffsetDp] **times [progress]**. Spread (1) the glyph's
 *    body is centred on the copy's circle; folded (0) the offset is nothing, so the copy draws exactly where its
 *    original marker does (anchor on the coordinate, `markerSymbolLayer` has no offset) and clearing the copies
 *    moves nothing. The owner, dispatch 2026-09-28-299: "After being fanned out, the icons return to their start
 *    position. But sometimes they don't perfectly align back in their position when the animation finishes,
 *    resulting in the icons snapping into place." The offset was the full centring offset at every progress, so the
 *    pin (14 dp), the find (12.5 dp) and the flag (7.59 dp across, 12.5 down, as it was then; -358 made it 6.98 and 11.5) jumped by that much at the end of a
 *    fold and the start of an open; the photo and search centre, whose anchor is their centre, and the dots, which
 *    have no offset, never did;
 *  - **circles:** one point per copy, where the copy is now: the layer draws the copy's background
 *    circle there (dispatch 2026-09-28-265; it replaced the journal-entry halo a kept record's copy had), its
 *    radius times its [FanOutIds.CIRCLE_SCALE_PROPERTY], which is [progress]: with the glyph's offset scaled, a
 *    full circle at the coordinate would leave a pin's body up to 14 dp off its centre near 0, so the owner chose
 *    to shrink it with the fold (-299);
 *  - **dots:** a sighting, with its `observationId` and its `selected` flag from [focusedObservationId],
 *    the properties the dot layer's own paint reads.
 *
 * A member whose layer draws no marker of ours is left out and logged, never drawn as a guess.
 */
internal fun fanFrameCollections(
    members: List<FanMember>,
    at: (FanMember) -> LatLng,
    focusedObservationId: Long?,
    /** The fold's progress, 0 folded to 1 spread: what the icon offsets and the circles' scale are multiplied by. */
    progress: Float,
    /** The registry's layers in draw order, bottom to top: where a copy's original draws among the others. */
    drawOrder: List<MapLayerSpec> = MAP_LAYER_REGISTRY,
    /** A saved route's selected stop's marker, whose copy draws the accent (Amendment 6 B, [fanCopyIcon]); `null` for none. */
    accent: FanKey? = null,
    /**
     * The highlights moving in this frame, by the marker each stands on (Amendment 7, RouteStopAccentMotion.kt). A member here whose
     * highlight is not yet full, or is pulsing, draws its plain copy with the highlight over it at that opacity and size; at rest it draws
     * the accent copy alone, as before. Empty: the [accent] copy at rest.
     */
    accentMotion: Map<FanKey, RouteStopAccentShown> = emptyMap(),
): FanFrame {
    if (members.isEmpty()) return EMPTY_FRAME
    val legs = mutableListOf<Feature>()
    val circles = mutableListOf<Feature>()
    val dots = mutableListOf<Feature>()
    val icons = mutableListOf<Feature>()
    for (member in members) {
        val now = at(member)
        val layerId = member.key.layerId
        // Only the selected stop's highlight at rest is drawn as the accent copy alone. A highlight fading out, even at its first frame
        // (still full), is its plain copy with the highlight over it: the hide expects a stop no longer selected by its plain image.
        val moving = accentMotion[member.key]?.takeIf { it.opacity < 1f || it.scale != 1f || member.key != accent }
        val icon = if (moving != null) markerIconForLayer(layerId) else fanCopyIcon(layerId, member.key, accent)
        val image = icon?.imageId
        when {
            layerId == MapLayerIds.SIGHTINGS -> dots += Feature.fromGeometry(Point.fromLngLat(now.lng, now.lat)).apply {
                val observationId = member.key.featureId.toLongOrNull()
                if (observationId != null) addNumberProperty("observationId", observationId)
                addBooleanProperty("selected", observationId != null && observationId == focusedObservationId)
            }
            image != null -> {
                icons += Feature.fromGeometry(Point.fromLngLat(now.lng, now.lat)).apply {
                    addStringProperty(FanOutIds.IMAGE_PROPERTY, image)
                    val offset = icon!!.glyph.fanCentringOffsetDp()
                    addProperty(FanOutIds.ICON_OFFSET_PROPERTY, JsonArray().apply { add(offset.xDp * progress); add(offset.yDp * progress) })
                    addStringProperty(FEATURE_ID_PROPERTY, member.key.featureId)
                    val place = drawOrder.indexOfFirst { it.id == layerId }
                    if (place < 0) Log.w(FAN_OUT_TAG, "$layerId is not in the draw order; its copy sorts below every other.")
                    addNumberProperty(FanOutIds.SORT_KEY_PROPERTY, place)
                }
                if (moving != null) fanAccentOverlay(member, now, progress, moving, layerId, drawOrder)?.let { icons += it }
            }
            else -> {
                Log.w(FAN_OUT_TAG, "A fanned marker on $layerId has no icon to draw; it is left out.")
                continue
            }
        }
        circles += Feature.fromGeometry(Point.fromLngLat(now.lng, now.lat)).apply {
            // A circle is a symbol: the circle image, centred on the coordinate, sized by the progress, below every glyph. It has no feature id, so the
            // signal that waits for the copies never takes it for one.
            addStringProperty(FanOutIds.IMAGE_PROPERTY, FanOutIds.CIRCLE_IMAGE)
            addProperty(FanOutIds.ICON_OFFSET_PROPERTY, JsonArray().apply { add(0f); add(0f) })
            addNumberProperty(FanOutIds.CIRCLE_SCALE_PROPERTY, progress)
            addNumberProperty(FanOutIds.SORT_KEY_PROPERTY, FAN_CIRCLE_SORT_KEY)
        }
        legs += Feature.fromGeometry(
            LineString.fromLngLats(listOf(Point.fromLngLat(member.lng, member.lat), Point.fromLngLat(now.lng, now.lat))),
        )
    }
    return FanFrame(
        legs = FeatureCollection.fromFeatures(legs),
        circles = FeatureCollection.fromFeatures(circles),
        dots = FeatureCollection.fromFeatures(dots),
        icons = FeatureCollection.fromFeatures(icons),
    )
}

/**
 * The filter that hides [ids] from a layer: every feature whose [property] is not one of them.
 * `["all"]` (an `all` of nothing, which is true of every feature) for none, which is also how a hidden
 * layer is let go, since this SDK version has no "remove filter" call that this file has verified. Not
 * `literal(true)`: whether the native filter parser takes a bare boolean is unverified, while `["all"]`
 * is the canonical match-everything filter. Device-only either way.
 */
/**
 * Amendment 7: the selected stop's highlight over its plain copy at [now], at [moving]'s opacity and size, just above its own layer's copies
 * (half a place up the sort order). It carries the copy's feature id and the accent image, so the hide waits for it as the copy the
 * accent is drawn on (expectedCopies). Its offset is divided by its size, since `icon-offset` is scaled with `icon-size`: the swell stays
 * centred on the copy. `null`, logged, for a layer with no accent image.
 */
private fun fanAccentOverlay(member: FanMember, now: LatLng, progress: Float, moving: RouteStopAccentShown, layerId: String, drawOrder: List<MapLayerSpec>): Feature? {
    val accentIcon = fanCopyIcon(layerId, member.key, member.key) ?: return null
    if (accentIcon == markerIconForLayer(layerId)) return null // fanCopyIcon logged it: no accent image for this layer
    return Feature.fromGeometry(Point.fromLngLat(now.lng, now.lat)).apply {
        addStringProperty(FanOutIds.IMAGE_PROPERTY, accentIcon.imageId)
        val offset = accentIcon.glyph.fanCentringOffsetDp()
        val scale = moving.scale
        addProperty(FanOutIds.ICON_OFFSET_PROPERTY, JsonArray().apply { add(offset.xDp * progress / scale); add(offset.yDp * progress / scale) })
        addStringProperty(FEATURE_ID_PROPERTY, member.key.featureId)
        addNumberProperty(FanOutIds.SORT_KEY_PROPERTY, drawOrder.indexOfFirst { it.id == layerId } + 0.5f)
        addNumberProperty(FanOutIds.ACCENT_OPACITY_PROPERTY, moving.opacity)
        addNumberProperty(FanOutIds.ACCENT_SCALE_PROPERTY, scale)
    }
}

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
    // Dispatch 2026-11-06-05, Amendment 6 B: a route's selected stop's accent goes with the pin it stands on; its copy carries it instead.
    when (val accent = style.getLayer(PlottedRouteIds.SELECTED_LAYER)) {
        is SymbolLayer -> accent.setFilter(fanOutHiddenFilter(FAN_ACCENT_KEY_PROPERTY, fanHiddenAccentKeys(members)))
        null -> Log.w(FAN_OUT_TAG, "Layer ${PlottedRouteIds.SELECTED_LAYER} is not in the loaded style; the selected stop's accent was not hidden.")
        else -> Log.w(FAN_OUT_TAG, "Layer ${PlottedRouteIds.SELECTED_LAYER} is a ${accent::class.java.simpleName}; the selected stop's accent was not hidden.")
    }
    // Dispatch 2026-11-02-04: a fanned pin's name goes with its pin (WaypointNameLayers.kt), by the same waypoint ids.
    when (val names = style.getLayer(WaypointNameIds.LAYER)) {
        is SymbolLayer -> names.setFilter(fanOutHiddenFilter(FEATURE_ID_PROPERTY, fanHiddenNameIds(members)))
        null -> Log.w(FAN_OUT_TAG, "Layer ${WaypointNameIds.LAYER} is not in the loaded style; the fanned pins' names were not hidden.")
        else -> Log.w(FAN_OUT_TAG, "Layer ${WaypointNameIds.LAYER} is a ${names::class.java.simpleName}; the fanned pins' names were not hidden.")
    }
}

/**
 * Pushes [frame] into the fan's three sources ([fanPushPlan]). A source missing from the style is logged, not skipped
 * silently: every style load adds all three ([addFanOutLayers]).
 */
internal fun pushFanFrame(style: Style, frame: FanFrame) {
    fanPushPlan(frame).forEach { (sourceId, collection) ->
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
internal class MapLibreProbe(
    private val map: MapLibreMap,
    override val density: Float,
    /** Where a marker's record is now, from the lists the map draws; `null` when it is no longer drawn (never guessed). */
    private val locate: (FanKey) -> LatLng? = { null },
) : MapProbe {
    override fun hitsAt(xPx: Float, yPx: Float, layerIds: List<String>): List<TapHit> =
        layerIds.flatMap { id -> map.queryRenderedFeatures(PointF(xPx, yPx), id).map { tapHitOf(id, it) } }

    override fun hitsInBox(xPx: Float, yPx: Float, halfPx: Float, layerIds: List<String>): List<TapHit> {
        val box = RectF(xPx - halfPx, yPx - halfPx, xPx + halfPx, yPx + halfPx)
        return layerIds.flatMap { id -> map.queryRenderedFeatures(box, id).map { tapHitOf(id, it) } }
    }

    override fun markersOf(keys: List<FanKey>): List<ProbedMarker> = keys.mapNotNull { key ->
        val at = locate(key) ?: return@mapNotNull null
        val screen = map.projection.toScreenLocation(MapLibreLatLng(at.lat, at.lng))
        ProbedMarker(key, at.lat, at.lng, screen.x, screen.y)
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
