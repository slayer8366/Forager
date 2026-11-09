package com.zynergylabs.forager.app.ui.map.street

import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapStyleSource
import com.zynergylabs.forager.app.ui.map.drawsFromDownloads
import com.zynergylabs.forager.app.ui.map.mapAttributionFor
import com.zynergylabs.forager.app.ui.map.mapCreditsFor
import com.zynergylabs.forager.app.ui.map.mapStyleSourceFor
import com.zynergylabs.forager.app.ui.map.navigationMaxZoom
import com.zynergylabs.forager.app.ui.map.nightLowZoomBasemap
import com.zynergylabs.forager.app.ui.map.operatingMaxZoom
import com.zynergylabs.forager.app.ui.map.rasterDrawn
import com.zynergylabs.forager.app.ui.map.styleJsonFor
import com.zynergylabs.forager.app.ui.map.trailMapFallbackDrawn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Street map's roles while it is in the app (dispatch 2026-11-03-05 D8, RECORD -2003: "Nothing changes today"): the
 * raster that stands in for a failed Trail Map (RECORD -1513), and the raster a night Topographical draws below its fade
 * once Trail Map has fallen back (RECORD -324). Moved here from OwnTrailFallbackTest, OwnTrailStyleSourceTest,
 * BasemapStyleTest and OfflineStyleSwapTest, which now ask the registry (D7); without the Street map these roles fall to
 * the owner's "Downloads, then Topo" (`TrailMapFallbackWithoutStandInTest`).
 */
class StreetMapFallbackTest {

    @Test
    fun `Trail Map's raster stand-in is the Street map's style exactly, day and night`() {
        assertEquals(StreetMap.BASEMAP, Basemap.FORAGER_TRAIL.rasterDrawn())
        listOf(false, true).forEach { night ->
            assertEquals("night=$night", styleJsonFor(StreetMap.BASEMAP, night), styleJsonFor(Basemap.FORAGER_TRAIL, night))
        }
    }

    @Test
    fun `after the fallback Trail Map is the Street map's raster, credited and capped as it, and topo at night has it below`() {
        val day = mapStyleSourceFor(Basemap.FORAGER_TRAIL, night = false, useOfflineTiles = false, ownTrail = false) as MapStyleSource.Json
        assertTrue("on OpenStreetMap's tiles", day.json.contains("https://tile.openstreetmap.org/{z}/{x}/{y}.png"))
        val topoNight = mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = true, useOfflineTiles = false, ownTrail = false) as MapStyleSource.Json
        assertTrue("with OpenStreetMap below", topoNight.json.contains("https://tile.openstreetmap.org/{z}/{x}/{y}.png"))
        assertTrue("under its own id", topoNight.json.contains("\"id\": \"basemap-street\""))
        assertEquals(StreetMap.BASEMAP, nightLowZoomBasemap(Basemap.OPEN_TOPO_MAP))

        assertEquals(listOf("© OpenStreetMap contributors"), mapCreditsFor(Basemap.FORAGER_TRAIL, useOfflineTiles = false, ownTrail = false))
        assertEquals(
            listOf(Basemap.OPEN_TOPO_MAP.attribution, "© OpenStreetMap contributors"),
            mapCreditsFor(Basemap.OPEN_TOPO_MAP, useOfflineTiles = false, nightMode = true, ownTrail = false),
        )
        assertEquals(19, operatingMaxZoom(Basemap.FORAGER_TRAIL, ownTrail = false))
        assertEquals(19.0, navigationMaxZoom(Basemap.FORAGER_TRAIL, navigating = false, ownTrail = false), 0.0)
        assertEquals("the fallback's log line", "OpenStreetMap's raster", trailMapFallbackDrawn())
    }

    @Test
    fun `the Street map is its own raster, credited as itself, whether or not Trail Map has fallen back`() {
        for (night in listOf(false, true)) for (ownTrail in listOf(false, true)) {
            assertEquals(
                MapStyleSource.Json(styleJsonFor(StreetMap.BASEMAP, night)),
                mapStyleSourceFor(StreetMap.BASEMAP, night, useOfflineTiles = false, ownTrail = ownTrail),
            )
            assertEquals(listOf("© OpenStreetMap contributors"), mapCreditsFor(StreetMap.BASEMAP, useOfflineTiles = false, nightMode = night, ownTrail = ownTrail))
        }
        assertEquals("© OpenStreetMap contributors", mapAttributionFor(StreetMap.BASEMAP, useOfflineTiles = false))
        assertEquals(19, operatingMaxZoom(StreetMap.BASEMAP, ownTrail = true))
    }

    @Test
    fun `with the Street map standing in, a failed Trail Map with a signal is never drawn from the downloads`() {
        // RECORD -2003, "Nothing changes today": the downloads-first half of "Downloads, then Topo" is only without it.
        assertFalse(drawsFromDownloads(false, Basemap.FORAGER_TRAIL, noSignal = false, hasDownloadedArea = true, trailMapDraws = false))
    }
}
