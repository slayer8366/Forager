package com.zynergylabs.forager.app.ui.map

import android.util.Log
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.ui.theme.MapPalette
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/*
 * A plotted route on the Maps tab (dispatch 2026-10-30-08, step 3; the first stop, (c)): the route being plotted, or a saved
 * one opened from the Trip Planner or its pin. Its trail parts solid, with a casing; its joins to the trail and its straight
 * legs thin and dashed (the owner: "Dashed link to trail (Recommended)"); and, on a saved route, a small dot at each stop
 * (Amendment 1, Q8: "Yes, small dots (Recommended)"), since a route keeps copies of its stops and a waypoint deleted later
 * leaves no marker there.
 *
 * Map content, not chrome: the 80% rule does not apply. MapLibre layers take no touches. Outside MAP_LAYER_REGISTRY, apart
 * from the navigation layers (RouteHomeLayers.kt), with their own ids, and drawn below them: a way being followed is never
 * hidden under a plotted route.
 */

/** What the map draws of a plotted route: its [legs], and [stopDots] (empty while plotting). */
data class PlottedRouteOnMap(val legs: List<RouteLeg>, val stopDots: List<LatLng> = emptyList())

internal object PlottedRouteIds {
    const val SOURCE = "plotted-route"
    const val STOPS_SOURCE = "plotted-route-stops"
    const val CASING_LAYER = "plotted-route-casing-layer"
    const val TRAIL_LAYER = "plotted-route-trail-layer"
    const val STRAIGHT_LAYER = "plotted-route-straight-layer"
    const val STOPS_LAYER = "plotted-route-stops-layer"
    const val PART_PROPERTY = "part"
    const val PART_TRAIL = "trail"
    const val PART_STRAIGHT = "straight"
}

/** One drawn run of a plotted route: trail or straight, and its points. */
internal data class PlottedRoutePart(val part: String, val points: List<LatLng>)

/**
 * [legs] as drawn runs: a trail leg as its join from the first stop (dashed), its trail part (solid) and its join to the second
 * stop (dashed), from [RouteLeg.Trail.trailFrom] and [RouteLeg.Trail.trailTo]; a straight leg whole, dashed. A join of zero
 * length (a stop exactly on the trail, N9 of Sorrel's #445/#446 review) is kept as it is: two points at one place.
 */
internal fun plottedRouteParts(legs: List<RouteLeg>): List<PlottedRoutePart> = legs.flatMap { leg ->
    when (leg) {
        is RouteLeg.Straight -> listOf(PlottedRoutePart(PlottedRouteIds.PART_STRAIGHT, leg.line))
        is RouteLeg.Trail -> listOf(
            PlottedRoutePart(PlottedRouteIds.PART_STRAIGHT, leg.line.subList(0, leg.trailFrom + 1)),
            PlottedRoutePart(PlottedRouteIds.PART_TRAIL, leg.line.subList(leg.trailFrom, leg.trailTo + 1)),
            PlottedRoutePart(PlottedRouteIds.PART_STRAIGHT, leg.line.subList(leg.trailTo, leg.line.size)),
        )
    }
}

/**
 * The route's colour: magenta. The owner chose "Orange (Recommended)" (Amendment 1, Q9) on the condition that orange was not
 * already used by another line or layer; it was (the test forecast's chanterelle ramp, Street's roads), and the owner then
 * chose "Magenta (Recommended)" (Amendment 2, RECORD -1853). Measured in Oklab against every map palette role, Street v3 and
 * v4's road colours, the night road table and both forecast ramps: by day the nearest track colour is the kept track, 0.181,
 * and the nearest of anything is the find marker, 0.133; by night the nearest track colour is the breadcrumb, 0.154, and the
 * nearest of anything is the planned-trip pin, 0.061, the route trip's own pin. The project's floor between fill roles is 0.10
 * (MapPaletteTest). The trail part carries the palette's casing, so it reads over grounds it is close to in luminance.
 */
internal const val PLOTTED_ROUTE_COLOUR_DAY = 0xFFFF00FF.toInt()
internal const val PLOTTED_ROUTE_COLOUR_NIGHT = 0xFFFF40FF.toInt()

private const val TRAIL_WIDTH_DP = 5f
private const val CASING_DP = 1.5f
private const val STRAIGHT_WIDTH_DP = 3f
private val STRAIGHT_DASHES = arrayOf(2f, 2f)
private const val STOP_DOT_RADIUS_DP = 4f

/** Adds the route's sources and layers to [style], below [belowLayerId] (the way home's lowest line). */
internal fun addPlottedRouteLayers(style: Style, palette: MapPalette, belowLayerId: String) {
    val colour = if (palette == MapPalette.NIGHT) PLOTTED_ROUTE_COLOUR_NIGHT else PLOTTED_ROUTE_COLOUR_DAY
    style.addSource(GeoJsonSource(PlottedRouteIds.SOURCE, FeatureCollection.fromFeatures(emptyList())))
    style.addSource(GeoJsonSource(PlottedRouteIds.STOPS_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    val trailOnly = Expression.eq(Expression.get(PlottedRouteIds.PART_PROPERTY), Expression.literal(PlottedRouteIds.PART_TRAIL))
    val straightOnly = Expression.eq(Expression.get(PlottedRouteIds.PART_PROPERTY), Expression.literal(PlottedRouteIds.PART_STRAIGHT))
    val casing = LineLayer(PlottedRouteIds.CASING_LAYER, PlottedRouteIds.SOURCE).withFilter(trailOnly).withProperties(
        PropertyFactory.lineColor(palette.casing),
        PropertyFactory.lineWidth(TRAIL_WIDTH_DP + 2 * CASING_DP),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    val trail = LineLayer(PlottedRouteIds.TRAIL_LAYER, PlottedRouteIds.SOURCE).withFilter(trailOnly).withProperties(
        PropertyFactory.lineColor(colour),
        PropertyFactory.lineWidth(TRAIL_WIDTH_DP),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
    )
    val straight = LineLayer(PlottedRouteIds.STRAIGHT_LAYER, PlottedRouteIds.SOURCE).withFilter(straightOnly).withProperties(
        PropertyFactory.lineColor(colour),
        PropertyFactory.lineWidth(STRAIGHT_WIDTH_DP),
        PropertyFactory.lineDasharray(STRAIGHT_DASHES),
    )
    val stops = CircleLayer(PlottedRouteIds.STOPS_LAYER, PlottedRouteIds.STOPS_SOURCE).withProperties(
        PropertyFactory.circleColor(colour),
        PropertyFactory.circleRadius(STOP_DOT_RADIUS_DP),
        PropertyFactory.circleStrokeColor(palette.casing),
        PropertyFactory.circleStrokeWidth(CASING_DP),
    )
    style.addLayerBelow(casing, belowLayerId)
    style.addLayerAbove(trail, PlottedRouteIds.CASING_LAYER)
    style.addLayerAbove(straight, PlottedRouteIds.TRAIL_LAYER)
    style.addLayerAbove(stops, PlottedRouteIds.STRAIGHT_LAYER)
}

/** What each of the route's sources receives for [route]; both empty for none, so a closed route clears the map. */
internal fun plottedRouteFeatureCollections(route: PlottedRouteOnMap?): Map<String, FeatureCollection> = mapOf(
    PlottedRouteIds.SOURCE to FeatureCollection.fromFeatures(
        plottedRouteParts(route?.legs.orEmpty()).map { part ->
            Feature.fromGeometry(LineString.fromLngLats(part.points.map { Point.fromLngLat(it.lng, it.lat) })).also {
                it.addStringProperty(PlottedRouteIds.PART_PROPERTY, part.part)
            }
        },
    ),
    PlottedRouteIds.STOPS_SOURCE to FeatureCollection.fromFeatures(
        route?.stopDots.orEmpty().map { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)) },
    ),
)

/** Pushes [route] to the current style; nothing in a style change (logged by [currentStyle]), which runs it again on load. */
internal fun plottedRouteOnCurrentStyle(loaded: Style?, route: PlottedRouteOnMap?) {
    val style = currentStyle(loaded, "The plotted route") ?: return
    plottedRouteFeatureCollections(route).forEach { (id, collection) ->
        val source = style.getSourceAs<GeoJsonSource>(id)
        if (source == null) {
            Log.w(SIGHTINGS_MAP_TAG, "The plotted route's source $id is missing; the route is not drawn.")
        } else {
            source.setGeoJson(collection)
        }
    }
}
