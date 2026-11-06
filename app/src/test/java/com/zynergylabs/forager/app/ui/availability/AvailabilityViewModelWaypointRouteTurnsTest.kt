package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.nextturn.RouteTurns
import com.zynergylabs.forager.app.domain.nextturn.RouteTurnsReader
import com.zynergylabs.forager.app.domain.nextturn.WaysReading
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import com.zynergylabs.forager.app.domain.wayto.AcceptEnds
import com.zynergylabs.forager.app.domain.wayto.FakeEngines
import com.zynergylabs.forager.app.domain.wayto.FakeTrailRouter
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The way to a waypoint's junctions held beside its line (dispatch 2026-11-06-01, Amendment 1, H4, 2b-1: "`routeTurnsFor`
 * held where each line is held (WayHomeWatch, AvailabilityWaypointRoute) with its route data folder, recomputed on each
 * re-route"), through the real entry point, [AvailabilityViewModel.onNavigateToWaypoint], on the real
 * [FindWayToWaypointUseCase] over a fake engine, as [AvailabilityViewModelWaypointRouteTest] does: one trail east-west
 * through [Ground]'s origin, the walker 40 m north of it, the waypoint 500 m east and 60 m south. The reader is made up, so
 * each read waits on its own answer. Nothing a user sees changes: no screen reads the turns yet (2b-2).
 */
class AvailabilityViewModelWaypointRouteTurnsTest {

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
    private val tracker = object : LocationTracker { override val fixes = this@AvailabilityViewModelWaypointRouteTurnsTest.fixes }
    private val router = FakeTrailRouter(listOf(at(-3_000.0, 0.0) to at(3_000.0, 0.0)))

    private class Read(val wayHome: WayHome, val line: WayHomeLine, val routeData: File?) {
        val answer = CompletableDeferred<RouteTurns>()
        var cancelled = false
    }

    private val reads = mutableListOf<Read>()
    private val reader = RouteTurnsReader { wayHome, line, routeData ->
        val read = Read(wayHome, line, routeData).also { reads += it }
        try {
            read.answer.await()
        } catch (e: CancellationException) {
            read.cancelled = true
            throw e
        }
    }

    private fun turns(ways: Int) = RouteTurns.Read(emptyList(), trackAheadAfterNode = null, reading = WaysReading(0L, 1, ways))

    private fun viewModel(): AvailabilityViewModel {
        val find = FindWayToWaypointUseCase(FakeRegions(Ground.region()), FakeEngines(router), ErrorLog { _, _, _ -> }, AcceptEnds)
        return mapLayersViewModel(
            waypointNavigationRepository = InMemoryWaypointNavigation(),
            locationTracker = tracker,
            readWaypoint = { id -> Result.success(pin.takeIf { it.id == id }) },
            currentTime = CurrentTimeProvider { t + dispatcher.scheduler.currentTime },
            findWayToWaypoint = { from, to, accuracyMeters -> find(from, to, accuracyMeters) },
            readRouteTurns = reader,
        )
    }

    private fun AvailabilityViewModel.shown() = uiState.value.waypointRoute

    private suspend fun TestScope.navigating(): AvailabilityViewModel {
        val vm = viewModel()
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(0.0, 40.0)))
        runCurrent()
        vm.onNavigateToWaypoint("wp-1")
        runCurrent()
        return vm
    }

    @Test
    fun `H4 - the way's junctions are read from the folder it was found on, for the line followed, and held beside it`() = runTest(dispatcher) {
        val vm = navigating()
        assertTrue("by trail: ${vm.shown()?.hud}", vm.shown()?.hud is ReturnRoute.Ahead)
        assertNull("while they are read, none", vm.shown()?.turns)

        val read = reads.single()
        assertEquals("the route data the way was found on", Ground.FOLDER, read.routeData)
        assertSame("the line's own way", read.line.wayHome, read.wayHome)
        assertEquals("from the walker to the waypoint", at(0.0, 40.0), read.wayHome.line.first())

        read.answer.complete(turns(9))
        runCurrent()
        assertEquals(turns(9), vm.shown()?.turns)

        fixes.emit(fixAt(at(200.0, 2.0))) // walking the line: each fix keeps them
        runCurrent()
        assertEquals(turns(9), vm.shown()?.turns)
        assertEquals("one read for one line", 1, reads.size)
    }

    @Test
    fun `H4 - a stray's re-route reads the new line's junctions afresh, and the old line's are gone`() = runTest(dispatcher) {
        val vm = navigating()
        reads.single().answer.complete(turns(1))
        runCurrent()

        val strayed = at(300.0, 60.0) // 60 m north of the trail: one re-route
        fixes.emit(fixAt(strayed))
        runCurrent()

        assertEquals("a second line, a second read", 2, reads.size)
        assertEquals("the new line starts at the stray", strayed, reads[1].wayHome.line.first())
        assertNull("the old line's turns are not the new line's", vm.shown()?.turns)

        reads[1].answer.complete(turns(2))
        runCurrent()
        assertEquals(turns(2), vm.shown()?.turns)
    }

    @Test
    fun `H4 - a read still running when the re-route's line comes is cancelled and never shown`() = runTest(dispatcher) {
        val vm = navigating()
        val first = reads.single()

        fixes.emit(fixAt(at(300.0, 60.0)))
        runCurrent()
        first.answer.complete(turns(1))
        runCurrent()

        assertEquals(true, first.cancelled)
        assertNull(vm.shown()?.turns)
    }
}
