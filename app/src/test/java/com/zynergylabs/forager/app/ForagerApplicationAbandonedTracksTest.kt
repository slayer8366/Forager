package com.zynergylabs.forager.app

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-09-28-400, Amendment 3, Part 3b, with the real application, container and
 * database. The owner's path 1, "A": a track nothing is recording any more becomes a normal
 * finished track, ended at its last recorded point.
 *
 * ## What launches the sweep, and why this class changed
 *
 * It was first launched from `ForagerApplication.onCreate`, and these tests drove `onCreate`. That
 * made the app's database open at every app start, which nothing had done before, and an existing
 * test that replaces the database file (`ForagerDatabaseDestructiveFallbackTest`) then failed in 7
 * runs of 10. The planner's second ruling moved the launch: **once per process, when the first
 * recording ViewModel is created.** `onCreate` only notes the process's start time. So here the
 * rows are written, `onCreate` is shown to end nothing, and then a ViewModel is built from the
 * container the way `MainActivity`'s factory builds it.
 *
 * An earlier version of these tests dated rows an hour back while `onCreate` still swept. The
 * first app start's own sweep could then reach them before the start under test did, and a revert
 * check failed for a reason that was not its edit. That is how the interference was first seen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForagerApplicationAbandonedTracksTest {

    private fun point(t: Long) = TrackPoint(lat = 45.0, lng = -122.0, altitude = null, accuracyMeters = 5f, timestampEpochMillis = t)

    private fun ForagerApplication.endOf(trackId: String): Long? = runBlocking { container.trackRepository.getById(trackId) }.getOrThrow()?.endedAtEpochMillis

    /** Built as `MainActivity`'s factory builds it, from the container. */
    private fun recordingViewModel(app: ForagerApplication): TrackRecordingViewModel {
        val container = app.container
        return TrackRecordingViewModel(
            container.trackRepository,
            container.startTrackUseCase,
            container.getWaypointsUseCase,
            container.createWaypointUseCase,
            container.deleteWaypointUseCase,
            container.computeReturnToStartUseCase,
            container.locationTracker,
            container.getTracksUseCase,
            container.returnWatch,
            container.alertAudibility,
            deleteTrack = container.deleteTrackUseCase,
            getTrackOriginWaypoint = container.getTrackOriginWaypointUseCase,
            alreadyRecordingMessage = "Forager is already recording a track. Stop it before starting another.",
            abandonedTrackSweepOnce = container.abandonedTrackSweepOnce,
        )
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(25L)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `app start alone ends nothing, and the first recording ViewModel ends a track an earlier process left open, at its last stored point`() {
        val app = ApplicationProvider.getApplicationContext<ForagerApplication>()
        val hourAgo = System.currentTimeMillis() - 3_600_000L
        runBlocking {
            val tracks = app.container.trackRepository
            // Left open by an earlier process, with stored points: the one to end.
            tracks.create(Track(id = "stuck", name = null, startedAtEpochMillis = hourAgo, endedAtEpochMillis = null, points = emptyList())).getOrThrow()
            tracks.appendPoints("stuck", listOf(point(hourAgo + 60_000L), point(hourAgo + 120_000L))).getOrThrow()
            // Left open with no stored point: ended at its own start time (the owner's "Option A", Part 3c).
            tracks.create(Track(id = "empty", name = null, startedAtEpochMillis = hourAgo, endedAtEpochMillis = null, points = emptyList())).getOrThrow()
            // Already finished: its end must not move.
            tracks.create(Track(id = "finished", name = null, startedAtEpochMillis = hourAgo, endedAtEpochMillis = hourAgo + 30_000L, points = emptyList())).getOrThrow()
            tracks.appendPoints("finished", listOf(point(hourAgo + 90_000L))).getOrThrow()
        }

        // A new process: the same onCreate, a new container over the same database file.
        app.onCreate()
        runBlocking {
            // Started in the new process: its start is later than the moment onCreate noted.
            val later = System.currentTimeMillis() + 60_000L
            app.container.trackRepository.create(Track(id = "new", name = null, startedAtEpochMillis = later, endedAtEpochMillis = null, points = emptyList())).getOrThrow()
            app.container.trackRepository.appendPoints("new", listOf(point(later + 1_000L))).getOrThrow()
        }
        Thread.sleep(300)
        assertNull("app start alone ends nothing: onCreate does not query the database", app.endOf("stuck"))

        val viewModel = recordingViewModel(app)

        val deadline = System.currentTimeMillis() + 10_000
        while (app.endOf("stuck") == null && System.currentTimeMillis() < deadline) settle()
        assertEquals("the track left open by an earlier process is ended at its last stored point", hourAgo + 120_000L, app.endOf("stuck"))
        while (viewModel.uiState.value.tracks.firstOrNull { it.id == "stuck" }?.endedAtEpochMillis == null && System.currentTimeMillis() < deadline) settle()
        assertEquals(
            "and the screen that launched the sweep shows it finished without Records being reopened",
            hourAgo + 120_000L,
            viewModel.uiState.value.tracks.firstOrNull { it.id == "stuck" }?.endedAtEpochMillis,
        )
        assertEquals("a track with no stored point is ended at its own start time", hourAgo, app.endOf("empty"))
        assertEquals(
            "and the screen shows it finished too",
            hourAgo,
            viewModel.uiState.value.tracks.firstOrNull { it.id == "empty" }?.endedAtEpochMillis,
        )
        assertNull("a track started in this process is left open", app.endOf("new"))
        assertEquals("a finished track keeps its end", hourAgo + 30_000L, app.endOf("finished"))
    }

    /** The container's own wiring: the sweep it holds asks the container's own watch which track to leave alone. */
    @Test
    fun `the container's sweep leaves open the track the container's watch is for`() {
        val app = ApplicationProvider.getApplicationContext<ForagerApplication>()
        val hourAgo = System.currentTimeMillis() - 3_600_000L
        runBlocking {
            val tracks = app.container.trackRepository
            tracks.create(Track(id = "watched", name = null, startedAtEpochMillis = hourAgo, endedAtEpochMillis = null, points = emptyList())).getOrThrow()
            tracks.appendPoints("watched", listOf(point(hourAgo + 60_000L))).getOrThrow()
            tracks.create(Track(id = "stuck", name = null, startedAtEpochMillis = hourAgo, endedAtEpochMillis = null, points = emptyList())).getOrThrow()
            tracks.appendPoints("stuck", listOf(point(hourAgo + 60_000L))).getOrThrow()
        }
        app.container.returnWatch.begin("watched", TrackRecordingMode.HIGH_ACCURACY)
        try {
            val sweep = runBlocking { app.container.abandonedTrackSweepOnce.runOnce() }!!.getOrThrow()

            assertEquals(listOf("stuck"), sweep.ended.map { it.trackId })
            assertEquals(hourAgo + 60_000L, app.endOf("stuck"))
            assertNull("the watch's track is left open", app.endOf("watched"))
        } finally {
            app.container.returnWatch.end(null)
        }
    }
}
