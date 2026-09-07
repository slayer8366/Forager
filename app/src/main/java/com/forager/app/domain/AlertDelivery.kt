package com.forager.app.domain

/**
 * Which alert is being delivered. One kind today; the turnaround alert (light-budget work, not
 * built) is the second this was shaped for.
 */
enum class AlertKind { OFF_TRACK }

/**
 * One alert to deliver. [overridesSilence] is **deliberately a parameter of the call, not a
 * constant inside the delivery** (alert-delivery dispatch, owner decision): off-track is advisory
 * and the turnaround alert, when it exists, is safety, and overriding a phone the user silenced on
 * purpose is defensible for one and arguably rude for the other. The two must be able to differ
 * without the delivery changing shape. Off-track passes `true` — someone who turned on track
 * recording and walked into the woods has opted into being told they have strayed (owner's
 * words) — and the turnaround alert will pass whatever it decides. **Do not hard-code this inside
 * an implementation**; that is the one thing this parameter exists to prevent.
 */
data class Alert(
    val kind: AlertKind,
    val overridesSilence: Boolean,
)

/**
 * The owned seam through which anything in this app interrupts the user — a notification, a
 * vibration, whatever the platform implementation decides an [Alert] is made of. Domain and
 * ViewModel code call this; the Android implementation lives in `com.forager.app.alert`.
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
 */
fun interface AlertDelivery {
    fun deliver(alert: Alert)
}
