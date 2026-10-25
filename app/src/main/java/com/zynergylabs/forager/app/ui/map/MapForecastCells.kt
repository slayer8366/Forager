package com.zynergylabs.forager.app.ui.map

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.zynergylabs.forager.app.domain.ForecastCell
import com.zynergylabs.forager.app.domain.ForecastCellsResult
import com.zynergylabs.forager.app.ui.map.layers.COLOUR_FIELDS
import com.zynergylabs.forager.app.ui.map.layers.ForecastCellsShown
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * The map's colour-field cells: one effect, moved whole from [SightingsMap] by UI split Phase 2.2 (dispatch
 * 2026-10-19-29), called from the place it stood there and keyed exactly as it was. [forecast] is the feed as
 * [SightingsMap] has it, captured when the effect launches, as before; the feed's latest callbacks are read
 * through [SightingsMapState.forecast], as `currentForecast` was. The comment inside is the effect's own.
 */
@Composable
internal fun MapForecastCells(state: SightingsMapState, forecast: MapForecastFeed?) {
    // Colour-field cells (map layers L0b, B5): each time the camera goes idle, and after every style
    // load (a basemap change and a night-mode change both reload the style, which drops every source,
    // and initializeOverlayLayers re-adds the cell sources empty), the store is asked for the blocks
    // touching the visible area, per colour field the feed names, for the feed's week. That is the same
    // path a downloaded store will use. Below MIN_FORECAST_ZOOM, or past the MAX_FORECAST_BLOCKS
    // backstop, nothing is requested and each field draws empty (planner's ruling on Q9). A field the
    // store has no data for draws empty too. What the drawn cells say about their dates goes back to
    // the host for the legend (MapForecastFeed.onCellsShown). Keyed on the feed's store, week and
    // groups rather than the feed itself, whose callback is a new lambda on every recomposition.
    LaunchedEffect(state.loadedStyle, forecast?.store, forecast?.week, forecast?.groupsByLayer, state.cameraIdleCount) {
        val style = state.loadedStyle ?: return@LaunchedEffect
        val map = state.mapLibreMap ?: return@LaunchedEffect
        val feed = forecast
        val blocks = feed?.takeIf { it.groupsByLayer.isNotEmpty() }?.let {
            val zoom = map.cameraPosition.zoom
            val bounds = map.projection.visibleRegion.latLngBounds
            forecastBlocksToRequest(zoom, bounds.latitudeSouth, bounds.longitudeWest, bounds.latitudeNorth, bounds.longitudeEast)
                .also { requested ->
                    if (requested == null && zoom >= MIN_FORECAST_ZOOM) {
                        Log.w(SIGHTINGS_MAP_TAG, "The view touches more than $MAX_FORECAST_BLOCKS forecast blocks; no cells requested.")
                    }
                }
        }
        val shown = mutableMapOf<String, ForecastCellsShown>()
        COLOUR_FIELDS.forEach { field ->
            val group = feed?.groupsByLayer?.get(field.layerId)
            val cells = if (feed == null || group == null || blocks == null) {
                emptyList()
            } else {
                when (val result = feed.store.cells(group, feed.week, blocks)) {
                    ForecastCellsResult.NoForecastData -> emptyList()
                    is ForecastCellsResult.Cells -> {
                        if (result.rejectedCount > 0) {
                            Log.w(SIGHTINGS_MAP_TAG, "${result.rejectedCount} forecast feature(s) for $group were rejected and are not drawn.")
                        }
                        result.cells
                    }
                }
            }
            if (!pushForecastCells(style, field.sourceId, cells)) return@LaunchedEffect
            forecastCellsShownOf(cells)?.let { shown[field.layerId] = it }
        }
        state.forecast?.onCellsShown?.invoke(shown)
        // Data part C (dispatch -668): whether the legend should say "Zoom in to see the forecast".
        // Only while there is a feed to draw; with none the legend is not shown at all.
        state.forecast?.onZoomedOutChanged?.invoke(feed != null && isBelowForecastZoom(map.cameraPosition.zoom))
    }
}

/**
 * One colour field's cells pushed into [style]'s [sourceId] source: the read that crashed on the S22 (dispatch
 * 2026-10-25-01, finding F1), moved out of the effect above so a test can hand it a style in the state MapLibre leaves
 * an old style in. A source the style does not have is logged and skipped, as before. Returns whether the effect may
 * go on to the next field.
 *
 * A style that is no longer fully loaded is logged and stops the effect (false). `MapLibreMap.setStyle` clears the
 * old style at once (MapLibre 13.5.0, read with javap), and every getter on it then throws. [SightingsMapState.loadedStyle]
 * keeps that old style until the new style's callback (MapStyleReload.kt). A run of the effect in that gap, for
 * example on a camera idle, would read a cleared style; on the S22 that crashed the app 4 times in 17 style changes.
 * Nothing is drawn on a style that is gone, and the effect stops before the legend callbacks, so nothing the user sees
 * changes from that run. The new style's callback sets loadedStyle, a key of the effect, and that run draws the cells.
 * The read the effect skips is the one that used to crash.
 */
internal fun pushForecastCells(style: Style, sourceId: String, cells: List<ForecastCell>): Boolean {
    if (false && !style.isFullyLoaded) {
        Log.w(SIGHTINGS_MAP_TAG, "The $sourceId source was not read: a newer style is loading. The cells are drawn again when it finishes.")
        return false
    }
    val source = style.getSourceAs<GeoJsonSource>(sourceId)
    if (source == null) {
        Log.w(SIGHTINGS_MAP_TAG, "The $sourceId source is not in the loaded style; its cells were not drawn.")
    } else {
        source.setGeoJson(forecastCellsFeatureCollection(cells))
    }
    return true
}
