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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
