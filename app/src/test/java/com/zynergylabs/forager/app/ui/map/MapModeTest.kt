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
    fun `the default map mode is Trail`() {
        // Was "the default map mode is Street" (RECORD -1297); the opening map became Forager Trail Map (dispatch 2026-10-22-01,
        // RECORD -1554: "New user → opens on Trail Map"). REPLACEMENT_FOR_UNKNOWN stays Topographical (below).
        assertEquals(MapMode.TRAIL, MapMode.DEFAULT)
        assertEquals(Basemap.FORAGER_TRAIL, Basemap.DEFAULT)
    }

    @Test
    fun `each mode resolves to its own fixed basemap`() {
        assertEquals(Basemap.FORAGER_TRAIL, MapMode.TRAIL.basemap)
        assertEquals(Basemap.OPEN_TOPO_MAP, MapMode.TOPOGRAPHIC.basemap)
        assertEquals(Basemap.OSM_STANDARD, MapMode.OPENSTREETMAP.basemap)
        // The Journal's map reads the mode back from the basemap (JournalTab), so the lookup must hold both ways.
        MapMode.entries.forEach { assertEquals(it, MapMode.forBasemap(it.basemap)) }
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
     * Dispatch 2026-10-22-01 Amendment 2 (RECORD -1556): the chips read exactly "Trail", "Topo" and "OSM", in that
     * order, left to right. Was "the modes are Street and Topographical, in that order" (dispatch 2026-09-28-708).
     */
    @Test
    fun `the modes are Trail, Topo and OSM, in that order`() {
        assertEquals(listOf(MapMode.TRAIL, MapMode.TOPOGRAPHIC, MapMode.OPENSTREETMAP), MapMode.entries.toList())
        assertEquals(listOf("Trail", "Topo", "OSM"), MapMode.entries.map { it.label })
    }

    /**
     * The stored key is what a phone holds across restarts, so it is pinned literally: a renamed key
     * would read back as unknown and quietly move every user who had picked that mode to Topographical.
     */
    @Test
    fun `each mode is stored under a fixed key, and reads back from it`() {
        // Trail keeps "street", so Street chosen before the update opens on Trail (RECORD -1556, option (a)).
        assertEquals("street", MapMode.TRAIL.storageKey)
        assertEquals("topographic", MapMode.TOPOGRAPHIC.storageKey)
        assertEquals("openstreetmap", MapMode.OPENSTREETMAP.storageKey)
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
