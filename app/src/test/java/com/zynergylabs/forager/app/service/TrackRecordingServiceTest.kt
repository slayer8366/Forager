package com.zynergylabs.forager.app.service

import android.Manifest
import android.app.Application
import android.content.Intent
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.domain.TrackRepository
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager

/**
 * [TrackRecordingService] driven through Robolectric's real [android.app.Service] lifecycle
 * ([Robolectric.buildService]/`ServiceController`, the same "verify what the platform API
 * actually does" discipline [com.zynergylabs.forager.app.location.AndroidLocationProviderTest] uses for
 * `LocationManager`), not a hand-built stand-in for the service — there's no interface to fake
 * here, only the real class.
 *
 * Covers the confirmed crash directly: a captured stack trace showed
 * `startForegroundWithLocationType()` throwing a `SecurityException` — "Starting FGS with type
 * location ... requires permissions ... ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION" — because
 * `onStartCommand()` called it unconditionally. `onStartCommand()`'s own `hasLocationPermission()`
 * check is what's under test: [android.app.Service.startForeground] must never be reached without
 * that permission, no matter what caller sent `ACTION_START` — the "defence in depth" half of the
 * fix (`MainActivity`'s own two gates cover the normal in-app path; see its own doc comment).
 *
 * ## The permission-granted path, and the instability that used to rule it out
 *
 * This class carried, for several dispatches, a note that there was "no permission-granted
 * counterpart here": [TrackRecordingService.startRecording] launches a real coroutine collecting
 * [com.zynergylabs.forager.app.location.AndroidLocationTracker.fixes] on `Dispatchers.Default`, outside a
 * test's control, and a first attempt at such a test outlived its own test method (never joined,
 * no fix ever delivered to complete its collection) and went on to throw against a torn-down
 * `Context` inside the *next* test class's Robolectric sandbox, failing an unrelated test with no
 * connection to this one visible in its stack trace. That is a demonstrated instability, not a
 * hypothetical one.
 *
 * The notification stop-action test below needs that path — there is no notification without a
 * started recording — so it defuses the instability rather than inheriting it, in two ways that are
 * both about *ending* the collection, never about faking the service:
 *
 * - **Both location providers are disabled** before the service starts, so
 *   `AndroidLocationTracker.fixes`' `callbackFlow` registers no listener at all and simply awaits
 *   close. Nothing is delivered, and nothing is left waiting on a delivery.
 * - **The recording is stopped inside the test body**, by the real `ACTION_STOP` the notification
 *   carries, and the assertion waits for that stop to complete before the controller is destroyed.
 *   The collection is cancelled with `recordingJob` on that path, so no coroutine outlives the
 *   method. This is the same shape as `TrackRecordingViewModelTest.runRecordingTest`'s rule (see
 *   CLAUDE.md) applied to the service: a started recording is stopped before the test returns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackRecordingServiceTest {

    private lateinit var context: Application
    private lateinit var trackRepository: TrackRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // The real container the service itself reaches through `application as ForagerApplication`
        // — the same TrackRepository instance, so what the stop path writes is what this reads back.
        trackRepository = (context as ForagerApplication).container.trackRepository
    }

    private fun startIntent(trackId: String = "track-1") =
        Intent(context, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_START
            putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
            putExtra(TrackRecordingService.EXTRA_MODE, TrackRecordingMode.BALANCED.name)
        }

    @Test
    fun `onStartCommand with no location permission never promotes to foreground, and does not crash`() {
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent())
        val service = controller.create().get()

        // The confirmed crash was an uncaught SecurityException thrown out of onStartCommand
        // itself, on the main thread — reaching the assertion below at all, with nothing thrown
        // out of startCommand(), is already most of what this test proves.
        controller.startCommand(0, 1)

        assertNull(
            "startForeground() must never be reached without location permission — that's the " +
                "exact call the confirmed crash's SecurityException came from",
            shadowOf(service).lastForegroundNotification,
        )

        controller.destroy()
    }

    /**
     * The notification shade's only affordance for a recording whose Activity is gone.
     *
     * This does not assert that a stop action *exists* and separately assert that `ACTION_STOP`
     * ends a track — two semantic claims about wiring that would both hold with the action pointed
     * at the wrong component, the wrong action string, or a `PendingIntent` the platform would
     * refuse. It takes the `PendingIntent` off the notification the service actually posted to
     * `startForeground`, unwraps the exact `Intent` the platform would deliver when a finger taps
     * that button, and feeds *that* `Intent` — nothing hand-built — back through
     * [android.app.Service.onStartCommand] via the `ServiceController`. What it asserts at the end
     * is the track's own row: `endedAtEpochMillis` non-null, which is `EndTrackUseCase`'s write and
     * nothing else in this file can produce.
     */
    @Test
    fun `the ongoing notification's stop action ends the recorded track`() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        disableLocationProviders()
        val trackId = "track-stopped-from-the-shade"
        runBlocking {
            trackRepository.create(
                Track(id = trackId, name = null, startedAtEpochMillis = 1_000L, endedAtEpochMillis = null, points = emptyList()),
            ).getOrThrow()
        }

        val controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
        val service = controller.create().get()
        controller.startCommand(0, 1)

        val notification = shadowOf(service).lastForegroundNotification
        assertNotNull("the service must be in the foreground before there is a notification to act on", notification)
        val stopAction = notification.actions.orEmpty().singleOrNull {
            it.title?.toString() == context.getString(com.zynergylabs.forager.app.R.string.track_recording_notification_stop_action)
        }
        assertNotNull(
            "the ongoing notification must carry a 'Stop recording' action — it is the only way to " +
                "end a recording whose Activity has been destroyed",
            stopAction,
        )

        // The Intent the platform itself would deliver on a tap, not one this test composed.
        val shadowPendingIntent = shadowOf(stopAction!!.actionIntent)
        assertTrue("the stop action must address the service, not an Activity that may not exist", shadowPendingIntent.isServiceIntent)
        val deliveredIntent = shadowPendingIntent.savedIntent
        assertEquals(TrackRecordingService.ACTION_STOP, deliveredIntent.action)
        assertEquals(TrackRecordingService::class.java.name, deliveredIntent.component?.className)

        controller.withIntent(deliveredIntent).startCommand(0, 2)

        assertNotNull(
            "the track must be ended after the notification's own stop Intent reaches onStartCommand",
            awaitEndedAt(trackId),
        )
        // The other half of "handled honestly": the service does not linger in the foreground with
        // its track already closed. Asserted after the row, so this never masks a missing end —
        // and waiting for it is also what guarantees stopRecording()'s coroutine has run to
        // completion before destroy(), rather than being cancelled mid-flight by scope.cancel()
        // and leaving work on Dispatchers.Default for the next test class's sandbox to inherit
        // (the failure mode this class's doc comment records).
        assertTrue(
            "the service must stop itself once the notification's stop has ended the track",
            awaitStoppedBySelf(service),
        )
        controller.destroy()
        drainBeforeTeardown()
    }

    /**
     * This is the only test in the repo that starts the service's permission-granted path, and that
     * path does real work on `Dispatchers.Default` — a shared JVM pool that outlives this sandbox.
     * `destroy()` *requests* cancellation; the continuations that carry it out, and any main-thread
     * work `stopSelf()` posted, still have to run somewhere. Drained here, deliberately, so they run
     * inside this test's own sandbox rather than arriving in the next test class's — the exact bleed
     * this class's doc comment records, and measured: without this, a full-suite run failed
     * `JournalTabTest`'s photo-pull assertion in 2 of 3 runs while the same suite without this test
     * was green in 5 of 5.
     */
    private fun drainBeforeTeardown() {
        shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(250L)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    /**
     * No provider enabled means `AndroidLocationTracker.fixes` registers no listener and awaits
     * close — see this class's doc comment. The stop path under test does not read a fix.
     */
    private fun disableLocationProviders() {
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val shadow: ShadowLocationManager = shadowOf(locationManager)
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
    }

    /**
     * `stopRecording()` ends the track from a coroutine on the service's own `Dispatchers.Default`
     * scope, so the write is not visible the instant `startCommand` returns. Polls the repository
     * for it rather than sleeping a fixed interval, and returns `null` on timeout so the failure
     * reads as "never ended" rather than as a hang.
     */
    /** As [awaitEndedAt], for `stopSelf()` — which `stopRecording()` calls after the track is ended. */
    private fun awaitStoppedBySelf(service: TrackRecordingService, timeoutMillis: Long = 5_000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (shadowOf(service).isStoppedBySelf) return true
            Thread.sleep(25L)
        }
        return false
    }

    private fun awaitEndedAt(trackId: String, timeoutMillis: Long = 10_000L): Long? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val endedAt = runBlocking { trackRepository.getById(trackId) }.getOrNull()?.endedAtEpochMillis
            if (endedAt != null) return endedAt
            Thread.sleep(25L)
        }
        return null
    }

    /**
     * **The defect this test exists for, stated as a failure a user can have.** A recording is
     * stopped from the notification shade. The Activity is alive but backgrounded. The user returns
     * to the app and the record button still says "recording", because
     * `TrackRecordingService.stopRecording()` and `TrackRecordingViewModel.stopRecording()` are two
     * different methods with the same name on two different classes, and the shade only ever calls
     * the first. `TrackRecordingUiState.isRecording` is `activeTrack != null`, an in-memory field
     * nothing repopulates from storage.
     *
     * **Why the existing stop-action test above cannot catch it.** That test asserts the track's own
     * row — `endedAtEpochMillis` non-null — which is exactly right for what it targets and is
     * structurally incapable of failing on this defect, because it never reads ViewModel or UI state
     * at all. A fix landed without this assertion would recreate the original condition: green suite,
     * broken device. This is the "check that never saw the data that could fail it" family in
     * `CLAUDE.md`, caught before rather than after.
     *
     * **The route is the real one, not a hand-built stand-in.** The track row is created by the
     * ViewModel's own `startRecording`, the same call `MainActivity`'s record button makes. The stop
     * is the `Intent` taken off the `PendingIntent` the service actually posted, unwrapped and fed
     * back through `onStartCommand` — the same route the test above establishes. The resume is
     * `onEnteredForeground()`, which is what `MainActivity`'s `DefaultLifecycleObserver` calls on
     * `ON_START`.
     *
     * The assertion **before** the resume is deliberate and is not redundant. Without it this test
     * could pass because something incidental cleared the state, rather than because the resync did.
     * It pins the current two-representation design; when the structural fix lands and `isRecording`
     * is derived from the row, that line is the one that should fail and force this to be re-read.
     */
    @Test
    fun `a stop from the shade leaves the UI reporting not recording once the Activity resumes`() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        disableLocationProviders()

        val container = (context as ForagerApplication).container
        val viewModel = TrackRecordingViewModel(
            container.trackRepository,
            container.startTrackUseCase,
            container.getWaypointsUseCase,
            container.createWaypointUseCase,
            container.deleteWaypointUseCase,
            container.computeReturnToStartUseCase,
            container.detectOffTrackUseCase,
            container.locationTracker,
            container.getTracksUseCase,
            container.alertDelivery,
            container.alertAudibility,
        )
        try {
            viewModel.startRecording()
            val trackId = awaitActiveTrackId(viewModel)
            assertNotNull(
                "the ViewModel must have created a track and set activeTrack before there is anything for the shade to stop",
                trackId,
            )

            val controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId!!))
            val service = controller.create().get()
            controller.startCommand(0, 1)

            val notification = shadowOf(service).lastForegroundNotification
            assertNotNull("the service must be in the foreground before there is a notification to act on", notification)
            val stopAction = notification.actions.orEmpty().singleOrNull {
                it.title?.toString() == context.getString(com.zynergylabs.forager.app.R.string.track_recording_notification_stop_action)
            }
            assertNotNull("the ongoing notification must carry a 'Stop recording' action", stopAction)
            val deliveredIntent = shadowOf(stopAction!!.actionIntent).savedIntent
            assertEquals(TrackRecordingService.ACTION_STOP, deliveredIntent.action)

            controller.withIntent(deliveredIntent).startCommand(0, 2)
            assertNotNull(
                "the track must be ended by the shade's own stop Intent before the UI claim below means anything",
                awaitEndedAt(trackId),
            )

            // The state the map control actually reads, before the resume. See this test's doc
            // comment: this line pins the two-representation design rather than asserting a wish.
            assertTrue(
                "precondition: with the track already ended, the ViewModel still believes it is recording — " +
                    "that divergence is the defect, and without it the assertion below proves nothing",
                viewModel.uiState.value.isRecording,
            )

            viewModel.onEnteredForeground()

            assertFalse(
                "after the Activity resumes, the state the record button reads (TrackRecordingUiState.isRecording, " +
                    "which is activeTrack != null) must agree with the track's own ended row — it did not, so a user " +
                    "returning to the app still sees a recording that stopped from the shade",
                awaitStillRecording(viewModel),
            )

            controller.destroy()
        } finally {
            // CLAUDE.md's unstopped-poll-loop rule applied to a Robolectric test: startRecording()
            // leaves beginPolling()'s unbounded delay loop and a fixes collection running on
            // viewModelScope, so a body that throws above must not leave either for the next
            // sandbox to inherit. Harmless when the resync already cleared them.
            viewModel.stopRecording()
            idleAndSettle()
            drainBeforeTeardown()
        }
    }

    /**
     * [TrackRecordingViewModel.startRecording] creates the track row in a coroutine, so `activeTrack`
     * is not set the instant the call returns. Polls rather than sleeping a fixed interval, and
     * returns `null` on timeout so a failure reads as "never started" rather than as a hang — the
     * same shape as [awaitEndedAt].
     */
    private fun awaitActiveTrackId(viewModel: TrackRecordingViewModel, timeoutMillis: Long = 10_000L): String? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            viewModel.uiState.value.activeTrack?.let { return it.trackId }
            idleAndSettle()
        }
        return null
    }

    /**
     * Whether the ViewModel still claims to be recording after giving the resync time to land.
     *
     * Polls rather than settling once. [TrackRecordingViewModel.onEnteredForeground] re-reads the
     * track's row, which is a database round-trip off the main thread, so its result is no more
     * visible the instant the call returns than [TrackRecordingViewModel.startRecording]'s write was
     * — and [awaitActiveTrackId] already had to poll for that one, in this same test. A single
     * `idle()` is what a first version of this test used, and it reported the fix as not working
     * when the fix was fine: the assertion simply ran before the read came back.
     *
     * Returns `true` on timeout, so a resync that genuinely never clears still fails the assertion
     * rather than hanging — the same "read as a failure, not as a hang" rule [awaitEndedAt] follows.
     */
    private fun awaitStillRecording(viewModel: TrackRecordingViewModel, timeoutMillis: Long = 10_000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (!viewModel.uiState.value.isRecording) return false
            idleAndSettle()
        }
        return true
    }

    /**
     * Runs whatever the main looper has due now, gives work on other dispatchers a moment to post
     * back, then drains again. `idle()` alone is not enough here: the repository writes run off the
     * main thread, so there is nothing queued to drain until they have posted their continuation.
     */
    private fun idleAndSettle() {
        shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(25L)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }
}
