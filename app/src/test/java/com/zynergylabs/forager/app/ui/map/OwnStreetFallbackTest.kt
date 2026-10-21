package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.map.OFFLINE_STYLE_ATTRIBUTION
import com.zynergylabs.forager.app.map.OFFLINE_STYLE_URL
import com.zynergylabs.forager.app.map.STREET_STYLE_URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The own Street's fallback (RECORD -1513; `OwnStreetFallback.kt`): which failed loads fall back, what the map draws and
 * credits after, and that the holder flips once and logs once. MapLibre cannot run here, so the listener calling the
 * holder, and the map redrawing, are the device check's (S22: a dead style URL, Street and Topographical at night).
 */
class OwnStreetFallbackTest {

    private val street = MapStyleSource.Uri(STREET_STYLE_URL)
    private val streetUnderTopo = MapStyleSource.StreetWithTopoOver(STREET_STYLE_URL, Basemap.OPEN_TOPO_MAP)
    private val raster = MapStyleSource.Json(styleJsonFor(Basemap.OSM_STANDARD))

    @Test
    fun `only the own Street online falls back, alone or under topo at night`() {
        assertTrue("Street", fallsBack(street, useOfflineTiles = false))
        assertTrue("Street under topo at night", fallsBack(streetUnderTopo, useOfflineTiles = false))
        assertFalse("a raster basemap", fallsBack(raster, useOfflineTiles = false))
        assertFalse("the older offline style", fallsBack(MapStyleSource.Uri(OFFLINE_STYLE_URL), useOfflineTiles = true))
        assertFalse("offline regions in the Street style", fallsBack(street, useOfflineTiles = true))
    }

    @Test
    fun `the map uses the own Street only while the switch is on and it has not failed`() {
        assertTrue(effectiveOwnStreet(switch = true, fellBack = false))
        assertFalse(effectiveOwnStreet(switch = true, fellBack = true))
        assertFalse(effectiveOwnStreet(switch = false, fellBack = false))
        assertFalse(effectiveOwnStreet(switch = false, fellBack = true))
    }

    @Test
    fun `after the fallback Street is OpenStreetMap's raster Street, credited and capped as before the own Street`() {
        val ownStreet = effectiveOwnStreet(switch = true, fellBack = true)

        val day = mapStyleSourceFor(Basemap.OSM_STANDARD, night = false, useOfflineTiles = false, ownStreet = ownStreet)
        assertTrue("Street by day is a raster JSON style: $day", day is MapStyleSource.Json)
        assertTrue("on OpenStreetMap's tiles", (day as MapStyleSource.Json).json.contains(Basemap.OSM_STANDARD.tileUrlTemplate))
        val topoNight = mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = true, useOfflineTiles = false, ownStreet = ownStreet)
        assertTrue("topo at night is the raster night style: $topoNight", topoNight is MapStyleSource.Json)
        assertTrue("with OpenStreetMap below", (topoNight as MapStyleSource.Json).json.contains(Basemap.OSM_STANDARD.tileUrlTemplate))

        assertEquals(listOf(Basemap.OSM_STANDARD.attribution), mapCreditsFor(Basemap.OSM_STANDARD, useOfflineTiles = false, ownStreet = ownStreet))
        assertEquals(
            listOf(Basemap.OPEN_TOPO_MAP.attribution, Basemap.OSM_STANDARD.attribution),
            mapCreditsFor(Basemap.OPEN_TOPO_MAP, useOfflineTiles = false, nightMode = true, ownStreet = ownStreet),
        )
        assertEquals(Basemap.OSM_STANDARD.maxZoom, operatingMaxZoom(Basemap.OSM_STANDARD, ownStreet))
        assertEquals(Basemap.OSM_STANDARD.maxZoom.toDouble(), navigationMaxZoom(Basemap.OSM_STANDARD, navigating = false, ownStreet = ownStreet), 0.0)
        // Before the fallback, the own Street's: its credit and the owner's 18 (-1289).
        assertEquals(listOf(OFFLINE_STYLE_ATTRIBUTION), mapCreditsFor(Basemap.OSM_STANDARD, useOfflineTiles = false, ownStreet = true))
        assertEquals(18.0, navigationMaxZoom(Basemap.OSM_STANDARD, navigating = false, ownStreet = true), 0.0)
    }

    @Test
    fun `while navigating a basemap cap above the start zoom is not lowered`() {
        // Dispatch 2026-09-28-440, Amendment 1, driven with the switch off, where Street's cap is OpenStreetMap's 19.
        assertEquals(19.0, navigationMaxZoom(Basemap.OSM_STANDARD, navigating = true, ownStreet = false), 0.0)
        assertEquals(NAVIGATION_VIEW_ZOOM, navigationMaxZoom(Basemap.OSM_STANDARD, navigating = true, ownStreet = true), 0.0)
    }

    @Test
    fun `the fallback changes what the style effect asks for, so it reloads the style`() {
        val before = requestedMapStyle(Basemap.OSM_STANDARD, useOfflineTiles = false, nightMode = false, nightModeLoaded = true, ownStreet = true)!!
        val after = requestedMapStyle(Basemap.OSM_STANDARD, useOfflineTiles = false, nightMode = false, nightModeLoaded = true, ownStreet = false)!!
        assertTrue(needsStyleReload(applied = before, requested = after))
    }

    @Test
    fun `a failed own Street load falls back for the process and logs one line`() {
        val lines = mutableListOf<String>()
        val fallback = OwnStreetFallback(log = { lines += it })
        assertTrue("before any failure", fallback.ownStreet(switch = true))

        assertTrue("falls back", fallback.onStyleLoadFailed(MapStyleRequest(street, useOfflineTiles = false), "Failed to load style: 404"))
        assertTrue(fallback.fellBack)
        assertFalse("the map now draws OpenStreetMap's Street", fallback.ownStreet(switch = true))
        assertEquals(
            listOf("Own Street style failed to load (Failed to load style: 404); drawing OpenStreetMap's Street until the app restarts."),
            lines,
        )

        assertFalse("a second failure is not a second fallback", fallback.onStyleLoadFailed(MapStyleRequest(streetUnderTopo, useOfflineTiles = false), "again"))
        assertEquals("still one line", 1, lines.size)
    }

    @Test
    fun `a failure that is not the own Street online changes nothing and logs nothing`() {
        val lines = mutableListOf<String>()
        val fallback = OwnStreetFallback(log = { lines += it })

        assertFalse(fallback.onStyleLoadFailed(MapStyleRequest(raster, useOfflineTiles = false), "raster"))
        assertFalse(fallback.onStyleLoadFailed(MapStyleRequest(street, useOfflineTiles = true), "offline"))
        assertFalse(fallback.onStyleLoadFailed(null, "before any request"))
        assertFalse(fallback.fellBack)
        assertTrue(fallback.ownStreet(switch = true))
        assertEquals(emptyList<String>(), lines)
    }

    @Test
    fun `own Street tile errors are logged a few times and never fall back`() {
        val lines = mutableListOf<String>()
        val fallback = OwnStreetFallback(log = { lines += it })

        repeat(5) { fallback.onTileError(MapStyleRequest(street, useOfflineTiles = false), "protomaps", 14, it, 7) }
        fallback.onTileError(MapStyleRequest(raster, useOfflineTiles = false), "basemap-raster", 14, 1, 1)

        assertEquals(
            listOf(
                "Own Street tile failed to load: source=protomaps z=14 x=0 y=7 (no fallback for tiles)",
                "Own Street tile failed to load: source=protomaps z=14 x=1 y=7 (no fallback for tiles)",
                "Own Street tile failed to load: source=protomaps z=14 x=2 y=7 (no fallback for tiles); further tile errors are not logged in this process",
            ),
            lines,
        )
        assertFalse(fallback.fellBack)
        assertTrue(fallback.ownStreet(switch = true))
    }
}
