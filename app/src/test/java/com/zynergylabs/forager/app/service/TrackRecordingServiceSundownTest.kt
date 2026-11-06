package com.zynergylabs.forager.app.service

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.AppContainer
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.R
import com.zynergylabs.forager.app.alert.SUNDOWN_NOTIFICATION_ID
import com.zynergylabs.forager.app.domain.DEFAULT_DARKNESS_MARGIN_MINUTES
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
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager

/**
 * The real [TrackRecordingService] and the real [AppContainer]: **a sundown alert is delivered
 * with no Activity and no ViewModel in existence,** through the service's own `onStartCommand` and
 * the fixes the platform hands its collector (dispatch 2026-09-28-516). The reading is the
 * notification the real `AndroidAlertDelivery` posts on the sundown channel.
 *
 * The container's clock is the system clock and cannot be swapped, so the leave-by time is put
 * behind "now" by the setting rather than by the clock: a darkness margin of a whole day puts
 * sunset minus the margin before now at any hour, so the first evaluation with a position fires
 * leave-by at once (the dispatch's "start past the leave-by time"). The walk back is unknown here:
 * nothing is written to the track until the service's first flush, so there is no path to measure.
 * The timing of the three moments is [com.zynergylabs.forager.app.domain.SundownWatchTest]'s, on a
 * fake clock.
 *
 * Every test stops what it started inside its own body, as `TrackRecordingServiceSwipeAwayTest`'s
 * header records, and puts the margin back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackRecordingServiceSundownTest {

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

    private fun startIntent(trackId: String) =
        Intent(context, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_START
            putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
            putExtra(TrackRecordingService.EXTRA_MODE, TrackRecordingMode.HIGH_ACCURACY.name)
        }

    private fun stopIntent() = Intent(context, TrackRecordingService::class.java).setAction(TrackRecordingService.ACTION_STOP)

    @Test
    fun `with no Activity and no ViewModel, the service posts the leave-by alert once it has a position`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            runBlocking { container.sundownPreferencesRepository.setDarknessMarginMinutes(WHOLE_DAY_MINUTES) }.getOrThrow()
            val trackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
            controller.create().get()
            controller.startCommand(0, 1)
            assertEquals("precondition: the service is the only listener", 1, awaitListenerCount(1))
            assertNull("precondition: no sundown notification before a position", sundownNotification())

            simulateGpsFix()

            val notification = awaitSundownNotification()
            assertNotNull("expected the leave-by notification from the service alone", notification)
            val title = notification!!.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
            val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
            assertTrue("titled with the sunset time, not an order: '$title'", title.startsWith("Sunset at "))
            assertEquals(context.getString(R.string.sundown_walk_back_unknown), text)
        } finally {
            controller?.let { end(it) }
            runBlocking { container.sundownPreferencesRepository.setDarknessMarginMinutes(DEFAULT_DARKNESS_MARGIN_MINUTES) }
        }
    }

    /**
     * The owner's merge condition: a recording with **no live reading at all** still gets a sunset
     * time and its alerts, from the platform's last known position (-510's
     * `AndroidLastKnownLocationSource`, through the container). No fix is ever simulated; the
     * provider only holds a position, as a phone indoors would.
     */
    @Test
    fun `with no live reading at all, the service posts the leave-by alert from the last known position`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            runBlocking { container.sundownPreferencesRepository.setDarknessMarginMinutes(WHOLE_DAY_MINUTES) }.getOrThrow()
            shadowLocationManager.setLastKnownLocation(
                LocationManager.GPS_PROVIDER,
                Location(LocationManager.GPS_PROVIDER).apply {
                    latitude = 45.0
                    longitude = -122.0
                    accuracy = 30f
                    time = System.currentTimeMillis() - 2 * 60 * 60 * 1_000L
                },
            )
            val trackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
            controller.create().get()
            controller.startCommand(0, 1)

            val notification = awaitSundownNotification()
            assertNotNull("expected the leave-by notification with no live reading, from the last known position", notification)
            val title = notification!!.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
            val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
            assertTrue("titled with the sunset time: '$title'", title.startsWith("Sunset at "))
            assertEquals("never a walk back from a last known position", context.getString(R.string.sundown_walk_back_unknown), text)
        } finally {
            controller?.let { end(it) }
            shadowLocationManager.setLastKnownLocation(LocationManager.GPS_PROVIDER, null)
            runBlocking { container.sundownPreferencesRepository.setDarknessMarginMinutes(DEFAULT_DARKNESS_MARGIN_MINUTES) }
        }
    }

    @Test
    fun `with the alerts turned off, the same recording posts nothing`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            runBlocking { container.sundownPreferencesRepository.setDarknessMarginMinutes(WHOLE_DAY_MINUTES) }.getOrThrow()
            runBlocking { container.sundownPreferencesRepository.setAlertsEnabled(false) }.getOrThrow()
            val trackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
            controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
            controller.create().get()
            controller.startCommand(0, 1)
            assertEquals(1, awaitListenerCount(1))

            simulateGpsFix()

            assertNull("alerts off: nothing on the sundown channel", awaitSundownNotification(timeoutMillis = 2_000L))
        } finally {
            controller?.let { end(it) }
            runBlocking {
                container.sundownPreferencesRepository.setAlertsEnabled(true)
                container.sundownPreferencesRepository.setDarknessMarginMinutes(DEFAULT_DARKNESS_MARGIN_MINUTES)
            }
        }
    }

    /** One GPS fix stamped now, on a whole second, as the S22's GPS stamps them. */
    private fun simulateGpsFix() {
        shadowLocationManager.simulateLocation(
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 45.0
                longitude = -122.0
                accuracy = 5f
                time = System.currentTimeMillis() / 1_000L * 1_000L
                elapsedRealtimeNanos = 10_000_000_000L
            },
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun sundownNotification() =
        shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(SUNDOWN_NOTIFICATION_ID)

    private fun awaitSundownNotification(timeoutMillis: Long = 10_000L): Notification? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            sundownNotification()?.let { return it }
            idleAndSettle()
        }
        return null
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

    private companion object {
        const val WHOLE_DAY_MINUTES = 24 * 60
    }
}
