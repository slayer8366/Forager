package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.Alert
import com.zynergylabs.forager.app.domain.AlertDelivery
import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.OffTrailState
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLines
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import com.zynergylabs.forager.app.domain.wayhome.WayHomeWatch
import com.zynergylabs.forager.app.ui.availability.navigationReadout
import com.zynergylabs.forager.app.ui.availability.returnRouteOf
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
 * The quiet off-trail state through its real entry point (dispatch 2026-10-26-13, plan step 7 PR 3; RECORD -1631):
 * Return is [TrackRecordingViewModel.startReturn], the function the Return control calls, with a real [ReturnWatch] and
 * a real [WayHomeWatch]; each fix goes to both watches in the recording service's order (`TrackRecordingService`'s fix
 * collector: the return watch first, the way home last). Asserted on what the screen reads: the HUD's status line
 * (`navigationReadout` of `returnRouteOf`, as `MainActivity` builds it) and the line the map draws
 * ([TrackRecordingUiState.routeLineShown]). And quiet: no alert is delivered on any path. The return watch is begun
 * and given the walk's kept points, as the service does, so its alert is live in this rig: the second test is the
 * positive control showing the rig sees an alert when there is one (far from both the trail and the track).
 *
 * Every recording is stopped inside the body, in a `finally` ([runRecordingTest]; CLAUDE.md, the stalled-run pitfall).
 */
class TrackRecordingOffTrailTest {

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
    private val alerts = mutableListOf<Alert>()
    private val returnWatch = ReturnWatch(ComputeReturnToStartUseCase(), AlertDelivery { alerts += it })
    private val answers = mutableListOf<CompletableDeferred<WayHomeResult>>()

    private fun TestScope.wayHomeWatch() = WayHomeWatch(
        scope = backgroundScope,
        readTrack = tracks::getById,
        findWayHome = { _, _ -> CompletableDeferred<WayHomeResult>().also { answers += it }.await() },
        isReturning = { id -> returnWatch.state.value.let { it.trackId == id && it.isReturning } },
        errorLog = { _, _, _ -> },
    )

    private fun point(at: LatLng, i: Int) = TrackPoint(at.lat, at.lng, null, 5f, T + i * 1_000L)

    /** One fix to both watches, as the service gives it, and to the screen's tracker; [second] after [T]. */
    private suspend fun TestScope.fix(watch: WayHomeWatch, east: Double, north: Double, second: Int) {
        val p = Ground.at(east, north)
        val candidate = TrackPoint(p.lat, p.lng, null, 5f, T + second * 1_000L)
        returnWatch.onFix(candidate, FixProvider.GPS)
        watch.onFix(candidate, FixProvider.GPS)
        tracker.emitted.emit(LocationFix.Update(p.lat, p.lng, null, 5f, T + second * 1_000L, FixProvider.GPS))
        runCurrent()
    }

    private fun statusLine(state: TrackRecordingUiState, second: Int): String {
        val here = Ground.at(0.0, -50.0)
        val liveFix = LocationFix.Update(here.lat, here.lng, null, 5f, T + second * 1_000L, FixProvider.GPS)
        val route = returnRouteOf(state.routeHome, state.wayHomeNote, state.wayHomeOffTrail)
        return navigationReadout(TrueHeadingReading.Available(0f), liveFix, state.originWaypoint!!, DistanceUnit.MILES, T + second * 1_000L, route = route).statusText
    }

    /** Recording, the walk out stored and kept (start at (0, 120), south to the trail at (0, 0), off it to (0, -50)), Return tapped, the way home found. */
    private suspend fun TestScope.returnedOffTheTrail(watch: WayHomeWatch): TrackRecordingViewModel {
        val viewModel = wayHomeRecordingViewModel(tracks, tracker, returnWatch, watch, T).also(createdViewModels::add)
        viewModel.startRecording()
        runCurrent()
        returnWatch.begin("track-1", TrackRecordingMode.HIGH_ACCURACY)
        val walk = listOf(point(Ground.at(0.0, 120.0), 0), point(Ground.at(0.0, 20.0), 1), point(Ground.at(0.0, 0.0), 2), point(Ground.at(0.0, -50.0), 3))
        tracks.appendPoints("track-1", walk)
        walk.forEach(returnWatch::onKeptPoint)
        // The first good fix at the walk's start, where the screen makes the origin waypoint (TrackRecordingAutoWaypoints),
        // then the walker where they stand. The first run of this test made the origin at (0, -50), its first fix, so the
        // HUD read "Arrived" (an empty status line) there: a rig fault, not the state under test.
        fix(watch, 0.0, 120.0, 0)
        fix(watch, 0.0, -50.0, 4)

        viewModel.startReturn()
        runCurrent()
        answers.single().complete(WayHomeResult.AlongTrails(WayHomeLines.hairpin))
        runCurrent()
        return viewModel
    }

    @Test
    fun `Return while off the trail goes quiet at 15 s, then Back on trail, then the usual line, with no alert`() = runRecordingTest {
        val watch = wayHomeWatch()
        val viewModel = returnedOffTheTrail(watch)
        assertTrue("the return watch is judging", returnWatch.state.value.isReturning)
        assertEquals("the origin is the walk's start", Ground.at(0.0, 120.0).lat, viewModel.uiState.value.originWaypoint!!.lat, 1e-9)
        assertEquals(OffTrailState.ON, viewModel.uiState.value.wayHomeOffTrail)

        // Still off the trail, on the dotted track back, for 15 s.
        for (s in 10..25) fix(watch, 0.0, -50.0, s)
        val off = viewModel.uiState.value
        assertEquals(OffTrailState.OFF, off.wayHomeOffTrail)
        assertEquals("Off trail · Return to Route along your track", statusLine(off, 25))
        assertTrue("the trail line dims", off.routeLineShown!!.trailDimmed)
        assertFalse("the line the tick keeps is not marked", off.routeLine!!.trailDimmed)

        // Follow the track to the trail and stay on it 10 s.
        for (s in 26..36) fix(watch, 20.0, 0.0, s)
        val back = viewModel.uiState.value
        assertEquals(OffTrailState.BACK_ON, back.wayHomeOffTrail)
        assertEquals("Back on trail", statusLine(back, 36))
        assertFalse("the trail line is back to full strength", back.routeLineShown!!.trailDimmed)

        for (s in 37..41) fix(watch, 40.0, 0.0, s)
        val usual = viewModel.uiState.value
        assertEquals(OffTrailState.ON, usual.wayHomeOffTrail)
        assertEquals("the HUD's usual line", "", statusLine(usual, 41))

        // On along the trail, 100 m from the path walked before Return, for 16 s: the alert counts the trail shown as on
        // course (AppContainer's wayHomeTrail; Sorrel's S-415-1), so the rig judges as the service does.
        for (s in 42..58) fix(watch, 100.0, 0.0, s)
        assertEquals(OffTrailState.ON, viewModel.uiState.value.wayHomeOffTrail)

        assertEquals("quiet: no alert on any of it", emptyList<Alert>(), alerts)
    }

    @Test
    fun `positive control - far from both the trail and the track, this rig does see the one alert`() = runRecordingTest {
        val watch = wayHomeWatch()
        returnedOffTheTrail(watch)
        for (s in 10..26) fix(watch, 300.0, -300.0, s)
        assertEquals(1, alerts.size)
    }

    private companion object {
        const val T = 1_700_000_000_000L
    }
}
