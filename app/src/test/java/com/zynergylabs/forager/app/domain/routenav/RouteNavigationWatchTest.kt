package com.zynergylabs.forager.app.domain.routenav

import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.route.StraightReason
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Dispatch 2026-11-03-03, A8: the watch the background service feeds. Fixes go in through [RouteNavigationWatch.onFix], its real
 * entry point; what comes out is read from its [RouteNavigationWatch.state], the kept progress and the buzzes counted.
 */
class RouteNavigationWatchTest {

    private val now = 2_000_000_000L
    private val start = LatLng(45.326, -122.634)

    private fun north(metres: Double) = LatLng(start.lat + metres / GeoDistance.metersPerDegree(start.lat).lat, start.lng)

    private val creek = RouteStop("Creek", north(0.0))
    private val ridge = RouteStop("Ridge", north(200.0))
    private val summit = RouteStop("Summit", north(400.0))
    private fun leg(a: RouteStop, b: RouteStop) = RouteLeg.Straight(listOf(a.position, b.position), 200.0, StraightReason.NO_REGION_COVERS)
    private val route = PlottedRoute(listOf(creek, ridge, summit), listOf(leg(creek, ridge), leg(ridge, summit)))

    private fun gps(at: LatLng, provider: FixProvider = FixProvider.GPS) = LocationFix.Update(at.lat, at.lng, null, 3.79f, now, provider)

    private class Kept : RouteNavigationRepository {
        val progress = mutableMapOf<String, Int>()
        var failWrites = false
        override suspend fun getCurrent(): Result<RouteNavigationCurrent?> = Result.success(null)
        override suspend fun setCurrent(current: RouteNavigationCurrent?): Result<Unit> = Result.success(Unit)
        override suspend fun getProgress(tripId: String): Result<Int?> = Result.success(progress[tripId])
        override suspend fun setProgress(tripId: String, nextStopIndex: Int?): Result<Unit> {
            if (failWrites) return Result.failure(IllegalStateException("disk full"))
            if (nextStopIndex == null) progress.remove(tripId) else progress[tripId] = nextStopIndex
            return Result.success(Unit)
        }
    }

    private val scope = TestScope()
    private val kept = Kept()
    private var buzzes = 0
    private val logged = mutableListOf<String>()
    private val watch = RouteNavigationWatch(kept, { buzzes++ }, scope, { _, message, _ -> logged += message })

    private fun begin(next: Int = 0) = watch.begin(RouteNavigation("trip-1", next, resumesReturn = false), "Ridge loop", route)

    @Test
    fun `nothing is watched until a navigation begins, and a fix then does nothing`() {
        watch.onFix(gps(north(0.0)), now)
        scope.runCurrent()
        assertNull(watch.state.value.navigation)
        assertEquals(0, buzzes)
    }

    @Test
    fun `a stop reached moves on, says which, keeps the route's next stop and buzzes once`() {
        begin(next = 1)
        watch.onFix(gps(north(198.0)), now)
        scope.runCurrent()

        assertEquals(RouteNavigation("trip-1", 2, resumesReturn = false), watch.state.value.navigation)
        assertEquals(ReachedStop(1, "Ridge", now), watch.state.value.lastReached)
        assertEquals(mapOf("trip-1" to 2), kept.progress)
        assertEquals(1, buzzes)
    }

    @Test
    fun `a fix that reaches nothing buzzes nothing and keeps nothing`() {
        begin(next = 1)
        watch.onFix(gps(north(100.0)), now)
        scope.runCurrent()

        assertEquals(1, watch.state.value.navigation?.nextStopIndex)
        assertNull(watch.state.value.lastReached)
        assertEquals(emptyMap<String, Int>(), kept.progress)
        assertEquals(0, buzzes)
    }

    @Test
    fun `a network fix at the stop moves nothing`() {
        begin(next = 1)
        watch.onFix(gps(north(200.0), FixProvider.NETWORK), now)
        scope.runCurrent()
        assertEquals(1, watch.state.value.navigation?.nextStopIndex)
        assertEquals(0, buzzes)
    }

    @Test
    fun `the last stop reached finishes the route and clears its kept next stop`() {
        kept.progress["trip-1"] = 2
        begin(next = 2)
        watch.onFix(gps(north(400.0)), now)
        scope.runCurrent()

        assertEquals(3, watch.state.value.navigation?.nextStopIndex)
        assertEquals(RouteNavigationTarget.Finished, routeNavigationTarget(route, 3))
        assertEquals("finished, the route starts over next time", emptyMap<String, Int>(), kept.progress)
        assertEquals(1, buzzes)
    }

    @Test
    fun `walking the whole route reaches each stop in turn, one per fix`() {
        begin(next = 0)
        listOf(0.0, 100.0, 200.0, 300.0, 400.0, 400.0).forEach { watch.onFix(gps(north(it)), now) }
        scope.runCurrent()
        assertEquals(3, watch.state.value.navigation?.nextStopIndex)
        assertEquals(ReachedStop(2, "Summit", now), watch.state.value.lastReached)
        assertEquals(3, buzzes)
    }

    @Test
    fun `ending the watch keeps what the route kept`() {
        begin(next = 1)
        watch.onFix(gps(north(200.0)), now)
        scope.runCurrent()
        watch.end()
        watch.onFix(gps(north(400.0)), now)
        scope.runCurrent()

        assertNull(watch.state.value.navigation)
        assertEquals(mapOf("trip-1" to 2), kept.progress)
        assertEquals(1, buzzes)
    }

    @Test
    fun `a failed write is logged and the navigation carries on`() {
        kept.failWrites = true
        begin(next = 1)
        watch.onFix(gps(north(200.0)), now)
        scope.runCurrent()

        assertEquals(2, watch.state.value.navigation?.nextStopIndex)
        assertEquals(listOf("Couldn't keep route 'trip-1''s next stop; Navigate will not carry on from it."), logged)
    }

    @Test
    fun `a buzz that throws is logged and the stop still counts`() {
        val throwing = RouteNavigationWatch(kept, { error("no vibrator") }, scope, { _, message, _ -> logged += message })
        throwing.begin(RouteNavigation("trip-1", 1, resumesReturn = false), "Ridge loop", route)
        throwing.onFix(gps(north(200.0)), now)
        scope.runCurrent()

        assertEquals(2, throwing.state.value.navigation?.nextStopIndex)
        assertEquals(listOf("The stop-reached vibration could not be issued."), logged)
    }
}
