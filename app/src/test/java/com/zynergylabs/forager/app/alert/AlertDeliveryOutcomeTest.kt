package com.zynergylabs.forager.app.alert

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.Alert
import com.zynergylabs.forager.app.domain.AlertDeliveryOutcome
import com.zynergylabs.forager.app.domain.AlertKind
import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.ReturnRecord
import com.zynergylabs.forager.app.domain.ReturnRecordEvent
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-09-28-451, the owner's "Report and catch": the Android delivery says whether it
 * posted its notification and issued its vibration, and an exception from either is caught and
 * reported instead of thrown, so it can no longer end the recording service's fix collection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertDeliveryOutcomeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val offTrack = Alert(AlertKind.OFF_TRACK, overridesSilence = false)

    @Test
    fun `a delivered alert reports its notification posted and its vibration issued`() {
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val outcome = AndroidAlertDelivery(context).deliverReporting(offTrack)
        assertEquals(AlertDeliveryOutcome(notificationPosted = true, notificationProblem = null, vibrated = true, vibrationProblem = null), outcome)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(Shadows.shadowOf(manager).getNotification(OFF_TRACK_NOTIFICATION_ID))
    }

    @Config(sdk = [33])
    @Test
    fun `a denied notification permission is reported, and the vibration still runs`() {
        Shadows.shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val outcome = AndroidAlertDelivery(context).deliverReporting(offTrack)
        assertEquals(AlertDeliveryOutcome(notificationPosted = false, notificationProblem = "POST_NOTIFICATIONS denied", vibrated = true, vibrationProblem = null), outcome)
    }

    @Test
    fun `a throwing notify is caught and reported, and the vibration still runs`() {
        var vibrated = false
        val delivery = AndroidAlertDelivery(context, { _, _ -> throw SecurityException("no") }, { _, _ -> vibrated = true })
        val outcome = delivery.deliverReporting(offTrack)
        assertEquals(AlertDeliveryOutcome(notificationPosted = false, notificationProblem = "SecurityException", vibrated = true, vibrationProblem = null), outcome)
        assertEquals(true, vibrated)
    }

    @Test
    fun `a throwing vibration is caught and reported`() {
        val delivery = AndroidAlertDelivery(context, { _, _ -> true }, { _, _ -> throw IllegalStateException("no vibrator") })
        assertEquals(AlertDeliveryOutcome(notificationPosted = true, notificationProblem = null, vibrated = false, vibrationProblem = "IllegalStateException"), delivery.deliverReporting(offTrack))
    }

    /**
     * The collection the service runs: every fix goes to the watch. A notify that throws used to
     * propagate out of the watch's onFix and end it. Now the walk goes on: the fix after the alert
     * is still measured, and the record has the delivery's outcome.
     */
    @Test
    fun `a throwing notify no longer ends the fix collection`() {
        val written = mutableListOf<ReturnRecordEvent>()
        val delivery = AndroidAlertDelivery(context, { _, _ -> throw SecurityException("no") }, { _, _ -> })
        val watch = ReturnWatch(ComputeReturnToStartUseCase(), delivery, ReturnRecord { written += it })
        watch.begin("track-1", TrackRecordingMode.HIGH_ACCURACY)
        watch.setStartPoint("track-1", TrackPoint(45.0, -122.0, null, 4f, 1_000L))
        watch.startReturn("track-1")
        val collection = (0..5).map { i -> TrackPoint(45.001 + i * 0.001, -122.0, null, 4f, 2_000L + i * 5_000L) }
        collection.forEach { watch.onFix(it, FixProvider.GPS) } // the fourth goes off and delivers; the collection carries on
        assertEquals("the last fix was measured", 666.0, watch.state.value.returnToStart!!.distanceMeters, 5.0)
        val delivered = written.filterIsInstance<ReturnRecordEvent.AlertDelivered>().single()
        assertEquals("SecurityException", delivered.outcome?.notificationProblem)
    }
}
