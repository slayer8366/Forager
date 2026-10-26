package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.WaypointNavigation
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Step 6 PR 1 (dispatch 2026-10-26-06, RECORD -1653, -1655, -1657): the straight line to a waypoint, held by the real
 * [AvailabilityViewModel] over fakes and driven through [AvailabilityViewModel.onNavigateToWaypoint]. The clock is the
 * test scheduler's, so the line's two fades are seen happening with no new fix, as the screen's own clock did before
 * the move: current under 30 s, faded from 30 s (the owner's "Fade with the HUD", -506), grey and kept once lost at
 * 5 min ("Keep the last line, faded").
 */
class AvailabilityViewModelWaypointLineTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val t = 1_700_000_000_000L
    private val creek = Waypoint("wp-1", 45.326, -122.634, null, "Creek pin", "", 1_000L)
    private val fix = LocationFix.Update(lat = 45.3215, lng = -122.634, altitude = 50.0, accuracyMeters = 3.79f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val drawn = listOf(LatLng(fix.lat, fix.lng), LatLng(creek.lat, creek.lng))

    private val fixes = MutableSharedFlow<LocationFix>(replay = 1)
    private val tracker = object : LocationTracker { override val fixes = this@AvailabilityViewModelWaypointLineTest.fixes }
    private val store = InMemoryWaypointNavigation()

    private fun viewModel() = mapLayersViewModel(
        waypointNavigationRepository = store,
        locationTracker = tracker,
        readWaypoint = { id -> Result.success(creek.takeIf { it.id == id }) },
        currentTime = CurrentTimeProvider { t + dispatcher.scheduler.currentTime },
    )

    @Test
    fun `Navigate draws the line from the fix, current, and it fades at 30 s with no new fix, then greys once lost`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEnteredForeground()
        fixes.emit(fix)
        runCurrent()

        vm.onNavigateToWaypoint("wp-1")
        runCurrent()
        assertEquals(StraightLine(drawn, isCurrent = true), vm.uiState.value.waypointStraightLine)

        advanceTimeBy(29_999L)
        runCurrent()
        assertEquals("29.999 s old: still current", StraightLine(drawn, isCurrent = true), vm.uiState.value.waypointStraightLine)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals("30 s old: faded, not grey", StraightLine(drawn, isCurrent = false, isOffline = false), vm.uiState.value.waypointStraightLine)

        advanceTimeBy(5 * 60_000L - 30_000L)
        runCurrent()
        assertEquals("5 min old: lost, the last line kept, grey", StraightLine(drawn, isCurrent = false, isOffline = true), vm.uiState.value.waypointStraightLine)

        vm.onStopWaypointNavigation()
        runCurrent()
        assertNull("ending the navigation removes the line", vm.uiState.value.waypointStraightLine)
    }

    @Test
    fun `a navigation picked back up looks its waypoint up by id and draws the line from the first fix`() = runTest(dispatcher) {
        store.stored = WaypointNavigation("wp-1", resumesReturn = false)
        val vm = viewModel()
        vm.onEnteredForeground()
        runCurrent()
        assertNull("no fix yet: no line", vm.uiState.value.waypointStraightLine)

        fixes.emit(fix)
        runCurrent()
        assertEquals(StraightLine(drawn, isCurrent = true), vm.uiState.value.waypointStraightLine)
    }
}
