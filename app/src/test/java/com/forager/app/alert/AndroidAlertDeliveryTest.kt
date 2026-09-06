package com.forager.app.alert

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.VibrationAttributes
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.forager.app.R
import com.forager.app.domain.Alert
import com.forager.app.domain.AlertKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The Android [com.forager.app.domain.AlertDelivery] — successor to `OffTrackAlertTest`, whose three
 * `Context`-taking functions moved here from `MainActivity.kt` (alert-delivery dispatch) so the
 * notification and the vibration are reachable without a composed tree. Same reason for
 * `sdk = [30]`: Robolectric ships no `VibratorManager` shadow (the API 31+ path production takes),
 * only the legacy `Vibrator` service, and at 30 the attribute-carrying overload is the
 * `AudioAttributes` one — so the usage is asserted **on the attribute** there. The API 33+
 * `VibrationAttributes` branch is driven through the `vibrateWith` seam with a legacy `Vibrator`;
 * the real 33+ production path (`VibratorManager.defaultVibrator`) is device-only, a coverage gap
 * named in the dispatch report rather than papered over.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AndroidAlertDeliveryTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val notificationManager get() = activity.getSystemService(NotificationManager::class.java)
    @Suppress("DEPRECATION")
    private val vibrator get() = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    /**
     * Item 2 (owner decision): the channel no longer vibrates — the direct alarm-usage call is the
     * vibration, and a channel vibration both dies with the ringer and double-buzzes. Immutable
     * after creation, hence the `_v2` id and the deletion of the old one. This assertion used to
     * read `shouldVibrate() == true` on `off_track_alert`; changed under the dispatch's Item 2, not
     * absorbed. Fails with `enableVibration(true)` restored.
     */
    @Test
    fun `the channel is HIGH importance, does not vibrate, and replaces the legacy vibrating channel`() {
        notificationManager.createNotificationChannel(
            NotificationChannel("off_track_alert", "legacy", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) },
        )

        AndroidAlertDelivery(activity)

        val channel = notificationManager.getNotificationChannel("off_track_alert_v2")
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel?.importance)
        assertFalse("the channel must not vibrate: the direct alarm-usage call is the vibration", channel?.shouldVibrate() ?: true)
        assertTrue("the pre-dispatch channel must be deleted", Shadows.shadowOf(notificationManager).isChannelDeleted("off_track_alert"))
    }

    @Test
    fun `an off-track alert posts the notification on the new channel with the expected title and text`() {
        AndroidAlertDelivery(activity).deliver(Alert(AlertKind.OFF_TRACK, overridesSilence = true))

        val notification = Shadows.shadowOf(notificationManager).getNotification(1002)
        assertNotNull("expected a posted notification", notification)
        assertEquals("off_track_alert_v2", notification.channelId)
        assertEquals(activity.getString(R.string.off_track_notification_title), NotificationCompat.getContentTitle(notification))
        assertEquals(activity.getString(R.string.off_track_notification_text), NotificationCompat.getContentText(notification))
    }

    @Config(sdk = [33])
    @Test
    fun `a denied POST_NOTIFICATIONS on API 33+ means no notification, not a crash`() {
        Shadows.shadowOf(activity).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        postOffTrackNotification(activity)

        assertNull(Shadows.shadowOf(notificationManager).getNotification(1002))
    }

    /**
     * Item 2's whole point, asserted on the attribute and not on the fact of a vibration: an
     * overriding alert vibrates with **alarm** usage, which does not go through the ringer. Fails
     * with the attributes dropped (null) or the usage set to notification.
     */
    @Test
    fun `an overriding alert vibrates the two-pulse pattern with alarm usage`() {
        AndroidAlertDelivery(activity).deliver(Alert(AlertKind.OFF_TRACK, overridesSilence = true))

        val shadow = Shadows.shadowOf(vibrator)
        assertTrue(shadow.isVibrating)
        assertArrayEquals(longArrayOf(0L, 250L, 150L, 250L), shadow.pattern)
        assertEquals(AudioAttributes.USAGE_ALARM, shadow.audioAttributesFromLastVibration?.usage)
    }

    /** The override is a parameter, not a constant: a non-overriding alert takes the ordinary, ringer-bound notification usage. */
    @Test
    fun `a non-overriding alert vibrates with notification usage`() {
        vibrateForAlert(activity, overridesSilence = false)

        assertEquals(AudioAttributes.USAGE_NOTIFICATION, Shadows.shadowOf(vibrator).audioAttributesFromLastVibration?.usage)
    }

    /** The API 33+ branch, through the seam only — see the class doc comment for why the production path there is device-only. */
    @Config(sdk = [33])
    @Test
    fun `on API 33 and above the vibration carries VibrationAttributes with alarm usage`() {
        vibrateWith(vibrator, overridesSilence = true)

        val attributes = Shadows.shadowOf(vibrator).vibrationAttributesFromLastVibration as? VibrationAttributes
        assertNotNull("expected VibrationAttributes on the API 33+ overload", attributes)
        assertEquals(VibrationAttributes.USAGE_ALARM, attributes?.usage)
    }
}
