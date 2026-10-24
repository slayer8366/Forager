package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteEnd
import com.zynergylabs.forager.app.domain.routing.TrailRouteFailure
import com.zynergylabs.forager.app.domain.routing.TrailRouteResult
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import com.zynergylabs.forager.app.domain.routing.TrailSnapResult
import com.zynergylabs.forager.app.routing.RoutingTestData.ACROSS_FROM
import com.zynergylabs.forager.app.routing.RoutingTestData.ACROSS_TO
import com.zynergylabs.forager.app.routing.RoutingTestData.NO_TRAIL_NEAR
import com.zynergylabs.forager.app.routing.RoutingTestData.OFF_TRAIL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Both ways of finding the nearest trail (RECORD -1589, "Measure, then pick"), through the [com.zynergylabs.forager.app.domain.routing.TrailSnapper]
 * interface, on the real vendored BRouter and the Harriman fixture (dispatch 2026-10-23-09 Amendment 1, part 1).
 * Nothing is stubbed. Which method ships waits for the S22's timing; these show both give the same answer.
 */
class BRouterTrailSnapperTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logged = mutableListOf<Throwable>()
    private val errorLog = ErrorLog { _, _, error -> synchronized(logged) { logged += error } }

    private val router by lazy { BRouterTrailRouter(RoutingTestData.profileDir(tmp), errorLog) }
    private val data: File by lazy { RoutingTestData.segments(tmp) }

    private fun snap(method: TrailSnapMethod, point: LatLng, toward: LatLng = ACROSS_TO) =
        runBlocking { BRouterTrailSnapper(router, method).nearestTrail(point, toward, data) }

    private fun trailPoint(result: TrailSnapResult): LatLng {
        assertTrue("a point on the network, not $result", result is TrailSnapResult.OnNetwork)
        return (result as TrailSnapResult.OnNetwork).trailPoint
    }

    @Test
    fun `a point on a trail is its own nearest trail point, by both methods`() {
        for (method in TrailSnapMethod.entries) {
            val metres = GeoDistance.metersBetween(ACROSS_FROM, trailPoint(snap(method, ACROSS_FROM)))
            assertTrue("$method: $metres m from a trail node", metres < 1.0)
        }
    }

    @Test
    fun `a point off the trail is matched to the same trail point 34 m away by both methods`() {
        val bySnap = trailPoint(snap(TrailSnapMethod.SNAP, OFF_TRAIL))
        val byRoute = trailPoint(snap(TrailSnapMethod.FULL_ROUTE, OFF_TRAIL))

        assertEquals("the two methods agree", bySnap, byRoute)
        assertEquals(34.3, GeoDistance.metersBetween(OFF_TRAIL, bySnap), 0.5)
    }

    @Test
    fun `the snap's point is where the router's own route from that point starts`() {
        val route = runBlocking { router.route(OFF_TRAIL, ACROSS_TO, data) }

        assertTrue("$route", route is TrailRouteResult.Found)
        assertEquals((route as TrailRouteResult.Found).route.points.first(), trailPoint(snap(TrailSnapMethod.SNAP, OFF_TRAIL)))
    }

    @Test
    fun `a point with no trail in catching range fails as outside the data for its own end, by both methods`() {
        for (method in TrailSnapMethod.entries) {
            assertEquals("$method", TrailSnapResult.Failed(TrailRouteFailure.EndOutsideData(RouteEnd.START)), snap(method, NO_TRAIL_NEAR))
        }
    }

    @Test
    fun `the snap does not read the point it is routed toward, and the full route does`() {
        assertTrue(snap(TrailSnapMethod.SNAP, OFF_TRAIL, toward = NO_TRAIL_NEAR) is TrailSnapResult.OnNetwork)
        assertEquals(TrailSnapResult.Failed(TrailRouteFailure.EndOutsideData(RouteEnd.END)), snap(TrailSnapMethod.FULL_ROUTE, OFF_TRAIL, toward = NO_TRAIL_NEAR))
    }

    @Test
    fun `a snap logs nothing, where the router's own route of one point would be a logged engine failure`() {
        trailPoint(snap(TrailSnapMethod.SNAP, OFF_TRAIL))
        assertEquals(emptyList<Throwable>(), logged.toList())

        val oneCall = runBlocking { router.route(OFF_TRAIL, OFF_TRAIL, data) }
        assertTrue("a route of one point is no route: $oneCall", (oneCall as TrailRouteResult.Failed).failure is TrailRouteFailure.EngineFailed)
        assertEquals(1, logged.size)
    }
}
