package com.zynergylabs.forager.app.service

import android.Manifest
import android.app.Application
import android.content.Intent
import android.location.LocationManager
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.AppContainer
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.domain.NoEntryReferenceCounts
import com.zynergylabs.forager.app.domain.RecordingHalt
import com.zynergylabs.forager.app.domain.RecordingHaltReason
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.ui.track.RECORDING_COULD_NOT_START_MESSAGE
import com.zynergylabs.forager.app.ui.track.RECORDING_NEEDS_LOCATION_PERMISSION_MESSAGE
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * RECORD -660 (Amendment 1 to dispatch -658), item 4: when the recording service will not run a
 * recording the screen started, the screen shows not recording and says why.
 *
 * The permission case runs end to end through the real entry points: the ViewModel's own Record,
 * then the start intent `MainActivity`'s effect sends, delivered to the real service with location
 * permission withdrawn in between (the narrow window `MainActivity`'s second check exists for). The
 * service reports through the container's [com.zynergylabs.forager.app.domain.RecordingHalts], the one
 * the ViewModel is built with, as `MainActivity` builds it.
 *
 * The refused case cannot be produced here: Robolectric's `startForeground` does not refuse. Its
 * report is delivered to the same container object the service writes, and the ViewModel's handling
 * of it is read the same way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackRecordingHaltTest {

    private lateinit var context: Application
    private lateinit var container: AppContainer
    private val created = mutableListOf<TrackRecordingViewModel>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        container = (context as ForagerApplication).container
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(locationManager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
    }

    @Test
    fun `permission withdrawn before the service starts - the screen shows not recording, says why, and the track is ended`() {
        val vm = viewModelIn(ViewModelStore())
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
            val trackId = awaitActiveTrackId(vm)
            assertNotNull("precondition: Record made a track and the screen is recording", trackId)

            shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId!!))
            controller.create().get()
            controller.startCommand(0, 1)

            assertEquals("the screen shows not recording", true, await { vm.uiState.value.activeTrack == null })
            assertEquals(RECORDING_NEEDS_LOCATION_PERMISSION_MESSAGE, vm.uiState.value.startRecordingErrorMessage)
            assertEquals("the track is ended, not left open", true, await { trackEnded(trackId) })
            // Dispatch 2026-10-21-02: the halt reports the stored end too, so Records reloads its list. Awaited,
            // as the end is: the report follows the write on the service's coroutine.
            assertEquals(
                "the service reports the halted track's stored end, which the screen reloads its track list on",
                true,
                await { container.trackEnds.latest.value == trackId },
            )
        } finally {
            created.forEach { it.stopRecording() }
            controller?.destroy()
            settle()
        }
    }

    @Test
    fun `a foreground refusal reported by the service shows not recording, with the owner's words`() {
        val vm = viewModelIn(ViewModelStore())
        try {
            vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
            val trackId = awaitActiveTrackId(vm)
            assertNotNull(trackId)

            container.recordingHalts.report(RecordingHalt(trackId!!, RecordingHaltReason.FOREGROUND_REFUSED))

            assertEquals(true, await { vm.uiState.value.activeTrack == null })
            assertEquals(RECORDING_COULD_NOT_START_MESSAGE, vm.uiState.value.startRecordingErrorMessage)
        } finally {
            created.forEach { it.stopRecording() }
            settle()
        }
    }

    @Test
    fun `a report for another track changes nothing`() {
        val vm = viewModelIn(ViewModelStore())
        try {
            vm.startRecording(TrackRecordingMode.HIGH_ACCURACY)
            val trackId = awaitActiveTrackId(vm)
            assertNotNull(trackId)

            container.recordingHalts.report(RecordingHalt("some-other-track", RecordingHaltReason.FOREGROUND_REFUSED))
            settle()

            assertEquals(trackId, vm.uiState.value.activeTrack?.trackId)
            assertNull(vm.uiState.value.startRecordingErrorMessage)
        } finally {
            created.forEach { it.stopRecording() }
            settle()
        }
    }

    /** Built the way `MainActivity`'s factory builds it, with the container's halts. */
    private fun viewModelIn(store: ViewModelStore): TrackRecordingViewModel =
        ViewModelProvider(
            store,
            viewModelFactory {
                initializer {
                    TrackRecordingViewModel(
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
                        alreadyRecordingMessage = "already recording",
                        abandonedTrackSweepOnce = container.abandonedTrackSweepOnce,
                        recordingHalts = container.recordingHalts.latest,
                        entryReferenceCounts = NoEntryReferenceCounts,
                    ).also(created::add)
                }
            },
        )[TrackRecordingViewModel::class.java]

    private fun startIntent(trackId: String) =
        Intent(context, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_START
            putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
            putExtra(TrackRecordingService.EXTRA_MODE, TrackRecordingMode.HIGH_ACCURACY.name)
        }

    private fun trackEnded(trackId: String): Boolean =
        runBlocking { container.trackRepository.getById(trackId) }.getOrThrow()?.endedAtEpochMillis != null

    private fun awaitActiveTrackId(vm: TrackRecordingViewModel): String? {
        await { vm.uiState.value.activeTrack != null }
        return vm.uiState.value.activeTrack?.trackId
    }

    /** Polls [condition], running the main looper; `false` after the timeout, so a miss fails rather than hangs. */
    private fun await(timeoutMillis: Long = 10_000L, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            settle()
        }
        return condition()
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(25L)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
