package com.zynergylabs.forager.app.alert

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Vibrator
import android.text.format.DateFormat
import com.zynergylabs.forager.app.domain.Alert
import com.zynergylabs.forager.app.domain.AlertKind
import com.zynergylabs.forager.app.domain.BackByAlertDetail
import com.zynergylabs.forager.app.domain.SundownAlertDetail
import com.zynergylabs.forager.app.domain.WalkBack
import com.zynergylabs.forager.app.service.TrackRecordingService
import java.util.Date
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The back-by alert as the real [AndroidAlertDelivery] posts it (dispatch 2026-09-28-645, plan task
 * T15), and the vibration each kind plays since that dispatch's Amendment 1 (the owner: "Also fix the
 * sundown alerts to buzz three times"; Back by "buzzes three times too"). `sdk = [30]` for the reason
 * [AndroidAlertDeliveryTest] gives: Robolectric's legacy `Vibrator` is the one it can read back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class BackByAlertDeliveryTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val notificationManager get() = activity.getSystemService(NotificationManager::class.java)

    @Suppress("DEPRECATION")
    private val vibrator get() = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private val backByAt = 1_791_055_800_000L
    private val backBy = Alert(AlertKind.BACK_BY, overridesSilence = true, backBy = BackByAlertDetail(trackId = "t1", backByAtEpochMillis = backByAt))

    private val threePulses = longArrayOf(0L, 400L, 200L, 400L, 200L, 400L)

    @Test
    fun `its own channel, HIGH, with no channel vibration`() {
        AndroidAlertDelivery(activity)
        val channel = notificationManager.getNotificationChannel("back_by_alert")
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel?.importance)
        assertFalse("the direct alarm-usage call is the vibration", channel?.shouldVibrate() ?: true)
    }

    @Test
    fun `it reads You planned to be back by the time, on its own channel and id`() {
        AndroidAlertDelivery(activity).deliver(backBy)
        val notification = Shadows.shadowOf(notificationManager).getNotification(1004)
        assertNotNull("posted under its own id", notification)
        assertEquals("back_by_alert", notification.channelId)
        val time = DateFormat.getTimeFormat(activity).format(Date(backByAt))
        assertEquals("You planned to be back by $time", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    /** "I'm back" and "+30 min" address the recording service with the recording's id, as its own Stop action does. */
    @Test
    fun `its two buttons are I'm back and +30 min, each a service intent naming the recording`() {
        AndroidAlertDelivery(activity).deliver(backBy)
        val notification = Shadows.shadowOf(notificationManager).getNotification(1004)
        val actions = notification.actions
        assertEquals(2, actions.size)
        assertEquals("I'm back", actions[0].title.toString())
        assertEquals("+30 min", actions[1].title.toString())
        val expected = listOf(TrackRecordingService.ACTION_BACK_BY_IM_BACK, TrackRecordingService.ACTION_BACK_BY_LATER)
        actions.forEachIndexed { i, action ->
            val shadow = Shadows.shadowOf(action.actionIntent)
            assertEquals("a service intent", true, shadow.isServiceIntent)
            val intent = shadow.savedIntent
            assertEquals(TrackRecordingService::class.java.name, intent.component?.className)
            assertEquals(expected[i], intent.action)
            assertEquals("t1", intent.getStringExtra(TrackRecordingService.EXTRA_TRACK_ID))
        }
    }

    @Test
    fun `Back by vibrates three pulses with alarm usage`() {
        AndroidAlertDelivery(activity).deliver(backBy)
        val shadow = Shadows.shadowOf(vibrator)
        assertArrayEquals(threePulses, shadow.pattern)
        assertEquals(AudioAttributes.USAGE_ALARM, shadow.audioAttributesFromLastVibration?.usage)
    }

    /** Until this dispatch every alert played off-track's two; SUNDOWN_VIBRATION_PATTERN_MILLIS was declared and unused. */
    @Test
    fun `the sundown alerts vibrate three pulses, each of the three`() {
        val detail = SundownAlertDetail(backByAt, WalkBack.Unknown, backByAt - 3_600_000L)
        for (kind in listOf(AlertKind.HEADS_UP, AlertKind.LEAVE_BY, AlertKind.SUNSET)) {
            AndroidAlertDelivery(activity).deliver(Alert(kind, overridesSilence = true, sundown = detail))
            assertArrayEquals("$kind", threePulses, Shadows.shadowOf(vibrator).pattern)
        }
    }

    @Test
    fun `off-track keeps its two pulses`() {
        AndroidAlertDelivery(activity).deliver(Alert(AlertKind.OFF_TRACK, overridesSilence = false))
        assertArrayEquals(longArrayOf(0L, 250L, 150L, 250L), Shadows.shadowOf(vibrator).pattern)
    }
}
