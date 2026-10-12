package com.zynergylabs.forager.app.ui.map.layers

/** One stop of a colour ramp: at [chance] (0 to 1) a cell is filled with [argb]. */
data class RampStop(val chance: Float, val argb: Int)

/** A sequential colour ramp, lowest chance first. */
data class ColourRamp(val stops: List<RampStop>)

/**
 * A colour field the map draws from stored forecast cells (map layers L0b, B6 and B7): its registry
 * layer and source, the forecast group whose cells it draws, its name, and its ramp. Beside the
 * registry, not inside [MapLayerSpec], because only this kind of layer has a group and a ramp.
 *
 * Every colour field today is a chance layer (planner message 2 on dispatch 2026-09-28-03: "Both
 * layers are chance layers in the real format"), so the legend gives each a 0% and 100% end label.
 * R8 in forager-forecast allows a percent only on a chance layer; a later colour field that is not
 * one needs its own flag here before it can be added.
 */
data class ColourFieldSpec(
    val layerId: String,
    val sourceId: String,
    val group: String,
    val label: String,
    val ramp: ColourRamp,
)

/** The attribution credit every synthetic colour field adds while it is visible (dispatch, B6). */
const val SYNTHETIC_DATA_CREDIT = "Synthetic test data"

/**
 * The two synthetic test layers (map layers L0b, B6, as planner message 2 changed it): one chance
 * layer per group, for the forecast project's first two groups (forager-forecast D9), named as test
 * data so the fixed term for the real number is never attached to fake values (owner: "Real format,
 * 'test data' name"). Their cells come only from the debug build's synthetic store; the release
 * build's store reports no forecast data, so neither layer is ever offered or drawn there.
 *
 * The ramps are the L0b coder's choice, stated in the completion report. Both run light to dark, in
 * hues away from the marker palette's purples and magentas (`MapPalette`), and each starts at a
 * saturated colour so the lowest chance still reads as a filled cell, never as the empty "no forecast
 * here" cell (R5: an unscored cell draws nothing).
 */
val COLOUR_FIELDS: List<ColourFieldSpec> = listOf(
    ColourFieldSpec(
        layerId = MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS,
        sourceId = MapSourceIds.FORECAST_CHICKEN_OF_THE_WOODS,
        group = "chicken-of-the-woods",
        label = "Test forecast: chicken of the woods (synthetic data)",
        ramp = ColourRamp(
            listOf(
                RampStop(0f, 0xFF80DEEA.toInt()),
                RampStop(0.25f, 0xFF26C6DA.toInt()),
                RampStop(0.5f, 0xFF0097A7.toInt()),
                RampStop(0.75f, 0xFF00697A.toInt()),
                RampStop(1f, 0xFF003B4A.toInt()),
            ),
        ),
    ),
    ColourFieldSpec(
        layerId = MapLayerIds.FORECAST_CHANTERELLES,
        sourceId = MapSourceIds.FORECAST_CHANTERELLES,
        group = "chanterelles",
        label = "Test forecast: chanterelles (synthetic data)",
        ramp = ColourRamp(
            listOf(
                RampStop(0f, 0xFFFFD54F.toInt()),
                RampStop(0.25f, 0xFFFFB300.toInt()),
                RampStop(0.5f, 0xFFFB8C00.toInt()),
                RampStop(0.75f, 0xFFE65100.toInt()),
                RampStop(1f, 0xFF8D2B00.toInt()),
            ),
        ),
    ),
)
