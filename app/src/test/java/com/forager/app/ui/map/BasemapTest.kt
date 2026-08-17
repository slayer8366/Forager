package com.forager.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * coverage and credit text the UI relies on, and that the two enums agree about what a limit means.
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
    fun `the default basemap is USGS Topo`() {
        assertEquals(Basemap.USGS_TOPO, Basemap.DEFAULT)
    }

    /**
     * The load-bearing one. USGS covers the United States only, and the default is USGS, so the
     * only thing that keeps this app usable in Europe or anywhere else is that the selector offers
     * a basemap without that limit. Delete [Basemap.OSM_STANDARD] and [Basemap.OPEN_TOPO_MAP] and
     * every other test here still passes while the map is blank for every non-US user.
     */
    @Test
    fun `at least one basemap works outside the United States`() {
        val worldwide = Basemap.entries.filter { it.coverage == BasemapCoverage.WORLDWIDE }
        assertTrue(
            "Every basemap is limited to ${BasemapCoverage.UNITED_STATES_ONLY}, so a user outside " +
                "the US would have no working option. At least one WORLDWIDE basemap must be offered.",
            worldwide.isNotEmpty(),
        )
    }

    /** The current OSM standard street map stays on offer: it is the basemap this feature replaced as default. */
    @Test
    fun `the OpenStreetMap standard basemap is still offered and unrestricted`() {
        assertEquals(BasemapCoverage.WORLDWIDE, Basemap.OSM_STANDARD.coverage)
    }

    @Test
    fun `a limited-coverage basemap carries a note and a worldwide one does not`() {
        assertNull(
            "A basemap that works everywhere has no coverage caveat to show, so the UI must get " +
                "null rather than reassuring text it would then have to render.",
            BasemapCoverage.WORLDWIDE.note,
        )
        val restricted = BasemapCoverage.UNITED_STATES_ONLY.note
        assertNotNull(
            "The US-only coverage note is what the selector shows in place of coverage detection.",
            restricted,
        )
        assertTrue(
            "The note is the app's only statement of the coverage limit, so it has to name it: $restricted",
            restricted!!.contains("United States"),
        )
    }

    @Test
    fun `every US-only basemap is a USGS one and every USGS basemap is US-only`() {
        // Stated as a biconditional on purpose: adding a USGS service without its coverage note, or
        // marking a worldwide source US-only, are both real mistakes and neither is caught by a
        // one-directional check.
        Basemap.entries.forEach { basemap ->
            val isUsgs = basemap.attribution.contains("USGS")
            val isRestricted = basemap.coverage == BasemapCoverage.UNITED_STATES_ONLY
            assertEquals(
                "${basemap.name}: a USGS basemap covers the US only, and nothing else here does. " +
                    "attribution=\"${basemap.attribution}\" coverage=${basemap.coverage}",
                isUsgs,
                isRestricted,
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
}
