package com.forager.app.ui.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Night mode's basemap dimming, checked at the level a JVM test can actually reach: the style JSON
 * `styleJsonFor` produces. `BasemapStyleTest`'s own doc comment records why the layers themselves
 * cannot be constructed here — every MapLibre `Layer`/`Source` subclass constructor calls a native
 * initialiser — so this asserts the document handed to MapLibre, not the render.
 *
 * That boundary is the point of the check rather than a limitation of it: the failure this guards
 * against is the paint block being absent, malformed, or applied in day mode, all of which are
 * properties of the JSON. Whether 0.22 brightness is *comfortable* is a device-gate question and
 * no assertion here speaks to it.
 */
class BasemapNightStyleTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun rasterLayer(basemap: Basemap, night: Boolean) =
        json.parseToJsonElement(styleJsonFor(basemap, night = night))
            .jsonObject.getValue("layers").jsonArray
            .single { it.jsonObject.getValue("id").jsonPrimitive.content == RASTER_LAYER_ID }
            .jsonObject

    @Test
    fun `day mode adds no paint block at all`() {
        for (basemap in Basemap.entries) {
            assertNull(
                "${basemap.name} should carry no raster paint in day mode — the tiles ship as authored",
                rasterLayer(basemap, night = false)["paint"],
            )
        }
    }

    @Test
    fun `night mode dims, desaturates and re-sharpens every basemap`() {
        for (basemap in Basemap.entries) {
            val paint = rasterLayer(basemap, night = true)["paint"]
                ?: error("${basemap.name} has no raster paint in night mode")
            val obj = paint.jsonObject

            val brightness = obj.getValue("raster-brightness-max").jsonPrimitive.double
            assertTrue(
                "${basemap.name}: brightness cap $brightness should dim well below full",
                brightness in 0.05..0.45,
            )

            val saturation = obj.getValue("raster-saturation").jsonPrimitive.double
            assertTrue(
                "${basemap.name}: saturation $saturation should pull colour out, not add it",
                saturation < 0.0 && saturation >= -1.0,
            )

            val contrast = obj.getValue("raster-contrast").jsonPrimitive.double
            assertTrue("${basemap.name}: contrast $contrast out of spec range", contrast in -1.0..1.0)
        }
    }

    /**
     * The dimming and the night palette are two halves of one decision: `MapPalette.NIGHT`'s
     * contrast is asserted against `NIGHT_TILE_REFERENCE`, which models what the tiles look like
     * *after* this paint block is applied. If someone raises the brightness cap without revisiting
     * that reference, the palette's contrast numbers quietly stop describing the real ground.
     *
     * This cannot verify the modelled tone is right — that is hardware's job — but it can refuse to
     * let the two drift apart silently, by pinning the value the reference was computed for.
     */
    @Test
    fun `the brightness cap is the one MapPalette's night tile reference was modelled for`() {
        val brightness = rasterLayer(Basemap.DEFAULT, night = true)
            .getValue("paint").jsonObject
            .getValue("raster-brightness-max").jsonPrimitive.double
        assertEquals(
            "Changing this means re-modelling MapPalette.NIGHT_TILE_REFERENCE and re-checking " +
                "MapPaletteTest's night contrast figures against the new ground.",
            0.22,
            brightness,
            1e-9,
        )
    }

    @Test
    fun `night mode changes nothing else about the style`() {
        for (basemap in Basemap.entries) {
            val day = json.parseToJsonElement(styleJsonFor(basemap, night = false)).jsonObject
            val night = json.parseToJsonElement(styleJsonFor(basemap, night = true)).jsonObject
            assertEquals("${basemap.name}: sources must be identical", day["sources"], night["sources"])
            assertEquals("${basemap.name}: glyphs must be identical", day["glyphs"], night["glyphs"])
            assertEquals("${basemap.name}: version must be identical", day["version"], night["version"])

            val dayLayer = rasterLayer(basemap, night = false)
            val nightLayer = rasterLayer(basemap, night = true)
            assertEquals("${basemap.name}: layer type must be identical", dayLayer["type"], nightLayer["type"])
            assertEquals("${basemap.name}: layer source must be identical", dayLayer["source"], nightLayer["source"])
        }
    }
}
