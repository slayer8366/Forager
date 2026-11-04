package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.map.OFFLINE_STYLE_ATTRIBUTION
import com.zynergylabs.forager.app.map.OFFLINE_STYLE_URL
import com.zynergylabs.forager.app.map.TRAIL_STYLE_URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The own Street's fallback (RECORD -1513; `OwnTrailFallback.kt`): which failed loads fall back, what the map draws and
 * credits after, and that the holder flips once and logs once. MapLibre cannot run here, so the listener calling the
 * holder, and the map redrawing, are the device check's (S22: a dead style URL, Street and Topographical at night).
 */
class OwnTrailFallbackTest {

    private val street = MapStyleSource.Uri(TRAIL_STYLE_URL)
    private val streetUnderTopo = MapStyleSource.TrailWithTopoOver(TRAIL_STYLE_URL, Basemap.OPEN_TOPO_MAP)
    private val raster = MapStyleSource.Json(styleJsonFor(Basemap.OPEN_TOPO_MAP))

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
        assertTrue(effectiveOwnTrail(switch = true, fellBack = false))
        assertFalse(effectiveOwnTrail(switch = true, fellBack = true))
        assertFalse(effectiveOwnTrail(switch = false, fellBack = false))
        assertFalse(effectiveOwnTrail(switch = false, fellBack = true))
    }

    @Test
    // Dispatch 2026-10-22-01: "Street" here is Forager Trail Map, its own Basemap entry (FORAGER_TRAIL); the rules are unchanged.
    fun `after the fallback Street is OpenStreetMap's raster Street, credited and capped as before the own Street`() {
        val ownTrail = effectiveOwnTrail(switch = true, fellBack = true)

        val day = mapStyleSourceFor(Basemap.FORAGER_TRAIL, night = false, useOfflineTiles = false, ownTrail = ownTrail)
        assertTrue("Street by day is a raster JSON style: $day", day is MapStyleSource.Json)
        // The raster that stands in for Trail Map (the Street map's while it is in the app, ui/map/street/StreetMapFallbackTest;
        // Topographical with none, TrailMapFallbackWithoutStandInTest; dispatch 2026-11-03-05 D7, D8).
        val standIn = Basemap.FORAGER_TRAIL.rasterDrawn()
        assertTrue("on the stand-in's tiles", (day as MapStyleSource.Json).json.contains(standIn.tileUrlTemplate))
        val topoNight = mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = true, useOfflineTiles = false, ownTrail = ownTrail)
        assertTrue("topo at night is the raster night style: $topoNight", topoNight is MapStyleSource.Json)
        nightLowZoomBasemap(Basemap.OPEN_TOPO_MAP)?.let { below ->
            assertTrue("with the night under-topo raster below", (topoNight as MapStyleSource.Json).json.contains(below.tileUrlTemplate))
        }

        assertEquals(listOf(standIn.attribution), mapCreditsFor(Basemap.FORAGER_TRAIL, useOfflineTiles = false, ownTrail = ownTrail))
        assertEquals(
            listOfNotNull(Basemap.OPEN_TOPO_MAP.attribution, nightLowZoomBasemap(Basemap.OPEN_TOPO_MAP)?.attribution),
            mapCreditsFor(Basemap.OPEN_TOPO_MAP, useOfflineTiles = false, nightMode = true, ownTrail = ownTrail),
        )
        assertEquals(standIn.maxZoom, operatingMaxZoom(Basemap.FORAGER_TRAIL, ownTrail))
        assertEquals(standIn.maxZoom.toDouble(), navigationMaxZoom(Basemap.FORAGER_TRAIL, navigating = false, ownTrail = ownTrail), 0.0)
        // Before the fallback, the own Street's: its credit and the owner's 18 (-1289).
        assertEquals(listOf(OFFLINE_STYLE_ATTRIBUTION), mapCreditsFor(Basemap.FORAGER_TRAIL, useOfflineTiles = false, ownTrail = true))
        assertEquals(18.0, navigationMaxZoom(Basemap.FORAGER_TRAIL, navigating = false, ownTrail = true), 0.0)
    }

    @Test
    fun `while navigating a basemap cap above the start zoom is not lowered`() {
        // Dispatch 2026-09-28-440, Amendment 1. The Street map's 19 is held in ui/map/street/StreetMapTest (dispatch 2026-11-03-05 D7).
        assertEquals(NAVIGATION_VIEW_ZOOM, navigationMaxZoom(Basemap.FORAGER_TRAIL, navigating = true, ownTrail = true), 0.0)
    }

    @Test
    fun `the fallback changes what the style effect asks for, so it reloads the style`() {
        val before = requestedMapStyle(Basemap.FORAGER_TRAIL, useOfflineTiles = false, nightMode = false, nightModeLoaded = true, ownTrail = true)!!
        val after = requestedMapStyle(Basemap.FORAGER_TRAIL, useOfflineTiles = false, nightMode = false, nightModeLoaded = true, ownTrail = false)!!
        assertTrue(needsStyleReload(applied = before, requested = after))
    }

    @Test
    fun `a failed own Street load falls back for the process and logs one line`() {
        val lines = mutableListOf<String>()
        val fallback = OwnTrailFallback(log = { lines += it })
        assertTrue("before any failure", fallback.ownTrail(switch = true))

        assertTrue("falls back", fallback.onStyleLoadFailed(MapStyleRequest(street, useOfflineTiles = false), "Failed to load style: 404"))
        assertTrue(fallback.fellBack)
        assertFalse("the map now draws OpenStreetMap's Street", fallback.ownTrail(switch = true))
        assertEquals(
            listOf("Trail Map style failed to load (Failed to load style: 404); drawing ${trailMapFallbackDrawn()} until the connection returns or the app restarts."),
            lines,
        )

        assertFalse("a second failure is not a second fallback", fallback.onStyleLoadFailed(MapStyleRequest(streetUnderTopo, useOfflineTiles = false), "again"))
        assertEquals("still one line", 1, lines.size)
    }

    @Test
    fun `a failure that is not the own Street online changes nothing and logs nothing`() {
        val lines = mutableListOf<String>()
        val fallback = OwnTrailFallback(log = { lines += it })

        assertFalse(fallback.onStyleLoadFailed(MapStyleRequest(raster, useOfflineTiles = false), "raster"))
        assertFalse(fallback.onStyleLoadFailed(MapStyleRequest(street, useOfflineTiles = true), "offline"))
        assertFalse(fallback.onStyleLoadFailed(null, "before any request"))
        assertFalse(fallback.fellBack)
        assertTrue(fallback.ownTrail(switch = true))
        assertEquals(emptyList<String>(), lines)
    }

    @Test
    fun `own Street tile errors are logged a few times and never fall back`() {
        val lines = mutableListOf<String>()
        val fallback = OwnTrailFallback(log = { lines += it })

        repeat(5) { fallback.onTileError(MapStyleRequest(street, useOfflineTiles = false), "protomaps", 14, it, 7) }
        fallback.onTileError(MapStyleRequest(raster, useOfflineTiles = false), "basemap-raster", 14, 1, 1)

        assertEquals(
            listOf(
                "Map tile failed to load while Trail Map is showing: source=protomaps z=14 x=0 y=7 (no fallback for tiles)",
                "Map tile failed to load while Trail Map is showing: source=protomaps z=14 x=1 y=7 (no fallback for tiles)",
                "Map tile failed to load while Trail Map is showing: source=protomaps z=14 x=2 y=7 (no fallback for tiles); further tile errors are not logged in this process",
            ),
            lines,
        )
        assertFalse(fallback.fellBack)
        assertTrue(fallback.ownTrail(switch = true))
    }
}
