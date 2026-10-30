package com.zynergylabs.forager.app.ui.route

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.RoutePlotDraft
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.route.StraightReason
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [RoutePlottingViewModel], headless, on a fake leg router whose legs the test finishes by hand (dispatch 2026-10-30-08, step 3).
 * No poll loop here: every coroutine the ViewModel starts ends when its leg is finished or cancelled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoutePlottingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val creek = RouteStop("Creek", LatLng(45.2, -122.5))
    private val ridge = RouteStop("Ridge", LatLng(45.22, -122.52))
    private val falls = RouteStop("Falls", LatLng(45.24, -122.55))

    private fun straight(from: LatLng, to: LatLng, meters: Double = 100.0) = RouteLeg.Straight(listOf(from, to), meters, StraightReason.NO_REGION_COVERS)

    /** Each leg asked for waits on its own deferred; [requested] is the order they were asked for in. */
    private class FakeLegs {
        val requested = mutableListOf<Pair<LatLng, LatLng>>()
        val pending = mutableListOf<CompletableDeferred<RouteLeg>>()
        var cancelled = 0
        suspend fun route(from: LatLng, to: LatLng): RouteLeg {
            requested += from to to
            val deferred = CompletableDeferred<RouteLeg>().also { pending += it }
            try {
                return deferred.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled++
                throw e
            }
        }
    }

    private val legs = FakeLegs()
    private val saves = mutableListOf<Triple<PlottedRoute, String, LocalDate?>>()
    private var saveResult: Result<PlannedTrip>? = null
    private var savedCallbacks = 0
    private val logged = mutableListOf<String>()

    private fun viewModel() = RoutePlottingViewModel(
        routeLeg = legs::route,
        saveRoute = { route, name, date ->
            saves += Triple(route, name, date)
            saveResult ?: Result.success(PlannedTrip("t", name, route.stops.first().position, date, route))
        },
        onSaved = { savedCallbacks++ },
        errorLog = ErrorLog { _, message, _ -> logged += message },
    )

    private fun RoutePlottingViewModel.draft() = (uiState.value.mode as RoutePlotMode.Plotting).draft

    @Test
    fun `Plot route starts plotting from the waypoint, with the card peeking and no leg asked for`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onStartPlotting(creek)
        advanceUntilIdle()

        assertEquals(RoutePlotMode.Plotting(RoutePlotDraft(listOf(creek))), vm.uiState.value.mode)
        assertFalse(vm.uiState.value.expanded)
        assertEquals(emptyList<Pair<LatLng, LatLng>>(), legs.requested)
    }

    @Test
    fun `waypoint taps add stops at once and their legs are routed one at a time, in order`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onStartPlotting(creek)

        vm.onWaypointTapped(ridge)
        vm.onWaypointTapped(falls)
        advanceUntilIdle()

        assertEquals(listOf(creek, ridge, falls), vm.draft().stops)
        assertEquals(listOf(creek.position to ridge.position), legs.requested)

        legs.pending[0].complete(straight(creek.position, ridge.position, 120.0))
        advanceUntilIdle()

        assertEquals(listOf(creek.position to ridge.position, ridge.position to falls.position), legs.requested)
        assertEquals(1, vm.draft().legs.size)

        legs.pending[1].complete(straight(ridge.position, falls.position, 80.0))
        advanceUntilIdle()

        assertEquals(200.0, vm.draft().toRoute()!!.totalMeters, 1e-9)
    }

    @Test
    fun `the same waypoint tapped twice in a row is ignored`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onStartPlotting(creek)

        vm.onWaypointTapped(creek)
        advanceUntilIdle()

        assertEquals(listOf(creek), vm.draft().stops)
        assertEquals(emptyList<Pair<LatLng, LatLng>>(), legs.requested)
    }

    @Test
    fun `waypoint taps while not plotting do nothing`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onWaypointTapped(creek)
        advanceUntilIdle()

        assertEquals(RoutePlotMode.Off, vm.uiState.value.mode)
    }

    @Test
    fun `Undo with a leg in flight cancels it and removes its stop`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onStartPlotting(creek)
        vm.onWaypointTapped(ridge)
        advanceUntilIdle()

        vm.onUndo()
        advanceUntilIdle()

        assertEquals(1, legs.cancelled)
        assertEquals(listOf(creek), vm.draft().stops)
        // A late answer for the cancelled leg is never added.
        legs.pending[0].complete(straight(creek.position, ridge.position))
        advanceUntilIdle()
        assertEquals(emptyList<RouteLeg>(), vm.draft().legs)
    }

    @Test
    fun `Undo of a stop queued behind a leg in flight leaves that leg running`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onStartPlotting(creek)
        vm.onWaypointTapped(ridge)
        vm.onWaypointTapped(falls)
        advanceUntilIdle()

        vm.onUndo()
        legs.pending[0].complete(straight(creek.position, ridge.position))
        advanceUntilIdle()

        assertEquals(0, legs.cancelled)
        assertEquals(listOf(creek, ridge), vm.draft().stops)
        assertEquals(1, vm.draft().legs.size)
        assertEquals(1, legs.requested.size)
    }

    @Test
    fun `Cancel drops the route and the leg in flight`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onStartPlotting(creek)
        vm.onWaypointTapped(ridge)
        advanceUntilIdle()

        vm.onCancel()
        advanceUntilIdle()

        assertEquals(RoutePlotMode.Off, vm.uiState.value.mode)
        assertEquals(1, legs.cancelled)
    }

    @Test
    fun `the save dialog opens only once every leg is in`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onStartPlotting(creek)
        vm.onOpenSaveDialog()
        assertFalse("one stop", vm.uiState.value.saveDialogOpen)

        vm.onWaypointTapped(ridge)
        advanceUntilIdle()
        vm.onOpenSaveDialog()
        assertFalse("a leg in flight", vm.uiState.value.saveDialogOpen)

        legs.pending[0].complete(straight(creek.position, ridge.position))
        advanceUntilIdle()
        vm.onOpenSaveDialog()
        assertTrue(vm.uiState.value.saveDialogOpen)
    }

    @Test
    fun `Save saves the route with its name and date, ends plotting and reloads the trips`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onStartPlotting(creek)
        vm.onWaypointTapped(ridge)
        advanceUntilIdle()
        legs.pending[0].complete(straight(creek.position, ridge.position))
        advanceUntilIdle()
        vm.onOpenSaveDialog()

        vm.onSave("Creek to Ridge", LocalDate.of(2026, 11, 2))
        advanceUntilIdle()

        assertEquals(listOf(Triple(vm.lastSavedRouteForTest(), "Creek to Ridge", LocalDate.of(2026, 11, 2))), saves)
        assertEquals(RoutePlottingUiState(), vm.uiState.value)
        assertEquals(1, savedCallbacks)
    }

    @Test
    fun `a failed save keeps the dialog open with the route, says so and logs it`() = runTest(dispatcher) {
        saveResult = Result.failure(IllegalStateException("disk full"))
        val vm = viewModel()
        vm.onStartPlotting(creek)
        vm.onWaypointTapped(ridge)
        advanceUntilIdle()
        legs.pending[0].complete(straight(creek.position, ridge.position))
        advanceUntilIdle()
        vm.onOpenSaveDialog()

        vm.onSave("Creek to Ridge", null)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.saveDialogOpen)
        assertTrue(vm.uiState.value.saveFailed)
        assertFalse(vm.uiState.value.saving)
        assertEquals(listOf(creek, ridge), vm.draft().stops)
        assertEquals(0, savedCallbacks)
        assertEquals(1, logged.size)
    }

    @Test
    fun `a saved route opens at once when nothing is being plotted, with the card peeking`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onSetExpanded(true)
        val trip = savedTrip()

        vm.onOpenSavedRoute(trip, fromTripPlanner = false)

        assertEquals(RoutePlotMode.Showing(trip, serial = 1), vm.uiState.value.mode)
        assertFalse(vm.uiState.value.expanded)

        vm.onCloseSavedRoute()
        assertEquals(RoutePlotMode.Off, vm.uiState.value.mode)
    }

    @Test
    fun `a saved route tapped mid-plot asks first, Keep plotting keeps the route and Discard opens the saved one`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onStartPlotting(creek)
        vm.onWaypointTapped(ridge)
        advanceUntilIdle()
        val trip = savedTrip()

        vm.onOpenSavedRoute(trip, fromTripPlanner = false)
        assertEquals(trip, vm.uiState.value.pendingDiscard)
        assertEquals(listOf(creek, ridge), vm.draft().stops)

        vm.onKeepPlotting()
        assertNull(vm.uiState.value.pendingDiscard)
        assertEquals(listOf(creek, ridge), vm.draft().stops)

        vm.onOpenSavedRoute(trip, fromTripPlanner = false)
        vm.onConfirmDiscard()
        advanceUntilIdle()
        assertNull(vm.uiState.value.pendingDiscard)
        assertEquals(RoutePlotMode.Showing(trip, serial = 1), vm.uiState.value.mode)
        assertEquals(1, legs.cancelled)
    }

    @Test
    fun `the card's anchor is kept`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onStartPlotting(creek)

        vm.onSetExpanded(true)

        assertTrue(vm.uiState.value.expanded)
    }

    @Test
    fun `each open of a saved route is counted, and a route opened from the Trip Planner says so`() = runTest(dispatcher) {
        val vm = viewModel()
        val trip = savedTrip()

        vm.onOpenSavedRoute(trip, fromTripPlanner = true)
        assertEquals(RoutePlotMode.Showing(trip, serial = 1, fromTripPlanner = true), vm.uiState.value.mode)

        vm.onCloseSavedRoute()
        vm.onOpenSavedRoute(trip, fromTripPlanner = false)
        assertEquals(RoutePlotMode.Showing(trip, serial = 2, fromTripPlanner = false), vm.uiState.value.mode)
    }

    private fun savedTrip(): PlannedTrip {
        val route = PlottedRoute(listOf(ridge, falls), listOf(straight(ridge.position, falls.position)))
        return PlannedTrip("saved", "Ridge to Falls", ridge.position, null, route)
    }

    private fun RoutePlottingViewModel.lastSavedRouteForTest(): PlottedRoute =
        PlottedRoute(listOf(creek, ridge), listOf(straight(creek.position, ridge.position)))
}
