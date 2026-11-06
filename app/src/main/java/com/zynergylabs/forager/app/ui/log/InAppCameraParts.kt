package com.zynergylabs.forager.app.ui.log

// Moved unchanged from InAppCameraDialog.kt (dispatch 2026-10-19-23, UI split Phase 1, PR 10f), apart from private to internal on the declarations the dialog calls.

import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.domain.rawCaptureSize
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.CameraCapturePhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureSession
import com.zynergylabs.forager.app.photo.RawShotOutcome
import com.zynergylabs.forager.app.photo.RawShotSaver
import com.zynergylabs.forager.app.photo.TimerMode

internal const val IN_APP_CAMERA_TAG = "in-app-camera"
internal const val CAMERA_SHUTTER_TAG = "in-app-camera-shutter"
internal const val CAMERA_SHUTTER_BAND_TAG = "in-app-camera-shutter-band"
internal const val CAMERA_COUNT_TAG = "in-app-camera-count"
internal const val CAMERA_ERROR_TAG = "in-app-camera-error"
internal const val CAMERA_OPENING_TAG = "in-app-camera-opening"
internal const val CAMERA_UNAVAILABLE_TAG = "in-app-camera-unavailable"
internal const val SHUTTER_DESCRIPTION = "Take photo"
internal const val CANCEL_TIMER_DESCRIPTION = "Cancel timer"
internal const val CAMERA_COUNTDOWN_TAG = "in-app-camera-countdown"
internal const val CAPTURE_FAILED_MESSAGE = "That photo didn't save. Try again."
internal const val CAMERA_NOTICE_TAG = "in-app-camera-notice"

/** What a RAW shot came to, for the screen: a photo (with a [notice] when it was kept as JPG only), or no photo at all. */
internal sealed interface RawShotScreenOutcome {
    data class Saved(val source: CameraCapturePhotoSource, val notice: String?) : RawShotScreenOutcome
    data object Failed : RawShotScreenOutcome
}

/**
 * One RAW shot through the shot step (Amendment 1, item 3): the camera writes the JPG and hands back the sensor data, and
 * [saver] writes and checks the DNG, or says why the JPG is the whole photo. A shot whose JPG did not save is a failed
 * shot, exactly as a JPG shot's is.
 */
internal suspend fun takeRawShot(session: CameraCaptureSession, saver: RawShotSaver, capture: CameraCaptureFiles.Capture): RawShotScreenOutcome {
    val shot = session.captureRaw(capture.file).getOrElse { return RawShotScreenOutcome.Failed }
    return when (val outcome = saver.save(shot)) {
        is RawShotOutcome.Kept -> RawShotScreenOutcome.Saved(CameraCapturePhotoSource(capture, rawFile = outcome.dng), notice = null)
        is RawShotOutcome.JpgOnly -> RawShotScreenOutcome.Saved(CameraCapturePhotoSource(capture), notice = outcome.message)
    }
}

/**
 * The strip's chips for this session, in order along the edge: Flash, Timer, Grid, Location
 * (decision B8, Closed decision A), then the gear (dispatch 2026-09-28-707), always present. The flash chip only when the bound camera has a flash unit: it
 * would hide itself anyway, but a chip that composes nothing would still take a slot. Read in
 * composition, so the chip arrives when the bind reports the unit. The Timer chip always
 * (2026-09-26), the grid chip always (2026-09-22), and the Location chip always (2026-09-26):
 * every camera can time a shot, draw a grid and show the photo-location setting.
 */
internal fun stripChips(
    session: CameraCaptureSession,
    timerMode: TimerMode,
    onTimerModeChanged: (TimerMode) -> Unit,
    gridMode: GridMode,
    onGridModeChanged: (GridMode) -> Unit,
    autoSaveLocationToPhotos: Boolean,
    onAutoSaveLocationToPhotosChanged: (Boolean) -> Unit,
    onOpenCameraSettings: () -> Unit,
    onOpenFormatMenu: () -> Unit,
): List<StripChip> = buildList {
    if (session.hasFlashUnit) add { deviceRotation, displayRotation -> FlashChip(session, deviceRotation, displayRotation) }
    // The format chip, after the flash as the owner's mockup draws it (dispatch 2026-10-15-01), only where the probe
    // offers RAW within the operating limit; elsewhere there is no chip and nothing changes (RECORD -974).
    if (rawCaptureSize(session.capabilities) != null) {
        add { deviceRotation, displayRotation -> FormatChip(session.outputFormat, onOpenFormatMenu, deviceRotation, displayRotation) }
    }
    add { deviceRotation, displayRotation -> TimerChip(timerMode, onTimerModeChanged, deviceRotation, displayRotation) }
    add { deviceRotation, displayRotation -> GridChip(gridMode, onGridModeChanged, deviceRotation, displayRotation) }
    add { deviceRotation, displayRotation ->
        LocationChip(autoSaveLocationToPhotos, onAutoSaveLocationToPhotosChanged, deviceRotation, displayRotation)
    }
    add { deviceRotation, displayRotation -> CameraSettingsChip(onOpenCameraSettings, deviceRotation, displayRotation) }
}
