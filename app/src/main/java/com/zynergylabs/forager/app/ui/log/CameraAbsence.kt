package com.zynergylabs.forager.app.ui.log

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Four minutes away with the camera open closes it. Owner's number, 2026-09-17.
 *
 * ## Why an absence threshold and not close-on-background
 *
 * Backgrounding does not mean the task is finished: a user who opens the gallery picker, or takes a
 * glance at a notification, is mid-session. `PhotoAcquisitionLaunchers.kt:77-86` records the sharp
 * version of that — an app's own launch produces the same `ON_PAUSE`/`ON_STOP` as a real departure,
 * and conflating them is what used to close the find being edited on every round trip. A threshold
 * sidesteps the distinction instead of trying to draw it: a picker round trip is seconds, a real
 * departure is not.
 *
 * **Four minutes is a judgement, not a measurement.** Nobody has data on how long a picker round
 * trip takes on a slow device, or how long people actually leave mid-session. If it is wrong in
 * either direction it is a number to change here, not a design to revisit.
 *
 * ## What this feature is, and what it turned out not to be
 *
 * It is **one thing**: close the dialog. The dispatch that commissioned it also asked for the user
 * to be returned mid-edit and for unpersisted captures to be saved to the album; the pre-build
 * report (`docs/audits/2026-09-17-camera-absence-timeout-prebuild-report.md`) found neither
 * survives contact with the code, and the owner struck both.
 *
 * - **Captures are already saved.** Every successful capture persists on the shutter tap
 *   ([InAppCameraDialog]'s `onShutter`), reaching `filesDir/photos` and a gallery row before the
 *   user does anything else. There is no unsaved bucket for a timeout to flush.
 * - **Leaving the edit is not a collision, it is agreement.** Backgrounding already runs the
 *   journal's incidental-exit auto-save (`AvailabilityScreen`'s `ON_STOP` observer) and arms
 *   cartography's backgrounded-while-dirty prompt (`CartographyScreen`'s). Those are what the app
 *   does on a genuine departure, and four minutes away is the definition of one. Suppressing them
 *   would make the camera being open evidence the user is still engaged, which it is not — the
 *   absence is evidence they were not.
 *
 * ## The clock is elapsed-real-time, not wall-clock
 *
 * [SystemClock.elapsedRealtime] counts while the device sleeps and cannot jump: it is the right
 * clock for "how long was this app away". [CurrentTimeProvider][com.zynergylabs.forager.app.domain.CurrentTimeProvider],
 * this repo's other injected clock, is wall-clock epoch millis, which an NTP correction or a user
 * changing the time can move under a measurement in progress — a timestamp to store, not an
 * interval to measure. The reading is taken here, at the UI edge, and handed to the ViewModel as a
 * number, so [InAppCameraViewModel] keeps the "no Android dependency, tested headless" property its
 * own doc claims.
 *
 * No service, no alarm, no wake lock: nothing has to fire while the app is away, because the
 * decision is a subtraction made on return.
 */
internal const val CAMERA_ABSENCE_TIMEOUT_MILLIS: Long = 4 * 60 * 1000

/**
 * Whether an absence that began at [leftAtElapsedMillis] and ended at [returnedAtElapsedMillis] is
 * long enough to close the camera. `null` means the app was never observed leaving, which is the
 * ordinary state of an open camera and never closes anything.
 *
 * At exactly the threshold this closes: the spec is "four minutes or more". A negative interval —
 * which elapsed-real-time cannot produce, but which a caller could pass — is treated as no absence
 * rather than as an enormous one.
 */
internal fun cameraClosesAfterAbsence(
    leftAtElapsedMillis: Long?,
    returnedAtElapsedMillis: Long,
    thresholdMillis: Long = CAMERA_ABSENCE_TIMEOUT_MILLIS,
): Boolean {
    val leftAt = leftAtElapsedMillis ?: return false
    val away = returnedAtElapsedMillis - leftAt
    if (away < 0) return false
    return away >= thresholdMillis
}

/**
 * Reports the app leaving and returning, in elapsed-real-time, for as long as this is in
 * composition. Composed only while the camera is open, so an absence is only ever measured for a
 * session that exists.
 *
 * `ON_STOP` rather than `ON_PAUSE`: a permission dialog or a partially-covering window pauses
 * without stopping, and neither is the user leaving. `ON_START` rather than `ON_RESUME` for the
 * same reason on the way back — the decision should be made once per real departure, not again each
 * time a dialog above the app is dismissed.
 *
 * [elapsedMillis] is injectable so a test can drive an absence without waiting four minutes, and so
 * that the two directions of the threshold are distinguishable by construction rather than by a
 * pinned harness clock that could silently fail to take.
 *
 * **One replayed `ON_START` arrives on entering composition**, because `addObserver` brings a new
 * observer up to the owner's current state. Opening the camera therefore reports a return before it
 * has reported any departure, and that is deliberately harmless: [InAppCameraViewModel.open] clears
 * the recorded departure and [InAppCameraViewModel.onReturnedToApp] returns early without one, so
 * the replay measures nothing. It is also what makes the feature survive an Activity recreation
 * during an absence — the rebuilt composition's replayed `ON_START` is the return, and the
 * departure the ViewModel held is still there to compare it against.
 */
@Composable
internal fun CameraAbsenceWatcher(
    onLeftApp: (Long) -> Unit,
    onReturnedToApp: (Long) -> Unit,
    elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnLeft by rememberUpdatedState(onLeftApp)
    val currentOnReturned by rememberUpdatedState(onReturnedToApp)
    val currentClock by rememberUpdatedState(elapsedMillis)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> currentOnLeft(currentClock())
                Lifecycle.Event.ON_START -> currentOnReturned(currentClock())
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
