package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.map.OFFLINE_STYLE_ATTRIBUTION
import com.zynergylabs.forager.app.map.OFFLINE_STYLE_URL
import com.zynergylabs.forager.app.map.TRAIL_STYLE_URL
import com.zynergylabs.forager.app.map.TRAIL_STYLE_V1_URL
import com.zynergylabs.forager.app.map.TRAIL_STYLE_V2_URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which style the map loads, its credit and its zoom limit, for Forager Trail Map drawing and fallen back (dispatch
 * 2026-10-18-05 Part 2; since dispatch 2026-10-22-01 Trail Map is its own map type, [Basemap.FORAGER_TRAIL], and
 * OpenStreetMap is always its raster). These are the values `SightingsMap`'s style effect hands to MapLibre:
 * [mapStyleSourceFor], [operatingMaxZoom] (also through [navigationMaxZoom]) and [mapCreditsFor]. `ownTrail` is now
 * whether Trail Map draws in this process (`OwnTrailFallback`, -1513): `true` by default, `false` once it fell back.
 * In the test names, "the switch off" reads as "fallen back" and "own Street" as "Trail Map".
 */
class OwnTrailStyleSourceTest {

    @Test
    fun `with the switch off every basemap loads its raster style as before, day and night`() {
        for (basemap in Basemap.entries) for (night in listOf(false, true)) {
            assertEquals(
                MapStyleSource.Json(styleJsonFor(basemap, night)),
                mapStyleSourceFor(basemap, night, useOfflineTiles = false, ownTrail = false),
            )
        }
    }

    @Test
    fun `with the switch on Street loads the own vector style by URL, day and night`() {
        for (night in listOf(false, true)) {
            // Was Basemap.STREET: Trail Map is its own entry since dispatch 2026-10-22-01.
            assertEquals(MapStyleSource.Uri(TRAIL_STYLE_URL), mapStyleSourceFor(Basemap.FORAGER_TRAIL, night, useOfflineTiles = false, ownTrail = true))
            // The Street map is its own raster whether or not Trail Map has fallen back: ui/map/street/StreetMapFallbackTest.
        }
    }

    @Test
    fun `with the switch on Topographical by day is untouched`() {
        assertEquals(
            MapStyleSource.Json(styleJsonFor(Basemap.OPEN_TOPO_MAP, false)),
            mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = false, useOfflineTiles = false, ownTrail = true),
        )
    }

    @Test
    fun `with the switch on Topographical at night is the own Street with topo over it, fading in from 9_5 to 9_7, labels ending at 9_5`() {
        val source = mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = true, useOfflineTiles = false, ownTrail = true)
        assertEquals(MapStyleSource.TrailWithTopoOver(TRAIL_STYLE_URL, Basemap.OPEN_TOPO_MAP), source)
        source as MapStyleSource.TrailWithTopoOver
        assertEquals(NIGHT_FADE_START_ZOOM, source.topoMinZoom, 0.0)
        assertEquals(NIGHT_FADE_END_ZOOM, source.topoFadeEndZoom, 0.0)
        // -1291: "The Street labels must not show through that composition": they end where the topo begins.
        assertEquals(source.topoMinZoom, source.trailLabelsMaxZoom, 0.0)
    }

    @Test
    fun `offline tiles load the style the regions hold, switch on or off`() {
        for (ownTrail in listOf(false, true)) {
            assertEquals(MapStyleSource.Uri(OFFLINE_STYLE_URL), mapStyleSourceFor(Basemap.FORAGER_TRAIL, false, useOfflineTiles = true, ownTrail = ownTrail))
            assertEquals(
                MapStyleSource.Uri(TRAIL_STYLE_URL),
                mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, true, useOfflineTiles = true, offlineStyleUrl = TRAIL_STYLE_URL, ownTrail = ownTrail),
            )
        }
    }

    @Test
    fun `the Street sprite is inverted at night only for the own Street style`() {
        assertTrue(usesTrailSprite(MapStyleSource.Uri(TRAIL_STYLE_URL)))
        // Trail Map online is v4 since the switch (dispatch 2026-11-01-coder-11 step (d)), written out so this fails if v4
        // leaves the Street night's list; a phone whose newest region is v3 or v2 loads that offline, which keeps the night.
        assertTrue(usesTrailSprite(MapStyleSource.Uri("https://forager-pmtiles.brandonlee1-894.workers.dev/style/street-v4.json")))
        assertTrue(usesTrailSprite(MapStyleSource.Uri("https://forager-pmtiles.brandonlee1-894.workers.dev/style/street-v3.json")))
        assertTrue(usesTrailSprite(MapStyleSource.Uri(TRAIL_STYLE_V2_URL)))
        // A region downloaded before v2 loads v1 offline; it takes the Street night too (dispatch 2026-10-22-03 Amendment 1).
        assertTrue(usesTrailSprite(MapStyleSource.Uri(TRAIL_STYLE_V1_URL)))
        assertTrue(usesTrailSprite(MapStyleSource.TrailWithTopoOver(TRAIL_STYLE_URL, Basemap.OPEN_TOPO_MAP)))
        assertFalse(usesTrailSprite(MapStyleSource.Uri(OFFLINE_STYLE_URL)))
        assertFalse(usesTrailSprite(MapStyleSource.Json(styleJsonFor(Basemap.OPEN_TOPO_MAP))))
    }

    @Test
    fun `the zoom limit is 18 on the own Street and each basemap's own otherwise`() {
        // Was Basemap.STREET for the own Street's 18: Trail Map is its own entry since dispatch 2026-10-22-01.
        assertEquals(18, operatingMaxZoom(Basemap.FORAGER_TRAIL, ownTrail = true))
        assertEquals("fallen back, the stand-in's", Basemap.FORAGER_TRAIL.rasterDrawn().maxZoom, operatingMaxZoom(Basemap.FORAGER_TRAIL, ownTrail = false))
        assertEquals(Basemap.OPEN_TOPO_MAP.maxZoom, operatingMaxZoom(Basemap.OPEN_TOPO_MAP, ownTrail = true))
        // The release values, through the camera's own entry point (18 for Trail Map; the Street map's 19 is held in
        // ui/map/street/StreetMapTest, dispatch 2026-11-03-05 D7).
        assertEquals(18.0, navigationMaxZoom(Basemap.FORAGER_TRAIL, navigating = false), 0.0)
    }

    @Test
    fun `the credit over the own Street is Protomaps and OpenStreetMap, also under topo at night`() {
        assertEquals(listOf(OFFLINE_STYLE_ATTRIBUTION), mapCreditsFor(Basemap.FORAGER_TRAIL, useOfflineTiles = false, ownTrail = true))
        assertEquals(
            listOf(Basemap.OPEN_TOPO_MAP.attribution, OFFLINE_STYLE_ATTRIBUTION),
            mapCreditsFor(Basemap.OPEN_TOPO_MAP, useOfflineTiles = false, nightMode = true, ownTrail = true),
        )
        // Fallen back: the credit of the raster drawn in Trail Map's place, and under topo at night of the raster drawn
        // there, if any (the Street map's while it is in the app: ui/map/street/StreetMapFallbackTest; D7, D8).
        assertEquals(listOf(Basemap.FORAGER_TRAIL.rasterDrawn().attribution), mapCreditsFor(Basemap.FORAGER_TRAIL, useOfflineTiles = false, ownTrail = false))
        assertEquals(
            listOfNotNull(Basemap.OPEN_TOPO_MAP.attribution, nightLowZoomBasemap(Basemap.OPEN_TOPO_MAP)?.attribution),
            mapCreditsFor(Basemap.OPEN_TOPO_MAP, useOfflineTiles = false, nightMode = true, ownTrail = false),
        )
    }

    @Test
    fun `a change in the offline style the regions hold is a style reload`() {
        val before = requestedMapStyle(Basemap.OPEN_TOPO_MAP, useOfflineTiles = true, nightMode = false, nightModeLoaded = true)!!
        val after = requestedMapStyle(Basemap.OPEN_TOPO_MAP, useOfflineTiles = true, nightMode = false, nightModeLoaded = true, offlineStyleUrl = TRAIL_STYLE_URL)!!
        assertEquals(OFFLINE_STYLE_URL, before.offlineStyleUrl)
        assertTrue(needsStyleReload(before, after))
    }
}
