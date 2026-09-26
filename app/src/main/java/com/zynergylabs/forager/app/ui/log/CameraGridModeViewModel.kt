package com.zynergylabs.forager.app.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GridMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The camera's grid mode, as stored (decision record B7a: it persists, unlike torch). `MainActivity`
 * builds it over the container's `CameraGridModeRepository` and hands [mode] and
 * [onGridModeChanged] down to the camera, the way `InAppCameraViewModel`'s target goes down.
 *
 * **Its own ViewModel, not a field on `AvailabilityViewModel`** where "Lock camera to portrait"
 * lives. That one is a Settings value with a Settings screen; this is written only from inside the
 * camera, and a class of its own is testable headless with three arguments where the other takes
 * some twenty. Not on `InAppCameraViewModel` either: that one has no dependencies by design, and
 * eleven headless tests build it bare.
 *
 * **[mode] is what the repository holds, not what was asked for.** A change is shown once its write
 * has succeeded; a failed write leaves the shown mode as it was and is logged, so the chip never
 * shows a mode that will not survive the camera closing. A failed read leaves Off and is logged.
 */
internal class CameraGridModeViewModel(
    private val getGridMode: suspend () -> Result<GridMode>,
    private val setGridMode: suspend (GridMode) -> Result<Unit>,
    private val errorLog: ErrorLog,
) : ViewModel() {

    private val _mode = MutableStateFlow(GridMode.Off)
    val mode: StateFlow<GridMode> = _mode.asStateFlow()

    init {
        viewModelScope.launch {
            getGridMode().fold(
                onSuccess = { stored -> _mode.value = stored },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the camera grid mode; showing Off.", error) },
            )
        }
    }

    fun onGridModeChanged(mode: GridMode) {
        viewModelScope.launch {
            setGridMode(mode).fold(
                onSuccess = { _mode.value = mode },
                onFailure = { error -> errorLog.w(TAG, "Couldn't store the camera grid mode $mode; the chip keeps showing ${_mode.value}.", error) },
            )
        }
    }

    private companion object {
        const val TAG = "CameraGridModeViewModel"
    }
}
