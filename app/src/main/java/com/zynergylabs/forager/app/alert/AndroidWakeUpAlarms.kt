package com.zynergylabs.forager.app.alert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.domain.WakeUpAlarm
import com.zynergylabs.forager.app.domain.WakeUpAlarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * The Android [WakeUpAlarms] (dispatch 2026-09-28-796; the owner, RECORD -797: "Check on GPS + wake-up alarm
 * (Recommended)"). [AlarmManager.setAndAllowWhileIdle] with `RTC_WAKEUP`: **inexact**, so it needs no
 * permission (the exact `setExactAndAllowWhileIdle` needs `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`, which
 * -646 chose not to ask for); it is delivered in Doze too, possibly some minutes late, and Android limits
 * such alarms to about one per app every few minutes while idle, which one alarm per watch stays within.
 *
 * Delivered to [WakeUpAlarmReceiver], not to the recording service: the platform holds the processor awake
 * while a receiver handles its alarm (and through [BroadcastReceiver.goAsync] until it finishes), and this
 * app has no wake lock of its own to hold it awake while a service's coroutine runs. One PendingIntent per
 * [WakeUpAlarm], so scheduling again replaces the earlier time and cancelling finds it.
 */
class AndroidWakeUpAlarms(context: Context) : WakeUpAlarms {
    private val appContext = context.applicationContext

    override fun schedule(alarm: WakeUpAlarm, atEpochMillis: Long): Boolean = try {
        val manager = appContext.getSystemService(AlarmManager::class.java)
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMillis, pendingIntentFor(appContext, alarm))
        true
    } catch (e: Exception) {
        Log.w(TAG, "The $alarm wake-up could not be scheduled.", e)
        false
    }

    override fun cancel(alarm: WakeUpAlarm) {
        try {
            appContext.getSystemService(AlarmManager::class.java).cancel(pendingIntentFor(appContext, alarm))
        } catch (e: Exception) {
            Log.w(TAG, "The $alarm wake-up could not be cancelled.", e)
        }
    }

    internal companion object {
        const val TAG = "WakeUpAlarms"
        const val ACTION_WAKE_UP = "com.zynergylabs.forager.app.alert.action.WAKE_UP"
        const val EXTRA_ALARM = "com.zynergylabs.forager.app.alert.extra.ALARM"

        /** Distinct from the other PendingIntents' request codes (the service's 0 and 1, the back-by buttons' 2 and 3). */
        private fun requestCodeFor(alarm: WakeUpAlarm) = 40 + alarm.ordinal

        internal fun pendingIntentFor(context: Context, alarm: WakeUpAlarm): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCodeFor(alarm),
                Intent(context, WakeUpAlarmReceiver::class.java).setAction(ACTION_WAKE_UP).putExtra(EXTRA_ALARM, alarm.name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

/**
 * Hears [AndroidWakeUpAlarms]' alarms and hands each to its watch, the app's one instance, which records it
 * and evaluates once. Kept open with [goAsync] until the evaluation ends, at most [EVALUATION_LIMIT_MILLIS],
 * inside the platform's limit for a receiver; one that runs over is logged and ended, and the next timer
 * tick, fix or alarm evaluates again.
 */
class WakeUpAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarm = intent.getStringExtra(AndroidWakeUpAlarms.EXTRA_ALARM)?.let { name -> WakeUpAlarm.entries.firstOrNull { it.name == name } }
        if (intent.action != AndroidWakeUpAlarms.ACTION_WAKE_UP || alarm == null) {
            Log.w(AndroidWakeUpAlarms.TAG, "A wake-up with action '${intent.action}' and alarm '${intent.getStringExtra(AndroidWakeUpAlarms.EXTRA_ALARM)}' was ignored.")
            return
        }
        val container = (context.applicationContext as ForagerApplication).container
        val pending = goAsync()
        scope.launch {
            try {
                withTimeout(EVALUATION_LIMIT_MILLIS) {
                    when (alarm) {
                        WakeUpAlarm.BACK_BY -> container.backByWatch.onAlarm()
                        WakeUpAlarm.SUNDOWN -> container.sundownWatch.onAlarm()
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(AndroidWakeUpAlarms.TAG, "The $alarm wake-up's evaluation ran past ${EVALUATION_LIMIT_MILLIS} ms and was ended.", e)
            } catch (e: Exception) {
                Log.w(AndroidWakeUpAlarms.TAG, "The $alarm wake-up's evaluation failed; the next tick tries again.", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Under the ten seconds Android gives a receiver's [goAsync]. */
        const val EVALUATION_LIMIT_MILLIS = 8_000L
    }
}
