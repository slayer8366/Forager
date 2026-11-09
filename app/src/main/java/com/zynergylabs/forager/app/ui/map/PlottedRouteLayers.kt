package com.zynergylabs.forager.app.ui.map

import android.util.Log
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.RouteStop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.theme.MapPalette
import com.zynergylabs.forager.app.ui.theme.MushroomDeep
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

/*
 * A plotted route on the Maps tab (dispatch 2026-10-30-08, step 3; the first stop, (c)): the route being plotted, or a saved
 * one opened from the Trip Planner or its pin. Its trail parts solid, with a casing; its joins to the trail and its straight
 * legs thin and dashed (the owner: "Dashed link to trail (Recommended)"), with the same casing (Amendment 3); and, on a saved route, a small dot at each stop
 * (Amendment 1, Q8: "Yes, small dots (Recommended)"), since a route keeps copies of its stops and a waypoint deleted later
 * leaves no marker there.
 *
 * Map content, not chrome: the 80% rule does not apply. MapLibre layers take no touches. Outside MAP_LAYER_REGISTRY, apart
 * from the navigation layers (RouteHomeLayers.kt), with their own ids, and drawn below them: a way being followed is never
 * hidden under a plotted route.
 */

/**
 * What the map draws of a plotted route: its [legs], [stopDots] (empty while plotting), and [stopNames], the stops whose
 * names are always drawn, while plotting and on a saved route (dispatch 2026-11-02-04, A2; WaypointNameLayers.kt).
 */
data class PlottedRouteOnMap(
    val legs: List<RouteLeg>,
    val stopDots: List<LatLng> = emptyList(),
    val stopNames: List<RouteStop> = emptyList(),
    /** A saved route's selected stop's marker, drawn in the accent colour (dispatch 2026-11-06-05, Amendment 2, item 4); `null` for none. */
    val selected: SelectedStopMarker? = null,
)

/**
 * Where the selected stop's waypoint ([trip] false) or trip stands: its pin, or its flag, is drawn there again in the accent colour.
 * [featureId] is that waypoint's or trip's own id, as its marker carries it, so the fan can tell which of its copies is this one
 * (dispatch 2026-11-06-05, Amendment 6 B; [fanKey]).
 */
data class SelectedStopMarker(val at: LatLng, val trip: Boolean, val featureId: String) {
    /** The marker this accent stands on, by its layer and id, as a fan names its members (Amendment 6 B). */
    val fanKey: FanKey get() = FanKey(if (trip) MapLayerIds.PLANNED_TRIPS else MapLayerIds.WAYPOINTS, featureId)
}

/**
 * The selected stop's accent (dispatch 2026-11-06-05, Amendment 1, step 4, the owner: "Accent colour"; Amendment 2, "Keep cream, judge
 * on phone"; Amendment 6, "a dimmer cream by day", RECORD -2187): [RouteStopDimmerCream] #E3D3B5 by day, rust red [MushroomDeep]
 * #A2612C at night (the owner: "Leave it"), per the palette, on the palette's own casing. The same colour is the fan's accent copy, through
 * `MarkerIcon.WAYPOINT_ROUTE_SELECTED` / `PLANNED_TRIP_ROUTE_SELECTED` (FanRouteStopAccent.kt). For the owner to judge on the phone.
 */
internal fun routeStopSelectedColour(palette: MapPalette): Int =
    (if (palette == MapPalette.NIGHT) MushroomDeep else RouteStopDimmerCream).toArgb()

/**
 * The day accent's dimmer cream (Amendment 6, RECORD -2187; the owner: "Also reduce the brightness of the highlight in day mode", then
 * "Dimmer cream", "A slightly darker solid cream in place of today's"). It is [Cream] #EDE3D0 at the same hue and saturation (HSL about
 * 39 degrees, 45%) with its lightness lowered from 0.87 to 0.80. A named colour of its own rather than one from the palette: the palette's
 * warm neutrals a little darker than Cream (NeutralGray200 #CFC9BE, OutlineVariantLight #D5CEBF, SurfaceContainerHighestLight #E2DCCC)
 * carry 15-27% saturation against Cream's 45%, so they read grey, not as a darker cream; MushroomContainerLight #F6DFC8 is peach and
 * lighter. Rejected: Cream at 80% opacity over the pin colour, the owner's other offer, which they did not pick.
 */
internal val RouteStopDimmerCream = Color(0xFFE3D3B5)

internal object PlottedRouteIds {
    const val SOURCE = "plotted-route"
    const val STOPS_SOURCE = "plotted-route-stops"
    const val CASING_LAYER = "plotted-route-casing-layer"
    const val STRAIGHT_CASING_LAYER = "plotted-route-straight-casing-layer"
    const val TRAIL_LAYER = "plotted-route-trail-layer"
    const val STRAIGHT_LAYER = "plotted-route-straight-layer"
    const val STOPS_LAYER = "plotted-route-stops-layer"
    const val SELECTED_SOURCE = "plotted-route-selected"
    const val SELECTED_LAYER = "plotted-route-selected-layer"
    const val SELECTED_ICON_PROPERTY = "icon"
    /** Amendment 7: the highlight's opacity and size in this frame (RouteStopAccentMotion.kt), read per feature by the accent layer. */
    const val SELECTED_OPACITY_PROPERTY = "opacity"
    const val SELECTED_SCALE_PROPERTY = "scale"
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
 * chose "Magenta (Recommended)" (Amendment 2, RECORD -1853). Measured in Oklab against every map palette role, Trail Map v3 and
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
private val STRAIGHT_DASHES = listOf(2f, 2f)
private const val STRAIGHT_CASING_WIDTH_DP = STRAIGHT_WIDTH_DP + 2 * CASING_DP
private const val STOP_DOT_RADIUS_DP = 4f

/**
 * One of the route's line layers, as data, so a headless test reads what MapLibre is given: its [part] (trail or straight),
 * [colour], [widthDp], [dashes] (in line widths, as `lineDasharray` takes them; `null` for solid) and whether its caps and joins
 * are round.
 */
internal data class PlottedRouteLineSpec(
    val layerId: String,
    val part: String,
    val colour: Int,
    val widthDp: Float,
    val dashes: List<Float>?,
    val roundCaps: Boolean,
)

/** The route's line layers in [palette]'s colours, bottom to top. */
internal fun plottedRouteLineSpecs(palette: MapPalette): List<PlottedRouteLineSpec> {
    val colour = if (palette == MapPalette.NIGHT) PLOTTED_ROUTE_COLOUR_NIGHT else PLOTTED_ROUTE_COLOUR_DAY
    return listOf(
        PlottedRouteLineSpec(PlottedRouteIds.CASING_LAYER, PlottedRouteIds.PART_TRAIL, palette.casing, TRAIL_WIDTH_DP + 2 * CASING_DP, null, roundCaps = true),
        // Amendment 3 (RECORD -1861), the owner: "Add a thin outline (Recommended)": the dashed parts carry the same casing as
        // the trail part, white by day and black by night. Dashed as they are, with the dash and gap scaled to the wider line
        // (lineDasharray is in line widths), so each dash is outlined along its sides and the gaps stay open.
        PlottedRouteLineSpec(PlottedRouteIds.STRAIGHT_CASING_LAYER, PlottedRouteIds.PART_STRAIGHT, palette.casing, STRAIGHT_CASING_WIDTH_DP, STRAIGHT_DASHES.map { it * STRAIGHT_WIDTH_DP / STRAIGHT_CASING_WIDTH_DP }, roundCaps = false),
        PlottedRouteLineSpec(PlottedRouteIds.TRAIL_LAYER, PlottedRouteIds.PART_TRAIL, colour, TRAIL_WIDTH_DP, null, roundCaps = true),
        PlottedRouteLineSpec(PlottedRouteIds.STRAIGHT_LAYER, PlottedRouteIds.PART_STRAIGHT, colour, STRAIGHT_WIDTH_DP, STRAIGHT_DASHES, roundCaps = false),
    )
}

private fun plottedRouteLineLayer(spec: PlottedRouteLineSpec): LineLayer {
    val onlyPart = Expression.eq(Expression.get(PlottedRouteIds.PART_PROPERTY), Expression.literal(spec.part))
    val properties = buildList {
        add(PropertyFactory.lineColor(spec.colour))
        add(PropertyFactory.lineWidth(spec.widthDp))
        if (spec.roundCaps) {
            add(PropertyFactory.lineCap(Property.LINE_CAP_ROUND))
            add(PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND))
        }
        spec.dashes?.let { add(PropertyFactory.lineDasharray(it.toTypedArray())) }
    }
    return LineLayer(spec.layerId, PlottedRouteIds.SOURCE).withFilter(onlyPart).withProperties(*properties.toTypedArray())
}

/** Adds the route's sources and layers to [style], below [belowLayerId] (the way home's lowest line), in [plottedRouteLineSpecs]' order. */
internal fun addPlottedRouteLayers(style: Style, palette: MapPalette, belowLayerId: String) {
    val colour = if (palette == MapPalette.NIGHT) PLOTTED_ROUTE_COLOUR_NIGHT else PLOTTED_ROUTE_COLOUR_DAY
    style.addSource(GeoJsonSource(PlottedRouteIds.SOURCE, FeatureCollection.fromFeatures(emptyList())))
    style.addSource(GeoJsonSource(PlottedRouteIds.STOPS_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    var below = belowLayerId
    // Added top first, each below the one before, so they stack bottom to top as listed, all under the way home.
    plottedRouteLineSpecs(palette).asReversed().forEach { spec ->
        style.addLayerBelow(plottedRouteLineLayer(spec), below)
        below = spec.layerId
    }
    val stops = CircleLayer(PlottedRouteIds.STOPS_LAYER, PlottedRouteIds.STOPS_SOURCE).withProperties(
        PropertyFactory.circleColor(colour),
        PropertyFactory.circleRadius(STOP_DOT_RADIUS_DP),
        PropertyFactory.circleStrokeColor(palette.casing),
        PropertyFactory.circleStrokeWidth(CASING_DP),
    )
    style.addLayerBelow(stops, belowLayerId)
    // Dispatch 2026-11-06-05, Amendment 2, item 4: the selected stop's marker, drawn over the waypoints' own pins (and the trips' flags,
    // which are under them), so the waypoint's pin is seen in the accent colour. Its two images are registered with every other marker's.
    style.addSource(GeoJsonSource(PlottedRouteIds.SELECTED_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    val selected = SymbolLayer(PlottedRouteIds.SELECTED_LAYER, PlottedRouteIds.SELECTED_SOURCE).withProperties(
        PropertyFactory.iconImage(Expression.get(PlottedRouteIds.SELECTED_ICON_PROPERTY)),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_CENTER),
        // Amendment 7: each highlight's fade and pulse, per feature, so a frame of the motion is new source data, never a new layer.
        PropertyFactory.iconOpacity(Expression.coalesce(Expression.number(Expression.get(PlottedRouteIds.SELECTED_OPACITY_PROPERTY)), Expression.literal(1f))),
        PropertyFactory.iconSize(Expression.coalesce(Expression.number(Expression.get(PlottedRouteIds.SELECTED_SCALE_PROPERTY)), Expression.literal(1f))),
    )
    if (style.getLayer(MapLayerIds.WAYPOINTS) != null) {
        style.addLayerAbove(selected, MapLayerIds.WAYPOINTS)
    } else {
        Log.w(SIGHTINGS_MAP_TAG, "The waypoints layer is missing; a route's selected stop is drawn above everything.")
        style.addLayer(selected)
    }
}

/**
 * What each of the route's sources receives for [route]; both empty for none, so a closed route clears the map. The selected stop's source
 * receives [accent], the highlight's frame (Amendment 7), at rest unless given.
 */
internal fun plottedRouteFeatureCollections(
    route: PlottedRouteOnMap?,
    accent: RouteStopAccentFrame = RouteStopAccentFrame.settled(route?.selected),
): Map<String, FeatureCollection> = mapOf(
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
    PlottedRouteIds.SELECTED_SOURCE to routeStopAccentFeatures(accent),
)

/** The selected stop's source for one [frame] of its highlight (Amendment 7): a point per highlight drawn, fading ones first, each with its opacity and size. */
internal fun routeStopAccentFeatures(frame: RouteStopAccentFrame): FeatureCollection = FeatureCollection.fromFeatures(
    frame.shown.map { shown ->
        val marker = shown.marker
        Feature.fromGeometry(Point.fromLngLat(marker.at.lng, marker.at.lat)).also {
            val icon = if (marker.trip) MarkerIcon.PLANNED_TRIP_ROUTE_SELECTED else MarkerIcon.WAYPOINT_ROUTE_SELECTED
            it.addStringProperty(PlottedRouteIds.SELECTED_ICON_PROPERTY, icon.imageId)
            // Amendment 6 B: the marker it stands on, so the fan hides it while that marker is fanned (fanHiddenAccentKeys).
            it.addStringProperty(FAN_ACCENT_KEY_PROPERTY, fanAccentKeyValue(marker.fanKey))
            it.addNumberProperty(PlottedRouteIds.SELECTED_OPACITY_PROPERTY, shown.opacity)
            it.addNumberProperty(PlottedRouteIds.SELECTED_SCALE_PROPERTY, shown.scale)
        }
    },
)

/**
 * Pushes [route] to the current style; nothing in a style change (logged by [currentStyle]), which runs it again on load. Not the selected
 * stop's source: its highlight moves frame by frame and is pushed by [routeStopAccentOnCurrentStyle] alone (Amendment 7), so a route
 * change never draws it at rest for a frame in the middle of its fade.
 */
internal fun plottedRouteOnCurrentStyle(loaded: Style?, route: PlottedRouteOnMap?) {
    val style = currentStyle(loaded, "The plotted route") ?: return
    plottedRouteFeatureCollections(route).filterKeys { it != PlottedRouteIds.SELECTED_SOURCE }.forEach { (id, collection) ->
        val source = style.getSourceAs<GeoJsonSource>(id)
        if (source == null) {
            Log.w(SIGHTINGS_MAP_TAG, "The plotted route's source $id is missing; the route is not drawn.")
        } else {
            source.setGeoJson(collection)
        }
    }
}
