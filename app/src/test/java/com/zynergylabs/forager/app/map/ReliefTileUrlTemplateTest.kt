package com.zynergylabs.forager.app.map

import com.zynergylabs.forager.app.domain.relief.ReliefGroundHeightSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Dispatch 2026-11-03-07, D2 (the planner: "the URL template pinned by a test against forager-trail-v4.json's dem
 * source"): MapLibre stores each relief tile under the style's own tile URL, so the store's key, the zoom and the tile
 * size the lookup assumes must be the Trail Map style's. A style that moves the relief fails here, not silently on the
 * phone as a missing line. The file is the one the Worker serves as `/style/trail-v4.json` ([TRAIL_STYLE_URL]).
 */
class ReliefTileUrlTemplateTest {

    private val dem = Json.parseToJsonElement(File("../map-style/forager-trail-v4.json").readText())
        .jsonObject.getValue("sources").jsonObject.getValue("dem").jsonObject

    @Test
    fun `the store reads relief under the Trail Map style's own dem tile URL`() {
        assertEquals(listOf(RELIEF_TILE_URL_TEMPLATE), dem.getValue("tiles").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `the lookup's zoom, tile size and encoding are the dem source's`() {
        assertEquals("raster-dem", dem.getValue("type").jsonPrimitive.content)
        assertEquals("terrarium", dem.getValue("encoding").jsonPrimitive.content)
        assertEquals(ReliefGroundHeightSource.RELIEF_ZOOM, dem.getValue("maxzoom").jsonPrimitive.content.toInt())
        assertEquals(ReliefGroundHeightSource.RELIEF_TILE_SIZE, dem.getValue("tileSize").jsonPrimitive.content.toInt())
    }

    @Test
    fun `the style the phone downloads is the one this file is`() {
        assertEquals("https://forager-pmtiles.brandonlee1-894.workers.dev/style/trail-v4.json", TRAIL_STYLE_URL)
    }
}
