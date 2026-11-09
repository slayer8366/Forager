package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.AlertDelivery
import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.nextturn.JunctionClass
import com.zynergylabs.forager.app.domain.nextturn.JunctionJudgement
import com.zynergylabs.forager.app.domain.nextturn.JunctionKind
import com.zynergylabs.forager.app.domain.nextturn.NextTurn
import com.zynergylabs.forager.app.domain.nextturn.NextTurnBlank
import com.zynergylabs.forager.app.domain.nextturn.RouteJunction
import com.zynergylabs.forager.app.domain.nextturn.RouteTurns
import com.zynergylabs.forager.app.domain.nextturn.TurnBand
import com.zynergylabs.forager.app.domain.nextturn.TurnSide
import com.zynergylabs.forager.app.domain.nextturn.WaysReading
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLines
import com.zynergylabs.forager.app.domain.wayhome.WayHomeNote
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import com.zynergylabs.forager.app.domain.wayhome.WayHomeWatch
import com.zynergylabs.forager.app.ui.availability.ReturnRoute
import com.zynergylabs.forager.app.ui.availability.returnRouteOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
 * (TrackRecordingRouteTicks, AvailabilityWaypointRoute) and pass the result to the card." The way home's next turn through Return
 * ([TrackRecordingViewModel.startReturn], the function the Return control calls), with a real [ReturnWatch] and a real [WayHomeWatch]
 * whose search and junction read the test answers, read as `MainActivity` hands the card its route ([returnRouteOf] with the state's
 * [TrackRecordingUiState.nextTurn]).
 *
 * Every recording is stopped inside the body, in a `finally` ([runRecordingTest]): the route tick and the poll are unbounded loops
 * (CLAUDE.md, the stalled-run pitfall).
 */
class TrackRecordingNextTurnTest {

    private val dispatcher = StandardTestDispatcher()
    private val createdViewModels = mutableListOf<TrackRecordingViewModel>()

    @Before fun setUpMain() = Dispatchers.setMain(dispatcher)

    @After fun tearDownMain() = Dispatchers.resetMain()

    private fun runRecordingTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            createdViewModels.forEach { it.stopRecording() }
        }
    }

    private val tracks = WayHomeTestTracks()
    private val tracker = WayHomeTestTracker()
    private val returnWatch = ReturnWatch(ComputeReturnToStartUseCase(), AlertDelivery { })
    private val answers = mutableListOf<CompletableDeferred<WayHomeResult>>()

    /** The hairpin's line, as the watch builds it from the way home found; the turn is at its middle node, on the trail. */
    private val line = WayHomeLine(WayHomeLines.hairpin)
    private val turnNode = line.nodes.size / 2
    private val rightTurn = RouteTurns.Read(
        listOf(RouteJunction(turnNode, line.alongMeters[turnNode], JunctionKind.JUNCTION, 3, JunctionJudgement(JunctionClass.TURN, band = TurnBand.PLAIN, side = TurnSide.RIGHT))),
        trackAheadAfterNode = null,
        reading = WaysReading(0L, 1, 3),
    )

    private fun TestScope.wayHomeWatch() = WayHomeWatch(
        scope = backgroundScope,
        readTrack = tracks::getById,
        findWayHome = { _, _ -> CompletableDeferred<WayHomeResult>().also { answers += it }.await() },
        isReturning = { id -> returnWatch.state.value.let { it.trackId == id && it.isReturning } },
        errorLog = { _, _, _ -> },
        readTurns = { _, _, _ -> rightTurn },
    )

    private fun point(at: LatLng, i: Int) = TrackPoint(at.lat, at.lng, null, 5f, T + i * 1_000L)

    /** Recording, with a walk stored that ends where the walker stands, at the hairpin's beginning, a fix there, and Return. */
    private suspend fun TestScope.returningAtTheHairpin(): TrackRecordingViewModel {
        val viewModel = wayHomeRecordingViewModel(tracks, tracker, returnWatch, wayHomeWatch(), T).also(createdViewModels::add)
        viewModel.startRecording()
        runCurrent()
        tracks.appendPoints("track-1", listOf(point(Ground.at(0.0, -150.0), 0), point(Ground.at(0.0, -100.0), 1), point(Ground.at(0.0, -50.0), 2)))
        val here = Ground.at(0.0, -50.0)
        tracker.emitted.emit(LocationFix.Update(here.lat, here.lng, null, 5f, T + 3_000L, FixProvider.GPS))
        runCurrent()
        viewModel.startReturn()
        runCurrent()
        return viewModel
    }

    private fun TrackRecordingViewModel.cardRoute(): ReturnRoute = uiState.value.let { returnRouteOf(it.routeHome, it.wayHomeNote, it.wayHomeOffTrail, it.nextTurn) }

    @Test
    fun `J2 - on the way home along trails the card's route carries the next turn the tick works out from the line and the walker's place`() = runRecordingTest {
        val viewModel = returningAtTheHairpin()
        answers.single().complete(WayHomeResult.AlongTrails(WayHomeLines.hairpin))
        runCurrent()
        advanceTimeBy(5_001L) // the next route tick, with the junctions read
        runCurrent()

        val route = viewModel.cardRoute()
        assertTrue("by trail: $route", route is ReturnRoute.Ahead)
        val next = (route as ReturnRoute.Ahead).nextTurn
        assertTrue("the junction's turn: $next", next is NextTurn.Turn)
        next as NextTurn.Turn
        assertEquals(TurnBand.PLAIN, next.band)
        assertEquals(TurnSide.RIGHT, next.side)
        // From the needle's place near the line's start, along the line to the turn's node: no further than the node, and not before.
        assertTrue("metres to the turn, ${next.metersAway}, of ${line.alongMeters[turnNode]}", next.metersAway > line.alongMeters[turnNode] - 80.0 && next.metersAway <= line.alongMeters[turnNode])
    }

    @Test
    fun `E2 - on the way home along the walker's track (no trail route) the card's route carries a blank`() = runRecordingTest {
        val viewModel = returningAtTheHairpin()
        answers.single().complete(WayHomeResult.TrackOnly(WayHomeNote.ROUTES_NOT_DOWNLOADED))
        runCurrent()
        advanceTimeBy(5_001L)
        runCurrent()

        val route = viewModel.cardRoute()
        assertTrue("the track home: $route", route is ReturnRoute.Ahead)
        assertEquals(NextTurn.Blank(NextTurnBlank.NO_TRAIL_ROUTE), (route as ReturnRoute.Ahead).nextTurn)
    }

    private companion object {
        const val T = 1_700_000_000_000L
    }
}
