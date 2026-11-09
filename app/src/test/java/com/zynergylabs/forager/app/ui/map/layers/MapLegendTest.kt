package com.zynergylabs.forager.app.ui.map.layers

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The legend chip's shown and hidden rule and its content (map layers L0b, B4, as planner message 2
 * gave it), from the same effective layer state the map draws with.
 */
class MapLegendTest {

    private val registry = MAP_LAYER_REGISTRY
    private val both = setOf(MapLayerIds.FORECAST_CHANTERELLES, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS)

    private fun effective(available: Set<String>, stored: MapLayersState = MapLayersState.DEFAULT) =
        withUnavailableColourFieldsHidden(stored, registry, available)

    private fun hidden(id: String) = MapLayersState(layers = mapOf(id to LayerState(visible = false)))

    @Test
    fun `the legend shows while a colour field is visible, and not once none is`() {
        val one = mapLegendFor(effective(setOf(MapLayerIds.FORECAST_CHANTERELLES)), registry, COLOUR_FIELDS, emptyMap())
        assertEquals(listOf(MapLayerIds.FORECAST_CHANTERELLES), one?.layers?.map { it.layerId })

        assertNull("no data: no colour field is visible", mapLegendFor(effective(emptySet()), registry, COLOUR_FIELDS, emptyMap()))
        assertNull(
            "data, but the user switched it off",
            mapLegendFor(effective(setOf(MapLayerIds.FORECAST_CHANTERELLES), hidden(MapLayerIds.FORECAST_CHANTERELLES)), registry, COLOUR_FIELDS, emptyMap()),
        )
    }

    @Test
    fun `collapsed, the chip names the one visible layer, or reads 2 layers, and expanded lists them top of the draw order first`() {
        val one = mapLegendFor(effective(both, hidden(MapLayerIds.FORECAST_CHANTERELLES)), registry, COLOUR_FIELDS, emptyMap())
        assertEquals("Test forecast: chicken of the woods (synthetic data)", one?.collapsedLabel)

        val two = mapLegendFor(effective(both), registry, COLOUR_FIELDS, emptyMap())
        assertEquals("2 layers", two?.collapsedLabel)
        assertEquals(
            listOf("Test forecast: chanterelles (synthetic data)", "Test forecast: chicken of the woods (synthetic data)"),
            two?.layers?.map { it.name },
        )
        assertEquals(COLOUR_FIELDS.single { it.layerId == MapLayerIds.FORECAST_CHANTERELLES }.ramp, two?.layers?.first()?.ramp)

        val reordered = moveColourField(effective(both), registry, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS, ColourFieldMove.UP)
        assertEquals(
            "the legend follows the stored order",
            listOf(MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS, MapLayerIds.FORECAST_CHANTERELLES),
            mapLegendFor(reordered, registry, COLOUR_FIELDS, emptyMap())?.layers?.map { it.layerId },
        )
    }

    /**
     * Data part C (dispatch -668, the owner's "Fix both (Recommended)", RECORD -656): below the
     * colour fields' minimum zoom the legend carries the note; otherwise it does not.
     */
    @Test
    fun `the legend is marked zoomed out only when the map says the camera is below the forecast's zoom`() {
        val zoomedOut = mapLegendFor(effective(both), registry, COLOUR_FIELDS, emptyMap(), zoomedOut = true)
        val zoomedIn = mapLegendFor(effective(both), registry, COLOUR_FIELDS, emptyMap(), zoomedOut = false)

        assertEquals(true, zoomedOut?.zoomedOut)
        assertEquals(false, zoomedIn?.zoomedOut)
        assertEquals("Zoom in to see the forecast", LEGEND_ZOOM_IN_FOR_FORECAST)
        assertNull("no visible field: no legend, zoomed out or not", mapLegendFor(effective(emptySet()), registry, COLOUR_FIELDS, emptyMap(), zoomedOut = true))
    }

    @Test
    fun `each layer's dates read Week of the week, weather to the weather date, and are left out while none of its cells is in view`() {
        val shown = mapOf(
            MapLayerIds.FORECAST_CHANTERELLES to ForecastCellsShown(week = LocalDate.of(2026, 9, 28), weatherThrough = LocalDate.of(2026, 9, 26)),
        )

        val legend = mapLegendFor(effective(both), registry, COLOUR_FIELDS, shown)

        assertEquals("Week of Sep 28, 2026, weather to Sep 26, 2026", legend?.layers?.single { it.layerId == MapLayerIds.FORECAST_CHANTERELLES }?.dates)
        assertNull(legend?.layers?.single { it.layerId == MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS }?.dates)
        assertEquals("Week of Sep 28, 2026, weather to Sep 26, 2026", legendDatesLine(shown.values.single()))
    }
}
