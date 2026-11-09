package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.nextturn.JunctionClass
import com.zynergylabs.forager.app.domain.nextturn.JunctionJudgement
import com.zynergylabs.forager.app.domain.nextturn.JunctionKind
import com.zynergylabs.forager.app.domain.nextturn.NextTurn
import com.zynergylabs.forager.app.domain.nextturn.NextTurnBlank
import com.zynergylabs.forager.app.domain.nextturn.RouteJunction
import com.zynergylabs.forager.app.domain.nextturn.RouteTurns
import com.zynergylabs.forager.app.domain.nextturn.RouteTurnsReader
import com.zynergylabs.forager.app.domain.nextturn.TurnSide
import com.zynergylabs.forager.app.domain.nextturn.WaysReading
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import com.zynergylabs.forager.app.domain.wayto.AcceptEnds
import com.zynergylabs.forager.app.domain.wayto.FakeEngines
import com.zynergylabs.forager.app.domain.wayto.FakeTrailRouter
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Dispatch 2026-11-06-01, Amendment 1, J2 (RECORD -2183): "Call nextTurn(...) where the line and progress live
 * (TrackRecordingRouteTicks, AvailabilityWaypointRoute) and pass the result to the card." The way to a waypoint's next turn through
 * the real entry point, [AvailabilityViewModel.onNavigateToWaypoint], on the real [FindWayToWaypointUseCase] over a fake engine, as
 * `AvailabilityViewModelWaypointRouteTurnsTest` does: one trail east-west through [Ground]'s origin, the walker 40 m north of it, the
 * waypoint 500 m east and 60 m south. Read from what the card is given, the route's [ReturnRoute.Ahead.nextTurn].
 */
class AvailabilityViewModelWaypointNextTurnTest {

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
    private val tracker = object : LocationTracker { override val fixes = this@AvailabilityViewModelWaypointNextTurnTest.fixes }
    private val router = FakeTrailRouter(listOf(at(-3_000.0, 0.0) to at(3_000.0, 0.0)))

    private var readLine: WayHomeLine? = null
    private val answer = CompletableDeferred<RouteTurns>()
    private val reader = RouteTurnsReader { _, line, _ -> readLine = line; answer.await() }

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

    private suspend fun TestScope.navigating(): AvailabilityViewModel {
        val vm = viewModel()
        vm.onEnteredForeground()
        fixes.emit(fixAt(at(0.0, 40.0)))
        runCurrent()
        vm.onNavigateToWaypoint("wp-1")
        runCurrent()
        return vm
    }

    private fun AvailabilityViewModel.cardNextTurn(): NextTurn {
        val hud = uiState.value.waypointRoute?.hud
        assertTrue("by trail: $hud", hud is ReturnRoute.Ahead)
        return (hud as ReturnRoute.Ahead).nextTurn
    }

    @Test
    fun `J2 - the way to a waypoint's route carries the next turn once its junctions are read, worked out again at each fix`() = runTest(dispatcher) {
        val vm = navigating()
        assertEquals("while the junctions are read", NextTurn.Blank(NextTurnBlank.NOT_YET_KNOWN), vm.cardNextTurn())

        val line = readLine!!
        val node = line.nodes.size / 2
        answer.complete(
            RouteTurns.Read(
                listOf(RouteJunction(node, line.alongMeters[node], JunctionKind.JUNCTION, 3, JunctionJudgement(JunctionClass.FORK, keep = TurnSide.LEFT))),
                trackAheadAfterNode = null,
                reading = WaysReading(0L, 1, 3),
            ),
        )
        runCurrent()
        val first = vm.cardNextTurn()
        assertTrue("the fork, as soon as it is read: $first", first is NextTurn.Fork && first.keep == TurnSide.LEFT)

        fixes.emit(fixAt(at(150.0, 2.0))) // 150 m on along the trail: nearer the fork
        runCurrent()
        val later = vm.cardNextTurn()
        assertTrue("still the fork: $later", later is NextTurn.Fork)
        assertTrue("nearer: ${(later as NextTurn.Fork).metersAway} after ${(first as NextTurn.Fork).metersAway}", later.metersAway < first.metersAway - 100.0)
    }
}
