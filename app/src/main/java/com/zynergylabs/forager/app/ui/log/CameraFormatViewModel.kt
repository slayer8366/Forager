package com.zynergylabs.forager.app.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.ErrorLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The format chip's remembered choice (RECORD -1005), held as [CameraGridModeViewModel] holds the grid mode: read once
 * from the repository, JPG until then and if the read fails (logged), and changed only once the write has landed, so the
 * chip never shows a choice that was not stored.
 */
internal class CameraFormatViewModel(
    private val getCameraFormat: suspend () -> Result<CameraFormat>,
    private val setCameraFormat: suspend (CameraFormat) -> Result<Unit>,
    private val errorLog: ErrorLog,
) : ViewModel() {

    private val _format = MutableStateFlow(CameraFormat.Jpg)
    val format: StateFlow<CameraFormat> = _format.asStateFlow()

    init {
        viewModelScope.launch {
            getCameraFormat().fold(
                onSuccess = { stored -> _format.value = stored },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the camera format; showing JPG.", error) },
            )
        }
    }

    fun onCameraFormatChanged(format: CameraFormat) {
        viewModelScope.launch {
            setCameraFormat(format).fold(
                onSuccess = { _format.value = format },
                onFailure = { error -> errorLog.w(TAG, "Couldn't store the camera format $format; the chip keeps showing ${_format.value}.", error) },
            )
        }
    }

    private companion object {
        const val TAG = "CameraFormatViewModel"
    }
}
