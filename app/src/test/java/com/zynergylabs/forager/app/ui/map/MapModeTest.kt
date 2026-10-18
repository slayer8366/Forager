package com.zynergylabs.forager.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The mode-to-basemap wiring the map's own quick-fire picker depends on, headless. Replaces
 * `MapServiceTest` — see [MapMode]'s own doc comment for what superseded the two-tier
 * service/mode split that test covered.
 */
class MapModeTest {

    @Test
    fun `the default map mode is Street`() {
        // Was "the default map mode is Topographical", asserting assertEquals(MapMode.TOPOGRAPHIC, MapMode.DEFAULT);
        // the opening map became Street by the owner's ruling (dispatch 2026-10-18-04, Amendment 2; RECORD -1297). REPLACEMENT_FOR_UNKNOWN stays Topographical (below).
        assertEquals(MapMode.STREET, MapMode.DEFAULT)
    }

    @Test
    fun `each mode resolves to its own fixed basemap`() {
        assertEquals(Basemap.OSM_STANDARD, MapMode.STREET.basemap)
        assertEquals(Basemap.OPEN_TOPO_MAP, MapMode.TOPOGRAPHIC.basemap)
    }

    @Test
    fun `every basemap in the catalogue belongs to exactly one mode`() {
        val allResolved = MapMode.entries.map { it.basemap }
        assertEquals(
            "Every Basemap entry must be reachable through some MapMode, and none twice.",
            Basemap.entries.toSet(),
            allResolved.toSet(),
        )
        assertEquals(Basemap.entries.size, allResolved.size)
    }

    @Test
    fun `modes are labeled distinctly`() {
        val labels = MapMode.entries.map { it.label }
        assertEquals(labels.toSet().size, labels.size)
    }

    /**
     * Dispatch 2026-09-28-708: the map offers exactly Street and Topographical. Replaces "only Satellite
     * resolves to a USGS basemap" and "only Satellite is US-only coverage", whose subject is gone.
     */
    @Test
    fun `the modes are Street and Topographical, in that order`() {
        assertEquals(listOf(MapMode.STREET, MapMode.TOPOGRAPHIC), MapMode.entries.toList())
        assertEquals(listOf("Street", "Topographical"), MapMode.entries.map { it.label })
    }

    /**
     * The stored key is what a phone holds across restarts, so it is pinned literally: a renamed key
     * would read back as unknown and quietly move every user who had picked that mode to Topographical.
     */
    @Test
    fun `each mode is stored under a fixed key, and reads back from it`() {
        assertEquals("street", MapMode.STREET.storageKey)
        assertEquals("topographic", MapMode.TOPOGRAPHIC.storageKey)
        MapMode.entries.forEach { assertEquals(it, MapMode.forStoredKey(it.storageKey)) }
    }

    @Test
    fun `a stored key that names no mode reads as none, and the caller moves it to Topographical`() {
        assertNull(MapMode.forStoredKey("satellite"))
        assertNull(MapMode.forStoredKey("SATELLITE"))
        assertNull(MapMode.forStoredKey(""))
        assertEquals("the owner's answer for anyone on Satellite: \"Topo (Recommended)\"", MapMode.TOPOGRAPHIC, MapMode.REPLACEMENT_FOR_UNKNOWN)
    }
}
