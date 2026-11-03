package com.zynergylabs.forager.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D26 (dispatch 2026-10-30-08, RECORD -1969): the credit strip never fades while OpenStreetMap's raster tiles are drawn,
 * "including the -1513 fallback and topo at night after it" (the rule follows the tiles). [drawsStreetRaster] is that
 * condition; it is checked here against what the map is actually asked to draw ([mapStyleSourceFor]): a raster style whose JSON
 * names OpenStreetMap's tile server.
 */
class DrawsStreetRasterTest {

    private val streetTiles = Basemap.STREET.tileUrlTemplate

    private fun styleDrawsOsm(basemap: Basemap, offline: Boolean, night: Boolean, ownTrail: Boolean): Boolean =
        when (val source = mapStyleSourceFor(basemap, night, offline, ownTrail = ownTrail)) {
            is MapStyleSource.Json -> source.json.contains(streetTiles)
            else -> false
        }

    @Test
    fun `it says OpenStreetMap is drawn exactly when the style asked for draws OpenStreetMap's tiles, in every case`() {
        var drawn = 0
        var cases = 0
        for (basemap in Basemap.entries) for (offline in listOf(false, true)) for (night in listOf(false, true)) for (ownTrail in listOf(true, false)) {
            cases++
            val expected = styleDrawsOsm(basemap, offline, night, ownTrail)
            if (expected) drawn++
            assertEquals("$basemap offline=$offline night=$night ownTrail=$ownTrail", expected, drawsStreetRaster(basemap, offline, night, ownTrail))
        }
        assertEquals("every combination checked: 3 maps x offline x night x fallback", 24, cases)
        // Positive control on the cross-check itself: OSM by day and night, the Trail fallback by day and night, topo at night after
        // the fallback. A cross-check that found none would pass a predicate that is always false.
        assertEquals("cases whose style draws OpenStreetMap's tiles", 7, drawn)
    }

    @Test
    fun `the OSM map, the Trail Map fallback and topo at night after it draw OpenStreetMap's tiles`() {
        assertTrue("OSM by day", drawsStreetRaster(Basemap.STREET, useOfflineTiles = false, night = false, ownTrail = true))
        assertTrue("OSM at night", drawsStreetRaster(Basemap.STREET, useOfflineTiles = false, night = true, ownTrail = true))
        assertTrue("Trail Map fallen back (-1513)", drawsStreetRaster(Basemap.FORAGER_TRAIL, useOfflineTiles = false, night = false, ownTrail = false))
        assertTrue("topo at night after the fallback", drawsStreetRaster(Basemap.OPEN_TOPO_MAP, useOfflineTiles = false, night = true, ownTrail = false))
    }

    @Test
    fun `Trail Map, topo by day, topo at night over Trail Map and every offline map do not`() {
        assertFalse("Trail Map", drawsStreetRaster(Basemap.FORAGER_TRAIL, useOfflineTiles = false, night = false, ownTrail = true))
        assertFalse("topo by day", drawsStreetRaster(Basemap.OPEN_TOPO_MAP, useOfflineTiles = false, night = false, ownTrail = true))
        assertFalse("topo by day after the fallback", drawsStreetRaster(Basemap.OPEN_TOPO_MAP, useOfflineTiles = false, night = false, ownTrail = false))
        assertFalse("topo at night over Trail Map", drawsStreetRaster(Basemap.OPEN_TOPO_MAP, useOfflineTiles = false, night = true, ownTrail = true))
        for (basemap in Basemap.entries) {
            assertFalse("$basemap offline", drawsStreetRaster(basemap, useOfflineTiles = true, night = false, ownTrail = false))
        }
    }
}
