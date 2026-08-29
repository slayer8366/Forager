package com.forager.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
 * Field-test dispatch item 4's `Context`-taking top-level functions from `MainActivity.kt` —
 * split out of the Activity class the same way
 * `com.forager.app.ui.availability.directionsIntent`/`launchDirections` are, specifically so the
 * real notification and vibration this posts are testable under Robolectric without this app's
 * full DI graph (`AppContainer`, Room, the live location tracker).
 *
 * `sdk = [30]`, not this repo's usual `sdk = [36]`: Robolectric ships no shadow for
 * `VibratorManager` (the API 31+ path `vibrateOffTrackAlert` takes), only [ShadowSystemVibrator]
 * for the legacy `Vibrator` service this sdk exercises instead — seeing a real, introspectable
 * vibration here matters more than pinning the exact production API level, and
 * `vibrateOffTrackAlert`'s own two branches are otherwise identical in what they ask the vibrator
 * to do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class OffTrackAlertTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun `createOffTrackNotificationChannel creates a HIGH-importance, vibrating channel`() {
        createOffTrackNotificationChannel(activity)

        val manager = activity.getSystemService(NotificationManager::class.java)
        // "off_track_alert" mirrors MainActivity.kt's own private OFF_TRACK_CHANNEL_ID constant —
        // see that file's own doc comment for why a dedicated channel, not
        // TrackRecordingService's "track_recording" one, is what gets created here.
        val channel = manager.getNotificationChannel("off_track_alert")

        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel?.importance)
        assertTrue("expected the channel to have vibration enabled", channel?.shouldVibrate() ?: false)
    }

    @Test
    fun `postOffTrackAlert posts a real notification with the expected title, text, and channel`() {
        postOffTrackAlert(activity)

        val manager = activity.getSystemService(NotificationManager::class.java)
        // 1002 mirrors MainActivity.kt's own private OFF_TRACK_NOTIFICATION_ID constant — distinct
        // from TrackRecordingService's own ongoing-recording notification id (1001), so one never
        // overwrites the other.
        val notification = Shadows.shadowOf(manager).getNotification(1002)

        assertNotNull("expected a posted notification", notification)
        assertEquals("off_track_alert", notification.channelId)
        assertEquals(
            activity.getString(R.string.off_track_notification_title),
            NotificationCompat.getContentTitle(notification),
        )
        assertEquals(
            activity.getString(R.string.off_track_notification_text),
            NotificationCompat.getContentText(notification),
        )
    }

    @Config(sdk = [33])
    @Test
    fun `postOffTrackAlert does nothing when POST_NOTIFICATIONS is denied on API 33+`() {
        Shadows.shadowOf(activity).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        postOffTrackAlert(activity)

        val manager = activity.getSystemService(NotificationManager::class.java)
        assertNull(
            "a denied POST_NOTIFICATIONS must mean no notification, not a crash — the same " +
                "'declared, not forced' stance TrackRecordingService's own notification takes",
            Shadows.shadowOf(manager).getNotification(1002),
        )
    }

    @Test
    fun `vibrateOffTrackAlert triggers two short buzzes, not a single pulse`() {
        vibrateOffTrackAlert(activity)

        val vibrator = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val shadowVibrator = Shadows.shadowOf(vibrator)

        assertTrue(shadowVibrator.isVibrating)
        // 0, 250, 150, 250 mirrors MainActivity.kt's own private OFF_TRACK_VIBRATION_PATTERN_MILLIS
        // — see that constant's own doc comment for why two buzzes, not one.
        assertArrayEquals(longArrayOf(0L, 250L, 150L, 250L), shadowVibrator.pattern)
    }
}
