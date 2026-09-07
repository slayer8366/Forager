package com.forager.app.alert

import android.app.Activity
import android.app.NotificationManager
import android.media.AudioManager
import com.forager.app.domain.AlertAudibilityState
import com.forager.app.domain.RingerMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/** The three platform reads behind [com.forager.app.domain.AlertAudibility], each driven through its Robolectric shadow. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidAlertAudibilityTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val audioManager get() = activity.getSystemService(AudioManager::class.java)
    private val notificationManager get() = activity.getSystemService(NotificationManager::class.java)

    @Test
    fun `a normal ringer with no filter and notifications on reads as audible`() {
        audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL)
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        Shadows.shadowOf(notificationManager).setNotificationsEnabled(true)

        assertEquals(AlertAudibilityState(RingerMode.NORMAL, doNotDisturbOn = false, notificationsEnabled = true), AndroidAlertAudibility(activity).current())
    }

    @Test
    fun `a silenced ringer reads as SILENT and vibrate mode as VIBRATE`() {
        audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT)
        assertEquals(RingerMode.SILENT, AndroidAlertAudibility(activity).current().ringerMode)

        audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE)
        assertEquals(RingerMode.VIBRATE, AndroidAlertAudibility(activity).current().ringerMode)
    }

    @Test
    fun `any interruption filter other than ALL reads as Do Not Disturb on`() {
        for (filter in listOf(NotificationManager.INTERRUPTION_FILTER_PRIORITY, NotificationManager.INTERRUPTION_FILTER_ALARMS, NotificationManager.INTERRUPTION_FILTER_NONE)) {
            notificationManager.setInterruptionFilter(filter)
            assertEquals("filter $filter", true, AndroidAlertAudibility(activity).current().doNotDisturbOn)
        }
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        assertEquals(false, AndroidAlertAudibility(activity).current().doNotDisturbOn)
    }

    @Test
    fun `notifications switched off for the app read as disabled`() {
        Shadows.shadowOf(notificationManager).setNotificationsEnabled(false)
        assertEquals(false, AndroidAlertAudibility(activity).current().notificationsEnabled)
    }
}
