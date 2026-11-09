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
import com.zynergylabs.forager.app.alert.BACK_BY_NOTIFICATION_ID
import com.zynergylabs.forager.app.alert.OFF_TRACK_NOTIFICATION_ID
import com.zynergylabs.forager.app.alert.postOffTrackNotification
import com.zynergylabs.forager.app.alert.SUNDOWN_NOTIFICATION_ID
import com.zynergylabs.forager.app.alert.postSundownNotification
import com.zynergylabs.forager.app.alert.postBackByNotification
import com.zynergylabs.forager.app.domain.Alert
import com.zynergylabs.forager.app.domain.AlertKind
import com.zynergylabs.forager.app.domain.BackByAlertDetail
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import kotlinx.coroutines.runBlocking
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
 * A recording that ends leaves no sundown or back-by alert in the shade (RECORD -800; the owner: "Yes, add it
 * now (Recommended)"). On 2026-10-09 a leave-by posted at 17:45 still read "Sunset at 6:35 PM" after its
 * recording had ended.
 *
 * The real [TrackRecordingService] and [AppContainer]. The back-by alert is brought by the service itself, as
 * in `TrackRecordingServiceBackByLoopTest`; the sundown alert is posted with the production
 * [postSundownNotification], since what is tested is the ending, not the sundown decision. Ends are driven
 * through the service's own entries: the Stop intent, `onDestroy` with no Stop (a service torn down), and
 * the application's `onCreate` (a new process after a kill).
 *
 * Every test stops what it started inside its own body, in a `finally`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackRecordingServiceAlertClearTest {

    private lateinit var context: Application
    private lateinit var container: AppContainer
    private lateinit var shadowLocationManager: ShadowLocationManager

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
    }

    @Test
    fun `Stop takes down the sundown, back-by and off-track alerts`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            controller = recordingWithBothAlerts()
            controller.withIntent(stopIntent()).startCommand(0, 2)
            awaitStoppedBySelf(controller)
            idleAndSettle()
            assertNull("the sundown alert is still in the shade after Stop", notification(SUNDOWN_NOTIFICATION_ID))
            assertNull("the back-by alert is still in the shade after Stop", notification(BACK_BY_NOTIFICATION_ID))
            assertNull("the off-track alert is still in the shade after Stop", notification(OFF_TRACK_NOTIFICATION_ID))
        } finally {
            controller?.let { end(it) }
        }
    }

    @Test
    fun `a service destroyed without a Stop takes down all three alerts`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            controller = recordingWithBothAlerts()
            controller.destroy()
            destroyed = true
            idleAndSettle()
            assertNull("the sundown alert is still in the shade after onDestroy", notification(SUNDOWN_NOTIFICATION_ID))
            assertNull("the back-by alert is still in the shade after onDestroy", notification(BACK_BY_NOTIFICATION_ID))
            assertNull("the off-track alert is still in the shade after onDestroy", notification(OFF_TRACK_NOTIFICATION_ID))
        } finally {
            controller?.let { end(it) }
        }
    }

    @Test
    fun `a new process takes down alerts left from a recording whose process was killed`() {
        // A killed process runs no onDestroy: its alerts are simply there when the next process starts. They are
        // posted here with the production post functions, as the service's delivery posts them.
        postSundownAlert()
        assertTrue(
            postBackByNotification(
                context,
                Alert(AlertKind.BACK_BY, overridesSilence = true, backBy = BackByAlertDetail("killed-track", System.currentTimeMillis())),
            ),
        )
        idleAndSettle()
        assertNotNull(notification(SUNDOWN_NOTIFICATION_ID))
        assertNotNull(notification(BACK_BY_NOTIFICATION_ID))

        (context as ForagerApplication).onCreate()
        idleAndSettle()

        assertNull("the sundown alert is still in the shade after the process started", notification(SUNDOWN_NOTIFICATION_ID))
        assertNull("the back-by alert is still in the shade after the process started", notification(BACK_BY_NOTIFICATION_ID))
        assertNull("the off-track alert is still in the shade after the process started", notification(OFF_TRACK_NOTIFICATION_ID))
    }

    @Test
    fun `answering the back-by alert with I'm back leaves the sundown and off-track alerts and the recording`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            controller = recordingWithBothAlerts()
            val trackId = currentTrackId!!
            controller.withIntent(
                Intent(context, TrackRecordingService::class.java)
                    .setAction(TrackRecordingService.ACTION_BACK_BY_IM_BACK)
                    .putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId),
            ).startCommand(0, 3)
            idleAndSettle()
            assertNull("I'm back did not take the back-by alert down", notification(BACK_BY_NOTIFICATION_ID))
            assertNotNull("answering Back by took the sundown alert down too", notification(SUNDOWN_NOTIFICATION_ID))
            assertNotNull("answering Back by took the off-track alert down too", notification(OFF_TRACK_NOTIFICATION_ID))
            assertTrue("answering Back by stopped the recording", !shadowOf(controller.get()).isStoppedBySelf)
        } finally {
            controller?.let { end(it) }
        }
    }

    private var currentTrackId: String? = null
    private var destroyed = false

    /** A recording with its back-by alert brought by the service and a sundown alert posted. */
    private fun recordingWithBothAlerts(): ServiceController<TrackRecordingService> {
        val trackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
        currentTrackId = trackId
        val controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
        controller.create().get()
        controller.startCommand(0, 1)
        idleAndSettle()
        Thread.sleep(250L)
        idleAndSettle()
        simulateGpsFix()
        assertTrue("the watch takes the time", container.backByWatch.set(trackId, System.currentTimeMillis() + 1_000L))
        Thread.sleep(1_100L)
        simulateGpsFix()
        assertNotNull("the service did not bring the back-by alert", awaitNotification(BACK_BY_NOTIFICATION_ID, 25_000L))
        postSundownAlert()
        assertNotNull(notification(SUNDOWN_NOTIFICATION_ID))
        return controller
    }

    /** The sundown alert and the off-track alert, posted with the production post functions. */
    private fun postSundownAlert() {
        assertTrue(postSundownNotification(context, Alert(AlertKind.SUNSET, overridesSilence = true)))
        assertTrue(postOffTrackNotification(context))
        idleAndSettle()
        assertNotNull(notification(OFF_TRACK_NOTIFICATION_ID))
    }

    private fun startIntent(trackId: String) =
        Intent(context, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_START
            putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
            putExtra(TrackRecordingService.EXTRA_MODE, TrackRecordingMode.HIGH_ACCURACY.name)
        }

    private fun stopIntent() = Intent(context, TrackRecordingService::class.java).setAction(TrackRecordingService.ACTION_STOP)

    private var lastFixTime = 0L

    private fun simulateGpsFix() {
        lastFixTime = maxOf(System.currentTimeMillis() / 1_000L * 1_000L, lastFixTime + 1_000L)
        shadowLocationManager.simulateLocation(
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 45.0
                longitude = -122.0
                accuracy = 5f
                time = lastFixTime
                elapsedRealtimeNanos = lastFixTime * 1_000_000L
            },
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun notification(id: Int) =
        shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(id)

    private fun awaitNotification(id: Int, timeoutMillis: Long): android.app.Notification? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            notification(id)?.let { return it }
            idleAndSettle()
        }
        return notification(id)
    }

    private fun awaitStoppedBySelf(controller: ServiceController<TrackRecordingService>) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (!shadowOf(controller.get()).isStoppedBySelf && System.currentTimeMillis() < deadline) {
            idleAndSettle()
        }
    }

    private fun end(controller: ServiceController<TrackRecordingService>) {
        val service = controller.get()
        if (!shadowOf(service).isStoppedBySelf) {
            controller.withIntent(stopIntent()).startCommand(0, 99)
            awaitStoppedBySelf(controller)
        }
        if (!destroyed) controller.destroy()
        idleAndSettle()
        context.getSystemService(NotificationManager::class.java).cancelAll()
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
