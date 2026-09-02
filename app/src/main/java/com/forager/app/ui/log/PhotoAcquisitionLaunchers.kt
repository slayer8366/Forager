package com.forager.app.ui.log

import android.Manifest
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
import com.forager.app.photo.ContentUriPhotoSource

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
            onPhotoSourceSelected(ContentUriPhotoSource(capture.uri))
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
    ) { uris -> uris.forEach { uri -> onPhotoSourceSelected(ContentUriPhotoSource(uri)) } }

    return PhotoAcquisitionLaunchers(
        launchCamera = { requestCameraPermission.launch(Manifest.permission.CAMERA) },
        launchGallery = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
}

/** The two acquisition triggers [rememberPhotoAcquisitionLaunchers] hands back — a plain pair of callbacks, not a sealed type, since neither caller branches on which one fired. */
internal class PhotoAcquisitionLaunchers(val launchCamera: () -> Unit, val launchGallery: () -> Unit)

/** How many photos a single "Gallery" pick can select at once — the project owner's own cap, not a platform default. Shared by every acquisition surface via [rememberPhotoAcquisitionLaunchers]. */
internal const val MAX_PHOTOS_PER_PICK = 10
