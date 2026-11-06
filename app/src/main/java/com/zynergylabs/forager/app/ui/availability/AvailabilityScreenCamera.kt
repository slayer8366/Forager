package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.ui.log.CameraXInAppCamera
import com.zynergylabs.forager.app.ui.log.InAppCameraSlot
import com.zynergylabs.forager.app.ui.log.InAppCameraTarget

/*
 * Phase 2.1 of the UI split (dispatch 2026-10-19-28, RECORD -1436): [AvailabilityScreen]'s in-app camera
 * inputs, grouped into one bundle as `CompactMapTabInputs.kt` grouped [CompactMapTab]'s (RECORD -739).
 * These were eight separate parameters of the screen; each field keeps that parameter's default and doc
 * comment, and the screen unpacks the bundle into locals with the old names at the top of its body, so
 * the body reads as it did. Public because [AvailabilityScreen] is public. `cameraCaptureFiles` stays a
 * plain parameter of the screen: its default reads `LocalContext.current`, which a constructor default
 * cannot.
 */

/** The in-app camera's state and callbacks for [AvailabilityScreen]. Every field is defaulted, as the parameters were. */
data class AvailabilityScreenCamera(
    /**
     * Which surface the in-app camera is open for, or null when closed. Retained by
     * `InAppCameraViewModel` across Activity recreation, and composed by this screen **above the
     * window-width branch** so a rotation's width-class flip does not dispose it — see
     * [com.zynergylabs.forager.app.ui.log.InAppCameraHost] for the bug and the decision.
     */
    val inAppCameraTarget: InAppCameraTarget? = null,
    val onOpenCamera: (InAppCameraTarget) -> Unit = {},
    val onCloseCamera: () -> Unit = {},
    /** The camera dialog itself; defaults to CameraX. A slot for the same reason `mapSlot` is one: CameraX cannot run under Robolectric. */
    val inAppCamera: InAppCameraSlot = CameraXInAppCamera,
    /** The camera's persisted grid mode and the way to change it, from `CameraGridModeViewModel`. Defaulted like the camera's other inputs. */
    val cameraGridMode: GridMode = GridMode.Off,
    val onCameraGridModeChanged: (GridMode) -> Unit = {},
    /** The camera's remembered format and the way to change it, from `CameraFormatViewModel` (dispatch 2026-10-15-01). Defaulted like the camera's other inputs. */
    val cameraFormat: CameraFormat = CameraFormat.Jpg,
    val onCameraFormatChanged: (CameraFormat) -> Unit = {},
)
