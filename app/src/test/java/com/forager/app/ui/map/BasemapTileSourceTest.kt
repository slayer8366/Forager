package com.forager.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What [tileSourceFor] actually resolves to in the pinned osmdroid artifact.
 *
 * ## Why these assertions exist
 *
 * Every number and string in [Basemap] is a claim about `osmdroid-android-6.1.20`. They were read
 * out of that artifact's bytecode rather than from documentation, but a claim checked once by hand
 * and then written into a source file is a claim that silently rots at the next version bump — and
 * the way it would rot is invisible: a lowered zoom ceiling shows up as the map refusing to zoom, a
 * renamed source shows up as a cold tile cache, an emptied copyright string shows up as a map that
 * ships with no attribution at all. None of those fail a build. These do.
 *
 * Robolectric rather than a plain JVM test because `TileSourceFactory`'s sources are
 * `BitmapTileSourceBase` subclasses, which reach into `android.graphics` to decode a tile.
 *
 * ## What this cannot check
 *
 * That the endpoints serve tiles, and that they stop serving them outside the US. No unit test
 * should make a network call; `scripts/verify-usgs-basemap.sh` does that against the live services
 * and is the evidence behind the coverage and zoom-ceiling claims documented in [Basemap].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class BasemapTileSourceTest {

    /**
     * osmdroid's own name for each source. This is not cosmetic: it is the value osmdroid writes
     * into the `provider` column that separates one basemap's cached tiles from another's, so this
     * table is also the assertion that the caches stay separate.
     */
    private val expectedSourceNames = mapOf(
        Basemap.USGS_TOPO to "USGS National Map Topo",
        Basemap.USGS_IMAGERY_TOPO to "USGS National Map Sat",
        Basemap.OPEN_TOPO_MAP to "OpenTopoMap",
        Basemap.OSM_STANDARD to "Mapnik",
    )

    @Test
    fun `every basemap resolves to the expected osmdroid source`() {
        Basemap.entries.forEach { basemap ->
            assertEquals(
                "${basemap.name} resolved to the wrong osmdroid tile source",
                expectedSourceNames.getValue(basemap),
                tileSourceFor(basemap).name(),
            )
        }
    }

    /**
     * The disk-cache separation check. osmdroid's `SqlTileWriter` stores every basemap's tiles in one
     * table and tells them apart by `provider = ITileSource.name()` alone, so two basemaps sharing a
     * name would serve each other's tiles for the same z/x/y — the "mixed tiles from two basemaps in
     * one view" bug, arriving through the cache rather than through the swap.
     */
    @Test
    fun `no two basemaps share a tile source name`() {
        val names = Basemap.entries.map { tileSourceFor(it).name() }
        assertEquals(
            "Basemaps sharing an osmdroid source name share a tile cache namespace: $names",
            names.size,
            names.toSet().size,
        )
    }

    /**
     * The drift guard for [Basemap.maxZoom]. Our value is the app's own operating limit, so it is
     * allowed to be *stricter* than osmdroid's — but never looser, which is the direction that puts
     * the user in front of missing tiles. Today the two are equal for all four sources.
     */
    @Test
    fun `no basemap lets the user zoom past what its tile source declares`() {
        Basemap.entries.forEach { basemap ->
            val declared = tileSourceFor(basemap).maximumZoomLevel
            assertTrue(
                "${basemap.name} applies a ceiling of ${basemap.maxZoom} but its osmdroid source " +
                    "declares $declared. A ceiling above the source's own maximum zooms the user " +
                    "into tiles that do not exist.",
                basemap.maxZoom <= declared,
            )
        }
    }

    /**
     * Pins the actual ceilings, which is what makes the previous test meaningful: without this, an
     * osmdroid bump that raised every declared maximum would keep that test green while [Basemap]
     * quietly stopped matching the artifact it documents.
     */
    @Test
    fun `the declared zoom ceilings are the ones Basemap documents`() {
        val expected = mapOf(
            Basemap.USGS_TOPO to 15,
            Basemap.USGS_IMAGERY_TOPO to 15,
            Basemap.OPEN_TOPO_MAP to 17,
            Basemap.OSM_STANDARD to 19,
        )
        Basemap.entries.forEach { basemap ->
            assertEquals(
                "${basemap.name}: osmdroid's declared maximum zoom changed. Re-check the coverage " +
                    "and zoom evidence in Basemap's doc comment before adjusting this.",
                expected.getValue(basemap),
                tileSourceFor(basemap).maximumZoomLevel,
            )
        }
    }

    /**
     * USGS Topo really does stop lower than the OSM standard map. This is the difference the selector
     * has to tell the user about, so it is worth asserting as a relationship and not only as two
     * numbers — if a future osmdroid made them equal, the UI's zoom-ceiling line would still be
     * true but would no longer be telling anyone anything.
     */
    @Test
    fun `USGS Topo stops zooming in sooner than the OSM standard map`() {
        assertTrue(
            "USGS Topo (${Basemap.USGS_TOPO.maxZoom}) is expected to cap lower than " +
                "OpenStreetMap (${Basemap.OSM_STANDARD.maxZoom}).",
            Basemap.USGS_TOPO.maxZoom < Basemap.OSM_STANDARD.maxZoom,
        )
    }

    /**
     * The attribution check, and the one that answers a question this feature had to ask directly:
     * `CopyrightOverlay.draw` reads `getCopyrightNotice()` off the live tile source and **returns
     * without drawing anything** when that string is null or empty. So a source with an empty notice
     * ships a map with no attribution at all, and nothing about the build would say so.
     *
     * All four are non-empty today. The USGS pair return the bare string `"USGS"`, which is why
     * [Basemap.attribution] carries the fuller credit into the selector rather than relying on this.
     */
    @Test
    fun `every basemap supplies a non-empty on-map copyright notice`() {
        Basemap.entries.forEach { basemap ->
            val notice = tileSourceFor(basemap).copyrightNotice
            assertTrue(
                "${basemap.name} has no copyright notice, so osmdroid's CopyrightOverlay would " +
                    "draw nothing and the map would carry no attribution.",
                !notice.isNullOrEmpty(),
            )
        }
    }

    /**
     * The USGS sources use ArcGIS's `tile/{level}/{row}/{col}` ordering — z/**y**/x — not the z/x/y an
     * `XYTileSource` emits. Getting that backwards is the failure mode worth a test: transposed
     * coordinates return a perfectly valid tile from the wrong place on Earth, so the map looks like
     * it works. Asserting the built URL rather than trusting the factory, per CLAUDE.md's rule about
     * asserting real output.
     *
     * The tile is the one `scripts/verify-usgs-basemap.sh` fetches for the project's reference
     * location (Portland, Oregon), so the URL this test pins and the URL proven to serve a JPEG are
     * the same string.
     */
    @Test
    fun `the USGS tile URL uses ArcGIS row-column order`() {
        assertEquals(
            "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/12/1468/652",
            tileUrlFor(Basemap.USGS_TOPO),
        )
        assertEquals(
            "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryTopo/MapServer/tile/12/1468/652",
            tileUrlFor(Basemap.USGS_IMAGERY_TOPO),
        )
    }

    /**
     * The contrast case for the test above: the OSM standard source puts x before y, so the two
     * orderings are demonstrably different rather than assumed to be.
     */
    @Test
    fun `the OSM standard tile URL uses x-y order`() {
        assertEquals("https://tile.openstreetmap.org/12/652/1468.png", tileUrlFor(Basemap.OSM_STANDARD))
    }
}

/**
 * The URL [basemap] would fetch for the tile covering the project's reference location — Portland,
 * Oregon (45.326, -122.634) — at zoom 12. Note the argument order: `getTileIndex` takes zoom, **x**,
 * **y**, so the transposition this test is looking for happens inside `getTileURLString`, not here.
 */
private fun tileUrlFor(basemap: Basemap): String =
    (tileSourceFor(basemap) as OnlineTileSourceBase)
        .getTileURLString(MapTileIndex.getTileIndex(12, 652, 1468))

/**
 * Pinned for the same reason [com.forager.app.ui.availability.AvailabilityScreen]'s layout tests pin
 * it: the manifest's targetSdk is 37 and Robolectric 4.16.1 has no runtime for it.
 */
internal const val ROBOLECTRIC_SDK = 36
