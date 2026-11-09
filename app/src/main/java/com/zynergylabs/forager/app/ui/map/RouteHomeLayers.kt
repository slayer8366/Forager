package com.zynergylabs.forager.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.theme.MapPalette
import org.maplibre.android.maps.Style
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
        RouteHomeIds.AHEAD_SOURCE to lineCollection(line?.ahead?.takeIf { arrivedAt == null }),
        RouteHomeIds.STRAIGHT_SOURCE to lineCollection(route?.straight?.takeIf { arrivedAt == null && !route.straightOffline }),
        RouteHomeIds.STRAIGHT_OFFLINE_SOURCE to lineCollection(route?.straight?.takeIf { arrivedAt == null && route.straightOffline }),
        RouteHomeIds.ARRIVED_SOURCE to FeatureCollection.fromFeatures(
            listOfNotNull(arrivedAt?.let { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)) }),
        ),
    )
}

/** The line ahead's opacity: full while the route is current, faded while it is withheld. */
internal fun routeAheadOpacity(route: RouteOnMap?): Float = if (route?.line?.aheadIsCurrent == false) ROUTE_AHEAD_FADED_OPACITY else 1f

/**
 * Dispatch -502: the straight line's opacity. Full while drawn from a fresh fix; faded, the same as the way back's
 * withheld line, while drawn from a stale one (the owner: "Fade with the HUD") or kept after the fix is lost ("Keep
 * the last line, faded").
 */
internal fun routeStraightOpacity(route: RouteOnMap?): Float = if (route?.straightIsCurrent == false) ROUTE_AHEAD_FADED_OPACITY else 1f

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
    val aheadCasing = LineLayer(RouteHomeIds.AHEAD_CASING_LAYER, RouteHomeIds.AHEAD_SOURCE).withProperties(
        PropertyFactory.lineColor(palette.casing),
        PropertyFactory.lineWidth(ROUTE_AHEAD_WIDTH_DP + 2 * CASING_WIDTH_DP),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    val ahead = LineLayer(RouteHomeIds.AHEAD_LAYER, RouteHomeIds.AHEAD_SOURCE).withProperties(
        PropertyFactory.lineColor(if (night) ROUTE_AHEAD_COLOUR_NIGHT else ROUTE_AHEAD_COLOUR_DAY),
        PropertyFactory.lineWidth(ROUTE_AHEAD_WIDTH_DP),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
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
    style.addLayerAbove(straight, RouteHomeIds.AHEAD_LAYER)
    style.addLayerAbove(straightOffline, RouteHomeIds.STRAIGHT_LAYER)
    style.addLayerAbove(ring, aboveMarkerLayerId)
}

/** Puts [route] on the map: each source's features, and the line ahead's opacity (and its casing's). */
internal fun updateRouteHomeLayers(style: Style, route: RouteOnMap?) {
    routeHomeFeatureCollections(route).forEach { (sourceId, collection) ->
        style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(collection)
    }
    val opacity = routeAheadOpacity(route)
    style.getLayer(RouteHomeIds.AHEAD_LAYER)?.setProperties(PropertyFactory.lineOpacity(opacity))
    style.getLayer(RouteHomeIds.AHEAD_CASING_LAYER)?.setProperties(PropertyFactory.lineOpacity(opacity))
    style.getLayer(RouteHomeIds.STRAIGHT_LAYER)?.setProperties(PropertyFactory.lineOpacity(routeStraightOpacity(route)))
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
