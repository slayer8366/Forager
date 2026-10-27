package com.zynergylabs.forager.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.domain.RouteLinePartKind
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.theme.MapPalette
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/*
 * The way back drawn on the map while returning (dispatch 2026-09-28-497, plan task T7). The step path
 * the owner confirmed:
 * 1. Tap Return: the whole way back is drawn along the path walked, in its own colour.
 * 2. As the walker goes, the part already passed stays drawn, dimmed and greyed ("it's acceptable to
 *    also grey them out to make it easier to identify an already traveled path"); the part ahead stays
 *    bright.
 * 3. A withheld route keeps its last line, faded ("having your tracks disappear is a scary thing").
 * 4. Arrival: the start marker turns into a ring with a check, and the route line ends.
 * 6. Ending navigation: the line goes away.
 *
 * Map content, not chrome: the 80% rule for chrome over the map does not apply. These are MapLibre
 * layers, not Compose surfaces, so they take no touches. They are added outside MAP_LAYER_REGISTRY, as
 * the fan-out layers are: the registry's tests pin its exact layers, and a navigation line belongs in
 * neither the Layers sheet nor tap routing. They draw above every track line, the live breadcrumb
 * included (both lines are placed above KEPT_TRACKS, the registry's last line), and below every marker.
 *
 * Navigating to a chosen waypoint (dispatch 2026-09-28-502, plan task T8), step 3 as the owner confirmed it:
 * "A straight dashed line" from the walker to the waypoint, until real trail routes arrive, thin and dashed
 * so it does not read as a path to follow. It is [RouteOnMap.straight], drawn by its own layer above the
 * return's lines; the return's lines are not drawn meanwhile. Arrival is the start's: the ring at the
 * waypoint, and the line ends. Once the fix is lost, the last line is kept, faded, as the withheld way back
 * is (the owner's choice at the desk check: "Keep the last line, faded"): [RouteOnMap.straightIsCurrent].
 *
 * Step 6 PR 3 (dispatch 2026-10-26-11): inside a downloaded area with trail routes, the way to the waypoint along trails
 * replaces that line once worked out, through [RouteOnMap.line] as the way home's line does: its trail solid, and its
 * two straight legs (to the nearest trail, and from the trail to the waypoint) thin and dashed like the straight line,
 * in their own layer on the line ahead's source ([RouteHomeIds.AHEAD_STRAIGHT_LAYER]).
 */

/**
 * What the map draws of the way back: [line] (`null` for none) and, once arrived, the start's place for its ring.
 * [straight] is navigating to a waypoint's line (dispatch 2026-09-28-502): the walker to the waypoint, dashed.
 */
data class RouteOnMap(val line: RouteLine?, val arrivedAt: LatLng?, val straight: List<LatLng>? = null, val straightIsCurrent: Boolean = true, val straightOffline: Boolean = false)

internal object RouteHomeIds {
    const val STRAIGHT_SOURCE = "route-home-straight"
    const val STRAIGHT_LAYER = "route-home-straight-layer"
    const val STRAIGHT_OFFLINE_SOURCE = "route-home-straight-offline"
    const val STRAIGHT_OFFLINE_LAYER = "route-home-straight-offline-layer"
    const val PASSED_SOURCE = "route-home-passed"
    const val PASSED_LAYER = "route-home-passed-layer"
    const val AHEAD_SOURCE = "route-home-ahead"
    const val AHEAD_CASING_LAYER = "route-home-ahead-casing-layer"
    const val AHEAD_LAYER = "route-home-ahead-layer"
    const val AHEAD_DOTTED_LAYER = "route-home-ahead-dotted-layer"

    /** Step 6 PR 3 (dispatch 2026-10-26-11): the way to a waypoint's straight legs, thin and dashed, from the ahead source. */
    const val AHEAD_STRAIGHT_LAYER = "route-home-ahead-straight-layer"

    /**
     * The line ahead's features' property naming their kind (dispatch 2026-10-24-10): [PART_TRACK], [PART_TRAIL] or, since
     * step 6 PR 3, [PART_STRAIGHT]; none for the track home.
     */
    const val PART_PROPERTY = "part"
    const val PART_TRACK = "track"
    const val PART_TRAIL = "trail"
    const val PART_STRAIGHT = "straight"

    /**
     * Carried by every feature of a line ahead drawn grey ([RouteLine.aheadIsOffline], dispatch 2026-10-26-11 Amendment 2,
     * item 1): the line ahead's layers take the passed line's grey for it, as the straight line kept once location is lost
     * does. Absent on every other line, so the way home is drawn as before.
     */
    const val OFFLINE_PROPERTY = "offline"
    const val ARRIVED_SOURCE = "route-home-arrived"
    const val ARRIVED_LAYER = "route-home-arrived-layer"
    const val ARRIVED_IMAGE = "route-home-arrived-image"
}

/** The line ahead's opacity while the route is withheld: the last line kept, visibly faded. Provisional, to be judged on the phone. */
internal const val ROUTE_AHEAD_FADED_OPACITY = 0.4f

/** The line at Return, dimmed, so what has been passed recedes. Provisional. */
internal const val ROUTE_PASSED_OPACITY = 0.6f

/** The line ahead: an azure apart from the tracks' purples. Provisional, the owner's to choose. */
private const val ROUTE_AHEAD_COLOUR_DAY = 0xFF0288D1.toInt()
private const val ROUTE_AHEAD_COLOUR_NIGHT = 0xFF4FC3F7.toInt()

/** The line at Return: grey. */
private const val ROUTE_PASSED_COLOUR_DAY = 0xFF8A8A8A.toInt()
private const val ROUTE_PASSED_COLOUR_NIGHT = 0xFF9E9E9E.toInt()

private const val ROUTE_AHEAD_WIDTH_DP = 6f
private const val ROUTE_PASSED_WIDTH_DP = 5f

/** Dispatch -502: the straight line to a waypoint, thin, in the line ahead's azure. Provisional, to be judged on the phone. */
private const val STRAIGHT_WIDTH_DP = 3f

/** Its dashes and gaps, in multiples of its width (MapLibre's unit for `line-dasharray`): 6 dp on, 6 dp off. Provisional. */
private val STRAIGHT_DASHES = arrayOf(2f, 2f)

/**
 * Dispatch 2026-10-24-10 (RECORD -1589, "Dotted, same colour"): the walker's own track in a way home along trails, as
 * round dots in the line ahead's colour and width, one width long and two apart (MapLibre's unit is the line's width).
 * Its dots carry no casing: a solid casing under them would read as a solid line. Provisional, to be judged on the phone.
 */
private val AHEAD_DOTS = arrayOf(0f, 2f)

/** The arrival ring's diameter. */
private const val ARRIVED_RING_DP = 36f

/**
 * What each route source receives for [route]: the line at Return to the dimmed source, the line ahead
 * to the bright one (none once arrived: the route line ends there), and the ring's point once arrived.
 * Navigating to a waypoint, the straight line goes to its own source (dispatch -502).
 * A line of fewer than two points is no line. Every source is always present, so a cleared route
 * empties the map.
 */
internal fun routeHomeFeatureCollections(route: RouteOnMap?): Map<String, FeatureCollection> {
    val line = route?.line
    val arrivedAt = route?.arrivedAt
    return mapOf(
        RouteHomeIds.PASSED_SOURCE to lineCollection(line?.atReturn),
        RouteHomeIds.AHEAD_SOURCE to aheadCollection(line?.takeIf { arrivedAt == null }),
        RouteHomeIds.STRAIGHT_SOURCE to lineCollection(route?.straight?.takeIf { arrivedAt == null && !route.straightOffline }),
        RouteHomeIds.STRAIGHT_OFFLINE_SOURCE to lineCollection(route?.straight?.takeIf { arrivedAt == null && route.straightOffline }),
        RouteHomeIds.ARRIVED_SOURCE to FeatureCollection.fromFeatures(
            listOfNotNull(arrivedAt?.let { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)) }),
        ),
    )
}

/**
 * The line ahead's opacity: full while the route is current, faded while it is withheld. Grey ([RouteLine.aheadIsOffline],
 * the way to a waypoint once location is lost) is full, as the straight line's grey layer is, "so it still shows over a
 * pale map" (dispatch -502; Amendment 2 of dispatch 2026-10-26-11, item 1).
 */
internal fun routeAheadOpacity(route: RouteOnMap?): Float {
    val line = route?.line
    return if (line != null && !line.aheadIsOffline && !line.aheadIsCurrent) ROUTE_AHEAD_FADED_OPACITY else 1f
}

/**
 * Dispatch -502: the straight line's opacity. Full while drawn from a fresh fix; faded, the same as the way back's
 * withheld line, while drawn from a stale one (the owner: "Fade with the HUD") or kept after the fix is lost ("Keep
 * the last line, faded").
 */
internal fun routeStraightOpacity(route: RouteOnMap?): Float = if (route?.straightIsCurrent == false) ROUTE_AHEAD_FADED_OPACITY else 1f

/**
 * The line ahead: one feature with no kind for the track home, as before; for a way home along trails one feature per
 * run, each naming its kind, so the dotted and solid layers each draw their own and both change in one update.
 */
private fun aheadCollection(line: RouteLine?): FeatureCollection {
    val collection = if (line == null || line.aheadParts.isEmpty()) {
        lineCollection(line?.ahead)
    } else {
        FeatureCollection.fromFeatures(
            line.aheadParts.filter { it.points.size >= 2 }.map { part ->
                Feature.fromGeometry(LineString.fromLngLats(part.points.map { Point.fromLngLat(it.lng, it.lat) })).apply {
                    addStringProperty(RouteHomeIds.PART_PROPERTY, partPropertyOf(part.kind))
                }
            },
        )
    }
    // Amendment 2 of dispatch 2026-10-26-11, item 1: a line kept once location is lost is drawn grey, every part of it.
    if (line?.aheadIsOffline == true) collection.features()?.forEach { it.addBooleanProperty(RouteHomeIds.OFFLINE_PROPERTY, true) }
    return collection
}

/**
 * The kind each run of the line ahead is drawn as. Step 6 PR 3 (N-412-1): a straight leg of the way to a waypoint is its
 * own kind, thin and dashed as the straight line to a waypoint is, never dotted, which means "your own track" (RECORD
 * -1589). Until then every kind but the trail was tagged track.
 */
private fun partPropertyOf(kind: RouteLinePartKind): String = when (kind) {
    RouteLinePartKind.TRAIL -> RouteHomeIds.PART_TRAIL
    RouteLinePartKind.TRACK -> RouteHomeIds.PART_TRACK
    RouteLinePartKind.STRAIGHT -> RouteHomeIds.PART_STRAIGHT
}

private fun lineCollection(points: List<LatLng>?): FeatureCollection =
    FeatureCollection.fromFeatures(
        listOfNotNull(
            points?.takeIf { it.size >= 2 }?.let { line -> Feature.fromGeometry(LineString.fromLngLats(line.map { Point.fromLngLat(it.lng, it.lat) })) },
        ),
    )

/**
 * Adds the route's sources, image and layers to [style], once per style load, after the registry's
 * layers: the two lines above [aboveLineLayerId] (the registry's last line), the ring above
 * [aboveMarkerLayerId] (the waypoints). Empty until [updateRouteHomeLayers] fills them.
 */
internal fun addRouteHomeLayers(style: Style, palette: MapPalette, density: Float, aboveLineLayerId: String, aboveMarkerLayerId: String) {
    val night = palette == MapPalette.NIGHT
    // Amendment 2 of dispatch 2026-10-26-11, item 1: the line ahead in its azure, or, carrying the grey mark, in the grey
    // the straight line takes once location is lost (its offline layer, below).
    val aheadColour = Expression.switchCase(
        Expression.has(RouteHomeIds.OFFLINE_PROPERTY),
        Expression.color(if (night) ROUTE_PASSED_COLOUR_NIGHT else ROUTE_PASSED_COLOUR_DAY),
        Expression.color(if (night) ROUTE_AHEAD_COLOUR_NIGHT else ROUTE_AHEAD_COLOUR_DAY),
    )
    listOf(RouteHomeIds.PASSED_SOURCE, RouteHomeIds.AHEAD_SOURCE, RouteHomeIds.STRAIGHT_SOURCE, RouteHomeIds.STRAIGHT_OFFLINE_SOURCE, RouteHomeIds.ARRIVED_SOURCE).forEach {
        if (style.getSource(it) == null) style.addSource(GeoJsonSource(it, FeatureCollection.fromFeatures(emptyList())))
    }
    style.addImage(RouteHomeIds.ARRIVED_IMAGE, arrivedRingImage(if (night) ROUTE_AHEAD_COLOUR_NIGHT else ROUTE_AHEAD_COLOUR_DAY, density))
    val passed = LineLayer(RouteHomeIds.PASSED_LAYER, RouteHomeIds.PASSED_SOURCE).withProperties(
        PropertyFactory.lineColor(if (night) ROUTE_PASSED_COLOUR_NIGHT else ROUTE_PASSED_COLOUR_DAY),
        PropertyFactory.lineWidth(ROUTE_PASSED_WIDTH_DP),
        PropertyFactory.lineOpacity(ROUTE_PASSED_OPACITY),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    // Dispatch 2026-10-24-10: the solid line and its casing draw everything but the walker's track in a way home
    // along trails, which the dotted layer draws, and (step 6 PR 3) the way to a waypoint's straight legs, which the
    // thin dashed layer draws.
    val notDotted = Expression.all(
        Expression.neq(Expression.get(RouteHomeIds.PART_PROPERTY), Expression.literal(RouteHomeIds.PART_TRACK)),
        Expression.neq(Expression.get(RouteHomeIds.PART_PROPERTY), Expression.literal(RouteHomeIds.PART_STRAIGHT)),
    )
    val dottedOnly = Expression.eq(Expression.get(RouteHomeIds.PART_PROPERTY), Expression.literal(RouteHomeIds.PART_TRACK))
    val straightOnly = Expression.eq(Expression.get(RouteHomeIds.PART_PROPERTY), Expression.literal(RouteHomeIds.PART_STRAIGHT))
    val aheadCasing = LineLayer(RouteHomeIds.AHEAD_CASING_LAYER, RouteHomeIds.AHEAD_SOURCE).withFilter(notDotted).withProperties(
        PropertyFactory.lineColor(palette.casing),
        PropertyFactory.lineWidth(ROUTE_AHEAD_WIDTH_DP + 2 * CASING_WIDTH_DP),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    val ahead = LineLayer(RouteHomeIds.AHEAD_LAYER, RouteHomeIds.AHEAD_SOURCE).withFilter(notDotted).withProperties(
        PropertyFactory.lineColor(aheadColour),
        PropertyFactory.lineWidth(ROUTE_AHEAD_WIDTH_DP),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    val aheadDotted = LineLayer(RouteHomeIds.AHEAD_DOTTED_LAYER, RouteHomeIds.AHEAD_SOURCE).withFilter(dottedOnly).withProperties(
        PropertyFactory.lineColor(aheadColour),
        PropertyFactory.lineWidth(ROUTE_AHEAD_WIDTH_DP),
        PropertyFactory.lineDasharray(AHEAD_DOTS),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    // Step 6 PR 3: the way to a waypoint's straight legs, as the straight line to a waypoint is drawn: thin, dashed, no casing.
    val aheadStraight = LineLayer(RouteHomeIds.AHEAD_STRAIGHT_LAYER, RouteHomeIds.AHEAD_SOURCE).withFilter(straightOnly).withProperties(
        PropertyFactory.lineColor(aheadColour),
        PropertyFactory.lineWidth(STRAIGHT_WIDTH_DP),
        PropertyFactory.lineDasharray(STRAIGHT_DASHES),
        PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
    )
    val straight = LineLayer(RouteHomeIds.STRAIGHT_LAYER, RouteHomeIds.STRAIGHT_SOURCE).withProperties(
        PropertyFactory.lineColor(if (night) ROUTE_AHEAD_COLOUR_NIGHT else ROUTE_AHEAD_COLOUR_DAY),
        PropertyFactory.lineWidth(STRAIGHT_WIDTH_DP),
        PropertyFactory.lineDasharray(STRAIGHT_DASHES),
        PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
    )
    // Dispatch -502: the line kept once location is lost, grey (the owner: "An offline grey color will work"), the
    // passed way back's grey at full opacity so it still shows over a pale map. Provisional.
    val straightOffline = LineLayer(RouteHomeIds.STRAIGHT_OFFLINE_LAYER, RouteHomeIds.STRAIGHT_OFFLINE_SOURCE).withProperties(
        PropertyFactory.lineColor(if (night) ROUTE_PASSED_COLOUR_NIGHT else ROUTE_PASSED_COLOUR_DAY),
        PropertyFactory.lineWidth(STRAIGHT_WIDTH_DP),
        PropertyFactory.lineDasharray(STRAIGHT_DASHES),
        PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
    )
    val ring = SymbolLayer(RouteHomeIds.ARRIVED_LAYER, RouteHomeIds.ARRIVED_SOURCE).withProperties(
        PropertyFactory.iconImage(RouteHomeIds.ARRIVED_IMAGE),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true),
    )
    style.addLayerAbove(passed, aboveLineLayerId)
    style.addLayerAbove(aheadCasing, RouteHomeIds.PASSED_LAYER)
    style.addLayerAbove(ahead, RouteHomeIds.AHEAD_CASING_LAYER)
    style.addLayerAbove(aheadDotted, RouteHomeIds.AHEAD_LAYER)
    style.addLayerAbove(aheadStraight, RouteHomeIds.AHEAD_DOTTED_LAYER)
    style.addLayerAbove(straight, RouteHomeIds.AHEAD_STRAIGHT_LAYER)
    style.addLayerAbove(straightOffline, RouteHomeIds.STRAIGHT_LAYER)
    style.addLayerAbove(ring, aboveMarkerLayerId)
}

/**
 * The solid line ahead's opacity, and its casing's: [routeAheadOpacity], and while the walker is off the trail
 * ([RouteLine.trailDimmed], plan step 7 PR 3) no more than [ROUTE_PASSED_OPACITY] (RECORD -1676, the owner: "60%, like
 * passed route"). The lower of the two wins, so the withheld fade (0.4) is never multiplied into near nothing ("having
 * your tracks disappear is a scary thing"). Only the solid layers: the dotted track is left as it is (RECORD -1676, "A:
 * trail dims only"). The solid layers draw only the trail part of a way home along trails; the line is dimmed only when
 * it has one (`TrackRecordingUiState.routeLineShown`; for the way to a waypoint, `AvailabilityWaypointRoute`, step 6 PR 4,
 * where the dashed straight legs are left as they are too).
 */
internal fun routeTrailOpacity(route: RouteOnMap?): Float {
    val opacity = routeAheadOpacity(route)
    return if (route?.line?.trailDimmed == true) minOf(opacity, ROUTE_PASSED_OPACITY) else opacity
}

/** Each line layer's opacity for [route], by layer id: what [updateRouteHomeLayers] sets. */
internal fun routeHomeLayerOpacities(route: RouteOnMap?): Map<String, Float> {
    val ahead = routeAheadOpacity(route)
    val trail = routeTrailOpacity(route)
    return mapOf(
        RouteHomeIds.AHEAD_LAYER to trail,
        RouteHomeIds.AHEAD_CASING_LAYER to trail,
        RouteHomeIds.AHEAD_DOTTED_LAYER to ahead,
        RouteHomeIds.AHEAD_STRAIGHT_LAYER to ahead,
        RouteHomeIds.STRAIGHT_LAYER to routeStraightOpacity(route),
    )
}

/** Puts [route] on the map: each source's features, and each line layer's opacity ([routeHomeLayerOpacities]). */
internal fun updateRouteHomeLayers(style: Style, route: RouteOnMap?) {
    routeHomeFeatureCollections(route).forEach { (sourceId, collection) ->
        style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(collection)
    }
    routeHomeLayerOpacities(route).forEach { (layerId, opacity) ->
        style.getLayer(layerId)?.setProperties(PropertyFactory.lineOpacity(opacity))
    }
}

/**
 * The start once arrived: a ring with a check, a change of form, not of colour alone (the plan's
 * check). Drawn once per style load; a static swap with no animation, so Reduce Motion has nothing to
 * suppress.
 */
private fun arrivedRingImage(colour: Int, density: Float): Bitmap {
    val size = (ARRIVED_RING_DP * density).toInt()
    val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = 3f * density
    val centre = size / 2f
    val radius = centre - stroke
    canvas.drawCircle(centre, centre, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
    canvas.drawCircle(centre, centre, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour; style = Paint.Style.STROKE; strokeWidth = stroke })
    val check = Path().apply {
        moveTo(size * 0.30f, size * 0.52f)
        lineTo(size * 0.45f, size * 0.66f)
        lineTo(size * 0.71f, size * 0.38f)
    }
    canvas.drawPath(
        check,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colour
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        },
    )
    return bitmap
}
