package com.zynergylabs.forager.app.ui.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Night mode's basemap paint, checked at the level a JVM test can actually reach: the style JSON
 * `styleJsonFor` produces. `BasemapStyleTest`'s own doc comment records why the layers themselves
 * cannot be constructed here — every MapLibre `Layer`/`Source` subclass constructor calls a native
 * initialiser — so this asserts the document handed to MapLibre, not the render.
 *
 * That boundary is the point of the check rather than a limitation of it: the failure this guards
 * against is the paint block being absent, malformed, applied in day mode, or missing from a basemap
 * (Satellite was once exempt; dispatch 2026-09-28-708 removed it), all of which are properties of the JSON. Whether the V1 inversion looks right on a device, and
 * whether markers stay legible against the inverted ground (colour build C2), are device questions
 * and no assertion here speaks to either.
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
     * The V1 night transform (owner's choice; `docs/audits/2026-09-27-marker-swatch-board.md` §1):
     * exactly `raster-brightness-min 1`, `raster-brightness-max 0` and `raster-hue-rotate 180`, and
     * nothing else. Replaces the earlier desaturate-and-contrast block, whose `raster-saturation` and
     * `raster-contrast` are asserted absent here on purpose, as is the old "no brightness at all".
     * Street carries it at every zoom; Topographical carries it from map zoom 9.5 up (next test).
     */
    @Test
    fun `night mode on Street carries exactly the three V1 properties`() {
        val paint = rasterLayer(Basemap.OSM_STANDARD, night = true)["paint"]
            ?: error("OSM_STANDARD has no raster paint in night mode")
        val obj = paint.jsonObject

        assertEquals("the night paint is exactly the V1 properties", setOf("raster-brightness-min", "raster-brightness-max", "raster-hue-rotate"), obj.keys)
        assertEquals("brightness-min", 1.0, obj.getValue("raster-brightness-min").jsonPrimitive.double, 0.0)
        assertEquals("brightness-max", 0.0, obj.getValue("raster-brightness-max").jsonPrimitive.double, 0.0)
        assertEquals("hue-rotate", 180.0, obj.getValue("raster-hue-rotate").jsonPrimitive.double, 0.0)
    }

    /**
     * Topographical's night style carries V1 on its topo layer exactly as Street does, plus a `raster-opacity` fade-in,
     * from map zoom 9.5 (`minzoom`); below that a Street layer carries the same V1 (`TopoNightStreetSwitchTest`).
     */
    @Test
    fun `night mode on Topographical carries the three V1 properties and the opacity fade on its topo layer`() {
        val layer = rasterLayer(Basemap.OPEN_TOPO_MAP, night = true)
        val paint = layer["paint"]?.jsonObject ?: error("OPEN_TOPO_MAP has no raster paint in night mode")

        assertEquals(setOf("raster-brightness-min", "raster-brightness-max", "raster-hue-rotate", "raster-opacity"), paint.keys)
        assertEquals(1.0, paint.getValue("raster-brightness-min").jsonPrimitive.double, 0.0)
        assertEquals(0.0, paint.getValue("raster-brightness-max").jsonPrimitive.double, 0.0)
        assertEquals(180.0, paint.getValue("raster-hue-rotate").jsonPrimitive.double, 0.0)
        assertEquals(9.5, layer.getValue("minzoom").jsonPrimitive.double, 0.0)
    }

    /**
     * Replaces "Satellite's night style JSON equals its day style JSON" and "only Satellite opts out of
     * the night paint" (dispatch 2026-09-28-708): Satellite was the one basemap that stayed day, and the
     * per-basemap opt-out went with it. What remains to pin is that no basemap stays day, so a basemap
     * added back cannot silently skip night mode.
     */
    @Test
    fun `every basemap's own layer takes the V1 paint at night`() {
        for (basemap in Basemap.entries) {
            val paint = rasterLayer(basemap, night = true)["paint"]?.jsonObject
                ?: error("${basemap.name} has no raster paint in night mode")
            assertEquals("${basemap.name} brightness-min", 1.0, paint.getValue("raster-brightness-min").jsonPrimitive.double, 0.0)
            assertEquals("${basemap.name} brightness-max", 0.0, paint.getValue("raster-brightness-max").jsonPrimitive.double, 0.0)
            assertEquals("${basemap.name} hue-rotate", 180.0, paint.getValue("raster-hue-rotate").jsonPrimitive.double, 0.0)
        }
    }

    @Test
    fun `night mode changes nothing else about the style`() {
        for (basemap in Basemap.entries) {
            val day = json.parseToJsonElement(styleJsonFor(basemap, night = false)).jsonObject
            val night = json.parseToJsonElement(styleJsonFor(basemap, night = true)).jsonObject
            if (basemap == Basemap.OPEN_TOPO_MAP) {
                // Topo night adds the Street source and nothing else; the topo source itself is the day one.
                val dayMap = day.getValue("sources").jsonObject
                val nightMap = night.getValue("sources").jsonObject
                assertEquals("${basemap.name}: the topo source is unchanged", dayMap.getValue(RASTER_SOURCE_ID), nightMap.getValue(RASTER_SOURCE_ID))
                assertEquals("${basemap.name}: night adds one source", dayMap.size + 1, nightMap.size)
            } else {
                assertEquals("${basemap.name}: sources must be identical", day["sources"], night["sources"])
            }
            assertEquals("${basemap.name}: glyphs must be identical", day["glyphs"], night["glyphs"])
            assertEquals("${basemap.name}: version must be identical", day["version"], night["version"])

            val dayLayer = rasterLayer(basemap, night = false)
            val nightLayer = rasterLayer(basemap, night = true)
            assertEquals("${basemap.name}: layer type must be identical", dayLayer["type"], nightLayer["type"])
            assertEquals("${basemap.name}: layer source must be identical", dayLayer["source"], nightLayer["source"])
        }
    }
}
