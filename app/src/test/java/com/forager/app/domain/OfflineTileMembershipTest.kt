package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineTileMembershipTest {

    private val portland = Region(lat = 45.5, lng = -122.6, radiusKm = 10)

    @Test
    fun `a region's own center point is within its own tile footprint`() {
        assertTrue(isCoordinateWithinRegionTiles(LatLng(45.5, -122.6), portland, zoom = 15))
    }

    @Test
    fun `a point a fraction of a degree from center, well inside a 10km radius, is within the footprint`() {
        // Roughly 3km east of center at this latitude — comfortably inside the 10km radius.
        assertTrue(isCoordinateWithinRegionTiles(LatLng(45.5, -122.56), portland, zoom = 15))
    }

    @Test
    fun `a point far outside the region is not within its footprint`() {
        // Seattle, ~230km north — nowhere near a 10km-radius region centred on Portland.
        assertFalse(isCoordinateWithinRegionTiles(LatLng(47.6, -122.3), portland, zoom = 15))
    }

    @Test
    fun `a point just outside one region's footprint is still correctly excluded, not trivially true`() {
        val tinyRegion = Region(lat = 0.0, lng = 0.0, radiusKm = 1)
        // ~1.1 degrees of longitude at the equator is well over 100km — far outside a 1km-radius region.
        assertFalse(isCoordinateWithinRegionTiles(LatLng(0.0, 1.1), tinyRegion, zoom = 15))
    }

    @Test
    fun `a region crossing the antimeridian still correctly includes a point just past 180 degrees`() {
        val nearDateline = Region(lat = 45.0, lng = 179.9, radiusKm = 20)

        // -179.95 is the same side of the globe as 179.9 once the antimeridian is crossed — well
        // within a 20km radius of it.
        assertTrue(isCoordinateWithinRegionTiles(LatLng(45.0, -179.95), nearDateline, zoom = 12))
    }

    @Test
    fun `a region crossing the antimeridian correctly excludes a point on the opposite side of the globe`() {
        val nearDateline = Region(lat = 45.0, lng = 179.9, radiusKm = 20)

        assertFalse(isCoordinateWithinRegionTiles(LatLng(45.0, 0.0), nearDateline, zoom = 12))
    }

    @Test
    fun `membership at a coarser zoom is at least as inclusive as at a finer zoom for the same point`() {
        // A coarser zoom's tiles cover more ground, so anything within the fine-zoom footprint must
        // also be within the coarse one — never the reverse.
        val nearEdge = LatLng(45.5, -122.45)

        val fineZoomResult = isCoordinateWithinRegionTiles(nearEdge, portland, zoom = 15)
        val coarseZoomResult = isCoordinateWithinRegionTiles(nearEdge, portland, zoom = 10)

        assertTrue(!fineZoomResult || coarseZoomResult)
    }
}
