package com.zynergylabs.forager.app.service

import android.Manifest
import android.app.Application
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.AppContainer
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.domain.KeptTail
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.domain.recordingLineOf
import kotlinx.coroutines.runBlocking
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
 * Dispatch 2026-10-26-05 (RECORD -1651): **the recording line reaches the walker's newest kept point**, written or not.
 *
 * The real [TrackRecordingService] with the production [RecordingWatches], the real container (its
 * `recordingKeptTail`) and Room repository, and the platform's location path, as
 * `TrackRecordingServiceWayHomeNewestPointTest` has them. Twenty-three fixes: the first twenty are written (the
 * service's batch of 20), the last three are kept and pending. The precondition reads the store, so the case is the
 * one that can fail: three kept points not in the stored track. It also pins that nothing is written differently:
 * twenty stored, as before, until the Stop writes the rest.
 *
 * Every test stops what it started inside its own body, as `TrackRecordingServiceSwipeAwayTest`'s header records.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackRecordingServiceRecordingLineTest {

    private lateinit var context: Application
    private lateinit var container: AppContainer
    private lateinit var shadowLocationManager: ShadowLocationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        container = (context as ForagerApplication).container
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        shadowLocationManager = shadowOf(locationManager)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
    }

    @Test
    fun `the recording line reaches the newest kept point, and what is written is unchanged`() {
        var controller: ServiceController<TrackRecordingService>? = null
        var stopped = false
        try {
            val trackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
            controller.create()
            controller.startCommand(0, 1)
            assertEquals("precondition: the service is listening", 1, awaitListenerCount(1))

            repeat(FIXES) { index -> simulateFix(index + 1) }
            assertEquals("precondition: the batch of 20 is written", FLUSH_BATCH_SIZE, awaitStoredCount(trackId, FLUSH_BATCH_SIZE))
            idleAndSettle()
            val stored = storedPoints(trackId)
            assertEquals(
                "the stored track is the batch of 20, as before: the last three kept points are not written yet",
                (1..FLUSH_BATCH_SIZE).map { fixLat(it) },
                stored.map { it.lat },
            )

            val tail = container.recordingKeptTail.tail.value
            assertEquals("the held points are this recording's", trackId, tail.trackId)
            assertEquals(
                "the line is drawn on every kept point, in order, once each: the twenty written and the three pending",
                (1..FIXES).map { fixLat(it) },
                recordingLineOf(stored, tail, trackId).map { it.lat },
            )

            controller.withIntent(stopIntent()).startCommand(0, 2)
            stopped = true
            assertEquals("the Stop writes the rest, as before", FIXES, awaitStoredCount(trackId, FIXES))
            assertEquals("the Stop forgets the held points", KeptTail.NONE, container.recordingKeptTail.tail.value)
        } finally {
            controller?.let { end(it, stopped) }
        }
    }

    private fun startIntent(trackId: String) =
        Intent(context, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_START
            putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
            putExtra(TrackRecordingService.EXTRA_MODE, TrackRecordingMode.HIGH_ACCURACY.name)
        }

    private fun stopIntent() = Intent(context, TrackRecordingService::class.java).setAction(TrackRecordingService.ACTION_STOP)

    private fun fixLat(index: Int) = 45.000 + index * 0.001

    /** As `TrackRecordingServiceWatchFailureTest.simulateFix`: 10 s and 0.001 degrees apart, so HIGH_ACCURACY keeps every one. */
    private fun simulateFix(index: Int) {
        shadowLocationManager.simulateLocation(
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = fixLat(index)
                longitude = -122.0
                accuracy = 5f
                time = FIRST_FIX_EPOCH_MILLIS + index * 10_000L
                elapsedRealtimeNanos = (index + 1) * 10_000_000_000L
            },
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun storedPoints(trackId: String) = runBlocking { container.trackRepository.getById(trackId) }.getOrThrow()!!.points

    private fun awaitStoredCount(trackId: String, atLeast: Int, timeoutMillis: Long = 10_000L): Int {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var count = storedPoints(trackId).size
        while (count < atLeast && System.currentTimeMillis() < deadline) {
            idleAndSettle()
            count = storedPoints(trackId).size
        }
        return count
    }

    private fun awaitListenerCount(expected: Int, timeoutMillis: Long = 10_000L): Int {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var count = shadowLocationManager.requestLocationUpdateListeners.size
        while (count != expected && System.currentTimeMillis() < deadline) {
            idleAndSettle()
            count = shadowLocationManager.requestLocationUpdateListeners.size
        }
        return count
    }

    private fun end(controller: ServiceController<TrackRecordingService>, stopped: Boolean) {
        val service = controller.get()
        if (!stopped && !shadowOf(service).isStoppedBySelf) {
            controller.withIntent(stopIntent()).startCommand(0, 99)
        }
        val deadline = System.currentTimeMillis() + 5_000L
        while (!shadowOf(service).isStoppedBySelf && System.currentTimeMillis() < deadline) Thread.sleep(25L)
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

    private companion object {
        /** Mirrors `TrackRecordingService`'s own private constant of the same name. */
        const val FLUSH_BATCH_SIZE = 20
        const val FIXES = 23
        const val FIRST_FIX_EPOCH_MILLIS = 1_790_000_000_000L
    }
}
