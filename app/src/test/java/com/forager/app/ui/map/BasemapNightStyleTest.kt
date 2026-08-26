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
 * Night mode's basemap paint, checked at the level a JVM test can actually reach: the style JSON
 * `styleJsonFor` produces. `BasemapStyleTest`'s own doc comment records why the layers themselves
 * cannot be constructed here — every MapLibre `Layer`/`Source` subclass constructor calls a native
 * initialiser — so this asserts the document handed to MapLibre, not the render.
 *
 * That boundary is the point of the check rather than a limitation of it: the failure this guards
 * against is the paint block being absent, malformed, or applied in day mode, all of which are
 * properties of the JSON. Whether the desaturation/contrast tuning is *comfortable*, and whether
 * markers stay legible against the now-full-brightness ground (see `BasemapStyles.kt`'s
 * `NIGHT_RASTER_PAINT` doc comment, "Dimming removed"), are device-gate questions and no assertion
 * here speaks to either.
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

    /**
     * No brightness assertion here any more — see `BasemapStyles.kt`'s `NIGHT_RASTER_PAINT` doc
     * comment, "Dimming removed": `raster-brightness-max` is not a property this style JSON sets
     * at all now, night or day, so there is nothing to assert a range on.
     */
    @Test
    fun `night mode desaturates and re-sharpens every basemap, without dimming it`() {
        for (basemap in Basemap.entries) {
            val paint = rasterLayer(basemap, night = true)["paint"]
                ?: error("${basemap.name} has no raster paint in night mode")
            val obj = paint.jsonObject

            assertTrue(
                "${basemap.name}: raster-brightness-max should not be set at all -- dimming was removed",
                "raster-brightness-max" !in obj,
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
