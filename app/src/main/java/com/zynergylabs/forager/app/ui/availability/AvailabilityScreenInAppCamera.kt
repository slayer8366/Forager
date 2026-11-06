package com.zynergylabs.forager.app.ui.availability

// Dispatch 2026-10-26-17 (RECORD -1682), a pure move: the in-app camera's call, moved out of AvailabilityScreen's body with the
// bundle unpacks only it read (the camera bundle's but onOpenCamera, which the frame reads too, and one field each of the
// settings, findsLog and journalEntries bundles). The body calls this unconditionally, last, at the line where InAppCameraHost
// stood, so the camera is still composed after the drawer host and still draws on top. The call is unchanged.

import androidx.compose.runtime.Composable
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.ui.log.InAppCameraHost

@Composable
internal fun AvailabilityScreenInAppCamera(
    cameraInputs: AvailabilityScreenCamera,
    cameraCaptureFiles: CameraCaptureFiles,
    uiState: AvailabilityUiState,
    settings: AvailabilityScreenSettings,
    findsLog: AvailabilityScreenFindsLog,
    journalEntries: AvailabilityScreenJournalEntries,
) {
    // Phase 2.1 (dispatch 2026-10-19-28): the camera bundle, unpacked under the parameter names it replaced.
    val inAppCameraTarget = cameraInputs.inAppCameraTarget
    val onCloseCamera = cameraInputs.onCloseCamera
    val inAppCamera = cameraInputs.inAppCamera
    val cameraGridMode = cameraInputs.cameraGridMode
    val onCameraGridModeChanged = cameraInputs.onCameraGridModeChanged
    val cameraFormat = cameraInputs.cameraFormat
    val onCameraFormatChanged = cameraInputs.onCameraFormatChanged
    // Phase 2.1 (dispatch 2026-10-19-28): the findsLog bundle, unpacked under the parameter names it replaced.
    val onAddLogPhoto = findsLog.onAddLogPhoto
    val onAddGalleryPhoto = findsLog.onAddGalleryPhoto
    // Phase 2.1 (dispatch 2026-10-19-28): the journalEntries bundle, unpacked under the parameter names it replaced.
    val onAcquirePhotoForCartographyEntry = journalEntries.onAcquirePhotoForCartographyEntry
    // Phase 2.1 (dispatch 2026-10-19-28): the settings bundle, unpacked under the parameter names it replaced.
    val onAutoSaveLocationToPhotosChanged = settings.onAutoSaveLocationToPhotosChanged
    // The in-app camera, once, after the tree above. Its own open flag lives in InAppCameraViewModel,
    // which survives a recreation. See InAppCameraHost for both mechanisms and the owner's decision.
    // Until dispatch 2026-09-28-245 a window crossing 600 dp flipped between two trees, which is why
    // it stayed outside them; there is one tree now, and the placement (after it) is kept for the
    // reason below.
    //
    // It sits *after* the branch rather than before it (2026-09-19): since the camera draws in the
    // Activity's own window instead of a dialog's, nothing but composition order puts it on top,
    // and a sibling composed first draws and hit-tests underneath. zIndex was tried and measured
    // insufficient — see InAppCameraDialog's own Box. The reason for the original placement is
    // untouched: the camera is still outside both width-class trees, so the flip still cannot
    // dispose it, which is what "above the branch" was always about.
    InAppCameraHost(
        target = inAppCameraTarget,
        cameraCaptureFiles = cameraCaptureFiles,
        gridMode = cameraGridMode,
        onGridModeChanged = onCameraGridModeChanged,
        cameraFormat = cameraFormat,
        onCameraFormatChanged = onCameraFormatChanged,
        autoSaveLocationToPhotos = uiState.autoSaveLocationToPhotos,
        onAutoSaveLocationToPhotosChanged = onAutoSaveLocationToPhotosChanged,
        onLogEntryPhoto = onAddLogPhoto,
        onAlbumPhoto = onAddGalleryPhoto,
        onCartographyEntryPhoto = onAcquirePhotoForCartographyEntry,
        onDismiss = onCloseCamera,
        camera = inAppCamera,
    )
}
