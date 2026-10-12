package com.zynergylabs.forager.app.alert

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zynergylabs.forager.app.R
import com.zynergylabs.forager.app.domain.Alert
import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.AlertDelivery
import com.zynergylabs.forager.app.domain.AlertDeliveryOutcome
import com.zynergylabs.forager.app.domain.AlertKind
import com.zynergylabs.forager.app.domain.WalkBack
import com.zynergylabs.forager.app.domain.DoNotDisturbSource
import com.zynergylabs.forager.app.domain.vibrationSkipReason
import com.zynergylabs.forager.app.service.TrackRecordingService
import java.util.Date

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
class AndroidAlertDelivery private constructor(
    context: Context,
    /** Posts the alert's notification; `false` when it could not (POST_NOTIFICATIONS denied). A seam for tests. */
    private val postNotification: (Context, Alert) -> Boolean,
    /**
     * The ringer, read as each vibration is issued, to record whether Android will play it
     * (dispatch 2026-09-28-685, fix 3). The trip-start warning's own seam, reused; faked in tests.
     */
    private val audibility: AlertAudibility,
    /** Do Not Disturb, read with the ringer (dispatch 2026-09-28-685, Amendment 1, RECORD -694). Faked in tests. */
    private val doNotDisturb: DoNotDisturbSource,
    /**
     * Issues the alert's vibration. Given the whole [Alert] since dispatch 2026-09-28-645 (Amendment 1),
     * so the pattern can follow the kind ([vibrationPatternFor]).
     *
     * Merge with dispatch 2026-09-28-685 (RECORD -687): the small fixes kept the tests' seam as
     * `(Context, Boolean)` and added the ringer and Do Not Disturb after it. Both are kept: the tests'
     * constructors below still take the `(Context, Boolean)` seam, adapted here, and only production
     * reads the kind. This one is private and its parameters are ordered apart from the five-argument
     * test constructor's, so the two do not clash once their function types are erased on the JVM.
     */
    private val vibrate: (Context, Alert) -> Unit,
) : AlertDelivery {
    constructor(context: Context) : this(
        context,
        ::postNotificationFor,
        AndroidAlertAudibility(context),
        AndroidDoNotDisturbSource(context),
        { c, alert -> vibrateForAlert(c, alert.overridesSilence, vibrationPatternFor(alert.kind)) },
    )

    /** The small fixes' test constructor (dispatch 2026-09-28-685): every seam, the vibration told only whether it overrides silence. */
    internal constructor(
        context: Context,
        postNotification: (Context, Alert) -> Boolean,
        vibrate: (Context, Boolean) -> Unit,
        audibility: AlertAudibility,
        doNotDisturb: DoNotDisturbSource,
    ) : this(context, postNotification, audibility, doNotDisturb, { c: Context, alert: Alert -> vibrate(c, alert.overridesSilence) })

    /**
     * The two seams tests replace most, with the platform's ringer and Do Not Disturb. Its own constructor, not defaults on
     * the one above, so a call ending in a trailing lambda still means the vibration (dispatch 2026-09-28-685).
     */
    internal constructor(
        context: Context,
        postNotification: (Context, Alert) -> Boolean,
        vibrate: (Context, Boolean) -> Unit,
    ) : this(context, postNotification, vibrate, AndroidAlertAudibility(context), AndroidDoNotDisturbSource(context))

    private val appContext = context.applicationContext

    init {
        createOffTrackNotificationChannel(appContext)
        createSundownNotificationChannel(appContext)
        createBackByNotificationChannel(appContext)
    }

    override fun deliver(alert: Alert) {
        deliverReporting(alert)
    }

    /**
     * The notification, then the vibration, each on its own: a failure of one does not stop the
     * other (dispatch 2026-09-28-451; the owner: "Report and catch"). An exception from either is
     * caught, logged and reported, never thrown. Before this it propagated into the recording
     * service's fix collection, which it ended. What is posted and vibrated is unchanged.
     */
    override fun deliverReporting(alert: Alert): AlertDeliveryOutcome {
        var notificationProblem: String? = null
        val posted = try {
            postNotification(appContext, alert).also { if (!it) notificationProblem = "POST_NOTIFICATIONS denied" }
        } catch (e: Exception) {
            Log.w(TAG, "The ${alert.kind} alert's notification could not be posted.", e)
            notificationProblem = e::class.simpleName
            false
        }
        var vibrationProblem: String? = null
        val vibrated = try {
            vibrate(appContext, alert)
            true
        } catch (e: Exception) {
            Log.w(TAG, "The ${alert.kind} alert's vibration could not be issued.", e)
            vibrationProblem = e::class.simpleName
            false
        }
        val skipped = if (vibrated) vibrationSkipped(alert) else null
        return AlertDeliveryOutcome(posted, notificationProblem, vibrated && skipped == null, vibrationProblem, skipped)
    }

    /**
     * Why Android will not play the vibration just issued ([vibrationSkipReason]), for the record
     * only: what was delivered is already decided and unchanged (dispatch 2026-09-28-685, fix 3; the
     * owner, RECORD -678; Do Not Disturb added by Amendment 1, RECORD -694). A reading that fails is logged and says nothing.
     */
    private fun vibrationSkipped(alert: Alert): String? {
        val filter = try {
            doNotDisturb.current()
        } catch (e: Exception) {
            Log.w(TAG, "Do Not Disturb could not be read; the ${alert.kind} alert's vibration is not checked against it.", e)
            null
        }
        val ringerMode = try {
            audibility.current().ringerMode
        } catch (e: Exception) {
            Log.w(TAG, "The ringer could not be read; the ${alert.kind} alert's vibration is not checked against it.", e)
            null
        }
        return vibrationSkipReason(alert.overridesSilence, filter, ringerMode)
    }

    private companion object {
        const val TAG = "AlertDelivery"
    }
}

/** The notification for [alert]'s kind; `false` when it could not be posted. */
internal fun postNotificationFor(context: Context, alert: Alert): Boolean = when (alert.kind) {
    AlertKind.OFF_TRACK -> postOffTrackNotification(context)
    AlertKind.HEADS_UP, AlertKind.LEAVE_BY, AlertKind.SUNSET -> postSundownNotification(context, alert)
    AlertKind.BACK_BY -> postBackByNotification(context, alert)
}

/**
 * The vibration for [kind]: off-track's two short buzzes; three longer ones for the sundown alerts and
 * Back by. Until dispatch 2026-09-28-645 every alert played off-track's two, and
 * [SUNDOWN_VIBRATION_PATTERN_MILLIS] was declared and never used (found in that dispatch's verify
 * report; the owner, Amendment 1: "Also fix the sundown alerts to buzz three times"; Back by "buzzes
 * three times too").
 */
internal fun vibrationPatternFor(kind: AlertKind): LongArray = when (kind) {
    AlertKind.OFF_TRACK -> OFF_TRACK_VIBRATION_PATTERN_MILLIS
    AlertKind.HEADS_UP, AlertKind.LEAVE_BY, AlertKind.SUNSET, AlertKind.BACK_BY -> SUNDOWN_VIBRATION_PATTERN_MILLIS
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
internal fun postOffTrackNotification(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    val notification = NotificationCompat.Builder(context, OFF_TRACK_CHANNEL_ID)
        .setContentTitle(context.getString(R.string.off_track_notification_title))
        .setContentText(context.getString(R.string.off_track_notification_text))
        .setSmallIcon(R.drawable.ic_track_recording)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(OFF_TRACK_NOTIFICATION_ID, notification)
    return true
}

/** VIBRATE is a normal (install-time) permission — declared in AndroidManifest.xml, no runtime check needed. */
internal fun vibrateForAlert(context: Context, overridesSilence: Boolean, pattern: LongArray = OFF_TRACK_VIBRATION_PATTERN_MILLIS) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vibrateWith(vibrator, overridesSilence, pattern)
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
internal fun vibrateWith(vibrator: Vibrator, overridesSilence: Boolean, pattern: LongArray = OFF_TRACK_VIBRATION_PATTERN_MILLIS) {
    val effect = VibrationEffect.createWaveform(pattern, -1)
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

/**
 * Takes down a recording's sundown, back-by and off-track alerts (RECORD -800, -801; the owner: "Yes, add it now
 * (Recommended)"). A recording that ends any way leaves no "Sunset at …" or "Back by …" in the shade:
 * on 2026-10-09 a leave-by posted at 17:45 still read "Sunset at 6:35 PM" after its recording had ended.
 * Called when a recording stops, when the service is destroyed, and when a process starts, which covers a
 * killed process whose `onDestroy` never ran: a new process has no recording, since one lives only in the
 * service of the process that ran it, and a watch that is still due posts again. Answering an alert is not
 * this path, and leaves the other alert where it is.
 */
internal fun cancelRecordingAlerts(context: Context) {
    val manager = NotificationManagerCompat.from(context)
    manager.cancel(SUNDOWN_NOTIFICATION_ID)
    manager.cancel(BACK_BY_NOTIFICATION_ID)
    // The off-track alert too (RECORD -801, the owner: "Yes"): it is about a recording, and one that has
    // ended has no track to be off.
    manager.cancel(OFF_TRACK_NOTIFICATION_ID)
}

/**
 * Its own channel, separate from off-track, so a user can silence one without losing the other.
 * That separation is the point: the owner's ruling is that off-track respects a silenced phone
 * and the sundown alerts do not, and two channels is what lets someone act on that difference in
 * Android's own settings rather than only in this app's.
 */
internal const val SUNDOWN_CHANNEL_ID = "sundown_alert"
internal const val SUNDOWN_NOTIFICATION_ID = 1003

/**
 * Three pulses rather than off-track's two, and longer. This is the alert that means the light is
 * going, and it should not be mistaken through a coat pocket for the advisory one. Played since
 * dispatch 2026-09-28-645 ([vibrationPatternFor]); declared and unused before it. Back by plays it too.
 */
internal val SUNDOWN_VIBRATION_PATTERN_MILLIS = longArrayOf(0L, 400L, 200L, 400L, 200L, 400L)

internal fun createSundownNotificationChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
        SUNDOWN_CHANNEL_ID,
        context.getString(R.string.sundown_notification_channel_name),
        // HIGH for the same reason off-track is: meant to be noticed on a pocketed phone. The
        // channel carries no vibration; the direct call does, so it can carry alarm usage.
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        enableVibration(false)
        description = context.getString(R.string.sundown_notification_channel_description)
    }
    manager.createNotificationChannel(channel)
}

/**
 * Best-effort, the same stance [postOffTrackNotification] takes: a POST_NOTIFICATIONS denial means
 * no notification, not a crash, and the vibration still runs on its install-time permission.
 *
 * One id for all three, so each replaces the one before it in the shade: the newest is the one
 * that is true.
 */
internal fun postSundownNotification(context: Context, alert: Alert): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    val (title, text) = sundownNotificationText(context, alert)
    val notification = NotificationCompat.Builder(context, SUNDOWN_CHANNEL_ID)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setSmallIcon(R.drawable.ic_track_recording)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(SUNDOWN_NOTIFICATION_ID, notification)
    return true
}

/**
 * The sundown alerts' words (dispatch 2026-09-28-516; the owner's wording in the coder's window,
 * 2026-10-05). The heads-up and leave-by are titled with the sunset time and never order the
 * walker back ("instead of 'turn around now' just tell them when sundown is"; people go on night
 * forays). They share their text; the start-by clock time tells them apart, and a clock time
 * rather than "in 30 min" keeps a notification read late true. The walk back is said the way the
 * estimate supports it:
 *
 * - measured ([WalkBack.About]): "about", **the way you came** (on the owner's three S22 walks the
 *   way home was longer than the way out, and the estimate measures only the way out), and the
 *   start-by time;
 * - thin ([WalkBack.AtLeast]): "at least", and the start-by time "at the latest";
 * - withheld ([WalkBack.Unknown]): "unknown", and nothing about getting back.
 *
 * The sunset alert is unchanged. An alert with no [Alert.sundown] (none is built that way) says
 * the walk back is unknown rather than inventing a time.
 */
internal fun sundownNotificationText(context: Context, alert: Alert): Pair<String, String> {
    if (alert.kind == AlertKind.SUNSET) {
        return context.getString(R.string.sundown_sunset_notification_title) to context.getString(R.string.sundown_sunset_notification_text)
    }
    val detail = alert.sundown ?: return context.getString(R.string.sundown_notification_channel_name) to
        context.getString(R.string.sundown_walk_back_unknown)
    val clock = DateFormat.getTimeFormat(context)
    val title = context.getString(R.string.sundown_alert_title, clock.format(Date(detail.sunsetAtEpochMillis)))
    val startBy = clock.format(Date(detail.leaveByAtEpochMillis))
    // Dispatch 2026-09-28-685, fix 4: a start-by time already gone is never named ("Start back now").
    val startByGone = detail.leaveByHasPassed
    val text = when (val walkBack = detail.walkBack) {
        is WalkBack.About -> if (startByGone) {
            context.getString(R.string.sundown_walk_back_measured_now, formatWalkDuration(walkBack.millis))
        } else {
            context.getString(R.string.sundown_walk_back_measured, formatWalkDuration(walkBack.millis), startBy)
        }
        is WalkBack.AtLeast -> if (startByGone) {
            context.getString(R.string.sundown_walk_back_at_least_now, formatWalkDuration(walkBack.millis))
        } else {
            context.getString(R.string.sundown_walk_back_at_least, formatWalkDuration(walkBack.millis), startBy)
        }
        WalkBack.Unknown -> context.getString(R.string.sundown_walk_back_unknown)
    }
    return title to text
}

/**
 * A walking time as the owner wrote it ("1 h 30", "45 min"), **rounded up** to the minute: every
 * uncertainty in the walk back rounds toward more time ([com.zynergylabs.forager.app.domain.returnWalkingTime]).
 */
internal fun formatWalkDuration(millis: Long): String {
    val minutes = (millis + 59_999L) / 60_000L
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> "$minutes min"
        rest == 0L -> "$hours h"
        else -> "$hours h %02d".format(rest)
    }
}

/**
 * Back by's own channel (dispatch 2026-09-28-645), apart from the sundown and off-track ones, so it
 * can be adjusted on its own in Android's settings. HIGH, no channel vibration: the direct call
 * carries alarm usage, as the sundown alerts' does, so it breaks through silent mode.
 */
internal const val BACK_BY_CHANNEL_ID = "back_by_alert"
internal const val BACK_BY_NOTIFICATION_ID = 1004

internal fun createBackByNotificationChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
        BACK_BY_CHANNEL_ID,
        context.getString(R.string.back_by_notification_channel_name),
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        enableVibration(false)
        description = context.getString(R.string.back_by_notification_channel_description)
    }
    manager.createNotificationChannel(channel)
}

/**
 * "You planned to be back by 3:30 PM" (the owner's words), with "I'm back" and "+30 min". The two
 * actions address `TrackRecordingService` directly ([PendingIntent.getService]), as its own "Stop
 * recording" action does: the service is what runs while the app is swiped away, and it holds the
 * watch. Each carries the recording's id, so a button on a notification left from an earlier
 * recording changes nothing in a later one. Best-effort on POST_NOTIFICATIONS, as the other alerts.
 */
internal fun postBackByNotification(context: Context, alert: Alert): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    val detail = alert.backBy ?: run {
        Log.w("AlertDelivery", "A back-by alert with no time was not posted.")
        return false
    }
    val title = backByNotificationTitle(context, detail.backByAtEpochMillis)
    val notification = NotificationCompat.Builder(context, BACK_BY_CHANNEL_ID)
        .setContentTitle(title)
        .setSmallIcon(R.drawable.ic_track_recording)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .addAction(
            R.drawable.ic_track_recording,
            context.getString(R.string.back_by_action_im_back),
            backByActionIntent(context, TrackRecordingService.ACTION_BACK_BY_IM_BACK, detail.trackId, REQUEST_CODE_BACK_BY_IM_BACK),
        )
        .addAction(
            R.drawable.ic_track_recording,
            context.getString(R.string.back_by_action_later),
            backByActionIntent(context, TrackRecordingService.ACTION_BACK_BY_LATER, detail.trackId, REQUEST_CODE_BACK_BY_LATER),
        )
        .build()
    NotificationManagerCompat.from(context).notify(BACK_BY_NOTIFICATION_ID, notification)
    return true
}

/** The alert's title, in the phone's 12/24-hour clock, as the sundown alerts write theirs. */
internal fun backByNotificationTitle(context: Context, backByAtEpochMillis: Long): String =
    context.getString(R.string.back_by_notification_title, DateFormat.getTimeFormat(context).format(Date(backByAtEpochMillis)))

private fun backByActionIntent(context: Context, action: String, trackId: String, requestCode: Int): PendingIntent =
    PendingIntent.getService(
        context,
        requestCode,
        Intent(context, TrackRecordingService::class.java)
            .setAction(action)
            .putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

// Distinct from each other and from TrackRecordingService's own (0 and 1), so the platform's
// (requestCode, filterEquals(Intent)) identity never hands one action the other's Intent.
private const val REQUEST_CODE_BACK_BY_IM_BACK = 2
private const val REQUEST_CODE_BACK_BY_LATER = 3
