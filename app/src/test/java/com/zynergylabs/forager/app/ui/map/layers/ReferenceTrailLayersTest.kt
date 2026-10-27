package com.zynergylabs.forager.app.ui.map.layers

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 8b (dispatch 2026-10-26-15, Amendments 1 and 2; RECORD -1680, -1681, -1682): the official-trails band in the layer
 * registry. Headless: the registry is plain data, and the two files it mirrors are read from the repository.
 *
 * - The ids are written once in Kotlin ([MapLayerIds]) and must be exactly the ones the style's design source makes
 *   (`map-style/overrides/forager-reference.mjs`, read here as text) and the served street-v3 carries.
 * - Each band style layer has its own spec, tappable as a line (8c's taps come only from the registry); the number label
 *   takes no taps; the Forest Service band owns the one switch and the others follow it.
 * - None carries a base opacity: the style's own 0.35 is the only copy of that figure, so the registry's opacity path
 *   writes nothing to these layers but their visibility.
 */
class ReferenceTrailLayersTest {

    private val referenceIds = listOf(MapLayerIds.REFERENCE_FS_BAND, MapLayerIds.REFERENCE_BLM_BAND, MapLayerIds.REFERENCE_FS_NUMBER)

    private val designSource = File("../map-style/overrides/forager-reference.mjs").readText()

    private fun styleLayers(path: String) =
        Json.parseToJsonElement(File(path).readText()).jsonObject.getValue("layers").jsonArray.map { it.jsonObject }

    private fun spec(id: String) = MAP_LAYER_REGISTRY.single { it.id == id }

    @Test
    fun `the Kotlin ids are exactly the reference layer ids the design source makes, and its source id`() {
        val inSource = Regex("""["'](reference_[a-z0-9_]+)["']""").findAll(designSource).map { it.groupValues[1] }.toSet()
        assertEquals(referenceIds.toSet(), inSource)
        val sourceId = Regex("""REFERENCE_SOURCE_ID\s*=\s*"([^"]+)"""").find(designSource)?.groupValues?.get(1)
        assertEquals(MapSourceIds.REFERENCE, sourceId)
    }

    @Test
    fun `the served street-v3 carries the three layers on the reference source, in the registry's order, and v2 carries none`() {
        val v3 = styleLayers("../server/pmtiles-worker/src/street-style-v3.json")
        val inV3 = v3.filter { it.getValue("id").jsonPrimitive.content.startsWith("reference_") }
        assertEquals(referenceIds, inV3.map { it.getValue("id").jsonPrimitive.content })
        inV3.forEach { assertEquals(MapSourceIds.REFERENCE, it.getValue("source").jsonPrimitive.content) }
        val v2 = styleLayers("../server/pmtiles-worker/src/street-style-v2.json")
        assertEquals(emptyList<String>(), v2.map { it.getValue("id").jsonPrimitive.content }.filter { it.startsWith("reference_") })
    }

    @Test
    fun `each reference layer is its own style-layer spec, on the style's source, with the style's id`() {
        referenceIds.forEach { id ->
            val spec = spec(id)
            assertEquals(id, LayerRenderer.STYLE_LAYER, spec.renderer)
            assertEquals(id, MapSourceIds.REFERENCE, spec.sourceId)
            assertEquals(id, LayerKind.LINE, spec.kind)
            assertEquals(id, ZGroup.LINES, spec.zGroup)
        }
        assertEquals(referenceIds, MAP_LAYER_REGISTRY.filter { it.renderer == LayerRenderer.STYLE_LAYER }.map { it.id })
    }

    @Test
    fun `the two bands are tappable as lines and the number label takes no taps`() {
        assertEquals(TapGroup.LINE, spec(MapLayerIds.REFERENCE_FS_BAND).tapGroup)
        assertEquals(TapGroup.LINE, spec(MapLayerIds.REFERENCE_BLM_BAND).tapGroup)
        assertEquals(TapGroup.NONE, spec(MapLayerIds.REFERENCE_FS_NUMBER).tapGroup)
        assertEquals(
            listOf(MapLayerIds.REFERENCE_FS_BAND, MapLayerIds.REFERENCE_BLM_BAND),
            tappableLayerIds(orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT)).filter { it in referenceIds },
        )
    }

    @Test
    fun `one switch - the Forest Service band owns it, the BLM band and the number follow, and only the owner is offered`() {
        assertEquals(null, spec(MapLayerIds.REFERENCE_FS_BAND).stateOwnerId)
        assertEquals(MapLayerIds.REFERENCE_FS_BAND, spec(MapLayerIds.REFERENCE_BLM_BAND).stateOwnerId)
        assertEquals(MapLayerIds.REFERENCE_FS_BAND, spec(MapLayerIds.REFERENCE_FS_NUMBER).stateOwnerId)
        assertEquals(listOf(MapLayerIds.REFERENCE_FS_BAND), referenceIds.filter { spec(it).userToggleable })
        val off = MapLayersState(layers = mapOf(MapLayerIds.REFERENCE_FS_BAND to LayerState(visible = false)))
        referenceIds.forEach { assertEquals(it, false, layerPaintFor(spec(it), off).visible) }
        referenceIds.forEach { assertEquals(it, true, layerPaintFor(spec(it), MapLayersState.DEFAULT).visible) }
    }

    @Test
    fun `no reference spec carries an opacity - the paint it resolves is visibility alone, so the style's 0_35 is never written`() {
        referenceIds.forEach { id ->
            assertEquals(id, emptyList<BaseOpacity>(), spec(id).baseOpacities)
            assertEquals(id, false, spec(id).userOpacity)
            assertEquals(id, false, spec(id).userReorderable)
            assertEquals(id, emptyList<OpacityValue>(), layerPaintFor(spec(id), MapLayersState.DEFAULT).opacities)
        }
        val withOpacity = MAP_LAYER_REGISTRY.map { if (it.id == MapLayerIds.REFERENCE_FS_BAND) it.copy(baseOpacities = listOf(BaseOpacity(OpacityProperty.LINE, 0.35f))) else it }
        assertEquals(
            listOf("${MapLayerIds.REFERENCE_FS_BAND} is a STYLE_LAYER layer but sets line-opacity"),
            registryProblems(withOpacity),
        )
    }

    @Test
    fun `the credit is the owner's wording, on the switch's own layer only`() {
        assertEquals("Trails: USDA Forest Service, CC BY 4.0 · BLM", spec(MapLayerIds.REFERENCE_FS_BAND).credit)
        assertEquals(null, spec(MapLayerIds.REFERENCE_BLM_BAND).credit)
        assertEquals(null, spec(MapLayerIds.REFERENCE_FS_NUMBER).credit)
    }

    @Test
    fun `the reference layers sit below every app line, in the lines band, and the registry has no problems`() {
        val order = orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT)
        val lines = order.filter { it.zGroup == ZGroup.LINES }.map { it.id }
        assertEquals(referenceIds, lines.take(referenceIds.size))
        assertTrue(lines.drop(referenceIds.size).none { it in referenceIds })
        assertEquals(emptyList<String>(), registryProblems(MAP_LAYER_REGISTRY))
    }

    @Test
    fun `a stored choice for the switch is restored, and one for a follower is refused`() {
        val restored = restoreMapLayersState(
            com.zynergylabs.forager.app.domain.MapLayerPreferences.NONE.copy(
                visibility = mapOf(MapLayerIds.REFERENCE_FS_BAND to false, MapLayerIds.REFERENCE_BLM_BAND to false),
            ),
            MAP_LAYER_REGISTRY,
        )
        assertEquals(false, restored.state.stateOf(MapLayerIds.REFERENCE_FS_BAND).visible)
        assertEquals(
            listOf("stored visibility for ${MapLayerIds.REFERENCE_BLM_BAND}: the Layers sheet does not switch this layer"),
            restored.rejected,
        )
    }
}
