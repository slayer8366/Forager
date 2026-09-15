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

    fun open(target: InAppCameraTarget) {
        _target.value = target
    }

    fun close() {
        _target.value = null
    }
}
