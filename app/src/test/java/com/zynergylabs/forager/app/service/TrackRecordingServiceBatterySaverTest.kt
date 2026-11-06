package com.zynergylabs.forager.app.service

import android.Manifest
import android.app.Application
import android.content.Intent
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.AppContainer
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.data.repository.DataStoreBatterySaverPreferenceRepository
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager

/**
 * The real [TrackRecordingService] and the real [AppContainer], with no Activity and no ViewModel: the
 * service asks the platform for a position at the interval the stored Battery saver switch says
 * (dispatch 2026-09-28-767). The stored value is written by another instance on the same file, whose
 * scope is ended first, the way a value stored before a restart is there for the next process; the
 * container's own instance is first read by the service. The reading is the GPS request's interval as
 * the platform holds it.
 *
 * Every test stops what it started inside its own body, as `TrackRecordingServiceSwipeAwayTest`'s
 * header records.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackRecordingServiceBatterySaverTest {

    private lateinit var context: Application
    private lateinit var container: AppContainer
    private lateinit var shadowLocationManager: ShadowLocationManager

    private fun dataStoreFile() = File(context.filesDir, "datastore/battery_saver_preferences.preferences_pb")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dataStoreFile().delete()
        container = (context as ForagerApplication).container
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        shadowLocationManager = shadowOf(locationManager)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
    }

    @After
    fun tearDown() {
        dataStoreFile().delete()
    }

    /** Stores [on] through an instance of its own, ended before the service starts. */
    private fun storeBeforeRestart(on: Boolean) = runBlocking {
        val job = SupervisorJob()
        DataStoreBatterySaverPreferenceRepository(context, CoroutineScope(Dispatchers.IO + job)).setEnabled(on).getOrThrow()
        job.cancelAndJoin()
    }

    private fun startIntent(trackId: String) =
        Intent(context, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_START
            putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
            putExtra(TrackRecordingService.EXTRA_MODE, TrackRecordingMode.HIGH_ACCURACY.name)
        }

    private fun stopIntent() = Intent(context, TrackRecordingService::class.java).setAction(TrackRecordingService.ACTION_STOP)

    @Test
    fun `with Battery saver stored on, the service asks for a GPS position every 5 seconds`() {
        storeBeforeRestart(true)
        assertEquals(listOf(5_000L), recordAndReadGpsIntervals(until = listOf(5_000L)))
    }

    /** The control: the same path with the switch stored off asks every second, so the case above is the switch's doing. */
    @Test
    fun `with Battery saver stored off, the service asks for a GPS position every second`() {
        storeBeforeRestart(false)
        assertEquals(listOf(1_000L), recordAndReadGpsIntervals(until = listOf(1_000L)))
    }

    /** Starts a recording through the service's own start command and reads the GPS requests once they reach [until] or 10 s pass. */
    private fun recordAndReadGpsIntervals(until: List<Long>): List<Long> {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            val trackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
            controller.create().get()
            controller.startCommand(0, 1)
            val deadline = System.currentTimeMillis() + 10_000L
            var intervals = gpsIntervals()
            while (intervals != until && System.currentTimeMillis() < deadline) {
                idleAndSettle()
                intervals = gpsIntervals()
            }
            // Settled a little longer, so a later re-registration would show.
            repeat(4) { idleAndSettle() }
            return gpsIntervals()
        } finally {
            controller?.let { end(it) }
        }
    }

    private fun gpsIntervals(): List<Long> = shadowLocationManager.getLocationRequests(LocationManager.GPS_PROVIDER).map { it.intervalMillis }

    private fun end(controller: ServiceController<TrackRecordingService>) {
        val service = controller.get()
        if (!shadowOf(service).isStoppedBySelf) {
            controller.withIntent(stopIntent()).startCommand(0, 99)
            val deadline = System.currentTimeMillis() + 5_000L
            while (!shadowOf(service).isStoppedBySelf && System.currentTimeMillis() < deadline) Thread.sleep(25L)
        }
        controller.destroy()
        idleAndSettle()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(250L)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun idleAndSettle() {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(25L)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
