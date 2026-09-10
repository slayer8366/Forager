package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.ui.theme.MapPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three pure decisions Stage 2e-ii adds to [SightingsMap] — which style document to load, what
 * credit to show over it, and when a reload is due — asserted away from the native map, the same
 * boundary `BasemapStyleTest` draws for [styleJsonFor]. Nothing here shows a tile came from the
 * downloaded store; only a device can (see the pre-build report's §5.2).
 *
 * Expected values are literals, not derived from the code under test: the offline URL below is the
 * worker path the regions were downloaded against, typed here independently, so a change to either
 * side is a failing test rather than a silent agreement.
 */
class OfflineStyleSwapTest {

    private val palette = MapPalette.DAY

    @Test
    fun `offline on loads the regions' own style by URI, the exact string the download used`() {
        val source = mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = false, useOfflineTiles = true)

        assertEquals(
            MapStyleSource.Uri("https://forager-pmtiles.brandonlee1-894.workers.dev/style/offline.json"),
            source,
        )
    }

    @Test
    fun `offline on ignores the basemap and night mode, since the store holds one style`() {
        val expected = mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = false, useOfflineTiles = true)

        Basemap.entries.forEach { basemap ->
            listOf(false, true).forEach { night ->
                assertEquals("basemap=$basemap night=$night", expected, mapStyleSourceFor(basemap, night, useOfflineTiles = true))
            }
        }
    }

    @Test
    fun `offline off loads the basemap's own raster style as JSON, night applied`() {
        Basemap.entries.forEach { basemap ->
            val day = mapStyleSourceFor(basemap, night = false, useOfflineTiles = false)
            val night = mapStyleSourceFor(basemap, night = true, useOfflineTiles = false)

            assertTrue("$basemap day must be a JSON style", day is MapStyleSource.Json)
            assertTrue("$basemap must name its own tile template", (day as MapStyleSource.Json).json.contains(basemap.tileUrlTemplate))
            assertTrue("$basemap night must still be the basemap's JSON style, with night paint", (night as MapStyleSource.Json).json.contains("raster-saturation"))
            assertFalse("day must not carry the night paint", day.json.contains("raster-saturation"))
        }
    }

    @Test
    fun `the credit over the offline style is Protomaps and OpenStreetMap, never the basemap's`() {
        Basemap.entries.forEach { basemap ->
            assertEquals("Protomaps © OpenStreetMap", mapAttributionFor(basemap, useOfflineTiles = true))
        }
    }

    @Test
    fun `the credit over a basemap is that basemap's own`() {
        assertEquals("© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)", mapAttributionFor(Basemap.OPEN_TOPO_MAP, useOfflineTiles = false))
        assertEquals("© OpenStreetMap contributors", mapAttributionFor(Basemap.OSM_STANDARD, useOfflineTiles = false))
        assertEquals("USGS The National Map, orthoimagery — public domain", mapAttributionFor(Basemap.USGS_IMAGERY_ONLY, useOfflineTiles = false))
    }

    @Test
    fun `flipping only the offline flag is a reload -- the gap the old basemap-and-palette guard left open`() {
        val online = AppliedMapStyle(Basemap.OPEN_TOPO_MAP, palette, useOfflineTiles = false)
        val offline = online.copy(useOfflineTiles = true)

        assertTrue(needsStyleReload(applied = online, requested = offline))
        assertTrue(needsStyleReload(applied = offline, requested = online))
    }

    @Test
    fun `nothing applied yet is a reload, and an identical request is not`() {
        val style = AppliedMapStyle(Basemap.OSM_STANDARD, palette, useOfflineTiles = false)

        assertTrue(needsStyleReload(applied = null, requested = style))
        assertFalse(needsStyleReload(applied = style, requested = style))
    }

    @Test
    fun `a basemap change is still a reload, with the offline flag unchanged`() {
        val topo = AppliedMapStyle(Basemap.OPEN_TOPO_MAP, palette, useOfflineTiles = false)
        val osm = topo.copy(basemap = Basemap.OSM_STANDARD)

        assertTrue(needsStyleReload(applied = topo, requested = osm))
    }
}
