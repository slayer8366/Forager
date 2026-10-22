package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.map.OFFLINE_STYLE_ATTRIBUTION
import com.zynergylabs.forager.app.map.OFFLINE_STYLE_URL
import com.zynergylabs.forager.app.map.STREET_STYLE_URL
import com.zynergylabs.forager.app.map.STREET_STYLE_V1_URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which style the map loads, its credit and its zoom limit, for Forager Trail Map drawing and fallen back (dispatch
 * 2026-10-18-05 Part 2; since dispatch 2026-10-22-01 Trail Map is its own map type, [Basemap.FORAGER_TRAIL], and
 * OpenStreetMap is always its raster). These are the values `SightingsMap`'s style effect hands to MapLibre:
 * [mapStyleSourceFor], [operatingMaxZoom] (also through [navigationMaxZoom]) and [mapCreditsFor]. `ownStreet` is now
 * whether Trail Map draws in this process (`OwnStreetFallback`, -1513): `true` by default, `false` once it fell back.
 * In the test names, "the switch off" reads as "fallen back" and "own Street" as "Trail Map".
 */
class OwnStreetStyleSourceTest {

    @Test
    fun `with the switch off every basemap loads its raster style as before, day and night`() {
        for (basemap in Basemap.entries) for (night in listOf(false, true)) {
            assertEquals(
                MapStyleSource.Json(styleJsonFor(basemap, night)),
                mapStyleSourceFor(basemap, night, useOfflineTiles = false, ownStreet = false),
            )
        }
    }

    @Test
    fun `with the switch on Street loads the own vector style by URL, day and night`() {
        for (night in listOf(false, true)) {
            // Was Basemap.OSM_STANDARD: Trail Map is its own entry since dispatch 2026-10-22-01.
            assertEquals(MapStyleSource.Uri(STREET_STYLE_URL), mapStyleSourceFor(Basemap.FORAGER_TRAIL, night, useOfflineTiles = false, ownStreet = true))
            // OpenStreetMap is its own raster whether or not Trail Map has fallen back (RECORD -1554).
            assertEquals(MapStyleSource.Json(styleJsonFor(Basemap.OSM_STANDARD, night)), mapStyleSourceFor(Basemap.OSM_STANDARD, night, useOfflineTiles = false, ownStreet = true))
        }
    }

    @Test
    fun `with the switch on Topographical by day is untouched`() {
        assertEquals(
            MapStyleSource.Json(styleJsonFor(Basemap.OPEN_TOPO_MAP, false)),
            mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = false, useOfflineTiles = false, ownStreet = true),
        )
    }

    @Test
    fun `with the switch on Topographical at night is the own Street with topo over it, fading in from 9_5 to 9_7, labels ending at 9_5`() {
        val source = mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = true, useOfflineTiles = false, ownStreet = true)
        assertEquals(MapStyleSource.StreetWithTopoOver(STREET_STYLE_URL, Basemap.OPEN_TOPO_MAP), source)
        source as MapStyleSource.StreetWithTopoOver
        assertEquals(NIGHT_FADE_START_ZOOM, source.topoMinZoom, 0.0)
        assertEquals(NIGHT_FADE_END_ZOOM, source.topoFadeEndZoom, 0.0)
        // -1291: "The Street labels must not show through that composition": they end where the topo begins.
        assertEquals(source.topoMinZoom, source.streetLabelsMaxZoom, 0.0)
    }

    @Test
    fun `offline tiles load the style the regions hold, switch on or off`() {
        for (ownStreet in listOf(false, true)) {
            assertEquals(MapStyleSource.Uri(OFFLINE_STYLE_URL), mapStyleSourceFor(Basemap.OSM_STANDARD, false, useOfflineTiles = true, ownStreet = ownStreet))
            assertEquals(
                MapStyleSource.Uri(STREET_STYLE_URL),
                mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, true, useOfflineTiles = true, offlineStyleUrl = STREET_STYLE_URL, ownStreet = ownStreet),
            )
        }
    }

    @Test
    fun `the Street sprite is inverted at night only for the own Street style`() {
        assertTrue(usesStreetSprite(MapStyleSource.Uri(STREET_STYLE_URL)))
        // A region downloaded before v2 loads v1 offline; it takes the Street night too (dispatch 2026-10-22-03 Amendment 1).
        assertTrue(usesStreetSprite(MapStyleSource.Uri(STREET_STYLE_V1_URL)))
        assertTrue(usesStreetSprite(MapStyleSource.StreetWithTopoOver(STREET_STYLE_URL, Basemap.OPEN_TOPO_MAP)))
        assertFalse(usesStreetSprite(MapStyleSource.Uri(OFFLINE_STYLE_URL)))
        assertFalse(usesStreetSprite(MapStyleSource.Json(styleJsonFor(Basemap.OSM_STANDARD))))
    }

    @Test
    fun `the zoom limit is 18 on the own Street and each basemap's own otherwise`() {
        // Was Basemap.OSM_STANDARD for the own Street's 18: Trail Map is its own entry since dispatch 2026-10-22-01.
        assertEquals(18, operatingMaxZoom(Basemap.FORAGER_TRAIL, ownStreet = true))
        assertEquals("fallen back, OpenStreetMap's", Basemap.OSM_STANDARD.maxZoom, operatingMaxZoom(Basemap.FORAGER_TRAIL, ownStreet = false))
        assertEquals(Basemap.OSM_STANDARD.maxZoom, operatingMaxZoom(Basemap.OSM_STANDARD, ownStreet = true))
        assertEquals(Basemap.OPEN_TOPO_MAP.maxZoom, operatingMaxZoom(Basemap.OPEN_TOPO_MAP, ownStreet = true))
        // The release values, through the camera's own entry point (the dispatch: 18 for Trail Map, 19 for OpenStreetMap).
        assertEquals(18.0, navigationMaxZoom(Basemap.FORAGER_TRAIL, navigating = false), 0.0)
        assertEquals(19.0, navigationMaxZoom(Basemap.OSM_STANDARD, navigating = false), 0.0)
    }

    @Test
    fun `the credit over the own Street is Protomaps and OpenStreetMap, also under topo at night`() {
        assertEquals(listOf(OFFLINE_STYLE_ATTRIBUTION), mapCreditsFor(Basemap.FORAGER_TRAIL, useOfflineTiles = false, ownStreet = true))
        assertEquals(
            listOf(Basemap.OPEN_TOPO_MAP.attribution, OFFLINE_STYLE_ATTRIBUTION),
            mapCreditsFor(Basemap.OPEN_TOPO_MAP, useOfflineTiles = false, nightMode = true, ownStreet = true),
        )
        // Fallen back: OpenStreetMap's own credit, for the raster drawn in Trail Map's place.
        assertEquals(listOf(Basemap.OSM_STANDARD.attribution), mapCreditsFor(Basemap.FORAGER_TRAIL, useOfflineTiles = false, ownStreet = false))
        // OpenStreetMap chosen: its own credit either way.
        assertEquals(listOf(Basemap.OSM_STANDARD.attribution), mapCreditsFor(Basemap.OSM_STANDARD, useOfflineTiles = false, ownStreet = true))
        assertEquals(
            listOf(Basemap.OPEN_TOPO_MAP.attribution, Basemap.OSM_STANDARD.attribution),
            mapCreditsFor(Basemap.OPEN_TOPO_MAP, useOfflineTiles = false, nightMode = true, ownStreet = false),
        )
    }

    @Test
    fun `a change in the offline style the regions hold is a style reload`() {
        val before = requestedMapStyle(Basemap.OSM_STANDARD, useOfflineTiles = true, nightMode = false, nightModeLoaded = true)!!
        val after = requestedMapStyle(Basemap.OSM_STANDARD, useOfflineTiles = true, nightMode = false, nightModeLoaded = true, offlineStyleUrl = STREET_STYLE_URL)!!
        assertEquals(OFFLINE_STYLE_URL, before.offlineStyleUrl)
        assertTrue(needsStyleReload(before, after))
    }
}
