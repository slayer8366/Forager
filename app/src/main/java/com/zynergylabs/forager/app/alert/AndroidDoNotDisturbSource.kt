package com.zynergylabs.forager.app.alert

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.zynergylabs.forager.app.domain.DoNotDisturbFilter
import com.zynergylabs.forager.app.domain.DoNotDisturbSource

/**
 * The Android [DoNotDisturbSource] (dispatch 2026-09-28-685, Amendment 1, RECORD -694): `getCurrentInterruptionFilter`,
 * API 23, free to read; only setting it needs notification-policy access, which this app does not ask for. Read as each
 * alert's vibration is issued, to record whether Do Not Disturb drops it.
 */
class AndroidDoNotDisturbSource(context: Context) : DoNotDisturbSource {
    private val appContext = context.applicationContext

    override fun current(): DoNotDisturbFilter = when (val filter = appContext.getSystemService(NotificationManager::class.java).currentInterruptionFilter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> DoNotDisturbFilter.OFF
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> DoNotDisturbFilter.PRIORITY
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> DoNotDisturbFilter.ALARMS_ONLY
        NotificationManager.INTERRUPTION_FILTER_NONE -> DoNotDisturbFilter.TOTAL_SILENCE
        else -> {
            Log.w(TAG, "Interruption filter unreadable ($filter); the alert record does not check Do Not Disturb.")
            DoNotDisturbFilter.UNKNOWN
        }
    }

    private companion object {
        const val TAG = "AndroidDoNotDisturb"
    }
}
