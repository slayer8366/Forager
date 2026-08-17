package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test
    fun `bounding box is centred on the search point`() {
        val center = LatLng(45.326, -122.634)
        val box = GeoDistance.boundingBox(center, radiusKm = 15)

        assertEquals(center.lat, (box.north + box.south) / 2.0, 1e-9)
        assertEquals(center.lng, (box.east + box.west) / 2.0, 1e-9)
        assertTrue(box.north > center.lat)
        assertTrue(box.south < center.lat)
        assertTrue(box.east > center.lng)
        assertTrue(box.west < center.lng)
    }

    /**
     * Round-trips the box's north/south edges back through [GeoDistance.metersBetween]: the
     * meridian distance from the centre to either edge should equal the requested radius, since a
     * degree of latitude is constant regardless of where on Earth it's measured.
     */
    @Test
    fun `bounding box north-south span matches the requested radius in real metres`() {
        val center = LatLng(45.326, -122.634)
        val radiusKm = 20
        val box = GeoDistance.boundingBox(center, radiusKm)

        val toNorthEdge = GeoDistance.metersBetween(center, LatLng(box.north, center.lng))
        val toSouthEdge = GeoDistance.metersBetween(center, LatLng(box.south, center.lng))

        assertEquals(radiusKm * 1_000.0, toNorthEdge, 1.0)
        assertEquals(radiusKm * 1_000.0, toSouthEdge, 1.0)
    }

    /**
     * The reason a separate longitude calculation exists at all: the same radius must produce a
     * wider degree-span at a higher latitude, because a degree of longitude is shorter there. A
     * naive equal-degree box (the bug this helper exists to avoid) would be under-wide nearer the
     * poles and over-wide nearer the equator for the same real-world radius.
     */
    @Test
    fun `bounding box east-west degree span widens toward the poles for the same radius`() {
        val atEquator = GeoDistance.boundingBox(LatLng(0.0, 0.0), radiusKm = 15)
        val atSixtyNorth = GeoDistance.boundingBox(LatLng(60.0, 0.0), radiusKm = 15)

        val equatorLngSpan = atEquator.east - atEquator.west
        val sixtyNorthLngSpan = atSixtyNorth.east - atSixtyNorth.west

        assertTrue(
            "expected the box at 60°N ($sixtyNorthLngSpan°) to be wider in degrees than at the " +
                "equator ($equatorLngSpan°) for the same real-world radius",
            sixtyNorthLngSpan > equatorLngSpan,
        )
        // The latitude span, by contrast, does not depend on latitude at all.
        assertEquals(atEquator.north - atEquator.south, atSixtyNorth.north - atSixtyNorth.south, 1e-6)
    }

    @Test
    fun `zero radius collapses the box onto the centre point`() {
        val center = LatLng(45.326, -122.634)
        val box = GeoDistance.boundingBox(center, radiusKm = 0)

        assertEquals(center.lat, box.north, 1e-9)
        assertEquals(center.lat, box.south, 1e-9)
        assertEquals(center.lng, box.east, 1e-9)
        assertEquals(center.lng, box.west, 1e-9)
    }

    @Test
    fun `a negative radius is rejected rather than producing an inverted box`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeoDistance.boundingBox(LatLng(45.0, 0.0), radiusKm = -1)
        }
    }

    /**
     * A centre near a pole must not blow the box out to the whole globe or produce NaN/Infinity —
     * the real failure mode of dividing by `cos(latitude)` with no floor as latitude approaches 90°.
     */
    @Test
    fun `a search near the pole produces a finite, clamped box instead of blowing up`() {
        val nearNorthPole = GeoDistance.boundingBox(LatLng(89.95, 10.0), radiusKm = 10)

        assertEquals(90.0, nearNorthPole.north, 1e-9)
        assertTrue(nearNorthPole.south.isFinite())
        assertTrue(nearNorthPole.east.isFinite())
        assertTrue(nearNorthPole.west.isFinite())
        assertTrue(nearNorthPole.east in -180.0..180.0)
        assertTrue(nearNorthPole.west in -180.0..180.0)
    }

    /** Crossing the antimeridian must wrap, not report an east edge past 180°. */
    @Test
    fun `bounding box east-west edges wrap across the antimeridian`() {
        val box = GeoDistance.boundingBox(LatLng(0.0, 179.9), radiusKm = 50)

        assertTrue("east should have wrapped past 180° to a negative longitude, was ${box.east}", box.east < 0.0)
        assertTrue(box.east in -180.0..180.0)
        assertTrue(box.west in -180.0..180.0)
        // The box crosses the seam: numerically, its "east" edge is a smaller value than "west".
        assertTrue(box.east < box.west)
    }
}
