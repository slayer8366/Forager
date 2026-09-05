package com.forager.app.domain

import com.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimateOfflineTileCountTest {

    private val portland = Region(lat = 45.5, lng = -122.6, radiusKm = 5)

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
     * owner's own latitude (Oregon City, ~45.36°N). **Expected values are literals computed
     * independently** (a separate slippy-map implementation summing zooms 10..14 over the same
     * equirectangular bounding box), not derived from the function under test; the 15 km case is
     * the region the owner downloaded, whose MapLibre status reported 480 required resources
     * (tiles plus the style and tileset JSON). With the ceiling reverted to MAX_ZOOM these read
     * 224, 1781 and 18696 — the 3.7× inflation that was refusing radii the budget actually fits.
     */
    @Test
    fun `the served-ceiling estimate matches the download's own enumeration at three radii spanning the slider`() {
        val oregonCity = { radiusKm: Int -> Region(lat = 45.357, lng = -122.607, radiusKm = radiusKm) }
        assertEquals(68, estimateServedOfflineTileCount(oregonCity(5)))
        assertEquals(485, estimateServedOfflineTileCount(oregonCity(15)))
        assertEquals(4772, estimateServedOfflineTileCount(oregonCity(50)))
    }

    @Test
    fun `a larger radius never estimates fewer tiles`() {
        val small = estimateOfflineTileCount(Region(lat = 45.0, lng = -122.0, radiusKm = 5), 10.0, 14.0)
        val large = estimateOfflineTileCount(Region(lat = 45.0, lng = -122.0, radiusKm = 15), 10.0, 14.0)

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
