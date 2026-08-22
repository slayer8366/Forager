package com.forager.app.service

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.TrackRecordingMode
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [TrackRecordingService] driven through Robolectric's real [android.app.Service] lifecycle
 * ([Robolectric.buildService]/`ServiceController`, the same "verify what the platform API
 * actually does" discipline [com.forager.app.location.AndroidLocationProviderTest] uses for
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
 * No permission-granted counterpart here: [TrackRecordingService.startRecording] launches a real
 * coroutine collecting [com.forager.app.location.AndroidLocationTracker.fixes] on
 * `Dispatchers.Default`, outside this test's control — a first attempt at that test outlived this
 * class's own test method (never joined, no fix ever delivered to complete its collection) and
 * went on to throw against a torn-down `Context` deep inside the *next* test class's Robolectric
 * sandbox, failing an unrelated test with no connection to this one visible in its own stack trace.
 * That's a real, demonstrated instability, not a hypothetical one — removed rather than chased,
 * since the dispatch this fixes only requires the permission-absent path anyway, and this fix
 * changes nothing about the permission-granted path (`startRecording()` runs exactly as before).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackRecordingServiceTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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
}
