package com.zynergylabs.forager.app.alert

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.zynergylabs.forager.app.domain.BackgroundRun
import com.zynergylabs.forager.app.domain.BackgroundRunCheck

/**
 * The Android [BackgroundRunCheck] (dispatch 2026-09-28-626; Amendment 1, RECORD -627: "Only when
 * truly blocked"). One read, needing no permission: `ActivityManager.isBackgroundRestricted`
 * (API 28), the phone's "Restricted" battery mode for Forager.
 *
 * Not `PowerManager.isIgnoringBatteryOptimizations`: nearly every app is optimised, so that read
 * would prompt nearly everyone, and the owner chose the stronger signal. No restricted permission
 * (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) is used anywhere for this.
 *
 * Below API 28 the read does not exist: [BackgroundRun.UNSUPPORTED], not a guess.
 */
class AndroidBackgroundRunCheck(context: Context) : BackgroundRunCheck {
    private val appContext = context.applicationContext

    override fun current(): BackgroundRun {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return BackgroundRun.UNSUPPORTED
        val restricted = appContext.getSystemService(ActivityManager::class.java).isBackgroundRestricted
        return if (restricted) BackgroundRun.BLOCKED else BackgroundRun.ALLOWED
    }
}
