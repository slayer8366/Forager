package com.forager.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mode-to-basemap wiring the map's own quick-fire picker depends on, headless. Replaces
 * `MapServiceTest` — see [MapMode]'s own doc comment for what superseded the two-tier
 * service/mode split that test covered.
 */
class MapModeTest {

    @Test
    fun `the default map mode is Topographical`() {
        assertEquals(MapMode.TOPOGRAPHIC, MapMode.DEFAULT)
    }

    @Test
    fun `each mode resolves to its own fixed basemap`() {
        assertEquals(Basemap.OSM_STANDARD, MapMode.STREET.basemap)
        assertEquals(Basemap.OPEN_TOPO_MAP, MapMode.TOPOGRAPHIC.basemap)
        assertEquals(Basemap.USGS_IMAGERY_ONLY, MapMode.SATELLITE.basemap)
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

    @Test
    fun `only Satellite resolves to a USGS basemap`() {
        // The structural half of decision #7 (see AvailabilityScreen's OfflineMapsPanel doc
        // comment): offline downloads never followed live basemap selection anyway, but this pins
        // the fact that Street and Topographical are never USGS, so nothing about them could
        // accidentally start depending on US-only coverage.
        assertTrue(MapMode.SATELLITE.basemap.attribution.contains("USGS"))
        assertTrue(!MapMode.STREET.basemap.attribution.contains("USGS"))
        assertTrue(!MapMode.TOPOGRAPHIC.basemap.attribution.contains("USGS"))
    }

    @Test
    fun `only Satellite is US-only coverage`() {
        assertEquals(BasemapCoverage.UNITED_STATES_ONLY, MapMode.SATELLITE.basemap.coverage)
        assertEquals(BasemapCoverage.WORLDWIDE, MapMode.STREET.basemap.coverage)
        assertEquals(BasemapCoverage.WORLDWIDE, MapMode.TOPOGRAPHIC.basemap.coverage)
        assertNotEquals(MapMode.STREET.basemap, MapMode.TOPOGRAPHIC.basemap)
    }
}
