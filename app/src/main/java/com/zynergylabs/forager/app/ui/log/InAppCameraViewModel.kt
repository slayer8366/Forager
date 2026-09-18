package com.zynergylabs.forager.app.ui.log

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the in-app camera is open, and for which surface — held here and nowhere else.
 *
 * ## Why a ViewModel, and not `remember` or `rememberSaveable`
 *
 * The rule this state has to satisfy was written on `PhotoAcquisitionLaunchers` on 2026-09-14,
 * when the flag was a `remember` there: *the camera rebinds from scratch on an Activity
 * recreation anyway, and a dialog reopening by itself after the user was sent away is worse than
 * making them tap Camera again.* That sentence is still true. What a boolean in `remember` could
 * not do is tell the two kinds of Activity destruction apart: a rotation, where the user is
 * holding the phone and framing a shot and losing the viewfinder is a bug; and process death or a
 * memory-pressure destruction, where the user was sent away and the camera should stay closed.
 * `remember` closed it in both cases. `rememberSaveable` would reopen it in both.
 *
 * A `ViewModel` draws exactly that line, by construction: `ComponentActivity` keeps its
 * `ViewModelStore` across a configuration change and clears it in `onDestroy` when
 * `isChangingConfigurations()` is false. So this survives a rotation and dies with process death
 * or a non-configuration destruction, and the original decision is now enforced by the platform
 * rather than approximated by discarding state. Deliberately **not** backed by
 * `SavedStateHandle`: that would restore it after process death, the case the rule exists for.
 *
 * It also survives the window-width-class tree swap that a rotation triggers on a phone, which is
 * the second mechanism of the bug this fixed and the one the ViewModel alone does not address —
 * see [InAppCameraHost] for the other half.
 *
 * Plain state, no coroutines, no Android dependency beyond [ViewModel] itself; tested headless.
 */
internal class InAppCameraViewModel : ViewModel() {

    private val _target = MutableStateFlow<InAppCameraTarget?>(null)

    /** Null when the camera is closed. */
    val target: StateFlow<InAppCameraTarget?> = _target.asStateFlow()

    /**
     * When the app was last seen leaving with this camera open, in elapsed-real-time; null when it
     * has not left, or when a return has already been accounted for. Held here rather than in the
     * composition for the same reason [target] is: it has to survive the Activity recreation a
     * rotation used to cause, and it has to die with the process, where a new process closes the
     * camera anyway. See [CameraAbsenceWatcher] for why the clock is read at the UI edge and passed
     * in as a number — this class keeps its no-Android, tested-headless property.
     */
    private var leftAtElapsedMillis: Long? = null

    fun open(target: InAppCameraTarget) {
        _target.value = target
        // A fresh session is not part of any earlier absence.
        leftAtElapsedMillis = null
    }

    fun close() {
        _target.value = null
        leftAtElapsedMillis = null
    }

    /**
     * The app went away. Recorded only while the camera is open: an absence with nothing open is
     * not something this class has any use for, and storing it would make the next open inherit it.
     */
    fun onLeftApp(elapsedMillis: Long) {
        if (_target.value != null) leftAtElapsedMillis = elapsedMillis
    }

    /**
     * The app came back. Closes the camera when the absence reached the threshold, and otherwise
     * leaves the session exactly as it was — the whole point of a threshold rather than
     * close-on-background. The recorded departure is consumed either way, so a later return without
     * an intervening departure cannot re-trigger it.
     */
    fun onReturnedToApp(elapsedMillis: Long) {
        val leftAt = leftAtElapsedMillis ?: return
        leftAtElapsedMillis = null
        if (cameraClosesAfterAbsence(leftAt, elapsedMillis)) close()
    }
}
