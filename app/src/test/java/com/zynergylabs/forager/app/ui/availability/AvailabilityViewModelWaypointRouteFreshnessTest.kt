package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LOST_AFTER_MILLIS
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.STALE_AFTER_MILLIS
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayto.FakeEngines
import com.zynergylabs.forager.app.domain.wayto.FakeTrailRouter
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Dispatch 2026-10-26-11 Amendment 2, item 1 (RECORD -1670, the owner: "Fade and grey too (Recommended)"; Sorrel's
 * S-413-1): the way to a waypoint along trails fades with the HUD on a stale fix and greys once location is lost, by the
 * straight line's own rule and figures ([nextStraightLine]: stale from [STALE_AFTER_MILLIS], lost from
 * [LOST_AFTER_MILLIS]), with no new fix to prompt it. Through the real entry point,
 * [AvailabilityViewModel.onNavigateToWaypoint], on the real [FindWayToWaypointUseCase] over a fake engine, as
 * [AvailabilityViewModelWaypointRouteTest]. The straight line is held beside the trail line in the same state, so each
 * step compares the two.
 */
class AvailabilityViewModelWaypointRouteFreshnessTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val t = 1_700_000_000_000L
    private val there = at(500.0, -60.0)
    private val pin = Waypoint("wp-1", there.lat, there.lng, null, "Creek pin", "", 1_000L)

    private fun fixAt(p: LatLng, at: Long = t) = LocationFix.Update(lat = p.lat, lng = p.lng, altitude = 50.0, accuracyMeters = 3.79f, timestampEpochMillis = at, provider = FixProvider.GPS)

    private val fixes = MutableSharedFlow<LocationFix>(replay = 1)
    private val tracker = object : LocationTracker { override val fixes = this@AvailabilityViewModelWaypointRouteFreshnessTest.fixes }
    private val router = FakeTrailRouter(listOf(at(-3_000.0, 0.0) to at(3_000.0, 0.0)))

    private fun viewModel(): AvailabilityViewModel {
        val find = FindWayToWaypointUseCase(FakeRegions(Ground.region()), FakeEngines(router), ErrorLog { _, _, _ -> })
        return mapLayersViewModel(
            waypointNavigationRepository = InMemoryWaypointNavigation(),
            locationTracker = tracker,
            readWaypoint = { id -> Result.success(pin.takeIf { it.id == id }) },
            currentTime = CurrentTimeProvider { t + dispatcher.scheduler.currentTime },
            findWayToWaypoint = { from, to -> find(from, to) },
        )
    }

    @Test
    fun `the trail line fades with the HUD on a stale fix and greys once location is lost, as the straight line does`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(0.0, 40.0)))
        runCurrent()
        vm.onNavigateToWaypoint("wp-1")
        runCurrent()

        val fresh = vm.uiState.value.waypointRoute!!.line!!
        assertTrue("a fresh fix: current", fresh.aheadIsCurrent)
        assertFalse("a fresh fix: not grey", fresh.aheadIsOffline)

        advanceTimeBy(STALE_AFTER_MILLIS)
        runCurrent()
        val stale = vm.uiState.value.waypointRoute!!.line!!
        assertFalse("a stale fix: faded, with no new fix", stale.aheadIsCurrent)
        assertFalse("a stale fix: not grey yet", stale.aheadIsOffline)
        assertFalse("the straight line's rule says faded at the same moment", vm.uiState.value.waypointStraightLine!!.isCurrent)
        assertEquals("the line itself is kept", fresh.aheadParts, stale.aheadParts)
        assertTrue("by trail still", vm.uiState.value.waypointRoute!!.hud is ReturnRoute.Ahead)

        advanceTimeBy(LOST_AFTER_MILLIS - STALE_AFTER_MILLIS)
        runCurrent()
        val lost = vm.uiState.value.waypointRoute!!.line!!
        assertTrue("location lost: grey", lost.aheadIsOffline)
        assertFalse("location lost: not current", lost.aheadIsCurrent)
        assertTrue("the straight line's rule says grey at the same moment", vm.uiState.value.waypointStraightLine!!.isOffline)
        assertEquals("the last line kept", fresh.aheadParts, lost.aheadParts)

        fixes.emit(fixAt(at(10.0, 40.0), at = t + testScheduler.currentTime))
        runCurrent()
        val back = vm.uiState.value.waypointRoute!!.line!!
        assertTrue("a fresh fix brings it back current", back.aheadIsCurrent)
        assertFalse("a fresh fix brings it back from grey", back.aheadIsOffline)
        assertEquals("no second search for any of it", 1, router.asked.size)
    }
}
