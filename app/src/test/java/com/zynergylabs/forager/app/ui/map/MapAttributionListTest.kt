package com.zynergylabs.forager.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The attribution caption as a list of credits (map layers L0a, A5): the basemap's credit first, then
 * any active layer's, joined for display, with today's single-credit caption unchanged.
 */
class MapAttributionListTest {

    @Test
    fun `with no layer credits the list is the one credit the caption has always shown`() {
        Basemap.entries.forEach { basemap ->
            listOf(false, true).forEach { offline ->
                assertEquals(listOf(mapAttributionFor(basemap, offline)), mapCreditsFor(basemap, offline))
            }
        }
    }

    @Test
    fun `a single credit's caption is that credit exactly, for every basemap and the offline style`() {
        Basemap.entries.forEach { basemap ->
            listOf(false, true).forEach { offline ->
                assertEquals(mapAttributionFor(basemap, offline), attributionCaption(mapCreditsFor(basemap, offline)))
            }
        }
    }

    @Test
    fun `a layer's credit follows the basemap's, joined by a middle dot`() {
        val credits = mapCreditsFor(Basemap.OSM_STANDARD, useOfflineTiles = false, layerCredits = listOf("Forecast: example"))
        assertEquals(listOf("© OpenStreetMap contributors", "Forecast: example"), credits)
        assertEquals("© OpenStreetMap contributors · Forecast: example", attributionCaption(credits))
    }

    @Test
    fun `over the offline style the offline credit comes first`() {
        val credits = mapCreditsFor(Basemap.OPEN_TOPO_MAP, useOfflineTiles = true, layerCredits = listOf("Forecast: example"))
        assertEquals(listOf("Protomaps © OpenStreetMap", "Forecast: example"), credits)
    }

    @Test
    fun `a credit already present is not repeated`() {
        val credits = mapCreditsFor(
            Basemap.OSM_STANDARD,
            useOfflineTiles = false,
            layerCredits = listOf("© OpenStreetMap contributors", "Forecast: example", "Forecast: example"),
        )
        assertEquals(listOf("© OpenStreetMap contributors", "Forecast: example"), credits)
    }
}
