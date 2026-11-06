package com.zynergylabs.forager.app.ui.map.layers

import java.time.LocalDate
import com.zynergylabs.forager.app.ui.format.displayDate

/**
 * What a colour field's cells in view say about their own dates: the forecast week and the weather
 * they were computed through. [weatherThrough] is the earliest date among the cells in view, so the
 * legend never claims fresher weather than some cell on screen has.
 */
data class ForecastCellsShown(val week: LocalDate, val weatherThrough: LocalDate)

/** One visible colour field in the expanded legend. [dates] is `null` while none of its cells is in view. */
data class LegendLayer(val layerId: String, val name: String, val ramp: ColourRamp, val dates: String?)

/**
 * The legend chip's content: [collapsedLabel] on the chip, [layers] top of the draw order first when
 * expanded. [zoomedOut] is true while the camera is below the colour fields' minimum zoom, and the chip
 * then says [LEGEND_ZOOM_IN_FOR_FORECAST], collapsed or expanded (data part C, dispatch -668).
 */
data class MapLegend(val collapsedLabel: String, val layers: List<LegendLayer>, val zoomedOut: Boolean = false)

/** Each ramp's end labels (planner message 2: "its ramp with 0% and 100% end labels"). */
const val LEGEND_RAMP_LOW_LABEL = "0%"
const val LEGEND_RAMP_HIGH_LABEL = "100%"

/** Beside the empty-cell swatch, per visible layer (the dispatch, B4, and D55: "no forecast here" in the legend). */
const val LEGEND_NO_FORECAST_HERE = "no forecast here"

/**
 * Under the chip's label while the camera is below the colour fields' minimum zoom. The owner's own
 * words, verbatim (RECORD -656, "Small ones: Fix both (Recommended)").
 */
const val LEGEND_ZOOM_IN_FOR_FORECAST = "Zoom in to see the forecast"

/**
 * The reference class, once, under the ramps (owner: "Use that wording (Recommended)"; planner message 2).
 * Source: slayer8366/Forager-app 0172c33,
 * presentation/src/main/kotlin/com/zynergy/forager/presentation/SightingChance.kt:33-35.
 */
const val LEGEND_REFERENCE_CLASS =
    "Chance this group is reported in each 11 km cell this week, where anyone is reporting fungi. " +
        "Compare areas, not spots. A high chance is not a find."

/**
 * The legend chip's content (map layers L0b, B4, as planner message 2 gave it), or `null` when the chip
 * is not shown: it shows only while at least one colour field is visible in [state], the state the map
 * draws with (`withUnavailableColourFieldsHidden` already applied, so a layer with no data never counts).
 *
 * Collapsed, the chip names the one visible layer, or reads "2 layers". Expanded, it lists every visible
 * layer, top of the draw order first as the sheet does, each with its name, its ramp and, while any of
 * its cells is in view ([shown]), its week and data dates ([legendDatesLine]). The chip also carries,
 * per layer, "no forecast here" beside an empty swatch, and the reference class once under the ramps;
 * both are fixed text ([LEGEND_NO_FORECAST_HERE], [LEGEND_REFERENCE_CLASS]).
 */
fun mapLegendFor(
    state: MapLayersState,
    registry: List<MapLayerSpec>,
    colourFields: List<ColourFieldSpec>,
    shown: Map<String, ForecastCellsShown>,
    zoomedOut: Boolean = false,
): MapLegend? {
    val specsById = colourFields.associateBy { it.layerId }
    val visible = colourFieldsTopFirst(registry, state)
        .filter { layerPaintFor(it, state, registry).visible }
        .mapNotNull { specsById[it.id] }
    if (visible.isEmpty()) return null
    val collapsed = visible.singleOrNull()?.label ?: "${visible.size} layers"
    return MapLegend(
        collapsedLabel = collapsed,
        layers = visible.map { field -> LegendLayer(field.layerId, field.label, field.ramp, shown[field.layerId]?.let(::legendDatesLine)) },
        zoomedOut = zoomedOut,
    )
}

/**
 * "Week of <week>, weather to <weather_through>" (planner message 2), the dates the cells carry (D55: `week` is the ISO week
 * start, `weather_through` a date), written "Sep 28, 2026" like every date in the app (RECORD -656, applied by -702).
 */
fun legendDatesLine(shown: ForecastCellsShown): String =
    "Week of ${displayDate(shown.week)}, weather to ${displayDate(shown.weatherThrough)}"
