package com.forager.app.alert

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.forager.app.R
import com.forager.app.domain.Alert
import com.forager.app.domain.AlertDelivery
import com.forager.app.domain.AlertKind

/**
 * The Android [AlertDelivery]: a notification for the visible record and the shade entry, and an
 * **independent** vibration issued with alarm or notification usage according to
 * [Alert.overridesSilence] — see [AlertDelivery]'s own doc comment for why the override is a
 * parameter and for the swipe-away hole this does not close.
 *
 * Moved here from `MainActivity.kt`'s three top-level functions (alert-delivery dispatch) so the
 * delivery is reachable from `TrackRecordingViewModel` without a composed tree. Built from the
 * application context, and creates its channel on construction (`AppContainer` builds it at
 * process start), so the channel exists in the app's notification settings before any alert.
 *
 * **Two limbs, one ringer-independent.** A notification's own vibration goes through the ringer
 * and dies in silent mode; the direct vibration does not have to. The channel is therefore created
 * **without** vibration (it used to have it, which also double-buzzed alongside the direct call),
 * and the direct call carries the usage. A channel's vibration setting is immutable once created,
 * so this is a **new channel id** ([OFF_TRACK_CHANNEL_ID], `_v2`) and the old one is deleted —
 * owner-accepted cost: any per-channel adjustment a user made to the old channel is gone, and
 * Android lists one deleted category in the app's notification settings.
 */
class AndroidAlertDelivery(context: Context) : AlertDelivery {
    private val appContext = context.applicationContext

    init {
        createOffTrackNotificationChannel(appContext)
    }

    override fun deliver(alert: Alert) {
        when (alert.kind) {
            AlertKind.OFF_TRACK -> {
                postOffTrackNotification(appContext)
                vibrateForAlert(appContext, overridesSilence = alert.overridesSilence)
            }
        }
    }
}

internal const val OFF_TRACK_CHANNEL_ID = "off_track_alert_v2"

/** The pre-dispatch channel, created with vibration enabled; deleted on every channel creation so a device that had it loses the double-buzz. */
internal const val LEGACY_OFF_TRACK_CHANNEL_ID = "off_track_alert"
internal const val OFF_TRACK_NOTIFICATION_ID = 1002

/** Two short buzzes, not one — more likely to be felt through fabric than a single pulse, still brief enough not to feel alarmist. */
internal val OFF_TRACK_VIBRATION_PATTERN_MILLIS = longArrayOf(0L, 250L, 150L, 250L)

internal fun createOffTrackNotificationChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
        OFF_TRACK_CHANNEL_ID,
        context.getString(R.string.off_track_notification_channel_name),
        // HIGH, not TrackRecordingService's own LOW — this is a safety alert meant to be noticed
        // on a pocketed phone, not a silent ongoing-status notice.
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        // Off on purpose: the vibration is the direct, alarm-usage call in vibrateForAlert, which
        // survives a silenced ringer; a channel vibration would not, and would buzz a second time.
        enableVibration(false)
    }
    manager.createNotificationChannel(channel)
    manager.deleteNotificationChannel(LEGACY_OFF_TRACK_CHANNEL_ID)
}

/**
 * Posting is best-effort: the same "declared, not forced" stance `TrackRecordingService`'s own
 * ongoing notification takes on POST_NOTIFICATIONS — a denial means no notification shows, not a
 * crash, and the vibration (a different, install-time VIBRATE permission) still runs. The user is
 * told about a denial once, at trip start — see `alertAudibilityWarning`.
 */
internal fun postOffTrackNotification(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val notification = NotificationCompat.Builder(context, OFF_TRACK_CHANNEL_ID)
        .setContentTitle(context.getString(R.string.off_track_notification_title))
        .setContentText(context.getString(R.string.off_track_notification_text))
        .setSmallIcon(R.drawable.ic_track_recording)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(OFF_TRACK_NOTIFICATION_ID, notification)
}

/** VIBRATE is a normal (install-time) permission — declared in AndroidManifest.xml, no runtime check needed. */
internal fun vibrateForAlert(context: Context, overridesSilence: Boolean) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vibrateWith(vibrator, overridesSilence)
}

/**
 * The attribute-carrying vibration, split from the vibrator lookup so the API 33+ branch can be
 * driven under Robolectric at all (its `VibratorManager` has no shadow; a plain `Vibrator` does).
 *
 * Two branches are forced by `minSdk 26` (from the SDK's own `api-versions.xml`):
 * `Vibrator.vibrate(VibrationEffect, VibrationAttributes)` exists from API 33, and before that the
 * only overload that carries a usage takes `AudioAttributes` (26–32, deprecated in 33). Alarm usage
 * is what makes the vibration independent of the ringer; notification usage is the ordinary,
 * ringer-bound kind — which one is [overridesSilence]'s call, per [AlertDelivery].
 *
 * **Coverage gap, real, not a formality:** production on API 31+ reaches this through
 * `VibratorManager.defaultVibrator`, which Robolectric cannot construct, so the 33+ branch is
 * tested only through this seam with a legacy `Vibrator`, and the real 33+ production path is
 * device-only.
 */
internal fun vibrateWith(vibrator: Vibrator, overridesSilence: Boolean) {
    val effect = VibrationEffect.createWaveform(OFF_TRACK_VIBRATION_PATTERN_MILLIS, -1)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val usage = if (overridesSilence) VibrationAttributes.USAGE_ALARM else VibrationAttributes.USAGE_NOTIFICATION
        vibrator.vibrate(effect, VibrationAttributes.Builder().setUsage(usage).build())
    } else {
        val usage = if (overridesSilence) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION
        @Suppress("DEPRECATION")
        vibrator.vibrate(
            effect,
            AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        )
    }
}
