package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.ui.map.layers.LayerState
import com.zynergylabs.forager.app.ui.map.layers.LayerRenderer
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.activeLayerCredits
import com.zynergylabs.forager.app.ui.map.layers.layerPaintFor
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 8b (dispatch 2026-10-26-15 Amendment 2): the official-trails switch on a loaded style, headless. The style is a
 * fixture read from the repository: the served street-v3 (which holds the three reference layers) and street-v2 (today's,
 * which holds none), each as the set of its layer ids behind [StyleLayerSwitches], the seam `MapLibreStyleLayerSwitches`
 * puts over a real `Style`. Every write the code makes to the style is recorded.
 *
 * What runs here is what the map runs: [applyReferenceTrailsOnStyleLoad] is the one call in `MapStyleReload`'s style-load
 * callback; [applyStyleLayerPaint] over [layerPaintFor] of each style-layer spec is `MapFan`'s registry walk, the path a
 * flip of the switch takes; [withReferenceTrailsHiddenUnlessLoaded] is what `SightingsMap` draws, credits and hit-tests
 * with; and the caption is `SightingsMapLayout`'s own expression over it.
 */
class ReferenceTrailsVisibilityTest {

    private val referenceIds = listOf(MapLayerIds.REFERENCE_FS_BAND, MapLayerIds.REFERENCE_BLM_BAND, MapLayerIds.REFERENCE_FS_NUMBER)

    private fun styleJson(path: String) = Json.parseToJsonElement(File(path).readText()).jsonObject

    private fun layerIds(path: String): Set<String> =
        styleJson(path).getValue("layers").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet()

    /** A loaded style holding exactly [ids]; records every visibility it is given, in order. */
    private class FixtureStyle(private val ids: Set<String>) : StyleLayerSwitches {
        val writes = mutableListOf<Pair<String, Boolean>>()
        override fun has(layerId: String): Boolean = layerId in ids
        override fun setVisible(layerId: String, visible: Boolean) {
            check(layerId in ids) { "wrote $layerId, which this style does not have" }
            writes += layerId to visible
        }
    }

    private fun v3() = FixtureStyle(layerIds("../server/pmtiles-worker/src/street-style-v3.json"))
    private fun v2() = FixtureStyle(layerIds("../server/pmtiles-worker/src/street-style-v2.json"))

    private val off = MapLayersState(layers = mapOf(MapLayerIds.REFERENCE_FS_BAND to LayerState(visible = false)))

    /** `MapFan`'s walk of the registry, for the style-layer specs: what a flip of the switch does to the loaded style. */
    private fun flip(style: StyleLayerSwitches, drawn: MapLayersState) =
        MAP_LAYER_REGISTRY.filter { it.renderer == LayerRenderer.STYLE_LAYER }.forEach { applyStyleLayerPaint(style, layerPaintFor(it, drawn)) }

    private fun caption(stored: MapLayersState, loaded: Boolean, basemap: Basemap = Basemap.FORAGER_TRAIL) =
        attributionCaption(mapCreditsFor(basemap, false, activeLayerCredits(MAP_LAYER_REGISTRY, withReferenceTrailsHiddenUnlessLoaded(stored, loaded))))

    @Test
    fun `on a v3 style load the band is found and its three layers take the user's choice - on`() {
        val style = v3()
        assertEquals(true, applyReferenceTrailsOnStyleLoad(style, drawnOnThisMap = true, chosen = MapLayersState.DEFAULT))
        assertEquals(referenceIds.map { it to true }, style.writes)
    }

    @Test
    fun `on a v3 style load with the switch off, all three layers are hidden`() {
        val style = v3()
        assertEquals(true, applyReferenceTrailsOnStyleLoad(style, drawnOnThisMap = true, chosen = off))
        assertEquals(referenceIds.map { it to false }, style.writes)
    }

    @Test
    fun `on today's v2 style load nothing is found and nothing is written`() {
        val style = v2()
        assertEquals(false, applyReferenceTrailsOnStyleLoad(style, drawnOnThisMap = true, chosen = MapLayersState.DEFAULT))
        assertEquals(emptyList<Pair<String, Boolean>>(), style.writes)
    }

    @Test
    fun `under Topographical the Street style below carries the layers but the band is not offered, and they are hidden`() {
        val style = v3()
        assertEquals(false, applyReferenceTrailsOnStyleLoad(style, drawnOnThisMap = false, chosen = MapLayersState.DEFAULT))
        assertEquals(referenceIds.map { it to false }, style.writes)
    }

    @Test
    fun `a flip of the switch on a v3 map hides then shows all three layers through the registry walk`() {
        val style = v3()
        flip(style, withReferenceTrailsHiddenUnlessLoaded(off, loaded = true))
        flip(style, withReferenceTrailsHiddenUnlessLoaded(MapLayersState.DEFAULT, loaded = true))
        assertEquals(referenceIds.map { it to false } + referenceIds.map { it to true }, style.writes)
    }

    @Test
    fun `a flip on today's v2 map writes nothing and throws nothing`() {
        val style = v2()
        flip(style, withReferenceTrailsHiddenUnlessLoaded(off, loaded = false))
        flip(style, withReferenceTrailsHiddenUnlessLoaded(MapLayersState.DEFAULT, loaded = false))
        assertEquals(emptyList<Pair<String, Boolean>>(), style.writes)
    }

    @Test
    fun `the drawn state hides the reference layers while the loaded style lacks them, and leaves every other layer as given`() {
        val given = MapLayersState(layers = mapOf(MapLayerIds.FINDS to LayerState(visible = false)))
        val drawn = withReferenceTrailsHiddenUnlessLoaded(given, loaded = false)
        referenceIds.forEach { id -> assertEquals(id, false, layerPaintFor(MAP_LAYER_REGISTRY.single { it.id == id }, drawn).visible) }
        MAP_LAYER_REGISTRY.filter { it.renderer != LayerRenderer.STYLE_LAYER }.forEach {
            assertEquals(it.id, layerPaintFor(it, given), layerPaintFor(it, drawn))
        }
        assertEquals(given, withReferenceTrailsHiddenUnlessLoaded(given, loaded = true))
    }

    @Test
    fun `the caption adds the credit only while the band is on and loaded, and on today's style it is exactly what it was`() {
        val today = attributionCaption(mapCreditsFor(Basemap.FORAGER_TRAIL, false, activeLayerCredits(MAP_LAYER_REGISTRY.filter { it.renderer != LayerRenderer.STYLE_LAYER }, MapLayersState.DEFAULT)))
        val credit = "Trails: USDA Forest Service, CC BY 4.0 · BLM"
        assertEquals("$today · $credit", caption(MapLayersState.DEFAULT, loaded = true))
        assertEquals(today, caption(off, loaded = true))
        assertEquals(today, caption(MapLayersState.DEFAULT, loaded = false))
        Basemap.entries.filter { it != Basemap.FORAGER_TRAIL }.forEach { basemap ->
            assertTrue(basemap.name, !caption(MapLayersState.DEFAULT, loaded = false, basemap = basemap).contains(credit))
        }
    }

    @Test
    fun `at night each band takes Trail Map's plain V1 recolour, like every other line`() {
        val layers = styleJson("../server/pmtiles-worker/src/street-style-v3.json").getValue("layers").jsonArray.map { it.jsonObject }
        listOf(MapLayerIds.REFERENCE_FS_BAND, MapLayerIds.REFERENCE_BLM_BAND).forEach { id ->
            val layer = layers.single { it.getValue("id").jsonPrimitive.content == id }
            val day = layer.getValue("paint").jsonObject.getValue("line-color").jsonPrimitive.content
            val property = LayerColourProperty(id, "line-color", StyleColourValue.Literal(day))
            val street = streetNightRecolourOf(property, layer.getValue("source-layer").jsonPrimitive.content)
            assertTrue("$id: $street", street is NightRecolour.Recoloured)
            assertEquals(id, offlineNightRecolourOf(property), street)
            assertNotEquals(id, StyleColourValue.Literal(day), (street as NightRecolour.Recoloured).night)
        }
    }
}
