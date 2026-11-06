package com.zynergylabs.forager.app.service

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.AppContainer
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.alert.BACK_BY_NOTIFICATION_ID
import com.zynergylabs.forager.app.domain.BACK_BY_LATER_MILLIS
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
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
 * The real [TrackRecordingService] and the real [AppContainer]: **the back-by alert is delivered with
 * no Activity and no ViewModel in existence**, and its two buttons work through the real notification
 * intents (dispatch 2026-09-28-645, plan task T15). The reading is the notification the real
 * `AndroidAlertDelivery` posts, the intents its actions carry, and the container's `BackByWatch`.
 *
 * The container's clock is the system clock and cannot be swapped, so the time is set a moment in the
 * past, just before the service begins (the watch keeps a time set early for the same track): the
 * service's first evaluation, at start, fires it. The timing on a fake clock is `BackByWatchTest`'s.
 *
 * Every test stops what it started inside its own body, as `TrackRecordingServiceSundownTest` does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackRecordingServiceBackByTest {

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

    /** Starts recording a new track with its Back by already past, and waits for the alert. */
    private fun recordingWithBackByPassed(): Triple<ServiceController<TrackRecordingService>, String, Notification> {
        val trackId = runBlocking { container.startTrackUseCase(null) }.getOrThrow().id
        assertTrue(container.backByWatch.set(trackId, System.currentTimeMillis() - 1_000L))
        val controller = Robolectric.buildService(TrackRecordingService::class.java, startIntent(trackId))
        controller.create().get()
        controller.startCommand(0, 1)
        val notification = awaitBackByNotification()
        assertNotNull("expected the back-by alert from the service alone", notification)
        return Triple(controller, trackId, notification!!)
    }

    /** The intent a tap on [notification]'s action [index] sends: the real PendingIntent's own. */
    private fun actionIntent(notification: Notification, index: Int): Intent = shadowOf(notification.actions[index].actionIntent).savedIntent

    @Test
    fun `with no Activity and no ViewModel, the service posts the back-by alert at the time`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            val (c, _, notification) = recordingWithBackByPassed()
            controller = c
            val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
            assertTrue("the owner's words: '$title'", title.startsWith("You planned to be back by "))
        } finally {
            controller?.let { end(it) }
        }
    }

    @Test
    fun `I'm back, through the notification's own intent, ends the reminder and the recording carries on`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            val (c, trackId, notification) = recordingWithBackByPassed()
            controller = c
            c.withIntent(actionIntent(notification, 0)).startCommand(0, 2)
            idleAndSettle()
            assertNull("the reminder is over", container.backByWatch.shown.value)
            assertNull("the alert is taken down", backByNotification())
            assertFalse("the recording carries on", shadowOf(c.get()).isStoppedBySelf)
            assertTrue("still recording this track", container.returnWatch.state.value.let { it.isBegun && it.trackId == trackId })
        } finally {
            controller?.let { end(it) }
        }
    }

    @Test
    fun `+30 min, through the notification's own intent, moves the time to 30 minutes from now`() {
        var controller: ServiceController<TrackRecordingService>? = null
        try {
            val (c, _, notification) = recordingWithBackByPassed()
            controller = c
            val before = System.currentTimeMillis()
            c.withIntent(actionIntent(notification, 1)).startCommand(0, 2)
            idleAndSettle()
            val after = System.currentTimeMillis()
            val at = container.backByWatch.shown.value?.backByAtEpochMillis
            assertNotNull(at)
            assertTrue("30 minutes from the tap: $at", at!! in before + BACK_BY_LATER_MILLIS..after + BACK_BY_LATER_MILLIS)
            assertNull("the alert is taken down", backByNotification())
            assertFalse("the recording carries on", shadowOf(c.get()).isStoppedBySelf)
        } finally {
            controller?.let { end(it) }
        }
    }

    @Test
    fun `stopping the recording ends the reminder and takes the alert down`() {
        val (controller, _, _) = recordingWithBackByPassed()
        end(controller)
        assertNull(container.backByWatch.shown.value)
        assertNull(backByNotification())
    }

    /** A button on an alert left from a recording that has ended: the service, started only to hear it, changes nothing and stops. */
    @Test
    fun `a button tapped after the recording has ended changes nothing, and the service stops`() {
        val (first, _, notification) = recordingWithBackByPassed()
        val imBack = actionIntent(notification, 0)
        end(first)
        val controller = Robolectric.buildService(TrackRecordingService::class.java, imBack)
        try {
            controller.create().get()
            controller.startCommand(0, 1)
            idleAndSettle()
            assertTrue("stopped itself", shadowOf(controller.get()).isStoppedBySelf)
            assertNull(container.backByWatch.shown.value)
            assertEquals("no location listener: nothing recording", 0, shadowLocationManager.requestLocationUpdateListeners.size)
        } finally {
            controller.destroy()
            idleAndSettle()
        }
    }

    /**
     * Process death (the verify report, item 6): the system's sticky restart arrives with no intent
     * and records nothing, so nothing of a Back by survives and nothing fires.
     */
    @Test
    fun `after the service is destroyed, a sticky restart with no intent has no Back by and posts nothing`() {
        val (controller, _, _) = recordingWithBackByPassed()
        controller.destroy()
        idleAndSettle()
        assertNull("ended with the service", container.backByWatch.shown.value)
        context.getSystemService(NotificationManager::class.java).cancelAll()

        val restarted = Robolectric.buildService(TrackRecordingService::class.java, null)
        try {
            restarted.create().get()
            restarted.startCommand(0, 1)
            assertNull("nothing posted after a restart", awaitBackByNotification(timeoutMillis = 2_000L))
            assertNull(container.backByWatch.shown.value)
        } finally {
            restarted.destroy()
            idleAndSettle()
        }
    }

    private fun backByNotification() =
        shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(BACK_BY_NOTIFICATION_ID)

    private fun awaitBackByNotification(timeoutMillis: Long = 10_000L): Notification? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            backByNotification()?.let { return it }
            idleAndSettle()
        }
        return null
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
