package com.zynergylabs.forager.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a failed Trail Map style draws when no added map type stands in for it: the owner's "Downloads, then Topo"
 * (dispatch 2026-11-03-05 D8, RECORD -2003), the case the Street map's deletion leaves (D7). The functions take the
 * stand-in as a parameter, so this runs with the Street map in the app too; CI's street-removable job runs it with the
 * Street map deleted, where the registry's own answer is the same `null`.
 */
class TrailMapFallbackWithoutStandInTest {

    @Test
    fun `with no stand-in, a failed Trail Map draws from the downloaded areas when there are any, signal or not`() {
        assertTrue("signal, a downloaded area", drawsFromDownloads(false, Basemap.FORAGER_TRAIL, noSignal = false, hasDownloadedArea = true, trailMapDraws = false, standIn = null))
        assertTrue("no signal, a downloaded area", drawsFromDownloads(false, Basemap.FORAGER_TRAIL, noSignal = true, hasDownloadedArea = true, trailMapDraws = false, standIn = null))
        assertFalse("no downloaded area: not from the downloads", drawsFromDownloads(false, Basemap.FORAGER_TRAIL, noSignal = false, hasDownloadedArea = false, trailMapDraws = false, standIn = null))
        assertFalse("Trail Map drawing: nothing changes", drawsFromDownloads(false, Basemap.FORAGER_TRAIL, noSignal = false, hasDownloadedArea = true, trailMapDraws = true, standIn = null))
        assertFalse("Topographical chosen: its own raster", drawsFromDownloads(false, Basemap.OPEN_TOPO_MAP, noSignal = false, hasDownloadedArea = true, trailMapDraws = false, standIn = null))
    }

    @Test
    fun `with no stand-in and no downloaded area, a failed Trail Map is drawn as Topographical, credited and capped as it`() {
        assertEquals(Basemap.OPEN_TOPO_MAP, Basemap.FORAGER_TRAIL.rasterDrawn(standIn = null))
        val json = styleJsonFor(Basemap.FORAGER_TRAIL, night = false, standIn = null, underTopo = null)
        assertTrue("OpenTopoMap's tiles: $json", json.contains(Basemap.OPEN_TOPO_MAP.tileUrlTemplate))
        assertFalse("never Trail Map's vector tiles in a raster source", json.contains(Basemap.FORAGER_TRAIL.tileUrlTemplate))
        assertEquals("the log line says so", "the downloaded areas, or Topographical with none", trailMapFallbackDrawn(standIn = null))
    }

    @Test
    fun `with nothing registered under topo, a night Topographical draws topo alone once Trail Map has fallen back`() {
        assertNull(nightLowZoomBasemap(Basemap.OPEN_TOPO_MAP, underTopo = null))
        val json = styleJsonFor(Basemap.OPEN_TOPO_MAP, night = true, standIn = null, underTopo = null)
        assertEquals("one source", 1, Regex("\"type\": \"raster\",\\s*\"tiles\"").findAll(json).count())
        assertTrue(json.contains(Basemap.OPEN_TOPO_MAP.tileUrlTemplate))
    }

    @Test
    fun `with no stand-in, the chosen Trail chip is grey only when Topographical is shown in its place`() {
        // The owner: "grey = chosen, but something else is shown." From the downloads it is Trail Map, what was chosen.
        assertEquals("a downloaded area: Trail Map from it, not grey", false, chosenMapModeStandsAside(MapMode.TRAIL, noSignal = false, hasDownloadedArea = true, trailMapDraws = false, standIn = null))
        assertEquals("none: Topographical instead, grey", true, chosenMapModeStandsAside(MapMode.TRAIL, noSignal = false, hasDownloadedArea = false, trailMapDraws = false, standIn = null))
    }

    @Test
    fun `a stand-in, while there is one, keeps today's fallback, never from the downloads with a signal`() {
        // RECORD -2003, "Nothing changes today". Topographical stands in here only as some raster other than none.
        val standIn = Basemap.OPEN_TOPO_MAP
        assertFalse(trailMapFailedWithNoStandIn(Basemap.FORAGER_TRAIL, hasDownloadedArea = true, trailMapDraws = false, standIn = standIn))
        assertFalse(drawsFromDownloads(false, Basemap.FORAGER_TRAIL, noSignal = false, hasDownloadedArea = true, trailMapDraws = false, standIn = standIn))
        assertTrue("no signal still draws from the downloads, as before", drawsFromDownloads(false, Basemap.FORAGER_TRAIL, noSignal = true, hasDownloadedArea = true, trailMapDraws = false, standIn = standIn))
    }
}
