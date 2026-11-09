package com.zynergylabs.forager.app.ui.map.layers

import com.zynergylabs.forager.app.domain.MapLayerPreferences
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.attributionCaption
import com.zynergylabs.forager.app.ui.map.mapAttributionFor
import com.zynergylabs.forager.app.ui.map.mapCreditsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The map's layer state as the Layers sheet and the store hand it to the map (map layers L0b): stored
 * choices restored against the registry (B3), the colour fields' order and opacity (B1, checked through
 * `orderedLayers` and `layerPaintFor`, the functions the map draws with), a colour field without data
 * hidden (B5), and the synthetic credit in the caption (B6).
 */
class MapLayerPreferencesStateTest {

    private val registry = MAP_LAYER_REGISTRY
    private fun spec(id: String) = registry.single { it.id == id }
    private fun colourGroup(state: MapLayersState) = orderedLayers(registry, state).filter { it.zGroup == ZGroup.COLOUR_FIELDS }.map { it.id }

    @Test
    fun `stored visibility applies to the layers the sheet can switch, and a stored choice for any other layer is refused and reported`() {
        val restored = restoreMapLayersState(
            MapLayerPreferences(
                visibility = mapOf(
                    MapLayerIds.FINDS to false,
                    MapLayerIds.KEPT_TRACKS to false,
                    MapLayerIds.SIGHTINGS to false,
                    "no-such-layer" to false,
                ),
                opacity = emptyMap(),
                order = emptyList(),
            ),
            registry,
        )

        assertEquals(false, restored.state.stateOf(MapLayerIds.FINDS).visible)
        assertEquals(false, restored.state.stateOf(MapLayerIds.KEPT_TRACKS).visible)
        assertEquals("the sighting dots are not in the sheet, so a stored choice cannot hide them", true, restored.state.stateOf(MapLayerIds.SIGHTINGS).visible)
        assertEquals(2, restored.rejected.size)
        assertTrue(restored.rejected.toString(), restored.rejected.any { MapLayerIds.SIGHTINGS in it })
        assertTrue(restored.rejected.toString(), restored.rejected.any { "no-such-layer" in it })
    }

    @Test
    fun `a stored opacity is kept only for a layer with a slider, and a value outside 0 to 1 is refused, never clamped`() {
        val restored = restoreMapLayersState(
            MapLayerPreferences(
                visibility = emptyMap(),
                opacity = mapOf(
                    MapLayerIds.FORECAST_CHANTERELLES to 0.4f,
                    MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS to 1.5f,
                    MapLayerIds.FINDS to 0.3f,
                ),
                order = emptyList(),
            ),
            registry,
        )

        assertEquals(0.4f, restored.state.stateOf(MapLayerIds.FORECAST_CHANTERELLES).opacity)
        assertEquals("refused, so the default, not 1.5 clamped to 1", 1f, restored.state.stateOf(MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS).opacity)
        assertEquals(1f, restored.state.stateOf(MapLayerIds.FINDS).opacity)
        assertEquals(2, restored.rejected.size)
        assertTrue(restored.rejected.toString(), restored.rejected.any { MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS in it && "1.5" in it })
        assertTrue(restored.rejected.toString(), restored.rejected.any { MapLayerIds.FINDS in it })
    }

    @Test
    fun `a stored order keeps only the colour fields, in the stored order, and the map draws them in it`() {
        val restored = restoreMapLayersState(
            MapLayerPreferences(
                visibility = emptyMap(),
                opacity = emptyMap(),
                order = listOf(MapLayerIds.FINDS, MapLayerIds.FORECAST_CHANTERELLES, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS),
            ),
            registry,
        )

        assertEquals(listOf(MapLayerIds.FORECAST_CHANTERELLES, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS), restored.state.reorderableOrder)
        assertEquals(listOf(MapLayerIds.FORECAST_CHANTERELLES, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS), colourGroup(restored.state))
        assertEquals(1, restored.rejected.size)
        assertTrue(restored.rejected.toString(), restored.rejected.single().contains(MapLayerIds.FINDS))
    }

    @Test
    fun `the colour fields list top of the draw order first, Move up and Move down step one place, and opacity multiplies through layerPaintFor`() {
        val start = MapLayersState.DEFAULT
        // Registry order is bottom to top: chicken of the woods, then chanterelles on top.
        assertEquals(listOf(MapLayerIds.FORECAST_CHANTERELLES, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS), colourFieldsTopFirst(registry, start).map { it.id })

        val chickenUp = moveColourField(start, registry, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS, ColourFieldMove.UP)
        assertEquals(listOf(MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS, MapLayerIds.FORECAST_CHANTERELLES), colourFieldsTopFirst(registry, chickenUp).map { it.id })
        assertEquals("drawn bottom to top", listOf(MapLayerIds.FORECAST_CHANTERELLES, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS), colourGroup(chickenUp))

        assertEquals("already on top: no change", chickenUp, moveColourField(chickenUp, registry, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS, ColourFieldMove.UP))
        val back = moveColourField(chickenUp, registry, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS, ColourFieldMove.DOWN)
        assertEquals(listOf(MapLayerIds.FORECAST_CHANTERELLES, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS), colourFieldsTopFirst(registry, back).map { it.id })

        val half = back.copy(layers = back.layers + (MapLayerIds.FORECAST_CHANTERELLES to LayerState(visible = true, opacity = 0.5f)))
        val chanterelles = spec(MapLayerIds.FORECAST_CHANTERELLES)
        val base = chanterelles.baseOpacities.single()
        assertEquals(OpacityProperty.FILL, base.property)
        assertEquals(listOf(OpacityValue(OpacityProperty.FILL, base.base * 0.5f)), layerPaintFor(chanterelles, half).opacities)
    }

    @Test
    fun `a colour field with no data is hidden in the state the map gets, whatever the stored choice, and nothing else changes`() {
        val stored = MapLayersState(layers = mapOf(MapLayerIds.FINDS to LayerState(visible = false)))

        val effective = withUnavailableColourFieldsHidden(stored, registry, available = setOf(MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS))

        assertEquals(false, effective.stateOf(MapLayerIds.FORECAST_CHANTERELLES).visible)
        assertEquals(true, effective.stateOf(MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS).visible)
        assertEquals(false, effective.stateOf(MapLayerIds.FINDS).visible)
        assertEquals(true, effective.stateOf(MapLayerIds.WAYPOINTS).visible)
        assertEquals("the stored state itself is not rewritten", true, stored.stateOf(MapLayerIds.FORECAST_CHANTERELLES).visible)
    }

    @Test
    fun `while a synthetic layer is visible the caption gains the synthetic credit once, and with none visible it does not`() {
        val both = withUnavailableColourFieldsHidden(
            MapLayersState.DEFAULT,
            registry,
            available = setOf(MapLayerIds.FORECAST_CHANTERELLES, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS),
        )
        val credits = mapCreditsFor(Basemap.OSM_STANDARD, useOfflineTiles = false, layerCredits = activeLayerCredits(registry, both))
        assertEquals(listOf(mapAttributionFor(Basemap.OSM_STANDARD, false), "Synthetic test data"), credits)
        assertEquals("© OpenStreetMap contributors · Synthetic test data", attributionCaption(credits))

        val none = withUnavailableColourFieldsHidden(MapLayersState.DEFAULT, registry, available = emptySet())
        assertEquals(emptyList<String>(), activeLayerCredits(registry, none))

        val userHidBoth = both.copy(
            layers = both.layers +
                (MapLayerIds.FORECAST_CHANTERELLES to LayerState(visible = false)) +
                (MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS to LayerState(visible = false)),
        )
        assertEquals(emptyList<String>(), activeLayerCredits(registry, userHidBoth))
    }
}
