package com.forager.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mode-derivation logic Settings and the quick-fire map icon both depend on, headless.
 *
 * The load-bearing claim under test is "if a map has two modes, toggle the two" — [isTopoMode] is
 * independent of [MapService], so switching service must never reset it. That is asserted directly
 * below rather than left to be discovered from a UI test, since it is a pure function of two values
 * with no Android or Compose involved.
 */
class MapServiceTest {

    @Test
    fun `the default map service is OpenStreetMap`() {
        // A deliberate change from Basemap's own PR #13 default (USGS Topo) — see MapService's doc
        // comment for why a US-only source cannot be what the app opens on.
        assertEquals(MapService.OPEN_STREET_MAP, MapService.DEFAULT)
    }

    @Test
    fun `each service resolves to its own topo and regular basemap`() {
        assertEquals(Basemap.OPEN_TOPO_MAP, MapService.OPEN_STREET_MAP.basemapFor(isTopoMode = true))
        assertEquals(Basemap.OSM_STANDARD, MapService.OPEN_STREET_MAP.basemapFor(isTopoMode = false))
        assertEquals(Basemap.USGS_TOPO, MapService.USGS.basemapFor(isTopoMode = true))
        assertEquals(Basemap.USGS_IMAGERY_TOPO, MapService.USGS.basemapFor(isTopoMode = false))
    }

    @Test
    fun `switching service preserves topo or regular mode`() {
        // The whole point of keeping isTopoMode as state independent of MapService: a user in topo
        // mode under OpenStreetMap who switches the service to USGS lands on USGS Topo, not on
        // whatever USGS's own basemapFor(false) would give if the mode were reset on a service swap.
        val isTopoMode = true
        val onOpenStreetMap = MapService.OPEN_STREET_MAP.basemapFor(isTopoMode)
        val afterSwitchingToUsgs = MapService.USGS.basemapFor(isTopoMode)

        assertEquals(Basemap.OPEN_TOPO_MAP, onOpenStreetMap)
        assertEquals(Basemap.USGS_TOPO, afterSwitchingToUsgs)
        assertTrue(
            "Both basemaps must be the topo one for their service: the mode carried across the switch.",
            afterSwitchingToUsgs.label.contains("Topo") || afterSwitchingToUsgs == Basemap.USGS_TOPO,
        )

        val notTopoMode = false
        assertEquals(Basemap.OSM_STANDARD, MapService.OPEN_STREET_MAP.basemapFor(notTopoMode))
        assertEquals(Basemap.USGS_IMAGERY_TOPO, MapService.USGS.basemapFor(notTopoMode))
    }

    @Test
    fun `every basemap in the catalogue belongs to exactly one service, in the matching mode`() {
        val allResolved = MapService.entries.flatMap { listOf(it.basemapFor(true), it.basemapFor(false)) }
        assertEquals(
            "Every Basemap entry must be reachable through some MapService, and none twice.",
            Basemap.entries.toSet(),
            allResolved.toSet(),
        )
        assertEquals(Basemap.entries.size, allResolved.size)
    }

    @Test
    fun `services are labeled distinctly`() {
        val labels = MapService.entries.map { it.label }
        assertNotEquals(labels[0], labels[1])
    }

    @Test
    fun `offline downloading only ever resolves through the USGS service`() {
        // The structural half of decision #7: OfflineMapRepository callers must only ever be handed
        // a USGS basemap. This does not test the UI gate itself (AvailabilityScreen's tests do), but
        // pins the fact the gate depends on: MapService.USGS's two basemaps are both USGS ones, and
        // OPEN_STREET_MAP's are not.
        assertTrue(MapService.USGS.topoBasemap.attribution.contains("USGS"))
        assertTrue(MapService.USGS.regularBasemap.attribution.contains("USGS"))
        assertTrue(!MapService.OPEN_STREET_MAP.topoBasemap.attribution.contains("USGS"))
        assertTrue(!MapService.OPEN_STREET_MAP.regularBasemap.attribution.contains("USGS"))
    }
}
