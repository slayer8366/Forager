package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoDistanceTest {

    @Test
    fun `identical points are zero metres apart`() {
        val point = LatLng(45.5, -122.6)

        assertEquals(0.0, GeoDistance.metersBetween(point, point), 1e-9)
    }

    @Test
    fun `one degree of latitude is about 111 kilometres regardless of longitude`() {
        val atPrimeMeridian = GeoDistance.metersBetween(LatLng(0.0, 0.0), LatLng(1.0, 0.0))
        val farEast = GeoDistance.metersBetween(LatLng(59.0, 140.0), LatLng(60.0, 140.0))

        assertEquals(111_194.9, atPrimeMeridian, 1.0)
        assertEquals(111_194.9, farEast, 1.0)
    }

    /**
     * The core reason this helper exists: a degree of longitude shrinks with latitude, so
     * degree-space arithmetic is not distance. At 60°N one degree of longitude is roughly half
     * what it is at the equator.
     */
    @Test
    fun `one degree of longitude shrinks with latitude`() {
        val atEquator = GeoDistance.metersBetween(LatLng(0.0, 0.0), LatLng(0.0, 1.0))
        val atSixtyNorth = GeoDistance.metersBetween(LatLng(60.0, 0.0), LatLng(60.0, 1.0))

        assertEquals(111_194.9, atEquator, 1.0)
        assertEquals(55_597.5, atSixtyNorth, 1.0)
    }

    @Test
    fun `distance is symmetric`() {
        val a = LatLng(51.5, -0.12)
        val b = LatLng(48.85, 2.35)

        assertEquals(GeoDistance.metersBetween(a, b), GeoDistance.metersBetween(b, a), 1e-6)
    }

    /** London to Paris is ~343 km great-circle; a known external reference, not a self-check. */
    @Test
    fun `matches a known great-circle distance between two cities`() {
        val london = LatLng(51.5074, -0.1278)
        val paris = LatLng(48.8566, 2.3522)

        assertEquals(343_500.0, GeoDistance.metersBetween(london, paris), 1_000.0)
    }

    /** Crossing the antimeridian must be a short hop, not a trip the long way round the globe. */
    @Test
    fun `points either side of the antimeridian are close, not half a world apart`() {
        val west = LatLng(0.0, 179.99)
        val east = LatLng(0.0, -179.99)

        val meters = GeoDistance.metersBetween(west, east)

        assertEquals(2_223.9, meters, 1.0)
        assertTrue("expected a short hop, got $meters m", meters < 5_000)
    }
}
