package com.zynergylabs.forager.app.ui.map.layers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layer state to MapLibre property values (map layers L0a, A3, with the owner's ruling 3 on
 * `prompts/preserved/2026-09-27-30.md`: opacity is a 0 to 1 multiplier on each of a layer's own
 * opacities, default 1). Headless: [layerPaintFor] is pure; `SightingsMap` only turns its result
 * into `visibility` and opacity properties.
 */
class MapLayerStateTest {

    private fun spec(id: String) = MAP_LAYER_REGISTRY.single { it.id == id }

    private fun paint(id: String, state: MapLayersState = MapLayersState.DEFAULT) = layerPaintFor(spec(id), state)

    @Test
    fun `the default state draws every layer visible at exactly today's opacities`() {
        MAP_LAYER_REGISTRY.forEach { spec ->
            val paint = layerPaintFor(spec, MapLayersState.DEFAULT)
            assertEquals(spec.id, paint.layerId)
            assertTrue("${spec.id} is visible by default", paint.visible)
            assertEquals(spec.id, spec.baseOpacities.map { OpacityValue(it.property, it.base) }, paint.opacities)
        }
        assertEquals(
            listOf(OpacityValue(OpacityProperty.CIRCLE, 0.7f), OpacityValue(OpacityProperty.CIRCLE_STROKE, 0.85f)),
            paint(MapLayerIds.SIGHTINGS).opacities,
        )
    }

    @Test
    fun `an opacity setting scales each of the layer's opacities, the sighting fill and its ring both`() {
        val state = MapLayersState(layers = mapOf(MapLayerIds.SIGHTINGS to LayerState(opacity = 0.5f)))
        assertEquals(
            listOf(OpacityValue(OpacityProperty.CIRCLE, 0.35f), OpacityValue(OpacityProperty.CIRCLE_STROKE, 0.425f)),
            paint(MapLayerIds.SIGHTINGS, state).opacities,
        )
        assertEquals(listOf(OpacityValue(OpacityProperty.ICON, 0.5f)), paint(MapLayerIds.WAYPOINTS, state.copy(layers = mapOf(MapLayerIds.WAYPOINTS to LayerState(opacity = 0.5f)))).opacities)
    }

    @Test
    fun `an opacity setting of zero draws the layer fully transparent but still visible`() {
        val state = MapLayersState(layers = mapOf(MapLayerIds.OFFLINE_REGION_FILL to LayerState(opacity = 0f)))
        val paint = paint(MapLayerIds.OFFLINE_REGION_FILL, state)
        assertTrue(paint.visible)
        assertEquals(listOf(OpacityValue(OpacityProperty.FILL, 0f)), paint.opacities)
    }

    // J8 (planner, continuation 2026-09-28-53, Q4: "A record whose own overlay is switched off gets no
    // highlight"; restated under continuation 2026-09-28-64): hiding a record layer now also hides the
    // journal-entry halo drawn with it. The expectation changed with that ruling: the finds halo is named
    // and asserted hidden, and every other layer is still asserted unchanged, as before.
    @Test
    fun `a hidden layer's visible flag is false, the journal halo drawn with it hides too, and no other layer changes`() {
        val state = MapLayersState(layers = mapOf(MapLayerIds.FINDS to LayerState(visible = false)))
        assertFalse(paint(MapLayerIds.FINDS, state).visible)
        assertTrue("the finds halo is shown by default", paint(MapLayerIds.JOURNAL_ENTRY_FINDS, MapLayersState.DEFAULT).visible)
        assertFalse("the finds halo hides with Finds (-53, Q4)", paint(MapLayerIds.JOURNAL_ENTRY_FINDS, state).visible)
        MAP_LAYER_REGISTRY.filter { it.id != MapLayerIds.FINDS && it.id != MapLayerIds.JOURNAL_ENTRY_FINDS }.forEach {
            assertEquals(it.id, layerPaintFor(it, MapLayersState.DEFAULT), layerPaintFor(it, state))
        }
    }

    @Test
    fun `a casing follows its own track, and its own entry in the state is not read`() {
        val trackState = MapLayersState(layers = mapOf(MapLayerIds.BREADCRUMB to LayerState(visible = false, opacity = 0.5f)))
        val casing = paint(MapLayerIds.BREADCRUMB_CASING, trackState)
        assertFalse(casing.visible)
        assertEquals(listOf(OpacityValue(OpacityProperty.LINE, 0.5f)), casing.opacities)

        val casingOnly = MapLayersState(layers = mapOf(MapLayerIds.KEPT_TRACKS_CASING to LayerState(visible = false)))
        assertTrue(paint(MapLayerIds.KEPT_TRACKS_CASING, casingOnly).visible)
    }

    @Test
    fun `the offline outline follows the offline fill`() {
        val state = MapLayersState(layers = mapOf(MapLayerIds.OFFLINE_REGION_FILL to LayerState(visible = false, opacity = 0.5f)))
        val outline = paint(MapLayerIds.OFFLINE_REGION_OUTLINE, state)
        assertFalse(outline.visible)
        assertEquals(listOf(OpacityValue(OpacityProperty.LINE, 0.5f)), outline.opacities)
        assertEquals(listOf(OpacityValue(OpacityProperty.FILL, 0.1f)), paint(MapLayerIds.OFFLINE_REGION_FILL, state).opacities)
    }

    @Test
    fun `a state entry for a layer the registry does not have changes nothing`() {
        val state = MapLayersState(layers = mapOf("removed-layer" to LayerState(visible = false)))
        MAP_LAYER_REGISTRY.forEach { assertEquals(it.id, layerPaintFor(it, MapLayersState.DEFAULT), layerPaintFor(it, state)) }
    }

    @Test
    fun `an opacity outside 0 to 1 is refused, not clamped`() {
        assertThrows(IllegalArgumentException::class.java) { LayerState(opacity = 1.5f) }
        assertThrows(IllegalArgumentException::class.java) { LayerState(opacity = -0.1f) }
        assertThrows(IllegalArgumentException::class.java) { LayerState(opacity = Float.NaN) }
    }

    // activeLayerCredits

    private fun credited(id: String, credit: String?, group: ZGroup = ZGroup.COLOUR_FIELDS) = MapLayerSpec(
        id = id,
        kind = LayerKind.COLOUR_FIELD,
        renderer = LayerRenderer.FILL,
        sourceId = "src-$id",
        zGroup = group,
        paletteRole = null,
        userToggleable = true,
        userOpacity = true,
        userReorderable = group == ZGroup.COLOUR_FIELDS,
        tapGroup = TapGroup.COLOUR_FIELD,
        baseOpacities = listOf(BaseOpacity(OpacityProperty.FILL, 0.6f)),
        credit = credit,
    )

    @Test
    fun `the real registry adds no credit while no colour field has data`() {
        // Map layers L0b: the synthetic colour fields carry a credit, and they are hidden in the
        // state the map gets whenever the store has no data for them (always, in a release build).
        val noForecastData = withUnavailableColourFieldsHidden(MapLayersState.DEFAULT, MAP_LAYER_REGISTRY, available = emptySet())
        assertEquals(emptyList<String>(), activeLayerCredits(MAP_LAYER_REGISTRY, noForecastData))
    }

    @Test
    fun `each visible layer's credit, in draw order, once, and none for a hidden layer`() {
        val registry = listOf(
            credited("a", "Forecast A"),
            credited("b", "Shared credit"),
            credited("c", "Shared credit"),
            credited("d", null),
            credited("e", "Hidden credit"),
        )
        val state = MapLayersState(
            layers = mapOf("e" to LayerState(visible = false)),
            reorderableOrder = listOf("b", "a"),
        )
        assertEquals(listOf("Shared credit", "Forecast A"), activeLayerCredits(registry, state))
    }
}
