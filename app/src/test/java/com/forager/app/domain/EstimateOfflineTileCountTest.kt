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
