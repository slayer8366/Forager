package com.zynergylabs.forager.app.domain

/** The device's ringer switch as the alert cares about it. [VIBRATE] is not a silenced state for a vibration. */
enum class RingerMode { NORMAL, VIBRATE, SILENT }

/**
 * What the device would do with an alert right now — read once at the start of a trip
 * (alert-delivery dispatch, Item 3), never watched live.
 *
 * [doNotDisturbOn] is any interruption filter other than "all" (priority, alarms-only or total
 * silence): an alarm-usage vibration usually survives the first two and never the third, and the
 * filter alone cannot tell which the user's policy allows, so all three warn as "may not be felt".
 * [notificationsEnabled] covers both a denied `POST_NOTIFICATIONS` on API 33+ and the app's
 * notifications switched off in settings on any API level — either way the shade entry is gone
 * and only the vibration remains.
 */
data class AlertAudibilityState(
    val ringerMode: RingerMode,
    val doNotDisturbOn: Boolean,
    val notificationsEnabled: Boolean,
)

/** Owned seam over `AudioManager` / `NotificationManager`; the Android implementation is `com.zynergylabs.forager.app.alert.AndroidAlertAudibility`. */
interface AlertAudibility {
    fun current(): AlertAudibilityState
}

/**
 * The one-time trip-start warning for [state], or `null` when nothing needs saying. Copy is the
 * owner's (alert-delivery dispatch, Item 3): it says what is true and never tells the user to
 * change a setting they may have chosen on purpose. "May not be felt", not "will not": the
 * off-track vibration is issued with alarm usage precisely so that it survives these states, and
 * whether it does on a given device under a given Do Not Disturb policy is not knowable from here.
 *
 * Precedence: Do Not Disturb over a silenced ringer (the stronger state, and DND usually implies
 * silence anyway); a vibrate-mode ringer warns of nothing, since vibration is what the alert uses.
 * Notifications being off is a third, independent clause — appended to either warning, or standing
 * alone when the phone is otherwise audible.
 */
fun alertAudibilityWarning(state: AlertAudibilityState): String? {
    val silence = when {
        state.doNotDisturbOn -> DO_NOT_DISTURB_WARNING
        state.ringerMode == RingerMode.SILENT -> SILENCED_WARNING
        else -> null
    }
    val notifications = if (state.notificationsEnabled) null else NOTIFICATIONS_OFF_WARNING
    return listOfNotNull(silence, notifications).takeIf { it.isNotEmpty() }?.joinToString(" ")
}

const val SILENCED_WARNING = "Your phone is silenced. If you go off track, the alert may not be felt."
const val DO_NOT_DISTURB_WARNING = "Do Not Disturb is on. If you go off track, the alert may not be felt."
const val NOTIFICATIONS_OFF_WARNING = "Notifications are off for Forager, so an off-track alert won't show on screen."
