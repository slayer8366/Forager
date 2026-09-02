package com.forager.app.ui.log

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.forager.app.domain.model.PhotoSource
import com.forager.app.photo.CameraCaptureFiles
import com.forager.app.photo.CameraCapturePhotoSource
import com.forager.app.photo.GalleryImportPhotoSource

/**
 * The Camera-permission-then-capture and system-Gallery-picker launchers both [LogEntryDetailScreen]'s
 * `PhotosSection` and [PhotoGalleryScreen] need — extracted once both screens needed the exact same
 * `ActivityResultContracts`/[CameraCaptureFiles]/permission wiring (standalone-photos dispatch:
 * "reuse the existing contracts, `CameraCaptureFiles`, and FileProvider... reuse; do not
 * reimplement"), rather than a second hand-copy of it drifting from the first over time.
 *
 * Returns the two trigger functions only, not rendered buttons — each screen keeps its own button
 * layout and labels ([LogEntryDetailScreen]'s own `FlowRow` alongside its unrelated "From Album"
 * button; [PhotoGalleryScreen]'s a plain pair above its grid), which differ enough between the two
 * hosts that sharing the button `Composable`s themselves, not just the launcher logic, would need a
 * slot API for no real savings over each screen writing its own two-line `Button`.
 *
 * ## `ACCESS_MEDIA_LOCATION`, requested at the moment of first import (photo-geodata dispatch)
 *
 * [launchGallery] fires [requestMediaLocationPermission] immediately before [pickPhotos], every
 * time, regardless of the previous outcome — there is no "already asked, don't ask again" state
 * tracked here. That is deliberate: Android's own permission dialog already throttles repeat
 * requests after a denial (a second request within the same session re-prompts; a later one after
 * "Don't ask again" is a silent no-op that returns denied immediately), so a second layer of
 * throttling here would just duplicate platform behavior the OS already owns. A denial never blocks
 * the pick itself — [pickPhotos] still launches either way; without the grant,
 * [com.forager.app.photo.FilePhotoStore] simply reads no EXIF location for whatever gets picked
 * (see that class's own doc comment).
 */
@Composable
internal fun rememberPhotoAcquisitionLaunchers(
    cameraCaptureFiles: CameraCaptureFiles,
    onPhotoSourceSelected: (PhotoSource) -> Unit,
): PhotoAcquisitionLaunchers {
    var pendingCapture by remember { mutableStateOf<CameraCaptureFiles.Capture?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val capture = pendingCapture
        pendingCapture = null
        if (success && capture != null) {
            onPhotoSourceSelected(CameraCapturePhotoSource(capture.uri))
        } else {
            capture?.let(cameraCaptureFiles::deleteCapture)
        }
    }
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val capture = cameraCaptureFiles.newCapture()
            pendingCapture = capture
            takePicture.launch(capture.uri)
        }
    }

    // PickMultipleVisualMedia, not PickVisualMedia: the single-select contract only ever returns
    // one Uri — see LogEntryDetailScreen's own former doc comment on this exact bug, now this
    // function's history. onPhotoSourceSelected still takes one PhotoSource at a time; callers
    // issuing one back-to-back call per selected Uri is exactly what a multi-photo pick needs.
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS_PER_PICK),
    ) { uris -> uris.forEach { uri -> onPhotoSourceSelected(GalleryImportPhotoSource(uri)) } }

    // Result ignored — see this function's own doc comment on why a denial never blocks the pick.
    val requestMediaLocationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    return PhotoAcquisitionLaunchers(
        launchCamera = { requestCameraPermission.launch(Manifest.permission.CAMERA) },
        launchGallery = {
            // Below API 29 there is nothing ACCESS_MEDIA_LOCATION gates (MediaStore.setRequireOriginal
            // doesn't exist yet — see FilePhotoStore's own doc comment), so requesting it there would
            // just be a pointless prompt.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestMediaLocationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
            pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
    )
}

/** The two acquisition triggers [rememberPhotoAcquisitionLaunchers] hands back — a plain pair of callbacks, not a sealed type, since neither caller branches on which one fired. */
internal class PhotoAcquisitionLaunchers(val launchCamera: () -> Unit, val launchGallery: () -> Unit)

/** How many photos a single "Gallery" pick can select at once — the project owner's own cap, not a platform default. Shared by every acquisition surface via [rememberPhotoAcquisitionLaunchers]. */
internal const val MAX_PHOTOS_PER_PICK = 10
