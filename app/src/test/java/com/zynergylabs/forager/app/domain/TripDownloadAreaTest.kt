package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.route.StraightReason
import com.zynergylabs.forager.app.domain.routedata.contains
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-11-05-08, Amendment 1, A4: where a trip's Download Maps opens Offline Maps. Every expected radius below is worked
 * out by hand from the spans (a degree of latitude is 111.19 km; of longitude, that times the cosine of the latitude), not read
 * back from the function. "Inside" is the downloaded rectangle, [GeoDistance.boundingBox]. Made-up points.
 */
class TripDownloadAreaTest {

    private fun stop(name: String, lat: Double, lng: Double) = RouteStop(name, LatLng(lat, lng))

    private fun straight(a: RouteStop, b: RouteStop) =
        RouteLeg.Straight(listOf(a.position, b.position), GeoDistance.metersBetween(a.position, b.position), StraightReason.NO_REGION_COVERS)

    /** A trail leg from [a] to [b] through [via], the whole line being the trail part. */
    private fun trail(a: RouteStop, b: RouteStop, vararg via: LatLng): RouteLeg.Trail {
        val line = listOf(a.position) + via + b.position
        return RouteLeg.Trail(line, 1_000.0, trailFrom = 0, trailTo = line.lastIndex)
    }

    private fun routeTrip(vararg legs: RouteLeg, stops: List<RouteStop>) =
        PlannedTrip("route", "Test route", stops.first().position, null, PlottedRoute(stops, legs.toList()))

    private fun around(trip: PlannedTrip): TripDownloadArea.AroundRoute {
        val area = tripDownloadArea(trip)
        assertTrue("a route trip opens around its route, was $area", area is TripDownloadArea.AroundRoute)
        return area as TripDownloadArea.AroundRoute
    }

    private fun line(trip: PlannedTrip) = trip.route!!.legs.flatMap { it.line }

    private fun outsideAt(trip: PlannedTrip, center: LatLng, radiusKm: Int) = line(trip).filterNot { GeoDistance.boundingBox(center, radiusKm).contains(it) }

    /** Every point of the line inside the area at its radius, and one km smaller leaving one out (unless at 1 km). */
    private fun assertSmallestCovering(trip: PlannedTrip, area: TripDownloadArea.AroundRoute) {
        assertEquals("every point of the line is inside the area that downloads", emptyList<LatLng>(), outsideAt(trip, area.center, area.radiusKm))
        if (area.radiusKm > 1) {
            assertTrue("one km smaller leaves a point of the line out", outsideAt(trip, area.center, area.radiusKm - 1).isNotEmpty())
        }
    }

    @Test
    fun `a plain trip opens on its own point and leaves the radius to the page`() {
        val trip = PlannedTrip("plain", "Test trip", LatLng(40.0, -100.0), null)
        assertEquals(TripDownloadArea.AtTrip(LatLng(40.0, -100.0)), tripDownloadArea(trip))
    }

    /**
     * The whole line, not the stops (A4). The stops are 0.02 degrees apart, which on their own need 2 km; the trail between
     * them runs 0.08 degrees south and 0.10 north of the first, so the line spans 0.20 degrees of latitude: half is 11.12 km,
     * so 12 km, centred on the line's middle rather than the stops'.
     */
    @Test
    fun `a route whose trail bends past its stops opens on the line's middle at the radius the line needs`() {
        val a = stop("A", 40.00, -100.0)
        val b = stop("B", 40.02, -100.0)
        val trip = routeTrip(trail(a, b, LatLng(39.92, -100.0), LatLng(40.12, -100.0)), stops = listOf(a, b))

        val area = around(trip)

        assertEquals("the middle of the line's latitudes", 40.02, area.center.lat, 1e-9)
        assertEquals("the middle of the line's longitudes", -100.0, area.center.lng, 1e-9)
        assertEquals("the radius the line needs", 12, area.radiusKm)
        assertTrue(area.lineCovered)
        assertFalse(area.stopsOutside)
        assertSmallestCovering(trip, area)

        val stopsOnly = around(routeTrip(straight(a, b), stops = listOf(a, b)))
        assertEquals("the stops alone would need only 2 km", 2, stopsOnly.radiusKm)
    }

    /** East-west: 0.4 degrees of longitude at 40 degrees north is 0.2 each way, 17.04 km, so 18 km. */
    @Test
    fun `an east-west route needs the longitude span at its own latitude`() {
        val a = stop("West", 40.0, -100.2)
        val b = stop("East", 40.0, -99.8)
        val trip = routeTrip(straight(a, b), stops = listOf(a, b))

        val area = around(trip)

        assertEquals(LatLng(40.0, -100.0).lat, area.center.lat, 1e-9)
        assertEquals(-100.0, area.center.lng, 1e-9)
        assertEquals(18, area.radiusKm)
        assertSmallestCovering(trip, area)
    }

    @Test
    fun `a route a hundred metres long opens at the slider's smallest radius, 1 km`() {
        val a = stop("A", 40.0, -100.0)
        val b = stop("B", 40.0009, -100.0)
        val trip = routeTrip(straight(a, b), stops = listOf(a, b))

        val area = around(trip)

        assertEquals(1, area.radiusKm)
        assertTrue(area.lineCovered)
        assertSmallestCovering(trip, area)
    }

    /** A3: stops 0.6 degrees apart, 33.4 km each way from the middle, so the page opens at 24 km and both stops are outside it. */
    @Test
    fun `a route bigger than one download opens at 24 km in its middle, with stops outside`() {
        val a = stop("A", 40.0, -100.0)
        val b = stop("B", 40.6, -100.0)
        val trip = routeTrip(straight(a, b), stops = listOf(a, b))

        val area = around(trip)

        assertEquals(24, area.radiusKm)
        assertEquals(40.3, area.center.lat, 1e-9)
        assertFalse(area.lineCovered)
        assertTrue("a stop is outside at 24 km", area.stopsOutside)
        val box = GeoDistance.boundingBox(area.center, 24)
        assertFalse(box.contains(a.position))
        assertFalse(box.contains(b.position))
    }

    /**
     * C2: every stop inside at 24 km but part of the line outside. The stops are 0.1 degrees apart; the trail runs 0.25 south
     * of the first and 0.25 north of the second, 0.3 each way from the middle (33.4 km). At 24 km the area reaches 0.216 each
     * way, which holds both stops and neither bend.
     */
    @Test
    fun `a route whose stops fit at 24 km but whose line doesn't opens at 24 km with no stop outside`() {
        val a = stop("A", 40.0, -100.0)
        val b = stop("B", 40.1, -100.0)
        val trip = routeTrip(trail(a, b, LatLng(39.75, -100.0), LatLng(40.35, -100.0)), stops = listOf(a, b))

        val area = around(trip)

        assertEquals(24, area.radiusKm)
        assertEquals(40.05, area.center.lat, 1e-9)
        assertFalse("the line is not covered", area.lineCovered)
        assertFalse("no stop is outside", area.stopsOutside)
        assertEquals(listOf(LatLng(39.75, -100.0), LatLng(40.35, -100.0)), outsideAt(trip, area.center, 24))
    }

    /** A to B and back to A (allowed, Amendment 1 of dispatch 2026-10-30-02), on a diagonal, with a trail leg out and a straight one back. */
    @Test
    fun `an out-and-back route on a diagonal is covered by the smallest area that holds its line`() {
        val a = stop("A", 40.0, -100.0)
        val b = stop("B", 40.05, -99.94)
        val trip = routeTrip(
            trail(a, b, LatLng(40.01, -100.03), LatLng(40.06, -99.99)),
            straight(b, a),
            stops = listOf(a, b, a),
        )

        val area = around(trip)

        assertTrue(area.lineCovered)
        assertFalse(area.stopsOutside)
        assertSmallestCovering(trip, area)
    }

    /** Three stops, each leg its own kind, the second bending east of every stop. */
    @Test
    fun `a three-stop route is covered at its smallest radius, the bend included`() {
        val a = stop("A", 40.0, -100.0)
        val b = stop("B", 40.03, -100.01)
        val c = stop("C", 40.06, -99.99)
        val bend = LatLng(40.045, -99.92)
        val trip = routeTrip(straight(a, b), trail(b, c, bend), stops = listOf(a, b, c))

        val area = around(trip)

        assertTrue(area.lineCovered)
        assertSmallestCovering(trip, area)
        assertTrue("the bend is what sets the radius", GeoDistance.boundingBox(area.center, area.radiusKm - 1).let { !it.contains(bend) })
    }
}
