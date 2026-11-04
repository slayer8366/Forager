package com.zynergylabs.forager.app.domain.routenav

import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.LOST_AFTER_MILLIS
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.STALE_AFTER_MILLIS
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.route.StraightReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-11-03-03 (A4, A5): the route navigation's progress, headless (B2). A route due north in three stops, Creek, Ridge
 * and Summit, 200 m apart, each leg a straight two-point line, so every expected distance is worked out from the stops alone with
 * [GeoDistance], never read back from the code under test. One more stop, Spring, sits 10 m past Ridge on a second route, closer
 * than the arrival radius, for the one-stop-per-fix rule.
 */
class RouteNavigationProgressTest {

    private val now = 1_000_000_000L

    /** [metres] north of the route's start, and [eastMetres] east of it, flattened about the start as the code under test does. */
    private fun north(metres: Double, eastMetres: Double = 0.0): LatLng {
        val perDegree = GeoDistance.metersPerDegree(START.lat)
        return LatLng(START.lat + metres / perDegree.lat, START.lng + eastMetres / perDegree.lng)
    }

    private val creek = RouteStop("Creek", north(0.0))
    private val ridge = RouteStop("Ridge", north(200.0))
    private val summit = RouteStop("Summit", north(400.0))
    private val spring = RouteStop("Spring", north(210.0))

    private fun leg(from: RouteStop, to: RouteStop) =
        RouteLeg.Straight(listOf(from.position, to.position), GeoDistance.metersBetween(from.position, to.position), StraightReason.NO_REGION_COVERS)

    private val route = PlottedRoute(listOf(creek, ridge, summit), listOf(leg(creek, ridge), leg(ridge, summit)))
    private val closeStops = PlottedRoute(listOf(creek, ridge, spring), listOf(leg(creek, ridge), leg(ridge, spring)))

    private fun navigation(next: Int) = RouteNavigation("trip-1", next, resumesReturn = false)

    private fun fix(at: LatLng, provider: FixProvider = FixProvider.GPS, accuracy: Float? = 3.79f, ageMillis: Long = 0L) =
        LocationFix.Update(at.lat, at.lng, altitude = null, accuracyMeters = accuracy, timestampEpochMillis = now - ageMillis, provider = provider)

    @Test
    fun `a navigation starts toward the first stop, which no leg reaches`() {
        assertEquals(RouteNavigationTarget.ToStop(0, creek, null, 3), routeNavigationTarget(route, 0))
        assertEquals(RouteStopDistance.ToFirstStop, distanceToNextStop(route, 0, north(-500.0)))
    }

    @Test
    fun `each later stop is reached by the leg that ends at it`() {
        assertEquals(RouteNavigationTarget.ToStop(1, ridge, route.legs[0], 3), routeNavigationTarget(route, 1))
        assertEquals(RouteNavigationTarget.ToStop(2, summit, route.legs[1], 3), routeNavigationTarget(route, 2))
    }

    @Test
    fun `past the last stop it is finished, and beyond that it is out of range, never a stop`() {
        assertEquals(RouteNavigationTarget.Finished, routeNavigationTarget(route, 3))
        assertEquals(RouteNavigationTarget.OutOfRange(4, 3), routeNavigationTarget(route, 4))
        assertEquals(null, distanceToNextStop(route, 3, north(400.0)))
    }

    @Test
    fun `a GPS fix at the next stop moves on to the one after it`() {
        val step = nextRouteNavigationStep(navigation(1), route, fix(north(195.0)), now)
        assertEquals(RouteNavigationStep.Reached(1, navigation(2)), step)
    }

    @Test
    fun `already at the first stop, the first fix moves straight on to the second`() {
        assertEquals(RouteNavigationStep.Reached(0, navigation(1)), nextRouteNavigationStep(navigation(0), route, fix(north(3.0)), now))
    }

    @Test
    fun `the last stop reached finishes the route`() {
        val step = nextRouteNavigationStep(navigation(2), route, fix(north(400.0)), now)
        assertEquals(RouteNavigationStep.Reached(2, navigation(3)), step)
        assertEquals(RouteNavigationTarget.Finished, routeNavigationTarget(route, (step as RouteNavigationStep.Reached).next.nextStopIndex))
    }

    @Test
    fun `finished, a fix moves nothing`() {
        assertEquals(RouteNavigationStep.Stays, nextRouteNavigationStep(navigation(3), route, fix(north(400.0)), now))
    }

    @Test
    fun `outside the arrival radius nothing moves`() {
        // hasArrived: twice 3.79 m is under its 15 m floor, so 15 m is the radius; 20 m short of Ridge is outside it.
        assertEquals(RouteNavigationStep.Stays, nextRouteNavigationStep(navigation(1), route, fix(north(180.0)), now))
    }

    @Test
    fun `the radius widens with a poor fix, as the waypoint's Arrived does`() {
        // Twice 12 m is 24 m: 20 m short of Ridge is inside it.
        assertEquals(RouteNavigationStep.Reached(1, navigation(2)), nextRouteNavigationStep(navigation(1), route, fix(north(180.0), accuracy = 12f), now))
    }

    @Test
    fun `a network fix at the stop moves nothing, nor one whose provider is unknown`() {
        assertEquals(RouteNavigationStep.Stays, nextRouteNavigationStep(navigation(1), route, fix(north(200.0), provider = FixProvider.NETWORK), now))
        assertEquals(RouteNavigationStep.Stays, nextRouteNavigationStep(navigation(1), route, fix(north(200.0), provider = FixProvider.UNKNOWN), now))
    }

    @Test
    fun `a lost fix at the stop moves nothing, a stale one still does`() {
        assertEquals(RouteNavigationStep.Stays, nextRouteNavigationStep(navigation(1), route, fix(north(200.0), ageMillis = LOST_AFTER_MILLIS), now))
        assertEquals(RouteNavigationStep.Reached(1, navigation(2)), nextRouteNavigationStep(navigation(1), route, fix(north(200.0), ageMillis = STALE_AFTER_MILLIS), now))
    }

    @Test
    fun `reaching a later stop early does nothing`() {
        // Headed to Ridge, standing on Summit.
        assertEquals(RouteNavigationStep.Stays, nextRouteNavigationStep(navigation(1), route, fix(north(400.0)), now))
    }

    @Test
    fun `two stops inside one radius move on one per fix`() {
        // Ridge and Spring are 10 m apart; a fix 5 m past Ridge is within 15 m of both.
        val here = fix(north(205.0))
        val first = nextRouteNavigationStep(navigation(1), closeStops, here, now)
        assertEquals("the first fix reaches Ridge only", RouteNavigationStep.Reached(1, navigation(2)), first)
        val second = nextRouteNavigationStep((first as RouteNavigationStep.Reached).next, closeStops, here, now)
        assertEquals("the next fix reaches Spring", RouteNavigationStep.Reached(2, navigation(3)), second)
    }

    @Test
    fun `on the leg, the distance is along it to the next stop, and drops as the walker goes`() {
        val at50 = distanceToNextStop(route, 1, north(50.0)) as RouteStopDistance.AlongRoute
        val at150 = distanceToNextStop(route, 1, north(150.0)) as RouteStopDistance.AlongRoute
        assertEquals(GeoDistance.metersBetween(north(50.0), ridge.position), at50.meters, 0.5)
        assertEquals(GeoDistance.metersBetween(north(150.0), ridge.position), at150.meters, 0.5)
        assertTrue(at150.meters < at50.meters)
    }

    @Test
    fun `after a stop the distance starts again from the next leg`() {
        val onSecondLeg = distanceToNextStop(route, 2, north(210.0)) as RouteStopDistance.AlongRoute
        assertEquals(GeoDistance.metersBetween(north(210.0), summit.position), onSecondLeg.meters, 0.5)
    }

    @Test
    fun `along a bent leg the distance follows the line, not the straight way`() {
        // Creek to a point 100 m north and 100 m east, then to Ridge: 2 x 141 m along, 200 m straight.
        val bend = north(100.0, eastMetres = 100.0)
        val bent = RouteLeg.Straight(listOf(creek.position, bend, ridge.position), 0.0, StraightReason.NO_REGION_COVERS)
        val bentRoute = PlottedRoute(listOf(creek, ridge), listOf(bent))
        val along = distanceToNextStop(bentRoute, 1, creek.position) as RouteStopDistance.AlongRoute
        val expected = GeoDistance.metersBetween(creek.position, bend) + GeoDistance.metersBetween(bend, ridge.position)
        assertEquals(expected, along.meters, 0.5)
    }

    @Test
    fun `within 50 m of the leg it stays along the route`() {
        val beside = north(100.0, eastMetres = 45.0)
        val distance = distanceToNextStop(route, 1, beside)
        assertEquals(RouteStopDistance.AlongRoute::class, distance!!::class)
        assertEquals(GeoDistance.metersBetween(north(100.0), ridge.position), (distance as RouteStopDistance.AlongRoute).meters, 0.5)
    }

    @Test
    fun `more than 50 m off the leg it is straight to the next stop`() {
        val off = north(100.0, eastMetres = 60.0)
        assertEquals(RouteStopDistance.StraightLine(GeoDistance.metersBetween(off, ridge.position)), distanceToNextStop(route, 1, off))
    }

    @Test
    fun `off the leg past its end counts from the stop, so the switch holds there too`() {
        // 60 m beyond Summit, to the north: off the second leg's end by 60 m.
        val beyond = north(460.0)
        assertEquals(RouteStopDistance.StraightLine(GeoDistance.metersBetween(beyond, summit.position)), distanceToNextStop(route, 2, beyond))
    }

    private companion object {
        val START = LatLng(45.326, -122.634)
    }
}
