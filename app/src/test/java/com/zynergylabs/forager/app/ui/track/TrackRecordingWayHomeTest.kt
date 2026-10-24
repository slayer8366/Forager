package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.AbandonedTrackSweepOnce
import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.AlertAudibilityState
import com.zynergylabs.forager.app.domain.AlertDelivery
import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.CreateWaypointUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeleteTrackUseCase
import com.zynergylabs.forager.app.domain.DeleteWaypointUseCase
import com.zynergylabs.forager.app.domain.EndAbandonedTracksUseCase
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.GetTrackOriginWaypointUseCase
import com.zynergylabs.forager.app.domain.GetTracksUseCase
import com.zynergylabs.forager.app.domain.GetWaypointsUseCase
import com.zynergylabs.forager.app.domain.InMemoryKeptTrackPaths
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.RingerMode
import com.zynergylabs.forager.app.domain.RouteHome
import com.zynergylabs.forager.app.domain.RouteLinePartKind
import com.zynergylabs.forager.app.domain.StartTrackUseCase
import com.zynergylabs.forager.app.domain.TrackRepository
import com.zynergylabs.forager.app.domain.WaypointRepository
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLines
import com.zynergylabs.forager.app.domain.wayhome.WayHomeNote
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import com.zynergylabs.forager.app.domain.wayhome.WayHomeWatch
import com.zynergylabs.forager.app.ui.availability.ReturnRoute
import com.zynergylabs.forager.app.ui.availability.returnRouteOf
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The way home along trails through its real entry point (dispatch 2026-10-24-10 Amendment 1, PR C): Return is
 * [TrackRecordingViewModel.startReturn], the function the Return control calls (`MainActivity`'s `onToggleReturning`),
 * with a real [ReturnWatch] and a real [WayHomeWatch] whose search the test answers. Asserted on the state the screen
 * reads: the track at once, then the way home swapped in with its dotted and solid parts, the note on a fallback, a
 * second Return's new line, and Stop clearing it.
 *
 * Every recording is stopped inside the body, in a `finally` ([runRecordingTest]): the route tick and the poll are
 * unbounded loops (CLAUDE.md, the stalled-run pitfall).
 */
class TrackRecordingWayHomeTest {

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

    /** Each search waits for the test's answer; [cancelled] counts the searches cancelled while waiting. */
    private val answers = mutableListOf<CompletableDeferred<WayHomeResult>>()
    private var cancelled = 0

    private fun TestScope.wayHomeWatch() = WayHomeWatch(
        scope = backgroundScope,
        readTrack = tracks::getById,
        findWayHome = { _, _ ->
            val answer = CompletableDeferred<WayHomeResult>().also { answers += it }
            try {
                answer.await()
            } catch (e: CancellationException) {
                cancelled++
                throw e
            }
        },
        isReturning = { id -> returnWatch.state.value.let { it.trackId == id && it.isReturning } },
        errorLog = { _, _, _ -> },
    )

    private fun viewModel(wayHome: WayHomeWatch): TrackRecordingViewModel =
        wayHomeRecordingViewModel(tracks, tracker, returnWatch, wayHome, T).also(createdViewModels::add)

    private fun point(at: LatLng, i: Int) = TrackPoint(at.lat, at.lng, null, 5f, T + i * 1_000L)

    /** Recording, with a walk stored that ends where the walker stands, at the hairpin's beginning, and a fix there. */
    private suspend fun TestScope.recordingAtTheHairpin(viewModel: TrackRecordingViewModel) {
        viewModel.startRecording()
        runCurrent()
        tracks.appendPoints("track-1", listOf(point(Ground.at(0.0, -150.0), 0), point(Ground.at(0.0, -100.0), 1), point(Ground.at(0.0, -50.0), 2)))
        val here = Ground.at(0.0, -50.0)
        tracker.emitted.emit(LocationFix.Update(here.lat, here.lng, null, 5f, T + 3_000L, FixProvider.GPS))
        runCurrent()
    }

    private val line = WayHomeLine(WayHomeLines.hairpin)

    @Test
    fun `Return shows the track at once, then the way home swaps in, dotted and solid`() = runRecordingTest {
        val viewModel = viewModel(wayHomeWatch())
        recordingAtTheHairpin(viewModel)

        viewModel.startReturn()
        runCurrent()
        val first = viewModel.uiState.value
        assertTrue("the track at once: ${first.routeHome}", first.routeHome is RouteHome.Ahead)
        assertEquals("the track line has no parts", emptyList<Any>(), first.routeLine!!.aheadParts)
        assertEquals(1, answers.size)

        answers.single().complete(WayHomeResult.AlongTrails(WayHomeLines.hairpin))
        runCurrent()
        val swapped = viewModel.uiState.value
        val route = swapped.routeHome as RouteHome.Ahead
        assertEquals(listOf(RouteLinePartKind.TRACK, RouteLinePartKind.TRAIL, RouteLinePartKind.TRACK), swapped.routeLine!!.aheadParts.map { it.kind })
        assertEquals("the line at Return is the way home now", line.nodes, swapped.routeLine!!.atReturn)
        assertEquals("the HUD's figure is along the way home", 570.0, (returnRouteOf(route, swapped.wayHomeNote) as ReturnRoute.Ahead).routeMeters, 0.5)
        assertNull(swapped.wayHomeNote)
    }

    @Test
    fun `no route possible gives the track and its note`() = runRecordingTest {
        val viewModel = viewModel(wayHomeWatch())
        recordingAtTheHairpin(viewModel)
        viewModel.startReturn()
        runCurrent()
        answers.single().complete(WayHomeResult.TrackOnly(WayHomeNote.ROUTES_NOT_DOWNLOADED))
        runCurrent()
        val state = viewModel.uiState.value
        assertEquals(WayHomeNote.ROUTES_NOT_DOWNLOADED, state.wayHomeNote)
        assertEquals("the track line, solid", emptyList<Any>(), state.routeLine!!.aheadParts)
        assertEquals(WayHomeNote.ROUTES_NOT_DOWNLOADED, (returnRouteOf(state.routeHome, state.wayHomeNote) as ReturnRoute.Ahead).note)
    }

    @Test
    fun `a second Return cancels the first search, and Stop clears the way home`() = runRecordingTest {
        val watch = wayHomeWatch()
        val viewModel = viewModel(watch)
        recordingAtTheHairpin(viewModel)
        viewModel.startReturn()
        runCurrent()
        viewModel.stopReturn()
        viewModel.startReturn()
        runCurrent()
        assertEquals("the first search was cancelled", 1, cancelled)
        assertEquals(2, answers.size)
        answers[1].complete(WayHomeResult.AlongTrails(WayHomeLines.hairpin))
        runCurrent()
        val shown = viewModel.uiState.value.wayHome!!
        assertEquals("the second Return's way home is the one shown", watch.shown.value, shown)
        assertTrue(shown.result is WayHomeResult.AlongTrails)

        viewModel.stopRecording()
        runCurrent()
        assertNull(viewModel.uiState.value.wayHome)
        assertNull(watch.shown.value)
    }

    private companion object {
        const val T = 1_700_000_000_000L
    }
}
