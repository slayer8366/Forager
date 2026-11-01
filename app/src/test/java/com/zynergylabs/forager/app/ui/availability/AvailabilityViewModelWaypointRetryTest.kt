package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.wayto.AcceptEnds
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.RouteLinePartKind
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.routing.TrailRouteFailure
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayto.FakeEngines
import com.zynergylabs.forager.app.domain.wayto.FakeTrailRouter
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointNote
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Dispatch 2026-10-26-11 Amendment 1, item C (RECORD -1669, the owner: "Try again on entering an area (Recommended)"),
 * through the real entry point, [AvailabilityViewModel.onNavigateToWaypoint], on the real [FindWayToWaypointUseCase] over
 * a fake engine with one trail running east-west through [Ground]'s origin. The step path: Navigate outside your area >
 * the straight line and note > walk into an area that covers you and the waypoint > the three-part route and "by trail"
 * replace it, without restarting Navigate. The ViewModel and the use case read one region list ([regions]), as the app's
 * two readers of the one repository do.
 */
class AvailabilityViewModelWaypointRetryTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val t = 1_700_000_000_000L
    private val there = at(500.0, -60.0)

    /** 1 km about the waypoint: a walker 1.5 km west of it is outside, one 500 m west is inside. */
    private val area = Ground.region(centre = there, radiusKm = 1)

    private fun fixAt(p: LatLng, at: Long = t) = LocationFix.Update(lat = p.lat, lng = p.lng, altitude = 50.0, accuracyMeters = 3.79f, timestampEpochMillis = at, provider = FixProvider.GPS)

    private val fixes = MutableSharedFlow<LocationFix>(replay = 1)
    private val tracker = object : LocationTracker { override val fixes = this@AvailabilityViewModelWaypointRetryTest.fixes }
    private val store = InMemoryWaypointNavigation()
    private val router = FakeTrailRouter(listOf(at(-3_000.0, 0.0) to at(3_000.0, 0.0)))

    /** Where each search was asked from, in order: every call into the use case, engine run or not. */
    private val searches = mutableListOf<LatLng>()

    /** Held for the next search when set, so a search can be caught in flight. */
    private var gate: CompletableDeferred<Unit>? = null

    private val regions = MutableRegions()

    private fun viewModel(waypoint: LatLng = there, vararg listed: OfflineRegionSummary): AvailabilityViewModel {
        regions.list = listed.toList()
        val pin = Waypoint("wp-1", waypoint.lat, waypoint.lng, null, "Creek pin", "", 1_000L)
        val find = FindWayToWaypointUseCase(regions, FakeEngines(router), ErrorLog { _, _, _ -> }, AcceptEnds)
        return mapLayersViewModel(
            waypointNavigationRepository = store,
            locationTracker = tracker,
            offlineMapRepository = regions,
            readWaypoint = { id -> Result.success(pin.takeIf { it.id == id }) },
            currentTime = CurrentTimeProvider { t + dispatcher.scheduler.currentTime },
            findWayToWaypoint = { from, to ->
                searches += from
                gate?.await()
                find(from, to)
            },
        )
    }

    private fun AvailabilityViewModel.hud() = uiState.value.waypointRoute?.hud

    @Test
    fun `walking into an area holding the walker and the waypoint works the trail route out again, by trail`() = runTest(dispatcher) {
        val vm = viewModel(there, area)
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(-1_500.0, 40.0)))
        runCurrent()
        vm.onNavigateToWaypoint("wp-1")
        runCurrent()
        assertEquals(ReturnRoute.Straight(WayToWaypointNote.WAYPOINT_IN_ANOTHER_AREA), vm.hud())

        fixes.emit(fixAt(at(-1_200.0, 40.0))) // still outside
        runCurrent()
        assertEquals("no search on a fix outside the area", 1, searches.size)

        gate = CompletableDeferred()
        fixes.emit(fixAt(at(0.0, 40.0))) // inside
        runCurrent()
        assertEquals("one search on walking in, from there", listOf(at(-1_500.0, 40.0), at(0.0, 40.0)), searches)
        fixes.emit(fixAt(at(10.0, 40.0)))
        runCurrent()
        assertEquals("one search at a time", 2, searches.size)

        gate!!.complete(Unit)
        runCurrent()
        assertTrue("by trail, not ${vm.hud()}", vm.hud() is ReturnRoute.Ahead)
        val line = vm.uiState.value.waypointRoute!!.line!!
        assertEquals(listOf(RouteLinePartKind.STRAIGHT, RouteLinePartKind.TRAIL, RouteLinePartKind.STRAIGHT), line.aheadParts.map { it.kind })
    }

    @Test
    fun `far from any trail tries again after walking on more than 50 m, not on every fix`() = runTest(dispatcher) {
        val vm = viewModel(there, Ground.region())
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(0.0, 400.0))) // 400 m from the trail, past the engine's 250 m
        runCurrent()
        vm.onNavigateToWaypoint("wp-1")
        runCurrent()
        assertEquals(ReturnRoute.Straight(WayToWaypointNote.WALKER_FAR_FROM_TRAIL), vm.hud())

        fixes.emit(fixAt(at(0.0, 351.0))) // 49 m on
        runCurrent()
        assertEquals("49 m on: no search", 1, searches.size)
        fixes.emit(fixAt(at(0.0, 349.0))) // 51 m on, still out of reach
        runCurrent()
        assertEquals("51 m on: one search", 2, searches.size)
        assertEquals(ReturnRoute.Straight(WayToWaypointNote.WALKER_FAR_FROM_TRAIL), vm.hud())

        fixes.emit(fixAt(at(0.0, 300.0))) // 49 m on from that search
        runCurrent()
        assertEquals("counted from the last search", 2, searches.size)
        fixes.emit(fixAt(at(0.0, 240.0))) // within reach of the trail
        runCurrent()
        assertEquals(3, searches.size)
        assertTrue("by trail, not ${vm.hud()}", vm.hud() is ReturnRoute.Ahead)
    }

    @Test
    fun `a download finishing while navigating is taken at the next fix`() = runTest(dispatcher) {
        val vm = viewModel(there)
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(0.0, 40.0)))
        runCurrent()
        vm.onNavigateToWaypoint("wp-1")
        runCurrent()
        assertEquals(ReturnRoute.Straight(WayToWaypointNote.NO_TRAIL_ROUTES), vm.hud())

        // The download's own end: the repository lists the region, and the ViewModel reloads its list
        // (AvailabilityOfflineMaps.beginDownload's onSuccess calls this same reload).
        regions.list = listOf(area)
        vm.offlineMaps.loadOfflineRegions()
        runCurrent()
        assertEquals("no search for the reload itself", 1, searches.size)

        fixes.emit(fixAt(at(0.0, 41.0), at = t + 1_000L))
        runCurrent()
        assertEquals(2, searches.size)
        assertTrue("by trail, not ${vm.hud()}", vm.hud() is ReturnRoute.Ahead)
    }

    @Test
    fun `a straight result walking cannot change starts no search, however far the walker goes`() = runTest(dispatcher) {
        val vm = viewModel(at(500.0, -400.0), Ground.region()) // the waypoint 400 m from the trail
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(0.0, 40.0)))
        runCurrent()
        vm.onNavigateToWaypoint("wp-1")
        runCurrent()
        assertEquals(ReturnRoute.Straight(WayToWaypointNote.WAYPOINT_FAR_FROM_TRAIL), vm.hud())

        for (east in listOf(100.0, 200.0, 300.0, 400.0)) {
            fixes.emit(fixAt(at(east, 40.0)))
            runCurrent()
        }
        assertEquals(1, searches.size)
    }

    /**
     * Amendment 2, item 3 (RECORD -1670, the owner: "Every 50 m walked (Recommended)"): inside the area, a straight result
     * walking can change (here the engine finding no path) tries again after each 50 m walked from the last ask, without
     * restarting Navigate; a search per fix never.
     */
    @Test
    fun `inside the area, no route found tries again after walking on more than 50 m, and the route found replaces it`() = runTest(dispatcher) {
        val vm = viewModel(there, area)
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(0.0, 40.0)))
        runCurrent()
        router.failWith = TrailRouteFailure.NoPath("no path")
        vm.onNavigateToWaypoint("wp-1")
        runCurrent()
        assertEquals(ReturnRoute.Straight(WayToWaypointNote.NO_ROUTE_FOUND), vm.hud())

        fixes.emit(fixAt(at(49.0, 40.0))) // 49 m on
        runCurrent()
        assertEquals("49 m on: no search", 1, searches.size)

        router.failWith = null
        fixes.emit(fixAt(at(51.0, 40.0))) // 51 m on
        runCurrent()
        assertEquals("51 m on: one search, from there", listOf(at(0.0, 40.0), at(51.0, 40.0)), searches)
        assertTrue("by trail, not ${vm.hud()}", vm.hud() is ReturnRoute.Ahead)
    }

    /** The one repository the ViewModel's list and the use case both read; [list] changes as a download would. */
    private class MutableRegions : OfflineMapRepository {
        var list: List<OfflineRegionSummary> = emptyList()
        override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> = error("not used")
        override suspend fun deleteRegion(id: Long): Result<Unit> = error("not used")
        override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(list)
    }
}
