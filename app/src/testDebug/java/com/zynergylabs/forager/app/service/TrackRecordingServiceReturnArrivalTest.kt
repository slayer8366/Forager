package com.zynergylabs.forager.app.service

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.AppContainer
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.alert.OFF_TRACK_NOTIFICATION_ID
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import java.io.File
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
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager

/**
 * RECORD -844 and -845, through the real [TrackRecordingService] and the real [AppContainer]: a return
 * ends by itself at the start, off-track then neither evaluates nor posts on the drive-off, the
 * recording carries on, and Back by still ends on arrival. The bug: on the 2026-10-10 walk both phones
 * posted off-track during the drive-off, after Back by and the sundown alerts had judged arrival (RECORD
 * -841, -843).
 *
 * The fixes reach the service through the platform's own listener path, as in
 * `TrackRecordingServiceSwipeAwayTest`; the start point and Return are the calls the recording ViewModel
 * makes on the container's `ReturnWatch`. The readings are the off-track notification the real
 * `AndroidAlertDelivery` would post, the watch's state, and the stored track.
 *
 * Back by is evaluated by calling the container's `BackByWatch.tick()`, the call the service's 15 s loop
 * makes, so the test does not wait 15 s of real time; its `onFix` is fed by the real service.
 *
 * Fix times move 10 s a fix from the moment the test starts, so HIGH_ACCURACY's sampler keeps each one
 * and the fixes are fresh to the container's system clock.
 *
 * Every test stops what it started inside its own body, in a `finally`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackRecordingServiceReturnArrivalTest {

    private lateinit var context: Application
    private lateinit var container: AppContainer
    private lateinit var shadowLocationManager: ShadowLocationManager
    private var fixTime = 0L
    private var fixes = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        container = (context as ForagerApplication).container
        shadowOf(context).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        shadowLocationManager = shadowOf(locationManager)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        fixTime = System.currentTimeMillis() / 1_000L * 1_000L
    }

    /** Starts recording a new track whose start, origin waypoint and watch's start point, is at 45.0 N, 122.0 W. */
    private fun startedRecording(): Pair<ServiceController<TrackRecordingService>, String> {
        val trackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
        val origin = Waypoint(id = "origin-$trackId", lat = 45.0, lng = -122.0, altitude = null, name = "Start", note = "", createdAtEpochMillis = fixTime, trackId = trackId, designation = WaypointDesignation.ORIGIN)
        runBlocking {
            container.waypointRepository.save(origin).getOrThrow()
            container.trackRepository.setOriginWaypoint(trackId, origin.id).getOrThrow()
        }
        val controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
        controller.create().get()
        controller.startCommand(0, 1)
        assertEquals("precondition: the service is listening", 1, awaitListenerCount(1))
        container.returnWatch.setStartPoint(trackId, TrackPoint(45.0, -122.0, null, null, fixTime))
        return controller to trackId
    }

    @Test
    fun `Return ends by itself at the start, the drive-off posts no off-track, the recording carries on, and Back by ends on arrival`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            val (c, trackId) = startedRecording()
            controller = c
            assertTrue(container.backByWatch.set(trackId, System.currentTimeMillis() + 60 * 60_000L))

            listOf(0.0, 100.0, 200.0, 300.0).forEach(::simulateGpsFix) // the walk out
            assertTrue(container.returnWatch.startReturn(trackId))
            listOf(200.0, 100.0).forEach(::simulateGpsFix) // the way back, outside the 15 m circle
            assertTrue("precondition: still returning 100 m out", container.returnWatch.state.value.isReturning)

            simulateGpsFix(0.0) // at the start

            assertTrue("the return ended at the start", awaitState { !it.isReturning && it.arrivedAfterReturn })

            // The service's 15 s Back by tick, called here. The arrival fix reaches Back by's onFix just after the
            // return watch's, on the service's thread, so the tick is repeated until it has (10 s at most).
            val deadline = System.currentTimeMillis() + 10_000L
            do {
                runBlocking { container.backByWatch.tick() }
                idleAndSettle()
            } while (container.backByWatch.shown.value != null && System.currentTimeMillis() < deadline)
            assertNull("Back by ended on arrival, as it did while a return ran", container.backByWatch.shown.value)

            // The drive-off: 300 m a fix, away from the start, enough fixes for the service to flush 20 kept points.
            repeat(14) { i -> simulateGpsFix(400.0 + i * 300.0) }

            assertTrue("the recording carries on: the drive-off is stored", awaitPointCount(trackId, 20) >= 20)
            assertNull("no off-track notification on the drive-off", offTrackNotification())
            assertFalse(container.returnWatch.state.value.isOffTrack)
            assertNull("the track is still open", runBlocking { container.trackRepository.getById(trackId) }.getOrThrow()?.endedAtEpochMillis)
            assertFalse("the service is still running", shadowOf(c.get()).isStoppedBySelf)
            assertTrue("and still begun for the track", container.returnWatch.state.value.let { it.isBegun && it.trackId == trackId })
        } finally {
            controller?.let { end(it) }
        }
    }

    @Test
    fun `Return tapped at the start does not end there, straying posts off-track, and coming back to the start ends it`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            val (c, trackId) = startedRecording()
            controller = c
            simulateGpsFix(0.0)
            assertTrue(container.returnWatch.startReturn(trackId))
            repeat(3) { simulateGpsFix(5.0) } // half a minute standing at the start
            assertTrue("must leave first: still returning", container.returnWatch.state.value.isReturning)

            listOf(111.0, 222.0, 333.0).forEach(::simulateGpsFix) // away from the start and off the path, 20 s
            assertNotNull("straying before arrival still posts off-track", awaitOffTrackNotification())
            assertTrue("and the return goes on", container.returnWatch.state.value.isReturning)

            simulateGpsFix(0.0)
            assertTrue("left and came back: ended", awaitState { !it.isReturning && it.arrivedAfterReturn })
        } finally {
            controller?.let { end(it) }
        }
    }

    private fun startIntent(trackId: String) =
        Intent(context, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_START
            putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
            putExtra(TrackRecordingService.EXTRA_MODE, TrackRecordingMode.HIGH_ACCURACY.name)
        }

    private fun stopIntent() = Intent(context, TrackRecordingService::class.java).setAction(TrackRecordingService.ACTION_STOP)

    /** One GPS fix [metresNorth] of the start, 10 s after the last, through the platform's listener path. */
    private fun simulateGpsFix(metresNorth: Double) {
        fixes++
        fixTime += 10_000L
        shadowLocationManager.simulateLocation(
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 45.0 + metresNorth / 111_195.0
                longitude = -122.0
                accuracy = 5f
                time = fixTime
                elapsedRealtimeNanos = fixes * 10_000_000_000L
            },
        )
        idleAndSettle()
    }

    /** The watch is fed on the service's own thread; polls until [condition] holds, `false` on timeout. */
    private fun awaitState(timeoutMillis: Long = 10_000L, condition: (com.zynergylabs.forager.app.domain.ReturnWatchState) -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition(container.returnWatch.state.value)) return true
            idleAndSettle()
        }
        return condition(container.returnWatch.state.value)
    }

    private fun offTrackNotification() =
        shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(OFF_TRACK_NOTIFICATION_ID)

    private fun awaitOffTrackNotification(timeoutMillis: Long = 10_000L): android.app.Notification? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            offTrackNotification()?.let { return it }
            idleAndSettle()
        }
        return null
    }

    /** Every stored point, kept or not: the unfiltered read. Polls until at least [atLeast], and returns the count it last read. */
    private fun awaitPointCount(trackId: String, atLeast: Int, timeoutMillis: Long = 10_000L): Int {
        fun count() = runBlocking { container.trackRepository.getFullRecord(trackId) }.getOrThrow().size
        val deadline = System.currentTimeMillis() + timeoutMillis
        var n = count()
        while (n < atLeast && System.currentTimeMillis() < deadline) {
            idleAndSettle()
            n = count()
        }
        return n
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
