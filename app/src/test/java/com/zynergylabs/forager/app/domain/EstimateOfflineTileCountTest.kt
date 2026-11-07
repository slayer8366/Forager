package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimateOfflineTileCountTest {

    private val portland = Region(lat = 45.5, lng = -121.19375, radiusKm = 5)

    @Test
    fun `roughly matches the design doc's own observed count for a 5km region at 45N`() {
        val estimate = estimateOfflineTileCount(portland, minZoom = 10.0, maxZoom = 14.0)

        // The design doc records 71 tiles actually observed for a 5km-radius region at 45N —
        // this is a real slippy-map grid count (not the same approximation), so it's asserted as a
        // ballpark rather than an exact match: tile-grid alignment shifts the true count by a tile
        // or two depending on exactly where the center falls within its cell at each zoom.
        assertTrue("expected roughly 71 tiles, was $estimate", estimate in 40..120)
    }

    /**
     * Tile-estimate dispatch: the served-ceiling estimate must agree with what a download actually
     * enumerates — three radii spanning the slider's range, including its 50 km maximum, at the
     * made-up point (see below). **Expected values are literals computed
     * independently** (a separate slippy-map implementation summing zooms 10..14 over the same
     * equirectangular bounding box), not derived from the function under test; the 15 km case is
     * the size of a region the owner downloaded, whose MapLibre status reported 480 required resources
     * (tiles plus the style and tileset JSON). With the ceiling reverted to MAX_ZOOM these read
     * 224, 1781 and 18696 — the 3.7× inflation that was refusing radii the budget actually fits.
     */
    /**
     * Two-data-corrections dispatch, Part B: SERVED_MAX_ZOOM rose from 14 to 15 once the worker's
     * zoom-15 overflow went live, and the slider's ceiling shrank from 50 km to 24 km to fit the
     * budget. Three radii spanning the new slider range, including its maximum, at the same
     * made-up point; literals re-derived independently in Python from the slippy-map definition
     * (docs/audits/2026-09-06-filter-and-tile-cost-prebuild-report.md, B2), not from this code.
     * The previous literals at ceiling 14 were 68 / 485 / 4772 for 5 / 15 / 50 km — 224 and 1781
     * are the same 5 and 15 km at ceiling 15, the cross-check figures the dispatch supplied.
     * Fails with SERVED_MAX_ZOOM reverted to 14.0 (68 / 485 / 1112).
     *
     * The point is made up (2026-11-06, replacing a real place): at every zoom from 10 to 16 it
     * gives the same tile count as the place it replaced, for each radius here and for 50 km, so
     * every figure above still holds.
     */
    @Test
    fun `the served-ceiling estimate matches the download's own enumeration at three radii spanning the slider`() {
        val madeUpPoint = { radiusKm: Int -> Region(lat = 45.357, lng = -121.5523125, radiusKm = radiusKm) }
        assertEquals(224, estimateServedOfflineTileCount(madeUpPoint(5)))
        assertEquals(1781, estimateServedOfflineTileCount(madeUpPoint(15)))
        assertEquals(4304, estimateServedOfflineTileCount(madeUpPoint(OfflineMapRepository.MAX_RADIUS_KM)))
        assertEquals(24, OfflineMapRepository.MAX_RADIUS_KM)
    }

    @Test
    fun `a larger radius never estimates fewer tiles`() {
        val small = estimateOfflineTileCount(Region(lat = 45.0, lng = -120.59375, radiusKm = 5), 10.0, 14.0)
        val large = estimateOfflineTileCount(Region(lat = 45.0, lng = -120.59375, radiusKm = 15), 10.0, 14.0)

        assertTrue(large >= small)
    }

    @Test
    fun `a wider zoom range never estimates fewer tiles`() {
        val narrow = estimateOfflineTileCount(portland, minZoom = 12.0, maxZoom = 14.0)
        val wide = estimateOfflineTileCount(portland, minZoom = 10.0, maxZoom = 14.0)

        assertTrue(wide >= narrow)
    }

    @Test
    fun `a single zoom level covering the whole world is exactly one tile`() {
        val wholeWorld = Region(lat = 0.0, lng = 0.0, radiusKm = 1)

        assertEquals(1, estimateOfflineTileCount(wholeWorld, minZoom = 0.0, maxZoom = 0.0))
    }

    @Test
    fun `a region crossing the antimeridian still produces a positive, finite estimate`() {
        val nearDateline = Region(lat = 45.0, lng = 179.9, radiusKm = 20)

        val estimate = estimateOfflineTileCount(nearDateline, minZoom = 10.0, maxZoom = 14.0)

        assertTrue(estimate > 0)
    }

    @Test
    fun `a region near the pole does not crash or produce a nonsensical count`() {
        val nearPole = Region(lat = 89.5, lng = 0.0, radiusKm = 5)

        val estimate = estimateOfflineTileCount(nearPole, minZoom = 10.0, maxZoom = 14.0)

        assertTrue(estimate > 0)
    }
}
