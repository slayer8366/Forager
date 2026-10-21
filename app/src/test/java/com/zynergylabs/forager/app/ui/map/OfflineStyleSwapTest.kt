package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.ui.theme.MapPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three pure decisions Stage 2e-ii adds to [SightingsMap] — which style document to load, what
 * credit to show over it, and when a reload is due — asserted away from the native map, the same
 * boundary `BasemapStyleTest` draws for [styleJsonFor]. Nothing here shows a tile came from the
 * downloaded store; only a device can (see the pre-build report's §5.2).
 *
 * Expected values are literals, not derived from the code under test: the offline URL below is the
 * worker path the regions were downloaded against, typed here independently, so a change to either
 * side is a failing test rather than a silent agreement.
 */
class OfflineStyleSwapTest {

    private val palette = MapPalette.DAY

    @Test
    fun `offline on loads the regions' own style by URI, the exact string the download used`() {
        val source = mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = false, useOfflineTiles = true)

        assertEquals(
            MapStyleSource.Uri("https://forager-pmtiles.brandonlee1-894.workers.dev/style/offline.json"),
            source,
        )
    }

    @Test
    fun `offline on ignores the basemap and night mode, since the store holds one style`() {
        val expected = mapStyleSourceFor(Basemap.OPEN_TOPO_MAP, night = false, useOfflineTiles = true)

        Basemap.entries.forEach { basemap ->
            listOf(false, true).forEach { night ->
                assertEquals("basemap=$basemap night=$night", expected, mapStyleSourceFor(basemap, night, useOfflineTiles = true))
            }
        }
    }

    @Test
    fun `offline off loads the basemap's own raster style as JSON, night applied`() {
        Basemap.entries.forEach { basemap ->
            // Driven with the switch off: the raster path, which the own Street replaces for Street (D3, forced by the owner's -1511).
            val day = mapStyleSourceFor(basemap, night = false, useOfflineTiles = false, ownStreet = false)
            val night = mapStyleSourceFor(basemap, night = true, useOfflineTiles = false, ownStreet = false)

            assertTrue("$basemap day must be a JSON style", day is MapStyleSource.Json)
            assertTrue("$basemap must name its own tile template", (day as MapStyleSource.Json).json.contains(basemap.tileUrlTemplate))
            assertTrue(
                "$basemap night must still be the basemap's JSON style, with the V1 night paint",
                (night as MapStyleSource.Json).json.contains("raster-hue-rotate"),
            )
            assertFalse("day must not carry the night paint", day.json.contains("raster-hue-rotate"))
        }
    }

    @Test
    fun `the credit over the offline style is Protomaps and OpenStreetMap, never the basemap's`() {
        Basemap.entries.forEach { basemap ->
            assertEquals("Protomaps © OpenStreetMap", mapAttributionFor(basemap, useOfflineTiles = true))
        }
    }

    @Test
    fun `the credit over a basemap is that basemap's own`() {
        assertEquals("© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)", mapAttributionFor(Basemap.OPEN_TOPO_MAP, useOfflineTiles = false))
        assertEquals("Protomaps © OpenStreetMap", mapAttributionFor(Basemap.OSM_STANDARD, useOfflineTiles = false)) // Street is the own map, forced by the owner's -1511
    }

    @Test
    fun `flipping only the offline flag is a reload -- the gap the old basemap-and-palette guard left open`() {
        val online = AppliedMapStyle(Basemap.OPEN_TOPO_MAP, palette, useOfflineTiles = false, night = false)
        val offline = online.copy(useOfflineTiles = true)

        assertTrue(needsStyleReload(applied = online, requested = offline))
        assertTrue(needsStyleReload(applied = offline, requested = online))
    }

    @Test
    fun `nothing applied yet is a reload, and an identical request is not`() {
        val style = AppliedMapStyle(Basemap.OSM_STANDARD, palette, useOfflineTiles = false, night = false)

        assertTrue(needsStyleReload(applied = null, requested = style))
        assertFalse(needsStyleReload(applied = style, requested = style))
    }

    @Test
    fun `a basemap change is still a reload, with the offline flag unchanged`() {
        val topo = AppliedMapStyle(Basemap.OPEN_TOPO_MAP, palette, useOfflineTiles = false, night = false)
        val osm = topo.copy(basemap = Basemap.OSM_STANDARD)

        assertTrue(needsStyleReload(applied = topo, requested = osm))
    }

    /**
     * What the style effect builds for one set of inputs: the Night Maps toggle, for the basemap and for
     * the marker palette alike. Before C2 this helper fixed the palette at DAY; before dispatch
     * 2026-09-28-708 the basemap's night was `effectiveNight`, which differed from the toggle only over
     * Satellite and went with it. Written out here rather than calling production code, so the
     * "once loaded" test below compares against an expectation, not against itself.
     */
    private fun applied(basemap: Basemap, nightMode: Boolean, useOfflineTiles: Boolean = false) = AppliedMapStyle(
        basemap = basemap,
        palette = MapPalette.forMode(nightMode),
        useOfflineTiles = useOfflineTiles,
        night = nightMode,
    )

    @Test
    fun `turning Night Maps on or off is a reload on Topographical and Street`() {
        for (basemap in listOf(Basemap.OPEN_TOPO_MAP, Basemap.OSM_STANDARD)) {
            val day = applied(basemap, nightMode = false)
            val night = applied(basemap, nightMode = true)

            assertTrue("$basemap: day -> night", needsStyleReload(applied = day, requested = night))
            assertTrue("$basemap: night -> day", needsStyleReload(applied = night, requested = day))
        }
    }

    @Test
    fun `the marker palette follows Night Maps on every basemap, online and offline`() {
        Basemap.entries.forEach { basemap ->
            listOf(false, true).forEach { offline ->
                listOf(false, true).forEach { nightMode ->
                    val requested = requestedMapStyle(basemap, useOfflineTiles = offline, nightMode = nightMode, nightModeLoaded = true)
                    assertEquals(
                        "$basemap offline=$offline night=$nightMode",
                        if (nightMode) MapPalette.NIGHT else MapPalette.DAY,
                        requested?.palette,
                    )
                }
            }
        }
    }

    @Test
    fun `turning Night Maps on or off over the offline style is a reload, whatever the basemap`() {
        Basemap.entries.forEach { basemap ->
            val day = applied(basemap, nightMode = false, useOfflineTiles = true)
            val night = applied(basemap, nightMode = true, useOfflineTiles = true)

            assertTrue("$basemap offline: day -> night", needsStyleReload(applied = day, requested = night))
            assertTrue("$basemap offline: night -> day", needsStyleReload(applied = night, requested = day))
        }
    }

    /**
     * Replaces "effective night is the toggle, except Satellite's own raster style, which stays day"
     * (dispatch 2026-09-28-708): with Satellite gone the basemap's night is the toggle everywhere, so
     * this asserts that through [requestedMapStyle], the function the style effect calls.
     */
    @Test
    fun `the requested basemap night is the Night Maps toggle on every basemap, online and offline`() {
        Basemap.entries.forEach { basemap ->
            listOf(false, true).forEach { offline ->
                assertEquals("$basemap offline=$offline night on", true, requestedMapStyle(basemap, offline, nightMode = true, nightModeLoaded = true)?.night)
                assertEquals("$basemap offline=$offline night off", false, requestedMapStyle(basemap, offline, nightMode = false, nightModeLoaded = true)?.night)
            }
        }
    }

    /**
     * The cold-launch gate, at the level a JVM test reaches: the style effect asks
     * [requestedMapStyle] for what to load and returns early on `null`, so nothing is requested
     * (no `mapStyleSourceFor`, no `setStyle`) until the Night Maps preference has loaded. The
     * effect's own keys are not reachable headless (see the completion report): SightingsMap
     * constructs a MapView, whose native initialiser Robolectric cannot run.
     */
    @Test
    fun `no style is requested before the Night Maps preference has loaded`() {
        Basemap.entries.forEach { basemap ->
            listOf(false, true).forEach { nightMode ->
                listOf(false, true).forEach { offline ->
                    assertNull(
                        "$basemap night=$nightMode offline=$offline",
                        requestedMapStyle(basemap, useOfflineTiles = offline, nightMode = nightMode, nightModeLoaded = false),
                    )
                }
            }
        }
    }

    @Test
    fun `once loaded, the requested style carries the Night Maps toggle`() {
        Basemap.entries.forEach { basemap ->
            listOf(false, true).forEach { nightMode ->
                listOf(false, true).forEach { offline ->
                    assertEquals(
                        "$basemap night=$nightMode offline=$offline",
                        applied(basemap, nightMode = nightMode, useOfflineTiles = offline),
                        requestedMapStyle(basemap, useOfflineTiles = offline, nightMode = nightMode, nightModeLoaded = true),
                    )
                }
            }
        }
    }
}
