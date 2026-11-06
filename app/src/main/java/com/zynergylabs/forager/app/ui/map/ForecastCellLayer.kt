package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.ForecastBlock
import com.zynergylabs.forager.app.domain.ForecastCell
import com.zynergylabs.forager.app.ui.map.layers.ColourRamp
import com.zynergylabs.forager.app.ui.map.layers.ForecastCellsShown
import kotlin.math.ceil
import kotlin.math.floor
import org.maplibre.android.style.expressions.Expression
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * Below this camera zoom the colour fields request nothing and draw nothing (planner's ruling on Q9,
 * message 3 on dispatch 2026-09-28-03: an explicit operating limit, CLAUDE.md). Chosen so the count of
 * blocks one camera idle asks for stays bounded: at zoom 7 a map sees 360 / (512 * 2^7) = 0.0055 degree
 * per dp, so even a 1280 by 800 dp window (larger than any map pane this app lays out) spans 7.03 by
 * 4.39 degrees at the equator, where a Mercator view spans the most latitude, and touches at most 9 by 6
 * = **54 blocks** (5,400 cells a layer) however it is placed (`ForecastCellLayerTest`). A phone in
 * portrait at zoom 7 sees about 2 by 4.5 degrees. No new copy: the legend still shows while a field is
 * on, and the field is simply empty until the user zooms in.
 *
 * Superseded in part by data part C (dispatch -668): the owner chose "Fix both (Recommended)" (RECORD
 * -656), so below this zoom the legend chip now says "Zoom in to see the forecast"
 * ([com.zynergylabs.forager.app.ui.map.layers.LEGEND_ZOOM_IN_FOR_FORECAST], via
 * [isBelowForecastZoom]). The limit itself is unchanged.
 */
internal const val MIN_FORECAST_ZOOM = 7.0

/** True below [MIN_FORECAST_ZOOM], where the colour fields request and draw nothing. */
internal fun isBelowForecastZoom(zoom: Double): Boolean = zoom < MIN_FORECAST_ZOOM

/**
 * A backstop beside [MIN_FORECAST_ZOOM]: the most 1-degree blocks one camera idle asks for. Zoom alone
 * does not bound a tilted camera's visible area, so a view past this many blocks requests nothing and
 * each colour field draws empty, and `SightingsMap` logs it. Above the 54 blocks the zoom limit allows
 * for a flat camera.
 */
internal const val MAX_FORECAST_BLOCKS = 64

/** Half a cell's side: a cell reaches this far past its centre (0.1-degree cells, edges at x.x5). */
private const val CELL_HALF_DEGREES = 0.05

/**
 * The blocks whose cells can show in the view from ([south], [west]) to ([north], [east]) at camera
 * [zoom], or `null` when nothing is to be requested: below [MIN_FORECAST_ZOOM], or more than
 * [MAX_FORECAST_BLOCKS]. A cell is in view when its square overlaps the view, so its centre can be up to
 * half a cell outside it; the centres that can be in view are the tenths from `south - 0.05` to
 * `north + 0.05`, and the blocks are the ones holding those (a cell belongs to the block holding its
 * centre).
 */
internal fun forecastBlocksToRequest(zoom: Double, south: Double, west: Double, north: Double, east: Double): Set<ForecastBlock>? {
    if (isBelowForecastZoom(zoom)) return null
    fun firstTenth(degrees: Double) = ceil((degrees - CELL_HALF_DEGREES) * 10 - EPSILON) / 10
    fun lastTenth(degrees: Double) = floor((degrees + CELL_HALF_DEGREES) * 10 + EPSILON) / 10
    val blocks = ForecastBlock.touching(
        south = firstTenth(south),
        west = firstTenth(west),
        north = lastTenth(north),
        east = lastTenth(east),
    )
    return blocks.takeIf { it.size <= MAX_FORECAST_BLOCKS }
}

private const val EPSILON = 1e-9

/**
 * A colour field's cells as its source receives them: one 0.1-degree square per **applicable** cell,
 * with its `chance` for the fill colour and its centre as `featureId`. A cell that is not applicable draws
 * nothing (D55 and R5: an unscored cell is left blank), and so does one a file left out.
 */
internal fun forecastCellsFeatureCollection(cells: List<ForecastCell>): FeatureCollection =
    FeatureCollection.fromFeatures(
        cells.filter { it.applicable }.map { cell ->
            val lat = cell.centre.lat
            val lng = cell.centre.lng
            val ring = listOf(
                Point.fromLngLat(lng - CELL_HALF_DEGREES, lat - CELL_HALF_DEGREES),
                Point.fromLngLat(lng + CELL_HALF_DEGREES, lat - CELL_HALF_DEGREES),
                Point.fromLngLat(lng + CELL_HALF_DEGREES, lat + CELL_HALF_DEGREES),
                Point.fromLngLat(lng - CELL_HALF_DEGREES, lat + CELL_HALF_DEGREES),
                Point.fromLngLat(lng - CELL_HALF_DEGREES, lat - CELL_HALF_DEGREES),
            )
            Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply {
                addNumberProperty(CHANCE_PROPERTY, cell.chance)
                addStringProperty(FEATURE_ID_PROPERTY, forecastCellFeatureId(cell))
            }
        },
    )

/** The property a cell's fill colour reads. */
internal const val CHANCE_PROPERTY = "chance"

/**
 * A colour field's `fill-color`: its ramp interpolated linearly on each cell's [CHANCE_PROPERTY]. A plain
 * value built without a native call ([Expression] has none, per this package's other expression
 * builders), so a headless test compares it directly.
 */
internal fun colourFieldFillColour(ramp: ColourRamp): Expression =
    Expression.interpolate(
        Expression.linear(),
        Expression.get(CHANCE_PROPERTY),
        *ramp.stops.map { Expression.stop(it.chance, Expression.color(it.argb)) }.toTypedArray(),
    )

/**
 * What the legend dates a colour field by, from the cells drawn in view: their week, and the earliest
 * `weather_through` among them, so the legend never claims fresher weather than a cell on screen has.
 * Cells that draw nothing do not count. `null` when none is drawn.
 */
internal fun forecastCellsShownOf(cells: List<ForecastCell>): ForecastCellsShown? {
    val drawn = cells.filter { it.applicable }
    if (drawn.isEmpty()) return null
    return ForecastCellsShown(week = drawn.first().week, weatherThrough = drawn.minOf { it.weatherThrough })
}
