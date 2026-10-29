package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.wayto.AcceptEnds
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayhome.OffTrailState
import com.zynergylabs.forager.app.domain.wayto.FakeEngines
import com.zynergylabs.forager.app.domain.wayto.FakeTrailRouter
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointNote
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
 * Step 6 PR 4 (dispatch 2026-10-27-01; RECORD -1725): the way home's quiet off-trail state on the way to a waypoint,
 * through the real entry point, [AvailabilityViewModel.onNavigateToWaypoint], on the real [FindWayToWaypointUseCase]
 * over a fake engine with one trail running east-west through [Ground]'s origin. The waypoint is 500 m east and 60 m
 * south. The fixes claim 3.79 m, so "off" is further than 43.79 m from the whole line (`OFF_TRACK_LINE_METERS` plus the
 * accuracy) and a stray is further than 50 m: the 45-49 m positions below are off without re-routing.
 *
 * Fixes come every 5 s of the test's clock, each stamped with it, so the rule's 15 s, 10 s and 5 s are the fixes' own
 * time, as on the phone.
 */
class AvailabilityViewModelWaypointOffTrailTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val t = 1_700_000_000_000L
    private val there = at(500.0, -60.0)
    private val pin = Waypoint("wp-1", there.lat, there.lng, null, "Creek pin", "", 1_000L)

    private val fixes = MutableSharedFlow<LocationFix>(replay = 1)
    private val tracker = object : LocationTracker { override val fixes = this@AvailabilityViewModelWaypointOffTrailTest.fixes }
    private val router = FakeTrailRouter(listOf(at(-3_000.0, 0.0) to at(3_000.0, 0.0)))

    private fun viewModel(regions: List<OfflineRegionSummary> = listOf(Ground.region())): AvailabilityViewModel {
        val find = FindWayToWaypointUseCase(FakeRegions(*regions.toTypedArray()), FakeEngines(router), ErrorLog { _, _, _ -> }, AcceptEnds)
        return mapLayersViewModel(
            waypointNavigationRepository = InMemoryWaypointNavigation(),
            locationTracker = tracker,
            readWaypoint = { id -> Result.success(pin.takeIf { it.id == id }) },
            currentTime = CurrentTimeProvider { t + dispatcher.scheduler.currentTime },
            findWayToWaypoint = { from, to -> find(from, to) },
        )
    }

    private fun TestScope.fixAt(p: LatLng) =
        LocationFix.Update(lat = p.lat, lng = p.lng, altitude = 50.0, accuracyMeters = 3.79f, timestampEpochMillis = t + testScheduler.currentTime, provider = FixProvider.GPS)

    /** A fix at [p] now, then the clock on by [thenMillis]. */
    private suspend fun TestScope.fix(p: LatLng, thenMillis: Long = 5_000L) {
        fixes.emit(fixAt(p))
        runCurrent()
        advanceTimeBy(thenMillis)
        runCurrent()
    }

    /** Navigating from [from], with the first route worked out. */
    private suspend fun TestScope.navigating(from: LatLng, regions: List<OfflineRegionSummary> = listOf(Ground.region())): AvailabilityViewModel {
        val vm = viewModel(regions)
        vm.onEnteredForeground()
        fixes.emit(fixAt(from))
        runCurrent()
        vm.onNavigateToWaypoint("wp-1")
        runCurrent()
        return vm
    }

    private fun AvailabilityViewModel.ahead() = uiState.value.waypointRoute?.hud as? ReturnRoute.Ahead
    private fun AvailabilityViewModel.offTrail() = ahead()?.offTrail
    private fun AvailabilityViewModel.dimmed() = uiState.value.waypointRoute?.line?.trailDimmed

    @Test
    fun `on the dashed lead-in, 47 m from the trail, and then on the trail, it never reads off`() = runTest(dispatcher) {
        // The lead-in runs from (0, 48) to the trail at (0, 0): (0, 47) is on it, and 47 m from the trail itself.
        val vm = navigating(at(0.0, 48.0))
        repeat(5) { fix(at(0.0, 47.0)) } // 25 s on the dashed leg: off by the trail alone, on by the whole line
        assertEquals("on the dashed leg is on", OffTrailState.ON, vm.offTrail())
        assertEquals(false, vm.dimmed())
        repeat(5) { fix(at(200.0, 1.0)) }
        assertEquals("on the trail is on", OffTrailState.ON, vm.offTrail())
        assertEquals(false, vm.dimmed())
        assertEquals("one route: nothing strayed", 1, router.asked.size)
    }

    @Test
    fun `46 m off the line reads off at 15 s, and not before, dimming the trail, with the waypoint's words`() = runTest(dispatcher) {
        val vm = navigating(at(0.0, 40.0))
        val off = at(250.0, 46.0)
        fix(off); fix(off); fix(off) // 0, 5, 10 s
        assertEquals("10 s off: still on", OffTrailState.ON, vm.offTrail())
        assertEquals(false, vm.dimmed())
        fixes.emit(fixAt(off)) // 15 s
        runCurrent()
        assertEquals("15 s off: off", OffTrailState.OFF, vm.offTrail())
        assertEquals("the trail dims", true, vm.dimmed())
        assertTrue("the waypoint's words", vm.ahead()!!.toWaypoint)
        val readout = navigationReadout(TrueHeadingReading.Available(0f), fixAt(off), pin, DistanceUnit.MILES, t + testScheduler.currentTime, route = vm.ahead())
        assertEquals(WAYPOINT_OFF_TRAIL_TEXT, readout.statusText)
        assertEquals("46 m is not a stray: one route", 1, router.asked.size)
    }

    @Test
    fun `back on the line for 10 s shows Back on trail for 5 s, then the usual line`() = runTest(dispatcher) {
        val vm = navigating(at(0.0, 40.0))
        val off = at(250.0, 46.0)
        repeat(3) { fix(off) }
        fix(off) // 15 s: off
        assertEquals(OffTrailState.OFF, vm.offTrail())

        val on = at(260.0, 1.0)
        fix(on) // 20 s: back on the line
        fix(on) // 25 s
        assertEquals("5 s back on: still off", OffTrailState.OFF, vm.offTrail())
        assertEquals(true, vm.dimmed())
        fix(on, thenMillis = 4_000L) // 30 s: 10 s back on
        assertEquals("10 s back on: Back on trail", OffTrailState.BACK_ON, vm.offTrail())
        assertEquals("full strength again", false, vm.dimmed())
        fix(on, thenMillis = 1_000L) // 34 s
        assertEquals("4 s into it: still Back on trail", OffTrailState.BACK_ON, vm.offTrail())
        fix(on) // 35 s
        assertEquals("5 s on: the usual line", OffTrailState.ON, vm.offTrail())
        assertEquals("the usual line is empty", "", navigationReadout(TrueHeadingReading.Available(0f), fixAt(on), pin, DistanceUnit.MILES, t + testScheduler.currentTime, route = vm.ahead()).statusText)
    }

    @Test
    fun `a stray over 50 m re-routes from the walker, and the new line clears the dim at once, with no Back on trail`() = runTest(dispatcher) {
        val vm = navigating(at(0.0, 40.0))
        val off = at(300.0, 46.0)
        repeat(3) { fix(off) }
        fix(off) // 15 s: off
        assertEquals(OffTrailState.OFF, vm.offTrail())
        assertEquals(true, vm.dimmed())

        val strayed = at(300.0, 60.0)
        fixes.emit(fixAt(strayed)) // 20 s: 60 m from the line, a stray
        runCurrent()
        assertEquals("re-routed from the stray", listOf(at(0.0, 40.0), strayed), router.asked.map { it.first })
        assertEquals("the new line: the usual line at once", OffTrailState.ON, vm.offTrail())
        assertEquals("the new line at full strength", false, vm.dimmed())

        advanceTimeBy(5_000L)
        runCurrent()
        repeat(4) {
            fix(at(300.0, 58.0)) // on the new lead-in
            assertEquals("never Back on trail after a re-route", OffTrailState.ON, vm.offTrail())
        }
    }

    /**
     * Holds by construction, flagged as CLAUDE.md asks: a straight-line route has no line to judge and its HUD route,
     * [ReturnRoute.Straight], carries no off-trail state, so no one-line revert of this PR can turn it red. It is here
     * as the step path's case 5, not as evidence that the rule bites.
     */
    @Test
    fun `a straight-line route never reads off trail, however far off`() = runTest(dispatcher) {
        val vm = navigating(at(0.0, 40.0), regions = emptyList())
        repeat(6) { fix(at(250.0, 200.0)) }
        assertEquals(WaypointRouteShown(ReturnRoute.Straight(WayToWaypointNote.NO_TRAIL_ROUTES), null), vm.uiState.value.waypointRoute)
        assertNull(vm.ahead())
        val readout = navigationReadout(TrueHeadingReading.Available(0f), fixAt(at(250.0, 200.0)), pin, DistanceUnit.MILES, t + testScheduler.currentTime, route = vm.uiState.value.waypointRoute?.hud)
        assertFalse("no off-trail words: ${readout.statusText}", readout.statusText.startsWith("Off trail"))
    }
}
