package com.zynergylabs.forager.app.ui.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.CameraXCaptureSession
import com.zynergylabs.forager.app.sensor.AndroidLevelProvider

/**
 * Which surface asked for the in-app camera, so the one hoisted dialog can hand each photo to the
 * right consumer. **Data, not a callback, on purpose**: this value is retained across Activity
 * recreation by [InAppCameraViewModel], and a lambda captured from a composition that no longer
 * exists is exactly the kind of thing that must not be retained. The routing from target to
 * consumer happens in [InAppCameraHost], once, from callbacks that are live in the current
 * composition.
 */
enum class InAppCameraTarget {
    /** A find being edited: the photo attaches to it (`onAddLogPhoto`). */
    LOG_ENTRY,

    /** The Album: the photo is persisted standalone (`onAddGalleryPhoto`). */
    ALBUM,

    /** A Cartography entry being edited: persisted to the Album and kept by the entry (`onAcquirePhotoForCartographyEntry`). */
    CARTOGRAPHY_ENTRY,
}

/**
 * The camera dialog as a slot, for the same reason the map is one ([com.zynergylabs.forager.app.ui.map.MapSlot]):
 * CameraX cannot run under Robolectric, so a test of the screen that hosts the dialog hands in a
 * dialog over `FakeCameraCaptureSession` and asserts the hosting, not CameraX.
 */
typealias InAppCameraSlot = @Composable (
    cameraCaptureFiles: CameraCaptureFiles,
    /** Settings' "Lock camera to portrait", handed to the session at creation — see `effectiveDeviceRotation`. */
    lockToPortrait: Boolean,
    /** The persisted grid mode and the way to change it — see `CameraGridModeViewModel`. */
    gridMode: GridMode,
    onGridModeChanged: (GridMode) -> Unit,
    autoSaveLocationToPhotos: Boolean,
    onAutoSaveLocationToPhotosChanged: (Boolean) -> Unit,
    onPhotoCaptured: (PhotoSource) -> Unit,
    onDismiss: () -> Unit,
) -> Unit

/**
 * The production slot: one [CameraXCaptureSession] per open camera, discarded when the dialog
 * leaves composition, because it holds a bound CameraX use case that must not outlive the
 * viewfinder it draws into. Moved here from `PhotoAcquisitionLaunchers` on 2026-09-15 when the
 * dialog was hoisted; unchanged otherwise.
 */
internal val CameraXInAppCamera: InAppCameraSlot = { cameraCaptureFiles, lockToPortrait, gridMode, onGridModeChanged, autoSaveLocationToPhotos, onAutoSaveLocationToPhotosChanged, onPhotoCaptured, onDismiss ->
    val context = LocalContext.current.applicationContext
    // One provider per open camera; it registers a sensor listener only while the level is shown.
    val levelProvider = remember { AndroidLevelProvider(context) }
    // Keyed on the setting so a session never carries a stale value; in practice it cannot change
    // while the camera is open, since the dialog covers Settings.
    val session = remember(lockToPortrait) { CameraXCaptureSession(context, lockToPortrait) }
    InAppCameraDialog(
        session = session,
        cameraCaptureFiles = cameraCaptureFiles,
        lockToPortrait = lockToPortrait,
        onPhotoCaptured = onPhotoCaptured,
        onDismiss = onDismiss,
        gridMode = gridMode,
        onGridModeChanged = onGridModeChanged,
        autoSaveLocationToPhotos = autoSaveLocationToPhotos,
        onAutoSaveLocationToPhotosChanged = onAutoSaveLocationToPhotosChanged,
        levelProvider = levelProvider,
        viewfinder = { modifier -> session.Viewfinder(modifier) },
    )
}

/**
 * The in-app camera, composed **once, above the window-width branch** — owner's decision,
 * 2026-09-15: *"The camera is not a property of the navigation layout, and the current placement
 * is what ties its lifetime to a breakpoint nobody meant it to depend on. One dialog, composed
 * once, above the branch. The three screens keep their Camera buttons and call up to it."*
 *
 * ## The bug this moved for
 *
 * Found on the device check: rotating the phone with the camera open closed it and dropped the
 * user on the screen underneath, in the wide layout. Two mechanisms, and the one everyone
 * reasoned about first was the smaller. (1) A rotation recreates the Activity, and the open flag
 * lived in `remember` inside the screen. (2) A phone in landscape is `WindowWidthClass.MEDIUM`,
 * and `AvailabilityScreen` composes a different tree per width class; each of the three screens
 * with a Camera button sat inside that per-class tree, so the width flip alone disposed the
 * dialog, its session and its flag, **whether or not the Activity was recreated**.
 * `android:configChanges` would have addressed (1) and left (2) exactly as it was. So: the flag
 * moved to [InAppCameraViewModel], which survives (1), and the dialog moved here, above the
 * branch, which survives (2). The screens' Camera buttons now call up through
 * `onOpenCamera(target)`; the routing back down is the `when` below.
 *
 * One dialog is also one CameraX session, rather than one per screen that could offer a button.
 */
@Composable
internal fun InAppCameraHost(
    target: InAppCameraTarget?,
    cameraCaptureFiles: CameraCaptureFiles,
    /** Settings' "Lock camera to portrait", from `AvailabilityUiState`; passed straight to the slot. */
    lockToPortrait: Boolean,
    /** The persisted grid mode, from `CameraGridModeViewModel`; passed straight to the slot. */
    gridMode: GridMode,
    onGridModeChanged: (GridMode) -> Unit,
    autoSaveLocationToPhotos: Boolean,
    onAutoSaveLocationToPhotosChanged: (Boolean) -> Unit,
    onLogEntryPhoto: (PhotoSource) -> Unit,
    onAlbumPhoto: (PhotoSource) -> Unit,
    onCartographyEntryPhoto: (PhotoSource) -> Unit,
    onDismiss: () -> Unit,
    camera: InAppCameraSlot = CameraXInAppCamera,
) {
    if (target == null) return
    val onPhotoCaptured: (PhotoSource) -> Unit = when (target) {
        InAppCameraTarget.LOG_ENTRY -> onLogEntryPhoto
        InAppCameraTarget.ALBUM -> onAlbumPhoto
        InAppCameraTarget.CARTOGRAPHY_ENTRY -> onCartographyEntryPhoto
    }
    camera(cameraCaptureFiles, lockToPortrait, gridMode, onGridModeChanged, autoSaveLocationToPhotos, onAutoSaveLocationToPhotosChanged, onPhotoCaptured, onDismiss)
}
