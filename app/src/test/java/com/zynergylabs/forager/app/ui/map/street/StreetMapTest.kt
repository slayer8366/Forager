package com.zynergylabs.forager.app.ui.map.street

import com.zynergylabs.forager.app.ui.map.ADDED_MAP_TYPES
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.creditNeverFades
import com.zynergylabs.forager.app.ui.map.navigationMaxZoom
import com.zynergylabs.forager.app.ui.map.nightUnderTopo
import com.zynergylabs.forager.app.ui.map.styleJsonFor
import com.zynergylabs.forager.app.ui.map.trailMapStandIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * The Street map, OpenStreetMap's raster, in its own place (dispatch 2026-11-03-05 A2, D7): its chip, its key, its tiles
 * and credit word for word, and its roles. Deleted with `ui/map/street/` (CI's street-removable job).
 */
class StreetMapTest {

    @Test
    fun `its chip reads Street and it is stored as street`() {
        // A2 (RECORD -1999): "The OSM button should be renamed to "Street"", and "just use street for OSM".
        assertEquals("Street", StreetMap.mapMode.label)
        assertEquals("street", StreetMap.mapMode.storageKey)
        assertSame(StreetMap.mapMode, MapMode.forStoredKey("street"))
    }

    @Test
    fun `the chips read Trail, Topo and Street, in that order`() {
        assertEquals(listOf("Trail", "Topo", "Street"), MapMode.entries.map { it.label })
        assertEquals(listOf(MapMode.TRAIL, MapMode.TOPOGRAPHIC, StreetMap.mapMode), MapMode.entries)
    }

    @Test
    fun `its tile address, credit and zoom limit stay word for word`() {
        // A2: "Its tile address (tile.openstreetmap.org), its licence credit and every OSM data credit stay word for word."
        assertEquals("https://tile.openstreetmap.org/{z}/{x}/{y}.png", StreetMap.BASEMAP.tileUrlTemplate)
        assertEquals("© OpenStreetMap contributors", StreetMap.BASEMAP.attribution)
        assertEquals("© OpenStreetMap contributors", StreetMap.BASEMAP.fullAttribution)
        assertEquals(19, StreetMap.BASEMAP.maxZoom)
        assertTrue(StreetMap.BASEMAP.isRaster)
    }

    @Test
    fun `it is registered once and keeps today's roles`() {
        assertEquals(listOf(StreetMap), ADDED_MAP_TYPES)
        assertTrue(StreetMap.BASEMAP in Basemap.entries)
        // RECORD -2003, "Nothing changes today": while the Street map is in the app it stays the fallback.
        assertSame(StreetMap.BASEMAP, trailMapStandIn)
        assertSame(StreetMap, nightUnderTopo)
        assertEquals("basemap-street", StreetMap.nightUnderTopoId)
        assertTrue(StreetMap.BASEMAP.creditNeverFades())
    }

    @Test
    fun `while navigating its higher cap of 19 is not lowered`() {
        // Moved from NavigationViewTest (dispatch 2026-11-03-05 D7).
        assertEquals("a higher cap is not lowered", 19.0, navigationMaxZoom(StreetMap.BASEMAP, navigating = true), 0.0)
        assertEquals(19.0, navigationMaxZoom(StreetMap.BASEMAP, navigating = false), 0.0)
    }

    @Test
    fun `at night it carries exactly the three V1 properties, at every zoom`() {
        // Moved from BasemapNightStyleTest (dispatch 2026-11-03-05 D7).
        val layers = Json.parseToJsonElement(styleJsonFor(StreetMap.BASEMAP, night = true)).jsonObject.getValue("layers").jsonArray
        val paint = layers.single().jsonObject.getValue("paint").jsonObject
        assertEquals(setOf("raster-brightness-min", "raster-brightness-max", "raster-hue-rotate"), paint.keys)
        assertEquals(1.0, paint.getValue("raster-brightness-min").jsonPrimitive.double, 0.0)
        assertEquals(0.0, paint.getValue("raster-brightness-max").jsonPrimitive.double, 0.0)
        assertEquals(180.0, paint.getValue("raster-hue-rotate").jsonPrimitive.double, 0.0)
    }
}
