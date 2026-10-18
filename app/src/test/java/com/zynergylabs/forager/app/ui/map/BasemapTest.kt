package com.zynergylabs.forager.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariants of the basemap catalogue itself, headless on the JVM — no Android, no osmdroid.
 *
 * These deliberately do not restate the table in [Basemap]. Asserting `USGS_TOPO.maxZoom == 15`
 * would only prove the file can be read back, and CLAUDE.md calls a check that cannot fail on a
 * real change what it is. What is asserted here is the set of properties the *feature* depends on
 * and that a plausible future edit could break: that the app is still usable outside the United
 * States, that every option can be told apart in the menu, that no option is shipped without the
 * credit text the UI relies on, and that no US-only service has crept back in.
 *
 * The claims that need the real osmdroid artifact — names, zoom ceilings, URL shape, copyright
 * strings, cache separation — are in `BasemapTileSourceTest`. The claim that a live swap preserves
 * the map's overlays is in `SightingsMapBasemapSwapTest`.
 */
class BasemapTest {

    /**
     * The lowest ceiling a basemap can have and still be usable at all.
     *
     * `zoomForRadiusKm` in `SightingsMap.kt` opens the map between 9 and 13 depending on search
     * radius, 13 being the tightest (a radius of 5km or less). A basemap whose ceiling sat below 13
     * would be clamped to below its own opening zoom the moment it was selected for a small search
     * — the map would silently refuse to open where the code says it opens. Duplicated as a literal
     * rather than imported because `zoomForRadiusKm` lives in a Compose/osmdroid file that a plain
     * JVM test should not be loading; the coupling is stated here so a change to either end has a
     * reason to look at the other.
     */
    private val minimumUsableMaxZoom = 13

    @Test
    fun `the default basemap is OpenStreetMap's Street, derived from MapMode's default`() {
        // Not USGS Topo (PR #13's original default, deleted from the catalogue entirely once
        // MapMode superseded it): MapMode.DEFAULT is Topographical via OpenStreetMap, because USGS
        // covers the United States only and a US-only opening basemap would break the map outright
        // for every user outside it. See MapMode's own doc comment.
        // Was assertEquals(Basemap.OPEN_TOPO_MAP, Basemap.DEFAULT): the opening map became Street (dispatch 2026-10-18-04, Amendment 2; RECORD -1297).
        assertEquals(Basemap.OSM_STANDARD, Basemap.DEFAULT)
        assertEquals(MapMode.DEFAULT.basemap, Basemap.DEFAULT)
    }

    /**
     * Dispatch 2026-09-28-708 removed Satellite, the last USGS basemap, and with it [Basemap]'s
     * coverage field and its United-States-only note, which no remaining basemap needed. This replaces
     * the four coverage tests that went with them (that one basemap works outside the US, that OSM
     * Standard is unrestricted, that a limited basemap carries a note, and the USGS/US-only
     * biconditional): every basemap left is worldwide, so what can still go wrong is a US-only service
     * coming back without its limit being stated. That is the case this pins, by the two marks a USGS
     * National Map basemap carried.
     */
    @Test
    fun `no basemap is a US-only USGS service`() {
        Basemap.entries.forEach { basemap ->
            assertFalse("${basemap.name}'s credit names USGS: ${basemap.attribution}", basemap.attribution.contains("USGS"))
            assertFalse(
                "${basemap.name} draws from the US-only National Map: ${basemap.tileUrlTemplate}",
                basemap.tileUrlTemplate.contains("nationalmap.gov"),
            )
        }
    }

    @Test
    fun `every basemap has the label description and attribution the selector renders`() {
        Basemap.entries.forEach { basemap ->
            assertTrue("${basemap.name} has a blank label", basemap.label.isNotBlank())
            assertTrue("${basemap.name} has a blank description", basemap.description.isNotBlank())
            // Public domain still gets credited — an option with no attribution would render a
            // dangling separator in the menu and credit nobody.
            assertTrue("${basemap.name} has a blank attribution", basemap.attribution.isNotBlank())
        }
    }

    @Test
    fun `basemap labels are distinct`() {
        val labels = Basemap.entries.map { it.label }
        assertEquals(
            "Two basemaps sharing a label are indistinguishable in the selector: $labels",
            labels.size,
            labels.toSet().size,
        )
    }

    @Test
    fun `every basemap can reach the tightest zoom the map opens at`() {
        Basemap.entries.forEach { basemap ->
            assertTrue(
                "${basemap.name} caps zoom at ${basemap.maxZoom}, below the $minimumUsableMaxZoom " +
                    "that zoomForRadiusKm opens a small-radius search at, so selecting it would " +
                    "clamp the map below its own opening zoom.",
                basemap.maxZoom >= minimumUsableMaxZoom,
            )
        }
    }

    /**
     * Dispatch 2026-10-18-04, item 3. OpenTopoMap's own page asks for "Kartendaten: © OpenStreetMap-Mitwirkende, SRTM |
     * Kartendarstellung: © OpenTopoMap (CC-BY-SA)" (Juniper's research, Q2). The owner's "option c": the short form stays
     * in the always-visible caption, where the full one would wrap on the S22 in portrait, and the full English form goes
     * in the tap-to-reveal attribution ([Basemap.fullAttribution]). Both texts are pinned here, and the caption is read
     * through the function the map's caption draws ([attributionCaption] of [mapCreditsFor]).
     */
    @Test
    fun `OpenTopoMap's caption keeps the short credit and its full credit is the long form`() {
        assertEquals("© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)", Basemap.OPEN_TOPO_MAP.fullAttribution)
        assertEquals("© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)", Basemap.OPEN_TOPO_MAP.attribution)
        assertEquals(
            "© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)",
            attributionCaption(mapCreditsFor(Basemap.OPEN_TOPO_MAP, useOfflineTiles = false)),
        )
    }

    @Test
    fun `Street's caption and full credit are the same line`() {
        assertEquals(Basemap.OSM_STANDARD.attribution, Basemap.OSM_STANDARD.fullAttribution)
    }
}
