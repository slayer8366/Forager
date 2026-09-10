package com.zynergylabs.forager.app.alert

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.AlertAudibilityState
import com.zynergylabs.forager.app.domain.RingerMode

/**
 * The Android [AlertAudibility]. Three reads, none needing a permission: `AudioManager.getRingerMode`
 * (API 1), `NotificationManager.getCurrentInterruptionFilter` (API 23 — reading the filter is free;
 * only *setting* it needs notification-policy access, which this app does not request), and
 * `NotificationManagerCompat.areNotificationsEnabled` (API 24). Read once at trip start by
 * `TrackRecordingViewModel.startRecording`; deliberately not a live watcher (alert-delivery
 * dispatch, Item 3.5).
 */
class AndroidAlertAudibility(context: Context) : AlertAudibility {
    private val appContext = context.applicationContext

    override fun current(): AlertAudibilityState {
        val audio = appContext.getSystemService(AudioManager::class.java)
        val ringerMode = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            else -> RingerMode.NORMAL
        }
        val filter = appContext.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        if (filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            // The platform could not say. Treated as "not on" rather than warning about a state
            // that may not exist — logged so the fallback is visible when it fires.
            Log.w(TAG, "Interruption filter unreadable (UNKNOWN); not warning about Do Not Disturb.")
        }
        val doNotDisturbOn = filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        return AlertAudibilityState(
            ringerMode = ringerMode,
            doNotDisturbOn = doNotDisturbOn,
            notificationsEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled(),
        )
    }

    private companion object {
        const val TAG = "AndroidAlertAudibility"
    }
}
