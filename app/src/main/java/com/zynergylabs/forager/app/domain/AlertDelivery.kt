package com.zynergylabs.forager.app.domain

/**
 * Which alert is being delivered.
 */
enum class AlertKind {
    OFF_TRACK,

    /**
     * Thirty minutes before the leave-by time (dispatch 2026-09-28-516, RECORD -515). Passes
     * `overridesSilence = true`, as all three sundown alerts do — owner ruling, 2026-09-11: these
     * are the alerts about not being stranded after dark, so they are alarms. Anyone who does not
     * want them turns the feature off in settings.
     */
    HEADS_UP,

    /**
     * The leave-by time: sunset, minus the darkness margin, minus the walk back. Replaced the
     * turnaround alert ("Time to head back"): the alerts inform and never order the walker back
     * (the owner: "instead of 'turn around now' just tell them when sundown is").
     */
    LEAVE_BY,

    /** The sun has set. Same override as [HEADS_UP]. */
    SUNSET,

    /**
     * The walker's "Back by" time has passed (dispatch 2026-09-28-645, plan task T15): "You planned
     * to be back by 3:30 PM", with "I'm back" and "+30 min". Overrides silence, like the sunset alert
     * (the owner: "Strong alert + two buttons (Recommended)"). See [BackByWatch].
     */
    BACK_BY,
}

/** What a back-by alert says and acts on: the time, and the recording its two buttons address. */
data class BackByAlertDetail(
    val trackId: String,
    val backByAtEpochMillis: Long,
)

/**
 * The walk back as a sundown alert states it (dispatch 2026-09-28-516; the owner's copy rulings,
 * 2026-10-04). Only [About] — a measured estimate — may say anything about getting back.
 */
sealed interface WalkBack {
    /** A measured estimate ([ReturnWalkingTime.Estimate.isAtLeast] false). */
    data class About(val millis: Long) : WalkBack

    /** An estimate on thin data: a floor, never a promise. */
    data class AtLeast(val millis: Long) : WalkBack

    /** Withheld ([ReturnWalkingTime.Withheld]) or unreadable: no number is honest. */
    data object Unknown : WalkBack

    val millisOrNull: Long?
        get() = when (this) {
            is About -> millis
            is AtLeast -> millis
            Unknown -> null
        }
}

/**
 * What a sundown alert says: the sunset time, the walk back it was decided on, and the leave-by
 * time as a clock time ("start by 5:42 PM"), so a notification read late stays true (the owner,
 * 2026-10-05).
 */
data class SundownAlertDetail(
    val sunsetAtEpochMillis: Long,
    val walkBack: WalkBack,
    val leaveByAtEpochMillis: Long,
    /**
     * When the alert was decided ([SundownWatch]'s clock), so its text can tell a start-by time
     * already gone (dispatch 2026-09-28-685, fix 4). `null` from a caller that does not say, which
     * keeps the clock time as before.
     */
    val decidedAtEpochMillis: Long? = null,
) {
    /**
     * The start-by time is already gone: its clock minute, the one the text would name, is before the
     * minute the alert was decided in. Happens when the leave-by alert fires late: a margin change or
     * a longer walk back moved leave-by behind now, or the recording started after it (the
     * 2026-10-07 S22 walk: "start by 5:07 PM" sent at 5:08). Whole minutes since the epoch match the
     * local clock's minutes in every zone whose offset is whole minutes, which is all in use today.
     * The heads-up cannot reach this: once leave-by has passed, the leave-by alert fires in its place.
     */
    val leaveByHasPassed: Boolean
        get() = decidedAtEpochMillis != null && Math.floorDiv(leaveByAtEpochMillis, 60_000L) < Math.floorDiv(decidedAtEpochMillis, 60_000L)
}

/**
 * One alert to deliver. [overridesSilence] is **deliberately a parameter of the call, not a
 * constant inside the delivery** (alert-delivery dispatch, owner decision): off-track is advisory
 * and the turnaround alert, when it exists, is safety, and overriding a phone the user silenced on
 * purpose is defensible for one and arguably rude for the other. The two must be able to differ
 * without the delivery changing shape. **Off-track now passes `false`, reversing the original ruling (owner,
 * 2026-09-11).** It first passed `true`, on the reasoning that someone who started track recording
 * and walked into the woods has opted into being told they have strayed. The owner's revised
 * reading: straying is often deliberate, so off-track is the kind of thing a person may reasonably
 * want to hear only if their notifications are audible, whereas [AlertKind.HEADS_UP], [AlertKind.LEAVE_BY] and
 * [AlertKind.SUNSET] are about not being stranded after dark and override silence. The parameter
 * existing per call is what made this a one-line reversal rather than a redesign. **Do not
 * hard-code this inside an implementation**; that is the one thing it exists to prevent.
 */
data class Alert(
    val kind: AlertKind,
    val overridesSilence: Boolean,
    /** The sundown alerts' content; `null` for off-track, which has none. */
    val sundown: SundownAlertDetail? = null,
    /** The back-by alert's content ([AlertKind.BACK_BY]); `null` for every other kind. */
    val backBy: BackByAlertDetail? = null,
)

/**
 * The owned seam through which anything in this app interrupts the user — a notification, a
 * vibration, whatever the platform implementation decides an [Alert] is made of. Domain and
 * ViewModel code call this; the Android implementation lives in `com.zynergylabs.forager.app.alert`.
 *
 * **Why this exists (alert-delivery dispatch).** The off-track alert used to be delivered from a
 * `LaunchedEffect` in `MainActivity`'s composition, keyed on a counter the ViewModel bumped. The
 * *decision* — `TrackRecordingViewModel.returnToStart`, fed by its own location collection in
 * `viewModelScope` — already ran with the Activity stopped, but Compose's window recomposer pauses
 * below `STARTED`, so the effect, and with it the notification and the vibration, waited for the
 * next resume. The owner walked off-track with the phone in a pocket and got the alert when the
 * screen came on. Delivery now goes through this interface, called directly from the decision, and
 * the composed path is gone: there is one call site and no counter.
 *
 * **A hole this does not close, recorded here so the next person finds it without archaeology.**
 * The decision lives in `TrackRecordingViewModel`, whose lifetime is the Activity's: it survives
 * the Activity being *stopped* (screen off, pocketed, app backgrounded — the case this dispatch
 * fixed, because `TrackRecordingService` keeps the process alive while recording), but **if the
 * task is swiped away while recording, the Activity is destroyed, the ViewModel is cleared, and
 * the off-track decision dies with it** — the returning flag, the rolling window and the cooldown
 * are all ViewModel state. The foreground service keeps recording points (`START_STICKY`), so the
 * track is fine; nothing will ever say "off track" again for that recording. This predates the
 * change (the decision was already in the ViewModel) and is not fixed by it. Closing it means
 * moving navigation ownership — returning state, start point, window, cooldown, and this call —
 * into the service, with the ViewModel as a mirror. That is a real hole in a safety feature and a
 * decision the owner has not yet made.
 *
 * **Closed for the off-track alert (dispatch 2026-09-28-400, Amendment 2; the owner chose
 * "Option B").** The two paragraphs above are left as they were written, as the record of the
 * hole. The decision, the returning flag, the start point, the window, the cooldown and the call
 * to [deliver] are now [ReturnWatch]'s. It is held by `AppContainer` and begun, fed and ended by
 * `TrackRecordingService`, and the ViewModel calls it and copies its state. So the one call site
 * is `ReturnWatch.onFix`, not `TrackRecordingViewModel.returnToStart`, and a swiped-away app
 * still alerts for as long as the service runs. What is still open: a reopened app does not yet
 * show the recording it left (Part 3 of that dispatch), and the two sundown alerts have no caller
 * (plan task T2).
 *
 * **The sundown alerts now have one (dispatch 2026-09-28-516):** [SundownWatch], held by
 * `AppContainer` and driven by `TrackRecordingService` the same way, and three of them, not two.
 */
fun interface AlertDelivery {
    fun deliver(alert: Alert)

    /**
     * The same delivery, saying what happened (dispatch 2026-09-28-451; the owner chose "Report
     * and catch"). `null` from a delivery that cannot say, which is every one but the Android one.
     */
    fun deliverReporting(alert: Alert): AlertDeliveryOutcome? {
        deliver(alert)
        return null
    }
}

/**
 * What an alert's delivery did: whether its notification was posted and its vibration issued, and
 * if not, why ([notificationProblem], [vibrationProblem]: a permission denied, or the exception's
 * class). For the Return record; nothing a walker sees.
 *
 * [vibrationSkipped] (dispatch 2026-09-28-685, fix 3; the owner, RECORD -678: "Fix: record it as
 * skipped (Recommended)"): the vibration was issued without error but Android does not play it, and
 * why, in words ([VIBRATION_SKIPPED_PHONE_ON_SILENT]). [vibrated] is then `false`. Until that
 * dispatch the record said `vibration=done` for an off-track buzz a silenced phone dropped
 * (`ignored_for_ringer_mode` in Android's own vibrator history, the 2026-10-07 S22 walk): a failure
 * reported as success. Defaulted so a caller that cannot tell says nothing it does not know.
 */
data class AlertDeliveryOutcome(
    val notificationPosted: Boolean,
    val notificationProblem: String?,
    val vibrated: Boolean,
    val vibrationProblem: String?,
    val vibrationSkipped: String? = null,
)

/** Why a vibration was skipped: the phone is on silent and the alert does not override it. */
const val VIBRATION_SKIPPED_PHONE_ON_SILENT = "phone on silent"

/** Why a vibration was skipped: Do Not Disturb drops this kind of alert (dispatch 2026-09-28-685, Amendment 1). */
const val VIBRATION_SKIPPED_DO_NOT_DISTURB = "Do Not Disturb"

/**
 * Android's interruption filter, the Do Not Disturb state, as the alert record cares about it (dispatch 2026-09-28-685,
 * Amendment 1, RECORD -694; the owner: "Yes, cover Do Not Disturb (Recommended)"). [UNKNOWN] is a filter the platform
 * could not report.
 */
enum class DoNotDisturbFilter { OFF, PRIORITY, ALARMS_ONLY, TOTAL_SILENCE, UNKNOWN }

/** Owned seam over `NotificationManager.getCurrentInterruptionFilter`; the Android implementation is `AndroidDoNotDisturbSource`. */
fun interface DoNotDisturbSource {
    fun current(): DoNotDisturbFilter
}

/**
 * Why Android will not play an alert's vibration, or `null` when nothing here says it won't
 * (dispatch 2026-09-28-685, fix 3). Only one case is known from the ringer alone: a silenced ringer
 * and an alert that does not override silence ([Alert.overridesSilence] `false`, the off-track
 * alert by the owner's 2026-09-11 ruling), which Android drops as `ignored_for_ringer_mode`. Vibrate
 * and normal modes play it; an alert that overrides silence carries alarm usage and plays on silent
 * (both seen on the S22, 2026-10-07). This decides what is recorded, never what is delivered: the
 * vibration is still issued either way, so the alert's behaviour is unchanged.
 */
fun vibrationSkippedBecause(overridesSilence: Boolean, ringerMode: RingerMode): String? =
    if (!overridesSilence && ringerMode == RingerMode.SILENT) VIBRATION_SKIPPED_PHONE_ON_SILENT else null

/**
 * Why Do Not Disturb drops an alert's vibration, or `null` when it does not (dispatch 2026-09-28-685, Amendment 1).
 * An alert that overrides silence vibrates with alarm usage; one that does not, with notification usage.
 *
 * - Total silence drops both.
 * - Alarms only drops notification usage and lets alarms through.
 * - Priority only drops notification usage unless this app or its channel was made an exception, which this app does
 *   not read (that needs notification-policy access it does not ask for). So it is recorded as skipped: inferred from
 *   Android's defaults, not observed. It lets alarms through, Android's default for that mode, also not read.
 * - Off or unknown drops nothing this can tell.
 *
 * Ahead of the ringer in [vibrationSkipReason], as in the trip-start warning (`alertAudibilityWarning`).
 */
fun vibrationSkippedByDoNotDisturb(overridesSilence: Boolean, filter: DoNotDisturbFilter): String? = when (filter) {
    DoNotDisturbFilter.TOTAL_SILENCE -> VIBRATION_SKIPPED_DO_NOT_DISTURB
    DoNotDisturbFilter.ALARMS_ONLY, DoNotDisturbFilter.PRIORITY -> if (overridesSilence) null else VIBRATION_SKIPPED_DO_NOT_DISTURB
    DoNotDisturbFilter.OFF, DoNotDisturbFilter.UNKNOWN -> null
}

/**
 * The one reason recorded, Do Not Disturb first, then the ringer; `null` when neither drops the vibration. A `null`
 * input was unreadable and says nothing. Not covered: a phone-level setting that turns vibration off (Android's
 * vibration and haptics settings), which no reading here can see.
 */
fun vibrationSkipReason(overridesSilence: Boolean, filter: DoNotDisturbFilter?, ringerMode: RingerMode?): String? =
    filter?.let { vibrationSkippedByDoNotDisturb(overridesSilence, it) }
        ?: ringerMode?.let { vibrationSkippedBecause(overridesSilence, it) }
