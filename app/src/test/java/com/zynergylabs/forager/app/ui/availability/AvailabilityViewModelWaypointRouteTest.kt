package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.wayto.AcceptEnds
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.RouteLinePartKind
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Step 6 PR 3 (dispatch 2026-10-26-11): the way to a waypoint along trails, through the real entry point,
 * [AvailabilityViewModel.onNavigateToWaypoint], on the real [FindWayToWaypointUseCase] over a fake engine with one trail
 * running east-west through [Ground]'s origin. The walker is 40 m north of it, the waypoint 500 m east and 60 m south.
 */
class AvailabilityViewModelWaypointRouteTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val t = 1_700_000_000_000L
    private val there = at(500.0, -60.0)
    private val pin = Waypoint("wp-1", there.lat, there.lng, null, "Creek pin", "", 1_000L)

    private fun fixAt(p: LatLng) = LocationFix.Update(lat = p.lat, lng = p.lng, altitude = 50.0, accuracyMeters = 3.79f, timestampEpochMillis = t, provider = FixProvider.GPS)

    private val fixes = MutableSharedFlow<LocationFix>(replay = 1)
    private val tracker = object : LocationTracker { override val fixes = this@AvailabilityViewModelWaypointRouteTest.fixes }
    private val store = InMemoryWaypointNavigation()
    private val router = FakeTrailRouter(listOf(at(-3_000.0, 0.0) to at(3_000.0, 0.0)))
    private val logs = mutableListOf<String>()

    private fun viewModel(
        regions: List<OfflineRegionSummary> = listOf(Ground.region()),
        gate: CompletableDeferred<Unit>? = null,
    ): AvailabilityViewModel {
        val find = FindWayToWaypointUseCase(FakeRegions(*regions.toTypedArray()), FakeEngines(router), ErrorLog { _, _, _ -> }, AcceptEnds)
        return mapLayersViewModel(
            waypointNavigationRepository = store,
            locationTracker = tracker,
            readWaypoint = { id -> Result.success(pin.takeIf { it.id == id }) },
            currentTime = CurrentTimeProvider { t + dispatcher.scheduler.currentTime },
            errorLog = ErrorLog { _, message, _ -> logs += message },
            findWayToWaypoint = { from, to ->
                gate?.await()
                find(from, to)
            },
        )
    }

    private fun AvailabilityViewModel.shown() = uiState.value.waypointRoute

    @Test
    fun `Navigate shows the straight line while the route is worked out, then the way along trails, by trail`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val vm = viewModel(gate = gate)
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(0.0, 40.0)))
        runCurrent()

        vm.onNavigateToWaypoint("wp-1")
        runCurrent()
        assertNull("while it is worked out: today's straight HUD and line", vm.shown()?.hud)
        assertNull(vm.shown()?.line)
        assertNotNull("the straight dashed line meanwhile", vm.uiState.value.waypointStraightLine)

        gate.complete(Unit)
        runCurrent()

        val hud = vm.shown()?.hud
        assertTrue("by trail, not $hud", hud is ReturnRoute.Ahead)
        // 40 m lead-in + 500 m of trail + 60 m final leg, counted from the walker's own node (the lead-in, so no trail allowance).
        assertEquals(600.0, (hud as ReturnRoute.Ahead).routeMeters, 1.0)
        val line = vm.shown()!!.line!!
        assertEquals(listOf(RouteLinePartKind.STRAIGHT, RouteLinePartKind.TRAIL, RouteLinePartKind.STRAIGHT), line.aheadParts.map { it.kind })
        assertTrue("no passed line: the step path's three parts only", line.atReturn.isEmpty())
        assertEquals("one route, from the walker to the waypoint", listOf(at(0.0, 40.0) to there), router.asked.map { it.first to it.second })
    }

    @Test
    fun `walking the line counts the figure down along it`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(0.0, 40.0)))
        runCurrent()
        vm.onNavigateToWaypoint("wp-1")
        runCurrent()

        fixes.emit(fixAt(at(200.0, 2.0)))
        runCurrent()

        val hud = vm.shown()?.hud as ReturnRoute.Ahead
        // On the trail 200 m along: about 300 m of trail and the 60 m leg ahead, with the reach (no trail allowance on the
        // waypoint's line since Amendment 3, item 5; RECORD -1671).
        assertTrue("counted down: ${hud.routeMeters}", hud.routeMeters < 600.0 && hud.routeMeters >= 360.0)
        assertEquals("still one route", 1, router.asked.size)
    }

    /**
     * Amendment 3, item 5 (RECORD -1671, the owner: "Drop it (Recommended)"; Juniper's O2, checked by Sorrel): on the
     * trail, the waypoint's figure is the 60 m reach alone, without the way home's 25 m. From (205, 0) the earliest node
     * within 60 m is the trail's at about (150, 0), 190 m along the 600 m line, so about 410 m; with the 25 m it would
     * read about 435. The tolerance is the node fill's (500 m of trail may fill at 9.8 m steps, not 10), well under 25.
     * The walker is truly 355 m from the waypoint along the line, so it is still never short.
     */
    @Test
    fun `on the trail the waypoint's figure is the reach alone, without the way home's 25 m`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(0.0, 40.0)))
        runCurrent()
        vm.onNavigateToWaypoint("wp-1")
        runCurrent()

        fixes.emit(fixAt(at(205.0, 0.0)))
        runCurrent()

        val hud = vm.shown()?.hud
        assertTrue("by trail, not $hud", hud is ReturnRoute.Ahead)
        assertEquals("no 25 m on the waypoint's figure", 412.0, (hud as ReturnRoute.Ahead).routeMeters, 5.0)
        assertTrue("never short of the 355 m left", hud.routeMeters >= 355.0)
    }

    @Test
    fun `outside a downloaded area it is today's straight line, with the note`() = runTest(dispatcher) {
        val vm = viewModel(regions = emptyList())
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(0.0, 40.0)))
        runCurrent()

        vm.onNavigateToWaypoint("wp-1")
        runCurrent()

        assertEquals(WaypointRouteShown(ReturnRoute.Straight(WayToWaypointNote.NO_TRAIL_ROUTES), null), vm.shown())
        assertNotNull("the straight dashed line stays", vm.uiState.value.waypointStraightLine)
    }

    @Test
    fun `more than 50 m off the line re-routes from where the walker is, once`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(0.0, 40.0)))
        runCurrent()
        vm.onNavigateToWaypoint("wp-1")
        runCurrent()

        val strayed = at(300.0, 60.0) // 60 m north of the trail
        fixes.emit(fixAt(strayed))
        runCurrent()

        assertEquals("a second route, from the stray", listOf(at(0.0, 40.0), strayed), router.asked.map { it.first })
        val line = vm.shown()!!.line!!
        assertTrue("the new line starts at the walker", GeoDistance.metersBetween(strayed, line.ahead.first()) < 1.0)
        assertTrue(vm.shown()!!.hud is ReturnRoute.Ahead)

        fixes.emit(fixAt(at(300.0, 30.0))) // on the new lead-in
        runCurrent()
        assertEquals("on the new line: no more", 2, router.asked.size)
    }

    @Test
    fun `ending the navigation removes the way along trails`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(0.0, 40.0)))
        runCurrent()
        vm.onNavigateToWaypoint("wp-1")
        runCurrent()
        assertNotNull(vm.shown())

        vm.onStopWaypointNavigation()
        runCurrent()

        assertNull(vm.shown())
    }

    @Test
    fun `with no trail routing given, nothing is worked out and the HUD is today's`() = runTest(dispatcher) {
        val vm = mapLayersViewModel(
            waypointNavigationRepository = store,
            locationTracker = tracker,
            readWaypoint = { id -> Result.success(pin.takeIf { it.id == id }) },
        )
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(0.0, 40.0)))
        runCurrent()
        vm.onNavigateToWaypoint("wp-1")
        runCurrent()
        assertNull(vm.shown())
    }

    @Test
    fun `a search that throws falls back to the straight line, logged`() = runTest(dispatcher) {
        val vm = mapLayersViewModel(
            waypointNavigationRepository = store,
            locationTracker = tracker,
            readWaypoint = { id -> Result.success(pin.takeIf { it.id == id }) },
            errorLog = ErrorLog { _, message, _ -> logs += message },
            findWayToWaypoint = { _, _ -> throw IllegalStateException("boom") },
        )
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(0.0, 40.0)))
        runCurrent()
        vm.onNavigateToWaypoint("wp-1")
        runCurrent()
        assertEquals(WaypointRouteShown(ReturnRoute.Straight(WayToWaypointNote.NO_ROUTE_FOUND), null), vm.shown())
        assertTrue("logged: $logs", logs.any { "way to the waypoint" in it })
    }
}
