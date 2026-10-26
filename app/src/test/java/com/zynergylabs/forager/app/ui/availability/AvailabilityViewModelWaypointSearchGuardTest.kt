package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayto.FakeEngines
import com.zynergylabs.forager.app.domain.wayto.FakeTrailRouter
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointNote
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.CoroutineContext

/**
 * Dispatch 2026-10-26-11 Amendment 2, item 2 (RECORD -1670, the owner: "Yes, with the fade fix"; Sorrel's S-413-2): a
 * search that completes without suspending must not leave its finished job standing as the search in flight, which would
 * block every later search for that navigation. Through the real entry point, [AvailabilityViewModel.onNavigateToWaypoint].
 *
 * The ViewModel's scope is `Dispatchers.Main.immediate`: on the main thread a launch needs no dispatch, so its body runs in
 * the caller's stack until it first suspends, unless the caller is itself inside a dispatch-free resumption (coroutines
 * queue those on the thread's event loop). When this test was written the case that reached it was a navigation resumed by
 * a real dispatch, as after the waypoint's lookup returns from the database's thread: its first fix's search then started
 * in place. It no longer does: since Amendment 2 the follower reads fixes with `collectLatest`
 * (`AvailabilityWaypointRoute.follow`), whose per-fix child runs inside an unconfined event loop, so the search's launch is
 * queued on that loop and its body runs only after the job is held (Sorrel's re-check of #413, S-413R-1). So this test
 * does not guard the `CoroutineStart.LAZY` line in `startSearch`: with it reverted (a4) the test stays green. That line
 * is the guard, by construction, on every path; this test stands as a regression check on the search path.
 * [MainImmediateLike] is the main dispatcher on the test scheduler: no dispatch needed while running a dispatched task
 * ("on the main thread"), dispatched otherwise. A [StandardTestDispatcher] never starts a launch in place.
 * Every step of the real [FindWayToWaypointUseCase] over these fakes returns without suspending.
 */
class AvailabilityViewModelWaypointSearchGuardTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(MainImmediateLike(dispatcher))

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val t = 1_700_000_000_000L
    private val there = at(500.0, -60.0)
    private val pin = Waypoint("wp-1", there.lat, there.lng, null, "Creek pin", "", 1_000L)

    private fun fixAt(p: LatLng) = LocationFix.Update(lat = p.lat, lng = p.lng, altitude = 50.0, accuracyMeters = 3.79f, timestampEpochMillis = t, provider = FixProvider.GPS)

    private val fixes = MutableSharedFlow<LocationFix>(replay = 1)
    private val tracker = object : LocationTracker { override val fixes = this@AvailabilityViewModelWaypointSearchGuardTest.fixes }
    private val router = FakeTrailRouter(listOf(at(-3_000.0, 0.0) to at(3_000.0, 0.0)))
    private val searches = mutableListOf<LatLng>()

    /** The waypoint's lookup, held so the navigation resumes from it by a real dispatch, as from the database's thread. */
    private val lookedUp = CompletableDeferred<Unit>()

    private fun viewModel(): AvailabilityViewModel {
        // One region list for the ViewModel and the use case, as the app's two readers of the one repository have: since
        // Amendment 3, item 3 (RECORD -1671), walking on retries only while the ViewModel's list has the walker inside an area.
        val regions = FakeRegions(Ground.region())
        val find = FindWayToWaypointUseCase(regions, FakeEngines(router), ErrorLog { _, _, _ -> })
        return mapLayersViewModel(
            offlineMapRepository = regions,
            waypointNavigationRepository = InMemoryWaypointNavigation(),
            locationTracker = tracker,
            readWaypoint = { id ->
                lookedUp.await()
                Result.success(pin.takeIf { it.id == id })
            },
            currentTime = CurrentTimeProvider { t + dispatcher.scheduler.currentTime },
            findWayToWaypoint = { from, to ->
                searches += from
                find(from, to)
            },
        )
    }

    @Test
    fun `a search that completes without suspending leaves none in flight, so walking on searches again`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(0.0, 400.0))) // 400 m from the trail, past the engine's 250 m
        runCurrent()
        vm.onNavigateToWaypoint("wp-1")
        runCurrent()
        lookedUp.complete(Unit)
        runCurrent()
        assertEquals(ReturnRoute.Straight(WayToWaypointNote.WALKER_FAR_FROM_TRAIL), vm.uiState.value.waypointRoute?.hud)
        assertEquals("the first search", 1, searches.size)

        fixes.emit(fixAt(at(0.0, 240.0))) // 160 m on, within reach of the trail
        runCurrent()
        assertEquals("a second search, not blocked by the first one's finished job", 2, searches.size)
        assertTrue("by trail, not ${vm.uiState.value.waypointRoute?.hud}", vm.uiState.value.waypointRoute?.hud is ReturnRoute.Ahead)
    }

    /**
     * `Dispatchers.Main.immediate` on the test scheduler: a resumption or launch made while running one of its own dispatched
     * tasks (on "the main thread") needs no dispatch and runs in place; one made from anywhere else is dispatched. Delays go
     * to the test scheduler's virtual clock.
     */
    @OptIn(InternalCoroutinesApi::class)
    private class MainImmediateLike(private val inner: TestDispatcher) : CoroutineDispatcher(), Delay {
        /** Per thread, as "on the main thread" is: a resumption from another thread is always dispatched. */
        private val onMain = ThreadLocal.withInitial { false }

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = !onMain.get()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            inner.dispatch(context, Runnable {
                val was = onMain.get()
                onMain.set(true)
                try {
                    block.run()
                } finally {
                    onMain.set(was)
                }
            })
        }

        override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) =
            (inner as Delay).scheduleResumeAfterDelay(timeMillis, continuation)
    }
}
